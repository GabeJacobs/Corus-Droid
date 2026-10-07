package fm.corus.android.service

import org.junit.Assert.assertEquals
import org.junit.Test

class GiftAnalyticsTest {
    @Test fun parametersMatchWebAndIosAndUseGaCompatibleTypes() {
        assertEquals(mapOf("action" to "send_completed", "source" to "picker",
            "gift_type" to "flowers", "has_note" to "true", "available" to 2,
            "capacity" to 3, "result" to "sent"),
            GiftAnalytics.params("send_completed", "picker", "flowers", true, 2, 3, "sent"))
    }
    @Test fun optionalFieldsAreOmitted() {
        assertEquals(mapOf("action" to "thanks_started", "source" to "notification"),
            GiftAnalytics.params("thanks_started", "notification"))
    }
}
