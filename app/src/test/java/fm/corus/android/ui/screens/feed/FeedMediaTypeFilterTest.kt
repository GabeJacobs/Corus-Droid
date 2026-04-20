package fm.corus.android.ui.screens.feed

import fm.corus.android.data.model.CymbalUser
import fm.corus.android.data.model.MediaType
import fm.corus.android.data.remote.CloudFunctionsDataSource
import fm.corus.android.data.remote.TMDBApiService
import fm.corus.android.data.repository.AuthRepository
import fm.corus.android.data.repository.MessageRepository
import fm.corus.android.data.repository.PostRepository
import fm.corus.android.data.repository.UserRepository
import fm.corus.android.domain.NowPlayingManager
import fm.corus.android.domain.PostCreationEvent
import fm.corus.android.domain.PostEngagementManager
import fm.corus.android.service.AnalyticsService
import fm.corus.android.service.RemoteConfigService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
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
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

/**
 * Regression: tapping "Films" in the feed filter used to return an empty feed when the
 * user's first page of posts was all tracks — filtering happened client-side on an
 * already-fetched page. This test pins the fix: the active filter is forwarded to the
 * backend so the server returns only the requested media type.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class FeedMediaTypeFilterTest {

    private val testDispatcher = StandardTestDispatcher()

    private lateinit var postRepository: PostRepository
    private lateinit var authRepository: AuthRepository
    private lateinit var engagementManager: PostEngagementManager
    private lateinit var userRepository: UserRepository
    private lateinit var messageRepository: MessageRepository
    private lateinit var cloudFunctions: CloudFunctionsDataSource
    private lateinit var tmdbApiService: TMDBApiService
    private lateinit var nowPlayingManager: NowPlayingManager
    private lateinit var remoteConfig: RemoteConfigService
    private lateinit var analyticsService: AnalyticsService
    private lateinit var postCreationEvent: PostCreationEvent

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        postRepository = mock()
        authRepository = mock {
            on { currentUserId } doReturn "user1"
            on { userProfile } doReturn MutableStateFlow<CymbalUser?>(null)
        }
        engagementManager = mock()
        userRepository = mock {
            on { followingIds } doReturn MutableStateFlow(emptySet())
        }
        messageRepository = mock()
        cloudFunctions = mock()
        tmdbApiService = mock()
        nowPlayingManager = mock()
        remoteConfig = mock()
        analyticsService = mock()
        postCreationEvent = mock {
            on { events } doReturn MutableSharedFlow()
        }
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun vm(): FeedViewModel = FeedViewModel(
        postRepository = postRepository,
        authRepository = authRepository,
        engagementManager = engagementManager,
        userRepository = userRepository,
        messageRepository = messageRepository,
        cloudFunctions = cloudFunctions,
        tmdbApiService = tmdbApiService,
        nowPlayingManager = nowPlayingManager,
        remoteConfig = remoteConfig,
        analyticsService = analyticsService,
        postCreationEvent = postCreationEvent,
    )

    @Test
    fun `loadFeed forwards active mediaType filter to backend`() = runTest(testDispatcher) {
        whenever(postRepository.getFeedPage(any(), any(), anyOrNull(), any(), anyOrNull()))
            .doReturn(CloudFunctionsDataSource.FeedPage(emptyList(), false))

        val viewModel = vm()
        viewModel.setFeedMediaFilter(MediaType.MOVIE)
        advanceUntilIdle()

        // Core regression check: the "Films" selection must make it to the server as mediaType=MOVIE.
        // Without this forwarding, the old client-side filter emptied the feed when the first page
        // contained no films.
        verify(postRepository).getFeedPage(
            userId = eq("user1"),
            pageSize = any(),
            lastTimestamp = anyOrNull(),
            onePerFollower = any(),
            mediaType = eq(MediaType.MOVIE),
        )
    }

    @Test
    fun `setFeedMediaFilter resets pagination so new filter fetches from the top`() = runTest(testDispatcher) {
        whenever(postRepository.getFeedPage(any(), any(), anyOrNull(), any(), anyOrNull()))
            .doReturn(CloudFunctionsDataSource.FeedPage(emptyList(), false))

        val viewModel = vm()
        viewModel.loadFeed(refresh = true)
        advanceUntilIdle()

        viewModel.setFeedMediaFilter(MediaType.MOVIE)
        advanceUntilIdle()

        // Every call must be a fresh first-page fetch (lastTimestamp null).
        // If we forwarded a stale lastTimestamp, the user could land mid-feed when switching filters.
        verify(postRepository, org.mockito.kotlin.atLeast(2)).getFeedPage(
            userId = any(),
            pageSize = any(),
            lastTimestamp = eq(null),
            onePerFollower = any(),
            mediaType = anyOrNull(),
        )
    }
}
