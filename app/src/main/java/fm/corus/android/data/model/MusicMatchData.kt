package fm.corus.android.data.model

data class SharedTrackPreview(
    val trackId: String,
    val trackName: String,
    val artistName: String,
    val albumArtURL: String? = null,
    val posterURL: String? = null,
    val isMovie: Boolean = false,
) {
    val displayImageURL: String? get() = if (isMovie) posterURL else albumArtURL
}

data class SharedMoviePreview(
    val movieId: String,
    val movieTitle: String,
    val directorName: String,
    val posterURL: String? = null,
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
                )
            } ?: emptyList()

            val moviePreviews = (data["sharedMoviePreviews"] as? List<Map<String, Any?>>)?.map {
                SharedMoviePreview(
                    movieId = it["movieId"] as? String ?: "",
                    movieTitle = it["movieTitle"] as? String ?: "",
                    directorName = it["directorName"] as? String ?: "",
                    posterURL = it["posterURL"] as? String,
                )
            } ?: emptyList()

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
            )
        }
    }
}

data class SuggestionReason(val mutualNames: List<String> = emptyList())

data class SuggestedUserMatch(
    val user: CymbalUser,
    val matchData: MusicMatchData? = null,
    val suggestionReason: SuggestionReason? = null,
) {
    val id: String get() = user.id
}

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
