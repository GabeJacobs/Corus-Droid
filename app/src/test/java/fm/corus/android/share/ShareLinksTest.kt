package fm.corus.android.share

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Port of the iOS extension's parse battery (same URLs, same expectations)
 * so the two platforms can never drift on which links open the composer.
 * The must-be-null cases are the content-safety gate: podcasts, audiobooks,
 * playlists, artists, and random links never enter the share flow.
 */
class ShareLinksTest {

    @Test
    fun `spotify track with share-menu query params parses`() {
        val link = SharedMusicLink.parse(
            "https://open.spotify.com/track/2bQorkqtGzEJatTcD8I1F0?si=nOcHauxWQZyLa8oRRJ4XXw&utm_source=native-share-menu"
        )
        assertEquals(SharedMusicLink.SpotifyTrack("2bQorkqtGzEJatTcD8I1F0"), link)
    }

    @Test
    fun `spotify intl-prefixed track parses`() {
        val link = SharedMusicLink.parse("https://open.spotify.com/intl-fr/track/2bQorkqtGzEJatTcD8I1F0?si=abc")
        assertEquals(SharedMusicLink.SpotifyTrack("2bQorkqtGzEJatTcD8I1F0"), link)
    }

    @Test
    fun `spotify album parses`() {
        val link = SharedMusicLink.parse("https://open.spotify.com/album/2guirTSEqLizK7j9i1MTTZ?si=xyz")
        assertEquals(SharedMusicLink.SpotifyAlbum("2guirTSEqLizK7j9i1MTTZ"), link)
    }

    @Test
    fun `spotify podcast episode show playlist audiobook are rejected`() {
        assertNull(SharedMusicLink.parse("https://open.spotify.com/episode/4rOoJ6Egrf8K2IrywzwOMk"))
        assertNull(SharedMusicLink.parse("https://open.spotify.com/show/4rOoJ6Egrf8K2IrywzwOMk"))
        assertNull(SharedMusicLink.parse("https://open.spotify.com/playlist/37i9dQZF1DXcBWIGoYBM5M"))
        assertNull(SharedMusicLink.parse("https://open.spotify.com/audiobook/7iHfbu1YPACw6oZPAFJtqe"))
    }

    @Test
    fun `apple music song inside album parses with storefront`() {
        val link = SharedMusicLink.parse("https://music.apple.com/us/album/pang/1477916966?i=1477917174")
        assertEquals(SharedMusicLink.AppleMusicSong("1477917174", "us"), link)
    }

    @Test
    fun `apple music direct song link parses`() {
        val link = SharedMusicLink.parse("https://music.apple.com/us/song/cellophane/1441849838")
        assertEquals(SharedMusicLink.AppleMusicSong("1441849838", "us"), link)
    }

    @Test
    fun `apple music album without song id parses as album`() {
        val link = SharedMusicLink.parse("https://music.apple.com/us/album/pang/1477916966")
        assertEquals(SharedMusicLink.AppleMusicAlbum("1477916966", "us"), link)
    }

    @Test
    fun `apple podcasts host is rejected`() {
        assertNull(SharedMusicLink.parse("https://podcasts.apple.com/us/podcast/the-daily/id1200361736"))
    }

    @Test
    fun `soundcloud track parses and playlists profiles are rejected`() {
        assertEquals(
            SharedMusicLink.SoundCloudTrack("https://soundcloud.com/forss/flickermood"),
            SharedMusicLink.parse("https://soundcloud.com/forss/flickermood?in=someone/sets/mix"),
        )
        assertNull(SharedMusicLink.parse("https://soundcloud.com/forss/sets/soulhack"))
        assertNull(SharedMusicLink.parse("https://soundcloud.com/forss"))
    }

    @Test
    fun `deezer track and album parse with locale prefixes`() {
        assertEquals(SharedMusicLink.DeezerTrack("3135556"), SharedMusicLink.parse("https://www.deezer.com/us/track/3135556"))
        assertEquals(SharedMusicLink.DeezerAlbum("302127"), SharedMusicLink.parse("https://www.deezer.com/en/album/302127"))
    }

    @Test
    fun `deezer podcast show is rejected`() {
        assertNull(SharedMusicLink.parse("https://www.deezer.com/us/show/496882"))
    }

    @Test
    fun `tidal track and album parse including browse and share suffix`() {
        assertEquals(SharedMusicLink.TidalTrack("25680741"), SharedMusicLink.parse("https://tidal.com/track/25680741/u"))
        assertEquals(SharedMusicLink.TidalTrack("25680741"), SharedMusicLink.parse("https://tidal.com/browse/track/25680741"))
        assertEquals(SharedMusicLink.TidalAlbum("8674433"), SharedMusicLink.parse("https://listen.tidal.com/album/8674433"))
        assertEquals(SharedMusicLink.TidalAlbum("8674433"), SharedMusicLink.parse("https://tidal.com/browse/album/8674433/u"))
    }

    @Test
    fun `tidal playlists videos artists are rejected`() {
        assertNull(SharedMusicLink.parse("https://tidal.com/playlist/1b418bb8-90a7-4f87-901d-707993838346"))
        assertNull(SharedMusicLink.parse("https://tidal.com/video/12345678"))
        assertNull(SharedMusicLink.parse("https://tidal.com/artist/3634161"))
    }

    @Test
    fun `audiomack song parses and albums playlists are rejected`() {
        assertEquals(
            SharedMusicLink.AudiomackTrack("https://audiomack.com/j-cole/song/no-role-modelz"),
            SharedMusicLink.parse("https://audiomack.com/j-cole/song/no-role-modelz?ref=share"),
        )
        assertNull(SharedMusicLink.parse("https://audiomack.com/j-cole/album/forest-hills-drive"))
        assertNull(SharedMusicLink.parse("https://audiomack.com/j-cole"))
    }

    @Test
    fun `random links are rejected`() {
        assertNull(SharedMusicLink.parse("https://www.youtube.com/watch?v=abc123"))
        assertNull(SharedMusicLink.parse("not a url at all"))
    }

    @Test
    fun `short link hosts are detected`() {
        assertTrue(SharedMusicLink.isShortLink("https://spotify.link/AbCdEf"))
        assertTrue(SharedMusicLink.isShortLink("https://on.soundcloud.com/xyz"))
        assertTrue(SharedMusicLink.isShortLink("https://deezer.page.link/abc"))
        assertFalse(SharedMusicLink.isShortLink("https://open.spotify.com/track/2bQorkqtGzEJatTcD8I1F0"))
    }

    @Test
    fun `firstUrlIn extracts the link from share text`() {
        assertEquals(
            "https://open.spotify.com/track/2bQorkqtGzEJatTcD8I1F0?si=x",
            SharedMusicLink.firstUrlIn("Biting Down by Lorde https://open.spotify.com/track/2bQorkqtGzEJatTcD8I1F0?si=x"),
        )
        assertEquals(
            "https://music.apple.com/us/song/cellophane/1441849838",
            SharedMusicLink.firstUrlIn("Check this: https://music.apple.com/us/song/cellophane/1441849838."),
        )
        assertNull(SharedMusicLink.firstUrlIn("no links here"))
    }

    @Test
    fun `albums are flagged as albums`() {
        assertTrue(SharedMusicLink.parse("https://music.apple.com/us/album/pang/1477916966")!!.isAlbum)
        assertTrue(SharedMusicLink.parse("https://open.spotify.com/album/2guirTSEqLizK7j9i1MTTZ")!!.isAlbum)
        assertFalse(SharedMusicLink.parse("https://open.spotify.com/track/2bQorkqtGzEJatTcD8I1F0")!!.isAlbum)
    }
}
