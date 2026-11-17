package fm.corus.android.ui.screens.live

import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.Icon
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LocalPinnableContainer
import androidx.compose.ui.layout.PinnableContainer
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.positionInRoot
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
    scrollState: LazyListState? = null,
    content: @Composable (handle: @Composable () -> Unit) -> Unit,
) {
    var offset by remember { mutableFloatStateOf(0f) }
    var dragging by remember { mutableStateOf(false) }
    var rowTop by remember { mutableFloatStateOf(0f) }
    var startTop by remember { mutableFloatStateOf(0f) }
    var scrollDistance by remember { mutableFloatStateOf(0f) }
    val pinnable = LocalPinnableContainer.current
    val scope = rememberCoroutineScope()
    val latestMove by rememberUpdatedState(move)
    val canReorder = enabled && count > 1
    val stride = with(LocalDensity.current) { rowHeight.toPx() }
    val edgeSize = with(LocalDensity.current) { 36.dp.toPx() }
    val maxScrollSpeed = with(LocalDensity.current) { 540.dp.toPx() }
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
            .then(if (scrollState == null) Modifier else Modifier.onPlaced { rowTop = it.positionInRoot().y })
            .offset {
                val previewShift = if (dragging || dragPreview.value == null) 0 else shift
                // A pinned item can be placed right outside the viewport, even if its
                // original slot is many rows away. Keep drawing it under the finger.
                val correction = if (dragging && scrollState != null) startTop - scrollDistance - rowTop else 0f
                IntOffset(0, (offset + correction).roundToInt() + previewShift)
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
                Modifier.size(40.dp, 48.dp).pointerInput(index, count, stride, canReorder, dragPreview, scrollState, pinnable) {
                    if (!canReorder) return@pointerInput
                    var settleJob: Job? = null
                    var scrollJob: Job? = null
                    var pin: PinnableContainer.PinnedHandle? = null
                    var fingerY = 0f
                    var firstDragEvent = false
                    var travel = 0f
                    fun destination() = (index + (offset / stride).roundToInt()).coerceIn(0, count - 1)
                    fun updatePreview() {
                        offset = (travel + scrollDistance).coerceIn(-index * stride, (count - 1 - index) * stride)
                        dragPreview.value = LiveQueueDrag(index, destination())
                    }
                    fun finishDrag() {
                        scrollJob?.cancel()
                        scrollJob = null
                        pin?.release()
                        pin = null
                        if (dragging) dragPreview.value = null
                        dragging = false
                        offset = 0f
                    }
                    try {
                        detectVerticalDragGestures(
                            onDragStart = { start ->
                                if (dragPreview.value == null) {
                                    startTop = rowTop
                                    scrollDistance = 0f
                                    travel = 0f
                                    dragging = true
                                    dragPreview.value = LiveQueueDrag(index, index)
                                    if (scrollState != null) {
                                        pin = pinnable?.pin()
                                        val itemTop = scrollState.layoutInfo.visibleItemsInfo
                                            .firstOrNull { it.index == index }?.offset
                                        if (itemTop != null) {
                                            fingerY = itemTop + (stride - 48.dp.toPx()) / 2 + start.y
                                            firstDragEvent = true
                                            scrollJob = scope.launch {
                                                var lastFrame = withFrameNanos { it }
                                                while (true) {
                                                    val frame = withFrameNanos { it }
                                                    val seconds = ((frame - lastFrame) / 1_000_000_000f).coerceAtMost(.05f)
                                                    lastFrame = frame
                                                    val layout = scrollState.layoutInfo
                                                    val top = layout.viewportStartOffset
                                                    val bottom = layout.viewportEndOffset
                                                    val edge = minOf(edgeSize, (bottom - top) / 2f)
                                                    if (edge <= 0f) continue
                                                    val towardTop = ((top + edge - fingerY) / edge).coerceIn(0f, 1f)
                                                    val towardBottom = ((fingerY - bottom + edge) / edge).coerceIn(0f, 1f)
                                                    val step = (towardBottom - towardTop) * maxScrollSpeed * seconds
                                                    if ((step < 0 && scrollState.canScrollBackward) ||
                                                        (step > 0 && scrollState.canScrollForward)
                                                    ) {
                                                        val consumed = scrollState.scrollBy(step)
                                                        if (consumed != 0f) {
                                                            scrollDistance += consumed
                                                            updatePreview()
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            },
                            onDragCancel = {
                                if (settleJob?.isActive != true) finishDrag()
                            },
                            onDragEnd = {
                                if (!dragging || settleJob?.isActive == true) return@detectVerticalDragGestures
                                scrollJob?.cancel()
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
                            travel += delta
                            // The first delta is touch slop overflow; start.y already includes it.
                            if (!firstDragEvent) fingerY += delta
                            firstDragEvent = false
                            updatePreview()
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
