package fm.corus.android.data.repository

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import fm.corus.android.data.local.PreferencesDataStore
import fm.corus.android.data.model.CymbalUser
import fm.corus.android.data.model.SuggestedUserMatch
import fm.corus.android.data.remote.CloudFunctionsDataSource
import fm.corus.android.data.remote.FirebaseStorageDataSource
import fm.corus.android.data.remote.FirestoreDataSource
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
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
) {
    companion object {
        private const val PROFILE_TTL_MS = 5L * 60 * 1000      // 5 minutes — matches iOS
        private const val USERNAME_TTL_MS = 5L * 60 * 1000      // 5 minutes — matches iOS
        private const val SUGGESTED_MATCHES_TTL_MS = 4L * 60 * 60 * 1000 // 4 hours — matches iOS
    }

    // ── TTL Caches (matching iOS DatabaseService caching) ──

    private val profileCache = ConcurrentHashMap<String, CacheEntry<CymbalUser>>()
    private val usernameCache = ConcurrentHashMap<String, CacheEntry<String>>() // username → uid
    @Volatile private var suggestedMatchesCache: CacheEntry<List<SuggestedUserMatch>>? = null

    // Cached following set
    private val _followingIds = MutableStateFlow<Set<String>>(emptySet())
    val followingIds: StateFlow<Set<String>> = _followingIds.asStateFlow()

    // Cached blocked set
    private val _blockedIds = MutableStateFlow<Set<String>>(emptySet())
    val blockedIds: StateFlow<Set<String>> = _blockedIds.asStateFlow()

    // Cached muted set (two-layer: in-memory + persisted to DataStore)
    private val _mutedIds = MutableStateFlow<Set<String>>(emptySet())
    val mutedIds: StateFlow<Set<String>> = _mutedIds.asStateFlow()

    suspend fun prefetchFollowingSet(userId: String) {
        _followingIds.value = firestoreDataSource.fetchFollowingIds(userId)
    }

    suspend fun prefetchBlockedSet(userId: String) {
        _blockedIds.value = firestoreDataSource.fetchBlockedIds(userId)
    }

    fun isFollowing(userId: String): Boolean = _followingIds.value.contains(userId)

    // ── Profile (with TTL cache, matching iOS) ──

    suspend fun fetchUserProfile(uid: String): CymbalUser? {
        profileCache[uid]?.let { entry ->
            // Current user's profile never expires from cache (matching iOS) —
            // it's always needed for optimistic UI (comments, etc.)
            val isCurrentUser = uid == auth.currentUser?.uid
            if (isCurrentUser || entry.isValid(PROFILE_TTL_MS)) return entry.value
        }
        val user = firestoreDataSource.fetchUserProfile(uid) ?: return null
        profileCache[uid] = CacheEntry(user)
        return user
    }

    fun cacheUser(user: CymbalUser) {
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
        val timestamp = System.currentTimeMillis()
        firestoreDataSource.updateUserProfile(uid, mapOf("avatarURL" to "$url?v=$timestamp"))
        invalidateUserProfileCache(uid)
        return "$url?v=$timestamp"
    }

    suspend fun checkUsernameAvailable(username: String): Boolean {
        return firestoreDataSource.checkUsernameAvailable(username)
    }

    // ── Follow ──

    suspend fun followUser(userId: String, targetUserId: String) {
        firestoreDataSource.followUser(userId, targetUserId)
        _followingIds.value = _followingIds.value + targetUserId
        // Send follow notification (matches iOS)
        try {
            firestoreDataSource.createNotification(
                type = "follow",
                fromUserId = userId,
                toUserId = targetUserId,
            )
        } catch (_: Exception) { }
    }

    suspend fun unfollowUser(userId: String, targetUserId: String) {
        firestoreDataSource.unfollowUser(userId, targetUserId)
        _followingIds.value = _followingIds.value - targetUserId
    }

    suspend fun fetchFollowerIds(userId: String): Set<String> {
        return firestoreDataSource.fetchFollowerIds(userId)
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

    suspend fun getSuggestedUsers(userId: String): List<SuggestedUserMatch> {
        suggestedMatchesCache?.let { entry ->
            if (entry.isValid(SUGGESTED_MATCHES_TTL_MS)) return entry.value
        }
        val result = cloudFunctions.getSuggestedUsers(userId)
        suggestedMatchesCache = CacheEntry(result)
        preferencesDataStore.persistSuggestedMatches(result, userId)
        return result
    }

    // ── Popular ──

    suspend fun fetchPopularUsers(limit: Int = 10, excludeIds: Set<String> = emptySet()): List<CymbalUser> {
        return firestoreDataSource.fetchPopularUsers(limit, excludeIds)
    }

    // ── Search ──

    suspend fun searchUsers(query: String, limit: Int = 20): List<CymbalUser> {
        return firestoreDataSource.searchUsersByUsername(query, limit)
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

    suspend fun submitReport(reporterId: String, targetUserId: String?, postId: String?, reason: String, details: String) {
        firestoreDataSource.submitReport(reporterId, targetUserId, postId, reason, details)
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

    fun clearCaches() {
        _followingIds.value = emptySet()
        _blockedIds.value = emptySet()
        _mutedIds.value = emptySet()
        profileCache.clear()
        usernameCache.clear()
        suggestedMatchesCache = null
    }
}
