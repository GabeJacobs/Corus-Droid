package fm.corus.android.domain

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Unit tests for [ScrubberClock] — the singleton that holds mini-player
 * scrubber position state. Mirrors the iOS ScrubberClock unit checks.
 *
 * Because it's a singleton with shared state across the JVM, every test
 * resets it in @After to avoid leaking state between cases.
 */
class ScrubberClockTest {

    @After
    fun tearDown() {
        ScrubberClock.reset()
    }

    @Test
    fun update_storesValidValues() {
        ScrubberClock.update(time = 1500L, duration = 30000L)
        assertEquals(1500L, ScrubberClock.time.value)
        assertEquals(30000L, ScrubberClock.duration.value)
    }

    @Test
    fun update_ignoresNegativeTime() {
        ScrubberClock.update(time = 100L, duration = 1000L)
        ScrubberClock.update(time = -50L, duration = 2000L)
        // Time stays at last valid; duration updates.
        assertEquals(100L, ScrubberClock.time.value)
        assertEquals(2000L, ScrubberClock.duration.value)
    }

    @Test
    fun update_ignoresZeroOrNegativeDuration() {
        ScrubberClock.update(time = 50L, duration = 5000L)
        ScrubberClock.update(time = 200L, duration = 0L)
        // Duration unchanged; time updates.
        assertEquals(200L, ScrubberClock.time.value)
        assertEquals(5000L, ScrubberClock.duration.value)
    }

    @Test
    fun setTime_updatesPositionOnly() {
        ScrubberClock.update(time = 0L, duration = 30000L)
        ScrubberClock.setTime(12000L)
        assertEquals(12000L, ScrubberClock.time.value)
        assertEquals(30000L, ScrubberClock.duration.value)
    }

    @Test
    fun setTime_ignoresNegativeValues() {
        ScrubberClock.update(time = 500L, duration = 30000L)
        ScrubberClock.setTime(-1L)
        assertEquals(500L, ScrubberClock.time.value)
    }

    @Test
    fun reset_zerosBothFields() {
        ScrubberClock.update(time = 1500L, duration = 30000L)
        ScrubberClock.reset()
        assertEquals(0L, ScrubberClock.time.value)
        assertEquals(0L, ScrubberClock.duration.value)
    }

    @Test
    fun update_dedupesUnchangedValues() {
        // Setting the same value should be a no-op for StateFlow emission.
        // We can't directly observe "was this a no-op" in a unit test without
        // a collector, but we can at least confirm the value stays stable.
        ScrubberClock.update(time = 1000L, duration = 30000L)
        ScrubberClock.update(time = 1000L, duration = 30000L)
        assertEquals(1000L, ScrubberClock.time.value)
        assertEquals(30000L, ScrubberClock.duration.value)
    }
}
