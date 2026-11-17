package fm.corus.android.ui.screens.live

import fm.corus.android.domain.awaitSpotifyPlayback
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LiveSpotifyStartupTest {
    @Test fun waitsForPlaybackAfterSpotifyAcceptsPlay() = runTest {
        assertTrue(awaitSpotifyPlayback { currentTime >= 1_600L })
    }

    @Test fun failsIfSpotifyNeverConfirmsPlayback() = runTest {
        assertFalse(awaitSpotifyPlayback { false })
    }

    @Test fun waitsForTheLiveTrackToAdvanceAndRestartsAfterAnEarlyPause() = runTest {
        var restarted = false
        val samples = ArrayDeque(listOf(0L, null))
        val position = confirmLiveSpotifyStartup(position = { samples.removeFirstOrNull() }) {
            assertFalse(restarted)
            restarted = true
            samples.addAll(listOf(0L, 350L))
        }
        assertTrue(restarted)
        assertEquals(350L, position)
    }

    @Test fun stationarySpotifyCannotStartTheLiveRoom() = runTest {
        var restarts = 0
        val position = confirmLiveSpotifyStartup(position = { 0L }) { restarts++ }
        assertNull(position)
        assertEquals(1, restarts)
    }
}
