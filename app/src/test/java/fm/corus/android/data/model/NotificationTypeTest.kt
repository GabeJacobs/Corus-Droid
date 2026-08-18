package fm.corus.android.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationTypeTest {

    // Regression: server writes repost notifications with type "repost". A refactor
    // dropped the REPOST enum case, so from() fell back to LIKE and reposts rendered
    // as "liked your corus" (duplicate-looking when the user also liked a post).
    @Test
    fun `repost string maps to REPOST not the LIKE fallback`() {
        assertEquals(NotificationType.REPOST, NotificationType.from("repost"))
    }

    // Guard against future orphaned types: every enum value must round-trip through
    // from(), otherwise it silently degrades to the LIKE fallback.
    @Test
    fun `every type round-trips through from`() {
        NotificationType.entries.forEach { type ->
            assertEquals(type, NotificationType.from(type.value))
        }
    }

    @Test
    fun `trending string maps to TRENDING not the LIKE fallback`() {
        assertEquals(NotificationType.TRENDING, NotificationType.from("trending"))
    }

    @Test
    fun `unknown and null values fall back to LIKE`() {
        assertEquals(NotificationType.LIKE, NotificationType.from("not_a_type"))
        assertEquals(NotificationType.LIKE, NotificationType.from(null))
    }

    @Test
    fun `comment reply and mention support comment actions`() {
        assertTrue(NotificationType.COMMENT.supportsCommentActions)
        assertTrue(NotificationType.REPLY.supportsCommentActions)
        assertTrue(NotificationType.MENTION.supportsCommentActions)
    }

    @Test
    fun `comment_like does not support comment actions`() {
        assertFalse(NotificationType.COMMENT_LIKE.supportsCommentActions)
    }

    @Test
    fun `non-comment types do not support comment actions`() {
        assertFalse(NotificationType.LIKE.supportsCommentActions)
        assertFalse(NotificationType.SAVE.supportsCommentActions)
        assertFalse(NotificationType.FOLLOW.supportsCommentActions)
        assertFalse(NotificationType.TAG.supportsCommentActions)
        assertFalse(NotificationType.NEW_POST.supportsCommentActions)
        assertFalse(NotificationType.CONTACT_JOINED.supportsCommentActions)
    }
}
