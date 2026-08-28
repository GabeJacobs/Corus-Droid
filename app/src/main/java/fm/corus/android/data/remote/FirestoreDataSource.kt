package fm.corus.android.data.remote

import android.util.Log
import fm.corus.android.domain.UsernameValidator
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldPath
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
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
    /** Usernames that should never appear in popular users, new users, suggestions, or search results. */
    private val hiddenUsernames: Set<String> = setOf("mario", "johnnytravolta", "xhdvdvvd", "testuser", "apple")

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

    // ── Banned-users live cache ──
    //
    // Live snapshot of `banned_users` so banned authors disappear from local
    // UI within seconds of an admin ban (and reappear on unban). Mirrors the
    // backend `getBannedUserIds()` and the iOS `cachedBannedSet` so all
    // three layers agree on what's hidden. The collection is small — one
    // doc per banned user — so a long-lived snapshot listener is cheap.

    @Volatile
    private var cachedBannedSet: Set<String> = emptySet()
    private var bannedUsersListener: ListenerRegistration? = null

    /**
     * Subscribe to the global banned_users denylist. Idempotent — call once
     * at app startup (or on sign-in). The live listener keeps
     * [cachedBannedSet] in sync without polling.
     */
    fun startBannedUsersListener() {
        if (bannedUsersListener != null) return
        bannedUsersListener = firestore.collection("banned_user_ids")
            .addSnapshotListener { snap, err ->
                if (err != null) {
                    Log.w("FirestoreDataSource", "banned_users listener error", err)
                    return@addSnapshotListener
                }
                cachedBannedSet = snap?.documents?.map { it.id }?.toSet() ?: emptySet()
            }
    }

    fun stopBannedUsersListener() {
        bannedUsersListener?.remove()
        bannedUsersListener = null
        cachedBannedSet = emptySet()
    }

    /** O(1) lookup against the live banned-users set. */
    fun isUserBannedLocally(uid: String): Boolean = cachedBannedSet.contains(uid)

    /**
     * Drops items whose author is in the banned set. Reuse at any call site
     * that produces user-authored content from a direct Firestore query
     * that bypasses the cloud-function filter.
     */
    fun <T> filterBannedAuthors(items: List<T>, authorId: (T) -> String?): List<T> =
        filterBannedAuthorsPure(items, cachedBannedSet, authorId)

    // ── Users ──

    suspend fun fetchUserProfile(uid: String): CymbalUser? {
        // Banned users disappear from every viewer except themselves. Self-
        // lookups still resolve (so the banned user can read their own
        // banned_users doc); every other code path treats them as missing.
        val callerUid = firebaseAuth.currentUser?.uid
        if (uid != callerUid && cachedBannedSet.contains(uid)) return null
        val doc = firestore.collection("users_v2").document(uid).get().await()
        if (!doc.exists()) return null
        val data = doc.data ?: return null
        return CymbalUser.fromMap(uid, data)
    }

    suspend fun createUserProfile(uid: String, username: String, displayName: String, email: String, phoneNumber: String = "") {
        val data = mapOf(
            "uid" to uid,
            "username" to username.lowercase(),
            "displayName" to displayName,
            // searchTokens is server-owned (regenerateSearchTokensOnUserWrite
            // populates it from displayName within ~1s); send an empty list
            // to satisfy the create rule's `is list` shape check.
            "searchTokens" to emptyList<String>(),
            "email" to email,
            "phoneNumber" to phoneNumber,
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
        // merge so that if a pre-onboarding stub doc already exists for this uid
        // (e.g. a server-side write persisted fcmToken/timeZone before the
        // profile was created), we initialize the profile over it instead of
        // clobbering those server-owned fields. The matching Firestore rule
        // allows this owner write when the existing doc has no `username` yet.
        firestore.collection("users_v2").document(uid).set(data, SetOptions.merge()).await()
    }

    suspend fun updateUserProfile(uid: String, fields: Map<String, Any?>) {
        firestore.collection("users_v2").document(uid).update(fields).await()
    }

    suspend fun checkUsernameAvailable(username: String): Boolean {
        // Reserved handles (brand/support/system) are never available; treat as
        // taken without a network round-trip.
        if (UsernameValidator.isReserved(username)) return false
        val query = firestore.collection("users_v2")
            .whereEqualTo("username", username.lowercase())
            .limit(1)
            .get()
            .await()
        return query.isEmpty
    }

    // ── Following ──
    //
    // followUser / unfollowUser are now server-driven (CloudFunctionsDataSource)
    // so the backend can enforce the rolling 24h follow cap. The Firestore
    // docs the callable writes are identical to what this datasource used to
    // write, so reconcileFollowingCountOnWrite / feed-fanout / notification
    // triggers all keep working unchanged.

    suspend fun isFollowing(userId: String, targetUserId: String): Boolean {
        val doc = firestore.collection("users_v2").document(userId)
            .collection("following").document(targetUserId)
            .get().await()
        return doc.exists()
    }

    suspend fun fetchFollowingIds(userId: String): Set<String> {
        // Read-cost optimization (parity with the web/server readers): prefer the
        // server-maintained denormalized mirror at users_v2/{uid}/aggregates/following
        // ({ ids, oversize }) — ONE document read instead of scanning the whole
        // /following subcollection. Falls back to the scan whenever the doc is
        // missing (not yet backfilled), flagged oversize, malformed, or errors — and
        // whenever the RC kill-switch is off — so it stays fully backward-compatible
        // and instantly revertible. The /following subcollection remains the source
        // of truth; isFollowing and followingCount are untouched.
        if (remoteConfigService.followingDenormReadsEnabled) {
            try {
                val aggDoc = firestore.collection("users_v2").document(userId)
                    .collection("aggregates").document("following").get().await()
                if (aggDoc.exists() && aggDoc.getBoolean("oversize") != true) {
                    val ids = (aggDoc.get("ids") as? List<*>)?.filterIsInstance<String>()
                    if (ids != null) return ids.toSet()
                }
            } catch (e: Exception) {
                // fall through to the authoritative subcollection scan
            }
        }
        val snapshot = firestore.collection("users_v2").document(userId)
            .collection("following").get().await()
        return snapshot.documents.map { it.id }.toSet()
    }

    suspend fun fetchFollowerIds(userId: String): Set<String> {
        val snapshot = firestore.collection("users_v2").document(userId)
            .collection("followers").get().await()
        return snapshot.documents.map { it.id }.toSet()
    }

    /**
     * Returns the subset of [candidateIds] that appear in [userId]'s followers
     * collection — i.e. which of those users follow [userId]. Batched in chunks
     * of 30 to respect Firestore's `whereIn` limit. Mirrors iOS
     * `checkFollowerStatusBatch`.
     */
    suspend fun checkFollowerStatusBatch(userId: String, candidateIds: List<String>): Set<String> =
        membershipBatch(userId, "followers", candidateIds)

    /**
     * Returns the subset of [candidateIds] that appear in [userId]'s following
     * collection — i.e. which of those users [userId] follows. Batched in
     * chunks of 30 to respect Firestore's `whereIn` limit. Used to scope a
     * follow-list search to members of the list.
     */
    suspend fun checkFollowingStatusBatch(userId: String, candidateIds: List<String>): Set<String> =
        membershipBatch(userId, "following", candidateIds)

    /**
     * Which of [candidateIds] exist in `users_v2/{ownerId}/{subcollection}`,
     * checked via `documentId() in` chunks of 30 run CONCURRENTLY. A sequential
     * loop here was dozens of serial round-trips for a well-connected viewer
     * (the 5-6s mutual-count stall); fanning the chunks out collapses it to ~one.
     */
    private suspend fun membershipBatch(
        ownerId: String,
        subcollection: String,
        candidateIds: List<String>,
    ): Set<String> = coroutineScope {
        if (candidateIds.isEmpty()) return@coroutineScope emptySet()
        candidateIds.chunked(30).map { chunk ->
            async {
                firestore.collection("users_v2").document(ownerId)
                    .collection(subcollection)
                    .whereIn(FieldPath.documentId(), chunk)
                    .get().await()
                    .documents.map { it.id }
            }
        }.awaitAll().flatten().toSet()
    }

    /**
     * Returns the subset of [postIds] the current user has liked, read from
     * their OWN `users_v2/{uid}/liked` index in chunks of 30. Firestore bills a
     * `whereIn` query only for the docs it returns (the liked ones), so a page
     * of 30 where the viewer liked 2 costs ~2 reads — not 30 per-card
     * `likes/{uid}` reads. Used to seed a whole page's like-state at once
     * (resilient fallback for posts the backend didn't stamp with `isLiked`).
     */
    suspend fun fetchLikedStates(postIds: List<String>): Set<String> {
        val uid = firebaseAuth.currentUser?.uid ?: return emptySet()
        val ids = postIds.filter { it.isNotEmpty() }.distinct() // `whereIn` rejects dupes
        if (ids.isEmpty()) return emptySet()
        val result = mutableSetOf<String>()
        ids.chunked(30).forEach { chunk ->
            val snapshot = firestore.collection("users_v2").document(uid)
                .collection("liked")
                .whereIn(FieldPath.documentId(), chunk)
                .get().await()
            snapshot.documents.forEach { result.add(it.id) }
        }
        return result
    }

    /**
     * Returns the subset of [postIds] the current user has saved, read from
     * their OWN `users_v2/{uid}/saves` index in chunks of 30. Like
     * [fetchLikedStates], the `whereIn` query bills only for the saved matches,
     * so a page of 30 where the viewer saved 1 costs ~1 read — not 30 per-card
     * `saves/{postId}` reads. Used to seed a page's save-state at once (the
     * backend doesn't stamp posts with `isSaved`).
     */
    suspend fun fetchSavedStates(postIds: List<String>): Set<String> {
        val uid = firebaseAuth.currentUser?.uid ?: return emptySet()
        val ids = postIds.filter { it.isNotEmpty() }.distinct() // `whereIn` rejects dupes
        if (ids.isEmpty()) return emptySet()
        val result = mutableSetOf<String>()
        ids.chunked(30).forEach { chunk ->
            val snapshot = firestore.collection("users_v2").document(uid)
                .collection("saves")
                .whereIn(FieldPath.documentId(), chunk)
                .get().await()
            snapshot.documents.forEach { result.add(it.id) }
        }
        return result
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
        val likeRef = firestore.collection("posts").document(postId)
            .collection("likes").document(userId)
        val userLikeRef = firestore.collection("users_v2").document(userId)
            .collection("liked").document(postId)
        val postRef = firestore.collection("posts").document(postId)

        // Read-then-conditional-write inside a transaction so a phantom-commit retry
        // (server committed but client got an auth/network error in the response)
        // observes the existing like marker and skips a second increment.
        firestore.runTransaction { txn ->
            val existing = txn.get(likeRef)
            if (existing.exists()) return@runTransaction null
            txn.set(likeRef, mapOf("createdAt" to FieldValue.serverTimestamp()))
            txn.set(userLikeRef, mapOf("createdAt" to FieldValue.serverTimestamp()))
            txn.update(postRef, "likeCount", FieldValue.increment(1))
            null
        }.await()
    }

    suspend fun unlikePost(userId: String, postId: String) = withAuthRetry {
        val likeRef = firestore.collection("posts").document(postId)
            .collection("likes").document(userId)
        val userLikeRef = firestore.collection("users_v2").document(userId)
            .collection("liked").document(postId)
        val postRef = firestore.collection("posts").document(postId)

        firestore.runTransaction { txn ->
            val existing = txn.get(likeRef)
            if (!existing.exists()) return@runTransaction null
            txn.delete(likeRef)
            txn.delete(userLikeRef)
            txn.update(postRef, "likeCount", FieldValue.increment(-1))
            null
        }.await()
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

        // cymbalCount/trackCount/movieCount are bumped by the
        // onPostCreatedFanoutFeedPointers Cloud Function via the idempotent
        // helper. Doing it here would double-count cymbalCount.

        // repostCount on the original post is bumped server-side by the
        // onPostCreated cloud function — incrementing here too would
        // double-count. Matches iOS DatabaseService.createPost.

        return docRef.id
    }

    suspend fun updatePostVoiceNoteURL(postId: String, url: String) {
        firestore.collection("posts").document(postId)
            .update("voiceNoteURL", url)
            .await()
    }

    suspend fun updateCaption(postId: String, caption: String, hashtags: List<String>) {
        // Write the parsed hashtags alongside the caption. This edit path goes
        // direct to Firestore (no createPost callable), so nothing normalizes
        // the array server-side — lowercase here so the backend
        // onHashtagPostsChanged trigger re-indexes and array-contains queries
        // match. iOS / Web do the same on edit.
        firestore.collection("posts").document(postId)
            .update(
                mapOf(
                    "caption" to caption,
                    "hashtags" to hashtags.map { it.lowercase() },
                )
            )
            .await()
    }

    @Suppress("UNUSED_PARAMETER")
    suspend fun createNotification(
        type: String,
        fromUserId: String,
        toUserId: String,
        postId: String? = null,
        postAlbumArtURL: String? = null,
        commentText: String? = null,
        commentId: String? = null,
        attachmentType: String? = null,
    ) {
        // Server-only notification writes. Cloud Functions handle every type
        // (see onCommentCreatedNotify, onPostLikeCreated, etc.). Existing call
        // sites are kept intact as no-ops; full removal will follow in a later
        // release once this build has rolled out.
        return
    }

    suspend fun deletePost(postId: String, userId: String) {
        firestore.collection("posts").document(postId).delete().await()
        // cymbalCount/trackCount/movieCount are decremented by the
        // onPostDeletedCleanupFeedPointers Cloud Function. Doing it here would
        // double-count.
    }

    // ── Comments ──

    suspend fun addComment(
        postId: String,
        userId: String,
        text: String,
        parentCommentId: String? = null,
        replyToUserId: String? = null,
        gifURL: String? = null,
        attachedSong: fm.corus.android.data.model.CommentAttachedSong? = null,
        attachedFilm: fm.corus.android.data.model.CommentAttachedFilm? = null,
        attachedArtist: fm.corus.android.data.model.CommentAttachedArtist? = null,
        attachedAlbum: fm.corus.android.data.model.CommentAttachedAlbum? = null,
        attachedDirector: fm.corus.android.data.model.CommentAttachedDirector? = null,
    ): String {
        // Mutual exclusion: at most one attachment of any kind.
        val attachmentCount = (if (gifURL != null) 1 else 0) +
            (if (attachedSong != null) 1 else 0) +
            (if (attachedFilm != null) 1 else 0) +
            (if (attachedArtist != null) 1 else 0) +
            (if (attachedAlbum != null) 1 else 0) +
            (if (attachedDirector != null) 1 else 0)
        require(attachmentCount <= 1) { "Comment can have at most one attachment" }

        // Backwards-compat: when the caption is empty but we're attaching media,
        // synthesize a fallback `text` so old clients still render something legible.
        // New clients detect `textIsAttachmentFallback` and suppress text rendering.
        val trimmedCaption = text.trim()
        var writtenText = text
        var isAttachmentFallback = false
        if (trimmedCaption.isEmpty()) {
            when {
                attachedSong != null -> {
                    writtenText = attachedSong.fallbackText
                    isAttachmentFallback = true
                }
                attachedFilm != null -> {
                    writtenText = attachedFilm.fallbackText
                    isAttachmentFallback = true
                }
                attachedArtist != null -> {
                    writtenText = attachedArtist.fallbackText
                    isAttachmentFallback = true
                }
                attachedAlbum != null -> {
                    writtenText = attachedAlbum.fallbackText
                    isAttachmentFallback = true
                }
                attachedDirector != null -> {
                    writtenText = attachedDirector.fallbackText
                    isAttachmentFallback = true
                }
            }
        }

        val commentRef = firestore.collection("posts").document(postId)
            .collection("comments").document()
        val commentData = mutableMapOf<String, Any?>(
            "id" to commentRef.id,
            "userId" to userId,
            "text" to writtenText,
            "createdAt" to FieldValue.serverTimestamp(),
            "likeCount" to 0,
            "replyCount" to 0,
        )
        parentCommentId?.let { commentData["parentCommentId"] = it }
        replyToUserId?.let { commentData["replyToUserId"] = it }
        gifURL?.let { commentData["gifURL"] = it }
        attachedSong?.let { commentData["attachedSong"] = it.toFirestoreMap() }
        attachedFilm?.let { commentData["attachedFilm"] = it.toFirestoreMap() }
        attachedArtist?.let { commentData["attachedArtist"] = it.toFirestoreMap() }
        attachedAlbum?.let { commentData["attachedAlbum"] = it.toFirestoreMap() }
        attachedDirector?.let { commentData["attachedDirector"] = it.toFirestoreMap() }
        if (isAttachmentFallback) commentData["textIsAttachmentFallback"] = true
        commentRef.set(commentData).await()

        // commentCount is reconciled server-side by onCommentCreatedReconcileCount.

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

    /**
     * Returns the parentCommentId field of a comment, or null if the comment is top-level
     * or cannot be fetched. Used to re-root replies-to-replies onto the top-level comment,
     * since the comment system only supports two levels.
     */
    suspend fun getCommentParentId(postId: String, commentId: String): String? {
        return try {
            firestore.collection("posts").document(postId)
                .collection("comments").document(commentId)
                .get().await().getString("parentCommentId")
        } catch (_: Exception) {
            null
        }
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

        // commentCount is reconciled server-side by onCommentDeletedReconcileCount.

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

    suspend fun fetchBlockedByIds(userId: String): Set<String> {
        val snapshot = firestore.collection("users_v2").document(userId)
            .collection("blockedBy").get().await()
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

    /** Windowed trending hashtag list, reading from the pre-ranked
     *  `trending_cache/hashtags` cache doc built by `refreshTrendingCache`.
     *  Mirrors `fetchTrendingSongs`/`fetchTrendingMovies` so swapping the
     *  window doesn't re-aggregate. */
    @Suppress("UNCHECKED_CAST")
    suspend fun fetchTrendingHashtagsWindowed(
        window: TrendingWindow = TrendingWindow.DEFAULT,
        limit: Int = 20,
    ): List<TrendingHashtag> {
        val doc = firestore.collection("trending_cache").document("hashtags")
            .get().await()
        val data = doc.data ?: return emptyList()
        val items = pickWindowItems(data, window)
        return items.take(limit).mapIndexedNotNull { i, item ->
            val name = item["name"] as? String ?: return@mapIndexedNotNull null
            if (name.isEmpty()) return@mapIndexedNotNull null
            TrendingHashtag(
                id = name,
                rank = (item["rank"] as? Number)?.toInt() ?: (i + 1),
                name = name,
                cymbalCount = (item["cymbalCount"] as? Number)?.toInt() ?: 0,
                followerCount = (item["followerCount"] as? Number)?.toInt() ?: 0,
            )
        }
    }

    /** Authoritative aggregates from `hashtags/{tag}`. Returns null if the
     *  doc doesn't exist (legacy tag that hasn't been touched by any
     *  trigger). */
    suspend fun fetchHashtag(tag: String): CymbalHashtag? {
        val key = tag.lowercase()
        val doc = firestore.collection("hashtags").document(key).get().await()
        if (!doc.exists()) return null
        val data = doc.data ?: return null
        return decodeHashtag(doc.id, data)
    }

    /** Top contributors for the facepile. Ordered by `lastPostedAt` desc so
     *  the recently-active authors show first. Reads denormalized profile
     *  fields written by the `onHashtagPostsChanged` trigger. */
    suspend fun fetchTopHashtagContributors(tag: String, limit: Int = 6): List<HashtagContributor> {
        val key = tag.lowercase()
        val snap = firestore.collection("hashtags").document(key)
            .collection("contributors")
            .orderBy("lastPostedAt", Query.Direction.DESCENDING)
            .limit(limit.toLong())
            .get().await()
        return snap.documents.mapNotNull { doc ->
            val data = doc.data ?: return@mapNotNull null
            decodeContributor(doc.id, data)
        }
    }

    /** Paginated contributor uids for the "Contributors of #tag" list. The
     *  caller hydrates full user profiles via `fetchUsersByIds`. */
    suspend fun fetchHashtagContributorIds(
        tag: String,
        limit: Int = 30,
        after: DocumentSnapshot? = null,
    ): Pair<List<String>, DocumentSnapshot?> {
        val key = tag.lowercase()
        var q = firestore.collection("hashtags").document(key)
            .collection("contributors")
            .orderBy("lastPostedAt", Query.Direction.DESCENDING)
            .limit(limit.toLong())
        if (after != null) q = q.startAfter(after)
        val snap = q.get().await()
        return snap.documents.map { it.id } to snap.documents.lastOrNull()
    }

    /** Paginated follower uids for the "Followers of #tag" list. Uses a
     *  collectionGroup query on `hashtagsFollowed` — each match's parent
     *  path is `users_v2/{uid}/hashtagsFollowed/{tag}`. Requires the
     *  single-field collectionGroup index on `hashtagsFollowed.tag` plus
     *  the recursive-wildcard read rule. */
    suspend fun fetchHashtagFollowerIds(
        tag: String,
        limit: Int = 30,
        after: DocumentSnapshot? = null,
    ): Pair<List<String>, DocumentSnapshot?> {
        val key = tag.lowercase()
        var q: com.google.firebase.firestore.Query = firestore
            .collectionGroup("hashtagsFollowed")
            .whereEqualTo("tag", key)
            .limit(limit.toLong())
        if (after != null) q = q.startAfter(after)
        val snap = q.get().await()
        val ids = snap.documents.mapNotNull { doc ->
            val parts = doc.reference.path.split("/")
            if (parts.size >= 4 && parts[0] == "users_v2") parts[1] else null
        }
        return ids to snap.documents.lastOrNull()
    }

    @Suppress("UNCHECKED_CAST")
    suspend fun fetchTrendingHashtags(limit: Int = 10): List<CymbalHashtag> {
        val snapshot = firestore.collection("hashtags")
            .orderBy("cymbalCount", Query.Direction.DESCENDING)
            .limit(limit.toLong())
            .get().await()
        return snapshot.documents.mapNotNull { doc ->
            val data = doc.data ?: return@mapNotNull null
            decodeHashtag(doc.id, data)
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun decodeHashtag(id: String, data: Map<String, Any?>): CymbalHashtag {
        return CymbalHashtag(
            id = id,
            name = data["name"] as? String ?: id,
            cymbalCount = (data["cymbalCount"] as? Number)?.toInt() ?: 0,
            coverArtURLs = (data["coverArtURLs"] as? List<String>) ?: emptyList(),
            followerCount = (data["followerCount"] as? Number)?.toInt() ?: 0,
            contributorCount = (data["contributorCount"] as? Number)?.toInt() ?: 0,
            recentCount = (data["recentCount"] as? Number)?.toInt() ?: 0,
        )
    }

    private fun decodeContributor(id: String, data: Map<String, Any?>): HashtagContributor {
        val ts = data["lastPostedAt"] as? com.google.firebase.Timestamp
        val lastMs = ts?.toDate()?.time ?: 0L
        return HashtagContributor(
            id = id,
            username = data["username"] as? String ?: "",
            displayName = data["displayName"] as? String ?: "",
            photoURL = data["photoURL"] as? String,
            lastPostedAtMs = lastMs,
        )
    }

    /** Follow a hashtag — mirrors the web `followHashtag` exactly: writes
     *  `users_v2/{uid}/hashtagsFollowed/{tag}` and bumps `hashtagCount` in a
     *  batch so the two stay in sync. */
    suspend fun followHashtag(uid: String, tag: String) {
        val lower = tag.lowercase()
        val batch = firestore.batch()
        batch.set(
            firestore.collection("users_v2").document(uid)
                .collection("hashtagsFollowed").document(lower),
            mapOf(
                "tag" to lower,
                "createdAt" to FieldValue.serverTimestamp(),
            )
        )
        batch.update(
            firestore.collection("users_v2").document(uid),
            "hashtagCount", FieldValue.increment(1)
        )
        batch.commit().await()
    }

    suspend fun unfollowHashtag(uid: String, tag: String) {
        val lower = tag.lowercase()
        val batch = firestore.batch()
        batch.delete(
            firestore.collection("users_v2").document(uid)
                .collection("hashtagsFollowed").document(lower)
        )
        batch.update(
            firestore.collection("users_v2").document(uid),
            "hashtagCount", FieldValue.increment(-1)
        )
        batch.commit().await()
    }

    suspend fun isHashtagFollowed(uid: String, tag: String): Boolean {
        val lower = tag.lowercase()
        val doc = firestore.collection("users_v2").document(uid)
            .collection("hashtagsFollowed").document(lower)
            .get().await()
        return doc.exists()
    }

    /** Bulk-fetch the set of hashtag names the user currently follows.
     *  Mirrors web's `getFollowedHashtags` and the iOS counterpart. */
    suspend fun fetchFollowedHashtagNames(uid: String, max: Int = 200): Set<String> {
        val snapshot = firestore.collection("users_v2").document(uid)
            .collection("hashtagsFollowed")
            .limit(max.toLong())
            .get().await()
        return snapshot.documents.map { it.id }.toSet()
    }

    /** Preview info for a hashtag in the search list: 2x2 album-art mosaic +
     *  the live total post count. The `hashtags/{tag}.cymbalCount` counter is
     *  increment-only, so it drifts upward over time — the live count from a
     *  fresh `count()` aggregation keeps the search row in sync with the
     *  detail screen. Mirrors `fetchHashtagPreview` on iOS / Web. */
    data class HashtagPreview(val coverArt: List<String>, val totalCount: Int)

    suspend fun fetchHashtagPreview(tag: String, artLimit: Int = 4): HashtagPreview {
        val key = tag.trim().removePrefix("#").lowercase()
        if (key.isEmpty()) return HashtagPreview(emptyList(), 0)
        val postsCollection = firestore.collection("posts")
        val tagFilter = postsCollection.whereArrayContains("hashtags", key)
        val artQuery = tagFilter
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .limit(artLimit.toLong())
        val artSnap = artQuery.get().await()
        val countSnap = tagFilter.count().get(com.google.firebase.firestore.AggregateSource.SERVER).await()
        val coverArt = artSnap.documents.mapNotNull { doc ->
            // Songs use albumArtURL; films use posterURL. Newer film posts also
            // set albumArtURL for backwards compat, but older ones don't, so
            // fall through both fields.
            val data = doc.data ?: return@mapNotNull null
            (data["albumArtURL"] as? String)
                ?.takeIf { it.isNotEmpty() }
                ?: (data["posterURL"] as? String)?.takeIf { it.isNotEmpty() }
                ?: (data["albumArtLargeURL"] as? String)?.takeIf { it.isNotEmpty() }
                ?: (data["posterLargeURL"] as? String)?.takeIf { it.isNotEmpty() }
        }
        return HashtagPreview(coverArt, countSnap.count.toInt())
    }

    /** Prefix-match hashtags by `name`. Mirrors the iOS implementation:
     *  Firestore range trick (`>= prefix && <= prefix + ""`) over the
     *  `hashtags` collection, then sorted client-side by `cymbalCount` desc.
     *  Strips a leading `#` and lowercases the prefix. Returns empty for a
     *  blank prefix without hitting Firestore. */
    @Suppress("UNCHECKED_CAST")
    suspend fun searchHashtagsByPrefix(prefix: String, limit: Int = 20): List<CymbalHashtag> {
        val lower = prefix.trim().removePrefix("#").lowercase()
        if (lower.isEmpty()) return emptyList()
        val upper = lower + ""
        val snapshot = firestore.collection("hashtags")
            .orderBy("name")
            .startAt(lower)
            .endAt(upper)
            .limit(limit.toLong())
            .get().await()
        return snapshot.documents.mapNotNull { doc ->
            val data = doc.data ?: return@mapNotNull null
            CymbalHashtag(
                id = doc.id,
                name = data["name"] as? String ?: doc.id,
                cymbalCount = (data["cymbalCount"] as? Number)?.toInt() ?: 0,
                coverArtURLs = (data["coverArtURLs"] as? List<String>) ?: emptyList(),
                // Trailing 7-day count — the composer autocomplete ranks + labels
                // by this so stale tags sink and the number stays bounded.
                recentCount = (data["recentCount"] as? Number)?.toInt() ?: 0,
            )
        }.sortedByDescending { it.cymbalCount }
    }

    // ── Per-post engagement listener (matching iOS PostEngagementStore) ──

    data class PostCounts(val likeCount: Int, val commentCount: Int, val repostCount: Int, val saveCount: Int)

    /** One-shot read of denormalized counts on a post doc. Cheaper than a full
     *  cloud-function `getPostDetail` when we only need to refresh badges. */
    suspend fun fetchPostCounts(postId: String): PostCounts? {
        return try {
            val data = firestore.collection("posts").document(postId).get().await().data ?: return null
            PostCounts(
                likeCount = (data["likeCount"] as? Number)?.toInt() ?: 0,
                commentCount = (data["commentCount"] as? Number)?.toInt() ?: 0,
                repostCount = (data["repostCount"] as? Number)?.toInt() ?: 0,
                saveCount = ((data["saveCount"] as? Number)?.toInt() ?: 0).coerceAtLeast(0),
            )
        } catch (_: Exception) {
            null
        }
    }

    fun listenForPostUpdates(
        postId: String,
        onUpdate: (likeCount: Int, commentCount: Int, repostCount: Int, saveCount: Int) -> Unit,
    ): ListenerRegistration {
        return firestore.collection("posts").document(postId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) return@addSnapshotListener
                val data = snapshot?.data ?: return@addSnapshotListener
                val likeCount = (data["likeCount"] as? Number)?.toInt() ?: 0
                val commentCount = (data["commentCount"] as? Number)?.toInt() ?: 0
                val repostCount = (data["repostCount"] as? Number)?.toInt() ?: 0
                val saveCount = ((data["saveCount"] as? Number)?.toInt() ?: 0).coerceAtLeast(0)
                onUpdate(likeCount, commentCount, repostCount, saveCount)
            }
    }

    /**
     * Real-time listener on a post's comments subcollection, mirroring iOS
     * `DatabaseService.listenForCommentChanges`. Fires `onChange` whenever a
     * comment document changes — including comment-like increments, which write
     * `likeCount` onto the comment doc — so an open comments screen keeps its
     * like counts and heart state fresh instead of showing a load-time snapshot.
     *
     * The initial snapshot is skipped: the screen already loaded comments via
     * `getComments`, so reacting to the listener's first emission would just
     * trigger a redundant reload.
     */
    fun listenForCommentChanges(
        postId: String,
        onChange: () -> Unit,
    ): ListenerRegistration {
        var skippedInitial = false
        return firestore.collection("posts").document(postId)
            .collection("comments")
            .addSnapshotListener { snapshot, error ->
                if (error != null || snapshot == null) return@addSnapshotListener
                if (!skippedInitial) {
                    skippedInitial = true
                    return@addSnapshotListener
                }
                if (snapshot.documentChanges.isEmpty()) return@addSnapshotListener
                onChange()
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
            .update(mapOf("fcmToken" to FieldValue.delete(), "fcmTokenUpdatedAt" to FieldValue.delete()))
            .await()
    }

    // ── Trending Songs (from trending_cache/songs, matching iOS) ──

    /** Picks the items array for the requested window from a trending_cache
     *  doc, falling back to the legacy `items` field when the new shape isn't
     *  present yet (rollout window before the next BE refresh tick). */
    @Suppress("UNCHECKED_CAST")
    private fun pickWindowItems(
        data: Map<String, Any?>,
        window: TrendingWindow,
    ): List<Map<String, Any?>> {
        val bucket = data[window.key] as? Map<String, Any?>
        val windowed = bucket?.get("items") as? List<Map<String, Any?>>
        if (windowed != null) return windowed
        return (data["items"] as? List<Map<String, Any?>>).orEmpty()
    }

    private fun parseTrendingSong(item: Map<String, Any?>): TrendingSong? {
        val trackId = item["trackId"] as? String ?: return null
        return TrendingSong(
            id = trackId,
            rank = (item["rank"] as? Number)?.toInt() ?: 0,
            track = CymbalTrack.fromMap(item),
            cymbalCount = (item["cymbalCount"] as? Number)?.toInt() ?: 0,
        )
    }

    @Suppress("UNCHECKED_CAST")
    private fun parseTrendingMovie(item: Map<String, Any?>): TrendingMovie? {
        val movieId = item["movieId"] as? String ?: return null
        return TrendingMovie(
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
            movieReleaseDate = (item["movieReleaseDate"] as? String)?.ifEmpty { null },
            cymbalCount = (item["cymbalCount"] as? Number)?.toInt() ?: 0,
        )
    }

    /** Reads the trending-songs cache once and returns all three windows.
     *  The repository uses this to populate per-window cache entries from
     *  a single Firestore read so window switching is free. */
    suspend fun fetchTrendingSongsByWindow(limit: Int = 20): Map<TrendingWindow, List<TrendingSong>> {
        val doc = firestore.collection("trending_cache").document("songs").get().await()
        val data = doc.data ?: return emptyMap()
        return TrendingWindow.values().associateWith { window ->
            pickWindowItems(data, window).take(limit).mapNotNull { parseTrendingSong(it) }
        }
    }

    /** Same shape as `fetchTrendingSongsByWindow` but for movies. */
    suspend fun fetchTrendingMoviesByWindow(limit: Int = 20): Map<TrendingWindow, List<TrendingMovie>> {
        val doc = firestore.collection("trending_cache").document("movies").get().await()
        val data = doc.data ?: return emptyMap()
        return TrendingWindow.values().associateWith { window ->
            pickWindowItems(data, window).take(limit).mapNotNull { parseTrendingMovie(it) }
        }
    }

    /** Same shape as `fetchTrendingSongsByWindow` but for artists
     *  (trending_cache/artists — week/month/year). A missing week bucket
     *  stays empty rather than falling back to the legacy month `items`. */
    suspend fun fetchTrendingArtistsByWindow(limit: Int = 20): Map<TrendingWindow, List<TrendingArtist>> {
        val doc = firestore.collection("trending_cache").document("artists").get().await()
        val data = doc.data ?: return emptyMap()
        return TrendingWindow.values().associateWith { window ->
            if (window == TrendingWindow.WEEK && data["week"] == null) {
                emptyList()
            } else {
                pickWindowItems(data, window).take(limit).mapNotNull { parseTrendingArtist(it) }
            }
        }
    }

    private fun parseTrendingArtist(item: Map<String, Any?>): TrendingArtist? {
        val name = (item["artistName"] as? String)?.takeIf { it.isNotBlank() } ?: return null
        return TrendingArtist(
            id = name.lowercase(),
            rank = (item["rank"] as? Number)?.toInt() ?: 0,
            artistName = name,
            albumArtURL = (item["albumArtURL"] as? String)?.takeIf { it.isNotBlank() },
            albumArtLargeURL = (item["albumArtLargeURL"] as? String)?.takeIf { it.isNotBlank() },
            cymbalCount = (item["cymbalCount"] as? Number)?.toInt() ?: 0,
        )
    }

    /** Same shape as `fetchTrendingArtistsByWindow` but for albums
     *  (trending_cache/albums). A missing week bucket stays empty. */
    suspend fun fetchTrendingAlbumsByWindow(limit: Int = 20): Map<TrendingWindow, List<TrendingAlbum>> {
        val doc = firestore.collection("trending_cache").document("albums").get().await()
        val data = doc.data ?: return emptyMap()
        return TrendingWindow.values().associateWith { window ->
            if (window == TrendingWindow.WEEK && data["week"] == null) {
                emptyList()
            } else {
                pickWindowItems(data, window).take(limit).mapNotNull { parseTrendingAlbum(it) }
            }
        }
    }

    private fun parseTrendingAlbum(item: Map<String, Any?>): TrendingAlbum? {
        val name = item["albumName"] as? String ?: ""
        val albumId = item["albumId"] as? String ?: ""
        if (name.isEmpty() && albumId.isEmpty()) return null
        val id = if (albumId.isEmpty()) name.lowercase() else albumId
        return TrendingAlbum(
            id = id,
            rank = (item["rank"] as? Number)?.toInt() ?: 0,
            albumId = albumId,
            albumName = name,
            artistName = item["artistName"] as? String ?: "",
            albumArtURL = (item["albumArtURL"] as? String)?.takeIf { it.isNotBlank() },
            albumArtLargeURL = (item["albumArtLargeURL"] as? String)?.takeIf { it.isNotBlank() },
            cymbalCount = (item["cymbalCount"] as? Number)?.toInt() ?: 0,
        )
    }

    /**
     * Unique songs still inside the 30-day new-release window (New Songs).
     * Grouped by ISRC, then track id, then artist + title. Every row opens
     * the song page. Mirrors iOS `fetchNewReleaseAlbums`.
     */
    suspend fun fetchNewReleaseAlbums(limit: Int = 20): List<TrendingAlbum> {
        val nowMs = System.currentTimeMillis()
        val snap = firestore.collection("posts")
            .whereEqualTo("mediaType", "track")
            .whereGreaterThan("newReleaseExpiresAtMs", nowMs)
            .orderBy("newReleaseExpiresAtMs", Query.Direction.DESCENDING)
            .limit(100)
            .get()
            .await()
        val bySong = linkedMapOf<String, Pair<TrendingAlbum, String>>()
        for (doc in snap.documents) {
            val data = doc.data ?: continue
            @Suppress("UNCHECKED_CAST")
            val author = data["author"] as? Map<String, Any?>
            if (author?.get("isBot") == true) continue
            val uid = data["userId"] as? String
            if (uid != null && isUserBannedLocally(uid)) continue
            val parsed = parseNewReleaseSongFromPost(data) ?: continue
            val key = parsed.first
            val album = parsed.second
            val releaseDate = album.trackReleaseDate
            val existing = bySong[key]
            if (existing != null) {
                val prev = existing.first
                bySong[key] = prev.copy(
                    albumId = prev.albumId.ifEmpty { album.albumId },
                    cymbalCount = prev.cymbalCount + 1,
                    trackId = prev.trackId.ifEmpty { album.trackId },
                    trackName = prev.trackName.ifEmpty { album.trackName },
                    trackReleaseDate = maxOf(prev.trackReleaseDate, releaseDate),
                    trackReleaseDatePrecision = prev.trackReleaseDatePrecision.ifEmpty {
                        album.trackReleaseDatePrecision
                    },
                ) to maxOf(existing.second, releaseDate)
                continue
            }
            bySong[key] = album to releaseDate
        }
        return bySong.values
            .sortedWith(
                compareByDescending<Pair<TrendingAlbum, String>> { it.second }
                    .thenByDescending { it.first.cymbalCount },
            )
            .take(limit)
            .mapIndexed { index, entry ->
                entry.first.copy(rank = index + 1, openAsSong = true)
            }
    }

    private fun parseNewReleaseSongFromPost(data: Map<String, Any?>): Pair<String, TrendingAlbum>? {
        val trackId = (data["trackId"] as? String)?.trim().orEmpty()
        val albumId = (data["albumId"] as? String)?.trim().orEmpty()
        val albumName = (data["albumName"] as? String)?.trim().orEmpty()
        val trackName = (data["trackName"] as? String)?.trim().orEmpty()
        val artistName = (data["artistName"] as? String)?.trim().orEmpty()
        val isrc = (data["isrc"] as? String)?.trim().orEmpty()
        val releaseDate = (data["trackReleaseDate"] as? String)?.trim().orEmpty()
        val precision = (data["trackReleaseDatePrecision"] as? String)?.trim().orEmpty()
        val title = trackName.ifEmpty { albumName }
        if (trackId.isEmpty()) return null
        val key = when {
            isrc.isNotEmpty() -> "isrc:${isrc.lowercase()}"
            trackId.isNotEmpty() -> "track:${trackId.lowercase()}"
            else -> "name:${artistName.lowercase()}\u0000${title.lowercase()}"
        }
        return key to TrendingAlbum(
            id = key,
            rank = 0,
            albumId = albumId,
            albumName = albumName,
            artistName = artistName,
            albumArtURL = (data["albumArtURL"] as? String)?.takeIf { it.isNotBlank() },
            albumArtLargeURL = (data["albumArtLargeURL"] as? String)?.takeIf { it.isNotBlank() },
            cymbalCount = 1,
            openAsSong = true,
            trackId = trackId,
            trackName = title,
            trackReleaseDate = releaseDate,
            trackReleaseDatePrecision = precision,
        )
    }

    // ── User Search ──

    suspend fun searchUsersByUsername(query: String, limit: Int = 20): List<CymbalUser> {
        val lowered = query.lowercase()
        val snapshot = firestore.collection("users_v2")
            .whereGreaterThanOrEqualTo("username", lowered)
            .whereLessThan("username", lowered + "\uf8ff")
            .limit(limit.toLong())
            .get().await()
        val users = snapshot.documents.mapNotNull { doc ->
            val data = doc.data ?: return@mapNotNull null
            CymbalUser.fromMap(doc.id, data)
        }
        return filterBannedAuthors(users) { it.id }
    }

    /** Search users whose searchTokens array contains the given (already-lowercased) token. */
    suspend fun searchUsersByToken(token: String, limit: Int = 20): List<CymbalUser> {
        val snapshot = firestore.collection("users_v2")
            .whereArrayContains("searchTokens", token)
            .limit(limit.toLong())
            .get().await()
        val users = snapshot.documents.mapNotNull { doc ->
            val data = doc.data ?: return@mapNotNull null
            CymbalUser.fromMap(doc.id, data)
        }
        return filterBannedAuthors(users) { it.id }
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
                if (cachedBannedSet.contains(doc.id)) return@forEach
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
            .orderBy("buzzScore", com.google.firebase.firestore.Query.Direction.DESCENDING)
            .limit(fetchCount.toLong())
            .get().await()
        return snapshot.documents.mapNotNull { doc ->
            if (excludeIds.contains(doc.id)) return@mapNotNull null
            if (cachedBannedSet.contains(doc.id)) return@mapNotNull null
            val data = doc.data ?: return@mapNotNull null
            val user = CymbalUser.fromMap(doc.id, data)
            // Eligibility (active / not-bot / not-banned / not-hidden) is enforced
            // server-side via buzzScore; only real-time safety filters remain here.
            if (user.isBot) return@mapNotNull null
            if (hiddenUsernames.contains(user.username)) return@mapNotNull null
            user
        }.take(limit)
    }

    suspend fun fetchPopularUsersPaginated(
        limit: Int = 20,
        excludeIds: Set<String> = emptySet(),
        afterDocId: String? = null,
    ): List<CymbalUser> {
        val fetchCount = limit * 3 + excludeIds.size
        var query = firestore.collection("users_v2")
            .orderBy("buzzScore", com.google.firebase.firestore.Query.Direction.DESCENDING)
            .limit(fetchCount.toLong())

        if (afterDocId != null) {
            val afterDoc = firestore.collection("users_v2").document(afterDocId).get().await()
            if (afterDoc.exists()) {
                query = query.startAfter(afterDoc)
            }
        }

        val snapshot = query.get().await()
        val users = mutableListOf<CymbalUser>()
        for (doc in snapshot.documents) {
            if (excludeIds.contains(doc.id)) continue
            if (cachedBannedSet.contains(doc.id)) continue
            val data = doc.data ?: continue
            val user = CymbalUser.fromMap(doc.id, data)
            // Eligibility enforced server-side via buzzScore; keep only safety filters.
            if (user.isBot) continue
            if (hiddenUsernames.contains(user.username)) continue
            users.add(user)
            if (users.size >= limit) break
        }
        return users
    }

    suspend fun fetchNewUsers(limit: Int = 10, excludeIds: Set<String> = emptySet()): List<CymbalUser> {
        val fetchCount = limit + excludeIds.size + 20
        val snapshot = firestore.collection("users_v2")
            .orderBy("createdAt", com.google.firebase.firestore.Query.Direction.DESCENDING)
            .limit(fetchCount.toLong())
            .get().await()
        return snapshot.documents.mapNotNull { doc ->
            if (excludeIds.contains(doc.id)) return@mapNotNull null
            if (cachedBannedSet.contains(doc.id)) return@mapNotNull null
            val data = doc.data ?: return@mapNotNull null
            val user = CymbalUser.fromMap(doc.id, data)
            if (user.isBot) return@mapNotNull null
            if (hiddenUsernames.contains(user.username)) return@mapNotNull null
            if (user.createdAt == null) return@mapNotNull null
            user
        }.take(limit)
    }

    suspend fun fetchNewUsersPaginated(
        limit: Int = 20,
        excludeIds: Set<String> = emptySet(),
        afterDocId: String? = null,
    ): List<CymbalUser> {
        val fetchCount = limit * 2 + excludeIds.size
        var query = firestore.collection("users_v2")
            .orderBy("createdAt", com.google.firebase.firestore.Query.Direction.DESCENDING)
            .limit(fetchCount.toLong())

        if (afterDocId != null) {
            val afterDoc = firestore.collection("users_v2").document(afterDocId).get().await()
            if (afterDoc.exists()) {
                query = query.startAfter(afterDoc)
            }
        }

        val snapshot = query.get().await()
        val users = mutableListOf<CymbalUser>()
        for (doc in snapshot.documents) {
            if (excludeIds.contains(doc.id)) continue
            if (cachedBannedSet.contains(doc.id)) continue
            val data = doc.data ?: continue
            val user = CymbalUser.fromMap(doc.id, data)
            if (user.isBot) continue
            if (hiddenUsernames.contains(user.username)) continue
            if (user.createdAt == null) continue
            users.add(user)
            if (users.size >= limit) break
        }
        return users
    }

    /**
     * Active Corus Club members, ordered by initial sign-up date (newest
     * first). `clubMemberSince` is set on first activation (trial or
     * purchase) and is preserved across renewals — only cleared on cancel —
     * so this orders by the *original* sign-up, not by renewal time.
     *
     * Avoids needing a composite Firestore index by fetching all active
     * members (`isClubMember == true`) and sorting client-side. The active
     * set is small enough that a single bounded read is fine; we cap the
     * underlying fetch at 500 documents.
     */
    suspend fun fetchClubMembers(limit: Int = 6, excludeIds: Set<String> = emptySet()): List<CymbalUser> {
        // Order by clubMemberSince server-side and fetch only a small buffer
        // beyond the requested count (to absorb client-side exclude/bot/hidden
        // filtering) instead of pulling the entire club and sorting on-device.
        // All active club members have clubMemberSince set, so newest-first
        // order is preserved server-side. Requires the users_v2 composite index
        // on (isClubMember ASC, clubMemberSince DESC).
        val snapshot = firestore.collection("users_v2")
            .whereEqualTo("isClubMember", true)
            .orderBy("clubMemberSince", Query.Direction.DESCENDING)
            .limit((limit + excludeIds.size + 10).toLong())
            .get().await()
        return snapshot.documents
            .mapNotNull { doc ->
                if (excludeIds.contains(doc.id)) return@mapNotNull null
                if (cachedBannedSet.contains(doc.id)) return@mapNotNull null
                val data = doc.data ?: return@mapNotNull null
                val user = CymbalUser.fromMap(doc.id, data)
                if (user.isBot) return@mapNotNull null
                if (hiddenUsernames.contains(user.username)) return@mapNotNull null
                user
            }
            .take(limit)
    }

    // ── Comment Likes ──

    suspend fun likeComment(userId: String, postId: String, commentId: String) = withAuthRetry {
        val likeRef = firestore.collection("posts").document(postId)
            .collection("comments").document(commentId)
            .collection("likes").document(userId)
        val commentRef = firestore.collection("posts").document(postId)
            .collection("comments").document(commentId)

        // Same idempotency pattern as likePost: read existing inside the txn
        // so a repeat call (re-open after the liked-state didn't load, phantom
        // retry, etc.) doesn't double-increment likeCount while the like doc
        // is just overwritten.
        firestore.runTransaction { txn ->
            val existing = txn.get(likeRef)
            if (existing.exists()) return@runTransaction null
            txn.set(likeRef, mapOf("createdAt" to FieldValue.serverTimestamp()))
            txn.update(commentRef, "likeCount", FieldValue.increment(1))
            null
        }.await()
    }

    suspend fun unlikeComment(userId: String, postId: String, commentId: String) = withAuthRetry {
        val likeRef = firestore.collection("posts").document(postId)
            .collection("comments").document(commentId)
            .collection("likes").document(userId)
        val commentRef = firestore.collection("posts").document(postId)
            .collection("comments").document(commentId)

        firestore.runTransaction { txn ->
            val existing = txn.get(likeRef)
            if (!existing.exists()) return@runTransaction null
            txn.delete(likeRef)
            txn.update(commentRef, "likeCount", FieldValue.increment(-1))
            null
        }.await()
    }

    suspend fun isCommentLiked(userId: String, postId: String, commentId: String): Boolean {
        val doc = firestore.collection("posts").document(postId)
            .collection("comments").document(commentId)
            .collection("likes").document(userId)
            .get().await()
        return doc.exists()
    }

    /** Mirrors iOS `checkCommentLikesBatch` — returns the set of commentIds the
     *  user has already liked. Used to populate the liked-state when a
     *  comments sheet opens, so the heart appears correctly without the user
     *  having to tap. */
    suspend fun checkCommentLikesBatch(
        userId: String,
        postId: String,
        commentIds: List<String>,
    ): Set<String> = coroutineScope {
        if (commentIds.isEmpty()) return@coroutineScope emptySet()
        commentIds.map { commentId ->
            async {
                try {
                    if (isCommentLiked(userId, postId, commentId)) commentId else null
                } catch (_: Exception) { null }
            }
        }.awaitAll().filterNotNull().toSet()
    }

    // ── Mutual connections (precomputed) ──

    /** A precomputed mutual-connection row: the candidate user, up to 5 sample
     *  names of friends-of-friends who follow them, and the full overlap count
     *  used for ranking. */
    data class MutualConnection(
        val user: CymbalUser,
        val mutualUsernames: List<String>,
        val mutualCount: Int,
    )

    /** The viewer's persisted onboarding-quiz picks (users_v2/{uid}/private/
     *  tasteSeed — owner-readable per rules, server-written by
     *  getOnboardingTasteMatches). Raw pick maps, already in the shape the
     *  matcher callable accepts. Empty when the user never took the quiz. */
    @Suppress("UNCHECKED_CAST")
    suspend fun fetchMyTasteSeedPicks(uid: String): List<Map<String, Any?>> {
        return try {
            val doc = firestore.collection("users_v2").document(uid)
                .collection("private").document("tasteSeed").get().await()
            (doc.data?.get("picks") as? List<Map<String, Any?>>) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    @Suppress("UNCHECKED_CAST")
    suspend fun fetchPrecomputedMutualConnections(userId: String, limit: Int = 20): List<MutualConnection> {
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
            val count = (data["mutualCount"] as? Number)?.toInt() ?: names.size
            MutualConnection(user, names, count)
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
    ): List<MutualConnection> = coroutineScope {
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
                MutualConnection(user, usernames.toList(), usernames.size)
            }
            .sortedByDescending { it.mutualCount }
            .take(limit)
    }

    // ── Username lookup ──

    suspend fun fetchUserByUsername(username: String): CymbalUser? {
        val snapshot = firestore.collection("users_v2")
            .whereEqualTo("username", username.lowercase())
            .limit(1)
            .get().await()
        val doc = snapshot.documents.firstOrNull() ?: return null
        // A direct username lookup (e.g. paste-link-into-search) for a banned
        // account returns "user not found" instead of the profile.
        val callerUid = firebaseAuth.currentUser?.uid
        if (doc.id != callerUid && cachedBannedSet.contains(doc.id)) return null
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

    suspend fun fetchCommentLikers(
        postId: String,
        commentId: String,
        limit: Int = 20,
        lastTimestamp: Long? = null,
    ): LikersPage {
        var query = firestore.collection("posts").document(postId)
            .collection("comments").document(commentId)
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
        val data = hashMapOf<String, Any?>(
            "reporterId" to reporterId,
            "reason" to reason,
            "details" to details,
            "createdAt" to FieldValue.serverTimestamp(),
        )
        // Canonical fields the backend resolves on (contentType/contentId), plus
        // locators for comment/message reports. Legacy targetUserId/postId kept
        // for the post-report call sites the backend still normalizes.
        contentType?.let { data["contentType"] = it }
        contentId?.let { data["contentId"] = it }
        targetUserId?.let { data["targetUserId"] = it }
        postId?.let { data["postId"] = it }
        commentPostId?.let { data["commentPostId"] = it }
        contentAuthorId?.let { data["contentAuthorId"] = it }
        threadId?.let { data["threadId"] = it }
        firestore.collection("reports").add(data).await()
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
        // Read the legacy main-doc location first (where it still lives today).
        val mainDoc = firestore.collection("users_v2").document(userId).get().await()
        val fromMain = (mainDoc.get("syncedContacts") as? List<String>).orEmpty()
        if (fromMain.isNotEmpty()) return fromMain
        // syncedContacts is being migrated OFF the publicly-readable user doc
        // into the owner-private subcollection (it contains contacts' phone
        // numbers). Once the server move lands, the main-doc field is gone, so
        // fall back to the private copy. Reading both keeps this build working
        // before, during, and after the move. The private doc is owner-only, so
        // this only resolves for the caller's own uid (the only caller); guard
        // against a denied read for safety.
        return try {
            val privDoc = firestore.collection("users_v2").document(userId)
                .collection("private").document("contact").get().await()
            (privDoc.get("syncedContacts") as? List<String>).orEmpty()
        } catch (e: Exception) {
            Log.w("FirestoreDS", "fetchSyncedContacts private fallback failed", e)
            emptyList()
        }
    }

    // NOTE: contact matching used to run a client-side
    // users_v2.whereIn("phoneNumber", ...) query here. That exposed
    // phoneNumber on the publicly-listable user doc, so it was replaced by
    // CloudFunctionsDataSource.findContactMatches (the findContactMatches
    // callable). Do not reintroduce a client-side phoneNumber query.

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

    // ── Favorites ──
    // Private per-user list at users_v2/{userId}/favorites/{targetId},
    // mirroring iOS. Presence of the doc == favorited.

    suspend fun addFavorite(userId: String, targetId: String) {
        firestore.collection("users_v2").document(userId)
            .collection("favorites").document(targetId)
            .set(mapOf("createdAt" to FieldValue.serverTimestamp()))
            .await()
    }

    suspend fun removeFavorite(userId: String, targetId: String) {
        firestore.collection("users_v2").document(userId)
            .collection("favorites").document(targetId)
            .delete().await()
    }

    suspend fun isFavorite(userId: String, targetId: String): Boolean {
        val doc = firestore.collection("users_v2").document(userId)
            .collection("favorites").document(targetId)
            .get().await()
        return doc.exists()
    }
}

/**
 * Pure logic for [FirestoreDataSource.filterBannedAuthors]. Top-level so it
 * can be exercised by unit tests without constructing the singleton or
 * touching Firestore. Items with a `null` author id are preserved (we don't
 * have enough information to ban them).
 */
internal fun <T> filterBannedAuthorsPure(
    items: List<T>,
    bannedIds: Set<String>,
    authorId: (T) -> String?,
): List<T> {
    if (bannedIds.isEmpty()) return items
    return items.filter { authorId(it)?.let(bannedIds::contains) != true }
}
