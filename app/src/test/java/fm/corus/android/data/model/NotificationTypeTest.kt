package fm.corus.android.data.model

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationTypeTest {

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
