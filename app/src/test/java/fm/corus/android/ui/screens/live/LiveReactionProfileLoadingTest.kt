package fm.corus.android.ui.screens.live

import android.app.Application
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import kotlinx.coroutines.awaitCancellation
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class LiveReactionProfileLoadingTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun changingEmojiKeepsKnownPeopleVisibleWhileMissingPeopleLoad() {
        val requests = mutableListOf<List<String>>()
        compose.setContent {
            LiveChatReactionDetails(
                groups = linkedMapOf("❤️" to listOf("a"), "🔥" to listOf("a", "b")),
                initialEmoji = "❤️",
                loadPeople = { ids ->
                    requests += ids
                    if (ids == listOf("a")) mapOf("a" to LivePerson("a", "alice", null))
                    else awaitCancellation()
                },
                dismiss = {},
            )
        }
        compose.onNodeWithText("@alice").assertIsDisplayed()
        compose.onNodeWithText("🔥").performClick()
        compose.onNodeWithText("@alice").assertIsDisplayed()
        compose.runOnIdle { assertEquals(listOf(listOf("a"), listOf("b")), requests) }
        compose.onNodeWithText("❤️").performClick()
        compose.onNodeWithText("@alice").assertIsDisplayed()
        compose.runOnIdle { assertEquals(2, requests.size) }
    }

    @Test
    fun cachedPeopleAppearImmediatelyAndOnlyMissingIdsAreRequested() {
        val requests = mutableListOf<List<String>>()
        compose.setContent {
            LiveChatReactionDetails(
                groups = mapOf("❤️" to listOf("a", "b")), initialEmoji = "❤️",
                knownPeople = mapOf("a" to LivePerson("a", "alice", null)),
                loadPeople = { ids -> requests += ids; awaitCancellation() },
                dismiss = {},
            )
        }
        compose.onNodeWithText("@alice").assertIsDisplayed()
        compose.runOnIdle { assertEquals(listOf(listOf("b")), requests) }
    }

    @Test
    fun fullyCachedGroupDoesNotLoadPeople() {
        val requests = mutableListOf<List<String>>()
        compose.setContent {
            LiveChatReactionDetails(
                groups = mapOf("❤️" to listOf("a")), initialEmoji = "❤️",
                knownPeople = mapOf("a" to LivePerson("a", "alice", null)),
                loadPeople = { ids -> requests += ids; emptyMap() },
                dismiss = {},
            )
        }
        compose.onNodeWithText("@alice").assertIsDisplayed()
        compose.runOnIdle { assertEquals(emptyList<List<String>>(), requests) }
    }

    @Test
    fun failedLookupKeepsCachedPeopleVisible() {
        compose.setContent {
            LiveChatReactionDetails(
                groups = mapOf("❤️" to listOf("a", "b")), initialEmoji = "❤️",
                knownPeople = mapOf("a" to LivePerson("a", "alice", null)),
                loadPeople = { throw IllegalStateException("offline") }, dismiss = {},
            )
        }
        compose.onNodeWithText("@alice").assertIsDisplayed()
    }
}
