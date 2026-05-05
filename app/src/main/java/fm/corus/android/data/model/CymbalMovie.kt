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

/** Time window the trending cache aggregates over.
 *
 *  The Firestore cache doc stores one ranked list per window plus a legacy
 *  `items` field that mirrors the month list for back-compat with older
 *  builds (rollout window before the next BE refresh tick).
 */
enum class TrendingWindow(val key: String) {
    WEEK("week"),
    MONTH("month"),
    YEAR("year");

    companion object {
        val DEFAULT: TrendingWindow = MONTH

        fun fromKey(value: String?): TrendingWindow =
            values().firstOrNull { it.key == value } ?: DEFAULT
    }
}

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
