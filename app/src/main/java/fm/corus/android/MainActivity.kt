package fm.corus.android

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.google.firebase.auth.FirebaseAuth
import dagger.hilt.android.AndroidEntryPoint
import fm.corus.android.data.repository.SubscriptionRepository
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

    private val _pendingNotificationDestination = MutableStateFlow<DeepLinkDestination?>(null)
    val pendingNotificationDestination: StateFlow<DeepLinkDestination?> = _pendingNotificationDestination.asStateFlow()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handleNotificationIntent(intent)
        enableEdgeToEdge()

        lifecycle.addObserver(LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME && FirebaseAuth.getInstance().currentUser != null) {
                subscriptionRepository.checkStatus()
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
