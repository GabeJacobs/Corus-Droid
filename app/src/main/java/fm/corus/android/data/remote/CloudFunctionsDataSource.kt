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
 * Extracts matched user IDs from a `findContactMatches` response payload.
 * Response shape: `{ "matches": [ { "id": "...", "username": ... }, ... ] }`.
 * Kept top-level so it can be unit-tested without mocking FirebaseFunctions
 * (per the project's "Mockito tests fail from CLI" note). Defensive against
 * null payloads, a non-list `matches`, and entries missing/!-string `id`.
 */
@Suppress("UNCHECKED_CAST")
internal fun parseContactMatchIds(data: Map<String, Any?>?): List<String> {
    val matches = data?.get("matches") as? List<*> ?: return emptyList()
    return matches.mapNotNull { entry ->
        ((entry as? Map<String, Any?>)?.get("id") as? String)?.takeIf { it.isNotBlank() }
    }
}

/**
 * Parses a `getForYouFeed` Cloud Function response payload into typed fields.
 * Kept top-level so it can be unit-tested without mocking FirebaseFunctions.
 * Returns null fields/defaults when the server sends an unexpected shape so
 * older clients don't crash on a future response that adds optional keys.
 */
@Suppress("UNCHECKED_CAST")
internal fun parseForYouFeedResponse(
    data: Map<String, Any?>?,
): Quadruple<List<Map<String, Any?>>, Boolean, String, Boolean> {
    if (data == null) return Quadruple(emptyList(), false, "", false)
    val postsData = (data["posts"] as? List<Map<String, Any?>>) ?: emptyList()
    val hasMore = data["hasMore"] as? Boolean ?: false
    val token = data["sessionToken"] as? String ?: ""
    val fellBack = data["fellBackToFollowing"] as? Boolean ?: false
    return Quadruple(postsData, hasMore, token, fellBack)
}

internal data class Quadruple<A, B, C, D>(val a: A, val b: B, val c: C, val d: D)

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

    /**
     * Chronological feed limited to the users the caller has favorited. Mirrors
     * [getFeedPage] but hits the `getFavoritesFeedPage` callable, which queries
     * the caller's `users_v2/{uid}/favorites` subcollection.
     */
    @Suppress("UNCHECKED_CAST")
    suspend fun getFavoritesFeedPage(
        userId: String,
        pageSize: Int = 7,
        lastTimestamp: Long? = null,
        mediaType: MediaType? = null,
        newReleasesOnly: Boolean = false,
    ): FeedPage {
        val params = mutableMapOf<String, Any>(
            "userId" to userId,
            "pageSize" to pageSize,
        )
        lastTimestamp?.let { params["beforeMs"] = it }
        mediaType?.let { params["mediaType"] = it.value }
        if (newReleasesOnly) params["newReleasesOnly"] = true

        val result = functions.getHttpsCallable("getFavoritesFeedPage").call(params).await()
        val data = result.getData() as? Map<String, Any?> ?: return FeedPage(emptyList(), false)

        val postsData = data["posts"] as? List<Map<String, Any?>> ?: emptyList()
        val posts = postsData.map { CymbalPost.fromCloudData(it) }
        val hasMore = data["hasMore"] as? Boolean ?: (posts.size >= pageSize)

        return FeedPage(posts, hasMore)
    }

    data class ForYouFeedPage(
        val posts: List<CymbalPost>,
        val hasMore: Boolean,
        val sessionToken: String,
        val fellBackToFollowing: Boolean,
    )

    /**
     * Calls the `getForYouFeed` callable (algorithmically-ranked feed).
     * `sessionToken == null` starts a fresh session; subsequent pages pass
     * the token from the first call along with `pageIndex`. `seenPostIds`
     * is a client-side ring buffer (cap 500) used to suppress already-shown
     * posts when a session expires mid-scroll.
     */
    /**
     * `scope` is "forYou" (pool = your follows) or "trending" (pool = the
     * whole app). Both use the same ranked callable + pagination machinery.
     */
    @Suppress("UNCHECKED_CAST")
    suspend fun getForYouFeed(
        userId: String,
        pageSize: Int = 7,
        sessionToken: String? = null,
        pageIndex: Int = 0,
        seenPostIds: List<String> = emptyList(),
        mediaType: MediaType? = null,
        newReleasesOnly: Boolean = false,
        scope: String = "forYou",
        isRefresh: Boolean = false,
    ): ForYouFeedPage {
        val params = mutableMapOf<String, Any>(
            "userId" to userId,
            "pageSize" to pageSize,
            "pageIndex" to pageIndex,
            "scope" to scope,
        )
        if (isRefresh) params["isRefresh"] = true
        sessionToken?.takeIf { it.isNotEmpty() }?.let { params["sessionToken"] = it }
        if (seenPostIds.isNotEmpty()) {
            params["seenPostIds"] = seenPostIds.take(500)
        }
        mediaType?.let { params["mediaType"] = it.value }
        if (newReleasesOnly) params["newReleasesOnly"] = true

        val result = functions.getHttpsCallable("getForYouFeed").call(params).await()
        val data = result.getData() as? Map<String, Any?>
        val parsed = parseForYouFeedResponse(data)
        val posts = parsed.a.map { CymbalPost.fromCloudData(it) }
        return ForYouFeedPage(posts, parsed.b, parsed.c, parsed.d)
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

    /**
     * Thrown when the server rejects a follow because the user has hit the
     * rolling 24h follow cap. `retryAfterSeconds` is when the next slot
     * opens (the oldest event in the window ages out). UI maps this to a
     * top-anchored toast.
     */
    class FollowLimitReachedException(
        val dailyLimit: Int,
        val retryAfterSeconds: Int,
    ) : Exception("FOLLOW_LIMIT_REACHED")

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

    // ── Likes ──
    // Server-side: the `likePost`/`unlikePost` callables atomically write
    // the like doc, the user's `liked` entry, and bump `posts.likeCount`,
    // then synchronously refresh `recentLikers` before returning. This
    // closes the ~10–20s trigger-aggregation window during which a fresh
    // refetch could return `isLiked=true` alongside a `recentLikers`
    // preview that omits the current user.

    suspend fun likePost(postId: String) {
        functions.getHttpsCallable("likePost")
            .call(mapOf("postId" to postId)).await()
    }

    suspend fun unlikePost(postId: String) {
        functions.getHttpsCallable("unlikePost")
            .call(mapOf("postId" to postId)).await()
    }

    // Records a UNIQUE in-app listener for a corus. Server-side the `recordPlay`
    // callable writes posts/{postId}/plays/{uid} (lifetime-unique by uid) and
    // bumps posts.playCount — excluding self-plays and repeat listeners. Plays
    // are stored but not yet surfaced anywhere; this is the foundation for a
    // future play-milestone push notification. Best-effort: callers fire-and-
    // forget and never block playback on the result.
    suspend fun recordPlay(postId: String) {
        functions.getHttpsCallable("recordPlay")
            .call(mapOf("postId" to postId)).await()
    }

    suspend fun likeComment(postId: String, commentId: String) {
        functions.getHttpsCallable("likeComment")
            .call(mapOf("postId" to postId, "commentId" to commentId)).await()
    }

    suspend fun unlikeComment(postId: String, commentId: String) {
        functions.getHttpsCallable("unlikeComment")
            .call(mapOf("postId" to postId, "commentId" to commentId)).await()
    }

    // ── Follow / Unfollow ──
    // Server-side: the `followUser` callable enforces a rolling 24h follow
    // cap, then writes the same two Firestore docs the old client-side
    // batch wrote, so every existing trigger keeps working untouched. On
    // limit hit, `FirebaseFunctionsException.Code.RESOURCE_EXHAUSTED` is
    // mapped to `FollowLimitReachedException` here so UI can match it.
    suspend fun followUser(targetUserId: String) {
        try {
            functions.getHttpsCallable("followUser")
                .call(mapOf("targetUserId" to targetUserId)).await()
        } catch (e: com.google.firebase.functions.FirebaseFunctionsException) {
            if (e.code == com.google.firebase.functions.FirebaseFunctionsException.Code.RESOURCE_EXHAUSTED) {
                val details = e.details as? Map<*, *>
                val dailyLimit = (details?.get("dailyLimit") as? Number)?.toInt() ?: 400
                val retryAfterSeconds = (details?.get("retryAfterSeconds") as? Number)?.toInt() ?: 0
                throw FollowLimitReachedException(
                    dailyLimit = dailyLimit,
                    retryAfterSeconds = retryAfterSeconds,
                )
            }
            throw e
        }
    }

    suspend fun unfollowUser(targetUserId: String) {
        functions.getHttpsCallable("unfollowUser")
            .call(mapOf("targetUserId" to targetUserId)).await()
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
        artistIds: List<String>? = null,
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
        directorIds: List<String>? = null,
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
        artistIds?.takeIf { it.isNotEmpty() }?.let { params["artistIds"] = it }
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
        directorIds?.takeIf { it.isNotEmpty() }?.let { params["directorIds"] = it }
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
        // `supports` declares which result sources this client can render.
        // Backend uses it to gate Apple-Music-only catalog results — old
        // builds without this field get the pre-Apple behavior (Spotify +
        // SoundCloud only), so shipping this is back-compat-safe. The
        // server also gates Apple results on a Remote Config flag /
        // per-UID allowlist; declaring capability here doesn't bypass that.
        val result = functions.getHttpsCallable("searchSongs").call(
            mapOf(
                "query" to query,
                "offset" to offset,
                "limit" to limit,
                "market" to market,
                "includeSoundCloud" to includeSoundCloud,
                "supports" to listOf("spotify", "soundcloud", "applemusic"),
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

            // Authoritative artist/director names the viewer actually shares with this
            // user — the card prefers these over count labels ("2 artist matches").
            // Read straight from the row; mirrors iOS fetchSuggestedUserMatches. Without
            // these the card always fell back to counts.
            val sharedArtistNames = (row["sharedArtistNames"] as? List<*>)?.filterIsInstance<String>() ?: emptyList()
            val sharedDirectorNames = (row["sharedDirectorNames"] as? List<*>)?.filterIsInstance<String>() ?: emptyList()

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
                    sharedArtistNames = sharedArtistNames,
                    sharedDirectorNames = sharedDirectorNames,
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

    /**
     * Resolve which of the caller's contact phone numbers belong to existing
     * Corus users, via the `findContactMatches` callable. Returns matched
     * user IDs (the caller is excluded server-side); callers hydrate full
     * profiles via [UserRepository.fetchUsersByIdsBatched].
     *
     * Replaces the former client-side `users_v2` `whereIn("phoneNumber", ...)`
     * query, which exposed phoneNumber on the publicly-listable user doc.
     * Send the same regex-normalized numbers as before — the server filters
     * to E.164 (`^\+\d{6,15}$`), the only form that ever matched (stored
     * phoneNumber is always Firebase-Auth E.164), so match parity holds.
     */
    @Suppress("UNCHECKED_CAST")
    suspend fun findContactMatches(phoneNumbers: List<String>): List<String> {
        if (phoneNumbers.isEmpty()) return emptyList()
        val result = functions
            .getHttpsCallable("findContactMatches")
            .call(mapOf("phones" to phoneNumbers))
            .await()
        return parseContactMatchIds(result.getData() as? Map<String, Any?>)
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

    // ── Client-side playlists (Apple Music / TIDAL) ───────────────────────────
    // Non-Spotify services build the playlist on the user's own account from a
    // raw track list the backend resolves (the `appleMusicTracks` flag — a
    // generic "give me descriptors, I'll build it client-side" switch reused by
    // TIDAL). Mirrors the iOS PlaylistTrackDescriptor / parsePlaylistTracksResponse.

    /** One track from the backend's `appleMusicTracks` response. */
    data class PlaylistTrackDescriptor(
        val trackId: String,
        val isrc: String?,
        val name: String,
        val artist: String,
        val album: String,
    )

    /** Result of parsing an `appleMusicTracks` response. Mirrors the error/
     *  paywall/SoundCloud handling of the Spotify path so messaging stays
     *  consistent across services. */
    sealed class PlaylistTracksOutcome {
        object Paywall : PlaylistTracksOutcome()
        /** No usable tracks. `soundcloudSkipped` lets the caller explain *why*
         *  (an all-SoundCloud feed) vs. a generic empty/error state. */
        data class Failure(val soundcloudSkipped: Int) : PlaylistTracksOutcome()
        data class Tracks(
            val descriptors: List<PlaylistTrackDescriptor>,
            val soundcloudSkipped: Int,
            /** Profile owner's username, for titling the playlist. null for feed. */
            val username: String?,
        ) : PlaylistTracksOutcome()
    }

    companion object {
        internal fun parsePlaylistTracksResponse(data: Map<String, Any?>): PlaylistTracksOutcome {
            val soundcloudSkipped = (data["soundcloudSkipped"] as? Number)?.toInt() ?: 0
            if (data["error"] != null && data["message"] != null) {
                if ((data["code"] as? String) == "PAYWALL") return PlaylistTracksOutcome.Paywall
                return PlaylistTracksOutcome.Failure(soundcloudSkipped)
            }
            val raw = data["tracks"] as? List<*> ?: return PlaylistTracksOutcome.Failure(soundcloudSkipped)
            val descriptors = raw.mapNotNull { item ->
                val m = item as? Map<*, *> ?: return@mapNotNull null
                val name = (m["name"] as? String).orEmpty()
                val artist = (m["artist"] as? String).orEmpty()
                val isrc = (m["isrc"] as? String).orEmpty().ifEmpty { null }
                val trackId = (m["trackId"] as? String).orEmpty()
                val album = (m["album"] as? String).orEmpty()
                // Need at least a name or an ISRC to resolve the song on TIDAL.
                if (name.isEmpty() && isrc == null) return@mapNotNull null
                PlaylistTrackDescriptor(trackId = trackId, isrc = isrc, name = name, artist = artist, album = album)
            }
            if (descriptors.isEmpty()) return PlaylistTracksOutcome.Failure(soundcloudSkipped)
            return PlaylistTracksOutcome.Tracks(descriptors, soundcloudSkipped, data["username"] as? String)
        }
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

    /** TIDAL/Apple Music variant: returns the resolved track descriptors instead
     *  of a server-built Spotify playlist (the client builds it on the user's
     *  own account). Uses the correct `supportsPlaylistGating` key so the
     *  paywall applies, matching iOS. */
    @Suppress("UNCHECKED_CAST")
    suspend fun generateProfilePlaylistTracks(
        userId: String,
        source: ProfilePlaylistSource = ProfilePlaylistSource.Posts,
    ): PlaylistTracksOutcome {
        val payload = mutableMapOf<String, Any>(
            "userId" to userId,
            "supportsPlaylistGating" to true,
            "appleMusicTracks" to true,
        )
        if (source != ProfilePlaylistSource.Posts) payload["source"] = source.wire
        val result = functions.getHttpsCallable("generateProfilePlaylist").call(payload).await()
        val data = result.getData() as? Map<String, Any?> ?: return PlaylistTracksOutcome.Failure(0)
        return parsePlaylistTracksResponse(data)
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

    // ── Music-service link-out resolvers ──────────────────────────────────────
    // Resolve a Spotify track to its counterpart page URL on another service, for
    // the post "open in <service>" link-out. Each mirrors `appleMusicLookup` but
    // returns the catalog page URL (`<service>URL`) rather than the preview. The
    // backend runs the shared matcher and caches the mapping; returns null on no
    // match / error so the caller can no-op. Shared param builder keeps the three
    // identical except for the callable name and the response key.
    private fun linkOutParams(name: String, artist: String, isrc: String?, spotifyTrackId: String?): Map<String, Any> {
        val params = mutableMapOf<String, Any>("name" to name, "artist" to artist)
        if (!isrc.isNullOrBlank()) params["isrc"] = isrc
        if (!spotifyTrackId.isNullOrBlank()) params["spotifyTrackId"] = spotifyTrackId
        return params
    }

    @Suppress("UNCHECKED_CAST")
    suspend fun appleMusicLinkOutUrl(name: String, artist: String, isrc: String?, spotifyTrackId: String?): String? {
        val result = functions.getHttpsCallable("appleMusicLookup").call(linkOutParams(name, artist, isrc, spotifyTrackId)).await()
        val data = result.getData() as? Map<String, Any?> ?: return null
        return data["appleMusicURL"] as? String
    }

    @Suppress("UNCHECKED_CAST")
    suspend fun tidalLinkOutUrl(name: String, artist: String, isrc: String?, spotifyTrackId: String?): String? {
        val result = functions.getHttpsCallable("tidalLookup").call(linkOutParams(name, artist, isrc, spotifyTrackId)).await()
        val data = result.getData() as? Map<String, Any?> ?: return null
        return data["tidalURL"] as? String
    }

    /** Resolve a Spotify track to its TIDAL track *id* (for building a playlist
     *  on the user's TIDAL account). Sibling of [tidalLinkOutUrl], which reads
     *  the catalog page URL; this reads `tidalId`. null on no match / error. */
    @Suppress("UNCHECKED_CAST")
    suspend fun tidalLookupId(name: String, artist: String, isrc: String?, spotifyTrackId: String?): String? {
        val result = functions.getHttpsCallable("tidalLookup").call(linkOutParams(name, artist, isrc, spotifyTrackId)).await()
        val data = result.getData() as? Map<String, Any?> ?: return null
        return (data["tidalId"] as? String)?.ifEmpty { null }
    }

    @Suppress("UNCHECKED_CAST")
    suspend fun deezerLinkOutUrl(name: String, artist: String, isrc: String?, spotifyTrackId: String?): String? {
        val result = functions.getHttpsCallable("deezerLookup").call(linkOutParams(name, artist, isrc, spotifyTrackId)).await()
        val data = result.getData() as? Map<String, Any?> ?: return null
        return data["deezerURL"] as? String
    }

    @Suppress("UNCHECKED_CAST")
    /**
     * @param feedMode Which feed the user is viewing ("following" / "trending" /
     *   "favorites"). Drives both the playlist's source and its name on the
     *   server. Defaults to "following" for back-compat.
     * @param sessionToken The live Trending ranked-session token the user is
     *   scrolling, so the server builds the playlist from the exact list shown.
     *   Only meaningful for "trending"; ignored otherwise.
     */
    suspend fun generateFeedPlaylist(
        newReleasesOnly: Boolean = false,
        feedMode: String = "following",
        sessionToken: String? = null,
    ): PlaylistResult {
        val params = mutableMapOf<String, Any>("supportsGating" to true, "feedMode" to feedMode)
        if (newReleasesOnly) params["newReleasesOnly"] = true
        if (feedMode == "trending" && !sessionToken.isNullOrEmpty()) params["sessionToken"] = sessionToken
        val result = functions.getHttpsCallable("generateFeedPlaylist").call(params).await()
        val data = result.getData() as? Map<String, Any?> ?: throw Exception("Invalid response")
        return parsePlaylistResponse(data)
    }

    /** TIDAL/Apple Music variant of [generateFeedPlaylist]: returns resolved
     *  track descriptors for client-side playlist building. */
    @Suppress("UNCHECKED_CAST")
    suspend fun generateFeedPlaylistTracks(
        newReleasesOnly: Boolean = false,
        feedMode: String = "following",
        sessionToken: String? = null,
    ): PlaylistTracksOutcome {
        val params = mutableMapOf<String, Any>(
            "supportsPlaylistGating" to true,
            "feedMode" to feedMode,
            "appleMusicTracks" to true,
        )
        if (newReleasesOnly) params["newReleasesOnly"] = true
        if (feedMode == "trending" && !sessionToken.isNullOrEmpty()) params["sessionToken"] = sessionToken
        val result = functions.getHttpsCallable("generateFeedPlaylist").call(params).await()
        val data = result.getData() as? Map<String, Any?> ?: return PlaylistTracksOutcome.Failure(0)
        return parsePlaylistTracksResponse(data)
    }

    // ── Post Limit ──

    data class CheckCanPostResult(
        val canPost: Boolean,
        /** 24h rolling count. Only meaningful for free-tier users; 0 for subscribers. */
        val recentCount: Int,
        /** 6h rolling count for the hard-cap check. Only meaningful for subscribers; 0 for free-tier. */
        val recentCountHard: Int,
        val dailyLimit: Int?,
    )

    @Suppress("UNCHECKED_CAST")
    suspend fun checkCanPost(): CheckCanPostResult {
        // Send the device timezone so the server anchors the daily limit to the
        // user's local calendar day (calendar-day reset).
        val params = mapOf("timeZone" to java.util.TimeZone.getDefault().id)
        val result = functions.getHttpsCallable("checkCanPost").call(params).await()
        val data = result.getData() as? Map<String, Any?>
            ?: return CheckCanPostResult(canPost = true, recentCount = 0, recentCountHard = 0, dailyLimit = null)
        return CheckCanPostResult(
            canPost = data["canPost"] as? Boolean ?: true,
            recentCount = (data["recentCount"] as? Number)?.toInt() ?: 0,
            recentCountHard = (data["recentCountHard"] as? Number)?.toInt() ?: 0,
            dailyLimit = (data["dailyLimit"] as? Number)?.toInt(),
        )
    }

    // ── createPost callable ──
    //
    // Server-driven post creation. Atomically validates the rolling 24h limit
    // and writes the post + hashtag increments in a single WriteBatch.
    // Replaces FirestoreDataSource.createPost so the silent-delete branch in
    // onPostCreatedFanoutFeedPointers never has to fire for clients on this
    // path. Mirrors iOS PostService and the Web `createPostViaCallable`.

    data class CreatePostResult(
        val postId: String,
        val recentCount: Int,
        val recentCountHard: Int,
        val dailyLimit: Int,
        val isFirstPoster: Boolean,
    )

    /** Thrown when the server rejects a post for limit reasons. Compose
     *  surfaces this as the paywall (or the hard-cap alert when [hardCap] is
     *  true). Mirrors iOS `PostService.CreatePostError.postLimitReached`. */
    class PostLimitReachedException(
        val recentCount: Int,
        val dailyLimit: Int,
        val hardCap: Boolean,
    ) : Exception("POST_LIMIT_REACHED")

    /** Thrown when the repost original no longer exists. */
    class RepostOriginalMissingException : Exception("REPOST_ORIGINAL_MISSING")

    /** Thrown when the account is banned/suspended. */
    class PostingBannedException : Exception("POSTING_BANNED")

    @Suppress("UNCHECKED_CAST")
    suspend fun createPost(payload: Map<String, Any?>): CreatePostResult {
        try {
            // Send the device timezone so the server anchors the daily limit to
            // the user's local calendar day (calendar-day reset).
            val withTz = payload + ("timeZone" to java.util.TimeZone.getDefault().id)
            val result = functions.getHttpsCallable("createPost").call(withTz).await()
            val data = result.getData() as? Map<String, Any?> ?: emptyMap()
            return CreatePostResult(
                postId = data["postId"] as? String ?: "",
                recentCount = (data["recentCount"] as? Number)?.toInt() ?: 0,
                recentCountHard = (data["recentCountHard"] as? Number)?.toInt() ?: 0,
                dailyLimit = (data["dailyLimit"] as? Number)?.toInt() ?: 3,
                isFirstPoster = data["isFirstPoster"] as? Boolean ?: false,
            )
        } catch (e: com.google.firebase.functions.FirebaseFunctionsException) {
            when (e.code) {
                com.google.firebase.functions.FirebaseFunctionsException.Code.RESOURCE_EXHAUSTED -> {
                    val details = e.details as? Map<String, Any?>
                    val recent = (details?.get("recentCount") as? Number)?.toInt() ?: 0
                    val limit = (details?.get("dailyLimit") as? Number)?.toInt() ?: 3
                    val hardCap = details?.get("hardCap") as? Boolean ?: false
                    throw PostLimitReachedException(recent, limit, hardCap)
                }
                com.google.firebase.functions.FirebaseFunctionsException.Code.NOT_FOUND -> {
                    throw RepostOriginalMissingException()
                }
                com.google.firebase.functions.FirebaseFunctionsException.Code.FAILED_PRECONDITION -> {
                    throw PostingBannedException()
                }
                else -> throw e
            }
        }
    }
}
