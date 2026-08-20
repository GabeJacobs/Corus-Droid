package fm.corus.android.ui.util

import fm.corus.android.data.local.PreferencesDataStore
import fm.corus.android.service.AnalyticsService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NotificationPermissionReaskController @Inject constructor(
    private val preferencesDataStore: PreferencesDataStore,
    private val analyticsService: AnalyticsService,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    val dismissedBanner: StateFlow<Boolean> = preferencesDataStore
        .hasDismissedNotifDisabledBanner
        .stateIn(scope, SharingStarted.Eagerly, true)

    val hasRequestedPushPermission: StateFlow<Boolean> = preferencesDataStore
        .hasRequestedPushPermission
        .stateIn(scope, SharingStarted.Eagerly, false)

    fun dismissBanner() {
        analyticsService.logNotificationPermissionReaskTapped("activity_banner", "dismiss")
        scope.launch { preferencesDataStore.setHasDismissedNotifDisabledBanner() }
    }

    fun markPushPermissionRequested() {
        scope.launch { preferencesDataStore.setHasRequestedPushPermission() }
    }

    fun logBannerEnable(openedSettings: Boolean) {
        analyticsService.logNotificationPermissionReaskTapped(
            "activity_banner",
            if (openedSettings) "open_settings" else "allow",
        )
    }
}
