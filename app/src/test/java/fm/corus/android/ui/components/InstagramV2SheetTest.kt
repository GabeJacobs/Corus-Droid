package fm.corus.android.ui.components

import android.app.Application
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import fm.corus.android.data.model.CymbalUser
import fm.corus.android.data.model.ShareRecipient
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class InstagramV2SheetTest {
    @get:Rule val compose = createComposeRule()
    @Test fun `search stays inline and recipient selection never sends until Send`() {
        var sent: String? = null
        var dismissed = false
        val recipient = ShareRecipient(user = CymbalUser("recipient-id", "listener", "Listener"))
        compose.setContent {
            MaterialTheme {
                PostToInstagramV2Sheet(
                    subject = InstagramV2Subject("Test song", "Artist", null, "https://corus.fm/song/test", "https://corus.fm/song/test"),
                    recentContacts = listOf(recipient), searchResults = listOf(recipient), isSearching = false,
                    isLoadingContacts = false, instagramShareEnabled = false,
                    onSearchQueryChange = {}, onSendToUser = { id, _ -> sent = id }, onDismiss = { dismissed = true },
                )
            }
        }
        compose.onNodeWithText("Cover").assertIsDisplayed()
        compose.onNodeWithText("Search").performClick()
        compose.onNodeWithText("Done").assertIsDisplayed()
        compose.onNodeWithText("listener").performClick()
        compose.runOnIdle { assertNull(sent); assertFalse(dismissed) }
        compose.onNodeWithText("Done").performClick()
        compose.onNodeWithText("Cover").assertIsDisplayed()
        compose.onNodeWithText("Send").assertIsDisplayed().performClick()
        compose.runOnIdle { assertEquals("recipient-id", sent); assertTrue(dismissed) }
    }
}
