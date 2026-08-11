package fm.corus.android.ui.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FullPlayerAlbumArtTest {

    @Test
    fun `slide only when new track is the prepared up-next`() {
        assertTrue(
            shouldSlideAlbumArt(
                previousTrackId = "a",
                newTrackId = "b",
                preparedNextTrackId = "b",
                newUrl = "https://art/b.jpg",
                preparedNextUrl = "https://art/b.jpg",
            ),
        )
        assertTrue(
            shouldSlideAlbumArt(
                previousTrackId = "a",
                newTrackId = "b",
                preparedNextTrackId = "b",
                newUrl = null,
                preparedNextUrl = "https://art/b.jpg",
            ),
        )
    }

    @Test
    fun `arbitrary jumps snap without sliding`() {
        assertFalse(
            shouldSlideAlbumArt(
                previousTrackId = "a",
                newTrackId = "z",
                preparedNextTrackId = "b",
                newUrl = "https://art/z.jpg",
                preparedNextUrl = "https://art/b.jpg",
            ),
        )
        assertFalse(
            shouldSlideAlbumArt(
                previousTrackId = null,
                newTrackId = "b",
                preparedNextTrackId = "b",
                newUrl = "https://art/b.jpg",
                preparedNextUrl = "https://art/b.jpg",
            ),
        )
    }

    @Test
    fun `slide travel clears the card past the host edge`() {
        // host 400, art 200, pad 8 → 200 + 100 + 8
        assertEquals(308f, albumArtSlideTravelPx(400f, 200f, 8f), 0.01f)
    }
}
