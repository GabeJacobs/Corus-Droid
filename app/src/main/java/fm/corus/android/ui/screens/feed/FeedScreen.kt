package fm.corus.android.ui.screens.feed

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import fm.corus.android.data.model.CymbalPost
import fm.corus.android.data.model.CymbalTrack
import fm.corus.android.data.model.CymbalUser
import fm.corus.android.data.model.MediaType
import fm.corus.android.data.model.SuggestedUserMatch
import fm.corus.android.ui.components.PostActionMenu
import fm.corus.android.ui.components.PostCard
import fm.corus.android.ui.components.SharePostSheet
import fm.corus.android.ui.components.SkeletonPostCard
import fm.corus.android.ui.components.TasteMatchCard
import fm.corus.android.ui.components.ToastManager
import fm.corus.android.ui.theme.CorusColors
import fm.corus.android.ui.theme.CorusFont
import fm.corus.android.ui.theme.CorusSpacing

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FeedScreen(
    viewModel: FeedViewModel = hiltViewModel(),
    scrollToTopTrigger: Int = 0,
    onNavigateToPost: (String) -> Unit = {},
    onNavigateToUser: (CymbalUser) -> Unit = {},
    onNavigateToUserByUsername: (String) -> Unit = {},
    onNavigateToComments: (String) -> Unit = {},
    onNavigateToLikes: (String) -> Unit = {},
    onNavigateToHashtag: (String) -> Unit = {},
    onNavigateToSong: (CymbalTrack) -> Unit = {},
    onNavigateToFilm: (String) -> Unit = {},
    onNavigateToBotList: (String?) -> Unit = {},
) {
    val posts by viewModel.filteredPosts.collectAsState()
    val allPosts by viewModel.posts.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val isGeneratingPlaylist by viewModel.nowPlayingManager.isGeneratingPlaylist.collectAsState()
    val playlistError by viewModel.nowPlayingManager.playlistError.collectAsState()

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
    val isBotsLoading by viewModel.isBotsLoading.collectAsState()
    val nowPlayingState by viewModel.nowPlayingManager.state.collectAsState()
    val loadingTrackId by viewModel.nowPlayingManager.loadingTrackId.collectAsState()
    val context = LocalContext.current
    var filterMenuExpanded by remember { mutableStateOf(false) }
    var sharePost by remember { mutableStateOf<CymbalPost?>(null) }
    var menuPost by remember { mutableStateOf<CymbalPost?>(null) }
    var editCaptionPost by remember { mutableStateOf<CymbalPost?>(null) }
    var showDeleteConfirm by remember { mutableStateOf<CymbalPost?>(null) }
    var filmInfoPost by remember { mutableStateOf<CymbalPost?>(null) }

    LaunchedEffect(Unit) {
        if (allPosts.isEmpty()) {
            viewModel.loadFeed()
        }
    }

    // Pagination handled per-item via onAppear (see itemsIndexed below)

    Column(modifier = Modifier.fillMaxSize()) {
        // Header — centered "corus" logo with filter menu on left, matching iOS ZStack
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = CorusSpacing.sm),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "corus",
                style = CorusFont.appTitle,
                color = CorusColors.Text,
            )

            // Music note / playlist button on the right (hidden when filter is Movie)
            if (posts.isNotEmpty() && feedMediaFilter != MediaType.MOVIE) {
                IconButton(
                    onClick = { viewModel.generateFeedPlaylist() },
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
                            contentDescription = "Generate feed playlist",
                            tint = CorusColors.Secondary,
                            modifier = Modifier.size(22.dp),
                        )
                    }
                }
            }

            // Filter menu on the left
            Box(
                modifier = Modifier.align(Alignment.CenterStart),
            ) {
                IconButton(onClick = { filterMenuExpanded = true }) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.List,
                        contentDescription = "Filter",
                        tint = if (feedMediaFilter != null) CorusColors.Accent else CorusColors.Secondary,
                    )
                }
                DropdownMenu(
                    expanded = filterMenuExpanded,
                    onDismissRequest = { filterMenuExpanded = false },
                ) {
                    DropdownMenuItem(
                        text = { Text("All") },
                        onClick = {
                            viewModel.setFeedMediaFilter(null)
                            filterMenuExpanded = false
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("Music") },
                        onClick = {
                            viewModel.setFeedMediaFilter(MediaType.TRACK)
                            filterMenuExpanded = false
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("Film") },
                        onClick = {
                            viewModel.setFeedMediaFilter(MediaType.MOVIE)
                            filterMenuExpanded = false
                        },
                    )
                }
            }
        }

        // Posts start directly below header — no divider (matching iOS)
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

        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = { viewModel.loadFeed(refresh = true) },
            modifier = Modifier.fillMaxSize(),
        ) {
            when {
                // Loading skeleton state — show until first load completes
                posts.isEmpty() && (!hasLoaded || isLoading) -> {
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        items(3) {
                            SkeletonPostCard()
                            if (it < 2) {
                                HorizontalDivider(color = CorusColors.Divider)
                            }
                        }
                    }
                }

                // Empty state — only after first load completes with no posts
                posts.isEmpty() && hasLoaded && !isLoading -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.padding(horizontal = CorusSpacing.xxl),
                        ) {
                            Text(
                                text = "corus is fun with\njust a few friends",
                                style = CorusFont.songTitleLarge,
                                color = CorusColors.Text,
                                textAlign = TextAlign.Center,
                            )

                            Spacer(modifier = Modifier.height(CorusSpacing.sm))

                            Text(
                                text = "know someone with good taste?",
                                style = CorusFont.body,
                                color = CorusColors.Secondary,
                                textAlign = TextAlign.Center,
                            )

                            Spacer(modifier = Modifier.height(CorusSpacing.lg))

                            Button(
                                onClick = {
                                    val sendIntent = Intent().apply {
                                        action = Intent.ACTION_SEND
                                        putExtra(Intent.EXTRA_TEXT, "Check out Corus — share your music & movie taste! https://corus.fm")
                                        type = "text/plain"
                                    }
                                    context.startActivity(Intent.createChooser(sendIntent, "Invite Friends"))
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = CorusColors.Accent,
                                ),
                                shape = RoundedCornerShape(CorusSpacing.pillCornerRadius),
                            ) {
                                Text(
                                    text = "invite friends",
                                    style = CorusFont.button,
                                    color = CorusColors.Background,
                                )
                            }
                        }
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
                                onHashtagTap = { hashtag -> onNavigateToHashtag(hashtag) },
                                onSongCountTap = {
                                    if (post.isMovie) {
                                        onNavigateToFilm(post.movieId ?: "")
                                    } else {
                                        onNavigateToSong(post.track)
                                    }
                                },
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
    }

    // ── Share Post Bottom Sheet ──
    sharePost?.let { post ->
        val shareSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)
        val shareSearchResults by viewModel.shareSearchResults.collectAsState()
        val recentShareContacts by viewModel.recentShareContacts.collectAsState()
        val isShareSearching by viewModel.isShareSearching.collectAsState()
        val isLoadingShareContacts by viewModel.isLoadingShareContacts.collectAsState()

        LaunchedEffect(Unit) {
            viewModel.loadRecentShareContacts()
        }

        ModalBottomSheet(
            onDismissRequest = { sharePost = null },
            sheetState = shareSheetState,
            containerColor = CorusColors.Background,
            dragHandle = null,
            shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
        ) {
            SharePostSheet(
                post = post,
                recentContacts = recentShareContacts,
                searchResults = shareSearchResults,
                isSearching = isShareSearching,
                isLoadingContacts = isLoadingShareContacts,
                instagramShareEnabled = viewModel.remoteConfig.instagramShareEnabled,
                sheetState = shareSheetState,
                onSearchQueryChange = { query -> viewModel.searchShareUsers(query) },
                onSendToUser = { userId, message ->
                    viewModel.sendPostToUser(userId, post, message)
                    ToastManager.show("Post sent!")
                    sharePost = null
                },
                onRepost = {
                    viewModel.repostPost(post)
                    ToastManager.show("Reposted!")
                    sharePost = null
                },
                onDismiss = { sharePost = null },
                onAnalyticsLog = { method ->
                    viewModel.analyticsService.logPostShared(
                        postId = post.id,
                        mediaType = if (post.isMovie) "movie" else "track",
                        method = method,
                    )
                },
            )
        }
    }

    // ── Post Context Menu (full menu matching iOS) ──
    menuPost?.let { post ->
        val menuSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        val isOwn = viewModel.isOwnPost(post)

        ModalBottomSheet(
            onDismissRequest = { menuPost = null },
            sheetState = menuSheetState,
            containerColor = CorusColors.Background,
            dragHandle = null,
            shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
        ) {
            PostActionMenu(
                post = post,
                isMine = isOwn,
                onDismiss = { menuPost = null },
                onViewSongPage = { onNavigateToSong(post.track) },
                onViewFilmPage = { onNavigateToFilm(post.movieId ?: "") },
                onRepost = {
                    viewModel.repostPost(post)
                },
                onSharePost = { sharePost = post },
                onCopyLink = {
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    clipboard.setPrimaryClip(ClipData.newPlainText("Post Link", "https://corus.fm/post/${post.id}"))
                    ToastManager.show("Link copied")
                },
                onEditCaption = { editCaptionPost = post },
                onDeletePost = { showDeleteConfirm = post },
                onReportPost = {
                    viewModel.reportPost(post.id, post.user.id)
                },
                onBlockUser = {
                    viewModel.blockUser(post.user.id)
                },
            )
        }
    }

    // ── Delete Confirmation Dialog ──
    showDeleteConfirm?.let { post ->
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = null },
            title = { Text("Delete Post", style = CorusFont.songTitle, color = CorusColors.Text) },
            text = { Text("Are you sure you want to delete this post? This cannot be undone.", style = CorusFont.body, color = CorusColors.Secondary) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deletePost(post.id)
                    showDeleteConfirm = null
                }) {
                    Text("Delete", style = CorusFont.button, color = CorusColors.Error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = null }) {
                    Text("Cancel", style = CorusFont.button, color = CorusColors.Text)
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
            onDismissRequest = { editCaptionPost = null },
            sheetState = editSheetState,
            containerColor = CorusColors.Background,
            dragHandle = null,
            shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
        ) {
            EditCaptionSheet(
                postId = post.id,
                initialCaption = post.caption.orEmpty(),
                albumArtURL = post.displayImageURL,
                onDismiss = { editCaptionPost = null },
                onSaved = { newCaption ->
                    editCaptionPost = null
                    ToastManager.show("Caption updated")
                },
            )
        }
    }

    // ── Film Info Sheet ──
    filmInfoPost?.let { post ->
        FilmInfoSheet(
            post = post,
            onDismiss = { filmInfoPost = null },
            fetchMovieDetails = { movieId -> viewModel.fetchMovieDetails(movieId) },
        )
    }
}
