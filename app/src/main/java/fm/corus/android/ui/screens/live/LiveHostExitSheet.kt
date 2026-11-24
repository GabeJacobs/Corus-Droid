package fm.corus.android.ui.screens.live

import androidx.compose.foundation.layout.*
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.google.firebase.functions.FirebaseFunctionsException
import fm.corus.android.localization.CorusStrings
import fm.corus.android.ui.theme.CorusColors
import fm.corus.android.ui.theme.CorusFont
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

internal enum class LiveHostExitIntent { STAY, JOIN, PLAY }
private class NoAvailableHostDJ : Exception()

@Composable
internal fun LiveHostExitSheet(
    room: LiveRoom, vm: LiveViewModel, intent: LiveHostExitIntent, close: () -> Unit,
    onStepDown: suspend (String) -> Unit, onEnd: suspend () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var eligible by remember(room.id) { mutableStateOf<List<String>>(emptyList()) }
    var working by remember(room.id) { mutableStateOf(false) }
    var transferring by remember(room.id) { mutableStateOf(false) }
    var error by remember(room.id) { mutableStateOf<Int?>(null) }
    LaunchedEffect(room.id, room.djs.map { it.text("uid") }) {
        eligible = emptyList()
        if (room.djs.isNotEmpty()) try {
            eligible = vm.hostExitOptions(room.id)
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { /* A tap retries and presents a friendly error. */ }
    }
    val next = room.djs.firstOrNull { it.text("uid") == eligible.firstOrNull() }?.let(LivePerson::parse)
    val message = when {
        room.djs.isEmpty() -> CorusStrings.live_host_exit_end_message
        intent == LiveHostExitIntent.STAY -> CorusStrings.live_host_exit_stay
        intent == LiveHostExitIntent.JOIN -> CorusStrings.live_host_exit_join
        else -> CorusStrings.live_host_exit_play
    }
    LiveSheet(stringResource(CorusStrings.live_host_exit_title), { if (!working) close() }) {
        LiveText(stringResource(message), CorusFont.body.copy(textAlign = TextAlign.Center),
            CorusColors.Secondary, Modifier.fillMaxWidth())
        if (room.djs.isNotEmpty()) {
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                // Reserve the tallest possible host caption before the server responds.
                // Overlapping invisible text also accounts for wrapping and font scaling.
                room.djs.forEach { dj ->
                    val person = LivePerson.parse(dj)
                    LiveText(stringResource(CorusStrings.live_host_exit_next_host, "@${person.username.trimStart('@')}"),
                        CorusFont.caption.copy(textAlign = TextAlign.Center), CorusColors.Secondary,
                        Modifier.fillMaxWidth().alpha(0f).clearAndSetSemantics {})
                }
                next?.let {
                    LiveText(stringResource(CorusStrings.live_host_exit_next_host, "@${it.username.trimStart('@')}"),
                        CorusFont.caption.copy(textAlign = TextAlign.Center), CorusColors.Secondary, Modifier.fillMaxWidth())
                }
            }
        }
        if (room.djs.isNotEmpty()) {
            LivePrimary(stringResource(CorusStrings.live_host_exit_step_down),
                enabled = !working && room.pendingSkip == null, loading = working && transferring,
            ) {
                if (!working) {
                    working = true
                    transferring = true
                    error = null
                    scope.launch {
                        try {
                            // Recheck on every tap: a DJ may have just left or become unavailable.
                            val candidates = vm.hostExitOptions(room.id)
                            eligible = candidates
                            val uid = candidates.firstOrNull() ?: throw NoAvailableHostDJ()
                            onStepDown(uid)
                        } catch (cancelled: CancellationException) { throw cancelled }
                        catch (_: NoAvailableHostDJ) { error = CorusStrings.live_host_exit_unavailable }
                        catch (failure: Exception) {
                            error = if (failure is FirebaseFunctionsException &&
                                failure.code == FirebaseFunctionsException.Code.FAILED_PRECONDITION)
                                CorusStrings.live_host_exit_changed else CorusStrings.live_host_exit_failed
                        } finally { working = false; transferring = false }
                    }
                }
            }
        }
        error?.let {
            LiveText(stringResource(it), CorusFont.caption.copy(textAlign = TextAlign.Center),
                CorusColors.Error, Modifier.fillMaxWidth())
        }
        LivePrimary(stringResource(CorusStrings.live_host_exit_end_all), enabled = !working,
            destructive = true, loading = working && !transferring,
        ) {
            if (!working) {
                working = true
                error = null
                scope.launch {
                    try { onEnd() }
                    catch (cancelled: CancellationException) { throw cancelled }
                    catch (_: Exception) { error = CorusStrings.live_host_exit_end_failed }
                    finally { working = false }
                }
            }
        }
        TextButton(onClick = close, enabled = !working, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
            LiveText(stringResource(CorusStrings.live_host_exit_keep), CorusFont.bodyMedium)
        }
    }
}
