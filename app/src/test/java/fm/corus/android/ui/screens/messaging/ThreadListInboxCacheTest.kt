package fm.corus.android.ui.screens.messaging

import fm.corus.android.data.model.CymbalThread
import fm.corus.android.data.model.CymbalUser
import fm.corus.android.data.repository.AuthRepository
import fm.corus.android.data.repository.MessageRepository
import fm.corus.android.data.repository.UserRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.mockito.kotlin.any
import org.mockito.kotlin.doSuspendableAnswer
import fm.corus.android.data.remote.CloudFunctionsDataSource
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.runCurrent
import org.junit.Assert.assertFalse

/**
 * Regression tests for cross-instance inbox seeding. The Messages screen is a
 * `navigate()` / `popBackStack()` destination, so its ViewModel is rebuilt every
 * time the user reopens it. Before this, the rebuilt ViewModel started with an
 * empty list and `isLoading = true`, so the skeleton showed on every reopen even
 * seconds after viewing. The ViewModel now seeds from `MessageRepository`'s
 * cached inbox (scoped to the current user) and renders instantly instead.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ThreadListInboxCacheTest {

    private val testDispatcher = StandardTestDispatcher()

    private lateinit var authRepository: AuthRepository
    private lateinit var userRepository: UserRepository
    private lateinit var remoteConfigService: fm.corus.android.service.RemoteConfigService
    private lateinit var analyticsService: fm.corus.android.service.AnalyticsService

    private fun user(id: String) = CymbalUser(id = id, username = "u_$id", displayName = id)

    private fun thread(id: String) = CymbalThread(
        id = id,
        otherUser = user(id),
        otherUserId = id,
        lastMessageText = "hello",
        lastMessageAt = java.util.Date(if (id == "t1") 200 else 100),
        lastMessageFromUserId = id,
    )

    private fun repoWithCache(cache: MessageRepository.CachedInbox?): MessageRepository = mock {
        on { confirmedSends } doReturn kotlinx.coroutines.flow.MutableSharedFlow()
        on { leftThreads } doReturn kotlinx.coroutines.flow.MutableSharedFlow()
        on { cachedInbox } doReturn cache
        on { listenToThreadSummaries(org.mockito.kotlin.any(), org.mockito.kotlin.any()) } doReturn
            kotlinx.coroutines.flow.emptyFlow()
    }

    private fun viewModel(repo: MessageRepository) = ThreadListViewModel(
        repo, authRepository, userRepository, remoteConfigService, analyticsService,
    )

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        authRepository = mock { on { currentUserId } doReturn "me" }
        userRepository = mock { on { blockedIds } doReturn MutableStateFlow(emptySet()) }
        remoteConfigService = mock()
        analyticsService = mock()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun seedsFromCacheForCurrentUserAndSkipsSkeleton() {
        val cached = MessageRepository.CachedInbox(
            userId = "me",
            threads = listOf(thread("t1"), thread("t2")),
            nextCursor = 123L,
            hasMore = true,
        )
        val vm = viewModel(repoWithCache(cached))

        assertEquals(false, vm.isLoading.value)
        assertEquals(cached.threads, vm.threads.value)
        assertTrue(vm.hasMoreThreads.value)
    }

    @Test
    fun noCacheShowsSkeleton() {
        val vm = viewModel(repoWithCache(null))

        assertEquals(true, vm.isLoading.value)
        assertEquals(emptyList<CymbalThread>(), vm.threads.value)
    }

    @Test
    fun cacheForADifferentUserIsIgnored() {
        val cached = MessageRepository.CachedInbox(
            userId = "someone_else",
            threads = listOf(thread("t1")),
            nextCursor = null,
            hasMore = false,
        )
        val vm = viewModel(repoWithCache(cached))

        // A previous user's inbox must never seed the current user's screen.
        assertEquals(true, vm.isLoading.value)
        assertEquals(emptyList<CymbalThread>(), vm.threads.value)
    }
    @Test
    fun sendDuringRefreshForcesAnotherReadAndShowsNewConversation() = runTest(testDispatcher) {
        val sends = MutableSharedFlow<String>(replay = 1)
        val oldPage = CompletableDeferred<CloudFunctionsDataSource.ThreadListPage>()
        val old = thread("old").copy(lastMessageAt = java.util.Date(100))
        val shared = thread("shared").copy(lastMessageAt = java.util.Date(200))
        val repo = repoWithCache(MessageRepository.CachedInbox("me", listOf(old), null, false))
        whenever(repo.confirmedSends).thenReturn(sends)
        var reads = 0
        whenever(repo.listThreadsPage(any(), any(), org.mockito.kotlin.anyOrNull())).doSuspendableAnswer {
            reads++
            if (reads == 1) oldPage.await()
            else CloudFunctionsDataSource.ThreadListPage(listOf(shared, old), null, false)
        }
        val vm = viewModel(repo)
        vm.loadThreads()
        runCurrent()
        sends.emit("me")
        runCurrent()
        assertEquals(1, reads)
        assertEquals(0L, vm.scrollToNewestRequest.value)
        oldPage.complete(CloudFunctionsDataSource.ThreadListPage(listOf(old), null, false))
        runCurrent()
        assertEquals(2, reads)
        assertEquals(1L, vm.scrollToNewestRequest.value)
        assertEquals("shared", vm.threads.value.first().id)
        assertFalse(vm.isLoading.value)
    }

}
