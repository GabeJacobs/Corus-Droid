package fm.corus.android.ui.components

import android.widget.Toast
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.analytics.FirebaseAnalytics
import android.os.Bundle
import fm.corus.android.R
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class CreatorTrackingState {
    var tracked by mutableStateOf(false)
    var busy by mutableStateOf(true)
    var available by mutableStateOf(false)
    var toggle: () -> Unit = {}
}

@Composable
fun rememberCreatorTracking(kind: String, id: String, name: String, enabled: Boolean): CreatorTrackingState {
    val context = LocalContext.current
    val uid = FirebaseAuth.getInstance().currentUser?.uid
    val scope = rememberCoroutineScope()
    val state = remember(kind, id, uid) { CreatorTrackingState() }
    val functions = remember { FirebaseFunctions.getInstance("us-central1") }
    val input = mapOf("kind" to kind, "id" to id, "name" to name)
    LaunchedEffect(kind, id, name, enabled, uid) {
        state.available = false
        if (!enabled || uid == null || name.isBlank()) return@LaunchedEffect
        state.busy = true
        try {
            val data = functions.getHttpsCallable("getCreatorTracking").call(input).await().getData() as? Map<*, *>
            if (FirebaseAuth.getInstance().currentUser?.uid == uid) {
                state.tracked = data?.get("tracked") == true
                state.available = data?.get("enabled") == true
            }
        } catch (_: Exception) { state.available = false }
        state.busy = false
    }
    state.toggle = {
        if (!state.busy && state.available && enabled && uid != null) {
            state.busy = true
            scope.launch {
                val action = if (state.tracked) "untrack" else "track"
                var outcome = "success"
                try {
                    val data = functions.getHttpsCallable("setCreatorTracking")
                        .call(input + ("tracked" to !state.tracked)).await().getData() as? Map<*, *>
                    if (FirebaseAuth.getInstance().currentUser?.uid == uid) {
                        state.tracked = data?.get("tracked") == true
                        val releasesOn = data?.get("newReleasesEnabled") != false
                        val res = if (kind == "artist") {
                            if (!state.tracked) R.string.corus_discovery_artist_untracked
                            else if (releasesOn) R.string.corus_discovery_artist_tracked else R.string.corus_discovery_artist_tracked_off
                        } else {
                            if (!state.tracked) R.string.corus_discovery_director_untracked
                            else if (releasesOn) R.string.corus_discovery_director_tracked else R.string.corus_discovery_director_tracked_off
                        }
                        Toast.makeText(context, res, Toast.LENGTH_LONG).show()
                    }
                } catch (_: Exception) {
                    outcome = "error"
                    Toast.makeText(context, R.string.corus_discovery_tracking_error, Toast.LENGTH_LONG).show()
                } finally { state.busy = false }
                FirebaseAnalytics.getInstance(context).logEvent("discovery_tracking", Bundle().apply {
                    putString("action", action); putString("kind", kind); putString("source", "menu"); putString("result", outcome)
                })
            }
        }
    }
    return state
}
