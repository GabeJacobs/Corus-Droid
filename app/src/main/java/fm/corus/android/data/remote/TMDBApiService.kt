package fm.corus.android.data.remote

import com.google.firebase.functions.FirebaseFunctions
import kotlinx.coroutines.tasks.await
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TMDBApiService @Inject constructor(
    private val functions: FirebaseFunctions,
    private val json: Json,
) {
    companion object {
        private const val IMAGE_BASE_URL = "https://image.tmdb.org/t/p/"

        fun posterURL(path: String?, size: String = "w342"): String? {
            path ?: return null
            return "$IMAGE_BASE_URL$size$path"
        }

        fun posterLargeURL(path: String?): String? = posterURL(path, "w780")
    }

    /**
     * Calls the `tmdbProxy` Cloud Function and returns TMDB's raw JSON body.
     *
     * Movie data is fetched through the backend instead of straight from the
     * device because `api.themoviedb.org` is network-blocked in some regions
     * (e.g. Russia), where direct calls failed 100% of the time. The proxy
     * runs on Google's network and keeps the TMDB API key server-side.
     */
    private suspend fun callTmdb(params: Map<String, Any?>): String {
        val result = functions.getHttpsCallable("tmdbProxy").call(params).await()
        val data = result.getData() as? Map<*, *>
        return data?.get("body") as? String
            ?: throw IllegalStateException("tmdbProxy returned no body")
    }

    suspend fun searchMovies(query: String, page: Int = 1): TMDBSearchResponse {
        val body = callTmdb(mapOf("action" to "search", "query" to query, "page" to page))
        return json.decodeFromString(body)
    }

    suspend fun getMovieDetails(movieId: Int): TMDBMovieDetails {
        val body = callTmdb(
            mapOf(
                "action" to "details",
                "movieId" to movieId,
                "appendToResponse" to "credits,videos",
            )
        )
        return json.decodeFromString(body)
    }

    suspend fun getMovieCredits(movieId: Int): TMDBCredits {
        val body = callTmdb(mapOf("action" to "credits", "movieId" to movieId))
        return json.decodeFromString(body)
    }
}

// ── TMDB Response Models ──

@Serializable
data class TMDBSearchResponse(
    val results: List<TMDBSearchResult> = emptyList(),
    val page: Int = 1,
    @SerialName("total_pages") val totalPages: Int = 0,
    @SerialName("total_results") val totalResults: Int = 0,
)

@Serializable
data class TMDBSearchResult(
    val id: Int,
    val title: String = "",
    @SerialName("poster_path") val posterPath: String? = null,
    @SerialName("release_date") val releaseDate: String? = null,
    @SerialName("vote_average") val voteAverage: Double = 0.0,
    val overview: String = "",
)

@Serializable
data class TMDBMovieDetails(
    val id: Int,
    val title: String = "",
    @SerialName("poster_path") val posterPath: String? = null,
    @SerialName("release_date") val releaseDate: String? = null,
    @SerialName("vote_average") val voteAverage: Double = 0.0,
    val overview: String = "",
    val runtime: Int? = null,
    val genres: List<TMDBGenre> = emptyList(),
    @SerialName("production_countries") val productionCountries: List<TMDBCountry> = emptyList(),
    @SerialName("original_language") val originalLanguage: String = "",
    val status: String = "",
    @SerialName("imdb_id") val imdbId: String? = null,
    val homepage: String? = null,
    val credits: TMDBCredits? = null,
    val videos: TMDBVideos? = null,
) {
    val directorName: String
        get() = credits?.crew?.filter { it.job == "Director" }?.joinToString(", ") { it.name } ?: ""

    /**
     * Per-director TMDB IDs from the credits crew. Used by ID-based taste
     * matching for film co-directors (e.g. the Coen brothers as one credit).
     * Empty when the credits payload is missing or contains no Director rows.
     */
    val directorIds: List<String>
        get() = credits?.crew?.filter { it.job == "Director" }?.map { it.id.toString() } ?: emptyList()

    val castNames: List<String>
        get() = credits?.cast?.take(10)?.map { it.name } ?: emptyList()

    val trailerURL: String?
        get() = videos?.results?.firstOrNull { it.type == "Trailer" && it.site == "YouTube" }
            ?.let { "https://www.youtube.com/watch?v=${it.key}" }

    val releaseYear: String
        get() = releaseDate?.take(4) ?: ""

    val runtimeText: String?
        get() {
            val mins = runtime ?: return null
            if (mins <= 0) return null
            val h = mins / 60
            val m = mins % 60
            return if (h > 0) "${h}h ${m}m" else "${m}m"
        }

    val genreNames: List<String>
        get() = genres.map { it.name }

    val countryName: String?
        get() = productionCountries.firstOrNull()?.name

    val languageDisplayName: String?
        get() {
            if (originalLanguage.isBlank()) return null
            val locale = java.util.Locale.forLanguageTag(originalLanguage)
            val name = locale.getDisplayLanguage(java.util.Locale.ENGLISH)
            return name.ifBlank { null }
        }

    val castWithCharacters: List<TMDBCastEntry>
        get() = credits?.cast?.take(5)?.map { TMDBCastEntry(it.name, it.character) } ?: emptyList()

    val writers: List<String>
        get() {
            val writerJobs = setOf("Screenplay", "Writer", "Story", "Novel", "Short Story")
            return credits?.crew
                ?.filter { it.job in writerJobs }
                ?.map { it.name }
                ?.distinct()
                ?: emptyList()
        }

    val imdbURL: String?
        get() = imdbId?.let { "https://www.imdb.com/title/$it" }

    val homepageURL: String?
        get() = homepage?.takeIf { it.isNotBlank() }
}

@Serializable
data class TMDBCredits(
    val cast: List<TMDBCastMember> = emptyList(),
    val crew: List<TMDBCrewMember> = emptyList(),
)

@Serializable
data class TMDBCastMember(
    val id: Int = 0,
    val name: String = "",
    val character: String = "",
)

@Serializable
data class TMDBCrewMember(
    val id: Int = 0,
    val name: String = "",
    val job: String = "",
)

@Serializable
data class TMDBVideos(
    val results: List<TMDBVideo> = emptyList(),
)

@Serializable
data class TMDBVideo(
    val key: String = "",
    val site: String = "",
    val type: String = "",
)

@Serializable
data class TMDBGenre(
    val id: Int = 0,
    val name: String = "",
)

@Serializable
data class TMDBCountry(
    @SerialName("iso_3166_1") val iso: String = "",
    val name: String = "",
)

data class TMDBCastEntry(
    val name: String,
    val character: String,
)
