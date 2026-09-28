package fm.corus.android.data.model

import org.junit.Assert.*
import org.junit.Test

class GiftNotificationTest {
    @Test fun receiptDecodesThankStateFromCallableAndFirestore() {
        for (value in listOf(1234L, com.google.firebase.Timestamp(java.util.Date(1234L)))) {
            val notification = CymbalNotification.fromMap("receipt", mapOf(
                "type" to "gift", "giftId" to "gift-id", "postId" to "post", "thankedAt" to value))
            assertEquals("gift-id", notification.giftId)
            assertEquals(1234L, notification.giftThankedAt?.time)
            assertFalse(notification.canThankGift)
        }
    }

    @Test fun legacyReceiptResolvesGiftIdWithoutTruncatingUnderscores() {
        val notification = CymbalNotification.fromMap("gift_sender_with_underscores_post_id", mapOf(
            "type" to "gift", "postId" to "post_id"))
        assertEquals("sender_with_underscores", notification.giftId)
        assertTrue(notification.canThankGift)
        assertFalse(notification.copy(postId = null).canThankGift)
        assertFalse(notification.copy(giftId = null).canThankGift)
        assertNull(CymbalNotification.fromMap("gift__post_id", mapOf(
            "type" to "gift", "postId" to "post_id")).giftId)
        assertNull(CymbalNotification.fromMap("unrelated", mapOf(
            "type" to "gift", "postId" to "post_id")).giftId)
    }

    @Test fun giftThanksReceiptsDoNotBecomeLikes() {
        val notification = CymbalNotification.fromMap("thanks", mapOf(
            "type" to "gift_thanks", "giftType" to "flowers", "postId" to "post"))
        assertEquals("gift_thanks", notification.type.value)
        assertEquals("thanked you for your gift.", notification.message)
    }
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

    @Test fun giftThanksDecodesAsItsOwnActivityType() {
        val notification = CymbalNotification.fromMap("gift_thanks_1", mapOf(
            "type" to "gift_thanks",
            "giftId" to "gift-1",
            "giftType" to "flowers",
            "postId" to "post",
            "createdAt" to 123L,
        ))
        assertEquals(NotificationType.GIFT_THANKS, notification.type)
        assertEquals("gift-1", notification.giftId)
        assertEquals("thanked you for your gift.", notification.message)
    }
}
