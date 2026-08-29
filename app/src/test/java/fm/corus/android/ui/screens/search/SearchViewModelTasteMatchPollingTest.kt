package fm.corus.android.ui.screens.search

import android.util.Log
import fm.corus.android.data.local.PreferencesDataStore
import fm.corus.android.data.model.CymbalUser
import fm.corus.android.data.model.MusicMatchData
import fm.corus.android.data.model.SuggestedUserMatch
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
import org.mockito.MockedStatic
import org.mockito.Mockito.mockStatic
import org.mockito.kotlin.any
import org.mockito.kotlin.atLeast
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

/**
 * Tests for the Search taste-match gate + cold-start poll.
 *
 * The gate: a viewer below [SearchViewModel.TASTE_MATCH_MIN_POSTS] posts can't
 * have taste matches (those need >=3 shared artists), so we skip the expensive
 * suggest scan, its cold-start poll, and the loading skeleton entirely — they
 * see the explainer immediately instead of a multi-second shimmer.
 *
 * The poll: once a viewer clears the threshold, an empty initial response keeps
 * the skeleton up for ~3s in case the backend's eager recompute (fired by a
 * recent post) is still in flight.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SearchViewModelTasteMatchPollingTest {

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

    // android.util.Log is a stub in JVM unit tests; loadInitialData's fan-out
    // (loadNewUsers etc.) logs in its catch blocks, so mock it or those throw.
    private lateinit var logMock: MockedStatic<Log>

    // A real taste match = >=3 distinct shared artists (the names the card lists).
    private val tasteMatch = SuggestedUserMatch(
        user = CymbalUser(id = "match-1", username = "rodrigofan", displayName = "Rodrigo Fan"),
        matchData = MusicMatchData(
            similarityScore = 0.4,
            sharedArtistNames = listOf("Caetano Veloso", "Gilberto Gil", "Os Mutantes"),
        ),
    )

    private fun viewerWith(cymbalCount: Int) = MutableStateFlow(
        CymbalUser(id = "viewer", username = "viewer", displayName = "Viewer", cymbalCount = cymbalCount),
    )

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        logMock = mockStatic(Log::class.java)
        firestoreDataSource = mock()
        // Default viewer clears the post threshold, so the fetch + cold-start poll
        // run; the below-threshold path is covered by its own tests.
        authRepository = mock {
            on { currentUserId } doReturn "viewer"
            on { userProfile } doReturn viewerWith(3)
        }
        userRepository = mock {
            on { followingIds } doReturn MutableStateFlow(emptySet())
        }
        exploreRepository = mock()
        cloudFunctions = mock()
        musicSearchRepository = mock()
        tmdbRepository = mock()
        preferencesDataStore = mock {
            on { recentSearches } doReturn kotlinx.coroutines.flow.flowOf(emptyList())
            on { contactsSyncStatus } doReturn kotlinx.coroutines.flow.flowOf("notAsked")
            // The VM eagerly maps these window prefs at construction; leaving them
            // unstubbed (null) NPEs the eager stateIn collector before any test runs.
            on { trendingSongsWindow } doReturn kotlinx.coroutines.flow.flowOf("week")
            on { trendingFilmsWindow } doReturn kotlinx.coroutines.flow.flowOf("week")
            on { trendingHashtagsWindow } doReturn kotlinx.coroutines.flow.flowOf("month")
            on { trendingArtistsWindow } doReturn kotlinx.coroutines.flow.flowOf("week")
            on { trendingAlbumsWindow } doReturn kotlinx.coroutines.flow.flowOf("week")
            on { trendingDirectorsWindow } doReturn kotlinx.coroutines.flow.flowOf("week")
        }
        remoteConfigService = mock()
        analyticsService = mock()
        nowPlayingManager = mock()
    }

    @After
    fun tearDown() {
        logMock.close()
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
        networkMonitor = org.mockito.kotlin.mock {
            on { isConnected } doReturn kotlinx.coroutines.flow.MutableStateFlow(true)
        },
    )

    @Test
    fun `an empty initial response polls and merges a late-arriving taste match`() = runTest(testDispatcher) {
        // First call: empty (the backend's eager recompute is still in flight).
        // Second call (poll): the recompute has finished and a taste match lands.
        whenever(userRepository.getSuggestedUsers(eq("viewer"), any()))
            .thenReturn(emptyList())
            .thenReturn(listOf(tasteMatch))

        val vm = createViewModel()
        vm.loadInitialData()
        advanceUntilIdle()

        assertFalse("polling stops once a taste match lands", vm.isTasteMatchPolling.value)
        assertEquals(
            "merged list should contain the late-arriving taste match",
            listOf("match-1"),
            vm.suggestedMatches.value.map { it.user.id },
        )
        verify(userRepository, atLeast(2)).getSuggestedUsers(eq("viewer"), any())
    }

    @Test
    fun `polling runs the full window then stops when no taste matches ever arrive`() = runTest(testDispatcher) {
        whenever(userRepository.getSuggestedUsers(eq("viewer"), any())).thenReturn(emptyList())

        val vm = createViewModel()
        vm.loadInitialData()
        advanceUntilIdle()

        assertFalse("polling has ended", vm.isTasteMatchPolling.value)
        assertTrue("no taste matches", vm.suggestedMatches.value.none { it.isTasteMatch })
        // Initial call + 4 poll attempts.
        verify(userRepository, times(5)).getSuggestedUsers(eq("viewer"), any())
    }

    @Test
    fun `non-empty initial response skips polling entirely`() = runTest(testDispatcher) {
        whenever(userRepository.getSuggestedUsers(eq("viewer"), any())).thenReturn(listOf(tasteMatch))

        val vm = createViewModel()
        vm.loadInitialData()
        advanceUntilIdle()

        assertFalse("polling should never activate when taste matches arrive on the first call", vm.isTasteMatchPolling.value)
        // Only the initial call — no follow-up polls.
        verify(userRepository, times(1)).getSuggestedUsers(eq("viewer"), any())
    }

    @Test
    fun `a zero-post viewer skips the fetch, the poll, and the skeleton gate`() = runTest(testDispatcher) {
        whenever(authRepository.userProfile).thenReturn(viewerWith(0))
        whenever(userRepository.getSuggestedUsers(eq("viewer"), any())).thenReturn(emptyList())

        val vm = createViewModel()
        vm.loadInitialData()
        advanceUntilIdle()

        assertTrue("below-threshold gate drives the skeleton off", vm.belowTasteMatchThreshold.value)
        assertFalse("polling must never activate below the threshold", vm.isTasteMatchPolling.value)
        // The scan is skipped entirely — no taste matches can exist yet.
        verify(userRepository, never()).getSuggestedUsers(eq("viewer"), any())
    }

    @Test
    fun `a one-post viewer skips the fetch, the poll, and the skeleton gate`() = runTest(testDispatcher) {
        // The exact case from the report: one post, five seconds of skeleton, then
        // an empty state. Below the 2-post threshold we skip straight to the empty
        // state with no fetch and no shimmer.
        whenever(authRepository.userProfile).thenReturn(viewerWith(1))
        whenever(userRepository.getSuggestedUsers(eq("viewer"), any())).thenReturn(emptyList())

        val vm = createViewModel()
        vm.loadInitialData()
        advanceUntilIdle()

        assertTrue("below-threshold gate drives the skeleton off", vm.belowTasteMatchThreshold.value)
        assertFalse("polling must never activate below the threshold", vm.isTasteMatchPolling.value)
        verify(userRepository, never()).getSuggestedUsers(eq("viewer"), any())
    }

    @Test
    fun `a viewer exactly at the post threshold fetches taste matches`() = runTest(testDispatcher) {
        whenever(authRepository.userProfile).thenReturn(viewerWith(SearchViewModel.TASTE_MATCH_MIN_POSTS))
        whenever(userRepository.getSuggestedUsers(eq("viewer"), any())).thenReturn(listOf(tasteMatch))

        val vm = createViewModel()
        vm.loadInitialData()
        advanceUntilIdle()

        assertFalse("at the threshold the gate is off — normal loading", vm.belowTasteMatchThreshold.value)
        assertTrue("taste matches load for a viewer at the threshold", vm.suggestedMatches.value.any { it.isTasteMatch })
        verify(userRepository, atLeast(1)).getSuggestedUsers(eq("viewer"), any())
    }

    @Test
    fun `a zero-post viewer WITH a quiz seed still fetches taste matches`() = runTest(testDispatcher) {
        // Onboarding-quiz picks (users_v2.tasteSeedCount > 0) mean the backend
        // can rank matches from the seed before the first post — the post-count
        // pre-gate must not suppress the fetch for them.
        whenever(authRepository.userProfile).thenReturn(
            MutableStateFlow(
                CymbalUser(
                    id = "viewer", username = "viewer", displayName = "Viewer",
                    cymbalCount = 0, tasteSeedCount = 5,
                ),
            ),
        )
        whenever(userRepository.getSuggestedUsers(eq("viewer"), any())).thenReturn(listOf(tasteMatch))

        val vm = createViewModel()
        vm.loadInitialData()
        advanceUntilIdle()

        assertFalse("a quiz seed bypasses the post-count gate", vm.belowTasteMatchThreshold.value)
        assertTrue("seed-based taste matches load", vm.suggestedMatches.value.any { it.isTasteMatch })
        verify(userRepository, atLeast(1)).getSuggestedUsers(eq("viewer"), any())
    }
}
