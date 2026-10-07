package fm.corus.android

import android.app.Activity
import android.content.Intent
import android.os.Bundle

/**
 * Launcher aliases end here, so disabling one cannot finish the open MainActivity.
 * This activity has no UI and immediately gives the task its stable identity.
 */
class LauncherActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        startActivity(Intent(intent).apply {
            setClass(this@LauncherActivity, MainActivity::class.java)
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        })
        finish()
    }
}
