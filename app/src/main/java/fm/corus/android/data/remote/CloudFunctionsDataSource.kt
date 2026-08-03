package fm.corus.android.data.remote

import com.google.firebase.functions.FirebaseFunctions
import fm.corus.android.data.model.*
import fm.corus.android.data.repository.parseUnifiedTrack
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout
import java.util.Date
import java.util.concurrent.ConcurrentHashMap
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
 * Parses a `resolveAudiomackPreview` Cloud Function response into a non-blank
 * preview URL string. Response shape: `{ "previewUrl": "<signed url>" }` — a
 * signed, short-lived URL that streams audio/mp4 (M4A) directly. Callers
 * resolve it at play time and never persist it. Kept top-level so it can be
 * unit-tested without instantiating the data source (mirrors
 * [parseBackCoverResponse]).
 */
internal fun parseAudiomackPreviewResponse(data: Map<String, Any?>?): String? {
    val url = data?.get("previewUrl") as? String
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

/** A premium Taste Matches gate parsed from a `getForYouFeed` payload:
 *  `gated` is "needMorePosts" | "paywall" | "unavailable". */
internal data class TasteMatchesGateInfo(val gated: String, val postCount: Int, val threshold: Int)

/**
 * Parses the Taste Matches gate from a `getForYouFeed` payload, or null when the
 * server returned a normal (un-gated) feed. Top-level + pure so it's unit-tested
 * without mocking FirebaseFunctions (mirrors [parseForYouFeedResponse]).
 */
internal fun parseTasteMatchesGate(data: Map<String, Any?>?): TasteMatchesGateInfo? {
    val gated = data?.get("gated") as? String ?: return null
    val postCount = (data["postCount"] as? Number)?.toInt() ?: 0
    val threshold = (data["threshold"] as? Number)?.toInt() ?: 0
    return TasteMatchesGateInfo(gated, postCount, threshold)
}

/** `getOnboardingTasteMatches` response: taste-aligned people for a brand-new
 *  user, ranked by shared-artist count then activity. [strongCount] = matches
 *  at the "Strong match" tier (>=3 shared); [totalCount] = candidates with >=1
 *  shared, before the page limit. Mirrors web `OnboardingTasteMatchesResult`. */
data class OnboardingTasteMatchesResult(
    val users: List<SuggestedUserMatch> = emptyList(),
    val strongCount: Int = 0,
    val totalCount: Int = 0,
)

/**
 * Parses a `getOnboardingTasteMatches` response. Rows carry the TasteMatchUser
 * shape (identity fields + sharedArtists / sharedArtistNames /
 * sharedDirectorNames / sharedTrackPreviews); this adapts each to a
 * [SuggestedUserMatch] the canonical TasteMatchCard renders unchanged —
 * mirroring web `tasteMatchUserToSuggestedMatch`, which builds matchData
 * unconditionally (unlike the suggestion engine's gated parseUserRows: these
 * rows have no similarityScore, and a 0-preview match must still show its
 * shared-artist subtitle). Top-level + pure so it's unit-tested without
 * mocking FirebaseFunctions (mirrors [parseTasteMatchesGate]).
 */
@Suppress("UNCHECKED_CAST")
internal fun parseOnboardingTasteMatchesResponse(
    data: Map<String, Any?>?,
): OnboardingTasteMatchesResult {
    if (data == null) return OnboardingTasteMatchesResult()
    val rows = data["users"] as? List<Map<String, Any?>> ?: emptyList()
    val users = rows.mapNotNull { row ->
        val uid = row["id"] as? String ?: return@mapNotNull null
        val user = CymbalUser.fromMap(uid, row)
        val previews = (row["sharedTrackPreviews"] as? List<Map<String, Any?>>)?.map { dict ->
            val albumArt = (dict["albumArtURL"] as? String)?.takeIf { it.isNotBlank() }
            val poster = (dict["posterURL"] as? String)?.takeIf { it.isNotBlank() }
            SharedTrackPreview(
                trackId = dict["trackId"] as? String ?: "",
                trackName = dict["trackName"] as? String ?: "",
                artistName = dict["artistName"] as? String ?: "",
                albumArtURL = albumArt,
                posterURL = poster,
                // Same isMovie derivation as web: a poster with no album art.
                isMovie = albumArt == null && poster != null,
            )
        } ?: emptyList()
        SuggestedUserMatch(
            user = user,
            matchData = MusicMatchData(
                sharedArtists = (row["sharedArtists"] as? Number)?.toInt() ?: 0,
                sharedTrackPreviews = previews,
                sharedArtistNames = (row["sharedArtistNames"] as? List<*>)
                    ?.filterIsInstance<String>() ?: emptyList(),
                sharedDirectorNames = (row["sharedDirectorNames"] as? List<*>)
                    ?.filterIsInstance<String>() ?: emptyList(),
            ),
        )
    }
    return OnboardingTasteMatchesResult(
        users = users.filter { !it.user.isBot },
        strongCount = (data["strongCount"] as? Number)?.toInt() ?: 0,
        totalCount = (data["totalCount"] as? Number)?.toInt() ?: 0,
    )
}

/**
 * Whether a feed mode builds its playlist from a live ranked session, so the
 * server can rebuild the exact list the user is scrolling. True for the ranked
 * modes ("trending" and "tasteMatches"); false for "following" / "favorites",
 * which the server reconstructs from durable sources. Forgetting "tasteMatches"
 * here drops the session token from the request, leaving the server with no
 * list to build from — which surfaced as a generic "Something went wrong" on
 * Taste Matches playlist exports.
 */
internal fun feedModeUsesRankedSession(feedMode: String): Boolean =
    feedMode == "trending" || feedMode == "tasteMatches"

/**
 * Extracts the artist id whose name *exactly* matches [name] (trimmed,
 * case-insensitive) from a `searchSongs` response's `artists` array
 * (`[{id, name, imageUrl}]`). Null when there's no exact match — the caller
 * must never guess an id from a fuzzy result. Top-level + pure so it's
 * unit-tested without mocking FirebaseFunctions (mirrors the destination-page
 * parsers below).
 */
internal fun parseArtistIdByNameResponse(data: Map<String, Any?>?, name: String): String? {
    val artists = data?.get("artists") as? List<*> ?: return null
    val want = name.trim().lowercase()
    for (entry in artists) {
        val artist = entry as? Map<*, *> ?: continue
        val id = (artist["id"] as? String)?.takeIf { it.isNotEmpty() } ?: continue
        val candidate = (artist["name"] as? String)?.trim()?.lowercase() ?: continue
        if (candidate == want) return id
    }
    return null
}

// ── Artist / Album / Director destination-page parsers ───────────────────────
// Pure map→model parsers for the six destination callables, kept top-level so
// they can be unit-tested without mocking FirebaseFunctions (same pattern as
// parseBackCoverResponse / parseForYouFeedResponse above). All are defensive:
// unexpected shapes degrade to nulls/empties instead of throwing.

@Suppress("UNCHECKED_CAST")
internal fun parseArtistDetailResponse(data: Map<String, Any?>?): ArtistDetail? {
    val artist = data?.get("artist") as? Map<String, Any?> ?: return null
    val id = (artist["id"] as? String)?.takeIf { it.isNotEmpty() } ?: return null
    val topTracksJson = (data["topTracks"] as? List<Map<String, Any?>>) ?: emptyList()
    return ArtistDetail(
        id = id,
        name = artist["name"] as? String ?: "",
        imageUrl = (artist["imageUrl"] as? String)?.ifEmpty { null },
        genres = (artist["genres"] as? List<*>)?.mapNotNull { it as? String } ?: emptyList(),
        topTracks = topTracksJson.mapNotNull { parseUnifiedTrack(it) },
        albums = (data["albums"] as? List<Map<String, Any?>>)
            ?.mapNotNull { AlbumSummary.fromMap(it) } ?: emptyList(),
        musicVideos = (data["musicVideos"] as? List<Map<String, Any?>>)
            ?.mapNotNull { MusicVideo.fromMap(it) } ?: emptyList(),
        corusUser = (data["corusUser"] as? Map<String, Any?>)?.let { CorusUserLink.fromMap(it) },
        // Per-track share stats (count + facepile) for the Popular rows, keyed by
        // track id. parseUnifiedTrack drops these additive fields, so parse them
        // off the same topTracks payload.
        corusStats = topTracksJson.mapNotNull { TrackCorusStats.entryFromTrack(it) }.toMap(),
    )
}

@Suppress("UNCHECKED_CAST")
internal fun parseAlbumCatalogResponse(data: Map<String, Any?>?): AlbumCatalog? {
    val album = data?.get("album") as? Map<String, Any?> ?: return null
    val id = (album["id"] as? String)?.takeIf { it.isNotEmpty() } ?: return null
    return AlbumCatalog(
        id = id,
        title = album["title"] as? String ?: "",
        artistName = album["artistName"] as? String ?: "",
        artistIds = (album["artistIds"] as? List<*>)?.mapNotNull { it as? String }?.filter { it.isNotEmpty() }
            ?: emptyList(),
        year = (album["year"] as? Number)?.toInt(),
        coverUrl = (album["coverUrl"] as? String)?.ifEmpty { null },
        tracks = (data["tracks"] as? List<Map<String, Any?>>)
            ?.mapNotNull { parseUnifiedTrack(it) } ?: emptyList(),
        releaseDate = (album["releaseDate"] as? String)?.ifEmpty { null },
        isPreRelease = album["isPreRelease"] as? Boolean ?: false,
    )
}

@Suppress("UNCHECKED_CAST")
internal fun parseDirectorDetailResponse(data: Map<String, Any?>?): DirectorDetail? {
    val director = data?.get("director") as? Map<String, Any?> ?: return null
    val id = (director["id"] as? String)?.takeIf { it.isNotEmpty() } ?: return null
    return DirectorDetail(
        id = id,
        name = director["name"] as? String ?: "",
        imageUrl = (director["imageUrl"] as? String)?.ifEmpty { null },
        knownFor = director["knownFor"] as? String ?: "",
        biography = director["biography"] as? String ?: "",
        films = (data["films"] as? List<Map<String, Any?>>)
            ?.mapNotNull { DirectorFilm.fromMap(it) } ?: emptyList(),
        trailers = (data["trailers"] as? List<Map<String, Any?>>)
            ?.mapNotNull { MusicVideo.fromMap(it) } ?: emptyList(),
    )
}

/** getArtistPosts / getDirectorPosts share this response shape. */
@Suppress("UNCHECKED_CAST")
internal fun parseDestinationPostsResponse(
    data: Map<String, Any?>?,
): CloudFunctionsDataSource.DestinationPostsPage {
    if (data == null) return CloudFunctionsDataSource.DestinationPostsPage()
    val posts = (data["posts"] as? List<Map<String, Any?>>)
        ?.map { CymbalPost.fromCloudData(it) } ?: emptyList()
    val posters = (data["posters"] as? List<Map<String, Any?>>)
        ?.mapNotNull { UserLite.fromMap(it) } ?: emptyList()
    val viewerPosts = (data["viewerPosts"] as? List<Map<String, Any?>>)
        ?.map { CymbalPost.fromCloudData(it) } ?: emptyList()
    return CloudFunctionsDataSource.DestinationPostsPage(
        posts = posts,
        uniquePosterCount = (data["uniquePosterCount"] as? Number)?.toInt() ?: 0,
        posters = posters,
        viewerPosts = viewerPosts,
    )
}

@Suppress("UNCHECKED_CAST")
internal fun parseAlbumPostsResponse(
    data: Map<String, Any?>?,
): CloudFunctionsDataSource.AlbumPostsPage {
    if (data == null) return CloudFunctionsDataSource.AlbumPostsPage()
    val posts = (data["posts"] as? List<Map<String, Any?>>)
        ?.map { CymbalPost.fromCloudData(it) } ?: emptyList()
    // Per-track share counts (track id → count). Callable numbers arrive as
    // Number; only tracks with a non-zero count are present.
    val trackShareCounts = (data["trackShareCounts"] as? Map<String, Any?>)
        ?.mapNotNull { (id, value) -> (value as? Number)?.toInt()?.let { id to it } }
        ?.toMap() ?: emptyMap()
    return CloudFunctionsDataSource.AlbumPostsPage(
        posts = posts,
        uniquePosterCount = (data["uniquePosterCount"] as? Number)?.toInt() ?: 0,
        firstPosterId = (data["firstPosterId"] as? String)?.ifEmpty { null },
        trackShareCounts = trackShareCounts,
    )
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
        /** Premium Taste Matches gate: "needMorePosts" | "paywall" |
         *  "unavailable" when the server returns a gated response (no posts),
         *  else null. Lets the feed render the cold-start / paywall state. */
        val gated: String? = null,
        val gatedPostCount: Int = 0,
        val gatedThreshold: Int = 0,
    )

    /**
     * Calls the `getForYouFeed` callable (algorithmically-ranked feed).
     * `sessionToken == null` starts a fresh session; subsequent pages pass
     * the token from the first call along with `pageIndex`. `seenPostIds`
     * is a client-side ring buffer (cap 500) used to suppress already-shown
     * posts when a session expires mid-scroll.
     */
    /**
     * `scope` is "trending" (pool = the whole app) or "tasteMatches" (the
     * premium curator pool). Both use the same ranked callable + pagination
     * machinery. (The callable is named getForYouFeed for historical reasons;
     * the standalone "For You" feed mode was retired in favor of Taste Matches.)
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
        scope: String = "trending",
        isRefresh: Boolean = false,
        releaseDecade: Int? = null,
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
        releaseDecade?.let { params["releaseDecade"] = it }

        val result = functions.getHttpsCallable("getForYouFeed").call(params).await()
        val data = result.getData() as? Map<String, Any?>
        // Premium Taste Matches gate: the server returns {gated:...} with no
        // posts when the caller must subscribe or post more. Surface it so the
        // feed renders the cold-start / paywall state, not a generic empty.
        parseTasteMatchesGate(data)?.let { gate ->
            return ForYouFeedPage(
                emptyList(), false, "", false, gate.gated, gate.postCount, gate.threshold,
            )
        }
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

    /**
     * A page of liked posts plus the server's authoritative [hasMore] flag.
     * [hasMore] is computed server-side from the *ref* count (it survives pages
     * that hydrate fewer posts than requested because some refs point to
     * deleted/banned posts), so callers must use it instead of inferring
     * "more pages" from `posts.size >= limit` — a short page does not mean the
     * end of the list.
     */
    data class LikedPostsPage(val posts: List<CymbalPost>, val hasMore: Boolean)

    @Suppress("UNCHECKED_CAST")
    suspend fun getLikedPosts(userId: String, viewerId: String, limit: Int = 30, offset: Int = 0): LikedPostsPage {
        val params = mutableMapOf<String, Any>(
            "userId" to userId, "viewerId" to viewerId, "limit" to limit, "offset" to offset
        )
        val result = functions.getHttpsCallable("getLikedPosts").call(params).await()
        val data = result.getData() as? Map<String, Any?> ?: return LikedPostsPage(emptyList(), false)
        val posts = (data["posts"] as? List<Map<String, Any?>>)?.map { CymbalPost.fromCloudData(it) } ?: emptyList()
        val hasMore = data["hasMore"] as? Boolean ?: false
        return LikedPostsPage(posts, hasMore)
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

    /** Result of a favoritePerson callable invocation. */
    data class FavoritePersonResult(val favoritesCount: Int, val alreadyFavorited: Boolean)

    /** Thrown when the server rejects a favorite because the user has hit the free cap. */
    class FavoriteCapReachedException(val favoritesCount: Int) : Exception("FAVORITE_CAP_REACHED")

    /**
     * Favorite a person via the `favoritePerson` callable. Server enforces the
     * favorite-people cap and maintains `favoritesCount`. Mirrors [savePost].
     */
    @Suppress("UNCHECKED_CAST")
    suspend fun favoritePerson(targetUserId: String): FavoritePersonResult {
        try {
            val result = functions.getHttpsCallable("favoritePerson")
                .call(mapOf("targetUserId" to targetUserId)).await()
            val data = result.getData() as? Map<String, Any?> ?: emptyMap()
            val count = (data["favoritesCount"] as? Number)?.toInt() ?: 0
            val already = data["alreadyFavorited"] as? Boolean ?: false
            return FavoritePersonResult(favoritesCount = count, alreadyFavorited = already)
        } catch (e: com.google.firebase.functions.FirebaseFunctionsException) {
            if (e.code == com.google.firebase.functions.FirebaseFunctionsException.Code.RESOURCE_EXHAUSTED) {
                val details = e.details as? Map<String, Any?>
                val count = (details?.get("favoritesCount") as? Number)?.toInt() ?: 0
                throw FavoriteCapReachedException(count)
            }
            throw e
        }
    }

    /** Unfavorite a person via the `unfavoritePerson` callable. Returns the new favoritesCount. */
    @Suppress("UNCHECKED_CAST")
    suspend fun unfavoritePerson(targetUserId: String): Int {
        val result = functions.getHttpsCallable("unfavoritePerson")
            .call(mapOf("targetUserId" to targetUserId)).await()
        val data = result.getData() as? Map<String, Any?> ?: emptyMap()
        return (data["favoritesCount"] as? Number)?.toInt() ?: 0
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

    data class MutualFollowersPage(
        val users: List<CymbalUser>,
        val nextCursor: String?,
        // Total mutual count, capped server-side. Only meaningful on the first
        // page (cursor == null); -1 afterwards.
        val mutualCount: Int,
        val mutualCountCapped: Boolean,
    )

    /**
     * Instagram-style "Mutual" tab ("Followed by people you follow"): accounts
     * the signed-in viewer follows who also follow [profileId]
     * (viewer.following ∩ profile.followers). Server-side intersection via the
     * `getMutualFollowers` callable so every client shares one definition;
     * empty on your own profile. Pass [cursor] from a prior page to paginate.
     */
    @Suppress("UNCHECKED_CAST")
    suspend fun fetchMutualFollowers(
        profileId: String,
        cursor: String? = null,
        limit: Int = 20,
    ): MutualFollowersPage {
        val params = mutableMapOf<String, Any>("profileId" to profileId, "limit" to limit)
        cursor?.let { params["cursor"] = it }
        val result = functions.getHttpsCallable("getMutualFollowers").call(params).await()
        val data = result.getData() as? Map<String, Any?>
            ?: return MutualFollowersPage(emptyList(), null, 0, false)
        val rows = data["users"] as? List<Map<String, Any?>> ?: emptyList()
        val users = rows.mapNotNull { row ->
            val id = row["id"] as? String ?: return@mapNotNull null
            if (id.isEmpty()) null else CymbalUser.fromMap(id, row)
        }
        return MutualFollowersPage(
            users = users,
            nextCursor = data["nextCursor"] as? String,
            mutualCount = (data["mutualCount"] as? Number)?.toInt() ?: 0,
            mutualCountCapped = data["mutualCountCapped"] as? Boolean ?: false,
        )
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
        isrc: String? = null,
        trackName: String? = null,
        artistName: String? = null,
        pageSize: Int = 15,
        beforeMs: Long? = null,
    ): SongPostsPage {
        val params = mutableMapOf<String, Any>("trackId" to trackId, "pageSize" to pageSize)
        if (!spotifyURI.isNullOrBlank()) params["spotifyURI"] = spotifyURI
        // ISRC lets getSongPosts match the SAME recording across store IDs — an
        // Apple-catalog track (e.g. an artist-page "Popular" row, `am:` id, no
        // spotifyURI) whose posts were made from Spotify. Without it, matching
        // falls back to the store-specific trackId and the page wrongly reads
        // "no one has posted this song yet."
        if (!isrc.isNullOrBlank()) params["isrc"] = isrc
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

    /**
     * One page of inbox threads plus the cursor for the next page. [nextCursor] is
     * the `updatedAt` of the last thread scanned (millis) — pass it back as
     * `startAfter`. [hasMore] is authoritative from the server: block/ban filtering
     * can leave [threads] shorter than the page size, so don't infer it from size.
     */
    data class ThreadListPage(
        val threads: List<CymbalThread>,
        val nextCursor: Long? = null,
        val hasMore: Boolean = false,
    )

    @Suppress("UNCHECKED_CAST")
    suspend fun listThreadsPage(userId: String, limit: Int = 30, startAfter: Long? = null): ThreadListPage {
        val params = mutableMapOf<String, Any>("userId" to userId, "limit" to limit)
        startAfter?.let { params["startAfter"] = it }
        val result = functions.getHttpsCallable("listThreads").call(params).await()
        val data = result.getData() as? Map<String, Any?> ?: return ThreadListPage(emptyList())
        val threads = (data["threads"] as? List<Map<String, Any?>> ?: emptyList())
            .map { CymbalThread.fromMap(it["id"] as? String ?: "", it) }
        val nextCursor = (data["nextCursor"] as? Number)?.toLong()
        val hasMore = data["hasMore"] as? Boolean ?: false
        return ThreadListPage(threads, nextCursor, hasMore)
    }

    suspend fun listThreads(userId: String): List<CymbalThread> =
        listThreadsPage(userId).threads

    /**
     * Server-side DM inbox search. The inbox paginates threads in, so the local
     * filter only sees what's already loaded. This searches the caller's full
     * thread history (username, display name, last-message text) and reuses the
     * `listThreads` thread shape. Returns threads ordered by recency.
     */
    @Suppress("UNCHECKED_CAST")
    suspend fun searchThreads(userId: String, query: String, limit: Int = 30): List<CymbalThread> {
        val params = mapOf<String, Any>("userId" to userId, "query" to query, "limit" to limit)
        val result = functions.getHttpsCallable("searchThreads").call(params).await()
        val data = result.getData() as? Map<String, Any?> ?: return emptyList()
        return (data["threads"] as? List<Map<String, Any?>> ?: emptyList())
            .map { CymbalThread.fromMap(it["id"] as? String ?: "", it) }
    }

    /**
     * People the user deliberately shares posts/songs/films with, ranked by a
     * server-side recency+frequency blend. Higher-intent than DM recency (which is
     * noise for accounts that message everyone) — used to seed the share sheet.
     */
    @Suppress("UNCHECKED_CAST")
    suspend fun listShareRecipients(limit: Int = 12): List<CymbalUser> {
        val result = functions.getHttpsCallable("listShareRecipients")
            .call(mapOf("limit" to limit)).await()
        val data = result.getData() as? Map<String, Any?> ?: return emptyList()
        val recipients = data["recipients"] as? List<Map<String, Any?>> ?: return emptyList()
        return recipients.mapNotNull { row ->
            val userMap = row["otherUser"] as? Map<String, Any?> ?: return@mapNotNull null
            val otherUserId = row["otherUserId"] as? String ?: ""
            CymbalUser.fromMap(otherUserId, userMap)
        }
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
        artistId: String? = null,
        artistImageURL: String? = null,
        albumId: String? = null,
        albumTitle: String? = null,
        albumArtistName: String? = null,
        albumCoverURL: String? = null,
        albumYear: String? = null,
        directorId: String? = null,
        directorImageURL: String? = null,
        sharedUserId: String? = null,
        sharedUsername: String? = null,
        sharedDisplayName: String? = null,
        sharedAvatarURL: String? = null,
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
        artistId?.let { params["artistId"] = it }
        artistImageURL?.let { params["artistImageURL"] = it }
        albumId?.let { params["albumId"] = it }
        albumTitle?.let { params["albumTitle"] = it }
        albumArtistName?.let { params["albumArtistName"] = it }
        albumCoverURL?.let { params["albumCoverURL"] = it }
        albumYear?.let { params["albumYear"] = it }
        directorId?.let { params["directorId"] = it }
        directorImageURL?.let { params["directorImageURL"] = it }
        sharedUserId?.let { params["sharedUserId"] = it }
        sharedUsername?.let { params["sharedUsername"] = it }
        sharedDisplayName?.let { params["sharedDisplayName"] = it }
        sharedAvatarURL?.let { params["sharedAvatarURL"] = it }
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

    /** Edit one of the caller's own messages. Server-enforced: author-only, text
     *  messages only, within a 15-minute window. On success the message gains an
     *  `editedAt`, surfaced as an "edited" indicator. */
    suspend fun editMessage(threadId: String, messageId: String, text: String) {
        functions.getHttpsCallable("editMessage").call(
            mapOf("threadId" to threadId, "messageId" to messageId, "text" to text)
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

    // ── Group messaging ──

    data class GroupAddResult(
        val added: List<String>,
        val rejected: List<Pair<String, String>>,
    )

    @Suppress("UNCHECKED_CAST")
    suspend fun createGroupThread(participantIds: List<String>, name: String? = null, photoURL: String? = null): String {
        val params = mutableMapOf<String, Any>("participantIds" to participantIds)
        name?.takeIf { it.isNotBlank() }?.let { params["name"] = it }
        photoURL?.let { params["photoURL"] = it }
        val result = functions.getHttpsCallable("createGroupThread").call(params).await()
        val data = result.getData() as? Map<String, Any?> ?: throw Exception("Failed to create group")
        return data["threadId"] as? String ?: throw Exception("No threadId returned")
    }

    @Suppress("UNCHECKED_CAST")
    suspend fun addGroupMembers(threadId: String, userIds: List<String>): GroupAddResult {
        val result = functions.getHttpsCallable("addGroupMembers").call(
            mapOf("threadId" to threadId, "userIds" to userIds)
        ).await()
        val data = result.getData() as? Map<String, Any?> ?: return GroupAddResult(emptyList(), emptyList())
        val added = (data["added"] as? List<*>)?.filterIsInstance<String>() ?: emptyList()
        val rejected = (data["rejected"] as? List<Map<String, Any?>> ?: emptyList()).mapNotNull {
            val id = it["id"] as? String ?: return@mapNotNull null
            id to (it["reason"] as? String ?: "")
        }
        return GroupAddResult(added, rejected)
    }

    suspend fun removeGroupMember(threadId: String, userId: String) {
        functions.getHttpsCallable("removeGroupMember").call(
            mapOf("threadId" to threadId, "userId" to userId)
        ).await()
    }

    suspend fun leaveGroup(threadId: String) {
        functions.getHttpsCallable("leaveGroup").call(mapOf("threadId" to threadId)).await()
    }

    suspend fun renameGroup(threadId: String, name: String) {
        functions.getHttpsCallable("renameGroup").call(
            mapOf("threadId" to threadId, "name" to name)
        ).await()
    }

    suspend fun setGroupPhoto(threadId: String, photoURL: String) {
        functions.getHttpsCallable("setGroupPhoto").call(
            mapOf("threadId" to threadId, "photoURL" to photoURL)
        ).await()
    }

    /** ok=false is a hard block (bot/blocked/etc.); ok=true+soft=true means addable
     *  but on a not-yet-updated build (the picker shows a soft hint). */
    data class GroupAddability(val ok: Boolean, val soft: Boolean)

    /** Pre-check which users can be added to a group. Fails open. */
    @Suppress("UNCHECKED_CAST")
    suspend fun checkGroupAddable(userIds: List<String>, threadId: String? = null): Map<String, GroupAddability> {
        val params = mutableMapOf<String, Any>("userIds" to userIds)
        threadId?.let { params["threadId"] = it }
        val result = functions.getHttpsCallable("checkGroupAddable").call(params).await()
        val data = result.getData() as? Map<String, Any?> ?: return emptyMap()
        val addable = data["addable"] as? Map<String, Any?> ?: return emptyMap()
        return addable.mapValues { (_, v) ->
            val entry = v as? Map<String, Any?>
            GroupAddability(
                ok = entry?.get("ok") as? Boolean ?: false,
                soft = entry?.get("soft") as? Boolean ?: false,
            )
        }
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
        includeArtists: Boolean = false,
        includeAlbums: Boolean = false,
        albumsMatchArtist: Boolean = false,
        collapse: String = "recording",
    ): Map<String, Any?> {
        // `supports` declares which result sources this client can render.
        // Backend uses it to gate Apple-Music-only catalog results — old
        // builds without this field get the pre-Apple behavior (Spotify +
        // SoundCloud only), so shipping this is back-compat-safe. The
        // server also gates Apple results on a Remote Config flag /
        // per-UID allowlist; declaring capability here doesn't bypass that.
        val params = mutableMapOf<String, Any>(
            "query" to query,
            "offset" to offset,
            "limit" to limit,
            "market" to market,
            "includeSoundCloud" to includeSoundCloud,
            "supports" to listOf("spotify", "soundcloud", "applemusic", "audiomack"),
            // "recording" (default; one row per recording, for search) vs
            // "cover" (keeps alternate album covers pickable, for the compose
            // picker). Absent-value default matches the backend, so search rows
            // are unaffected.
            "collapse" to collapse,
        )
        // Artist/album rows for the destination pages feature. Attached ONLY
        // when requested (artist_pages_enabled on) — with the keys absent the
        // backend response is byte-identical to today's, so flag-off clients
        // can't be affected by the extension.
        if (includeArtists) params["includeArtists"] = true
        if (includeAlbums) params["includeAlbums"] = true
        // Album PICKERS only: also keep albums whose ARTIST matches the query.
        // The search tab's rule is title-only on purpose (an artist query shows
        // the artist row, not their discography), which left a picker's Albums
        // tab empty for "arcade fire". Absent → today's album list.
        if (includeAlbums && albumsMatchArtist) params["albumsMatchArtist"] = true
        val result = functions.getHttpsCallable("searchSongs").call(params).await()
        return result.getData() as? Map<String, Any?> ?: emptyMap()
    }

    /**
     * Resolve a Spotify artist id by *exact* (trimmed, case-insensitive) name
     * match via `searchSongs`' includeArtists extension. Backs the song page's
     * artist-line fallback for tracks with no artist ids anywhere
     * (Apple-sourced tracks, legacy posts) so the line is still tappable when
     * artist_pages_enabled is on. Null on no exact match / error — the caller
     * degrades to plain text rather than guessing. Mirrors iOS
     * `DestinationPagesService.resolveArtistIdByName`.
     */
    @Suppress("UNCHECKED_CAST")
    suspend fun resolveArtistIdByName(name: String): String? {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return null
        return try {
            val result = functions.getHttpsCallable("searchSongs").call(
                mapOf(
                    "query" to trimmed,
                    "offset" to 0,
                    "limit" to 1,
                    "includeArtists" to true,
                )
            ).await()
            parseArtistIdByNameResponse(result.getData() as? Map<String, Any?>, trimmed)
        } catch (_: Exception) {
            null
        }
    }

    /** Spotify artist ids + album id for a track that reached the client without
     *  them (Apple-sourced tracks, older posts). Backed by the
     *  `resolveTrackDestinations` callable, which caches the result server-side
     *  keyed by ISRC so the first tap on a song resolves it for everyone. One
     *  call serves both the "Go to Artist" and "Go to Album" rows; empty on any
     *  miss. Mirrors iOS DestinationPagesService.resolveTrackDestinations. */
    data class TrackDestinations(
        val artistIds: List<String> = emptyList(),
        val albumId: String? = null,
        val goToAlbumAsSong: Boolean = false,
    )

    suspend fun resolveTrackDestinations(
        trackId: String,
        isrc: String?,
        name: String,
        artist: String,
        appleMusicId: String? = null,
    ): TrackDestinations {
        return try {
            val params = mutableMapOf<String, Any>(
                "trackId" to trackId,
                "name" to name,
                "artist" to artist,
            )
            if (!isrc.isNullOrBlank()) params["isrc"] = isrc
            if (!appleMusicId.isNullOrBlank()) params["appleMusicId"] = appleMusicId
            val result = functions.getHttpsCallable("resolveTrackDestinations").call(params).await()
            val data = result.getData() as? Map<*, *>
            val artistIds = (data?.get("artistIds") as? List<*>)
                ?.mapNotNull { (it as? String)?.takeIf { s -> s.isNotBlank() } }
                ?: emptyList()
            val albumId = (data?.get("albumId") as? String)?.takeIf { it.isNotBlank() }
            val goToAlbumAsSong = data?.get("goToAlbumAsSong") as? Boolean ?: false
            TrackDestinations(artistIds, albumId, goToAlbumAsSong)
        } catch (_: Exception) {
            TrackDestinations()
        }
    }

    /** TMDB director person ids for a film that reached the client without them
     *  (stub posts, older flows). Backed by the `resolveFilmDirectors` callable,
     *  which resolves the movie's credits from TMDB and caches the result
     *  server-side keyed by movieId — so the first "Go to Director" tap resolves
     *  it for everyone. Empty on a miss. Mirrors [resolveTrackDestinations]. */
    suspend fun resolveFilmDirectors(movieId: String): List<String> {
        return try {
            val result = functions.getHttpsCallable("resolveFilmDirectors")
                .call(mapOf("movieId" to movieId)).await()
            val data = result.getData() as? Map<*, *>
            (data?.get("directorIds") as? List<*>)
                ?.mapNotNull { (it as? String)?.takeIf { s -> s.isNotBlank() } }
                ?: emptyList()
        } catch (_: Exception) {
            emptyList()
        }
    }

    // ── Artist / Album / Director destination pages ───────────────────────────
    // Six read-only callables backing the artist/album/director pages (feature-
    // flagged behind `artist_pages_enabled`; the backend is not flag-gated).
    // Catalog responses are cached in-memory per id (~5 min) so pushing from a
    // page to its see-all screen doesn't refetch — the server's own SWR caches
    // handle everything beyond that.

    /** getArtistPosts / getDirectorPosts response (identical shapes). */
    data class DestinationPostsPage(
        val posts: List<CymbalPost> = emptyList(),
        val uniquePosterCount: Int = 0,
        val posters: List<UserLite> = emptyList(),
        val viewerPosts: List<CymbalPost> = emptyList(),
    )

    /** getAlbumPosts response. Posts arrive with isFirstPoster already stamped
     *  server-side against [firstPosterId]. [trackShareCounts] maps a catalog
     *  track id → how many Corus users shared it, for the album tracklist's
     *  trailing "N shared" slot; only tracks with a non-zero count are present. */
    data class AlbumPostsPage(
        val posts: List<CymbalPost> = emptyList(),
        val uniquePosterCount: Int = 0,
        val firstPosterId: String? = null,
        val trackShareCounts: Map<String, Int> = emptyMap(),
    )

    private val artistDetailCache = ConcurrentHashMap<String, Pair<Long, ArtistDetail>>()
    private val albumCatalogCache = ConcurrentHashMap<String, Pair<Long, AlbumCatalog>>()
    private val directorDetailCache = ConcurrentHashMap<String, Pair<Long, DirectorDetail>>()

    private fun <T> cachedCatalog(cache: ConcurrentHashMap<String, Pair<Long, T>>, id: String): T? {
        val entry = cache[id] ?: return null
        if (System.currentTimeMillis() - entry.first > DESTINATION_CATALOG_CACHE_TTL_MS) {
            cache.remove(id)
            return null
        }
        return entry.second
    }

    @Suppress("UNCHECKED_CAST")
    suspend fun fetchArtistDetail(artistId: String, artistName: String? = null): ArtistDetail {
        cachedCatalog(artistDetailCache, artistId)?.let { return it }
        val params = mutableMapOf<String, Any>("artistId" to artistId)
        if (!artistName.isNullOrBlank()) params["artistName"] = artistName
        val result = functions.getHttpsCallable("getArtistDetail").call(params).await()
        val detail = parseArtistDetailResponse(result.getData() as? Map<String, Any?>)
            ?: throw Exception("Invalid getArtistDetail response")
        artistDetailCache[artistId] = System.currentTimeMillis() to detail
        return detail
    }

    @Suppress("UNCHECKED_CAST")
    suspend fun fetchArtistPosts(
        artistId: String,
        artistName: String? = null,
        pageSize: Int = 15,
        beforeMs: Long? = null,
        postersLimit: Int = 24,
        includeViewerPosts: Boolean = false,
    ): DestinationPostsPage {
        val params = mutableMapOf<String, Any>(
            "artistId" to artistId,
            "pageSize" to pageSize,
            "postersLimit" to postersLimit,
        )
        if (!artistName.isNullOrBlank()) params["artistName"] = artistName
        beforeMs?.let { params["beforeMs"] = it }
        if (includeViewerPosts) params["includeViewerPosts"] = true
        val result = functions.getHttpsCallable("getArtistPosts").call(params).await()
        return parseDestinationPostsResponse(result.getData() as? Map<String, Any?>)
    }

    /** [albumId] may be a Spotify album id OR `am:{appleAlbumId}` — always
     *  passed through untouched; the backend branches on the prefix. */
    @Suppress("UNCHECKED_CAST")
    suspend fun fetchAlbumCatalog(albumId: String): AlbumCatalog {
        cachedCatalog(albumCatalogCache, albumId)?.let { return it }
        val result = functions.getHttpsCallable("getAlbumCatalog")
            .call(mapOf("albumId" to albumId)).await()
        val catalog = parseAlbumCatalogResponse(result.getData() as? Map<String, Any?>)
            ?: throw Exception("Invalid getAlbumCatalog response")
        albumCatalogCache[albumId] = System.currentTimeMillis() to catalog
        return catalog
    }

    @Suppress("UNCHECKED_CAST")
    suspend fun fetchAlbumPosts(
        albumId: String,
        pageSize: Int = 15,
        beforeMs: Long? = null,
    ): AlbumPostsPage {
        val params = mutableMapOf<String, Any>("albumId" to albumId, "pageSize" to pageSize)
        beforeMs?.let { params["beforeMs"] = it }
        val result = functions.getHttpsCallable("getAlbumPosts").call(params).await()
        return parseAlbumPostsResponse(result.getData() as? Map<String, Any?>)
    }

    /** getReposters: the people who reposted [postId], each as their own repost
     *  (a real post carrying repostedFromPostId), newest first. Reuses
     *  [DestinationPostsPage] — `uniquePosterCount` carries the live total repost
     *  count for the header; `posters`/`viewerPosts` are unused here. See the
     *  backend getReposters + RepostersBottomSheet. */
    @Suppress("UNCHECKED_CAST")
    suspend fun fetchReposters(
        postId: String,
        pageSize: Int = 20,
        beforeMs: Long? = null,
    ): DestinationPostsPage {
        val params = mutableMapOf<String, Any>("postId" to postId, "pageSize" to pageSize)
        beforeMs?.let { params["beforeMs"] = it }
        val result = functions.getHttpsCallable("getReposters").call(params).await()
        val data = result.getData() as? Map<String, Any?> ?: return DestinationPostsPage()
        val posts = (data["posts"] as? List<Map<String, Any?>>)
            ?.map { CymbalPost.fromCloudData(it) } ?: emptyList()
        return DestinationPostsPage(
            posts = posts,
            uniquePosterCount = (data["totalCount"] as? Number)?.toInt() ?: posts.size,
        )
    }

    @Suppress("UNCHECKED_CAST")
    suspend fun fetchDirectorDetail(directorId: String): DirectorDetail {
        cachedCatalog(directorDetailCache, directorId)?.let { return it }
        val result = functions.getHttpsCallable("getDirectorDetail")
            .call(mapOf("directorId" to directorId)).await()
        val detail = parseDirectorDetailResponse(result.getData() as? Map<String, Any?>)
            ?: throw Exception("Invalid getDirectorDetail response")
        directorDetailCache[directorId] = System.currentTimeMillis() to detail
        return detail
    }

    @Suppress("UNCHECKED_CAST")
    suspend fun fetchDirectorPosts(
        directorId: String,
        directorName: String? = null,
        pageSize: Int = 15,
        beforeMs: Long? = null,
        postersLimit: Int = 24,
        includeViewerPosts: Boolean = false,
    ): DestinationPostsPage {
        val params = mutableMapOf<String, Any>(
            "directorId" to directorId,
            "pageSize" to pageSize,
            "postersLimit" to postersLimit,
        )
        if (!directorName.isNullOrBlank()) params["directorName"] = directorName
        beforeMs?.let { params["beforeMs"] = it }
        if (includeViewerPosts) params["includeViewerPosts"] = true
        val result = functions.getHttpsCallable("getDirectorPosts").call(params).await()
        return parseDestinationPostsResponse(result.getData() as? Map<String, Any?>)
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

    // ── Audiomack ──

    /**
     * Resolves a fresh, signed ~30s preview URL for an Audiomack track. The URL
     * streams audio/mp4 (M4A) directly and is short-lived — resolve it at
     * playback time, never at post-creation time, and never persist it (mirrors
     * [soundcloudResolveStream], and the Apple preview lookup [appleMusicLookup]).
     * Returns null when the server sends no usable url; the caller (NowPlaying)
     * wraps this in try/catch and degrades to the Audiomack link-out on failure.
     */
    @Suppress("UNCHECKED_CAST")
    suspend fun resolveAudiomackPreview(audiomackId: String): String? {
        val result = functions.getHttpsCallable("resolveAudiomackPreview").call(
            mapOf("audiomackId" to audiomackId)
        ).await()
        return parseAudiomackPreviewResponse(result.getData() as? Map<String, Any?>)
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
        // Cap the wait so a slow/hung backend can't leave the Search taste-match
        // loader spinning forever (mirrors iOS's 15s race in DatabaseService).
        // withTimeout THROWS on expiry rather than returning empty, so a transient
        // hang never poisons the repository's 4h cache with an empty result — the
        // caller catches it and shows the existing/empty state instead.
        val result = withTimeout(SUGGESTED_USERS_TIMEOUT_MS) {
            functions.getHttpsCallable("getSuggestedUsers").call(
                mapOf("currentUserId" to userId, "limit" to limit, "includeFollowing" to includeFollowing)
            ).await()
        }
        val data = result.getData() as? Map<String, Any?> ?: return emptyList()
        val rows = data["users"] as? List<Map<String, Any?>> ?: return emptyList()
        return parseUserRows(rows).filter { !it.user.isBot }
    }

    /** One page of the LIVE taste-matches list. The backend resolves the uid from
     *  auth; pass the prior page's nextCursor to load the next 15. */
    @Suppress("UNCHECKED_CAST")
    suspend fun getTasteMatchesPage(limit: Int = 15, cursor: String? = null): TasteMatchesPage {
        val params = mutableMapOf<String, Any>("limit" to limit.coerceIn(1, 30))
        cursor?.let { params["cursor"] = it }
        val result = withTimeout(SUGGESTED_USERS_TIMEOUT_MS) {
            functions.getHttpsCallable("getTasteMatchesPage").call(params).await()
        }
        val data = result.getData() as? Map<String, Any?>
            ?: return TasteMatchesPage(emptyList(), null, false)
        val rows = data["users"] as? List<Map<String, Any?>> ?: emptyList()
        return TasteMatchesPage(
            matches = parseUserRows(rows).filter { !it.user.isBot },
            nextCursor = data["nextCursor"] as? String,
            hasMore = data["hasMore"] as? Boolean ?: false,
        )
    }

    /**
     * Onboarding taste matches: hand the backend a few picks from a brand-new
     * user (who hasn't posted yet) and get back taste-aligned people to follow,
     * ranked by shared-artist count then activity (NOT the feed's
     * library-discounted strength, which floats one-post ghosts). No hard >=3
     * gate — the client tiers the result. [picks] is the
     * [fm.corus.android.data.model.quizPicksToTastePicks] payload. Mirrors web
     * `getOnboardingTasteMatches` (lib/firestore/onboarding-taste.ts).
     */
    @Suppress("UNCHECKED_CAST")
    suspend fun getOnboardingTasteMatches(
        picks: List<Map<String, Any?>>,
        limit: Int = 20,
        minSharedArtists: Int = 1,
    ): OnboardingTasteMatchesResult {
        if (picks.isEmpty()) return OnboardingTasteMatchesResult()
        val result = withTimeout(SUGGESTED_USERS_TIMEOUT_MS) {
            functions.getHttpsCallable("getOnboardingTasteMatches").call(
                mapOf(
                    "picks" to picks,
                    "limit" to limit,
                    "minSharedArtists" to minSharedArtists,
                )
            ).await()
        }
        return parseOnboardingTasteMatchesResponse(result.getData() as? Map<String, Any?>)
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

    /**
     * Server-authoritative "NEW ON CORUS" rail. Filters shadow + hard banned
     * users server-side (getBannedUserIds) — which the old client-side
     * cachedBannedSet filter couldn't do reliably (it races the ban-list sync
     * and leaks the shadow list to clients). Returns (users, nextCursor).
     */
    @Suppress("UNCHECKED_CAST")
    suspend fun getNewUsers(
        limit: Int = 10,
        excludeIds: Set<String> = emptySet(),
        afterUserId: String? = null,
    ): Pair<List<CymbalUser>, String?> {
        val params = mutableMapOf<String, Any>(
            "limit" to limit,
            "excludeIds" to excludeIds.toList(),
        )
        afterUserId?.let { params["afterUserId"] = it }
        val result = functions.getHttpsCallable("getNewUsers").call(params).await()
        val data = result.getData() as? Map<String, Any?> ?: return Pair(emptyList(), null)
        val rows = data["users"] as? List<Map<String, Any?>> ?: emptyList()
        val users = rows.mapNotNull { row ->
            val uid = row["id"] as? String ?: return@mapNotNull null
            CymbalUser.fromMap(uid, row)
        }
        val next = data["nextCursor"] as? String
        return Pair(users, next)
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
        // Upper bound on the getSuggestedUsers callable so the Search loader can
        // never hang indefinitely on a slow/cold backend (matches iOS's 15s race).
        private const val SUGGESTED_USERS_TIMEOUT_MS = 15_000L

        // Client-side TTL for the artist/album/director catalog caches. Short —
        // it only needs to cover a page → see-all push; the server's own SWR
        // caches (30m artist/director, 7d album) do the real work.
        private const val DESTINATION_CATALOG_CACHE_TTL_MS = 5 * 60 * 1000L

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

        /** Request payload for the `generateHashtagPlaylist` callable. Shared by
         *  the Spotify path and the client-side tracks path so the wire shape
         *  stays pinned in one place (and testable — the backend key names are
         *  exact-match, so a silent rename would regress the feature, not crash). */
        internal fun hashtagPlaylistPayload(
            hashtag: String,
            fullExport: Boolean,
            appleMusicTracks: Boolean,
        ): Map<String, Any> {
            val payload = mutableMapOf<String, Any>(
                "hashtag" to hashtag,
                "supportsPlaylistGating" to true,
            )
            if (appleMusicTracks) payload["appleMusicTracks"] = true
            if (fullExport) payload["fullExport"] = true
            return payload
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
        // Lifts the backend's 75-track snapshot cap to export the whole source.
        fullExport: Boolean = false,
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
        if (fullExport) payload["fullExport"] = true
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
        // Lifts the backend's 75-track snapshot cap to export the whole source.
        fullExport: Boolean = false,
    ): PlaylistTracksOutcome {
        val payload = mutableMapOf<String, Any>(
            "userId" to userId,
            "supportsPlaylistGating" to true,
            "appleMusicTracks" to true,
        )
        if (source != ProfilePlaylistSource.Posts) payload["source"] = source.wire
        if (fullExport) payload["fullExport"] = true
        val result = functions.getHttpsCallable("generateProfilePlaylist").call(payload).await()
        val data = result.getData() as? Map<String, Any?> ?: return PlaylistTracksOutcome.Failure(0)
        return parsePlaylistTracksResponse(data)
    }

    /** Hashtag-page playlist: the server builds a Spotify playlist from the
     *  tag's track posts (newest first, deduped, film posts excluded
     *  server-side). Mirrors [generateProfilePlaylist]. */
    @Suppress("UNCHECKED_CAST")
    suspend fun generateHashtagPlaylist(
        hashtag: String,
        // Lifts the backend's 75-track snapshot cap to export the whole tag.
        fullExport: Boolean = false,
    ): PlaylistResult {
        val result = functions.getHttpsCallable("generateHashtagPlaylist")
            .call(hashtagPlaylistPayload(hashtag, fullExport, appleMusicTracks = false)).await()
        val data = result.getData() as? Map<String, Any?> ?: throw Exception("Invalid response")
        return parsePlaylistResponse(data)
    }

    /** TIDAL variant of [generateHashtagPlaylist]: returns resolved track
     *  descriptors for client-side playlist building on the user's account. */
    @Suppress("UNCHECKED_CAST")
    suspend fun generateHashtagPlaylistTracks(
        hashtag: String,
        // Lifts the backend's 75-track snapshot cap to export the whole tag.
        fullExport: Boolean = false,
    ): PlaylistTracksOutcome {
        val result = functions.getHttpsCallable("generateHashtagPlaylist")
            .call(hashtagPlaylistPayload(hashtag, fullExport, appleMusicTracks = true)).await()
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

    /** Result of [spotifyTrackLookup]. [found] == false means the backend
     *  CONFIRMED the track isn't on Spotify (caller may remember it); a transient
     *  error surfaces as a thrown exception, not this type. */
    data class SpotifyLookupResult(val found: Boolean, val webUrl: String?, val spotifyUri: String? = null)

    /** Resolve an Apple/SoundCloud-sourced track to its Spotify OPEN target (the
     *  reverse of [appleMusicLinkOutUrl]). ISRC-cache-first server-side, so the
     *  common call costs zero Spotify quota. Backs the mini-player "open in
     *  Spotify" tap under Apple-primary search. */
    @Suppress("UNCHECKED_CAST")
    suspend fun spotifyTrackLookup(name: String, artist: String, isrc: String?, appleTrackId: String?): SpotifyLookupResult {
        val result = functions.getHttpsCallable("spotifyTrackLookup").call(linkOutParams(name, artist, isrc, appleTrackId)).await()
        val data = result.getData() as? Map<String, Any?> ?: return SpotifyLookupResult(false, null)
        val webUrl = (data["spotifyWebURL"] as? String)?.ifEmpty { null }
        val spotifyUri = (data["spotifyURI"] as? String)?.ifEmpty { null }
        val found = (data["found"] as? Boolean ?: false) && (webUrl != null || spotifyUri != null)
        return SpotifyLookupResult(found, webUrl, spotifyUri)
    }

    @Suppress("UNCHECKED_CAST")
    /**
     * @param feedMode Which feed the user is viewing ("following" / "trending" /
     *   "tasteMatches" / "favorites"). Drives both the playlist's source and its
     *   name on the server. Defaults to "following" for back-compat.
     * @param sessionToken The live ranked-session token the user is scrolling,
     *   so the server builds the playlist from the exact list shown. Meaningful
     *   for the ranked modes ("trending" and "tasteMatches"); ignored otherwise.
     */
    suspend fun generateFeedPlaylist(
        newReleasesOnly: Boolean = false,
        feedMode: String = "following",
        sessionToken: String? = null,
    ): PlaylistResult {
        val params = mutableMapOf<String, Any>("supportsGating" to true, "feedMode" to feedMode)
        if (newReleasesOnly) params["newReleasesOnly"] = true
        if (feedModeUsesRankedSession(feedMode) && !sessionToken.isNullOrEmpty())
            params["sessionToken"] = sessionToken
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
        if (feedModeUsesRankedSession(feedMode) && !sessionToken.isNullOrEmpty())
            params["sessionToken"] = sessionToken
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

    /** Thrown when proactive UGC moderation rejects the caption
     *  (INVALID_ARGUMENT + details.moderationBlocked). Dark until the
     *  server's ugc_text_moderation_enabled Remote Config flag flips.
     *  Mirrors iOS `PostService.CreatePostError.captionBlocked`. */
    class CaptionBlockedException : Exception("CAPTION_BLOCKED")

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
                    // Share-input resolution miss (tidal/deezer with no
                    // catalog match) rides NOT_FOUND too — don't misreport it
                    // as a missing repost original.
                    if (e.message?.contains("no_catalog_match") == true) throw e
                    throw RepostOriginalMissingException()
                }
                com.google.firebase.functions.FirebaseFunctionsException.Code.FAILED_PRECONDITION -> {
                    // The share unreleased gate rides FAILED_PRECONDITION too.
                    if (e.message?.contains("unreleased") == true) throw e
                    throw PostingBannedException()
                }
                com.google.firebase.functions.FirebaseFunctionsException.Code.INVALID_ARGUMENT -> {
                    // INVALID_ARGUMENT also rides plain validation failures
                    // ("caption too long") — pivot on the details flag.
                    val details = e.details as? Map<String, Any?>
                    if (details?.get("moderationBlocked") == true) throw CaptionBlockedException()
                    throw e
                }
                else -> throw e
            }
        }
    }
}
