package fm.corus.android.ui.navigation

import org.junit.Assert.assertEquals
import org.junit.Test

class NotificationTabBadgeTest {

    @Test
    fun `on Feed tab combines unread notifications and DMs`() {
        val badge = notificationTabBadge(
            selectedTab = CorusTab.FEED,
            notificationCount = 3,
            unreadMessageCount = 2,
        )
        assertEquals(5, badge)
    }

    @Test
    fun `on Explore tab combines unread notifications and DMs`() {
        val badge = notificationTabBadge(
            selectedTab = CorusTab.EXPLORE,
            notificationCount = 4,
            unreadMessageCount = 0,
        )
        assertEquals(4, badge)
    }

    @Test
    fun `on Profile tab combines unread notifications and DMs`() {
        val badge = notificationTabBadge(
            selectedTab = CorusTab.PROFILE,
            notificationCount = 1,
            unreadMessageCount = 7,
        )
        assertEquals(8, badge)
    }

    @Test
    fun `on Notifications tab hides notifications portion`() {
        // Matches iOS: the user just entered Activity and markNotificationsRead
        // fires — suppressing the notifications portion avoids the badge re-appearing.
        val badge = notificationTabBadge(
            selectedTab = CorusTab.NOTIFICATIONS,
            notificationCount = 10,
            unreadMessageCount = 3,
        )
        assertEquals(3, badge)
    }

    @Test
    fun `zero counts render no badge`() {
        val badge = notificationTabBadge(
            selectedTab = CorusTab.FEED,
            notificationCount = 0,
            unreadMessageCount = 0,
        )
        assertEquals(0, badge)
    }
}
