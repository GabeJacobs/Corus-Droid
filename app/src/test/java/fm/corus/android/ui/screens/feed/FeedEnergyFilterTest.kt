package fm.corus.android.ui.screens.feed

import fm.corus.android.data.model.CymbalUser
import fm.corus.android.data.model.FeedEnergy
import fm.corus.android.data.model.FeedFilter
import fm.corus.android.data.model.MediaType
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.isNull
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyBlocking
import org.mockito.kotlin.wheneverBlocking
import org.mockito.kotlin.whenever

@OptIn(ExperimentalCoroutinesApi::class)
class FeedEnergyFilterTest {

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
    private lateinit var storedDecade: MutableStateFlow<String>
    private lateinit var storedMode: MutableStateFlow<String>

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        storedDecade = MutableStateFlow("")
        storedMode = MutableStateFlow("trending")
        preferencesDataStore = mock {
            on { feedFollowsNowPlaying } doReturn MutableStateFlow(true)
            on { feedFilter } doReturn MutableStateFlow("ALL")
            on { feedFilterSyncSeed() } doReturn "ALL"
            on { feedMode } doReturn storedMode
            on { feedModeSyncSeed() } doReturn "trending"
            on { feedDecade } doReturn storedDecade
            on { feedDecadeSyncSeed() } doReturn ""
            on { forYouSeenIdsJson } doReturn MutableStateFlow("[]")
            on { hasTappedAlbumArt } doReturn MutableStateFlow(false)
            on { hasConfirmedFeedPlaylist } doReturn MutableStateFlow(false)
            on { playFullSongs } doReturn MutableStateFlow(false)
        }
        postRepository = mock()
        authRepository = mock {
            on { currentUserId } doReturn "user1"
            on { userProfile } doReturn MutableStateFlow<CymbalUser?>(null)
            on { currentUser } doReturn MutableStateFlow<com.google.firebase.auth.FirebaseUser?>(null)
        }
        engagementManager = mock()
        userRepository = mock {
            on { followingIds } doReturn MutableStateFlow(emptySet())
            on { hiddenUserIds } doReturn MutableStateFlow(emptySet())
            on { followingLoaded } doReturn MutableStateFlow(true)
        }
        messageRepository = mock()
        cloudFunctions = mock()
        tmdbApiService = mock()
        nowPlayingManager = mock()
        remoteConfig = mock {
            on { revision } doReturn MutableStateFlow(0)
            on { forceTasteMatchesPaywallFlow } doReturn MutableStateFlow(false)
            on { trendingFeedEnabled } doReturn true
            on { feedDecadeFilterEnabled } doReturn true
            on { feedEnergyFilterEnabled } doReturn true
        }
        analyticsService = mock()
        postCreationEvent = mock { on { events } doReturn MutableSharedFlow() }
        postDeletionEvent = mock { on { events } doReturn MutableSharedFlow() }
        wheneverBlocking {
            postRepository.getForYouFeed(
                any(), any(), anyOrNull(), any(), any(), anyOrNull(), any(), any(), any(), anyOrNull(),
                energyLevel = anyOrNull(),
            )
        }.doReturn(CloudFunctionsDataSource.ForYouFeedPage(emptyList(), false, "tok", false))
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun vm(): FeedViewModel = FeedViewModel(
        postRepository = postRepository,
        authRepository = authRepository,
        subscriptionRepository = mock {
            on { favoritesCount } doReturn MutableStateFlow(0)
            on { favoritesTabUnlocked } doReturn MutableStateFlow(false)
            on { hasFullAccessFlow } doReturn MutableStateFlow(false)
        },
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
        playbackModePromptManager = mock(),
        context = mock(),
        feedSwitchHintManager = mock { on { shouldShow } doReturn MutableStateFlow(false) },
        feedScrollRouter = fm.corus.android.domain.FeedScrollRouter(),
    )

    @Test
    fun `selecting energy uses music and forwards energy with the decade`() = runTest(testDispatcher) {
        val viewModel = vm()
        advanceUntilIdle()
        viewModel.setFeedDecade(1990)
        advanceUntilIdle()
        viewModel.setFeedEnergy(FeedEnergy.LOW)
        advanceUntilIdle()
        assertEquals(FeedFilter.MUSIC, viewModel.feedFilter.value)
        assertEquals(FeedEnergy.LOW, viewModel.feedEnergy.value)
        verifyBlocking(postRepository) {
            getForYouFeed(any(), any(), anyOrNull(), any(), any(), eq(MediaType.TRACK), eq(false), eq("trending"), any(), eq(1990), eq("low"))
        }
        verify(analyticsService).logFeedEnergyFilterTapped("low", "trending")
        assertTrue(viewModel.showEnergyIntroduction.value)
        viewModel.dismissEnergyIntroduction()
        assertFalse(viewModel.showEnergyIntroduction.value)
        verify(preferencesDataStore).markEnergyIntroductionSeen("user1")
    }

    @Test
    fun `any energy preserves music and films clear energy`() = runTest(testDispatcher) {
        val viewModel = vm()
        advanceUntilIdle()
        viewModel.setFeedEnergy(FeedEnergy.HIGH)
        advanceUntilIdle()
        viewModel.setFeedEnergy(null)
        advanceUntilIdle()
        assertEquals(FeedFilter.MUSIC, viewModel.feedFilter.value)
        assertNull(viewModel.feedEnergy.value)
        viewModel.setFeedEnergy(FeedEnergy.LOW)
        advanceUntilIdle()
        viewModel.setFeedFilter(FeedFilter.FILM)
        advanceUntilIdle()
        assertNull(viewModel.feedEnergy.value)
    }

    @Test
    fun `remote kill switch ignores a persisted selection`() = runTest(testDispatcher) {
        whenever(preferencesDataStore.feedEnergySeed("user1")).doReturn("high")
        whenever(remoteConfig.feedEnergyFilterEnabled).doReturn(false)
        val viewModel = vm()
        viewModel.loadFeed()
        advanceUntilIdle()
        assertNull(viewModel.feedEnergy.value)
        assertFalse(viewModel.showEnergyIntroduction.value)
        verifyBlocking(postRepository) {
            getForYouFeed(any(), any(), anyOrNull(), any(), any(), anyOrNull(), any(), any(), any(), anyOrNull(), isNull())
        }
    }

    @Test
    fun `live kill switch clears narrowing and reloads without energy`() = runTest(testDispatcher) {
        val revision = MutableStateFlow(0)
        whenever(remoteConfig.revision).doReturn(revision)
        val viewModel = vm()
        advanceUntilIdle()
        viewModel.setFeedEnergy(FeedEnergy.MEDIUM)
        advanceUntilIdle()
        whenever(remoteConfig.feedEnergyFilterEnabled).doReturn(false)
        revision.value++
        advanceUntilIdle()
        assertNull(viewModel.feedEnergy.value)
        assertFalse(viewModel.showEnergyIntroduction.value)
        verifyBlocking(postRepository) {
            getForYouFeed(any(), any(), anyOrNull(), any(), any(), eq(MediaType.TRACK), any(), any(), any(), anyOrNull(), isNull())
        }
    }
}
