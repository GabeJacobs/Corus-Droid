package fm.corus.android.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MessageLinkPreviewTest {
    @Test
    fun `parse requires a url`() {
        assertNull(MessageLinkPreview.parse(mapOf("title" to "Nope")))
        val card = MessageLinkPreview.parse(
            mapOf(
                "url" to "https://variety.com/story",
                "title" to "South Park",
                "domain" to "www.variety.com",
                "isVideo" to true,
                "kind" to "link",
            )
        )
        assertEquals("South Park", card?.title)
        assertEquals("variety.com", card?.displayDomain)
        assertTrue(card?.isVideo == true)
    }

    @Test
    fun `url-only detection hides the raw string`() {
        val url = "https://www.instagram.com/reel/abc"
        assertTrue(MessageLinkPreview.isUrlOnly(url, url))
        assertTrue(MessageLinkPreview.isUrlOnly("  $url  ", url))
        assertFalse(MessageLinkPreview.isUrlOnly("watch $url", url))
    }
}
