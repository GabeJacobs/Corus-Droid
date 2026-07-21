package fm.corus.android.data.model

import java.util.Date
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CymbalCommentEditWindowTest {

    private val createdMs = 1_700_000_000_000L

    private fun comment(
        gifURL: String? = null,
        textIsAttachmentFallback: Boolean = false,
    ) = CymbalComment(
        id = "c1",
        user = CymbalUser(id = "u1", username = "tester", displayName = "Tester"),
        text = "hi",
        timestamp = Date(createdMs),
        gifURL = gifURL,
        textIsAttachmentFallback = textIsAttachmentFallback,
    )

    private fun at(offsetMs: Long) = createdMs + offsetMs

    @Test
    fun `a fresh comment is editable`() {
        assertTrue(comment().isEditable(at(0)))
        assertTrue(comment().isEditable(at(CymbalComment.EDIT_WINDOW_MS - 1)))
    }

    @Test
    fun `the window closes at the boundary`() {
        assertFalse(comment().isEditable(at(CymbalComment.EDIT_WINDOW_MS)))
        assertFalse(comment().isEditable(at(CymbalComment.EDIT_WINDOW_MS + 1)))
    }

    @Test
    fun `gif comments are never editable`() {
        assertFalse(comment(gifURL = "https://g.if").isEditable(at(0)))
    }

    @Test
    fun `synthesized attachment text is never editable`() {
        assertFalse(comment(textIsAttachmentFallback = true).isEditable(at(0)))
    }

    @Test
    fun `a client clock running behind keeps the window open`() {
        assertTrue(comment().isEditable(at(-60_000)))
    }

    @Test
    fun `the client window stays inside the server grace`() {
        assertTrue(CymbalComment.EDIT_WINDOW_MS < 16 * 60 * 1000L)
    }
}
