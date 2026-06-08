package fm.corus.android.ui.screens.search

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
import kotlinx.coroutines.test.advanceTimeBy
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
import org.mockito.kotlin.atLeast
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

/**
 * Tests for the cold-start poll that keeps the taste-match rail skeleton up
 * for up to ~3s when the initial getSuggestedUsers response is empty. This is
 * the load-bearing UX behavior for the "just signed up and posted 3 songs"
 * scenario where the backend's eager recompute may still be in flight when
 * the user lands on Search.
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

    private val tasteMatch = SuggestedUserMatch(
        user = CymbalUser(id = "match-1", username = "rodrigofan", displayName = "Rodrigo Fan"),
        matchData = MusicMatchData(similarityScore = 0.4, sharedPostedTracks = 2),
    )

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        firestoreDataSource = mock()
        // Default viewer has posted (cymbalCount > 0) so the cold-start poll is
        // allowed to run; the no-taste-data path is covered by its own test.
        authRepository = mock {
            on { currentUserId } doReturn "viewer"
            on { userProfile } doReturn MutableStateFlow(
                CymbalUser(id = "viewer", username = "viewer", displayName = "Viewer", cymbalCount = 3),
            )
        }
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
        networkMonitor = org.mockito.kotlin.mock {
            on { isConnected } doReturn kotlinx.coroutines.flow.MutableStateFlow(true)
        },
    )

    @Test
    fun `empty initial response triggers polling and merges late-arriving taste matches`() = runTest(testDispatcher) {
        // First call: empty (the backend's eager recompute is still in flight).
        // Subsequent calls: the recompute has finished and a taste match lands.
        whenever(cloudFunctions.getSuggestedUsers(eq("viewer")))
            .thenReturn(emptyList())
            .thenReturn(listOf(tasteMatch))

        val vm = createViewModel()
        vm.loadInitialData()
        advanceUntilIdle()

        // Initial fetch returned empty → polling should be active and no taste matches yet.
        assertTrue("polling should activate after empty initial fetch", vm.isTasteMatchPolling.value)
        assertTrue("no taste matches yet", vm.suggestedMatches.value.none { it.matchData?.hasSimilarityData == true })

        // One poll tick (750ms) — second call lands with the match.
        advanceTimeBy(800)
        advanceUntilIdle()

        assertFalse("polling stops once a taste match lands", vm.isTasteMatchPolling.value)
        assertEquals(
            "merged list should contain the late-arriving taste match",
            listOf("match-1"),
            vm.suggestedMatches.value.map { it.user.id },
        )
        verify(cloudFunctions, atLeast(2)).getSuggestedUsers(eq("viewer"))
    }

    @Test
    fun `polling stops after ~3s when no taste matches ever arrive`() = runTest(testDispatcher) {
        whenever(cloudFunctions.getSuggestedUsers(eq("viewer"))).thenReturn(emptyList())

        val vm = createViewModel()
        vm.loadInitialData()
        advanceUntilIdle()

        // Walk past the full 4 × 750ms poll window plus a small buffer.
        advanceTimeBy(3_500)
        advanceUntilIdle()

        assertFalse("polling has ended", vm.isTasteMatchPolling.value)
        assertTrue("no taste matches", vm.suggestedMatches.value.none { it.matchData?.hasSimilarityData == true })
        // Initial call + 4 poll attempts.
        verify(cloudFunctions, times(5)).getSuggestedUsers(eq("viewer"))
    }

    @Test
    fun `new user with no posts skips polling even on an empty response`() = runTest(testDispatcher) {
        // Brand-new user: no posts means no taste data can exist and no eager
        // recompute is in flight, so the poll (and its skeleton) must be skipped.
        whenever(authRepository.userProfile).thenReturn(
            MutableStateFlow(CymbalUser(id = "viewer", username = "viewer", displayName = "Viewer", cymbalCount = 0)),
        )
        whenever(cloudFunctions.getSuggestedUsers(eq("viewer"))).thenReturn(emptyList())

        val vm = createViewModel()
        vm.loadInitialData()
        advanceUntilIdle()

        assertTrue("we should know this user has no taste data", vm.currentUserHasNoTasteData.value)
        assertFalse("polling must never activate for a no-posts user", vm.isTasteMatchPolling.value)
        // Only the initial fetch — no follow-up poll attempts.
        verify(cloudFunctions, times(1)).getSuggestedUsers(eq("viewer"))
    }

    @Test
    fun `non-empty initial response skips polling entirely`() = runTest(testDispatcher) {
        whenever(cloudFunctions.getSuggestedUsers(eq("viewer"))).thenReturn(listOf(tasteMatch))

        val vm = createViewModel()
        vm.loadInitialData()
        advanceUntilIdle()

        assertFalse("polling should never activate when taste matches arrive on the first call", vm.isTasteMatchPolling.value)
        // Only the initial call — no follow-up polls.
        verify(cloudFunctions, times(1)).getSuggestedUsers(eq("viewer"))
    }
}
