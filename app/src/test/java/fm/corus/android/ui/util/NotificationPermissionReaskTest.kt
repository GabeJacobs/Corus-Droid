package fm.corus.android.ui.util

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationPermissionReaskTest {

    @Test
    fun `banner shows when notifications are off and not dismissed`() {
        assertTrue(NotificationPermissionReask.shouldShowBanner(isEnabled = false, dismissed = false))
    }

    @Test
    fun `banner hides when notifications are on`() {
        assertFalse(NotificationPermissionReask.shouldShowBanner(isEnabled = true, dismissed = false))
    }

    @Test
    fun `banner stays gone after dismiss`() {
        assertFalse(NotificationPermissionReask.shouldShowBanner(isEnabled = false, dismissed = true))
    }

    @Test
    fun `after Don't Allow, tap opens system settings`() {
        assertTrue(
            PushNotificationPermission.shouldOpenSystemSettings(
                notificationsEnabled = false,
                sdkAtLeastTiramisu = true,
                runtimePermissionGranted = false,
                hasRequestedOsPrompt = true,
            ),
        )
    }

    @Test
    fun `never-asked Android 13 still uses the system dialog`() {
        assertFalse(
            PushNotificationPermission.shouldOpenSystemSettings(
                notificationsEnabled = false,
                sdkAtLeastTiramisu = true,
                runtimePermissionGranted = false,
                hasRequestedOsPrompt = false,
            ),
        )
    }
}
