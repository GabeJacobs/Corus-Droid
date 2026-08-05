package fm.corus.android.data.repository

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import fm.corus.android.data.local.PreferencesDataStore
import fm.corus.android.data.model.CymbalUser
import fm.corus.android.data.model.SuggestedUserMatch
import fm.corus.android.data.remote.CloudFunctionsDataSource
import fm.corus.android.data.remote.FirebaseStorageDataSource
import fm.corus.android.data.remote.FirestoreDataSource
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UserRepository @Inject constructor(
    private val firestoreDataSource: FirestoreDataSource,
    private val storageDataSource: FirebaseStorageDataSource,
    private val cloudFunctions: CloudFunctionsDataSource,
    private val preferencesDataStore: PreferencesDataStore,
    private val auth: FirebaseAuth,
    private val subscriptionRepository: SubscriptionRepository,
) {
    companion object {
        private const val PROFILE_TTL_MS = 5L * 60 * 1000      // 5 minutes — matches iOS
        private const val USERNAME_TTL_MS = 5L * 60 * 1000      // 5 minutes — matches iOS
        private const val SUGGESTED_MATCHES_TTL_MS = 4L * 60 * 60 * 1000 // 4 hours — matches iOS
        // Stale-while-revalidate windows for the taste-matches rail first page —
        // matches iOS DatabaseService (freshWindow 60s, maxAge 24h).
        private const val TASTE_MATCHES_FRESH_WINDOW_MS = 60L * 1000        // 60s
        private const val TASTE_MATCHES_MAX_AGE_MS = 24L * 60 * 60 * 1000   // 24 hours
    }

    // ── TTL Caches (matching iOS DatabaseService caching) ──

    private val profileCache = ConcurrentHashMap<String, CacheEntry<CymbalUser>>()
    private val usernameCache = ConcurrentHashMap<String, CacheEntry<String>>() // username → uid
    @Volatile private var suggestedMatchesCache: CacheEntry<List<SuggestedUserMatch>>? = null

    /** In-memory holder for the cached taste-matches FIRST page (userId-keyed). */
    private data class CachedTasteMatchesFirstPage(
        val userId: String,
        val matches: List<SuggestedUserMatch>,
        val nextCursor: String?,
        val hasMore: Boolean,
    )
    @Volatile private var tasteMatchesFirstPageCache: CacheEntry<CachedTasteMatchesFirstPage>? = null

    // Cached following set (two-layer: in-memory + persisted to DataStore)
    private val _followingIds = MutableStateFlow<Set<String>>(emptySet())
    val followingIds: StateFlow<Set<String>> = _followingIds.asStateFlow()

    // True once the following set has been seeded at least once (from persisted
    // cache or network). Lets the Trending inline "Follow" pill stay hidden
    // while membership is still unknown on a true cold start, instead of
    // flashing "Follow" for an author the viewer may already follow.
    private val _followingLoaded = MutableStateFlow(false)
    val followingLoaded: StateFlow<Boolean> = _followingLoaded.asStateFlow()

    /**
     * Fires whenever the local user unfollows someone. Downstream listeners
     * (e.g. NowPlayingManager) react by pruning that user's content from
     * in-memory state so it doesn't linger past the unfollow.
     */
    private val _unfollowEvents = MutableSharedFlow<String>(extraBufferCapacity = 16)
    val unfollowEvents: SharedFlow<String> = _unfollowEvents.asSharedFlow()

    // Cached blocked set
    private val _blockedIds = MutableStateFlow<Set<String>>(emptySet())
    val blockedIds: StateFlow<Set<String>> = _blockedIds.asStateFlow()

    // Cached muted set (two-layer: in-memory + persisted to DataStore)
    private val _mutedIds = MutableStateFlow<Set<String>>(emptySet())
    val mutedIds: StateFlow<Set<String>> = _mutedIds.asStateFlow()

    suspend fun prefetchFollowingSet(userId: String) {
        // Layer 1: Load from DataStore for instant correctness on first render
        // (so the Trending follow pill doesn't flash before the network lands).
        val persisted = preferencesDataStore.loadFollowingIdsAsync(userId)
        if (persisted != null) {
            _followingIds.value = persisted
            _followingLoaded.value = true
        }
        // Layer 2: Fetch fresh from Firestore (source of truth)
        val fresh = firestoreDataSource.fetchFollowingIds(userId)
        _followingIds.value = fresh
        _followingLoaded.value = true
        preferencesDataStore.persistFollowingIds(fresh, userId)
    }

    /**
     * Verifies the local user's forward-follow status for [candidateIds]
     * against the server and heals the cached [followingIds] set with the
     * result. The cache is seeded once at launch and otherwise only mutated by
     * local follow/unfollow, so a follow made on another device never reaches
     * it — leaving the Activity feed to show a stale "Follow back". Authoritative
     * over the queried subset: confirmed ids are added, queried ids the server
     * doesn't confirm are removed (so it also corrects a remote unfollow).
     */
    suspend fun reconcileFollowingStatus(userId: String, candidateIds: List<String>) {
        if (candidateIds.isEmpty()) return
        val confirmed = firestoreDataSource.checkFollowingStatusBatch(userId, candidateIds)
        val queried = candidateIds.toSet()
        _followingIds.value = (_followingIds.value - queried) + confirmed
        preferencesDataStore.persistFollowingIds(_followingIds.value, userId)
    }

    suspend fun prefetchBlockedSet(userId: String) {
        _blockedIds.value = firestoreDataSource.fetchBlockedIds(userId)
    }

    /**
     * Subscribe to the global banned-users denylist. Idempotent — safe to
     * call repeatedly. The listener stays active for the app lifetime so
     * banned-author content disappears within seconds of an admin ban.
     */
    fun startBannedUsersListener() {
        firestoreDataSource.startBannedUsersListener()
    }

    fun isUserBannedLocally(uid: String): Boolean =
        firestoreDataSource.isUserBannedLocally(uid)

    fun isFollowing(userId: String): Boolean = _followingIds.value.contains(userId)

    /**
     * Asks Firestore whether `askerId` follows `targetId`. Use only when the
     * answer is for *another* user's following set (e.g. comments-audience
     * `FOLLOWING` gate, where we need to know whether the post AUTHOR follows
     * the VIEWER). For the local user's own following set, use the cached
     * [isFollowing] above instead — it avoids a network round-trip.
     */
    suspend fun doesUserFollow(askerId: String, targetId: String): Boolean =
        firestoreDataSource.isFollowing(askerId, targetId)

    // ── Profile (with TTL cache, matching iOS) ──

    suspend fun fetchUserProfile(uid: String): CymbalUser? {
        profileCache[uid]?.let { entry ->
            // Current user's profile never expires from cache (matching iOS) —
            // it's always needed for optimistic UI (comments, etc.)
            val isCurrentUser = uid == auth.currentUser?.uid
            if (isCurrentUser || entry.isValid(PROFILE_TTL_MS)) {
                if (isCurrentUser) {
                    subscriptionRepository.setSavesCount(entry.value.savesCount)
                    subscriptionRepository.setFavoritesCount(entry.value.favoritesCount)
                    subscriptionRepository.setPlaylistTrialUsed(entry.value.playlistTrialUsed)
                }
                return entry.value
            }
        }
        val user = firestoreDataSource.fetchUserProfile(uid) ?: return null
        profileCache[uid] = CacheEntry(user)
        if (uid == auth.currentUser?.uid) {
            subscriptionRepository.setSavesCount(user.savesCount)
            subscriptionRepository.setFavoritesCount(user.favoritesCount)
            subscriptionRepository.setPlaylistTrialUsed(user.playlistTrialUsed)
        }
        return user
    }

    /**
     * Seeds the user-profile cache, but only when there isn't already a fresh
     * entry. Callers (feeds, search, notifications) often pass user snapshots
     * that are themselves cached/stale; clobbering a valid TTL entry with that
     * data would re-introduce stale customizations (frame, vinyl, avatar)
     * until the entry naturally expires.
     */
    fun cacheUser(user: CymbalUser) {
        val existing = profileCache[user.id]
        if (existing != null && existing.isValid(PROFILE_TTL_MS)) return
        profileCache[user.id] = CacheEntry(user)
    }

    fun invalidateUserProfileCache(uid: String) {
        profileCache.remove(uid)
    }

    suspend fun updateUserProfile(uid: String, fields: Map<String, Any?>) {
        firestoreDataSource.updateUserProfile(uid, fields)
        invalidateUserProfileCache(uid)
    }

    suspend fun uploadAvatar(uid: String, imageData: ByteArray): String {
        val url = storageDataSource.uploadAvatar(uid, imageData)
        // The Firebase download URL already carries a query string
        // (?alt=media&token=...), so the cache-buster must be appended with '&',
        // not '?'. A second '?' produces a malformed URL that corrupts the token
        // and breaks the image load on stricter clients (iOS).
        val separator = if (url.contains('?')) '&' else '?'
        val bustedUrl = "$url${separator}v=${System.currentTimeMillis()}"
        firestoreDataSource.updateUserProfile(uid, mapOf("avatarURL" to bustedUrl))
        invalidateUserProfileCache(uid)
        return bustedUrl
    }

    suspend fun checkUsernameAvailable(username: String): Boolean {
        return firestoreDataSource.checkUsernameAvailable(username)
    }

    // ── Follow ──

    // Server-driven: the `followUser` callable enforces the rolling 24h follow
    // cap and writes the same two Firestore docs. On a limit hit, callers
    // receive a `CloudFunctionsDataSource.FollowLimitReachedException` and
    // should show a top toast. The follow notification is now created
    // server-side by `onFollowCreatedNotify`, so we no longer need a separate
    // notification write here.
    suspend fun followUser(userId: String, targetUserId: String) {
        cloudFunctions.followUser(targetUserId)
        _followingIds.value = _followingIds.value + targetUserId
        preferencesDataStore.persistFollowingIds(_followingIds.value, userId)
        // Following set changed → mutual counts for any profile may shift.
        invalidateMutualCounts()
        // The server bumps targetUser.followerCount and userId.followingCount.
        // Drop both cache entries so the next profile fetch reflects the new
        // counts instead of serving a stale entry (target: 5-min TTL; self:
        // never expires) — otherwise refreshing the profile keeps showing the
        // pre-follow count, making the follow look like it never processed.
        invalidateUserProfileCache(targetUserId)
        invalidateUserProfileCache(userId)
    }

    suspend fun unfollowUser(userId: String, targetUserId: String) {
        cloudFunctions.unfollowUser(targetUserId)
        _followingIds.value = _followingIds.value - targetUserId
        preferencesDataStore.persistFollowingIds(_followingIds.value, userId)
        _unfollowEvents.tryEmit(targetUserId)
        // Following set changed → mutual counts for any profile may shift.
        invalidateMutualCounts()
        invalidateUserProfileCache(targetUserId)
        invalidateUserProfileCache(userId)
    }

    suspend fun fetchFollowerIds(userId: String): Set<String> {
        return firestoreDataSource.fetchFollowerIds(userId)
    }

    /**
     * Returns which of [candidateIds] follow [userId] (the reverse follow
     * direction), batched server-side. Used to decide "Follow back" vs "Follow"
     * for contact_joined notifications, where the relationship isn't implied by
     * the notification type.
     */
    suspend fun checkFollowerStatusBatch(userId: String, candidateIds: List<String>): Set<String> {
        return firestoreDataSource.checkFollowerStatusBatch(userId, candidateIds)
    }

    /** Returns which of [candidateIds] are followed by [userId], batched server-side. */
    suspend fun checkFollowingStatusBatch(userId: String, candidateIds: List<String>): Set<String> {
        return firestoreDataSource.checkFollowingStatusBatch(userId, candidateIds)
    }

    data class PaginatedUsersResult(
        val users: List<CymbalUser>,
        val lastDocument: DocumentSnapshot?,
    )

    suspend fun fetchFollowersPaginated(
        userId: String,
        limit: Int,
        startAfter: DocumentSnapshot? = null,
    ): PaginatedUsersResult {
        val result = firestoreDataSource.fetchFollowerIdsPaginated(userId, limit, startAfter)
        val profiles = fetchUsersByIdsBatched(result.ids)
        return PaginatedUsersResult(users = profiles, lastDocument = result.lastDocument)
    }

    suspend fun fetchFollowingPaginated(
        userId: String,
        limit: Int,
        startAfter: DocumentSnapshot? = null,
    ): PaginatedUsersResult {
        val result = firestoreDataSource.fetchFollowingIdsPaginated(userId, limit, startAfter)
        val profiles = fetchUsersByIdsBatched(result.ids)
        return PaginatedUsersResult(users = profiles, lastDocument = result.lastDocument)
    }

    /**
     * Instagram-style "Mutual" tab: accounts the viewer follows who also follow
     * [profileId] (viewer.following ∩ profile.followers). Server-side
     * intersection via the `getMutualFollowers` callable, which returns
     * ready-to-render users + an opaque string cursor (no id→profile hydration
     * needed). [cursor] paginates; `mutualCount` is only set on the first page.
     */
    suspend fun fetchMutualFollowers(
        profileId: String,
        cursor: String? = null,
        limit: Int,
    ): CloudFunctionsDataSource.MutualFollowersPage {
        return cloudFunctions.fetchMutualFollowers(profileId, cursor, limit)
    }

    data class MutualCount(val count: Int, val capped: Boolean)

    private data class CachedMutualCount(val value: MutualCount, val viewerId: String, val atMs: Long)
    // Per-profile mutual-COUNT cache (viewer-specific). Cleared on follow/unfollow
    // since the viewer's following set drives the count.
    private val mutualCountCache = mutableMapOf<String, CachedMutualCount>()
    private val mutualCountTtlMs = 5 * 60 * 1000L

    fun invalidateMutualCounts() {
        mutualCountCache.clear()
    }

    /**
     * Cheap mutual-follower COUNT for [profileId]: intersect the viewer's
     * (cached) following set with the profile's followers via
     * [checkFollowerStatusBatch] — reads ≈ the overlap, not the graph. Used to
     * render the Mutual tab label/visibility instantly without the paginated
     * `getMutualFollowers` callable (only hit when the tab is opened). Cached per
     * profile with a short TTL; warmed by the profile view. Returns (0,false) on
     * your own profile / signed out.
     */
    suspend fun mutualFollowerCount(
        viewerId: String,
        profileId: String,
        forceRefresh: Boolean = false,
    ): MutualCount {
        if (viewerId.isEmpty() || viewerId == profileId) return MutualCount(0, false)
        val now = System.currentTimeMillis()
        if (!forceRefresh) {
            val cached = mutualCountCache[profileId]
            if (cached != null && cached.viewerId == viewerId && now - cached.atMs < mutualCountTtlMs) {
                return cached.value
            }
        }

        // Reuse the in-memory following set (free); fall back to a one-time read.
        var ids = _followingIds.value
        if (ids.isEmpty()) {
            prefetchFollowingSet(viewerId)
            ids = _followingIds.value
        }
        if (ids.isEmpty()) {
            val zero = MutualCount(0, false)
            mutualCountCache[profileId] = CachedMutualCount(zero, viewerId, now)
            return zero
        }

        val mutuals = checkFollowerStatusBatch(profileId, ids.toList())
        val n = mutuals.size
        val cap = 1000
        val result = MutualCount(minOf(n, cap), n > cap)
        mutualCountCache[profileId] = CachedMutualCount(result, viewerId, now)
        return result
    }

    /**
     * Batch-fetch user profiles: checks the in-memory TTL cache first, then
     * fetches any cache misses via a single batched Firestore query (chunked
     * by 30, matching iOS's `fetchUsers(byIds:)`). Results are returned in
     * the same order as [ids], with unknown IDs omitted.
     */
    suspend fun fetchUsersByIdsBatched(ids: List<String>): List<CymbalUser> {
        if (ids.isEmpty()) return emptyList()

        val cached = mutableMapOf<String, CymbalUser>()
        val missIds = mutableListOf<String>()
        val currentUid = auth.currentUser?.uid
        for (id in ids) {
            val entry = profileCache[id]
            if (entry != null && (id == currentUid || entry.isValid(PROFILE_TTL_MS))) {
                cached[id] = entry.value
            } else {
                missIds.add(id)
            }
        }

        if (missIds.isNotEmpty()) {
            val fetched = firestoreDataSource.fetchUsersByIds(missIds)
            for (user in fetched) {
                profileCache[user.id] = CacheEntry(user)
                cached[user.id] = user
            }
        }

        return ids.mapNotNull { cached[it] }
    }

    // ── Block ──

    suspend fun blockUser(userId: String, targetUserId: String) {
        cloudFunctions.blockUser(userId, targetUserId)
        _blockedIds.value = _blockedIds.value + targetUserId
    }

    suspend fun unblockUser(userId: String, targetUserId: String) {
        cloudFunctions.unblockUser(userId, targetUserId)
        _blockedIds.value = _blockedIds.value - targetUserId
    }

    // ── Mute (two-layer cache: in-memory + DataStore, matching iOS) ──

    suspend fun prefetchMutedSet(userId: String) {
        // Layer 1: Load from DataStore for instant offline access
        val persisted = preferencesDataStore.loadMutedIdsAsync(userId)
        if (persisted != null) {
            _mutedIds.value = persisted
        }
        // Layer 2: Fetch fresh from Firestore (source of truth)
        val fresh = firestoreDataSource.fetchMutedUserIds(userId).toSet()
        _mutedIds.value = fresh
        preferencesDataStore.persistMutedIds(fresh, userId)
    }

    fun isUserMuted(userId: String): Boolean = _mutedIds.value.contains(userId)

    suspend fun muteUser(currentUserId: String, targetUserId: String) {
        cloudFunctions.muteUser(targetUserId)
        _mutedIds.value = _mutedIds.value + targetUserId
        preferencesDataStore.persistMutedIds(_mutedIds.value, currentUserId)
    }

    suspend fun unmuteUser(currentUserId: String, targetUserId: String) {
        cloudFunctions.unmuteUser(targetUserId)
        _mutedIds.value = _mutedIds.value - targetUserId
        preferencesDataStore.persistMutedIds(_mutedIds.value, currentUserId)
    }

    suspend fun fetchMutedUsers(userId: String): List<CymbalUser> {
        val ids = _mutedIds.value.toList()
        return fetchUsersByIdsBatched(ids)
    }

    // ── Suggestions (with 4-hour cache + DataStore persistence, matching iOS) ──

    /**
     * Load suggested matches from DataStore into memory on app start.
     * Call during auth init alongside prefetchFollowingSet etc.
     */
    suspend fun prefetchSuggestedMatches(userId: String) {
        if (suggestedMatchesCache != null) return
        val persisted = preferencesDataStore.loadSuggestedMatchesAsync(userId) ?: return
        val (matches, fetchedAt) = persisted
        suggestedMatchesCache = CacheEntry(matches, fetchedAt)
    }

    /**
     * Last-known suggestions from the in-memory cache or DataStore, with NO
     * network call and NO staleness gate. Used for stale-while-revalidate so the
     * Search taste rail paints instantly on a fresh launch instead of shimmering
     * through the network fetch (the async [prefetchSuggestedMatches] often loses
     * the race to the user opening Search, and >4h-old data is otherwise hidden).
     */
    suspend fun peekPersistedSuggestions(userId: String): List<SuggestedUserMatch>? {
        suggestedMatchesCache?.let { return it.value }
        val persisted = preferencesDataStore.loadSuggestedMatchesAsync(userId) ?: return null
        val (matches, fetchedAt) = persisted
        suggestedMatchesCache = CacheEntry(matches, fetchedAt)
        return matches
    }

    suspend fun getSuggestedUsers(userId: String, forceRefresh: Boolean = false): List<SuggestedUserMatch> {
        if (!forceRefresh) {
            suggestedMatchesCache?.let { entry ->
                if (entry.isValid(SUGGESTED_MATCHES_TTL_MS)) return entry.value
            }
        }
        // 15 (not 50): the inline rail shows a handful; the full list is the
        // paginated See-All page. 50 made the backend pre-enrich 50 users (a
        // per-user post-scan each) before returning a single card.
        val result = cloudFunctions.getSuggestedUsers(userId, limit = 15)
        suggestedMatchesCache = CacheEntry(result)
        preferencesDataStore.persistSuggestedMatches(result, userId)
        return result
    }

    /** One page of the live, cursor-paginated taste-matches list. No repo cache
     *  on the callable itself: the backend snapshots the ranking per session so
     *  pages 2..N are cheap. The rail separately caches the FIRST page (below)
     *  for stale-while-revalidate instant paint. */
    suspend fun getTasteMatchesPage(
        cursor: String? = null,
        limit: Int = 15,
    ): fm.corus.android.data.model.TasteMatchesPage {
        return cloudFunctions.getTasteMatchesPage(limit = limit, cursor = cursor)
    }

    // ── Taste Matches Rail first-page cache (stale-while-revalidate, matches iOS) ──
    //
    // Caches ONLY the first page (cursor == null) of getTasteMatchesPage — its
    // matches + nextCursor + hasMore + a fetchedAt — so the rail paints instantly
    // on open instead of shimmering, then revalidates in the background. Two
    // windows: FRESH_WINDOW (skip the revalidation on rapid re-opens) and MAX_AGE
    // (too old to paint → fall back to a cold skeleton + fetch).

    /** The cached first page, ready for instant paint. */
    data class CachedTasteMatchesPage(
        val matches: List<SuggestedUserMatch>,
        val nextCursor: String?,
        val hasMore: Boolean,
        val fetchedAt: Long,
    )

    /** Loads the in-memory holder, hydrating it from DataStore on a cold start.
     *  Returns null when there's no cache, it's for a different user, or it's
     *  empty. TTL/max-age gating is the caller's job. */
    private suspend fun loadTasteMatchesFirstPageCache(userId: String): CacheEntry<CachedTasteMatchesFirstPage>? {
        tasteMatchesFirstPageCache?.let { if (it.value.userId == userId) return it }
        val persisted = preferencesDataStore.loadTasteMatchesPageAsync(userId) ?: return null
        if (persisted.matches.isEmpty()) return null
        val entry = CacheEntry(
            CachedTasteMatchesFirstPage(userId, persisted.matches, persisted.nextCursor, persisted.hasMore),
            persisted.fetchedAt,
        )
        tasteMatchesFirstPageCache = entry
        return entry
    }

    /**
     * The cached first page to paint immediately, or null when there's nothing
     * usable (no cache, signed out, wrong user, empty, or older than
     * [TASTE_MATCHES_MAX_AGE_MS]). The user is resolved from the signed-in auth
     * uid, matching iOS `getCachedTasteMatchesFirstPage`.
     */
    suspend fun getCachedTasteMatchesFirstPage(): CachedTasteMatchesPage? {
        val uid = auth.currentUser?.uid ?: return null
        val entry = loadTasteMatchesFirstPageCache(uid) ?: return null
        if (entry.value.matches.isEmpty()) return null
        if (!entry.isValid(TASTE_MATCHES_MAX_AGE_MS)) return null
        return CachedTasteMatchesPage(
            matches = entry.value.matches,
            nextCursor = entry.value.nextCursor,
            hasMore = entry.value.hasMore,
            fetchedAt = entry.fetchedAt,
        )
    }

    /** True when the cache is newer than [TASTE_MATCHES_FRESH_WINDOW_MS], so the
     *  rail can skip the background revalidation. Mirrors iOS `isTasteMatchesCacheFresh`. */
    suspend fun isTasteMatchesCacheFresh(): Boolean {
        val uid = auth.currentUser?.uid ?: return false
        val entry = loadTasteMatchesFirstPageCache(uid) ?: return false
        return entry.isValid(TASTE_MATCHES_FRESH_WINDOW_MS)
    }

    /** Persists the first page in memory + DataStore (stamped now). No-op when
     *  signed out. */
    suspend fun cacheTasteMatchesFirstPage(page: fm.corus.android.data.model.TasteMatchesPage) {
        val uid = auth.currentUser?.uid ?: return
        tasteMatchesFirstPageCache = CacheEntry(
            CachedTasteMatchesFirstPage(uid, page.matches, page.nextCursor, page.hasMore),
        )
        preferencesDataStore.persistTasteMatchesPage(page.matches, page.nextCursor, page.hasMore, uid)
    }

    /** Drops the first-page cache (memory + DataStore) so a taste change forces a
     *  fresh fetch on next open. Mirrors iOS `invalidateTasteMatchesCache`. */
    suspend fun invalidateTasteMatchesCache() {
        tasteMatchesFirstPageCache = null
        val uid = auth.currentUser?.uid ?: return
        preferencesDataStore.clearTasteMatchesPage(uid)
    }

    /**
     * A taste change (the viewer creating/deleting their own post) shifts BOTH
     * the suggested-matches rail and the taste-matches rail — they derive from the
     * same taste signal. Drop both first-page caches (memory + persisted) so the
     * next open fetches fresh instead of serving a stale list across relaunches.
     * Mirrors iOS, where `invalidateSuggestedMatchesCache()` also calls
     * `invalidateTasteMatchesCache()`.
     */
    suspend fun invalidateTasteAndSuggestedMatchesCache() {
        suggestedMatchesCache = null
        val uid = auth.currentUser?.uid
        if (uid != null) preferencesDataStore.clearSuggestedMatches(uid)
        invalidateTasteMatchesCache()
    }

    // ── Popular ──

    suspend fun fetchPopularUsers(limit: Int = 10, excludeIds: Set<String> = emptySet()): List<CymbalUser> {
        return firestoreDataSource.fetchPopularUsers(limit, excludeIds)
    }

    suspend fun fetchNewUsers(limit: Int = 10, excludeIds: Set<String> = emptySet()): List<CymbalUser> {
        // Prefer the server-filtered getNewUsers callable (reliably hides shadow +
        // hard banned users). Fall back to the legacy direct read only if the
        // callable fails, so the rail still renders on a network hiccup.
        return try {
            cloudFunctions.getNewUsers(limit, excludeIds).first
        } catch (e: Exception) {
            firestoreDataSource.fetchNewUsers(limit, excludeIds)
        }
    }

    suspend fun fetchPopularUsersPaginated(
        limit: Int = 20,
        excludeIds: Set<String> = emptySet(),
        afterDocId: String? = null,
    ): List<CymbalUser> = firestoreDataSource.fetchPopularUsersPaginated(limit, excludeIds, afterDocId)

    suspend fun fetchNewUsersPaginated(
        limit: Int = 20,
        excludeIds: Set<String> = emptySet(),
        afterDocId: String? = null,
    ): List<CymbalUser> = try {
        cloudFunctions.getNewUsers(limit, excludeIds, afterDocId).first
    } catch (e: Exception) {
        firestoreDataSource.fetchNewUsersPaginated(limit, excludeIds, afterDocId)
    }

    // ── Corus Club Members ──

    suspend fun fetchClubMembers(limit: Int = 6, excludeIds: Set<String> = emptySet()): List<CymbalUser> {
        return firestoreDataSource.fetchClubMembers(limit, excludeIds)
    }

    // ── Search ──

    /**
     * Blended user search matching iOS DatabaseService.searchUsers:
     *   1. Username prefix match (main query).
     *   2. Per-word searchTokens arrayContains lookups (up to 3 unique words), so
     *      users whose display name contains the query also surface.
     * Results are deduped and sorted: followed > exact username > prefix > follower
     * count desc > alphabetical.
     *
     * Followed users are surfaced by the same username/token queries as everyone
     * else — `searchTokens` is kept complete server-side for every user by the
     * `regenerateSearchTokensOnUserWrite` trigger, so a followed user always
     * matches by name or handle. [includeFollowed] therefore only affects
     * *ranking* (float people you follow to the top); it reads the already-loaded
     * in-memory [_followingIds] set and never hits the network. We used to also
     * fetch every followed user's full profile here as a safety net, but for
     * users who follow thousands of accounts that bulk fetch dominated search
     * latency, and with complete searchTokens it's redundant.
     */
    suspend fun searchUsers(
        query: String,
        limit: Int = 20,
        includeFollowed: Boolean = false,
    ): List<CymbalUser> = coroutineScope {
        val lowered = query.lowercase()
        if (lowered.isEmpty()) return@coroutineScope emptyList()
        val queryLimit = limit * 3
        val queryWords = lowered.split(Regex("\\s+")).filter { it.isNotEmpty() }
        val usernamePrefix = lowered.replace(" ", "")
        val followedIds = if (includeFollowed) _followingIds.value else emptySet()

        val mainQuery = async {
            runCatching { firestoreDataSource.searchUsersByUsername(usernamePrefix, queryLimit) }
                .getOrDefault(emptyList())
        }
        val nameQueries = queryWords.distinct().take(3).map { token ->
            async {
                runCatching { firestoreDataSource.searchUsersByToken(token, queryLimit) }
                    .getOrDefault(emptyList())
            }
        }

        val mainResults = mainQuery.await()
        val allNameResults = nameQueries.awaitAll().flatten()

        val seenIds = mutableSetOf<String>()
        val users = mutableListOf<CymbalUser>()
        for (user in mainResults) {
            if (seenIds.add(user.id)) users.add(user)
        }
        for (user in allNameResults) {
            if (queryWords.size > 1) {
                val nameWords = user.displayName.lowercase()
                    .split(Regex("\\s+")).filter { it.isNotEmpty() }
                val allMatch = queryWords.all { q -> nameWords.any { it.startsWith(q) } }
                if (!allMatch && !user.username.startsWith(usernamePrefix)) continue
            }
            if (seenIds.add(user.id)) users.add(user)
        }

        users.sortedWith(
            compareByDescending<CymbalUser> { followedIds.contains(it.id) }
                .thenByDescending { it.username == usernamePrefix }
                .thenByDescending { it.username.startsWith(usernamePrefix) }
                .thenByDescending { it.followerCount }
                .thenBy { it.username },
        ).take(limit)
    }

    suspend fun fetchUserByUsername(username: String): CymbalUser? {
        val lowerName = username.lowercase()
        // Two-level cache: username → uid, then uid → profile (matching iOS)
        usernameCache[lowerName]?.let { entry ->
            if (entry.isValid(USERNAME_TTL_MS)) {
                return fetchUserProfile(entry.value)
            }
        }
        val user = firestoreDataSource.fetchUserByUsername(lowerName) ?: return null
        usernameCache[lowerName] = CacheEntry(user.id)
        profileCache[user.id] = CacheEntry(user)
        return user
    }

    // ── Feedback / Report ──

    suspend fun submitFeedback(
        userId: String,
        type: String,
        subject: String,
        description: String,
        deviceInfo: Map<String, String>,
    ) {
        firestoreDataSource.submitFeedback(userId, type, subject, description, deviceInfo)
    }

    suspend fun submitReport(
        reporterId: String,
        targetUserId: String? = null,
        postId: String? = null,
        reason: String,
        details: String,
        contentType: String? = null,
        contentId: String? = null,
        commentPostId: String? = null,
        contentAuthorId: String? = null,
        threadId: String? = null,
    ) {
        firestoreDataSource.submitReport(
            reporterId = reporterId,
            targetUserId = targetUserId,
            postId = postId,
            reason = reason,
            details = details,
            contentType = contentType,
            contentId = contentId,
            commentPostId = commentPostId,
            contentAuthorId = contentAuthorId,
            threadId = threadId,
        )
    }

    suspend fun subscribeToUserPosts(subscriberId: String, targetUserId: String) {
        firestoreDataSource.subscribeToUserPosts(subscriberId, targetUserId)
    }

    suspend fun unsubscribeFromUserPosts(subscriberId: String, targetUserId: String) {
        firestoreDataSource.unsubscribeFromUserPosts(subscriberId, targetUserId)
    }

    suspend fun isSubscribedToUserPosts(subscriberId: String, targetUserId: String): Boolean {
        return firestoreDataSource.isSubscribedToUserPosts(subscriberId, targetUserId)
    }

    // Favorite/unfavorite route through the callable so the server enforces the
    // favorite-people cap and maintains `favoritesCount`. Returns the new count
    // and syncs it into SubscriptionRepository for the local pre-check. On cap
    // hit, `favoritePerson` throws CloudFunctionsDataSource.FavoriteCapReachedException.
    suspend fun addFavorite(userId: String, targetId: String): Int {
        val result = cloudFunctions.favoritePerson(targetId)
        subscriptionRepository.setFavoritesCount(result.favoritesCount)
        return result.favoritesCount
    }

    suspend fun removeFavorite(userId: String, targetId: String): Int {
        val count = cloudFunctions.unfavoritePerson(targetId)
        subscriptionRepository.setFavoritesCount(count)
        return count
    }

    suspend fun isFavorite(userId: String, targetId: String): Boolean {
        return firestoreDataSource.isFavorite(userId, targetId)
    }

    fun clearCaches() {
        _followingIds.value = emptySet()
        _followingLoaded.value = false
        _blockedIds.value = emptySet()
        _mutedIds.value = emptySet()
        profileCache.clear()
        usernameCache.clear()
        suggestedMatchesCache = null
        tasteMatchesFirstPageCache = null
    }
}
