package fm.corus.android.ui.screens.live

import android.app.Application
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.click
import androidx.compose.ui.test.doubleClick
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.text.TextLayoutResult
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@GraphicsMode(GraphicsMode.Mode.NATIVE)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class LiveChatMentionTapTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun blueMentionsOpenTheirProfilesAndKeepMessageGestures() {
        val body = "Hello @gabe and @isa"
        val message = LiveMessage("message", mapOf(
            "uid" to "author", "displayName" to "Author", "text" to body,
            // GIF messages also have body text. Avoid the device-only ICU emoji
            // regex so this test exercises taps on the host JVM too.
            "gifURL" to "https://example.com/chat.gif",
        ))
        val vm = mock<LiveViewModel> {
            onBlocking { chatMentionUserId("gabe") } doReturn "gabe-id"
            onBlocking { chatMentionUserId("isa") } doReturn "isa-id"
        }
        val opened = mutableListOf<String>()
        val options = mutableListOf<String>()
        compose.setContent {
            RoomChat(
                listOf(message), LiveUiState(uid = "listener", room = LiveRoom("room", emptyMap())),
                vm, 0, opened::add, { row, _ -> options.add(row.id) }, Modifier,
            )
        }
        val text = compose.onNodeWithText(body, useUnmergedTree = true).assertIsDisplayed()
        val layouts = mutableListOf<TextLayoutResult>()
        text.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        val layout = layouts.single()
        fun position(token: String) = layout.getBoundingBox(body.indexOf(token) + 1).center

        text.performTouchInput { click(position("Hello")) }
        compose.mainClock.advanceTimeBy(400) // Single taps wait for the double-tap window.
        compose.runOnIdle { assertEquals(emptyList<String>(), opened) }
        text.performTouchInput { click(position("@gabe")) }
        compose.mainClock.advanceTimeBy(400)
        compose.runOnIdle { assertEquals(listOf("gabe-id"), opened) }
        text.performTouchInput { click(position("@isa")) }
        compose.mainClock.advanceTimeBy(400)
        compose.runOnIdle { assertEquals(listOf("gabe-id", "isa-id"), opened) }

        text.performTouchInput { doubleClick(position("Hello")) }
        verify(vm).setChatReaction(message.id, "❤️", addOnly = true)
        text.performTouchInput { longClick(position("Hello")) }
        compose.runOnIdle {
            assertEquals(listOf(message.id), options)
            assertEquals(listOf("gabe-id", "isa-id"), opened)
        }
        text.performTouchInput { longClick(position("@gabe")) }
        compose.runOnIdle {
            assertEquals(listOf(message.id, message.id), options)
            assertEquals("Long press on a mention must not also open its profile", listOf("gabe-id", "isa-id"), opened)
        }
        text.performTouchInput { doubleClick(position("@gabe")) }
        verify(vm, times(2)).setChatReaction(message.id, "❤️", addOnly = true)
        compose.runOnIdle {
            assertEquals("Double tap on a mention must like without opening a profile", listOf("gabe-id", "isa-id"), opened)
        }
    }
}
