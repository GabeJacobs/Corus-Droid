package fm.corus.android.data.repository

import fm.corus.android.data.model.CymbalMovie
import fm.corus.android.data.remote.TMDBApiService
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock

/**
 * Film search rows come from `searchFilms`, which already drops posterless
 * TMDB objects. The repository must pass those shaped rows through (and
 * seed the director cache) so compose / Search All / Search Film stay in
 * lockstep.
 */
class TMDBRepositoryPosterFilterTest {

    @Test
    fun `searchMovies returns shaped searchFilms rows`() = runTest {
        val api = mock<TMDBApiService> {
            onBlocking { searchFilms(eq("strip club djs"), eq(1)) } doReturn (
                listOf(
                    CymbalMovie(
                        id = "1",
                        title = "Movie 1",
                        directorName = "A Director",
                        posterURL = "https://image.tmdb.org/t/p/w342/ok.jpg",
                    ),
                    CymbalMovie(
                        id = "4",
                        title = "Movie 4",
                        posterURL = "https://image.tmdb.org/t/p/w342/also-ok.jpg",
                    ),
                ) to false
            )
        }

        val movies = TMDBRepository(api).searchMovies("strip club djs")

        assertEquals(listOf("1", "4"), movies.map { it.id })
        assertEquals("A Director", movies[0].directorName)
    }
}
