package fm.corus.android.ui.screens.profile

import com.google.firebase.firestore.DocumentSnapshot
import fm.corus.android.data.model.CymbalUser
import fm.corus.android.data.remote.CloudFunctionsDataSource
import fm.corus.android.data.repository.AuthRepository
import fm.corus.android.data.repository.UserRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

/**
 * Regression coverage for follow-list search. Search must run as a scoped
 * global user search (so results are ranked like the main Users tab) and then
 * keep only the people who actually belong to this list — never a client-side
 * filter over whatever pages happen to be loaded, which used to miss anyone
 * past the first page (the "Isa" bug).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class FollowListViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    private lateinit var userRepository: UserRepository
    private lateinit var authRepository: AuthRepository
    private lateinit var cloudFunctions: CloudFunctionsDataSource

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        userRepository = mock()
        authRepository = mock()
        cloudFunctions = mock()
        whenever(authRepository.currentUserId).thenReturn("viewer-1")
        whenever(userRepository.followingIds).thenReturn(MutableStateFlow(emptySet()))
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun makeViewModel() =
        FollowListViewModel(userRepository, authRepository, cloudFunctions)

    private fun makeUser(id: String) = CymbalUser(
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
        followerCount = 0,
        followingCount = 0,
        hashtagCount = 0,
        cymbalCount = 0,
        savesCount = 0,
    )

    private suspend fun stubInitialLoad(isFollowers: Boolean) {
        // First page can be empty; search must not depend on it.
        val empty = UserRepository.PaginatedUsersResult(emptyList(), null)
        if (isFollowers) {
            whenever(userRepository.fetchFollowersPaginated(any(), any(), anyOrNull()))
                .thenReturn(empty)
        } else {
            whenever(userRepository.fetchFollowingPaginated(any(), any(), anyOrNull()))
                .thenReturn(empty)
        }
    }

    @Test
    fun `search on a following list keeps only ranked candidates the profile follows`() =
        runTest(testDispatcher) {
            stubInitialLoad(isFollowers = false)
            // Ranked global results, mirroring the main Users search order.
            val ranked = listOf(makeUser("isa"), makeUser("isaac"), makeUser("stranger"))
            whenever(userRepository.searchUsers(eq("isa"), any(), any())).thenReturn(ranked)
            // Of those, the profile only follows isa + isaac.
            whenever(userRepository.checkFollowingStatusBatch(eq("target"), any()))
                .thenReturn(setOf("isa", "isaac"))

            val vm = makeViewModel()
            vm.loadFollowList("target", FollowListMode.FOLLOWING)
            advanceUntilIdle()

            vm.search("isa")
            advanceUntilIdle()

            // "stranger" is dropped; rank order is preserved.
            assertEquals(listOf("isa", "isaac"), vm.searchResults.value.map { it.id })
            assertFalse(vm.isSearching.value)
            // Following list must check the following subcollection, not followers.
            verify(userRepository).checkFollowingStatusBatch(eq("target"), any())
            verify(userRepository, never()).checkFollowerStatusBatch(eq("target"), any())
        }

    @Test
    fun `search on a followers list checks the followers subcollection`() =
        runTest(testDispatcher) {
            stubInitialLoad(isFollowers = true)
            whenever(userRepository.searchUsers(eq("isa"), any(), any()))
                .thenReturn(listOf(makeUser("isa"), makeUser("nope")))
            whenever(userRepository.checkFollowerStatusBatch(eq("target"), any()))
                .thenReturn(setOf("isa"))

            val vm = makeViewModel()
            vm.loadFollowList("target", FollowListMode.FOLLOWERS)
            advanceUntilIdle()

            vm.search("isa")
            advanceUntilIdle()

            assertEquals(listOf("isa"), vm.searchResults.value.map { it.id })
            verify(userRepository).checkFollowerStatusBatch(eq("target"), any())
            verify(userRepository, never()).checkFollowingStatusBatch(eq("target"), any())
        }

    @Test
    fun `blank query clears results and never hits the network`() =
        runTest(testDispatcher) {
            stubInitialLoad(isFollowers = false)
            val vm = makeViewModel()
            vm.loadFollowList("target", FollowListMode.FOLLOWING)
            advanceUntilIdle()

            vm.search("   ")
            advanceUntilIdle()

            assertEquals(emptyList<String>(), vm.searchResults.value.map { it.id })
            assertFalse(vm.isSearching.value)
            verify(userRepository, never()).searchUsers(any(), any(), any())
        }

    @Test
    fun `a newer query supersedes an in-flight one`() =
        runTest(testDispatcher) {
            stubInitialLoad(isFollowers = false)
            whenever(userRepository.searchUsers(eq("isa"), any(), any()))
                .thenReturn(listOf(makeUser("isa")))
            whenever(userRepository.searchUsers(eq("isaac"), any(), any()))
                .thenReturn(listOf(makeUser("isaac")))
            whenever(userRepository.checkFollowingStatusBatch(eq("target"), any()))
                .thenReturn(setOf("isa", "isaac"))

            val vm = makeViewModel()
            vm.loadFollowList("target", FollowListMode.FOLLOWING)
            advanceUntilIdle()

            // Fire two queries back to back; the first job is cancelled by the second.
            vm.search("isa")
            vm.search("isaac")
            advanceUntilIdle()

            assertEquals(listOf("isaac"), vm.searchResults.value.map { it.id })
        }

    // ── Mutual tab ──

    @Test
    fun `mutual load surfaces users, count, and cursor from the callable`() =
        runTest(testDispatcher) {
            whenever(userRepository.fetchMutualFollowers(eq("target"), anyOrNull(), any()))
                .thenReturn(
                    CloudFunctionsDataSource.MutualFollowersPage(
                        users = listOf(makeUser("ben"), makeUser("cleo")),
                        nextCursor = "cursor-2",
                        mutualCount = 16,
                        mutualCountCapped = false,
                    )
                )

            val vm = makeViewModel()
            vm.loadFollowList("target", FollowListMode.MUTUAL)
            advanceUntilIdle()

            assertEquals(listOf("ben", "cleo"), vm.users.value.map { it.id })
            assertEquals(16, vm.mutualCount.value)
            // The viewer follows every mutual by definition.
            assertEquals(true, vm.followingStatus.value["ben"])
            assertEquals(true, vm.followingStatus.value["cleo"])
            // A non-null cursor means there's another page.
            assertEquals(true, vm.hasMore.value)
        }

    @Test
    fun `mutual search scopes to followers-of-profile intersect viewer-following`() =
        runTest(testDispatcher) {
            whenever(userRepository.fetchMutualFollowers(eq("target"), anyOrNull(), any()))
                .thenReturn(
                    CloudFunctionsDataSource.MutualFollowersPage(emptyList(), null, 5, false)
                )
            val ranked = listOf(makeUser("ava"), makeUser("ben"), makeUser("cleo"), makeUser("dan"))
            whenever(userRepository.searchUsers(eq("a"), any(), any())).thenReturn(ranked)
            // ava+ben+cleo follow the profile; ben+cleo+dan are followed by the viewer.
            whenever(userRepository.checkFollowerStatusBatch(eq("target"), any()))
                .thenReturn(setOf("ava", "ben", "cleo"))
            whenever(userRepository.checkFollowingStatusBatch(eq("viewer-1"), any()))
                .thenReturn(setOf("ben", "cleo", "dan"))

            val vm = makeViewModel()
            vm.loadFollowList("target", FollowListMode.MUTUAL)
            advanceUntilIdle()

            vm.search("a")
            advanceUntilIdle()

            // Only the intersection (ben, cleo) survives; rank order preserved.
            assertEquals(listOf("ben", "cleo"), vm.searchResults.value.map { it.id })
        }

    @Test
    fun `mutualCount is unknown until the first page loads`() {
        val vm = makeViewModel()
        // -1 sentinel lets the screen keep the tab hidden until we know it's > 0.
        assertEquals(-1, vm.mutualCount.value)
    }

    @Test
    fun `mutualResolved gates the tab strip until the first page settles`() =
        runTest(testDispatcher) {
            whenever(userRepository.fetchMutualFollowers(eq("target"), anyOrNull(), any()))
                .thenReturn(
                    CloudFunctionsDataSource.MutualFollowersPage(emptyList(), null, 0, false)
                )

            val vm = makeViewModel()
            // Before loading, the tab strip must wait (false), so it can paint
            // complete with the Mutual tab already in its leftmost slot.
            assertFalse(vm.mutualResolved.value)

            vm.loadFollowList("target", FollowListMode.MUTUAL)
            advanceUntilIdle()

            // Even with zero mutuals, resolution completes so the strip can render.
            assertEquals(true, vm.mutualResolved.value)
        }

    @Test
    fun `loadMutualCount uses the cheap repo count and resolves the tab`() =
        runTest(testDispatcher) {
            whenever(userRepository.mutualFollowerCount(eq("viewer-1"), eq("target"), any()))
                .thenReturn(UserRepository.MutualCount(16, false))

            val vm = makeViewModel()
            vm.loadMutualCount("viewer-1", "target")
            advanceUntilIdle()

            assertEquals(16, vm.mutualCount.value)
            assertEquals(true, vm.mutualResolved.value)
            // The cheap path must NOT hit the paginated backend list endpoint.
            verify(userRepository, never()).fetchMutualFollowers(any(), anyOrNull(), any())
        }

    @Test
    fun `loadMutualCount is idempotent`() =
        runTest(testDispatcher) {
            whenever(userRepository.mutualFollowerCount(any(), any(), any()))
                .thenReturn(UserRepository.MutualCount(3, false))

            val vm = makeViewModel()
            vm.loadMutualCount("viewer-1", "target")
            vm.loadMutualCount("viewer-1", "target")
            advanceUntilIdle()

            verify(userRepository).mutualFollowerCount(eq("viewer-1"), eq("target"), any())
        }

    @Test
    fun `loadFollowList is idempotent across repeat calls`() =
        runTest(testDispatcher) {
            whenever(userRepository.fetchMutualFollowers(eq("target"), anyOrNull(), any()))
                .thenReturn(
                    CloudFunctionsDataSource.MutualFollowersPage(
                        users = listOf(makeUser("ben")),
                        nextCursor = null,
                        mutualCount = 1,
                        mutualCountCapped = false,
                    )
                )

            val vm = makeViewModel()
            // The eager count load and the pager page can both call this.
            vm.loadFollowList("target", FollowListMode.MUTUAL)
            vm.loadFollowList("target", FollowListMode.MUTUAL)
            advanceUntilIdle()

            assertEquals(1, vm.mutualCount.value)
            // Only one network fetch despite two calls.
            verify(userRepository).fetchMutualFollowers(eq("target"), anyOrNull(), any())
        }
}
