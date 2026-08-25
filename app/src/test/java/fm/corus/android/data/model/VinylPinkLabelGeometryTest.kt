package fm.corus.android.data.model

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Pins pink to the Figma Updated Screen canvas (582 x 441) and the
 * unprocessed 3x PNG export. Album art sits at (105, 58, 270) so the
 * disc stays slightly below the cover, matching the mock.
 */
class VinylPinkLabelGeometryTest {

    private val delta = 1e-4f

    @Test fun `pink uses the Figma Updated Screen canvas`() {
        assertEquals(441f / 582f, VinylStyle.PINK.canvasRatio, delta)
    }

    @Test fun `pink label matches Figma InnerCircle`() {
        assertEquals(311f / 582f, VinylStyle.PINK.labelXFrac, delta)
        assertEquals(142f / 441f, VinylStyle.PINK.labelYFrac, delta)
        assertEquals(102f / 582f, VinylStyle.PINK.labelWFrac, delta)
        assertEquals(104f / 441f, VinylStyle.PINK.labelHFrac, delta)
    }

    @Test fun `pink album art matches Figma Big album Art`() {
        assertEquals(105f / 582f, VinylStyle.PINK.artXFrac, delta)
        assertEquals(58f / 441f, VinylStyle.PINK.artYFrac, delta)
        assertEquals(270f / 582f, VinylStyle.PINK.artSizeFrac, delta)
    }
}
