package fm.corus.android.ui.screens.messaging

import android.app.Application
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
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
}
