package fm.corus.android.ui.screens.search

import androidx.lifecycle.SavedStateHandle
import fm.corus.android.data.model.CymbalUser
import fm.corus.android.data.model.SuggestedUserMatch
import fm.corus.android.data.remote.FirestoreDataSource
import fm.corus.android.data.repository.AuthRepository
import fm.corus.android.data.repository.PostRepository
import fm.corus.android.data.repository.UserRepository
import fm.corus.android.service.AnalyticsService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

/**
 * Regression test for the frozen pull-to-refresh indicator on the Taste
 * Matches screen.
 *
 * `getSuggestedUsers` (the tasteMatches source) is backed by a 4-hour in-memory
 * cache. When `refresh()` read through that cache, the call returned
 * synchronously with no network suspension, so `isRefreshing` flipped
 * true→false within a single frame and Material3's PullToRefreshBox indicator
 * froze at the pulled position instead of retracting. Forcing a real fetch
 * keeps the spinner alive long enough to settle and retract — and gives the
 * user genuinely fresh matches. This guards that refresh() passes
 * forceRefresh = true.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(application = android.app.Application::class)
class SuggestedUsersListViewModelRefreshTest {

    private val testDispatcher = StandardTestDispatcher()

    private lateinit var userRepository: UserRepository
    private lateinit var authRepository: AuthRepository
    private lateinit var firestoreDataSource: FirestoreDataSource
    private lateinit var postRepository: PostRepository
    private lateinit var analyticsService: AnalyticsService

    private val match = SuggestedUserMatch(
        user = CymbalUser(id = "match-1", username = "rodrigofan", displayName = "Rodrigo Fan"),
    )

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        authRepository = mock {
            on { currentUserId } doReturn "viewer"
        }
        userRepository = mock {
            on { followingIds } doReturn MutableStateFlow(emptySet())
        }
        firestoreDataSource = mock()
        postRepository = mock()
        analyticsService = mock()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel(): SuggestedUsersListViewModel = SuggestedUsersListViewModel(
        userRepository = userRepository,
        authRepository = authRepository,
        firestoreDataSource = firestoreDataSource,
        postRepository = postRepository,
        analyticsService = analyticsService,
        savedStateHandle = SavedStateHandle(
            mapOf(
                "title" to "Taste Matches",
                "useRowLayout" to false,
                "source" to "tasteMatches",
            ),
        ),
    )

    @Test
    fun `refresh force-refreshes suggested users to avoid the cached instant-return freeze`() =
        runTest(testDispatcher) {
            whenever(userRepository.getSuggestedUsers(any(), any())).thenReturn(listOf(match))

            val vm = createViewModel()
            advanceUntilIdle() // let init's initial load settle

            vm.refresh()
            advanceUntilIdle()

            // The pull-to-refresh path must bypass the 4-hour cache.
            verify(userRepository).getSuggestedUsers(eq("viewer"), eq(true))
        }
}
