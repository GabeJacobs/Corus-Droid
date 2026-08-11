package fm.corus.android.domain

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NowPlayingManagerSkipPreviousTest {

    @Test
    fun `restarts when more than 3 seconds into the track`() {
        assertTrue(shouldRestartInsteadOfSkipPrevious(positionMs = 3_001L, queueIndex = 2))
    }

    @Test
    fun `skips previous when at or under 3 seconds with a prior queue item`() {
        assertFalse(shouldRestartInsteadOfSkipPrevious(positionMs = 0L, queueIndex = 1))
        assertFalse(shouldRestartInsteadOfSkipPrevious(positionMs = 3_000L, queueIndex = 3))
    }

    @Test
    fun `restarts when there is no previous queue item`() {
        assertTrue(shouldRestartInsteadOfSkipPrevious(positionMs = 0L, queueIndex = 0))
        assertTrue(shouldRestartInsteadOfSkipPrevious(positionMs = 500L, queueIndex = null))
    }
}
