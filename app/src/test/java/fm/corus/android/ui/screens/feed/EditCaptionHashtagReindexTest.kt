package fm.corus.android.ui.screens.feed

import fm.corus.android.data.repository.AuthRepository
import fm.corus.android.data.repository.PostRepository
import fm.corus.android.data.repository.UserRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify

/**
 * Regression guard for hashtag re-indexing on caption edits.
 *
 * The backend `onHashtagPostsChanged` trigger keys off the post doc's
 * `hashtags` array field, NOT the caption text. So editing a caption to add a
 * hashtag only joins that hashtag's feed if the client re-parses the caption
 * and writes the `hashtags` array. This previously regressed on Android because
 * `saveCaption` wrote only the caption, leaving the array stale (added tags not
 * indexed, removed tags not cleared). These tests assert the parsed hashtags
 * are always forwarded to the repository alongside the caption.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class EditCaptionHashtagReindexTest {

    private val testDispatcher = StandardTestDispatcher()
    private val postRepo = mock<PostRepository>()
    private val userRepo = mock<UserRepository>()
    private val authRepo = mock<AuthRepository>()

    private lateinit var viewModel: EditCaptionViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        // No mentions in these captions, so mention-notify short-circuits; stub
        // currentUserId anyway so notifyNewMentions doesn't NPE.
        org.mockito.kotlin.whenever(authRepo.currentUserId).thenReturn("me")
        viewModel = EditCaptionViewModel(postRepo, userRepo, authRepo)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `saveCaption forwards parsed hashtags to repository`() = runTest {
        val caption = "Great #JazzTuesday and #film"

        viewModel.saveCaption(postId = "p1", caption = caption, onSuccess = {})
        advanceUntilIdle()

        verify(postRepo).updateCaption("p1", caption, listOf("JazzTuesday", "film"))
    }

    @Test
    fun `saveCaption forwards empty list when caption has no hashtags`() = runTest {
        val caption = "just a normal caption"

        viewModel.saveCaption(postId = "p1", caption = caption, onSuccess = {})
        advanceUntilIdle()

        // Empty list (not a stale array) is what clears a removed hashtag.
        verify(postRepo).updateCaption("p1", caption, emptyList())
    }

    @Test
    fun `saveCaption invokes onSuccess after a successful write`() = runTest {
        var succeeded = false

        viewModel.saveCaption(postId = "p1", caption = "hello #world", onSuccess = { succeeded = true })
        advanceUntilIdle()

        org.junit.Assert.assertTrue(succeeded)
    }
}
