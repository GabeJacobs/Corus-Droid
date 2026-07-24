package fm.corus.android.ui.screens.feed

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Regression tests for the film detail header swapping its poster once posts
 * load.
 *
 * Repro ("The Odyssey"): the tapped search/catalog row seeded the current TMDB
 * poster; the first poster (@mkingsley, a month earlier) had snapshotted an
 * older key-art poster for the same film. Loading posts overwrote the header
 * wholesale, so the poster visibly swapped to the older art.
 *
 * Rule (iOS parity — its header renders the immutable seed movie): the seeded
 * row's identity wins field-by-field; a loaded post only fills fields the seed
 * left blank.
 */
class MergeMovieHeaderTest {

    private val seed = MovieHeaderInfo(
        movieTitle = "The Odyssey",
        directorName = "Christopher Nolan",
        releaseYear = "2026",
        posterURL = "https://route/odyssey-current.jpg",
        posterLargeURL = "https://route/odyssey-current-large.jpg",
        trailerURL = "https://youtu.be/route-trailer",
    )

    private val fromPost = MovieHeaderInfo(
        movieTitle = "The Odyssey",
        directorName = "Christopher Nolan",
        releaseYear = "2026",
        posterURL = "https://post/odyssey-old.jpg",
        posterLargeURL = "https://post/odyssey-old-large.jpg",
        trailerURL = "https://youtu.be/post-trailer",
    )

    @Test
    fun seedPosterWinsOverFirstPostPoster() {
        val merged = mergeMovieHeader(existing = seed, fromPost = fromPost)
        assertEquals("https://route/odyssey-current.jpg", merged.posterURL)
        assertEquals("https://route/odyssey-current-large.jpg", merged.posterLargeURL)
        assertEquals("https://youtu.be/route-trailer", merged.trailerURL)
    }

    @Test
    fun postFillsFieldsTheSeedLeftBlank() {
        val sparseSeed = MovieHeaderInfo(
            movieTitle = "The Odyssey",
            directorName = null,
            releaseYear = "",
            posterURL = "https://route/odyssey-current.jpg",
            posterLargeURL = null,
            trailerURL = null,
        )
        val merged = mergeMovieHeader(existing = sparseSeed, fromPost = fromPost)
        // Seed's own poster + title stay.
        assertEquals("https://route/odyssey-current.jpg", merged.posterURL)
        assertEquals("The Odyssey", merged.movieTitle)
        // Blank/null seed fields fall back to the post.
        assertEquals("Christopher Nolan", merged.directorName)
        assertEquals("2026", merged.releaseYear)
        assertEquals("https://post/odyssey-old-large.jpg", merged.posterLargeURL)
        assertEquals("https://youtu.be/post-trailer", merged.trailerURL)
    }

    @Test
    fun noSeedTakesPostHeaderWholesale() {
        // Deep link / notification: nothing seeded, so the post populates the header.
        val merged = mergeMovieHeader(existing = null, fromPost = fromPost)
        assertEquals(fromPost, merged)
    }
}
