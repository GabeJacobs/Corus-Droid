package fm.corus.android.data.remote

import android.util.Log
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldPath
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import fm.corus.android.data.model.*
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import java.util.Date
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FirestoreDataSource @Inject constructor(
    private val firestore: FirebaseFirestore,
) {
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
        val data = mapOf(
            "username" to username.lowercase(),
            "displayName" to displayName,
            "email" to email,
            "bio" to "",
            "avatarURL" to "",
            "isVerified" to false,
            "isClubMember" to false,
            "isBot" to false,
            "followerCount" to 0,
            "followingCount" to 0,
            "hashtagCount" to 0,
            "cymbalCount" to 0,
            "savesCount" to 0,
            "vinylColor" to "black",
            "frameColor" to "black",
            "createdAt" to FieldValue.serverTimestamp(),
        )
        firestore.collection("users_v2").document(uid).set(data).await()
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
            mapOf("timestamp" to FieldValue.serverTimestamp())
        )
        batch.set(
            firestore.collection("users_v2").document(targetUserId)
                .collection("followers").document(userId),
            mapOf("timestamp" to FieldValue.serverTimestamp())
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

    suspend fun likePost(userId: String, postId: String) {
        val batch = firestore.batch()
        batch.set(
            firestore.collection("posts").document(postId)
                .collection("likes").document(userId),
            mapOf("timestamp" to FieldValue.serverTimestamp())
        )
        batch.set(
            firestore.collection("users_v2").document(userId)
                .collection("liked").document(postId),
            mapOf("timestamp" to FieldValue.serverTimestamp())
        )
        batch.update(
            firestore.collection("posts").document(postId),
            "likeCount", FieldValue.increment(1)
        )
        batch.commit().await()
    }

    suspend fun unlikePost(userId: String, postId: String) {
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
            .set(mapOf("timestamp" to FieldValue.serverTimestamp()))
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

    suspend fun createPost(userId: String, data: Map<String, Any?>): String {
        val docRef = firestore.collection("posts").document()
        val postData = data.toMutableMap()
        postData["id"] = docRef.id
        postData["userId"] = userId
        postData["timestamp"] = FieldValue.serverTimestamp()
        postData["likeCount"] = 0
        postData["commentCount"] = 0
        postData["repostCount"] = 0
        docRef.set(postData).await()

        // Increment user's cymbal count
        firestore.collection("users_v2").document(userId)
            .update("cymbalCount", FieldValue.increment(1))
            .await()

        return docRef.id
    }

    suspend fun createRepost(
        currentUserId: String,
        originalPost: CymbalPost,
    ): String {
        val repostData = hashMapOf<String, Any>(
            "userId" to currentUserId,
            "type" to originalPost.mediaType.value,
            "repostedFromPostId" to originalPost.id,
            "repostedFromUserId" to originalPost.user.id,
            "repostedFromUsername" to originalPost.user.username,
            "timestamp" to FieldValue.serverTimestamp(),
            "likeCount" to 0,
            "commentCount" to 0,
            "repostCount" to 0,
        )

        if (originalPost.isTrack) {
            repostData.putAll(mapOf(
                "trackId" to originalPost.track.id,
                "trackName" to originalPost.track.name,
                "artistName" to originalPost.track.artistName,
                "albumName" to originalPost.track.albumName,
                "albumArtURL" to (originalPost.track.albumArtURL ?: ""),
                "albumArtLargeURL" to (originalPost.track.albumArtLargeURL ?: ""),
                "spotifyURI" to originalPost.track.spotifyURI,
                "spotifyWebURL" to originalPost.track.spotifyWebURL,
            ))
        } else if (originalPost.isMovie) {
            repostData.putAll(mapOf(
                "movieId" to (originalPost.movieId ?: ""),
                "movieTitle" to (originalPost.movieTitle ?: ""),
                "directorName" to (originalPost.directorName ?: ""),
                "year" to (originalPost.releaseYear ?: ""),
                "posterURL" to (originalPost.posterURL ?: ""),
                "posterLargeURL" to (originalPost.posterLargeURL ?: ""),
            ))
        }

        val docRef = firestore.collection("posts").document()
        repostData["id"] = docRef.id
        docRef.set(repostData).await()

        // Increment repost count on the original post
        firestore.collection("posts").document(originalPost.id)
            .update("repostCount", FieldValue.increment(1))
            .await()

        // Increment user's cymbal count
        firestore.collection("users_v2").document(currentUserId)
            .update("cymbalCount", FieldValue.increment(1))
            .await()

        return docRef.id
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
    ) {
        if (fromUserId == toUserId) return
        val data = mutableMapOf<String, Any>(
            "type" to type,
            "fromUserId" to fromUserId,
            "toUserId" to toUserId,
            "isRead" to false,
            "timestamp" to FieldValue.serverTimestamp(),
        )
        if (postId != null) data["postId"] = postId
        if (postAlbumArtURL != null) data["postAlbumArtURL"] = postAlbumArtURL
        firestore.collection("notifications").add(data).await()
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
            "timestamp" to FieldValue.serverTimestamp(),
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
            firestore.collection("posts").document(postId)
                .collection("comments").document(parentCommentId)
                .update("replyCount", FieldValue.increment(1))
                .await()
        }

        return commentRef.id
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
            .collection("muted_users").get().await()
        return snapshot.documents.map { it.id }
    }

    suspend fun unmuteUser(currentUserId: String, targetUserId: String) {
        firestore.collection("users_v2").document(currentUserId)
            .collection("muted_users").document(targetUserId)
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

    // ── Notifications listener ──

    fun observeNotifications(userId: String, limit: Int = 15): Flow<List<CymbalNotification>> = callbackFlow {
        val registration: ListenerRegistration = firestore.collection("notifications")
            .whereEqualTo("toUserId", userId)
            .orderBy("timestamp", Query.Direction.DESCENDING)
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
            .whereLessThanOrEqualTo("username", lowered + "\uf8ff")
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
        val snapshot = firestore.collection("users_v2")
            .whereEqualTo("isBot", false)
            .orderBy("followerCount", com.google.firebase.firestore.Query.Direction.DESCENDING)
            .limit((limit + excludeIds.size).toLong())
            .get().await()
        return snapshot.documents.mapNotNull { doc ->
            if (excludeIds.contains(doc.id)) return@mapNotNull null
            val data = doc.data ?: return@mapNotNull null
            CymbalUser.fromMap(doc.id, data)
        }.take(limit)
    }

    // ── Comment Likes ──

    suspend fun likeComment(userId: String, postId: String, commentId: String) {
        val batch = firestore.batch()
        batch.set(
            firestore.collection("posts").document(postId)
                .collection("comments").document(commentId)
                .collection("likes").document(userId),
            mapOf("timestamp" to FieldValue.serverTimestamp())
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
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .limit(limit.toLong())
        if (lastTimestamp != null) {
            query = query.startAfter(Date(lastTimestamp))
        }
        val snapshot = query.get().await()
        val docs = snapshot.documents
        val userIds = docs.map { it.id }
        val users = userIds.mapNotNull { fetchUserProfile(it) }
        val nextTimestamp = docs.lastOrNull()?.getTimestamp("timestamp")?.toDate()?.time
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
                "timestamp" to FieldValue.serverTimestamp(),
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
                "timestamp" to FieldValue.serverTimestamp(),
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
