package fm.corus.android.ui.theme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CorusShimmerThemeTest {
    @Test
    fun `dark mode keeps a visible floor so bones do not composite to black`() {
        val theme = corusShimmerTheme(darkTheme = true)
        val alphas = theme.shaderColors.map { it.alpha }
        assertEquals(3, alphas.size)
        assertEquals(DARK_SHIMMER_LOW_ALPHA, alphas[0], 0.01f)
        assertEquals(1.00f, alphas[1], 0.01f)
        assertEquals(DARK_SHIMMER_LOW_ALPHA, alphas[2], 0.01f)
        assertTrue(DARK_SHIMMER_LOW_ALPHA > 0.5f)
    }

    @Test
    fun `light mode keeps the library floor — light bones stay visible on white`() {
        val theme = corusShimmerTheme(darkTheme = false)
        val alphas = theme.shaderColors.map { it.alpha }
        assertEquals(3, alphas.size)
        assertEquals(LIGHT_SHIMMER_LOW_ALPHA, alphas[0], 0.01f)
        assertEquals(1.00f, alphas[1], 0.01f)
        assertEquals(LIGHT_SHIMMER_LOW_ALPHA, alphas[2], 0.01f)
    }
}
