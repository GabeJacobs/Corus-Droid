package fm.corus.android.data.remote

import com.google.firebase.functions.FirebaseFunctions
import fm.corus.android.data.model.*
import kotlinx.coroutines.tasks.await
import java.util.Date
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Wraps all Firebase callable Cloud Functions.
 * Mirrors the iOS DatabaseService's cloud function calls.
 */
@Singleton
class CloudFunctionsDataSource @Inject constructor(
    private val functions: FirebaseFunctions,
) {
    // ── Feed ──

    data class FeedPage(
        val posts: List<CymbalPost>,
        val hasMore: Boolean,
        val uniquePosterCount: Int = 0,
    )

    @Suppress("UNCHECKED_CAST")
    suspend fun getFeedPage(
        userId: String,
        pageSize: Int = 7,
        lastTimestamp: Long? = null,
        onePerFollower: Boolean = false,
    ): FeedPage {
        val params = mutableMapOf<String, Any>(
            "userId" to userId,
            "pageSize" to pageSize,
            "onePerFollower" to onePerFollower,
        )
        lastTimestamp?.let { params["beforeMs"] = it }

        val result = functions.getHttpsCallable("getFeedPage").call(params).await()
        val data = result.getData() as? Map<String, Any?> ?: return FeedPage(emptyList(), false)

        val postsData = data["posts"] as? List<Map<String, Any?>> ?: emptyList()
        val posts = postsData.map { CymbalPost.fromCloudData(it) }
        // Match iOS fallback: if hasMore not returned, assume more exist when page is full
        val hasMore = data["hasMore"] as? Boolean ?: (posts.size >= pageSize)
        val uniquePosterCount = (data["uniquePosterCount"] as? Number)?.toInt() ?: 0

        return FeedPage(posts, hasMore, uniquePosterCount)
    }

    // ── Post Detail ──

    @Suppress("UNCHECKED_CAST")
    suspend fun getPostDetail(postId: String, userId: String): CymbalPost? {
        val result = functions.getHttpsCallable("getPostDetail").call(
            mapOf("postId" to postId, "userId" to userId)
        ).await()
        val outerData = result.getData() as? Map<String, Any?> ?: return null
        val postData = outerData["post"] as? Map<String, Any?> ?: return null
        return CymbalPost.fromCloudData(postData)
    }

    // ── Comments ──

    @Suppress("UNCHECKED_CAST")
    suspend fun getComments(postId: String, limit: Int = 100, lastTimestamp: Long? = null): List<CymbalComment> {
        val params = mutableMapOf<String, Any>("postId" to postId, "limit" to limit)
        lastTimestamp?.let { params["lastTimestamp"] = it }
        val result = functions.getHttpsCallable("getComments").call(params).await()
        val data = result.getData() as? Map<String, Any?> ?: return emptyList()
        val comments = data["comments"] as? List<Map<String, Any?>> ?: return emptyList()
        return comments.map { CymbalComment.fromMap(it) }
    }

    @Suppress("UNCHECKED_CAST")
    suspend fun getReplies(commentId: String, postId: String, limit: Int = 50, lastTimestamp: Long? = null): List<CymbalComment> {
        val params = mutableMapOf<String, Any>(
            "commentId" to commentId, "postId" to postId, "limit" to limit
        )
        lastTimestamp?.let { params["lastTimestamp"] = it }
        val result = functions.getHttpsCallable("getReplies").call(params).await()
        val data = result.getData() as? Map<String, Any?> ?: return emptyList()
        val replies = data["replies"] as? List<Map<String, Any?>> ?: return emptyList()
        return replies.map { CymbalComment.fromMap(it) }
    }

    // ── Profile ──

    @Suppress("UNCHECKED_CAST")
    suspend fun getProfileData(userId: String, viewerId: String): Map<String, Any?>? {
        val result = functions.getHttpsCallable("getProfileData").call(
            mapOf("userId" to userId, "viewerId" to viewerId)
        ).await()
        return result.getData() as? Map<String, Any?>
    }

    @Suppress("UNCHECKED_CAST")
    suspend fun getProfilePosts(userId: String, viewerId: String, limit: Int = 30, lastTimestamp: Long? = null): List<CymbalPost> {
        val params = mutableMapOf<String, Any>(
            "userId" to userId, "viewerId" to viewerId, "pageSize" to limit
        )
        lastTimestamp?.let { params["beforeMs"] = it }
        val result = functions.getHttpsCallable("getProfilePosts").call(params).await()
        val data = result.getData() as? Map<String, Any?> ?: return emptyList()
        val posts = data["posts"] as? List<Map<String, Any?>> ?: return emptyList()
        return posts.map { CymbalPost.fromCloudData(it) }
    }

    @Suppress("UNCHECKED_CAST")
    suspend fun getLikedPosts(userId: String, viewerId: String, limit: Int = 30, offset: Int = 0): List<CymbalPost> {
        val params = mutableMapOf<String, Any>(
            "userId" to userId, "viewerId" to viewerId, "limit" to limit, "offset" to offset
        )
        val result = functions.getHttpsCallable("getLikedPosts").call(params).await()
        val data = result.getData() as? Map<String, Any?> ?: return emptyList()
        val posts = data["posts"] as? List<Map<String, Any?>> ?: return emptyList()
        return posts.map { CymbalPost.fromCloudData(it) }
    }

    @Suppress("UNCHECKED_CAST")
    suspend fun getSavedPosts(userId: String, limit: Int = 30, offset: Int = 0): List<CymbalPost> {
        val params = mutableMapOf<String, Any>("userId" to userId, "limit" to limit, "offset" to offset)
        val result = functions.getHttpsCallable("getSavedPosts").call(params).await()
        val data = result.getData() as? Map<String, Any?> ?: return emptyList()
        val posts = data["posts"] as? List<Map<String, Any?>> ?: return emptyList()
        return posts.map { CymbalPost.fromCloudData(it) }
    }

    // ── Song / Movie Posts ──

    data class SongPostsPage(
        val posts: List<CymbalPost>,
        val uniquePosterCount: Int?,
        val firstPosterId: String?,
    )

    data class MoviePostsPage(
        val posts: List<CymbalPost>,
        val uniquePosterCount: Int?,
        val firstPosterId: String?,
    )

    @Suppress("UNCHECKED_CAST")
    suspend fun fetchSongPostsFromCloud(
        trackId: String,
        spotifyURI: String? = null,
        trackName: String? = null,
        artistName: String? = null,
        pageSize: Int = 15,
        beforeMs: Long? = null,
    ): SongPostsPage {
        val params = mutableMapOf<String, Any>("trackId" to trackId, "pageSize" to pageSize)
        if (!spotifyURI.isNullOrBlank()) params["spotifyURI"] = spotifyURI
        if (!trackName.isNullOrBlank()) params["trackName"] = trackName
        if (!artistName.isNullOrBlank()) params["artistName"] = artistName
        beforeMs?.let { params["beforeMs"] = it }

        val result = functions.getHttpsCallable("getSongPosts").call(params).await()
        val data = result.getData() as? Map<String, Any?> ?: return SongPostsPage(emptyList(), null, null)

        val postsData = data["posts"] as? List<Map<String, Any?>> ?: emptyList()
        val uniquePosterCount = (data["uniquePosterCount"] as? Number)?.toInt()
        val firstPosterId = data["firstPosterId"] as? String

        var posts = postsData.map { CymbalPost.fromCloudData(it) }
        if (firstPosterId != null) {
            posts = posts.map { post ->
                if (post.user.id == firstPosterId) post.copy(isFirstPoster = true) else post
            }
        }

        return SongPostsPage(posts, uniquePosterCount, firstPosterId)
    }

    @Suppress("UNCHECKED_CAST")
    suspend fun fetchMoviePostsFromCloud(
        movieId: String,
        movieTitle: String? = null,
        pageSize: Int = 15,
        beforeMs: Long? = null,
    ): MoviePostsPage {
        val params = mutableMapOf<String, Any>("movieId" to movieId, "pageSize" to pageSize)
        if (!movieTitle.isNullOrBlank()) params["movieTitle"] = movieTitle
        beforeMs?.let { params["beforeMs"] = it }

        val result = functions.getHttpsCallable("getMoviePosts").call(params).await()
        val data = result.getData() as? Map<String, Any?> ?: return MoviePostsPage(emptyList(), null, null)

        val postsData = data["posts"] as? List<Map<String, Any?>> ?: emptyList()
        val uniquePosterCount = (data["uniquePosterCount"] as? Number)?.toInt()
        val firstPosterId = data["firstPosterId"] as? String

        var posts = postsData.map { CymbalPost.fromCloudData(it) }
        if (firstPosterId != null) {
            posts = posts.map { post ->
                if (post.user.id == firstPosterId) post.copy(isFirstPoster = true) else post
            }
        }

        return MoviePostsPage(posts, uniquePosterCount, firstPosterId)
    }

    @Suppress("UNCHECKED_CAST")
    suspend fun getHashtagPosts(hashtag: String, userId: String, limit: Int = 30, lastTimestamp: Long? = null): List<CymbalPost> {
        val params = mutableMapOf<String, Any>("hashtag" to hashtag, "userId" to userId, "limit" to limit)
        lastTimestamp?.let { params["lastTimestamp"] = it }
        val result = functions.getHttpsCallable("getHashtagPosts").call(params).await()
        val data = result.getData() as? Map<String, Any?> ?: return emptyList()
        val posts = data["posts"] as? List<Map<String, Any?>> ?: return emptyList()
        return posts.map { CymbalPost.fromCloudData(it) }
    }

    // ── Notifications ──

    @Suppress("UNCHECKED_CAST")
    suspend fun getNotifications(userId: String, limit: Int = 15, lastTimestamp: Long? = null): List<CymbalNotification> {
        val params = mutableMapOf<String, Any>("userId" to userId, "limit" to limit)
        lastTimestamp?.let { params["lastTimestamp"] = it }
        val result = functions.getHttpsCallable("getNotifications").call(params).await()
        val data = result.getData() as? Map<String, Any?> ?: return emptyList()
        val notifications = data["notifications"] as? List<Map<String, Any?>> ?: return emptyList()
        return notifications.map {
            CymbalNotification.fromMap(it["id"] as? String ?: "", it)
        }
    }

    // ── Messaging ──

    @Suppress("UNCHECKED_CAST")
    suspend fun listThreads(userId: String): List<CymbalThread> {
        val result = functions.getHttpsCallable("listThreads").call(
            mapOf("userId" to userId)
        ).await()
        val data = result.getData() as? Map<String, Any?> ?: return emptyList()
        val threads = data["threads"] as? List<Map<String, Any?>> ?: return emptyList()
        return threads.map { CymbalThread.fromMap(it["id"] as? String ?: "", it) }
    }

    @Suppress("UNCHECKED_CAST")
    suspend fun listMessages(threadId: String, limit: Int = 50, lastTimestamp: Long? = null): List<CymbalMessage> {
        val params = mutableMapOf<String, Any>("threadId" to threadId, "limit" to limit)
        lastTimestamp?.let { params["lastTimestamp"] = it }
        val result = functions.getHttpsCallable("listMessages").call(params).await()
        val data = result.getData() as? Map<String, Any?> ?: return emptyList()
        val messages = data["messages"] as? List<Map<String, Any?>> ?: return emptyList()
        return messages.map { CymbalMessage.fromMap(it["id"] as? String ?: "", it) }
    }

    suspend fun sendMessage(threadId: String, fromUserId: String, text: String, type: String = "text", mediaURL: String? = null, sharedPostId: String? = null, trackName: String? = null, artistName: String? = null, albumArtURL: String? = null, spotifyURL: String? = null, movieTitle: String? = null, directorName: String? = null, posterURL: String? = null, tmdbWebURL: String? = null, replyToMessageId: String? = null, replyToText: String? = null, replyToUserId: String? = null) {
        val params = mutableMapOf<String, Any>(
            "threadId" to threadId,
            "fromUserId" to fromUserId,
            "text" to (text),
            "type" to type,
        )
        mediaURL?.let { params["mediaURL"] = it }
        sharedPostId?.let { params["sharedPostId"] = it }
        trackName?.let { params["trackName"] = it }
        artistName?.let { params["artistName"] = it }
        albumArtURL?.let { params["albumArtURL"] = it }
        spotifyURL?.let { params["spotifyURL"] = it }
        movieTitle?.let { params["movieTitle"] = it }
        directorName?.let { params["directorName"] = it }
        posterURL?.let { params["posterURL"] = it }
        tmdbWebURL?.let { params["tmdbWebURL"] = it }
        replyToMessageId?.let { params["replyToMessageId"] = it }
        replyToText?.let { params["replyToText"] = it }
        replyToUserId?.let { params["replyToUserId"] = it }
        functions.getHttpsCallable("sendMessage").call(params).await()
    }

    suspend fun toggleMessageReaction(threadId: String, messageId: String, emoji: String) {
        functions.getHttpsCallable("toggleMessageReaction").call(
            mapOf("threadId" to threadId, "messageId" to messageId, "emoji" to emoji)
        ).await()
    }

    @Suppress("UNCHECKED_CAST")
    suspend fun getOrCreateThread(userId: String, otherUserId: String): String {
        val result = functions.getHttpsCallable("getOrCreateThread").call(
            mapOf("userId" to userId, "otherUserId" to otherUserId)
        ).await()
        val data = result.getData() as? Map<String, Any?> ?: throw Exception("Failed to create thread")
        return data["threadId"] as? String ?: throw Exception("No threadId returned")
    }

    suspend fun markThreadRead(threadId: String, userId: String) {
        functions.getHttpsCallable("markThreadRead").call(
            mapOf("threadId" to threadId, "userId" to userId)
        ).await()
    }

    // ── GIF Search (Giphy via Cloud Function) ──

    data class GifSearchResult(
        val results: List<TenorGif>,
        val next: String,
    )

    @Suppress("UNCHECKED_CAST")
    suspend fun searchGifs(query: String = "", limit: Int = 20, pos: String = ""): GifSearchResult {
        val params = mutableMapOf<String, Any>("limit" to limit)
        if (query.isNotBlank()) params["query"] = query
        if (pos.isNotBlank()) params["pos"] = pos

        val result = functions.getHttpsCallable("searchTenorGifs").call(params).await()
        val data = result.getData() as? Map<String, Any?> ?: emptyMap()
        val rawResults = data["results"] as? List<Map<String, Any?>> ?: emptyList()

        return GifSearchResult(
            results = rawResults.map { r ->
                TenorGif(
                    id = r["id"] as? String ?: "",
                    tinyGifURL = r["tinyGifURL"] as? String ?: "",
                    gifURL = r["gifURL"] as? String ?: "",
                    tinyGifWidth = (r["tinyGifWidth"] as? Number)?.toInt() ?: 0,
                    tinyGifHeight = (r["tinyGifHeight"] as? Number)?.toInt() ?: 0,
                )
            },
            next = data["next"] as? String ?: "",
        )
    }

    // ── Spotify ──

    @Suppress("UNCHECKED_CAST")
    suspend fun spotifySearch(query: String, offset: Int = 0, limit: Int = 20, market: String = "US"): Map<String, Any?> {
        val result = functions.getHttpsCallable("spotifySearch").call(
            mapOf("query" to query, "offset" to offset, "limit" to limit, "market" to market)
        ).await()
        return result.getData() as? Map<String, Any?> ?: emptyMap()
    }

    @Suppress("UNCHECKED_CAST")
    suspend fun spotifyGetTrack(trackId: String): Map<String, Any?> {
        val result = functions.getHttpsCallable("spotifyGetTrack").call(
            mapOf("trackId" to trackId)
        ).await()
        return result.getData() as? Map<String, Any?> ?: emptyMap()
    }

    // ── Social ──

    suspend fun blockUser(userId: String, targetUserId: String) {
        functions.getHttpsCallable("blockUser").call(
            mapOf("userId" to userId, "targetUserId" to targetUserId)
        ).await()
    }

    suspend fun unblockUser(userId: String, targetUserId: String) {
        functions.getHttpsCallable("unblockUser").call(
            mapOf("userId" to userId, "targetUserId" to targetUserId)
        ).await()
    }

    // ── Mute ──

    suspend fun muteUser(targetUserId: String) {
        functions.getHttpsCallable("muteUser").call(
            mapOf("targetUserId" to targetUserId)
        ).await()
    }

    suspend fun unmuteUser(targetUserId: String) {
        functions.getHttpsCallable("unmuteUser").call(
            mapOf("targetUserId" to targetUserId)
        ).await()
    }

    // ── Suggestions ──

    @Suppress("UNCHECKED_CAST")
    suspend fun getSuggestedUsers(userId: String, limit: Int = 50, includeFollowing: Boolean = true): List<SuggestedUserMatch> {
        val result = functions.getHttpsCallable("getSuggestedUsers").call(
            mapOf("currentUserId" to userId, "limit" to limit, "includeFollowing" to includeFollowing)
        ).await()
        val data = result.getData() as? Map<String, Any?> ?: return emptyList()
        val rows = data["users"] as? List<Map<String, Any?>> ?: return emptyList()
        return parseUserRows(rows)
    }

    @Suppress("UNCHECKED_CAST")
    suspend fun getBotSuggestions(userId: String, limit: Int = 30, botType: String? = null): List<SuggestedUserMatch> {
        val params = mutableMapOf<String, Any>(
            "currentUserId" to userId,
            "limit" to limit,
            "botsOnly" to true,
        )
        botType?.let { params["botType"] = it }
        val result = functions.getHttpsCallable("getSuggestedUsers").call(params).await()
        val data = result.getData() as? Map<String, Any?> ?: return emptyList()
        val rows = data["users"] as? List<Map<String, Any?>> ?: return emptyList()
        return parseUserRows(rows)
    }

    @Suppress("UNCHECKED_CAST")
    private fun parseUserRows(rows: List<Map<String, Any?>>): List<SuggestedUserMatch> {
        return rows.mapNotNull { row ->
            val uid = row["id"] as? String ?: return@mapNotNull null
            val user = CymbalUser.fromMap(uid, row)

            // Match data is inline in the row (not nested under a "matchData" key)
            val score = (row["similarityScore"] as? Number)?.toDouble() ?: 0.0
            val sharedPostedTracks = (row["sharedPostedTracks"] as? Number)?.toInt() ?: 0
            val sharedLikedTracks = (row["sharedLikedTracks"] as? Number)?.toInt() ?: 0
            val sharedArtists = (row["sharedArtists"] as? Number)?.toInt() ?: 0
            val adjacentArtists = (row["adjacentArtists"] as? Number)?.toInt() ?: 0
            val sharedPostedMovies = (row["sharedPostedMovies"] as? Number)?.toInt() ?: 0
            val sharedLikedMovies = (row["sharedLikedMovies"] as? Number)?.toInt() ?: 0
            val sharedDirectors = (row["sharedDirectors"] as? Number)?.toInt() ?: 0
            val sharedHashtags = (row["sharedHashtags"] as? Number)?.toInt() ?: 0
            val mutualFollows = (row["mutualFollows"] as? Number)?.toInt() ?: 0

            val trackPreviews = (row["sharedTrackPreviews"] as? List<Map<String, Any?>>)?.mapNotNull { dict ->
                val trackId = dict["trackId"] as? String ?: ""
                val albumArt = dict["albumArtURL"] as? String ?: ""
                val posterArt = dict["posterURL"] as? String ?: ""
                if (trackId.isEmpty() && albumArt.isEmpty() && posterArt.isEmpty()) return@mapNotNull null
                SharedTrackPreview(
                    trackId = trackId,
                    trackName = dict["trackName"] as? String ?: "",
                    artistName = dict["artistName"] as? String ?: "",
                    albumArtURL = dict["albumArtURL"] as? String,
                    posterURL = dict["posterURL"] as? String,
                    isMovie = dict["isMovie"] as? Boolean ?: false,
                )
            } ?: emptyList()

            val moviePreviews = (row["sharedMoviePreviews"] as? List<Map<String, Any?>>)?.mapNotNull { dict ->
                val movieId = dict["movieId"] as? String ?: return@mapNotNull null
                if (movieId.isEmpty()) return@mapNotNull null
                SharedMoviePreview(
                    movieId = movieId,
                    movieTitle = dict["movieTitle"] as? String ?: "",
                    directorName = dict["directorName"] as? String ?: "",
                    posterURL = dict["posterURL"] as? String,
                )
            } ?: emptyList()

            val hasMatchData = sharedPostedTracks + sharedLikedTracks > 0 || sharedArtists > 0 ||
                adjacentArtists > 0 || sharedPostedMovies + sharedLikedMovies > 0 ||
                sharedDirectors > 0 || score > 0 || trackPreviews.isNotEmpty() || moviePreviews.isNotEmpty()

            val matchData = if (hasMatchData) {
                MusicMatchData(
                    similarityScore = score,
                    sharedPostedTracks = sharedPostedTracks,
                    sharedLikedTracks = sharedLikedTracks,
                    sharedArtists = sharedArtists,
                    adjacentArtists = adjacentArtists,
                    sharedPostedMovies = sharedPostedMovies,
                    sharedLikedMovies = sharedLikedMovies,
                    sharedDirectors = sharedDirectors,
                    sharedHashtags = sharedHashtags,
                    mutualFollows = mutualFollows,
                    sharedTrackPreviews = trackPreviews,
                    sharedMoviePreviews = moviePreviews,
                )
            } else null

            // Mutual names are inline in the row
            val mutualNames = (row["mutualNames"] as? List<*>)?.filterIsInstance<String>() ?: emptyList()
            val reason = if (mutualNames.isNotEmpty()) SuggestionReason(mutualNames) else null

            SuggestedUserMatch(user = user, matchData = matchData, suggestionReason = reason)
        }
    }

    // ── Contacts ──

    suspend fun notifyContactsOnSync() {
        functions.getHttpsCallable("notifyContactsOnSync").call(emptyMap<String, Any>()).await()
    }

    // ── Account ──

    suspend fun deleteAllUserData() {
        functions.getHttpsCallable("deleteAllUserData").call().await()
    }

    // ── Subscription ──

    suspend fun syncClubMemberStatus(userId: String, isClubMember: Boolean) {
        functions.getHttpsCallable("syncClubMemberStatus").call(
            mapOf("userId" to userId, "isClubMember" to isClubMember)
        ).await()
    }

    // ── Playlist ──

    data class PlaylistResult(
        val playlistURI: String,
        val playlistWebURL: String,
        val cached: Boolean,
    )

    @Suppress("UNCHECKED_CAST")
    suspend fun generateProfilePlaylist(userId: String): PlaylistResult {
        val result = functions.getHttpsCallable("generateProfilePlaylist").call(
            mapOf("userId" to userId)
        ).await()
        val data = result.getData() as? Map<String, Any?> ?: throw Exception("Invalid response")
        if (data["error"] != null) {
            val msg = data["message"] as? String ?: "Unknown error"
            throw Exception(msg)
        }
        return PlaylistResult(
            playlistURI = data["playlistURI"] as? String ?: throw Exception("Missing playlistURI"),
            playlistWebURL = data["playlistWebURL"] as? String ?: throw Exception("Missing playlistWebURL"),
            cached = data["cached"] as? Boolean ?: false,
        )
    }

    @Suppress("UNCHECKED_CAST")
    suspend fun appleMusicLookup(name: String, artist: String, isrc: String?, spotifyTrackId: String?): String? {
        val params = mutableMapOf<String, Any>("name" to name, "artist" to artist)
        if (!isrc.isNullOrBlank()) params["isrc"] = isrc
        if (!spotifyTrackId.isNullOrBlank()) params["spotifyTrackId"] = spotifyTrackId
        val result = functions.getHttpsCallable("appleMusicLookup").call(params).await()
        val data = result.getData() as? Map<String, Any?> ?: return null
        return data["previewUrl"] as? String
    }

    @Suppress("UNCHECKED_CAST")
    suspend fun generateFeedPlaylist(): PlaylistResult {
        val result = functions.getHttpsCallable("generateFeedPlaylist").call(emptyMap<String, Any>()).await()
        val data = result.getData() as? Map<String, Any?> ?: throw Exception("Invalid response")
        if (data["error"] != null) {
            val msg = data["message"] as? String ?: "Unknown error"
            throw Exception(msg)
        }
        return PlaylistResult(
            playlistURI = data["playlistURI"] as? String ?: throw Exception("Missing playlistURI"),
            playlistWebURL = data["playlistWebURL"] as? String ?: throw Exception("Missing playlistWebURL"),
            cached = data["cached"] as? Boolean ?: false,
        )
    }

    // ── Post Limit ──

    @Suppress("UNCHECKED_CAST")
    suspend fun getTodayPostCount(userId: String): Int {
        val result = functions.getHttpsCallable("getTodayPostCount").call(
            mapOf("userId" to userId)
        ).await()
        val data = result.getData() as? Map<String, Any?> ?: return 0
        return (data["count"] as? Number)?.toInt() ?: 0
    }
}
