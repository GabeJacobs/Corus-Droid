package fm.corus.android.ui.screens.profile

import fm.corus.android.data.model.CymbalPost
import fm.corus.android.data.model.CymbalTrack
import fm.corus.android.data.model.CymbalUser
import fm.corus.android.data.model.MediaType
import fm.corus.android.data.remote.CloudFunctionsDataSource
import fm.corus.android.data.repository.AuthRepository
import fm.corus.android.data.repository.SubscriptionRepository
import fm.corus.android.data.repository.UserRepository
import fm.corus.android.domain.NowPlayingManager
import fm.corus.android.domain.PostCreationEvent
import fm.corus.android.domain.PostDeletionEvent
import fm.corus.android.domain.PostEngagementManager
import fm.corus.android.service.AnalyticsService
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
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.util.Date

/**
 * Verifies the throttled featured-post refresh fired when the user re-enters
 * the profile tab. The throttle exists to keep Firestore costs bounded — users
 * can tap the profile tab very frequently, and we only want one refetch per
 * minute, with pull-to-refresh as the always-fresh escape hatch.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ProfileViewModelFeaturedRefreshTest {

    private val testDispatcher = StandardTestDispatcher()

    private lateinit var authRepository: AuthRepository
    private lateinit var cloudFunctions: CloudFunctionsDataSource
    private lateinit var userRepository: UserRepository
    private lateinit var subscriptionRepository: SubscriptionRepository
    private lateinit var nowPlayingManager: NowPlayingManager
    private lateinit var engagementManager: PostEngagementManager
    private lateinit var postCreationEvent: PostCreationEvent
    private lateinit var postDeletionEvent: PostDeletionEvent
    private lateinit var analyticsService: AnalyticsService

    private var fakeNow: Long = 1_000_000L

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        authRepository = mockAuth("user1")
        cloudFunctions = org.mockito.kotlin.mock()
        userRepository = org.mockito.kotlin.mock()
        subscriptionRepository = org.mockito.kotlin.mock {
            on { isClubMember } doReturn MutableStateFlow(false)
            on { hasFullAccessFlow } doReturn MutableStateFlow(false)
        }
        nowPlayingManager = org.mockito.kotlin.mock()
        engagementManager = org.mockito.kotlin.mock()
        postCreationEvent = org.mockito.kotlin.mock {
            on { events } doReturn MutableSharedFlow()
        }
        postDeletionEvent = org.mockito.kotlin.mock {
            on { events } doReturn MutableSharedFlow()
        }
        analyticsService = org.mockito.kotlin.mock()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun mockAuth(uid: String): AuthRepository = org.mockito.kotlin.mock {
        on { currentUserId } doReturn uid
        on { userProfile } doReturn MutableStateFlow<CymbalUser?>(null)
    }

    private fun createViewModel(): ProfileViewModel = ProfileViewModel(
        context = org.mockito.kotlin.mock(),
        authRepository = authRepository,
        cloudFunctions = cloudFunctions,
        userRepository = userRepository,
        messageRepository = org.mockito.kotlin.mock(),
        subscriptionRepository = subscriptionRepository,
        nowPlayingManager = nowPlayingManager,
        engagementManager = engagementManager,
        postCreationEvent = postCreationEvent,
        postDeletionEvent = postDeletionEvent,
        commentEditedEvent = fm.corus.android.domain.CommentEditedEvent(),
        commentDeletedEvent = fm.corus.android.domain.CommentDeletedEvent(),
        saveChangedEvent = fm.corus.android.domain.SaveChangedEvent(),
        analyticsService = analyticsService,
        musicServicePreference = org.mockito.kotlin.mock(),
        remoteConfigService = org.mockito.kotlin.mock(),
        networkMonitor = org.mockito.kotlin.mock {
            on { isConnected } doReturn kotlinx.coroutines.flow.MutableStateFlow(true)
        },
        ownProfileLaunchCache = OwnProfileLaunchCache(cloudFunctions),
    ).also { it.clock = { fakeNow } }

    private fun makePost(id: String, mediaType: MediaType = MediaType.TRACK, likeCount: Int = 0): CymbalPost = CymbalPost(
        id = id,
        user = CymbalUser(id = "user1", username = "user1", displayName = "U1"),
        track = CymbalTrack(id = "t_$id", name = "T $id", artistName = "A", albumName = "Alb"),
        timestamp = Date(),
        mediaType = mediaType,
        likeCount = likeCount,
        commentCount = 0,
    )

    @Test
    fun `refreshFeaturedPostsIfStale skips when no posts are loaded`() = runTest {
        val vm = createViewModel()

        vm.refreshFeaturedPostsIfStale()
        advanceUntilIdle()

        verify(cloudFunctions, never()).getPostDetail(any(), any())
    }

    @Test
    fun `refreshFeaturedPostsIfStale is throttled immediately after loadProfile`() = runTest {
        whenever(
            cloudFunctions.getProfilePosts(
                userId = eq("user1"),
                viewerId = eq("user1"),
                limit = any(),
                lastTimestamp = anyOrNull(),
                mediaType = anyOrNull(),
            ),
        ).doReturn(listOf(makePost("p1"), makePost("p2", MediaType.MOVIE)))

        val vm = createViewModel()
        vm.loadProfile()
        advanceUntilIdle()

        // loadProfile just stamped lastFeaturedRefreshAt; tab-activation within
        // the throttle window must not fire another fetch.
        vm.refreshFeaturedPostsIfStale()
        advanceUntilIdle()

        verify(cloudFunctions, never()).getPostDetail(any(), any())
    }

    @Test
    fun `refreshFeaturedPostsIfStale fetches latest track and movie posts after throttle window expires`() = runTest {
        whenever(
            cloudFunctions.getProfilePosts(
                userId = eq("user1"),
                viewerId = eq("user1"),
                limit = any(),
                lastTimestamp = anyOrNull(),
                mediaType = anyOrNull(),
            ),
        ).doReturn(
            listOf(
                makePost("track1", likeCount = 5),
                makePost("track2"),
                makePost("movie1", MediaType.MOVIE, likeCount = 3),
            ),
        )
        whenever(cloudFunctions.getPostDetail(eq("track1"), eq("user1")))
            .doReturn(makePost("track1", likeCount = 42))
        whenever(cloudFunctions.getPostDetail(eq("movie1"), eq("user1")))
            .doReturn(makePost("movie1", MediaType.MOVIE, likeCount = 17))

        val vm = createViewModel()
        vm.loadProfile()
        advanceUntilIdle()

        // Advance past the 60s throttle window.
        fakeNow += 61_000L
        vm.refreshFeaturedPostsIfStale()
        advanceUntilIdle()

        // Only the FEATURED (latest) posts of each media type — never every
        // post on the screen — and only one read per featured post.
        verify(cloudFunctions, times(1)).getPostDetail(eq("track1"), eq("user1"))
        verify(cloudFunctions, times(1)).getPostDetail(eq("movie1"), eq("user1"))
        verify(cloudFunctions, never()).getPostDetail(eq("track2"), any())

        // The fresh likeCount lands in the engagement manager so the featured
        // tile re-renders with the updated number.
        verify(engagementManager).initState(
            postId = eq("track1"),
            likeCount = eq(42),
            commentCount = any(),
            repostCount = any(),
            isLiked = any(),
            isSaved = any(),
        )
        verify(engagementManager).initState(
            postId = eq("movie1"),
            likeCount = eq(17),
            commentCount = any(),
            repostCount = any(),
            isLiked = any(),
            isSaved = any(),
        )

        // Public posts state also reflects the refreshed counts so a later
        // pull-to-refresh seed comparison or filter-by-segment works correctly.
        val refreshedTrack = vm.posts.value.first { it.id == "track1" }
        val refreshedMovie = vm.posts.value.first { it.id == "movie1" }
        assertEquals(42, refreshedTrack.likeCount)
        assertEquals(17, refreshedMovie.likeCount)
    }

    @Test
    fun `refreshFeaturedPostsIfStale stamps timestamp before launching fetches to prevent retry storms`() = runTest {
        whenever(
            cloudFunctions.getProfilePosts(
                userId = eq("user1"),
                viewerId = eq("user1"),
                limit = any(),
                lastTimestamp = anyOrNull(),
                mediaType = anyOrNull(),
            ),
        ).doReturn(listOf(makePost("p1")))
        whenever(cloudFunctions.getPostDetail(eq("p1"), eq("user1"))).doReturn(makePost("p1"))

        val vm = createViewModel()
        vm.loadProfile()
        advanceUntilIdle()

        fakeNow += 61_000L

        // Three rapid taps during the same throttle window: only the first
        // should escape — the next two must short-circuit even before the
        // first fetch resolves, otherwise users tapping the profile tab
        // quickly could trigger N reads instead of 1.
        vm.refreshFeaturedPostsIfStale()
        vm.refreshFeaturedPostsIfStale()
        vm.refreshFeaturedPostsIfStale()
        advanceUntilIdle()

        verify(cloudFunctions, times(1)).getPostDetail(eq("p1"), eq("user1"))
    }
}
