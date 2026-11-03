package fm.corus.android.service

import org.junit.Assert.*
import org.junit.Test

class ReferralSDKReadinessTest {
    @Test fun `readiness before configuration starts once when enabled`() {
        val state = ReferralSDKReadiness()
        state.ready()
        assertFalse(state.takeStart(false))
        assertTrue(state.takeStart(true))
        assertFalse(state.takeStart(true))
        assertFalse(state.takeStart(false))
        assertFalse(state.takeStart(true))
    }
    @Test fun `configuration before readiness waits and new sessions start again`() {
        val state = ReferralSDKReadiness()
        assertFalse(state.takeStart(true))
        state.ready()
        assertTrue(state.takeStart(true))
        state.ready()
        assertTrue(state.takeStart(true))
        assertFalse(state.takeStart(true))
    }
}
