package fm.corus.android.ui.screens.messaging

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Frame sizing for DM photos/GIFs. Mirrors iOS `AnimatedGifView.aspectFittedSize`
 * so Android no longer letterboxes landscape media inside a fixed max box.
 */
class MessageMediaSizingTest {

    private val maxWidth = 240.dp
    private val maxHeight = 300.dp

    @Test
    fun `landscape media keeps full width and shrinks height`() {
        val size = aspectFittedDp(480f, 270f, maxWidth, maxHeight)!!
        assertEquals(240.dp, size.width)
        assertEquals(135.dp, size.height)
    }

    @Test
    fun `square media stays within the width cap`() {
        val size = aspectFittedDp(300f, 300f, maxWidth, maxHeight)!!
        assertEquals(240.dp, size.width)
        assertEquals(240.dp, size.height)
    }

    @Test
    fun `tall media caps height and shrinks width`() {
        val size = aspectFittedDp(270f, 480f, maxWidth, maxHeight)!!
        assertEquals(300.dp, size.height)
        // 300 * (270/480) = 168.75
        assertEquals(168.75.dp, size.width)
    }

    @Test
    fun `degenerate dimensions fall back to the placeholder path`() {
        assertNull(aspectFittedDp(0f, 100f, maxWidth, maxHeight))
        assertNull(aspectFittedDp(100f, 0f, maxWidth, maxHeight))
        assertNull(aspectFittedDp(-1f, 100f, maxWidth, maxHeight))
    }
}
