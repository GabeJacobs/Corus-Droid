package fm.corus.android.data.remote

import com.google.firebase.functions.FirebaseFunctions
import fm.corus.android.data.model.*
import kotlinx.coroutines.tasks.await
import java.util.Date
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Overwrite each post's isFirstPoster from the server-resolved firstPosterId.
 * Both sets true on the match and clears stale true flags on non-matches, so
 * a post with isFirstPoster=true stored in Firestore (e.g. from an older client
 * that wrote the flag locally and raced another user) can never display two trophies.
 */
internal fun applyFirstPoster(
    posts: List<CymbalPost>,
    firstPosterId: String?,
): List<CymbalPost> = posts.map { post ->
    post.copy(isFirstPoster = firstPosterId != null && post.user.id == firstPosterId)
}

/**
 * Parses a `fetchBackCover` Cloud Function response into a non-blank URL string.
 * Kept top-level so it can be unit-tested without instantiating the data source.
 */
internal fun parseBackCoverResponse(data: Map<String, Any?>?): String? {
    val url = data?.get("backCoverURL") as? String
    return url?.takeIf { it.isNotBlank() }
}

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
        mediaType: MediaType? = null,
        newReleasesOnly: Boolean = false,
    ): FeedPage {
        val params = mutableMapOf<String, Any>(
            "userId" to userId,
            "pageSize" to pageSize,
            "onePerFollower" to onePerFollower,
        )
        lastTimestamp?.let { params["beforeMs"] = it }
        mediaType?.let { params["mediaType"] = it.value }
        if (newReleasesOnly) params["newReleasesOnly"] = true

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

    // ── Back Cover (Discogs / MusicBrainz) ──

    @Suppress("UNCHECKED_CAST")
    suspend fun fetchBackCover(postId: String): String? {
        return try {
            val result = functions.getHttpsCallable("fetchBackCover").call(
                mapOf("postId" to postId)
            ).await()
            parseBackCoverResponse(result.getData() as? Map<String, Any?>)
        } catch (_: Exception) {
            null
        }
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

    data class ProfileData(
        val user: CymbalUser?,
        val posts: List<CymbalPost>,
        val match: MusicMatchData? = null,
    )

    /**
     * Fetches user profile metadata (with live cymbalCount via count() aggregation)
     * AND the first page of posts in a single round-trip. Use this for cold profile
     * loads so the header count and the posts grid come from the same backend
     * snapshot — prevents the "header says 2, grid says no songs yet" flash that
     * occurs with separate getUserProfile + getProfilePosts calls when Firestore's
     * composite index is still propagating a brand-new user's first posts.
     */
    @Suppress("UNCHECKED_CAST")
    suspend fun getProfileData(
        userId: String,
        pageSize: Int = 15,
        mediaType: String? = null,
    ): ProfileData {
        val params = mutableMapOf<String, Any>("userId" to userId, "pageSize" to pageSize)
        mediaType?.let { params["mediaType"] = it }
        val result = functions.getHttpsCallable("getProfileData").call(params).await()
        val data = result.getData() as? Map<String, Any?> ?: return ProfileData(null, emptyList())

        val userMap = data["user"] as? Map<String, Any?>
        val user = userMap?.let { CymbalUser.fromMap(it["id"] as? String ?: "", it) }
        val postDicts = data["posts"] as? List<Map<String, Any?>> ?: emptyList()
        val posts = postDicts.map { CymbalPost.fromCloudData(it) }
        val matchMap = data["match"] as? Map<String, Any?>
        val match = matchMap?.let {
            val parsed = MusicMatchData.fromMap(it)
            // Mirror iOS: return null when there's nothing to render so the
            // teaser stays hidden without per-field checks at the call site.
            if (parsed.sharedTrackPreviews.isEmpty()
                && parsed.sharedMoviePreviews.isEmpty()
                && parsed.sharedArtists == 0
                && parsed.sharedArtistNames.isEmpty()
                && parsed.sharedDirectorNames.isEmpty()
            ) null else parsed
        }
        return ProfileData(user, posts, match)
    }

    @Suppress("UNCHECKED_CAST")
    suspend fun getProfilePosts(
        userId: String,
        viewerId: String,
        limit: Int = 30,
        lastTimestamp: Long? = null,
        mediaType: String? = null,
    ): List<CymbalPost> {
        val params = mutableMapOf<String, Any>(
            "userId" to userId, "viewerId" to viewerId, "pageSize" to limit
        )
        lastTimestamp?.let { params["beforeMs"] = it }
        mediaType?.let { params["mediaType"] = it }
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

    /** Result of a savePost callable invocation. */
    data class SavePostResult(val savesCount: Int, val alreadySaved: Boolean)

    /** Thrown when the server rejects a save because the user has hit the free cap. */
    class SaveCapReachedException(val savesCount: Int) : Exception("SAVE_CAP_REACHED")

    @Suppress("UNCHECKED_CAST")
    suspend fun savePost(postId: String): SavePostResult {
        try {
            val result = functions.getHttpsCallable("savePost")
                .call(mapOf("postId" to postId)).await()
            val data = result.getData() as? Map<String, Any?> ?: emptyMap()
            val count = (data["savesCount"] as? Number)?.toInt() ?: 0
            val already = data["alreadySaved"] as? Boolean ?: false
            return SavePostResult(savesCount = count, alreadySaved = already)
        } catch (e: com.google.firebase.functions.FirebaseFunctionsException) {
            if (e.code == com.google.firebase.functions.FirebaseFunctionsException.Code.RESOURCE_EXHAUSTED) {
                val details = e.details as? Map<String, Any?>
                val count = (details?.get("savesCount") as? Number)?.toInt() ?: 0
                throw SaveCapReachedException(count)
            }
            throw e
        }
    }

    @Suppress("UNCHECKED_CAST")
    suspend fun unsavePost(postId: String): Int {
        val result = functions.getHttpsCallable("unsavePost")
            .call(mapOf("postId" to postId)).await()
        val data = result.getData() as? Map<String, Any?> ?: emptyMap()
        return (data["savesCount"] as? Number)?.toInt() ?: 0
    }

    @Suppress("UNCHECKED_CAST")
    suspend fun reconcileSavesCount(): Int {
        val result = functions.getHttpsCallable("reconcileSavesCount")
            .call(emptyMap<String, Any>()).await()
        val data = result.getData() as? Map<String, Any?> ?: emptyMap()
        return (data["savesCount"] as? Number)?.toInt() ?: 0
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

    data class HashtagPostsPage(
        val posts: List<CymbalPost>,
        val totalCount: Int,
        val hasMore: Boolean,
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

        val posts = applyFirstPoster(postsData.map { CymbalPost.fromCloudData(it) }, firstPosterId)

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

        val posts = applyFirstPoster(postsData.map { CymbalPost.fromCloudData(it) }, firstPosterId)

        return MoviePostsPage(posts, uniquePosterCount, firstPosterId)
    }

    @Suppress("UNCHECKED_CAST")
    suspend fun getHashtagPosts(hashtag: String, pageSize: Int = 15, beforeMs: Long? = null): HashtagPostsPage {
        val params = mutableMapOf<String, Any>("hashtag" to hashtag, "pageSize" to pageSize)
        beforeMs?.let { params["beforeMs"] = it }
        val result = functions.getHttpsCallable("getHashtagPosts").call(params).await()
        val data = result.getData() as? Map<String, Any?>
            ?: return HashtagPostsPage(emptyList(), 0, false)
        val postsData = data["posts"] as? List<Map<String, Any?>> ?: emptyList()
        val posts = postsData.map { CymbalPost.fromCloudData(it) }
        val totalCount = (data["totalCount"] as? Number)?.toInt() ?: 0
        val hasMore = data["hasMore"] as? Boolean ?: false
        return HashtagPostsPage(posts, totalCount, hasMore)
    }

    // ── Notifications ──

    @Suppress("UNCHECKED_CAST")
    suspend fun getNotifications(userId: String, limit: Int = 15, lastTimestamp: Long? = null): List<CymbalNotification> {
        val params = mutableMapOf<String, Any>("userId" to userId, "limit" to limit)
        lastTimestamp?.let { params["startAfter"] = it }
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
        lastTimestamp?.let { params["beforeMs"] = it }
        val result = functions.getHttpsCallable("listMessages").call(params).await()
        val data = result.getData() as? Map<String, Any?> ?: return emptyList()
        val messages = data["messages"] as? List<Map<String, Any?>> ?: return emptyList()
        return messages.map { CymbalMessage.fromMap(it["id"] as? String ?: "", it) }
    }

    suspend fun sendMessage(
        threadId: String,
        fromUserId: String,
        text: String,
        type: String = "text",
        mediaURL: String? = null,
        sharedPostId: String? = null,
        trackId: String? = null,
        trackName: String? = null,
        artistName: String? = null,
        albumName: String? = null,
        albumArtURL: String? = null,
        albumArtLargeURL: String? = null,
        spotifyURI: String? = null,
        spotifyURL: String? = null,
        previewUrl: String? = null,
        isrc: String? = null,
        durationMs: Int? = null,
        source: String? = null,
        soundcloudId: String? = null,
        soundcloudPermalinkUrl: String? = null,
        movieId: String? = null,
        movieTitle: String? = null,
        directorName: String? = null,
        releaseYear: String? = null,
        posterURL: String? = null,
        posterLargeURL: String? = null,
        tmdbWebURL: String? = null,
        replyToMessageId: String? = null,
        replyToText: String? = null,
        replyToUserId: String? = null,
        clientMessageId: String? = null,
    ) {
        val params = mutableMapOf<String, Any>(
            "threadId" to threadId,
            "fromUserId" to fromUserId,
            "text" to (text),
            "type" to type,
        )
        mediaURL?.let { params["mediaURL"] = it }
        sharedPostId?.let { params["sharedPostId"] = it }
        trackId?.let { params["trackId"] = it }
        trackName?.let { params["trackName"] = it }
        artistName?.let { params["artistName"] = it }
        albumName?.let { params["albumName"] = it }
        albumArtURL?.let { params["albumArtURL"] = it }
        albumArtLargeURL?.let { params["albumArtLargeURL"] = it }
        spotifyURI?.let { params["spotifyURI"] = it }
        spotifyURL?.let { params["spotifyURL"] = it }
        previewUrl?.let { params["previewUrl"] = it }
        isrc?.let { params["isrc"] = it }
        durationMs?.let { params["durationMs"] = it }
        source?.let { params["source"] = it }
        soundcloudId?.let { params["soundcloudId"] = it }
        soundcloudPermalinkUrl?.let { params["soundcloudPermalinkUrl"] = it }
        movieId?.let { params["movieId"] = it }
        movieTitle?.let { params["movieTitle"] = it }
        directorName?.let { params["directorName"] = it }
        releaseYear?.let { params["releaseYear"] = it }
        posterURL?.let { params["posterURL"] = it }
        posterLargeURL?.let { params["posterLargeURL"] = it }
        tmdbWebURL?.let { params["tmdbWebURL"] = it }
        replyToMessageId?.let { params["replyToMessageId"] = it }
        replyToText?.let { params["replyToText"] = it }
        replyToUserId?.let { params["replyToUserId"] = it }
        clientMessageId?.let { params["clientMessageId"] = it }
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

    // ── GIF Search (Klipy via Cloud Function) ──

    data class GifSearchResult(
        val results: List<KlipyGif>,
        val currentPage: Int,
        val hasNext: Boolean,
    )

    @Suppress("UNCHECKED_CAST")
    suspend fun searchKlipyGifs(query: String = "", page: Int = 1, perPage: Int = 24): GifSearchResult {
        val params = mutableMapOf<String, Any>("page" to page, "perPage" to perPage)
        if (query.isNotBlank()) params["query"] = query

        val result = functions.getHttpsCallable("searchKlipyGifs").call(params).await()
        val data = result.getData() as? Map<String, Any?> ?: emptyMap()
        val rawResults = data["results"] as? List<Map<String, Any?>> ?: emptyList()

        return GifSearchResult(
            results = rawResults.map { r ->
                KlipyGif(
                    id = r["id"] as? String ?: "",
                    slug = r["slug"] as? String ?: "",
                    title = r["title"] as? String ?: "",
                    thumbnailURL = r["thumbnailURL"] as? String ?: "",
                    fullURL = r["fullURL"] as? String ?: "",
                    thumbnailWidth = (r["thumbnailWidth"] as? Number)?.toInt() ?: 0,
                    thumbnailHeight = (r["thumbnailHeight"] as? Number)?.toInt() ?: 0,
                )
            },
            currentPage = (data["currentPage"] as? Number)?.toInt() ?: page,
            hasNext = data["hasNext"] as? Boolean ?: false,
        )
    }

    suspend fun triggerKlipyShare(slug: String) {
        try {
            functions.getHttpsCallable("triggerKlipyShare").call(mapOf("slug" to slug)).await()
        } catch (_: Exception) {
            // best-effort analytics ping
        }
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

    // ── Unified Songs Search (Spotify + SoundCloud) ──

    /**
     * Single source of truth for song search across Android, iOS, and Web.
     * The backend fans out to both Spotify and SoundCloud, merges, and ranks.
     * Returns a unified track list that already includes `source`,
     * `soundcloudId`, and `soundcloudPermalinkUrl` for SoundCloud entries.
     */
    @Suppress("UNCHECKED_CAST")
    suspend fun searchSongs(
        query: String,
        offset: Int = 0,
        limit: Int = 20,
        market: String = "US",
        includeSoundCloud: Boolean = true,
    ): Map<String, Any?> {
        val result = functions.getHttpsCallable("searchSongs").call(
            mapOf(
                "query" to query,
                "offset" to offset,
                "limit" to limit,
                "market" to market,
                "includeSoundCloud" to includeSoundCloud,
            )
        ).await()
        return result.getData() as? Map<String, Any?> ?: emptyMap()
    }

    // ── SoundCloud ──

    /**
     * Resolves a fresh, signed HLS stream URL for a SoundCloud track.
     * URLs are short-lived (~1h) — call at playback time, not post-creation time.
     */
    @Suppress("UNCHECKED_CAST")
    suspend fun soundcloudResolveStream(soundcloudId: String): Map<String, Any?> {
        val result = functions.getHttpsCallable("soundcloudResolveStream").call(
            mapOf("soundcloudId" to soundcloudId)
        ).await()
        return result.getData() as? Map<String, Any?> ?: emptyMap()
    }

    /**
     * Marks all posts referencing a given SoundCloud track ID as unavailable.
     * Called reactively when stream resolution returns 404 / 403 (deleted,
     * privatized, or geo-blocked).
     */
    suspend fun markSoundCloudUnavailable(soundcloudId: String, reason: String) {
        functions.getHttpsCallable("markSoundCloudUnavailable").call(
            mapOf("soundcloudId" to soundcloudId, "reason" to reason)
        ).await()
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
        return parseUserRows(rows).filter { !it.user.isBot }
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
    internal fun parseUserRows(rows: List<Map<String, Any?>>): List<SuggestedUserMatch> {
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

            // Gate matches iOS fetchSuggestedUserMatches: only build matchData when there's a
            // server-computed score or a concrete taste signal (previews / adjacent artists /
            // posted-movie or director overlap). Shared posted/liked track counts and raw
            // sharedArtists/sharedLikedMovies alone aren't enough — those users fall into
            // mutual/popular sections instead of the Taste Matches section.
            val hasMatchData = score > 0 || trackPreviews.isNotEmpty() || adjacentArtists > 0 ||
                moviePreviews.isNotEmpty() || sharedPostedMovies > 0 || sharedDirectors > 0

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

            // Mutual-connection metadata is inline in the row. `mutualCount`
            // is the full overlap-set size; `mutualNames` is the (≤ 5) sample
            // list. Falls back to names.size for back-compat with older payloads.
            val mutualNames = (row["mutualNames"] as? List<*>)?.filterIsInstance<String>() ?: emptyList()
            val mutualCount = (row["mutualCount"] as? Number)?.toInt() ?: mutualNames.size
            val reason = if (mutualNames.isNotEmpty())
                SuggestionReason(mutualNames = mutualNames, mutualCount = mutualCount)
            else null

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
        /** Number of SoundCloud tracks omitted from the Spotify playlist. */
        val soundcloudSkipped: Int = 0,
    )

    class PaywallRequiredException : Exception("Playlist generation limit reached")

    /**
     * Special exception when the source feed/profile contained ONLY SoundCloud
     * tracks, so a Spotify playlist couldn't be built. UIs should surface a
     * different message than a generic playlist failure.
     */
    class OnlySoundCloudException(val skipped: Int) : Exception("Only SoundCloud tracks available")

    internal fun parsePlaylistResponse(data: Map<String, Any?>): PlaylistResult {
        if (data["error"] != null) {
            if ((data["code"] as? String) == "PAYWALL") throw PaywallRequiredException()
            val skipped = (data["soundcloudSkipped"] as? Number)?.toInt() ?: 0
            if (skipped > 0) throw OnlySoundCloudException(skipped)
            val msg = data["message"] as? String ?: "Unknown error"
            throw Exception(msg)
        }
        return PlaylistResult(
            playlistURI = data["playlistURI"] as? String ?: throw Exception("Missing playlistURI"),
            playlistWebURL = data["playlistWebURL"] as? String ?: throw Exception("Missing playlistWebURL"),
            cached = data["cached"] as? Boolean ?: false,
            soundcloudSkipped = (data["soundcloudSkipped"] as? Number)?.toInt() ?: 0,
        )
    }

    /** Source the profile playlist should pull from. `Posts` is the legacy
     *  default; `Likes` and `Saves` were added later. Wire format matches the
     *  backend `source` parameter exactly. */
    enum class ProfilePlaylistSource(val wire: String) {
        Posts("posts"),
        Likes("likes"),
        Saves("saves"),
    }

    @Suppress("UNCHECKED_CAST")
    suspend fun generateProfilePlaylist(
        userId: String,
        source: ProfilePlaylistSource = ProfilePlaylistSource.Posts,
    ): PlaylistResult {
        // Only attach `source` for the new branches so the request shape stays
        // identical to older clients for the posts case.
        val payload = mutableMapOf<String, Any>(
            "userId" to userId,
            "supportsGating" to true,
        )
        if (source != ProfilePlaylistSource.Posts) {
            payload["source"] = source.wire
        }
        val result = functions.getHttpsCallable("generateProfilePlaylist").call(payload).await()
        val data = result.getData() as? Map<String, Any?> ?: throw Exception("Invalid response")
        return parsePlaylistResponse(data)
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
    suspend fun generateFeedPlaylist(newReleasesOnly: Boolean = false): PlaylistResult {
        val params = mutableMapOf<String, Any>("supportsGating" to true)
        if (newReleasesOnly) params["newReleasesOnly"] = true
        val result = functions.getHttpsCallable("generateFeedPlaylist").call(params).await()
        val data = result.getData() as? Map<String, Any?> ?: throw Exception("Invalid response")
        return parsePlaylistResponse(data)
    }

    // ── Post Limit ──

    data class CheckCanPostResult(
        val canPost: Boolean,
        val recentCount: Int,
        val dailyLimit: Int?,
    )

    @Suppress("UNCHECKED_CAST")
    suspend fun checkCanPost(): CheckCanPostResult {
        val result = functions.getHttpsCallable("checkCanPost").call().await()
        val data = result.getData() as? Map<String, Any?>
            ?: return CheckCanPostResult(canPost = true, recentCount = 0, dailyLimit = null)
        return CheckCanPostResult(
            canPost = data["canPost"] as? Boolean ?: true,
            recentCount = (data["recentCount"] as? Number)?.toInt() ?: 0,
            dailyLimit = (data["dailyLimit"] as? Number)?.toInt(),
        )
    }
}
