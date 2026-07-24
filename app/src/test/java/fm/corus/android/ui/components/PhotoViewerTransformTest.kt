package fm.corus.android.ui.components

import androidx.compose.ui.geometry.Offset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class PhotoViewerTransformTest {

    private fun assertFixedPoint(
        scale: Float,
        offset: Offset,
        zoomChange: Float,
        centroid: Offset,
        containerW: Float,
        containerH: Float,
        imageAspect: Float,
    ) {
        val (newScale, newOffset) = anchoredZoom(
            scale = scale,
            offset = offset,
            zoomChange = zoomChange,
            pan = Offset.Zero,
            centroid = centroid,
            containerW = containerW,
            containerH = containerH,
            imageAspect = imageAspect,
        )
        val center = Offset(containerW / 2f, containerH / 2f)
        val contentPoint = (centroid - center - offset) / scale
        val projected = contentPoint * newScale + newOffset + center
        assertEquals(centroid.x, projected.x, 0.001f)
        assertEquals(centroid.y, projected.y, 0.001f)
    }

    @Test
    fun `anchored zoom keeps the image point under the centroid fixed`() {
        assertFixedPoint(
            scale = 1f, offset = Offset.Zero, zoomChange = 2f,
            centroid = Offset(250f, 250f), containerW = 1000f, containerH = 1000f, imageAspect = 1f,
        )
        assertFixedPoint(
            scale = 2f, offset = Offset(100f, -50f), zoomChange = 1.5f,
            centroid = Offset(600f, 300f), containerW = 1000f, containerH = 1000f, imageAspect = 1f,
        )
        assertFixedPoint(
            scale = 1.5f, offset = Offset(0f, 10f), zoomChange = 1.2f,
            centroid = Offset(540f, 500f), containerW = 1080f, containerH = 2000f, imageAspect = 0.8f,
        )
        assertFixedPoint(
            scale = 2f, offset = Offset(50f, 0f), zoomChange = 1.5f,
            centroid = Offset(1200f, 500f), containerW = 2000f, containerH = 1000f, imageAspect = 2.5f,
        )
    }

    @Test
    fun `anchored zoom hard-clamps scale to 4`() {
        val (scale, _) = anchoredZoom(
            scale = 3f, offset = Offset.Zero, zoomChange = 10f, pan = Offset.Zero,
            centroid = Offset(500f, 500f), containerW = 1000f, containerH = 1000f, imageAspect = 1f,
        )
        assertEquals(4f, scale, 0f)
    }

    @Test
    fun `anchored zoom hard-clamps scale to 1`() {
        val (scale, offset) = anchoredZoom(
            scale = 2f, offset = Offset(100f, 100f), zoomChange = 0.1f, pan = Offset.Zero,
            centroid = Offset(500f, 500f), containerW = 1000f, containerH = 1000f, imageAspect = 1f,
        )
        assertEquals(1f, scale, 0f)
        assertEquals(Offset.Zero, offset)
    }

    @Test
    fun `offsets stay zero at scale 1 regardless of pan`() {
        val (scale, offset) = anchoredZoom(
            scale = 1f, offset = Offset.Zero, zoomChange = 1f, pan = Offset(500f, 300f),
            centroid = Offset(100f, 100f), containerW = 1000f, containerH = 1000f, imageAspect = 1f,
        )
        assertEquals(1f, scale, 0f)
        assertEquals(Offset.Zero, offset)
    }

    @Test
    fun `square image in portrait container pins the overflowing axis and centers the other`() {
        assertEquals(
            Offset(500f, 0f),
            clampPhotoOffset(2f, Offset(800f, 300f), 1000f, 2000f, 1f),
        )
        assertEquals(
            Offset(-500f, 0f),
            clampPhotoOffset(2f, Offset(-800f, -300f), 1000f, 2000f, 1f),
        )
        assertEquals(
            Offset.Zero,
            clampPhotoOffset(1f, Offset(400f, 400f), 1000f, 2000f, 1f),
        )
    }

    @Test
    fun `two-three image in landscape container pins the overflowing axis and centers the other`() {
        assertEquals(
            Offset(0f, 500f),
            clampPhotoOffset(2f, Offset(300f, 700f), 2000f, 1000f, 2f / 3f),
        )
        assertEquals(
            Offset(0f, -500f),
            clampPhotoOffset(2f, Offset(-300f, -700f), 2000f, 1000f, 2f / 3f),
        )
    }

    @Test
    fun `unknown aspect clamps to zero offsets with no NaN`() {
        val clamped = clampPhotoOffset(2f, Offset(100f, 100f), 1000f, 2000f, 0f)
        assertEquals(Offset.Zero, clamped)
        assertFalse(clamped.x.isNaN())
        assertFalse(clamped.y.isNaN())
    }

    @Test
    fun `double tap from base zooms to 2_5 anchored at the tap point, clamped`() {
        val (scale, offset) = doubleTapTransform(
            scale = 1f, offset = Offset.Zero, tap = Offset(250f, 800f),
            containerW = 1000f, containerH = 2000f, imageAspect = 1f,
        )
        assertEquals(2.5f, scale, 0.001f)
        assertEquals(375f, offset.x, 0.001f)
        assertEquals(250f, offset.y, 0.001f)

        val (_, unclamped) = doubleTapTransform(
            scale = 1f, offset = Offset.Zero, tap = Offset(400f, 900f),
            containerW = 1000f, containerH = 2000f, imageAspect = 1f,
        )
        assertEquals(150f, unclamped.x, 0.001f)
        assertEquals(150f, unclamped.y, 0.001f)
    }

    @Test
    fun `double tap above the zoomed threshold resets to identity`() {
        val (scale, offset) = doubleTapTransform(
            scale = 2.5f, offset = Offset(120f, 40f), tap = Offset(500f, 500f),
            containerW = 1000f, containerH = 1000f, imageAspect = 1f,
        )
        assertEquals(1f, scale, 0f)
        assertEquals(Offset.Zero, offset)
    }

    @Test
    fun `double tap at 1_005 zooms in`() {
        val (scale, _) = doubleTapTransform(
            scale = 1.005f, offset = Offset.Zero, tap = Offset(500f, 500f),
            containerW = 1000f, containerH = 1000f, imageAspect = 1f,
        )
        assertEquals(2.5f, scale, 0.001f)
    }

    @Test
    fun `double tap at 1_02 resets`() {
        val (scale, offset) = doubleTapTransform(
            scale = 1.02f, offset = Offset(5f, 5f), tap = Offset(500f, 500f),
            containerW = 1000f, containerH = 1000f, imageAspect = 1f,
        )
        assertEquals(1f, scale, 0f)
        assertEquals(Offset.Zero, offset)
    }
}
