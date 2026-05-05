package fm.corus.android.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import fm.corus.android.R
import fm.corus.android.data.model.CymbalPost
import fm.corus.android.data.model.CymbalTrack
import fm.corus.android.ui.screens.feed.EditCaptionSheet
import fm.corus.android.ui.theme.CorusColors
import fm.corus.android.ui.theme.CorusFont
import fm.corus.android.ui.theme.CorusSpacing
import fm.corus.android.ui.theme.CorusSystemBars

/**
 * Shared bottom-sheet / dialog bundle for the post "…" menu: action menu,
 * share sheet, edit-caption sheet, and delete confirmation dialog. All three
 * post-listing screens (Feed, ProfileFeed, PostDetail) render these the same
 * way — this composable keeps them in sync.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PostMenuSheets(
    menuPost: CymbalPost?,
    sharePost: CymbalPost?,
    editCaptionPost: CymbalPost?,
    deleteConfirmPost: CymbalPost?,
    onMenuPostChange: (CymbalPost?) -> Unit,
    onSharePostChange: (CymbalPost?) -> Unit,
    onEditCaptionPostChange: (CymbalPost?) -> Unit,
    onDeleteConfirmPostChange: (CymbalPost?) -> Unit,
    actions: PostMenuActions,
    backCoverStateFor: (postId: String) -> BackCoverFlipState,
    onNavigateToSong: (CymbalTrack) -> Unit,
    onNavigateToFilm: (String) -> Unit,
    onRepost: (CymbalPost) -> Unit,
    onPostDeleted: (CymbalPost) -> Unit = {},
    onCaptionSaved: () -> Unit = {},
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val postSentMsg = stringResource(R.string.post_menu_toast_post_sent)
    val linkCopiedMsg = stringResource(R.string.post_menu_toast_link_copied)
    val captionUpdatedMsg = stringResource(R.string.post_menu_toast_caption_updated)
    val postLinkLabel = stringResource(R.string.post_menu_clip_label_post_link)

    // When Share is tapped from the "…" menu, we can't open the share sheet
    // immediately — two ModalBottomSheets in the same frame race and the new
    // sheet never appears. Stash the post here, dismiss the menu, then open
    // the share sheet once the menu is fully gone.
    var pendingSharePost by remember { mutableStateOf<CymbalPost?>(null) }
    LaunchedEffect(menuPost, pendingSharePost) {
        val pending = pendingSharePost
        if (menuPost == null && pending != null) {
            onSharePostChange(pending)
            pendingSharePost = null
        }
    }

    // ── Share Post Bottom Sheet ──
    sharePost?.let { post ->
        val shareSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        val shareSearchResults by actions.shareSearchResults.collectAsState()
        val recentShareContacts by actions.recentShareContacts.collectAsState()
        val isShareSearching by actions.isShareSearching.collectAsState()
        val isLoadingShareContacts by actions.isLoadingShareContacts.collectAsState()

        LaunchedEffect(Unit) {
            actions.loadRecentShareContacts()
        }

        ModalBottomSheet(
            onDismissRequest = { onSharePostChange(null) },
            sheetState = shareSheetState,
            containerColor = CorusColors.Background,
            dragHandle = null,
            shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
            contentWindowInsets = { WindowInsets.systemBars },
        ) {
            CorusSystemBars()
            BackHandler { onSharePostChange(null) }
            SharePostSheet(
                post = post,
                recentContacts = recentShareContacts,
                searchResults = shareSearchResults,
                isSearching = isShareSearching,
                isLoadingContacts = isLoadingShareContacts,
                instagramShareEnabled = actions.remoteConfig.instagramShareEnabled,
                sheetState = shareSheetState,
                onSearchQueryChange = { query -> actions.searchShareUsers(query) },
                onSendToUser = { userId, message ->
                    actions.sendPostToUser(userId, post, message)
                    ToastManager.show(postSentMsg)
                    onSharePostChange(null)
                },
                onRepost = {
                    onSharePostChange(null)
                    onRepost(post)
                },
                onDismiss = { onSharePostChange(null) },
                onAnalyticsLog = { method ->
                    actions.analyticsService.logPostShared(
                        postId = post.id,
                        mediaType = if (post.isMovie) "movie" else "track",
                        method = method,
                    )
                },
            )
        }
    }

    // ── Post Action Menu Bottom Sheet ──
    menuPost?.let { post ->
        val menuSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        val isOwn = actions.isOwnPost(post)

        ModalBottomSheet(
            onDismissRequest = { onMenuPostChange(null) },
            sheetState = menuSheetState,
            containerColor = CorusColors.Background,
            dragHandle = null,
            shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
        ) {
            CorusSystemBars()
            BackHandler { onMenuPostChange(null) }
            PostActionMenu(
                post = post,
                isMine = isOwn,
                onDismiss = { onMenuPostChange(null) },
                onViewSongPage = { onNavigateToSong(post.track) },
                onViewFilmPage = { onNavigateToFilm(post.movieId ?: "") },
                showBackCoverOption = actions.remoteConfig.vinylFlipEnabled && !post.isMovie,
                isBackCoverFlipped = backCoverStateFor(post.id).isFlipped,
                onViewBackCover = {
                    val state = backCoverStateFor(post.id)
                    if (state.isFlipped) {
                        state.flipBack()
                    } else {
                        scope.launchBackCoverFlip(state, post.id) { actions.fetchBackCover(it) }
                    }
                },
                onRepost = { onRepost(post) },
                onSharePost = { pendingSharePost = post },
                onCopyLink = {
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    clipboard.setPrimaryClip(ClipData.newPlainText(postLinkLabel, "https://corus.fm/post/${post.id}"))
                    ToastManager.show(linkCopiedMsg)
                },
                onEditCaption = { onEditCaptionPostChange(post) },
                onDeletePost = { onDeleteConfirmPostChange(post) },
                onReportPost = { actions.reportPost(post.id, post.user.id) },
                onBlockUser = { actions.blockUser(post.user.id) },
            )
        }
    }

    // ── Delete Confirmation Dialog ──
    deleteConfirmPost?.let { post ->
        AlertDialog(
            onDismissRequest = { onDeleteConfirmPostChange(null) },
            title = { Text(stringResource(R.string.post_menu_dialog_delete_title), style = CorusFont.songTitle, color = CorusColors.Text) },
            text = {
                Text(
                    stringResource(R.string.post_menu_dialog_delete_message),
                    style = CorusFont.body,
                    color = CorusColors.Secondary,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    actions.deletePost(post.id)
                    onDeleteConfirmPostChange(null)
                    onPostDeleted(post)
                }) {
                    Text(stringResource(R.string.post_menu_dialog_delete_confirm), style = CorusFont.button, color = CorusColors.Error)
                }
            },
            dismissButton = {
                TextButton(onClick = { onDeleteConfirmPostChange(null) }) {
                    Text(stringResource(R.string.post_menu_dialog_delete_cancel), style = CorusFont.button, color = CorusColors.Text)
                }
            },
            containerColor = CorusColors.Background,
            shape = RoundedCornerShape(CorusSpacing.cornerRadiusMedium),
        )
    }

    // ── Edit Caption Sheet ──
    editCaptionPost?.let { post ->
        val editSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

        ModalBottomSheet(
            onDismissRequest = { onEditCaptionPostChange(null) },
            sheetState = editSheetState,
            containerColor = CorusColors.Background,
            dragHandle = null,
            shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
        ) {
            CorusSystemBars()
            EditCaptionSheet(
                postId = post.id,
                initialCaption = post.caption.orEmpty(),
                albumArtURL = post.displayImageURL,
                onDismiss = { onEditCaptionPostChange(null) },
                onSaved = { _ ->
                    onEditCaptionPostChange(null)
                    ToastManager.show(captionUpdatedMsg)
                    onCaptionSaved()
                },
            )
        }
    }
}
