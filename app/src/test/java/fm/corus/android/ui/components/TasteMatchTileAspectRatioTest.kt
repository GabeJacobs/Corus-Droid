package fm.corus.android.ui.components

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Regression coverage for Taste Match sheet tile artwork shape.
 *
 * The bug: every sheet tile was forced to a 1:1 box with ContentScale.Crop, so
 * film posters were cropped to a square instead of keeping their native 2:3.
 * Films/directors must show the full 2:3 poster; songs/artists stay square.
 * Mirrors iOS (MoviePosterView vs AlbumArtView) and web (poster variant).
 */
class TasteMatchTileAspectRatioTest {

    @Test
    fun `films keep their native 2 to 3 poster`() {
        assertEquals(2f / 3f, tasteMatchTileAspectRatio(isMovie = true))
    }

    @Test
    fun `songs and artists stay square`() {
        assertEquals(1f, tasteMatchTileAspectRatio(isMovie = false))
    }
}
