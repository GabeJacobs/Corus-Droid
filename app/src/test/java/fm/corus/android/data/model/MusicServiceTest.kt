package fm.corus.android.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MusicServiceTest {

    @Test fun `tidal has expected value and label`() {
        assertEquals("tidal", MusicService.TIDAL.value)
        assertEquals("TIDAL", MusicService.TIDAL.displayLabel)
    }

    @Test fun `deezer has expected value and label`() {
        assertEquals("deezer", MusicService.DEEZER.value)
        assertEquals("Deezer", MusicService.DEEZER.displayLabel)
    }

    @Test fun `fromValue resolves tidal`() {
        assertEquals(MusicService.TIDAL, MusicService.fromValue("tidal"))
    }

    @Test fun `fromValue resolves deezer`() {
        // The exact wire string persisted to settings.musicService and shared
        // with iOS / web. If this regresses, a Deezer user's stored pref would
        // silently coerce to Spotify on the next fromValue() read.
        assertEquals(MusicService.DEEZER, MusicService.fromValue("deezer"))
    }

    @Test fun `fromValue still resolves existing services`() {
        assertEquals(MusicService.SPOTIFY, MusicService.fromValue("spotify"))
        assertEquals(MusicService.APPLE_MUSIC, MusicService.fromValue("appleMusic"))
    }

    @Test fun `fromValue falls back to spotify for unknown`() {
        assertEquals(MusicService.SPOTIFY, MusicService.fromValue("napster"))
    }

    @Test fun `all services have a non-empty label`() {
        MusicService.entries.forEach { service ->
            assertEquals(service.value, MusicService.fromValue(service.value).value)
            assert(service.displayLabel.isNotBlank())
        }
    }

    @Test fun `youtube music has expected value and label`() {
        // The exact wire string persisted to settings.musicService and shared
        // with iOS / web. A regression would coerce a YouTube Music user's pref
        // to Spotify on the next fromValue() read.
        assertEquals("youtubeMusic", MusicService.YOUTUBE_MUSIC.value)
        assertEquals("YouTube Music", MusicService.YOUTUBE_MUSIC.displayLabel)
        assertEquals(MusicService.YOUTUBE_MUSIC, MusicService.fromValue("youtubeMusic"))
    }

    @Test fun `youtube music is declared below tidal and above deezer`() {
        // Declaration order is allCases order and drives the picker sequence.
        // The product requirement is YouTube Music directly below TIDAL and above
        // Deezer.
        val order = MusicService.entries.map { it.value }
        val tidal = order.indexOf("tidal")
        val ytm = order.indexOf("youtubeMusic")
        val deezer = order.indexOf("deezer")
        assertTrue(tidal < ytm)
        assertTrue(ytm < deezer)
    }
}
