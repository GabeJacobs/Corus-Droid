package fm.corus.android.ui.util

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat

/**
 * Helpers for requesting the Android 13+ POST_NOTIFICATIONS runtime permission.
 *
 * Matches the iOS flow where a primer at the end of onboarding requests
 * permission (Allow only). MainTab still falls back for users who signed up
 * before the onboarding ask shipped.
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

    /**
     * After Don't Allow (or on pre-13), the OS will not show the runtime
     * dialog again — same as iOS after deny. Open app notification Settings.
     */
    fun shouldOpenSystemSettings(
        notificationsEnabled: Boolean,
        sdkAtLeastTiramisu: Boolean,
        runtimePermissionGranted: Boolean,
        hasRequestedOsPrompt: Boolean,
    ): Boolean {
        if (notificationsEnabled) return false
        if (!sdkAtLeastTiramisu) return true
        if (runtimePermissionGranted) return true
        return hasRequestedOsPrompt
    }

    fun shouldOpenSystemSettings(context: Context, hasRequestedOsPrompt: Boolean): Boolean =
        shouldOpenSystemSettings(
            notificationsEnabled = areNotificationsEnabled(context),
            sdkAtLeastTiramisu = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU,
            runtimePermissionGranted = isPushPermissionGranted(context),
            hasRequestedOsPrompt = hasRequestedOsPrompt,
        )

    /** True when the OS will actually deliver notifications (runtime
     *  permission on 13+ and the user's app-level notification toggle). */
    fun areNotificationsEnabled(context: Context): Boolean =
        NotificationManagerCompat.from(context).areNotificationsEnabled()

    fun openSystemNotificationSettings(context: Context) {
        val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
            putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }
}
