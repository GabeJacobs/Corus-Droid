package fm.corus.android.ui.screens.settings

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class ReferralNavigationTest {
    @get:Rule val compose = createComposeRule()

    @Test fun `Back slides history right while restoring the invite page from the left`() {
        val history = mutableStateOf(true)
        compose.setContent {
            Box(Modifier.size(360.dp, 600.dp)) {
                ReferralHistoryNavigation(history.value) { showingHistory ->
                    Box(Modifier.size(360.dp, 600.dp)) { Text(if (showingHistory) "History" else "Invite") }
                }
            }
        }
        compose.onNodeWithText("History").assertExists()
        compose.mainClock.autoAdvance = false
        compose.runOnIdle { history.value = false }
        compose.mainClock.advanceTimeByFrame()
        compose.waitForIdle()
        compose.mainClock.advanceTimeBy(100)
        compose.waitForIdle()
        val outgoing = compose.onNodeWithText("History").getUnclippedBoundsInRoot()
        val incoming = compose.onNodeWithText("Invite").getUnclippedBoundsInRoot()
        assertTrue(outgoing.left > 0.dp)
        assertTrue(incoming.left < 0.dp)
        compose.mainClock.autoAdvance = true
        compose.waitForIdle()
        compose.onNodeWithText("History").assertDoesNotExist()
        compose.onNodeWithText("Invite").assertExists()
    }
}
