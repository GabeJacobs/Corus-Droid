package fm.corus.android.ui.components

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.ViewRootForTest
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.junit4.createComposeRule
import fm.corus.android.ui.theme.CorusColors
import fm.corus.android.ui.theme.CorusTheme
import kotlin.math.roundToInt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class, qualifiers = "w360dp-h640dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ProfileCollageProgressTest {
    @get:Rule val compose = createComposeRule()

    @Test fun `zero posts paints no filled pixels including the right endpoint`() {
        val (pixels, accent) = renderBar(0)
        assertEquals(0, pixels.count { it == accent })
    }

    @Test fun `three posts fills the left third without a right endpoint marker`() {
        val (pixels, accent) = renderBar(3)
        assertTrue(pixels[pixels.size / 6] == accent)
        assertTrue(pixels[pixels.size / 2] != accent)
        assertEquals(0, pixels.drop(pixels.size / 2).count { it == accent })
    }

    private fun renderBar(count: Int): Pair<List<Int>, Int> {
        var accent = 0
        compose.setContent {
            CorusTheme(darkTheme = false) {
                accent = CorusColors.Accent.toArgb()
                ProfileCollageTeaser(ShareProfileSubject("fixture", "fixture", "Fixture", null,
                    postCount = count), null)
            }
        }
        val node = compose.onNode(SemanticsMatcher.keyIsDefined(SemanticsProperties.ProgressBarRangeInfo))
            .fetchSemanticsNode()
        assertEquals(count / 9f, node.config[SemanticsProperties.ProgressBarRangeInfo].current)
        val bounds = node.boundsInRoot
        val bitmap = Bitmap.createBitmap(bounds.width.roundToInt(), bounds.height.roundToInt(), Bitmap.Config.ARGB_8888)
        compose.runOnIdle {
            val canvas = Canvas(bitmap)
            canvas.translate(-bounds.left, -bounds.top)
            (node.root as ViewRootForTest).view.draw(canvas)
        }
        val pixels = (0 until bitmap.width).map { bitmap.getPixel(it, bitmap.height / 2) }
        bitmap.recycle()
        return pixels to accent
    }
}
