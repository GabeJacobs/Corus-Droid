package fm.corus.android.ui.components

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import fm.corus.android.R
import fm.corus.android.data.model.CymbalPost
import fm.corus.android.data.model.CymbalTrack
import fm.corus.android.data.model.MusicService
import fm.corus.android.data.model.TrackSource
import fm.corus.android.domain.SongPlayRouting
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
    /** `artist_pages_enabled` remote-config gate for the menu's "Go to Artist" /
     *  "Go to Album" rows. Both rows also require the navigation callback below
     *  to be non-null (screens pass null while the flag is off). */
    artistPagesEnabled: Boolean = false,
    /** Route to the artist page. Null while `artist_pages_enabled` is off —
     *  same nullable pattern as the post card's tappable artist subtitle. */
    onNavigateToArtist: ((fm.corus.android.ui.navigation.ArtistPageRoute) -> Unit)? = null,
    /** Route to the album page. Null while `artist_pages_enabled` is off. */
    onNavigateToAlbum: ((fm.corus.android.ui.navigation.AlbumPageRoute) -> Unit)? = null,
    /** Route to the director page (movies). Null while `artist_pages_enabled` is off. */
    onNavigateToDirector: ((fm.corus.android.ui.navigation.DirectorPageRoute) -> Unit)? = null,
    /**
     * Manual Up Next — mirrors iOS Add to Queue. Return true when the track
     * was queued (or started) so we can toast “Added to Queue”.
     */
    onAddToQueue: (CymbalPost) -> Boolean = { false },
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val postSentMsg = stringResource(R.string.post_menu_toast_post_sent)
    val captionUpdatedMsg = stringResource(R.string.post_menu_toast_caption_updated)
    val addedToQueueMsg = stringResource(R.string.post_menu_toast_added_to_queue)
    val albumNotFoundMsg = stringResource(R.string.song_detail_album_not_found)
    val artistNotFoundMsg = stringResource(R.string.song_detail_artist_not_found)
    var isResolvingAlbum by remember { mutableStateOf(false) }
    var isResolvingArtist by remember { mutableStateOf(false) }
    val engagementStates by actions.engagementStates.collectAsState()

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

    // "Added to Queue" toast after the menu Dialog is gone — same timing as iOS
    // PostCard's showAddedToQueue overlay (fires once the sheet dismisses).
    var pendingAddedToQueueToast by remember { mutableStateOf(false) }
    LaunchedEffect(menuPost, pendingAddedToQueueToast) {
        if (menuPost == null && pendingAddedToQueueToast) {
            ToastManager.show(addedToQueueMsg)
            pendingAddedToQueueToast = false
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
        val isSaved = engagementStates[post.id]?.isSaved ?: false

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
                        actions::resolveSpotifyFromAppleTrack,
                    )
                },
                onAddToQueue = {
                    if (onAddToQueue(post)) pendingAddedToQueueToast = true
                },
                onViewSongPage = { onNavigateToSong(post.track) },
                onViewFilmPage = { onNavigateToFilm(post.movieId ?: "") },
                showBackCoverOption = actions.remoteConfig.vinylFlipEnabled && !post.isMovie && post.track.source != TrackSource.SOUNDCLOUD && post.track.source != TrackSource.AUDIOMACK && post.track.source != TrackSource.TIDAL && post.track.source != TrackSource.DEEZER,
                isBackCoverFlipped = backCoverStateFor(post.id).isFlipped,
                isSaved = isSaved,
                // Rows gate on the flag; the nav callbacks are non-null whenever
                // the flag is on (screens pass them together, mirroring the
                // artist-subtitle path). Tap helpers no-op if a callback is null.
                artistPagesEnabled = artistPagesEnabled,
                // Audiomack is source-locked with no Corus artist/album page, so
                // its "Go to Artist"/"Go to Album" rows link out to Audiomack's own
                // pages (only when the backend supplied a non-blank url). Non-
                // Audiomack sources fall through to the internal-nav tap builders.
                onGoToArtist = resolveMenuGoToArtistTap(
                    context = context,
                    post = post,
                    artistPagesEnabled = artistPagesEnabled,
                    onNavigateToArtist = onNavigateToArtist,
                    scope = scope,
                    resolveArtistId = actions::resolveArtistIdForTrack,
                    onArtistNotFound = { ToastManager.show(artistNotFoundMsg) },
                    onResolvingChange = { isResolvingArtist = it },
                ),
                onGoToAlbum = resolveMenuGoToAlbumTap(
                    context = context,
                    post = post,
                    artistPagesEnabled = artistPagesEnabled,
                    onNavigateToAlbum = onNavigateToAlbum,
                    onNavigateToSong = { onNavigateToSong(post.track) },
                    prereleaseAlbumPagesEnabled = actions.remoteConfig.prereleaseAlbumPagesEnabled,
                    scope = scope,
                    resolveDestinations = actions::resolveTrackDestinationsForTrack,
                    onAlbumNotFound = { ToastManager.show(albumNotFoundMsg) },
                    onResolvingChange = { isResolvingAlbum = it },
                ),
                onGoToDirector = onGoToDirectorTap(post, onNavigateToDirector) ?: {},
                onViewBackCover = {
                    val state = backCoverStateFor(post.id)
                    if (state.isFlipped) {
                        state.flipBack()
                    } else {
                        scope.launchBackCoverFlip(state, post.id) { actions.fetchBackCover(it) }
                    }
                },
                onSharePost = { pendingSharePost = post },
                onToggleSave = { actions.toggleSave(post.id) },
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

    DestinationResolvingHud(visible = isResolvingAlbum || isResolvingArtist)
}

/**
 * Top-center "Loading…" HUD shown while a destination id (artist / album) is
 * resolved on tap. Shared by the post "…" menu rows and the post card's tappable
 * artist subtitle so both give identical feedback during the brief resolve.
 */
@Composable
fun DestinationResolvingHud(visible: Boolean) {
    if (!visible) return
    Popup(alignment = Alignment.TopCenter) {
        Row(
            modifier = Modifier
                .padding(top = 72.dp)
                .clip(RoundedCornerShape(50))
                .background(Color.Black.copy(alpha = 0.8f))
                .padding(horizontal = CorusSpacing.md, vertical = CorusSpacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CircularProgressIndicator(
                color = Color.White,
                strokeWidth = 2.dp,
                modifier = Modifier.size(16.dp),
            )
            Spacer(modifier = Modifier.width(CorusSpacing.sm))
            Text(
                text = stringResource(R.string.song_detail_resolving),
                style = CorusFont.caption,
                color = Color.White,
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
    resolveSpotifyFromAppleTrack: suspend (CymbalTrack) -> String? = { null },
) {
    val track = post.track
    fun open(url: String?) {
        url?.takeIf { it.isNotBlank() }?.let {
            runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(it))) }
        }
    }
    when {
        track.source == TrackSource.SOUNDCLOUD -> open(track.soundcloudPermalinkUrl)
        // Audiomack is link-out only — open its page regardless of the viewer's
        // preferred service (mirrors the SoundCloud lock above).
        track.source == TrackSource.AUDIOMACK -> open(track.audiomackUrl)
        // TIDAL/Deezer exclusives are link-out only too (Audiomack treatment) —
        // open the track's own page regardless of the viewer's preferred service.
        track.source == TrackSource.TIDAL -> open(track.tidalURL)
        track.source == TrackSource.DEEZER -> open(track.deezerURL)
        track.source == TrackSource.APPLEMUSIC &&
            (musicService == MusicService.SPOTIFY || musicService == MusicService.APPLE_MUSIC) -> {
            val displayed = SongPlayRouting.displayedLinkOutService(
                track.source,
                musicService,
                knownNotOnSpotify = track.notOnSpotify,
            )
            if (displayed == MusicService.SPOTIFY) {
                analytics.logSpotifyLinkTapped(track.id)
                scope.launch { open(resolveSpotifyFromAppleTrack(track)) }
            } else {
                analytics.logMusicServiceLinkTapped(MusicService.APPLE_MUSIC.value, track.id)
                open(track.appleMusicURL)
            }
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

/**
 * Opens an external URL (an Audiomack artist/album page link-out from the "…"
 * menu). Swallows the ActivityNotFoundException so a missing browser can't crash
 * the tap. Mirrors the link-out pattern in [openTrackInPreferredService].
 */
internal fun openExternalUrl(context: Context, url: String) {
    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
}

/** Menu "Go to Artist" tap: internal nav when enabled, Audiomack link-out fallback. */
internal fun resolveMenuGoToArtistTap(
    context: Context,
    post: CymbalPost,
    artistPagesEnabled: Boolean,
    onNavigateToArtist: ((fm.corus.android.ui.navigation.ArtistPageRoute) -> Unit)?,
    scope: CoroutineScope,
    resolveArtistId: suspend (CymbalTrack) -> String?,
    onArtistNotFound: () -> Unit,
    onResolvingChange: (Boolean) -> Unit,
): () -> Unit {
    return onGoToArtistTap(
        context = context,
        post = post,
        onNavigateToArtist = onNavigateToArtist,
        scope = scope,
        resolveArtistId = resolveArtistId,
        onArtistNotFound = onArtistNotFound,
        onResolvingChange = onResolvingChange,
    ) ?: if (!artistPagesEnabled) {
        post.track.audiomackArtistLinkOutUrl?.let { url -> { openExternalUrl(context, url) } }
    } else {
        null
    } ?: {}
}

/** Menu "Go to Album" tap: internal nav when enabled, Audiomack link-out fallback. */
internal fun resolveMenuGoToAlbumTap(
    context: Context,
    post: CymbalPost,
    artistPagesEnabled: Boolean,
    onNavigateToAlbum: ((fm.corus.android.ui.navigation.AlbumPageRoute) -> Unit)?,
    onNavigateToSong: () -> Unit,
    prereleaseAlbumPagesEnabled: Boolean,
    scope: CoroutineScope,
    resolveDestinations: suspend (CymbalTrack) -> fm.corus.android.data.remote.CloudFunctionsDataSource.TrackDestinations,
    onAlbumNotFound: () -> Unit,
    onResolvingChange: (Boolean) -> Unit,
): () -> Unit {
    return onGoToAlbumTap(
        context = context,
        post = post,
        onNavigateToAlbum = onNavigateToAlbum,
        onNavigateToSong = onNavigateToSong,
        prereleaseAlbumPagesEnabled = prereleaseAlbumPagesEnabled,
        scope = scope,
        resolveDestinations = resolveDestinations,
        onAlbumNotFound = onAlbumNotFound,
        onResolvingChange = onResolvingChange,
    ) ?: if (!artistPagesEnabled) {
        post.track.audiomackAlbumLinkOutUrl?.let { url -> { openExternalUrl(context, url) } }
    } else {
        null
    } ?: {}
}

/**
 * Builds the "Go to Artist" click for a track post: routes to the artist page,
 * reusing the exact route the post card's tappable artist subtitle uses
 * (first credited id, primaryNameHint for the name).
 *
 * Fast-path uses `track.artistIds[0]` when the post already carries one; when it
 * doesn't (an Apple-Music search post lands with `artistIds:[]`), it resolves the
 * artist id on tap via [resolveArtistId] behind a brief HUD, then navigates or
 * reports a miss. Mirrors [onGoToAlbumTap]. Null when [onNavigateToArtist] is
 * null (flag off), the post is a movie, or the source has no Corus artist page
 * (Spotify / Apple Music / Audiomack with ISRC resolve; Audiomack link-out fallback).
 */
internal fun onGoToArtistTap(
    context: Context,
    post: CymbalPost,
    onNavigateToArtist: ((fm.corus.android.ui.navigation.ArtistPageRoute) -> Unit)?,
    scope: CoroutineScope,
    resolveArtistId: suspend (CymbalTrack) -> String?,
    onArtistNotFound: () -> Unit,
    onResolvingChange: (Boolean) -> Unit = {},
): (() -> Unit)? {
    val navigate = onNavigateToArtist ?: return null
    if (post.isMovie) return null
    if (post.track.source != TrackSource.SPOTIFY
        && post.track.source != TrackSource.APPLEMUSIC
        && post.track.source != TrackSource.AUDIOMACK) return null
    fun go(artistId: String) {
        val name = fm.corus.android.data.model.primaryNameHint(
            post.track.artistName,
            post.track.artistIds.size,
        )
        navigate(
            fm.corus.android.ui.navigation.ArtistPageRoute(
                artistId = artistId,
                name = name.ifEmpty { null },
            )
        )
    }
    return {
        val known = post.track.artistIds.firstOrNull { it.isNotBlank() }
        if (known != null) {
            go(known)
        } else {
            scope.launch {
                onResolvingChange(true)
                try {
                    val resolved = resolveArtistId(post.track)?.takeIf { it.isNotBlank() }
                    if (resolved != null) go(resolved)
                    else if (post.track.source == TrackSource.AUDIOMACK) {
                        post.track.audiomackArtistLinkOutUrl?.let { openExternalUrl(context, it) }
                            ?: onArtistNotFound()
                    } else onArtistNotFound()
                } finally {
                    onResolvingChange(false)
                }
            }
        }
    }
}

/**
 * Builds the "Go to Album" click for a track post: routes to the album page via
 * `track.albumId` (Spotify album id or `am:<appleAlbumId>`), matching the
 * AlbumPageRoute the song page and search rows build. Null when
 * [onNavigateToAlbum] is null (flag off) or the post carries no album id.
 */
internal fun onGoToAlbumTap(
    context: Context,
    post: CymbalPost,
    onNavigateToAlbum: ((fm.corus.android.ui.navigation.AlbumPageRoute) -> Unit)?,
    onNavigateToSong: (() -> Unit)?,
    prereleaseAlbumPagesEnabled: Boolean,
    scope: CoroutineScope,
    resolveDestinations: suspend (CymbalTrack) -> fm.corus.android.data.remote.CloudFunctionsDataSource.TrackDestinations,
    onAlbumNotFound: () -> Unit,
    onResolvingChange: (Boolean) -> Unit = {},
): (() -> Unit)? {
    val navigateAlbum = onNavigateToAlbum ?: return null
    if (post.isMovie) return null
    if (post.track.source != TrackSource.SPOTIFY
        && post.track.source != TrackSource.APPLEMUSIC
        && post.track.source != TrackSource.AUDIOMACK) return null
    fun go(albumId: String) = navigateAlbum(
        fm.corus.android.ui.navigation.AlbumPageRoute(
            albumId = albumId,
            title = post.track.albumName.ifBlank { null },
            artist = post.track.artistName.ifBlank { null },
            coverUrl = post.track.albumArtURL,
            year = post.track.releaseDate?.take(4)?.toIntOrNull(),
        )
    )
    fun routeToSongIfNeeded(dest: fm.corus.android.data.remote.CloudFunctionsDataSource.TrackDestinations): Boolean {
        if (fm.corus.android.domain.shouldRouteGoToAlbumToSong(
                post.track, prereleaseAlbumPagesEnabled, dest.goToAlbumAsSong,
            )
        ) {
            onNavigateToSong?.invoke()
            return true
        }
        return false
    }
    return albumTap@{
        if (fm.corus.android.domain.shouldRouteGoToAlbumToSong(
                post.track, prereleaseAlbumPagesEnabled,
            )
        ) {
            onNavigateToSong?.invoke()
            return@albumTap
        }
        val known = post.track.albumId?.takeIf { it.isNotBlank() }
        if (known != null) {
            go(known)
        } else {
            scope.launch {
                onResolvingChange(true)
                try {
                    val dest = resolveDestinations(post.track)
                    if (routeToSongIfNeeded(dest)) return@launch
                    val resolved = dest.albumId?.takeIf { it.isNotBlank() }
                    if (resolved != null) go(resolved)
                    else if (post.track.source == TrackSource.AUDIOMACK) {
                        post.track.audiomackAlbumLinkOutUrl?.let { openExternalUrl(context, it) }
                            ?: onAlbumNotFound()
                    } else onAlbumNotFound()
                } finally {
                    onResolvingChange(false)
                }
            }
        }
    }
}

/**
 * Builds the "Go to Director" tap for a movie post, mirroring the tappable
 * director subtitle. Null when [onNavigateToDirector] is null (flag off), the
 * post isn't a movie, or it carries no director id.
 */
internal fun onGoToDirectorTap(
    post: CymbalPost,
    onNavigateToDirector: ((fm.corus.android.ui.navigation.DirectorPageRoute) -> Unit)?,
): (() -> Unit)? {
    val navigate = onNavigateToDirector ?: return null
    if (!post.isMovie) return null
    val directorId = post.directorIds.firstOrNull { it.isNotBlank() } ?: return null
    return {
        navigate(
            fm.corus.android.ui.navigation.DirectorPageRoute(
                directorId = directorId,
                name = post.directorName?.ifBlank { null },
            )
        )
    }
}
