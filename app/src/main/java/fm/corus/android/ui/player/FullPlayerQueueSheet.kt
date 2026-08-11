package fm.corus.android.ui.player

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.ui.graphics.graphicsLayer
import kotlinx.coroutines.delay
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.RemoveCircle
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import coil3.compose.AsyncImage
import fm.corus.android.R
import fm.corus.android.domain.NowPlayingManager
import fm.corus.android.domain.QueuedTrack
import fm.corus.android.ui.theme.CorusColors
import fm.corus.android.ui.theme.CorusFont
import fm.corus.android.ui.theme.CorusSpacing
import kotlin.math.roundToInt

/** Shared Edit/Done chrome timing — one Animatable drives every visible row. */
private val EditChromeMs = 200
private val EditMinusSlot = 36.dp
private val EditHandleSlot = 28.dp

/**
 * Full-player Queue sheet — Now Playing / Up Next / Earlier.
 * Mirrors iOS `FullPlayerQueueSheet` (Edit/Done, swipe remove, drag reorder).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FullPlayerQueueSheet(
    nowPlayingManager: NowPlayingManager,
    onDismiss: () -> Unit,
) {
    val state by nowPlayingManager.state.collectAsState()
    var revision by remember { mutableIntStateOf(0) }
    val queue = remember(state.trackId, state.sourcePostId, revision) {
        nowPlayingManager.queueSnapshot()
    }
    val currentIndex = remember(state.trackId, state.sourcePostId, revision) {
        nowPlayingManager.currentQueueIndexSnapshot()
    }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)
    var isEditing by remember { mutableStateOf(false) }
    // One shared progress for minus / drag-handle — avoids N× expandHorizontally
    // remeasures in the LazyColumn (was the main Edit toggle hitch).
    val editChromeProgress = remember { Animatable(0f) }

    val upNext = remember(queue, currentIndex) {
        val idx = currentIndex ?: return@remember emptyList()
        queue.mapIndexedNotNull { i, t -> if (i > idx) i to t else null }
            .withStableKeys("next")
    }
    val earlier = remember(queue, currentIndex) {
        val idx = currentIndex ?: return@remember emptyList()
        queue.mapIndexedNotNull { i, t -> if (i < idx) i to t else null }
            .withStableKeys("earlier")
    }

    LaunchedEffect(upNext.isEmpty()) {
        if (upNext.isEmpty()) isEditing = false
    }

    LaunchedEffect(isEditing) {
        editChromeProgress.animateTo(
            targetValue = if (isEditing) 1f else 0f,
            animationSpec = tween(EditChromeMs, easing = FastOutSlowInEasing),
        )
    }

    fun bumpRevision() {
        revision++
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = CorusColors.Background,
    ) {
        QueueSheetToolbar(
            showsEdit = upNext.isNotEmpty(),
            isEditing = isEditing,
            onToggleEdit = { isEditing = !isEditing },
            onClose = onDismiss,
        )

        LazyColumn(
            contentPadding = PaddingValues(
                horizontal = CorusSpacing.lg,
                vertical = CorusSpacing.sm,
            ),
            verticalArrangement = Arrangement.spacedBy(CorusSpacing.xs),
        ) {
            currentIndex?.let { idx ->
                if (idx in queue.indices) {
                    item(key = "now-header") {
                        SectionHeader(stringResource(R.string.full_player_queue_now_playing))
                    }
                    item(key = "now-${queueRowIdentity(queue[idx])}") {
                        QueueRow(
                            track = queue[idx],
                            isCurrent = true,
                        )
                    }
                }
            }

            if (upNext.isNotEmpty()) {
                item(key = "next-header") {
                    SectionHeader(stringResource(R.string.full_player_queue_up_next))
                }
                items(
                    upNext,
                    key = { it.key },
                ) { row ->
                    EditableQueueRow(
                        track = row.track,
                        rowKey = row.key,
                        absoluteIndex = row.index,
                        isEditing = isEditing,
                        editChromeProgress = editChromeProgress.value,
                        canReorder = true,
                        minReorderIndex = (currentIndex ?: -1) + 1,
                        maxReorderIndex = queue.lastIndex,
                        onJump = {
                            nowPlayingManager.jumpToQueueIndex(row.index)
                            onDismiss()
                        },
                        onRemove = {
                            nowPlayingManager.removeQueueItem(row.index)
                            bumpRevision()
                        },
                        onMove = { from, to ->
                            nowPlayingManager.moveQueueItem(from, to)
                            bumpRevision()
                        },
                        // Exit collapse is owned by EditableQueueRow — avoid
                        // animateItem fadeOut keeping a dismissed (red) shell.
                        modifier = Modifier.animateItem(
                            fadeInSpec = tween(180),
                            fadeOutSpec = null,
                            placementSpec = tween(220, easing = FastOutSlowInEasing),
                        ),
                    )
                }
            } else if (queue.size <= 1) {
                item(key = "empty") {
                    Text(
                        text = stringResource(R.string.full_player_queue_empty),
                        style = CorusFont.body,
                        color = CorusColors.Secondary,
                        modifier = Modifier.padding(vertical = CorusSpacing.md),
                    )
                }
            }

            if (earlier.isNotEmpty()) {
                item(key = "earlier-header") {
                    SectionHeader(stringResource(R.string.full_player_queue_earlier))
                }
                items(
                    earlier,
                    key = { it.key },
                ) { row ->
                    EditableQueueRow(
                        track = row.track,
                        rowKey = row.key,
                        absoluteIndex = row.index,
                        isEditing = isEditing,
                        editChromeProgress = editChromeProgress.value,
                        canReorder = false,
                        minReorderIndex = 0,
                        maxReorderIndex = 0,
                        onJump = {
                            nowPlayingManager.jumpToQueueIndex(row.index)
                            onDismiss()
                        },
                        onRemove = {
                            nowPlayingManager.removeQueueItem(row.index)
                            bumpRevision()
                        },
                        onMove = { _, _ -> },
                        modifier = Modifier.animateItem(
                            fadeInSpec = tween(180),
                            fadeOutSpec = null,
                            placementSpec = tween(220, easing = FastOutSlowInEasing),
                        ),
                    )
                }
            }

            item { Spacer(modifier = Modifier.height(32.dp)) }
        }
    }
}

private data class StableQueueRow(
    val key: String,
    val index: Int,
    val track: QueuedTrack,
)

private fun queueRowIdentity(track: QueuedTrack): String =
    listOf(
        track.trackId,
        track.sourcePostId.orEmpty(),
        track.spotifyURI.orEmpty(),
        track.previewUrl.orEmpty(),
        track.trackName,
    ).joinToString("|")

private fun List<Pair<Int, QueuedTrack>>.withStableKeys(section: String): List<StableQueueRow> {
    val seen = mutableMapOf<String, Int>()
    return map { (index, track) ->
        val identity = queueRowIdentity(track)
        val occurrence = seen[identity] ?: 0
        seen[identity] = occurrence + 1
        StableQueueRow(
            key = "$section|$identity#$occurrence",
            index = index,
            track = track,
        )
    }
}

@Composable
private fun QueueSheetToolbar(
    showsEdit: Boolean,
    isEditing: Boolean,
    onToggleEdit: () -> Unit,
    onClose: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = CorusSpacing.sm)
            .height(44.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier.width(72.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            if (showsEdit) {
                TextButton(onClick = onToggleEdit) {
                    Text(
                        text = stringResource(
                            if (isEditing) R.string.full_player_queue_done
                            else R.string.full_player_queue_edit,
                        ),
                        style = CorusFont.body.copy(fontWeight = FontWeight.Medium),
                        color = CorusColors.Secondary,
                    )
                }
            }
        }
        Text(
            text = stringResource(R.string.full_player_queue_title),
            style = CorusFont.usernameLarge.copy(fontWeight = FontWeight.SemiBold),
            color = CorusColors.Text,
            textAlign = TextAlign.Center,
            modifier = Modifier.weight(1f),
        )
        Box(
            modifier = Modifier.width(72.dp),
            contentAlignment = Alignment.CenterEnd,
        ) {
            IconButton(onClick = onClose) {
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = stringResource(R.string.full_player_queue_close),
                    tint = CorusColors.Secondary,
                )
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = CorusFont.caption.copy(fontWeight = FontWeight.SemiBold),
        color = CorusColors.Secondary,
        modifier = Modifier.padding(top = CorusSpacing.md, bottom = CorusSpacing.xs),
    )
}

private val QueueRowExitMs = 200

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditableQueueRow(
    track: QueuedTrack,
    rowKey: String,
    absoluteIndex: Int,
    isEditing: Boolean,
    editChromeProgress: Float,
    canReorder: Boolean,
    minReorderIndex: Int,
    maxReorderIndex: Int,
    onJump: () -> Unit,
    onRemove: () -> Unit,
    onMove: (from: Int, to: Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    // Row art (44) + vertical padding + LazyColumn spacedBy — used only to map
    // total drag distance → index delta on release (not mid-drag).
    val rowStridePx = with(density) { (44.dp + CorusSpacing.xs * 2 + CorusSpacing.xs).toPx() }
    var dragOffsetY by remember(rowKey) { mutableFloatStateOf(0f) }
    var isDragging by remember(rowKey) { mutableStateOf(false) }
    var dragStartIndex by remember(rowKey) { mutableIntStateOf(absoluteIndex) }
    // Collapse locally first, then commit remove — avoids SwipeToDismissBox
    // settling on a red shell that animateItem kept around as a layout gap.
    var isPendingRemoval by remember(rowKey) { mutableStateOf(false) }

    val indexState = rememberUpdatedState(absoluteIndex)
    val minState = rememberUpdatedState(minReorderIndex)
    val maxState = rememberUpdatedState(maxReorderIndex)
    val onMoveState = rememberUpdatedState(onMove)
    val onRemoveState = rememberUpdatedState(onRemove)

    LaunchedEffect(isPendingRemoval) {
        if (!isPendingRemoval) return@LaunchedEffect
        delay(QueueRowExitMs.toLong())
        onRemoveState.value()
    }

    // Commit only on release. Mid-drag onMove + list recomposition used to
    // restart pointerInput (keyed on absoluteIndex) and cancel after one step.
    val dragHandleModifier = if (isEditing && canReorder && !isPendingRemoval) {
        Modifier.pointerInput(rowKey, rowStridePx) {
            detectDragGestures(
                onDragStart = {
                    isDragging = true
                    dragStartIndex = indexState.value
                    dragOffsetY = 0f
                },
                onDragEnd = {
                    val steps = (dragOffsetY / rowStridePx).roundToInt()
                    val from = dragStartIndex
                    val to = (from + steps).coerceIn(minState.value, maxState.value)
                    if (to != from) {
                        onMoveState.value(from, to)
                    }
                    dragOffsetY = 0f
                    isDragging = false
                },
                onDragCancel = {
                    dragOffsetY = 0f
                    isDragging = false
                },
                onDrag = { change, dragAmount ->
                    change.consume()
                    dragOffsetY += dragAmount.y
                },
            )
        }
    } else {
        Modifier
    }

    AnimatedVisibility(
        visible = !isPendingRemoval,
        enter = EnterTransition.None,
        exit = fadeOut(animationSpec = tween(150)) + shrinkVertically(
            animationSpec = tween(QueueRowExitMs, easing = FastOutSlowInEasing),
            shrinkTowards = Alignment.Top,
        ),
        modifier = modifier.zIndex(if (isDragging) 1f else 0f),
    ) {
        // Keep one tree for edit/browse so shared edit chrome can animate in place.
        val dismissState = rememberSwipeToDismissBoxState(
            confirmValueChange = { value ->
                if (isEditing || isPendingRemoval) return@rememberSwipeToDismissBoxState false
                if (value == SwipeToDismissBoxValue.EndToStart) {
                    isPendingRemoval = true
                    // Confirm dismiss so content stays off-screen; we clear the
                    // red chrome below and shrink the empty row instead.
                    true
                } else {
                    false
                }
            },
        )
        LaunchedEffect(isEditing) {
            if (isEditing) dismissState.snapTo(SwipeToDismissBoxValue.Settled)
        }

        SwipeToDismissBox(
            state = dismissState,
            enableDismissFromStartToEnd = false,
            enableDismissFromEndToStart = !isEditing && !isPendingRemoval,
            modifier = Modifier.then(
                if (isEditing && canReorder) {
                    Modifier.offset { IntOffset(0, dragOffsetY.roundToInt()) }
                } else {
                    Modifier
                },
            ),
            backgroundContent = {
                // Hide red once removal is committed so exit isn't a stuck red bar.
                if (!isPendingRemoval) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(CorusColors.Error)
                            .padding(horizontal = CorusSpacing.lg),
                        contentAlignment = Alignment.CenterEnd,
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Delete,
                            contentDescription = stringResource(R.string.full_player_queue_remove),
                            tint = androidx.compose.ui.graphics.Color.White,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
            },
        ) {
            Box(
                modifier = Modifier
                    .background(CorusColors.Background)
                    .graphicsLayer { alpha = if (isPendingRemoval) 0f else 1f },
            ) {
                QueueRow(
                    track = track,
                    isCurrent = false,
                    isEditing = isEditing,
                    editChromeProgress = editChromeProgress,
                    showDragHandle = canReorder,
                    onClick = if (!isEditing) onJump else null,
                    onRemove = { isPendingRemoval = true },
                    dragHandleModifier = dragHandleModifier,
                )
            }
        }
    }
}

@Composable
private fun QueueRow(
    track: QueuedTrack,
    isCurrent: Boolean,
    isEditing: Boolean = false,
    editChromeProgress: Float = 0f,
    showDragHandle: Boolean = false,
    onClick: (() -> Unit)? = null,
    onRemove: (() -> Unit)? = null,
    dragHandleModifier: Modifier = Modifier,
    modifier: Modifier = Modifier,
) {
    val showMinus = onRemove != null && !isCurrent && editChromeProgress > 0.001f
    val showHandle = showDragHandle && editChromeProgress > 0.001f

    Row(
        modifier = modifier
            .fillMaxWidth()
            .then(
                if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier,
            )
            .padding(vertical = CorusSpacing.xs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(CorusSpacing.sm),
    ) {
        if (showMinus) {
            Box(
                modifier = Modifier
                    .width(EditMinusSlot * editChromeProgress)
                    .height(EditMinusSlot)
                    .clip(RectangleShape)
                    .graphicsLayer { alpha = editChromeProgress },
                contentAlignment = Alignment.Center,
            ) {
                IconButton(
                    onClick = { onRemove?.invoke() },
                    enabled = isEditing,
                    modifier = Modifier.size(EditMinusSlot),
                ) {
                    Icon(
                        imageVector = Icons.Filled.RemoveCircle,
                        contentDescription = stringResource(R.string.full_player_queue_remove),
                        tint = CorusColors.Error,
                    )
                }
            }
        }

        AsyncImage(
            model = track.albumArtURL,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(CorusColors.Divider),
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = track.trackName,
                style = CorusFont.username.copy(fontWeight = FontWeight.SemiBold),
                color = if (isCurrent) CorusColors.Accent else CorusColors.Text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = track.artistName,
                style = CorusFont.caption,
                color = CorusColors.Secondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (isCurrent) {
            Icon(
                imageVector = Icons.Filled.GraphicEq,
                contentDescription = null,
                tint = CorusColors.Accent,
                modifier = Modifier.size(18.dp),
            )
        }
        if (showHandle) {
            Icon(
                imageVector = Icons.Filled.DragHandle,
                contentDescription = stringResource(R.string.full_player_queue_reorder),
                tint = CorusColors.Secondary,
                modifier = dragHandleModifier
                    .width(EditHandleSlot * editChromeProgress)
                    .height(EditHandleSlot)
                    .clip(RectangleShape)
                    .graphicsLayer { alpha = editChromeProgress }
                    .padding(2.dp),
            )
        }
    }
}
