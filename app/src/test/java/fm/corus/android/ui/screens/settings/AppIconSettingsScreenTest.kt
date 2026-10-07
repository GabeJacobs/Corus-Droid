package fm.corus.android.ui.screens.settings

import android.app.Application
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import fm.corus.android.service.AppIcon
import fm.corus.android.ui.theme.CorusTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class AppIconSettingsScreenTest {
    @get:Rule val compose = createComposeRule()

    @Test fun `nonmembers see locked choices and settings upsell`() {
        var unlocks = 0
        compose.setContent {
            CorusTheme { AppIconSettingsContent(AppIcon.DEFAULT, false, false, {}, { unlocks++ }, { error("Locked icon changed") }) }
        }
        compose.onNodeWithText("Default").assertIsSelected().assertIsNotEnabled()
        compose.onNodeWithText("Corus Blue").assertIsNotSelected().assertIsNotEnabled()
        compose.onNodeWithText("App icons are available to Corus Club members.").assertIsDisplayed()
        compose.onNodeWithText("Unlock with Corus Club").performClick()
        assertEquals(1, unlocks)
    }

    @Test fun `members can select blue and see current selection`() {
        var choice: AppIcon? = null
        compose.setContent {
            CorusTheme { AppIconSettingsContent(AppIcon.CORUS_BLUE, true, false, {}, {}, { choice = it }) }
        }
        compose.onNodeWithText("Corus Blue").assertIsSelected().assertIsEnabled()
        compose.onNodeWithText("Default").assertIsNotSelected().performClick()
        assertEquals(AppIcon.DEFAULT, choice)
        compose.onNodeWithText("Unlock with Corus Club").assertDoesNotExist()
    }

    @Test fun `switching prevents overlapping choices`() {
        compose.setContent {
            CorusTheme { AppIconSettingsContent(AppIcon.DEFAULT, true, true, {}, {}, { error("Overlapping change") }) }
        }
        compose.onNodeWithText("Default").assertIsNotEnabled()
        compose.onNodeWithText("Corus Blue").assertIsNotEnabled()
    }
}
