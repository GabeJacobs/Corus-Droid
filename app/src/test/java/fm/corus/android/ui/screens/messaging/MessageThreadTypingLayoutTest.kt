package fm.corus.android.ui.screens.messaging

import android.app.Application
import android.content.Context
import android.view.View
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import fm.corus.android.ui.components.LocalBottomBarHeight
import org.junit.Assert.assertEquals
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

    private lateinit var rootView: View

    private fun render(row: CymbalThread?, parentHandlesIme: Boolean = false, reservedChrome: androidx.compose.ui.unit.Dp = 0.dp) {
        val messageRepository = mock<MessageRepository> {
            on { outgoingShares } doReturn kotlinx.coroutines.flow.MutableStateFlow(emptyList())
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
            rootView = LocalView.current
            CompositionLocalProvider(
                LocalHapticManager provides mock<HapticManager>(),
                LocalBottomBarHeight provides reservedChrome,
            ) {
                Box(Modifier.height(viewportHeight.value).testTag("thread-viewport")) { MessageThreadScreen(
                    threadId = "thread1",
                    otherUserId = "other",
                    viewModel = viewModel,
                    parentHandlesIme = parentHandlesIme,
                ) }
            }
        }
        composeRule.waitForIdle()
    }

    private fun composerBottomGap(): Float {
        val viewport = composeRule.onNodeWithTag("thread-viewport").fetchSemanticsNode().boundsInRoot
        val composer = composeRule.onNodeWithText(context.getString(R.string.messaging_thread_placeholder))
            .assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        return viewport.bottom - composer.bottom
    }

    private fun dispatchKeyboard(height: androidx.compose.ui.unit.Dp) {
        val pixels = with(composeRule.density) { height.roundToPx() }
        composeRule.runOnIdle {
            ViewCompat.dispatchApplyWindowInsets(rootView,
                WindowInsetsCompat.Builder()
                    .setInsets(WindowInsetsCompat.Type.ime(), Insets.of(0, 0, 0, pixels))
                    .setVisible(WindowInsetsCompat.Type.ime(), pixels > 0)
                    .build())
        }
        composeRule.waitForIdle()
    }

    @Test
    @Config(qualifiers = "en-rUS-w393dp-h800dp")
    fun `sheet resized for keyboard does not lift the conversation a second time`() {
        viewportHeight.value = 600.dp
        render(CymbalThread(id = "thread1", otherUserId = "other"), parentHandlesIme = true)
        val initialGap = composerBottomGap()
        composeRule.runOnIdle { viewportHeight.value = 360.dp }
        dispatchKeyboard(240.dp)
        assertEquals("The sheet already removed keyboard space", initialGap, composerBottomGap(), 1f)
        dispatchKeyboard(0.dp)
        composeRule.runOnIdle { viewportHeight.value = 600.dp }
        assertEquals(initialGap, composerBottomGap(), 1f)
    }

    @Test
    @Config(qualifiers = "en-rUS-w393dp-h800dp")
    fun `messages tab still lifts composer above keyboard minus reserved chrome`() {
        viewportHeight.value = 600.dp
        render(CymbalThread(id = "thread1", otherUserId = "other"), reservedChrome = 80.dp)
        val initialGap = composerBottomGap()
        dispatchKeyboard(240.dp)
        val expectedLift = with(composeRule.density) { 160.dp.toPx() }
        assertEquals(initialGap + expectedLift, composerBottomGap(), 1f)
        dispatchKeyboard(0.dp)
        assertEquals(initialGap, composerBottomGap(), 1f)
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
