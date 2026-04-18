package fm.corus.android.ui.util

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat

/**
 * Helpers for requesting the Android 13+ POST_NOTIFICATIONS runtime permission.
 *
 * Matches the iOS flow where permission is requested during onboarding (end of
 * Follow Friends) with a MainTab fallback for users who signed up before this
 * code shipped.
 *
 * On Android < 13, POST_NOTIFICATIONS is granted at install time, so
 * [isPushPermissionGranted] returns true and [shouldRequestPushPermission]
 * returns false — callers can invoke these unconditionally.
 */
object PushNotificationPermission {

    /** The runtime permission introduced in Android 13 (API 33). */
    val permission: String = Manifest.permission.POST_NOTIFICATIONS

    fun isPushPermissionGranted(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
        return ContextCompat.checkSelfPermission(context, permission) ==
            PackageManager.PERMISSION_GRANTED
    }

    /**
     * Whether we should show the system permission dialog. False if already
     * granted, or if we're on a pre-Android-13 device where no runtime prompt
     * is needed.
     */
    fun shouldRequestPushPermission(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return false
        return !isPushPermissionGranted(context)
    }
}
