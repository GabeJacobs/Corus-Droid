package fm.corus.android.service

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlinx.coroutines.test.runTest

class ReferralClientGateTest {
    @Test fun `rollout is off until resolved for this account`() {
        val gate = ReferralClientGate()
        assertFalse(gate.enabledFor("alice"))
        gate.resolve(true, "alice")
        assertTrue(gate.enabledFor("alice"))
        assertFalse(gate.enabledFor("bob"))
        assertFalse(gate.enabledFor(null))
        gate.resolve(false, "alice")
        assertFalse(gate.enabledFor("alice"))
    }

    @Test fun `disabled requests stop before accessing Firebase and can be reset safely`() = runTest {
        ReferralService.setEnabled(false, null)
        ReferralService.track("session")
        ReferralService.reload()
        assertFalse(ReferralService.enabled.value)
        assertFalse(ReferralService.status.value.enabled)
        try {
            ReferralService.call("getReferralStatus")
            throw AssertionError("Disabled call must not reach Firebase")
        } catch (expected: IllegalStateException) {
            assertTrue(expected.message.orEmpty().contains("disabled"))
        }
    }
}
