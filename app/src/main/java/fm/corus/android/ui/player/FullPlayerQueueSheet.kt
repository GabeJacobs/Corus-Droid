package fm.corus.android.ui.player

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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import fm.corus.android.R
import fm.corus.android.domain.NowPlayingManager
import fm.corus.android.domain.QueuedTrack
import fm.corus.android.ui.theme.CorusColors
import fm.corus.android.ui.theme.CorusFont
import fm.corus.android.ui.theme.CorusSpacing
import kotlin.math.roundToInt

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

    val upNext = remember(queue, currentIndex) {
        val idx = currentIndex ?: return@remember emptyList()
        queue.mapIndexedNotNull { i, t -> if (i > idx) i to t else null }
    }
    val earlier = remember(queue, currentIndex) {
        val idx = currentIndex ?: return@remember emptyList()
        queue.mapIndexedNotNull { i, t -> if (i < idx) i to t else null }
    }

    LaunchedEffect(upNext.isEmpty()) {
        if (upNext.isEmpty()) isEditing = false
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
                    item(key = "now-${queue[idx].trackId}-$idx") {
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
                    key = { (index, track) -> "next-${track.trackId}-$index-${track.sourcePostId}" },
                ) { (index, track) ->
                    EditableQueueRow(
                        track = track,
                        absoluteIndex = index,
                        isEditing = isEditing,
                        canReorder = true,
                        minReorderIndex = (currentIndex ?: -1) + 1,
                        maxReorderIndex = queue.lastIndex,
                        onJump = {
                            nowPlayingManager.jumpToQueueIndex(index)
                            onDismiss()
                        },
                        onRemove = {
                            nowPlayingManager.removeQueueItem(index)
                            bumpRevision()
                        },
                        onMove = { from, to ->
                            nowPlayingManager.moveQueueItem(from, to)
                            bumpRevision()
                        },
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
                    key = { (index, track) -> "earlier-${track.trackId}-$index-${track.sourcePostId}" },
                ) { (index, track) ->
                    EditableQueueRow(
                        track = track,
                        absoluteIndex = index,
                        isEditing = isEditing,
                        canReorder = false,
                        minReorderIndex = 0,
                        maxReorderIndex = 0,
                        onJump = {
                            nowPlayingManager.jumpToQueueIndex(index)
                            onDismiss()
                        },
                        onRemove = {
                            nowPlayingManager.removeQueueItem(index)
                            bumpRevision()
                        },
                        onMove = { _, _ -> },
                    )
                }
            }

            item { Spacer(modifier = Modifier.height(32.dp)) }
        }
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditableQueueRow(
    track: QueuedTrack,
    absoluteIndex: Int,
    isEditing: Boolean,
    canReorder: Boolean,
    minReorderIndex: Int,
    maxReorderIndex: Int,
    onJump: () -> Unit,
    onRemove: () -> Unit,
    onMove: (from: Int, to: Int) -> Unit,
) {
    val density = LocalDensity.current
    val rowHeightPx = with(density) { 60.dp.toPx() }
    var dragOffsetY by remember(absoluteIndex) { mutableFloatStateOf(0f) }
    var draggingIndex by remember { mutableIntStateOf(absoluteIndex) }

    LaunchedEffect(absoluteIndex) {
        draggingIndex = absoluteIndex
        dragOffsetY = 0f
    }

    val dragHandleModifier = if (isEditing && canReorder) {
        Modifier.pointerInput(absoluteIndex, minReorderIndex, maxReorderIndex) {
            detectDragGestures(
                onDragStart = {
                    draggingIndex = absoluteIndex
                    dragOffsetY = 0f
                },
                onDragEnd = {
                    dragOffsetY = 0f
                    draggingIndex = absoluteIndex
                },
                onDragCancel = {
                    dragOffsetY = 0f
                    draggingIndex = absoluteIndex
                },
                onDrag = { change, dragAmount ->
                    change.consume()
                    dragOffsetY += dragAmount.y
                    val steps = (dragOffsetY / rowHeightPx).toInt()
                    if (steps != 0) {
                        val from = draggingIndex
                        val to = (from + steps).coerceIn(minReorderIndex, maxReorderIndex)
                        if (to != from) {
                            onMove(from, to)
                            draggingIndex = to
                            dragOffsetY -= steps * rowHeightPx
                        }
                    }
                },
            )
        }
    } else {
        Modifier
    }

    val row = @Composable {
        QueueRow(
            track = track,
            isCurrent = false,
            isEditing = isEditing,
            showDragHandle = isEditing && canReorder,
            onClick = if (!isEditing) onJump else null,
            onRemove = if (isEditing) onRemove else null,
            dragHandleModifier = dragHandleModifier,
            modifier = if (isEditing && canReorder) {
                Modifier.offset { IntOffset(0, dragOffsetY.roundToInt()) }
            } else {
                Modifier
            },
        )
    }

    if (isEditing) {
        row()
    } else {
        val dismissState = rememberSwipeToDismissBoxState()
        LaunchedEffect(dismissState.currentValue) {
            if (dismissState.currentValue == SwipeToDismissBoxValue.EndToStart) {
                onRemove()
            }
        }
        SwipeToDismissBox(
            state = dismissState,
            enableDismissFromStartToEnd = false,
            backgroundContent = {
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
            },
        ) {
            Box(modifier = Modifier.background(CorusColors.Background)) {
                row()
            }
        }
    }
}

@Composable
private fun QueueRow(
    track: QueuedTrack,
    isCurrent: Boolean,
    isEditing: Boolean = false,
    showDragHandle: Boolean = false,
    onClick: (() -> Unit)? = null,
    onRemove: (() -> Unit)? = null,
    dragHandleModifier: Modifier = Modifier,
    modifier: Modifier = Modifier,
) {
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
        if (isEditing && onRemove != null) {
            IconButton(
                onClick = onRemove,
                modifier = Modifier.size(36.dp),
            ) {
                Icon(
                    imageVector = Icons.Filled.RemoveCircle,
                    contentDescription = stringResource(R.string.full_player_queue_remove),
                    tint = CorusColors.Error,
                )
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
        if (showDragHandle) {
            Icon(
                imageVector = Icons.Filled.DragHandle,
                contentDescription = stringResource(R.string.full_player_queue_reorder),
                tint = CorusColors.Secondary,
                modifier = dragHandleModifier
                    .size(28.dp)
                    .padding(2.dp),
            )
        }
    }
}
