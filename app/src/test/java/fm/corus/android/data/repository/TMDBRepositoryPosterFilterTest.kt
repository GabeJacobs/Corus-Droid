package fm.corus.android.data.repository

import fm.corus.android.data.remote.TMDBApiService
import fm.corus.android.data.remote.TMDBSearchResponse
import fm.corus.android.data.remote.TMDBSearchResult
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock

/**
 * Regression test: film search results with no TMDB poster must be hidden.
 *
 * Bug: searching the Films tab on the compose ("Set Corus") page surfaced
 * TMDB movies with no poster_path (e.g. "Strip Club Dj's" (2003)), which could
 * then be posted as film posts with a blank posterURL. Corus requires artwork
 * on every post — the bot pipeline skips posterless films and the createPost
 * callable now rejects them — so search must not offer them at all.
 */
class TMDBRepositoryPosterFilterTest {

    private fun result(id: Int, posterPath: String?) = TMDBSearchResult(
        id = id,
        title = "Movie $id",
        posterPath = posterPath,
        releaseDate = "2003-01-01",
    )

    @Test
    fun `searchMovies drops results without a poster`() = runTest {
        val api = mock<TMDBApiService> {
            onBlocking { searchMovies(eq("strip club djs"), eq(1)) } doReturn TMDBSearchResponse(
                results = listOf(
                    result(1, posterPath = "/ok.jpg"),
                    result(2, posterPath = null),
                    result(3, posterPath = ""),
                    result(4, posterPath = "/also-ok.jpg"),
                ),
            )
        }

        val movies = TMDBRepository(api).searchMovies("strip club djs")

        assertEquals(listOf("1", "4"), movies.map { it.id })
    }
}
