package fm.corus.android.data.model

import com.google.firebase.Timestamp
import java.util.Date

data class CymbalMessage(
    val id: String,
    val threadId: String,
    val fromUserId: String,
    val text: String? = null,
    val type: MessageType = MessageType.TEXT,
    val mediaURL: String? = null,
    val createdAt: Date = Date(),
    val sendStatus: MessageSendStatus = MessageSendStatus.SENT,
    val sharedPostId: String? = null,
    val trackId: String? = null,
    val trackName: String? = null,
    val artistName: String? = null,
    val albumName: String? = null,
    val albumArtURL: String? = null,
    val albumArtLargeURL: String? = null,
    val spotifyURI: String? = null,
    val spotifyURL: String? = null,
    val previewUrl: String? = null,
    val isrc: String? = null,
    val durationMs: Int? = null,
    val trackSource: String? = null,
    val soundcloudId: String? = null,
    val soundcloudPermalinkUrl: String? = null,
    val movieId: String? = null,
    val movieTitle: String? = null,
    val directorName: String? = null,
    val releaseYear: String? = null,
    val posterURL: String? = null,
    val posterLargeURL: String? = null,
    val tmdbWebURL: String? = null,
    // sharedArtist / sharedAlbum / sharedDirector fields
    val artistId: String? = null,
    val artistImageURL: String? = null,
    val albumId: String? = null,
    val albumTitle: String? = null,
    val albumArtistName: String? = null,
    val albumCoverURL: String? = null,
    val albumYear: String? = null,
    val directorId: String? = null,
    val directorImageURL: String? = null,
    val likedByUserIds: List<String> = emptyList(),
    val reactions: Map<String, List<String>> = emptyMap(),
    val replyToMessageId: String? = null,
    val replyToText: String? = null,
    val replyToUserId: String? = null,
    /** Set when the author edited the message; drives the "edited" indicator. */
    val editedAt: Date? = null,
    val failureReason: MessageFailureReason = MessageFailureReason.GENERIC,
    // Group lifecycle events ("X added Y"): `systemEvent` keys the event, `text`
    // holds the canonical fallback string. Null/empty for normal messages.
    val systemEvent: String? = null,
    val systemActorId: String? = null,
    val systemTargetIds: List<String> = emptyList(),
) {
    /** True once the author has edited the message. */
    val isEdited: Boolean get() = editedAt != null

    /** True for group lifecycle events, rendered as a centered system row. */
    val isSystem: Boolean get() = type == MessageType.SYSTEM

    /**
     * Reconstruct a [CommentAttachedSong] from the message's track fields so
     * existing playback + navigation pipelines can consume it. Returns null for
     * non-track messages or legacy messages that pre-date the trackId field.
     */
    val attachedSong: CommentAttachedSong?
        get() {
            if (type != MessageType.SHARED_TRACK) return null
            val id = trackId ?: derivedTrackId(spotifyURL) ?: return null
            if (id.isEmpty()) return null
            val name = trackName?.takeIf { it.isNotEmpty() } ?: return null
            val artist = artistName ?: return null
            val resolvedSource = trackSource?.lowercase()
                ?: if (id.startsWith("sc:")) "soundcloud" else "spotify"
            val resolvedURI = when {
                !spotifyURI.isNullOrEmpty() -> spotifyURI
                resolvedSource == "spotify" -> "spotify:track:$id"
                else -> ""
            }
            return CommentAttachedSong(
                trackId = id,
                trackName = name,
                artistName = artist,
                albumName = albumName ?: "",
                albumArtURL = albumArtURL,
                albumArtLargeURL = albumArtLargeURL ?: albumArtURL,
                spotifyURI = resolvedURI,
                spotifyWebURL = spotifyURL ?: "",
                previewUrl = previewUrl,
                isrc = isrc,
                durationMs = durationMs ?: 0,
            )
        }

    /** Source of the attached track (spotify | soundcloud), if known. */
    val attachedSongSource: TrackSource?
        get() {
            if (type != MessageType.SHARED_TRACK) return null
            val raw = trackSource?.lowercase()
            return when {
                raw == "soundcloud" -> TrackSource.SOUNDCLOUD
                raw == "spotify" -> TrackSource.SPOTIFY
                trackId?.startsWith("sc:") == true -> TrackSource.SOUNDCLOUD
                else -> TrackSource.SPOTIFY
            }
        }

    val attachedFilm: CommentAttachedFilm?
        get() {
            if (type != MessageType.SHARED_FILM) return null
            val id = movieId ?: derivedMovieId(tmdbWebURL) ?: return null
            if (id.isEmpty()) return null
            val title = movieTitle?.takeIf { it.isNotEmpty() } ?: return null
            return CommentAttachedFilm(
                movieId = id,
                movieTitle = title,
                directorName = directorName ?: "",
                releaseYear = releaseYear ?: "",
                posterURL = posterURL,
                posterLargeURL = posterLargeURL ?: posterURL,
                tmdbWebURL = tmdbWebURL ?: "",
            )
        }

    companion object {
        @Suppress("UNCHECKED_CAST")
        fun fromMap(id: String, data: Map<String, Any?>): CymbalMessage {
            val timestampMs = data["createdAt"] as? Number
            val createdAt = if (timestampMs != null) Date(timestampMs.toLong()) else Date()

            return CymbalMessage(
                id = id,
                threadId = data["threadId"] as? String ?: "",
                fromUserId = data["fromUserId"] as? String ?: "",
                text = data["text"] as? String,
                type = MessageType.from(data["type"] as? String),
                mediaURL = data["mediaURL"] as? String,
                createdAt = createdAt,
                sharedPostId = data["sharedPostId"] as? String,
                trackId = data["trackId"] as? String,
                trackName = data["trackName"] as? String,
                artistName = data["artistName"] as? String,
                albumName = data["albumName"] as? String,
                albumArtURL = data["albumArtURL"] as? String,
                albumArtLargeURL = data["albumArtLargeURL"] as? String,
                spotifyURI = data["spotifyURI"] as? String,
                spotifyURL = data["spotifyURL"] as? String,
                previewUrl = data["previewUrl"] as? String,
                isrc = data["isrc"] as? String,
                durationMs = (data["durationMs"] as? Number)?.toInt(),
                trackSource = data["source"] as? String,
                soundcloudId = data["soundcloudId"] as? String,
                soundcloudPermalinkUrl = data["soundcloudPermalinkUrl"] as? String,
                movieId = data["movieId"] as? String,
                movieTitle = data["movieTitle"] as? String,
                directorName = data["directorName"] as? String,
                releaseYear = data["releaseYear"] as? String,
                posterURL = data["posterURL"] as? String,
                posterLargeURL = data["posterLargeURL"] as? String,
                tmdbWebURL = data["tmdbWebURL"] as? String,
                artistId = data["artistId"] as? String,
                artistImageURL = data["artistImageURL"] as? String,
                albumId = data["albumId"] as? String,
                albumTitle = data["albumTitle"] as? String,
                albumArtistName = data["albumArtistName"] as? String,
                albumCoverURL = data["albumCoverURL"] as? String,
                albumYear = data["albumYear"] as? String,
                directorId = data["directorId"] as? String,
                directorImageURL = data["directorImageURL"] as? String,
                likedByUserIds = data["likedByUserIds"] as? List<String> ?: emptyList(),
                reactions = (data["reactions"] as? Map<String, Any?>)?.mapValues { (_, v) ->
                    (v as? List<*>)?.filterIsInstance<String>() ?: emptyList()
                } ?: emptyMap(),
                replyToMessageId = data["replyToMessageId"] as? String,
                replyToText = data["replyToText"] as? String,
                replyToUserId = data["replyToUserId"] as? String,
                editedAt = (data["editedAt"] as? Number)?.let { Date(it.toLong()) },
                systemEvent = data["systemEvent"] as? String,
                systemActorId = data["actorId"] as? String,
                systemTargetIds = (data["targetIds"] as? List<*>)?.filterIsInstance<String>() ?: emptyList(),
            )
        }

        @Suppress("UNCHECKED_CAST")
        fun fromFirestoreDoc(id: String, threadId: String, data: Map<String, Any?>): CymbalMessage {
            val ts = data["createdAt"] as? Timestamp
            val createdAt = ts?.toDate() ?: Date()

            return CymbalMessage(
                id = id,
                threadId = threadId,
                fromUserId = data["fromUserId"] as? String ?: "",
                text = data["text"] as? String,
                type = MessageType.from(data["type"] as? String),
                mediaURL = data["mediaURL"] as? String,
                createdAt = createdAt,
                sharedPostId = data["sharedPostId"] as? String,
                trackId = data["trackId"] as? String,
                trackName = data["trackName"] as? String,
                artistName = data["artistName"] as? String,
                albumName = data["albumName"] as? String,
                albumArtURL = data["albumArtURL"] as? String,
                albumArtLargeURL = data["albumArtLargeURL"] as? String,
                spotifyURI = data["spotifyURI"] as? String,
                spotifyURL = data["spotifyURL"] as? String,
                previewUrl = data["previewUrl"] as? String,
                isrc = data["isrc"] as? String,
                durationMs = (data["durationMs"] as? Number)?.toInt(),
                trackSource = data["source"] as? String,
                soundcloudId = data["soundcloudId"] as? String,
                soundcloudPermalinkUrl = data["soundcloudPermalinkUrl"] as? String,
                movieId = data["movieId"] as? String,
                movieTitle = data["movieTitle"] as? String,
                directorName = data["directorName"] as? String,
                releaseYear = data["releaseYear"] as? String,
                posterURL = data["posterURL"] as? String,
                posterLargeURL = data["posterLargeURL"] as? String,
                tmdbWebURL = data["tmdbWebURL"] as? String,
                artistId = data["artistId"] as? String,
                artistImageURL = data["artistImageURL"] as? String,
                albumId = data["albumId"] as? String,
                albumTitle = data["albumTitle"] as? String,
                albumArtistName = data["albumArtistName"] as? String,
                albumCoverURL = data["albumCoverURL"] as? String,
                albumYear = data["albumYear"] as? String,
                directorId = data["directorId"] as? String,
                directorImageURL = data["directorImageURL"] as? String,
                likedByUserIds = data["likedByUserIds"] as? List<String> ?: emptyList(),
                reactions = (data["reactions"] as? Map<String, Any?>)?.mapValues { (_, v) ->
                    (v as? List<*>)?.filterIsInstance<String>() ?: emptyList()
                } ?: emptyMap(),
                replyToMessageId = data["replyToMessageId"] as? String,
                replyToText = data["replyToText"] as? String,
                replyToUserId = data["replyToUserId"] as? String,
                editedAt = (data["editedAt"] as? Timestamp)?.toDate(),
                systemEvent = data["systemEvent"] as? String,
                systemActorId = data["actorId"] as? String,
                systemTargetIds = (data["targetIds"] as? List<*>)?.filterIsInstance<String>() ?: emptyList(),
            )
        }

        /** Pull the Spotify track ID out of a canonical web URL like
         *  `https://open.spotify.com/track/{id}` so legacy messages (sent before
         *  we started persisting `trackId`) still navigate in-app. */
        private fun derivedTrackId(spotifyWebURL: String?): String? {
            val raw = spotifyWebURL?.trim().orEmpty()
            if (raw.isEmpty()) return null
            val idx = raw.indexOf("/track/")
            if (idx < 0) return null
            val tail = raw.substring(idx + "/track/".length)
            val id = tail.substringBefore('?').substringBefore('/')
            return id.takeIf { it.isNotEmpty() }
        }

        /** Pull the TMDB movie ID out of a canonical web URL like
         *  `https://www.themoviedb.org/movie/{id}` (optionally `-{slug}`). */
        private fun derivedMovieId(tmdbWebURL: String?): String? {
            val raw = tmdbWebURL?.trim().orEmpty()
            if (raw.isEmpty()) return null
            val idx = raw.indexOf("/movie/")
            if (idx < 0) return null
            val segment = raw.substring(idx + "/movie/".length)
                .substringBefore('?')
                .substringBefore('/')
            val id = segment.substringBefore('-')
            return id.takeIf { it.isNotEmpty() }
        }
    }
}
