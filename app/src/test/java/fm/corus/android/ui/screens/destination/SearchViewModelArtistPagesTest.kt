package fm.corus.android.ui.screens.destination

import android.util.Log
import fm.corus.android.data.local.PreferencesDataStore
import fm.corus.android.data.model.AlbumSearchSummary
import fm.corus.android.data.model.ArtistSummary
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
import fm.corus.android.ui.screens.search.SearchViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
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
import org.mockito.MockedStatic
import org.mockito.Mockito.mockStatic
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

/**
 * Flag-gated search params for the artist-pages feature:
 *  - flag ON  → searchSongs opts into includeArtists/includeAlbums and the
 *    artist/album rows populate; the Film tab fetches director rows.
 *  - flag OFF → the request params stay false (the callable receives no new
 *    keys) and no director search fires, keeping the tabs byte-identical to
 *    today's behavior.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SearchViewModelArtistPagesTest {

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
    private lateinit var networkMonitor: NetworkMonitor

    private lateinit var logMock: MockedStatic<Log>

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
            on { recentSearches } doReturn flowOf(emptyList())
            on { contactsSyncStatus } doReturn flowOf("notAsked")
            on { trendingSongsWindow } doReturn flowOf("week")
            on { trendingFilmsWindow } doReturn flowOf("week")
            on { trendingHashtagsWindow } doReturn flowOf("month")
            on { trendingArtistsWindow } doReturn flowOf("week")
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

    @Test
    fun `flag on - song search opts into artists and albums and exposes the rows`() =
        runTest(testDispatcher) {
            whenever(remoteConfigService.artistPagesEnabled).thenReturn(true)
            whenever(remoteConfigService.soundcloudEnabled).thenReturn(false)
            whenever(
                musicSearchRepository.search(any(), any(), any(), any(), any(), any(), any(), any())
            ).thenReturn(
                MusicSearchRepository.Page(
                    tracks = emptyList(),
                    hasMore = false,
                    artists = listOf(ArtistSummary(id = "a1", name = "Radiohead")),
                    albums = listOf(AlbumSearchSummary(id = "al1", title = "OK Computer", artistName = "Radiohead")),
                )
            )

            val vm = createViewModel()
            vm.search("radiohead", tab = 1)
            advanceUntilIdle()

            verify(musicSearchRepository).search(
                eq("radiohead"), eq(0), eq(20),
                includeSoundCloud = eq(false),
                includeArtists = eq(true),
                includeAlbums = eq(true),
                albumsMatchArtist = eq(false),
                collapse = eq("recording"),
            )
            assertEquals(1, vm.artistSearchResults.value.size)
            assertEquals("a1", vm.artistSearchResults.value.first().id)
            assertEquals(1, vm.albumSearchResults.value.size)
            assertEquals("al1", vm.albumSearchResults.value.first().id)
        }

    @Test
    fun `flag off - song search request carries no artist or album opt-in`() =
        runTest(testDispatcher) {
            whenever(remoteConfigService.artistPagesEnabled).thenReturn(false)
            whenever(remoteConfigService.soundcloudEnabled).thenReturn(false)
            whenever(
                musicSearchRepository.search(any(), any(), any(), any(), any(), any(), any(), any())
            ).thenReturn(MusicSearchRepository.Page(tracks = emptyList(), hasMore = false))

            val vm = createViewModel()
            vm.search("radiohead", tab = 1)
            advanceUntilIdle()

            verify(musicSearchRepository).search(
                eq("radiohead"), eq(0), eq(20),
                includeSoundCloud = eq(false),
                includeArtists = eq(false),
                includeAlbums = eq(false),
                albumsMatchArtist = eq(false),
                collapse = eq("recording"),
            )
            assertTrue(vm.artistSearchResults.value.isEmpty())
            assertTrue(vm.albumSearchResults.value.isEmpty())
        }

    @Test
    fun `flag on - film search also fetches director rows`() = runTest(testDispatcher) {
        whenever(remoteConfigService.artistPagesEnabled).thenReturn(true)
        whenever(tmdbRepository.searchMovies(any(), any())).thenReturn(emptyList())
        whenever(tmdbRepository.prefetchDirectors(any())).thenReturn(emptyList())
        whenever(tmdbRepository.searchDirectors(eq("scorsese")))
            .thenReturn(listOf(ArtistSummary(id = "1032", name = "Martin Scorsese")))

        val vm = createViewModel()
        vm.search("scorsese", tab = 2)
        advanceUntilIdle()

        verify(tmdbRepository).searchDirectors(eq("scorsese"))
        assertEquals(1, vm.directorSearchResults.value.size)
        assertEquals("1032", vm.directorSearchResults.value.first().id)
    }

    @Test
    fun `flag off - film search never calls the director search`() = runTest(testDispatcher) {
        whenever(remoteConfigService.artistPagesEnabled).thenReturn(false)
        whenever(tmdbRepository.searchMovies(any(), any())).thenReturn(emptyList())
        whenever(tmdbRepository.prefetchDirectors(any())).thenReturn(emptyList())

        val vm = createViewModel()
        vm.search("scorsese", tab = 2)
        advanceUntilIdle()

        verify(tmdbRepository, never()).searchDirectors(any())
        assertTrue(vm.directorSearchResults.value.isEmpty())
    }

    @Test
    fun `director search failure degrades to an empty row without erroring the tab`() =
        runTest(testDispatcher) {
            whenever(remoteConfigService.artistPagesEnabled).thenReturn(true)
            whenever(tmdbRepository.searchMovies(any(), any()))
                .thenReturn(listOf(fm.corus.android.data.model.CymbalMovie(id = "1", title = "Film", posterURL = "x")))
            whenever(tmdbRepository.prefetchDirectors(any()))
                .thenReturn(listOf(fm.corus.android.data.model.CymbalMovie(id = "1", title = "Film", posterURL = "x")))
            whenever(tmdbRepository.searchDirectors(any())).thenThrow(RuntimeException("proxy down"))

            val vm = createViewModel()
            vm.search("scorsese", tab = 2)
            advanceUntilIdle()

            assertTrue(vm.directorSearchResults.value.isEmpty())
            assertEquals(1, vm.filmSearchResults.value.size)
        }
}
