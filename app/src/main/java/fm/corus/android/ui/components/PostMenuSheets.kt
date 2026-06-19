package fm.corus.android.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
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
import fm.corus.android.data.model.MusicService
import fm.corus.android.data.model.TrackSource
import fm.corus.android.service.AnalyticsService
import fm.corus.android.ui.screens.feed.EditCaptionSheet
import fm.corus.android.ui.theme.CorusColors
import fm.corus.android.ui.theme.CorusFont
import fm.corus.android.ui.theme.CorusSpacing
import fm.corus.android.ui.theme.CorusSystemBars
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

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
    musicService: MusicService = MusicService.SPOTIFY,
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
            // Only pad for the bottom system bar (nav bar) so the action row clears it.
            // systemBars would also add the TOP status-bar inset above the drag handle —
            // that inset is device-dependent (taller status bars push it down further),
            // which is the "too much padding above the handle" some devices showed.
            contentWindowInsets = { WindowInsets.systemBars.only(WindowInsetsSides.Bottom) },
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
                musicService = musicService,
                onDismiss = { onMenuPostChange(null) },
                onOpenInService = {
                    openTrackInPreferredService(
                        context, scope, post, musicService,
                        actions.analyticsService, actions::resolveServiceLinkUrl,
                    )
                },
                onViewSongPage = { onNavigateToSong(post.track) },
                onViewFilmPage = { onNavigateToFilm(post.movieId ?: "") },
                showBackCoverOption = actions.remoteConfig.vinylFlipEnabled && !post.isMovie && post.track.source != TrackSource.SOUNDCLOUD,
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

/**
 * Opens a post's track in the viewer's preferred music service. Mirrors the
 * inline service-logo button on the post card and iOS's `openInMusicService`:
 * SoundCloud-source and Apple-Music-only tracks lock to their own service;
 * everything else honors [musicService] (Spotify opens its own URI directly;
 * Apple Music / TIDAL / Deezer resolve the catalog URL via [resolveServiceLinkUrl]).
 */
fun openTrackInPreferredService(
    context: Context,
    scope: CoroutineScope,
    post: CymbalPost,
    musicService: MusicService,
    analytics: AnalyticsService,
    resolveServiceLinkUrl: suspend (CymbalTrack) -> String?,
) {
    val track = post.track
    fun open(url: String?) {
        url?.takeIf { it.isNotBlank() }?.let {
            runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(it))) }
        }
    }
    when {
        track.source == TrackSource.SOUNDCLOUD -> open(track.soundcloudPermalinkUrl)
        track.source == TrackSource.APPLEMUSIC &&
            (musicService == MusicService.SPOTIFY || musicService == MusicService.APPLE_MUSIC) -> {
            // Apple-only tracks aren't on Spotify, so a Spotify (or Apple Music)
            // viewer opens directly in Apple Music. URL derives from the resolved
            // appleMusicId or the `am:`-prefixed trackId. TIDAL/Deezer viewers
            // fall through to the resolver below (those catalogs carry the track).
            analytics.logMusicServiceLinkTapped(MusicService.APPLE_MUSIC.value, track.id)
            open(track.appleMusicURL)
        }
        musicService == MusicService.SPOTIFY -> {
            analytics.logSpotifyLinkTapped(track.id)
            val uri = track.spotifyURI
            val webUrl = track.spotifyWebURL
            try {
                when {
                    !uri.isNullOrBlank() -> context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(uri)))
                    !webUrl.isNullOrBlank() -> context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(webUrl)))
                }
            } catch (_: Exception) {
                open(webUrl)
            }
        }
        else -> {
            // Apple Music / TIDAL / Deezer preference on a Spotify-source post:
            // resolve that service's catalog URL via the backend, then open.
            analytics.logMusicServiceLinkTapped(musicService.value, track.id)
            scope.launch { open(resolveServiceLinkUrl(track)) }
        }
    }
}
