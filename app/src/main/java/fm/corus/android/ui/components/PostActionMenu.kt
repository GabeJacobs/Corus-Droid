package fm.corus.android.ui.components

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
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
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import fm.corus.android.data.model.CymbalPost
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
    onDismiss: () -> Unit,
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
    val context = LocalContext.current
    val isMovie = post.isMovie
    val spotifyUri = post.track.spotifyURI.takeIf { it.isNotBlank() }

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

        // Play in Spotify (for tracks)
        if (!isMovie && spotifyUri != null) {
            MenuRow(
                icon = Icons.Filled.PlayArrow,
                label = "Play in Spotify",
                onClick = {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(spotifyUri))
                    try {
                        context.startActivity(intent)
                    } catch (_: Exception) {
                        // Spotify not installed — try web
                        val webUri = spotifyUri.replace("spotify:", "https://open.spotify.com/")
                            .replace(":", "/")
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(webUri)))
                    }
                    onDismiss()
                },
            )
        }

        // View Song/Film Page
        if (isMovie) {
            MenuRow(
                icon = Icons.Filled.Movie,
                label = "View Film Page",
                onClick = { onViewFilmPage(); onDismiss() },
            )
        } else {
            MenuRow(
                icon = Icons.Filled.MusicNote,
                label = "View Song Page",
                onClick = { onViewSongPage(); onDismiss() },
            )
        }

        // View Back / Front Cover (tracks only, when enabled via remote config)
        if (!isMovie && showBackCoverOption) {
            MenuRow(
                icon = Icons.Outlined.Style,
                label = if (isBackCoverFlipped) "View Front Cover" else "View Back Cover",
                onClick = { onViewBackCover(); onDismiss() },
            )
        }

        // Repost
        MenuRow(
            icon = Icons.Filled.Repeat,
            label = "Repost",
            onClick = { onRepost(); onDismiss() },
        )

        // Share
        MenuRow(
            icon = Icons.Filled.Share,
            label = "Share",
            onClick = { onSharePost(); onDismiss() },
        )

        // Copy Link
        MenuRow(
            icon = Icons.Filled.ContentCopy,
            label = "Copy Link",
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
                label = "Edit Caption",
                onClick = { onEditCaption(); onDismiss() },
            )

            // Delete
            MenuRow(
                icon = Icons.Filled.Delete,
                label = "Delete Post",
                tint = CorusColors.Error,
                onClick = { onDeletePost(); onDismiss() },
            )
        } else if (showReportBlockActions) {
            // Report
            MenuRow(
                icon = Icons.Filled.Flag,
                label = "Report",
                onClick = { onReportPost(); onDismiss() },
            )

            // Block
            MenuRow(
                icon = Icons.Filled.Block,
                label = "Block User",
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
