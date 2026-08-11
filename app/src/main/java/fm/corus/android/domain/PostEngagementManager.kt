package fm.corus.android.domain

import com.google.firebase.firestore.ListenerRegistration
import fm.corus.android.data.model.CymbalPost
import fm.corus.android.data.remote.CloudFunctionsDataSource
import fm.corus.android.data.remote.FirestoreDataSource
import fm.corus.android.data.repository.PostRepository
import fm.corus.android.data.repository.SubscriptionRepository
import fm.corus.android.service.AnalyticsService
import fm.corus.android.service.RemoteConfigService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/** UI events related to the save cap, observed by the host activity / Compose root. */
sealed class SaveCapEvent {
    /** User attempted to save when at/over cap. UI should open the paywall. */
    object PaywallRequested : SaveCapEvent()
    /** Show a transient warning toast/snackbar. `tappable=true` means tapping opens paywall. */
    data class WarningToast(val message: String, val savesRemaining: Int, val tappable: Boolean) : SaveCapEvent()
}

data class EngagementState(
    val likeCount: Int = 0,
    val commentCount: Int = 0,
    val repostCount: Int = 0,
    val isLiked: Boolean = false,
    val isSaved: Boolean = false,
    /** Save count. Optimistically incremented/decremented on tap (like the
     *  comment count) so the number reflects the viewer's own save the instant
     *  they toggle. This matters most for the sole-saver case: unsaving a post
     *  you were the only saver of must drop the count to 0 (hidden) immediately,
     *  not linger at 1 until the onSaveCreated/onSaveDeleted trigger reconciles
     *  the post doc. A save in-flight marker protects the optimistic value from a
     *  stale refresh/listener snapshot fired before the trigger committed. The UI
     *  still floors it to 1 while [isSaved] is true as a backstop. */
    val saveCount: Int = 0,
)

/**
 * Manages optimistic engagement state for posts.
 * Mirrors iOS PostEngagementStore — local state is updated immediately,
 * then synced to Firestore in the background.
 *
 * Also manages per-post real-time Firestore listeners with reference counting
 * (matching iOS PostEngagementStore.startListening/stopListening).
 */
@Singleton
class PostEngagementManager @Inject constructor(
    private val postRepository: PostRepository,
    private val firestoreDataSource: FirestoreDataSource,
    private val hapticManager: HapticManager,
    private val subscriptionRepository: SubscriptionRepository,
    private val remoteConfig: RemoteConfigService,
    private val analyticsService: AnalyticsService,
    private val saveChangedEvent: SaveChangedEvent,
    private val spotifySaveAutoAdd: SpotifySaveAutoAdd,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _states = MutableStateFlow<Map<String, EngagementState>>(emptyMap())
    val states: StateFlow<Map<String, EngagementState>> = _states.asStateFlow()

    private val _saveCapEvents = MutableSharedFlow<SaveCapEvent>(extraBufferCapacity = 4)
    val saveCapEvents: SharedFlow<SaveCapEvent> = _saveCapEvents.asSharedFlow()

    /** Post IDs the user has modified locally (like/save toggle) — these are preserved on refresh. */
    private val userModifiedPostIds = Collections.synchronizedSet(mutableSetOf<String>())

    // ── Real-time listener infrastructure (matching iOS PostEngagementStore) ──

    /** Reference count per postId — listener is active while refCount > 0. */
    private val listenerRefCounts = ConcurrentHashMap<String, Int>()

    /** Active Firestore ListenerRegistrations keyed by postId. */
    private val activeListeners = ConcurrentHashMap<String, ListenerRegistration>()

    /** Post IDs where a like/unlike network request is in flight.
     *  While in flight, listener updates skip likeCount to avoid overwriting optimistic state. */
    private val likeInFlightIds = Collections.synchronizedSet(mutableSetOf<String>())

    /**
     * Per-engagement in-flight markers with expiry timestamps (epoch millis).
     * `initState` and `applyListenerUpdate` skip the corresponding count if its
     * marker is still active, so a stale refresh (the post-comment / post-repost
     * cloud function aggregation can lag 10–20s before the post doc's count is
     * updated) can't clobber an optimistic mutation. Markers are time-bounded,
     * so other users' activity syncs on subsequent refreshes.
     */
    private val commentInFlightUntil = ConcurrentHashMap<String, Long>()
    private val repostInFlightUntil = ConcurrentHashMap<String, Long>()
    private val saveInFlightUntil = ConcurrentHashMap<String, Long>()
    private val likeRetainedUntil = ConcurrentHashMap<String, Long>()

    /** Retention window for optimistic-count protection. Sized to cover the
     *  cloud-function aggregation lag plus slack, while staying short enough
     *  that other users' activity syncs on the next refresh after that. */
    private val inFlightRetentionMs: Long = 30_000L

    private fun markCommentInFlight(postId: String) {
        commentInFlightUntil[postId] = System.currentTimeMillis() + inFlightRetentionMs
    }

    private fun markRepostInFlight(postId: String) {
        repostInFlightUntil[postId] = System.currentTimeMillis() + inFlightRetentionMs
    }

    private fun markSaveInFlight(postId: String) {
        saveInFlightUntil[postId] = System.currentTimeMillis() + inFlightRetentionMs
    }

    private fun markLikeRetained(postId: String) {
        likeRetainedUntil[postId] = System.currentTimeMillis() + inFlightRetentionMs
    }

    private fun isCommentInFlight(postId: String): Boolean = isMarkerActive(commentInFlightUntil, postId)
    private fun isRepostInFlight(postId: String): Boolean = isMarkerActive(repostInFlightUntil, postId)
    private fun isSaveInFlight(postId: String): Boolean = isMarkerActive(saveInFlightUntil, postId)
    private fun isLikeRetained(postId: String): Boolean = isMarkerActive(likeRetainedUntil, postId)

    private fun isMarkerActive(map: ConcurrentHashMap<String, Long>, postId: String): Boolean {
        val until = map[postId] ?: return false
        if (until <= System.currentTimeMillis()) {
            map.remove(postId)
            return false
        }
        return true
    }

    fun getState(postId: String): EngagementState? = _states.value[postId]

    fun initState(postId: String, likeCount: Int, commentCount: Int, repostCount: Int, isLiked: Boolean, isSaved: Boolean, saveCount: Int = 0) {
        _states.update { map ->
            if (map.containsKey(postId) && userModifiedPostIds.contains(postId)) map
            else {
                val existing = map[postId]
                if (existing != null) {
                    // Preserve local isLiked/isSaved state to avoid flash;
                    // checkLikeStatuses() will reconcile from Firestore shortly after.
                    // Per-count in-flight checks keep optimistic comment/repost/like
                    // bumps from being clobbered by a feed payload that was fetched
                    // before the cloud-function count aggregation committed.
                    val safeLikeCount = if (likeInFlightIds.contains(postId) || isLikeRetained(postId)) existing.likeCount else likeCount
                    val safeCommentCount = if (isCommentInFlight(postId)) existing.commentCount else commentCount
                    val safeRepostCount = if (isRepostInFlight(postId)) existing.repostCount else repostCount
                    // saveCount IS optimistic — guard it the same way so a feed
                    // payload fetched before the onSave(Created|Deleted) trigger
                    // committed can't clobber the viewer's just-toggled value.
                    val safeSaveCount = if (isSaveInFlight(postId)) existing.saveCount else saveCount
                    map + (postId to existing.copy(
                        likeCount = safeLikeCount,
                        commentCount = safeCommentCount,
                        repostCount = safeRepostCount,
                        saveCount = safeSaveCount,
                    ))
                } else {
                    map + (postId to EngagementState(likeCount, commentCount, repostCount, isLiked, isSaved, saveCount))
                }
            }
        }
    }

    // ── Real-time listeners ──

    fun startListening(postId: String) {
        val newCount = listenerRefCounts.merge(postId, 1, Int::plus) ?: 1
        if (newCount == 1) {
            val registration = firestoreDataSource.listenForPostUpdates(
                postId = postId,
                onUpdate = { likeCount, commentCount, repostCount, saveCount ->
                    applyListenerUpdate(postId, likeCount, commentCount, repostCount, saveCount)
                },
            )
            activeListeners[postId] = registration
        }
    }

    fun stopListening(postId: String) {
        val newCount = listenerRefCounts.merge(postId, -1, Int::plus) ?: 0
        if (newCount <= 0) {
            listenerRefCounts.remove(postId)
            activeListeners.remove(postId)?.remove()
        }
    }

    private fun applyListenerUpdate(postId: String, likeCount: Int, commentCount: Int, repostCount: Int, saveCount: Int) {
        _states.update { map ->
            val current = map[postId] ?: return@update map
            // The post-doc snapshot can fire with a stale count if some other
            // field on the post changed before the count-aggregation trigger
            // committed — so each count is gated by its own in-flight marker.
            val safeLikeCount = if (likeInFlightIds.contains(postId) || isLikeRetained(postId)) current.likeCount else likeCount
            val safeCommentCount = if (isCommentInFlight(postId)) current.commentCount else commentCount
            val safeRepostCount = if (isRepostInFlight(postId)) current.repostCount else repostCount
            // saveCount IS optimistic — protect it like the like/comment counts.
            val safeSaveCount = if (isSaveInFlight(postId)) current.saveCount else saveCount
            map + (postId to current.copy(
                likeCount = safeLikeCount,
                commentCount = safeCommentCount,
                repostCount = safeRepostCount,
                saveCount = safeSaveCount,
            ))
        }
    }

    // ── Optimistic engagement actions ──

    fun toggleLike(postId: String, userId: String) {
        val current = _states.value[postId] ?: return
        val newLiked = !current.isLiked
        val newCount = if (newLiked) current.likeCount + 1 else maxOf(0, current.likeCount - 1)

        // Mirrors iOS PostCard / PostDetailView / FeaturedCymbalView / FeaturedMoviePosterView toggleLike haptic.
        hapticManager.impact(HapticManager.ImpactStyle.LIGHT)

        likeInFlightIds.add(postId)
        userModifiedPostIds.add(postId)
        _states.update { map ->
            map + (postId to current.copy(isLiked = newLiked, likeCount = newCount))
        }
        val mediaType = postRepository.getCachedPost(postId)?.mediaType?.value ?: "track"
        if (newLiked) {
            analyticsService.logPostLiked(postId, mediaType)
        } else {
            analyticsService.logPostUnliked(postId, mediaType)
        }

        scope.launch {
            try {
                if (newLiked) {
                    postRepository.likePost(userId, postId)
                    // Send notification to post owner (matches iOS behavior)
                    try {
                        val post = postRepository.getCachedPost(postId)
                        if (post != null) {
                            postRepository.createNotification(
                                type = "like",
                                fromUserId = userId,
                                toUserId = post.user.id,
                                postId = postId,
                                postAlbumArtURL = post.displayImageURL,
                            )
                        }
                    } catch (_: Exception) { }
                } else {
                    postRepository.unlikePost(userId, postId)
                }
            } catch (e: Exception) {
                // Rollback on failure
                _states.update { map ->
                    map + (postId to current)
                }
                analyticsService.logLikeError(postId, e.message ?: "")
            } finally {
                likeInFlightIds.remove(postId)
                // Hold the in-flight marker for a retention window so a stale
                // refresh fired right after the request completes can't clobber
                // the optimistic count with the pre-toggle value the feed
                // payload was built with.
                markLikeRetained(postId)
                userModifiedPostIds.remove(postId)
            }
        }
    }

    fun toggleSave(postId: String, userId: String) {
        val current = _states.value[postId] ?: return
        val newSaved = !current.isSaved

        // Save cap pre-check on the way in (instant, no network). Server still
        // enforces; this is for UX latency. Only applies when about to save.
        if (newSaved && subscriptionRepository.shouldRejectSave()) {
            hapticManager.impact(HapticManager.ImpactStyle.LIGHT)
            analyticsService.logSaveCapReached(subscriptionRepository.savesCount.value)
            scope.launch { _saveCapEvents.emit(SaveCapEvent.PaywallRequested) }
            return
        }

        // Mirrors iOS PostCard / PostDetailView / PostContextMenu toggleSave haptic.
        hapticManager.impact(HapticManager.ImpactStyle.LIGHT)

        // Optimistic count — only for OTHER people's posts. The server excludes
        // the author's own save from the public count
        // (adjustPostSaveCountExcludingSelf), so bumping a self-save here would
        // show the author a transiently inflated number that snaps back on
        // reconcile. The in-flight marker protects the bump from a stale refresh;
        // both rollback paths below restore `current` (its pre-toggle saveCount
        // included), so no manual revert is needed.
        userModifiedPostIds.add(postId)
        val isOwnPost = postRepository.getCachedPost(postId)?.user?.id == userId
        val newSaveCount = when {
            isOwnPost -> current.saveCount
            newSaved -> current.saveCount + 1
            else -> maxOf(0, current.saveCount - 1)
        }
        if (!isOwnPost) markSaveInFlight(postId)
        _states.update { map ->
            map + (postId to current.copy(isSaved = newSaved, saveCount = newSaveCount))
        }
        val mediaType = postRepository.getCachedPost(postId)?.mediaType?.value ?: "track"
        if (newSaved) {
            analyticsService.logPostSaved(postId, mediaType)
        } else {
            analyticsService.logPostUnsaved(postId, mediaType)
        }

        scope.launch {
            try {
                if (newSaved) {
                    val result = postRepository.savePost(userId, postId)
                    subscriptionRepository.setSavesCount(result.savesCount)
                    maybeFireSaveWarningToast(result.savesCount)
                    val post = try { postRepository.getCachedPost(postId) } catch (_: Exception) { null }
                    // Keep the profile Saves tab live without a refetch.
                    saveChangedEvent.notify(postId, true, post)
                    // Send save notification (matches iOS)
                    if (post != null) {
                        try {
                            postRepository.createNotification(
                                type = "save",
                                fromUserId = userId,
                                toUserId = post.user.id,
                                postId = postId,
                                postAlbumArtURL = post.displayImageURL,
                            )
                        } catch (_: Exception) { }
                        // "Add Saved Songs to Library" opt-in — fire-and-forget,
                        // only on a NEW save (never on unsave, see the `else`
                        // branch below). Silently no-ops when the flag/toggle
                        // are off or the post isn't resolvable on Spotify.
                        spotifySaveAutoAdd.handleSaved(post)
                    }
                } else {
                    val newCount = postRepository.unsavePost(userId, postId)
                    subscriptionRepository.setSavesCount(newCount)
                    saveChangedEvent.notify(postId, false, null)
                }
            } catch (e: CloudFunctionsDataSource.SaveCapReachedException) {
                // Server rejected — local cache was stale. Sync count, revert UI, request paywall.
                subscriptionRepository.setSavesCount(e.savesCount)
                _states.update { map -> map + (postId to current) }
                analyticsService.logSaveCapReached(e.savesCount)
                _saveCapEvents.emit(SaveCapEvent.PaywallRequested)
            } catch (e: Exception) {
                _states.update { map ->
                    map + (postId to current)
                }
                analyticsService.logSaveError(postId, e.message ?: "")
            } finally {
                userModifiedPostIds.remove(postId)
            }
        }
    }

    private suspend fun maybeFireSaveWarningToast(savesCountAfter: Int) {
        if (!remoteConfig.saveCapEnforced) return
        if (subscriptionRepository.hasFullAccess) return
        val limit = remoteConfig.saveCapLimit
        val warnAt = remoteConfig.saveCapWarningAt
        if (savesCountAfter < warnAt || savesCountAfter > limit) return
        val remaining = (limit - savesCountAfter).coerceAtLeast(0)
        val (msg, tappable) = when (remaining) {
            0 -> "You've used your last free save. Upgrade for unlimited." to true
            1 -> "1 save left" to false
            else -> "$remaining saves left" to false
        }
        analyticsService.logSaveWarningToastShown(remaining)
        _saveCapEvents.emit(SaveCapEvent.WarningToast(msg, remaining, tappable))
    }

    fun incrementCommentCount(postId: String) {
        markCommentInFlight(postId)
        _states.update { map ->
            val current = map[postId] ?: return@update map
            map + (postId to current.copy(commentCount = current.commentCount + 1))
        }
    }

    fun decrementCommentCount(postId: String) {
        markCommentInFlight(postId)
        _states.update { map ->
            val current = map[postId] ?: return@update map
            map + (postId to current.copy(commentCount = (current.commentCount - 1).coerceAtLeast(0)))
        }
    }

    /** Optimistically bumps the repost count on the original post. Called
     *  by ComposeViewModel after a successful repost-with-caption post. */
    fun incrementRepostCount(postId: String) {
        markRepostInFlight(postId)
        _states.update { map ->
            val current = map[postId] ?: return@update map
            map + (postId to current.copy(repostCount = current.repostCount + 1))
        }
    }

    /** Safety net mirroring iOS `PostEngagementStore.reconcileCommentCount`:
     *  when a rendered preview list shows more comments than the cached badge
     *  count, ratchet the badge up. Never decreases. */
    fun reconcileCommentCount(postId: String, atLeast: Int) {
        if (atLeast <= 0) return
        _states.update { map ->
            val current = map[postId] ?: return@update map
            if (atLeast > current.commentCount) {
                map + (postId to current.copy(commentCount = atLeast))
            } else {
                map
            }
        }
    }

    /** Targeted refresh of a single post's denormalized counts. Called from the
     *  FCM service when a comment/like/repost notification arrives, so the feed
     *  badge updates immediately without waiting for pull-to-refresh.
     *  In-flight markers in `applyListenerUpdate` shape protection — we reuse
     *  the same guarded write path here. */
    fun refreshCountsFromServer(postId: String) {
        scope.launch {
            val counts = firestoreDataSource.fetchPostCounts(postId) ?: return@launch
            applyListenerUpdate(postId, counts.likeCount, counts.commentCount, counts.repostCount, counts.saveCount)
        }
    }

    /**
     * Reconcile like status from Firestore for the given page of posts.
     *
     * Resolves all [postIds] in ONE batched `whereIn` query against the viewer's
     * own `users_v2/{uid}/liked` index (FirestoreDataSource.fetchLikedStates),
     * which bills only for the liked matches — instead of a per-post
     * `isPostLiked` read. A 30-post page where the viewer liked 2 costs ~2 reads
     * instead of 30. The [userId] param is kept for call-site compatibility;
     * fetchLikedStates derives the uid from auth.
     *
     * Optimistic flips (userModified / in-flight / retained) are never clobbered.
     * A transient failure leaves state untouched; the next refresh reconciles.
     */
    fun checkLikeStatuses(postIds: List<String>, userId: String) {
        if (postIds.isEmpty()) return
        scope.launch {
            val liked = try {
                firestoreDataSource.fetchLikedStates(postIds)
            } catch (_: Exception) {
                return@launch
            }

            _states.update { map ->
                var updated = map
                for (postId in postIds) {
                    if (userModifiedPostIds.contains(postId)) continue
                    if (likeInFlightIds.contains(postId) || isLikeRetained(postId)) continue
                    val current = updated[postId] ?: continue
                    val isLiked = liked.contains(postId)
                    if (current.isLiked != isLiked) {
                        updated = updated + (postId to current.copy(isLiked = isLiked))
                    }
                }
                updated
            }
        }
    }

    /**
     * Reconcile save status from Firestore for the given page of posts.
     *
     * The save/bookmark equivalent of [checkLikeStatuses]: resolves all [postIds]
     * in ONE batched `whereIn` query against the viewer's own
     * `users_v2/{uid}/saves` index (FirestoreDataSource.fetchSavedStates), billing
     * only for the saved matches. Posts always seed `isSaved = false` (the backend
     * doesn't return it), so without this the bookmark icon never fills in — even
     * for posts opened straight from the Saves tab. The [userId] param is kept for
     * call-site symmetry with checkLikeStatuses; fetchSavedStates derives the uid
     * from auth.
     *
     * Optimistic toggles (userModified) are never clobbered. A transient failure
     * leaves state untouched; the next refresh reconciles.
     */
    fun checkSaveStatuses(postIds: List<String>, userId: String) {
        if (postIds.isEmpty()) return
        scope.launch {
            val saved = try {
                firestoreDataSource.fetchSavedStates(postIds)
            } catch (_: Exception) {
                return@launch
            }

            _states.update { map ->
                var updated = map
                for (postId in postIds) {
                    if (userModifiedPostIds.contains(postId)) continue
                    val current = updated[postId] ?: continue
                    val isSaved = saved.contains(postId)
                    if (current.isSaved != isSaved) {
                        updated = updated + (postId to current.copy(isSaved = isSaved))
                    }
                }
                updated
            }
        }
    }

    /**
     * Authoritatively mark posts as saved. Used when loading the viewer's own
     * Saves list, where list membership IS the ground truth — so the bookmark
     * renders filled immediately and there's no unfilled→filled flash from the
     * [checkSaveStatuses] Firestore round-trip when a post is opened from Saves.
     *
     * Forces `isSaved = true` even over a stale existing entry (unlike
     * [initState], which preserves the prior flag). In-flight optimistic toggles
     * (userModified) are left alone. Creates a minimal entry if none exists yet;
     * the subsequent initState / listener fills in the counts.
     */
    fun seedSavedState(postIds: List<String>) {
        if (postIds.isEmpty()) return
        _states.update { map ->
            var updated = map
            for (postId in postIds) {
                if (userModifiedPostIds.contains(postId)) continue
                val current = updated[postId]
                updated = if (current != null) {
                    if (current.isSaved) updated else updated + (postId to current.copy(isSaved = true))
                } else {
                    updated + (postId to EngagementState(isSaved = true))
                }
            }
            updated
        }
    }

    fun clearAll() {
        activeListeners.values.forEach { it.remove() }
        activeListeners.clear()
        listenerRefCounts.clear()
        likeInFlightIds.clear()
        commentInFlightUntil.clear()
        repostInFlightUntil.clear()
        saveInFlightUntil.clear()
        likeRetainedUntil.clear()
        _states.value = emptyMap()
        userModifiedPostIds.clear()
    }
}
