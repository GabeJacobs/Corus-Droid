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
    /** Number of users who have this post saved. Denormalized aggregate kept in
     *  sync server-side by the onSaveCreated / onSaveDeleted triggers. The
     *  who-saved list stays private; only this count is exposed. Server-only —
     *  no optimistic bump on tap (the bookmark fill flips optimistically, the
     *  count reconciles up to a floor of 1 while the trigger lags). */
    val saveCount: Int = 0,
    /** Set by the `getFeedPage` cloud function on posts injected into the
     *  home feed because the user follows the hashtag (not the author).
     *  `null` for posts from followed users and for direct-Firestore reads. */
    val injectedByHashtag: String? = null,

    // Movie support
    val mediaType: MediaType = MediaType.TRACK,
    val movieId: String? = null,
    val movieTitle: String? = null,
    val directorName: String? = null,
    /** Per-director TMDB IDs from the post doc. Empty for legacy posts. */
    val directorIds: List<String> = emptyList(),
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
    fun isNewRelease(today: LocalDate = LocalDate.now(ZoneOffset.UTC)): Boolean = when {
        isTrack -> isTrackNewRelease(track.releaseDate, track.releaseDatePrecision, today)
        isMovie -> isMovieNewRelease(movieReleaseDate, today)
        else -> false
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
        /**
         * Track new-release rule (see [isNewRelease]). Exposed on the companion so
         * the song detail screen can badge from a seed release date (route hint)
         * on the first frame — before its posts load — not only from a loaded
         * [CymbalPost]. Day precision: released within 30 days; month precision:
         * release year-month == current (UTC); year precision (or missing): never.
         */
        fun isTrackNewRelease(
            releaseDate: String?,
            precision: String?,
            today: LocalDate = LocalDate.now(ZoneOffset.UTC),
        ): Boolean {
            val raw = releaseDate ?: return false
            return when (precision) {
                "day" -> isWithinLast30Days(raw, today)
                "month" -> raw == "%04d-%02d".format(today.year, today.monthValue)
                else -> false
            }
        }

        /** Film new-release rule: full YYYY-MM-DD release date within 30 days. */
        fun isMovieNewRelease(
            releaseDate: String?,
            today: LocalDate = LocalDate.now(ZoneOffset.UTC),
        ): Boolean {
            val raw = releaseDate ?: return false
            return isWithinLast30Days(raw, today)
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

        @Suppress("UNCHECKED_CAST")
        fun fromCloudData(data: Map<String, Any?>): CymbalPost {
            // Author resolution order (Phase 1 denorm rollout):
            //   1. Full `user` block from the server callable — populated by
            //      `hydratePostAuthorsForDocs` on the backend, which uses the
            //      denormalized `author` block on the post doc when present
            //      and falls back to `batchGetUsers` only for legacy posts.
            //      This is the production path post-rollout.
            //   2. Top-level `author` denorm block — used when a caller
            //      bypasses the callable hydration (future direct-Firestore
            //      paths). Mirrors the iOS DatabaseService fallback.
            //   3. Empty userData — Past-rollout legacy posts that haven't
            //      hit the one-time backfill yet. The CymbalUser stub will
            //      still render but with empty fields; the feed filters such
            //      posts server-side.
            val userMap = data["user"] as? Map<String, Any?>
            val userId = (userMap?.get("id") as? String)
                ?: (data["userId"] as? String)
                ?: ""
            val user: CymbalUser = if (userMap != null) {
                CymbalUser.fromMap(userId, userMap)
            } else {
                val authorBlock = data["author"] as? Map<String, Any?>
                CymbalUser.fromAuthorPreview(userId, authorBlock)
                    ?: CymbalUser.fromMap(userId, emptyMap())
            }

            val mediaTypeStr = data["mediaType"] as? String
            val mediaType = MediaType.from(mediaTypeStr)

            val trackSource = TrackSource.fromRaw(data["trackSource"] as? String ?: data["source"] as? String)
            val isTrackSoundCloud = trackSource == TrackSource.SOUNDCLOUD
            // Audiomack is link-out only and not on Spotify — blank the Spotify
            // fields (like SoundCloud) so no broken "Open in Spotify" link is
            // synthesized from a non-Spotify trackId.
            val isTrackAudiomack = trackSource == TrackSource.AUDIOMACK
            val track = CymbalTrack(
                id = data["trackId"] as? String ?: "",
                name = data["trackName"] as? String ?: "",
                artistName = data["artistName"] as? String ?: "",
                // Per-artist Spotify IDs (mirrors the directorIds parsing below).
                // Empty for SoundCloud and pre-backfill posts. Parsed defensively
                // element-by-element — a mixed-type array can't crash the feed.
                artistIds = (data["artistIds"] as? List<*>)?.mapNotNull { it as? String }?.filter { it.isNotEmpty() }
                    ?: emptyList(),
                albumName = data["albumName"] as? String ?: "",
                // Spotify album id (or `am:<appleAlbumId>` for Apple-only tracks)
                // stamped by the create-time trigger and serialized on every post
                // read. Powers the "…" menu's "Go to Album" row. Absent/"" on
                // SoundCloud and pre-backfill posts -> null (row hidden). Read-only
                // on the client — never written. Mirrors iOS post decoder.
                albumId = (data["albumId"] as? String)?.ifEmpty { null },
                albumArtURL = data["albumArtThumbnailURL"] as? String ?: data["albumArtURL"] as? String,
                albumArtLargeURL = data["albumArtLargeURL"] as? String,
                spotifyURI = if (isTrackSoundCloud || isTrackAudiomack) "" else (data["spotifyURI"] as? String ?: ""),
                spotifyWebURL = if (isTrackSoundCloud || isTrackAudiomack) "" else (data["spotifyWebURL"] as? String ?: ""),
                durationMs = (data["durationMs"] as? Number)?.toInt() ?: 0,
                previewUrl = data["previewUrl"] as? String ?: data["previewURL"] as? String,
                isrc = data["isrc"] as? String,
                albumArtBackURL = data["albumArtBackURL"] as? String,
                releaseDate = (data["trackReleaseDate"] as? String)?.ifEmpty { null },
                releaseDatePrecision = (data["trackReleaseDatePrecision"] as? String)?.ifEmpty { null },
                source = trackSource,
                soundcloudId = (data["soundcloudId"] as? String)?.ifEmpty { null },
                soundcloudPermalinkUrl = (data["soundcloudPermalinkUrl"] as? String)?.ifEmpty { null },
                audiomackId = (data["audiomackId"] as? String)?.ifEmpty { null },
                audiomackUrl = (data["audiomackUrl"] as? String)?.ifEmpty { null },
                // Audiomack artist/album page URLs — link-out targets for the "…"
                // menu's "Go to Artist"/"Go to Album" and the tappable artist name
                // (Audiomack carries no Spotify artistIds/albumId). Backend derives
                // audiomackArtistUrl for every Audiomack post; audiomackAlbumUrl is
                // "" (-> null) for loose singles so the album row is never dead.
                audiomackArtistUrl = (data["audiomackArtistUrl"] as? String)?.ifEmpty { null },
                audiomackAlbumUrl = (data["audiomackAlbumUrl"] as? String)?.ifEmpty { null },
                // Tri-state, drives the service badge. Preserve "" (resolver
                // confirmed NOT on Apple Music) vs null (unknown / field absent).
                // Don't collapse "" to null, or a confirmed Spotify-only track
                // can't be told from "we don't know yet".
                appleMusicId = data["appleMusicId"] as? String,
                // Storefront the appleMusicId is valid in (Apple ids are
                // storefront-specific); null = unknown -> treat as reachable.
                appleMusicStorefront = data["appleMusicStorefront"] as? String,
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
                saveCount = ((data["saveCount"] as? Number)?.toInt() ?: 0).coerceAtLeast(0),
                injectedByHashtag = (data["injectedByHashtag"] as? String)?.ifEmpty { null },
                mediaType = mediaType,
                movieId = data["movieId"] as? String,
                movieTitle = data["movieTitle"] as? String,
                directorName = data["directorName"] as? String,
                directorIds = (data["directorIds"] as? List<*>)?.mapNotNull { it as? String }?.filter { it.isNotEmpty() }
                    ?: emptyList(),
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
