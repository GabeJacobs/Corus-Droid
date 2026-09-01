package fm.corus.android.ui.screens.messaging

import fm.corus.android.data.model.CymbalThread
import fm.corus.android.data.model.CymbalUser
import fm.corus.android.data.repository.AuthRepository
import fm.corus.android.data.repository.MessageRepository
import fm.corus.android.data.repository.UserRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.doSuspendableAnswer
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

/**
 * Regression tests for the inbox search backend fallback. Before this, the
 * inbox "Search messages" field filtered only the threads already paged into
 * memory, so a conversation further down the inbox (e.g. @walasia) was
 * unfindable. `searchInbox` now queries the full thread history server-side.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ThreadListSearchInboxTest {

    private val testDispatcher = StandardTestDispatcher()

    private lateinit var messageRepository: MessageRepository
    private lateinit var authRepository: AuthRepository
    private lateinit var userRepository: UserRepository
    private lateinit var analyticsService: fm.corus.android.service.AnalyticsService
    private lateinit var viewModel: ThreadListViewModel

    private fun user(id: String, username: String, displayName: String) = CymbalUser(
        id = id,
        username = username,
        displayName = displayName,
    )

    private fun thread(id: String, user: CymbalUser, fromUserId: String? = user.id) = CymbalThread(
        id = id,
        otherUser = user,
        otherUserId = user.id,
        lastMessageText = "hello",
        lastMessageFromUserId = fromUserId,
    )

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        messageRepository = mock {
            on { leftThreads } doReturn kotlinx.coroutines.flow.MutableSharedFlow()
            on { listenToThreadSummaries(any(), any()) } doReturn kotlinx.coroutines.flow.emptyFlow()
        }
        authRepository = mock {
            on { currentUserId } doReturn "me"
        }
        userRepository = mock { on { blockedIds } doReturn MutableStateFlow(emptySet()) }
        val remoteConfigService = mock<fm.corus.android.service.RemoteConfigService>()
        analyticsService = mock()
        viewModel = ThreadListViewModel(messageRepository, authRepository, userRepository, remoteConfigService, analyticsService)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun searchInboxQueriesBackendAndPublishesResults() = runTest(testDispatcher) {
        val walasia = thread("t_wal", user("u_wal", "walasia", "Walasia"))
        whenever(messageRepository.searchThreads(eq("me"), eq("walasia"), any()))
            .doReturn(fm.corus.android.data.model.InboxSearchResult(threads = listOf(walasia)))

        viewModel.searchInbox("walasia")
        advanceUntilIdle()

        assertEquals(listOf(walasia), viewModel.inboxSearchResults.first()?.threads)
        assertEquals(false, viewModel.isSearchingInbox.first())
        verify(messageRepository).searchThreads(eq("me"), eq("walasia"), any())
    }

    @Test
    fun searchInboxDropsThreadsWithNoLastMessage() = runTest(testDispatcher) {
        val real = thread("t1", user("u1", "walasia", "Walasia"), fromUserId = "u1")
        val emptyThread = thread("t2", user("u2", "walter", "Walter"), fromUserId = null)
        whenever(messageRepository.searchThreads(any(), any(), any()))
            .doReturn(fm.corus.android.data.model.InboxSearchResult(threads = listOf(real, emptyThread)))

        viewModel.searchInbox("wal")
        advanceUntilIdle()

        assertEquals(listOf(real), viewModel.inboxSearchResults.first()?.threads)
    }

    @Test
    fun blankQueryClearsResultsWithoutHittingBackend() = runTest(testDispatcher) {
        viewModel.searchInbox("   ")
        advanceUntilIdle()

        assertNull(viewModel.inboxSearchResults.first())
        assertEquals(false, viewModel.isSearchingInbox.first())
        verify(messageRepository, org.mockito.kotlin.never()).searchThreads(any(), any(), any())
    }

    @Test
    fun rapidTypingCancelsStaleSearchAndKeepsLatest() = runTest(testDispatcher) {
        val walasia = thread("t_wal", user("u_wal", "walasia", "Walasia"))
        whenever(messageRepository.searchThreads(any(), eq("wal"), any()))
            .doSuspendableAnswer { fm.corus.android.data.model.InboxSearchResult() }
        whenever(messageRepository.searchThreads(any(), eq("walasia"), any()))
            .doSuspendableAnswer { fm.corus.android.data.model.InboxSearchResult(threads = listOf(walasia)) }

        // Debounce (300ms) means the first query is superseded before it fires.
        viewModel.searchInbox("wal")
        viewModel.searchInbox("walasia")
        advanceUntilIdle()

        assertEquals(listOf(walasia), viewModel.inboxSearchResults.first()?.threads)
        verify(messageRepository, org.mockito.kotlin.never()).searchThreads(any(), eq("wal"), any())
    }

    @Test
    fun createGroupLogsGroupCreatedEvent() = runTest(testDispatcher) {
        whenever(messageRepository.createGroupThread(any(), anyOrNull(), anyOrNull()))
            .doSuspendableAnswer { "grp_1" }

        viewModel.createGroup(listOf("u1", "u2"), "Trip")
        advanceUntilIdle()

        // member_count includes the creator: 2 invitees + self = 3; the group was named.
        verify(analyticsService).logGroupCreated(3, true)
    }

    @Test
    fun createGroupWithoutNameReportsHasNameFalse() = runTest(testDispatcher) {
        whenever(messageRepository.createGroupThread(any(), anyOrNull(), anyOrNull()))
            .doSuspendableAnswer { "grp_2" }

        viewModel.createGroup(listOf("u1", "u2", "u3"), null)
        advanceUntilIdle()

        verify(analyticsService).logGroupCreated(4, false)
    }

    @Test
    fun backendFailureLeavesResultsNullSoLocalFilterStaysVisible() = runTest(testDispatcher) {
        whenever(messageRepository.searchThreads(any(), any(), any()))
            .doSuspendableAnswer { throw RuntimeException("network down") }

        viewModel.searchInbox("walasia")
        advanceUntilIdle()

        assertNull(viewModel.inboxSearchResults.first())
        assertEquals(false, viewModel.isSearchingInbox.first())
    }
}
