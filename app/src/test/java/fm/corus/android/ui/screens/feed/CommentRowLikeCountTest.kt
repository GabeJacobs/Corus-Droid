package fm.corus.android.ui.screens.feed

import android.app.Application
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import fm.corus.android.R
import fm.corus.android.data.model.CymbalComment
import fm.corus.android.data.model.CymbalUser
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * Regression tests for the comment like-count on [CommentContentRow] — the row
 * used by the full-screen [SinglePostCommentsScreen] that opens from a "liked
 * your comment" / "replied to you" notification deep-link.
 *
 * Two prior bugs this guards against (both matching iOS + the feed CommentsSheet):
 *
 *  1. The like *count* never rendered on this screen — the heart was drawn but
 *     the number beside it had no `Text` at all, so a comment with N likes
 *     showed a bare heart. The value was loaded the whole time (same getComments
 *     path as the sheet); it just wasn't painted.
 *  2. The heart was sized per-depth (`if (isReply) 14 else 16`), so replies got
 *     a visibly smaller heart than top-level comments. iOS + CommentsSheet use a
 *     uniform 14dp, so reply and top-level hearts must match.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class CommentRowLikeCountTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val likeCd: String =
        RuntimeEnvironment.getApplication().getString(R.string.comments_cd_like)

    private val author = CymbalUser(id = "u1", username = "alice", displayName = "Alice")

    private fun comment(likeCount: Int) = CymbalComment(
        id = "c1",
        user = author,
        text = "nice one",
        likeCount = likeCount,
    )

    @Composable
    private fun CommentRowUnderTest(comment: CymbalComment, isReply: Boolean) {
        CommentContentRow(
            comment = comment,
            isOwnComment = false,
            isLiked = false,
            isReply = isReply,
            highlightColor = Color.Transparent,
            onUserTap = {},
            onLike = {},
            onLikeLongPress = {},
            onReply = {},
            onEdit = {},
            onDelete = {},
            onReport = {},
            onBlock = {},
            onMentionTap = {},
            onHashtagTap = {},
        )
    }

    @Test
    fun `top-level comment renders its like count`() {
        composeRule.setContent { CommentRowUnderTest(comment(2), isReply = false) }
        composeRule.onNodeWithText("2").assertExists()
    }

    @Test
    fun `reply renders its like count`() {
        composeRule.setContent { CommentRowUnderTest(comment(5), isReply = true) }
        composeRule.onNodeWithText("5").assertExists()
    }

    @Test
    fun `no count is shown when there are zero likes`() {
        composeRule.setContent { CommentRowUnderTest(comment(0), isReply = false) }
        composeRule.onNodeWithText("0").assertDoesNotExist()
    }

    @Test
    fun `heart is the same size on replies and top-level comments`() {
        composeRule.setContent {
            Column {
                CommentRowUnderTest(comment(1), isReply = false)
                CommentRowUnderTest(comment(1), isReply = true)
            }
        }

        val hearts = composeRule.onAllNodesWithContentDescription(likeCd, useUnmergedTree = true)
        val first = hearts[0].getUnclippedBoundsInRoot()
        val second = hearts[1].getUnclippedBoundsInRoot()
        val firstWidth = (first.right - first.left).value
        val secondWidth = (second.right - second.left).value

        assertEquals(
            "Comment-like heart should be the same width on replies and top-level comments",
            firstWidth.toDouble(),
            secondWidth.toDouble(),
            0.5,
        )
    }
}
