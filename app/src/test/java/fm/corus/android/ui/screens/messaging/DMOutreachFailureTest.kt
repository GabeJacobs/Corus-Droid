package fm.corus.android.ui.screens.messaging

import org.junit.Assert.*
import org.junit.Test

class DMOutreachFailureTest {
    @Test fun parsesQuotaAndRestrictionDetails() {
        val retry = System.currentTimeMillis() + 3600000
        for (reason in listOf("newRecipientLimit", "dmOutreachRestricted")) {
            val parsed = DMOutreachFailure.fromDetails(mapOf("reason" to reason, "retryAtMs" to retry))!!
            assertEquals(reason, parsed.reason)
            assertEquals(retry, parsed.retryAtMs)
            assertFalse(parsed.canRetry)
        }
    }
    @Test fun genericAndPrivacyErrorsRemainSeparate() {
        for (details in listOf(null, mapOf("whoCanMessage" to "nobody"), mapOf("reason" to "other", "retryAtMs" to 100), mapOf("reason" to "newRecipientLimit", "retryAtMs" to "later"), mapOf("reason" to "newRecipientLimit", "retryAtMs" to Double.NaN))) {
            assertNull(DMOutreachFailure.fromDetails(details))
        }
    }
    @Test fun retryBecomesAvailableAfterReset() {
        assertTrue(DMOutreachFailure("newRecipientLimit", System.currentTimeMillis() - 1000).canRetry)
    }
}
