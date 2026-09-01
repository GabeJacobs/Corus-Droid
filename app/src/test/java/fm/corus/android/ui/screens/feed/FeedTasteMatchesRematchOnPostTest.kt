package fm.corus.android.ui.screens.feed

import fm.corus.android.data.model.CymbalPost
import fm.corus.android.data.model.CymbalTrack
import fm.corus.android.data.model.CymbalUser
import fm.corus.android.data.model.MediaType
import fm.corus.android.data.remote.CloudFunctionsDataSource
import fm.corus.android.data.remote.TMDBApiService
import fm.corus.android.data.repository.AuthRepository
import fm.corus.android.data.repository.MessageRepository
import fm.corus.android.data.repository.PostRepository
import fm.corus.android.data.repository.UserRepository
import fm.corus.android.domain.NowPlayingManager
import fm.corus.android.domain.PostCreated
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
import org.mockito.kotlin.doSuspendableAnswer
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.mockito.kotlin.wheneverBlocking

/**
 * Regression: creating a post while sitting on the Taste Matches "no matches
 * yet" empty state must surface a freshly-matched feed WITHOUT a manual
 * pull-to-refresh. The just-posted artist/director can now overlap someone
 * else's taste, but the server rebuilds that overlap from an async trigger, so
 * the feed polls the ranked endpoint until it serves (or gives up and settles
 * back on the empty state). Before the fix a post on this state did nothing —
 * the user was stranded on "no matches yet" and wrongly assumed they had none.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class FeedTasteMatchesRematchOnPostTest {

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
    private lateinit var postDeletionEvent: PostDeletionEvent
    private lateinit var preferencesDataStore: fm.corus.android.data.local.PreferencesDataStore

    // Post-creation bus we drive directly so the test can fire a "posted" event.
    private val postEvents = MutableSharedFlow<PostCreated>(extraBufferCapacity = 1)
    private val postCreationEvent: PostCreationEvent = mock { on { events } doReturn postEvents }

    private val modeFlow = MutableStateFlow("tasteMatches")

    // When true the ranked endpoint serves matches; when false it returns the
    // gated "noMatchesYet" response (posted enough, but no shared taste yet).
    private var serving = false

    private fun user(id: String) = CymbalUser(id = id, username = id, displayName = id)
    private fun track() = CymbalTrack(id = "t1", name = "n", artistName = "a", albumName = "al")
    private fun post(id: String) = CymbalPost(id = id, user = user("poster"), track = track())

    private val matchedPosts = listOf(post("m1"), post("m2"))

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
            on { hiddenUserIds } doReturn MutableStateFlow(emptySet())
            on { followingLoaded } doReturn MutableStateFlow(true)
        }
        messageRepository = mock()
        cloudFunctions = mock()
        tmdbApiService = mock()
        nowPlayingManager = mock()
        remoteConfig = mock()
        analyticsService = mock()
        postDeletionEvent = mock { on { events } doReturn MutableSharedFlow() }
        preferencesDataStore = mock {
            on { feedFollowsNowPlaying } doReturn MutableStateFlow(true)
            on { feedFilter } doReturn MutableStateFlow("ALL")
            on { feedMode } doReturn modeFlow
            on { feedModeSyncSeed() } doReturn "tasteMatches"
            on { forYouSeenIdsJson } doReturn MutableStateFlow("[]")
            on { hasTappedAlbumArt } doReturn MutableStateFlow(false)
            on { hasConfirmedFeedPlaylist } doReturn MutableStateFlow(false)
            on { playFullSongs } doReturn MutableStateFlow(false)
            on { feedFilterSyncSeed() } doReturn "ALL"
            on { feedDecadeSyncSeed() } doReturn ""
        }
        whenever(remoteConfig.tasteMatchesEnabled).doReturn(true)

        // The ranked endpoint answers by the `serving` flag: gated no-matches
        // until a post shifts the viewer's taste, then a served page.
        wheneverBlocking {
            postRepository.getForYouFeed(
                any(), any(), anyOrNull(), any(), any(), anyOrNull(), any(), any(), any(), anyOrNull(),
            )
        }.doSuspendableAnswer {
            if (serving) {
                CloudFunctionsDataSource.ForYouFeedPage(matchedPosts, false, "tok", false)
            } else {
                CloudFunctionsDataSource.ForYouFeedPage(
                    emptyList(), false, "", false, "noMatchesYet", 6, 3,
                )
            }
        }
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
    fun `posting flips the no-matches-yet feed to a served feed without pull-to-refresh`() =
        runTest(testDispatcher) {
            val viewModel = vm()
            advanceUntilIdle()
            viewModel.loadFeed()
            advanceUntilIdle()

            // Baseline: server says posted-enough-but-no-shared-taste.
            assertEquals(
                FeedViewModel.TasteMatchesGate.NoMatchesYet,
                viewModel.tasteMatchesGate.value,
            )
            assertTrue(viewModel.posts.value.isEmpty())

            // The user posts from the empty state; that post now shares taste
            // with someone, so the next ranked fetch serves matches.
            serving = true
            postEvents.emit(PostCreated(MediaType.TRACK))
            advanceUntilIdle()

            // Feed auto-refreshed into the served matches — no manual refresh.
            assertEquals(matchedPosts.map { it.id }, viewModel.posts.value.map { it.id })
            assertNull(viewModel.tasteMatchesGate.value)
            // Loading skeleton released once the feed served.
            assertFalse(viewModel.tasteMatchesSeeding.value)
        }

    @Test
    fun `posting that still yields no matches settles back on the empty state`() =
        runTest(testDispatcher) {
            val viewModel = vm()
            advanceUntilIdle()
            viewModel.loadFeed()
            advanceUntilIdle()

            assertEquals(
                FeedViewModel.TasteMatchesGate.NoMatchesYet,
                viewModel.tasteMatchesGate.value,
            )

            // Post something nobody else shares: the bounded poll must exhaust
            // and quietly restore the "no matches yet" state (no hung skeleton).
            postEvents.emit(PostCreated(MediaType.TRACK))
            advanceUntilIdle()

            assertEquals(
                FeedViewModel.TasteMatchesGate.NoMatchesYet,
                viewModel.tasteMatchesGate.value,
            )
            assertTrue(viewModel.posts.value.isEmpty())
            assertFalse(viewModel.tasteMatchesSeeding.value)
        }
}
