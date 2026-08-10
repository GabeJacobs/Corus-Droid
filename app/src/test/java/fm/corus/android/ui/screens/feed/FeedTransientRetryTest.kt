package fm.corus.android.ui.screens.feed

import fm.corus.android.data.model.CymbalPost
import fm.corus.android.data.model.CymbalTrack
import fm.corus.android.data.model.CymbalUser
import fm.corus.android.data.remote.CloudFunctionsDataSource
import fm.corus.android.data.repository.AuthRepository
import fm.corus.android.data.repository.MessageRepository
import fm.corus.android.data.repository.PostRepository
import fm.corus.android.data.repository.UserRepository
import fm.corus.android.domain.NowPlayingManager
import fm.corus.android.domain.PostCreationEvent
import fm.corus.android.domain.PostDeletionEvent
import fm.corus.android.domain.PostEngagementManager
import fm.corus.android.service.AnalyticsService
import fm.corus.android.service.NetworkMonitor
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.doSuspendableAnswer
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.mockito.kotlin.wheneverBlocking

/**
 * Regression: a feed load that fails while the device is online must NOT be
 * painted as "you're offline." On a cold launch the first callable often throws
 * a transient FirebaseFunctionsException (App Check / Play Integrity token still
 * minting, a Functions UNAVAILABLE/INTERNAL) even on strong wifi — the exact
 * blip a manual "Retry" clears. The ViewModel now retries that load once,
 * silently, before surfacing any error; the offline panel only appears when the
 * retry also fails. When the device is genuinely offline, there is no silent
 * retry — the failure surfaces immediately.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class FeedTransientRetryTest {

    private val testDispatcher = StandardTestDispatcher()

    private lateinit var postRepository: PostRepository
    private lateinit var authRepository: AuthRepository
    private lateinit var engagementManager: PostEngagementManager
    private lateinit var userRepository: UserRepository
    private lateinit var messageRepository: MessageRepository
    private lateinit var cloudFunctions: CloudFunctionsDataSource
    private lateinit var nowPlayingManager: NowPlayingManager
    private lateinit var remoteConfig: RemoteConfigService
    private lateinit var analyticsService: AnalyticsService
    private lateinit var postCreationEvent: PostCreationEvent
    private lateinit var postDeletionEvent: PostDeletionEvent
    private lateinit var preferencesDataStore: fm.corus.android.data.local.PreferencesDataStore

    private val modeFlow = MutableStateFlow("following")

    private fun user(id: String) = CymbalUser(id = id, username = id, displayName = id)
    private fun track() = CymbalTrack(id = "t1", name = "n", artistName = "a", albumName = "al")
    private fun post(id: String) = CymbalPost(id = id, user = user("poster"), track = track())

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
        nowPlayingManager = mock()
        remoteConfig = mock()
        analyticsService = mock()
        postCreationEvent = mock { on { events } doReturn MutableSharedFlow() }
        postDeletionEvent = mock { on { events } doReturn MutableSharedFlow() }
        preferencesDataStore = mock {
            on { feedFollowsNowPlaying } doReturn MutableStateFlow(true)
            on { feedFilter } doReturn MutableStateFlow("ALL")
            on { feedMode } doReturn modeFlow
            // Synchronous seed read during FeedViewModel construction; an
            // unstubbed mock returns null and resolveFeedMode() NPEs on it.
            on { feedModeSyncSeed() } doReturn "following"
            on { forYouSeenIdsJson } doReturn MutableStateFlow("[]")
            on { hasTappedAlbumArt } doReturn MutableStateFlow(false)
            on { hasConfirmedFeedPlaylist } doReturn MutableStateFlow(false)
        }
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    /** Builds a ViewModel whose NetworkMonitor reports [connected]. */
    private fun vm(connected: Boolean): FeedViewModel = FeedViewModel(
        postRepository = postRepository,
        authRepository = authRepository,
        subscriptionRepository = mock(),
        engagementManager = engagementManager,
        userRepository = userRepository,
        messageRepository = messageRepository,
        cloudFunctions = cloudFunctions,
        tmdbApiService = mock(),
        nowPlayingManager = nowPlayingManager,
        remoteConfig = remoteConfig,
        analyticsService = analyticsService,
        postCreationEvent = postCreationEvent,
        postDeletionEvent = postDeletionEvent,
        commentEditedEvent = fm.corus.android.domain.CommentEditedEvent(),
        commentDeletedEvent = fm.corus.android.domain.CommentDeletedEvent(),
        favoriteChangedEvent = fm.corus.android.domain.FavoriteChangedEvent(),
        musicServicePreference = mock(),
        networkMonitor = mock { on { isConnected } doReturn MutableStateFlow(connected) },
        preferencesDataStore = preferencesDataStore,
        playbackModePromptManager = mock(),
        context = mock(),
        feedSwitchHintManager = mock { on { shouldShow } doReturn MutableStateFlow(false) },
        feedScrollRouter = fm.corus.android.domain.FeedScrollRouter(),
    )

    @Test
    fun `transient failure while online retries once and recovers without an error`() =
        runTest(testDispatcher) {
            var calls = 0
            wheneverBlocking {
                postRepository.getFeedPage(any(), any(), anyOrNull(), any(), anyOrNull(), any())
            }.doSuspendableAnswer {
                calls++
                // First attempt fails (cold-start token blip); the retry succeeds.
                if (calls == 1) throw RuntimeException("transient cold-start failure")
                CloudFunctionsDataSource.FeedPage(listOf(post("p1"), post("p2")), false)
            }

            val viewModel = vm(connected = true)
            advanceUntilIdle()

            viewModel.loadFeed()
            advanceUntilIdle()

            // Exactly one silent retry, and the user never sees an error.
            assertEquals(2, calls)
            assertFalse(viewModel.lastLoadFailed.value)
            assertEquals(listOf("p1", "p2"), viewModel.posts.value.map { it.id })
        }

    @Test
    fun `slow cold-start that outlasts the first retry still auto-recovers`() =
        runTest(testDispatcher) {
            var calls = 0
            wheneverBlocking {
                postRepository.getFeedPage(any(), any(), anyOrNull(), any(), anyOrNull(), any())
            }.doSuspendableAnswer {
                calls++
                // Waking from a long sleep, the process was killed and this is a
                // cold start: App Check / Play Integrity and a cold Functions
                // instance can stay unready for several seconds — longer than a
                // single retry covers. The first two attempts blip; the third,
                // once warm, succeeds.
                if (calls <= 2) throw RuntimeException("cold-start still warming up")
                CloudFunctionsDataSource.FeedPage(listOf(post("p1"), post("p2")), false)
            }

            val viewModel = vm(connected = true)
            advanceUntilIdle()

            viewModel.loadFeed()
            advanceUntilIdle()

            // The device was online the whole time, so the reconnect handler never
            // fires — the silent retries are the only automatic recovery. Without
            // more than one, the user is stranded on the error panel until they tap
            // Retry (the reported "sticks until Retry" bug).
            assertEquals(3, calls)
            assertFalse(viewModel.lastLoadFailed.value)
            assertEquals(listOf("p1", "p2"), viewModel.posts.value.map { it.id })
        }

    @Test
    fun `persistent failure while online surfaces the error after the retries are exhausted`() =
        runTest(testDispatcher) {
            var calls = 0
            wheneverBlocking {
                postRepository.getFeedPage(any(), any(), anyOrNull(), any(), anyOrNull(), any())
            }.doSuspendableAnswer {
                calls++
                throw RuntimeException("server still unreachable")
            }

            val viewModel = vm(connected = true)
            advanceUntilIdle()

            viewModel.loadFeed()
            advanceUntilIdle()

            // One initial attempt + FEED_TRANSIENT_MAX_RETRIES silent retries, then
            // the error surfaces (a genuine, persistent outage — not a warm-up
            // blip). The screen keys its copy off isConnected (true here), so the
            // user sees "something's off on our end," not a wifi blame.
            assertEquals(5, calls)
            assertTrue(viewModel.lastLoadFailed.value)
            assertTrue(viewModel.posts.value.isEmpty())
        }

    @Test
    fun `cold start reading offline at first failure still retries within the grace and recovers`() =
        runTest(testDispatcher) {
            var calls = 0
            wheneverBlocking {
                postRepository.getFeedPage(any(), any(), anyOrNull(), any(), anyOrNull(), any())
            }.doSuspendableAnswer {
                calls++
                // Waking the phone, the app cold-starts before the Wi-Fi radio has
                // re-associated, so NetworkMonitor momentarily seeds isConnected
                // false and the first call fails. The radio comes up during the
                // grace and the retry succeeds. The user must NOT see an error
                // flash — it should load like any normal launch.
                if (calls == 1) throw RuntimeException("radio not ready yet")
                CloudFunctionsDataSource.FeedPage(listOf(post("p1")), false)
            }

            val viewModel = vm(connected = false)
            advanceUntilIdle()

            viewModel.loadFeed()
            advanceUntilIdle()

            // The connectivity grace retries despite the offline reading, and the
            // load recovers with no error surfaced.
            assertEquals(2, calls)
            assertFalse(viewModel.lastLoadFailed.value)
            assertEquals(listOf("p1"), viewModel.posts.value.map { it.id })
        }

    @Test
    fun `failure while genuinely offline surfaces after the connectivity grace`() =
        runTest(testDispatcher) {
            var calls = 0
            wheneverBlocking {
                postRepository.getFeedPage(any(), any(), anyOrNull(), any(), anyOrNull(), any())
            }.doSuspendableAnswer {
                calls++
                throw RuntimeException("no network")
            }

            val viewModel = vm(connected = false)
            advanceUntilIdle()

            viewModel.loadFeed()
            advanceUntilIdle()

            // Genuinely offline: the connectivity grace gives the radio a brief
            // chance to come up (so a normal wake never flashes an error), then the
            // failure surfaces with the "check your internet" copy. One initial
            // attempt + FEED_TRANSIENT_CONNECTIVITY_GRACE_RETRIES grace retries.
            assertEquals(3, calls)
            assertTrue(viewModel.lastLoadFailed.value)
        }
}
