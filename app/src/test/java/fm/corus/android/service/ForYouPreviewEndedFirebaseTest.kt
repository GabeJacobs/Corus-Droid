package fm.corus.android.service

import android.os.Bundle
import com.google.firebase.analytics.FirebaseAnalytics
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class ForYouPreviewEndedFirebaseTest {
    @Test fun `both offers reach Firebase using supported numeric trial parameters`() {
        for (hasTrial in listOf(true, false)) {
            val firebase = mock<FirebaseAnalytics>()
            val service = AnalyticsService(firebase)
            val session = ForYouPreviewEndedAnalytics()
            val clubSession = ForYouPreviewEndedAnalytics().apply { shown(hasTrial) }
            val events = listOf(session.shown(hasTrial)!!, session.dismissed(ForYouPreviewEndedDismissReason.CONTINUE)!!, clubSession.clubTapped()!!)
            for (event in events) {
                service.logEvent(event.name, event.params)
                val bundle = argumentCaptor<Bundle>()
                verify(firebase).logEvent(eq(event.name), bundle.capture())
                assertEquals("Firebase Android supports String, long and double, not Boolean", if (hasTrial) 1L else 0L, bundle.firstValue.get("has_club_trial"))
                if (event.name.endsWith("dismissed")) assertEquals("continue", bundle.firstValue.getString("reason"))
            }
        }
    }
}
