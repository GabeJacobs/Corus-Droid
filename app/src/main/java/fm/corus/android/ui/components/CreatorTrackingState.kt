package fm.corus.android.ui.components

import android.content.Context
import android.os.Bundle
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.functions.FirebaseFunctions
import fm.corus.android.R
import kotlinx.coroutines.CancellationException
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
    val state = remember(kind, id, name, enabled, uid) { CreatorTrackingState() }
    val activeState by rememberUpdatedState(state)
    var releasesOn by remember(uid) { mutableStateOf(true) }
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
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) { state.available = false }
        state.busy = false
    }
    state.toggle = {
        if (!state.busy && state.available && enabled && uid != null &&
            FirebaseAuth.getInstance().currentUser?.uid == uid
        ) {
            val previous = state.tracked
            val desired = !previous
            state.busy = true
            state.tracked = desired
            val optimisticMessage = trackingMessage(context, kind, name, desired, releasesOn)
            val toastId = ToastManager.show(optimisticMessage, durationMs = 3000)
            scope.launch {
                val action = if (desired) "track" else "untrack"
                var outcome = "success"
                try {
                    val data = functions.getHttpsCallable("setCreatorTracking")
                        .call(input + ("tracked" to desired)).await().getData() as? Map<*, *>
                    if (activeState !== state || FirebaseAuth.getInstance().currentUser?.uid != uid) return@launch
                    state.tracked = data?.get("tracked") as? Boolean ?: desired
                    releasesOn = data?.get("newReleasesEnabled") as? Boolean ?: releasesOn
                    val confirmedMessage = trackingMessage(context, kind, name, state.tracked, releasesOn)
                    if (confirmedMessage != optimisticMessage) {
                        ToastManager.dismiss(toastId)
                        ToastManager.show(confirmedMessage, durationMs = 3000)
                    }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    if (activeState !== state || FirebaseAuth.getInstance().currentUser?.uid != uid) return@launch
                    outcome = "error"
                    state.tracked = previous
                    ToastManager.dismiss(toastId)
                    ToastManager.show(context.getString(R.string.corus_discovery_tracking_error), durationMs = 3000)
                } finally { state.busy = false }
                FirebaseAnalytics.getInstance(context).logEvent("discovery_tracking", Bundle().apply {
                    putString("action", action); putString("kind", kind); putString("source", "menu"); putString("result", outcome)
                })
            }
        }
    }
    return state
}

private fun trackingMessage(context: Context, kind: String, name: String, tracked: Boolean, releasesOn: Boolean): String {
    val resource = if (kind == "artist") {
        when {
            !tracked -> R.string.corus_discovery_artist_untracked
            releasesOn -> R.string.corus_discovery_artist_tracked
            else -> R.string.corus_discovery_artist_tracked_off
        }
    } else {
        when {
            !tracked -> R.string.corus_discovery_director_untracked
            releasesOn -> R.string.corus_discovery_director_tracked
            else -> R.string.corus_discovery_director_tracked_off
        }
    }
    return context.getString(resource, name)
}
