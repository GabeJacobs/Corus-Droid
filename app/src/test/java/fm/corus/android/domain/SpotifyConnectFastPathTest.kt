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

    @Test
    fun playIntentArmsUpcomingAfterRequestedTrackConfirmed() {
        assertTrue(
            SpotifyConnectFastPath.shouldArmUpcomingSkipGuard(
                connectPlaying = true,
                hasNext = true,
                feedSkipInFlight = false,
                playIntentInFlight = true,
                lockedOrBackgrounded = true,
                positionSec = 5.0,
                durationSec = 180.0,
                requestedTrackConfirmed = true,
            ),
        )
    }

    @Test
    fun playExpectedOnMisrouteOnlyWhenLocked() {
        assertTrue(SpotifyConnectFastPath.shouldPlayExpectedOnMisroute(locked = true))
        assertFalse(SpotifyConnectFastPath.shouldPlayExpectedOnMisroute(locked = false))
        assertTrue(SpotifyConnectFastPath.shouldMuteUnexpectedWhenUnlocked(locked = false))
        assertFalse(SpotifyConnectFastPath.shouldMuteUnexpectedWhenUnlocked(locked = true))
    }

    @Test
    fun forceAdvanceForAppStateOnlyWhenLocked() {
        assertTrue(SpotifyConnectFastPath.shouldForceAdvanceForAppState(locked = true))
        assertFalse(SpotifyConnectFastPath.shouldForceAdvanceForAppState(locked = false))
    }

    @Test
    fun contextChangeRequiresBothSides() {
        assertTrue(
            SpotifyConnectFastPath.contextLooksLikeManualPick(
                previous = "spotify:album:abc",
                current = "spotify:user:me:collection",
            ),
        )
        assertFalse(
            SpotifyConnectFastPath.contextLooksLikeManualPick(
                previous = null,
                current = "spotify:user:me:collection",
            ),
        )
        assertFalse(
            SpotifyConnectFastPath.contextLooksLikeManualPick(
                previous = "spotify:album:abc",
                current = "spotify:album:abc",
            ),
        )
        assertTrue(
            SpotifyConnectFastPath.contextUnchanged(
                previous = "spotify:album:abc",
                incomingAfterTrackChange = "spotify:album:abc",
            ),
        )
        assertFalse(
            SpotifyConnectFastPath.contextUnchanged(
                previous = "spotify:album:abc",
                incomingAfterTrackChange = "spotify:user:me:collection",
            ),
        )
        assertFalse(
            "Stale currentContext must not count as unchanged — that's a Liked Song hijack",
            SpotifyConnectFastPath.contextUnchanged(
                previous = "spotify:album:abc",
                incomingAfterTrackChange = null,
            ),
        )
    }

    @Test
    fun awayForceAdvanceWaitsThenTreatsMissingContextAsControlCenterNext() {
        assertFalse(
            SpotifyConnectFastPath.shouldForceAdvanceWhenAway(
                awaitingContext = true,
                previousContext = "spotify:album:abc",
                incomingContext = null,
            ),
        )
        assertFalse(
            SpotifyConnectFastPath.shouldForceAdvanceWhenAway(
                awaitingContext = false,
                previousContext = "spotify:album:abc",
                incomingContext = "spotify:user:me:collection",
            ),
        )
        assertTrue(
            SpotifyConnectFastPath.shouldForceAdvanceWhenAway(
                awaitingContext = false,
                previousContext = "spotify:album:abc",
                incomingContext = "spotify:album:abc",
            ),
        )
        assertTrue(
            SpotifyConnectFastPath.shouldForceAdvanceWhenAway(
                awaitingContext = false,
                previousContext = "spotify:album:abc",
                incomingContext = null,
            ),
        )
    }

    @Test
    fun awaySkipDecisionIsShortEnoughToAvoidLongClip() {
        assertTrue(SpotifyConnectFastPath.AWAY_SKIP_DECISION_MS in 50L..150L)
    }

    @Test
    fun libraryPickIsCollectionAlbumArtistNotPlaylist() {
        assertTrue(
            SpotifyConnectFastPath.contextLooksLikeLibraryPick(
                "spotify:user:me:collection",
            ),
        )
        assertTrue(
            SpotifyConnectFastPath.contextLooksLikeLibraryPick("spotify:album:abc"),
        )
        assertTrue(
            SpotifyConnectFastPath.contextLooksLikeLibraryPick("spotify:artist:abc"),
        )
        assertFalse(
            "Control Center Next uses a playlist mix — do not roll back",
            SpotifyConnectFastPath.contextLooksLikeLibraryPick(
                "spotify:playlist:37i9dQZF1F5p3rmiWPIYgZ",
            ),
        )
        assertTrue(
            "This device reports Liked Songs as a playlist with a title",
            SpotifyConnectFastPath.contextLooksLikeLibraryPick(
                uri = "spotify:playlist:37i9dQZF1F5p3rmiWPIYgZ",
                type = "playlist",
                title = "Liked Songs",
            ),
        )
        assertFalse(
            SpotifyConnectFastPath.contextLooksLikeLibraryPick("spotify:track:abc"),
        )
    }

    @Test
    fun stickyTrackContextIsNotImmediateControlCenterNext() {
        assertTrue(SpotifyConnectFastPath.isTrackLevelContext("spotify:track:abc"))
        assertFalse(SpotifyConnectFastPath.isTrackLevelContext("spotify:album:abc"))
        assertFalse(
            "Same spotify:track: context is often a stale Liked Songs event",
            SpotifyConnectFastPath.shouldForceAdvanceWhenAway(
                awaitingContext = false,
                previousContext = "spotify:track:abc",
                incomingContext = "spotify:track:abc",
            ),
        )
    }
}
