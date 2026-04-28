package fm.corus.android.ui.screens.feed

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import fm.corus.android.R
import fm.corus.android.data.model.CymbalPost
import fm.corus.android.data.model.CymbalTrack
import fm.corus.android.data.model.CymbalUser
import fm.corus.android.data.model.MediaType
import fm.corus.android.data.model.SuggestedUserMatch
import fm.corus.android.domain.HapticManager
import fm.corus.android.ui.LocalHapticManager
import fm.corus.android.ui.components.PostCard
import fm.corus.android.ui.components.PostMenuSheets
import fm.corus.android.ui.components.SkeletonPostCard
import fm.corus.android.ui.components.TasteMatchCard
import fm.corus.android.ui.components.ToastManager
import fm.corus.android.ui.theme.CorusColors
import fm.corus.android.ui.theme.CorusFont
import fm.corus.android.ui.theme.CorusSpacing
import fm.corus.android.ui.theme.CorusSystemBars

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FeedScreen(
    viewModel: FeedViewModel = hiltViewModel(),
    scrollToTopTrigger: Int = 0,
    onNavigateToPost: (String) -> Unit = {},
    onNavigateToUser: (CymbalUser) -> Unit = {},
    onNavigateToUserById: (String) -> Unit = {},
    onNavigateToUserByUsername: (String) -> Unit = {},
    onNavigateToComments: (String) -> Unit = {},
    onNavigateToLikes: (String) -> Unit = {},
    onNavigateToHashtag: (String) -> Unit = {},
    onNavigateToSong: (CymbalTrack) -> Unit = {},
    onNavigateToFilm: (String) -> Unit = {},
    onNavigateToBotList: (String?) -> Unit = {},
    onRepost: (CymbalPost) -> Unit = {},
) {
    val posts by viewModel.filteredPosts.collectAsState()
    val allPosts by viewModel.posts.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val isGeneratingPlaylist by viewModel.nowPlayingManager.isGeneratingPlaylist.collectAsState()
    val playlistError by viewModel.nowPlayingManager.playlistError.collectAsState()
    val musicService by viewModel.musicServicePreference.current.collectAsState()
    var showPlaylistAlert by remember { mutableStateOf(false) }

    LaunchedEffect(playlistError) {
        if (playlistError != null) {
            ToastManager.show(playlistError!!)
            viewModel.nowPlayingManager.clearPlaylistError()
        }
    }
    val hasLoaded by viewModel.hasLoaded.collectAsState()
    val isRefreshing by viewModel.isRefreshing.collectAsState()
    val hasMore by viewModel.hasMore.collectAsState()
    val engagementStates by viewModel.engagementStates.collectAsState()
    val currentUserProfile by viewModel.currentUserProfile.collectAsState()
    val feedMediaFilter by viewModel.feedMediaFilter.collectAsState()
    val curatedMusicBots by viewModel.curatedMusicBots.collectAsState()
    val curatedFilmBots by viewModel.curatedFilmBots.collectAsState()
    val followedBotIds by viewModel.followedBotIds.collectAsState()
    val isBotsLoading by viewModel.isBotsLoading.collectAsState()
    val nowPlayingState by viewModel.nowPlayingManager.state.collectAsState()
    val loadingTrackId by viewModel.nowPlayingManager.loadingTrackId.collectAsState()
    val context = LocalContext.current
    var filterMenuExpanded by remember { mutableStateOf(false) }
    var sharePost by remember { mutableStateOf<CymbalPost?>(null) }
    var menuPost by remember { mutableStateOf<CymbalPost?>(null) }
    var editCaptionPost by remember { mutableStateOf<CymbalPost?>(null) }
    var showDeleteConfirm by remember { mutableStateOf<CymbalPost?>(null) }
    val backCoverStates = remember { mutableMapOf<String, fm.corus.android.ui.components.BackCoverFlipState>() }
    val scope = rememberCoroutineScope()
    fun backCoverStateFor(postId: String) =
        backCoverStates.getOrPut(postId) { fm.corus.android.ui.components.BackCoverFlipState() }
    var filmInfoPost by remember { mutableStateOf<CymbalPost?>(null) }
    var showClubOffer by remember { mutableStateOf(false) }

    val paywallRequested by viewModel.nowPlayingManager.paywallRequested.collectAsState()
    LaunchedEffect(paywallRequested) {
        if (paywallRequested) {
            showClubOffer = true
            viewModel.nowPlayingManager.clearPaywallRequested()
        }
    }

    LaunchedEffect(Unit) {
        if (allPosts.isEmpty()) {
            viewModel.loadFeed()
            viewModel.loadBotSuggestions()
        }
    }

    // Pagination handled per-item via onAppear (see itemsIndexed below)

    // Hoist list state above the when block so scroll position survives
    // navigation (rememberSaveable key stays stable across recompositions)
    val listState = rememberLazyListState()
    var lastScrollTrigger by rememberSaveable { mutableIntStateOf(0) }
    LaunchedEffect(scrollToTopTrigger) {
        if (scrollToTopTrigger > lastScrollTrigger) {
            listState.animateScrollToItem(0)
            lastScrollTrigger = scrollToTopTrigger
        }
    }

    val header: @Composable () -> Unit = {
        FeedHeader(
            showPlaylistButton = posts.isNotEmpty() && feedMediaFilter != MediaType.MOVIE,
            isGeneratingPlaylist = isGeneratingPlaylist,
            feedMediaFilter = feedMediaFilter,
            filterMenuExpanded = filterMenuExpanded,
            onFilterMenuExpandedChange = { filterMenuExpanded = it },
            onSetFilter = { viewModel.setFeedMediaFilter(it) },
            onGeneratePlaylist = {
                val isApple = musicService == fm.corus.android.data.model.MusicService.APPLE_MUSIC
                val hasSoundCloud = posts.any { it.isTrack && it.track.source == fm.corus.android.data.model.TrackSource.SOUNDCLOUD }
                if (isApple || hasSoundCloud) {
                    showPlaylistAlert = true
                } else {
                    viewModel.generateFeedPlaylist()
                }
            },
        )
    }

    val haptics = LocalHapticManager.current
    PullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = {
            // Mirrors iOS FeedView.refreshable haptic.
            haptics.impact(HapticManager.ImpactStyle.LIGHT)
            viewModel.loadFeed(refresh = true)
        },
        modifier = Modifier.fillMaxSize(),
    ) {
        when {
            // Loading skeleton state — show until first load completes, or
            // while a refresh (e.g. filter change) is in flight with no posts yet
            posts.isEmpty() && (!hasLoaded || isLoading || isRefreshing) -> {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    item { header() }
                    items(3) {
                        SkeletonPostCard()
                        if (it < 2) {
                            HorizontalDivider(color = CorusColors.Divider)
                        }
                    }
                }
            }

            // Empty state — only after a load settles with no posts
            posts.isEmpty() && hasLoaded && !isLoading && !isRefreshing -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState()),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    header()
                    Spacer(modifier = Modifier.height(40.dp))

                    // Invite friends section
                    Text(
                        text = stringResource(R.string.feed_empty_invite_title),
                        style = CorusFont.songTitleLarge,
                        color = CorusColors.Text,
                        textAlign = TextAlign.Center,
                    )

                    Spacer(modifier = Modifier.height(CorusSpacing.sm))

                    Text(
                        text = stringResource(R.string.feed_empty_invite_subtitle),
                        style = CorusFont.body,
                        color = CorusColors.Secondary,
                        textAlign = TextAlign.Center,
                    )

                    Spacer(modifier = Modifier.height(CorusSpacing.lg))

                    val inviteShareText = stringResource(R.string.feed_empty_invite_share_text)
                    val inviteChooser = stringResource(R.string.feed_empty_invite_chooser)
                    Button(
                        onClick = {
                            val sendIntent = Intent().apply {
                                action = Intent.ACTION_SEND
                                putExtra(Intent.EXTRA_TEXT, inviteShareText)
                                type = "text/plain"
                            }
                            context.startActivity(Intent.createChooser(sendIntent, inviteChooser))
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = CorusColors.Accent,
                        ),
                        shape = RoundedCornerShape(CorusSpacing.pillCornerRadius),
                    ) {
                        Text(
                            text = stringResource(R.string.feed_empty_invite_button),
                            style = CorusFont.button,
                            color = CorusColors.Background,
                        )
                    }

                    Spacer(modifier = Modifier.height(CorusSpacing.xl))

                    // Curated music bots section
                    if (curatedMusicBots.isNotEmpty()) {
                        DividerSectionHeader(text = stringResource(R.string.feed_empty_curated_music))
                        Spacer(modifier = Modifier.height(CorusSpacing.md))
                        FeedBotGrid(
                            bots = curatedMusicBots.take(2),
                            followedIds = followedBotIds,
                            viewModel = viewModel,
                            onNavigateToUser = onNavigateToUser,
                        )
                        if (curatedMusicBots.size > 2) {
                            Spacer(modifier = Modifier.height(CorusSpacing.sm))
                            TextButton(onClick = { onNavigateToBotList("music") }) {
                                Text(
                                    text = stringResource(R.string.feed_empty_see_all),
                                    style = CorusFont.buttonSmall,
                                    color = CorusColors.Accent,
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(CorusSpacing.lg))
                    }

                    // Curated film bots section
                    if (curatedFilmBots.isNotEmpty()) {
                        DividerSectionHeader(text = stringResource(R.string.feed_empty_curated_film))
                        Spacer(modifier = Modifier.height(CorusSpacing.md))
                        FeedBotGrid(
                            bots = curatedFilmBots.take(2),
                            followedIds = followedBotIds,
                            viewModel = viewModel,
                            onNavigateToUser = onNavigateToUser,
                        )
                        if (curatedFilmBots.size > 2) {
                            Spacer(modifier = Modifier.height(CorusSpacing.sm))
                            TextButton(onClick = { onNavigateToBotList("film") }) {
                                Text(
                                    text = stringResource(R.string.feed_empty_see_all),
                                    style = CorusFont.buttonSmall,
                                    color = CorusColors.Accent,
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(CorusSpacing.lg))
                    }

                    Spacer(modifier = Modifier.height(CorusSpacing.xxl))
                }
            }

            // Posts list
            else -> {
                // Pagination: load more when user scrolls within 3 items of end
                val shouldLoadMore by remember {
                    derivedStateOf {
                        val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
                        val total = listState.layoutInfo.totalItemsCount
                        total > 0 && lastVisible >= total - 3
                    }
                }
                LaunchedEffect(shouldLoadMore, hasMore, isLoading) {
                    if (shouldLoadMore && hasMore && !isLoading) {
                        viewModel.loadFeed()
                    }
                }

                LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                    item { header() }
                    itemsIndexed(
                        posts,
                        key = { _, post -> post.id },
                        contentType = { _, _ -> "post_card" },
                    ) { index, post ->
                        val engagement = engagementStates[post.id]
                        PostCard(
                            post = post,
                            likeCount = engagement?.likeCount ?: post.likeCount,
                            commentCount = engagement?.commentCount ?: post.commentCount,
                            isLiked = engagement?.isLiked ?: post.isLiked,
                            isSaved = engagement?.isSaved ?: false,
                            currentUser = currentUserProfile,
                            isPreviewLoading = loadingTrackId == post.track.id,
                            isPreviewPlaying = nowPlayingState.trackId == post.track.id && nowPlayingState.isPlaying,
                            onLikeTap = { viewModel.toggleLike(post.id) },
                            onSaveTap = { viewModel.toggleSave(post.id) },
                            onUserTap = { onNavigateToUser(post.user) },
                            onPostTap = { onNavigateToPost(post.id) },
                            onPreviewTap = {
                                viewModel.playPreview(post)
                            },
                            onTrailerTap = {
                                post.trailerURL?.let { url ->
                                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                                    context.startActivity(intent)
                                }
                            },
                            onCommentTap = { onNavigateToComments(post.id) },
                            onLikesTap = { onNavigateToLikes(post.id) },
                            onShareTap = { sharePost = post },
                            onMenuTap = { menuPost = post },
                            onFilmPageTap = { onNavigateToFilm(post.movieId ?: "") },
                            onSpotifyTap = {
                                if (post.isMovie) {
                                    filmInfoPost = post
                                } else if (post.track.source == fm.corus.android.data.model.TrackSource.SOUNDCLOUD) {
                                    val permalink = post.track.soundcloudPermalinkUrl
                                    if (!permalink.isNullOrBlank()) {
                                        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(permalink))) }
                                    }
                                } else {
                                    val uri = post.track.spotifyURI
                                    val webUrl = post.track.spotifyWebURL
                                    try {
                                        if (!uri.isNullOrBlank()) {
                                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(uri)))
                                        } else if (!webUrl.isNullOrBlank()) {
                                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(webUrl)))
                                        }
                                    } catch (_: Exception) {
                                        if (!webUrl.isNullOrBlank()) {
                                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(webUrl)))
                                        }
                                    }
                                }
                            },
                            onMentionTap = { username -> onNavigateToUserByUsername(username) },
                            onRepostedFromUserTap = { userId, username ->
                                if (userId != null) onNavigateToUserById(userId)
                                else onNavigateToUserByUsername(username)
                            },
                            onCommentUserTap = { user -> onNavigateToUserById(user.id) },
                            onHashtagTap = { hashtag ->
                                viewModel.analyticsService.logTrendingHashtagTapped(hashtag)
                                onNavigateToHashtag(hashtag)
                            },
                            onSongCountTap = {
                                if (post.isMovie) {
                                    onNavigateToFilm(post.movieId ?: "")
                                } else {
                                    onNavigateToSong(post.track)
                                }
                            },
                            onVoiceNotePlayed = { viewModel.analyticsService.logVoiceNotePlayed() },
                            backCoverFlipState = backCoverStateFor(post.id),
                        )
                        HorizontalDivider(
                            color = CorusColors.Divider,
                            modifier = Modifier.padding(vertical = CorusSpacing.sm),
                        )
                    }

                    if (isLoading && posts.isNotEmpty()) {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(CorusSpacing.lg),
                                contentAlignment = Alignment.Center,
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(24.dp),
                                    color = CorusColors.Accent,
                                    strokeWidth = 2.dp,
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    PostMenuSheets(
        menuPost = menuPost,
        sharePost = sharePost,
        editCaptionPost = editCaptionPost,
        deleteConfirmPost = showDeleteConfirm,
        onMenuPostChange = { menuPost = it },
        onSharePostChange = { sharePost = it },
        onEditCaptionPostChange = { editCaptionPost = it },
        onDeleteConfirmPostChange = { showDeleteConfirm = it },
        actions = viewModel,
        backCoverStateFor = ::backCoverStateFor,
        onNavigateToSong = onNavigateToSong,
        onNavigateToFilm = onNavigateToFilm,
        onRepost = onRepost,
    )

    // ── Film Info Sheet ──
    filmInfoPost?.let { post ->
        FilmInfoSheet(
            post = post,
            onDismiss = { filmInfoPost = null },
            fetchMovieDetails = { movieId -> viewModel.fetchMovieDetails(movieId) },
        )
    }

    // ── Spotify Playlist Confirmation ──
    // Surfaces when (a) the user prefers Apple Music (this is a Spotify-only
    // feature) or (b) the feed contains SoundCloud tracks that will be
    // skipped from the Spotify playlist.
    if (showPlaylistAlert) {
        val hasSoundCloud = posts.any { it.isTrack && it.track.source == fm.corus.android.data.model.TrackSource.SOUNDCLOUD }
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showPlaylistAlert = false },
            title = { androidx.compose.material3.Text("Spotify Feature") },
            text = {
                androidx.compose.material3.Text(
                    if (hasSoundCloud)
                        "Playlist generation creates a Spotify playlist. Any SoundCloud tracks will be skipped."
                    else
                        "Playlist generation creates a Spotify playlist. Would you like to generate it anyway?"
                )
            },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = {
                    showPlaylistAlert = false
                    viewModel.generateFeedPlaylist()
                }) { androidx.compose.material3.Text("Generate Spotify Playlist") }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = { showPlaylistAlert = false }) {
                    androidx.compose.material3.Text("Cancel")
                }
            },
        )
    }

    // ── Club Offer Paywall (playlist limit) ──
    if (showClubOffer) {
        val clubSheetState = androidx.compose.material3.rememberModalBottomSheetState(skipPartiallyExpanded = true)
        androidx.compose.material3.ModalBottomSheet(
            onDismissRequest = { showClubOffer = false },
            sheetState = clubSheetState,
            containerColor = fm.corus.android.ui.theme.CorusColors.Background,
            dragHandle = { androidx.compose.material3.BottomSheetDefaults.DragHandle() },
        ) {
            CorusSystemBars()
            fm.corus.android.ui.screens.subscription.CymbalClubOfferSheet(
                source = fm.corus.android.ui.screens.subscription.PaywallSource.PLAYLIST_LIMIT,
                onDismiss = { showClubOffer = false },
            )
        }
    }
}

/**
 * Divider — text — divider header used in the empty feed bot sections.
 * Matches the iOS "or follow some curated music bots" style.
 */
@Composable
private fun DividerSectionHeader(text: String) {
    Row(
        modifier = Modifier.padding(horizontal = CorusSpacing.xxl),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        HorizontalDivider(modifier = Modifier.weight(1f), color = CorusColors.Divider)
        Text(
            text = text,
            style = CorusFont.caption,
            color = CorusColors.Tertiary,
            modifier = Modifier.padding(horizontal = CorusSpacing.sm),
        )
        HorizontalDivider(modifier = Modifier.weight(1f), color = CorusColors.Divider)
    }
}

/**
 * 2-column grid of bot cards for the empty feed state.
 */
@Composable
private fun FeedBotGrid(
    bots: List<SuggestedUserMatch>,
    followedIds: Set<String>,
    viewModel: FeedViewModel,
    onNavigateToUser: (CymbalUser) -> Unit,
) {
    val rows = bots.chunked(2)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = CorusSpacing.xxl),
        verticalArrangement = Arrangement.spacedBy(CorusSpacing.md),
    ) {
        rows.forEach { rowBots ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(CorusSpacing.md),
            ) {
                rowBots.forEach { match ->
                    TasteMatchCard(
                        match = match,
                        isFollowing = followedIds.contains(match.user.id),
                        onUserTap = { onNavigateToUser(match.user) },
                        onFollowTap = { viewModel.toggleBotFollow(match.user) },
                        modifier = Modifier.weight(1f),
                    )
                }
                if (rowBots.size < 2) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

/**
 * Feed top bar — centered "corus" logo, filter menu on the left,
 * playlist button on the right. Rendered as the first item of the
 * scrolling list so it scrolls away with content (matching iOS).
 */
@Composable
private fun FeedHeader(
    showPlaylistButton: Boolean,
    isGeneratingPlaylist: Boolean,
    feedMediaFilter: MediaType?,
    filterMenuExpanded: Boolean,
    onFilterMenuExpandedChange: (Boolean) -> Unit,
    onSetFilter: (MediaType?) -> Unit,
    onGeneratePlaylist: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = CorusSpacing.sm),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = stringResource(R.string.feed_app_title),
            style = CorusFont.appTitle,
            color = CorusColors.Text,
        )

        if (showPlaylistButton) {
            IconButton(
                onClick = onGeneratePlaylist,
                modifier = Modifier.align(Alignment.CenterEnd),
                enabled = !isGeneratingPlaylist,
            ) {
                if (isGeneratingPlaylist) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = CorusColors.Secondary,
                        strokeWidth = 2.dp,
                    )
                } else {
                    Icon(
                        imageVector = Icons.Filled.QueueMusic,
                        contentDescription = stringResource(R.string.feed_cd_generate_playlist),
                        tint = CorusColors.Secondary,
                        modifier = Modifier.size(22.dp),
                    )
                }
            }
        }

        Box(modifier = Modifier.align(Alignment.CenterStart)) {
            IconButton(onClick = { onFilterMenuExpandedChange(true) }) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.List,
                    contentDescription = stringResource(R.string.feed_cd_filter),
                    tint = if (feedMediaFilter != null) CorusColors.Accent else CorusColors.Secondary,
                )
            }
            DropdownMenu(
                expanded = filterMenuExpanded,
                onDismissRequest = { onFilterMenuExpandedChange(false) },
            ) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.feed_filter_all)) },
                    onClick = {
                        onSetFilter(null)
                        onFilterMenuExpandedChange(false)
                    },
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.feed_filter_music)) },
                    onClick = {
                        onSetFilter(MediaType.TRACK)
                        onFilterMenuExpandedChange(false)
                    },
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.feed_filter_film)) },
                    onClick = {
                        onSetFilter(MediaType.MOVIE)
                        onFilterMenuExpandedChange(false)
                    },
                )
            }
        }
    }
}
