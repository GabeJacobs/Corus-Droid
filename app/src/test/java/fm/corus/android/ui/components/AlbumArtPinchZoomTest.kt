package fm.corus.android.ui.components

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AlbumArtPinchZoomTest {

    private val box = Size(1000f, 1000f)

    private fun assertFixedPoint(
        scale: Float,
        offset: Offset,
        zoomChange: Float,
        centroid: Offset,
    ) {
        val (newScale, newOffset) = albumArtAnchoredZoom(
            scale = scale,
            offset = offset,
            zoomChange = zoomChange,
            pan = Offset.Zero,
            centroid = centroid,
            container = box,
        )
        val center = Offset(box.width / 2f, box.height / 2f)
        val contentPoint = (centroid - center - offset) / scale
        val projected = contentPoint * newScale + newOffset + center
        assertEquals(centroid.x, projected.x, 0.001f)
        assertEquals(centroid.y, projected.y, 0.001f)
    }

    @Test
    fun `anchored zoom keeps the art point under the centroid fixed`() {
        assertFixedPoint(scale = 1f, offset = Offset.Zero, zoomChange = 2f, centroid = Offset(250f, 250f))
        assertFixedPoint(scale = 2f, offset = Offset(100f, -50f), zoomChange = 1.5f, centroid = Offset(600f, 300f))
    }

    @Test
    fun `scale hard-clamps to 4`() {
        val (scale, _) = albumArtAnchoredZoom(
            scale = 3f,
            offset = Offset.Zero,
            zoomChange = 10f,
            pan = Offset.Zero,
            centroid = Offset(500f, 500f),
            container = box,
        )
        assertEquals(4f, scale, 0f)
    }

    @Test
    fun `scale hard-clamps to 1 and clears offset`() {
        val (scale, offset) = albumArtAnchoredZoom(
            scale = 2f,
            offset = Offset(100f, 100f),
            zoomChange = 0.1f,
            pan = Offset.Zero,
            centroid = Offset(500f, 500f),
            container = box,
        )
        assertEquals(1f, scale, 0f)
        assertEquals(Offset.Zero, offset)
    }

    @Test
    fun `offset stays inside the zoomed bounds`() {
        val offset = clampAlbumArtOffset(2f, Offset(2000f, -2000f), box)
        assertEquals(500f, offset.x, 0f)
        assertEquals(-500f, offset.y, 0f)
    }

    @Test
    fun `two finger pan at constant scale moves the art by the centroid delta`() {
        val (scale, offset) = albumArtAnchoredZoom(
            scale = 2f,
            offset = Offset(40f, -20f),
            zoomChange = 1f,
            pan = Offset(60f, -30f),
            centroid = Offset(460f, 370f),
            container = box,
        )
        assertEquals(2f, scale, 0f)
        assertEquals(100f, offset.x, 0.001f)
        assertEquals(-50f, offset.y, 0.001f)
    }

    @Test
    fun `pinch and pan keeps the previous content point under the new centroid`() {
        val scale = 2f
        val offset = Offset(40f, -20f)
        val previous = Offset(400f, 400f)
        val centroid = Offset(460f, 370f)
        val (newScale, newOffset) = albumArtAnchoredZoom(
            scale = scale,
            offset = offset,
            zoomChange = 1.25f,
            pan = centroid - previous,
            centroid = centroid,
            container = box,
        )
        val center = Offset(box.width / 2f, box.height / 2f)
        val content = (previous - center - offset) / scale
        val projected = content * newScale + newOffset + center
        assertEquals(centroid.x, projected.x, 0.001f)
        assertEquals(centroid.y, projected.y, 0.001f)
    }

    @Test
    fun `snap-back uses the same threshold as the iOS back cover`() {
        assertFalse(albumArtShouldSnapBack(1f))
        assertFalse(albumArtShouldSnapBack(1.01f))
        assertTrue(albumArtShouldSnapBack(1.02f))
        assertTrue(albumArtShouldSnapBack(3f))
    }
}
