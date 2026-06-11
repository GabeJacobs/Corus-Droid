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
 * Regression coverage for follow-list search. Search filters the loaded list
 * client-side, and normal scroll-pagination is gated behind a blank query — so
 * a query like "Isa" used to miss anyone past the first loaded page. The fix
 * eagerly loads every remaining page on the first non-blank query
 * ([FollowListViewModel.loadAllRemaining]); these tests pin that behaviour.
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

    /** A page of [count] users with ids prefixed by [tag] to keep them unique. */
    private fun page(tag: String, count: Int) =
        UserRepository.PaginatedUsersResult(
            users = (0 until count).map { makeUser("$tag-$it") },
            lastDocument = mock<DocumentSnapshot>(),
        )

    @Test
    fun `loadAllRemaining keeps paginating until a short page ends the list`() =
        runTest(testDispatcher) {
            // PAGE_SIZE is 20: two full pages keep hasMore=true, a 5-user page stops it.
            whenever(userRepository.fetchFollowingPaginated(eq("target"), eq(20), anyOrNull()))
                .thenReturn(
                    page("p1", 20), // consumed by loadFollowList
                    page("p2", 20), // loadAllRemaining, still full
                    page("p3", 5),  // loadAllRemaining, short -> stop
                )

            val vm = makeViewModel()
            vm.loadFollowList("target", isFollowers = false)
            advanceUntilIdle()
            assertEquals(20, vm.users.value.size)
            assertTrue(vm.hasMore.value)

            vm.loadAllRemaining()
            advanceUntilIdle()

            assertEquals(45, vm.users.value.size)
            assertFalse(vm.hasMore.value)
            assertFalse(vm.isLoadingAll.value)
            // 1 initial + 2 exhaustive fetches.
            verify(userRepository, times(3))
                .fetchFollowingPaginated(eq("target"), eq(20), anyOrNull())
        }

    @Test
    fun `loadAllRemaining is a no-op once the whole list is already loaded`() =
        runTest(testDispatcher) {
            // A short first page means everyone is already loaded; hasMore is false.
            whenever(userRepository.fetchFollowingPaginated(eq("target"), eq(20), anyOrNull()))
                .thenReturn(page("only", 3))

            val vm = makeViewModel()
            vm.loadFollowList("target", isFollowers = false)
            advanceUntilIdle()
            assertFalse(vm.hasMore.value)

            vm.loadAllRemaining()
            advanceUntilIdle()

            assertEquals(3, vm.users.value.size)
            // No extra fetches beyond the initial load.
            verify(userRepository, times(1))
                .fetchFollowingPaginated(eq("target"), eq(20), anyOrNull())
        }

    @Test
    fun `loadAllRemaining exhausts the followers path too`() =
        runTest(testDispatcher) {
            whenever(userRepository.fetchFollowersPaginated(eq("target"), eq(20), anyOrNull()))
                .thenReturn(page("f1", 20), page("f2", 2))

            val vm = makeViewModel()
            vm.loadFollowList("target", isFollowers = true)
            advanceUntilIdle()

            vm.loadAllRemaining()
            advanceUntilIdle()

            assertEquals(22, vm.users.value.size)
            assertFalse(vm.hasMore.value)
            verify(userRepository, times(2))
                .fetchFollowersPaginated(eq("target"), eq(20), anyOrNull())
        }
}
