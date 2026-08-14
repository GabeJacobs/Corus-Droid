package fm.corus.android.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * System media cards (Samsung Control Center, lock screen) upscale session
 * artwork to a full-bleed background. Publishing [QueuedTrack.albumArtURL]
 * (the 64px thumbnail) is what made preview art look pixelated.
 */
class SessionArtworkUrlTest {

    @Test
    fun `prefers large artwork over the thumbnail`() {
        assertEquals(
            "https://img/large.jpg",
            sessionArtworkUrl(
                albumArtURL = "https://img/small.jpg",
                albumArtLargeURL = "https://img/large.jpg",
            ),
        )
    }

    @Test
    fun `falls back to thumbnail when large is missing`() {
        assertEquals(
            "https://img/small.jpg",
            sessionArtworkUrl(
                albumArtURL = "https://img/small.jpg",
                albumArtLargeURL = null,
            ),
        )
    }

    @Test
    fun `treats blank large url as missing`() {
        assertEquals(
            "https://img/small.jpg",
            sessionArtworkUrl(
                albumArtURL = "https://img/small.jpg",
                albumArtLargeURL = "   ",
            ),
        )
    }

    @Test
    fun `returns null when both urls are missing`() {
        assertNull(sessionArtworkUrl(albumArtURL = null, albumArtLargeURL = null))
        assertNull(sessionArtworkUrl(albumArtURL = "", albumArtLargeURL = "  "))
    }
}
