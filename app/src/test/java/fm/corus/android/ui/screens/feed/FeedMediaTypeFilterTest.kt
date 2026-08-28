package fm.corus.android.ui.screens.feed

import fm.corus.android.data.model.CymbalPost
import fm.corus.android.data.model.CymbalTrack
import fm.corus.android.data.model.CymbalUser
import fm.corus.android.data.model.FeedFilter
import fm.corus.android.data.model.MediaType
import fm.corus.android.data.remote.CloudFunctionsDataSource
import fm.corus.android.data.remote.TMDBApiService
import fm.corus.android.data.repository.AuthRepository
import fm.corus.android.data.repository.MessageRepository
import fm.corus.android.data.repository.PostRepository
import fm.corus.android.data.repository.SubscriptionRepository
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
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
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
    private lateinit var postDeletionEvent: PostDeletionEvent
    private lateinit var preferencesDataStore: fm.corus.android.data.local.PreferencesDataStore
    private lateinit var savedFeedFilter: MutableStateFlow<String>

    private fun user(id: String) = CymbalUser(id = id, username = id, displayName = id)
    private fun track() = CymbalTrack(id = "t1", name = "n", artistName = "a", albumName = "al")
    private fun post(id: String) = CymbalPost(id = id, user = user("poster"), track = track())

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        savedFeedFilter = MutableStateFlow("ALL")
        preferencesDataStore = mock {
            on { feedFollowsNowPlaying } doReturn MutableStateFlow(true)
            on { feedFilter } doReturn savedFeedFilter
            on { feedMode } doReturn MutableStateFlow("following")
            // Synchronous seed read during construction; unstubbed it returns
            // null and resolveFeedMode() NPEs. "" resolves to "following".
            on { feedModeSyncSeed() } doReturn ""
            // FeedViewModel's init collects these too; stub them so they emit a
            // real value instead of a null Flow (null upstreams NPE the eager
            // stateIn / .first() during runTest's coroutine drain on the CLI).
            on { forYouSeenIdsJson } doReturn MutableStateFlow("[]")
            on { hasTappedAlbumArt } doReturn MutableStateFlow(false)
            on { hasConfirmedFeedPlaylist } doReturn MutableStateFlow(false)
            on { playFullSongs } doReturn MutableStateFlow(false)
            on { feedFilterSyncSeed() } doReturn "ALL"
            on { feedDecadeSyncSeed() } doReturn ""
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
        remoteConfig = mock()
        analyticsService = mock()
        postCreationEvent = mock {
            on { events } doReturn MutableSharedFlow()
        }
        postDeletionEvent = mock {
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
        favoriteChangedEvent = fm.corus.android.domain.FavoriteChangedEvent(),
        commentEditedEvent = fm.corus.android.domain.CommentEditedEvent(),
        commentDeletedEvent = fm.corus.android.domain.CommentDeletedEvent(),
        musicServicePreference = mock(),
        networkMonitor = mock {
            on { isConnected } doReturn MutableStateFlow(true)
        },
        preferencesDataStore = preferencesDataStore,
        playbackModePromptManager = mock(),
        context = mock(),
        feedSwitchHintManager = mock { on { shouldShow } doReturn MutableStateFlow(false) },
        feedScrollRouter = fm.corus.android.domain.FeedScrollRouter(),
    )

    @Test
    fun `loadFeed forwards active mediaType filter to backend`() = runTest(testDispatcher) {
        whenever(postRepository.getFeedPage(any(), any(), anyOrNull(), any(), anyOrNull(), any()))
            .doReturn(CloudFunctionsDataSource.FeedPage(emptyList(), false))

        val viewModel = vm()
        // Let the init filter-restore coroutine settle before changing the
        // filter — otherwise it runs after our change and clobbers it back to
        // ALL (in production init always settles long before any user tap).
        advanceUntilIdle()
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
            newReleasesOnly = eq(false),
        )
    }

    @Test
    fun `setFeedMediaFilter resets pagination so new filter fetches from the top`() = runTest(testDispatcher) {
        whenever(postRepository.getFeedPage(any(), any(), anyOrNull(), any(), anyOrNull(), any()))
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
            newReleasesOnly = any(),
        )
    }

    @Test
    fun `Music - New Releases forwards mediaType=TRACK and newReleasesOnly=true`() = runTest(testDispatcher) {
        whenever(postRepository.getFeedPage(any(), any(), anyOrNull(), any(), anyOrNull(), any()))
            .doReturn(CloudFunctionsDataSource.FeedPage(emptyList(), false))

        val viewModel = vm()
        advanceUntilIdle() // let init filter-restore settle (see loadFeed test)
        viewModel.setFeedFilter(FeedFilter.MUSIC_NEW_RELEASES)
        advanceUntilIdle()

        // The combined filter must reach the backend as both params; without
        // newReleasesOnly the server would return the regular Music feed.
        verify(postRepository).getFeedPage(
            userId = eq("user1"),
            pageSize = any(),
            lastTimestamp = anyOrNull(),
            onePerFollower = any(),
            mediaType = eq(MediaType.TRACK),
            newReleasesOnly = eq(true),
        )
    }

    @Test
    fun `Film - New Releases forwards mediaType=MOVIE and newReleasesOnly=true`() = runTest(testDispatcher) {
        whenever(postRepository.getFeedPage(any(), any(), anyOrNull(), any(), anyOrNull(), any()))
            .doReturn(CloudFunctionsDataSource.FeedPage(emptyList(), false))

        val viewModel = vm()
        advanceUntilIdle() // let init filter-restore settle (see loadFeed test)
        viewModel.setFeedFilter(FeedFilter.FILM_NEW_RELEASES)
        advanceUntilIdle()

        verify(postRepository).getFeedPage(
            userId = eq("user1"),
            pageSize = any(),
            lastTimestamp = anyOrNull(),
            onePerFollower = any(),
            mediaType = eq(MediaType.MOVIE),
            newReleasesOnly = eq(true),
        )
    }

    @Test
    fun `setFeedFilter logs feed_filter_changed with canonical analytics value`() = runTest(testDispatcher) {
        whenever(postRepository.getFeedPage(any(), any(), anyOrNull(), any(), anyOrNull(), any()))
            .doReturn(CloudFunctionsDataSource.FeedPage(emptyList(), false))

        val viewModel = vm()
        viewModel.setFeedFilter(FeedFilter.MUSIC_NEW_RELEASES)
        advanceUntilIdle()

        verify(analyticsService).logFeedFilterChanged(eq("music_new_releases"))
    }

    @Test
    fun `setFeedFilter does not log when filter is unchanged`() = runTest(testDispatcher) {
        whenever(postRepository.getFeedPage(any(), any(), anyOrNull(), any(), anyOrNull(), any()))
            .doReturn(CloudFunctionsDataSource.FeedPage(emptyList(), false))

        val viewModel = vm()
        // Default state is ALL — selecting ALL again should be a no-op for analytics.
        viewModel.setFeedFilter(FeedFilter.ALL)
        advanceUntilIdle()

        org.mockito.kotlin.verify(analyticsService, org.mockito.kotlin.never()).logFeedFilterChanged(any())
    }

    @Test
    fun `setFeedMode logs feed_mode_changed`() = runTest(testDispatcher) {
        val viewModel = vm()
        // The analytics event fires synchronously, before the async ranked
        // refetch — so we assert without advancing the dispatcher (which keeps
        // the test off the getForYouFeed path that "trending" would trigger).
        viewModel.setFeedMode("trending")

        verify(analyticsService).logFeedModeChanged(eq("trending"))
    }

    @Test
    fun `setFeedMode flips isRefreshing in the same frame it empties the feed`() = runTest(testDispatcher) {
        whenever(postRepository.getFeedPage(any(), any(), anyOrNull(), any(), anyOrNull(), any()))
            .doReturn(CloudFunctionsDataSource.FeedPage(emptyList(), false))

        val viewModel = vm()
        viewModel.setFeedMode("trending")
        // Deliberately do NOT advance the dispatcher: this captures the exact
        // window between clearing the list and the load coroutine running.
        // Regression for the empty-state flash on following -> trending: the
        // feed renders the empty state when posts==[] && hasLoaded && !isLoading
        // && !isRefreshing, so isRefreshing MUST already be true here while the
        // list is empty, or the skeleton loses to the empty state for a frame.
        assert(viewModel.posts.value.isEmpty())
        assert(viewModel.isRefreshing.value) {
            "isRefreshing must be true synchronously after setFeedMode so the " +
                "empty state never flashes before the skeleton"
        }
    }

    @Test
    fun `setFeedFilter flips isRefreshing in the same frame it empties the feed`() = runTest(testDispatcher) {
        whenever(postRepository.getFeedPage(any(), any(), anyOrNull(), any(), anyOrNull(), any()))
            .doReturn(CloudFunctionsDataSource.FeedPage(emptyList(), false))

        val viewModel = vm()
        advanceUntilIdle() // let init filter-restore settle (see loadFeed test)
        viewModel.setFeedFilter(FeedFilter.MUSIC)
        // Same flash window as setFeedMode: capture state before the load
        // coroutine runs. isRefreshing must already be true while posts is empty.
        assert(viewModel.posts.value.isEmpty())
        assert(viewModel.isRefreshing.value) {
            "isRefreshing must be true synchronously after setFeedFilter so the " +
                "empty state never flashes before the skeleton"
        }
    }

    @Test
    fun `setFeedMode does not log when mode is unchanged`() = runTest(testDispatcher) {
        val viewModel = vm()
        // The default resolved mode is "following"; re-selecting it is a no-op.
        viewModel.setFeedMode("following")

        org.mockito.kotlin.verify(analyticsService, org.mockito.kotlin.never()).logFeedModeChanged(any())
    }

    @Test
    fun `All clears both mediaType and newReleasesOnly`() = runTest(testDispatcher) {
        whenever(postRepository.getFeedPage(any(), any(), anyOrNull(), any(), anyOrNull(), any()))
            .doReturn(CloudFunctionsDataSource.FeedPage(emptyList(), false))

        val viewModel = vm()
        advanceUntilIdle() // let init filter-restore settle (see loadFeed test)
        viewModel.setFeedFilter(FeedFilter.MUSIC_NEW_RELEASES)
        advanceUntilIdle()
        viewModel.setFeedFilter(FeedFilter.ALL)
        advanceUntilIdle()

        verify(postRepository).getFeedPage(
            userId = any(),
            pageSize = any(),
            lastTimestamp = anyOrNull(),
            onePerFollower = any(),
            mediaType = eq(null),
            newReleasesOnly = eq(false),
        )
    }

    @Test
    fun `setFeedFilter restores a previously loaded page from cache`() = runTest(testDispatcher) {
        whenever(postRepository.getFeedPage(any(), any(), anyOrNull(), any(), eq(null), any()))
            .doReturn(CloudFunctionsDataSource.FeedPage(listOf(post("all1")), false))
        whenever(postRepository.getFeedPage(any(), any(), anyOrNull(), any(), eq(MediaType.TRACK), any()))
            .doReturn(CloudFunctionsDataSource.FeedPage(listOf(post("music1")), false))

        val viewModel = vm()
        viewModel.loadFeed(refresh = true)
        advanceUntilIdle()
        org.junit.Assert.assertEquals(listOf("all1"), viewModel.posts.value.map { it.id })

        viewModel.setFeedFilter(FeedFilter.MUSIC)
        advanceUntilIdle()
        org.junit.Assert.assertEquals(listOf("music1"), viewModel.posts.value.map { it.id })

        viewModel.setFeedFilter(FeedFilter.ALL)
        // Cache restore is synchronous — ALL must reappear without a second
        // fetch, and without flipping the refresh skeleton.
        org.junit.Assert.assertEquals(listOf("all1"), viewModel.posts.value.map { it.id })
        org.junit.Assert.assertFalse(viewModel.isRefreshing.value)
        verify(postRepository, times(1)).getFeedPage(
            userId = any(),
            pageSize = any(),
            lastTimestamp = anyOrNull(),
            onePerFollower = any(),
            mediaType = eq(null),
            newReleasesOnly = eq(false),
        )
    }

    @Test
    fun `setFeedFilter persists the selection so it survives a restart`() = runTest(testDispatcher) {
        whenever(postRepository.getFeedPage(any(), any(), anyOrNull(), any(), anyOrNull(), any()))
            .doReturn(CloudFunctionsDataSource.FeedPage(emptyList(), false))

        val viewModel = vm()
        viewModel.setFeedFilter(FeedFilter.MUSIC)
        advanceUntilIdle()

        // The enum name is written to DataStore; restoring it on the next launch
        // is what makes the music/film choice sticky across app restarts.
        verify(preferencesDataStore).setFeedFilter(eq("MUSIC"))
    }

    @Test
    fun `seeds the saved filter synchronously on construction so the first load is not ALL`() = runTest(testDispatcher) {
        // A prior session selected "Film only". The synchronous SharedPreferences
        // mirror returns it eagerly, before the async DataStore restore runs.
        whenever(preferencesDataStore.feedFilterSyncSeed()).doReturn(FeedFilter.FILM.name)

        val viewModel = vm()
        // Deliberately do NOT advance the dispatcher: this captures the state the
        // screen's first loadFeed() reads. Regression for the music→film flash —
        // the filter must already be FILM here, not the ALL default, or the
        // initial page fetches all-content posts before the async restore lands.
        org.junit.Assert.assertEquals(FeedFilter.FILM, viewModel.feedFilter.value)
    }

    @Test
    fun `restores persisted filter on init and applies it to the first feed load`() = runTest(testDispatcher) {
        whenever(postRepository.getFeedPage(any(), any(), anyOrNull(), any(), anyOrNull(), any()))
            .doReturn(CloudFunctionsDataSource.FeedPage(emptyList(), false))
        // Simulate a prior session that had selected "Music only".
        savedFeedFilter.value = FeedFilter.MUSIC.name

        val viewModel = vm()
        // Screen drives the initial load after construction.
        viewModel.loadFeed()
        advanceUntilIdle()

        // Without restoration the first page would be fetched with mediaType=null (ALL),
        // dropping the user back to the unfiltered feed on every relaunch.
        verify(postRepository).getFeedPage(
            userId = eq("user1"),
            pageSize = any(),
            lastTimestamp = anyOrNull(),
            onePerFollower = any(),
            mediaType = eq(MediaType.TRACK),
            newReleasesOnly = eq(false),
        )
    }

    @Test
    fun `hasConfirmedFeedPlaylist reflects the persisted flag`() = runTest(testDispatcher) {
        // A returning user who already confirmed once should not be re-prompted.
        whenever(preferencesDataStore.hasConfirmedFeedPlaylist).doReturn(MutableStateFlow(true))

        val viewModel = vm()
        advanceUntilIdle()

        org.junit.Assert.assertTrue(viewModel.hasConfirmedFeedPlaylist.value)
    }

    @Test
    fun `markFeedPlaylistConfirmed persists the flag`() = runTest(testDispatcher) {
        // Confirming the first-time explainer must record the flag so the
        // playlist button runs directly (no explainer) on every later tap.
        val viewModel = vm()
        viewModel.markFeedPlaylistConfirmed()
        advanceUntilIdle()

        org.mockito.kotlin.verifyBlocking(preferencesDataStore) { setHasConfirmedFeedPlaylist() }
    }
}
