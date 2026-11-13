package fm.corus.android.ui.screens.live

import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
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
import androidx.compose.runtime.snapshots.Snapshot
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
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

internal data class LiveQueueDrag(val from: Int, val to: Int) {
    fun shiftFor(index: Int): Int = when {
        index > from && index <= to -> -1
        index < from && index >= to -> 1
        else -> 0
    }
}

internal fun liveQueueRowKeys(queue: List<LiveTrack>): List<String> {
    val occurrences = mutableMapOf<Pair<String, String>, Int>()
    return queue.map { track ->
        val source = track.song.source.raw
        val song = source to track.id
        val count = occurrences.getOrDefault(song, 0)
        occurrences[song] = count + 1
        "queue:$source:$count:${track.id}"
    }
}

@Composable
internal fun LiveQueueReorderRow(
    index: Int,
    count: Int,
    rowHeight: Dp,
    move: (Int) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    dragPreview: MutableState<LiveQueueDrag?>,
    content: @Composable (handle: @Composable () -> Unit) -> Unit,
) {
    var offset by remember { mutableFloatStateOf(0f) }
    var dragging by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val latestMove by rememberUpdatedState(move)
    val canReorder = enabled && count > 1
    val stride = with(LocalDensity.current) { rowHeight.toPx() }
    val drag = dragPreview.value
    val shift by animateIntAsState(
        targetValue = ((drag?.shiftFor(index) ?: 0) * stride).roundToInt(),
        animationSpec = if (drag == null) snap() else tween(140),
        label = "Queue row shift",
    )
    val moveUp = stringResource(R.string.edit_profile_tab_move_up)
    val moveDown = stringResource(R.string.edit_profile_tab_move_down)
    Box(
        modifier.fillMaxWidth().zIndex(if (dragging) 1f else 0f)
            .offset {
                val previewShift = if (dragging || dragPreview.value == null) 0 else shift
                IntOffset(0, offset.roundToInt() + previewShift)
            }
            .then(if (dragging) Modifier.shadow(8.dp).background(CorusColors.SheetBackground) else Modifier)
            .semantics {
                if (canReorder) customActions = buildList {
                    if (index > 0) add(CustomAccessibilityAction(moveUp) { latestMove(index - 1); true })
                    if (index < count - 1) add(CustomAccessibilityAction(moveDown) { latestMove(index + 1); true })
                }
            },
    ) {
        content {
            Box(
                Modifier.size(40.dp, 48.dp).pointerInput(index, count, stride, canReorder, dragPreview) {
                    if (!canReorder) return@pointerInput
                    var settleJob: Job? = null
                    fun destination() = (index + (offset / stride).roundToInt()).coerceIn(0, count - 1)
                    fun finishDrag() {
                        if (dragging) dragPreview.value = null
                        dragging = false
                        offset = 0f
                    }
                    try {
                        detectVerticalDragGestures(
                            onDragStart = {
                                if (dragPreview.value == null) {
                                    dragging = true
                                    dragPreview.value = LiveQueueDrag(index, index)
                                }
                            },
                            onDragCancel = {
                                if (settleJob?.isActive != true) finishDrag()
                            },
                            onDragEnd = {
                                if (!dragging || settleJob?.isActive == true) return@detectVerticalDragGestures
                                val destination = destination()
                                settleJob = scope.launch {
                                    animate(offset, (destination - index) * stride, animationSpec = tween(160)) { value, _ ->
                                        offset = value
                                    }
                                    // Switch from preview offsets to the new list order in one frame.
                                    Snapshot.withMutableSnapshot {
                                        if (destination != index) latestMove(destination)
                                        finishDrag()
                                    }
                                }
                            },
                        ) { change, delta ->
                            if (!dragging || settleJob?.isActive == true) return@detectVerticalDragGestures
                            change.consume()
                            offset = (offset + delta).coerceIn(-index * stride, (count - 1 - index) * stride)
                            dragPreview.value = LiveQueueDrag(index, destination())
                        }
                    } finally {
                        settleJob?.cancel()
                        finishDrag()
                    }
                },
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Default.Menu, if (canReorder) stringResource(R.string.live_reorder_song) else null,
                    tint = CorusColors.Tertiary, modifier = Modifier.size(20.dp))
            }
        }
    }
}
