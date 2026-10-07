package fm.corus.android.service

import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/** Exercises real PackageManager/task lifecycle behavior that Robolectric cannot model. */
@RunWith(AndroidJUnit4::class)
class AppIconSwitchLifecycleTest {
    @Test fun switchingLauncherIconKeepsTheOpenActivityResumed() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val icons = AppIconManager(context)
        val original = icons.selectedIcon()
        icons.changeIcon(AppIcon.DEFAULT)
        val launch = Intent(Intent.ACTION_MAIN)
            .addCategory(Intent.CATEGORY_LAUNCHER)
            .setComponent(ComponentName(context.packageName, "${context.packageName}.DefaultLauncher"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val lifecycle = ActivityLifecycleMonitorRegistry.getInstance()
        var opened: Activity? = null
        context.startActivity(launch)
        val deadline = SystemClock.uptimeMillis() + 15000
        while (opened == null && SystemClock.uptimeMillis() < deadline) {
            instrumentation.runOnMainSync {
                opened = lifecycle.getActivitiesInStage(Stage.RESUMED).firstOrNull {
                    it.packageName == context.packageName && it.javaClass.simpleName != "LauncherActivity"
                }
            }
            if (opened == null) SystemClock.sleep(100)
        }
        val activity = requireNotNull(opened) { "The launcher did not open Corus" }
        try {
            for (icon in listOf(AppIcon.CORUS_BLUE, AppIcon.DEFAULT)) {
                icons.changeIcon(icon)
                SystemClock.sleep(1500)
                var stage: Stage? = null
                var finishing = false
                instrumentation.runOnMainSync {
                    stage = lifecycle.getLifecycleStageOf(activity)
                    finishing = activity.isFinishing
                }
                assertEquals("Switching must not finish the open app", Stage.RESUMED, stage)
                assertFalse(finishing)
            }
        } finally {
            instrumentation.runOnMainSync { activity.finish() }
            icons.changeIcon(original)
        }
    }
}
