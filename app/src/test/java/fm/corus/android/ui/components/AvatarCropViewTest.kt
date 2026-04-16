package fm.corus.android.ui.components

import androidx.compose.ui.geometry.Offset
import org.junit.Assert.assertEquals
import org.junit.Test

class AvatarCropViewTest {

    @Test
    fun `no zoom no pan on square image crops full image`() {
        val (x, y, size) = computeCropRect(
            srcW = 1000, srcH = 1000,
            scale = 1f, offset = Offset.Zero, cropViewPx = 400f,
        )
        assertEquals(0, x)
        assertEquals(0, y)
        assertEquals(1000, size)
    }

    @Test
    fun `no zoom no pan on landscape image crops center square`() {
        // 2000x1000 landscape: display fills 400px height, so 800x400 display
        // ppd = 2000/800 = 2.5;  cropPixels = 400/1 * 2.5 = 1000
        // originX = (2000-1000)/2 = 500, originY = (1000-1000)/2 = 0
        val (x, y, size) = computeCropRect(
            srcW = 2000, srcH = 1000,
            scale = 1f, offset = Offset.Zero, cropViewPx = 400f,
        )
        assertEquals(500, x)
        assertEquals(0, y)
        assertEquals(1000, size)
    }

    @Test
    fun `no zoom no pan on portrait image crops center square`() {
        // 1000x2000 portrait: display fills 400px width, so 400x800 display
        // ppd = 1000/400 = 2.5;  cropPixels = 400/1 * 2.5 = 1000
        // originX = (1000-1000)/2 = 0, originY = (2000-1000)/2 = 500
        val (x, y, size) = computeCropRect(
            srcW = 1000, srcH = 2000,
            scale = 1f, offset = Offset.Zero, cropViewPx = 400f,
        )
        assertEquals(0, x)
        assertEquals(500, y)
        assertEquals(1000, size)
    }

    @Test
    fun `2x zoom halves the crop region`() {
        // 1000x1000, scale=2: cropPixels = 400/2 * (1000/400) = 500
        val (x, y, size) = computeCropRect(
            srcW = 1000, srcH = 1000,
            scale = 2f, offset = Offset.Zero, cropViewPx = 400f,
        )
        assertEquals(250, x)
        assertEquals(250, y)
        assertEquals(500, size)
    }

    @Test
    fun `pan shifts crop origin`() {
        // 1000x1000, pan right by 50 display px shifts crop left
        // ppd = 1000/400 = 2.5; offset.x=50 → shift = 50/1 * 2.5 = 125 src px
        val (x, y, size) = computeCropRect(
            srcW = 1000, srcH = 1000,
            scale = 1f, offset = Offset(50f, 0f), cropViewPx = 400f,
        )
        // originX = (1000-1000)/2 - 125 = -125 → clamped to 0
        assertEquals(0, x)
        assertEquals(0, y)
        assertEquals(1000, size)
    }

    @Test
    fun `pan left with zoom shifts crop right`() {
        // 1000x1000, scale=2, pan left by -50 display px
        // cropPixels = 500; ppd = 2.5
        // originX = (1000-500)/2 - (-50/2)*2.5 = 250 + 62.5 = 313
        val (x, y, size) = computeCropRect(
            srcW = 1000, srcH = 1000,
            scale = 2f, offset = Offset(-50f, 0f), cropViewPx = 400f,
        )
        assertEquals(313, x)
        assertEquals(250, y)
        assertEquals(500, size)
    }
}
