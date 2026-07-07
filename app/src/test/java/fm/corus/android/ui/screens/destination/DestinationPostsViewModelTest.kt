package fm.corus.android.ui.screens.destination

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
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.util.Date

/**
 * Pagination behavior of the "Who shared {name}" screen: full pages advance a
 * beforeMs cursor taken from the oldest post; the list is deliberately NOT
 * deduped by user (full history), only by post id across page boundaries.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DestinationPostsViewModelTest {

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

    private fun page(posts: List<CymbalPost>, count: Int = posts.size) =
        CloudFunctionsDataSource.DestinationPostsPage(posts = posts, uniquePosterCount = count)

    @Test
    fun `full first page sets hasMore and loadMore passes the oldest createdAt as cursor`() =
        runTest(testDispatcher) {
            // Newest-first page of PAGE_SIZE posts; oldest is at the tail.
            val firstPage = (0 until DestinationPostsViewModel.PAGE_SIZE)
                .map { post("p$it", "u$it", 10_000L - it) }
            val oldestMs = firstPage.last().timestamp.time
            whenever(
                cloudFunctions.fetchArtistPosts(
                    artistId = any(), artistName = anyOrNull(), pageSize = any(),
                    beforeMs = anyOrNull(), postersLimit = any(), includeViewerPosts = any(),
                )
            ).thenReturn(page(firstPage, count = 40))

            val vm = DestinationPostsViewModel(cloudFunctions)
            vm.load(DestinationPostsViewModel.Kind.ARTIST, "a1", "Mount Kimbie")
            advanceUntilIdle()

            assertEquals(DestinationPostsViewModel.PAGE_SIZE, vm.posts.value.size)
            assertEquals(40, vm.uniquePosterCount.value)
            assertTrue(vm.hasMore.value)
            assertFalse(vm.isLoading.value)

            // Second (short) page ends pagination.
            whenever(
                cloudFunctions.fetchArtistPosts(
                    artistId = any(), artistName = anyOrNull(), pageSize = any(),
                    beforeMs = anyOrNull(), postersLimit = any(), includeViewerPosts = any(),
                )
            ).thenReturn(page(listOf(post("p99", "u99", 5_000L))))

            vm.loadMore()
            advanceUntilIdle()

            verify(cloudFunctions).fetchArtistPosts(
                artistId = eq("a1"),
                artistName = eq("Mount Kimbie"),
                pageSize = eq(DestinationPostsViewModel.PAGE_SIZE),
                beforeMs = eq(oldestMs),
                postersLimit = eq(1),
                includeViewerPosts = eq(false),
            )
            assertEquals(DestinationPostsViewModel.PAGE_SIZE + 1, vm.posts.value.size)
            assertFalse(vm.hasMore.value)
        }

    @Test
    fun `posts are not deduped by user - full history`() = runTest(testDispatcher) {
        val samePoster = listOf(post("p1", "u1", 3000L), post("p2", "u1", 2000L))
        whenever(
            cloudFunctions.fetchArtistPosts(
                artistId = any(), artistName = anyOrNull(), pageSize = any(),
                beforeMs = anyOrNull(), postersLimit = any(), includeViewerPosts = any(),
            )
        ).thenReturn(page(samePoster))

        val vm = DestinationPostsViewModel(cloudFunctions)
        vm.load(DestinationPostsViewModel.Kind.ARTIST, "a1", null)
        advanceUntilIdle()

        assertEquals(2, vm.posts.value.size)
        assertFalse("short page must not report more", vm.hasMore.value)
    }

    @Test
    fun `director kind routes to getDirectorPosts`() = runTest(testDispatcher) {
        whenever(
            cloudFunctions.fetchDirectorPosts(
                directorId = any(), directorName = anyOrNull(), pageSize = any(),
                beforeMs = anyOrNull(), postersLimit = any(), includeViewerPosts = any(),
            )
        ).thenReturn(page(listOf(post("p1", "u1", 1000L))))

        val vm = DestinationPostsViewModel(cloudFunctions)
        vm.load(DestinationPostsViewModel.Kind.DIRECTOR, "1032", "Martin Scorsese")
        advanceUntilIdle()

        verify(cloudFunctions).fetchDirectorPosts(
            directorId = eq("1032"),
            directorName = eq("Martin Scorsese"),
            pageSize = eq(DestinationPostsViewModel.PAGE_SIZE),
            beforeMs = anyOrNull(),
            postersLimit = eq(1),
            includeViewerPosts = eq(false),
        )
        verify(cloudFunctions, never()).fetchArtistPosts(
            artistId = any(), artistName = anyOrNull(), pageSize = any(),
            beforeMs = anyOrNull(), postersLimit = any(), includeViewerPosts = any(),
        )
        assertEquals(1, vm.posts.value.size)
    }

    @Test
    fun `load failure sets the error state and clears loading`() = runTest(testDispatcher) {
        whenever(
            cloudFunctions.fetchArtistPosts(
                artistId = any(), artistName = anyOrNull(), pageSize = any(),
                beforeMs = anyOrNull(), postersLimit = any(), includeViewerPosts = any(),
            )
        ).thenThrow(RuntimeException("boom"))

        val vm = DestinationPostsViewModel(cloudFunctions)
        vm.load(DestinationPostsViewModel.Kind.ARTIST, "a1", null)
        advanceUntilIdle()

        assertTrue(vm.loadError.value)
        assertFalse(vm.isLoading.value)
        assertTrue(vm.posts.value.isEmpty())
    }
}
