package fm.corus.android.data.model

import fm.corus.android.ui.navigation.FilmDetailRoute

data class CymbalMovie(
    val id: String,
    val title: String,
    val directorName: String = "",
    /**
     * Per-director TMDB IDs from the search response. Used by ID-based taste
     * matching for film co-directors (e.g. the Coen brothers as one credit).
     * Empty for results decoded from older data.
     */
    val directorIds: List<String> = emptyList(),
    val year: String = "",
    val posterURL: String? = null,
    val posterLargeURL: String? = null,
    val tmdbWebURL: String = "",
    val overview: String = "",
    val rating: Double = 0.0,
    val cast: List<String> = emptyList(),
    val trailerURL: String? = null,
    val releaseDate: String? = null,
) {
    /**
     * Seed the film detail route with the metadata we already hold so the
     * header renders immediately — even for films with zero posts. Without
     * this, [FilmDetailRoute] would carry only the id and the detail screen
     * could populate its header only from the first post, leaving films
     * reached from a comment/DM attachment with no posts looking empty.
     */
    fun toFilmDetailRoute() = FilmDetailRoute(
        movieId = id,
        movieTitle = title.ifBlank { null },
        directorName = directorName.ifBlank { null },
        releaseYear = year.ifBlank { null },
        posterURL = posterURL,
        posterLargeURL = posterLargeURL,
        trailerURL = trailerURL,
        movieReleaseDate = releaseDate,
    )
}

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
        val DEFAULT: TrendingWindow = WEEK

        fun fromKey(value: String?): TrendingWindow =
            values().firstOrNull { it.key == value } ?: DEFAULT
    }
}

/** Windowed trending hashtag row. `cymbalCount` is the post count for the
 *  selected window (week/month/year), not all-time. `followerCount` is
 *  denormalized onto `trending_cache/hashtags` so the subtitle can render
 *  "N coruses · M followers" without a per-row Firestore read. */
data class TrendingHashtag(
    val id: String,
    val rank: Int,
    val name: String,
    val cymbalCount: Int,
    val followerCount: Int = 0,
)

/** One contributor row, denormalized onto `hashtags/{tag}/contributors/{uid}`
 *  so the facepile + list can read without per-user joins. */
data class HashtagContributor(
    val id: String,
    val username: String,
    val displayName: String,
    val photoURL: String?,
    val lastPostedAtMs: Long,
)

data class TrendingSong(
    val id: String,
    val rank: Int,
    val track: CymbalTrack,
    val cymbalCount: Int,
)

/** One row of trending_cache/artists — song counters re-merged by artist
 *  credit server-side; artwork is the artist's most-posted song's. Carries no
 *  artist ids (counters are name-keyed), so picks made from it are name-only. */
data class TrendingArtist(
    val id: String,      // lowercased artist name
    val rank: Int,
    val artistName: String,
    val albumArtURL: String? = null,
    val cymbalCount: Int = 0,
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
    /** Full YYYY-MM-DD release date, denormalized onto the trending cache so the
     *  film page can badge NEW RELEASE on the first frame. Null for older entries. */
    val movieReleaseDate: String? = null,
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
        releaseDate = movieReleaseDate,
    )
}
