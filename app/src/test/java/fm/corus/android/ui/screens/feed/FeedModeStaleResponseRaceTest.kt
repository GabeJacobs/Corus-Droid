package fm.corus.android.ui.screens.feed

import fm.corus.android.data.model.CymbalPost
import fm.corus.android.data.model.CymbalTrack
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
import kotlinx.coroutines.CompletableDeferred
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
import org.mockito.kotlin.doSuspendableAnswer
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.mockito.kotlin.wheneverBlocking

/**
 * Regression: switching feed modes rapidly must never show the wrong feed. A
 * fetch started under one mode can resolve *after* the user has switched to
 * another mode; the in-flight response from the now-deselected mode must be
 * discarded rather than written onto the feed the user is currently viewing.
 *
 * Without the mode guard in loadFeedSuspending, a slow Following response that
 * lands after the user has moved to Trending would append Following posts into
 * the Trending feed — the user taps Trending but sees Following content.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class FeedModeStaleResponseRaceTest {

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
            on { hiddenUserIds } doReturn MutableStateFlow(emptySet())
            on { followingLoaded } doReturn MutableStateFlow(true)
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
            // Synchronous seed read during construction; unstubbed it returns
            // null and resolveFeedMode() NPEs. "" resolves to the "following"
            // default (an unmirrored fresh install).
            on { feedModeSyncSeed() } doReturn ""
            on { forYouSeenIdsJson } doReturn MutableStateFlow("[]")
            on { hasTappedAlbumArt } doReturn MutableStateFlow(false)
            on { hasConfirmedFeedPlaylist } doReturn MutableStateFlow(false)
            on { playFullSongs } doReturn MutableStateFlow(false)
            on { feedFilterSyncSeed() } doReturn "ALL"
            on { feedDecadeSyncSeed() } doReturn ""
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
    fun `late following response is discarded after switching to trending`() =
        runTest(testDispatcher) {
            whenever(remoteConfig.trendingFeedEnabled).doReturn(true)

            val followingPosts = listOf(post("f1"), post("f2"))
            val trendingPosts = listOf(post("t1"))

            // The Following fetch blocks in-flight until the test releases it,
            // mimicking a slow response that lands after the mode has changed.
            val gate = CompletableDeferred<Unit>()
            wheneverBlocking {
                postRepository.getFeedPage(any(), any(), anyOrNull(), any(), anyOrNull(), any())
            }.doSuspendableAnswer {
                gate.await()
                CloudFunctionsDataSource.FeedPage(followingPosts, false)
            }
            wheneverBlocking {
                postRepository.getForYouFeed(
                    any(), any(), anyOrNull(), any(), any(), anyOrNull(), any(), any(), any(), anyOrNull(),
                )
            }.doReturn(CloudFunctionsDataSource.ForYouFeedPage(trendingPosts, false, "tok", false))

            val viewModel = vm()
            advanceUntilIdle()

            // Following load starts and parks on the gate (response in flight).
            viewModel.loadFeed()
            advanceUntilIdle()
            assertEquals("following", viewModel.feedMode.value)

            // User switches to Trending; that load resolves and renders.
            modeFlow.value = "trending"
            advanceUntilIdle()
            assertEquals("trending", viewModel.feedMode.value)
            assertEquals(
                trendingPosts.map { it.id },
                viewModel.posts.value.map { it.id },
            )

            // The slow Following response finally lands — it must be dropped,
            // not merged into the Trending feed the user is now viewing.
            gate.complete(Unit)
            advanceUntilIdle()
            assertEquals(
                trendingPosts.map { it.id },
                viewModel.posts.value.map { it.id },
            )
        }

    /**
     * The subtler ranked→ranked variant. Trending and Taste Matches SHARE the
     * ranked pagination cursor (`forYouSessionToken`) and seen-ID set. A Taste
     * Matches fetch that lands *after* the user switched to Trending must not
     * write its session token into that shared field: the backend keys the
     * ranked pool off the token, so the next Trending page would paginate with
     * the Taste Matches cursor and splice Taste Matches posts into Trending.
     *
     * The posts-level guard alone does NOT catch this — the stale response's own
     * posts are dropped, but the poisoned cursor only bites on the *next* page.
     * The mock below models the backend faithfully: a request carrying the
     * "tm-token" cursor returns Taste Matches posts regardless of the `scope`
     * argument, so a polluted cursor surfaces as Taste Matches ids in Trending.
     */
    @Test
    fun `late taste matches response must not pollute the trending pagination cursor`() =
        runTest(testDispatcher) {
            whenever(remoteConfig.trendingFeedEnabled).doReturn(true)
            whenever(remoteConfig.tasteMatchesEnabled).doReturn(true)

            val tmPage = listOf(post("tm1"))
            val tmLeaked = listOf(post("tmLeak1"))
            val trendingPage1 = listOf(post("tr1"))
            val trendingPage2 = listOf(post("tr2"))

            // The Taste Matches fetch parks in-flight until released, mimicking a
            // slow response that lands after the user has moved to Trending.
            val tmGate = CompletableDeferred<Unit>()
            wheneverBlocking {
                postRepository.getForYouFeed(
                    any(), any(), anyOrNull(), any(), any(), anyOrNull(), any(), any(), any(), anyOrNull(),
                )
            }.doSuspendableAnswer {
                val sessionToken = it.getArgument<String?>(2)
                val scope = it.getArgument<String>(7)
                when {
                    scope == "tasteMatches" -> {
                        tmGate.await()
                        CloudFunctionsDataSource.ForYouFeedPage(tmPage, true, "tm-token", false)
                    }
                    // Backend honors the (stale) Taste Matches cursor: the pool it
                    // returns follows the token, not the requested scope.
                    sessionToken == "tm-token" ->
                        CloudFunctionsDataSource.ForYouFeedPage(tmLeaked, true, "tm-token-2", false)
                    sessionToken == null ->
                        CloudFunctionsDataSource.ForYouFeedPage(trendingPage1, true, "trending-token", false)
                    sessionToken == "trending-token" ->
                        CloudFunctionsDataSource.ForYouFeedPage(trendingPage2, true, "trending-token-2", false)
                    else -> CloudFunctionsDataSource.ForYouFeedPage(emptyList(), false, "", false)
                }
            }

            // Open on Taste Matches; that ranked load parks on the gate.
            modeFlow.value = "tasteMatches"
            val viewModel = vm()
            advanceUntilIdle()
            viewModel.loadFeed()
            advanceUntilIdle()
            assertEquals("tasteMatches", viewModel.feedMode.value)

            // User switches to Trending before Taste Matches resolves; Trending
            // loads page 1 and takes over the (shared) cursor.
            modeFlow.value = "trending"
            advanceUntilIdle()
            assertEquals("trending", viewModel.feedMode.value)
            assertEquals(trendingPage1.map { it.id }, viewModel.posts.value.map { it.id })

            // The slow Taste Matches response finally lands. Its posts are dropped
            // by the posts guard, but it must ALSO not overwrite the shared cursor.
            tmGate.complete(Unit)
            advanceUntilIdle()
            assertEquals(trendingPage1.map { it.id }, viewModel.posts.value.map { it.id })

            // Paginate Trending. With the cursor intact this fetches Trending
            // page 2; a polluted "tm-token" cursor would instead surface Taste
            // Matches posts here.
            viewModel.loadFeed()
            advanceUntilIdle()
            assertEquals(
                listOf("tr1", "tr2"),
                viewModel.posts.value.map { it.id },
            )
        }
}
