package fm.corus.android.data.remote

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldPath
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import fm.corus.android.data.model.*
import fm.corus.android.service.RemoteConfigService
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import java.util.Date
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FirestoreDataSource @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val remoteConfigService: RemoteConfigService,
    private val firebaseAuth: FirebaseAuth,
) {
    /**
     * Runs a Firestore operation and, if it fails due to a stale auth token,
     * forces an ID-token refresh and retries once. Only retries for the
     * narrow set of errors Firestore emits when the server rejected the
     * request *before* any write committed, which keeps non-idempotent
     * operations (e.g. `FieldValue.increment`) safe to re-run.
     */
    private suspend fun <T> withAuthRetry(operation: suspend () -> T): T {
        return try {
            operation()
        } catch (e: Exception) {
            val user = firebaseAuth.currentUser
            if (!isAuthTokenError(e) || user == null) throw e
            user.getIdToken(true).await()
            operation()
        }
    }

    private fun isAuthTokenError(error: Throwable): Boolean {
        if (error is FirebaseFirestoreException) {
            return error.code == FirebaseFirestoreException.Code.PERMISSION_DENIED
                || error.code == FirebaseFirestoreException.Code.UNAUTHENTICATED
        }
        if (error is FirebaseAuthException) {
            return error.errorCode == "ERROR_USER_TOKEN_EXPIRED"
                || error.errorCode == "ERROR_INVALID_USER_TOKEN"
        }
        return false
    }

    // ── Ban Check ──

    suspend fun checkIfUserIsBanned(uid: String): Boolean {
        return try {
            val doc = firestore.collection("banned_users").document(uid).get().await()
            doc.exists()
        } catch (e: Exception) {
            false // Fail open — don't block login on network errors
        }
    }

    // ── Users ──

    suspend fun fetchUserProfile(uid: String): CymbalUser? {
        val doc = firestore.collection("users_v2").document(uid).get().await()
        if (!doc.exists()) return null
        val data = doc.data ?: return null
        return CymbalUser.fromMap(uid, data)
    }

    suspend fun createUserProfile(uid: String, username: String, displayName: String, email: String) {
        val searchTokens = generateSearchTokens(displayName)
        val data = mapOf(
            "uid" to uid,
            "username" to username.lowercase(),
            "displayName" to displayName,
            "searchTokens" to searchTokens,
            "email" to email,
            "bio" to "",
            "avatarURL" to "",
            "avatarSkipped" to false,
            "website" to "",
            "isVerified" to false,
            "isBot" to false,
            "followerCount" to 0,
            "followingCount" to 0,
            "hashtagCount" to 0,
            "cymbalCount" to 0,
            "savesCount" to 0,
            "settings" to mapOf(
                "messaging" to mapOf(
                    "pushEnabled" to true,
                    "whoCanMessage" to "everyone",
                ),
                "notifications" to mapOf(
                    "likes" to true,
                    "commentsAndReplies" to true,
                    "newFollowers" to true,
                    "followRequests" to true,
                    "contactJoined" to true,
                ),
            ),
            "vinylColor" to "black",
            "frameColor" to "black",
            "createdAt" to FieldValue.serverTimestamp(),
        )
        firestore.collection("users_v2").document(uid).set(data).await()
    }

    /** Generate prefix search tokens for a display name (matches iOS searchTokens logic). */
    private fun generateSearchTokens(displayName: String): List<String> {
        val words = displayName.lowercase().split(Regex("\\s+")).filter { it.isNotEmpty() }
        val tokens = mutableSetOf<String>()
        for (word in words) {
            for (i in 1..word.length) {
                tokens.add(word.substring(0, i))
            }
        }
        return tokens.toList()
    }

    suspend fun updateUserProfile(uid: String, fields: Map<String, Any?>) {
        firestore.collection("users_v2").document(uid).update(fields).await()
    }

    suspend fun checkUsernameAvailable(username: String): Boolean {
        val query = firestore.collection("users_v2")
            .whereEqualTo("username", username.lowercase())
            .limit(1)
            .get()
            .await()
        return query.isEmpty
    }

    // ── Following ──

    suspend fun followUser(userId: String, targetUserId: String) {
        val batch = firestore.batch()
        batch.set(
            firestore.collection("users_v2").document(userId)
                .collection("following").document(targetUserId),
            mapOf("createdAt" to FieldValue.serverTimestamp())
        )
        batch.set(
            firestore.collection("users_v2").document(targetUserId)
                .collection("followers").document(userId),
            mapOf("createdAt" to FieldValue.serverTimestamp())
        )
        batch.update(
            firestore.collection("users_v2").document(userId),
            "followingCount", FieldValue.increment(1)
        )
        batch.update(
            firestore.collection("users_v2").document(targetUserId),
            "followerCount", FieldValue.increment(1)
        )
        batch.commit().await()
    }

    suspend fun unfollowUser(userId: String, targetUserId: String) {
        val batch = firestore.batch()
        batch.delete(
            firestore.collection("users_v2").document(userId)
                .collection("following").document(targetUserId)
        )
        batch.delete(
            firestore.collection("users_v2").document(targetUserId)
                .collection("followers").document(userId)
        )
        batch.update(
            firestore.collection("users_v2").document(userId),
            "followingCount", FieldValue.increment(-1)
        )
        batch.update(
            firestore.collection("users_v2").document(targetUserId),
            "followerCount", FieldValue.increment(-1)
        )
        batch.commit().await()
    }

    suspend fun isFollowing(userId: String, targetUserId: String): Boolean {
        val doc = firestore.collection("users_v2").document(userId)
            .collection("following").document(targetUserId)
            .get().await()
        return doc.exists()
    }

    suspend fun fetchFollowingIds(userId: String): Set<String> {
        val snapshot = firestore.collection("users_v2").document(userId)
            .collection("following").get().await()
        return snapshot.documents.map { it.id }.toSet()
    }

    suspend fun fetchFollowerIds(userId: String): Set<String> {
        val snapshot = firestore.collection("users_v2").document(userId)
            .collection("followers").get().await()
        return snapshot.documents.map { it.id }.toSet()
    }

    data class PaginatedIdsResult(
        val ids: List<String>,
        val lastDocument: DocumentSnapshot?,
    )

    suspend fun fetchFollowerIdsPaginated(
        userId: String,
        limit: Int,
        startAfter: DocumentSnapshot? = null,
    ): PaginatedIdsResult {
        var query = firestore.collection("users_v2").document(userId)
            .collection("followers")
            .limit(limit.toLong())
        if (startAfter != null) {
            query = query.startAfter(startAfter)
        }
        val snapshot = query.get().await()
        return PaginatedIdsResult(
            ids = snapshot.documents.map { it.id },
            lastDocument = snapshot.documents.lastOrNull(),
        )
    }

    suspend fun fetchFollowingIdsPaginated(
        userId: String,
        limit: Int,
        startAfter: DocumentSnapshot? = null,
    ): PaginatedIdsResult {
        var query = firestore.collection("users_v2").document(userId)
            .collection("following")
            .limit(limit.toLong())
        if (startAfter != null) {
            query = query.startAfter(startAfter)
        }
        val snapshot = query.get().await()
        return PaginatedIdsResult(
            ids = snapshot.documents.map { it.id },
            lastDocument = snapshot.documents.lastOrNull(),
        )
    }

    // ── Likes ──

    suspend fun likePost(userId: String, postId: String) = withAuthRetry {
        val batch = firestore.batch()
        batch.set(
            firestore.collection("posts").document(postId)
                .collection("likes").document(userId),
            mapOf("createdAt" to FieldValue.serverTimestamp())
        )
        batch.set(
            firestore.collection("users_v2").document(userId)
                .collection("liked").document(postId),
            mapOf("createdAt" to FieldValue.serverTimestamp())
        )
        batch.update(
            firestore.collection("posts").document(postId),
            "likeCount", FieldValue.increment(1)
        )
        batch.commit().await()
    }

    suspend fun unlikePost(userId: String, postId: String) = withAuthRetry {
        val batch = firestore.batch()
        batch.delete(
            firestore.collection("posts").document(postId)
                .collection("likes").document(userId)
        )
        batch.delete(
            firestore.collection("users_v2").document(userId)
                .collection("liked").document(postId)
        )
        batch.update(
            firestore.collection("posts").document(postId),
            "likeCount", FieldValue.increment(-1)
        )
        batch.commit().await()
    }

    suspend fun isPostLiked(userId: String, postId: String): Boolean {
        val doc = firestore.collection("posts").document(postId)
            .collection("likes").document(userId)
            .get().await()
        return doc.exists()
    }

    // ── Saves ──

    suspend fun savePost(userId: String, postId: String) {
        firestore.collection("users_v2").document(userId)
            .collection("saves").document(postId)
            .set(mapOf("createdAt" to FieldValue.serverTimestamp()))
            .await()
    }

    suspend fun unsavePost(userId: String, postId: String) {
        firestore.collection("users_v2").document(userId)
            .collection("saves").document(postId)
            .delete()
            .await()
    }

    suspend fun isPostSaved(userId: String, postId: String): Boolean {
        val doc = firestore.collection("users_v2").document(userId)
            .collection("saves").document(postId)
            .get().await()
        return doc.exists()
    }

    // ── Posts ──

    /**
     * Count unique users who have posted this track (by trackId, spotifyURI, or name+artist).
     * Returns 0 if nobody has posted it yet (= caller is first poster).
     */
    suspend fun fetchUniquePosterCountByTrack(track: CymbalTrack): Int {
        val allDocs = mutableMapOf<String, String>() // docId → userId

        // Query by trackId
        if (track.id.isNotBlank()) {
            val byId = firestore.collection("posts")
                .whereEqualTo("trackId", track.id)
                .get().await()
            byId.documents.forEach { doc ->
                val uid = doc.getString("userId")
                if (uid != null) allDocs[doc.id] = uid
            }
        }

        // Query by spotifyURI
        if (track.spotifyURI.isNotBlank()) {
            val byUri = firestore.collection("posts")
                .whereEqualTo("spotifyURI", track.spotifyURI)
                .get().await()
            byUri.documents.forEach { doc ->
                val uid = doc.getString("userId")
                if (uid != null) allDocs[doc.id] = uid
            }
        }

        // Query by trackName + artistName
        if (track.name.isNotBlank() && track.artistName.isNotBlank()) {
            val byName = firestore.collection("posts")
                .whereEqualTo("trackName", track.name)
                .whereEqualTo("artistName", track.artistName)
                .get().await()
            byName.documents.forEach { doc ->
                val uid = doc.getString("userId")
                if (uid != null) allDocs[doc.id] = uid
            }
        }

        val uniqueUserIds = allDocs.values.toSet()
        val botIds = fetchBotUserIds(uniqueUserIds)
        return uniqueUserIds.count { it !in botIds }
    }

    /**
     * Count unique users who have posted this movie.
     */
    suspend fun fetchUniquePosterCountByMovie(movieId: String): Int {
        val snapshot = firestore.collection("posts")
            .whereEqualTo("movieId", movieId)
            .get().await()
        val uniqueUserIds = snapshot.documents.mapNotNull { it.getString("userId") }.toSet()
        val botIds = fetchBotUserIds(uniqueUserIds)
        return uniqueUserIds.count { it !in botIds }
    }

    /** Given a set of user IDs, returns the subset that are bots. */
    private suspend fun fetchBotUserIds(userIds: Set<String>): Set<String> {
        if (userIds.isEmpty()) return emptySet()
        val botIds = mutableSetOf<String>()
        // Firestore 'in' queries limited to 30 items
        userIds.chunked(30).forEach { chunk ->
            val snap = try {
                firestore.collection("users_v2")
                    .whereIn(FieldPath.documentId(), chunk)
                    .whereEqualTo("isBot", true)
                    .get().await()
            } catch (_: Exception) { null }
            snap?.documents?.forEach { botIds.add(it.id) }
        }
        return botIds
    }

    suspend fun createPost(userId: String, data: Map<String, Any>): String {
        val docRef = firestore.collection("posts").document()
        val postData = data.toMutableMap()
        postData["id"] = docRef.id
        postData["userId"] = userId
        postData["createdAt"] = FieldValue.serverTimestamp()
        postData["likeCount"] = 0
        postData["commentCount"] = 0
        postData["repostCount"] = 0
        postData["voiceNoteURL"] = data["voiceNoteURL"] ?: ""
        docRef.set(postData).await()

        // Increment user's cymbal count
        firestore.collection("users_v2").document(userId)
            .update("cymbalCount", FieldValue.increment(1))
            .await()

        // If this is a repost (attribution toggle on), bump the original post's
        // repostCount so the original poster's engagement row updates. Matches
        // iOS DatabaseService.createPost.
        val originalPostId = data["repostedFromPostId"] as? String
        if (!originalPostId.isNullOrEmpty()) {
            try {
                firestore.collection("posts").document(originalPostId)
                    .update("repostCount", FieldValue.increment(1))
                    .await()
            } catch (_: Exception) { }
        }

        return docRef.id
    }

    suspend fun updatePostVoiceNoteURL(postId: String, url: String) {
        firestore.collection("posts").document(postId)
            .update("voiceNoteURL", url)
            .await()
    }

    suspend fun updateCaption(postId: String, caption: String) {
        firestore.collection("posts").document(postId)
            .update(mapOf("caption" to caption))
            .await()
    }

    suspend fun createNotification(
        type: String,
        fromUserId: String,
        toUserId: String,
        postId: String? = null,
        postAlbumArtURL: String? = null,
        commentText: String? = null,
        commentId: String? = null,
    ) {
        if (remoteConfigService.serverNotificationsEnabled) return
        if (fromUserId == toUserId) return
        val data = mutableMapOf<String, Any>(
            "type" to type,
            "fromUserId" to fromUserId,
            "toUserId" to toUserId,
            "isRead" to false,
            "createdAt" to FieldValue.serverTimestamp(),
        )
        if (postId != null) data["postId"] = postId
        if (postAlbumArtURL != null) data["postAlbumArtURL"] = postAlbumArtURL
        if (commentText != null) data["commentText"] = commentText
        if (commentId != null) data["commentId"] = commentId

        // Deterministic IDs for actions that can be toggled — matches iOS behavior.
        val deterministicId = when (type) {
            "follow" -> "follow_${fromUserId}_${toUserId}"
            "like" -> "like_${fromUserId}_${postId.orEmpty()}"
            "comment_like" -> "comment_like_${fromUserId}_${commentId.orEmpty()}"
            "save" -> "save_${fromUserId}_${postId.orEmpty()}"
            else -> null
        }

        val collection = firestore.collection("notifications")
        if (deterministicId != null) {
            val docRef = collection.document(deterministicId)
            try { docRef.delete().await() } catch (_: Exception) { }
            docRef.set(data).await()
        } else {
            collection.add(data).await()
        }
    }

    suspend fun deletePost(postId: String, userId: String) {
        firestore.collection("posts").document(postId).delete().await()
        firestore.collection("users_v2").document(userId)
            .update("cymbalCount", FieldValue.increment(-1))
            .await()
    }

    // ── Comments ──

    suspend fun addComment(postId: String, userId: String, text: String, parentCommentId: String? = null, replyToUserId: String? = null, gifURL: String? = null): String {
        val commentRef = firestore.collection("posts").document(postId)
            .collection("comments").document()
        val commentData = mutableMapOf<String, Any?>(
            "id" to commentRef.id,
            "userId" to userId,
            "text" to text,
            "createdAt" to FieldValue.serverTimestamp(),
            "likeCount" to 0,
            "replyCount" to 0,
        )
        parentCommentId?.let { commentData["parentCommentId"] = it }
        replyToUserId?.let { commentData["replyToUserId"] = it }
        gifURL?.let { commentData["gifURL"] = it }
        commentRef.set(commentData).await()

        firestore.collection("posts").document(postId)
            .update("commentCount", FieldValue.increment(1))
            .await()

        if (parentCommentId != null) {
            // Non-fatal: Firestore rules disallow updating another user's comment,
            // so incrementing replyCount on someone else's comment fails with
            // PERMISSION_DENIED. The reply itself is already written; swallow the
            // error to match iOS behaviour (see Cymbal-Remake DatabaseService.addComment).
            try {
                firestore.collection("posts").document(postId)
                    .collection("comments").document(parentCommentId)
                    .update("replyCount", FieldValue.increment(1))
                    .await()
            } catch (e: Exception) {
                android.util.Log.w("Comments", "Failed to increment replyCount on $parentCommentId", e)
            }
        }

        return commentRef.id
    }

    suspend fun editComment(postId: String, commentId: String, newText: String) {
        firestore.collection("posts").document(postId)
            .collection("comments").document(commentId)
            .update(
                mapOf(
                    "text" to newText,
                    "editedAt" to FieldValue.serverTimestamp(),
                )
            ).await()
    }

    suspend fun deleteComment(postId: String, commentId: String) {
        val commentRef = firestore.collection("posts").document(postId)
            .collection("comments").document(commentId)
        val commentDoc = commentRef.get().await()
        val parentId = commentDoc.getString("parentCommentId")

        commentRef.delete().await()

        // Decrement post comment count
        firestore.collection("posts").document(postId)
            .update("commentCount", FieldValue.increment(-1))
            .await()

        // Decrement parent reply count if it's a reply
        if (parentId != null) {
            try {
                firestore.collection("posts").document(postId)
                    .collection("comments").document(parentId)
                    .update("replyCount", FieldValue.increment(-1))
                    .await()
            } catch (_: Exception) { }
        }
    }

    // ── Blocked Users ──

    suspend fun fetchBlockedIds(userId: String): Set<String> {
        val snapshot = firestore.collection("users_v2").document(userId)
            .collection("blocked").get().await()
        return snapshot.documents.map { it.id }.toSet()
    }

    // ── Muted Users ──

    suspend fun fetchMutedUserIds(userId: String): List<String> {
        val snapshot = firestore.collection("users_v2").document(userId)
            .collection("muted").get().await()
        return snapshot.documents.map { it.id }
    }

    suspend fun unmuteUser(currentUserId: String, targetUserId: String) {
        firestore.collection("users_v2").document(currentUserId)
            .collection("muted").document(targetUserId)
            .delete().await()
    }

    // ── Hashtags ──

    @Suppress("UNCHECKED_CAST")
    suspend fun fetchTrendingHashtags(limit: Int = 10): List<CymbalHashtag> {
        val snapshot = firestore.collection("hashtags")
            .orderBy("cymbalCount", Query.Direction.DESCENDING)
            .limit(limit.toLong())
            .get().await()
        return snapshot.documents.mapNotNull { doc ->
            val data = doc.data ?: return@mapNotNull null
            CymbalHashtag(
                id = doc.id,
                name = data["name"] as? String ?: doc.id,
                cymbalCount = (data["cymbalCount"] as? Number)?.toInt() ?: 0,
                coverArtURLs = (data["coverArtURLs"] as? List<String>) ?: emptyList(),
            )
        }
    }

    // ── Per-post engagement listener (matching iOS PostEngagementStore) ──

    fun listenForPostUpdates(
        postId: String,
        onUpdate: (likeCount: Int, commentCount: Int, repostCount: Int) -> Unit,
    ): ListenerRegistration {
        return firestore.collection("posts").document(postId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) return@addSnapshotListener
                val data = snapshot?.data ?: return@addSnapshotListener
                val likeCount = (data["likeCount"] as? Number)?.toInt() ?: 0
                val commentCount = (data["commentCount"] as? Number)?.toInt() ?: 0
                val repostCount = (data["repostCount"] as? Number)?.toInt() ?: 0
                onUpdate(likeCount, commentCount, repostCount)
            }
    }

    // ── Notifications listener ──

    fun observeNotifications(userId: String, limit: Int = 15): Flow<List<CymbalNotification>> = callbackFlow {
        val registration: ListenerRegistration = firestore.collection("notifications")
            .whereEqualTo("toUserId", userId)
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .limit(limit.toLong())
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                val notifications = snapshot?.documents?.mapNotNull { doc ->
                    val data = doc.data ?: return@mapNotNull null
                    CymbalNotification.fromMap(doc.id, data)
                } ?: emptyList()
                trySend(notifications)
            }
        awaitClose { registration.remove() }
    }

    // ── FCM Token ──

    suspend fun updateFCMToken(uid: String, token: String) {
        firestore.collection("users_v2").document(uid)
            .update(mapOf("fcmToken" to token, "fcmTokenUpdatedAt" to FieldValue.serverTimestamp()))
            .await()
    }

    suspend fun removeFCMToken(uid: String) {
        firestore.collection("users_v2").document(uid)
            .update(mapOf("fcmToken" to FieldValue.delete()))
            .await()
    }

    // ── Trending Songs (from trending_cache/songs, matching iOS) ──

    @Suppress("UNCHECKED_CAST")
    suspend fun fetchTrendingSongs(limit: Int = 20): List<TrendingSong> {
        val doc = firestore.collection("trending_cache").document("songs").get().await()
        val items = doc.data?.get("items") as? List<Map<String, Any?>> ?: return emptyList()
        return items.take(limit).mapNotNull { item ->
            val trackId = item["trackId"] as? String ?: return@mapNotNull null
            TrendingSong(
                id = trackId,
                rank = (item["rank"] as? Number)?.toInt() ?: 0,
                track = CymbalTrack.fromMap(item),
                cymbalCount = (item["cymbalCount"] as? Number)?.toInt() ?: 0,
            )
        }
    }

    // ── Trending Movies (from trending_cache/movies, matching iOS) ──

    @Suppress("UNCHECKED_CAST")
    suspend fun fetchTrendingMovies(limit: Int = 20): List<TrendingMovie> {
        val doc = firestore.collection("trending_cache").document("movies").get().await()
        val items = doc.data?.get("items") as? List<Map<String, Any?>> ?: return emptyList()
        return items.take(limit).mapNotNull { item ->
            val movieId = item["movieId"] as? String ?: return@mapNotNull null
            TrendingMovie(
                id = movieId,
                rank = (item["rank"] as? Number)?.toInt() ?: 0,
                movieId = movieId,
                movieTitle = item["movieTitle"] as? String ?: "",
                directorName = item["directorName"] as? String ?: "",
                releaseYear = item["releaseYear"] as? String ?: "",
                posterURL = item["posterURL"] as? String,
                posterLargeURL = item["posterLargeURL"] as? String,
                tmdbWebURL = item["tmdbWebURL"] as? String ?: "",
                trailerURL = item["trailerURL"] as? String,
                movieOverview = item["movieOverview"] as? String ?: "",
                movieRating = (item["movieRating"] as? Number)?.toDouble() ?: 0.0,
                movieCast = (item["movieCast"] as? List<String>) ?: emptyList(),
                cymbalCount = (item["cymbalCount"] as? Number)?.toInt() ?: 0,
            )
        }
    }

    // ── User Search ──

    suspend fun searchUsersByUsername(query: String, limit: Int = 20): List<CymbalUser> {
        val lowered = query.lowercase()
        val snapshot = firestore.collection("users_v2")
            .whereGreaterThanOrEqualTo("username", lowered)
            .whereLessThan("username", lowered + "\uf8ff")
            .limit(limit.toLong())
            .get().await()
        return snapshot.documents.mapNotNull { doc ->
            val data = doc.data ?: return@mapNotNull null
            CymbalUser.fromMap(doc.id, data)
        }
    }

    /** Search users whose searchTokens array contains the given (already-lowercased) token. */
    suspend fun searchUsersByToken(token: String, limit: Int = 20): List<CymbalUser> {
        val snapshot = firestore.collection("users_v2")
            .whereArrayContains("searchTokens", token)
            .limit(limit.toLong())
            .get().await()
        return snapshot.documents.mapNotNull { doc ->
            val data = doc.data ?: return@mapNotNull null
            CymbalUser.fromMap(doc.id, data)
        }
    }

    // ── Popular Users ──

    suspend fun fetchUsersByIds(ids: List<String>): List<CymbalUser> {
        if (ids.isEmpty()) return emptyList()
        val users = mutableListOf<CymbalUser>()
        ids.chunked(30).forEach { chunk ->
            val snapshot = firestore.collection("users_v2")
                .whereIn(FieldPath.documentId(), chunk)
                .get().await()
            snapshot.documents.forEach { doc ->
                val data = doc.data ?: return@forEach
                users.add(CymbalUser.fromMap(doc.id, data))
            }
        }
        return users
    }

    suspend fun fetchPopularUsers(limit: Int = 10, excludeIds: Set<String> = emptySet()): List<CymbalUser> {
        // Match iOS fetchPopularUsersBlended: only orderBy in the query,
        // filter bots/inactive users client-side to avoid composite index requirement.
        val fetchCount = 40 + excludeIds.size
        val snapshot = firestore.collection("users_v2")
            .orderBy("followerCount", com.google.firebase.firestore.Query.Direction.DESCENDING)
            .limit(fetchCount.toLong())
            .get().await()
        val twoWeeksAgo = System.currentTimeMillis() - (14L * 24 * 60 * 60 * 1000)
        return snapshot.documents.mapNotNull { doc ->
            if (excludeIds.contains(doc.id)) return@mapNotNull null
            val data = doc.data ?: return@mapNotNull null
            val user = CymbalUser.fromMap(doc.id, data)
            // Client-side filtering matching iOS: no bots, active users only
            if (user.isBot) return@mapNotNull null
            if (user.cymbalCount <= 0) return@mapNotNull null
            if (user.followerCount < 5) return@mapNotNull null
            // If lastPostedAt exists, require it to be within 2 weeks
            val lastPosted = user.lastPostedAt
            if (lastPosted != null && lastPosted.time < twoWeeksAgo) return@mapNotNull null
            user
        }.take(limit)
    }

    // ── Comment Likes ──

    suspend fun likeComment(userId: String, postId: String, commentId: String) {
        val batch = firestore.batch()
        batch.set(
            firestore.collection("posts").document(postId)
                .collection("comments").document(commentId)
                .collection("likes").document(userId),
            mapOf("createdAt" to FieldValue.serverTimestamp())
        )
        batch.update(
            firestore.collection("posts").document(postId)
                .collection("comments").document(commentId),
            "likeCount", FieldValue.increment(1)
        )
        batch.commit().await()
    }

    suspend fun unlikeComment(userId: String, postId: String, commentId: String) {
        val batch = firestore.batch()
        batch.delete(
            firestore.collection("posts").document(postId)
                .collection("comments").document(commentId)
                .collection("likes").document(userId)
        )
        batch.update(
            firestore.collection("posts").document(postId)
                .collection("comments").document(commentId),
            "likeCount", FieldValue.increment(-1)
        )
        batch.commit().await()
    }

    suspend fun isCommentLiked(userId: String, postId: String, commentId: String): Boolean {
        val doc = firestore.collection("posts").document(postId)
            .collection("comments").document(commentId)
            .collection("likes").document(userId)
            .get().await()
        return doc.exists()
    }

    // ── Mutual connections (precomputed) ──

    @Suppress("UNCHECKED_CAST")
    suspend fun fetchPrecomputedMutualConnections(userId: String, limit: Int = 20): List<Pair<CymbalUser, List<String>>> {
        val snapshot = firestore.collection("users_v2").document(userId)
            .collection("mutual_connections")
            .orderBy("mutualCount", Query.Direction.DESCENDING)
            .limit(limit.toLong())
            .get().await()

        return snapshot.documents.mapNotNull { doc ->
            val data = doc.data ?: return@mapNotNull null
            val userDoc = firestore.collection("users_v2").document(doc.id).get().await()
            val userData = userDoc.data ?: return@mapNotNull null
            val user = CymbalUser.fromMap(doc.id, userData)
            val names = (data["mutualNames"] as? List<String>).orEmpty()
            user to names
        }
    }

    /**
     * Client-side graph traversal fallback when precomputed mutual_connections is empty.
     * For each user the caller follows, fetches who *they* follow, then finds candidates
     * followed by multiple friends. Matches iOS fetchFriendsOfFriends logic.
     */
    suspend fun fetchFriendsOfFriends(
        currentUserId: String,
        excludeIds: Set<String>,
        limit: Int = 20,
    ): List<Pair<CymbalUser, List<String>>> = coroutineScope {
        // Get the users we follow (cap at 50, most recent first)
        val followingSnapshot = firestore.collection("users_v2").document(currentUserId)
            .collection("following")
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .limit(51)
            .get().await()
        val followingIds = followingSnapshot.documents.map { it.id }.filter { it != currentUserId }
        if (followingIds.isEmpty()) return@coroutineScope emptyList()

        // Fetch followed users to get their usernames
        val followedUsers = fetchUsersByIds(followingIds)
        val usernameById = followedUsers.associate { it.id to it.username }

        // For each followed user, fetch who they follow in parallel
        val candidateMutuals = mutableMapOf<String, MutableList<String>>() // candidateId -> [mutual usernames]
        val results = followingIds.map { followedId ->
            async {
                try {
                    val theirFollowing = fetchFollowingIds(followedId)
                    followedId to theirFollowing
                } catch (_: Exception) {
                    followedId to emptySet()
                }
            }
        }.awaitAll()

        for ((followedId, theirFollowing) in results) {
            val username = usernameById[followedId] ?: followedId
            for (candidateId in theirFollowing) {
                if (candidateId in excludeIds) continue
                candidateMutuals.getOrPut(candidateId) { mutableListOf() }.add(username)
            }
        }

        if (candidateMutuals.isEmpty()) return@coroutineScope emptyList()

        // Fetch candidate user profiles
        val allCandidateIds = candidateMutuals.keys.toList()
        val users = fetchUsersByIds(allCandidateIds)
        val userById = users.associateBy { it.id }

        // Sort by mutual count desc, filter out bots
        candidateMutuals.entries
            .mapNotNull { (id, usernames) ->
                val user = userById[id] ?: return@mapNotNull null
                if (user.isBot) return@mapNotNull null
                user to usernames.toList()
            }
            .sortedByDescending { it.second.size }
            .take(limit)
    }

    // ── Username lookup ──

    suspend fun fetchUserByUsername(username: String): CymbalUser? {
        val snapshot = firestore.collection("users_v2")
            .whereEqualTo("username", username.lowercase())
            .limit(1)
            .get().await()
        val doc = snapshot.documents.firstOrNull() ?: return null
        val data = doc.data ?: return null
        return CymbalUser.fromMap(doc.id, data)
    }

    // ── Post likers ──

    data class LikersPage(
        val users: List<CymbalUser>,
        val lastTimestamp: Long?,
        val hasMore: Boolean,
    )

    suspend fun fetchPostLikers(postId: String, limit: Int = 20, lastTimestamp: Long? = null): LikersPage {
        var query = firestore.collection("posts").document(postId)
            .collection("likes")
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .limit(limit.toLong())
        if (lastTimestamp != null) {
            query = query.startAfter(Date(lastTimestamp))
        }
        val snapshot = query.get().await()
        val docs = snapshot.documents
        val userIds = docs.map { it.id }
        val users = userIds.mapNotNull { fetchUserProfile(it) }
        val nextTimestamp = docs.lastOrNull()?.getTimestamp("createdAt")?.toDate()?.time
        return LikersPage(
            users = users,
            lastTimestamp = nextTimestamp,
            hasMore = docs.size >= limit,
        )
    }

    // ── Feedback ──

    suspend fun submitFeedback(
        userId: String,
        type: String,
        subject: String,
        description: String,
        deviceInfo: Map<String, String>,
    ) {
        firestore.collection("feedback").add(
            mapOf(
                "userId" to userId,
                "type" to type,
                "subject" to subject,
                "description" to description,
                "deviceInfo" to deviceInfo,
                "platform" to "android",
                "createdAt" to FieldValue.serverTimestamp(),
            )
        ).await()
    }

    // ── Reports ──

    suspend fun submitReport(reporterId: String, targetUserId: String?, postId: String?, reason: String, details: String) {
        firestore.collection("reports").add(
            mapOf(
                "reporterId" to reporterId,
                "targetUserId" to targetUserId,
                "postId" to postId,
                "reason" to reason,
                "details" to details,
                "createdAt" to FieldValue.serverTimestamp(),
            )
        ).await()
    }

    // ── Contacts ──

    suspend fun storeSyncedContacts(userId: String, phoneNumbers: List<String>) {
        try {
            firestore.collection("users_v2").document(userId)
                .update(mapOf("syncedContacts" to phoneNumbers)).await()
        } catch (e: Exception) {
            // Firestore security rules may reject this write; don't crash.
            Log.w("FirestoreDS", "storeSyncedContacts failed (permission denied?)", e)
        }
    }

    @Suppress("UNCHECKED_CAST")
    suspend fun fetchSyncedContacts(userId: String): List<String> {
        val doc = firestore.collection("users_v2").document(userId).get().await()
        return (doc.get("syncedContacts") as? List<String>) ?: emptyList()
    }

    suspend fun fetchUsersByPhoneNumbers(phoneNumbers: List<String>, excludeIds: Set<String>): List<CymbalUser> {
        if (phoneNumbers.isEmpty()) return emptyList()
        val users = mutableListOf<CymbalUser>()
        // Firestore `in` queries max 30 items
        phoneNumbers.chunked(30).forEach { chunk ->
            val snapshot = firestore.collection("users_v2")
                .whereIn("phoneNumber", chunk)
                .get().await()
            for (doc in snapshot.documents) {
                if (doc.id in excludeIds) continue
                val data = doc.data ?: continue
                users.add(CymbalUser.fromMap(doc.id, data))
            }
        }
        return users
    }

    // ── Notification read status ──

    suspend fun markNotificationRead(notificationId: String) {
        firestore.collection("notifications").document(notificationId)
            .update("isRead", true)
            .await()
    }

    suspend fun markAllNotificationsRead(userId: String) {
        val snapshot = firestore.collection("notifications")
            .whereEqualTo("toUserId", userId)
            .whereEqualTo("isRead", false)
            .get().await()
        val batch = firestore.batch()
        for (doc in snapshot.documents) {
            batch.update(doc.reference, "isRead", true)
        }
        batch.commit().await()
    }

    /**
     * Stamp the user's `lastSeenNotificationsAt` timestamp. Matches iOS —
     * used on first appearance of the Activity tab to later compute which
     * notifications are "new" for dim-styling purposes.
     */
    suspend fun updateLastSeenNotificationsAt(userId: String) {
        firestore.collection("users_v2").document(userId)
            .update("lastSeenNotificationsAt", FieldValue.serverTimestamp())
            .await()
    }

    /**
     * Returns the previously persisted `lastSeenNotificationsAt` in ms, or
     * null if never set. Called before [updateLastSeenNotificationsAt] so the
     * ViewModel can compare incoming notification timestamps against the
     * *prior* "seen" cutoff to flag them as new.
     */
    suspend fun fetchLastSeenNotificationsAt(userId: String): Long? {
        val doc = firestore.collection("users_v2").document(userId).get().await()
        val ts = doc.get("lastSeenNotificationsAt") as? com.google.firebase.Timestamp
        return ts?.toDate()?.time
    }

    // ── Post Notification Subscriptions ──

    suspend fun subscribeToUserPosts(subscriberId: String, targetUserId: String) {
        val docId = "${subscriberId}_${targetUserId}"
        firestore.collection("postSubscriptions").document(docId).set(
            mapOf(
                "subscriberId" to subscriberId,
                "targetUserId" to targetUserId,
                "createdAt" to FieldValue.serverTimestamp(),
            )
        ).await()
    }

    suspend fun unsubscribeFromUserPosts(subscriberId: String, targetUserId: String) {
        val docId = "${subscriberId}_${targetUserId}"
        firestore.collection("postSubscriptions").document(docId).delete().await()
    }

    suspend fun isSubscribedToUserPosts(subscriberId: String, targetUserId: String): Boolean {
        val docId = "${subscriberId}_${targetUserId}"
        val doc = firestore.collection("postSubscriptions").document(docId).get().await()
        return doc.exists()
    }
}
