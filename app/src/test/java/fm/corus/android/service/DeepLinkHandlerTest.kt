package fm.corus.android.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DeepLinkHandlerTest {

    @Test
    fun `message notification reads otherUserId from fromUserId`() {
        val data = mapOf(
            "type" to "message",
            "threadId" to "dm_abc",
            "fromUserId" to "user_123",
        )
        val dest = DeepLinkHandler.parseNotificationData(data)
        assertEquals(DeepLinkDestination.Thread("dm_abc", "user_123"), dest)
    }

    @Test
    fun `message notification falls back to userId if fromUserId missing`() {
        val data = mapOf(
            "type" to "message",
            "threadId" to "dm_abc",
            "userId" to "user_456",
        )
        val dest = DeepLinkHandler.parseNotificationData(data)
        assertEquals(DeepLinkDestination.Thread("dm_abc", "user_456"), dest)
    }

    @Test
    fun `message notification with only threadId still resolves`() {
        val data = mapOf("type" to "message", "threadId" to "dm_abc")
        val dest = DeepLinkHandler.parseNotificationData(data)
        assertEquals(DeepLinkDestination.Thread("dm_abc", ""), dest)
    }

    @Test
    fun `message notification without threadId is null`() {
        val data = mapOf("type" to "message", "fromUserId" to "user_123")
        assertNull(DeepLinkHandler.parseNotificationData(data))
    }

    @Test
    fun `fallback parse uses fromUserId for thread otherUserId`() {
        val data = mapOf("threadId" to "dm_abc", "fromUserId" to "user_123")
        val dest = DeepLinkHandler.parseNotificationData(data)
        assertEquals(DeepLinkDestination.Thread("dm_abc", "user_123"), dest)
    }

    @Test
    fun `contact_joined notification routes to profile`() {
        val data = mapOf(
            "type" to "contact_joined",
            "fromUserId" to "user_123",
            "userId" to "user_123",
            "postId" to "",
            "commentId" to "",
        )
        val dest = DeepLinkHandler.parseNotificationData(data)
        assertEquals(DeepLinkDestination.Profile("user_123"), dest)
    }

    @Test
    fun `fallback ignores empty postId and uses fromUserId`() {
        val data = mapOf(
            "postId" to "",
            "commentId" to "",
            "fromUserId" to "user_999",
            "userId" to "user_999",
        )
        val dest = DeepLinkHandler.parseNotificationData(data)
        assertEquals(DeepLinkDestination.Profile("user_999"), dest)
    }
}
