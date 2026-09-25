package fm.corus.android.service

import android.net.Uri
import org.junit.Assert.assertEquals
import org.junit.Test
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock

class ConcertDeepLinkTest {
    @Test
    fun `concert push opens its event`() {
        assertEquals(
            DeepLinkDestination.Concert("event_123"),
            DeepLinkHandler.parseNotificationData(mapOf("type" to "concert_going", "eventId" to "event_123")),
        )
    }

    @Test
    fun `shared public concert URL opens its event`() {
        val url: Uri = mock {
            on { scheme } doReturn "https"
            on { host } doReturn "corus.fm"
            on { pathSegments } doReturn listOf("concert", "show-in-new-york", "event_123")
        }
        assertEquals(DeepLinkDestination.Concert("event_123"), DeepLinkHandler.parse(url))
    }
}
