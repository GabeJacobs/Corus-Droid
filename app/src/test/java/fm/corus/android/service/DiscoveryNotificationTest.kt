package fm.corus.android.service
import fm.corus.android.data.model.CymbalNotification
import fm.corus.android.data.model.NotificationType
import org.junit.Assert.*
import org.junit.Test
class DiscoveryNotificationTest {
    @Test fun releasesOpenTheirCatalogPages() {
        for (type in listOf("album", "song", "film")) {
            val result = DeepLinkHandler.parseNotificationData(mapOf("type" to "new_release", "destinationType" to type, "destinationId" to "entity1"))
            assertEquals(DeepLinkDestination.Entity(EntitySegment.from(type)!!, "entity1"), result)
        }
        assertNull(DeepLinkHandler.parseNotificationData(mapOf("type" to "new_release", "destinationType" to "invalid")))
    }
    @Test fun multipleConcertDatesOpenTheCombinedList() {
        assertEquals(DeepLinkDestination.ConcertAnnouncement("notice1"), DeepLinkHandler.parseNotificationData(mapOf(
            "type" to "concert_announcement", "notificationId" to "notice1", "eventIds" to "[\"a\",\"b\"]", "eventId" to "a")))
        assertEquals(DeepLinkDestination.Concert("a"), DeepLinkHandler.parseNotificationData(mapOf(
            "type" to "concert_announcement", "eventIds" to "[\"a\"]", "eventId" to "a")))
    }
    @Test fun noticeMappingKeepsAnonymousIdentityAndDestination() {
        val n = CymbalNotification.fromMap("n1", mapOf("type" to "new_release", "creatorName" to "SZA", "releaseTitle" to "Lana", "destinationType" to "album", "destinationId" to "album1", "fromUserId" to ""))
        assertEquals(NotificationType.NEW_RELEASE, n.type)
        assertTrue(n.type.isDiscovery)
        assertEquals("", n.fromUser.id)
        assertEquals("album1", n.destinationId)
    }
}
