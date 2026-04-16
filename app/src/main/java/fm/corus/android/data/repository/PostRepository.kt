package fm.corus.android.data.repository

import fm.corus.android.data.model.CymbalComment
import fm.corus.android.data.model.CymbalPost
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
    ): CloudFunctionsDataSource.FeedPage {
        return cloudFunctions.getFeedPage(userId, pageSize, lastTimestamp, onePerFollower)
            .also { cachePosts(it.posts) }
    }

    // ── Post Detail ──

    suspend fun getPostDetail(postId: String, userId: String): CymbalPost? {
        return cloudFunctions.getPostDetail(postId, userId)
            ?.also { postCache[it.id] = it }
    }

    // ── Profile posts ──

    suspend fun getProfilePosts(userId: String, viewerId: String, limit: Int = 30, lastTimestamp: Long? = null): List<CymbalPost> {
        return cloudFunctions.getProfilePosts(userId, viewerId, limit, lastTimestamp)
            .also { cachePosts(it) }
    }

    // ── Content-specific feeds ──

    suspend fun fetchSongPostsFromCloud(
        trackId: String,
        spotifyURI: String? = null,
        trackName: String? = null,
        artistName: String? = null,
        pageSize: Int = 15,
        beforeMs: Long? = null,
    ): CloudFunctionsDataSource.SongPostsPage {
        return cloudFunctions.fetchSongPostsFromCloud(trackId, spotifyURI, trackName, artistName, pageSize, beforeMs)
    }

    suspend fun fetchMoviePostsFromCloud(
        movieId: String,
        movieTitle: String? = null,
        pageSize: Int = 15,
        beforeMs: Long? = null,
    ): CloudFunctionsDataSource.MoviePostsPage {
        return cloudFunctions.fetchMoviePostsFromCloud(movieId, movieTitle, pageSize, beforeMs)
    }

    suspend fun getHashtagPosts(hashtag: String, userId: String, limit: Int = 30, lastTimestamp: Long? = null): List<CymbalPost> {
        return cloudFunctions.getHashtagPosts(hashtag, userId, limit, lastTimestamp)
    }

    // ── First Poster Check ──

    suspend fun fetchUniquePosterCountByTrack(track: fm.corus.android.data.model.CymbalTrack): Int {
        return firestoreDataSource.fetchUniquePosterCountByTrack(track)
    }

    suspend fun fetchUniquePosterCountByMovie(movieId: String): Int {
        return firestoreDataSource.fetchUniquePosterCountByMovie(movieId)
    }

    // ── Create Post ──

    suspend fun createPost(userId: String, data: Map<String, Any>, voiceNoteData: ByteArray? = null): String {
        val postId = firestoreDataSource.createPost(userId, data)
        if (voiceNoteData != null) {
            val url = storageDataSource.uploadVoiceNote(postId, voiceNoteData)
            firestoreDataSource.updatePostVoiceNoteURL(postId, url)
        }
        return postId
    }

    suspend fun repostPost(userId: String, post: CymbalPost): String {
        return firestoreDataSource.createRepost(userId, post)
    }

    suspend fun updateCaption(postId: String, caption: String) {
        firestoreDataSource.updateCaption(postId, caption)
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
        firestoreDataSource.createNotification(type, fromUserId, toUserId, postId, postAlbumArtURL, commentText, commentId)
    }

    suspend fun deletePost(postId: String, userId: String) {
        firestoreDataSource.deletePost(postId, userId)
    }

    // ── Engagement ──

    suspend fun likePost(userId: String, postId: String) {
        firestoreDataSource.likePost(userId, postId)
    }

    suspend fun unlikePost(userId: String, postId: String) {
        firestoreDataSource.unlikePost(userId, postId)
    }

    suspend fun isPostLiked(userId: String, postId: String): Boolean {
        return firestoreDataSource.isPostLiked(userId, postId)
    }

    suspend fun savePost(userId: String, postId: String) {
        firestoreDataSource.savePost(userId, postId)
    }

    suspend fun unsavePost(userId: String, postId: String) {
        firestoreDataSource.unsavePost(userId, postId)
    }

    suspend fun isPostSaved(userId: String, postId: String): Boolean {
        return firestoreDataSource.isPostSaved(userId, postId)
    }

    // ── Engagement Helpers ──

    suspend fun likeComment(userId: String, postId: String, commentId: String) {
        firestoreDataSource.likeComment(userId, postId, commentId)
    }

    suspend fun unlikeComment(userId: String, postId: String, commentId: String) {
        firestoreDataSource.unlikeComment(userId, postId, commentId)
    }

    suspend fun isCommentLiked(userId: String, postId: String, commentId: String): Boolean {
        return firestoreDataSource.isCommentLiked(userId, postId, commentId)
    }

    suspend fun fetchPostLikers(postId: String, limit: Int = 20, lastTimestamp: Long? = null): fm.corus.android.data.remote.FirestoreDataSource.LikersPage {
        return firestoreDataSource.fetchPostLikers(postId, limit, lastTimestamp)
    }

    // ── Comments ──

    suspend fun getComments(postId: String, limit: Int = 100, lastTimestamp: Long? = null): List<CymbalComment> {
        return cloudFunctions.getComments(postId, limit, lastTimestamp)
    }

    suspend fun getReplies(commentId: String, postId: String, limit: Int = 50, lastTimestamp: Long? = null): List<CymbalComment> {
        return cloudFunctions.getReplies(commentId, postId, limit, lastTimestamp)
    }

    suspend fun addComment(postId: String, userId: String, text: String, parentCommentId: String? = null, replyToUserId: String? = null, gifURL: String? = null): String {
        return firestoreDataSource.addComment(postId, userId, text, parentCommentId, replyToUserId, gifURL)
    }

    suspend fun editComment(postId: String, commentId: String, newText: String) {
        firestoreDataSource.editComment(postId, commentId, newText)
    }

    suspend fun deleteComment(postId: String, commentId: String) {
        firestoreDataSource.deleteComment(postId, commentId)
    }
}
