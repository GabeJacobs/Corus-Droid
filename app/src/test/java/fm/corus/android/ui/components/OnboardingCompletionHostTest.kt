package fm.corus.android.ui.components

import android.app.Application
import androidx.compose.material3.Text
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class OnboardingCompletionHostTest {
    @get:Rule val compose = createComposeRule()

    @Test fun `checking referral HUD requires both finishing and a pending invitation`() {
        val completing = mutableStateOf(false)
        val pending = mutableStateOf(false)
        compose.setContent {
            OnboardingCompletionHost(completing.value, pending.value) { Text("Notification screen") }
        }
        fun indicators() = compose.onAllNodes(
            SemanticsMatcher.keyIsDefined(SemanticsProperties.ProgressBarRangeInfo),
        )
        indicators().assertCountEquals(0)
        compose.runOnIdle { completing.value = true }
        indicators().assertCountEquals(0)
        compose.runOnIdle { pending.value = true }
        indicators().assertCountEquals(1)
        compose.onNodeWithText("Checking Referral…").assertExists()
        compose.onNodeWithText("Notification screen").assertExists()
        compose.runOnIdle { completing.value = false }
        indicators().assertCountEquals(0)
        compose.runOnIdle { completing.value = true; pending.value = false }
        indicators().assertCountEquals(0)
    }
}
