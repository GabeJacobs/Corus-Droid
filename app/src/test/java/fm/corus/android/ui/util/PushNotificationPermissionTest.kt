package fm.corus.android.ui.util

import android.Manifest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PushNotificationPermissionTest {

    @Test
    fun `permission string points at POST_NOTIFICATIONS`() {
        // Guards against accidentally asking for the wrong permission; POST_NOTIFICATIONS
        // is the only runtime permission that gates Android 13+ push delivery.
        assertEquals(Manifest.permission.POST_NOTIFICATIONS, PushNotificationPermission.permission)
    }

    @Test
    fun `skipped primer keeps native permission request available`() {
        assertFalse(
            PushNotificationPermission.shouldOpenSystemSettings(
                notificationsEnabled = false,
                sdkAtLeastTiramisu = true,
                runtimePermissionGranted = false,
                hasRequestedOsPrompt = false,
            ),
        )
    }

    @Test
    fun `declined native permission opens system settings`() {
        assertTrue(
            PushNotificationPermission.shouldOpenSystemSettings(
                notificationsEnabled = false,
                sdkAtLeastTiramisu = true,
                runtimePermissionGranted = false,
                hasRequestedOsPrompt = true,
            ),
        )
    }
}
