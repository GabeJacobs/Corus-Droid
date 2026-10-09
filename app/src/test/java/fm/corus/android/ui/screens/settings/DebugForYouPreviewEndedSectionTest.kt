package fm.corus.android.ui.screens.settings

import android.app.Application
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import fm.corus.android.ui.theme.CorusTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class, qualifiers = "w393dp-h852dp")
class DebugForYouPreviewEndedSectionTest {
    @get:Rule val compose = createComposeRule()

    @Test fun `both variants reopen without Firebase and show locally captured events`() {
        compose.setContent { CorusTheme { DebugForYouPreviewEndedSection() } }
        repeat(2) {
            compose.onNodeWithText("Preview: Free Trial CTA").performClick()
            compose.onNodeWithText("Try Corus Club for Free").assertIsDisplayed()
            compose.onNodeWithTag("for_you_preview_ended_continue").performClick()
            compose.waitUntil { compose.onAllNodesWithText("Your Balanced and Stay Close preview has ended.").fetchSemanticsNodes().isEmpty() }
            compose.onNodeWithText("your_mix_preview_ended_dismissed", substring = true).assertExists()
            compose.onNodeWithText("reason=continue", substring = true).assertExists()
            compose.onNodeWithText("Preview: Join Club CTA").performClick()
            compose.onNodeWithText("Join Corus Club").assertIsDisplayed()
            compose.onNodeWithTag("for_you_preview_ended_club").performClick()
            compose.waitUntil { compose.onAllNodesWithText("Your Balanced and Stay Close preview has ended.").fetchSemanticsNodes().isEmpty() }
            compose.onNodeWithText("your_mix_preview_ended_club_tapped", substring = true).assertExists()
            compose.onNodeWithText("your_mix_preview_ended_dismissed", substring = true).assertDoesNotExist()
        }
    }
}
