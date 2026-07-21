package fm.corus.android.ui.screens.compose

import fm.corus.android.data.model.CymbalTrack
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
import org.mockito.kotlin.verifyBlocking

/**
 * Regression test for blurry album art when posting straight from the
 * mini-player.
 *
 * Bug (Android): search a song on the compose ("set Corus") page → play the
 * preview → tap the song in the mini-player → tap "Post Song". The post
 * uploaded with a blurry thumbnail because the preview only carried
 * `albumArtURL` (the small image) through [NowPlayingManager]; the high-res
 * `albumArtLargeURL` was dropped, so SongDetail had nothing better to post.
 *
 * Fix: [ComposeViewModel.togglePreview] now forwards `albumArtLargeURL` into
 * the now-playing state alongside the thumbnail, so the mini-player → SongDetail
 * → Post path posts full-res artwork.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ComposeViewModelPreviewArtworkTest {

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
        cloudFunctions = mock(),
        preferencesDataStore = mock(),
    )

    @Test
    fun `togglePreview forwards the high-res album art so mini-player posts are not blurry`() =
        runTest(testDispatcher) {
            // The ViewModel's init coroutine logs via android.util.Log, which throws
            // "not mocked" in plain JUnit. Stub the static so construction is inert.
            org.mockito.Mockito.mockStatic(android.util.Log::class.java).use {
            val vm = createViewModel()
            val track = CymbalTrack(
                id = "t1",
                name = "Song",
                artistName = "Artist",
                albumName = "Album",
                albumArtURL = "https://img/thumb_100x100.jpg",
                albumArtLargeURL = "https://img/large_640x640.jpg",
                previewUrl = "https://preview.m4a",
                isrc = "USABC1234567",
                source = TrackSource.SPOTIFY,
            )

            vm.togglePreview(track)
            advanceUntilIdle()

            // The high-res URL must be threaded through to NowPlayingManager —
            // dropping it (eq(null)) is exactly what reintroduces the blur bug.
            verifyBlocking(nowPlayingManager) {
                play(
                    trackId = eq("t1"),
                    trackName = eq("Song"),
                    artistName = eq("Artist"),
                    albumArtURL = eq("https://img/thumb_100x100.jpg"),
                    albumArtLargeURL = eq("https://img/large_640x640.jpg"),
                    previewUrl = eq("https://preview.m4a"),
                    spotifyURI = eq(null),
                    spotifyWebURL = eq(null),
                    isrc = eq("USABC1234567"),
                    sourcePostId = eq(null),
                    source = eq(TrackSource.SPOTIFY),
                    soundcloudId = eq(null),
                    soundcloudPermalinkUrl = eq(null),
                )
            }
            }
        }
}
