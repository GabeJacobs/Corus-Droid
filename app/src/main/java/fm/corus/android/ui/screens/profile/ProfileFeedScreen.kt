package fm.corus.android.ui.screens.profile

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import fm.corus.android.data.model.CymbalPost
import fm.corus.android.data.model.CymbalTrack
import fm.corus.android.ui.components.PostActionMenu
import fm.corus.android.ui.components.PostCard
import fm.corus.android.ui.components.launchBackCoverFlip
import fm.corus.android.ui.components.SharePostSheet
import fm.corus.android.ui.components.ToastManager
import fm.corus.android.ui.screens.feed.EditCaptionSheet
import fm.corus.android.ui.theme.CorusColors
import fm.corus.android.ui.theme.CorusFont
import fm.corus.android.ui.theme.CorusSpacing

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileFeedScreen(
    userId: String,
    username: String,
    segment: Int,
    initialPostId: String,
    viewModel: ProfileFeedViewModel = hiltViewModel(),
    onBack: () -> Unit = {},
    onNavigateToUser: (String) -> Unit = {},
    onNavigateToUserByUsername: (String) -> Unit = {},
    onNavigateToComments: (String) -> Unit = {},
    onNavigateToLikes: (String) -> Unit = {},
    onNavigateToHashtag: (String) -> Unit = {},
    onNavigateToSong: (CymbalTrack) -> Unit = {},
    onNavigateToFilm: (String) -> Unit = {},
    onRepost: (CymbalPost) -> Unit = {},
) {
    val posts by viewModel.posts.collectAsState()
    val hasMore by viewModel.hasMore.collectAsState()
    val isLoadingMore by viewModel.isLoadingMore.collectAsState()
    val engagementStates by viewModel.engagementStates.collectAsState()
    val currentUserProfile by viewModel.currentUserProfile.collectAsState()
    val nowPlayingState by viewModel.nowPlayingManager.state.collectAsState()
    val loadingTrackId by viewModel.nowPlayingManager.loadingTrackId.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var sharePost by remember { mutableStateOf<CymbalPost?>(null) }
    var menuPost by remember { mutableStateOf<CymbalPost?>(null) }
    var editCaptionPost by remember { mutableStateOf<CymbalPost?>(null) }
    var showDeleteConfirm by remember { mutableStateOf<CymbalPost?>(null) }
    val backCoverStates = remember { mutableMapOf<String, fm.corus.android.ui.components.BackCoverFlipState>() }
    fun backCoverStateFor(postId: String) =
        backCoverStates.getOrPut(postId) { fm.corus.android.ui.components.BackCoverFlipState() }

    val listState = rememberLazyListState()

    // Initialize feed from cache
    LaunchedEffect(Unit) {
        viewModel.initFeed(userId, segment)
    }

    // Scroll to the tapped post once posts are loaded
    var hasScrolledToInitial by remember { mutableStateOf(false) }
    LaunchedEffect(posts) {
        if (!hasScrolledToInitial && posts.isNotEmpty()) {
            val index = posts.indexOfFirst { it.id == initialPostId }
            if (index >= 0) {
                listState.scrollToItem(index)
            }
            hasScrolledToInitial = true
        }
    }

    // Pagination: load more when within 3 items of end
    val shouldLoadMore by remember {
        derivedStateOf {
            val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            val total = listState.layoutInfo.totalItemsCount
            total > 0 && lastVisible >= total - 3
        }
    }
    LaunchedEffect(shouldLoadMore, hasMore, isLoadingMore) {
        if (shouldLoadMore && hasMore && !isLoadingMore) {
            viewModel.loadMore()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text("@$username", style = CorusFont.screenTitle, color = CorusColors.Text)
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = CorusColors.Text,
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White),
                windowInsets = WindowInsets(0, 0, 0, 0),
            )
        },
    ) { padding ->
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .padding(top = padding.calculateTopPadding()),
            contentPadding = PaddingValues(bottom = padding.calculateBottomPadding()),
        ) {
            itemsIndexed(
                posts,
                key = { _, post -> post.id },
                contentType = { _, _ -> "post_card" },
            ) { _, post ->
                val engagement = engagementStates[post.id]
                PostCard(
                    post = post,
                    likeCount = engagement?.likeCount ?: post.likeCount,
                    commentCount = engagement?.commentCount ?: post.commentCount,
                    isLiked = engagement?.isLiked ?: post.isLiked,
                    isSaved = engagement?.isSaved ?: false,
                    currentUser = currentUserProfile,
                    isPreviewLoading = post.isTrack && loadingTrackId == post.track.id,
                    isPreviewPlaying = post.isTrack && nowPlayingState.trackId == post.track.id && nowPlayingState.isPlaying,
                    onLikeTap = { viewModel.toggleLike(post.id) },
                    onSaveTap = { viewModel.toggleSave(post.id) },
                    onUserTap = { onNavigateToUser(post.user.id) },
                    onPostTap = { /* Already viewing post in feed */ },
                    onPreviewTap = { viewModel.playPreview(post) },
                    onTrailerTap = {
                        post.trailerURL?.let { url ->
                            try { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) } catch (_: Exception) { }
                        }
                    },
                    onCommentTap = { onNavigateToComments(post.id) },
                    onLikesTap = { onNavigateToLikes(post.id) },
                    onShareTap = { sharePost = post },
                    onMenuTap = { menuPost = post },
                    onFilmPageTap = { onNavigateToFilm(post.movieId ?: "") },
                    onSpotifyTap = {
                        if (post.isMovie) {
                            onNavigateToFilm(post.movieId ?: "")
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
                    onMentionTap = { mentionUsername -> onNavigateToUserByUsername(mentionUsername) },
                    onRepostedFromUserTap = { userId, username ->
                        if (userId != null) onNavigateToUser(userId)
                        else onNavigateToUserByUsername(username)
                    },
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

            if (isLoadingMore) {
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
            BackHandler { sharePost = null }
            SharePostSheet(
                post = post,
                recentContacts = recentShareContacts,
                searchResults = shareSearchResults,
                isSearching = isShareSearching,
                isLoadingContacts = isLoadingShareContacts,
                instagramShareEnabled = false,
                sheetState = shareSheetState,
                onSearchQueryChange = { query -> viewModel.searchShareUsers(query) },
                onSendToUser = { targetUserId, message ->
                    viewModel.sendPostToUser(targetUserId, post, message)
                    ToastManager.show("Post sent!")
                    sharePost = null
                },
                onRepost = {
                    sharePost = null
                    onRepost(post)
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

    // ── Post Context Menu ──
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
            BackHandler { menuPost = null }
            PostActionMenu(
                post = post,
                isMine = isOwn,
                onDismiss = { menuPost = null },
                onViewSongPage = { onNavigateToSong(post.track) },
                onViewFilmPage = { onNavigateToFilm(post.movieId ?: "") },
                showBackCoverOption = viewModel.remoteConfig.vinylFlipEnabled,
                isBackCoverFlipped = backCoverStateFor(post.id).isFlipped,
                onViewBackCover = {
                    val state = backCoverStateFor(post.id)
                    if (state.isFlipped) {
                        state.flipBack()
                    } else {
                        scope.launchBackCoverFlip(state, post.id) { viewModel.fetchBackCover(it) }
                    }
                },
                onRepost = { onRepost(post) },
                onSharePost = { sharePost = post },
                onCopyLink = {
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    clipboard.setPrimaryClip(ClipData.newPlainText("Post Link", "https://corus.fm/post/${post.id}"))
                    ToastManager.show("Link copied")
                },
                onEditCaption = { editCaptionPost = post },
                onDeletePost = { showDeleteConfirm = post },
                onReportPost = { viewModel.reportPost(post.id, post.user.id) },
                onBlockUser = { viewModel.blockUser(post.user.id) },
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
                    if (posts.size <= 1) onBack()
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
                onSaved = { _ ->
                    editCaptionPost = null
                    ToastManager.show("Caption updated")
                },
            )
        }
    }

}
