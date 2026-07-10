package fm.corus.android.ui.screens.destination

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil3.compose.AsyncImage
import kotlinx.coroutines.flow.first
import fm.corus.android.R
import fm.corus.android.data.model.AlbumSummary
import fm.corus.android.data.model.CymbalTrack
import fm.corus.android.data.model.MusicVideo
import fm.corus.android.domain.CatalogPlaybackOrigin
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.systemBars
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import fm.corus.android.ui.components.CorusHeaderIconButton
import fm.corus.android.ui.components.ShareArtistSubject
import fm.corus.android.ui.components.ShareMediaSheet
import fm.corus.android.ui.components.ShareMediaSubject
import fm.corus.android.ui.components.ToastManager
import fm.corus.android.ui.theme.CorusSystemBars
import fm.corus.android.ui.components.SkeletonAlbumGridCell
import fm.corus.android.ui.components.SkeletonSongRow
import fm.corus.android.ui.components.SkeletonUserRow
import fm.corus.android.ui.navigation.AlbumPageRoute
import fm.corus.android.ui.navigation.SongDetailRoute
import fm.corus.android.ui.theme.CorusColors
import fm.corus.android.ui.theme.CorusFont
import fm.corus.android.ui.theme.CorusSpacing

/** Localized "{Album|Single|Compilation} · {year}" caption for a rail cover. */
@Composable
internal fun albumKindCaption(album: AlbumSummary): String {
    val kind = stringResource(
        when (album.albumType) {
            "single" -> R.string.destination_single_label
            "compilation" -> R.string.destination_compilation_label
            else -> R.string.destination_album_label
        }
    )
    return listOfNotNull(kind, album.year?.toString()).joinToString(" · ")
}

/** Flattened LazyColumn index of the `pos`-th Popular row, mirroring the leading
 *  item() blocks the screen emits before the tracklist: hero, optional "on
 *  Corus" badge, optional "Shared by" row, then the "Popular" section header.
 *  ("Your posts" now renders BELOW Popular, so it isn't counted.) Both the
 *  origin scroll and the in-place scroll use this — keep it in sync with the
 *  LazyColumn layout below. */
private fun artistPopularItemIndex(
    hasCorusUser: Boolean,
    uniquePosterCount: Int,
    pos: Int,
): Int {
    var offset = 1 // hero item
    if (hasCorusUser) offset += 1
    if (uniquePosterCount > 0) offset += 1 // "Shared by" row
    offset += 1 // "Popular" section header
    return offset + pos
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArtistPageScreen(
    artistId: String,
    nameHint: String? = null,
    imageUrlHint: String? = null,
    /** Mini-player "return to origin": scroll the Popular list to this catalog
     *  track id once loaded, expanding the list if it sits below the fold. */
    scrollToTrackId: String? = null,
    /** Mini-player tap while THIS page is already visible: scroll to this track
     *  in place (no re-push). Delivered via the entry's saved state; fires each
     *  tap. Cleared through [onInPlaceScrollConsumed] once handled. */
    inPlaceScrollTrackId: String? = null,
    onInPlaceScrollConsumed: () -> Unit = {},
    viewModel: ArtistPageViewModel = hiltViewModel(),
    onBack: () -> Unit = {},
    onNavigateToUser: (String) -> Unit = {},
    onNavigateToPost: (String) -> Unit = {},
    onNavigateToSong: (SongDetailRoute) -> Unit = {},
    onNavigateToAlbum: (AlbumPageRoute) -> Unit = {},
    onSeeAllPosts: () -> Unit = {},
    onSeeAllDiscography: () -> Unit = {},
    onSeeAllVideos: () -> Unit = {},
) {
    val detail by viewModel.detail.collectAsState()
    val isCatalogLoading by viewModel.isCatalogLoading.collectAsState()
    val catalogError by viewModel.catalogError.collectAsState()
    val posts by viewModel.posts.collectAsState()
    val viewerPosts by viewModel.viewerPosts.collectAsState()
    val posters by viewModel.posters.collectAsState()
    val uniquePosterCount by viewModel.uniquePosterCount.collectAsState()
    val isPostsLoading by viewModel.isPostsLoading.collectAsState()
    val postsError by viewModel.postsError.collectAsState()
    val recentShareContacts by viewModel.recentShareContacts.collectAsState()
    val shareSearchResults by viewModel.shareSearchResults.collectAsState()
    val isShareSearching by viewModel.isShareSearching.collectAsState()
    val isLoadingShareContacts by viewModel.isLoadingShareContacts.collectAsState()
    val context = LocalContext.current
    var showShareSheet by remember { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }

    // Popular shows 6 by default (keeps the social sections high); Show more
    // reveals the full top-10 the payload already carries.
    var showAllPopular by remember { mutableStateOf(false) }
    // The music-video card the user tapped — its full video plays inline.
    var activeVideo by remember { mutableStateOf<MusicVideo?>(null) }

    val artistName = detail?.name?.takeIf { it.isNotBlank() } ?: nameHint
    val heroImage = detail?.imageUrl ?: imageUrlHint
    val matchedVideos = detail?.musicVideos?.filter { it.youtubeId != null } ?: emptyList()

    val listState = rememberLazyListState()
    // Marks tracks played from this page so the mini-player returns here.
    val artistOrigin = CatalogPlaybackOrigin.Artist(
        id = artistId,
        name = artistName,
        imageUrl = heroImage,
    )

    LaunchedEffect(artistId) {
        viewModel.analyticsService.logArtistPageViewed(artistId)
        viewModel.loadCatalog(artistId, nameHint)
        viewModel.loadPosts(artistId, nameHint)
    }

    // Mini-player "return to origin": once the catalog AND social sections have
    // settled (so the header item count is stable), scroll the Popular list to
    // the song the mini-player handed us — expanding it first if the track sits
    // below the collapsed 6-row fold. Runs once. The target index mirrors the
    // LazyColumn's leading item() blocks below; keep the two in sync.
    var didScrollToOrigin by remember { mutableStateOf(false) }
    LaunchedEffect(scrollToTrackId, detail, isPostsLoading) {
        if (didScrollToOrigin) return@LaunchedEffect
        val target = scrollToTrackId ?: return@LaunchedEffect
        val loaded = detail ?: return@LaunchedEffect
        // Wait for the social fetch so the "Shared by" row has taken its final
        // count — otherwise the offset shifts under us. ("Your posts" now sits
        // BELOW Popular, so it no longer affects this offset.)
        if (isPostsLoading) return@LaunchedEffect
        val pos = loaded.topTracks.indexOfFirst { it.id == target }
        if (pos < 0) return@LaunchedEffect
        didScrollToOrigin = true
        if (pos >= 6) showAllPopular = true
        val targetIndex = artistPopularItemIndex(loaded.corusUser != null, uniquePosterCount, pos)
        // Wait until the (possibly just-expanded) list actually contains the row.
        snapshotFlow { listState.layoutInfo.totalItemsCount }.first { it > targetIndex }
        listState.animateScrollToItem(targetIndex)
    }

    // Mini-player tap while this artist page is already visible: scroll to the
    // requested track in place (no re-push). The page is already settled, so no
    // isPostsLoading gate — just expand if the track is below the fold, then
    // center it. Fires per tap; clears the saved-state signal when handled.
    LaunchedEffect(inPlaceScrollTrackId) {
        val target = inPlaceScrollTrackId ?: return@LaunchedEffect
        val loaded = detail
        val pos = loaded?.topTracks?.indexOfFirst { it.id == target } ?: -1
        if (loaded == null || pos < 0) { onInPlaceScrollConsumed(); return@LaunchedEffect }
        if (pos >= 6) showAllPopular = true
        val targetIndex = artistPopularItemIndex(loaded.corusUser != null, uniquePosterCount, pos)
        snapshotFlow { listState.layoutInfo.totalItemsCount }.first { it > targetIndex }
        listState.animateScrollToItem(targetIndex)
        onInPlaceScrollConsumed()
    }

    Scaffold(
        topBar = {
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
                    if (viewModel.entityShareEnabled) {
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
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.post_menu_share), style = CorusFont.body) },
                                    onClick = {
                                        showMenu = false
                                        showShareSheet = true
                                    },
                                )
                            }
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = CorusColors.Background),
                windowInsets = WindowInsets(0, 0, 0, 0),
            )
        },
    ) { padding ->
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(bottom = CorusSpacing.xxxl + CorusSpacing.xxxl),
        ) {
            // ── Hero: full-bleed image with name on a bottom gradient scrim.
            // Imageless artists (mostly featured-credit entities) get a compact
            // name header instead of an empty card with a gradient over it. May
            // upgrade to the full hero if the catalog fetch surfaces an image
            // the navigation hint lacked. ──
            item {
                if (heroImage != null) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = CorusSpacing.lg)
                            .aspectRatio(5f / 3f)
                            .clip(RoundedCornerShape(CorusSpacing.cornerRadiusLarge))
                            .background(CorusColors.CardBackground),
                    ) {
                        AsyncImage(
                            model = heroImage,
                            contentDescription = artistName,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop,
                        )
                        Column(
                            modifier = Modifier
                                .align(Alignment.BottomStart)
                                .fillMaxWidth()
                                .background(
                                    Brush.verticalGradient(
                                        colors = listOf(
                                            Color.Transparent,
                                            Color.Black.copy(alpha = 0.35f),
                                            Color.Black.copy(alpha = 0.75f),
                                        ),
                                    ),
                                )
                                .padding(CorusSpacing.lg)
                                .padding(top = CorusSpacing.xxl),
                        ) {
                            Text(
                                text = artistName
                                    ?: stringResource(R.string.destination_artist_label),
                                style = CorusFont.songTitleLarge,
                                color = Color.White,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Spacer(modifier = Modifier.height(CorusSpacing.xxs))
                            Text(
                                text = stringResource(R.string.destination_artist_label),
                                style = CorusFont.captionMedium,
                                color = Color.White.copy(alpha = 0.8f),
                            )
                        }
                    }
                } else if (detail == null && isCatalogLoading) {
                    // Still loading and the nav hint carried no image (e.g.
                    // tapped from a post, which passes only a name — search
                    // passes the artist image, so it hits the branch above).
                    // Reserve the full 5:3 hero footprint so a loaded image
                    // fills it WITHOUT a layout shift; most artists have one.
                    // Only a genuinely imageless artist (detail resolved, no
                    // image) falls through to the compact header.
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = CorusSpacing.lg)
                            .aspectRatio(5f / 3f)
                            .clip(RoundedCornerShape(CorusSpacing.cornerRadiusLarge))
                            .background(CorusColors.CardBackground),
                    ) {
                        if (artistName != null) {
                            Column(
                                modifier = Modifier
                                    .align(Alignment.BottomStart)
                                    .fillMaxWidth()
                                    .background(
                                        Brush.verticalGradient(
                                            colors = listOf(
                                                Color.Transparent,
                                                Color.Black.copy(alpha = 0.35f),
                                                Color.Black.copy(alpha = 0.75f),
                                            ),
                                        ),
                                    )
                                    .padding(CorusSpacing.lg)
                                    .padding(top = CorusSpacing.xxl),
                            ) {
                                Text(
                                    text = artistName,
                                    style = CorusFont.songTitleLarge,
                                    color = Color.White,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Spacer(modifier = Modifier.height(CorusSpacing.xxs))
                                Text(
                                    text = stringResource(R.string.destination_artist_label),
                                    style = CorusFont.captionMedium,
                                    color = Color.White.copy(alpha = 0.8f),
                                )
                            }
                        }
                    }
                } else if (artistName != null || catalogError) {
                    // Mirrors the search artist row's mic-in-circle placeholder.
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = CorusSpacing.lg),
                        horizontalArrangement = Arrangement.spacedBy(CorusSpacing.md),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            modifier = Modifier
                                .size(64.dp)
                                .clip(CircleShape)
                                .background(CorusColors.CardBackground),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Mic,
                                contentDescription = null,
                                tint = CorusColors.Tertiary,
                                modifier = Modifier.size(28.dp),
                            )
                        }
                        Column(
                            verticalArrangement = Arrangement.spacedBy(CorusSpacing.xxs),
                        ) {
                            Text(
                                text = artistName
                                    ?: stringResource(R.string.destination_artist_label),
                                style = CorusFont.songTitleLarge,
                                color = CorusColors.Text,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text = stringResource(R.string.destination_artist_label),
                                style = CorusFont.captionMedium,
                                color = CorusColors.Secondary,
                            )
                        }
                    }
                } else {
                    // Still loading with no name hint: hold the hero's space.
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = CorusSpacing.lg)
                            .aspectRatio(5f / 3f)
                            .clip(RoundedCornerShape(CorusSpacing.cornerRadiusLarge))
                            .background(CorusColors.CardBackground),
                    )
                }
            }

            // ── "{name} is on Corus" badge (only when a Corus user is linked) ──
            detail?.corusUser?.let { corusUser ->
                item {
                    Spacer(modifier = Modifier.height(CorusSpacing.sm))
                    ArtistOnCorusBadge(
                        user = corusUser,
                        onClick = { onNavigateToUser(corusUser.id) },
                    )
                }
            }

            // ── Shared by N people (hidden when 0) ──
            if (uniquePosterCount > 0) {
                item {
                    Spacer(modifier = Modifier.height(CorusSpacing.sm))
                    SharedByPeopleRow(
                        posters = posters,
                        count = uniquePosterCount,
                        onClick = onSeeAllPosts,
                    )
                }
            } else if (isPostsLoading && !postsError) {
                // Reserve the row's space while the social fetch is in flight
                // so content below doesn't shift when the facepile lands.
                item {
                    Spacer(modifier = Modifier.height(CorusSpacing.sm))
                    SkeletonSharedByPeopleRow()
                }
            }

            // ── Catalog (Popular + Discography) ──
            if (isCatalogLoading && detail == null) {
                item {
                    Column {
                        DestinationSectionHeader(stringResource(R.string.destination_popular))
                        repeat(4) { SkeletonSongRow() }
                        DestinationSectionHeader(stringResource(R.string.destination_discography))
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = CorusSpacing.lg),
                            horizontalArrangement = Arrangement.spacedBy(CorusSpacing.md),
                        ) {
                            items(4) {
                                SkeletonAlbumGridCell(
                                    modifier = Modifier
                                        .width(132.dp)
                                        .clip(RoundedCornerShape(CorusSpacing.cornerRadiusMedium)),
                                )
                            }
                        }
                    }
                }
            } else if (catalogError && detail == null) {
                item {
                    Text(
                        text = stringResource(R.string.destination_catalog_load_error),
                        style = CorusFont.body,
                        color = CorusColors.Secondary,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = CorusSpacing.lg, vertical = CorusSpacing.lg),
                    )
                }
            } else {
                val allTopTracks = detail?.topTracks ?: emptyList()
                val topTracks = allTopTracks.take(if (showAllPopular) 10 else 6)
                // The visible Popular rows as a queue, so tapping one rolls on
                // through the rest of the list. Built inline (the LazyListScope
                // block is not a @Composable context, so no remember here); it
                // only rebuilds when the screen recomposes, not per playback tick.
                val popularQueue = topTracks.map { it.toQueuedTrack(artistOrigin) }
                if (topTracks.isNotEmpty()) {
                    item {
                        DestinationSectionHeader(stringResource(R.string.destination_popular))
                    }
                    items(topTracks.size) { index ->
                        val track = topTracks[index]
                        CatalogTrackRow(
                            track = track,
                            nowPlaying = viewModel.nowPlayingManager,
                            queue = popularQueue,
                            origin = artistOrigin,
                            corusStats = detail?.corusStats?.get(track.id),
                            onRowTap = {
                                viewModel.analyticsService.logPostFromArtistPage(artistId, track.id)
                                // Seed this page's artist id onto Apple-sourced
                                // Popular tracks (empty artistIds) so the song
                                // page's "Go to Artist" always resolves rather
                                // than relying on a by-name lookup that can miss.
                                // Mirrors iOS/web seeding.
                                val seeded = if (track.artistIds.isEmpty()) {
                                    track.copy(artistIds = listOf(artistId))
                                } else {
                                    track
                                }
                                onNavigateToSong(seeded.toSongDetailRoute())
                            },
                            onPreviewStarted = {
                                viewModel.analyticsService.logArtistSongPreviewed(artistId, track.id)
                            },
                        )
                    }
                    if (allTopTracks.size > 6) {
                        item {
                            Text(
                                text = stringResource(
                                    if (showAllPopular) R.string.destination_show_less
                                    else R.string.destination_show_more
                                ),
                                style = CorusFont.captionMedium,
                                color = CorusColors.Secondary,
                                modifier = Modifier
                                    .clickable { showAllPopular = !showAllPopular }
                                    .padding(horizontal = CorusSpacing.lg, vertical = CorusSpacing.xs),
                            )
                        }
                    }
                }

                val albums = detail?.albums ?: emptyList()
                if (albums.isNotEmpty()) {
                    item {
                        DestinationSectionHeader(
                            title = stringResource(R.string.destination_discography),
                            onSeeAll = onSeeAllDiscography,
                        )
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = CorusSpacing.lg),
                            horizontalArrangement = Arrangement.spacedBy(CorusSpacing.md),
                        ) {
                            items(albums.take(12).size) { index ->
                                val album = albums[index]
                                DiscographyRailCell(
                                    album = album,
                                    onClick = {
                                        onNavigateToAlbum(
                                            AlbumPageRoute(
                                                albumId = album.id,
                                                title = album.title,
                                                artist = artistName,
                                                coverUrl = album.coverUrl,
                                                year = album.year,
                                            )
                                        )
                                    },
                                )
                            }
                        }
                    }
                }
            }

            // ── Your posts (only when the viewer has posted this artist) —
            //    below Popular + Discography to match web (Popular → Discography
            //    → Your posts → Recent). ──
            if (viewerPosts.isNotEmpty()) {
                item {
                    DestinationSectionHeader(stringResource(R.string.destination_your_posts))
                }
                items(viewerPosts.size) { index ->
                    val post = viewerPosts[index]
                    DestinationPostRow(
                        post = post,
                        onUserTap = { onNavigateToUser(post.user.id) },
                        onPostTap = { onNavigateToPost(post.id) },
                    )
                }
            }

            // ── Recent posts ──
            item {
                DestinationSectionHeader(
                    title = stringResource(R.string.destination_recent_posts),
                    // 6 inline + See all when there are more (matches web + iOS).
                    onSeeAll = if (posts.size > ArtistPageViewModel.INLINE_POSTS_CAP) onSeeAllPosts else null,
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
                        text = stringResource(R.string.destination_posts_load_error),
                        style = CorusFont.body,
                        color = CorusColors.Secondary,
                        modifier = Modifier.padding(horizontal = CorusSpacing.lg, vertical = CorusSpacing.sm),
                    )
                }
            } else if (posts.isEmpty()) {
                item {
                    Text(
                        text = stringResource(R.string.destination_no_posts_artist),
                        style = CorusFont.body,
                        color = CorusColors.Secondary,
                        modifier = Modifier.padding(horizontal = CorusSpacing.lg, vertical = CorusSpacing.sm),
                    )
                }
            } else {
                val shownPosts = posts.take(ArtistPageViewModel.INLINE_POSTS_CAP)
                items(shownPosts.size) { index ->
                    val post = shownPosts[index]
                    DestinationPostRow(
                        post = post,
                        onUserTap = { onNavigateToUser(post.user.id) },
                        onPostTap = { onNavigateToPost(post.id) },
                    )
                }
            }

            // ── Music videos rail — below the social content by design (posts
            //    are the differentiator; videos are the end-of-page delighter). ──
            if (matchedVideos.isNotEmpty()) {
                item {
                    MusicVideoRail(
                        videos = matchedVideos,
                        activeVideo = activeVideo,
                        onPlay = { video ->
                            viewModel.analyticsService.logMusicVideoPlayed(artistId, video.id)
                            activeVideo = video
                        },
                        onClosePlayer = { activeVideo = null },
                        onSeeAll = if (matchedVideos.size > 12) onSeeAllVideos else null,
                    )
                }
            }

            // ── Attribution footer ──
            item {
                DestinationAttributionFooter(
                    attribution = stringResource(R.string.destination_music_attribution),
                    onOpenSpotify = {
                        val url = "https://open.spotify.com/artist/${Uri.encode(artistId)}"
                        runCatching {
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                        }
                    },
                )
            }
        }
    }

    if (showShareSheet) {
        val shareSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        val sentMsg = stringResource(R.string.artist_detail_toast_artist_sent)
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
                subject = ShareMediaSubject.Artist(
                    ShareArtistSubject(
                        id = artistId,
                        name = artistName ?: "Artist",
                        imageUrl = heroImage,
                    )
                ),
                recentContacts = recentShareContacts,
                searchResults = shareSearchResults,
                isSearching = isShareSearching,
                isLoadingContacts = isLoadingShareContacts,
                onSearchQueryChange = { query -> viewModel.searchShareUsers(query) },
                onSendToUser = { userId, message ->
                    viewModel.sendArtistToUser(userId, artistId, artistName ?: "Artist", heroImage, message)
                    ToastManager.show(sentMsg)
                    showShareSheet = false
                },
                onDismiss = { showShareSheet = false },
                onAnalyticsLog = { method -> viewModel.analyticsService.logArtistShared(artistId, method) },
            )
        }
    }
}

@Composable
internal fun DiscographyRailCell(
    album: AlbumSummary,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .width(132.dp)
            .clickable(onClick = onClick),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(CorusSpacing.cornerRadiusMedium))
                .background(CorusColors.CardBackground),
        ) {
            if (album.coverUrl != null) {
                AsyncImage(
                    model = album.coverUrl,
                    contentDescription = album.title,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
            }
        }
        Spacer(modifier = Modifier.height(CorusSpacing.sm))
        Text(
            text = album.title,
            style = CorusFont.captionMedium,
            color = CorusColors.Text,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = albumKindCaption(album),
            style = CorusFont.caption,
            color = CorusColors.Secondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
