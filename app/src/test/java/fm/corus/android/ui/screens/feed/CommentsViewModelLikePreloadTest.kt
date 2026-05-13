package fm.corus.android.ui.screens.feed

import android.content.Context
import fm.corus.android.data.model.CymbalComment
import fm.corus.android.data.model.CymbalUser
import fm.corus.android.data.repository.AuthRepository
import fm.corus.android.data.repository.GifRepository
import fm.corus.android.data.repository.MessageRepository
import fm.corus.android.data.repository.PostRepository
import fm.corus.android.data.repository.UserRepository
import fm.corus.android.domain.CommentDeletedEvent
import fm.corus.android.domain.CommentEditedEvent
import fm.corus.android.domain.NowPlayingManager
import fm.corus.android.domain.PostDeletionEvent
import fm.corus.android.domain.PostEngagementManager
import fm.corus.android.service.AnalyticsService
import fm.corus.android.service.RemoteConfigService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.util.Date

/**
 * Regression test for the comment-like drift bug: count showed 5 while the
 * likes subcollection only had 1 entry, because Android never preloaded which
 * comments the current user had already liked. Without that preload, the
 * heart appeared empty on every re-open and re-tapping it incremented the
 * counter while the like doc was just overwritten.
 *
 * The fix calls `PostRepository.checkCommentLikesBatch` from `loadComments`
 * and hydrates `_likedCommentIds`. This test pins that behavior.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class CommentsViewModelLikePreloadTest {

    private val testDispatcher = StandardTestDispatcher()

    private val postRepo = mock<PostRepository>()
    private val authRepo = mock<AuthRepository>()
    private val userRepo = mock<UserRepository>()
    private val messageRepo = mock<MessageRepository>()
    private val engagementManager = mock<PostEngagementManager>()
    private val postDeletionEvent = mock<PostDeletionEvent>()
    private val commentEditedEvent = mock<CommentEditedEvent>()
    private val commentDeletedEvent = mock<CommentDeletedEvent>()
    private val nowPlayingManager = mock<NowPlayingManager>()
    private val remoteConfig = mock<RemoteConfigService>()
    private val gifRepo = mock<GifRepository>()
    private val analytics = mock<AnalyticsService>()
    private val context = mock<Context>()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        whenever(authRepo.currentUserId).thenReturn("me")
        whenever(authRepo.userProfile).thenReturn(MutableStateFlow(null))
        whenever(userRepo.followingIds).thenReturn(MutableStateFlow(emptySet()))
        whenever(engagementManager.states).thenReturn(MutableStateFlow(emptyMap()))
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun comment(id: String) = CymbalComment(
        id = id,
        user = CymbalUser(id = "author_$id", username = "u$id", displayName = "U$id"),
        text = "hello",
        timestamp = Date(0),
    )

    @Test
    fun `loadComments hydrates likedCommentIds from server preload`() = runTest {
        val all = listOf(comment("c1"), comment("c2"), comment("c3"))
        whenever(postRepo.getComments("p1")).doReturn(all)
        whenever(postRepo.checkCommentLikesBatch(eq("me"), eq("p1"), any())).doReturn(setOf("c1", "c3"))

        val vm = newViewModel()
        vm.loadComments("p1")
        advanceUntilIdle()

        assertEquals(setOf("c1", "c3"), vm.likedCommentIds.value)
        verify(postRepo).checkCommentLikesBatch(eq("me"), eq("p1"), any())
    }

    @Test
    fun `loadComments resets likedCommentIds when post changes`() = runTest {
        // First post: user has liked c1.
        whenever(postRepo.getComments("p1")).doReturn(listOf(comment("c1")))
        whenever(postRepo.checkCommentLikesBatch(eq("me"), eq("p1"), any())).doReturn(setOf("c1"))
        // Second post: user has liked nothing.
        whenever(postRepo.getComments("p2")).doReturn(listOf(comment("c9")))
        whenever(postRepo.checkCommentLikesBatch(eq("me"), eq("p2"), any())).doReturn(emptySet())

        val vm = newViewModel()
        vm.loadComments("p1")
        advanceUntilIdle()
        assertEquals(setOf("c1"), vm.likedCommentIds.value)

        // Switching posts must not leak the prior post's liked set; otherwise
        // hearts would render stale-filled on the new post.
        vm.loadComments("p2")
        // Right after switching but before the async load completes, the set
        // should be cleared.
        assertEquals(emptySet<String>(), vm.likedCommentIds.value)

        advanceUntilIdle()
        assertEquals(emptySet<String>(), vm.likedCommentIds.value)
    }

    @Test
    fun `loadComments skips preload when comments list is empty`() = runTest {
        whenever(postRepo.getComments("p1")).doReturn(emptyList())

        val vm = newViewModel()
        vm.loadComments("p1")
        advanceUntilIdle()

        assertEquals(emptySet<String>(), vm.likedCommentIds.value)
        // No call when there's nothing to check.
        verify(postRepo, org.mockito.kotlin.never())
            .checkCommentLikesBatch(any(), any(), any())
    }

    private fun newViewModel() = CommentsViewModel(
        postRepository = postRepo,
        authRepository = authRepo,
        userRepository = userRepo,
        messageRepository = messageRepo,
        engagementManager = engagementManager,
        postDeletionEvent = postDeletionEvent,
        commentEditedEvent = commentEditedEvent,
        commentDeletedEvent = commentDeletedEvent,
        nowPlayingManager = nowPlayingManager,
        remoteConfigService = remoteConfig,
        gifRepository = gifRepo,
        analyticsService = analytics,
        context = context,
    )
}
