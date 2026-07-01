package fm.corus.android.data.remote

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Pure parser tests for the premium Taste Matches gate carried by a
 * `getForYouFeed` payload. No FirebaseFunctions mocks (per the project's
 * "Mockito tests fail from CLI" note in auto-memory).
 */
class ParseTasteMatchesGateTest {

    @Test
    fun `null payload is not gated`() {
        assertNull(parseTasteMatchesGate(null))
    }

    @Test
    fun `served feed (no gated key) is not gated`() {
        val payload = mapOf(
            "posts" to listOf(mapOf("id" to "p1")),
            "hasMore" to true,
        )
        assertNull(parseTasteMatchesGate(payload))
    }

    @Test
    fun `needMorePosts gate parses count and threshold`() {
        val gate = parseTasteMatchesGate(
            mapOf("gated" to "needMorePosts", "postCount" to 1, "threshold" to 3),
        )
        assertEquals("needMorePosts", gate?.gated)
        assertEquals(1, gate?.postCount)
        assertEquals(3, gate?.threshold)
    }

    @Test
    fun `paywall gate parses with default zero counts`() {
        val gate = parseTasteMatchesGate(mapOf("gated" to "paywall"))
        assertEquals("paywall", gate?.gated)
        assertEquals(0, gate?.postCount)
        assertEquals(0, gate?.threshold)
    }

    @Test
    fun `noMatchesYet gate parses (posted enough, no shared artist yet)`() {
        val gate = parseTasteMatchesGate(mapOf("gated" to "noMatchesYet"))
        assertEquals("noMatchesYet", gate?.gated)
        assertEquals(0, gate?.postCount)
        assertEquals(0, gate?.threshold)
    }

    @Test
    fun `numeric fields tolerate Double (Firebase number) without throwing`() {
        val gate = parseTasteMatchesGate(
            mapOf("gated" to "needMorePosts", "postCount" to 2.0, "threshold" to 3.0),
        )
        assertEquals(2, gate?.postCount)
        assertEquals(3, gate?.threshold)
    }
}
