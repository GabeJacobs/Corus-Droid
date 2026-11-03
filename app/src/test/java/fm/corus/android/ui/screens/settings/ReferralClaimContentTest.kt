package fm.corus.android.ui.screens.settings

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import fm.corus.android.service.ReferralClaimOutcome
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.text.DateFormat
import java.util.Date

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class ReferralClaimContentTest {
    @get:Rule val compose = createComposeRule()

    @Test fun `claim stays the same size and success waits for Done with the server expiry`() {
        val outcome = mutableStateOf<ReferralClaimOutcome?>(null)
        val expiry = 1794916800000L
        var dismissed = false
        compose.setContent {
            Box(Modifier.size(360.dp, 620.dp)) {
                ReferralClaimContent(outcome.value, expiry, { dismissed = true }, Modifier.fillMaxSize().testTag("claim")) {
                    Text("Ready to claim")
                }
            }
        }
        val before = compose.onNodeWithTag("claim").getUnclippedBoundsInRoot()
        compose.runOnIdle { outcome.value = ReferralClaimOutcome.Applied }
        compose.mainClock.advanceTimeBy(500)
        assertEquals(before, compose.onNodeWithTag("claim").getUnclippedBoundsInRoot())
        compose.onNodeWithText("Ready to claim").assertDoesNotExist()
        compose.onNodeWithText("Your 3 months are active").assertExists()
        val date = DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(expiry))
        compose.onNodeWithText("Enjoy Corus Club until $date.").assertExists()
        compose.mainClock.advanceTimeBy(10000)
        assertEquals(false, dismissed)
        compose.onNodeWithText("Done").performClick()
        assertEquals(true, dismissed)
    }

    @Test fun `claim spinner disables duplicate taps without resizing the button`() {
        val busy = mutableStateOf(false)
        var taps = 0
        compose.setContent {
            Box(Modifier.size(320.dp, 80.dp).testTag("host")) {
                ReferralActionButton("Claim 3 months", busy = busy.value) { taps++ }
            }
        }
        val button = compose.onNode(hasClickAction())
        val before = button.getUnclippedBoundsInRoot()
        compose.runOnIdle { busy.value = true }
        button.assertIsNotEnabled()
        assertEquals(before, button.getUnclippedBoundsInRoot())
        compose.onNode(SemanticsMatcher.keyIsDefined(androidx.compose.ui.semantics.SemanticsProperties.ProgressBarRangeInfo)).assertExists()
        assertEquals(0, taps)
    }

    @Test fun `saved reward stays open with Done and does not promise active access`() {
        compose.setContent {
            Box(Modifier.size(360.dp, 620.dp)) {
                ReferralClaimContent(ReferralClaimOutcome.Saved, null, {}) { Text("Claim") }
            }
        }
        compose.onNodeWithText("Your 3 months are active").assertDoesNotExist()
        compose.onNodeWithText("Done").assertExists()
    }
}
