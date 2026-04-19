package fm.corus.android.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MentionTextTest {

    // ── parseMentionQuery ──

    @Test
    fun `returns null for empty text`() {
        assertNull(parseMentionQuery(""))
    }

    @Test
    fun `returns null when no mention present`() {
        assertNull(parseMentionQuery("hello world"))
    }

    @Test
    fun `returns null for lone @ symbol`() {
        assertNull(parseMentionQuery("hello @"))
    }

    @Test
    fun `detects mention after space`() {
        assertEquals("ga", parseMentionQuery("hello @ga"))
    }

    @Test
    fun `detects mention at start of text`() {
        assertEquals("user", parseMentionQuery("@user"))
    }

    @Test
    fun `detects mention after newline`() {
        assertEquals("ga", parseMentionQuery("check this out\n@ga"))
    }

    @Test
    fun `detects mention after multiple newlines`() {
        assertEquals("user", parseMentionQuery("line one\n\n@user"))
    }

    @Test
    fun `detects mention after carriage return`() {
        assertEquals("test", parseMentionQuery("hello\r\n@test"))
    }

    @Test
    fun `returns null when mention is not the last word`() {
        assertNull(parseMentionQuery("hello @ga world"))
    }

    @Test
    fun `handles mention with dots in username`() {
        assertEquals("john.doe", parseMentionQuery("hey @john.doe"))
    }

    @Test
    fun `returns null for trailing whitespace after mention`() {
        assertNull(parseMentionQuery("hello @ga "))
    }

    // ── applyMention ──

    @Test
    fun `applyMention replaces partial mention with full username and trailing space`() {
        assertEquals("hello @gabejacobs ", applyMention("hello @gab", "gabejacobs"))
    }

    @Test
    fun `applyMention handles mention at start`() {
        assertEquals("@gabejacobs ", applyMention("@gab", "gabejacobs"))
    }

    @Test
    fun `applyMention preserves text with no mention in progress`() {
        assertEquals("hello world", applyMention("hello world", "gabejacobs"))
    }

    @Test
    fun `applyMention replaces only the last at-mention`() {
        assertEquals("hi @alice and @bob ", applyMention("hi @alice and @b", "bob"))
    }

    @Test
    fun `applyMention handles mention after newline`() {
        assertEquals("line one\n@gabe ", applyMention("line one\n@ga", "gabe"))
    }

    @Test
    fun `applyMention replaces bare @ with full username`() {
        assertEquals("@user ", applyMention("@", "user"))
    }
}
