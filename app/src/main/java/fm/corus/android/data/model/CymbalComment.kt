package fm.corus.android.data.model

import java.util.Date

/**
 * Optional song attachment carried inline on a comment document.
 * Mutually exclusive with [CommentAttachedFilm] and [CymbalComment.gifURL].
 */
data class CommentAttachedSong(
    val trackId: String,
    val trackName: String,
    val artistName: String,
    val albumName: String = "",
    val albumArtURL: String? = null,
    val albumArtLargeURL: String? = null,
    val spotifyURI: String = "",
    val spotifyWebURL: String = "",
    val previewUrl: String? = null,
    val isrc: String? = null,
    val durationMs: Int = 0,
) {
    /** Synthesized fallback so old clients see something legible when there's no caption. */
    val fallbackText: String get() = "🎵 $trackName — $artistName"

    /** Convert to a [CymbalTrack] so existing navigation/preview pipelines accept it. */
    fun asCymbalTrack(): CymbalTrack = CymbalTrack(
        id = trackId,
        name = trackName,
        artistName = artistName,
        albumName = albumName,
        albumArtURL = albumArtURL,
        albumArtLargeURL = albumArtLargeURL,
        spotifyURI = spotifyURI,
        spotifyWebURL = spotifyWebURL,
        durationMs = durationMs,
        previewUrl = previewUrl,
        isrc = isrc,
    )

    /** Firestore payload (writes a sub-map under `attachedSong`). */
    fun toFirestoreMap(): Map<String, Any?> = buildMap {
        put("trackId", trackId)
        put("trackName", trackName)
        put("artistName", artistName)
        put("albumName", albumName)
        put("spotifyURI", spotifyURI)
        put("spotifyWebURL", spotifyWebURL)
        put("durationMs", durationMs)
        if (!albumArtURL.isNullOrEmpty()) put("albumArtURL", albumArtURL)
        if (!albumArtLargeURL.isNullOrEmpty()) put("albumArtLargeURL", albumArtLargeURL)
        if (!previewUrl.isNullOrEmpty()) put("previewUrl", previewUrl)
        if (!isrc.isNullOrEmpty()) put("isrc", isrc)
    }

    companion object {
        fun fromTrack(track: CymbalTrack): CommentAttachedSong = CommentAttachedSong(
            trackId = track.id,
            trackName = track.name,
            artistName = track.artistName,
            albumName = track.albumName,
            albumArtURL = track.albumArtURL,
            albumArtLargeURL = track.albumArtLargeURL,
            spotifyURI = track.spotifyURI,
            spotifyWebURL = track.spotifyWebURL,
            previewUrl = track.previewUrl,
            isrc = track.isrc,
            durationMs = track.durationMs,
        )

        fun fromMap(data: Map<String, Any?>?): CommentAttachedSong? {
            if (data == null) return null
            val trackId = data["trackId"] as? String ?: return null
            if (trackId.isEmpty()) return null
            val trackName = data["trackName"] as? String ?: return null
            val artistName = data["artistName"] as? String ?: return null
            return CommentAttachedSong(
                trackId = trackId,
                trackName = trackName,
                artistName = artistName,
                albumName = data["albumName"] as? String ?: "",
                albumArtURL = data["albumArtURL"] as? String,
                albumArtLargeURL = data["albumArtLargeURL"] as? String,
                spotifyURI = data["spotifyURI"] as? String ?: "",
                spotifyWebURL = data["spotifyWebURL"] as? String ?: "",
                previewUrl = data["previewUrl"] as? String,
                isrc = data["isrc"] as? String,
                durationMs = (data["durationMs"] as? Number)?.toInt() ?: 0,
            )
        }
    }
}

/**
 * Optional film attachment carried inline on a comment document.
 * Mutually exclusive with [CommentAttachedSong] and [CymbalComment.gifURL].
 */
data class CommentAttachedFilm(
    val movieId: String,
    val movieTitle: String,
    val directorName: String = "",
    val releaseYear: String = "",
    val posterURL: String? = null,
    val posterLargeURL: String? = null,
    val tmdbWebURL: String = "",
) {
    val fallbackText: String
        get() = if (releaseYear.isEmpty()) "🎬 $movieTitle" else "🎬 $movieTitle ($releaseYear)"

    fun asCymbalMovie(): CymbalMovie = CymbalMovie(
        id = movieId,
        title = movieTitle,
        directorName = directorName,
        year = releaseYear,
        posterURL = posterURL,
        posterLargeURL = posterLargeURL,
        tmdbWebURL = tmdbWebURL,
    )

    fun toFirestoreMap(): Map<String, Any?> = buildMap {
        put("movieId", movieId)
        put("movieTitle", movieTitle)
        put("directorName", directorName)
        put("releaseYear", releaseYear)
        put("tmdbWebURL", tmdbWebURL)
        if (!posterURL.isNullOrEmpty()) put("posterURL", posterURL)
        if (!posterLargeURL.isNullOrEmpty()) put("posterLargeURL", posterLargeURL)
    }

    companion object {
        fun fromMovie(movie: CymbalMovie): CommentAttachedFilm = CommentAttachedFilm(
            movieId = movie.id,
            movieTitle = movie.title,
            directorName = movie.directorName,
            releaseYear = movie.year,
            posterURL = movie.posterURL,
            posterLargeURL = movie.posterLargeURL,
            tmdbWebURL = movie.tmdbWebURL,
        )

        fun fromMap(data: Map<String, Any?>?): CommentAttachedFilm? {
            if (data == null) return null
            val movieId = data["movieId"] as? String ?: return null
            if (movieId.isEmpty()) return null
            val movieTitle = data["movieTitle"] as? String ?: return null
            return CommentAttachedFilm(
                movieId = movieId,
                movieTitle = movieTitle,
                directorName = data["directorName"] as? String ?: "",
                releaseYear = data["releaseYear"] as? String ?: "",
                posterURL = data["posterURL"] as? String,
                posterLargeURL = data["posterLargeURL"] as? String,
                tmdbWebURL = data["tmdbWebURL"] as? String ?: "",
            )
        }
    }
}

data class CymbalComment(
    val id: String,
    val user: CymbalUser,
    val text: String,
    val timestamp: Date = Date(),
    val likeCount: Int = 0,
    val parentCommentId: String? = null,
    val replyToUser: CymbalUser? = null,
    val replyCount: Int = 0,
    val gifURL: String? = null,
    val editedAt: Date? = null,
    val attachedSong: CommentAttachedSong? = null,
    val attachedFilm: CommentAttachedFilm? = null,
    /**
     * True when [text] was synthesized by the client purely so old clients have
     * something to render. New clients must suppress text rendering and only
     * show the attachment card when this is true.
     */
    val textIsAttachmentFallback: Boolean = false,
) {
    val isEdited: Boolean get() = editedAt != null

    /** What new clients should display for the comment text. */
    val displayText: String get() = if (textIsAttachmentFallback) "" else text

    val hasAttachment: Boolean get() = attachedSong != null || attachedFilm != null

    companion object {
        @Suppress("UNCHECKED_CAST")
        fun fromMap(data: Map<String, Any?>): CymbalComment {
            val userData = data["user"] as? Map<String, Any?> ?: emptyMap()
            val userId = userData["id"] as? String ?: ""
            val user = CymbalUser.fromMap(userId, userData)

            val replyToUserData = data["replyToUser"] as? Map<String, Any?>
            val replyToUser = replyToUserData?.let {
                val rtId = it["id"] as? String ?: ""
                CymbalUser.fromMap(rtId, it)
            }

            val timestampMs = data["createdAt"] as? Number ?: data["timestamp"] as? Number
            val timestamp = if (timestampMs != null) Date(timestampMs.toLong()) else Date()

            val editedAtMs = data["editedAt"] as? Number
            val editedAt = if (editedAtMs != null) Date(editedAtMs.toLong()) else null

            val attachedSong = CommentAttachedSong.fromMap(data["attachedSong"] as? Map<String, Any?>)
            val attachedFilm = CommentAttachedFilm.fromMap(data["attachedFilm"] as? Map<String, Any?>)

            return CymbalComment(
                id = data["id"] as? String ?: "",
                user = user,
                text = data["text"] as? String ?: "",
                timestamp = timestamp,
                likeCount = (data["likeCount"] as? Number)?.toInt() ?: 0,
                parentCommentId = data["parentCommentId"] as? String,
                replyToUser = replyToUser,
                replyCount = (data["replyCount"] as? Number)?.toInt() ?: 0,
                gifURL = data["gifURL"] as? String,
                editedAt = editedAt,
                attachedSong = attachedSong,
                attachedFilm = attachedFilm,
                textIsAttachmentFallback = data["textIsAttachmentFallback"] as? Boolean ?: false,
            )
        }

        fun fromPreviewMap(data: Map<String, Any?>): CymbalComment? {
            val commentId = data["commentId"] as? String ?: return null
            val userId = data["userId"] as? String ?: return null
            val text = data["text"] as? String ?: return null

            val user = CymbalUser(
                id = userId,
                username = data["username"] as? String ?: "",
                displayName = data["displayName"] as? String ?: "",
                avatarURL = data["avatarURL"] as? String,
                bio = "",
                isVerified = data["isVerified"] as? Boolean ?: false,
                isClubMember = data["isClubMember"] as? Boolean ?: false,
                isBot = data["isBot"] as? Boolean ?: false,
                botType = data["botType"] as? String,
            )

            val timestampMs = data["createdAt"] as? Number
            val timestamp = if (timestampMs != null) Date(timestampMs.toLong()) else Date()

            return CymbalComment(
                id = commentId,
                user = user,
                text = text,
                timestamp = timestamp,
            )
        }
    }
}
