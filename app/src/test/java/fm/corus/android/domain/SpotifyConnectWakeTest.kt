package fm.corus.android.domain

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SpotifyConnectWakeTest {

    @Test
    fun hungSilentConnectAbandonsFurtherSilentRetries() {
        assertTrue(
            SpotifyConnectWake.shouldAbandonSilentRetries(
                SpotifyPlaybackError.ApiError("Timed out connecting to Spotify"),
            ),
        )
    }

    @Test
    fun immediateIpcFailureKeepsSilentRetries() {
        assertFalse(
            SpotifyConnectWake.shouldAbandonSilentRetries(
                SpotifyPlaybackError.ApiError("Couldn't connect to Spotify"),
            ),
        )
        assertFalse(
            SpotifyConnectWake.shouldAbandonSilentRetries(
                SpotifyPlaybackError.NotAuthorized(),
            ),
        )
    }
}
