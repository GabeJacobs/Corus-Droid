package fm.corus.android.domain

import org.junit.Assert.*
import org.junit.Test

class LiveSpotifyPreviewGuardTest {
    private val room = "spotify:track:room"
    private fun ready(position: Long = 21_000, duration: Long = 138_000) = LiveSpotifyPreviewGuard().apply {
        follow(room, 1, 0, room)
        backgrounded = true
        allowed = true
        observe(position, duration, false, 10_000)
    }

    @Test fun contextBeforeTrackReleasesWithoutPause() {
        val guard = ready()
        guard.context("spotify:playlist:picked", 10_000, true, false)
        val selected = guard.mismatch("spotify:track:picked", false, 10_100, true, false)!!
        assertFalse(selected.resume)
        assertTrue(guard.suspended)
        assertTrue(guard.isCurrent(selected))
    }

    @Test fun trackBeforeContextRequiresFreshConfirmationAndResume() {
        val guard = ready()
        assertNull(guard.mismatch("spotify:track:picked", false, 10_100, true, false))
        assertFalse(guard.suspended)
        // The protective pause acknowledgement cannot itself release playback.
        assertNull(guard.mismatch("spotify:track:picked", true, 10_200, true, false))
        val selected = guard.context("spotify:album:picked", 10_300, true, false)!!
        assertTrue(selected.resume)
        assertTrue(guard.suspended)
    }

    @Test fun staleContextAndStalePlayingSamplesNeverRelease() {
        val staleContext = ready()
        staleContext.context("spotify:playlist:picked", 8_000, true, false)
        assertNull(staleContext.mismatch("spotify:track:picked", false, 10_100, true, false))
        assertNull(staleContext.context("spotify:playlist:picked", 11_700, true, false))
        assertFalse(staleContext.suspended)
        val staleSample = ready()
        staleSample.context("spotify:playlist:picked", 14_000, true, false)
        assertNull(staleSample.mismatch("spotify:track:picked", false, 14_100, true, false))
    }

    @Test fun naturalQueueAndNearEndContextChangesRemainProtected() {
        val nativeQueue = ready()
        assertNull(nativeQueue.mismatch("spotify:track:queue", false, 10_100, true, false))
        assertNull(nativeQueue.context(room, 10_200, true, false))
        for (position in listOf(124_000L, 137_500L)) {
            val nearEnd = ready(position)
            nearEnd.context("spotify:playlist:picked", 10_000, true, false)
            assertNull(nearEnd.mismatch("spotify:track:picked", false, 10_100, true, false))
            assertFalse(nearEnd.suspended)
        }
    }

    @Test fun foregroundLockCommandsPausedStateAndRoomHandoffsNeverYield() {
        for (condition in 0..6) {
            val guard = ready()
            if (condition == 0) guard.backgrounded = false
            if (condition == 1) guard.allowed = false
            if (condition == 4) guard.observe(21_000, 138_000, true, 10_000)
            if (condition == 5) guard.follow("spotify:track:next", 2, 10_000, null)
            if (condition == 6) guard.follow(room, 2, 9_000, room)
            guard.context("spotify:playlist:picked", 10_000, true, false)
            assertNull(guard.mismatch("spotify:track:picked", false, 10_100, condition != 2, condition == 3))
            assertFalse(guard.suspended)
        }
    }

    @Test fun unconfirmedRoomContextDoesNotPermitTakeover() {
        val guard = LiveSpotifyPreviewGuard()
        guard.follow(room, 1, 0, "spotify:playlist:old-feed")
        guard.backgrounded = true
        guard.allowed = true
        guard.observe(21_000, 138_000, false, 10_000)
        guard.context("spotify:playlist:picked", 10_000, true, false)
        assertNull(guard.mismatch("spotify:track:picked", false, 10_100, true, false))
    }

    @Test fun leavingOrReturningInvalidatesPendingRelease() {
        val guard = ready()
        guard.context("spotify:playlist:picked", 10_000, true, false)
        val selected = guard.mismatch("spotify:track:picked", false, 10_100, true, false)!!
        guard.clear()
        assertFalse(guard.suspended)
        assertFalse(guard.isCurrent(selected))
        guard.follow(room, 2, 10_500, room)
        guard.allowed = true
        guard.observe(21_000, 138_000, false, 10_600)
        assertNull(guard.mismatch("spotify:track:picked", false, 10_700, true, false))
    }
}
