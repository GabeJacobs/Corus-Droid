package fm.corus.android.data.repository

import fm.corus.android.data.model.CymbalMovie
import fm.corus.android.data.remote.TMDBApiService
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TMDBRepository @Inject constructor(
    private val tmdbApi: TMDBApiService,
) {
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
        return CymbalMovie(
            id = details.id.toString(),
            title = details.title,
            directorName = details.directorName,
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
}
