package fm.corus.android.ui.player

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import fm.corus.android.R
import fm.corus.android.domain.NowPlayingManager
import fm.corus.android.domain.QueuedTrack
import fm.corus.android.ui.theme.CorusColors
import fm.corus.android.ui.theme.CorusFont
import fm.corus.android.ui.theme.CorusSpacing

/**
 * Full-player Queue sheet — Now Playing / Up Next / Earlier.
 * Mirrors iOS `FullPlayerQueueSheet`.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FullPlayerQueueSheet(
    nowPlayingManager: NowPlayingManager,
    onDismiss: () -> Unit,
) {
    // Recompose when now-playing identity changes so queue sections refresh.
    val state by nowPlayingManager.state.collectAsState()
    var revision by remember { mutableStateOf(0) }
    val queue = remember(state.trackId, state.sourcePostId, revision) {
        nowPlayingManager.queueSnapshot()
    }
    val currentIndex = remember(state.trackId, state.sourcePostId, revision) {
        nowPlayingManager.currentQueueIndexSnapshot()
    }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)

    val upNext = remember(queue, currentIndex) {
        val idx = currentIndex ?: return@remember emptyList()
        queue.mapIndexedNotNull { i, t -> if (i > idx) i to t else null }
    }
    val earlier = remember(queue, currentIndex) {
        val idx = currentIndex ?: return@remember emptyList()
        queue.mapIndexedNotNull { i, t -> if (i < idx) i to t else null }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = CorusColors.Background,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = CorusSpacing.lg),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.full_player_queue_title),
                style = CorusFont.usernameLarge.copy(fontWeight = FontWeight.SemiBold),
                color = CorusColors.Text,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = onDismiss) {
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = stringResource(R.string.full_player_queue_close),
                    tint = CorusColors.Secondary,
                )
            }
        }

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
                    item(key = "now-${queue[idx].trackId}") {
                        QueueRow(track = queue[idx], isCurrent = true)
                    }
                }
            }

            if (upNext.isNotEmpty()) {
                item(key = "next-header") {
                    SectionHeader(stringResource(R.string.full_player_queue_up_next))
                }
                items(upNext, key = { "next-${it.first}-${it.second.trackId}" }) { (index, track) ->
                    QueueRow(
                        track = track,
                        isCurrent = false,
                        onClick = {
                            nowPlayingManager.jumpToQueueIndex(index)
                            onDismiss()
                        },
                        onRemove = {
                            nowPlayingManager.removeQueueItem(index)
                            revision++
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
                items(earlier, key = { "earlier-${it.first}-${it.second.trackId}" }) { (index, track) ->
                    QueueRow(
                        track = track,
                        isCurrent = false,
                        onClick = {
                            nowPlayingManager.jumpToQueueIndex(index)
                            onDismiss()
                        },
                        onRemove = {
                            nowPlayingManager.removeQueueItem(index)
                            revision++
                        },
                    )
                }
            }

            item { Spacer(modifier = Modifier.height(32.dp)) }
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

@Composable
private fun QueueRow(
    track: QueuedTrack,
    isCurrent: Boolean,
    onClick: (() -> Unit)? = null,
    onRemove: (() -> Unit)? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier,
            )
            .padding(vertical = CorusSpacing.xs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(CorusSpacing.sm),
    ) {
        AsyncImage(
            model = track.albumArtURL,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(6.dp)),
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = track.trackName,
                style = CorusFont.body.copy(
                    fontWeight = if (isCurrent) FontWeight.SemiBold else FontWeight.Medium,
                ),
                color = CorusColors.Text,
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
        if (onRemove != null) {
            Text(
                text = stringResource(R.string.full_player_queue_remove),
                style = CorusFont.caption,
                color = CorusColors.Secondary,
                modifier = Modifier
                    .clickable(onClick = onRemove)
                    .padding(CorusSpacing.sm),
            )
        }
    }
}
