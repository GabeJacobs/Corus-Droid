package fm.corus.android.ui.screens.search

import fm.corus.android.data.local.PreferencesDataStore
import fm.corus.android.data.model.CymbalHashtag
import fm.corus.android.data.model.TrendingWindow
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
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

/**
 * Tests for the hashtag-search additions to SearchViewModel:
 *  - blank prefix never hits Firestore
 *  - debounce coalesces fast typing into a single backend call
 *  - results are cached so a repeat query is served from memory
 *  - trending hashtags are loaded only once across multiple tab activations
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SearchViewModelHashtagTest {

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

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        firestoreDataSource = mock()
        authRepository = mock {
            on { currentUserId } doReturn "uid1"
            // The VM's init block collects userProfile immediately; without a
            // stub the collector NPEs and poisons every test in the class.
            on { userProfile } doReturn MutableStateFlow<fm.corus.android.data.model.CymbalUser?>(null)
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
            on { trendingSongsWindow } doReturn kotlinx.coroutines.flow.emptyFlow()
            on { trendingFilmsWindow } doReturn kotlinx.coroutines.flow.emptyFlow()
            on { trendingHashtagsWindow } doReturn kotlinx.coroutines.flow.emptyFlow()
            on { trendingArtistsWindow } doReturn kotlinx.coroutines.flow.emptyFlow()
            on { trendingAlbumsWindow } doReturn kotlinx.coroutines.flow.emptyFlow()
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
    fun `hashtags songs and films all default to week window`() =
        runTest(testDispatcher) {
            // Empty pref flows == nothing stored, so each StateFlow keeps its
            // stateIn() initial value: all three seed to WEEK.
            whenever(preferencesDataStore.trendingHashtagsWindow)
                .thenReturn(kotlinx.coroutines.flow.emptyFlow())
            whenever(preferencesDataStore.trendingSongsWindow)
                .thenReturn(kotlinx.coroutines.flow.emptyFlow())
            whenever(preferencesDataStore.trendingFilmsWindow)
                .thenReturn(kotlinx.coroutines.flow.emptyFlow())
            whenever(preferencesDataStore.trendingArtistsWindow)
                .thenReturn(kotlinx.coroutines.flow.emptyFlow())
            whenever(preferencesDataStore.trendingAlbumsWindow)
                .thenReturn(kotlinx.coroutines.flow.emptyFlow())

            val vm = createViewModel()
            advanceUntilIdle()

            assertEquals(TrendingWindow.WEEK, vm.trendingHashtagsWindow.value)
            assertEquals(TrendingWindow.WEEK, vm.trendingSongsWindow.value)
            assertEquals(TrendingWindow.WEEK, vm.trendingArtistsWindow.value)
            assertEquals(TrendingWindow.WEEK, vm.trendingAlbumsWindow.value)
            assertEquals(TrendingWindow.WEEK, vm.trendingFilmsWindow.value)
        }

    @Test
    fun `blank query does not hit firestore`() = runTest(testDispatcher) {
        val vm = createViewModel()
        vm.search("", tab = 3)
        advanceUntilIdle()
        verify(firestoreDataSource, never()).searchHashtagsByPrefix(any(), any())
        assertTrue(vm.hashtagSearchResults.value.isEmpty())
    }

    @Test
    fun `whitespace-only query does not hit firestore`() = runTest(testDispatcher) {
        val vm = createViewModel()
        vm.search("   ", tab = 3)
        advanceUntilIdle()
        verify(firestoreDataSource, never()).searchHashtagsByPrefix(any(), any())
    }

    @Test
    fun `debounce coalesces fast typing into one backend call`() = runTest(testDispatcher) {
        whenever(firestoreDataSource.searchHashtagsByPrefix(any(), any()))
            .thenReturn(listOf(CymbalHashtag(id = "music", name = "music", cymbalCount = 5)))
        val vm = createViewModel()

        // Three keystrokes within the debounce window — only the last query should reach Firestore.
        vm.search("m", tab = 3)
        advanceTimeBy(50)
        vm.search("mu", tab = 3)
        advanceTimeBy(50)
        vm.search("mus", tab = 3)
        advanceUntilIdle()

        verify(firestoreDataSource, times(1)).searchHashtagsByPrefix(eq("mus"), any())
    }

    @Test
    fun `repeated query is served from cache`() = runTest(testDispatcher) {
        whenever(firestoreDataSource.searchHashtagsByPrefix(any(), any()))
            .thenReturn(listOf(CymbalHashtag(id = "music", name = "music", cymbalCount = 5)))
        val vm = createViewModel()

        vm.search("music", tab = 3)
        advanceUntilIdle()
        vm.search("music", tab = 3)
        advanceUntilIdle()

        // Backend hit exactly once; the second call comes from the in-memory cache.
        verify(firestoreDataSource, times(1)).searchHashtagsByPrefix(eq("music"), any())
        assertEquals(1, vm.hashtagSearchResults.value.size)
    }

    @Test
    fun `trending hashtags load only once across multiple tab activations`() = runTest(testDispatcher) {
        whenever(firestoreDataSource.fetchTrendingHashtagsWindowed(any(), any()))
            .thenReturn(
                listOf(
                    fm.corus.android.data.model.TrendingHashtag(
                        id = "trend",
                        rank = 1,
                        name = "trend",
                        cymbalCount = 99,
                    ),
                ),
            )
        val vm = createViewModel()

        vm.setActiveTab(3)
        advanceUntilIdle()
        vm.setActiveTab(0)
        vm.setActiveTab(3)
        advanceUntilIdle()

        verify(firestoreDataSource, times(1)).fetchTrendingHashtagsWindowed(any(), any())
        assertEquals(1, vm.trendingHashtags.value.size)
        assertFalse(vm.isTrendingHashtagsLoading.value)
    }
}
