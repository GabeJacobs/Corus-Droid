package fm.corus.android.data.repository

import fm.corus.android.data.model.ArtistSummary
import fm.corus.android.data.model.CymbalMovie
import fm.corus.android.data.remote.TMDBApiService
import fm.corus.android.data.remote.TMDBPerson
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Client-side filter for the Film tab's director row (artist-pages feature),
 * mirroring the web implementation (`app/api/movies/search-people/route.ts`):
 * TMDB person search also matches aliases/associations — searching "pavement"
 * returns the band's bassist — so require an actual NAME match (bidirectional
 * contains, same rule as the Music tab's artist row). Prefer people whose
 * known-for department is Directing; fall back to the single top name-matched
 * person so director-actors (e.g. Greta Gerwig, TMDB department "Acting")
 * still surface. Max 3. Top-level + pure so it's unit-testable.
 */
internal fun filterDirectorSearchResults(
    people: List<TMDBPerson>,
    query: String,
): List<ArtistSummary> {
    val queryLower = query.trim().lowercase()
    if (queryLower.isEmpty()) return emptyList()
    val nameMatched = people.filter { p ->
        val nameLower = p.name.trim().lowercase()
        nameLower.isNotEmpty() && (nameLower.contains(queryLower) || queryLower.contains(nameLower))
    }
    val directors = nameMatched.filter { it.knownForDepartment == "Directing" }
    val chosen = (directors.ifEmpty { nameMatched.take(1) }).take(3)
    return chosen.map { p ->
        ArtistSummary(
            id = p.id.toString(),
            name = p.name,
            imageUrl = p.profilePath?.let { "https://image.tmdb.org/t/p/w185$it" },
        )
    }
}

@Singleton
class TMDBRepository @Inject constructor(
    private val tmdbApi: TMDBApiService,
) {
    private val directorCache = ConcurrentHashMap<Int, String>()

    suspend fun searchMovies(query: String, page: Int = 1): List<CymbalMovie> {
        val (films, _) = tmdbApi.searchFilms(query, page)
        films.forEach { movie ->
            val movieId = movie.id.toIntOrNull() ?: return@forEach
            if (movie.directorName.isNotEmpty()) directorCache[movieId] = movie.directorName
        }
        return films
    }

    suspend fun getMovieDetails(movieId: Int): CymbalMovie {
        val details = tmdbApi.getMovieDetails(movieId)
        val director = details.directorName
        if (director.isNotEmpty()) directorCache[movieId] = director
        return CymbalMovie(
            id = details.id.toString(),
            title = details.title,
            directorName = director,
            directorIds = details.directorIds,
            year = details.releaseYear,
            posterURL = TMDBApiService.posterURL(details.posterPath),
            posterLargeURL = TMDBApiService.posterLargeURL(details.posterPath),
            tmdbWebURL = "https://www.themoviedb.org/movie/${details.id}",
            overview = details.overview,
            rating = details.voteAverage,
            cast = details.castNames,
            trailerURL = details.trailerURL,
            releaseDate = details.releaseDate,
        )
    }

    /**
     * Director rows for the Film search tab. Wraps the tmdbProxy `personSearch`
     * action and applies the shared name-match + Directing-preference filter.
     */
    suspend fun searchDirectors(query: String): List<ArtistSummary> {
        if (query.isBlank()) return emptyList()
        return filterDirectorSearchResults(tmdbApi.searchPeople(query).results, query)
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
