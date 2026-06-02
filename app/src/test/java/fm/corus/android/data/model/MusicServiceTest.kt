package fm.corus.android.data.model

import org.junit.Assert.assertEquals
import org.junit.Test

class MusicServiceTest {

    @Test fun `tidal has expected value and label`() {
        assertEquals("tidal", MusicService.TIDAL.value)
        assertEquals("TIDAL", MusicService.TIDAL.displayLabel)
    }

    @Test fun `fromValue resolves tidal`() {
        assertEquals(MusicService.TIDAL, MusicService.fromValue("tidal"))
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
}
