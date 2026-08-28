package fm.corus.android.ui.screens.feed

import fm.corus.android.data.model.CymbalUser
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
class FeedDecadeFilterTest {

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
            on { trendingFeedEnabled } doReturn true
            on { feedDecadeFilterEnabled } doReturn true
        }
        analyticsService = mock()
        postCreationEvent = mock { on { events } doReturn MutableSharedFlow() }
        postDeletionEvent = mock { on { events } doReturn MutableSharedFlow() }
        wheneverBlocking {
            postRepository.getForYouFeed(
                any(), any(), anyOrNull(), any(), any(), anyOrNull(), any(), any(), any(), anyOrNull(),
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
    fun `a decade and a media-type filter compose into one Trending request`() =
        runTest(testDispatcher) {
            val viewModel = vm()
            advanceUntilIdle()
            viewModel.setFeedFilter(FeedFilter.MUSIC)
            advanceUntilIdle()
            viewModel.setFeedDecade(1990)
            advanceUntilIdle()

            verifyBlocking(postRepository) {
                getForYouFeed(
                    userId = eq("user1"),
                    pageSize = any(),
                    sessionToken = anyOrNull(),
                    pageIndex = any(),
                    seenPostIds = any(),
                    mediaType = eq(MediaType.TRACK),
                    newReleasesOnly = eq(false),
                    scope = eq("trending"),
                    isRefresh = any(),
                    releaseDecade = eq(1990),
                )
            }
        }

    @Test
    fun `no decade selected sends no decade at all`() = runTest(testDispatcher) {
        val viewModel = vm()
        viewModel.loadFeed()
        advanceUntilIdle()

        verifyBlocking(postRepository) {
            getForYouFeed(
                userId = any(),
                pageSize = any(),
                sessionToken = anyOrNull(),
                pageIndex = any(),
                seenPostIds = any(),
                mediaType = anyOrNull(),
                newReleasesOnly = any(),
                scope = eq("trending"),
                isRefresh = any(),
                releaseDecade = isNull(),
            )
        }
    }

    @Test
    fun `a persisted decade never reaches the server from another ranked feed`() =
        runTest(testDispatcher) {
            whenever(remoteConfig.tasteMatchesEnabled).doReturn(true)
            whenever(preferencesDataStore.feedDecadeSyncSeed()).doReturn("1990")
            storedDecade.value = "1990"
            storedMode.value = "tasteMatches"

            val viewModel = vm()
            advanceUntilIdle()
            viewModel.loadFeed()
            advanceUntilIdle()

            assertEquals("tasteMatches", viewModel.feedMode.value)
            assertNull(viewModel.appliedFeedDecade.value)
            verifyBlocking(postRepository, never()) {
                getForYouFeed(
                    userId = any(),
                    pageSize = any(),
                    sessionToken = anyOrNull(),
                    pageIndex = any(),
                    seenPostIds = any(),
                    mediaType = anyOrNull(),
                    newReleasesOnly = any(),
                    scope = any(),
                    isRefresh = any(),
                    releaseDecade = eq(1990),
                )
            }
        }

    @Test
    fun `a persisted decade never reaches the server while the gate is off`() =
        runTest(testDispatcher) {
            whenever(remoteConfig.feedDecadeFilterEnabled).doReturn(false)
            whenever(preferencesDataStore.feedDecadeSyncSeed()).doReturn("1990")
            storedDecade.value = "1990"

            val viewModel = vm()
            advanceUntilIdle()
            viewModel.loadFeed()
            advanceUntilIdle()

            assertNull(viewModel.appliedFeedDecade.value)
            verifyBlocking(postRepository, never()) {
                getForYouFeed(
                    userId = any(),
                    pageSize = any(),
                    sessionToken = anyOrNull(),
                    pageIndex = any(),
                    seenPostIds = any(),
                    mediaType = anyOrNull(),
                    newReleasesOnly = any(),
                    scope = any(),
                    isRefresh = any(),
                    releaseDecade = eq(1990),
                )
            }
        }

    @Test
    fun `the decade group is offered only on Trending and only while the gate is on`() =
        runTest(testDispatcher) {
            val viewModel = vm()

            assertTrue(viewModel.isDecadeFilterVisible("trending"))
            assertFalse(viewModel.isDecadeFilterVisible("following"))
            assertFalse(viewModel.isDecadeFilterVisible("favorites"))
            assertFalse(viewModel.isDecadeFilterVisible("tasteMatches"))

            whenever(remoteConfig.feedDecadeFilterEnabled).doReturn(false)
            assertFalse(viewModel.isDecadeFilterVisible("trending"))
        }

    @Test
    fun `picking a decade downgrades an active new-releases filter to its media narrowing`() =
        runTest(testDispatcher) {
            val viewModel = vm()
            advanceUntilIdle()
            viewModel.setFeedFilter(FeedFilter.MUSIC_NEW_RELEASES)
            advanceUntilIdle()
            viewModel.setFeedDecade(1990)
            advanceUntilIdle()

            assertEquals(FeedFilter.MUSIC, viewModel.feedFilter.value)
            verifyBlocking(postRepository) {
                getForYouFeed(
                    userId = any(),
                    pageSize = any(),
                    sessionToken = anyOrNull(),
                    pageIndex = any(),
                    seenPostIds = any(),
                    mediaType = eq(MediaType.TRACK),
                    newReleasesOnly = eq(false),
                    scope = eq("trending"),
                    isRefresh = any(),
                    releaseDecade = eq(1990),
                )
            }
            verifyBlocking(preferencesDataStore) { setFeedFilter(eq("MUSIC")) }
            verify(analyticsService, never()).logFeedFilterChanged(eq("music"))
        }

    @Test
    fun `picking a new-releases filter clears an active decade`() = runTest(testDispatcher) {
        val viewModel = vm()
        advanceUntilIdle()
        viewModel.setFeedDecade(1990)
        advanceUntilIdle()
        viewModel.setFeedFilter(FeedFilter.FILM_NEW_RELEASES)
        advanceUntilIdle()

        assertNull(viewModel.appliedFeedDecade.value)
        verify(analyticsService).logFeedDecadeChanged(eq("none"))
        verifyBlocking(preferencesDataStore) { setFeedDecade(eq("")) }
        verifyBlocking(postRepository) {
            getForYouFeed(
                userId = any(),
                pageSize = any(),
                sessionToken = anyOrNull(),
                pageIndex = any(),
                seenPostIds = any(),
                mediaType = eq(MediaType.MOVIE),
                newReleasesOnly = eq(true),
                scope = eq("trending"),
                isRefresh = any(),
                releaseDecade = isNull(),
            )
        }
    }

    @Test
    fun `logs feed_decade_changed with the decade digits and none when cleared`() =
        runTest(testDispatcher) {
            val viewModel = vm()
            advanceUntilIdle()
            viewModel.setFeedDecade(1990)
            advanceUntilIdle()
            viewModel.setFeedDecade(null)
            advanceUntilIdle()

            verify(analyticsService).logFeedDecadeChanged(eq("1990"))
            verify(analyticsService).logFeedDecadeChanged(eq("none"))
        }

    @Test
    fun `does not log or refetch when the decade is unchanged`() = runTest(testDispatcher) {
        val viewModel = vm()
        advanceUntilIdle()
        viewModel.setFeedDecade(null)
        viewModel.setFeedDecade(1990)
        advanceUntilIdle()
        viewModel.setFeedDecade(1990)
        advanceUntilIdle()

        verify(analyticsService, never()).logFeedDecadeChanged(eq("none"))
        verify(analyticsService).logFeedDecadeChanged(eq("1990"))
    }

    @Test
    fun `an unoffered decade is refused rather than sent to the server`() =
        runTest(testDispatcher) {
            val viewModel = vm()
            advanceUntilIdle()
            viewModel.setFeedDecade(1950)
            advanceUntilIdle()

            assertNull(viewModel.appliedFeedDecade.value)
            verify(analyticsService, never()).logFeedDecadeChanged(any())
        }

    @Test
    fun `persists the decade so it survives a restart`() = runTest(testDispatcher) {
        val viewModel = vm()
        advanceUntilIdle()
        viewModel.setFeedDecade(1980)
        advanceUntilIdle()

        verifyBlocking(preferencesDataStore) { setFeedDecade(eq("1980")) }
    }

    @Test
    fun `seeds the persisted decade synchronously so the first frame is narrowed`() =
        runTest(testDispatcher) {
            whenever(preferencesDataStore.feedDecadeSyncSeed()).doReturn("1990")
            storedDecade.value = "1990"

            val viewModel = vm()

            assertEquals(1990, viewModel.appliedFeedDecade.value)
        }

    @Test
    fun `a persisted decade that is no longer offered resolves to no narrowing`() =
        runTest(testDispatcher) {
            whenever(preferencesDataStore.feedDecadeSyncSeed()).doReturn("1950")
            storedDecade.value = "1950"

            val viewModel = vm()
            advanceUntilIdle()
            viewModel.loadFeed()
            advanceUntilIdle()

            assertNull(viewModel.appliedFeedDecade.value)
            verifyBlocking(postRepository) {
                getForYouFeed(
                    userId = any(),
                    pageSize = any(),
                    sessionToken = anyOrNull(),
                    pageIndex = any(),
                    seenPostIds = any(),
                    mediaType = anyOrNull(),
                    newReleasesOnly = any(),
                    scope = eq("trending"),
                    isRefresh = any(),
                    releaseDecade = isNull(),
                )
            }
        }

    @Test
    fun `restores a decade held only in DataStore and narrows the feed with it`() =
        runTest(testDispatcher) {
            storedDecade.value = "1970"

            val viewModel = vm()
            advanceUntilIdle()
            viewModel.loadFeed()
            advanceUntilIdle()

            assertEquals(1970, viewModel.appliedFeedDecade.value)
            verifyBlocking(postRepository) {
                getForYouFeed(
                    userId = any(),
                    pageSize = any(),
                    sessionToken = anyOrNull(),
                    pageIndex = any(),
                    seenPostIds = any(),
                    mediaType = anyOrNull(),
                    newReleasesOnly = any(),
                    scope = eq("trending"),
                    isRefresh = any(),
                    releaseDecade = eq(1970),
                )
            }
        }

    @Test
    fun `setFeedDecade flips isRefreshing in the same frame it empties the feed`() =
        runTest(testDispatcher) {
            val viewModel = vm()
            advanceUntilIdle()
            viewModel.setFeedDecade(1990)

            assertTrue(viewModel.posts.value.isEmpty())
            assertTrue(viewModel.isRefreshing.value)
        }
}
