package fm.corus.android.data.remote

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TMDBApiService @Inject constructor(
    private val client: HttpClient,
) {
    companion object {
        private const val BASE_URL = "https://api.themoviedb.org/3"
        private const val IMAGE_BASE_URL = "https://image.tmdb.org/t/p/"
        // Same API key as iOS app — read from BuildConfig in production
        private const val API_KEY = "YOUR_TMDB_API_KEY"

        fun posterURL(path: String?, size: String = "w342"): String? {
            path ?: return null
            return "$IMAGE_BASE_URL$size$path"
        }

        fun posterLargeURL(path: String?): String? = posterURL(path, "w780")
    }

    suspend fun searchMovies(query: String, page: Int = 1): TMDBSearchResponse {
        return client.get("$BASE_URL/search/movie") {
            parameter("api_key", API_KEY)
            parameter("query", query)
            parameter("page", page)
        }.body()
    }

    suspend fun getMovieDetails(movieId: Int): TMDBMovieDetails {
        return client.get("$BASE_URL/movie/$movieId") {
            parameter("api_key", API_KEY)
            parameter("append_to_response", "credits,videos")
        }.body()
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
    val credits: TMDBCredits? = null,
    val videos: TMDBVideos? = null,
) {
    val directorName: String
        get() = credits?.crew?.firstOrNull { it.job == "Director" }?.name ?: ""

    val castNames: List<String>
        get() = credits?.cast?.take(10)?.map { it.name } ?: emptyList()

    val trailerURL: String?
        get() = videos?.results?.firstOrNull { it.type == "Trailer" && it.site == "YouTube" }
            ?.let { "https://www.youtube.com/watch?v=${it.key}" }

    val releaseYear: String
        get() = releaseDate?.take(4) ?: ""
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
