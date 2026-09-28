package fm.corus.android.ui.screens.messaging

import android.app.Application
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.doubleClick
import androidx.compose.ui.test.longClick
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class MessageMediaSizingTest {
    @get:Rule val composeRule = createComposeRule()

    @Test
    fun `media reserves the compact iOS square before loading`() {
        composeRule.setContent {
            MessageMediaImage(
                url = "file:///nonexistent-corus-preview.jpg",
                contentDescription = "Photo preview",
                modifier = Modifier.testTag("preview"),
            )
        }
        composeRule.onNodeWithTag("preview")
            .assertWidthIsEqualTo(180.dp)
            .assertHeightIsEqualTo(180.dp)
    }

    @Test
    fun `single tapping image opens viewer`() {
        var singleTaps = 0
        composeRule.setContent {
            MessageMediaImage(
                url = "file:///nonexistent-corus-preview.jpg",
                contentDescription = "Photo preview",
                modifier = Modifier.testTag("preview"),
                onClick = { singleTaps++ },
            )
        }

        composeRule.onNodeWithTag("preview").assertHasClickAction().performClick()

        assertEquals(1, singleTaps)
    }

    @Test
    fun `double tapping image reacts without opening viewer`() {
        var singleTaps = 0
        var doubleTaps = 0
        composeRule.setContent {
            MessageMediaImage(
                url = "file:///nonexistent-corus-preview.jpg",
                contentDescription = "Photo preview",
                modifier = Modifier.testTag("preview"),
                onClick = { singleTaps++ },
                onDoubleClick = { doubleTaps++ },
            )
        }

        composeRule.onNodeWithTag("preview").performTouchInput { doubleClick() }

        assertEquals(0, singleTaps)
        assertEquals(1, doubleTaps)
    }

    @Test
    fun `long pressing image opens reaction picker without opening viewer`() {
        var singleTaps = 0
        var longPresses = 0
        composeRule.setContent {
            MessageMediaImage(
                url = "file:///nonexistent-corus-preview.jpg",
                contentDescription = "Photo preview",
                modifier = Modifier.testTag("preview"),
                onClick = { singleTaps++ },
                onLongClick = { longPresses++ },
            )
        }

        composeRule.onNodeWithTag("preview").performTouchInput { longClick() }

        assertEquals(0, singleTaps)
        assertEquals(1, longPresses)
    }
}
