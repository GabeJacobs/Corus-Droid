package fm.corus.android.domain

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * The coordinator is a singleton `object`, so each test resets the shared slot
 * (and the music-pause hook) before and after it runs.
 */
class TrailerPlaybackCoordinatorTest {

    @Before
    fun setUp() {
        TrailerPlaybackCoordinator.stopAll()
        TrailerPlaybackCoordinator.pauseMusic = null
    }

    @After
    fun tearDown() {
        TrailerPlaybackCoordinator.stopAll()
        TrailerPlaybackCoordinator.pauseMusic = null
    }

    @Test
    fun `play sets the active post id`() {
        TrailerPlaybackCoordinator.play("post-1")
        assertEquals("post-1", TrailerPlaybackCoordinator.activePostId.value)
    }

    @Test
    fun `keeps only one trailer active at a time`() {
        TrailerPlaybackCoordinator.play("post-1")
        TrailerPlaybackCoordinator.play("post-2")
        assertEquals("post-2", TrailerPlaybackCoordinator.activePostId.value)
    }

    @Test
    fun `play pauses music`() {
        var paused = false
        TrailerPlaybackCoordinator.pauseMusic = { paused = true }
        TrailerPlaybackCoordinator.play("post-1")
        assertTrue(paused)
    }

    @Test
    fun `stop clears the slot only when it owns it`() {
        TrailerPlaybackCoordinator.play("post-1")
        TrailerPlaybackCoordinator.stop("post-2") // not the active post — no-op
        assertEquals("post-1", TrailerPlaybackCoordinator.activePostId.value)
        TrailerPlaybackCoordinator.stop("post-1") // the active post — clears
        assertNull(TrailerPlaybackCoordinator.activePostId.value)
    }

    @Test
    fun `stopAll clears whatever is active`() {
        TrailerPlaybackCoordinator.play("post-1")
        TrailerPlaybackCoordinator.stopAll()
        assertNull(TrailerPlaybackCoordinator.activePostId.value)
    }

    @Test
    fun `film-detail trailer shares the single slot with feed trailers`() {
        // The film detail page joins the same slot as feed cards via a
        // "film:<movieId>" key, so starting a film trailer stops a feed post's
        // trailer — and a feed trailer taking over stops the film trailer.
        TrailerPlaybackCoordinator.play("post-1")
        TrailerPlaybackCoordinator.play("film:496243")
        assertEquals("film:496243", TrailerPlaybackCoordinator.activePostId.value)

        TrailerPlaybackCoordinator.play("post-2")
        assertEquals("post-2", TrailerPlaybackCoordinator.activePostId.value)
    }
}
