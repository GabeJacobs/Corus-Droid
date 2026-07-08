package fm.corus.android.ui.screens.feed

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.MovieCreation
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.outlined.Tv
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil3.compose.AsyncImage
import fm.corus.android.R
import fm.corus.android.data.model.CymbalPost
import fm.corus.android.data.model.CymbalMovie
import fm.corus.android.domain.TrailerPlaybackCoordinator
import fm.corus.android.ui.components.CorusHeaderIconButton
import fm.corus.android.ui.components.InlineYouTubePlayer
import fm.corus.android.ui.components.ShareMediaSheet
import fm.corus.android.ui.components.ShareMediaSubject
import fm.corus.android.ui.components.ToastManager
import fm.corus.android.ui.components.youTubeVideoID
import fm.corus.android.ui.components.SkeletonFilmDetailHeader
import fm.corus.android.ui.components.SkeletonUserRow
import fm.corus.android.ui.components.UserAvatarView
import fm.corus.android.ui.components.FirstPosterBadge
import fm.corus.android.ui.components.UsernameWithFlair
import fm.corus.android.ui.theme.CorusColors
import fm.corus.android.ui.theme.CorusFont
import fm.corus.android.ui.theme.CorusSpacing
import fm.corus.android.ui.theme.CorusSystemBars
import fm.corus.android.ui.util.DateUtils

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FilmDetailScreen(
    movieId: String,
    initialMovieTitle: String? = null,
    initialDirectorName: String? = null,
    initialReleaseYear: String? = null,
    initialPosterURL: String? = null,
    initialPosterLargeURL: String? = null,
    initialTrailerURL: String? = null,
    viewModel: FilmDetailViewModel = hiltViewModel(),
    onBack: () -> Unit = {},
    onNavigateToUser: (String) -> Unit = {},
    onNavigateToPost: (String) -> Unit = {},
    onNavigateToCompose: (String) -> Unit = {},
    /** Director page (artist_pages_enabled) — null while the flag is off,
     *  which keeps the director line as plain text. */
    onNavigateToDirector: ((fm.corus.android.ui.navigation.DirectorPageRoute) -> Unit)? = null,
) {
    val posts by viewModel.posts.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val isLoadingMore by viewModel.isLoadingMore.collectAsState()
    val hasMore by viewModel.hasMore.collectAsState()
    val loadError by viewModel.loadError.collectAsState()
    val uniquePosterCount by viewModel.uniquePosterCount.collectAsState()
    val movieHeader by viewModel.movieHeader.collectAsState()
    val context = LocalContext.current

    var showShareSheet by remember { mutableStateOf(false) }
    val shareSearchResults by viewModel.shareSearchResults.collectAsState()
    val recentShareContacts by viewModel.recentShareContacts.collectAsState()
    val isShareSearching by viewModel.isShareSearching.collectAsState()
    val isLoadingShareContacts by viewModel.isLoadingShareContacts.collectAsState()

    // TMDB director ids for the "..." menu's Go to Director row — same gating as
    // the tappable director line below the title (loaded posts carry them).
    val menuDirectorIds = posts.firstOrNull { it.directorIds.isNotEmpty() }?.directorIds ?: emptyList()
    val menuDirectorId = menuDirectorIds.firstOrNull()

    // The film as a CymbalMovie for the share sheet — header info + route hints,
    // with directorIds + tmdbWebURL pulled from a loaded post when present.
    val shareMovie = CymbalMovie(
        id = movieId,
        title = movieHeader?.movieTitle ?: initialMovieTitle ?: "",
        directorName = movieHeader?.directorName ?: initialDirectorName ?: "",
        directorIds = menuDirectorIds,
        year = movieHeader?.releaseYear ?: initialReleaseYear ?: "",
        posterURL = movieHeader?.posterURL ?: initialPosterURL,
        posterLargeURL = movieHeader?.posterLargeURL ?: initialPosterLargeURL,
        tmdbWebURL = posts.firstOrNull()?.tmdbWebURL
            ?: "https://www.themoviedb.org/movie/${movieId.removePrefix("tmdb_")}",
    )

    // Inline trailer playback (mirrors the feed's PostCard): tapping the poster
    // or the Watch Trailer button plays the trailer in place instead of leaving
    // for YouTube. Sized wider than the poster here — this detail page has the
    // room the feed card doesn't. A synthetic slot id shares the app-wide
    // single-trailer coordinator (which also pauses music on play).
    val trailerSlotId = "film:$movieId"
    var inlineTrailerVideoID by remember(movieId) { mutableStateOf<String?>(null) }
    val activeTrailerPostId by TrailerPlaybackCoordinator.activePostId.collectAsState()
    LaunchedEffect(activeTrailerPostId) {
        if (activeTrailerPostId != trailerSlotId && inlineTrailerVideoID != null) {
            inlineTrailerVideoID = null
        }
    }
    DisposableEffect(movieId) {
        onDispose { TrailerPlaybackCoordinator.stop(trailerSlotId) }
    }

    LaunchedEffect(movieId) {
        if (initialMovieTitle != null) {
            viewModel.setInitialMovieInfo(
                MovieHeaderInfo(
                    movieTitle = initialMovieTitle,
                    directorName = initialDirectorName,
                    releaseYear = initialReleaseYear,
                    posterURL = initialPosterURL,
                    posterLargeURL = initialPosterLargeURL,
                    trailerURL = initialTrailerURL,
                )
            )
        }
        viewModel.loadMoviePosts(movieId)
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
                    Box {
                        var menuExpanded by remember { mutableStateOf(false) }
                        CorusHeaderIconButton(
                            onClick = { menuExpanded = true },
                            imageVector = Icons.Filled.MoreVert,
                            contentDescription = stringResource(R.string.feed_cd_more_options),
                        )
                        DropdownMenu(
                            expanded = menuExpanded,
                            onDismissRequest = { menuExpanded = false },
                        ) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.post_menu_share), style = CorusFont.body) },
                                leadingIcon = { Icon(Icons.Filled.Share, contentDescription = null) },
                                onClick = {
                                    menuExpanded = false
                                    showShareSheet = true
                                },
                            )
                            if (onNavigateToDirector != null && menuDirectorId != null) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.post_menu_go_to_director), style = CorusFont.body) },
                                    leadingIcon = { Icon(Icons.Filled.MovieCreation, contentDescription = null) },
                                    onClick = {
                                        menuExpanded = false
                                        onNavigateToDirector(
                                            fm.corus.android.ui.navigation.DirectorPageRoute(
                                                directorId = menuDirectorId,
                                                name = fm.corus.android.data.model.primaryNameHint(
                                                    movieHeader?.directorName ?: initialDirectorName ?: "",
                                                    menuDirectorIds.size,
                                                ).ifEmpty { null },
                                            )
                                        )
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
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            horizontalAlignment = Alignment.CenterHorizontally,
            contentPadding = PaddingValues(bottom = CorusSpacing.xxxl + CorusSpacing.xxxl),
        ) {
            // Film header
            item {
                val header = movieHeader
                if (header == null && isLoading) {
                    SkeletonFilmDetailHeader()
                    HorizontalDivider(color = CorusColors.Divider, thickness = 0.5.dp)
                    return@item
                }

                Spacer(modifier = Modifier.height(CorusSpacing.xl))

                if (header != null) {
                    val trailerVideoID = youTubeVideoID(header.trailerURL)
                    val playingTrailer = inlineTrailerVideoID
                    if (playingTrailer != null) {
                        // Playing: a wide 16:9 player spanning the content width —
                        // much bigger than the 220dp poster. The feed keeps the
                        // poster-frame size; this detail page has room to go wide.
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = CorusSpacing.lg)
                                .aspectRatio(16f / 9f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color.Black),
                        ) {
                            // key() on the id so a new trailer disposes the old
                            // WebView and creates a fresh one (mirrors web/iOS).
                            key(playingTrailer) {
                                InlineYouTubePlayer(
                                    videoID = playingTrailer,
                                    modifier = Modifier.fillMaxSize(),
                                    showControls = true,
                                    onEnded = {
                                        inlineTrailerVideoID = null
                                        TrailerPlaybackCoordinator.stop(trailerSlotId)
                                    },
                                )
                            }
                            Box(
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(CorusSpacing.sm)
                                    .size(30.dp)
                                    .clip(CircleShape)
                                    .background(Color.Black.copy(alpha = 0.55f))
                                    .clickable {
                                        inlineTrailerVideoID = null
                                        TrailerPlaybackCoordinator.stop(trailerSlotId)
                                    },
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Close,
                                    contentDescription = stringResource(R.string.feed_cd_back),
                                    tint = Color.White,
                                    modifier = Modifier.size(16.dp),
                                )
                            }
                        }
                    } else {
                        // Poster with a play badge (no hover on mobile) — tapping
                        // anywhere on it starts the trailer inline.
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = if (trailerVideoID != null) {
                                Modifier.clickable {
                                    inlineTrailerVideoID = trailerVideoID
                                    TrailerPlaybackCoordinator.play(trailerSlotId)
                                }
                            } else Modifier,
                        ) {
                            AsyncImage(
                                model = header.posterLargeURL ?: header.posterURL,
                                contentDescription = header.movieTitle,
                                modifier = Modifier
                                    .width(220.dp)
                                    .aspectRatio(2f / 3f)
                                    .shadow(8.dp, RoundedCornerShape(8.dp))
                                    .clip(RoundedCornerShape(8.dp)),
                                contentScale = ContentScale.Crop,
                            )
                            if (trailerVideoID != null) {
                                Box(
                                    modifier = Modifier
                                        .size(56.dp)
                                        .clip(CircleShape)
                                        .background(Color.Black.copy(alpha = 0.55f)),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.PlayArrow,
                                        contentDescription = stringResource(R.string.film_detail_watch_trailer),
                                        tint = Color.White,
                                        modifier = Modifier.size(28.dp),
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(CorusSpacing.md))

                    // Title + year
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(CorusSpacing.xs),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = header.movieTitle ?: "",
                            style = CorusFont.songTitleLarge,
                            color = CorusColors.Text,
                            textAlign = TextAlign.Center,
                        )
                        if (!header.releaseYear.isNullOrBlank()) {
                            Text(
                                text = stringResource(R.string.film_detail_year_format, header.releaseYear),
                                style = CorusFont.artistName,
                                color = CorusColors.Tertiary,
                            )
                        }
                    }

                    // Director — tappable when the artist-pages flag is on AND
                    // the loaded posts carry directorIds (legacy posts don't).
                    // Style identical either way (no accent, no underline).
                    if (!header.directorName.isNullOrBlank()) {
                        Spacer(modifier = Modifier.height(CorusSpacing.xxs))
                        val directorIds = posts.firstOrNull()?.directorIds ?: emptyList()
                        val directorId = directorIds.firstOrNull()
                        val directorTapModifier = if (onNavigateToDirector != null && directorId != null) {
                            Modifier.clickable {
                                onNavigateToDirector(
                                    fm.corus.android.ui.navigation.DirectorPageRoute(
                                        directorId = directorId,
                                        name = fm.corus.android.data.model.primaryNameHint(
                                            header.directorName, directorIds.size,
                                        ).ifEmpty { null },
                                    )
                                )
                            }
                        } else Modifier
                        Text(
                            text = header.directorName,
                            style = CorusFont.artistName,
                            color = CorusColors.Secondary,
                            modifier = directorTapModifier,
                        )
                    }

                    Spacer(modifier = Modifier.height(CorusSpacing.md))

                    // Action buttons
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(CorusSpacing.sm),
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(CorusSpacing.md),
                        ) {
                            // Post Film capsule
                            Button(
                                onClick = { onNavigateToCompose(movieId) },
                                shape = RoundedCornerShape(50),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = CorusColors.Accent,
                                    contentColor = Color.White,
                                ),
                                contentPadding = PaddingValues(horizontal = CorusSpacing.lg, vertical = CorusSpacing.sm),
                            ) {
                                Icon(
                                    Icons.Filled.Add,
                                    contentDescription = null,
                                    modifier = Modifier.size(14.dp),
                                )
                                Spacer(modifier = Modifier.width(CorusSpacing.sm))
                                Text(stringResource(R.string.film_detail_post_film), style = CorusFont.buttonSmall)
                            }

                            // Watch Trailer capsule
                            if (!header.trailerURL.isNullOrBlank()) {
                                Button(
                                    onClick = {
                                        // Play inline in the poster above (matching
                                        // the feed); non-YouTube URLs fall back to
                                        // opening the link.
                                        val videoID = youTubeVideoID(header.trailerURL)
                                        if (videoID != null) {
                                            inlineTrailerVideoID = videoID
                                            TrailerPlaybackCoordinator.play(trailerSlotId)
                                        } else {
                                            viewModel.nowPlayingManager.pause()
                                            try { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(header.trailerURL))) } catch (_: Exception) { }
                                        }
                                    },
                                    shape = RoundedCornerShape(50),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = CorusColors.Accent,
                                        contentColor = Color.White,
                                    ),
                                    contentPadding = PaddingValues(horizontal = CorusSpacing.lg, vertical = CorusSpacing.sm),
                                ) {
                                    Icon(
                                        Icons.Filled.PlayArrow,
                                        contentDescription = null,
                                        modifier = Modifier.size(14.dp),
                                    )
                                    Spacer(modifier = Modifier.width(CorusSpacing.sm))
                                    Text(stringResource(R.string.film_detail_watch_trailer), style = CorusFont.buttonSmall)
                                }
                            }
                        }

                        // Where to Watch outline capsule
                        if (!header.movieTitle.isNullOrBlank()) {
                            OutlinedButton(
                                onClick = {
                                    val url = "https://www.justwatch.com/us/search?q=${Uri.encode(header.movieTitle)}"
                                    try { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) } catch (_: Exception) { }
                                },
                                shape = RoundedCornerShape(50),
                                border = ButtonDefaults.outlinedButtonBorder(enabled = true).copy(
                                    brush = androidx.compose.ui.graphics.SolidColor(CorusColors.Accent),
                                ),
                                contentPadding = PaddingValues(horizontal = CorusSpacing.lg, vertical = CorusSpacing.sm),
                            ) {
                                Icon(
                                    Icons.Outlined.Tv,
                                    contentDescription = null,
                                    modifier = Modifier.size(14.dp),
                                    tint = CorusColors.Accent,
                                )
                                Spacer(modifier = Modifier.width(CorusSpacing.sm))
                                Text(stringResource(R.string.film_detail_where_to_watch), style = CorusFont.buttonSmall, color = CorusColors.Accent)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(CorusSpacing.md))
                }

                HorizontalDivider(color = CorusColors.Divider, thickness = 0.5.dp)
            }

            // Posted by section
            if (isLoading) {
                item {
                    Text(
                        text = stringResource(R.string.film_detail_posted_by),
                        style = CorusFont.sectionHeader,
                        color = CorusColors.Secondary,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = CorusSpacing.lg)
                            .padding(top = CorusSpacing.lg, bottom = CorusSpacing.md),
                    )
                }
                items(6) { index ->
                    SkeletonUserRow()
                    if (index < 5) {
                        HorizontalDivider(
                            modifier = Modifier.padding(start = 72.dp),
                            color = CorusColors.Divider,
                            thickness = 0.5.dp,
                        )
                    }
                }
            } else if (loadError != null) {
                item {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(top = 40.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(stringResource(R.string.film_detail_load_error), style = CorusFont.bodyMedium, color = CorusColors.Secondary)
                        Spacer(modifier = Modifier.height(CorusSpacing.md))
                        TextButton(onClick = { viewModel.loadMoviePosts(movieId) }) {
                            Text(stringResource(R.string.film_detail_try_again), style = CorusFont.buttonSmall, color = CorusColors.Accent)
                        }
                    }
                }
            } else if (posts.isEmpty()) {
                item {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(top = 40.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(stringResource(R.string.film_detail_empty), style = CorusFont.body, color = CorusColors.Secondary)
                        Spacer(modifier = Modifier.height(CorusSpacing.md))
                        Button(
                            onClick = { onNavigateToCompose(movieId) },
                            shape = RoundedCornerShape(50),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = CorusColors.Accent,
                                contentColor = Color.White,
                            ),
                            contentPadding = PaddingValues(horizontal = CorusSpacing.lg, vertical = CorusSpacing.sm),
                        ) {
                            Text(stringResource(R.string.film_detail_be_the_first), style = CorusFont.buttonSmall)
                        }
                    }
                }
            } else {
                item {
                    val count = uniquePosterCount ?: posts.map { it.user.id }.toSet().size
                    Text(
                        text = pluralStringResource(R.plurals.film_detail_posted_by_count, count, formatFilmUserCount(count)),
                        style = CorusFont.sectionHeader,
                        color = CorusColors.Secondary,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = CorusSpacing.lg)
                            .padding(top = CorusSpacing.lg, bottom = CorusSpacing.md),
                    )
                }

                items(posts, key = { it.id }) { post ->
                    FilmPostedByRow(
                        post = post,
                        onUserTap = {
                            viewModel.analyticsService.logPostedByProfileTapped("film", movieId, post.user.id)
                            onNavigateToUser(post.user.id)
                        },
                        onPostTap = {
                            viewModel.analyticsService.logPostedByPostTapped("film", movieId, post.id)
                            onNavigateToPost(post.id)
                        },
                    )
                    if (post.id != posts.lastOrNull()?.id) {
                        HorizontalDivider(
                            modifier = Modifier.padding(start = 72.dp),
                            color = CorusColors.Divider,
                            thickness = 0.5.dp,
                        )
                    }

                    if (post.id == posts.lastOrNull()?.id && hasMore && !isLoadingMore) {
                        LaunchedEffect(post.id) { viewModel.loadMore() }
                    }
                }

                if (isLoadingMore) {
                    item {
                        CircularProgressIndicator(
                            color = CorusColors.Accent,
                            modifier = Modifier.padding(CorusSpacing.lg),
                        )
                    }
                }
            }
        }
    }

    // ── Share Film bottom sheet ──
    if (showShareSheet) {
        val shareSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        val filmSharedMsg = stringResource(R.string.film_detail_toast_film_sent)

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
                subject = ShareMediaSubject.Film(shareMovie),
                recentContacts = recentShareContacts,
                searchResults = shareSearchResults,
                isSearching = isShareSearching,
                isLoadingContacts = isLoadingShareContacts,
                onSearchQueryChange = { query -> viewModel.searchShareUsers(query) },
                onSendToUser = { userId, message ->
                    viewModel.sendFilmToUser(userId, shareMovie, message)
                    ToastManager.show(filmSharedMsg)
                    showShareSheet = false
                },
                onDismiss = { showShareSheet = false },
            )
        }
    }
}

@Composable
internal fun FilmPostedByRow(
    post: CymbalPost,
    onUserTap: () -> Unit = {},
    onPostTap: () -> Unit = {},
) {
    // Whole row opens the post; the avatar and name column carry their own
    // clickable for the profile. A child clickable consumes the tap, so taps on
    // the avatar/name go to onUserTap and everything else falls through to the
    // row's onPostTap.
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onPostTap)
            .padding(horizontal = CorusSpacing.lg, vertical = CorusSpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        UserAvatarView(
            avatarURL = post.user.avatarURL,
            displayName = post.user.displayName,
            size = CorusSpacing.avatarMedium,
            modifier = Modifier.clickable(onClick = onUserTap),
        )
        Spacer(modifier = Modifier.width(CorusSpacing.md))
        Column(modifier = Modifier.weight(1f)) {
            // Username + display name open the profile. Leads with the @username
            // (with the 1ST trophy inline) to match search/follow lists; display
            // name sits muted below.
            Column(modifier = Modifier.clickable(onClick = onUserTap)) {
                UsernameWithFlair(
                    username = post.user.username,
                    isVerified = post.user.isVerified,
                    isClubMember = post.user.isClubMember,
                    flairStyle = post.user.flairStyle,
                    isBot = post.user.isBot,
                    isFirstPoster = post.isFirstPoster,
                    showAtPrefix = true,
                    style = CorusFont.username,
                    color = CorusColors.Text,
                )
                if (post.user.displayName.isNotBlank()) {
                    Text(
                        text = post.user.displayName,
                        style = CorusFont.caption,
                        color = CorusColors.Secondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            // Caption snippet — part of the row's post tap (no clickable here).
            val caption = post.caption?.trim()
            if (!caption.isNullOrEmpty()) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "“$caption”",
                    style = CorusFont.caption,
                    color = CorusColors.Secondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Spacer(modifier = Modifier.width(CorusSpacing.sm))
        // Centered against the full row: timestamp + chevron read as a
        // row-level "open post" affordance.
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = DateUtils.relativeTime(LocalContext.current, post.timestamp),
                style = CorusFont.caption,
                color = CorusColors.Tertiary,
            )
            Spacer(modifier = Modifier.width(CorusSpacing.xs))
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = CorusColors.Tertiary,
                modifier = Modifier.size(16.dp),
            )
        }
    }
}

private fun formatFilmUserCount(count: Int): String {
    return when {
        count < 1000 -> "$count"
        count < 1_000_000 -> String.format("%.2fK", count / 1000.0)
        else -> String.format("%.2fM", count / 1_000_000.0)
    }
}
