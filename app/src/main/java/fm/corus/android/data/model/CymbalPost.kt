package fm.corus.android.data.model

import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.Date

data class CymbalPost(
    val id: String,
    val user: CymbalUser,
    val track: CymbalTrack,
    val caption: String? = null,
    val voiceNoteURL: String? = null,
    val hashtags: List<String> = emptyList(),
    val featuredHashtag: String? = null,
    val likeCount: Int = 0,
    val commentCount: Int = 0,
    val isLiked: Boolean = false,
    val timestamp: Date = Date(),
    val comments: List<CymbalComment> = emptyList(),
    val likers: List<CymbalUser> = emptyList(),
    val rankInHashtag: Int? = null,
    val trackPostCount: Int? = null,
    val isFirstPoster: Boolean = false,
    val repostedFromPostId: String? = null,
    val repostedFromUserId: String? = null,
    val repostedFromUsername: String? = null,
    val repostCount: Int = 0,
    /** Set by the `getFeedPage` cloud function on posts injected into the
     *  home feed because the user follows the hashtag (not the author).
     *  `null` for posts from followed users and for direct-Firestore reads. */
    val injectedByHashtag: String? = null,

    // Movie support
    val mediaType: MediaType = MediaType.TRACK,
    val movieId: String? = null,
    val movieTitle: String? = null,
    val directorName: String? = null,
    val releaseYear: String? = null,
    val posterURL: String? = null,
    val posterLargeURL: String? = null,
    val tmdbWebURL: String? = null,
    val trailerURL: String? = null,
    val movieOverview: String? = null,
    val movieRating: Double? = null,
    val movieCast: List<String>? = null,
    val movieReleaseDate: String? = null,
    /** Per-post comments-audience setting. `null` (or `EVERYONE`) means
     *  anyone can comment — back-compat with posts written before the
     *  feature shipped, since the field is omitted on those docs. */
    val commentsAudience: CommentsAudience? = null,
) {
    val isMovie: Boolean get() = mediaType == MediaType.MOVIE
    val isTrack: Boolean get() = mediaType == MediaType.TRACK

    /**
     * True iff the track or film release is "fresh enough":
     * - Tracks with day precision: released within the last 30 days
     * - Tracks with month precision: release year-month equals current year-month
     * - Tracks with year precision (or missing): never
     * - Films: full YYYY-MM-DD release date within the last 30 days (TMDB
     *   always provides day precision when it has a date)
     *
     * Recomputed on each call so the pill auto-expires without a backend flip
     * or feed refetch.
     */
    fun isNewRelease(today: LocalDate = LocalDate.now(ZoneOffset.UTC)): Boolean {
        if (isTrack) {
            val raw = track.releaseDate ?: return false
            return when (track.releaseDatePrecision) {
                "day" -> isWithinLast30Days(raw, today)
                "month" -> raw == "%04d-%02d".format(today.year, today.monthValue)
                else -> false
            }
        }
        if (isMovie) {
            val raw = movieReleaseDate ?: return false
            return isWithinLast30Days(raw, today)
        }
        return false
    }

    private fun isWithinLast30Days(dayString: String, today: LocalDate): Boolean {
        val released = try {
            LocalDate.parse(dayString, DateTimeFormatter.ISO_LOCAL_DATE)
        } catch (_: DateTimeParseException) {
            return false
        }
        val daysSinceRelease = java.time.temporal.ChronoUnit.DAYS.between(released, today)
        return daysSinceRelease in 0..29
    }

    val displayImageURL: String?
        get() = if (isMovie) posterURL else track.albumArtURL

    val displayImageLargeURL: String?
        get() = if (isMovie) posterLargeURL else track.albumArtLargeURL

    val displayTitle: String
        get() = if (isMovie) (movieTitle ?: "") else track.name

    val displaySubtitle: String
        get() = if (isMovie) (directorName ?: "") else track.artistName

    /**
     * Build a [CymbalMovie] view of this post for share flows that take a movie
     * (DM share, etc). Falls back to display strings for fields that may be
     * missing on legacy posts so the share never silently drops content.
     */
    fun toSharedMovie(): CymbalMovie = CymbalMovie(
        id = movieId.orEmpty(),
        title = movieTitle ?: displayTitle,
        directorName = directorName ?: displaySubtitle,
        year = releaseYear.orEmpty(),
        posterURL = posterURL,
        posterLargeURL = posterLargeURL,
        tmdbWebURL = tmdbWebURL.orEmpty(),
    )

    companion object {
        @Suppress("UNCHECKED_CAST")
        fun fromCloudData(data: Map<String, Any?>): CymbalPost {
            val userData = data["user"] as? Map<String, Any?> ?: emptyMap()
            val userId = userData["id"] as? String
                ?: data["userId"] as? String  // fallback to top-level userId
                ?: ""
            val user = CymbalUser.fromMap(userId, userData)

            val mediaTypeStr = data["mediaType"] as? String
            val mediaType = MediaType.from(mediaTypeStr)

            val trackSource = TrackSource.fromRaw(data["trackSource"] as? String ?: data["source"] as? String)
            val isTrackSoundCloud = trackSource == TrackSource.SOUNDCLOUD
            val track = CymbalTrack(
                id = data["trackId"] as? String ?: "",
                name = data["trackName"] as? String ?: "",
                artistName = data["artistName"] as? String ?: "",
                albumName = data["albumName"] as? String ?: "",
                albumArtURL = data["albumArtThumbnailURL"] as? String ?: data["albumArtURL"] as? String,
                albumArtLargeURL = data["albumArtLargeURL"] as? String,
                spotifyURI = if (isTrackSoundCloud) "" else (data["spotifyURI"] as? String ?: ""),
                spotifyWebURL = if (isTrackSoundCloud) "" else (data["spotifyWebURL"] as? String ?: ""),
                durationMs = (data["durationMs"] as? Number)?.toInt() ?: 0,
                previewUrl = data["previewUrl"] as? String ?: data["previewURL"] as? String,
                isrc = data["isrc"] as? String,
                albumArtBackURL = data["albumArtBackURL"] as? String,
                releaseDate = (data["trackReleaseDate"] as? String)?.ifEmpty { null },
                releaseDatePrecision = (data["trackReleaseDatePrecision"] as? String)?.ifEmpty { null },
                source = trackSource,
                soundcloudId = (data["soundcloudId"] as? String)?.ifEmpty { null },
                soundcloudPermalinkUrl = (data["soundcloudPermalinkUrl"] as? String)?.ifEmpty { null },
                unavailable = data["trackUnavailable"] as? Boolean ?: false,
                unavailableReason = (data["trackUnavailableReason"] as? String)?.ifEmpty { null },
            )

            // Preview comments from cloud functions use a flat structure
            val previewCommentsList = (data["previewComments"] as? List<Map<String, Any?>>)
                ?.mapNotNull { CymbalComment.fromPreviewMap(it) }
                ?: emptyList()
            // Fall back to nested "comments" format (e.g. from getComments endpoint)
            val commentsList = previewCommentsList.ifEmpty {
                (data["comments"] as? List<Map<String, Any?>>)?.map {
                    CymbalComment.fromMap(it)
                } ?: emptyList()
            }

            val likersRaw = (data["likers"] as? List<Map<String, Any?>>)
                ?: (data["recentLikers"] as? List<Map<String, Any?>>)
            val likersList = likersRaw?.map {
                val likerId = it["id"] as? String ?: ""
                CymbalUser.fromMap(likerId, it)
            } ?: emptyList()

            val timestampMs = data["createdAt"] as? Number ?: data["timestamp"] as? Number
            val timestamp = if (timestampMs != null) Date(timestampMs.toLong()) else Date()

            return CymbalPost(
                id = data["id"] as? String ?: "",
                user = user,
                track = track,
                caption = data["caption"] as? String,
                voiceNoteURL = data["voiceNoteURL"] as? String,
                hashtags = (data["hashtags"] as? List<String>) ?: emptyList(),
                featuredHashtag = data["featuredHashtag"] as? String,
                likeCount = (data["likeCount"] as? Number)?.toInt() ?: 0,
                commentCount = (data["commentCount"] as? Number)?.toInt() ?: 0,
                isLiked = data["isLiked"] as? Boolean ?: false,
                timestamp = timestamp,
                comments = commentsList,
                likers = likersList,
                rankInHashtag = (data["rankInHashtag"] as? Number)?.toInt(),
                trackPostCount = if (mediaType == MediaType.MOVIE)
                    (data["moviePostCount"] as? Number)?.toInt()
                else
                    (data["trackPostCount"] as? Number)?.toInt(),
                isFirstPoster = data["isFirstPoster"] as? Boolean ?: false,
                repostedFromPostId = data["repostedFromPostId"] as? String,
                repostedFromUserId = data["repostedFromUserId"] as? String,
                repostedFromUsername = data["repostedFromUsername"] as? String,
                repostCount = (data["repostCount"] as? Number)?.toInt() ?: 0,
                injectedByHashtag = (data["injectedByHashtag"] as? String)?.ifEmpty { null },
                mediaType = mediaType,
                movieId = data["movieId"] as? String,
                movieTitle = data["movieTitle"] as? String,
                directorName = data["directorName"] as? String,
                releaseYear = data["releaseYear"] as? String,
                posterURL = data["posterURL"] as? String,
                posterLargeURL = data["posterLargeURL"] as? String,
                tmdbWebURL = data["tmdbWebURL"] as? String,
                trailerURL = data["trailerURL"] as? String,
                movieOverview = data["movieOverview"] as? String,
                movieRating = (data["movieRating"] as? Number)?.toDouble(),
                movieCast = data["movieCast"] as? List<String>,
                movieReleaseDate = (data["movieReleaseDate"] as? String)?.ifEmpty { null },
                commentsAudience = CommentsAudience.parse(data["commentsAudience"]),
            )
        }
    }
}

/**
 * Per-post comments-audience setting. Wire format is the raw lowercase
 * string (`"everyone" | "followers" | "following" | "off"`); legacy posts
 * that pre-date the feature have no field and are treated as `EVERYONE`
 * everywhere.
 *
 *  - EVERYONE: anyone signed in
 *  - FOLLOWERS: viewer must follow the author (the author's followers)
 *  - FOLLOWING: author must follow the viewer (accounts the author follows)
 *  - OFF: no one, including the author
 */
enum class CommentsAudience(val wire: String) {
    EVERYONE("everyone"),
    FOLLOWERS("followers"),
    FOLLOWING("following"),
    OFF("off");

    companion object {
        /**
         * Lenient parse from a Firestore field — unknown / null / wrong-type
         * values fall back to `null` (= treat as everyone). The reader's job
         * is to never block legitimate comments because of a bad config write.
         */
        fun parse(raw: Any?): CommentsAudience? {
            val s = raw as? String ?: return null
            return values().firstOrNull { it.wire == s }
        }
    }
}
