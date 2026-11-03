package fm.corus.android.service

import org.junit.Assert.*
import org.junit.Test

class ReferralAnalyticsEventTest {
    @Test fun `banner names actions and sources match the iOS analytics contract`() {
        for (action in listOf("shown", "tapped", "dismissed")) {
            assertEquals(ReferralAnalyticsEvent("referral_feed_banner", action, "following"), referralAnalyticsEvent("feed_banner_$action"))
            assertEquals(ReferralAnalyticsEvent("referral_search_card", action, "users_search"), referralAnalyticsEvent("search_banner_$action"))
        }
    }
    @Test fun `other referral events keep their common funnel names without banner parameters`() {
        assertEquals(ReferralAnalyticsEvent("referral_claim_result"), referralAnalyticsEvent("claim_result"))
        assertEquals(ReferralAnalyticsEvent("referral_session"), referralAnalyticsEvent("session"))
    }
}
