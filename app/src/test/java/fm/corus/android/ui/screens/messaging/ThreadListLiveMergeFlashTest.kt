package fm.corus.android.ui.screens.messaging

import fm.corus.android.data.model.CymbalThread
import fm.corus.android.data.model.CymbalUser
import fm.corus.android.data.remote.CloudFunctionsDataSource
import fm.corus.android.data.repository.AuthRepository
import fm.corus.android.data.repository.MessageRepository
import fm.corus.android.data.repository.UserRepository
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
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
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.doSuspendableAnswer
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.util.Date

/**
 * Regression test for the inbox "No messages yet" flash. The live thread-summary
 * listener can deliver a partial snapshot (an offline-cache replay on attach, or a
 * page dominated by brand-new threads) whose only entries are threads not yet in
 * the loaded list. The merge prunes the known rows as "vanished" while the new ones
 * still need async profile resolution — so for the duration of that network call
 * the inbox would publish an empty list, flashing the empty state and poisoning the
 * reopen cache. The ViewModel now holds the current rows until resolution lands.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ThreadListLiveMergeFlashTest {

    private val testDispatcher = StandardTestDispatcher()

    private lateinit var messageRepository: MessageRepository
    private lateinit var authRepository: AuthRepository
    private lateinit var userRepository: UserRepository
    private lateinit var remoteConfigService: fm.corus.android.service.RemoteConfigService
    private lateinit var analyticsService: fm.corus.android.service.AnalyticsService

    private fun user(id: String) = CymbalUser(id = id, username = "user-$id", displayName = "User $id")

    private fun loaded(id: String, at: Long) = CymbalThread(
        id = id,
        otherUser = user(id),
        otherUserId = "u-$id",
        lastMessageText = "hi",
        lastMessageAt = Date(at),
        lastMessageFromUserId = "u-$id",
        unreadCount = 0,
    )

    // A live mirror summary: no resolved profile, just otherUserId.
    private fun summary(id: String, at: Long) = CymbalThread(
        id = id,
        otherUser = null,
        otherUserId = "u-$id",
        lastMessageText = "first message",
        lastMessageAt = Date(at),
        lastMessageFromUserId = "u-$id",
        unreadCount = 1,
    )

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        messageRepository = mock {
            on { leftThreads } doReturn MutableSharedFlow()
            on { cachedInbox } doReturn null
            on { recentlyLeftThreadIds() } doReturn emptySet()
        }
        authRepository = mock { on { currentUserId } doReturn "me" }
        userRepository = mock { on { blockedIds } doReturn MutableStateFlow(emptySet()) }
        remoteConfigService = mock()
        analyticsService = mock()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel() = ThreadListViewModel(
        messageRepository, authRepository, userRepository, remoteConfigService, analyticsService,
    )

    /**
     * The live listener's first (sub-pageSize, so "complete") snapshot carries only
     * a brand-new thread: the known rows are absent from it and would be pruned.
     * Gating the profile resolution freezes the inbox in the window where the empty
     * list used to show.
     */
    private suspend fun arrangeSnapshotOfOnlyANewThread(): CompletableDeferred<Unit> {
        whenever(messageRepository.listenToThreadSummaries(any(), any()))
            .thenReturn(flowOf(listOf(summary("new", at = 9_000))))
        val resolveGate = CompletableDeferred<Unit>()
        whenever(userRepository.fetchUsersByIdsBatched(listOf("u-new"))).doSuspendableAnswer {
            resolveGate.await()
            listOf(user("new").copy(id = "u-new"))
        }
        return resolveGate
    }

    @Test
    fun `partial live snapshot of only new threads does not blink the inbox empty`() = runTest {
        // Reopening Messages: the last-rendered inbox is on screen from the first
        // frame, and the snapshot arrives against it.
        whenever(messageRepository.cachedInbox).doReturn(
            MessageRepository.CachedInbox(
                userId = "me",
                threads = listOf(loaded("t1", at = 2_000), loaded("t2", at = 1_000)),
                nextCursor = null,
                hasMore = false,
            )
        )
        val resolveGate = arrangeSnapshotOfOnlyANewThread()

        val vm = viewModel()
        advanceUntilIdle()

        // Resolution is still pending here. The inbox must keep showing the known
        // rows, NOT blink to an empty list (which is the "No messages yet" flash).
        assertEquals(listOf("t1", "t2"), vm.threads.value.map { it.id })

        // Once the profile lands, the authoritative snapshot result is published:
        // the new thread, resolved, with the pruned rows dropped — identical final
        // state, just without the empty flash.
        resolveGate.complete(Unit)
        advanceUntilIdle()

        assertEquals(listOf("new"), vm.threads.value.map { it.id })
        assertEquals("user-new", vm.threads.value.first().otherUser?.username)
    }

    @Test
    fun `rows that land while profiles resolve survive the deferred publish`() = runTest {
        // Same snapshot, but the known rows arrive from the callable *during* the
        // profile fetch. A server round trip that answers after the snapshot was
        // raised is the newer word on what exists; the held publish must fold into
        // it rather than roll the list back to what the snapshot decided.
        whenever(messageRepository.listThreadsPage(any(), any(), anyOrNull())).thenReturn(
            CloudFunctionsDataSource.ThreadListPage(
                threads = listOf(loaded("t1", at = 2_000), loaded("t2", at = 1_000)),
                nextCursor = null,
                hasMore = false,
            )
        )
        val resolveGate = arrangeSnapshotOfOnlyANewThread()

        val vm = viewModel()
        vm.loadThreads()
        advanceUntilIdle()
        assertEquals(listOf("t1", "t2"), vm.threads.value.map { it.id })

        resolveGate.complete(Unit)
        advanceUntilIdle()

        assertEquals(listOf("new", "t1", "t2"), vm.threads.value.map { it.id })
    }
}
