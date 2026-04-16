package fm.corus.android.data.repository

import fm.corus.android.data.model.CymbalMovie
import fm.corus.android.data.remote.TMDBApiService
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TMDBRepository @Inject constructor(
    private val tmdbApi: TMDBApiService,
) {
    private val directorCache = ConcurrentHashMap<Int, String>()

    suspend fun searchMovies(query: String, page: Int = 1): List<CymbalMovie> {
        val response = tmdbApi.searchMovies(query, page)
        return response.results.map { result ->
            CymbalMovie(
                id = result.id.toString(),
                title = result.title,
                posterURL = TMDBApiService.posterURL(result.posterPath),
                posterLargeURL = TMDBApiService.posterLargeURL(result.posterPath),
                year = result.releaseDate?.take(4) ?: "",
                overview = result.overview,
                rating = result.voteAverage,
                tmdbWebURL = "https://www.themoviedb.org/movie/${result.id}",
            )
        }
    }

    suspend fun getMovieDetails(movieId: Int): CymbalMovie {
        val details = tmdbApi.getMovieDetails(movieId)
        val director = details.directorName
        if (director.isNotEmpty()) directorCache[movieId] = director
        return CymbalMovie(
            id = details.id.toString(),
            title = details.title,
            directorName = director,
            year = details.releaseYear,
            posterURL = TMDBApiService.posterURL(details.posterPath),
            posterLargeURL = TMDBApiService.posterLargeURL(details.posterPath),
            tmdbWebURL = "https://www.themoviedb.org/movie/${details.id}",
            overview = details.overview,
            rating = details.voteAverage,
            cast = details.castNames,
            trailerURL = details.trailerURL,
        )
    }

    suspend fun getDirectorName(movieId: Int): String {
        directorCache[movieId]?.let { return it }
        return try {
            val credits = tmdbApi.getMovieCredits(movieId)
            val directors = credits.crew.filter { it.job == "Director" }.map { it.name }
            val name = if (directors.isEmpty()) "Unknown" else directors.joinToString(", ")
            directorCache[movieId] = name
            name
        } catch (_: Exception) {
            "Unknown"
        }
    }

    suspend fun prefetchDirectors(movies: List<CymbalMovie>): List<CymbalMovie> {
        val uncached = movies.filter { directorCache[it.id.toIntOrNull() ?: -1] == null }
        if (uncached.isEmpty()) {
            return movies.map { it.copy(directorName = directorCache[it.id.toIntOrNull() ?: -1] ?: "") }
        }
        coroutineScope {
            uncached.map { movie ->
                async {
                    val movieId = movie.id.toIntOrNull() ?: return@async
                    getDirectorName(movieId)
                }
            }.awaitAll()
        }
        return movies.map { movie ->
            val movieId = movie.id.toIntOrNull() ?: -1
            movie.copy(directorName = directorCache[movieId] ?: "")
        }
    }
}
