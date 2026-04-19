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
            delay(2000)
            visible = false
            delay(300) // wait for exit animation
            if (current?.id == toast.id) current = null
        }
    }

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter,
    ) {
        AnimatedVisibility(
            visible = visible,
            enter = fadeIn() + slideInVertically { -it / 2 },
            exit = fadeOut() + slideOutVertically { -it / 2 },
        ) {
            toast?.let {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(CorusSpacing.sm),
                    modifier = Modifier
                        .padding(top = CorusSpacing.lg)
                        .clip(RoundedCornerShape(50))
                        .background(Color.Black.copy(alpha = 0.7f))
                        .padding(horizontal = CorusSpacing.md, vertical = CorusSpacing.sm),
                ) {
                    if (it.isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(14.dp),
                            strokeWidth = 2.dp,
                            color = Color.White,
                        )
                    }
                    Text(
                        text = it.text,
                        style = CorusFont.caption.copy(fontWeight = FontWeight.Medium),
                        color = Color.White,
                    )
                }
            }
        }
    }
}
