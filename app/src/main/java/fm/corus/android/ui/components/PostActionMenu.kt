package fm.corus.android.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.outlined.Style
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import fm.corus.android.R
import fm.corus.android.data.model.CymbalPost
import fm.corus.android.data.model.MusicService
import fm.corus.android.data.model.TrackSource
import fm.corus.android.ui.theme.CorusColors
import fm.corus.android.ui.theme.CorusFont
import fm.corus.android.ui.theme.CorusSpacing

/**
 * Bottom sheet menu with actions for a post.
 * Shows different options based on whether the current user owns the post.
 */
@Composable
fun PostActionMenu(
    post: CymbalPost,
    isMine: Boolean,
    musicService: MusicService = MusicService.SPOTIFY,
    onDismiss: () -> Unit,
    onOpenInService: () -> Unit = {},
    onViewSongPage: () -> Unit = {},
    onViewFilmPage: () -> Unit = {},
    onViewBackCover: () -> Unit = {},
    showBackCoverOption: Boolean = false,
    isBackCoverFlipped: Boolean = false,
    onRepost: () -> Unit = {},
    onSharePost: () -> Unit = {},
    onCopyLink: () -> Unit = {},
    onEditCaption: () -> Unit = {},
    onDeletePost: () -> Unit = {},
    onReportPost: () -> Unit = {},
    onBlockUser: () -> Unit = {},
) {
    val isMovie = post.isMovie

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = CorusSpacing.md),
    ) {
        // Drag handle
        Box(
            modifier = Modifier
                .padding(bottom = CorusSpacing.md)
                .align(Alignment.CenterHorizontally)
                .width(36.dp)
                .height(4.dp)
                .padding(top = CorusSpacing.sm),
        )

        // Open in the viewer's preferred music service (tracks only). Locks to
        // SoundCloud / Apple Music for those track sources; otherwise honors the
        // selected service (Spotify / Apple Music / TIDAL / Deezer). Mirrors iOS.
        if (!isMovie) {
            val openLabel = when (post.track.source) {
                TrackSource.SOUNDCLOUD -> stringResource(R.string.post_menu_open_soundcloud)
                TrackSource.APPLEMUSIC ->
                    stringResource(R.string.post_menu_play_in_service, MusicService.APPLE_MUSIC.displayLabel)
                else ->
                    stringResource(R.string.post_menu_play_in_service, musicService.displayLabel)
            }
            MenuRow(
                icon = Icons.Filled.PlayArrow,
                label = openLabel,
                onClick = { onOpenInService(); onDismiss() },
            )
        }

        // View Song/Film Page
        if (isMovie) {
            MenuRow(
                icon = Icons.Filled.Movie,
                label = stringResource(R.string.post_menu_view_film_page),
                onClick = { onViewFilmPage(); onDismiss() },
            )
        } else {
            MenuRow(
                icon = Icons.Filled.MusicNote,
                label = stringResource(R.string.post_menu_view_song_page),
                onClick = { onViewSongPage(); onDismiss() },
            )
        }

        // View Back / Front Cover (tracks only, when enabled via remote config)
        if (!isMovie && showBackCoverOption) {
            MenuRow(
                icon = Icons.Outlined.Style,
                label = stringResource(
                    if (isBackCoverFlipped) R.string.post_menu_view_front_cover
                    else R.string.post_menu_view_back_cover
                ),
                onClick = { onViewBackCover(); onDismiss() },
            )
        }

        // Repost
        MenuRow(
            icon = Icons.Filled.Repeat,
            label = stringResource(R.string.post_menu_repost),
            onClick = { onRepost(); onDismiss() },
        )

        // Share
        MenuRow(
            icon = Icons.AutoMirrored.Filled.Send,
            label = stringResource(R.string.post_menu_share),
            onClick = { onSharePost(); onDismiss() },
        )

        // Copy Link
        MenuRow(
            icon = Icons.Filled.ContentCopy,
            label = stringResource(R.string.post_menu_copy_link),
            onClick = { onCopyLink(); onDismiss() },
        )

        val showOwnerActions = isMine
        val showReportBlockActions = showPostReportBlockActions(isMine = isMine, authorIsBot = post.user.isBot)

        if (showOwnerActions || showReportBlockActions) {
            HorizontalDivider(color = CorusColors.Divider, modifier = Modifier.padding(horizontal = CorusSpacing.lg))
        }

        if (showOwnerActions) {
            // Edit Caption
            MenuRow(
                icon = Icons.Filled.Edit,
                label = stringResource(R.string.post_menu_edit_caption),
                onClick = { onEditCaption(); onDismiss() },
            )

            // Delete
            MenuRow(
                icon = Icons.Filled.Delete,
                label = stringResource(R.string.post_menu_delete_post),
                tint = CorusColors.Error,
                onClick = { onDeletePost(); onDismiss() },
            )
        } else if (showReportBlockActions) {
            // Report
            MenuRow(
                icon = Icons.Filled.Flag,
                label = stringResource(R.string.post_menu_report),
                onClick = { onReportPost(); onDismiss() },
            )

            // Block
            MenuRow(
                icon = Icons.Filled.Block,
                label = stringResource(R.string.post_menu_block_user),
                tint = CorusColors.Error,
                onClick = { onBlockUser(); onDismiss() },
            )
        }
    }
}

/**
 * Whether the report/block actions should be shown in the post action menu.
 * Bot-authored posts suppress these actions — there's no one to moderate.
 */
internal fun showPostReportBlockActions(isMine: Boolean, authorIsBot: Boolean): Boolean =
    !isMine && !authorIsBot

@Composable
private fun MenuRow(
    icon: ImageVector,
    label: String,
    tint: Color = CorusColors.Text,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = CorusSpacing.xl, vertical = CorusSpacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(22.dp),
        )
        Spacer(modifier = Modifier.width(CorusSpacing.lg))
        Text(
            text = label,
            style = CorusFont.body,
            color = tint,
        )
    }
}
