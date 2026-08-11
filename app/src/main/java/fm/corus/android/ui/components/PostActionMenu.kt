package fm.corus.android.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.Style
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
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
 *
 * Layout mirrors iOS `PostMenuItems` — most important at top, grouped by purpose:
 *   1. Discover          — Go to Song / Film, Artist, Album / Director, Back Cover
 *   ── divider ──
 *   2. Playback (tracks) — Play in service, Add to Queue
 *   ── divider ──
 *   3. Act on the post   — Save / Edit Caption, Share
 *   ── divider ──
 *   4. Social / danger   — Report + Block (others) / Delete (own)
 */
@Composable
fun PostActionMenu(
    post: CymbalPost,
    isMine: Boolean,
    musicService: MusicService = MusicService.SPOTIFY,
    onDismiss: () -> Unit,
    onOpenInService: () -> Unit = {},
    onAddToQueue: () -> Unit = {},
    onViewSongPage: () -> Unit = {},
    onViewFilmPage: () -> Unit = {},
    onViewBackCover: () -> Unit = {},
    showBackCoverOption: Boolean = false,
    isBackCoverFlipped: Boolean = false,
    /** Whether the viewer has saved this post — labels the Save / Unsave row,
     *  which only shows on other people's posts (own posts show Edit Caption). */
    isSaved: Boolean = false,
    /** `artist_pages_enabled` remote-config gate. When false, the "Go to Artist"
     *  and "Go to Album" rows never show — same gate as the tappable artist
     *  subtitle on the post card. */
    artistPagesEnabled: Boolean = false,
    onGoToArtist: () -> Unit = {},
    onGoToAlbum: () -> Unit = {},
    onGoToDirector: () -> Unit = {},
    onSharePost: () -> Unit = {},
    onToggleSave: () -> Unit = {},
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

        // ── 1. Discover ──
        if (isMovie) {
            MenuRow(
                icon = Icons.Filled.Movie,
                label = stringResource(R.string.post_menu_view_film_page),
                onClick = { onViewFilmPage(); onDismiss() },
            )
            if (showGoToDirectorRow(post = post, artistPagesEnabled = artistPagesEnabled)) {
                MenuRow(
                    // Material Campaign ≡ megaphone (matches iOS SF Symbol "megaphone").
                    icon = Icons.Filled.Campaign,
                    label = stringResource(R.string.post_menu_go_to_director),
                    onClick = { onGoToDirector(); onDismiss() },
                )
            }
        } else {
            MenuRow(
                icon = Icons.Filled.MusicNote,
                label = stringResource(R.string.post_menu_view_song_page),
                onClick = { onViewSongPage(); onDismiss() },
            )
        }

        if (showGoToArtistRow(post = post, artistPagesEnabled = artistPagesEnabled)) {
            MenuRow(
                icon = Icons.Filled.Person,
                label = stringResource(R.string.post_menu_go_to_artist),
                onClick = { onGoToArtist(); onDismiss() },
            )
        }
        if (showGoToAlbumRow(post = post, artistPagesEnabled = artistPagesEnabled)) {
            MenuRow(
                icon = Icons.Filled.Album,
                label = stringResource(R.string.post_menu_go_to_album),
                onClick = { onGoToAlbum(); onDismiss() },
            )
        }

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

        // ── 2. Playback (tracks) — under View Back Cover ──
        if (!isMovie) {
            HorizontalDivider(
                color = CorusColors.Divider,
                modifier = Modifier.padding(horizontal = CorusSpacing.lg),
            )

            val openLabel = when (post.track.source) {
                TrackSource.SOUNDCLOUD -> stringResource(R.string.post_menu_open_soundcloud)
                TrackSource.AUDIOMACK -> stringResource(R.string.post_menu_open_audiomack)
                TrackSource.TIDAL -> stringResource(R.string.post_menu_open_tidal)
                TrackSource.DEEZER -> stringResource(R.string.post_menu_open_deezer)
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

            if (showAddToQueueRow(post)) {
                MenuRow(
                    icon = Icons.AutoMirrored.Filled.PlaylistAdd,
                    label = stringResource(R.string.post_menu_add_to_queue),
                    onClick = { onAddToQueue(); onDismiss() },
                )
            }
        }

        // ── 3. Act on the post ──
        HorizontalDivider(
            color = CorusColors.Divider,
            modifier = Modifier.padding(horizontal = CorusSpacing.lg),
        )

        if (isMine) {
            MenuRow(
                icon = Icons.Filled.Edit,
                label = stringResource(R.string.post_menu_edit_caption),
                onClick = { onEditCaption(); onDismiss() },
            )
        } else {
            MenuRow(
                icon = if (isSaved) Icons.Filled.Bookmark else Icons.Outlined.BookmarkBorder,
                label = stringResource(
                    if (isSaved) R.string.post_menu_unsave else R.string.post_menu_save
                ),
                onClick = { onToggleSave(); onDismiss() },
            )
        }

        MenuRow(
            icon = Icons.AutoMirrored.Filled.Send,
            label = stringResource(R.string.post_menu_share),
            onClick = { onSharePost(); onDismiss() },
        )

        // ── 4. Destructive / social ──
        val showReportBlockActions = showPostReportBlockActions(isMine = isMine, authorIsBot = post.user.isBot)
        if (isMine || showReportBlockActions) {
            HorizontalDivider(
                color = CorusColors.Divider,
                modifier = Modifier.padding(horizontal = CorusSpacing.lg),
            )
        }

        if (isMine) {
            MenuRow(
                icon = Icons.Filled.Delete,
                label = stringResource(R.string.post_menu_delete_post),
                tint = CorusColors.Error,
                onClick = { onDeletePost(); onDismiss() },
            )
        } else if (showReportBlockActions) {
            MenuRow(
                icon = Icons.Filled.Flag,
                label = stringResource(R.string.post_menu_report),
                onClick = { onReportPost(); onDismiss() },
            )

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

/**
 * TIDAL/Deezer exclusives are link-out only — nothing to queue in-app (iOS).
 */
internal fun showAddToQueueRow(post: CymbalPost): Boolean =
    !post.isMovie &&
        post.track.source != TrackSource.TIDAL &&
        post.track.source != TrackSource.DEEZER

/**
 * Whether the "Go to Artist" row shows: track posts only (never films) that
 * either carry an Audiomack artist link-out url (source-locked, link-out only —
 * shown regardless of the flag since there's no Corus artist page), OR have
 * `artist_pages_enabled` on and a Spotify/Apple-Music source.
 *
 * Shown for those sources even when the post carries no `artistIds` yet: an
 * Apple-Music search post lands with `artistIds:[]` (the backend resolves the
 * Spotify ids minutes later), so the row resolves the artist on tap instead of
 * waiting for that backfill — mirroring [showGoToAlbumRow]. SoundCloud/TIDAL/
 * Deezer have no Corus artist page and are excluded. Films route to the director
 * page from their own subtitle, not here.
 */
internal fun showGoToArtistRow(post: CymbalPost, artistPagesEnabled: Boolean): Boolean =
    !post.isMovie && (
        post.track.audiomackArtistLinkOutUrl != null ||
            (artistPagesEnabled &&
                (post.track.source == TrackSource.SPOTIFY || post.track.source == TrackSource.APPLEMUSIC))
    )

/**
 * Whether the "Go to Album" row shows: track posts only (never films) that
 * either carry an Audiomack album link-out url (link-out only; "" for loose
 * singles -> hidden, never a dead item), OR have `artist_pages_enabled` on plus
 * a non-blank albumId (absent/"" on SoundCloud and pre-backfill posts -> hidden).
 */
internal fun showGoToAlbumRow(post: CymbalPost, artistPagesEnabled: Boolean): Boolean =
    !post.isMovie && (
        post.track.audiomackAlbumLinkOutUrl != null ||
            (artistPagesEnabled &&
                (post.track.source == TrackSource.SPOTIFY || post.track.source == TrackSource.APPLEMUSIC))
    )

/**
 * Whether the "Go to Director" row shows: movie posts only, `artist_pages_enabled`
 * on, and the post carries a non-blank director id (absent on pre-backfill posts
 * -> hidden). Mirrors [showGoToArtistRow] for films.
 */
internal fun showGoToDirectorRow(post: CymbalPost, artistPagesEnabled: Boolean): Boolean =
    artistPagesEnabled && post.isMovie && post.directorIds.any { it.isNotBlank() }

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
