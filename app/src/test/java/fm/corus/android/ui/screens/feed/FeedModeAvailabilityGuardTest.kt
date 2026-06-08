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
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

/**
 * Regression: a feed mode is device-persisted (DataStore), so it can outlive the
 * Remote Config flag that gated it — e.g. a device that persisted "forYou" then
 * logs into an account where for_you_enabled is off. The menu only shows enabled
 * modes, so returning a disabled mode left nothing selected and loaded a feed the
 * user couldn't switch away from. `resolveFeedMode` must fall back to "following"
 * whenever the chosen ranked mode's flag is off. Mirrors the iOS fix.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class FeedModeAvailabilityGuardTest {

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
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun vm(storedMode: String): FeedViewModel {
        preferencesDataStore = mock {
            on { feedFollowsNowPlaying } doReturn MutableStateFlow(true)
            on { feedFilter } doReturn MutableStateFlow("ALL")
            on { feedMode } doReturn MutableStateFlow(storedMode)
            on { forYouSeenIdsJson } doReturn MutableStateFlow("[]")
            on { hasTappedAlbumArt } doReturn MutableStateFlow(false)
        }
        return FeedViewModel(
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
            favoriteChangedEvent = fm.corus.android.domain.FavoriteChangedEvent(),
            commentEditedEvent = fm.corus.android.domain.CommentEditedEvent(),
            commentDeletedEvent = fm.corus.android.domain.CommentDeletedEvent(),
            musicServicePreference = mock(),
            networkMonitor = mock { on { isConnected } doReturn MutableStateFlow(true) },
            preferencesDataStore = preferencesDataStore,
            context = mock(),
            feedScrollRouter = fm.corus.android.domain.FeedScrollRouter(),
        )
    }

    @Test
    fun `persisted forYou falls back to following when for_you flag is off`() = runTest(testDispatcher) {
        whenever(remoteConfig.forYouEnabled).doReturn(false)
        whenever(remoteConfig.trendingFeedEnabled).doReturn(true)
        val viewModel = vm("forYou")
        advanceUntilIdle()
        assertEquals("following", viewModel.feedMode.value)
    }

    @Test
    fun `persisted forYou is honored when for_you flag is on`() = runTest(testDispatcher) {
        whenever(remoteConfig.forYouEnabled).doReturn(true)
        val viewModel = vm("forYou")
        advanceUntilIdle()
        assertEquals("forYou", viewModel.feedMode.value)
    }

    @Test
    fun `persisted trending falls back to following when trending flag is off`() = runTest(testDispatcher) {
        whenever(remoteConfig.trendingFeedEnabled).doReturn(false)
        whenever(remoteConfig.forYouEnabled).doReturn(false)
        val viewModel = vm("trending")
        advanceUntilIdle()
        assertEquals("following", viewModel.feedMode.value)
    }

    @Test
    fun `persisted trending is honored when trending flag is on`() = runTest(testDispatcher) {
        whenever(remoteConfig.trendingFeedEnabled).doReturn(true)
        val viewModel = vm("trending")
        advanceUntilIdle()
        assertEquals("trending", viewModel.feedMode.value)
    }

    @Test
    fun `unset mode opens in forYou only when default and for_you are both on`() = runTest(testDispatcher) {
        whenever(remoteConfig.defaultForYouFeedEnabled).doReturn(true)
        whenever(remoteConfig.forYouEnabled).doReturn(true)
        val viewModel = vm("")
        advanceUntilIdle()
        assertEquals("forYou", viewModel.feedMode.value)
    }

    @Test
    fun `unset mode opens in following when for_you is off`() = runTest(testDispatcher) {
        whenever(remoteConfig.defaultForYouFeedEnabled).doReturn(true)
        whenever(remoteConfig.forYouEnabled).doReturn(false)
        val viewModel = vm("")
        advanceUntilIdle()
        assertEquals("following", viewModel.feedMode.value)
    }
}
