package fm.corus.android.ui.components

import android.app.Application
import android.content.Context
import android.content.pm.PackageInfo
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * Verifies the Instagram share button is only offered when Instagram is actually installed,
 * mirroring iOS (which gates on `canOpenURL("instagram-stories://share")`). The share sheet
 * renders the button only when [isInstagramAvailable] is true, so this is the gate.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class InstagramAvailabilityTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun `not available when instagram is not installed`() {
        assertFalse(isInstagramAvailable(context))
    }

    @Test
    fun `available when instagram is installed`() {
        shadowOf(context.packageManager).installPackage(
            PackageInfo().apply { packageName = "com.instagram.android" },
        )

        assertTrue(isInstagramAvailable(context))
    }
}
