package fm.corus.android.ui.screens.compose

import fm.corus.android.data.model.CymbalMovie
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
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import java.time.LocalDate

/**
 * Regression test for the "new release" tag missing on a freshly-posted
 * movie until the app was relaunched. Root cause: the trophy post built
 * for first-posters dropped the movie's release date, so [isNewRelease]
 * always returned false on the optimistic UI even when the server-side
 * doc was correctly tagged.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ComposeTrophyPostReleaseDateTest {

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

    private val newReleaseMovie = CymbalMovie(
        id = "tmdb_999",
        title = "Brand New Film",
        directorName = "Some Director",
        year = "2026",
        posterURL = "https://example.com/p.jpg",
        tmdbWebURL = "https://www.themoviedb.org/movie/999",
        overview = "A film.",
        rating = 7.5,
        // Five days before "today" in the test below — well inside the 30-day window.
        releaseDate = "2026-05-08",
    )

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        postRepository = mock {
            onBlocking { createPost(any(), anyOrNull()) } doReturn CloudFunctionsDataSource.CreatePostResult(
                postId = "new_post_id",
                recentCount = 1,
                recentCountHard = 1,
                dailyLimit = 3,
                isFirstPoster = true,
            )
        }
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
            on { totalPostCount } doReturn MutableStateFlow(0)
            on { isClubMember } doReturn MutableStateFlow(false)
            on { isVerified } doReturn MutableStateFlow(false)
            on { dailyPostLimit } doReturn MutableStateFlow(3)
            on { savesCount } doReturn MutableStateFlow(0)
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

    private fun viewModel(): ComposeViewModel = ComposeViewModel(
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
        mock { on { isConnected } doReturn MutableStateFlow(true) },
    )

    @Test
    fun `trophy post for new-release movie carries releaseDate and isNewRelease is true`() = runTest(testDispatcher) {
        val vm = viewModel()
        vm.selectFilmResult(newReleaseMovie)
        vm.createPost(caption = "", mediaType = MediaType.MOVIE)
        advanceUntilIdle()

        val trophy = vm.trophyPost.value
        assertNotNull("Trophy post should be set for first-poster", trophy)
        assertEquals(
            "Trophy post must carry the movie's releaseDate so the new-release tag renders immediately",
            "2026-05-08",
            trophy?.movieReleaseDate,
        )
        // "Today" is 2026-05-13 per the system date — release was 5 days ago.
        assertTrue(
            "isNewRelease must return true on the optimistic trophy post for a recent release",
            trophy?.isNewRelease(LocalDate.of(2026, 5, 13)) == true,
        )
    }
}
