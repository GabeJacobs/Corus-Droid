package fm.corus.android.ui.screens.feed

import fm.corus.android.ui.components.extractMentions
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests the mention-diffing logic used when editing captions:
 * only newly added mentions should trigger notifications.
 */
class EditCaptionMentionNotificationTest {

    private fun newMentions(oldCaption: String, newCaption: String): Set<String> {
        val old = extractMentions(oldCaption).toSet()
        val new = extractMentions(newCaption).toSet()
        return new - old
    }

    @Test
    fun `no notifications when caption unchanged`() {
        val added = newMentions("hey @alice", "hey @alice")
        assertTrue(added.isEmpty())
    }

    @Test
    fun `notifies for newly added mention`() {
        val added = newMentions("hello world", "hello world @alice")
        assertEquals(setOf("alice"), added)
    }

    @Test
    fun `does not re-notify existing mention`() {
        val added = newMentions("hey @alice check this", "hey @alice check this @bob")
        assertEquals(setOf("bob"), added)
    }

    @Test
    fun `handles mention added on new line`() {
        val added = newMentions("great song", "great song\n@alice")
        assertEquals(setOf("alice"), added)
    }

    @Test
    fun `handles multiple new mentions`() {
        val added = newMentions("check this out", "check this out @alice @bob")
        assertEquals(setOf("alice", "bob"), added)
    }

    @Test
    fun `no notifications when mention removed`() {
        val added = newMentions("hey @alice @bob", "hey @alice")
        assertTrue(added.isEmpty())
    }

    @Test
    fun `handles empty old caption`() {
        val added = newMentions("", "@alice new caption")
        assertEquals(setOf("alice"), added)
    }

    @Test
    fun `handles empty new caption`() {
        val added = newMentions("@alice old caption", "")
        assertTrue(added.isEmpty())
    }

    @Test
    fun `mention detection is case-insensitive`() {
        val added = newMentions("hey @Alice", "hey @alice @Bob")
        assertEquals(setOf("bob"), added)
    }
}
