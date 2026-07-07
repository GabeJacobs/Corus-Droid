package fm.corus.android

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import fm.corus.android.i18n.LanguageManager
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.lifecycleScope
import com.google.firebase.auth.FirebaseAuth
import dagger.hilt.android.AndroidEntryPoint
import fm.corus.android.data.repository.SubscriptionRepository
import kotlinx.coroutines.launch
import fm.corus.android.service.AnalyticsService
import fm.corus.android.service.CorusFirebaseMessagingService
import fm.corus.android.service.DeepLinkDestination
import fm.corus.android.service.DeepLinkHandler
import fm.corus.android.ui.CorusApp
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var subscriptionRepository: SubscriptionRepository
    @Inject lateinit var analyticsService: AnalyticsService

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LanguageManager.wrapContext(newBase))
    }

    private val _pendingNotificationDestination = MutableStateFlow<DeepLinkDestination?>(null)
    val pendingNotificationDestination: StateFlow<DeepLinkDestination?> = _pendingNotificationDestination.asStateFlow()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handleNotificationIntent(intent)
        handleWebLinkIntent(intent)
        enableEdgeToEdge()

        lifecycle.addObserver(LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME && FirebaseAuth.getInstance().currentUser != null) {
                lifecycleScope.launch {
                    subscriptionRepository.checkStatus()
                    // Catches the time-rolloff case: user posted N times, left
                    // the app open overnight, comes back after the oldest post
                    // aged out. Skipped for paid/verified users and throttled
                    // to one call per POST_LIMIT_REFRESH_THROTTLE_MS.
                    subscriptionRepository.refreshPostLimitIfNeeded()
                }
            }
        })

        setContent {
            CorusApp(
                deepLinkIntent = intent,
                pendingNotificationDestination = pendingNotificationDestination,
                onNotificationDestinationConsumed = { _pendingNotificationDestination.value = null },
            )
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleNotificationIntent(intent)
        handleWebLinkIntent(intent)
    }

    /**
     * Handle an App Link / custom-scheme VIEW intent (e.g. the verified
     * https://app.corus.fm/settings/club App Link, or corus://…). Parses the
     * launch Uri into a [DeepLinkDestination] and routes it through the same
     * pendingNotificationDestination channel [handleNotificationIntent] uses, so
     * MainTabScreen navigates once it's composed. A notification intent doesn't
     * also carry a VIEW Uri, so this never fights [handleNotificationIntent];
     * the ACTION_VIEW guard keeps us from touching launcher/notification intents.
     */
    private fun handleWebLinkIntent(intent: Intent?) {
        if (intent?.action != Intent.ACTION_VIEW) return
        val destination = DeepLinkHandler.parse(intent) ?: return
        analyticsService.logDeepLinkOpened(destination.analyticsType())
        _pendingNotificationDestination.value = destination
        // Clear the data so a config-change relaunch doesn't re-navigate.
        intent.data = null
    }

    private fun handleNotificationIntent(intent: Intent?) {
        val extras = intent?.extras ?: return

        // Case 1: Foreground — our CorusFirebaseMessagingService built the notification
        // with a from_notification flag and notif_-prefixed data keys.
        val fromCustomNotification = extras.getBoolean(CorusFirebaseMessagingService.EXTRA_FROM_NOTIFICATION, false)

        // Case 2: Background — FCM auto-displayed the notification; the raw data
        // payload arrives as plain intent extras (no prefix, no from_notification flag).
        val hasRawFcmData = !fromCustomNotification && extras.getString("type") != null

        if (!fromCustomNotification && !hasRawFcmData) return

        val data = mutableMapOf<String, String>()
        if (fromCustomNotification) {
            for (key in extras.keySet()) {
                if (key.startsWith(CorusFirebaseMessagingService.EXTRA_NOTIF_PREFIX)) {
                    val cleanKey = key.removePrefix(CorusFirebaseMessagingService.EXTRA_NOTIF_PREFIX)
                    extras.getString(key)?.let { data[cleanKey] = it }
                }
            }
        } else {
            for (key in extras.keySet()) {
                extras.getString(key)?.let { data[key] = it }
            }
        }

        val destination = DeepLinkHandler.parseNotificationData(data)
        if (destination != null) {
            analyticsService.logDeepLinkOpened(destination.analyticsType())
            if (data["type"] == "taste_match") {
                val appState = if (fromCustomNotification) "foreground" else "background_or_terminated"
                analyticsService.logTasteMatchPushOpened(
                    subtype = data["subtype"] ?: "unknown",
                    fromUserId = data["fromUserId"] ?: data["userId"] ?: "",
                    appState = appState,
                )
            }
            _pendingNotificationDestination.value = destination
        }

        // Clear flags so re-launch doesn't re-trigger
        intent.removeExtra(CorusFirebaseMessagingService.EXTRA_FROM_NOTIFICATION)
        if (hasRawFcmData) intent.removeExtra("type")
    }
}
