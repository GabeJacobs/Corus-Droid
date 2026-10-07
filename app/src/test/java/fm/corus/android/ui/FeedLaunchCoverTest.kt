package fm.corus.android.ui

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ViewRootForTest
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import fm.corus.android.ui.theme.CorusColors
import fm.corus.android.ui.theme.CorusTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import kotlin.math.roundToInt

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class, qualifiers = "w393dp-h852dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class FeedLaunchCoverTest {
    @get:Rule val compose = createComposeRule()

    private fun screenshot(name: String) {
        val directory = System.getProperty("corus.feedLaunchEvidence") ?: return
        val node = compose.onNodeWithTag("launch-preview").fetchSemanticsNode()
        val bounds = node.boundsInRoot
        val bitmap = Bitmap.createBitmap(bounds.width.roundToInt(), bounds.height.roundToInt(), Bitmap.Config.ARGB_8888)
        compose.runOnIdle {
            val canvas = Canvas(bitmap)
            canvas.translate(-bounds.left, -bounds.top)
            (node.root as ViewRootForTest).view.draw(canvas)
        }
        File(directory, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }

    private fun checkCover(dark: Boolean) {
        var visible by mutableStateOf(true)
        var taps = 0
        compose.mainClock.autoAdvance = false
        compose.setContent {
            CorusTheme(darkTheme = dark) {
                Box(Modifier.fillMaxSize().background(CorusColors.Background).testTag("launch-preview")) {
                    Text("Feed", color = CorusColors.Text,
                        modifier = Modifier.align(Alignment.Center).clickable { taps++ })
                    FeedLaunchCover(visible)
                }
            }
        }
        compose.waitForIdle()
        val mode = if (dark) "dark" else "light"
        screenshot("launch-$mode")
        compose.onNodeWithText("Feed").performTouchInput { click() }
        compose.runOnIdle { assertEquals("Opaque cover must own taps", 0, taps); visible = false }
        compose.mainClock.advanceTimeByFrame()
        compose.waitForIdle()
        compose.mainClock.advanceTimeByFrame()
        compose.mainClock.advanceTimeBy(160)
        screenshot("launch-$mode-fade")
        compose.mainClock.advanceTimeBy(400)
        compose.waitForIdle()
        compose.onNodeWithText("Feed").performTouchInput { click() }
        compose.runOnIdle { assertEquals("Feed becomes interactive after fade", 1, taps) }
    }

    @Test fun `light launch cover fades and releases feed taps`() = checkCover(false)
    @Test fun `dark launch cover fades and releases feed taps`() = checkCover(true)
}
