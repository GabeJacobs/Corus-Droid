package fm.corus.android.service

import android.os.Bundle
import com.google.firebase.analytics.FirebaseAnalytics
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class ClubTrialAnalyticsTest {
    private val firebase = mock<FirebaseAnalytics>()
    private val service = AnalyticsService(firebase)

    @Test fun `all arms have stable labels and unknown is never called no trial`() {
        assertEquals("two_weeks", ClubTrialAnalytics.parameters("default")["club_trial_variant"])
        assertEquals("no_trial", ClubTrialAnalytics.parameters("club_trial_test_no_trial")["club_trial_variant"])
        assertEquals("one_month", ClubTrialAnalytics.parameters("club_trial_test_1_month")["club_trial_variant"])
        assertEquals("other", ClubTrialAnalytics.parameters("different")["club_trial_variant"])
        assertTrue(ClubTrialAnalytics.parameters(null).isEmpty())
    }

    @Test fun `refreshes do not duplicate impressions but reopening starts a new view`() {
        val exposure = ClubPaywallExposure()
        assertFalse(exposure.claim(null))
        assertTrue(exposure.claim("default"))
        assertFalse(exposure.claim("default"))
        assertTrue(exposure.claim("club_trial_test_no_trial"))
        assertFalse(exposure.claim("default"))
        exposure.reset()
        assertTrue(exposure.claim("default"))
    }

    @Test fun `offering properties annotate engagement and loaded is emitted only on changes`() {
        repeat(2) { service.setClubOffering("club_trial_test_no_trial") }
        service.logEvent("post_created")
        val params = argumentCaptor<Bundle>()
        verify(firebase).logEvent(eq("post_created"), params.capture())
        assertEquals("no_trial", params.firstValue.getString("club_trial_variant"))
        verify(firebase, times(1)).logEvent(eq("club_offering_loaded"), org.mockito.kotlin.any())
        verify(firebase, times(2)).setUserProperty("club_trial_variant", "no_trial")
    }

    @Test fun `purchase keeps original offering when the active offering changes`() {
        service.setClubOffering("club_trial_test_1_month")
        service.logPurchaseCompleted("monthly", "onboarding", "club_trial_test_no_trial", "monthly_no_trial")
        val params = argumentCaptor<Bundle>()
        verify(firebase).logEvent(eq("purchase_completed"), params.capture())
        assertEquals("no_trial", params.firstValue.getString("club_trial_variant"))
        assertEquals("monthly_no_trial", params.firstValue.getString("club_product_id"))
        assertEquals("onboarding", params.firstValue.getString("source"))
    }

    @Test fun `account changes and logout clear prior offering attribution`() {
        service.setUserId("first")
        service.setClubOffering("club_trial_test_1_month")
        service.setUserId("second")
        service.logEvent("after_switch")
        service.setClubOffering("club_trial_test_no_trial")
        service.clearUserProperties()
        service.logEvent("after_logout")
        for (name in listOf("after_switch", "after_logout")) {
            val params = argumentCaptor<Bundle>()
            verify(firebase).logEvent(eq(name), params.capture())
            assertFalse(params.firstValue.containsKey("club_trial_variant"))
            assertFalse(params.firstValue.containsKey("club_offering_id"))
        }
    }
}
