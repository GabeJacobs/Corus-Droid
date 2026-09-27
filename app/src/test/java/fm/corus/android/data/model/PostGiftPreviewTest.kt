package fm.corus.android.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PostGiftPreviewTest {
    @Test
    fun `parses the gift information already carried by a post`() {
        val preview = PostGiftPreview.fromMap(
            mapOf(
                "senderId" to "u1",
                "senderUsername" to "gabe",
                "senderDisplayName" to "Gabe",
                "senderAvatarURL" to "https://example.com/avatar.jpg",
                "giftType" to "flowers",
                "sentAt" to 1_758_456_000_000L,
            ),
        )

        requireNotNull(preview)
        assertEquals("u1", preview.senderId)
        assertEquals("gabe", preview.senderUsername)
        assertEquals("flowers", preview.giftType)
        assertEquals(1_758_456_000_000L, preview.sentAtMs)
        assertNull(preview.note)
    }
}
