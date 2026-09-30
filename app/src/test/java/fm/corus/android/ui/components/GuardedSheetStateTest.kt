package fm.corus.android.ui.components

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SheetState
import androidx.compose.material3.SheetValue
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import androidx.compose.ui.test.junit4.createComposeRule

@OptIn(ExperimentalMaterial3Api::class)
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35])
class GuardedSheetStateTest {
    @get:Rule val compose = createComposeRule()

    @Test fun shortDragIsRejectedAndDeliberateDragCanDismiss() {
        lateinit var state: SheetState
        compose.setContent {
            state = rememberGuardedSheetState()
            CorusModalBottomSheet(onDismissRequest = {}, sheetState = state, modifier = Modifier.testTag("sheet")) {
                Box(Modifier.height(600.dp))
            }
        }
        compose.waitForIdle()
        // Let the resting offset observer capture the expanded position.
        compose.mainClock.advanceTimeBy(600)
        compose.waitForIdle()
        compose.runOnIdle {
            assertEquals(SheetValue.Expanded, state.currentValue)
        }
        compose.onNodeWithTag("sheet").performTouchInput {
            swipe(Offset(center.x, 20f), Offset(center.x, 55f), durationMillis = 80)
        }
        compose.waitForIdle()
        compose.runOnIdle { assertEquals(SheetValue.Expanded, state.currentValue) }
        compose.onNodeWithTag("sheet").performTouchInput {
            swipe(Offset(center.x, 20f), Offset(center.x, height.toFloat() - 20f), durationMillis = 300)
        }
        compose.waitForIdle()
        compose.runOnIdle { assertEquals(SheetValue.Hidden, state.currentValue) }
    }
}
