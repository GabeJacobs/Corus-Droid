package fm.corus.android.ui.screens.messaging

import android.app.Application
import android.content.Context
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
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

/**
 * The screen a tapped push and a deep link both land on. Whatever the rule says
 * about a conversation has to be answered here, because nothing upstream of this
 * screen asks the question on those paths.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class MessageThreadScreenAccessTest {

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

    private fun render(row: CymbalThread?) {
        val messageRepository = mock<MessageRepository> {
            on { listenToMessages(any()) } doReturn flowOf(listOf(incoming))
            on { listenToGroupThreadInfo(any()) } doReturn emptyFlow()
            on { listenToRecipientUnreadCount(any(), any()) } doReturn emptyFlow()
            on { listenToReadReceiptsEnabled(any()) } doReturn emptyFlow()
            on { listenToThreadRow(any(), any()) } doReturn
                flowOf(MessageRepository.ThreadRowSnapshot(thread = row, fromCache = false))
        }
        val viewModel = MessageThreadViewModel(
            messageRepository = messageRepository,
            authRepository = mock<AuthRepository> { on { currentUserId } doReturn "me" },
            userRepository = mock<UserRepository> { on { blockedIds } doReturn MutableStateFlow(emptySet()) },
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
                MessageThreadScreen(
                    threadId = "thread1",
                    otherUserId = "other",
                    viewModel = viewModel,
                )
            }
        }
        composeRule.waitForIdle()
    }

    @Test
    fun `a blocked conversation opened from a notification shows nothing of itself`() {
        render(CymbalThread(id = "thread1", otherUserId = "other", blocked = true))

        composeRule.onNodeWithText(incoming.text!!).assertDoesNotExist()
        composeRule.onNodeWithText(context.getString(R.string.messaging_thread_placeholder)).assertDoesNotExist()
        composeRule.onNodeWithText(context.getString(R.string.messaging_thread_unavailable)).assertIsDisplayed()
    }

    @Test
    fun `an ordinary conversation still opens`() {
        render(CymbalThread(id = "thread1", otherUserId = "other"))

        composeRule.onNodeWithText(context.getString(R.string.messaging_thread_unavailable)).assertDoesNotExist()
        composeRule.onNodeWithText(incoming.text!!).assertIsDisplayed()
    }

    @Test
    fun `an empty 1-1 thread shows the profile opener above the composer`() {
        val messageRepository = mock<MessageRepository> {
            on { listenToMessages(any()) } doReturn flowOf(emptyList())
            on { listenToGroupThreadInfo(any()) } doReturn emptyFlow()
            on { listenToRecipientUnreadCount(any(), any()) } doReturn emptyFlow()
            on { listenToReadReceiptsEnabled(any()) } doReturn emptyFlow()
            on { listenToThreadRow(any(), any()) } doReturn
                flowOf(
                    MessageRepository.ThreadRowSnapshot(
                        thread = CymbalThread(id = "thread1", otherUserId = "other"),
                        fromCache = false,
                    ),
                )
        }
        val userRepository = mock<UserRepository> {
            on { blockedIds } doReturn MutableStateFlow(emptySet())
            onBlocking { fetchUserProfile(any()) } doReturn fm.corus.android.data.model.CymbalUser(
                id = "other",
                username = "devynbrowne",
                displayName = "Devyn",
                artistsInCommonCount = 3,
            )
        }
        val viewModel = MessageThreadViewModel(
            messageRepository = messageRepository,
            authRepository = mock<AuthRepository> { on { currentUserId } doReturn "me" },
            userRepository = userRepository,
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
                MessageThreadScreen(
                    threadId = "thread1",
                    otherUserId = "other",
                    viewModel = viewModel,
                )
            }
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Devyn").assertIsDisplayed()
        composeRule.onNodeWithText("@devynbrowne").assertIsDisplayed()
        composeRule.onNodeWithText(
            context.getString(R.string.notif_taste_match_body_artists, 3),
        ).assertIsDisplayed()
        composeRule.onNodeWithText(
            context.getString(R.string.messaging_thread_view_profile),
        ).assertIsDisplayed()
        composeRule.onNodeWithText(
            context.getString(R.string.messaging_thread_placeholder),
        ).assertIsDisplayed()
    }
}
