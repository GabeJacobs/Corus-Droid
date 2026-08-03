package fm.corus.android.ui.screens.compose

import fm.corus.android.data.local.PreferencesDataStore
import fm.corus.android.data.model.CymbalMovie
import fm.corus.android.data.model.CymbalPost
import fm.corus.android.data.model.CymbalTrack
import fm.corus.android.data.model.CymbalUser
import fm.corus.android.data.model.MediaType
import fm.corus.android.data.model.TrackSource
import fm.corus.android.data.remote.CloudFunctionsDataSource
import fm.corus.android.data.repository.AuthRepository
import fm.corus.android.data.repository.ExploreRepository
import fm.corus.android.data.repository.MusicSearchRepository
import fm.corus.android.data.repository.PostRepository
import fm.corus.android.data.repository.SpotifyRepository
import fm.corus.android.data.repository.SubscriptionRepository
import fm.corus.android.data.repository.TMDBRepository
import fm.corus.android.data.repository.UserRepository
import fm.corus.android.domain.HapticManager
import fm.corus.android.domain.NowPlayingManager
import fm.corus.android.domain.PostCreationEvent
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.stub
import org.mockito.kotlin.times
import org.mockito.kotlin.verifyBlocking

/**
 * Behavior of the unified compose picker (`compose_unified_search_enabled`):
 * the ALL fan-out serves both verticals off one debounce, narrowing to a chip
 * must reuse what was already fetched, and RECENTLY SAVED flattens the viewer's
 * saved posts to pickable songs/films collapsed by id.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ComposeViewModelUnifiedSearchTest {

    private val testDispatcher = StandardTestDispatcher()

    private lateinit var postRepository: PostRepository
    private lateinit var spotifyRepository: SpotifyRepository
    private lateinit var musicSearchRepository: MusicSearchRepository
    private lateinit var tmdbRepository: TMDBRepository
    private lateinit var authRepository: AuthRepository
    private lateinit var analyticsService: AnalyticsService
    private lateinit var userRepository: UserRepository
    private lateinit var subscriptionRepository: SubscriptionRepository
    private lateinit var exploreRepository: ExploreRepository
    private lateinit var nowPlayingManager: NowPlayingManager
    private lateinit var postCreationEvent: PostCreationEvent
    private lateinit var hapticManager: HapticManager
    private lateinit var remoteConfigService: RemoteConfigService
    private lateinit var networkMonitor: NetworkMonitor
    private lateinit var cloudFunctions: CloudFunctionsDataSource
    private lateinit var preferencesDataStore: PreferencesDataStore

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        // The saved-items cache is process-scoped by design (it outlives any one
        // ViewModel), so it must be reset between tests.
        SavedPickerCache.clear()
        postRepository = mock()
        spotifyRepository = mock()
        musicSearchRepository = mock()
        tmdbRepository = mock()
        authRepository = mock { on { currentUserId } doReturn null }
        analyticsService = mock()
        userRepository = mock()
        subscriptionRepository = mock()
        // Keep the trending-load init coroutine on its happy path.
        exploreRepository = mock {
            onBlocking { fetchTrendingSongs(any(), any()) } doReturn emptyList()
            onBlocking { fetchTrendingMovies(any(), any()) } doReturn emptyList()
        }
        nowPlayingManager = mock()
        postCreationEvent = mock()
        hapticManager = mock()
        remoteConfigService = mock { on { composeUnifiedSearchEnabled } doReturn true }
        networkMonitor = mock { on { isConnected } doReturn MutableStateFlow(true) }
        cloudFunctions = mock()
        preferencesDataStore = mock { on { lastComposeMediaTypeSyncSeed() } doReturn "track" }
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        SavedPickerCache.clear()
    }

    private fun createViewModel() = ComposeViewModel(
        postRepository = postRepository,
        postDraftRepository = mock(),
        spotifyRepository = spotifyRepository,
        musicSearchRepository = musicSearchRepository,
        tmdbRepository = tmdbRepository,
        authRepository = authRepository,
        analyticsService = analyticsService,
        userRepository = userRepository,
        subscriptionRepository = subscriptionRepository,
        exploreRepository = exploreRepository,
        nowPlayingManager = nowPlayingManager,
        postCreationEvent = postCreationEvent,
        hapticManager = hapticManager,
        remoteConfigService = remoteConfigService,
        networkMonitor = networkMonitor,
        cloudFunctions = cloudFunctions,
        preferencesDataStore = preferencesDataStore,
    )

    private fun track(id: String, name: String = "Song $id") = CymbalTrack(
        id = id,
        name = name,
        artistName = "Artist",
        albumName = "Album",
        albumArtURL = "https://img/$id.jpg",
        source = TrackSource.SPOTIFY,
    )

    private fun user() = CymbalUser(id = "u9", username = "someone", displayName = "Someone")

    private fun songPost(id: String, trackId: String) = CymbalPost(
        id = id,
        user = user(),
        track = track(trackId),
    )

    private fun filmPost(id: String, movieId: String, title: String = "Film $movieId") = CymbalPost(
        id = id,
        user = user(),
        track = CymbalTrack(id = "", name = "", artistName = "", albumName = ""),
        mediaType = MediaType.MOVIE,
        movieId = movieId,
        movieTitle = title,
        directorName = "Director",
        releaseYear = "2024",
    )

    private fun stubBothVerticals() {
        musicSearchRepository.stub {
            onBlocking { search(any(), any(), any(), any(), any(), any(), any(), any()) } doReturn
                MusicSearchRepository.Page(listOf(track("s1")), false)
        }
        tmdbRepository.stub {
            onBlocking { searchMovies(any(), any()) } doReturn listOf(CymbalMovie(id = "m1", title = "Dune"))
            onBlocking { prefetchDirectors(any()) } doAnswer { inv -> inv.getArgument(0) }
        }
    }

    @Test
    fun `switching chips does not refetch a query the fan-out already served`() =
        runTest(testDispatcher) {
            org.mockito.Mockito.mockStatic(android.util.Log::class.java).use {
                stubBothVerticals()
                val vm = createViewModel()

                // ALL: one debounce, both verticals.
                vm.searchUnified("dune")
                advanceUntilIdle()
                assertEquals(1, vm.unifiedSongResults.value.size)
                assertEquals(1, vm.filmResults.value.size)
                assertEquals("dune", vm.settledQueries.value[ComposeUnifiedFilter.SONGS])
                assertEquals("dune", vm.settledQueries.value[ComposeUnifiedFilter.FILMS])

                // Narrow to Songs on the same query: nothing to fetch.
                vm.setUnifiedFilter(ComposeUnifiedFilter.SONGS)
                vm.searchUnified("dune")
                advanceUntilIdle()

                // ...and back out to ALL, then over to Films.
                vm.setUnifiedFilter(ComposeUnifiedFilter.ALL)
                vm.searchUnified("dune")
                advanceUntilIdle()
                vm.setUnifiedFilter(ComposeUnifiedFilter.FILMS)
                vm.searchUnified("dune")
                advanceUntilIdle()

                verifyBlocking(musicSearchRepository, times(1)) {
                    search(eq("dune"), any(), any(), any(), any(), any(), any(), any())
                }
                verifyBlocking(tmdbRepository, times(1)) { searchMovies(eq("dune"), any()) }
                // Rows survive every switch — no clear, so no skeleton flash.
                assertEquals(1, vm.unifiedSongResults.value.size)
                assertEquals(1, vm.filmResults.value.size)
            }
        }

    @Test
    fun `a new query refetches both verticals`() =
        runTest(testDispatcher) {
            org.mockito.Mockito.mockStatic(android.util.Log::class.java).use {
                stubBothVerticals()
                val vm = createViewModel()

                vm.searchUnified("dune")
                advanceUntilIdle()
                vm.searchUnified("rush")
                advanceUntilIdle()

                verifyBlocking(musicSearchRepository, times(1)) {
                    search(eq("dune"), any(), any(), any(), any(), any(), any(), any())
                }
                verifyBlocking(musicSearchRepository, times(1)) {
                    search(eq("rush"), any(), any(), any(), any(), any(), any(), any())
                }
                assertEquals("rush", vm.settledQueries.value[ComposeUnifiedFilter.FILMS])
            }
        }

    @Test
    fun `clearing the query resets the chip to All and drops the served marks`() =
        runTest(testDispatcher) {
            org.mockito.Mockito.mockStatic(android.util.Log::class.java).use {
                stubBothVerticals()
                val vm = createViewModel()

                vm.searchUnified("dune")
                advanceUntilIdle()
                vm.setUnifiedFilter(ComposeUnifiedFilter.FILMS)
                vm.clearUnifiedSearch()

                assertEquals(ComposeUnifiedFilter.ALL, vm.unifiedFilter.value)
                assertTrue(vm.settledQueries.value.isEmpty())
                assertTrue(vm.unifiedSongResults.value.isEmpty())
                assertTrue(vm.filmResults.value.isEmpty())
            }
        }

    @Test
    fun `recently saved flattens saved posts newest-first and collapses duplicates`() =
        runTest(testDispatcher) {
            org.mockito.Mockito.mockStatic(android.util.Log::class.java).use {
                authRepository = mock { on { currentUserId } doReturn "me" }
                cloudFunctions = mock {
                    onBlocking { getSavedPosts(eq("me"), any(), any()) } doReturn listOf(
                        songPost("p1", "t1"),
                        filmPost("p2", "m1"),
                        // Same song saved from a different post — collapses.
                        songPost("p3", "t1"),
                        // Same film again — collapses.
                        filmPost("p4", "m1"),
                        songPost("p5", "t2"),
                    )
                }
                val vm = createViewModel()
                advanceUntilIdle()

                assertEquals(
                    listOf("track:t1", "movie:m1", "track:t2"),
                    vm.savedItems.value.map { it.id },
                )
                assertTrue(vm.savedItems.value[1] is SavedPickerItem.Film)
                assertEquals(false, vm.isLoadingSaved.value)
            }
        }

    @Test
    fun `saved posts are not fetched while the flag is off`() =
        runTest(testDispatcher) {
            org.mockito.Mockito.mockStatic(android.util.Log::class.java).use {
                remoteConfigService = mock { on { composeUnifiedSearchEnabled } doReturn false }
                authRepository = mock { on { currentUserId } doReturn "me" }
                val vm = createViewModel()
                advanceUntilIdle()

                verifyBlocking(cloudFunctions, times(0)) { getSavedPosts(any(), any(), any()) }
                assertTrue(vm.savedItems.value.isEmpty())
                // Must settle immediately, or the zero state's single skeleton
                // would hang forever behind a section that never loads.
                assertEquals(false, vm.isLoadingSaved.value)
            }
        }

    @Test
    fun `a second compose open reuses the cached saved items instead of refetching`() =
        runTest(testDispatcher) {
            org.mockito.Mockito.mockStatic(android.util.Log::class.java).use {
                authRepository = mock { on { currentUserId } doReturn "me" }
                cloudFunctions = mock {
                    onBlocking { getSavedPosts(eq("me"), any(), any()) } doReturn
                        listOf(songPost("p1", "t1"))
                }
                createViewModel()
                advanceUntilIdle()

                val second = createViewModel()
                advanceUntilIdle()

                verifyBlocking(cloudFunctions, times(1)) { getSavedPosts(eq("me"), any(), any()) }
                assertEquals(listOf("track:t1"), second.savedItems.value.map { it.id })
                assertEquals(false, second.isLoadingSaved.value)
            }
        }

    @Test
    fun `the saved cache is not served across an account switch`() =
        runTest(testDispatcher) {
            org.mockito.Mockito.mockStatic(android.util.Log::class.java).use {
                // The cache is process-global by design, so it must not hand the
                // previous account's saves to whoever signs in next.
                authRepository = mock { on { currentUserId } doReturn "me" }
                cloudFunctions = mock {
                    onBlocking { getSavedPosts(eq("me"), any(), any()) } doReturn
                        listOf(songPost("p1", "mine"))
                    onBlocking { getSavedPosts(eq("them"), any(), any()) } doReturn
                        listOf(songPost("p2", "theirs"))
                }
                createViewModel()
                advanceUntilIdle()

                authRepository = mock { on { currentUserId } doReturn "them" }
                val other = createViewModel()
                advanceUntilIdle()

                assertEquals(listOf("track:theirs"), other.savedItems.value.map { it.id })
            }
        }

    @Test
    fun `picking a film remembers movie as the last compose medium`() =
        runTest(testDispatcher) {
            org.mockito.Mockito.mockStatic(android.util.Log::class.java).use {
                val vm = createViewModel()
                assertEquals("track", vm.lastComposeMediaType.value)

                vm.selectFilmResult(CymbalMovie(id = "m1", title = "Dune"))
                advanceUntilIdle()

                assertEquals("movie", vm.lastComposeMediaType.value)
                verifyBlocking(preferencesDataStore, times(1)) { setLastComposeMediaType(eq("movie")) }

                vm.selectTrack(track("t1"))
                advanceUntilIdle()
                assertEquals("track", vm.lastComposeMediaType.value)
            }
        }
}
