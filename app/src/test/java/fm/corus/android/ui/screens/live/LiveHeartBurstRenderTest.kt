package fm.corus.android.ui.screens.live

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ViewRootForTest
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class, qualifiers = "w393dp-h852dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class LiveHeartBurstRenderTest {
    @get:Rule val compose = createComposeRule()

    @Test fun incomingHeartPaintsInHostControls() = assertHeartPaints(16, 24)

    @Test fun incomingHeartPaintsInChat() = assertHeartPaints(36, 72)

    private fun assertHeartPaints(start: Int, bottom: Int) {
        val reactions = mutableLongStateOf(0)
        compose.mainClock.autoAdvance = false
        compose.setContent {
            Box(Modifier.fillMaxSize().background(Color.Black).testTag("room")) {
                LiveHeartBurst(reactions.longValue,
                    Modifier.align(Alignment.BottomStart).padding(start = start.dp, bottom = bottom.dp))
            }
        }
        compose.waitForIdle()
        compose.runOnIdle { reactions.longValue = 1 }
        compose.waitForIdle()
        compose.mainClock.autoAdvance = false
        compose.mainClock.advanceTimeByFrame()
        compose.waitForIdle()
        compose.mainClock.advanceTimeBy(400)
        compose.waitForIdle()
        val node = compose.onNodeWithTag("room").fetchSemanticsNode()
        val view = (node.root as ViewRootForTest).view
        val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        compose.runOnIdle { view.draw(Canvas(bitmap)) }
        var colored = 0
        for (y in 0 until bitmap.height) for (x in 0 until bitmap.width) {
            val pixel = bitmap.getPixel(x, y)
            if (android.graphics.Color.red(pixel) > 80 && android.graphics.Color.red(pixel) > android.graphics.Color.green(pixel) * 1.3) colored++
        }
        assertTrue("Expected visible hearts, found $colored colored pixels", colored > 30)
    }
}
