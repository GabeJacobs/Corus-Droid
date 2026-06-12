package fm.corus.android.ui.screens.notifications

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import fm.corus.android.R
import fm.corus.android.data.model.CommentAttachedFilm
import fm.corus.android.data.model.CommentAttachedSong
import fm.corus.android.data.model.CymbalMovie
import fm.corus.android.data.model.CymbalNotification
import fm.corus.android.data.model.CymbalTrack
import fm.corus.android.data.repository.AuthRepository
import fm.corus.android.data.repository.NotificationRepository
import fm.corus.android.data.repository.PostRepository
import fm.corus.android.data.repository.UserRepository
import fm.corus.android.domain.NowPlayingManager
import fm.corus.android.domain.PostEngagementManager
import fm.corus.android.service.AnalyticsService
import fm.corus.android.service.NetworkMonitor
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import android.util.Log
import javax.inject.Inject

@HiltViewModel
class NotificationsViewModel @Inject constructor(
    private val notificationRepository: NotificationRepository,
    private val authRepository: AuthRepository,
    private val userRepository: UserRepository,
    private val postRepository: PostRepository,
    private val engagementManager: PostEngagementManager,
    val nowPlayingManager: NowPlayingManager,
    private val analyticsService: AnalyticsService,
    private val remoteConfigService: fm.corus.android.service.RemoteConfigService,
    private val gifRepository: fm.corus.android.data.repository.GifRepository,
    private val networkMonitor: NetworkMonitor,
    @ApplicationContext private val context: Context,
) : ViewModel() {

    val gifSupport: Boolean
        get() = remoteConfigService.gifSupport

    private val pageSize = 15

    private val _notifications = MutableStateFlow<List<CymbalNotification>>(emptyList())
    val notifications: StateFlow<List<CymbalNotification>> = _notifications.asStateFlow()

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    private val _isLoadingMore = MutableStateFlow(false)
    val isLoadingMore: StateFlow<Boolean> = _isLoadingMore.asStateFlow()

    private val _hasMoreNotifications = MutableStateFlow(true)
    val hasMoreNotifications: StateFlow<Boolean> = _hasMoreNotifications.asStateFlow()

    /** True when [loadNotifications] failed AND there's nothing to render — the
     *  screen swaps the normal empty state for an offline-retry view. */
    private val _hasLoadError = MutableStateFlow(false)
    val hasLoadError: StateFlow<Boolean> = _hasLoadError.asStateFlow()

    val isConnected: StateFlow<Boolean> = networkMonitor.isConnected

    init {
        viewModelScope.launch {
            networkMonitor.isConnected.collect { connected ->
                // Auto-retry the initial load when the network returns if the previous
                // attempt failed and we have nothing on screen. Mirrors iOS
                // NotificationsView's reconnect handler.
                if (connected && _hasLoadError.value && _notifications.value.isEmpty()) {
                    retryLoad()
                }
            }
        }
    }

    @Volatile private var hasStartedLoading = false

    val followingIds: StateFlow<Set<String>> = userRepository.followingIds

    /**
     * Reverse follow direction: actors who follow the current user. Drives the
     * "Follow back" vs "Follow" label on contact_joined ("joined Corus!") rows,
     * where — unlike FOLLOW rows — the relationship isn't implied by the type.
     */
    private val _followsMeIds = MutableStateFlow<Set<String>>(emptySet())
    val followsMeIds: StateFlow<Set<String>> = _followsMeIds.asStateFlow()

    private val _likedCommentIds = MutableStateFlow<Set<String>>(emptySet())
    val likedCommentIds: StateFlow<Set<String>> = _likedCommentIds.asStateFlow()

    private val _replyingToNotification = MutableStateFlow<CymbalNotification?>(null)
    val replyingToNotification: StateFlow<CymbalNotification?> = _replyingToNotification.asStateFlow()

    private val _isSendingReply = MutableStateFlow(false)
    val isSendingReply: StateFlow<Boolean> = _isSendingReply.asStateFlow()

    // ── Reply attachment state ──
    private val _replyPendingSong = MutableStateFlow<CommentAttachedSong?>(null)
    val replyPendingSong: StateFlow<CommentAttachedSong?> = _replyPendingSong.asStateFlow()

    private val _replyPendingFilm = MutableStateFlow<CommentAttachedFilm?>(null)
    val replyPendingFilm: StateFlow<CommentAttachedFilm?> = _replyPendingFilm.asStateFlow()

    private val _replyPendingGif = MutableStateFlow<fm.corus.android.data.model.KlipyGif?>(null)
    val replyPendingGif: StateFlow<fm.corus.android.data.model.KlipyGif?> = _replyPendingGif.asStateFlow()

    fun attachReplySong(track: CymbalTrack) {
        _replyPendingSong.value = CommentAttachedSong.fromTrack(track)
        _replyPendingFilm.value = null
        _replyPendingGif.value = null
    }

    fun attachReplyFilm(movie: CymbalMovie) {
        _replyPendingFilm.value = CommentAttachedFilm.fromMovie(movie)
        _replyPendingSong.value = null
        _replyPendingGif.value = null
    }

    fun attachReplyGif(gif: fm.corus.android.data.model.KlipyGif) {
        _replyPendingGif.value = gif
        _replyPendingSong.value = null
        _replyPendingFilm.value = null
    }

    fun clearReplyAttachment() {
        _replyPendingSong.value = null
        _replyPendingFilm.value = null
        _replyPendingGif.value = null
    }

    /** One-shot "Reply sent" / "Failed to send reply" toast events, consumed by the screen. */
    private val _replyToastEvents = Channel<String>(Channel.BUFFERED)
    val replyToastEvents: Flow<String> = _replyToastEvents.receiveAsFlow()

    /**
     * Client-side set of notification IDs that arrived since the user last
     * visited the Activity tab. Drives the subtle accent-tint background on
     * rows the user hasn't interacted with yet — matches iOS
     * `newNotificationIds`. Cleared when the user taps a row, and reset
     * each time the ViewModel is re-created.
     */
    private val _newNotificationIds = MutableStateFlow<Set<String>>(emptySet())
    val newNotificationIds: StateFlow<Set<String>> = _newNotificationIds.asStateFlow()

    /** Captured before updating the user doc so we can flag later-arriving
     * notifications as "new" relative to the previous seen cutoff. */
    private var lastSeenCutoffMs: Long? = null
    /** Tracks whether we've already computed the new-id set for this session —
     * subsequent listener emissions update counts but don't reshuffle the
     * highlight set. */
    private var hasComputedNewIds = false

    fun refreshNotifications() {
        val userId = authRepository.currentUserId ?: return
        viewModelScope.launch {
            _isRefreshing.value = true
            try {
                _notifications.value = notificationRepository.getNotifications(userId, limit = pageSize)
                _hasMoreNotifications.value = _notifications.value.size >= pageSize
                loadCommentLikeStatuses(userId)
                loadFollowsMeStatuses(userId)
            } catch (_: Exception) { }
            _isRefreshing.value = false
        }
    }

    /**
     * One-time setup of the live notification list: captures the previous
     * "seen" cutoff (for new-row highlighting) and attaches the real-time
     * listener. Runs once per ViewModel lifetime — and because all tab
     * screens stay composed (MainTabScreen keeps invisible tabs off-screen
     * rather than disposing them), the triggering `LaunchedEffect(Unit)` fires
     * at app launch, not on tab entry. The actual "mark as viewed" side effect
     * therefore lives in [markActivityViewed], which is driven by the Activity
     * tab-activation trigger so it re-runs on every visit. Do NOT stamp
     * lastSeen or markAllRead here — that would clear the badge at launch
     * before the user has viewed anything.
     */
    fun loadNotifications() {
        if (hasStartedLoading) return
        hasStartedLoading = true
        val userId = authRepository.currentUserId ?: return
        viewModelScope.launch {
            _isLoading.value = true
            // Capture the previous "seen" cutoff so we can flag later-arriving
            // notifications as new for dim styling. We intentionally do NOT
            // stamp a new lastSeen here — that happens in markActivityViewed
            // when the user actually opens the tab.
            lastSeenCutoffMs = try {
                notificationRepository.fetchLastSeenNotificationsAt(userId)
            } catch (_: Exception) { null }
            try {
                // Use real-time listener for the first page
                notificationRepository.observeNotifications(userId, limit = pageSize).collect { incoming ->
                    computeNewIdsOnce(incoming)
                    mergeNotifications(incoming)
                    _hasMoreNotifications.value = incoming.size >= pageSize
                    _isLoading.value = false
                    _hasLoadError.value = false
                    loadCommentLikeStatuses(userId)
                    loadFollowsMeStatuses(userId)
                }
            } catch (_: Exception) {
                // Fallback to one-shot fetch
                var fellBackOk = false
                try {
                    val fetched = notificationRepository.getNotifications(userId, limit = pageSize)
                    computeNewIdsOnce(fetched)
                    _notifications.value = fetched
                    _hasMoreNotifications.value = fetched.size >= pageSize
                    loadCommentLikeStatuses(userId)
                    loadFollowsMeStatuses(userId)
                    fellBackOk = true
                } catch (_: Exception) { }
                _isLoading.value = false
                if (fellBackOk) {
                    _hasLoadError.value = false
                } else if (_notifications.value.isEmpty()) {
                    _hasLoadError.value = true
                }
            }
        }
    }

    /** Re-run [loadNotifications] after a previous failure. Called from the
     *  retry button and from the reconnect observer when the network returns. */
    fun retryLoad() {
        hasStartedLoading = false
        _hasLoadError.value = false
        _isLoading.value = true
        loadNotifications()
    }

    /**
     * On the first batch of notifications we receive, compute the set of
     * "new since last seen" ids. A notification is highlighted only if it
     * is still unread AND (there's no prior cutoff, or its timestamp is
     * newer than the cutoff). The isRead check is essential: once the user
     * taps a notification (or the tab auto-marks all read), that `isRead=true`
     * state persists server-side — we must respect it on the next launch even
     * though the timestamp is still newer than the previous `lastSeen` stamp.
     */
    private fun computeNewIdsOnce(items: List<CymbalNotification>) {
        if (hasComputedNewIds) return
        hasComputedNewIds = true
        val cutoff = lastSeenCutoffMs
        val ids = items.filter { notif ->
            !notif.isRead && (cutoff == null || notif.timestamp.time > cutoff)
        }.map { it.id }
        _newNotificationIds.value = ids.toSet()
    }

    /** Removes a single notification from the "new" set — called when the user
     * taps the row. Mirrors iOS `markAsRead(notification)` behaviour. */
    fun markNotificationTapped(notificationId: String) {
        if (_newNotificationIds.value.contains(notificationId)) {
            _newNotificationIds.value = _newNotificationIds.value - notificationId
        }
        viewModelScope.launch {
            try { notificationRepository.markNotificationRead(notificationId) } catch (_: Exception) { }
        }
        // Taste-match-specific tap analytics (mirrors iOS).
        val n = _notifications.value.firstOrNull { it.id == notificationId } ?: return
        if (n.type == fm.corus.android.data.model.NotificationType.TASTE_MATCH) {
            analyticsService.logTasteMatchFeedRowTapped(
                subtype = n.subtype ?: "unknown",
                fromUserId = n.fromUser.id,
            )
        }
    }

    /** Fires once when a taste_match row appears in the activity feed. */
    fun markTasteMatchRowViewed(notification: CymbalNotification) {
        if (notification.type != fm.corus.android.data.model.NotificationType.TASTE_MATCH) return
        analyticsService.logTasteMatchFeedRowViewed(
            subtype = notification.subtype ?: "unknown",
            fromUserId = notification.fromUser.id,
        )
    }

    /**
     * Merges incoming real-time listener results (first page) with existing
     * paginated items, matching iOS behaviour. Only the "head" window is
     * reconciled — older paginated "tail" items are kept untouched.
     */
    private fun mergeNotifications(incoming: List<CymbalNotification>) {
        val current = _notifications.value
        if (current.isEmpty()) {
            _notifications.value = incoming
            Log.d("Notifications", "merge: initial set, ${incoming.size} items")
            return
        }

        val incomingIds = incoming.map { it.id }.toSet()

        // Split: items in the incoming window vs older paginated items
        val tailItems = current.filter { it.id !in incomingIds }

        Log.d("Notifications", "merge: incoming=${incoming.size}, tail=${tailItems.size}, total=${incoming.size + tailItems.size}")

        // Build head from incoming order (includes new + updated items)
        _notifications.value = incoming + tailItems
    }

    fun loadMoreNotifications() {
        val userId = authRepository.currentUserId ?: return
        Log.d("Notifications", "loadMore called: isLoadingMore=${_isLoadingMore.value}, hasMore=${_hasMoreNotifications.value}, count=${_notifications.value.size}")
        if (_isLoadingMore.value || !_hasMoreNotifications.value) return
        val lastTimestamp = _notifications.value.lastOrNull()?.timestamp?.time
        if (lastTimestamp == null) {
            Log.d("Notifications", "loadMore: no lastTimestamp, aborting")
            return
        }
        Log.d("Notifications", "loadMore: fetching with lastTimestamp=$lastTimestamp")

        viewModelScope.launch {
            _isLoadingMore.value = true
            try {
                val fetched = notificationRepository.getNotifications(
                    userId, limit = pageSize, lastTimestamp = lastTimestamp,
                )
                Log.d("Notifications", "loadMore: fetched ${fetched.size} items")
                val existingIds = _notifications.value.map { it.id }.toSet()
                val newItems = fetched.filter { it.id !in existingIds }
                Log.d("Notifications", "loadMore: ${newItems.size} new items after dedup")
                _notifications.value = _notifications.value + newItems
                _hasMoreNotifications.value = fetched.size >= pageSize && newItems.isNotEmpty()
                Log.d("Notifications", "loadMore: hasMore=${_hasMoreNotifications.value}, total=${_notifications.value.size}")
                loadCommentLikeStatuses(userId)
                loadFollowsMeStatuses(userId)
            } catch (e: Exception) {
                Log.e("Notifications", "loadMore failed", e)
                _hasMoreNotifications.value = false
            }
            _isLoadingMore.value = false
        }
    }

    fun toggleFollow(targetUserId: String) {
        val currentUserId = authRepository.currentUserId ?: return
        viewModelScope.launch {
            val wasFollowing = userRepository.isFollowing(targetUserId)
            try {
                if (wasFollowing) {
                    userRepository.unfollowUser(currentUserId, targetUserId)
                } else {
                    userRepository.followUser(currentUserId, targetUserId)
                }
            } catch (_: Exception) { }
        }
    }

    fun markAllRead() {
        val userId = authRepository.currentUserId ?: return
        viewModelScope.launch {
            try { notificationRepository.markAllRead(userId) } catch (_: Exception) { }
        }
    }

    /**
     * Marks the Activity feed as viewed: stamps `lastSeenNotificationsAt` and
     * marks every unread notification read so the badge clears for real.
     *
     * Driven by the Activity tab-activation trigger, so it runs on *every*
     * visit to the tab — not once per ViewModel lifetime like
     * [loadNotifications]. This is the fix for the badge that appeared to clear
     * (optimistic in-memory zero) but never actually marked notifications read
     * on repeat visits, causing the count to snap back to the full accumulated
     * total whenever a new notification arrived. Mirrors iOS NotificationsView
     * re-running its load on `.notificationsTabBecameActive`.
     *
     * Safe to call repeatedly: markAllRead only touches unread docs and the
     * lastSeen stamp is idempotent.
     */
    fun markActivityViewed() {
        val userId = authRepository.currentUserId ?: return
        viewModelScope.launch {
            try { notificationRepository.updateLastSeenNotificationsAt(userId) } catch (_: Exception) { }
        }
        markAllRead()
    }

    // ── Comment actions on notification rows (matches iOS NotificationsView) ──

    /**
     * Loads the current user's like status for each comment referenced by a
     * notification that supports comment actions. Mirrors iOS
     * `loadCommentLikeStatuses` — Firestore doesn't return isCommentLiked, so
     * we query the subcollection per (postId, commentId).
     */
    private fun loadCommentLikeStatuses(userId: String) {
        val pending = _notifications.value
            .filter { it.supportsCommentActions }
            .mapNotNull { notif ->
                val pid = notif.postId
                val cid = notif.commentId
                if (pid != null && cid != null && cid !in _likedCommentIds.value) pid to cid else null
            }
            .distinct()
        if (pending.isEmpty()) return

        viewModelScope.launch {
            val results = pending.map { (pid, cid) ->
                async {
                    cid to (try { postRepository.isCommentLiked(userId, pid, cid) } catch (_: Exception) { false })
                }
            }.awaitAll()
            val likedIds = results.filter { it.second }.map { it.first }.toSet()
            if (likedIds.isNotEmpty()) {
                _likedCommentIds.value = _likedCommentIds.value + likedIds
            }
        }
    }

    /**
     * Resolves the reverse follow direction for contact_joined ("joined Corus!")
     * rows — which actors already follow the current user — so their button can
     * read "Follow back" instead of "Follow" when appropriate. FOLLOW rows don't
     * need this: the relationship is implied by the notification type.
     */
    private fun loadFollowsMeStatuses(userId: String) {
        val candidateIds = _notifications.value
            .filter { it.type == fm.corus.android.data.model.NotificationType.CONTACT_JOINED }
            .map { it.fromUser.id }
            .filter { it != userId && it !in _followsMeIds.value }
            .distinct()
        if (candidateIds.isEmpty()) return

        viewModelScope.launch {
            val followers = try {
                userRepository.checkFollowerStatusBatch(userId, candidateIds)
            } catch (_: Exception) { emptySet() }
            if (followers.isNotEmpty()) {
                _followsMeIds.value = _followsMeIds.value + followers
            }
        }
    }

    fun toggleCommentLike(notification: CymbalNotification) {
        val userId = authRepository.currentUserId ?: return
        val postId = notification.postId ?: return
        val commentId = notification.commentId ?: return

        val wasLiked = _likedCommentIds.value.contains(commentId)
        // Optimistic update
        _likedCommentIds.value = if (wasLiked) {
            _likedCommentIds.value - commentId
        } else {
            _likedCommentIds.value + commentId
        }

        viewModelScope.launch {
            try {
                if (wasLiked) {
                    postRepository.unlikeComment(userId, postId, commentId)
                } else {
                    // The `onCommentLikeCreated` trigger creates the
                    // comment_like notification server-side (including the
                    // self-like skip), so we don't duplicate it here. Matches
                    // iOS + Web.
                    postRepository.likeComment(userId, postId, commentId)
                }
            } catch (_: Exception) {
                // Revert on failure
                _likedCommentIds.value = if (wasLiked) {
                    _likedCommentIds.value + commentId
                } else {
                    _likedCommentIds.value - commentId
                }
            }
        }
    }

    fun setReplyingToNotification(notification: CymbalNotification?) {
        _replyingToNotification.value = notification
    }

    fun sendReply(text: String) {
        val userId = authRepository.currentUserId ?: return
        val notification = _replyingToNotification.value ?: return
        val postId = notification.postId ?: return
        val sourceCommentId = notification.commentId ?: return
        val trimmed = text.trim()
        val attachedSong = _replyPendingSong.value
        val attachedFilm = _replyPendingFilm.value
        val pendingGif = _replyPendingGif.value
        if (trimmed.isEmpty() && attachedSong == null && attachedFilm == null && pendingGif == null) return

        // GIF dispatch is a separate code path because addComment treats gifURL
        // mutually exclusive with song/film at the type level on the iOS client;
        // we route through sendGifReply for parity.
        if (pendingGif != null) {
            sendGifReply(pendingGif.fullURL, pendingGif.slug, trimmed)
            return
        }

        // Optimistically dismiss the reply bar so the user sees immediate
        // acknowledgment even if the network call is slow. A Toast confirms
        // the final outcome (success or failure).
        _replyingToNotification.value = null
        _replyPendingSong.value = null
        _replyPendingFilm.value = null
        _isSendingReply.value = true

        viewModelScope.launch {
            try {
                // The comment system supports two levels (top-level + replies). If the
                // notification's source comment is itself a reply, re-root onto its
                // top-level parent — otherwise the new comment would be orphaned and
                // never displayed in the thread.
                val parentCommentId =
                    postRepository.getCommentParentId(postId, sourceCommentId) ?: sourceCommentId
                val newCommentId = postRepository.addComment(
                    postId = postId,
                    userId = userId,
                    text = trimmed,
                    parentCommentId = parentCommentId,
                    replyToUserId = notification.fromUser.id,
                    attachedSong = attachedSong,
                    attachedFilm = attachedFilm,
                )
                engagementManager.incrementCommentCount(postId)

                // Create reply notification to the parent comment author. Push copy
                // suppresses synthesized fallback text so it reads "shared a song/film".
                try {
                    val post = postRepository.getCachedPost(postId)
                    val attachmentType = when {
                        attachedSong != null -> "song"
                        attachedFilm != null -> "film"
                        else -> null
                    }
                    val truncated = when {
                        trimmed.isNotEmpty() ->
                            if (trimmed.length > 100) trimmed.take(100) + "…" else trimmed
                        attachmentType == "song" -> "shared a song"
                        attachmentType == "film" -> "shared a film"
                        else -> ""
                    }
                    postRepository.createNotification(
                        type = "reply",
                        fromUserId = userId,
                        toUserId = notification.fromUser.id,
                        postId = postId,
                        postAlbumArtURL = post?.displayImageURL,
                        commentText = truncated,
                        commentId = newCommentId,
                        attachmentType = attachmentType,
                    )
                } catch (_: Exception) { }

                _replyToastEvents.trySend(context.getString(R.string.notifications_toast_reply_sent))
            } catch (e: Exception) {
                Log.e("Notifications", "Failed to send reply", e)
                _replyToastEvents.trySend(context.getString(R.string.notifications_toast_reply_failed))
                // Restore so the user can retry
                _replyingToNotification.value = notification
                _replyPendingSong.value = attachedSong
                _replyPendingFilm.value = attachedFilm
            }
            _isSendingReply.value = false
        }
    }

    fun sendGifReply(gifURL: String, slug: String = "", text: String = "") {
        val userId = authRepository.currentUserId ?: return
        val notification = _replyingToNotification.value ?: return
        val postId = notification.postId ?: return
        val sourceCommentId = notification.commentId ?: return
        val trimmed = text.trim()

        // Fire-and-forget so the optimistic dismissal is instant.
        if (slug.isNotEmpty()) {
            viewModelScope.launch { gifRepository.triggerShare(slug) }
        }

        _replyingToNotification.value = null
        _replyPendingSong.value = null
        _replyPendingFilm.value = null
        _replyPendingGif.value = null
        _isSendingReply.value = true

        viewModelScope.launch {
            try {
                val parentCommentId =
                    postRepository.getCommentParentId(postId, sourceCommentId) ?: sourceCommentId
                val newCommentId = postRepository.addComment(
                    postId = postId,
                    userId = userId,
                    text = trimmed,
                    parentCommentId = parentCommentId,
                    replyToUserId = notification.fromUser.id,
                    gifURL = gifURL,
                )
                engagementManager.incrementCommentCount(postId)

                try {
                    val post = postRepository.getCachedPost(postId)
                    val truncated = if (trimmed.isNotEmpty()) {
                        if (trimmed.length > 100) trimmed.take(100) + "…" else trimmed
                    } else "shared a GIF"
                    postRepository.createNotification(
                        type = "reply",
                        fromUserId = userId,
                        toUserId = notification.fromUser.id,
                        postId = postId,
                        postAlbumArtURL = post?.displayImageURL,
                        commentText = truncated,
                        commentId = newCommentId,
                        attachmentType = "gif",
                    )
                } catch (_: Exception) { }

                _replyToastEvents.trySend(context.getString(R.string.notifications_toast_reply_sent))
            } catch (e: Exception) {
                Log.e("Notifications", "Failed to send GIF reply", e)
                _replyToastEvents.trySend(context.getString(R.string.notifications_toast_reply_failed))
                _replyingToNotification.value = notification
            }
            _isSendingReply.value = false
        }
    }
}
