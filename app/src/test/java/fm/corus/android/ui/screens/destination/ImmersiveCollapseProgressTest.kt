package fm.corus.android.ui.screens.destination

import fm.corus.android.ui.components.immersiveCollapseProgress
import org.junit.Assert.assertEquals
import org.junit.Test

/** Unit tests for the immersive artist-header collapse math. */
class ImmersiveCollapseProgressTest {

    private val distance = 244f // ~(300dp hero - 56dp bar) in px for these cases

    @Test
    fun `at rest the bar is fully expanded`() {
        assertEquals(
            0f,
            immersiveCollapseProgress(
                firstVisibleItemIndex = 0,
                firstVisibleItemScrollOffset = 0,
                collapseDistancePx = distance,
            ),
            0f,
        )
    }

    @Test
    fun `halfway through the collapse distance is half collapsed`() {
        assertEquals(
            0.5f,
            immersiveCollapseProgress(
                firstVisibleItemIndex = 0,
                firstVisibleItemScrollOffset = 122,
                collapseDistancePx = distance,
            ),
            0.001f,
        )
    }

    @Test
    fun `scrolling past the collapse distance clamps to fully collapsed`() {
        assertEquals(
            1f,
            immersiveCollapseProgress(
                firstVisibleItemIndex = 0,
                firstVisibleItemScrollOffset = 5_000,
                collapseDistancePx = distance,
            ),
            0f,
        )
    }

    @Test
    fun `once the hero item scrolls off, it is fully collapsed regardless of offset`() {
        assertEquals(
            1f,
            immersiveCollapseProgress(
                firstVisibleItemIndex = 3,
                firstVisibleItemScrollOffset = 0,
                collapseDistancePx = distance,
            ),
            0f,
        )
    }

    @Test
    fun `a zero collapse distance is treated as fully collapsed, never NaN`() {
        val result = immersiveCollapseProgress(
            firstVisibleItemIndex = 0,
            firstVisibleItemScrollOffset = 0,
            collapseDistancePx = 0f,
        )
        assertEquals(1f, result, 0f)
        assertEquals(false, result.isNaN())
    }
}
