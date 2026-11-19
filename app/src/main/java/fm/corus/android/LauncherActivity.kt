package fm.corus.android

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen

/**
 * Launcher aliases end here, so disabling one cannot finish the open MainActivity.
 * Keep the system splash visible while handing off to the stable MainActivity.
 */
class LauncherActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
        super.onCreate(savedInstanceState)
        // Transfer the starting window to MainActivity without drawing a second UI.
        splashScreen.setKeepOnScreenCondition { true }
        startActivity(Intent(intent).apply {
            setClass(this@LauncherActivity, MainActivity::class.java)
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        })
        finish()
    }
}
