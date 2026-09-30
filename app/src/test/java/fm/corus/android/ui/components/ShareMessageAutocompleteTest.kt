package fm.corus.android.ui.components

import android.app.Application
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import fm.corus.android.data.model.CymbalUser
import fm.corus.android.data.model.HashtagSuggestion
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35])
class ShareMessageAutocompleteTest {
    @get:Rule val compose = createComposeRule()

    @Test fun mentionCompletionOverlaysEditorAndPreservesFollowingText() {
        var value by mutableStateOf(TextFieldValue("Hello @ga later", TextRange(9)))
        compose.setContent {
            MaterialTheme {
                Column {
                    Spacer(Modifier.height(240.dp))
                    ShareMessageAutocomplete(value, { value = it }, true,
                        searchUsers = { listOf(CymbalUser("gabe", "gabe", "Gabe")) }) {
                        BasicTextField(value, { value = it }, Modifier.fillMaxWidth().testTag("editor"))
                    }
                }
            }
        }
        compose.waitUntil(5000) { compose.onAllNodesWithText("gabe").fetchSemanticsNodes().isNotEmpty() }
        val editor = compose.onNodeWithTag("editor").fetchSemanticsNode().boundsInRoot
        val suggestion = compose.onNodeWithText("gabe").fetchSemanticsNode().boundsInRoot
        assertTrue(suggestion.bottom <= editor.top)
        compose.onNodeWithText("gabe").performClick()
        compose.runOnIdle { assertEquals("Hello @gabe later", value.text); assertEquals(12, value.selection.start) }
        compose.onNodeWithText("gabe").assertDoesNotExist()
    }

    @Test fun hashtagCompletionUsesTrendingForBareHashAndStopsAfterSelection() {
        var value by mutableStateOf(TextFieldValue("#", TextRange(1)))
        var query: String? = null
        compose.setContent {
            MaterialTheme {
                Column {
                    Spacer(Modifier.height(240.dp))
                    ShareMessageAutocomplete(value, { value = it }, true, searchHashtags = {
                        query = it
                        listOf(HashtagSuggestion("music", 3, true))
                    }) {
                        BasicTextField(value, { value = it }, Modifier.fillMaxWidth())
                    }
                }
            }
        }
        compose.waitUntil(5000) { compose.onAllNodesWithText("#music").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("#music").performClick()
        compose.runOnIdle { assertEquals("", query); assertEquals("#music ", value.text) }
        compose.onNodeWithText("#music").assertDoesNotExist()
    }
}
