package fm.corus.android.domain

import fm.corus.android.data.model.MusicService
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [shouldShowSpotifyPlaylistAlert] decides whether tapping "Generate Playlist"
 * shows the "Generate Spotify Playlist?" alert first. TIDAL builds on the user's
 * own account so it never alerts; Apple Music / Deezer have no Android
 * client-side path so they always alert; Spotify alerts only to warn about
 * skipped SoundCloud tracks.
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
    fun `youtube music falls back to a spotify playlist like deezer`() {
        // YouTube Music is link-out only with no client-side playlist path, so it
        // must behave exactly like Deezer: always the Spotify-playlist alert, and
        // usesSpotifyFallback true so it gets the fallback path, not the native
        // one-time explainer.
        assertTrue(usesSpotifyFallback(MusicService.YOUTUBE_MUSIC))
        assertTrue(shouldShowSpotifyPlaylistAlert(MusicService.YOUTUBE_MUSIC, hasSoundCloud = false))
        assertTrue(shouldShowSpotifyPlaylistAlert(MusicService.YOUTUBE_MUSIC, hasSoundCloud = true))
    }

    @Test
    fun `spotify shows the alert only when soundcloud tracks would be skipped`() {
        assertFalse(shouldShowSpotifyPlaylistAlert(MusicService.SPOTIFY, hasSoundCloud = false))
        assertTrue(shouldShowSpotifyPlaylistAlert(MusicService.SPOTIFY, hasSoundCloud = true))
    }

    /**
     * The first-time export explainer is keyed on this: native exporters (TIDAL,
     * Spotify) get the one-time explainer; the Spotify-fallback services (Apple
     * Music, Deezer) always show the Spotify warning instead, never the explainer.
     */
    @Test
    fun `apple music and deezer use the spotify fallback`() {
        assertTrue(usesSpotifyFallback(MusicService.APPLE_MUSIC))
        assertTrue(usesSpotifyFallback(MusicService.DEEZER))
    }

    @Test
    fun `tidal and spotify export natively`() {
        assertFalse(usesSpotifyFallback(MusicService.TIDAL))
        assertFalse(usesSpotifyFallback(MusicService.SPOTIFY))
    }

    // ── Full-export gate ─────────────────────────────────────────────────────

    @Test
    fun `eligible count maps posts to trackCount, likes to likesCount, saves to savesCount`() {
        // Music tab (0) and Film tab (1) both source from posts → trackCount.
        assertEquals(120, profilePlaylistEligibleCount(0, trackCount = 120, likesCount = 5, savesCount = 9))
        assertEquals(120, profilePlaylistEligibleCount(1, trackCount = 120, likesCount = 5, savesCount = 9))
        // Likes tab (2) → likesCount.
        assertEquals(5, profilePlaylistEligibleCount(2, trackCount = 120, likesCount = 5, savesCount = 9))
        // Saves tab (3) → savesCount.
        assertEquals(9, profilePlaylistEligibleCount(3, trackCount = 120, likesCount = 5, savesCount = 9))
    }

    @Test
    fun `unknown counts read as zero`() {
        assertEquals(0, profilePlaylistEligibleCount(0, trackCount = null, likesCount = null, savesCount = 0))
        assertEquals(0, profilePlaylistEligibleCount(2, trackCount = 50, likesCount = null, savesCount = 0))
    }

    @Test
    fun `full export offered only above 75 eligible songs`() {
        assertFalse(shouldOfferProfileFullExport(0, trackCount = 75, likesCount = 0, savesCount = 0))
        assertTrue(shouldOfferProfileFullExport(0, trackCount = 76, likesCount = 0, savesCount = 0))
        // Likes/saves gate on their own counts.
        assertTrue(shouldOfferProfileFullExport(2, trackCount = 0, likesCount = 200, savesCount = 0))
        assertTrue(shouldOfferProfileFullExport(3, trackCount = 0, likesCount = 0, savesCount = 300))
        // Below threshold on the active tab → no chooser even if another count is large.
        assertFalse(shouldOfferProfileFullExport(2, trackCount = 9999, likesCount = 10, savesCount = 9999))
    }

    @Test
    fun `hashtag full export offered only above 75 posts`() {
        assertFalse(shouldOfferHashtagFullExport(0))
        assertFalse(shouldOfferHashtagFullExport(75))
        assertTrue(shouldOfferHashtagFullExport(76))
    }

    @Test
    fun `spotify playlist alert copy names the playlist and soundcloud skip`() {
        assertEquals("Generate Spotify Playlist?", SPOTIFY_PLAYLIST_ALERT_TITLE)
        assertEquals("SoundCloud songs will be skipped.", spotifyPlaylistAlertMessage(true))
        assertEquals("This creates a Spotify playlist.", spotifyPlaylistAlertMessage(false))
    }

    @Test
    fun `playlist export chooser copy names destination and context`() {
        assertEquals("Export playlist", PLAYLIST_EXPORT_CHOOSER_TITLE)
        assertEquals(
            "Export a Spotify playlist of your profile.",
            playlistExportChooserMessage(
                PlaylistExportChooserSource.OwnProfile,
                MusicService.SPOTIFY,
                hasSoundCloud = false,
            ),
        )
        assertEquals(
            "Export a Spotify playlist of your profile. SoundCloud songs will be skipped.",
            playlistExportChooserMessage(
                PlaylistExportChooserSource.OwnProfile,
                MusicService.SPOTIFY,
                hasSoundCloud = true,
            ),
        )
        assertEquals(
            "Export a Spotify playlist of their profile.",
            playlistExportChooserMessage(
                PlaylistExportChooserSource.OtherProfile,
                MusicService.SPOTIFY,
                hasSoundCloud = false,
            ),
        )
        // Apple Music falls back to Spotify on Android.
        assertEquals(
            "Export a Spotify playlist of your profile.",
            playlistExportChooserMessage(
                PlaylistExportChooserSource.OwnProfile,
                MusicService.APPLE_MUSIC,
                hasSoundCloud = false,
            ),
        )
        assertEquals(
            "Export a TIDAL playlist from this hashtag.",
            playlistExportChooserMessage(
                PlaylistExportChooserSource.Hashtag,
                MusicService.TIDAL,
                hasSoundCloud = false,
            ),
        )
    }
}
