package fm.corus.android.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Regression coverage for tapping a film attachment inside a comment (or DM).
 *
 * The film detail screen seeds its header from the navigation route's optional
 * metadata, falling back to the first post's data. A film with zero posts that
 * is opened from a comment therefore renders an empty header unless the route
 * carries the title/director/year/poster the comment already holds.
 *
 * The bug: navigation discarded everything but `movieId` (`FilmDetailRoute(movie.id)`),
 * so films reached from a comment looked empty on Android while loading fine on
 * iOS. [CymbalMovie.toFilmDetailRoute] now forwards the full metadata.
 */
class CymbalMovieFilmDetailRouteTest {

    @Test
    fun `toFilmDetailRoute carries full metadata`() {
        val movie = CymbalMovie(
            id = "tmdb_42",
            title = "A Woman",
            directorName = "Giada Colagrande",
            year = "2010",
            posterURL = "https://img/poster.jpg",
            posterLargeURL = "https://img/poster_large.jpg",
            trailerURL = "https://youtu.be/abc",
            releaseDate = "2010-09-04",
        )

        val route = movie.toFilmDetailRoute()

        assertEquals("tmdb_42", route.movieId)
        assertEquals("A Woman", route.movieTitle)
        assertEquals("Giada Colagrande", route.directorName)
        assertEquals("2010", route.releaseYear)
        assertEquals("https://img/poster.jpg", route.posterURL)
        assertEquals("https://img/poster_large.jpg", route.posterLargeURL)
        assertEquals("https://youtu.be/abc", route.trailerURL)
        assertEquals("2010-09-04", route.movieReleaseDate)
    }

    @Test
    fun `comment film attachment forwards header metadata through to the route`() {
        // Mirrors the comment-tap flow: CommentAttachmentCard does
        // onNavigateToFilm(attachedFilm.asCymbalMovie()), then navigation maps
        // that movie to a FilmDetailRoute.
        val attached = CommentAttachedFilm(
            movieId = "tmdb_42",
            movieTitle = "A Woman",
            directorName = "Giada Colagrande",
            releaseYear = "2010",
            posterURL = "https://img/poster.jpg",
            posterLargeURL = "https://img/poster_large.jpg",
        )

        val route = attached.asCymbalMovie().toFilmDetailRoute()

        // The crux of the regression: title/director/year/poster must survive,
        // not just the id.
        assertEquals("tmdb_42", route.movieId)
        assertEquals("A Woman", route.movieTitle)
        assertEquals("Giada Colagrande", route.directorName)
        assertEquals("2010", route.releaseYear)
        assertEquals("https://img/poster.jpg", route.posterURL)
        assertEquals("https://img/poster_large.jpg", route.posterLargeURL)
    }

    @Test
    fun `blank metadata maps to null so the route carries no empty strings`() {
        val movie = CymbalMovie(id = "tmdb_1", title = "Untitled")

        val route = movie.toFilmDetailRoute()

        assertEquals("tmdb_1", route.movieId)
        assertEquals("Untitled", route.movieTitle)
        assertNull(route.directorName)
        assertNull(route.releaseYear)
        assertNull(route.trailerURL)
    }
}
