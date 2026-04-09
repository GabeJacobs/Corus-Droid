package fm.corus.android.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.PersonRemove
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import fm.corus.android.data.model.CymbalPost
import fm.corus.android.ui.theme.CorusColors
import fm.corus.android.ui.theme.CorusFont

@Composable
fun PostContextMenu(
    expanded: Boolean,
    onDismiss: () -> Unit,
    post: CymbalPost,
    isOwnPost: Boolean,
    isSaved: Boolean,
    isFollowingAuthor: Boolean,
    onSpotifyTap: () -> Unit = {},
    onSongDetailTap: () -> Unit = {},
    onTrailerTap: () -> Unit = {},
    onRepostTap: () -> Unit = {},
    onShareTap: () -> Unit = {},
    onCopyLinkTap: () -> Unit = {},
    onSaveTap: () -> Unit = {},
    onEditCaptionTap: () -> Unit = {},
    onFollowTap: () -> Unit = {},
    onDeleteTap: () -> Unit = {},
    onReportTap: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismiss,
        modifier = modifier,
    ) {
        // Play in Spotify (for songs)
        if (!post.isMovie) {
            DropdownMenuItem(
                text = { Text("Play in Spotify", style = CorusFont.body, color = CorusColors.Text) },
                onClick = { onDismiss(); onSpotifyTap() },
                leadingIcon = { Icon(Icons.Filled.PlayArrow, contentDescription = null, tint = CorusColors.Secondary) },
            )
        }

        // View Song/Film Page
        DropdownMenuItem(
            text = {
                Text(
                    if (post.isMovie) "View Film Page" else "View Song Page",
                    style = CorusFont.body,
                    color = CorusColors.Text,
                )
            },
            onClick = { onDismiss(); onSongDetailTap() },
            leadingIcon = {
                Icon(
                    if (post.isMovie) Icons.Filled.Movie else Icons.Filled.MusicNote,
                    contentDescription = null,
                    tint = CorusColors.Secondary,
                )
            },
        )

        // Watch Trailer (for movies with trailer)
        if (post.isMovie && !post.trailerURL.isNullOrBlank()) {
            DropdownMenuItem(
                text = { Text("Watch Trailer", style = CorusFont.body, color = CorusColors.Text) },
                onClick = { onDismiss(); onTrailerTap() },
                leadingIcon = { Icon(Icons.Filled.PlayArrow, contentDescription = null, tint = CorusColors.Secondary) },
            )
        }

        // Repost
        DropdownMenuItem(
            text = { Text("Repost", style = CorusFont.body, color = CorusColors.Text) },
            onClick = { onDismiss(); onRepostTap() },
            leadingIcon = { Icon(Icons.Filled.Repeat, contentDescription = null, tint = CorusColors.Secondary) },
        )

        // Share
        DropdownMenuItem(
            text = { Text("Share", style = CorusFont.body, color = CorusColors.Text) },
            onClick = { onDismiss(); onShareTap() },
            leadingIcon = { Icon(Icons.Filled.Share, contentDescription = null, tint = CorusColors.Secondary) },
        )

        // Copy Link
        DropdownMenuItem(
            text = { Text("Copy Link", style = CorusFont.body, color = CorusColors.Text) },
            onClick = { onDismiss(); onCopyLinkTap() },
            leadingIcon = { Icon(Icons.Filled.ContentCopy, contentDescription = null, tint = CorusColors.Secondary) },
        )

        // Save/Unsave
        DropdownMenuItem(
            text = { Text(if (isSaved) "Unsave" else "Save", style = CorusFont.body, color = CorusColors.Text) },
            onClick = { onDismiss(); onSaveTap() },
            leadingIcon = {
                Icon(
                    if (isSaved) Icons.Filled.Bookmark else Icons.Outlined.BookmarkBorder,
                    contentDescription = null,
                    tint = CorusColors.Secondary,
                )
            },
        )

        if (isOwnPost) {
            // Edit Caption
            DropdownMenuItem(
                text = { Text("Edit Caption", style = CorusFont.body, color = CorusColors.Text) },
                onClick = { onDismiss(); onEditCaptionTap() },
                leadingIcon = { Icon(Icons.Filled.Edit, contentDescription = null, tint = CorusColors.Secondary) },
            )

            // Delete
            DropdownMenuItem(
                text = { Text("Delete", style = CorusFont.body, color = CorusColors.Error) },
                onClick = { onDismiss(); onDeleteTap() },
                leadingIcon = { Icon(Icons.Filled.Delete, contentDescription = null, tint = CorusColors.Error) },
            )
        } else {
            // Follow/Unfollow
            DropdownMenuItem(
                text = {
                    Text(
                        if (isFollowingAuthor) "Unfollow" else "Follow",
                        style = CorusFont.body,
                        color = CorusColors.Text,
                    )
                },
                onClick = { onDismiss(); onFollowTap() },
                leadingIcon = {
                    Icon(
                        if (isFollowingAuthor) Icons.Filled.PersonRemove else Icons.Filled.PersonAdd,
                        contentDescription = null,
                        tint = CorusColors.Secondary,
                    )
                },
            )

            // Report
            DropdownMenuItem(
                text = { Text("Report", style = CorusFont.body, color = CorusColors.Error) },
                onClick = { onDismiss(); onReportTap() },
                leadingIcon = { Icon(Icons.Filled.Flag, contentDescription = null, tint = CorusColors.Error) },
            )
        }
    }
}
