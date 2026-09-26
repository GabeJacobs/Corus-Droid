package fm.corus.android.data.model

import org.junit.Assert.*
import org.junit.Test

class GiftNotificationTest {
    @Test fun giftReceiptsDecodeWithoutBecomingLikes() {
        for ((type, phrase) in listOf("corus_heart" to "a Super Like", "flowers" to "Flowers", "mind_blown" to "Mind Blown", "boombox" to "a Boombox")) {
            val notification = CymbalNotification.fromMap("receipt", mapOf(
                "type" to "gift", "giftType" to type, "giftNote" to "Thank you!", "postTitle" to "Dried Roses", "createdAt" to 123L))
            assertEquals(NotificationType.GIFT, notification.type)
            assertEquals("sent you $phrase", notification.message)
            assertEquals("Thank you!", notification.giftNote)
            assertEquals("Dried Roses", notification.postTitle)
            assertNotNull(GiftDefinition.from(type).artboard)
        }
    }
    @Test fun missingAndFutureGiftMetadataIsSafe() {
        assertEquals("a Gift", GiftDefinition.from(null).sentPhrase)
        assertEquals("a Gift", GiftDefinition.from("future").sentPhrase)
        assertEquals("For something you shared on Corus", GiftDefinition.context(null))
        assertEquals("For sharing “Dried Roses”", GiftDefinition.context(" Dried Roses "))
    }
}
