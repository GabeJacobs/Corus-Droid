package fm.corus.android.ui.components

import android.app.Application
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.test.click
import androidx.compose.ui.unit.dp
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35])
class SheetKeyboardGestureTest {
    @get:Rule val compose = createComposeRule()
    @Test fun firstDownwardDragHidesKeyboardAndNextDragMovesSheet() {
        var keyboard = true
        var hidden = 0
        var moved = 0f
        compose.setContent {
            Box(Modifier.size(240.dp).testTag("handle")
                .keyboardFirstDownwardDrag({ keyboard }) { keyboard = false; hidden++ }
                .draggable(rememberDraggableState { moved += it }, Orientation.Vertical))
        }
        compose.onNodeWithTag("handle").performTouchInput { swipeDown() }
        compose.runOnIdle { assertEquals(1, hidden); assertEquals(0f, moved, 0.1f) }
        compose.onNodeWithTag("handle").performTouchInput { swipeDown() }
        compose.runOnIdle { assertEquals(1, hidden); assertTrue(moved > 0f) }
    }
    @Test fun tapsAndUpwardDragsKeepNormalBehavior() {
        var hidden = 0
        var moved = 0f
        compose.setContent {
            Box(Modifier.size(240.dp).testTag("header")
                .keyboardFirstDownwardDrag({ true }) { hidden++ }
                .draggable(rememberDraggableState { moved += it }, Orientation.Vertical))
        }
        compose.onNodeWithTag("header").performTouchInput { click() }
        compose.onNodeWithTag("header").performTouchInput { swipeUp() }
        compose.runOnIdle { assertEquals(0, hidden); assertTrue(moved < 0f) }
    }
}
