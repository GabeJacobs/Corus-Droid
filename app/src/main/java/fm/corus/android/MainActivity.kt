package fm.corus.android

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
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

    private val _pendingNotificationDestination = MutableStateFlow<DeepLinkDestination?>(null)
    val pendingNotificationDestination: StateFlow<DeepLinkDestination?> = _pendingNotificationDestination.asStateFlow()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handleNotificationIntent(intent)
        enableEdgeToEdge()

        lifecycle.addObserver(LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME && FirebaseAuth.getInstance().currentUser != null) {
                lifecycleScope.launch { subscriptionRepository.checkStatus() }
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
