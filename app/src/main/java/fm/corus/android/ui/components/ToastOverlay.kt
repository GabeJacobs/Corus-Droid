package fm.corus.android.ui.components

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import fm.corus.android.ui.theme.CorusFont
import fm.corus.android.ui.theme.CorusSpacing
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import java.util.concurrent.atomic.AtomicLong

/**
 * Global toast manager. Call `ToastManager.show("message")` from anywhere.
 *
 * For long-running operations, use [showLoading] to display a spinner toast
 * immediately, then call [update] on completion or [dismiss] on error.
 */
object ToastManager {
    sealed class Event {
        abstract val id: Long
        data class Show(override val id: Long, val text: String, val isLoading: Boolean) : Event()
        data class Update(override val id: Long, val text: String) : Event()
        data class Dismiss(override val id: Long) : Event()
    }

    private val _events = MutableSharedFlow<Event>(extraBufferCapacity = 8)
    val events: SharedFlow<Event> = _events.asSharedFlow()

    private val nextId = AtomicLong(0)

    fun show(message: String): Long {
        val id = nextId.incrementAndGet()
        _events.tryEmit(Event.Show(id, message, isLoading = false))
        return id
    }

    fun showLoading(message: String): Long {
        val id = nextId.incrementAndGet()
        _events.tryEmit(Event.Show(id, message, isLoading = true))
        return id
    }

    fun update(id: Long, message: String) {
        _events.tryEmit(Event.Update(id, message))
    }

    fun dismiss(id: Long) {
        _events.tryEmit(Event.Dismiss(id))
    }
}

private data class DisplayedToast(val id: Long, val text: String, val isLoading: Boolean)

/**
 * Host for [ToastManager] messages. Uses a [Popup] so the capsule renders above
 * the expanding player / tab bar (and remains visible after bottom sheets close),
 * matching iOS's confirmation toasts.
 */
@Composable
fun ToastHost(
    modifier: Modifier = Modifier,
) {
    var current by remember { mutableStateOf<DisplayedToast?>(null) }
    var visible by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        ToastManager.events.collect { event ->
            when (event) {
                is ToastManager.Event.Show -> {
                    current = DisplayedToast(event.id, event.text, event.isLoading)
                    visible = true
                }
                is ToastManager.Event.Update -> {
                    if (current?.id == event.id) {
                        current = DisplayedToast(event.id, event.text, isLoading = false)
                        visible = true
                    }
                }
                is ToastManager.Event.Dismiss -> {
                    if (current?.id == event.id) visible = false
                }
            }
        }
    }

    val toast = current
    LaunchedEffect(toast?.id, toast?.isLoading, toast?.text) {
        if (toast != null && !toast.isLoading && visible) {
            // iOS ToastOverlay on Add to Queue holds ~1.4s before fade-out.
            delay(1400)
            visible = false
            delay(300) // wait for exit animation
            if (current?.id == toast.id) current = null
        }
    }

    // Keep a no-op layout slot so callers can still pass a modifier; the
    // visible toast is windowed via Popup so z-order isn't tied to Scaffold.
    Box(modifier = modifier)

    if (toast != null) {
        Popup(
            alignment = Alignment.TopCenter,
            properties = PopupProperties(
                focusable = false,
                dismissOnBackPress = false,
                dismissOnClickOutside = false,
            ),
        ) {
            AnimatedVisibility(
                visible = visible,
                enter = fadeIn() + slideInVertically { -it / 2 },
                exit = fadeOut() + slideOutVertically { -it / 2 },
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(CorusSpacing.sm),
                    modifier = Modifier
                        .padding(top = 72.dp)
                        .clip(RoundedCornerShape(50))
                        .background(Color.Black.copy(alpha = 0.7f))
                        .padding(horizontal = CorusSpacing.md, vertical = CorusSpacing.sm),
                ) {
                    if (toast.isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(14.dp),
                            strokeWidth = 2.dp,
                            color = Color.White,
                        )
                    }
                    Text(
                        text = toast.text,
                        style = CorusFont.caption.copy(fontWeight = FontWeight.Medium),
                        color = Color.White,
                    )
                }
            }
        }
    }
}
