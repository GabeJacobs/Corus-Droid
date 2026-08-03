package fm.corus.android.ui.screens.messaging

import fm.corus.android.data.model.CymbalThread
import fm.corus.android.data.model.CymbalUser
import fm.corus.android.data.remote.CloudFunctionsDataSource
import fm.corus.android.data.repository.AuthRepository
import fm.corus.android.data.repository.MessageRepository
import fm.corus.android.data.repository.UserRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.isNull
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.util.Date

/**
 * A conversation with someone the user has blocked is not part of their inbox,
 * and neither is one with an account that has been banned. Both must hold on
 * every frame and from every source the list is built from — the reopen cache,
 * the live snapshot, the paginated callable and search — not just on the paths
 * that happen to run a server round trip first.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ThreadListBlockedRowTest {

    private val testDispatcher = StandardTestDispatcher()

    private lateinit var messageRepository: MessageRepository
    private lateinit var authRepository: AuthRepository
    private lateinit var userRepository: UserRepository
    private lateinit var remoteConfigService: fm.corus.android.service.RemoteConfigService
    private lateinit var analyticsService: fm.corus.android.service.AnalyticsService

    private lateinit var live: MutableSharedFlow<List<CymbalThread>>

    /** The device's own block set, as the app carries it: live, and mutated the
     *  moment the user blocks someone. */
    private lateinit var blockedIds: MutableStateFlow<Set<String>>

    private fun user(id: String) = CymbalUser(id = id, username = "user-$id", displayName = "User $id")

    /** A live mirror row: no resolved profile, and it carries its own block state. */
    private fun summary(id: String, at: Long, blocked: Boolean = false) = CymbalThread(
        id = id,
        otherUser = null,
        otherUserId = "u-$id",
        lastMessageText = "live $id",
        lastMessageAt = Date(at),
        lastMessageFromUserId = "u-$id",
        unreadCount = 1,
        blocked = blocked,
    )

    /** A callable row: profile already joined server-side. */
    private fun row(id: String, at: Long) = CymbalThread(
        id = id,
        otherUser = user("u-$id"),
        otherUserId = "u-$id",
        lastMessageText = "callable $id",
        lastMessageAt = Date(at),
        lastMessageFromUserId = "u-$id",
    )

    private fun page(threads: List<CymbalThread>, cursor: Long? = null, hasMore: Boolean = false) =
        CloudFunctionsDataSource.ThreadListPage(threads, cursor, hasMore)

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        live = MutableSharedFlow(replay = 1)
        messageRepository = mock {
            on { leftThreads } doReturn MutableSharedFlow()
            on { cachedInbox } doReturn null
            on { recentlyLeftThreadIds() } doReturn emptySet()
            on { listenToThreadSummaries(any(), any()) } doReturn live
        }
        authRepository = mock { on { currentUserId } doReturn "me" }
        val blocked = MutableStateFlow<Set<String>>(emptySet())
        blockedIds = blocked
        userRepository = mock { on { blockedIds } doReturn blocked }
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

    private suspend fun resolves(vararg ids: String) {
        whenever(userRepository.fetchUsersByIdsBatched(any())).doReturn(ids.map { user(it) })
    }

    @Test
    fun `a blocked correspondent is missing from the frame the listener paints`() = runTest {
        resolves("u-ok")

        val vm = viewModel()
        live.emit(listOf(summary("blocked", at = 2_000, blocked = true), summary("ok", at = 1_000)))
        advanceUntilIdle()

        assertEquals(listOf("ok"), vm.threads.value.map { it.id })
        // And the row is dropped before it costs a profile read, not after.
        verify(userRepository).fetchUsersByIdsBatched(listOf("u-ok"))
    }

    @Test
    fun `blocking a loaded conversation takes its row off the list`() = runTest {
        resolves("u-a")

        val vm = viewModel()
        live.emit(listOf(summary("a", at = 1_000)))
        advanceUntilIdle()
        assertEquals(listOf("a"), vm.threads.value.map { it.id })

        live.emit(listOf(summary("a", at = 1_000, blocked = true)))
        advanceUntilIdle()

        assertTrue(vm.threads.value.isEmpty())
    }

    @Test
    fun `blocking a conversation the live window cannot reach takes its row off`() = runTest {
        // The stamp only ever lands on a row the listener carries. This one sits
        // below the live window: it was paged in, and after the block the server
        // simply stops returning it, so nothing will ever re-state it. The device
        // knows its own block set, which is the whole answer for a row like this.
        whenever(messageRepository.listThreadsPage(eq("me"), any(), isNull()))
            .doReturn(page(listOf(row("newest", 5_000)), cursor = 5_000L, hasMore = true))
        whenever(messageRepository.listThreadsPage(eq("me"), any(), eq(5_000L)))
            .doReturn(page(listOf(row("tail", 3_000)), cursor = 3_000L, hasMore = false))

        val vm = viewModel()
        vm.loadThreads()
        advanceUntilIdle()
        vm.loadMoreThreads()
        advanceUntilIdle()
        assertEquals(listOf("newest", "tail"), vm.threads.value.map { it.id })

        blockedIds.value = setOf("u-tail")
        advanceUntilIdle()

        assertEquals(listOf("newest"), vm.threads.value.map { it.id })
    }

    @Test
    fun `the reopen cache cannot repaint a blocked correspondent`() = runTest {
        // The cached inbox keeps whatever the list last held, including a row
        // that was paged in before the block and never re-tested since.
        blockedIds.value = setOf("u-blocked")
        whenever(messageRepository.cachedInbox).doReturn(
            MessageRepository.CachedInbox("me", listOf(row("keep", 2_000), row("blocked", 1_000)), null, false)
        )

        val vm = viewModel()

        assertEquals(listOf("keep"), vm.threads.value.map { it.id })
    }

    @Test
    fun `the reopen cache cannot repaint a banned correspondent`() = runTest {
        whenever(messageRepository.cachedInbox).doReturn(
            MessageRepository.CachedInbox("me", listOf(row("keep", 2_000), row("ban", 1_000)), null, false)
        )
        whenever(userRepository.isUserBannedLocally("u-ban")).doReturn(true)

        // The seeded list is what the first composed frame draws, before any
        // coroutine has run — so it is asserted without advancing the scheduler.
        val vm = viewModel()

        assertEquals(listOf("keep"), vm.threads.value.map { it.id })
    }

    @Test
    fun `pagination cannot append a banned correspondent`() = runTest {
        whenever(userRepository.isUserBannedLocally("u-ban")).doReturn(true)
        whenever(messageRepository.listThreadsPage(eq("me"), any(), isNull()))
            .doReturn(page(listOf(row("keep", 5_000), row("ban", 4_000)), cursor = 4_000L, hasMore = true))
        whenever(messageRepository.listThreadsPage(eq("me"), any(), eq(4_000L)))
            .doReturn(page(listOf(row("older", 3_000), row("ban2", 2_000)), cursor = 2_000L, hasMore = false))
        whenever(userRepository.isUserBannedLocally("u-ban2")).doReturn(true)

        val vm = viewModel()
        vm.loadThreads()
        advanceUntilIdle()
        assertEquals(listOf("keep"), vm.threads.value.map { it.id })

        vm.loadMoreThreads()
        advanceUntilIdle()

        assertEquals(listOf("keep", "older"), vm.threads.value.map { it.id })
    }

    @Test
    fun `inbox search cannot surface a banned correspondent`() = runTest {
        whenever(userRepository.isUserBannedLocally("u-ban")).doReturn(true)
        whenever(messageRepository.searchThreads(eq("me"), eq("who"), any()))
            .doReturn(listOf(row("hit", 2_000), row("ban", 1_000)))

        val vm = viewModel()
        vm.searchInbox("who")
        advanceUntilIdle()

        assertEquals(listOf("hit"), vm.inboxSearchResults.value?.map { it.id })
    }

    @Test
    fun `a group survives a member the caller cannot see`() {
        val group = CymbalThread(
            id = "g",
            isGroup = true,
            memberIds = listOf("u-ban", "u-ok"),
            lastMessageAt = Date(1_000),
            lastMessageFromUserId = "u-ok",
        )
        val direct = CymbalThread(id = "d", otherUserId = "u-ban", lastMessageAt = Date(1_000))

        val visible = visibleInboxRows(listOf(group, direct), blockedIds = setOf("u-ban")) { it == "u-ban" }

        assertEquals(listOf("g"), visible.map { it.id })
    }

    @Test
    fun `an inbox with no one hidden in it is left alone`() = runTest {
        resolves("u-a", "u-b")

        val vm = viewModel()
        live.emit(listOf(summary("a", at = 2_000), summary("b", at = 1_000)))
        advanceUntilIdle()

        assertEquals(listOf("a", "b"), vm.threads.value.map { it.id })
    }
}
