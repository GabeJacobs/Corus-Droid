package fm.corus.android.ui.screens.live

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.Icon
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import fm.corus.android.R
import fm.corus.android.ui.theme.CorusColors
import kotlin.math.roundToInt

@Composable
internal fun LiveQueueReorderRow(
    index: Int,
    count: Int,
    rowHeight: Dp,
    move: (Int) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable (handle: @Composable () -> Unit) -> Unit,
) {
    var offset by remember { mutableFloatStateOf(0f) }
    var dragging by remember { mutableStateOf(false) }
    val latestMove by rememberUpdatedState(move)
    val stride = with(LocalDensity.current) { rowHeight.toPx() }
    val moveUp = stringResource(R.string.edit_profile_tab_move_up)
    val moveDown = stringResource(R.string.edit_profile_tab_move_down)
    Box(
        modifier.fillMaxWidth().zIndex(if (dragging) 1f else 0f)
            .offset { IntOffset(0, offset.roundToInt()) }
            .then(if (dragging) Modifier.shadow(8.dp).background(CorusColors.SheetBackground) else Modifier)
            .semantics {
                if (enabled) customActions = buildList {
                    if (index > 0) add(CustomAccessibilityAction(moveUp) { latestMove(index - 1); true })
                    if (index < count - 1) add(CustomAccessibilityAction(moveDown) { latestMove(index + 1); true })
                }
            },
    ) {
        content {
            Box(
                Modifier.size(40.dp, 48.dp).pointerInput(index, count, stride, enabled) {
                    if (!enabled || count < 2) return@pointerInput
                    try {
                        detectVerticalDragGestures(
                            onDragStart = { dragging = true },
                            onDragCancel = { dragging = false; offset = 0f },
                            onDragEnd = {
                                val destination = (index + (offset / stride).roundToInt()).coerceIn(0, count - 1)
                                dragging = false
                                offset = 0f
                                if (destination != index) latestMove(destination)
                            },
                        ) { change, delta ->
                            change.consume()
                            offset = (offset + delta).coerceIn(-index * stride, (count - 1 - index) * stride)
                        }
                    } finally {
                        dragging = false
                        offset = 0f
                    }
                },
                contentAlignment = Alignment.Center,
            ) {
                if (count > 1) {
                    Icon(Icons.Default.Menu, stringResource(R.string.live_reorder_song),
                        tint = CorusColors.Tertiary, modifier = Modifier.size(20.dp))
                }
            }
        }
    }
}
