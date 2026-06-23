package fm.corus.android.ui.screens.messaging

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar
import java.util.Date

/**
 * Regression tests for the thread date-separator rules (mirrors iOS
 * shouldShowTimeSeparator / day-boundary logic): a separator shows before the
 * first message, on a calendar-day change, or after a gap of an hour or more,
 * and a day-boundary separator (vs. an intra-day gap) always carries a day label.
 */
class MessageSeparatorTest {

    private fun date(year: Int, month: Int, day: Int, hour: Int, minute: Int = 0): Date {
        val cal = Calendar.getInstance()
        cal.set(year, month, day, hour, minute, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return cal.time
    }

    @Test
    fun firstMessageAlwaysShowsSeparator() {
        assertTrue(shouldShowSeparator(null, date(2026, 5, 23, 10)))
        assertTrue(isDayBoundary(null, date(2026, 5, 23, 10)))
    }

    @Test
    fun sameDaySmallGapNoSeparator() {
        val a = date(2026, 5, 23, 10, 0)
        val b = date(2026, 5, 23, 10, 30) // 30 min later
        assertFalse(shouldShowSeparator(a, b))
        assertFalse(isDayBoundary(a, b))
    }

    @Test
    fun sameDayHourGapShowsSeparatorButNotDayBoundary() {
        val a = date(2026, 5, 23, 10, 0)
        val b = date(2026, 5, 23, 11, 0) // exactly 1 hour later
        assertTrue(shouldShowSeparator(a, b))
        assertFalse(isDayBoundary(a, b)) // intra-day gap → time only, no day label
    }

    @Test
    fun differentDayShowsDayBoundary() {
        val a = date(2026, 5, 11, 23, 30)
        val b = date(2026, 5, 23, 9, 0) // 12 days later (matches the reported jump)
        assertTrue(shouldShowSeparator(a, b))
        assertTrue(isDayBoundary(a, b))
    }

    @Test
    fun nextDaySmallClockGapStillDayBoundary() {
        // 11:50pm → 12:10am is only 20 minutes but crosses midnight.
        val a = date(2026, 5, 23, 23, 50)
        val b = date(2026, 5, 24, 0, 10)
        assertTrue(shouldShowSeparator(a, b))
        assertTrue(isDayBoundary(a, b))
    }
}
