package fm.corus.android.ui.screens.live

import android.app.Application
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.click
import androidx.compose.ui.test.doubleClick
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToKey
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.test.performTouchInput
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoMoreInteractions
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class LiveChatReactionsTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun messageGesturesAndReactionChipsAreIndependent() {
        val message = LiveMessage("chat-message", mapOf(
            "uid" to "host", "displayName" to "Isa", "text" to "Play this one!",
            "chatReactions" to mapOf("🔥" to listOf("host", "listener"), "🎵" to listOf("host")),
        ))
        val state = LiveUiState(uid = "listener", room = LiveRoom("room", emptyMap()))
        val vm = mock<LiveViewModel>()
        val opened = mutableListOf<Pair<String, Boolean>>()
        compose.setContent {
            RoomChat(listOf(message), state, vm, 0, {}, { row, picker -> opened.add(row.id to picker) }, Modifier)
        }

        val body = compose.onNodeWithText("Play this one!").assertIsDisplayed()
        body.performTouchInput { doubleClick() }
        verify(vm).setChatReaction(message.id, "❤️", addOnly = true)
        assertEquals(emptyList<Pair<String, Boolean>>(), opened)

        body.performTouchInput { longClick() }
        assertEquals(listOf(message.id to false), opened)

        compose.onNodeWithText("🔥").assertIsDisplayed().performTouchInput { click() }
        compose.onNodeWithText("2").assertIsDisplayed()
        verify(vm).setChatReaction(message.id, "🔥", addOnly = false)
        compose.onNodeWithText("🎵").assertIsDisplayed().performTouchInput { click() }
        verify(vm).setChatReaction(message.id, "🎵", addOnly = false)
        compose.onNodeWithContentDescription("Reactions").performClick()
        verifyNoMoreInteractions(vm)
        assertEquals(listOf(message.id to false, message.id to true), opened)
    }

    @Test
    fun emojiPickerSearchSelectsAnEmojiOutsideQuickReactions() {
        val chosen = mutableListOf<String>()
        compose.setContent {
            LiveEmojiPicker(selected = emptySet(), pending = emptySet()) { chosen.add(it) }
        }
        compose.onNodeWithText("Search").performTextInput("party popper")
        compose.onNodeWithContentDescription("party popper").assertIsDisplayed().performClick()
        assertEquals(listOf("🎉"), chosen)
    }

    @Test
    fun skinToneVariantsAppearOnlyAfterHoldingTheDefaultEmoji() {
        val chosen = mutableListOf<String>()
        compose.setContent {
            LiveEmojiPicker(selected = emptySet(), pending = emptySet()) { chosen.add(it) }
        }
        compose.onNodeWithText("Search").performTextInput("thumbs up")
        compose.onNodeWithContentDescription("thumbs up: medium skin tone").assertDoesNotExist()
        compose.onNodeWithContentDescription("thumbs up").performTouchInput { longClick() }
        compose.onNodeWithContentDescription("thumbs up: medium skin tone").assertIsDisplayed().performClick()
        assertEquals(listOf("👍🏽"), chosen)
    }

    @Test
    fun emojiPickerCanScrollFromSmileysToLaterCategories() {
        compose.setContent {
            LiveEmojiPicker(selected = emptySet(), pending = emptySet()) {}
        }
        compose.onNodeWithText("Smileys and emotion").assertIsDisplayed()
        compose.onNode(hasScrollToIndexAction()).performScrollToKey("category-2")
        compose.onNodeWithText("Animals and nature").assertIsDisplayed()
        compose.onNode(hasScrollToIndexAction()).performScrollToKey("category-8")
        compose.onNodeWithText("Flags").assertIsDisplayed()
    }

    @Test
    fun optimisticReactionMergesWithServerCountsAndCanRollBack() {
        val key = "chat-message" to "🔥"
        val message = LiveMessage(key.first, mapOf("chatReactions" to mapOf(key.second to listOf("host"))))
        val confirmed = message.copy(fields = mapOf("chatReactions" to mapOf(key.second to listOf("host", "me"))))
        val state = LiveUiState(uid = "me")
        val adding = state.copy(pendingChatReactions = mapOf(key to PendingLiveChatReaction(active = true)))
        assertEquals(listOf("host", "me"), adding.reactionUsers(message, key.second))
        assertEquals(listOf("host", "me"), adding.reactionUsers(confirmed, key.second))
        assertEquals(listOf("host"), state.reactionUsers(message, key.second))

        val removing = state.copy(pendingChatReactions = mapOf(key to PendingLiveChatReaction(active = false)))
        assertEquals(listOf("host"), removing.reactionUsers(confirmed, key.second))
        assertEquals(listOf("host", "me"), state.reactionUsers(confirmed, key.second))
    }

    @Test
    fun replyPutsTheCaretAfterTheMentionAndSpace() {
        val reply = TextFieldValue("@gabe existing", TextRange(6))
        assertEquals(reply, liveChatReplyDraft(TextFieldValue("existing"), "gabe"))
        assertEquals(reply, liveChatReplyDraft(TextFieldValue("@gabe existing", TextRange.Zero), "@gabe"))
        assertEquals(TextFieldValue("@gabe ", TextRange(6)), liveChatReplyDraft(TextFieldValue(), "gabe"))
    }
}
