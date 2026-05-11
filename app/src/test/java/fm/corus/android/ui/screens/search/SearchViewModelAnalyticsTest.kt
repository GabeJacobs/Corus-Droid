package fm.corus.android.ui.screens.search

import fm.corus.android.data.local.PreferencesDataStore
import fm.corus.android.data.model.CymbalUser
import fm.corus.android.data.remote.CloudFunctionsDataSource
import fm.corus.android.data.remote.FirestoreDataSource
import fm.corus.android.data.repository.AuthRepository
import fm.corus.android.data.repository.ExploreRepository
import fm.corus.android.data.repository.MusicSearchRepository
import fm.corus.android.data.repository.TMDBRepository
import fm.corus.android.data.repository.UserRepository
import fm.corus.android.domain.NowPlayingManager
import fm.corus.android.service.AnalyticsService
import fm.corus.android.service.RemoteConfigService
import fm.corus.android.service.SearchSection
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
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

/**
 * Verifies that SearchViewModel.toggleFollow forwards the section to
 * AnalyticsService and that helper logging methods round-trip correctly.
 *
 * The section vocabulary is the user-visible measurement on the People-search
 * page — getting it wrong means the dashboard breaks silently. Worth a unit
 * test rather than relying on QA to spot a missing event in DebugView.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SearchViewModelAnalyticsTest {

    private val testDispatcher = StandardTestDispatcher()

    private lateinit var firestoreDataSource: FirestoreDataSource
    private lateinit var authRepository: AuthRepository
    private lateinit var userRepository: UserRepository
    private lateinit var exploreRepository: ExploreRepository
    private lateinit var cloudFunctions: CloudFunctionsDataSource
    private lateinit var musicSearchRepository: MusicSearchRepository
    private lateinit var tmdbRepository: TMDBRepository
    private lateinit var preferencesDataStore: PreferencesDataStore
    private lateinit var remoteConfigService: RemoteConfigService
    private lateinit var analyticsService: AnalyticsService
    private lateinit var nowPlayingManager: NowPlayingManager

    private val sampleUser = CymbalUser(
        id = "u-1",
        username = "alice",
        displayName = "Alice",
    )

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        firestoreDataSource = mock()
        authRepository = mock { on { currentUserId } doReturn "viewer" }
        userRepository = mock {
            on { followingIds } doReturn MutableStateFlow(emptySet())
        }
        exploreRepository = mock()
        cloudFunctions = mock()
        musicSearchRepository = mock()
        tmdbRepository = mock()
        preferencesDataStore = mock {
            on { recentSearchUsers } doReturn kotlinx.coroutines.flow.flowOf(emptyList())
            on { contactsSyncStatus } doReturn kotlinx.coroutines.flow.flowOf("notAsked")
        }
        remoteConfigService = mock()
        analyticsService = mock()
        nowPlayingManager = mock()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel(): SearchViewModel = SearchViewModel(
        userRepository = userRepository,
        authRepository = authRepository,
        exploreRepository = exploreRepository,
        cloudFunctions = cloudFunctions,
        musicSearchRepository = musicSearchRepository,
        tmdbRepository = tmdbRepository,
        preferencesDataStore = preferencesDataStore,
        firestoreDataSource = firestoreDataSource,
        remoteConfigService = remoteConfigService,
        analyticsService = analyticsService,
        nowPlayingManager = nowPlayingManager,
    )

    @Test
    fun `toggleFollow with section fires followed event when previously unfollowed`() = runTest(testDispatcher) {
        val vm = createViewModel()
        vm.toggleFollow(sampleUser, SearchSection.TasteMatches)
        advanceUntilIdle()
        verify(analyticsService).logSearchSectionUserFollowed(eq(SearchSection.TasteMatches), eq("u-1"))
        verify(analyticsService, never()).logSearchSectionUserUnfollowed(any(), any())
    }

    @Test
    fun `toggleFollow with section fires unfollowed event when previously followed`() = runTest(testDispatcher) {
        // Seed the local follow set by following first, then verify the second
        // tap fires the unfollow event with the same section value.
        whenever(userRepository.followUser(any(), any())).thenReturn(Unit)
        val vm = createViewModel()
        vm.toggleFollow(sampleUser, SearchSection.Popular) // follow
        advanceUntilIdle()
        vm.toggleFollow(sampleUser, SearchSection.Popular) // unfollow
        advanceUntilIdle()
        verify(analyticsService).logSearchSectionUserFollowed(eq(SearchSection.Popular), eq("u-1"))
        verify(analyticsService).logSearchSectionUserUnfollowed(eq(SearchSection.Popular), eq("u-1"))
    }

    @Test
    fun `toggleFollow without section fires no analytics`() = runTest(testDispatcher) {
        val vm = createViewModel()
        vm.toggleFollow(sampleUser, section = null)
        advanceUntilIdle()
        verify(analyticsService, never()).logSearchSectionUserFollowed(any(), any())
        verify(analyticsService, never()).logSearchSectionUserUnfollowed(any(), any())
    }

    @Test
    fun `logSearchSectionUserTapped delegates to analytics service with correct section`() {
        val vm = createViewModel()
        vm.logSearchSectionUserTapped(SearchSection.MutualConnections, "u-2")
        verify(analyticsService).logSearchSectionUserTapped(eq(SearchSection.MutualConnections), eq("u-2"))
    }

    @Test
    fun `logSearchSectionSeeAllTapped delegates to analytics service`() {
        val vm = createViewModel()
        vm.logSearchSectionSeeAllTapped(SearchSection.NewOnCorus)
        verify(analyticsService).logSearchSectionSeeAllTapped(eq(SearchSection.NewOnCorus))
    }

    @Test
    fun `every SearchSection has the canonical wire value`() {
        // Lock the cross-platform section vocabulary in place. Changing any of
        // these values silently breaks dashboards that join across iOS, Android
        // and web, so any rename forces a deliberate test update.
        val expected = mapOf(
            SearchSection.TasteMatches to "taste_matches",
            SearchSection.MutualConnections to "mutual_connections",
            SearchSection.FriendsOnCorus to "friends_on_corus",
            SearchSection.Popular to "popular",
            SearchSection.ClubMembers to "club_members",
            SearchSection.NewOnCorus to "new_on_corus",
        )
        for ((section, wire) in expected) {
            assert(section.value == wire) {
                "SearchSection.$section.value was '${section.value}', expected '$wire'"
            }
        }
    }
}
