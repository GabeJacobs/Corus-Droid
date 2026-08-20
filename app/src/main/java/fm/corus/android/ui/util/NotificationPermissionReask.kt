package fm.corus.android.ui.util

object NotificationPermissionReask {
    fun shouldShowBanner(isEnabled: Boolean, dismissed: Boolean): Boolean =
        !isEnabled && !dismissed
}
