package fm.corus.android.data.repository

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GiftRepositoryTest {
    @Test fun `gift status requires enabled and parses server inventory`() {
        val status = parseGiftStatus(mapOf(
            "enabled" to true,
            "inventory" to mapOf(
                "capacity" to 3L,
                "available" to 2L,
                "used" to 1L,
                "nextRefillAtMs" to 1_800_000_000_000L,
            ),
            "catalog" to listOf(
                mapOf("id" to "corus_heart"),
                mapOf("id" to "flowers"),
                mapOf("id" to "mind_blown"),
                mapOf("id" to "boombox"),
            ),
        ))

        requireNotNull(status)
        assertEquals(3, status.inventory.capacity)
        assertEquals(2, status.inventory.available)
        assertFalse(status.inventory.isEmpty)
        assertEquals(listOf("corus_heart", "flowers", "mind_blown", "boombox"), status.catalogIds)
        assertNull(parseGiftStatus(mapOf("enabled" to false)))
    }

    @Test fun `send result preserves idempotency inventory and recent receipts`() {
        val result = parseGiftSendResult(mapOf(
            "giftType" to "flowers",
            "alreadySent" to true,
            "inventory" to mapOf("capacity" to 1, "available" to 0, "used" to 1),
            "recentGifts" to listOf(mapOf(
                "senderId" to "gabe",
                "senderUsername" to "gabe",
                "senderDisplayName" to "Gabe",
                "giftType" to "flowers",
                "note" to "  thank you  ",
                "sentAt" to 1234L,
            )),
        ))

        requireNotNull(result)
        assertTrue(result.alreadySent)
        assertTrue(result.inventory.isEmpty)
        assertEquals("thank you", result.recentGifts.single().note)
        assertEquals(1234L, result.recentGifts.single().sentAtMs)
    }

    @Test fun `malformed responses fail closed`() {
        assertNull(parseGiftInventory(mapOf("available" to 1)))
        assertNull(parseGiftSendResult(mapOf("giftType" to "flowers")))
    }
}
