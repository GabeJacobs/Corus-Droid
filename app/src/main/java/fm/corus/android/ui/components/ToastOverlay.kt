package fm.corus.android.ui.components

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import fm.corus.android.ui.theme.CorusFont
import fm.corus.android.ui.theme.CorusSpacing
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * Global toast manager. Call `ToastManager.show("message")` from anywhere.
 */
object ToastManager {
    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val messages: SharedFlow<String> = _messages.asSharedFlow()

    fun show(message: String) {
        _messages.tryEmit(message)
    }
}

@Composable
fun ToastHost(
    modifier: Modifier = Modifier,
) {
    var currentMessage by remember { mutableStateOf<String?>(null) }
    var visible by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        ToastManager.messages.collect { message ->
            currentMessage = message
            visible = true
            delay(2000)
            visible = false
            delay(300) // wait for exit animation
            currentMessage = null
        }
    }

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.BottomCenter,
    ) {
        AnimatedVisibility(
            visible = visible,
            enter = fadeIn() + slideInVertically { it / 2 },
            exit = fadeOut() + slideOutVertically { it / 2 },
        ) {
            currentMessage?.let { message ->
                Text(
                    text = message,
                    style = CorusFont.caption.copy(
                        fontWeight = androidx.compose.ui.text.font.FontWeight.Medium,
                    ),
                    color = Color.White,
                    modifier = Modifier
                        .padding(bottom = CorusSpacing.lg)
                        .clip(RoundedCornerShape(50))
                        .background(Color.Black.copy(alpha = 0.7f))
                        .padding(horizontal = CorusSpacing.md, vertical = CorusSpacing.sm),
                )
            }
        }
    }
}
