package fm.corus.android

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import dagger.hilt.android.AndroidEntryPoint
import fm.corus.android.service.CorusFirebaseMessagingService
import fm.corus.android.service.DeepLinkDestination
import fm.corus.android.service.DeepLinkHandler
import fm.corus.android.ui.CorusApp
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val _pendingNotificationDestination = MutableStateFlow<DeepLinkDestination?>(null)
    val pendingNotificationDestination: StateFlow<DeepLinkDestination?> = _pendingNotificationDestination.asStateFlow()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handleNotificationIntent(intent)
        enableEdgeToEdge()
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
        if (intent?.getBooleanExtra(CorusFirebaseMessagingService.EXTRA_FROM_NOTIFICATION, false) != true) return

        val data = mutableMapOf<String, String>()
        intent.extras?.let { extras ->
            for (key in extras.keySet()) {
                if (key.startsWith(CorusFirebaseMessagingService.EXTRA_NOTIF_PREFIX)) {
                    val cleanKey = key.removePrefix(CorusFirebaseMessagingService.EXTRA_NOTIF_PREFIX)
                    extras.getString(key)?.let { data[cleanKey] = it }
                }
            }
        }

        val destination = DeepLinkHandler.parseNotificationData(data)
        if (destination != null) {
            _pendingNotificationDestination.value = destination
        }

        // Clear the flag so re-launch doesn't re-trigger
        intent.removeExtra(CorusFirebaseMessagingService.EXTRA_FROM_NOTIFICATION)
    }
}
