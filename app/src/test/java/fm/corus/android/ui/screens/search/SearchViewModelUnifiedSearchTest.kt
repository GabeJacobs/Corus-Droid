package fm.corus.android.ui.screens.search

import android.util.Log
import fm.corus.android.data.local.PreferencesDataStore
import fm.corus.android.data.model.CymbalHashtag
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
import fm.corus.android.service.NetworkMonitor
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
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

/**
 * Unified search (`unified_search_enabled`) tests for SearchViewModel:
 *  - flag OFF: search() dispatches exactly one vertical per tab (classic path)
 *  - flag ON + ALL filter: one debounce fans out to all four verticals
 *  - one vertical failing never blanks the others or raises the error state
 *  - chip switch on the same query reuses served results (no refetch)
 *  - narrowing to an unserved vertical fetches only that vertical
 *  - clearing the query resets the filter to ALL and empties results
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SearchViewModelUnifiedSearchTest {

    private val testDispatcher = StandardTestDispatcher()

    private lateinit var logMock: MockedStatic<Log>
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
    private lateinit var networkMonitor: NetworkMonitor

    private val user = CymbalUser(id = "u1", username = "gabe", displayName = "Gabe")
    private val hashtag = CymbalHashtag(id = "jazz", name = "jazz", cymbalCount = 3)
    private val emptyPage = MusicSearchRepository.Page(tracks = emptyList(), hasMore = false)

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        logMock = mockStatic(Log::class.java)
        firestoreDataSource = mock()
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
            on { recentSearches } doReturn kotlinx.coroutines.flow.flowOf(emptyList())
            on { contactsSyncStatus } doReturn kotlinx.coroutines.flow.flowOf("notAsked")
            // The trending-window StateFlows are built from these at
            // construction; a null flow NPEs inside stateIn's transform.
            on { trendingSongsWindow } doReturn kotlinx.coroutines.flow.emptyFlow()
            on { trendingFilmsWindow } doReturn kotlinx.coroutines.flow.emptyFlow()
            on { trendingHashtagsWindow } doReturn kotlinx.coroutines.flow.emptyFlow()
        }
        remoteConfigService = mock()
        analyticsService = mock()
        nowPlayingManager = mock()
        networkMonitor = mock {
            on { isConnected } doReturn MutableStateFlow(true)
        }
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
        networkMonitor = networkMonitor,
    )

    private suspend fun stubHappyBackends() {
        whenever(userRepository.searchUsers(any(), any(), any())).thenReturn(listOf(user))
        whenever(
            musicSearchRepository.search(
                any(), any(), any(), any(), any(), any(), any(), any(),
            ),
        ).thenReturn(emptyPage)
        whenever(tmdbRepository.searchMovies(any(), any())).thenReturn(emptyList())
        whenever(tmdbRepository.prefetchDirectors(any())).thenAnswer { it.arguments[0] }
        whenever(firestoreDataSource.searchHashtagsByPrefix(any(), any())).thenReturn(listOf(hashtag))
    }

    @Test
    fun `flag off - search hits only the active tab's vertical`() = runTest(testDispatcher) {
        // remoteConfigService mock defaults unifiedSearchEnabled to false.
        stubHappyBackends()
        val vm = createViewModel()

        vm.onSearchQueryChange("gabe")
        vm.search("gabe", 0)
        advanceUntilIdle()

        verify(userRepository, times(1)).searchUsers(any(), any(), any())
        verify(musicSearchRepository, never()).search(any(), any(), any(), any(), any(), any(), any(), any())
        verify(tmdbRepository, never()).searchMovies(any(), any())
        verify(firestoreDataSource, never()).searchHashtagsByPrefix(any(), any())
        assertEquals(listOf(user), vm.userSearchResults.value)
    }

    @Test
    fun `flag on - ALL fans out to all four verticals once`() = runTest(testDispatcher) {
        whenever(remoteConfigService.unifiedSearchEnabled).thenReturn(true)
        stubHappyBackends()
        val vm = createViewModel()

        vm.onSearchQueryChange("jazz")
        vm.search("jazz", 0)
        advanceUntilIdle()

        verify(userRepository, times(1)).searchUsers(any(), any(), any())
        verify(musicSearchRepository, times(1)).search(any(), any(), any(), any(), any(), any(), any(), any())
        verify(tmdbRepository, times(1)).searchMovies(any(), any())
        verify(firestoreDataSource, times(1)).searchHashtagsByPrefix(any(), any())
        assertEquals(listOf(user), vm.userSearchResults.value)
        assertEquals(listOf(hashtag), vm.hashtagSearchResults.value)
        assertFalse(vm.isSearching.value)
    }

    @Test
    fun `flag on - one vertical failing keeps the others and no error state`() =
        runTest(testDispatcher) {
            whenever(remoteConfigService.unifiedSearchEnabled).thenReturn(true)
            stubHappyBackends()
            whenever(musicSearchRepository.search(any(), any(), any(), any(), any(), any(), any(), any()))
                .thenThrow(RuntimeException("song search down"))
            val vm = createViewModel()

            vm.onSearchQueryChange("jazz")
            vm.search("jazz", 0)
            advanceUntilIdle()

            assertEquals(listOf(user), vm.userSearchResults.value)
            assertEquals(listOf(hashtag), vm.hashtagSearchResults.value)
            // Other verticals have results, so the all-empty error state must not show.
            assertFalse(vm.searchHasError.value)
            assertFalse(vm.isSearching.value)
        }

    @Test
    fun `flag on - all verticals failing raises the error state`() = runTest(testDispatcher) {
        whenever(remoteConfigService.unifiedSearchEnabled).thenReturn(true)
        whenever(userRepository.searchUsers(any(), any(), any()))
            .thenThrow(RuntimeException("down"))
        whenever(musicSearchRepository.search(any(), any(), any(), any(), any(), any(), any(), any()))
            .thenThrow(RuntimeException("down"))
        whenever(tmdbRepository.searchMovies(any(), any()))
            .thenThrow(RuntimeException("down"))
        whenever(firestoreDataSource.searchHashtagsByPrefix(any(), any()))
            .thenThrow(RuntimeException("down"))
        val vm = createViewModel()

        vm.onSearchQueryChange("jazz")
        vm.search("jazz", 0)
        advanceUntilIdle()

        assertTrue(vm.searchHasError.value)
        assertFalse(vm.isSearching.value)
    }

    @Test
    fun `flag on - chip switch on the same query does not refetch`() = runTest(testDispatcher) {
        whenever(remoteConfigService.unifiedSearchEnabled).thenReturn(true)
        stubHappyBackends()
        val vm = createViewModel()

        vm.onSearchQueryChange("jazz")
        vm.search("jazz", 0)
        advanceUntilIdle()

        // ALL already served "jazz" for every vertical: narrowing must not refetch.
        vm.setUnifiedFilter(UnifiedSearchFilter.HASHTAGS)
        advanceUntilIdle()

        verify(firestoreDataSource, times(1)).searchHashtagsByPrefix(any(), any())
        verify(userRepository, times(1)).searchUsers(any(), any(), any())
        assertEquals(UnifiedSearchFilter.HASHTAGS, vm.unifiedFilter.value)
    }

    @Test
    fun `flag on - narrowing to an unserved vertical fetches only that vertical`() =
        runTest(testDispatcher) {
            whenever(remoteConfigService.unifiedSearchEnabled).thenReturn(true)
            stubHappyBackends()
            val vm = createViewModel()

            // Start narrowed: only users has been served.
            vm.setUnifiedFilter(UnifiedSearchFilter.USERS)
            vm.onSearchQueryChange("jazz")
            vm.search("jazz", 0)
            advanceUntilIdle()
            verify(userRepository, times(1)).searchUsers(any(), any(), any())
            verify(firestoreDataSource, never()).searchHashtagsByPrefix(any(), any())

            // Switch to hashtags: fetches hashtags, does not refetch users.
            vm.setUnifiedFilter(UnifiedSearchFilter.HASHTAGS)
            advanceUntilIdle()
            verify(firestoreDataSource, times(1)).searchHashtagsByPrefix(any(), any())
            verify(userRepository, times(1)).searchUsers(any(), any(), any())
        }

    @Test
    fun `clearing the query resets the filter to ALL and empties results`() =
        runTest(testDispatcher) {
            whenever(remoteConfigService.unifiedSearchEnabled).thenReturn(true)
            stubHappyBackends()
            val vm = createViewModel()

            vm.onSearchQueryChange("jazz")
            vm.search("jazz", 0)
            advanceUntilIdle()
            vm.setUnifiedFilter(UnifiedSearchFilter.USERS)
            advanceUntilIdle()

            vm.onSearchQueryChange("")
            advanceUntilIdle()

            assertEquals(UnifiedSearchFilter.ALL, vm.unifiedFilter.value)
            assertTrue(vm.userSearchResults.value.isEmpty())
            assertTrue(vm.hashtagSearchResults.value.isEmpty())
            assertFalse(vm.isSearching.value)
        }
}
