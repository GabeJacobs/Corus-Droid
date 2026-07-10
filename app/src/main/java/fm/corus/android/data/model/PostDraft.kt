package fm.corus.android.data.model

/**
 * A post-in-progress saved to `users_v2/{uid}/postDrafts/{draftId}` so the
 * composer can be left and resumed later. Client-written (same trust model as
 * `/saves` and `/liked`, enforced by Firestore rules) — no Cloud Function on
 * this path.
 *
 * CROSS-PLATFORM CONTRACT
 * -----------------------
 * The `track`/`movie` maps use the WEB canonical field names (see
 * Corus-Web/app/lib/types.ts `SongSearchResult`/`MovieSearchResult` and
 * post-drafts.ts), NOT Android's post-doc field names. A draft written on web
 * or iOS must resume on Android and vice-versa. Because Android's own
 * [CymbalTrack]/[CymbalMovie] use different field names (`artistName` vs
 * `artist`, `title` vs `movieTitle` on the post path, etc.), this file owns the
 * native ↔ canonical mapping in BOTH directions:
 *   - [toCanonicalMap]  native  → canonical (on SAVE)
 *   - [trackFromCanonical]/[movieFromCanonical]  canonical → native (on RESUME/POST)
 *
 * Timestamps are epoch MILLISECONDS as plain [Long] numbers (NOT Firestore
 * Timestamps), matching `System.currentTimeMillis()` / web's `Date.now()`.
 */
data class PostDraft(
    val id: String,
    val mediaType: MediaType,
    val caption: String,
    val captionMode: String, // "text" | "voice"
    val commentsAudience: CommentsAudience? = null,
    val track: CymbalTrack? = null,
    val movie: CymbalMovie? = null,
    /** Cloud Storage download URL for a recorded voice memo, or null for text drafts. */
    val voiceNoteURL: String? = null,
    val createdAt: Long,
    val updatedAt: Long,
) {
    /**
     * Serialize to the canonical Firestore doc shape. `id`/`createdAt`/
     * `updatedAt` are managed by the repository, so they are not written here.
     * Optional keys that are absent are omitted; web writes a handful of track
     * fields as an explicit `null` (previewUrl/soundcloudId/
     * soundcloudPermalinkUrl/appleMusicId/isrc), which we mirror so a draft
     * round-trips byte-identically across clients.
     */
    fun toCanonicalMap(): Map<String, Any?> {
        val map = mutableMapOf<String, Any?>(
            "mediaType" to mediaType.value,
            "caption" to caption,
            "captionMode" to captionMode,
        )
        commentsAudience?.let { map["commentsAudience"] = it.wire }
        // Voice drafts carry the uploaded memo URL; web writes `null` for the
        // text branch, so mirror that (present-but-null) rather than omitting.
        if (captionMode == "voice") {
            map["voiceNoteURL"] = voiceNoteURL
        } else if (voiceNoteURL != null) {
            map["voiceNoteURL"] = voiceNoteURL
        }
        if (mediaType == MediaType.TRACK) {
            track?.let { map["track"] = trackToCanonical(it) }
        } else {
            movie?.let { map["movie"] = movieToCanonical(it) }
        }
        return pruneUndefinedButKeepNull(map)
    }

    companion object {
        /** Max drafts kept per user; oldest are trimmed on a fresh save. */
        const val MAX_POST_DRAFTS = 30

        /**
         * Native [CymbalTrack] → canonical `track` map (web `SongSearchResult`).
         * Field-name correspondences that DIFFER from Android's post-doc shape:
         *   name          ← CymbalTrack.name        (post doc: trackName)
         *   artist        ← CymbalTrack.artistName  (post doc: artistName)
         *   album         ← CymbalTrack.albumName   (post doc: albumName)
         *   releaseDate   ← CymbalTrack.releaseDate (post doc: trackReleaseDate)
         *   releaseDatePrecision ← releaseDatePrecision (post doc: trackReleaseDatePrecision)
         *   previewUrl/soundcloudId/soundcloudPermalinkUrl/appleMusicId/isrc are
         *   written as explicit null when empty (matches web's pruneUndefined,
         *   which keeps null).
         */
        fun trackToCanonical(t: CymbalTrack): Map<String, Any?> {
            val map = mutableMapOf<String, Any?>(
                "id" to t.id,
                "name" to t.name,
                "artist" to t.artistName,
                "album" to t.albumName,
                "albumArtURL" to (t.albumArtURL ?: ""),
                "durationMs" to t.durationMs,
                "spotifyWebURL" to t.spotifyWebURL,
                // Web writes these keys always (null when empty).
                "previewUrl" to t.previewUrl,
                "soundcloudId" to t.soundcloudId,
                "soundcloudPermalinkUrl" to t.soundcloudPermalinkUrl,
                "audiomackId" to t.audiomackId,
                "audiomackUrl" to t.audiomackUrl,
                "appleMusicId" to t.appleMusicId,
                "isrc" to t.isrc,
            )
            if (t.artistIds.isNotEmpty()) map["artistIds"] = t.artistIds
            t.albumId?.takeIf { it.isNotEmpty() }?.let { map["albumId"] = it }
            map["source"] = t.source.raw
            t.appleMusicURL?.let { map["appleMusicURL"] = it }
            t.releaseDate?.takeIf { it.isNotEmpty() }?.let { map["releaseDate"] = it }
            t.releaseDatePrecision?.takeIf { it.isNotEmpty() }?.let { map["releaseDatePrecision"] = it }
            return map
        }

        /**
         * Native [CymbalMovie] → canonical `movie` map (web `MovieSearchResult`).
         * Correspondences:
         *   title        ← CymbalMovie.title
         *   director     ← CymbalMovie.directorName
         *   releaseYear  ← CymbalMovie.year  (Number in canonical; Android holds
         *                  a String — coerce to Int, 0 when non-numeric/empty)
         *   posterURL/overview/rating map 1:1.
         */
        fun movieToCanonical(m: CymbalMovie): Map<String, Any?> {
            val map = mutableMapOf<String, Any?>(
                "id" to m.id,
                "title" to m.title,
                "director" to m.directorName,
                "releaseYear" to (m.year.toIntOrNull() ?: 0),
                "posterURL" to (m.posterURL ?: ""),
                "overview" to m.overview,
                "rating" to m.rating,
            )
            if (m.directorIds.isNotEmpty()) map["directorIds"] = m.directorIds
            m.releaseDate?.takeIf { it.isNotEmpty() }?.let { map["releaseDate"] = it }
            return map
        }

        /**
         * Canonical `track` map → native [CymbalTrack]. Reads the WEB field
         * names; a Spotify-sourced draft written by web populates the composer
         * correctly (name/artist/album/art/preview/duration/source), and a
         * SoundCloud/Apple draft preserves its source-specific ids.
         */
        fun trackFromCanonical(data: Map<String, Any?>): CymbalTrack {
            val source = TrackSource.fromRaw(data["source"] as? String)
            @Suppress("UNCHECKED_CAST")
            val artistIds = (data["artistIds"] as? List<*>)
                ?.mapNotNull { it as? String }?.filter { it.isNotEmpty() } ?: emptyList()
            val albumArt = (data["albumArtURL"] as? String)?.ifEmpty { null }
            return CymbalTrack(
                id = data["id"] as? String ?: "",
                name = data["name"] as? String ?: "",
                artistName = data["artist"] as? String ?: "",
                artistIds = artistIds,
                albumName = data["album"] as? String ?: "",
                albumId = (data["albumId"] as? String)?.ifEmpty { null },
                albumArtURL = albumArt,
                // Canonical shape carries only one art URL; use it for large too
                // so the compose preview (which prefers albumArtLargeURL) renders.
                albumArtLargeURL = albumArt,
                spotifyWebURL = data["spotifyWebURL"] as? String ?: "",
                durationMs = (data["durationMs"] as? Number)?.toInt() ?: 0,
                previewUrl = (data["previewUrl"] as? String)?.ifEmpty { null },
                isrc = (data["isrc"] as? String)?.ifEmpty { null },
                releaseDate = (data["releaseDate"] as? String)?.ifEmpty { null },
                releaseDatePrecision = (data["releaseDatePrecision"] as? String)?.ifEmpty { null },
                source = source,
                soundcloudId = (data["soundcloudId"] as? String)?.ifEmpty { null },
                soundcloudPermalinkUrl = (data["soundcloudPermalinkUrl"] as? String)?.ifEmpty { null },
                audiomackId = (data["audiomackId"] as? String)?.ifEmpty { null },
                audiomackUrl = (data["audiomackUrl"] as? String)?.ifEmpty { null },
                appleMusicId = data["appleMusicId"] as? String,
            )
        }

        /** Canonical `movie` map → native [CymbalMovie]. */
        fun movieFromCanonical(data: Map<String, Any?>): CymbalMovie {
            @Suppress("UNCHECKED_CAST")
            val directorIds = (data["directorIds"] as? List<*>)
                ?.mapNotNull { it as? String }?.filter { it.isNotEmpty() } ?: emptyList()
            val year = when (val y = data["releaseYear"]) {
                is Number -> if (y.toInt() == 0) "" else y.toInt().toString()
                is String -> y
                else -> ""
            }
            return CymbalMovie(
                id = data["id"] as? String ?: "",
                title = data["title"] as? String ?: "",
                directorName = data["director"] as? String ?: "",
                directorIds = directorIds,
                year = year,
                posterURL = (data["posterURL"] as? String)?.ifEmpty { null },
                overview = data["overview"] as? String ?: "",
                rating = (data["rating"] as? Number)?.toDouble() ?: 0.0,
                releaseDate = (data["releaseDate"] as? String)?.ifEmpty { null },
            )
        }

        /**
         * Deserialize a full draft doc (id + fields) into [PostDraft]. Reads the
         * canonical shape written by any client (web/iOS/Android). Timestamps are
         * numeric epoch millis; a legacy Firestore Timestamp (should not occur on
         * this path) is tolerated by falling back to its millis.
         */
        fun fromDoc(id: String, data: Map<String, Any?>): PostDraft {
            val mediaType = MediaType.from(data["mediaType"] as? String)
            @Suppress("UNCHECKED_CAST")
            val trackMap = data["track"] as? Map<String, Any?>
            @Suppress("UNCHECKED_CAST")
            val movieMap = data["movie"] as? Map<String, Any?>
            return PostDraft(
                id = id,
                mediaType = mediaType,
                caption = data["caption"] as? String ?: "",
                captionMode = data["captionMode"] as? String ?: "text",
                commentsAudience = CommentsAudience.parse(data["commentsAudience"]),
                track = if (mediaType == MediaType.TRACK) trackMap?.let { trackFromCanonical(it) } else null,
                movie = if (mediaType == MediaType.MOVIE) movieMap?.let { movieFromCanonical(it) } else null,
                voiceNoteURL = (data["voiceNoteURL"] as? String)?.ifEmpty { null },
                createdAt = readMillis(data["createdAt"]),
                updatedAt = readMillis(data["updatedAt"]),
            )
        }

        private fun readMillis(value: Any?): Long = when (value) {
            is Number -> value.toLong()
            is com.google.firebase.Timestamp -> value.toDate().time
            else -> 0L
        }

        /**
         * Firestore rejects `undefined`; Kotlin has no undefined so there is
         * nothing to strip, but explicit `null` values are RETAINED (web writes
         * some track fields as null on purpose). This is a no-op passthrough
         * kept for parity/readability with web's pruneUndefined.
         */
        private fun pruneUndefinedButKeepNull(map: Map<String, Any?>): Map<String, Any?> = map
    }
}
