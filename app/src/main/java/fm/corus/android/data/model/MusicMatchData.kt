package fm.corus.android.data.model

/**
 * How a shared preview came to be — used to distinguish "both of you posted
 * this song/film" from "the target posted a song by an artist you both like."
 */
enum class SharedPreviewKind {
    /** Pass 1: same trackId / movieId on both users. */
    SHARED_SONG,
    /** Pass 2: same artist/director, target's post fills in. */
    SHARED_ARTIST;

    companion object {
        fun fromWire(raw: String?): SharedPreviewKind =
            if (raw == "sharedArtist") SHARED_ARTIST else SHARED_SONG
    }
}

data class SharedTrackPreview(
    val trackId: String,
    val trackName: String,
    val artistName: String,
    val albumArtURL: String? = null,
    val posterURL: String? = null,
    val isMovie: Boolean = false,
    val postId: String? = null,
    val kind: SharedPreviewKind = SharedPreviewKind.SHARED_SONG,
) {
    val displayImageURL: String? get() = if (isMovie) posterURL else albumArtURL
}

data class SharedMoviePreview(
    val movieId: String,
    val movieTitle: String,
    val directorName: String,
    val posterURL: String? = null,
    val postId: String? = null,
    val kind: SharedPreviewKind = SharedPreviewKind.SHARED_SONG,
)

data class MusicMatchData(
    val similarityScore: Double = 0.0,
    val sharedPostedTracks: Int = 0,
    val sharedLikedTracks: Int = 0,
    val sharedArtists: Int = 0,
    val adjacentArtists: Int = 0,
    val sharedPostedMovies: Int = 0,
    val sharedLikedMovies: Int = 0,
    val sharedDirectors: Int = 0,
    val sharedHashtags: Int = 0,
    val mutualFollows: Int = 0,
    val sharedTrackPreviews: List<SharedTrackPreview> = emptyList(),
    val sharedMoviePreviews: List<SharedMoviePreview> = emptyList(),
    val sharedArtistNames: List<String> = emptyList(),
    val sharedDirectorNames: List<String> = emptyList(),
) {
    val hasSimilarityData: Boolean
        get() = totalSharedTracks > 0 || sharedArtists > 0 || adjacentArtists > 0 ||
                totalSharedMovies > 0 || sharedDirectors > 0

    val totalSharedTracks: Int get() = sharedPostedTracks + sharedLikedTracks
    val totalSharedMovies: Int get() = sharedPostedMovies + sharedLikedMovies
    val isHighMatch: Boolean get() = similarityScore >= 0.15

    companion object {
        @Suppress("UNCHECKED_CAST")
        fun fromMap(data: Map<String, Any?>): MusicMatchData {
            val trackPreviews = (data["sharedTrackPreviews"] as? List<Map<String, Any?>>)?.map {
                SharedTrackPreview(
                    trackId = it["trackId"] as? String ?: "",
                    trackName = it["trackName"] as? String ?: "",
                    artistName = it["artistName"] as? String ?: "",
                    albumArtURL = it["albumArtURL"] as? String,
                    posterURL = it["posterURL"] as? String,
                    isMovie = it["isMovie"] as? Boolean ?: false,
                    postId = it["postId"] as? String,
                    kind = SharedPreviewKind.fromWire(it["kind"] as? String),
                )
            } ?: emptyList()

            val moviePreviews = (data["sharedMoviePreviews"] as? List<Map<String, Any?>>)?.map {
                SharedMoviePreview(
                    movieId = it["movieId"] as? String ?: "",
                    movieTitle = it["movieTitle"] as? String ?: "",
                    directorName = it["directorName"] as? String ?: "",
                    posterURL = it["posterURL"] as? String,
                    postId = it["postId"] as? String,
                    kind = SharedPreviewKind.fromWire(it["kind"] as? String),
                )
            } ?: emptyList()

            val artistNames = (data["sharedArtistNames"] as? List<*>)
                ?.mapNotNull { it as? String } ?: emptyList()
            val directorNames = (data["sharedDirectorNames"] as? List<*>)
                ?.mapNotNull { it as? String } ?: emptyList()

            return MusicMatchData(
                similarityScore = (data["similarityScore"] as? Number)?.toDouble() ?: 0.0,
                sharedPostedTracks = (data["sharedPostedTracks"] as? Number)?.toInt() ?: 0,
                sharedLikedTracks = (data["sharedLikedTracks"] as? Number)?.toInt() ?: 0,
                sharedArtists = (data["sharedArtists"] as? Number)?.toInt() ?: 0,
                adjacentArtists = (data["adjacentArtists"] as? Number)?.toInt() ?: 0,
                sharedPostedMovies = (data["sharedPostedMovies"] as? Number)?.toInt() ?: 0,
                sharedLikedMovies = (data["sharedLikedMovies"] as? Number)?.toInt() ?: 0,
                sharedDirectors = (data["sharedDirectors"] as? Number)?.toInt() ?: 0,
                sharedHashtags = (data["sharedHashtags"] as? Number)?.toInt() ?: 0,
                mutualFollows = (data["mutualFollows"] as? Number)?.toInt() ?: 0,
                sharedTrackPreviews = trackPreviews,
                sharedMoviePreviews = moviePreviews,
                sharedArtistNames = artistNames,
                sharedDirectorNames = directorNames,
            )
        }
    }
}

data class SuggestionReason(
    val mutualNames: List<String> = emptyList(),
    /**
     * Total number of mutual followers (the underlying overlap-set size).
     * `mutualNames` is capped at 5 sample names server-side, so this carries
     * the full count for client-side ordering of the Mutual Connections rail.
     * Defaults to `mutualNames.size` for back-compat with payloads that don't
     * ship the field yet.
     */
    val mutualCount: Int = mutualNames.size,
)

data class SuggestedUserMatch(
    val user: CymbalUser,
    val matchData: MusicMatchData? = null,
    val suggestionReason: SuggestionReason? = null,
) {
    val id: String get() = user.id

    /**
     * A taste match = the viewer shares >=3 DISTINCT artists (or directors) with
     * this user. Songs/films alone do not qualify. Gating on the NAMED shared
     * artists (the exact names the Taste Match card lists) guarantees a taste-
     * match cell always has >=3 artists to show — no blank cards. (2026-06-26)
     */
    val isTasteMatch: Boolean
        get() {
            val data = matchData ?: return false
            return data.sharedArtistNames.size + data.sharedDirectorNames.size >= TASTE_MATCH_MIN_ARTISTS
        }

    companion object {
        const val TASTE_MATCH_MIN_ARTISTS = 3
    }
}

/** One page of the live, cursor-paginated taste-matches list. */
data class TasteMatchesPage(
    val matches: List<SuggestedUserMatch>,
    val nextCursor: String?,
    val hasMore: Boolean,
)

data class CymbalMessagingSettings(
    val pushEnabled: Boolean = true,
    val whoCanMessage: MessagePrivacyOption = MessagePrivacyOption.EVERYONE,
)

data class CymbalNotificationSettings(
    val likes: Boolean = true,
    val commentsAndReplies: Boolean = true,
    val newFollowers: Boolean = true,
    val followRequests: Boolean = true,
    val contactJoined: Boolean = true,
)
