package fm.corus.android.ui.screens.feed

import fm.corus.android.data.model.CymbalPost
import fm.corus.android.data.model.CymbalTrack
import fm.corus.android.data.model.CymbalUser
import fm.corus.android.data.remote.CloudFunctionsDataSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.util.Date

/**
 * Pagination behavior of the "Reposts" (who-reposted) screen: full pages advance
 * a beforeMs cursor taken from the oldest repost; totalCount surfaces the live
 * repost count; posts are only deduped by id across page boundaries.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class RepostersViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var cloudFunctions: CloudFunctionsDataSource

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        cloudFunctions = mock()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun post(id: String, userId: String, ms: Long) = CymbalPost(
        id = id,
        user = CymbalUser(id = userId, username = userId, displayName = userId),
        track = CymbalTrack(id = "t-$id", name = "Song", artistName = "Artist", albumName = ""),
        timestamp = Date(ms),
    )

    // getReposters reuses DestinationPostsPage; uniquePosterCount carries totalCount.
    private fun page(posts: List<CymbalPost>, total: Int = posts.size) =
        CloudFunctionsDataSource.DestinationPostsPage(posts = posts, uniquePosterCount = total)

    @Test
    fun `full first page sets hasMore and loadMore passes the oldest createdAt as cursor`() =
        runTest(testDispatcher) {
            val firstPage = (0 until RepostersViewModel.PAGE_SIZE)
                .map { post("p$it", "u$it", 10_000L - it) }
            val oldestMs = firstPage.last().timestamp.time
            whenever(
                cloudFunctions.fetchReposters(postId = any(), pageSize = any(), beforeMs = anyOrNull())
            ).thenReturn(page(firstPage, total = 40))

            val vm = RepostersViewModel(cloudFunctions)
            vm.load("post1")
            advanceUntilIdle()

            assertEquals(RepostersViewModel.PAGE_SIZE, vm.posts.value.size)
            assertEquals(40, vm.totalCount.value)
            assertTrue(vm.hasMore.value)
            assertFalse(vm.isLoading.value)

            // Second (short) page ends pagination.
            whenever(
                cloudFunctions.fetchReposters(postId = any(), pageSize = any(), beforeMs = anyOrNull())
            ).thenReturn(page(listOf(post("p99", "u99", 5_000L))))

            vm.loadMore()
            advanceUntilIdle()

            verify(cloudFunctions).fetchReposters(
                postId = eq("post1"),
                pageSize = eq(RepostersViewModel.PAGE_SIZE),
                beforeMs = eq(oldestMs),
            )
            assertEquals(RepostersViewModel.PAGE_SIZE + 1, vm.posts.value.size)
            assertFalse(vm.hasMore.value)
        }

    @Test
    fun `duplicate reposts across page boundaries are collapsed by id`() = runTest(testDispatcher) {
        val firstPage = (0 until RepostersViewModel.PAGE_SIZE).map { post("p$it", "u$it", 10_000L - it) }
        whenever(
            cloudFunctions.fetchReposters(postId = any(), pageSize = any(), beforeMs = anyOrNull())
        ).thenReturn(page(firstPage, total = 100))

        val vm = RepostersViewModel(cloudFunctions)
        vm.load("post1")
        advanceUntilIdle()

        // Next page repeats the last post id from page one plus one new one.
        val repeated = firstPage.last()
        whenever(
            cloudFunctions.fetchReposters(postId = any(), pageSize = any(), beforeMs = anyOrNull())
        ).thenReturn(page(listOf(repeated, post("pNew", "uNew", 1_000L))))

        vm.loadMore()
        advanceUntilIdle()

        // Only the genuinely new post is appended; the duplicate id is dropped.
        assertEquals(RepostersViewModel.PAGE_SIZE + 1, vm.posts.value.size)
        assertEquals(1, vm.posts.value.count { it.id == repeated.id })
    }

    @Test
    fun `load failure sets the error state and clears loading`() = runTest(testDispatcher) {
        whenever(
            cloudFunctions.fetchReposters(postId = any(), pageSize = any(), beforeMs = anyOrNull())
        ).thenThrow(RuntimeException("boom"))

        val vm = RepostersViewModel(cloudFunctions)
        vm.load("post1")
        advanceUntilIdle()

        assertTrue(vm.loadError.value)
        assertFalse(vm.isLoading.value)
        assertTrue(vm.posts.value.isEmpty())
    }
}
