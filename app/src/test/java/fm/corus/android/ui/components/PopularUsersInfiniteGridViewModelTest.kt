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
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

/**
 * Unit tests for the vertical-grid VM used on onboarding-style empty states
 * (notably the feed empty state, parity with iOS PopularUsersInfiniteGrid).
 *
 * The fetch + cursor + de-dup logic mirrors the horizontal rail VM; these
 * tests exist so a regression in the grid path doesn't pass silently because
 * only the rail is covered.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PopularUsersInfiniteGridViewModelTest {

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
        PopularUsersInfiniteGridViewModel(userRepository, postRepository, authRepository)

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
    fun `loadInitial fetches first page and populates matches`() = runTest(testDispatcher) {
        stubPostsEmpty()
        whenever(userRepository.fetchPopularUsersPaginated(any(), any(), eq(null)))
            .thenReturn(listOf(makeUser("a"), makeUser("b")))

        val vm = makeViewModel()
        vm.loadInitial(emptySet())
        advanceUntilIdle()

        assertEquals(listOf("a", "b"), vm.matches.value.map { it.user.id })
        assertFalse(vm.isLoading.value)
        assertFalse(vm.endReached.value)
    }

    @Test
    fun `loadMore advances cursor to last user's id and appends results`() = runTest(testDispatcher) {
        stubPostsEmpty()
        whenever(userRepository.fetchPopularUsersPaginated(any(), any(), eq(null)))
            .thenReturn(listOf(makeUser("a"), makeUser("b")))
        whenever(userRepository.fetchPopularUsersPaginated(any(), any(), eq("b")))
            .thenReturn(listOf(makeUser("c"), makeUser("d")))

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
            .thenReturn(listOf(makeUser("b"), makeUser("c")))

        val vm = makeViewModel()
        vm.loadInitial(emptySet())
        advanceUntilIdle()
        vm.loadMore(emptySet())
        advanceUntilIdle()

        assertEquals(listOf("a", "b", "c"), vm.matches.value.map { it.user.id })
    }

    @Test
    fun `viewer id is added to excludeIds so the grid never shows the current user`() =
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
}
