package fm.corus.android.ui.screens.feed

import fm.corus.android.data.model.CymbalUser
import fm.corus.android.data.remote.CloudFunctionsDataSource
import fm.corus.android.data.remote.TMDBApiService
import fm.corus.android.data.repository.AuthRepository
import fm.corus.android.data.repository.MessageRepository
import fm.corus.android.data.repository.PostRepository
import fm.corus.android.data.repository.UserRepository
import fm.corus.android.domain.NowPlayingManager
import fm.corus.android.domain.PostCreationEvent
import fm.corus.android.domain.PostDeletionEvent
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
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyBlocking
import org.mockito.kotlin.whenever
import org.mockito.kotlin.wheneverBlocking

/**
 * Regression: feedMode resolves from DataStore asynchronously, but its StateFlow
 * is seeded eagerly with "following". On a cold launch the feed screen's first
 * loadFeed() can run before the persisted ranked mode lands — fetching Following
 * while the menu later shows e.g. Trending as selected. The posts then never
 * match the selected mode. FeedViewModel's init collector must re-sync the feed
 * when the resolved mode diverges from what the current page was loaded with.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class FeedModeResyncOnLateResolveTest {

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
    private lateinit var postDeletionEvent: PostDeletionEvent
    private lateinit var preferencesDataStore: fm.corus.android.data.local.PreferencesDataStore

    // Handle to the DataStore-backed feed mode so the test can emit the persisted
    // value *after* the initial load, mimicking the cold-start resolution lag.
    private val modeFlow = MutableStateFlow("following")

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        postRepository = mock()
        authRepository = mock {
            on { currentUserId } doReturn "user1"
            on { userProfile } doReturn MutableStateFlow<CymbalUser?>(null)
            on { currentUser } doReturn MutableStateFlow<com.google.firebase.auth.FirebaseUser?>(null)
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
        postCreationEvent = mock { on { events } doReturn MutableSharedFlow() }
        postDeletionEvent = mock { on { events } doReturn MutableSharedFlow() }
        preferencesDataStore = mock {
            on { feedFollowsNowPlaying } doReturn MutableStateFlow(true)
            on { feedFilter } doReturn MutableStateFlow("ALL")
            on { feedMode } doReturn modeFlow
            on { forYouSeenIdsJson } doReturn MutableStateFlow("[]")
            on { hasTappedAlbumArt } doReturn MutableStateFlow(false)
        }
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun vm(): FeedViewModel = FeedViewModel(
        postRepository = postRepository,
        authRepository = authRepository,
        subscriptionRepository = mock(),
        engagementManager = engagementManager,
        userRepository = userRepository,
        messageRepository = messageRepository,
        cloudFunctions = cloudFunctions,
        tmdbApiService = tmdbApiService,
        nowPlayingManager = nowPlayingManager,
        remoteConfig = remoteConfig,
        analyticsService = analyticsService,
        postCreationEvent = postCreationEvent,
        postDeletionEvent = postDeletionEvent,
        commentEditedEvent = fm.corus.android.domain.CommentEditedEvent(),
        commentDeletedEvent = fm.corus.android.domain.CommentDeletedEvent(),
        favoriteChangedEvent = fm.corus.android.domain.FavoriteChangedEvent(),
        musicServicePreference = mock(),
        networkMonitor = mock { on { isConnected } doReturn MutableStateFlow(true) },
        preferencesDataStore = preferencesDataStore,
        context = mock(),
        feedScrollRouter = fm.corus.android.domain.FeedScrollRouter(),
    )

    @Test
    fun `late-resolved trending re-syncs the feed away from the raced following load`() =
        runTest(testDispatcher) {
            whenever(remoteConfig.trendingFeedEnabled).doReturn(true)
            wheneverBlocking {
                postRepository.getFeedPage(any(), any(), anyOrNull(), any(), anyOrNull(), any())
            }.doReturn(CloudFunctionsDataSource.FeedPage(emptyList(), false))
            wheneverBlocking {
                postRepository.getForYouFeed(
                    any(), any(), anyOrNull(), any(), any(), anyOrNull(), any(), any(), any(),
                )
            }.doReturn(CloudFunctionsDataSource.ForYouFeedPage(emptyList(), false, "tok", false))

            val viewModel = vm()
            advanceUntilIdle()
            // Mode still seeded to following; the screen's first load races ahead.
            assertEquals("following", viewModel.feedMode.value)
            viewModel.loadFeed()
            advanceUntilIdle()
            verifyBlocking(postRepository) {
                getFeedPage(any(), any(), anyOrNull(), any(), anyOrNull(), any())
            }

            // The persisted mode lands late — collector must re-sync to Trending.
            modeFlow.value = "trending"
            advanceUntilIdle()

            assertEquals("trending", viewModel.feedMode.value)
            verifyBlocking(postRepository) {
                getForYouFeed(
                    any(), any(), anyOrNull(), any(), any(), anyOrNull(), any(),
                    eq("trending"), any(),
                )
            }
        }

    @Test
    fun `no re-sync when the load already used the resolved mode`() =
        runTest(testDispatcher) {
            whenever(remoteConfig.trendingFeedEnabled).doReturn(true)
            wheneverBlocking {
                postRepository.getForYouFeed(
                    any(), any(), anyOrNull(), any(), any(), anyOrNull(), any(), any(), any(),
                )
            }.doReturn(CloudFunctionsDataSource.ForYouFeedPage(emptyList(), false, "tok", false))

            // Persisted mode is already present before the screen loads.
            modeFlow.value = "trending"
            val viewModel = vm()
            advanceUntilIdle()
            assertEquals("trending", viewModel.feedMode.value)

            viewModel.loadFeed()
            advanceUntilIdle()

            // The chronological Following feed must never be fetched.
            verifyBlocking(postRepository, never()) {
                getFeedPage(any(), any(), anyOrNull(), any(), anyOrNull(), any())
            }
        }
}
