package fm.corus.android.ui.player

import fm.corus.android.data.model.CymbalPost
import fm.corus.android.data.model.CymbalTrack
import fm.corus.android.data.model.CymbalUser
import fm.corus.android.data.model.MusicService
import fm.corus.android.data.model.TrackSource
import fm.corus.android.domain.NowPlayingState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FullPlayerMenuTest {

    private fun post(
        source: TrackSource = TrackSource.SPOTIFY,
        artistIds: List<String> = listOf("a1"),
        albumId: String? = "al1",
    ) = CymbalPost(
        id = "p1",
        user = CymbalUser(id = "u1", username = "gabe", displayName = "Gabe"),
        track = CymbalTrack(
            id = "t1",
            name = "Forever",
            artistName = "Noname",
            albumName = "Room 25",
            artistIds = artistIds,
            albumId = albumId,
            source = source,
        ),
    )

    @Test
    fun `open-in label matches feed menu for locked sources`() {
        assertEquals(
            FullPlayerOpenInLabel.OpenSoundCloud,
            fullPlayerOpenInServiceLabelKey(TrackSource.SOUNDCLOUD, MusicService.SPOTIFY),
        )
        assertEquals(
            FullPlayerOpenInLabel.OpenAudiomack,
            fullPlayerOpenInServiceLabelKey(TrackSource.AUDIOMACK, MusicService.SPOTIFY),
        )
        assertEquals(
            FullPlayerOpenInLabel.OpenBandcamp,
            fullPlayerOpenInServiceLabelKey(TrackSource.BANDCAMP, MusicService.SPOTIFY),
        )
        assertEquals(
            FullPlayerOpenInLabel.OpenTidal,
            fullPlayerOpenInServiceLabelKey(TrackSource.TIDAL, MusicService.APPLE_MUSIC),
        )
    }

    @Test
    fun `open-in label uses viewer service for spotify tracks`() {
        assertEquals(
            FullPlayerOpenInLabel.PlayIn("Spotify"),
            fullPlayerOpenInServiceLabelKey(TrackSource.SPOTIFY, MusicService.SPOTIFY),
        )
        assertEquals(
            FullPlayerOpenInLabel.PlayIn("Apple Music"),
            fullPlayerOpenInServiceLabelKey(TrackSource.SPOTIFY, MusicService.APPLE_MUSIC),
        )
    }

    @Test
    fun `share row shows whenever a track is on screen`() {
        assertTrue(fullPlayerShowsShareRow(post().track))
        assertFalse(fullPlayerShowsShareRow(null))
    }

    @Test
    fun `share track prefers the loaded source post`() {
        val source = post()
        val state = NowPlayingState(trackId = "other", trackName = "Other")
        assertEquals(source.track.id, fullPlayerShareTrack(source, state)?.id)
    }

    @Test
    fun `share track falls back to now-playing when there is no source post`() {
        val state = NowPlayingState(
            trackId = "t9",
            trackName = "Forever",
            artistName = "Noname",
            source = TrackSource.SPOTIFY,
        )
        val track = fullPlayerShareTrack(sourcePost = null, state = state)
        assertNotNull(track)
        assertEquals("t9", track!!.id)
        assertEquals("Forever", track.name)
    }

    @Test
    fun `share track is null when nothing is playing`() {
        assertNull(fullPlayerShareTrack(null, NowPlayingState()))
    }

    @Test
    fun `artist and album rows match iOS full-player gating`() {
        assertTrue(fullPlayerShowsArtistRow(TrackSource.SPOTIFY, artistPagesEnabled = true))
        assertTrue(fullPlayerShowsAlbumRow(TrackSource.SPOTIFY, artistPagesEnabled = true))
        assertTrue(fullPlayerShowsArtistRow(TrackSource.AUDIOMACK, artistPagesEnabled = true))
        assertFalse(fullPlayerShowsAlbumRow(TrackSource.AUDIOMACK, artistPagesEnabled = true))
        assertTrue(fullPlayerShowsArtistRow(TrackSource.BANDCAMP, artistPagesEnabled = true))
        assertFalse(fullPlayerShowsAlbumRow(TrackSource.BANDCAMP, artistPagesEnabled = true))
        assertFalse(fullPlayerShowsArtistRow(TrackSource.SPOTIFY, artistPagesEnabled = false))
        assertFalse(fullPlayerShowsArtistRow(TrackSource.SOUNDCLOUD, artistPagesEnabled = true))
        assertFalse(fullPlayerShowsAlbumRow(TrackSource.SOUNDCLOUD, artistPagesEnabled = true))
    }

    @Test
    fun `menu post falls back to now-playing track when source post is null`() {
        val state = NowPlayingState(
            trackId = "t9",
            trackName = "Forever",
            artistName = "Noname",
            source = TrackSource.SPOTIFY,
        )
        val menuPost = fullPlayerMenuPost(sourcePost = null, state = state)
        assertNotNull(menuPost)
        assertEquals("t9", menuPost!!.track.id)
        assertEquals("Forever", menuPost.track.name)
    }

    @Test
    fun `menu post prefers loaded source post`() {
        val source = post()
        val state = NowPlayingState(trackId = "other")
        assertEquals(source.id, fullPlayerMenuPost(source, state)?.id)
    }

    @Test
    fun `menu post is null when nothing is playing`() {
        assertNull(fullPlayerMenuPost(null, NowPlayingState()))
    }
}
