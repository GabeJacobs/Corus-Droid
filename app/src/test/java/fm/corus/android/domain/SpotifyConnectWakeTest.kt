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

    @Test
    fun idleOrFrozenSessionGetsExtendedVerify() {
        assertTrue(SpotifyConnectWake.shouldUseExtendedVerify(isLiveConnected = false))
        assertFalse(SpotifyConnectWake.shouldUseExtendedVerify(isLiveConnected = true))
    }

    @Test
    fun connectedAloneDoesNotKeepUnverifiedSession() {
        assertFalse(
            SpotifyConnectWake.shouldKeepUnverifiedSession(
                isPlaying = false,
                hasMatchingTrackUri = false,
            ),
        )
    }

    @Test
    fun playingOrMatchingUriKeepsUnverifiedSession() {
        assertTrue(
            SpotifyConnectWake.shouldKeepUnverifiedSession(
                isPlaying = true,
                hasMatchingTrackUri = false,
            ),
        )
        assertTrue(
            SpotifyConnectWake.shouldKeepUnverifiedSession(
                isPlaying = false,
                hasMatchingTrackUri = true,
            ),
        )
    }

    @Test
    fun handoffIgnoresStaleNextUnlessUserSkipped() {
        assertTrue(
            SpotifyConnectWake.shouldIgnoreStaleNextDuringHandoff(
                playIntentInFlight = true,
                userRequestedFeedSkip = false,
                reportedIsNextQueueEntry = true,
            ),
        )
        assertFalse(
            SpotifyConnectWake.shouldIgnoreStaleNextDuringHandoff(
                playIntentInFlight = true,
                userRequestedFeedSkip = true,
                reportedIsNextQueueEntry = true,
            ),
        )
        assertFalse(
            SpotifyConnectWake.shouldIgnoreStaleNextDuringHandoff(
                playIntentInFlight = false,
                userRequestedFeedSkip = false,
                reportedIsNextQueueEntry = true,
            ),
        )
        assertFalse(
            SpotifyConnectWake.shouldIgnoreStaleNextDuringHandoff(
                playIntentInFlight = true,
                userRequestedFeedSkip = false,
                reportedIsNextQueueEntry = false,
            ),
        )
    }
}
