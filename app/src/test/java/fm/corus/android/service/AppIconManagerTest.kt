package fm.corus.android.service

import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.*
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class AppIconManagerTest {
    private val packageName = "fm.corus.android"
    private fun component(icon: AppIcon) = ComponentName(packageName, "$packageName.${icon.alias}")
    private fun manager(pm: PackageManager): AppIconManager {
        val context = mock<Context>()
        whenever(context.packageName).thenReturn(packageName)
        whenever(context.packageManager).thenReturn(pm)
        return AppIconManager(context)
    }

    @Test fun `fresh install and explicit default state select Default`() {
        val pm = mock<PackageManager>()
        assertEquals(AppIcon.DEFAULT, manager(pm).selectedIcon())
        whenever(pm.getComponentEnabledSetting(component(AppIcon.CORUS_BLUE)))
            .thenReturn(PackageManager.COMPONENT_ENABLED_STATE_DISABLED)
        assertEquals(AppIcon.DEFAULT, manager(pm).selectedIcon())
    }

    @Test fun `selection is read from component state rather than cached preferences`() {
        val pm = mock<PackageManager>()
        whenever(pm.getComponentEnabledSetting(component(AppIcon.CORUS_BLUE)))
            .thenReturn(PackageManager.COMPONENT_ENABLED_STATE_ENABLED)
        assertEquals(AppIcon.CORUS_BLUE, manager(pm).selectedIcon())
    }

    @Test fun `Android 13 changes both launcher aliases atomically without killing app`() {
        val pm = mock<PackageManager>()
        manager(pm).changeIcon(AppIcon.CORUS_BLUE)
        val changes = argumentCaptor<List<PackageManager.ComponentEnabledSetting>>()
        verify(pm).setComponentEnabledSettings(changes.capture())
        assertEquals(2, changes.firstValue.size)
        assertEquals(setOf(component(AppIcon.DEFAULT), component(AppIcon.CORUS_BLUE)), changes.firstValue.map { it.componentName }.toSet())
        changes.firstValue.forEach { change ->
            assertEquals(PackageManager.DONT_KILL_APP, change.enabledFlags)
            assertEquals(if (change.componentName == component(AppIcon.CORUS_BLUE))
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED else PackageManager.COMPONENT_ENABLED_STATE_DISABLED, change.enabledState)
        }
        verify(pm, never()).setComponentEnabledSetting(any(), any(), any())
    }

    @Test @Config(sdk = [28]) fun `older Android enables new icon before disabling previous one`() {
        val pm = mock<PackageManager>()
        val manager = manager(pm)
        manager.changeIcon(AppIcon.CORUS_BLUE)
        inOrder(pm) {
            verify(pm).setComponentEnabledSetting(component(AppIcon.CORUS_BLUE), PackageManager.COMPONENT_ENABLED_STATE_ENABLED, PackageManager.DONT_KILL_APP)
            verify(pm).setComponentEnabledSetting(component(AppIcon.DEFAULT), PackageManager.COMPONENT_ENABLED_STATE_DISABLED, PackageManager.DONT_KILL_APP)
        }
    }

    @Test @Config(sdk = [28]) fun `failed sequential switch restores previous launcher overrides`() {
        val pm = mock<PackageManager>()
        doThrow(IllegalStateException("launcher unavailable")).doNothing().whenever(pm)
            .setComponentEnabledSetting(component(AppIcon.DEFAULT), PackageManager.COMPONENT_ENABLED_STATE_DISABLED, PackageManager.DONT_KILL_APP)
        try {
            manager(pm).changeIcon(AppIcon.CORUS_BLUE)
            fail("Expected switch failure")
        } catch (_: IllegalStateException) { }
        verify(pm).setComponentEnabledSetting(component(AppIcon.DEFAULT), PackageManager.COMPONENT_ENABLED_STATE_DEFAULT, PackageManager.DONT_KILL_APP)
        verify(pm).setComponentEnabledSetting(component(AppIcon.CORUS_BLUE), PackageManager.COMPONENT_ENABLED_STATE_DEFAULT, PackageManager.DONT_KILL_APP)
    }

    @Test fun `real manifest resolves exactly one launcher after each switch and keeps deep links`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val pm = context.packageManager
        val manager = AppIconManager(context)
        val launcher = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER).setPackage(context.packageName)
        fun launcherNames() = pm.queryIntentActivities(launcher, 0).map { it.activityInfo.name }
        assertEquals(listOf("${context.packageName}.DefaultLauncher"), launcherNames())
        manager.changeIcon(AppIcon.CORUS_BLUE)
        assertEquals(listOf("${context.packageName}.CorusBlueLauncher"), launcherNames())
        assertEquals(AppIcon.CORUS_BLUE, manager.selectedIcon())
        val deepLink = Intent(Intent.ACTION_VIEW, android.net.Uri.parse("corus://settings"))
            .addCategory(Intent.CATEGORY_BROWSABLE).setPackage(context.packageName)
        assertTrue(pm.queryIntentActivities(deepLink, 0).any { it.activityInfo.name == "${context.packageName}.MainActivity" })
        manager.changeIcon(AppIcon.DEFAULT)
        assertEquals(listOf("${context.packageName}.DefaultLauncher"), launcherNames())
    }
}
