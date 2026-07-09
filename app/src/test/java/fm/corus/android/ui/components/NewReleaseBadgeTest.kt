package fm.corus.android.ui.components

import android.app.Application
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Regression tests for the standalone [NewReleaseBadge] pill and its inline use
 * in [UsernameWithFlair]. The pill is the purple "NEW RELEASE" tag shown on the
 * song/film detail headers (web parity) and next to a poster's username on the
 * feed. Guards two things:
 *
 *  1. The badge renders its "NEW RELEASE" label (string resource wiring).
 *  2. [UsernameWithFlair] shows the pill iff `isNewRelease` is true — the flag
 *     the detail-screen refactor now routes through this shared component.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class NewReleaseBadgeTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `NewReleaseBadge renders the NEW RELEASE label`() {
        composeRule.setContent { NewReleaseBadge() }

        composeRule.onNodeWithText("NEW RELEASE").assertIsDisplayed()
    }

    @Test
    fun `UsernameWithFlair shows the pill when isNewRelease is true`() {
        composeRule.setContent {
            UsernameWithFlair(username = "rae", isNewRelease = true)
        }

        composeRule.onNodeWithText("NEW RELEASE").assertExists()
    }

    @Test
    fun `UsernameWithFlair hides the pill when isNewRelease is false`() {
        composeRule.setContent {
            UsernameWithFlair(username = "rae", isNewRelease = false)
        }

        composeRule.onNodeWithText("NEW RELEASE").assertDoesNotExist()
    }
}
