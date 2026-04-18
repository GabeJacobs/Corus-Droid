package fm.corus.android.ui.util

import android.Manifest
import org.junit.Assert.assertEquals
import org.junit.Test

class PushNotificationPermissionTest {

    @Test
    fun `permission string points at POST_NOTIFICATIONS`() {
        // Guards against accidentally asking for the wrong permission; POST_NOTIFICATIONS
        // is the only runtime permission that gates Android 13+ push delivery.
        assertEquals(Manifest.permission.POST_NOTIFICATIONS, PushNotificationPermission.permission)
    }
}
