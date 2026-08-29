package fm.corus.android.ui.components

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The chrome HUD is armed on the tap frame, but painting is deferred so a
 * fast trending-album / artist resolve never flashes a spinner.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class ChromeLoadingHudTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `fast resolve never paints the HUD`() {
        var visible by mutableStateOf(true)
        composeRule.setContent { ChromeLoadingHud(visible = visible) }
        composeRule.mainClock.autoAdvance = false

        composeRule.mainClock.advanceTimeBy(CHROME_HUD_SHOW_DELAY_MS - 50)
        composeRule.onNodeWithContentDescription("Loading…").assertDoesNotExist()

        visible = false
        composeRule.mainClock.autoAdvance = true
        composeRule.waitForIdle()
        composeRule.onNodeWithContentDescription("Loading…").assertDoesNotExist()
    }

    @Test
    fun `slow resolve paints the HUD after the show delay`() {
        composeRule.setContent { ChromeLoadingHud(visible = true) }
        composeRule.mainClock.autoAdvance = false

        composeRule.mainClock.advanceTimeBy(CHROME_HUD_SHOW_DELAY_MS - 50)
        composeRule.onNodeWithContentDescription("Loading…").assertDoesNotExist()

        composeRule.mainClock.advanceTimeBy(100)
        composeRule.mainClock.autoAdvance = true
        composeRule.waitForIdle()
        composeRule.onNodeWithContentDescription("Loading…").assertIsDisplayed()
    }
}
