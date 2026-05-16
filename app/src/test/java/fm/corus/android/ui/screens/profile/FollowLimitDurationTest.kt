package fm.corus.android.ui.screens.profile

import org.junit.Assert.assertEquals
import org.junit.Test

class FollowLimitDurationTest {

    @Test fun `one hour boundary becomes Hours of 1`() {
        assertEquals(FollowLimitDuration.Hours(1), describeFollowLimitRetry(3600))
    }

    @Test fun `under an hour but at least a minute becomes Minutes`() {
        assertEquals(FollowLimitDuration.Minutes(30), describeFollowLimitRetry(30 * 60))
    }

    @Test fun `rounds hours to nearest 2h`() {
        // 1h 50m -> 2h
        assertEquals(FollowLimitDuration.Hours(2), describeFollowLimitRetry(110 * 60))
    }

    @Test fun `rounds hours to nearest 1h`() {
        // 1h 10m -> 1h
        assertEquals(FollowLimitDuration.Hours(1), describeFollowLimitRetry(70 * 60))
    }

    @Test fun `rounds minutes 90s up to 2`() {
        assertEquals(FollowLimitDuration.Minutes(2), describeFollowLimitRetry(90))
    }

    @Test fun `60s rounds to 1 minute`() {
        assertEquals(FollowLimitDuration.Minutes(1), describeFollowLimitRetry(60))
    }

    @Test fun `under a minute becomes Soon`() {
        assertEquals(FollowLimitDuration.Soon, describeFollowLimitRetry(45))
    }

    @Test fun `zero seconds becomes Soon`() {
        assertEquals(FollowLimitDuration.Soon, describeFollowLimitRetry(0))
    }

    @Test fun `3601s rounds to Hours of 1`() {
        assertEquals(FollowLimitDuration.Hours(1), describeFollowLimitRetry(3601))
    }

    @Test fun `61s rounds to Minutes of 1`() {
        assertEquals(FollowLimitDuration.Minutes(1), describeFollowLimitRetry(61))
    }

    @Test fun `23h59m rounds up to 24h`() {
        assertEquals(FollowLimitDuration.Hours(24), describeFollowLimitRetry(23 * 3600 + 59 * 60))
    }
}
