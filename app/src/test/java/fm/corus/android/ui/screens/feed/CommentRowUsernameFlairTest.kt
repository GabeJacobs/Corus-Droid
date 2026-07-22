package fm.corus.android.ui.screens.feed

import android.app.Application
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import fm.corus.android.data.model.CymbalComment
import fm.corus.android.data.model.CymbalUser
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Date
import java.util.concurrent.TimeUnit

/**
 * Regression tests for the comment author line on [CommentContentRow] — the row
 * used by the full-screen [SinglePostCommentsScreen] (post detail → comments).
 *
 * That row hand-rolled its author line as a plain `Text(captionMedium)`, so
 * against iOS and the feed's [CommentsSheet] it was doubly wrong: the username
 * rendered at 12sp Medium instead of 14sp ExtraBold, and the flair badge beside
 * it was missing entirely. Both now come from the shared `UsernameWithFlair`.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class CommentRowUsernameFlairTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun commentBy(user: CymbalUser) = CymbalComment(
        id = "c1",
        user = user,
        text = "nice one",
        // Fixed age so the timestamp beside the username reads "5m".
        timestamp = Date(System.currentTimeMillis() - TimeUnit.MINUTES.toMillis(5)),
    )

    private val clubAuthor = CymbalUser(
        id = "u1",
        username = "alice",
        displayName = "Alice",
        isClubMember = true,
        profileFlair = "sparkle",
    )

    @Composable
    private fun CommentRowUnderTest(comment: CymbalComment, isReply: Boolean = false) {
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
    fun `club member's flair badge renders beside the username`() {
        composeRule.setContent { CommentRowUnderTest(commentBy(clubAuthor)) }

        composeRule
            .onNodeWithContentDescription("Sparkle", useUnmergedTree = true)
            .assertExists()
    }

    @Test
    fun `flair badge renders on replies too`() {
        composeRule.setContent { CommentRowUnderTest(commentBy(clubAuthor), isReply = true) }

        composeRule
            .onNodeWithContentDescription("Sparkle", useUnmergedTree = true)
            .assertExists()
    }

    @Test
    fun `username still renders`() {
        // The 14sp/ExtraBold half of the fix isn't assertable here — Robolectric
        // uses stub font metrics, so every Text measures the same height whatever
        // its style, and Compose exposes no style in the semantics tree. That part
        // is verified on device; this only guards that the swap to
        // UsernameWithFlair kept the name itself on screen.
        composeRule.setContent { CommentRowUnderTest(commentBy(clubAuthor)) }

        composeRule.onNodeWithText("alice", useUnmergedTree = true).assertExists()
    }
}
