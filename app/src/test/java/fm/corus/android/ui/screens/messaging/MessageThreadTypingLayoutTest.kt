package fm.corus.android.ui.screens.messaging

import android.app.Application
import android.content.Context
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import fm.corus.android.R
import fm.corus.android.data.model.CymbalMessage
import fm.corus.android.data.model.CymbalThread
import fm.corus.android.data.model.MessageType
import fm.corus.android.data.repository.AuthRepository
import fm.corus.android.data.repository.MessageRepository
import fm.corus.android.data.repository.UserRepository
import fm.corus.android.domain.HapticManager
import fm.corus.android.service.RemoteConfigService
import fm.corus.android.ui.LocalHapticManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Exercises the real chat layout with incoming typing and a keyboard-sized viewport. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class MessageThreadTypingLayoutTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val context: Context
        get() = ApplicationProvider.getApplicationContext()

    private val incoming = CymbalMessage(
        id = "m1",
        threadId = "thread1",
        fromUserId = "other",
        text = "meet me at the show",
        type = MessageType.TEXT,
    )

    private val viewportHeight = mutableStateOf(420.dp)

    private lateinit var viewModel: MessageThreadViewModel

    private fun render(row: CymbalThread?) {
        val messageRepository = mock<MessageRepository> {
            on { listenToMessages(any()) } doReturn flowOf((0..30).map { incoming.copy(id = "m$it", text = "Message $it") })
            on { listenToGroupThreadInfo(any()) } doReturn emptyFlow()
            on { listenToRecipientUnreadCount(any(), any()) } doReturn emptyFlow()
            on { listenToReadReceiptsEnabled(any()) } doReturn emptyFlow()
            on { listenToThreadRow(any(), any()) } doReturn
                flowOf(MessageRepository.ThreadRowSnapshot(thread = row, fromCache = false))
        }
        viewModel = MessageThreadViewModel(
            messageRepository = messageRepository,
            authRepository = mock<AuthRepository> { on { currentUserId } doReturn "me" },
            userRepository = mock<UserRepository> {
                on { blockedIds } doReturn MutableStateFlow(emptySet())
                on { blockedIdsLoaded } doReturn MutableStateFlow(true)
                on { blockedIdsLoadFailed } doReturn MutableStateFlow(false)
            },
            exploreRepository = mock(),
            postRepository = mock(),
            remoteConfigService = mock<RemoteConfigService>(),
            gifRepository = mock(),
            nowPlayingManager = mock(),
            analyticsService = mock(),
            context = context,
        )

        composeRule.setContent {
            CompositionLocalProvider(LocalHapticManager provides mock<HapticManager>()) {
                Box(Modifier.height(viewportHeight.value)) { MessageThreadScreen(
                    threadId = "thread1",
                    otherUserId = "other",
                    viewModel = viewModel,
                ) }
            }
        }
        composeRule.waitForIdle()
    }

    @Test
    fun `typing appearing at latest stays fully above composer in keyboard sized viewport`() {
        render(CymbalThread(id = "thread1", otherUserId = "other"))
        setTyping()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("chat-typing-indicator").assertIsDisplayed()
        val typing = composeRule.onNodeWithTag("chat-typing-indicator").fetchSemanticsNode().boundsInRoot
        val composer = composeRule.onNodeWithText(context.getString(R.string.messaging_thread_placeholder)).fetchSemanticsNode().boundsInRoot
        org.junit.Assert.assertTrue("Typing row must not be clipped: $typing", typing.height >= with(composeRule.density) { 31.dp.toPx() })
        org.junit.Assert.assertTrue("Typing row must be fully above composer: $typing vs $composer", typing.bottom <= composer.top)
        // Opening/closing the keyboard changes the viewport, not the anchor.
        for (height in listOf(600.dp, 360.dp)) {
            composeRule.runOnIdle { viewportHeight.value = height }
            composeRule.waitForIdle()
            val resized = composeRule.onNodeWithTag("chat-typing-indicator").fetchSemanticsNode().boundsInRoot
            org.junit.Assert.assertTrue("Typing row clipped after resize: $resized", resized.height >= with(composeRule.density) { 31.dp.toPx() })
        }
    }
    @Test
    fun `new incoming message moves existing rows upward over time`() {
        render(CymbalThread(id = "thread1", otherUserId = "other"))
        val before = composeRule.onNodeWithText("Message 0").fetchSemanticsNode().boundsInRoot.top
        composeRule.mainClock.autoAdvance = false
        receiveMessage()
        composeRule.mainClock.advanceTimeBy(96)
        composeRule.waitForIdle()
        val during = composeRule.onNodeWithText("Message 0").fetchSemanticsNode().boundsInRoot.top
        composeRule.mainClock.advanceTimeBy(500)
        composeRule.waitForIdle()
        val after = composeRule.onNodeWithText("Message 0").fetchSemanticsNode().boundsInRoot.top
        org.junit.Assert.assertTrue("Rows should start moving upward: $before -> $during", during < before)
        org.junit.Assert.assertTrue("Rows should continue moving, not appear instantly: $during -> $after", after < during)
        composeRule.onNodeWithText("New incoming message").assertIsDisplayed()
    }

    private fun receiveMessage() {
        composeRule.runOnIdle {
            val field = MessageThreadViewModel::class.java.getDeclaredField("_serverMessages")
            field.isAccessible = true
            @Suppress("UNCHECKED_CAST")
            val server = field.get(viewModel) as MutableStateFlow<List<CymbalMessage>>
            server.value = server.value + incoming.copy(
                id = "new-incoming", text = "New incoming message",
                createdAt = java.util.Date(System.currentTimeMillis() + 10_000),
            )
        }
    }

    private fun setTyping() {
        composeRule.runOnIdle {
            val field = MessageThreadViewModel::class.java.getDeclaredField("_typerIds")
            field.isAccessible = true
            @Suppress("UNCHECKED_CAST")
            val typers = field.get(viewModel) as MutableStateFlow<List<String>>
            typers.value = listOf("other")
        }
    }

    @Test
    fun `typing does not move the reader away from older messages`() {
        render(CymbalThread(id = "thread1", otherUserId = "other"))
        composeRule.onNode(hasScrollAction()).performScrollToIndex(12)
        composeRule.waitForIdle()
        val before = composeRule.onNodeWithText("Message 12").fetchSemanticsNode().boundsInRoot
        setTyping()
        composeRule.waitForIdle()
        val after = composeRule.onNodeWithText("Message 12").fetchSemanticsNode().boundsInRoot
        org.junit.Assert.assertEquals(before, after)
        composeRule.onNodeWithTag("chat-typing-indicator").assertDoesNotExist()
        receiveMessage()
        composeRule.waitForIdle()
        val afterArrival = composeRule.onNodeWithText("Message 12").fetchSemanticsNode().boundsInRoot
        org.junit.Assert.assertEquals("An incoming message must not move a reader in history", before, afterArrival)
    }
}
