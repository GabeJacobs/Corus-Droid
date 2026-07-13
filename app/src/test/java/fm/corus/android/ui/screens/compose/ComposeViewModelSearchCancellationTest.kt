package fm.corus.android.ui.screens.compose

import fm.corus.android.data.model.CymbalTrack
import fm.corus.android.data.model.MediaType
import fm.corus.android.data.model.TrackSource
import fm.corus.android.data.repository.AuthRepository
import fm.corus.android.data.repository.ExploreRepository
import fm.corus.android.data.repository.MusicSearchRepository
import fm.corus.android.data.repository.PostRepository
import fm.corus.android.data.repository.SpotifyRepository
import fm.corus.android.data.repository.SubscriptionRepository
import fm.corus.android.data.repository.TMDBRepository
import fm.corus.android.data.repository.UserRepository
import fm.corus.android.domain.NowPlayingManager
import fm.corus.android.domain.HapticManager
import fm.corus.android.domain.PostCreationEvent
import fm.corus.android.service.AnalyticsService
import fm.corus.android.service.NetworkMonitor
import fm.corus.android.service.RemoteConfigService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
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
import org.mockito.kotlin.doSuspendableAnswer
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.stub

/**
 * Regression test for the Android "Hmm, something's off" search error that
 * appeared while typing even though search was healthy.
 *
 * Bug: each keystroke calls `searchJob.cancel()`. When the cancel landed on the
 * in-flight `searchSongs` call (after the debounce, suspended at `.await()`), the
 * resulting [kotlinx.coroutines.CancellationException] was caught by the
 * catch-all `catch (e: Exception)` in [ComposeViewModel.search] and surfaced as
 * a search-service error. iOS never had this because it guards superseded
 * searches with `Task.isCancelled`.
 *
 * Fix: rethrow `CancellationException` so a superseded search unwinds cleanly
 * and only genuine failures set `searchHasError`.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ComposeViewModelSearchCancellationTest {

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

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        postRepository = mock()
        spotifyRepository = mock()
        musicSearchRepository = mock()
        tmdbRepository = mock()
        // null currentUserId keeps the profile-refresh init coroutine inert.
        authRepository = mock { on { currentUserId } doReturn null }
        analyticsService = mock()
        userRepository = mock()
        subscriptionRepository = mock()
        // Keep the trending-load init coroutine on its happy path so it never
        // reaches the android.util.Log.e branch (not mocked in plain JUnit).
        exploreRepository = mock {
            onBlocking { fetchTrendingSongs(any(), any()) } doReturn emptyList()
            onBlocking { fetchTrendingMovies(any(), any()) } doReturn emptyList()
        }
        nowPlayingManager = mock()
        postCreationEvent = mock()
        hapticManager = mock()
        remoteConfigService = mock()
        networkMonitor = mock { on { isConnected } doReturn MutableStateFlow(true) }
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
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
    )

    private fun track(id: String) = CymbalTrack(
        id = id,
        name = "Song $id",
        artistName = "Artist",
        albumName = "Album",
        albumArtURL = "https://img/$id.jpg",
        source = TrackSource.SPOTIFY,
    )

    @Test
    fun `a superseded search does not flash the search-service error`() =
        runTest(testDispatcher) {
            org.mockito.Mockito.mockStatic(android.util.Log::class.java).use {
                // First query hangs (models a slow in-flight searchSongs call);
                // it only ends when the job is cancelled. Second query resolves.
                musicSearchRepository.stub {
                    onBlocking { search(eq("a"), any(), any(), any(), any(), any(), any()) } doSuspendableAnswer {
                        awaitCancellation()
                    }
                    onBlocking { search(eq("ab"), any(), any(), any(), any(), any(), any()) } doReturn
                        MusicSearchRepository.Page(listOf(track("ab")), false)
                }
                val vm = createViewModel()

                // Let the first search get past the 300ms debounce and park in
                // the (hanging) network call.
                vm.search("a", MediaType.TRACK)
                advanceTimeBy(300)
                runCurrent()

                // Next keystroke supersedes it: cancels the in-flight job.
                vm.search("ab", MediaType.TRACK)
                // Drain the cancellation of the first job WITHOUT advancing into
                // the second job's debounce (so a stale reset can't mask the bug).
                runCurrent()

                // The cancelled search must not have surfaced an error. Before
                // the fix this was `true` here (CancellationException swallowed).
                assertFalse(
                    "Superseded search must not set searchHasError",
                    vm.searchHasError.value,
                )

                // Once the surviving search settles, results show, no error.
                advanceUntilIdle()
                assertFalse(vm.searchHasError.value)
                assertEquals(1, vm.searchResults.value.size)
                assertEquals("ab", vm.searchResults.value.first().id)
            }
        }

    @Test
    fun `a genuine search failure shows the error state`() =
        runTest(testDispatcher) {
            org.mockito.Mockito.mockStatic(android.util.Log::class.java).use {
                musicSearchRepository.stub {
                    onBlocking { search(eq("x"), any(), any(), any(), any(), any(), any()) } doThrow
                        RuntimeException("backend unreachable")
                }
                val vm = createViewModel()

                vm.search("x", MediaType.TRACK)
                advanceUntilIdle()

                assertTrue(
                    "A real failure must still set searchHasError",
                    vm.searchHasError.value,
                )
                assertTrue(vm.searchResults.value.isEmpty())
            }
        }

    @Test
    fun `reset clears a prior search error`() =
        runTest(testDispatcher) {
            org.mockito.Mockito.mockStatic(android.util.Log::class.java).use {
                musicSearchRepository.stub {
                    onBlocking { search(eq("x"), any(), any(), any(), any(), any(), any()) } doThrow
                        RuntimeException("backend unreachable")
                }
                val vm = createViewModel()

                vm.search("x", MediaType.TRACK)
                advanceUntilIdle()
                assertTrue(vm.searchHasError.value)

                vm.reset()
                assertFalse(
                    "Reopening compose must not carry a stale search error",
                    vm.searchHasError.value,
                )
            }
        }
}
