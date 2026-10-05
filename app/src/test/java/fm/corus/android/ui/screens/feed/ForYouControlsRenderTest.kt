package fm.corus.android.ui.screens.feed

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ViewRootForTest
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import fm.corus.android.ui.theme.CorusTheme
import java.io.File
import kotlin.math.roundToInt
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Render the production tab row rather than asserting a duplicate opacity formula. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class, qualifiers = "w393dp-h852dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ForYouControlsRenderTest {
    @get:Rule val compose = createComposeRule()

    @Test fun `rendered controls fade continuously with swipe progress and release their space`() {
        var page by mutableFloatStateOf(0f)
        compose.setContent {
            CorusTheme {
                Box(Modifier.width(393.dp).background(Color.White).testTag("controls-bar")) {
                    FeedModeTabBar(listOf("following", "tasteMatches", "trending", "favorites"),
                        "following", page, {}, prototypeEnabled = true, onTune = {})
                }
            }
        }
        val blueContrast = mutableMapOf<Float, Int>()
        for (position in listOf(0f, .25f, .5f, .75f, 1f, 1.25f, 1.5f, 1.75f, 2f)) {
            compose.runOnIdle { page = position }
            val node = compose.onNodeWithTag("controls-bar").fetchSemanticsNode()
            val bounds = node.boundsInRoot
            val bitmap = Bitmap.createBitmap(bounds.width.roundToInt(), bounds.height.roundToInt(), Bitmap.Config.ARGB_8888)
            // Robolectric has no hardware frame callback for PixelCopy. Draw
            // the real Compose owner through its native Canvas instead.
            compose.runOnIdle {
                val canvas = Canvas(bitmap)
                canvas.translate(-bounds.left, -bounds.top)
                (node.root as ViewRootForTest).view.draw(canvas)
            }
            // Only blue pixels above the underline can come from the controls icon.
            val bottom = (bitmap.height * .75f).toInt()
            var contrast = 0
            for (y in 0 until bottom) for (x in 0 until bitmap.width) {
                val pixel = bitmap.getPixel(x, y)
                val blue = android.graphics.Color.blue(pixel) - android.graphics.Color.red(pixel)
                if (blue > 20) contrast = maxOf(contrast, blue)
            }
            blueContrast[position] = contrast
            System.getProperty("corus.forYouEvidence")?.let { directory ->
                File(directory, "controls-page-$position.png").outputStream().use {
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
                }
            }
        }
        assertEquals(0, blueContrast.getValue(0f))
        assertEquals(0, blueContrast.getValue(2f))
        assertTrue(blueContrast.getValue(1f) > 100)
        assertTrue(blueContrast.getValue(.5f) in 55..85)
        assertTrue(blueContrast.getValue(.75f) > blueContrast.getValue(.5f))
        assertEquals(blueContrast.getValue(.5f), blueContrast.getValue(1.5f))
        assertEquals(blueContrast.getValue(.75f), blueContrast.getValue(1.25f))
    }
}
