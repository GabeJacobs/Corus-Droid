package fm.corus.android.ui.screens.messaging

import fm.corus.android.data.model.CymbalThread
import fm.corus.android.data.model.MessagingRestriction
import fm.corus.android.data.repository.AuthRepository
import fm.corus.android.data.repository.MessageRepository
import fm.corus.android.data.repository.UserRepository
import fm.corus.android.service.RemoteConfigService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.doSuspendableAnswer
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

/**
 * A conversation the product refuses to hand over must not open, whichever way
 * the user arrived — a tapped push and a deep link both land straight on this
 * screen, and neither has passed the inbox. The screen's own answer is
 * [MessageThreadViewModel.threadAccess], so that is what these hold to.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MessageThreadAccessTest {

    private val testDispatcher = StandardTestDispatcher()

    private lateinit var messageRepository: MessageRepository
    private lateinit var authRepository: AuthRepository
    private lateinit var userRepository: UserRepository
    private lateinit var blockedIds: MutableStateFlow<Set<String>>
    private lateinit var rows: MutableSharedFlow<MessageRepository.ThreadRowSnapshot>

    private fun row(thread: CymbalThread?, fromCache: Boolean = false) =
        MessageRepository.ThreadRowSnapshot(thread = thread, fromCache = fromCache)

    private fun direct(blocked: Boolean = false, otherUserId: String = "other") =
        CymbalThread(id = "thread1", otherUserId = otherUserId, blocked = blocked)

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        rows = MutableSharedFlow(replay = 1)
        messageRepository = mock {
            on { listenToMessages(any()) } doReturn emptyFlow()
            on { listenToGroupThreadInfo(any()) } doReturn emptyFlow()
            on { listenToRecipientUnreadCount(any(), any()) } doReturn emptyFlow()
            on { listenToReadReceiptsEnabled(any()) } doReturn emptyFlow()
            on { listenToThreadRow(any(), any()) } doReturn rows
        }
        authRepository = mock { on { currentUserId } doReturn "me" }
        blockedIds = MutableStateFlow(emptySet())
        userRepository = mock { on { this.blockedIds } doReturn blockedIds }
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel() = MessageThreadViewModel(
        messageRepository = messageRepository,
        authRepository = authRepository,
        userRepository = userRepository,
        exploreRepository = mock(),
        postRepository = mock(),
        remoteConfigService = mock<RemoteConfigService>(),
        gifRepository = mock(),
        nowPlayingManager = mock(),
        analyticsService = mock(),
        context = mock(),
    )

    @Test
    fun `an ordinary conversation opens`() = runTest {
        val vm = viewModel()
        vm.loadMessages("thread1", "other")
        rows.emit(row(direct()))
        advanceUntilIdle()

        assertEquals(ThreadAccess.OPEN, vm.threadAccess.value)
    }

    @Test
    fun `a conversation the caller blocked does not open`() = runTest {
        val vm = viewModel()
        vm.loadMessages("thread1", "other")
        rows.emit(row(direct(blocked = true)))
        advanceUntilIdle()

        assertEquals(ThreadAccess.UNAVAILABLE, vm.threadAccess.value)
    }

    @Test
    fun `a conversation with a banned account does not open`() = runTest {
        whenever(userRepository.isUserBannedLocally("other")).doReturn(true)

        val vm = viewModel()
        vm.loadMessages("thread1", "other")
        rows.emit(row(direct()))
        advanceUntilIdle()

        assertEquals(ThreadAccess.UNAVAILABLE, vm.threadAccess.value)
    }

    @Test
    fun `a block landing while the conversation is open takes it away`() = runTest {
        val vm = viewModel()
        vm.loadMessages("thread1", "other")
        rows.emit(row(direct()))
        advanceUntilIdle()
        assertEquals(ThreadAccess.OPEN, vm.threadAccess.value)

        rows.emit(row(direct(blocked = true)))
        advanceUntilIdle()

        assertEquals(ThreadAccess.UNAVAILABLE, vm.threadAccess.value)
    }

    @Test
    fun `blocking on this device takes it away without waiting for the row to say so`() = runTest {
        val vm = viewModel()
        vm.loadMessages("thread1", "other")
        rows.emit(row(direct()))
        advanceUntilIdle()
        assertEquals(ThreadAccess.OPEN, vm.threadAccess.value)

        blockedIds.value = setOf("other")
        advanceUntilIdle()

        assertEquals(ThreadAccess.UNAVAILABLE, vm.threadAccess.value)
    }

    @Test
    fun `a conversation the caller is no longer part of does not open`() = runTest {
        val vm = viewModel()
        vm.loadMessages("thread1", "other")
        rows.emit(row(null))
        advanceUntilIdle()

        assertEquals(ThreadAccess.UNAVAILABLE, vm.threadAccess.value)
    }

    @Test
    fun `a cold deep link waits for the answer rather than assuming one`() = runTest {
        val vm = viewModel()
        vm.loadMessages("thread1", "other")
        advanceUntilIdle()
        assertEquals(ThreadAccess.RESOLVING, vm.threadAccess.value)

        // The device has never held this row, so its own cache saying "absent"
        // is not the server saying "gone".
        rows.emit(row(null, fromCache = true))
        advanceUntilIdle()

        assertEquals(ThreadAccess.RESOLVING, vm.threadAccess.value)
    }

    @Test
    fun `inbox-miss compose is open before getOrCreate returns`() = runTest {
        whenever(userRepository.peekCachedUser(any())).doReturn(null)
        whenever(messageRepository.getOrCreateThread(any(), any())).doReturn("t1")
        // Row listener never emits — access must stay OPEN from compose alone.
        whenever(userRepository.fetchUserProfile(any())).doReturn(
            fm.corus.android.data.model.CymbalUser(
                id = "other",
                username = "other",
                displayName = "Other",
            ),
        )

        val vm = viewModel()
        vm.loadMessages("", "other")
        advanceUntilIdle()

        assertEquals(true, vm.openedAsNewCompose.value)
        assertEquals(ThreadAccess.OPEN, vm.threadAccess.value)
    }

    @Test
    fun `the row consulted is the caller's own, which is the one the rule is written against`() = runTest {
        val vm = viewModel()
        vm.loadMessages("thread1", "other")
        advanceUntilIdle()

        verify(messageRepository).listenToThreadRow(eq("me"), eq("thread1"))
    }

    @Test
    fun `a conversation that cannot even be started is not shown`() = runTest {
        // Opening from a profile has no thread id yet, so the create callable is
        // the thing that refuses — for a blocked or banned correspondent, or one
        // who has messaging off. Failing to create IS the refusal.
        whenever(messageRepository.getOrCreateThread(any(), any()))
            .doSuspendableAnswer { throw RuntimeException("Cannot message this user") }

        val vm = viewModel()
        vm.loadMessages("", "other")
        advanceUntilIdle()

        assertEquals(ThreadAccess.UNAVAILABLE, vm.threadAccess.value)
    }

    @Test
    fun `messaging off on the create path is a restriction, not a generic closed thread`() = runTest {
        whenever(messageRepository.getOrCreateThread(any(), any()))
            .doSuspendableAnswer { throw RuntimeException("This user has turned off messaging") }

        val vm = viewModel()
        vm.loadMessages("", "other")
        advanceUntilIdle()

        assertEquals(ThreadAccess.UNAVAILABLE, vm.threadAccess.value)
        assertEquals(MessagingRestriction.NOBODY, vm.messagingRestriction.value)
    }

    @Test
    fun `a group opens even though one member is blocked and another banned`() = runTest {
        whenever(userRepository.isUserBannedLocally("spammer")).doReturn(true)
        blockedIds.value = setOf("nemesis")

        val vm = viewModel()
        vm.loadMessages("grp1", "")
        rows.emit(
            row(
                CymbalThread(
                    id = "grp1",
                    isGroup = true,
                    memberIds = listOf("me", "nemesis", "spammer", "friend"),
                )
            )
        )
        advanceUntilIdle()

        assertEquals(ThreadAccess.OPEN, vm.threadAccess.value)
    }
}
