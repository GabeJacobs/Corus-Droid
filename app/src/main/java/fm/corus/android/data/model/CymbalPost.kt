package fm.corus.android.data.model

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
) {
    val isMovie: Boolean get() = mediaType == MediaType.MOVIE
    val isTrack: Boolean get() = mediaType == MediaType.TRACK

    val displayImageURL: String?
        get() = if (isMovie) posterURL else track.albumArtURL

    val displayImageLargeURL: String?
        get() = if (isMovie) posterLargeURL else track.albumArtLargeURL

    val displayTitle: String
        get() = if (isMovie) (movieTitle ?: "") else track.name

    val displaySubtitle: String
        get() = if (isMovie) (directorName ?: "") else track.artistName

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

            val track = CymbalTrack(
                id = data["trackId"] as? String ?: "",
                name = data["trackName"] as? String ?: "",
                artistName = data["artistName"] as? String ?: "",
                albumName = data["albumName"] as? String ?: "",
                albumArtURL = data["albumArtThumbnailURL"] as? String ?: data["albumArtURL"] as? String,
                albumArtLargeURL = data["albumArtLargeURL"] as? String,
                spotifyURI = data["spotifyURI"] as? String ?: "",
                spotifyWebURL = data["spotifyWebURL"] as? String ?: "",
                durationMs = (data["durationMs"] as? Number)?.toInt() ?: 0,
                previewUrl = data["previewUrl"] as? String ?: data["previewURL"] as? String,
                isrc = data["isrc"] as? String,
                albumArtBackURL = data["albumArtBackURL"] as? String,
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
            )
        }
    }
}
