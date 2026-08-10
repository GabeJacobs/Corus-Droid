package fm.corus.android.domain

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure playback-mode / toggle visibility rules — mirrors iOS SongPlayRoutingTests
 * and PlaybackModeResumeTests (no Context / Spotify install required).
 */
class SongPlayRoutingPlaybackModeTest {

    @Test
    fun feedToggleHiddenWhenAlwaysPlayFullSongs() {
        assertFalse(SongPlayRouting.showsFeedPlaybackModeToggle(alwaysPlayFullSongs = true))
        assertTrue(SongPlayRouting.showsFeedPlaybackModeToggle(alwaysPlayFullSongs = false))
    }

    @Test
    fun realizedSessionMatchesDesiredMode() {
        // Preview + want 30s → match
        assertTrue(
            SongPlayRouting.realizedSessionMatchesDesiredMode(
                isPreviewMode = true,
                desiresFullSong = false,
            ),
        )
        // Full + want full → match
        assertTrue(
            SongPlayRouting.realizedSessionMatchesDesiredMode(
                isPreviewMode = false,
                desiresFullSong = true,
            ),
        )
        // Preview + want full → mismatch
        assertFalse(
            SongPlayRouting.realizedSessionMatchesDesiredMode(
                isPreviewMode = true,
                desiresFullSong = true,
            ),
        )
        // Full + want 30s → mismatch
        assertFalse(
            SongPlayRouting.realizedSessionMatchesDesiredMode(
                isPreviewMode = false,
                desiresFullSong = false,
            ),
        )
    }

    @Test
    fun shouldRestartPausedSessionForDesiredMode() {
        // Paused preview, now wants full → restart
        assertTrue(
            SongPlayRouting.shouldRestartPausedSessionForDesiredMode(
                hasActiveTrack = true,
                isPlaying = false,
                isPreviewMode = true,
                desiresFullSong = true,
            ),
        )
        // Playing → never restart via this gate
        assertFalse(
            SongPlayRouting.shouldRestartPausedSessionForDesiredMode(
                hasActiveTrack = true,
                isPlaying = true,
                isPreviewMode = true,
                desiresFullSong = true,
            ),
        )
        // External Spotify → never restart via this gate
        assertFalse(
            SongPlayRouting.shouldRestartPausedSessionForDesiredMode(
                hasActiveTrack = true,
                isPlaying = false,
                isPreviewMode = false,
                desiresFullSong = false,
                isExternalSpotifyListening = true,
            ),
        )
        // Already matched → no restart
        assertFalse(
            SongPlayRouting.shouldRestartPausedSessionForDesiredMode(
                hasActiveTrack = true,
                isPlaying = false,
                isPreviewMode = true,
                desiresFullSong = false,
            ),
        )
    }
}
