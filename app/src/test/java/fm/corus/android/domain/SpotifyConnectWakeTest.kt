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
    fun handoffIgnoresStaleNextUntilRequestedTrackSettles() {
        assertTrue(
            SpotifyConnectWake.shouldIgnoreStaleNextDuringHandoff(
                playIntentInFlight = true,
                reportedIsNextQueueEntry = true,
                requestedTrackConfirmed = false,
            ),
        )
        assertTrue(
            SpotifyConnectWake.shouldIgnoreStaleNextDuringHandoff(
                playIntentInFlight = true,
                reportedIsNextQueueEntry = true,
                requestedTrackConfirmed = true,
                confirmedForMs = 200L,
            ),
        )
        assertFalse(
            SpotifyConnectWake.shouldIgnoreStaleNextDuringHandoff(
                playIntentInFlight = true,
                reportedIsNextQueueEntry = true,
                requestedTrackConfirmed = true,
                confirmedForMs = SpotifyConnectWake.HANDOFF_NEXT_SETTLE_MS,
            ),
        )
        assertFalse(
            SpotifyConnectWake.shouldIgnoreStaleNextDuringHandoff(
                playIntentInFlight = false,
                reportedIsNextQueueEntry = true,
                requestedTrackConfirmed = false,
            ),
        )
        assertFalse(
            SpotifyConnectWake.shouldIgnoreStaleNextDuringHandoff(
                playIntentInFlight = true,
                reportedIsNextQueueEntry = false,
                requestedTrackConfirmed = false,
            ),
        )
    }

    @Test
    fun feedSkipDoesNotForceAdvanceUntilRequestedTrackSettles() {
        assertFalse(
            SpotifyConnectWake.shouldForceAdvanceOnUnexpectedDuringFeedSkip(
                playIntentInFlight = true,
                requestedTrackConfirmed = false,
            ),
        )
        assertFalse(
            SpotifyConnectWake.shouldForceAdvanceOnUnexpectedDuringFeedSkip(
                playIntentInFlight = true,
                requestedTrackConfirmed = true,
                confirmedForMs = 200L,
            ),
        )
        assertTrue(
            SpotifyConnectWake.shouldForceAdvanceOnUnexpectedDuringFeedSkip(
                playIntentInFlight = true,
                requestedTrackConfirmed = true,
                confirmedForMs = SpotifyConnectWake.HANDOFF_NEXT_SETTLE_MS,
            ),
        )
        assertTrue(
            SpotifyConnectWake.shouldForceAdvanceOnUnexpectedDuringFeedSkip(
                playIntentInFlight = false,
                requestedTrackConfirmed = false,
            ),
        )
    }
}
