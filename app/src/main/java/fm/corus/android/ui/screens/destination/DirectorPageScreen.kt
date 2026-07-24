package fm.corus.android.ui.screens.destination

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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Movie
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil3.compose.AsyncImage
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import fm.corus.android.ui.components.ImmersiveBarHeight
import fm.corus.android.ui.components.ImmersiveCollapsingBar
import fm.corus.android.ui.components.ImmersiveCoverBackdrop
import fm.corus.android.ui.components.ImmersiveExtendUnderStatusBar
import fm.corus.android.ui.components.ImmersiveStatusBarIcons
import fm.corus.android.ui.components.currentStatusBarTopPx
import fm.corus.android.ui.components.extendIntoStatusBar
import fm.corus.android.ui.components.immersiveCollapseProgress
import fm.corus.android.R
import fm.corus.android.data.model.DirectorFilm
import fm.corus.android.data.model.MusicVideo
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
import fm.corus.android.ui.components.ExpandedPhoto
import fm.corus.android.ui.components.ShareDirectorSubject
import fm.corus.android.ui.components.ShareMediaSheet
import fm.corus.android.ui.components.ShareMediaSubject
import fm.corus.android.ui.components.SkeletonAlbumGridCell
import fm.corus.android.ui.components.SkeletonUserRow
import fm.corus.android.ui.components.ToastManager
import fm.corus.android.ui.theme.CorusSystemBars
import fm.corus.android.ui.navigation.FilmDetailRoute
import fm.corus.android.ui.theme.CorusColors
import fm.corus.android.ui.theme.CorusFont
import fm.corus.android.ui.theme.CorusSpacing

/** Visible height of the director's blurred-photo backdrop (taller for the 2:3
 *  portrait); also the collapse distance basis via [ImmersiveBarHeight]. */
private val DirectorImmersiveHeroHeight = 420.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DirectorPageScreen(
    directorId: String,
    nameHint: String? = null,
    imageUrlHint: String? = null,
    viewModel: DirectorPageViewModel = hiltViewModel(),
    onBack: () -> Unit = {},
    onNavigateToUser: (String) -> Unit = {},
    onNavigateToPost: (String) -> Unit = {},
    onNavigateToFilm: (FilmDetailRoute) -> Unit = {},
    onSeeAllPosts: () -> Unit = {},
    onSeeAllFilmography: () -> Unit = {},
    onSeeAllTrailers: () -> Unit = {},
    onShowPhoto: (ExpandedPhoto) -> Unit = {},
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
    var showShareSheet by remember { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }

    val directorName = detail?.name?.takeIf { it.isNotBlank() } ?: nameHint
    val photo = detail?.imageUrl ?: imageUrlHint

    // Only YouTube-matched trailers play inline (mirrors artist matchedVideos).
    var activeTrailer by remember { mutableStateOf<MusicVideo?>(null) }
    val matchedTrailers = (detail?.trailers ?: emptyList()).filter { it.youtubeId != null }

    LaunchedEffect(directorId) {
        viewModel.analyticsService.logDirectorPageViewed(directorId)
        viewModel.loadCatalog(directorId)
        viewModel.loadPosts(directorId, nameHint)
    }

    // Immersive header (prototype): blurred-photo hero + shared frosted collapsing
    // bar + status-bar blend (ui/components/ImmersiveHeader.kt).
    val listState = rememberLazyListState()
    val immersive = viewModel.immersiveHeaderEnabled && (photo != null || isCatalogLoading)
    val hazeState = remember { HazeState() }
    val statusBarTopPx = currentStatusBarTopPx()
    val extendUnderStatusBar = immersive && ImmersiveExtendUnderStatusBar && statusBarTopPx > 0
    val statusBarPadding = if (extendUnderStatusBar) {
        with(LocalDensity.current) { statusBarTopPx.toDp() }
    } else {
        0.dp
    }
    val heroCollapseDistancePx = with(LocalDensity.current) {
        (DirectorImmersiveHeroHeight - ImmersiveBarHeight).toPx()
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
            // Immersive draws the shared floating bar over the blurred photo below.
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
            }
        },
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
        if (immersive) {
            ImmersiveCoverBackdrop(
                artUrl = photo,
                height = DirectorImmersiveHeroHeight + statusBarPadding,
                listState = listState,
            )
        }
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .then(if (immersive) Modifier.hazeSource(hazeState) else Modifier),
            contentPadding = PaddingValues(bottom = CorusSpacing.xxxl + CorusSpacing.xxxl),
        ) {
            // ── Header: centered 2:3 portrait + name + "Director" ──
            item {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Spacer(
                        modifier = Modifier.height(
                            if (immersive) statusBarPadding + ImmersiveBarHeight + CorusSpacing.md
                            else CorusSpacing.sm
                        )
                    )
                    DirectorPhotoCard(
                        photo = photo,
                        directorName = directorName,
                        onTap = { url -> onShowPhoto(ExpandedPhoto(url, directorName)) },
                    )
                    Spacer(modifier = Modifier.height(CorusSpacing.md))
                    if (directorName != null || catalogError) {
                        Text(
                            text = directorName
                                ?: stringResource(R.string.destination_director_label),
                            style = CorusFont.songTitleLarge,
                            color = CorusColors.Text,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(horizontal = CorusSpacing.lg),
                        )
                    }
                    Spacer(modifier = Modifier.height(CorusSpacing.xxs))
                    Text(
                        text = stringResource(R.string.destination_director_label),
                        style = CorusFont.captionMedium,
                        color = CorusColors.Secondary,
                    )
                }
            }

            // ── Shared by N people ──
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
                item {
                    Spacer(modifier = Modifier.height(CorusSpacing.sm))
                    SkeletonSharedByPeopleRow()
                }
            }

            // ── Your posts ──
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
                        onFilmChipTap = post.movieId?.let { movieId ->
                            { onNavigateToFilm(post.toFilmDetailRoute(movieId)) }
                        },
                    )
                }
            }

            // ── Filmography ──
            if (isCatalogLoading && detail == null) {
                item {
                    DestinationSectionHeader(stringResource(R.string.destination_filmography))
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = CorusSpacing.lg),
                        horizontalArrangement = Arrangement.spacedBy(CorusSpacing.md),
                    ) {
                        items(4) {
                            SkeletonAlbumGridCell(
                                modifier = Modifier
                                    .width(110.dp)
                                    .clip(RoundedCornerShape(CorusSpacing.cornerRadiusMedium)),
                                aspectRatio = 2f / 3f,
                            )
                        }
                    }
                }
            } else if (catalogError && detail == null) {
                item {
                    Text(
                        text = stringResource(R.string.destination_catalog_load_error),
                        style = CorusFont.body,
                        color = CorusColors.Secondary,
                        modifier = Modifier.padding(horizontal = CorusSpacing.lg, vertical = CorusSpacing.lg),
                    )
                }
            } else {
                val films = detail?.films ?: emptyList()
                if (films.isNotEmpty()) {
                    item {
                        DestinationSectionHeader(
                            title = stringResource(R.string.destination_filmography),
                            onSeeAll = onSeeAllFilmography,
                        )
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = CorusSpacing.lg),
                            horizontalArrangement = Arrangement.spacedBy(CorusSpacing.md),
                        ) {
                            items(films.take(12).size) { index ->
                                val film = films[index]
                                FilmographyRailCell(
                                    film = film,
                                    onClick = {
                                        viewModel.analyticsService.logPostFromDirector(directorId, film.id)
                                        onNavigateToFilm(
                                            FilmDetailRoute(
                                                movieId = film.id,
                                                movieTitle = film.title,
                                                directorName = directorName,
                                                releaseYear = film.year?.toString(),
                                                posterURL = film.posterUrl,
                                            )
                                        )
                                    },
                                )
                            }
                        }
                    }
                }
            }

            // ── Recent posts ──
            item {
                DestinationSectionHeader(
                    title = stringResource(R.string.destination_recent_posts),
                    onSeeAll = if (posts.size >= DirectorPageViewModel.PAGE_SIZE) onSeeAllPosts else null,
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
                        text = stringResource(R.string.destination_no_posts_director),
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
                        onFilmChipTap = post.movieId?.let { movieId ->
                            { onNavigateToFilm(post.toFilmDetailRoute(movieId)) }
                        },
                    )
                }
            }

            // ── Trailers rail — below the social content by design (posts are
            //    the differentiator; trailers are the end-of-page delighter),
            //    mirroring the artist page's music-video rail. ──
            if (matchedTrailers.isNotEmpty()) {
                item {
                    Spacer(modifier = Modifier.height(CorusSpacing.lg))
                    MusicVideoRail(
                        videos = matchedTrailers,
                        activeVideo = activeTrailer,
                        onPlay = { video ->
                            viewModel.analyticsService.logTrailerPlayed(directorId, video.id)
                            activeTrailer = video
                        },
                        onClosePlayer = { activeTrailer = null },
                        onSeeAll = if (matchedTrailers.size > 12) onSeeAllTrailers else null,
                        title = stringResource(R.string.destination_trailers),
                    )
                }
            }

            // ── Attribution (TMDB; no Spotify link on director pages) ──
            item {
                DestinationAttributionFooter(
                    attribution = stringResource(R.string.destination_film_attribution),
                )
            }
        }
        if (immersive) {
            ImmersiveCollapsingBar(
                hazeState = hazeState,
                progress = collapseProgress,
                title = directorName,
                onBack = onBack,
                topInset = statusBarPadding,
                actions = { tint ->
                    if (viewModel.entityShareEnabled) {
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
            )
        }
        }
    }

    if (showShareSheet) {
        val shareSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        val sentMsg = stringResource(R.string.director_detail_toast_director_sent)
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
                subject = ShareMediaSubject.Director(
                    ShareDirectorSubject(
                        id = directorId,
                        name = directorName ?: "Director",
                        imageUrl = photo,
                    )
                ),
                recentContacts = recentShareContacts,
                searchResults = shareSearchResults,
                isSearching = isShareSearching,
                isLoadingContacts = isLoadingShareContacts,
                onSearchQueryChange = { query -> viewModel.searchShareUsers(query) },
                onSendToUser = { userId, message ->
                    viewModel.sendDirectorToUser(userId, directorId, directorName ?: "Director", photo, message)
                    ToastManager.show(sentMsg)
                    showShareSheet = false
                },
                onDismiss = { showShareSheet = false },
                onAnalyticsLog = { method -> viewModel.analyticsService.logDirectorShared(directorId, method) },
            )
        }
    }
}

/** Film-detail route with the hints a director-page post carries. */
private fun fm.corus.android.data.model.CymbalPost.toFilmDetailRoute(movieId: String) =
    FilmDetailRoute(
        movieId = movieId,
        movieTitle = movieTitle,
        directorName = directorName,
        releaseYear = releaseYear,
        posterURL = posterURL,
        posterLargeURL = posterLargeURL,
        trailerURL = trailerURL,
        movieReleaseDate = movieReleaseDate,
    )

@Composable
internal fun DirectorPhotoCard(
    photo: String?,
    directorName: String?,
    onTap: (String) -> Unit,
) {
    Box(
        modifier = Modifier
            .width(140.dp)
            .aspectRatio(2f / 3f)
            .shadow(4.dp, RoundedCornerShape(CorusSpacing.cornerRadiusLarge))
            .clip(RoundedCornerShape(CorusSpacing.cornerRadiusLarge))
            .then(
                if (photo != null) {
                    Modifier.clickable(
                        onClickLabel = stringResource(R.string.photo_viewer_open_cd),
                    ) { onTap(photo) }
                } else {
                    Modifier
                },
            )
            .background(CorusColors.CardBackground),
    ) {
        if (photo != null) {
            AsyncImage(
                model = photo,
                contentDescription = directorName,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        } else {
            Icon(
                imageVector = Icons.Filled.Movie,
                contentDescription = null,
                tint = CorusColors.Tertiary,
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(36.dp),
            )
        }
    }
}

@Composable
internal fun FilmographyRailCell(
    film: DirectorFilm,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .width(110.dp)
            .clickable(onClick = onClick),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(2f / 3f)
                .clip(RoundedCornerShape(CorusSpacing.cornerRadiusMedium))
                .background(CorusColors.CardBackground),
        ) {
            if (film.posterUrl != null) {
                AsyncImage(
                    model = film.posterUrl,
                    contentDescription = film.title,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
            }
        }
        Spacer(modifier = Modifier.height(CorusSpacing.sm))
        Text(
            text = film.title,
            style = CorusFont.captionMedium,
            color = CorusColors.Text,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (film.year != null) {
            Text(
                text = "${film.year}",
                style = CorusFont.caption,
                color = CorusColors.Secondary,
                maxLines = 1,
            )
        }
    }
}
