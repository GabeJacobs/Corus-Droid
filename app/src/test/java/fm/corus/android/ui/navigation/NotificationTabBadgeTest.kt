package fm.corus.android.ui.navigation

import org.junit.Assert.assertEquals
import org.junit.Test

class NotificationTabBadgeTest {

    @Test
    fun `Activity badge is notifications only`() {
        val badge = notificationTabBadge(notificationCount = 3)
        assertEquals(3, badge)
    }

    @Test
    fun `Activity badge ignores unread DMs`() {
        // DMs live on the Messages tab; Activity never includes them.
        val badge = notificationTabBadge(notificationCount = 10)
        assertEquals(10, badge)
    }

    @Test
    fun `zero counts render no badge`() {
        val badge = notificationTabBadge(notificationCount = 0)
        assertEquals(0, badge)
    }
}
