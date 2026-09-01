package fm.corus.android.ui.screens.messaging

import android.app.Application
import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ApplicationProvider
import fm.corus.android.R
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class ThreadOpenerTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val context: Context
        get() = ApplicationProvider.getApplicationContext()

    @Test
    fun `shows name handle overlap and view profile`() {
        composeRule.setContent {
            ThreadOpener(
                username = "devynbrowne",
                displayName = "Devyn",
                avatarURL = null,
                avatarThumbURL = null,
                artistsInCommon = 4,
                onViewProfile = {},
            )
        }

        composeRule.onNodeWithText("Devyn").assertIsDisplayed()
        composeRule.onNodeWithText("@devynbrowne").assertIsDisplayed()
        composeRule.onNodeWithText(
            context.getString(R.string.notif_taste_match_body_artists, 4),
        ).assertIsDisplayed()
        composeRule.onNodeWithText(
            context.getString(R.string.messaging_thread_view_profile),
        ).assertIsDisplayed()
    }

    @Test
    fun `omits the overlap line when there are no shared artists`() {
        composeRule.setContent {
            ThreadOpener(
                username = "devynbrowne",
                displayName = "Devyn",
                avatarURL = null,
                avatarThumbURL = null,
                artistsInCommon = null,
                onViewProfile = {},
            )
        }

        composeRule.onNodeWithText(
            context.getString(R.string.notif_taste_match_body_artists, 4),
            substring = false,
        ).assertDoesNotExist()
        composeRule.onNodeWithText("Devyn").assertIsDisplayed()
        composeRule.onNodeWithText(
            context.getString(R.string.messaging_thread_view_profile),
        ).assertIsDisplayed()
    }
}
