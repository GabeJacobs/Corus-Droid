package fm.corus.android.ui.screens.destination

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil3.compose.AsyncImage
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.systemBars
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import fm.corus.android.R
import fm.corus.android.data.model.TrackCorusStats
import fm.corus.android.data.model.primaryNameHint
import fm.corus.android.domain.CatalogPlaybackOrigin
import fm.corus.android.domain.toQueuedTrack
import fm.corus.android.ui.components.CorusHeaderIconButton
import fm.corus.android.ui.components.contentHazeSource
import fm.corus.android.ui.components.ImmersiveBarHeight
import fm.corus.android.ui.components.ImmersiveCollapsingBar
import fm.corus.android.ui.components.ImmersiveCoverBackdrop
import fm.corus.android.ui.components.ImmersiveExtendUnderStatusBar
import fm.corus.android.ui.components.ImmersiveStatusBarIcons
import fm.corus.android.ui.components.currentStatusBarTopPx
import fm.corus.android.ui.components.extendIntoStatusBar
import fm.corus.android.ui.components.immersiveCollapseProgress
import fm.corus.android.ui.components.ShareAlbumSubject
import fm.corus.android.ui.components.ShareMediaSheet
import fm.corus.android.ui.components.ShareMediaSubject
import fm.corus.android.ui.components.SkeletonUserRow
import fm.corus.android.ui.components.ToastManager
import fm.corus.android.ui.theme.CorusSystemBars
import fm.corus.android.ui.navigation.ArtistPageRoute
import fm.corus.android.ui.navigation.SongDetailRoute
import fm.corus.android.ui.theme.CorusColors
import fm.corus.android.ui.theme.CorusFont
import fm.corus.android.ui.theme.CorusSpacing

/** Visible height of the album's blurred-cover backdrop; also the collapse
 *  distance basis via [ImmersiveBarHeight]. */
private val AlbumImmersiveHeroHeight = 340.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlbumPageScreen(
    albumId: String,
    titleHint: String? = null,
    artistHint: String? = null,
    coverUrlHint: String? = null,
    yearHint: Int? = null,
    /** Mini-player "return to origin": scroll the tracklist to this catalog
     *  track id once the album loads. */
    scrollToTrackId: String? = null,
    /** Mini-player tap while THIS page is already visible: scroll to this track
     *  in place (no re-push). Delivered via the entry's saved state; fires each
     *  tap. Cleared through [onInPlaceScrollConsumed] once handled. */
    inPlaceScrollTrackId: String? = null,
    onInPlaceScrollConsumed: () -> Unit = {},
    viewModel: AlbumPageViewModel = hiltViewModel(),
    onBack: () -> Unit = {},
    onNavigateToUser: (String) -> Unit = {},
    onNavigateToPost: (String) -> Unit = {},
    onNavigateToSong: (SongDetailRoute) -> Unit = {},
    onNavigateToArtist: (ArtistPageRoute) -> Unit = {},
) {
    val catalog by viewModel.catalog.collectAsState()
    val isCatalogLoading by viewModel.isCatalogLoading.collectAsState()
    val catalogError by viewModel.catalogError.collectAsState()
    val posts by viewModel.posts.collectAsState()
    val uniquePosterCount by viewModel.uniquePosterCount.collectAsState()
    val trackShareCounts by viewModel.trackShareCounts.collectAsState()
    val isPostsLoading by viewModel.isPostsLoading.collectAsState()
    val postsError by viewModel.postsError.collectAsState()
    val recentShareContacts by viewModel.recentShareContacts.collectAsState()
    val shareSearchResults by viewModel.shareSearchResults.collectAsState()
    val isShareSearching by viewModel.isShareSearching.collectAsState()
    val isLoadingShareContacts by viewModel.isLoadingShareContacts.collectAsState()
    val context = LocalContext.current
    var showShareSheet by remember { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }

    val title = catalog?.title?.takeIf { it.isNotBlank() } ?: titleHint
    val artistName = catalog?.artistName?.takeIf { it.isNotBlank() } ?: artistHint
    val cover = catalog?.coverUrl ?: coverUrlHint
    val artistId = catalog?.artistIds?.firstOrNull()
    val showPreReleaseAlbumUI =
        viewModel.prereleaseAlbumPagesEnabled && (catalog?.isPreRelease == true)

    // Marks tracks played from this page so the mini-player returns here and
    // scrolls to the song.
    val albumOrigin = CatalogPlaybackOrigin.Album(
        id = albumId,
        title = title,
        artist = artistName,
        coverUrl = cover,
    )

    // Full tracklist as a queue so tapping the cover (or a track row) plays the
    // album and rolls on through the rest. `toQueuedTrack` is internal to this
    // package.
    val tracks = catalog?.tracks ?: emptyList()
    val albumQueue = remember(tracks, albumOrigin) { tracks.map { it.toQueuedTrack(albumOrigin) } }
    val canPlayFullSongs = viewModel.catalogListeningEntitled() && viewModel.preferFullPlaybackOnCatalog()
    val showAlbumPlayPill = !catalogError && (catalog == null || tracks.isNotEmpty())
    val nowPlayingState by viewModel.nowPlayingManager.state.collectAsState()
    val currentTrackInAlbum = nowPlayingState.trackId?.let { id -> tracks.any { it.id == id } } == true
    val isPlayingThisAlbum = currentTrackInAlbum && nowPlayingState.isPlaying
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    // Estimated tracklist-row height (px), for centering the return-to-origin
    // scroll in the viewport instead of pinning the row to the top.
    val catalogRowHeightPx = with(LocalDensity.current) { 64.dp.toPx() }.toInt()

    LaunchedEffect(albumId) {
        viewModel.analyticsService.logAlbumPageViewed(albumId)
        // Pass the header hints: they let the server fall back to Apple when
        // Spotify's catalog quota is exhausted and no post carries this album id.
        viewModel.load(albumId, titleHint, artistHint)
    }

    // Mini-player "return to origin": scroll the tracklist to the playing song
    // once the album loads. The header is a single item() before the tracklist,
    // so a track sits at list index (1 + its position). Runs once.
    var didScrollToOrigin by remember { mutableStateOf(false) }
    LaunchedEffect(scrollToTrackId, catalog) {
        if (didScrollToOrigin) return@LaunchedEffect
        val target = scrollToTrackId ?: return@LaunchedEffect
        val loaded = catalog ?: return@LaunchedEffect
        val pos = loaded.tracks.indexOfFirst { it.id == target }
        if (pos < 0) return@LaunchedEffect
        didScrollToOrigin = true
        val targetIndex = 1 + pos // 1 header item precedes the tracklist
        snapshotFlow { listState.layoutInfo.totalItemsCount }.first { it > targetIndex }
        listState.animateScrollToItemCentered(targetIndex, catalogRowHeightPx)
    }

    // Mini-player tap while this album page is already visible: scroll to the
    // requested track in place (no re-push). Fires per tap; clears the signal.
    LaunchedEffect(inPlaceScrollTrackId) {
        val target = inPlaceScrollTrackId ?: return@LaunchedEffect
        val loaded = catalog
        val pos = loaded?.tracks?.indexOfFirst { it.id == target } ?: -1
        if (loaded == null || pos < 0) { onInPlaceScrollConsumed(); return@LaunchedEffect }
        val targetIndex = 1 + pos // 1 header item precedes the tracklist
        snapshotFlow { listState.layoutInfo.totalItemsCount }.first { it > targetIndex }
        listState.animateScrollToItemCentered(targetIndex, catalogRowHeightPx)
        onInPlaceScrollConsumed()
    }

    // Immersive header (prototype): blurred-cover hero + shared frosted collapsing
    // bar + status-bar blend (ui/components/ImmersiveHeader.kt).
    val immersive = viewModel.immersiveHeaderEnabled && (cover != null || isCatalogLoading)
    val hazeState = remember { HazeState() }
    val statusBarTopPx = currentStatusBarTopPx()
    val extendUnderStatusBar = immersive && ImmersiveExtendUnderStatusBar && statusBarTopPx > 0
    val statusBarPadding = if (extendUnderStatusBar) {
        with(LocalDensity.current) { statusBarTopPx.toDp() }
    } else {
        0.dp
    }
    val heroCollapseDistancePx = with(LocalDensity.current) {
        (AlbumImmersiveHeroHeight - ImmersiveBarHeight).toPx()
    }
    val collapseProgress by remember {
        derivedStateOf {
            immersiveCollapseProgress(
                listState.firstVisibleItemIndex,
                listState.firstVisibleItemScrollOffset,
                heroCollapseDistancePx,
            )
        }
    }
    if (extendUnderStatusBar) {
        ImmersiveStatusBarIcons(collapseProgress)
    }

    Scaffold(
        modifier = if (extendUnderStatusBar) {
            Modifier.extendIntoStatusBar(statusBarTopPx)
        } else {
            Modifier
        },
        topBar = {
            // Immersive draws the shared floating bar over the blurred cover below.
            if (!immersive) {
            TopAppBar(
                title = {},
                navigationIcon = {
                    CorusHeaderIconButton(
                        onClick = onBack,
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.feed_cd_back),
                    )
                },
                actions = {
                    // "Share" is send-side gated by entityShareEnabled; "Go to
                    // Artist" belongs to the artist-pages feature (this page is
                    // only reachable when that's on) and shows only when the
                    // catalog carries a Spotify artist id — am: albums don't,
                    // matching the tappable artist name.
                    if (viewModel.entityShareEnabled || artistId != null) {
                        Box {
                            CorusHeaderIconButton(
                                onClick = { showMenu = true },
                                imageVector = Icons.Filled.MoreVert,
                                contentDescription = stringResource(R.string.feed_cd_more_options),
                            )
                            DropdownMenu(
                                expanded = showMenu,
                                onDismissRequest = { showMenu = false },
                                containerColor = CorusColors.CardBackground,
                            ) {
                                if (viewModel.entityShareEnabled) {
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.post_menu_share), style = CorusFont.body) },
                                        onClick = {
                                            showMenu = false
                                            showShareSheet = true
                                        },
                                    )
                                }
                                if (artistId != null) {
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.post_menu_go_to_artist), style = CorusFont.body) },
                                        onClick = {
                                            showMenu = false
                                            onNavigateToArtist(
                                                ArtistPageRoute(
                                                    artistId = artistId,
                                                    name = artistName?.let { primaryNameHint(it, catalog?.artistIds?.size ?: 0) },
                                                )
                                            )
                                        },
                                    )
                                }
                            }
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = CorusColors.Background),
                windowInsets = WindowInsets(0, 0, 0, 0),
            )
            }
        },
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
        if (immersive) {
            ImmersiveCoverBackdrop(
                artUrl = cover,
                height = AlbumImmersiveHeroHeight + statusBarPadding,
                listState = listState,
            )
        }
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .then(if (immersive) Modifier.hazeSource(hazeState) else Modifier)
                .contentHazeSource(),
            contentPadding = PaddingValues(bottom = CorusSpacing.xxxl + CorusSpacing.xxxl),
        ) {
            // ── Header: cover (tap to play the album), title, tappable artist,
            // meta line, Play/Pause pill (entitled users) ──
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = CorusSpacing.lg),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Spacer(
                        modifier = Modifier.height(
                            if (immersive) statusBarPadding + ImmersiveBarHeight + CorusSpacing.md
                            else CorusSpacing.sm
                        )
                    )
                    if (cover != null) {
                        AsyncImage(
                            model = cover,
                            contentDescription = title,
                            modifier = Modifier
                                .size(200.dp)
                                .shadow(4.dp, RoundedCornerShape(8.dp))
                                .clip(RoundedCornerShape(8.dp))
                                .clickable(enabled = albumQueue.isNotEmpty()) {
                                    viewModel.playAlbum(
                                        albumId = albumId,
                                        tracks = tracks,
                                        albumOrigin = albumOrigin,
                                        albumQueue = albumQueue,
                                    )
                                },
                            contentScale = ContentScale.Crop,
                        )
                        Spacer(modifier = Modifier.height(CorusSpacing.md))
                    }
                    if (title != null || catalogError) {
                        Text(
                            text = title ?: stringResource(R.string.destination_album_label),
                            style = CorusFont.songTitleLarge,
                            color = CorusColors.Text,
                            textAlign = TextAlign.Center,
                        )
                    }
                    if (!artistName.isNullOrBlank()) {
                        Spacer(modifier = Modifier.height(CorusSpacing.xxs))
                        // Tappable ONLY when the catalog carries a Spotify
                        // artist id (`am:` albums return artistIds: [] and the
                        // name renders as plain text). Exact same style either
                        // way — no accent, no underline.
                        val idCount = catalog?.artistIds?.size ?: 0
                        Text(
                            text = artistName,
                            style = CorusFont.bodyMedium,
                            color = CorusColors.Text,
                            textAlign = TextAlign.Center,
                            modifier = if (artistId != null) {
                                Modifier.clickable {
                                    onNavigateToArtist(
                                        ArtistPageRoute(
                                            artistId = artistId,
                                            name = primaryNameHint(artistName, idCount),
                                        )
                                    )
                                }
                            } else Modifier,
                        )
                    }
                    // Meta: "Album · {year} · {N} songs". "Album" (plus the
                    // year, when the source row hinted it) renders from first
                    // paint; the song count fills in on the same line when the
                    // catalog lands — no layout shift.
                    val trackCount = catalog?.tracks?.size ?: 0
                    val metaParts = buildList {
                        add(stringResource(R.string.destination_album_label))
                        (catalog?.year ?: yearHint)?.let { add(it.toString()) }
                        if (trackCount > 0) {
                            add(pluralStringResource(R.plurals.destination_song_count, trackCount, trackCount))
                        }
                    }
                    Spacer(modifier = Modifier.height(CorusSpacing.xxs))
                    Text(
                        text = metaParts.joinToString(" · "),
                        style = CorusFont.caption,
                        color = CorusColors.Secondary,
                    )
                    if (showPreReleaseAlbumUI) {
                        Spacer(modifier = Modifier.height(CorusSpacing.xxs))
                        Text(
                            text = stringResource(R.string.destination_prerelease_album_hint),
                            style = CorusFont.caption,
                            color = CorusColors.Secondary,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    if (showAlbumPlayPill) {
                        Spacer(modifier = Modifier.height(CorusSpacing.md))
                        AlbumPlayPausePill(
                            isPlaying = isPlayingThisAlbum,
                            showShimmer = catalog == null && isCatalogLoading,
                            onClick = {
                                viewModel.playAlbum(
                                    albumId = albumId,
                                    tracks = tracks,
                                    albumOrigin = albumOrigin,
                                    albumQueue = albumQueue,
                                )
                            },
                        )
                    }
                    Spacer(modifier = Modifier.height(CorusSpacing.md))
                }
            }

            // ── Tracklist ──
            if (isCatalogLoading && catalog == null) {
                items(6) { SkeletonNumberedTrackRow() }
            } else if (catalogError && catalog == null) {
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = CorusSpacing.xl),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(
                            text = stringResource(R.string.destination_album_load_error),
                            style = CorusFont.body,
                            color = CorusColors.Secondary,
                        )
                        TextButton(onClick = { viewModel.loadCatalog(albumId, titleHint, artistHint) }) {
                            Text(
                                text = stringResource(R.string.song_detail_try_again),
                                style = CorusFont.buttonSmall,
                                color = CorusColors.Accent,
                            )
                        }
                    }
                }
            } else {
                if (tracks.isEmpty()) {
                    item {
                        Text(
                            text = stringResource(R.string.destination_no_tracks),
                            style = CorusFont.body,
                            color = CorusColors.Secondary,
                            textAlign = TextAlign.Center,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = CorusSpacing.xl),
                        )
                    }
                } else {
                    items(tracks.size) { index ->
                        val track = tracks[index]
                        CatalogTrackRow(
                            track = track,
                            nowPlaying = viewModel.nowPlayingManager,
                            number = index + 1,
                            queue = albumQueue,
                            origin = albumOrigin,
                            preferFullSongOnPlay = viewModel.preferFullPlaybackOnCatalog(),
                            playbackEnabled = !showPreReleaseAlbumUI || fm.corus.android.domain.trackIsCatalogPlayable(track),
                            // Trailing slot shows how many Corus users shared this
                            // track (from getAlbumPosts) in place of its duration,
                            // blank when none. No facepile on album rows.
                            corusStats = trackShareCounts[track.id]?.let {
                                TrackCorusStats(count = it, posters = emptyList())
                            },
                            onRowTap = {
                                viewModel.analyticsService.logPostFromAlbum(albumId, track.id)
                                onNavigateToSong(track.toSongDetailRoute())
                            },
                            onPreviewStarted = {
                                viewModel.analyticsService.logAlbumTrackPreviewed(albumId, track.id)
                            },
                        )
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(CorusSpacing.md))
                HorizontalDivider(color = CorusColors.Divider, thickness = 0.5.dp)
            }

            // ── Shared from this album ──
            item {
                DestinationSectionHeader(
                    title = if (uniquePosterCount > 0) {
                        pluralStringResource(
                            R.plurals.destination_shared_by_people,
                            uniquePosterCount,
                            formatDestinationCount(uniquePosterCount),
                        )
                    } else {
                        stringResource(R.string.destination_shared_from_album)
                    },
                )
            }
            if (isPostsLoading) {
                items(4) { index ->
                    SkeletonUserRow()
                    if (index < 3) {
                        HorizontalDivider(
                            modifier = Modifier.padding(start = 72.dp),
                            color = CorusColors.Divider,
                            thickness = 0.5.dp,
                        )
                    }
                }
            } else if (postsError) {
                item {
                    Text(
                        text = stringResource(R.string.destination_album_posts_load_error),
                        style = CorusFont.body,
                        color = CorusColors.Secondary,
                        modifier = Modifier.padding(horizontal = CorusSpacing.lg, vertical = CorusSpacing.sm),
                    )
                }
            } else if (posts.isEmpty()) {
                item {
                    Text(
                        text = stringResource(R.string.destination_no_posts_album),
                        style = CorusFont.body,
                        color = CorusColors.Secondary,
                        modifier = Modifier.padding(horizontal = CorusSpacing.lg, vertical = CorusSpacing.sm),
                    )
                }
            } else {
                items(posts.size) { index ->
                    val post = posts[index]
                    DestinationPostRow(
                        post = post,
                        onUserTap = { onNavigateToUser(post.user.id) },
                        onPostTap = { onNavigateToPost(post.id) },
                    )
                }
            }

            // ── Attribution footer (Spotify link hidden for `am:` albums) ──
            item {
                DestinationAttributionFooter(
                    attribution = stringResource(R.string.destination_music_attribution),
                    onOpenSpotify = if (albumId.startsWith("am:")) null else {
                        {
                            val url = "https://open.spotify.com/album/${Uri.encode(albumId)}"
                            runCatching {
                                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                            }
                        }
                    },
                )
            }
        }
        if (immersive) {
            ImmersiveCollapsingBar(
                hazeState = hazeState,
                progress = collapseProgress,
                title = title,
                onBack = onBack,
                topInset = statusBarPadding,
                actions = { tint ->
                    if (viewModel.entityShareEnabled || artistId != null) {
                        Box {
                            CorusHeaderIconButton(
                                onClick = { showMenu = true },
                                imageVector = Icons.Filled.MoreVert,
                                contentDescription = stringResource(R.string.feed_cd_more_options),
                                tint = tint,
                            )
                            DropdownMenu(
                                expanded = showMenu,
                                onDismissRequest = { showMenu = false },
                                containerColor = CorusColors.CardBackground,
                            ) {
                                if (viewModel.entityShareEnabled) {
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.post_menu_share), style = CorusFont.body) },
                                        onClick = {
                                            showMenu = false
                                            showShareSheet = true
                                        },
                                    )
                                }
                                if (artistId != null) {
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.post_menu_go_to_artist), style = CorusFont.body) },
                                        onClick = {
                                            showMenu = false
                                            onNavigateToArtist(
                                                ArtistPageRoute(
                                                    artistId = artistId,
                                                    name = artistName?.let { primaryNameHint(it, catalog?.artistIds?.size ?: 0) },
                                                )
                                            )
                                        },
                                    )
                                }
                            }
                        }
                    }
                },
            )
        }
        }
    }

    if (showShareSheet) {
        val shareSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        val sentMsg = stringResource(R.string.album_detail_toast_album_sent)
        LaunchedEffect(Unit) { viewModel.loadRecentShareContacts() }
        ModalBottomSheet(
            onDismissRequest = { showShareSheet = false },
            sheetState = shareSheetState,
            containerColor = CorusColors.Background,
            dragHandle = null,
            shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
            contentWindowInsets = { WindowInsets.systemBars.only(WindowInsetsSides.Bottom) },
        ) {
            CorusSystemBars()
            BackHandler { showShareSheet = false }
            ShareMediaSheet(
                subject = ShareMediaSubject.Album(
                    ShareAlbumSubject(
                        id = albumId,
                        title = title ?: "Album",
                        artistName = artistName ?: "",
                        coverUrl = cover,
                        year = (catalog?.year ?: yearHint)?.toString(),
                    )
                ),
                recentContacts = recentShareContacts,
                searchResults = shareSearchResults,
                isSearching = isShareSearching,
                isLoadingContacts = isLoadingShareContacts,
                onSearchQueryChange = { query -> viewModel.searchShareUsers(query) },
                onSendToUser = { userId, message ->
                    viewModel.sendAlbumToUser(
                        userId, albumId, title ?: "Album", artistName ?: "", cover,
                        (catalog?.year ?: yearHint)?.toString(), message,
                    )
                    ToastManager.show(sentMsg)
                    showShareSheet = false
                },
                onDismiss = { showShareSheet = false },
                onAnalyticsLog = { method -> viewModel.analyticsService.logAlbumShared(albumId, method) },
            )
        }
    }
}
