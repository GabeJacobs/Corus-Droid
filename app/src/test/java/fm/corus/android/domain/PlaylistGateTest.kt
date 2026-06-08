package fm.corus.android.domain

import fm.corus.android.data.model.MusicService
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [shouldShowSpotifyPlaylistAlert] decides whether tapping "Generate Playlist"
 * shows the "Spotify Feature" alert first. TIDAL builds on the user's own
 * account so it never alerts; Apple Music / Deezer have no Android client-side
 * path so they always alert; Spotify alerts only to warn about skipped
 * SoundCloud tracks.
 */
class PlaylistGateTest {

    @Test
    fun `tidal never shows the alert`() {
        assertFalse(shouldShowSpotifyPlaylistAlert(MusicService.TIDAL, hasSoundCloud = false))
        assertFalse(shouldShowSpotifyPlaylistAlert(MusicService.TIDAL, hasSoundCloud = true))
    }

    @Test
    fun `apple music and deezer always show the alert`() {
        assertTrue(shouldShowSpotifyPlaylistAlert(MusicService.APPLE_MUSIC, hasSoundCloud = false))
        assertTrue(shouldShowSpotifyPlaylistAlert(MusicService.APPLE_MUSIC, hasSoundCloud = true))
        assertTrue(shouldShowSpotifyPlaylistAlert(MusicService.DEEZER, hasSoundCloud = false))
        assertTrue(shouldShowSpotifyPlaylistAlert(MusicService.DEEZER, hasSoundCloud = true))
    }

    @Test
    fun `spotify shows the alert only when soundcloud tracks would be skipped`() {
        assertFalse(shouldShowSpotifyPlaylistAlert(MusicService.SPOTIFY, hasSoundCloud = false))
        assertTrue(shouldShowSpotifyPlaylistAlert(MusicService.SPOTIFY, hasSoundCloud = true))
    }
}
