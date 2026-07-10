package fm.corus.android.ui.screens.search

import android.util.Log
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.MockedStatic
import org.mockito.Mockito.mockStatic
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

/**
 * Construction-order regression test for [SearchViewModel].
 *
 * The `init` block launches a collector on `authRepository.userProfile` (a
 * StateFlow) that writes `_belowTasteMatchThreshold.value` on every emission.
 * A StateFlow emits its current value synchronously on first collection, and on
 * device `viewModelScope` runs on `Dispatchers.Main.immediate`, so that first
 * emission executes *during construction*. If `_belowTasteMatchThreshold` is
 * declared after the `init` block it is still null at that point, and the emit
 * NPEs — crashing the app on launch the instant the Search graph is built.
 *
 * These tests pin the field's declaration above `init` by driving the collector
 * eagerly with [UnconfinedTestDispatcher] (which, like `Main.immediate`, runs
 * the launched coroutine synchronously) and asserting construction survives.
 * A [kotlinx.coroutines.test.StandardTestDispatcher] would NOT catch this — it
 * defers the coroutine, so the synchronous first emission never fires.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SearchViewModelInitOrderTest {

    // Eager dispatcher: launched coroutines run synchronously, mirroring the
    // on-device Main.immediate behavior that surfaces the init-order bug.
    private val testDispatcher = UnconfinedTestDispatcher()

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

    private lateinit var logMock: MockedStatic<Log>

    private fun viewerWith(cymbalCount: Int) = MutableStateFlow(
        CymbalUser(id = "viewer", username = "viewer", displayName = "Viewer", cymbalCount = cymbalCount),
    )

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        logMock = mockStatic(Log::class.java)
        firestoreDataSource = mock()
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
            on { recentSearches } doReturn flowOf(emptyList())
            on { contactsSyncStatus } doReturn flowOf("notAsked")
            on { trendingSongsWindow } doReturn flowOf("week")
            on { trendingFilmsWindow } doReturn flowOf("week")
            on { trendingHashtagsWindow } doReturn flowOf("month")
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
        networkMonitor = mock {
            on { isConnected } doReturn MutableStateFlow(true)
        },
    )

    @Test
    fun `the synchronous profile collector raises no uncaught exception during construction`() {
        // On device the collector's NPE lands on the main thread's uncaught
        // handler and kills the app; in a plain unit test it lands on the test
        // thread's handler instead of failing the constructor, so capture it
        // explicitly. With the field declared after `init` this records an NPE.
        val uncaught = java.util.concurrent.CopyOnWriteArrayList<Throwable>()
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { _, e -> uncaught.add(e) }
        try {
            createViewModel()
        } finally {
            Thread.setDefaultUncaughtExceptionHandler(previous)
        }
        assertTrue(
            "construction must not raise an uncaught exception, got: $uncaught",
            uncaught.isEmpty(),
        )
    }

    @Test
    fun `a below-threshold viewer constructs and the gate flips on synchronously`() {
        whenever(authRepository.userProfile).thenReturn(viewerWith(1))

        val vm = createViewModel()

        // Only reachable if `_belowTasteMatchThreshold` is initialized before the
        // init-block collector fires; a null field would have NPE'd the write and
        // left this false.
        assertTrue("a 1-post viewer is below the taste-match threshold", vm.belowTasteMatchThreshold.value)
    }
}
