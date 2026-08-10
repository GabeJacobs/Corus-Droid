package fm.corus.android.data.remote

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Pure parser tests for the free-trial banner state (`trial`) carried by a
 * LIVE `getForYouFeed` payload. No FirebaseFunctions mocks (per the project's
 * "Mockito tests fail from CLI" note in auto-memory). Mirrors
 * ParseTasteMatchesGateTest.
 */
class ParseTasteMatchesTrialTest {

    @Test
    fun `null payload has no trial`() {
        assertNull(parseTasteMatchesTrial(null))
    }

    @Test
    fun `payload without trial key has no trial`() {
        val payload = mapOf("posts" to listOf(mapOf("id" to "p1")), "hasMore" to true)
        assertNull(parseTasteMatchesTrial(payload))
    }

    @Test
    fun `trial map without phase parses as null`() {
        val payload = mapOf("trial" to mapOf("postCount" to 5))
        assertNull(parseTasteMatchesTrial(payload))
    }

    @Test
    fun `preview phase parses postCount only`() {
        val payload = mapOf("trial" to mapOf("phase" to "preview", "postCount" to 4))
        val trial = parseTasteMatchesTrial(payload)
        assertEquals("preview", trial?.phase)
        assertEquals(4, trial?.postCount)
        assertNull(trial?.startedAt)
        assertNull(trial?.endsAt)
        assertNull(trial?.daysRemaining)
    }

    @Test
    fun `trial phase parses full clock fields`() {
        val payload = mapOf(
            "trial" to mapOf(
                "phase" to "trial",
                "postCount" to 12,
                "startedAt" to 1_700_000_000_000L,
                "endsAt" to 1_700_604_800_000L,
                "daysRemaining" to 3,
            ),
        )
        val trial = parseTasteMatchesTrial(payload)
        assertEquals("trial", trial?.phase)
        assertEquals(12, trial?.postCount)
        assertEquals(1_700_000_000_000L, trial?.startedAt)
        assertEquals(1_700_604_800_000L, trial?.endsAt)
        assertEquals(3, trial?.daysRemaining)
    }

    @Test
    fun `numeric fields tolerate Double (Firebase number) without throwing`() {
        val payload = mapOf(
            "trial" to mapOf(
                "phase" to "trial",
                "postCount" to 12.0,
                "startedAt" to 1_700_000_000_000.0,
                "endsAt" to 1_700_604_800_000.0,
                "daysRemaining" to 3.0,
            ),
        )
        val trial = parseTasteMatchesTrial(payload)
        assertEquals(12, trial?.postCount)
        assertEquals(1_700_000_000_000L, trial?.startedAt)
        assertEquals(1_700_604_800_000L, trial?.endsAt)
        assertEquals(3, trial?.daysRemaining)
    }
}
