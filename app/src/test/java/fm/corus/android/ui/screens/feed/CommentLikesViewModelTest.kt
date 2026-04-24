package fm.corus.android.ui.screens.feed

import fm.corus.android.data.model.CymbalUser
import fm.corus.android.data.remote.FirestoreDataSource
import fm.corus.android.data.repository.AuthRepository
import fm.corus.android.data.repository.PostRepository
import fm.corus.android.data.repository.UserRepository
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@OptIn(ExperimentalCoroutinesApi::class)
class CommentLikesViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val postRepo = mock<PostRepository>()
    private val userRepo = mock<UserRepository>()
    private val authRepo = mock<AuthRepository>()

    private lateinit var viewModel: CommentLikesViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        whenever(authRepo.currentUserId).thenReturn("me")
        whenever(userRepo.followingIds).thenReturn(MutableStateFlow(setOf("existing")))
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun user(id: String) = CymbalUser(id = id, username = id, displayName = id)

    @Test
    fun `load fetches first page of comment likers`() = runTest {
        whenever(postRepo.fetchCommentLikers("p1", "c1", 20, null)).doReturn(
            FirestoreDataSource.LikersPage(
                users = listOf(user("a"), user("b")),
                lastTimestamp = 1000L,
                hasMore = false,
            )
        )

        viewModel = CommentLikesViewModel(postRepo, userRepo, authRepo)
        viewModel.load("p1", "c1")
        advanceUntilIdle()

        assertEquals(listOf(user("a"), user("b")), viewModel.likers.value)
        assertTrue(viewModel.followingIds.value.contains("existing"))
        verify(postRepo).fetchCommentLikers("p1", "c1", 20, null)
    }

    @Test
    fun `toggleFollow updates following set optimistically`() = runTest {
        whenever(postRepo.fetchCommentLikers("p1", "c1", 20, null)).doReturn(
            FirestoreDataSource.LikersPage(users = emptyList(), lastTimestamp = null, hasMore = false)
        )

        viewModel = CommentLikesViewModel(postRepo, userRepo, authRepo)
        viewModel.load("p1", "c1")
        advanceUntilIdle()

        viewModel.toggleFollow("new_user")
        // Optimistic update happens synchronously before the coroutine runs
        assertTrue(viewModel.followingIds.value.contains("new_user"))

        advanceUntilIdle()
        verify(userRepo).followUser("me", "new_user")
    }

    @Test
    fun `load is idempotent for same post and comment when likers already loaded`() = runTest {
        whenever(postRepo.fetchCommentLikers("p1", "c1", 20, null)).doReturn(
            FirestoreDataSource.LikersPage(users = listOf(user("a")), lastTimestamp = null, hasMore = false)
        )

        viewModel = CommentLikesViewModel(postRepo, userRepo, authRepo)
        viewModel.load("p1", "c1")
        advanceUntilIdle()

        // Second load for the same target should not re-fetch
        viewModel.load("p1", "c1")
        advanceUntilIdle()

        verify(postRepo).fetchCommentLikers("p1", "c1", 20, null)
    }
}
