package fm.corus.android.ui.screens.search

import org.junit.Assert.*
import org.junit.Test
import java.time.*

class ConcertCalendarPolicyTest {
    @Test fun `calendar uses venue timezone while user is travelling`() {
        assertEquals(Instant.parse("2026-10-03T02:00:00Z").toEpochMilli(),
            ConcertCalendarPolicy.startMillis("2026-10-02", "19:00:00", "America/Los_Angeles", ZoneId.of("Asia/Tokyo")))
    }
    @Test fun `invalid date cannot create calendar event`() {
        assertNull(ConcertCalendarPolicy.startMillis("TBA", "19:00:00", "America/New_York"))
    }
}
