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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Movie
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
import fm.corus.android.data.model.FeedFilter
import fm.corus.android.data.model.MediaType
import fm.corus.android.data.model.SuggestedUserMatch
import fm.corus.android.domain.HapticManager
import fm.corus.android.ui.LocalHapticManager
import fm.corus.android.ui.components.HorizontalPopularUsersRail
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
    val feedFilter by viewModel.feedFilter.collectAsState()
    val followedBotIds by viewModel.followedBotIds.collectAsState()
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
            feedFilter = feedFilter,
            filterMenuExpanded = filterMenuExpanded,
            onFilterMenuExpandedChange = { filterMenuExpanded = it },
            onSetFilter = { viewModel.setFeedFilter(it) },
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
            posts.isEmpty() && hasLoaded && !isLoading && !isRefreshing && feedFilter.newReleasesOnly -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState()),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    header()
                    Spacer(modifier = Modifier.height(60.dp))
                    Icon(
                        imageVector = Icons.Filled.LocalFireDepartment,
                        contentDescription = null,
                        tint = CorusColors.Tertiary,
                        modifier = Modifier.size(36.dp),
                    )
                    Spacer(modifier = Modifier.height(CorusSpacing.md))
                    Text(
                        text = stringResource(R.string.feed_empty_new_releases_title),
                        style = CorusFont.body,
                        color = CorusColors.Secondary,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(modifier = Modifier.height(CorusSpacing.xs))
                    Text(
                        text = stringResource(R.string.feed_empty_new_releases_subtitle),
                        style = CorusFont.caption,
                        color = CorusColors.Tertiary,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = CorusSpacing.xl),
                    )
                    Spacer(modifier = Modifier.height(CorusSpacing.lg))
                    Button(
                        onClick = { viewModel.setFeedFilter(FeedFilter.ALL) },
                        colors = ButtonDefaults.buttonColors(containerColor = CorusColors.Accent),
                        shape = RoundedCornerShape(CorusSpacing.pillCornerRadius),
                    ) {
                        Text(
                            text = stringResource(R.string.feed_empty_new_releases_show_all),
                            style = CorusFont.button,
                            color = CorusColors.Background,
                        )
                    }
                    Spacer(modifier = Modifier.height(CorusSpacing.xxl))
                }
            }

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

                    // Popular on Corus — paginated horizontal rail of real users.
                    // Replaces the old Curated Music/Film Bots empty-state sections.
                    HorizontalPopularUsersRail(
                        excludeIds = emptySet(),
                        isFollowed = { id -> followedBotIds.contains(id) },
                        onUserTap = { user -> onNavigateToUser(user) },
                        onFollowTap = { user -> viewModel.toggleBotFollow(user) },
                    )

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
                            repostCount = engagement?.repostCount ?: post.repostCount,
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
                                post.trailerURL?.takeIf { it.isNotBlank() }?.let { url ->
                                    viewModel.nowPlayingManager.pause()
                                    try {
                                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                                    } catch (_: Exception) { }
                                }
                            },
                            onCommentTap = { onNavigateToComments(post.id) },
                            onLikesTap = { onNavigateToLikes(post.id) },
                            onRepostTap = { onRepost(post) },
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
 * Feed top bar — centered "corus" logo, filter menu on the left,
 * playlist button on the right. Rendered as the first item of the
 * scrolling list so it scrolls away with content (matching iOS).
 */
@Composable
private fun FeedHeader(
    showPlaylistButton: Boolean,
    isGeneratingPlaylist: Boolean,
    feedFilter: FeedFilter,
    filterMenuExpanded: Boolean,
    onFilterMenuExpandedChange: (Boolean) -> Unit,
    onSetFilter: (FeedFilter) -> Unit,
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

        Row(
            modifier = Modifier.align(Alignment.CenterStart),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box {
                IconButton(onClick = { onFilterMenuExpandedChange(true) }) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.List,
                        contentDescription = stringResource(R.string.feed_cd_filter),
                        tint = if (!feedFilter.isAll) CorusColors.Accent else CorusColors.Secondary,
                    )
                }
                DropdownMenu(
                    expanded = filterMenuExpanded,
                    onDismissRequest = { onFilterMenuExpandedChange(false) },
                ) {
                    val activeCheckmark: @Composable () -> Unit = {
                        Icon(
                            imageVector = Icons.Filled.Check,
                            contentDescription = stringResource(R.string.common_selected),
                            tint = CorusColors.Accent,
                        )
                    }
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.feed_filter_all)) },
                        trailingIcon = if (feedFilter == FeedFilter.ALL) activeCheckmark else null,
                        onClick = {
                            onSetFilter(FeedFilter.ALL)
                            onFilterMenuExpandedChange(false)
                        },
                    )
                    HorizontalDivider()
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.feed_filter_music)) },
                        trailingIcon = if (feedFilter == FeedFilter.MUSIC) activeCheckmark else null,
                        onClick = {
                            onSetFilter(FeedFilter.MUSIC)
                            onFilterMenuExpandedChange(false)
                        },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.feed_filter_film)) },
                        trailingIcon = if (feedFilter == FeedFilter.FILM) activeCheckmark else null,
                        onClick = {
                            onSetFilter(FeedFilter.FILM)
                            onFilterMenuExpandedChange(false)
                        },
                    )
                    HorizontalDivider()
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.feed_filter_music_new_releases)) },
                        trailingIcon = if (feedFilter == FeedFilter.MUSIC_NEW_RELEASES) activeCheckmark else null,
                        onClick = {
                            onSetFilter(FeedFilter.MUSIC_NEW_RELEASES)
                            onFilterMenuExpandedChange(false)
                        },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.feed_filter_film_new_releases)) },
                        trailingIcon = if (feedFilter == FeedFilter.FILM_NEW_RELEASES) activeCheckmark else null,
                        onClick = {
                            onSetFilter(FeedFilter.FILM_NEW_RELEASES)
                            onFilterMenuExpandedChange(false)
                        },
                    )
                }
            }
            // Indicator icon next to the filter button. Same purple as the
            // existing NEW RELEASE badge for the new-releases filters; accent
            // blue for the plain media-type filters.
            when (feedFilter) {
                FeedFilter.MUSIC -> Icon(
                    imageVector = Icons.Filled.MusicNote,
                    contentDescription = null,
                    tint = CorusColors.Accent,
                    modifier = Modifier.size(20.dp),
                )
                FeedFilter.FILM -> Icon(
                    imageVector = Icons.Filled.Movie,
                    contentDescription = null,
                    tint = CorusColors.Accent,
                    modifier = Modifier.size(20.dp),
                )
                FeedFilter.MUSIC_NEW_RELEASES, FeedFilter.FILM_NEW_RELEASES -> Icon(
                    imageVector = Icons.Filled.LocalFireDepartment,
                    contentDescription = null,
                    tint = androidx.compose.ui.graphics.Color(0.62f, 0.35f, 0.95f),
                    modifier = Modifier.size(20.dp),
                )
                FeedFilter.ALL -> {}
            }
        }
    }
}
