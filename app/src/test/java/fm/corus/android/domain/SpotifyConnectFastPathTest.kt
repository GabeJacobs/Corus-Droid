package fm.corus.android.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SpotifyConnectFastPathTest {

    @Test
    fun midTrackForegroundDoesNotArm() {
        assertFalse(
            SpotifyConnectFastPath.shouldArmUpcomingSkipGuard(
                connectPlaying = true,
                hasNext = true,
                feedSkipInFlight = false,
                playIntentInFlight = false,
                lockedOrBackgrounded = false,
                positionSec = 30.0,
                durationSec = 180.0,
            ),
        )
    }

    @Test
    fun nearEndArmsEvenWhenForeground() {
        assertTrue(
            SpotifyConnectFastPath.shouldArmUpcomingSkipGuard(
                connectPlaying = true,
                hasNext = true,
                feedSkipInFlight = false,
                playIntentInFlight = false,
                lockedOrBackgrounded = false,
                positionSec = 176.0,
                durationSec = 180.0,
            ),
        )
        assertEquals(
            SpotifyConnectFastPath.NEAR_END_GUARD_MS,
            SpotifyConnectFastPath.guardDurationMs(lockedOrBackgrounded = false),
        )
    }

    @Test
    fun lockedArmsMidTrack() {
        assertTrue(
            SpotifyConnectFastPath.shouldArmUpcomingSkipGuard(
                connectPlaying = true,
                hasNext = true,
                feedSkipInFlight = false,
                playIntentInFlight = false,
                lockedOrBackgrounded = true,
                positionSec = 30.0,
                durationSec = 180.0,
            ),
        )
        assertEquals(
            SpotifyConnectFastPath.LOCKED_GUARD_MS,
            SpotifyConnectFastPath.guardDurationMs(lockedOrBackgrounded = true),
        )
    }

    @Test
    fun forceAdvanceWindowDoesNotReArmPastNext() {
        assertFalse(
            SpotifyConnectFastPath.shouldArmUpcomingSkipGuard(
                connectPlaying = true,
                hasNext = true,
                feedSkipInFlight = true,
                playIntentInFlight = false,
                lockedOrBackgrounded = true,
                positionSec = 0.2,
                durationSec = 180.0,
            ),
        )
    }

    @Test
    fun playIntentInFlightDoesNotArm() {
        assertFalse(
            SpotifyConnectFastPath.shouldArmUpcomingSkipGuard(
                connectPlaying = true,
                hasNext = true,
                feedSkipInFlight = false,
                playIntentInFlight = true,
                lockedOrBackgrounded = true,
                positionSec = 176.0,
                durationSec = 180.0,
            ),
        )
    }
}
