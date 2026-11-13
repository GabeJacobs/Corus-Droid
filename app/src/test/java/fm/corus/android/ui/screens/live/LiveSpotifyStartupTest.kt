package fm.corus.android.ui.screens.live

import fm.corus.android.domain.awaitSpotifyPlayback
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
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
}
