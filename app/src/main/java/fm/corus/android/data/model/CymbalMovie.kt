package fm.corus.android.data.model

data class CymbalMovie(
    val id: String,
    val title: String,
    val directorName: String = "",
    val year: String = "",
    val posterURL: String? = null,
    val posterLargeURL: String? = null,
    val tmdbWebURL: String = "",
    val overview: String = "",
    val rating: Double = 0.0,
    val cast: List<String> = emptyList(),
    val trailerURL: String? = null,
    val releaseDate: String? = null,
)

data class TrendingSong(
    val id: String,
    val rank: Int,
    val track: CymbalTrack,
    val cymbalCount: Int,
)

data class TrendingMovie(
    val id: String,
    val rank: Int,
    val movieId: String,
    val movieTitle: String,
    val directorName: String,
    val releaseYear: String,
    val posterURL: String? = null,
    val posterLargeURL: String? = null,
    val tmdbWebURL: String = "",
    val trailerURL: String? = null,
    val movieOverview: String = "",
    val movieRating: Double = 0.0,
    val movieCast: List<String> = emptyList(),
    val cymbalCount: Int = 0,
) {
    fun asCymbalMovie(): CymbalMovie = CymbalMovie(
        id = movieId,
        title = movieTitle,
        directorName = directorName,
        year = releaseYear,
        posterURL = posterURL,
        posterLargeURL = posterLargeURL,
        tmdbWebURL = tmdbWebURL,
        overview = movieOverview,
        rating = movieRating,
        cast = movieCast,
        trailerURL = trailerURL,
    )
}
