package fm.corus.android.ui.screens.compose

import fm.corus.android.data.model.CymbalTrack
import fm.corus.android.data.model.MediaType
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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

/**
 * Regression tests for the silent-delete bug fix.
 *
 * Before this change, an over-limit post could land in Firestore briefly then
 * be silently deleted by the trigger because the client's `checkCanPost`
 * pre-flight failed open on network errors. The new path routes through the
 * `createPost` Cloud Function callable, which atomically rejects over-limit
 * with `RESOURCE_EXHAUSTED`. The data-source maps that to
 * `PostLimitReachedException`; the ViewModel maps the exception to the
 * paywall instead of a generic error. These tests pin the paywall mapping
 * so we can't regress the user-visible behaviour.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ComposePostLimitTest {

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

    private val track = CymbalTrack(
        id = "track1",
        name = "Song",
        artistName = "Artist",
        albumName = "Album",
        spotifyURI = "spotify:track:track1",
        spotifyWebURL = "https://open.spotify.com/track/track1",
        durationMs = 200_000,
    )

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        postRepository = mock()
        spotifyRepository = mock()
        musicSearchRepository = mock()
        tmdbRepository = mock()
        authRepository = mock { on { currentUserId } doReturn "user_A" }
        analyticsService = mock()
        userRepository = mock()
        subscriptionRepository = mock {
            on { postCountLoaded } doReturn MutableStateFlow(true)
            on { hasFullAccessFlow } doReturn MutableStateFlow(false)
            on { recentPostCount } doReturn MutableStateFlow(0)
            on { recentPostCountHard } doReturn MutableStateFlow(0)
            on { totalPostCount } doReturn MutableStateFlow(0)
            on { isClubMember } doReturn MutableStateFlow(false)
            on { isVerified } doReturn MutableStateFlow(false)
            on { dailyPostLimit } doReturn MutableStateFlow(3)
            on { savesCount } doReturn MutableStateFlow(0)
            on { hasFullAccess } doReturn false
            on { shouldShowApproachingCapWarning(any()) } doReturn false
        }
        exploreRepository = mock()
        nowPlayingManager = mock()
        postCreationEvent = mock()
        hapticManager = mock()
        remoteConfigService = mock { on { commentControlsOnPosts } doReturn false }
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel(): ComposeViewModel {
        return ComposeViewModel(
            postRepository,
            spotifyRepository,
            musicSearchRepository,
            tmdbRepository,
            authRepository,
            analyticsService,
            userRepository,
            subscriptionRepository,
            exploreRepository,
            nowPlayingManager,
            postCreationEvent,
            hapticManager,
            remoteConfigService,
        )
    }

    @Test
    fun `createPost surfaces post-limit as paywall, not generic error`() = runTest(testDispatcher) {
        whenever(postRepository.createPost(any(), any())).thenAnswer {
            throw CloudFunctionsDataSource.PostLimitReachedException(
                recentCount = 3,
                dailyLimit = 3,
                hardCap = false,
            )
        }

        val vm = viewModel()
        vm.selectPreloadedTrack(track)
        vm.createPost(caption = "", mediaType = MediaType.TRACK)
        advanceUntilIdle()

        assertTrue("Paywall should open on over-limit", vm.showPostLimitPaywall.value)
        assertNull("Generic error should not be set", vm.error.value)
        assertFalse("Post success should not be set", vm.postSuccess.value)
    }

    @Test
    fun `createPost surfaces hard cap as Cooldown dialog, not paywall`() = runTest(testDispatcher) {
        whenever(postRepository.createPost(any(), any())).thenAnswer {
            throw CloudFunctionsDataSource.PostLimitReachedException(
                recentCount = 400,
                dailyLimit = 3,
                hardCap = true,
            )
        }

        val vm = viewModel()
        vm.selectPreloadedTrack(track)
        vm.createPost(caption = "", mediaType = MediaType.TRACK)
        advanceUntilIdle()

        assertFalse("Paywall should not open at the hard cap", vm.showPostLimitPaywall.value)
        assertTrue("Hard cap dialog should be shown", vm.showHardCapAlert.value)
        assertNull("Generic error should not be set", vm.error.value)
    }

    @Test
    fun `paid user crossing approaching-cap threshold fires the warning`() = runTest(testDispatcher) {
        // Mock a paid user whose latest post crossed the warn threshold; the
        // repository's throttle decides whether to fire and is mocked to true.
        whenever(subscriptionRepository.hasFullAccess).thenReturn(true)
        whenever(subscriptionRepository.shouldShowApproachingCapWarning(any())).thenReturn(true)
        whenever(subscriptionRepository.approachingCapRemaining).thenReturn(10)
        whenever(postRepository.createPost(any(), any())).thenReturn(
            CloudFunctionsDataSource.CreatePostResult(
                postId = "p1",
                recentCount = 0,
                recentCountHard = 390,
                dailyLimit = 3,
                isFirstPoster = false,
            )
        )

        val vm = viewModel()
        vm.selectPreloadedTrack(track)
        vm.createPost(caption = "", mediaType = MediaType.TRACK)
        advanceUntilIdle()

        assertTrue("Approaching-cap warning should fire", vm.showApproachingCapAlert.value)
        assertEquals(10, vm.approachingCapRemaining.value)
    }

    @Test
    fun `paid user below threshold does not fire the warning`() = runTest(testDispatcher) {
        whenever(subscriptionRepository.hasFullAccess).thenReturn(true)
        whenever(subscriptionRepository.shouldShowApproachingCapWarning(any())).thenReturn(false)
        whenever(postRepository.createPost(any(), any())).thenReturn(
            CloudFunctionsDataSource.CreatePostResult(
                postId = "p1",
                recentCount = 0,
                recentCountHard = 100,
                dailyLimit = 3,
                isFirstPoster = false,
            )
        )

        val vm = viewModel()
        vm.selectPreloadedTrack(track)
        vm.createPost(caption = "", mediaType = MediaType.TRACK)
        advanceUntilIdle()

        assertFalse("Warning should not fire below threshold", vm.showApproachingCapAlert.value)
    }

    @Test
    fun `createPost surfaces repost-missing with explanatory error`() = runTest(testDispatcher) {
        whenever(postRepository.createPost(any(), any())).thenAnswer {
            throw CloudFunctionsDataSource.RepostOriginalMissingException()
        }

        val vm = viewModel()
        vm.selectPreloadedTrack(track)
        vm.createPost(caption = "", mediaType = MediaType.TRACK)
        advanceUntilIdle()

        assertFalse(vm.showPostLimitPaywall.value)
        assertEquals("That post is no longer available.", vm.error.value)
    }
}
