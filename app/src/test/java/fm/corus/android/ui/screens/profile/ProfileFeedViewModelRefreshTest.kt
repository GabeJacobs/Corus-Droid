package fm.corus.android.ui.screens.profile

import android.content.Context
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
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.isNull
import org.mockito.kotlin.atLeastOnce
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.util.Date

/**
 * Verifies pull-to-refresh re-fetches the first page (replacing posts so
 * engagement counts come from the latest server snapshot, and any new
 * posts the user just published show up at the top) and never just
 * appends the way [ProfileFeedViewModel.loadMore] does.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ProfileFeedViewModelRefreshTest {

    private val testDispatcher = StandardTestDispatcher()

    private lateinit var context: Context
    private lateinit var authRepository: AuthRepository
    private lateinit var cloudFunctions: CloudFunctionsDataSource
    private lateinit var postRepository: PostRepository
    private lateinit var userRepository: UserRepository
    private lateinit var messageRepository: MessageRepository
    private lateinit var engagementManager: PostEngagementManager
    private lateinit var postDeletionEvent: PostDeletionEvent
    private lateinit var tmdbApiService: TMDBApiService
    private lateinit var nowPlayingManager: NowPlayingManager
    private lateinit var remoteConfig: RemoteConfigService
    private lateinit var analyticsService: AnalyticsService

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        context = mock()
        authRepository = mock {
            on { currentUserId } doReturn "viewer1"
        }
        cloudFunctions = mock()
        postRepository = mock()
        userRepository = mock()
        messageRepository = mock()
        engagementManager = mock {
            on { states } doReturn MutableStateFlow(emptyMap())
        }
        postDeletionEvent = mock {
            on { events } doReturn MutableSharedFlow()
        }
        tmdbApiService = mock()
        nowPlayingManager = mock()
        remoteConfig = mock()
        analyticsService = mock()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel(): ProfileFeedViewModel = ProfileFeedViewModel(
        context = context,
        authRepository = authRepository,
        cloudFunctions = cloudFunctions,
        postRepository = postRepository,
        userRepository = userRepository,
        messageRepository = messageRepository,
        engagementManager = engagementManager,
        postDeletionEvent = postDeletionEvent,
        commentEditedEvent = fm.corus.android.domain.CommentEditedEvent(),
        commentDeletedEvent = fm.corus.android.domain.CommentDeletedEvent(),
        tmdbApiService = tmdbApiService,
        nowPlayingManager = nowPlayingManager,
        feedScrollRouter = fm.corus.android.domain.FeedScrollRouter(),
        remoteConfig = remoteConfig,
        analyticsService = analyticsService,
        preferencesDataStore = mock {
            on { feedFollowsNowPlaying } doReturn MutableStateFlow(true)
        },
        musicServicePreference = mock(),
    )

    private fun makePost(id: String, mediaType: MediaType = MediaType.TRACK): CymbalPost = CymbalPost(
        id = id,
        user = CymbalUser(id = "u1", username = "u1", displayName = "U1"),
        track = CymbalTrack(id = "t_$id", name = "T $id", artistName = "A", albumName = "Alb"),
        timestamp = Date(),
        mediaType = mediaType,
        likeCount = 0,
        commentCount = 0,
    )

    @Test
    fun `refresh on songs feed re-fetches first page with null lastTimestamp`() = runTest {
        // Seed the feed via cache (simulates navigation from grid → feed).
        ProfileFeedCache.posts = listOf(makePost("p1"), makePost("p2"))
        ProfileFeedCache.hasMore = true

        val refreshed = listOf(
            makePost("p_new"),  // user just posted — appears on top
            makePost("p1").copy(likeCount = 42, commentCount = 7),  // counts updated
        )
        whenever(
            cloudFunctions.getProfilePosts(
                userId = eq("u1"),
                viewerId = eq("viewer1"),
                limit = any(),
                lastTimestamp = isNull(),
                mediaType = anyOrNull(),
            )
        ).doReturn(refreshed)

        val vm = createViewModel()
        vm.initFeed("u1", segment = 0)
        advanceUntilIdle()
        assertEquals(listOf("p1", "p2"), vm.posts.value.map { it.id })

        vm.refresh()
        advanceUntilIdle()

        // Posts list is *replaced* with the refreshed first page — the old
        // p2 (which wasn't in the refreshed result) is gone, the new post
        // surfaces at the top, and the existing post's counts come through.
        assertEquals(listOf("p_new", "p1"), vm.posts.value.map { it.id })
        assertEquals(42, vm.posts.value[1].likeCount)
        assertFalse(vm.isRefreshing.value)

        verify(cloudFunctions).getProfilePosts(
            userId = eq("u1"),
            viewerId = eq("viewer1"),
            limit = any(),
            lastTimestamp = isNull(),
            mediaType = anyOrNull(),
        )
    }

    @Test
    fun `refresh on hashtag feed re-fetches first page with null beforeMs`() = runTest {
        ProfileFeedCache.posts = listOf(makePost("h1"))
        ProfileFeedCache.hasMore = false

        whenever(
            cloudFunctions.getHashtagPosts(
                hashtag = eq("vinyl"),
                pageSize = any(),
                beforeMs = isNull(),
            )
        ).doReturn(
            CloudFunctionsDataSource.HashtagPostsPage(
                posts = listOf(makePost("h_new"), makePost("h1")),
                totalCount = 2,
                hasMore = true,
            )
        )

        val vm = createViewModel()
        vm.initFeed(userId = "", segment = 4, hashtag = "vinyl")
        advanceUntilIdle()

        vm.refresh()
        advanceUntilIdle()

        assertEquals(listOf("h_new", "h1"), vm.posts.value.map { it.id })
        assertEquals(true, vm.hasMore.value)

        verify(cloudFunctions).getHashtagPosts(
            hashtag = eq("vinyl"),
            pageSize = any(),
            beforeMs = isNull(),
        )
    }

    @Test
    fun `profile feed opens NO per-post real-time listeners (iOS parity)`() = runTest {
        // Regression for the read-cost fix: list surfaces (feed + profile feeds)
        // must NOT open a live Firestore listener per post. That was the dominant
        // source of /posts reads (~30 open listeners per feed page, re-attached on
        // scroll). Counts render from the denormalized post fields; live per-post
        // listeners live ONLY in PostDetail / Comments — matching iOS.
        ProfileFeedCache.posts = listOf(makePost("p1"), makePost("p2"))
        ProfileFeedCache.hasMore = true
        whenever(
            cloudFunctions.getProfilePosts(
                userId = eq("u1"),
                viewerId = eq("viewer1"),
                limit = any(),
                lastTimestamp = isNull(),
                mediaType = anyOrNull(),
            )
        ).doReturn(listOf(makePost("p1"), makePost("p2")))

        val vm = createViewModel()
        vm.initFeed("u1", segment = 0)
        advanceUntilIdle()
        vm.refresh() // exercises the load path that used to attach listeners
        advanceUntilIdle()

        assertEquals(listOf("p1", "p2"), vm.posts.value.map { it.id })
        // THE regression: zero per-post listeners on a list surface.
        verify(engagementManager, never()).startListening(any())
        // But the cheap batched like/save reconcile still runs (one query each),
        // so the heart/bookmark fill state stays correct.
        verify(engagementManager, atLeastOnce()).checkLikeStatuses(any(), any())
        verify(engagementManager, atLeastOnce()).checkSaveStatuses(any(), any())
    }
}
