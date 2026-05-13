package fm.corus.android.ui.screens.feed

import fm.corus.android.data.model.CymbalComment
import fm.corus.android.data.model.CymbalPost
import fm.corus.android.data.model.CymbalTrack
import fm.corus.android.data.model.CymbalUser
import fm.corus.android.domain.CommentDeletedEvent
import fm.corus.android.domain.CommentEditedEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

/**
 * Regression: when a user edited their own comment and went back to the feed,
 * the preview comment under the post card kept showing the old text. The
 * server-side `onCommentUpdatedUpdatePreview` trigger now rewrites the
 * denormalized `previewComments`, but UI shouldn't wait for the next fetch —
 * `applyCommentEditToPosts` patches the in-memory feed snapshot so a recycled
 * post card re-binds to fresh text.
 */
class ApplyCommentEditToPostsTest {

    private fun user(id: String) = CymbalUser(id = id, username = id, displayName = id)
    private fun track() = CymbalTrack(id = "t1", name = "n", artistName = "a", albumName = "al")
    private fun comment(id: String, text: String) =
        CymbalComment(id = id, user = user("commenter"), text = text)

    private fun post(id: String, previews: List<CymbalComment>) =
        CymbalPost(id = id, user = user("poster"), track = track(), comments = previews)

    @Test
    fun `patches matching preview comment text`() {
        val posts = listOf(
            post("p1", listOf(comment("c1", "old"), comment("c2", "other"))),
            post("p2", listOf(comment("c3", "unrelated"))),
        )
        val patched = applyCommentEditToPosts(
            posts,
            CommentEditedEvent.Payload(postId = "p1", commentId = "c1", newText = "new"),
        )
        assertEquals("new", patched[0].comments[0].text)
        assertEquals("other", patched[0].comments[1].text)
        assertEquals("unrelated", patched[1].comments[0].text)
    }

    @Test
    fun `sets editedAt on the patched entry`() {
        val posts = listOf(post("p1", listOf(comment("c1", "old"))))
        val patched = applyCommentEditToPosts(
            posts,
            CommentEditedEvent.Payload("p1", "c1", "new"),
        )
        assertNotNull(patched[0].comments[0].editedAt)
        assertNull(posts[0].comments[0].editedAt)
    }

    @Test
    fun `returns same list when no post matches`() {
        val posts = listOf(post("p1", listOf(comment("c1", "old"))))
        val patched = applyCommentEditToPosts(
            posts,
            CommentEditedEvent.Payload("p_other", "c1", "new"),
        )
        assertSame(posts, patched)
    }

    @Test
    fun `returns same list when post has no matching comment id`() {
        val posts = listOf(post("p1", listOf(comment("c1", "old"))))
        val patched = applyCommentEditToPosts(
            posts,
            CommentEditedEvent.Payload("p1", "c_missing", "new"),
        )
        assertSame(posts, patched)
    }

    @Test
    fun `leaves other posts untouched by identity`() {
        val keep = post("p_keep", listOf(comment("c_other", "other")))
        val target = post("p1", listOf(comment("c1", "old")))
        val patched = applyCommentEditToPosts(
            listOf(keep, target),
            CommentEditedEvent.Payload("p1", "c1", "new"),
        )
        assertSame(keep, patched[0])
    }

    // ── applyCommentDeleteToPosts ──

    @Test
    fun `delete removes matching preview comment`() {
        val posts = listOf(
            post("p1", listOf(comment("c1", "first"), comment("c2", "second"))),
            post("p2", listOf(comment("c3", "unrelated"))),
        )
        val patched = applyCommentDeleteToPosts(
            posts,
            CommentDeletedEvent.Payload(postId = "p1", commentId = "c1"),
        )
        assertEquals(1, patched[0].comments.size)
        assertEquals("c2", patched[0].comments[0].id)
        assertEquals("unrelated", patched[1].comments[0].text)
    }

    @Test
    fun `delete returns same list when post does not match`() {
        val posts = listOf(post("p1", listOf(comment("c1", "x"))))
        val patched = applyCommentDeleteToPosts(
            posts,
            CommentDeletedEvent.Payload("p_other", "c1"),
        )
        assertSame(posts, patched)
    }

    @Test
    fun `delete returns same list when comment is not in preview`() {
        val posts = listOf(post("p1", listOf(comment("c1", "x"))))
        val patched = applyCommentDeleteToPosts(
            posts,
            CommentDeletedEvent.Payload("p1", "c_not_in_preview"),
        )
        assertSame(posts, patched)
    }

    @Test
    fun `delete leaves other posts untouched by identity`() {
        val keep = post("p_keep", listOf(comment("c_other", "other")))
        val target = post("p1", listOf(comment("c1", "x")))
        val patched = applyCommentDeleteToPosts(
            listOf(keep, target),
            CommentDeletedEvent.Payload("p1", "c1"),
        )
        assertSame(keep, patched[0])
    }
}
