package fm.corus.android.ui.components

import org.junit.Assert.*
import org.junit.Test

class InstagramV2PaletteTest {
    @Test fun `recognizable red accent wins over white cover`() {
        val pixels = IntArray(100) { if (it < 25) 0xffb81f29.toInt() else -1 }
        val color = InstagramV2Palette.color(pixels)
        assertTrue(((color shr 16) and 255) > ((color shr 8) and 255) * 3)
    }
    @Test fun `transparent artwork falls back to neutral`() {
        assertEquals(0xff444444.toInt(), InstagramV2Palette.color(IntArray(64)))
    }
    @Test fun `neutral text always has readable contrast`() {
        for (v in 0..255) {
            val color = (255 shl 24) or (v shl 16) or (v shl 8) or v
            val ink = InstagramV2Palette.ink(color, color)
            val l = InstagramV2Palette.luminance(color)
            val contrast = if (ink == -1) 1.05 / (l + .05) else (l + .05) / .05
            assertTrue("contrast=$contrast for gray=$v", contrast >= 4.5)
        }
    }
}
