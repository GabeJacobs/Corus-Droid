package fm.corus.android.data.repository

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
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UserRepository @Inject constructor(
    private val firestoreDataSource: FirestoreDataSource,
    private val storageDataSource: FirebaseStorageDataSource,
    private val cloudFunctions: CloudFunctionsDataSource,
    private val preferencesDataStore: PreferencesDataStore,
) {
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

    // ── Profile ──

    suspend fun fetchUserProfile(uid: String): CymbalUser? {
        return firestoreDataSource.fetchUserProfile(uid)
    }

    suspend fun updateUserProfile(uid: String, fields: Map<String, Any?>) {
        firestoreDataSource.updateUserProfile(uid, fields)
    }

    suspend fun uploadAvatar(uid: String, imageData: ByteArray): String {
        val url = storageDataSource.uploadAvatar(uid, imageData)
        val timestamp = System.currentTimeMillis()
        firestoreDataSource.updateUserProfile(uid, mapOf("avatarURL" to "$url?v=$timestamp"))
        return "$url?v=$timestamp"
    }

    suspend fun checkUsernameAvailable(username: String): Boolean {
        return firestoreDataSource.checkUsernameAvailable(username)
    }

    // ── Follow ──

    suspend fun followUser(userId: String, targetUserId: String) {
        firestoreDataSource.followUser(userId, targetUserId)
        _followingIds.value = _followingIds.value + targetUserId
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
        val profiles = result.ids.mapNotNull { id ->
            try { fetchUserProfile(id) } catch (_: Exception) { null }
        }
        return PaginatedUsersResult(users = profiles, lastDocument = result.lastDocument)
    }

    suspend fun fetchFollowingPaginated(
        userId: String,
        limit: Int,
        startAfter: DocumentSnapshot? = null,
    ): PaginatedUsersResult {
        val result = firestoreDataSource.fetchFollowingIdsPaginated(userId, limit, startAfter)
        val profiles = result.ids.mapNotNull { id ->
            try { fetchUserProfile(id) } catch (_: Exception) { null }
        }
        return PaginatedUsersResult(users = profiles, lastDocument = result.lastDocument)
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
        val ids = firestoreDataSource.fetchMutedUserIds(userId)
        return ids.mapNotNull { id ->
            try { fetchUserProfile(id) } catch (_: Exception) { null }
        }
    }

    // ── Suggestions ──

    suspend fun getSuggestedUsers(userId: String): List<SuggestedUserMatch> {
        return cloudFunctions.getSuggestedUsers(userId)
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
        return firestoreDataSource.fetchUserByUsername(username)
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

    fun clearCaches() {
        _followingIds.value = emptySet()
        _blockedIds.value = emptySet()
        _mutedIds.value = emptySet()
    }
}
