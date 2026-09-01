package fm.corus.android.ui.components

import fm.corus.android.data.model.CymbalUser
import fm.corus.android.data.repository.AuthRepository
import fm.corus.android.data.repository.PostRepository
import fm.corus.android.data.repository.UserRepository
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
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

/**
 * Unit tests for the paginated popular-users rail VM.
 *
 * The rail replaces the old curated music/film bot grids on the search root,
 * onboarding, and feed empty states. These tests exercise the load-more cursor
 * machinery — the per-user post fetches that synthesize TasteMatchCard album
 * art are stubbed to empty here so the post repository doesn't need to mock
 * the full CymbalPost model.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class HorizontalPopularUsersRailViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    private lateinit var userRepository: UserRepository
    private lateinit var postRepository: PostRepository
    private lateinit var authRepository: AuthRepository

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        userRepository = mock()
        postRepository = mock()
        authRepository = mock()
        whenever(authRepository.currentUserId).thenReturn("viewer-1")
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun makeViewModel() =
        PopularUsersRailViewModel(userRepository, postRepository, authRepository)

    private fun makeUser(id: String, followers: Int = 100) = CymbalUser(
        id = id,
        username = id,
        displayName = id,
        avatarURL = null,
        avatarThumbURL = null,
        bio = "",
        website = null,
        isVerified = false,
        isClubMember = false,
        isBot = false,
        botType = null,
        followerCount = followers,
        followingCount = 0,
        hashtagCount = 0,
        cymbalCount = 1,
        savesCount = 0,
    )

    private suspend fun stubPostsEmpty() {
        whenever(postRepository.getProfilePosts(any(), any(), any(), anyOrNull(), anyOrNull()))
            .thenReturn(emptyList())
    }

    @Test
    fun `isLoading starts true so the first Search frame reserves skeleton height`() {
        val vm = makeViewModel()
        assertTrue(vm.isLoading.value)
        assertTrue(vm.matches.value.isEmpty())
    }

    @Test
    fun `loadInitial fetches first page and populates matches`() = runTest(testDispatcher) {
        stubPostsEmpty()
        val firstPage = listOf(makeUser("a"), makeUser("b"))
        whenever(userRepository.fetchPopularUsersPaginated(any(), any(), eq(null)))
            .thenReturn(firstPage)

        val vm = makeViewModel()
        vm.loadInitial(emptySet())
        advanceUntilIdle()

        assertEquals(listOf("a", "b"), vm.matches.value.map { it.user.id })
        assertFalse(vm.isLoading.value)
        assertFalse(vm.endReached.value)
    }

    @Test
    fun `loadInitial is idempotent — second call does not re-fetch`() = runTest(testDispatcher) {
        stubPostsEmpty()
        whenever(userRepository.fetchPopularUsersPaginated(any(), any(), anyOrNull()))
            .thenReturn(listOf(makeUser("a")))

        val vm = makeViewModel()
        vm.loadInitial(emptySet())
        advanceUntilIdle()
        vm.loadInitial(emptySet())
        advanceUntilIdle()

        verify(userRepository, times(1)).fetchPopularUsersPaginated(any(), any(), anyOrNull())
    }

    @Test
    fun `loadInitial re-fetches when excludeIds changes so the filter toggle refreshes the rail`() =
        runTest(testDispatcher) {
            stubPostsEmpty()
            whenever(userRepository.fetchPopularUsersPaginated(any(), any(), anyOrNull()))
                .thenReturn(listOf(makeUser("a")))

            val vm = makeViewModel()
            vm.loadInitial(emptySet())
            advanceUntilIdle()
            // "Unfollowed" filter flips on (or the following set finishes loading):
            // a different exclusion set must drop the old page and refetch.
            vm.loadInitial(setOf("followed-1"))
            advanceUntilIdle()

            verify(userRepository, times(2)).fetchPopularUsersPaginated(any(), any(), anyOrNull())
            assertEquals(listOf("a"), vm.matches.value.map { it.user.id })
        }

    @Test
    fun `loadMore advances cursor to last user's id and appends results`() = runTest(testDispatcher) {
        stubPostsEmpty()
        val page1 = listOf(makeUser("a"), makeUser("b"))
        val page2 = listOf(makeUser("c"), makeUser("d"))
        whenever(userRepository.fetchPopularUsersPaginated(any(), any(), eq(null)))
            .thenReturn(page1)
        whenever(userRepository.fetchPopularUsersPaginated(any(), any(), eq("b")))
            .thenReturn(page2)

        val vm = makeViewModel()
        vm.loadInitial(emptySet())
        advanceUntilIdle()
        vm.loadMore(emptySet())
        advanceUntilIdle()

        assertEquals(listOf("a", "b", "c", "d"), vm.matches.value.map { it.user.id })
        verify(userRepository).fetchPopularUsersPaginated(any(), any(), eq("b"))
    }

    @Test
    fun `loadMore stops paginating when an empty page comes back`() = runTest(testDispatcher) {
        stubPostsEmpty()
        whenever(userRepository.fetchPopularUsersPaginated(any(), any(), eq(null)))
            .thenReturn(listOf(makeUser("a")))
        whenever(userRepository.fetchPopularUsersPaginated(any(), any(), eq("a")))
            .thenReturn(emptyList())

        val vm = makeViewModel()
        vm.loadInitial(emptySet())
        advanceUntilIdle()
        vm.loadMore(emptySet())
        advanceUntilIdle()

        assertTrue(vm.endReached.value)

        // Once endReached is set, further loadMore calls must be no-ops so we
        // don't burn Firestore reads scrolling past the end of the rail.
        vm.loadMore(emptySet())
        advanceUntilIdle()
        verify(userRepository, times(2)).fetchPopularUsersPaginated(any(), any(), anyOrNull())
    }

    @Test
    fun `loadMore deduplicates users that overlap between pages`() = runTest(testDispatcher) {
        stubPostsEmpty()
        whenever(userRepository.fetchPopularUsersPaginated(any(), any(), eq(null)))
            .thenReturn(listOf(makeUser("a"), makeUser("b")))
        whenever(userRepository.fetchPopularUsersPaginated(any(), any(), eq("b")))
            .thenReturn(listOf(makeUser("b"), makeUser("c"))) // "b" repeats

        val vm = makeViewModel()
        vm.loadInitial(emptySet())
        advanceUntilIdle()
        vm.loadMore(emptySet())
        advanceUntilIdle()

        assertEquals(listOf("a", "b", "c"), vm.matches.value.map { it.user.id })
    }

    @Test
    fun `viewer id is added to excludeIds so the rail never shows the current user`() =
        runTest(testDispatcher) {
            stubPostsEmpty()
            whenever(userRepository.fetchPopularUsersPaginated(any(), any(), anyOrNull()))
                .thenReturn(emptyList())

            val vm = makeViewModel()
            vm.loadInitial(setOf("already-followed"))
            advanceUntilIdle()

            verify(userRepository).fetchPopularUsersPaginated(
                limit = any(),
                excludeIds = org.mockito.kotlin.argThat { contains("viewer-1") && contains("already-followed") },
                afterDocId = anyOrNull(),
            )
        }

    @Test
    fun `reset clears state so the rail can be reloaded after sign-out`() = runTest(testDispatcher) {
        stubPostsEmpty()
        whenever(userRepository.fetchPopularUsersPaginated(any(), any(), anyOrNull()))
            .thenReturn(listOf(makeUser("a")))

        val vm = makeViewModel()
        vm.loadInitial(emptySet())
        advanceUntilIdle()
        assertEquals(1, vm.matches.value.size)

        vm.reset()
        assertTrue(vm.matches.value.isEmpty())
        assertTrue(vm.isLoading.value)
        assertFalse(vm.endReached.value)

        // After reset, loadInitial fires again — proving the idempotency latch is also reset.
        vm.loadInitial(emptySet())
        advanceUntilIdle()
        assertEquals(1, vm.matches.value.size)
    }
}
