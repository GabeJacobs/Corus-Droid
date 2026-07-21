package fm.corus.android.data.repository

import fm.corus.android.data.model.CymbalComment
import fm.corus.android.data.model.CymbalPost
import fm.corus.android.data.model.MediaType
import fm.corus.android.data.remote.CloudFunctionsDataSource
import fm.corus.android.data.remote.FirebaseStorageDataSource
import fm.corus.android.data.remote.FirestoreDataSource
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PostRepository @Inject constructor(
    private val cloudFunctions: CloudFunctionsDataSource,
    private val firestoreDataSource: FirestoreDataSource,
    private val storageDataSource: FirebaseStorageDataSource,
    private val subscriptionRepository: SubscriptionRepository,
    private val userRepository: UserRepository,
) {
    // ── In-memory post cache (for instant caption display in sheets) ──

    private val postCache = java.util.concurrent.ConcurrentHashMap<String, CymbalPost>()

    fun getCachedPost(postId: String): CymbalPost? = postCache[postId]

    private fun cachePosts(posts: List<CymbalPost>) {
        for (post in posts) postCache[post.id] = post
    }

    // ── Feed ──

    suspend fun getFeedPage(
        userId: String,
        pageSize: Int = 7,
        lastTimestamp: Long? = null,
        onePerFollower: Boolean = false,
        mediaType: MediaType? = null,
        newReleasesOnly: Boolean = false,
    ): CloudFunctionsDataSource.FeedPage {
        return cloudFunctions.getFeedPage(userId, pageSize, lastTimestamp, onePerFollower, mediaType, newReleasesOnly)
            .also { cachePosts(it.posts) }
    }

    suspend fun getFavoritesFeedPage(
        userId: String,
        pageSize: Int = 7,
        lastTimestamp: Long? = null,
        mediaType: MediaType? = null,
        newReleasesOnly: Boolean = false,
    ): CloudFunctionsDataSource.FeedPage {
        return cloudFunctions.getFavoritesFeedPage(userId, pageSize, lastTimestamp, mediaType, newReleasesOnly)
            .also { cachePosts(it.posts) }
    }

    suspend fun getForYouFeed(
        userId: String,
        pageSize: Int = 7,
        sessionToken: String? = null,
        pageIndex: Int = 0,
        seenPostIds: List<String> = emptyList(),
        mediaType: MediaType? = null,
        newReleasesOnly: Boolean = false,
        scope: String = "trending",
        isRefresh: Boolean = false,
        releaseDecade: Int? = null,
    ): CloudFunctionsDataSource.ForYouFeedPage {
        return cloudFunctions.getForYouFeed(
            userId, pageSize, sessionToken, pageIndex, seenPostIds, mediaType, newReleasesOnly, scope, isRefresh,
            releaseDecade,
        ).also { cachePosts(it.posts) }
    }

    // ── Post Detail ──

    suspend fun getPostDetail(postId: String, userId: String): CymbalPost? {
        return cloudFunctions.getPostDetail(postId, userId)
            ?.also { postCache[it.id] = it }
    }

    // ── Back Cover ──

    suspend fun fetchBackCover(postId: String): String? {
        return cloudFunctions.fetchBackCover(postId)
    }

    // ── Profile posts ──

    suspend fun getProfilePosts(
        userId: String,
        viewerId: String,
        limit: Int = 30,
        lastTimestamp: Long? = null,
        mediaType: String? = null,
    ): List<CymbalPost> {
        return cloudFunctions.getProfilePosts(userId, viewerId, limit, lastTimestamp, mediaType)
            .also { cachePosts(it) }
    }

    suspend fun getProfileData(
        userId: String,
        pageSize: Int = 15,
        mediaType: String? = null,
    ): CloudFunctionsDataSource.ProfileData {
        return cloudFunctions.getProfileData(userId, pageSize, mediaType)
            .also { cachePosts(it.posts) }
    }

    // ── Content-specific feeds ──

    suspend fun fetchSongPostsFromCloud(
        trackId: String,
        spotifyURI: String? = null,
        isrc: String? = null,
        trackName: String? = null,
        artistName: String? = null,
        pageSize: Int = 15,
        beforeMs: Long? = null,
    ): CloudFunctionsDataSource.SongPostsPage {
        return cloudFunctions.fetchSongPostsFromCloud(
            trackId = trackId,
            spotifyURI = spotifyURI,
            isrc = isrc,
            trackName = trackName,
            artistName = artistName,
            pageSize = pageSize,
            beforeMs = beforeMs,
        )
    }

    suspend fun fetchMoviePostsFromCloud(
        movieId: String,
        movieTitle: String? = null,
        pageSize: Int = 15,
        beforeMs: Long? = null,
    ): CloudFunctionsDataSource.MoviePostsPage {
        return cloudFunctions.fetchMoviePostsFromCloud(movieId, movieTitle, pageSize, beforeMs)
    }

    suspend fun getHashtagPosts(
        hashtag: String,
        pageSize: Int = 15,
        beforeMs: Long? = null,
    ): CloudFunctionsDataSource.HashtagPostsPage {
        return cloudFunctions.getHashtagPosts(hashtag, pageSize, beforeMs)
    }

    // ── First Poster Check ──

    suspend fun fetchUniquePosterCountByTrack(track: fm.corus.android.data.model.CymbalTrack): Int {
        return firestoreDataSource.fetchUniquePosterCountByTrack(track)
    }

    suspend fun fetchUniquePosterCountByMovie(movieId: String): Int {
        return firestoreDataSource.fetchUniquePosterCountByMovie(movieId)
    }

    // ── Create Post ──
    //
    // Server-driven path: the `createPost` Cloud Function callable atomically
    // validates the rolling 24h post limit and writes the doc. Over-limit
    // surfaces as PostLimitReachedException (caller maps to paywall);
    // RESOURCE_EXHAUSTED is no longer a silent-delete window.
    //
    // Voice note flow: we used to upload after the doc was created (so the
    // server-driven cleanup path could find a postId-keyed blob to delete on
    // failure). Through the callable we have to upload first because the doc
    // ID is server-assigned; we key the blob to a fresh UUID instead. Voice
    // notes uploaded for posts that the server rejects are acceptable garbage
    // — small files, rare path, can be swept by a follow-up cleanup job.

    suspend fun createPost(
        payload: Map<String, Any?>,
        voiceNoteData: ByteArray? = null,
    ): CloudFunctionsDataSource.CreatePostResult {
        val voiceNoteURL: String? = if (voiceNoteData != null) {
            val uid = payload["__voiceUserId"] as? String
                ?: throw IllegalArgumentException("voiceNoteData requires __voiceUserId in payload")
            val voiceId = java.util.UUID.randomUUID().toString()
            storageDataSource.uploadVoiceNote(uid, voiceId, voiceNoteData)
        } else {
            null
        }
        val effective = payload.toMutableMap().apply {
            remove("__voiceUserId")
            if (voiceNoteURL != null) put("voiceNoteURL", voiceNoteURL)
        }
        val result = cloudFunctions.createPost(effective)
        // A new post changes the viewer's taste, so the suggested + taste-matches
        // rails' cached first pages are now stale — drop both so the next open
        // fetches fresh. Mirrors iOS ComposeView.invalidateSuggestedMatchesCache().
        userRepository.invalidateTasteAndSuggestedMatchesCache()
        return result
    }

    suspend fun updateCaption(postId: String, caption: String, hashtags: List<String>) {
        firestoreDataSource.updateCaption(postId, caption, hashtags)
    }

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
        firestoreDataSource.createNotification(type, fromUserId, toUserId, postId, postAlbumArtURL, commentText, commentId, attachmentType)
    }

    suspend fun deletePost(postId: String, userId: String) {
        val cachedTimestamp = postCache[postId]?.timestamp
        firestoreDataSource.deletePost(postId, userId)
        postCache.remove(postId)
        subscriptionRepository.decrementPostCount(cachedTimestamp)
        // Deleting a post also changes the viewer's taste — drop both rails'
        // cached first pages. Mirrors iOS DatabaseService.deletePost, which calls
        // invalidateSuggestedMatchesCache() (which in turn invalidates taste).
        userRepository.invalidateTasteAndSuggestedMatchesCache()
    }

    // ── Engagement ──

    suspend fun likePost(userId: String, postId: String) {
        cloudFunctions.likePost(postId)
    }

    suspend fun unlikePost(userId: String, postId: String) {
        cloudFunctions.unlikePost(postId)
    }

    suspend fun isPostLiked(userId: String, postId: String): Boolean {
        return firestoreDataSource.isPostLiked(userId, postId)
    }

    /** Save via the `savePost` cloud callable. Server enforces the cap and
     *  returns the new savesCount. Throws SaveCapReachedException on cap-hit. */
    suspend fun savePost(userId: String, postId: String): CloudFunctionsDataSource.SavePostResult {
        return cloudFunctions.savePost(postId)
    }

    /** Unsave via the `unsavePost` cloud callable. Returns new savesCount. */
    suspend fun unsavePost(userId: String, postId: String): Int {
        return cloudFunctions.unsavePost(postId)
    }

    suspend fun reconcileSavesCount(): Int = cloudFunctions.reconcileSavesCount()

    suspend fun isPostSaved(userId: String, postId: String): Boolean {
        return firestoreDataSource.isPostSaved(userId, postId)
    }

    // ── Engagement Helpers ──

    suspend fun likeComment(userId: String, postId: String, commentId: String) {
        cloudFunctions.likeComment(postId, commentId)
    }

    suspend fun unlikeComment(userId: String, postId: String, commentId: String) {
        cloudFunctions.unlikeComment(postId, commentId)
    }

    suspend fun isCommentLiked(userId: String, postId: String, commentId: String): Boolean {
        return firestoreDataSource.isCommentLiked(userId, postId, commentId)
    }

    suspend fun checkCommentLikesBatch(
        userId: String,
        postId: String,
        commentIds: List<String>,
    ): Set<String> {
        return firestoreDataSource.checkCommentLikesBatch(userId, postId, commentIds)
    }

    /**
     * Real-time listener on a post's comments subcollection. Mirrors iOS so an
     * open comments screen keeps comment like counts + heart state fresh.
     * Caller owns the returned registration and must `.remove()` it.
     */
    fun listenForCommentChanges(
        postId: String,
        onChange: () -> Unit,
    ): com.google.firebase.firestore.ListenerRegistration {
        return firestoreDataSource.listenForCommentChanges(postId, onChange)
    }

    suspend fun fetchPostLikers(postId: String, limit: Int = 20, lastTimestamp: Long? = null): fm.corus.android.data.remote.FirestoreDataSource.LikersPage {
        return firestoreDataSource.fetchPostLikers(postId, limit, lastTimestamp)
    }

    suspend fun fetchCommentLikers(
        postId: String,
        commentId: String,
        limit: Int = 20,
        lastTimestamp: Long? = null,
    ): fm.corus.android.data.remote.FirestoreDataSource.LikersPage {
        return firestoreDataSource.fetchCommentLikers(postId, commentId, limit, lastTimestamp)
    }

    // ── Comments ──

    suspend fun getComments(postId: String, limit: Int = 100, lastTimestamp: Long? = null): List<CymbalComment> {
        return cloudFunctions.getComments(postId, limit, lastTimestamp)
    }

    suspend fun getReplies(commentId: String, postId: String, limit: Int = 50, lastTimestamp: Long? = null): List<CymbalComment> {
        return cloudFunctions.getReplies(commentId, postId, limit, lastTimestamp)
    }

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
        return firestoreDataSource.addComment(
            postId, userId, text, parentCommentId, replyToUserId, gifURL, attachedSong, attachedFilm,
            attachedArtist, attachedAlbum, attachedDirector
        )
    }

    suspend fun getCommentParentId(postId: String, commentId: String): String? {
        return firestoreDataSource.getCommentParentId(postId, commentId)
    }

    suspend fun editComment(postId: String, commentId: String, newText: String) {
        firestoreDataSource.editComment(postId, commentId, newText)
    }

    suspend fun deleteComment(postId: String, commentId: String) {
        firestoreDataSource.deleteComment(postId, commentId)
    }
}
