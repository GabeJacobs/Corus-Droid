package fm.corus.android.domain

import org.junit.Assert.*
import org.junit.Test
import java.time.Instant

class DailyHashtagBannerTest {
    private val now = Instant.parse("2026-11-03T20:00:00Z").toEpochMilli()
    private fun items(posts: Int = 7, people: Int = 7) = listOf(mapOf<String, Any>("name" to "jazztuesday", "postCount" to posts, "distinctAuthors" to people))

    @Test fun requiresSevenDistinctContributors() {
        assertEquals("jazztuesday", parseDailyHashtagBanner("2026-11-03", now, items(), now)?.name)
        assertNull(parseDailyHashtagBanner("2026-11-03", now, items(20, 6), now))
    }
    @Test fun hidesOldRemovedAndMissingData() {
        assertNull(parseDailyHashtagBanner("2026-11-02", now, items(), now))
        assertNull(parseDailyHashtagBanner("2026-11-03", now - 7_200_000L, items(), now))
        assertNull(parseDailyHashtagBanner("2026-11-03", now, emptyList(), now))
        assertNull(parseDailyHashtagBanner(null, null, null, now))
    }
    @Test fun visibleDataExpiresAtMidnightAndWhenStale() {
        val candidate = parseDailyHashtagBanner("2026-11-03", now, items(), now)!!
        assertTrue(candidate.isCurrent(now))
        assertFalse(candidate.isCurrent(now + 7_200_000L))
        val midnight = Instant.parse("2026-11-04T00:00:00Z").toEpochMilli()
        assertFalse(candidate.copy(updatedAtMillis = midnight - 1000L).isCurrent(midnight))
    }
}
