package fm.corus.android.service

import org.junit.Assert.*
import org.junit.Test

class ForYouPreviewEndedAnalyticsTest {
    @Test fun `impression and Club action happen once with the displayed offer`() {
        val session = ForYouPreviewEndedAnalytics()
        assertNull(session.clubTapped()); assertNull(session.dismissed(ForYouPreviewEndedDismissReason.DISMISS))
        val shown = session.shown(true)
        assertEquals("The visible notice must emit an impression", "your_mix_preview_ended_shown", shown?.name)
        assertEquals(mapOf("has_club_trial" to 1L), shown?.params)
        assertNull(session.shown(false))
        val club = session.clubTapped()
        assertEquals("your_mix_preview_ended_club_tapped", club?.name)
        assertEquals(mapOf("has_club_trial" to 1L), club?.params)
        assertNull(session.clubTapped()); assertNull(session.dismissed(ForYouPreviewEndedDismissReason.DISMISS))
    }
    @Test fun `Continue and passive dismissal are distinct single outcomes`() {
        for (reason in ForYouPreviewEndedDismissReason.entries) {
            val session = ForYouPreviewEndedAnalytics()
            session.shown(false)
            val end = session.dismissed(reason)
            assertEquals("your_mix_preview_ended_dismissed", end?.name)
            assertEquals(mapOf("has_club_trial" to 0L, "reason" to reason.value), end?.params)
            assertNull(session.dismissed(reason)); assertNull(session.clubTapped())
        }
    }
}
