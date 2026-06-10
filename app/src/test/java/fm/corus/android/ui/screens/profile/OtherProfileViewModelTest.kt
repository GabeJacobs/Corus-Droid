package fm.corus.android.ui.screens.profile

import android.content.Context
import fm.corus.android.data.model.CymbalPost
import fm.corus.android.data.model.CymbalTrack
import fm.corus.android.data.model.CymbalUser
import fm.corus.android.data.model.MediaType
import fm.corus.android.data.remote.CloudFunctionsDataSource
import fm.corus.android.data.repository.AuthRepository
import fm.corus.android.data.repository.PostRepository
import fm.corus.android.data.repository.SubscriptionRepository
import fm.corus.android.data.repository.UserRepository
import fm.corus.android.domain.NowPlayingManager
import fm.corus.android.domain.PostDeletionEvent
import fm.corus.android.domain.PostEngagementManager
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
import org.mockito.kotlin.whenever
import java.util.Date

/**
 * Regression guard for the "header says 2, grid says no songs yet" bug on
 * cold profile loads from the New on Corus list.
 *
 * Context: when the header count and the posts grid come from two separate
 * cloud calls (fetchUserProfile + getProfilePosts), Firestore's composite
 * index for `(userId, createdAt)` can briefly lag the user document's
 * cymbalCount for brand-new users who just posted. The fix consolidates
 * cold load into a single getProfileData call so the header and grid
 * always come from the same backend snapshot.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class OtherProfileViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    private lateinit var context: Context
    private lateinit var userRepository: UserRepository
    private lateinit var postRepository: PostRepository
    private lateinit var authRepository: AuthRepository
    private lateinit var nowPlayingManager: NowPlayingManager
    private lateinit var engagementManager: PostEngagementManager
    private lateinit var subscriptionRepository: SubscriptionRepository
    private lateinit var postDeletionEvent: PostDeletionEvent
    private lateinit var cloudFunctions: CloudFunctionsDataSource

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        context = mock()
        authRepository = mock {
            on { currentUserId } doReturn "viewer1"
        }
        userRepository = mock {
            on { blockedIds } doReturn MutableStateFlow(emptySet())
            on { isFollowing(any()) } doReturn false
            on { isUserMuted(any()) } doReturn false
        }
        postRepository = mock()
        nowPlayingManager = mock()
        engagementManager = mock {
            on { states } doReturn MutableStateFlow(emptyMap())
        }
        subscriptionRepository = mock {
            on { hasFullAccessFlow } doReturn MutableStateFlow(false)
        }
        postDeletionEvent = mock {
            on { events } doReturn MutableSharedFlow()
        }
        cloudFunctions = mock()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel(): OtherProfileViewModel = OtherProfileViewModel(
        context = context,
        userRepository = userRepository,
        postRepository = postRepository,
        authRepository = authRepository,
        nowPlayingManager = nowPlayingManager,
        cloudFunctions = cloudFunctions,
        musicServicePreference = mock(),
        analyticsService = mock(),
        remoteConfig = mock(),
        engagementManager = engagementManager,
        subscriptionRepository = subscriptionRepository,
        postDeletionEvent = postDeletionEvent,
        favoriteChangedEvent = fm.corus.android.domain.FavoriteChangedEvent(),
        commentEditedEvent = fm.corus.android.domain.CommentEditedEvent(),
        commentDeletedEvent = fm.corus.android.domain.CommentDeletedEvent(),
    )

    private fun makeUser(id: String, cymbalCount: Int): CymbalUser = CymbalUser(
        id = id,
        username = "user_$id",
        displayName = "User $id",
        cymbalCount = cymbalCount,
    )

    private fun makePost(id: String, userId: String): CymbalPost = CymbalPost(
        id = id,
        user = makeUser(userId, 0),
        track = CymbalTrack(
            id = "t_$id",
            name = "Track $id",
            artistName = "Artist",
            albumName = "Album",
        ),
        timestamp = Date(),
        mediaType = MediaType.TRACK,
    )

    @Test
    fun `loadProfile populates header and grid from a single getProfileData call`() = runTest {
        val targetId = "target1"
        val cloudUser = makeUser(targetId, cymbalCount = 2)
        val cloudPosts = listOf(makePost("p1", targetId), makePost("p2", targetId))
        whenever(postRepository.getProfileData(eq(targetId), any(), anyOrNull()))
            .thenReturn(CloudFunctionsDataSource.ProfileData(cloudUser, cloudPosts))
        whenever(userRepository.isSubscribedToUserPosts(any(), any())).thenReturn(false)

        val viewModel = createViewModel()
        viewModel.start(targetId, initialIsFollowing = false)
        advanceUntilIdle()

        // Header count and posts list both came from the same backend snapshot,
        // so they can't disagree the way the original two-call path did.
        assertEquals(2, viewModel.profile.value?.cymbalCount)
        assertEquals(listOf("p1", "p2"), viewModel.posts.value.map { it.id })

        // Legacy two-call path must not be exercised when the consolidated
        // call succeeds — that's the whole point of the fix.
        verify(userRepository, never()).fetchUserProfile(any())
        verify(postRepository, never()).getProfilePosts(any(), any(), any(), anyOrNull(), anyOrNull())
    }

    @Test
    fun `loadProfile falls back to legacy path when getProfileData returns a null user`() = runTest {
        val targetId = "target2"
        // Simulate a backend response that doesn't include the user (e.g.
        // block relationship returns user=null, posts=[]).
        whenever(postRepository.getProfileData(eq(targetId), any(), anyOrNull()))
            .thenReturn(CloudFunctionsDataSource.ProfileData(null, emptyList()))
        whenever(userRepository.fetchUserProfile(eq(targetId)))
            .thenReturn(makeUser(targetId, cymbalCount = 0))
        whenever(postRepository.getProfilePosts(eq(targetId), any(), any(), anyOrNull(), anyOrNull()))
            .thenReturn(emptyList())
        whenever(userRepository.isSubscribedToUserPosts(any(), any())).thenReturn(false)

        val viewModel = createViewModel()
        viewModel.start(targetId, initialIsFollowing = false)
        advanceUntilIdle()

        verify(userRepository).fetchUserProfile(eq(targetId))
        verify(postRepository).getProfilePosts(eq(targetId), any(), any(), anyOrNull(), anyOrNull())
    }

    /**
     * Regression guard for the LIKES-tab bug: tapping LIKES on another user's
     * profile used to render `posts` (the owner's OWN posts) because the
     * ViewModel never fetched likes. The fix loads a dedicated [likedPosts]
     * list from getLikedPosts — the owner's liked posts, authored by others.
     */
    @Test
    fun `loadLikedPosts shows the owner's liked posts, not their own posts`() = runTest {
        val targetId = "target3"
        val ownPosts = listOf(makePost("own1", targetId))
        val liked = listOf(makePost("liked1", "other_a"), makePost("liked2", "other_b"))
        whenever(postRepository.getProfileData(eq(targetId), any(), anyOrNull()))
            .thenReturn(CloudFunctionsDataSource.ProfileData(makeUser(targetId, 1), ownPosts))
        whenever(userRepository.isSubscribedToUserPosts(any(), any())).thenReturn(false)
        whenever(cloudFunctions.getLikedPosts(eq(targetId), eq("viewer1"), any(), any()))
            .thenReturn(liked)

        val viewModel = createViewModel()
        viewModel.start(targetId, initialIsFollowing = false)
        advanceUntilIdle()
        viewModel.loadLikedPosts(targetId)
        advanceUntilIdle()

        assertEquals(listOf("liked1", "liked2"), viewModel.likedPosts.value.map { it.id })
        // The owner's own posts are unchanged and distinct — proving the LIKES
        // tab no longer reuses `posts`.
        assertEquals(listOf("own1"), viewModel.posts.value.map { it.id })
        verify(cloudFunctions).getLikedPosts(eq(targetId), eq("viewer1"), any(), any())
    }

    @Test
    fun `loadMoreLiked paginates with the running offset`() = runTest {
        val targetId = "target4"
        val firstPage = (1..30).map { makePost("l$it", "a$it") }
        val secondPage = listOf(makePost("l31", "a31"))
        whenever(postRepository.getProfileData(eq(targetId), any(), anyOrNull()))
            .thenReturn(CloudFunctionsDataSource.ProfileData(makeUser(targetId, 0), emptyList()))
        whenever(userRepository.isSubscribedToUserPosts(any(), any())).thenReturn(false)
        whenever(cloudFunctions.getLikedPosts(eq(targetId), eq("viewer1"), any(), eq(0)))
            .thenReturn(firstPage)
        whenever(cloudFunctions.getLikedPosts(eq(targetId), eq("viewer1"), any(), eq(30)))
            .thenReturn(secondPage)

        val viewModel = createViewModel()
        viewModel.start(targetId, initialIsFollowing = false)
        advanceUntilIdle()
        viewModel.loadLikedPosts(targetId)
        advanceUntilIdle()
        viewModel.loadMoreLiked(targetId)
        advanceUntilIdle()

        assertEquals(31, viewModel.likedPosts.value.size)
        // The second page is fetched at offset = size of the first page.
        verify(cloudFunctions).getLikedPosts(eq(targetId), eq("viewer1"), any(), eq(30))
    }
}
