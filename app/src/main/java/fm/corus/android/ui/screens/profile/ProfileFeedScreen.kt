package fm.corus.android.ui.screens.profile

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.pullToRefresh
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.*
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import fm.corus.android.data.model.CymbalPost
import fm.corus.android.data.model.CymbalTrack
import fm.corus.android.domain.HapticManager
import fm.corus.android.domain.PostPlaybackHighlight
import fm.corus.android.ui.LocalHapticManager
import fm.corus.android.ui.components.ImmersiveFrostedBar
import fm.corus.android.ui.components.LocalBottomBarHeight
import fm.corus.android.ui.components.contentHazeSource
import fm.corus.android.ui.components.PostCard
import fm.corus.android.ui.components.PostMenuSheets
import fm.corus.android.ui.components.rememberImmersiveHeaderState
import fm.corus.android.ui.components.ToastManager
import fm.corus.android.ui.screens.feed.FilmInfoSheet
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
    /** Set when [segment] == 4 (hashtag feed). The lowercased tag name. */
    hashtag: String = "",
    /**
     * True when the tab hosting this ProfileFeedScreen is currently
     * selected. Mini-player tap-to-scroll only registers while this is
     * true so a background tab's ProfileFeedScreen doesn't claim a tap
     * that should reach the visible feed instead.
     */
    isContainingTabSelected: Boolean = true,
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
    onShowReposters: (String) -> Unit = {},
    /** Artist/director pages (artist_pages_enabled) — null while the flag is
     *  off, which keeps post-card artist/director names as plain text. */
    onNavigateToArtist: ((fm.corus.android.ui.navigation.ArtistPageRoute) -> Unit)? = null,
    onNavigateToDirector: ((fm.corus.android.ui.navigation.DirectorPageRoute) -> Unit)? = null,
    /** Album page (artist_pages_enabled) — null while the flag is off, which
     *  hides the "…" menu's "Go to Album" row. */
    onNavigateToAlbum: ((fm.corus.android.ui.navigation.AlbumPageRoute) -> Unit)? = null,
) {
    val posts by viewModel.posts.collectAsState()
    val hasMore by viewModel.hasMore.collectAsState()
    val isLoadingMore by viewModel.isLoadingMore.collectAsState()
    val isRefreshing by viewModel.isRefreshing.collectAsState()
    val engagementStates by viewModel.engagementStates.collectAsState()
    val currentUserProfile by viewModel.currentUserProfile.collectAsState()
    val nowPlayingState by viewModel.nowPlayingManager.state.collectAsState()
    val loadingTrackId by viewModel.nowPlayingManager.loadingTrackId.collectAsState()
    val loadingSourcePostId by viewModel.nowPlayingManager.loadingSourcePostId.collectAsState()
    val isResolvingSpotify by viewModel.nowPlayingManager.isResolvingSpotifyFlow.collectAsState()
    val feedFollowsNowPlaying by viewModel.feedFollowsNowPlaying.collectAsState()
    val context = LocalContext.current
    val musicService by viewModel.musicServicePreference.current.collectAsState()
    val scope = rememberCoroutineScope()
    // Resolve-on-tap state for the tappable artist name (subtitle) — shared HUD +
    // miss toast, matching the "…" menu's Go to Artist row.
    var isResolvingSubtitle by remember { mutableStateOf(false) }
    val subtitleArtistNotFound = androidx.compose.ui.res.stringResource(fm.corus.android.R.string.song_detail_artist_not_found)

    var sharePost by remember { mutableStateOf<CymbalPost?>(null) }
    var menuPost by remember { mutableStateOf<CymbalPost?>(null) }
    var filmInfoPost by remember { mutableStateOf<CymbalPost?>(null) }
    var editCaptionPost by remember { mutableStateOf<CymbalPost?>(null) }
    var showDeleteConfirm by remember { mutableStateOf<CymbalPost?>(null) }
    val backCoverStates = remember { mutableMapOf<String, fm.corus.android.ui.components.BackCoverFlipState>() }
    fun backCoverStateFor(postId: String) =
        backCoverStates.getOrPut(postId) { fm.corus.android.ui.components.BackCoverFlipState() }

    val listState = rememberLazyListState()

    // Initialize feed from cache. If the cache was empty (e.g. process death
    // restored this route without the upstream grid populating it), pop back
    // to the profile so the user isn't stranded on a blank screen.
    LaunchedEffect(Unit) {
        if (!viewModel.initFeed(userId, segment, hashtag)) {
            onBack()
        }
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

    // Mini-player tap shortcut: register a scroll handler so a tap on the
    // mini-player while this screen is foreground scrolls to the post here
    // instead of pushing a redundant PostDetail page. Mirrors iOS
    // .profileFeedScrollToPost / ActiveProfileFeedHandle. Only registers
    // while the containing tab is selected — a background tab's
    // ProfileFeedScreen must not claim a tap meant for the visible feed.
    val currentPostsForRouter by rememberUpdatedState(posts)
    val routerScope = scope
    val profileFeedScrollHandler = remember<(String) -> Boolean>(listState) {
        handler@ { postId ->
            val idx = currentPostsForRouter.indexOfFirst { it.id == postId }
            if (idx < 0) return@handler false
            routerScope.launch { listState.animateScrollToItem(index = idx) }
            true
        }
    }
    DisposableEffect(isContainingTabSelected, profileFeedScrollHandler) {
        if (isContainingTabSelected) {
            viewModel.feedScrollRouter.handler = profileFeedScrollHandler
        }
        onDispose {
            if (viewModel.feedScrollRouter.handler === profileFeedScrollHandler) {
                viewModel.feedScrollRouter.handler = null
            }
        }
    }

    // Auto-scroll the feed to the now-playing post on song changes. Mirrors
    // iOS ProfileFeedView.onChange(of: nowPlaying.currentSourcePostId).
    // Skip the first composition (matches iOS .onChange semantics).
    var hasInitializedFollowScroll by remember { mutableStateOf(false) }
    LaunchedEffect(nowPlayingState.sourcePostId) {
        val newPostId = nowPlayingState.sourcePostId
        if (!hasInitializedFollowScroll) {
            hasInitializedFollowScroll = true
            return@LaunchedEffect
        }
        if (newPostId == null) return@LaunchedEffect
        if (!feedFollowsNowPlaying) return@LaunchedEffect
        // Don't yank the feed out from under a finger that's actively
        // scrolling (or a fling that's still decelerating). Checked before
        // we kick off our own animateScrollToItem, so this only reflects
        // user-driven motion.
        if (listState.isScrollInProgress) return@LaunchedEffect
        // Skip when the user just tapped this card to play it.
        val tapMarker = viewModel.nowPlayingManager.lastUserInitiatedSourcePostId
        if (tapMarker == newPostId) {
            viewModel.nowPlayingManager.lastUserInitiatedSourcePostId = null
            return@LaunchedEffect
        }
        val index = posts.indexOfFirst { it.id == newPostId }
        if (index < 0) return@LaunchedEffect
        listState.animateScrollToItem(index = index)
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

    // Immersive frosted nav bar (shared with the artist/album/director/song/film
    // pages): there's no hero here, so the bar is frosted from the first frame —
    // it blurs the feed scrolling beneath it (Haze) and blends up under the status
    // bar. Gated by the same immersive_artist_header_enabled flag (debug-on).
    val immersive = viewModel.remoteConfig.immersiveArtistHeaderEnabled
    val frost = rememberImmersiveHeaderState(immersive)
    val barTitle = if (segment == 4 && hashtag.isNotEmpty()) "#$hashtag" else "@$username"

    Scaffold(
        modifier = frost.scaffoldModifier,
        // Don't let the Scaffold reserve the status-bar inset for its body: that
        // pushes the content (and the frosted bar) back down by the status height,
        // cancelling extendIntoStatusBar's upward shift and leaving the bar sitting
        // below the status strip. The parent tab scaffold already handles insets.
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            // Immersive draws the shared frosted bar over the feed below instead.
            if (!immersive) {
                TopAppBar(
                    title = {
                        Text(barTitle, style = CorusFont.screenTitle, color = CorusColors.Text)
                    },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = androidx.compose.ui.res.stringResource(fm.corus.android.R.string.common_back),
                                tint = CorusColors.Text,
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = CorusColors.Background),
                    windowInsets = WindowInsets(0, 0, 0, 0),
                )
            }
        },
    ) { padding ->
        val haptics = LocalHapticManager.current
        val pullState = rememberPullToRefreshState()
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                // The haze source must stay UNCLIPPED so the feed renders behind the
                // status strip and the frosted bar can blur it there. That's why
                // pull-to-refresh is a modifier here rather than a PullToRefreshBox
                // wrapper — PullToRefreshBox clipToBounds() and would clip the source
                // off the status strip, leaving it a solid (opaque frost) band.
                .then(frost.hazeSourceModifier())
                // Also feed the shared frosted bottom bar so it blurs this feed.
                .contentHazeSource()
                .pullToRefresh(
                    isRefreshing = isRefreshing,
                    state = pullState,
                    onRefresh = {
                        // Mirrors iOS ProfileFeedView.refreshable haptic.
                        haptics.impact(HapticManager.ImpactStyle.LIGHT)
                        viewModel.refresh()
                    },
                ),
            contentPadding = PaddingValues(
                // Immersive: clear the frosted bar so the first post isn't pinned behind it.
                top = if (immersive) frost.contentTopPadding else 0.dp,
                // Clear the frosted bottom bar; content still scrolls under it.
                bottom = LocalBottomBarHeight.current,
            ),
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
                    repostCount = engagement?.repostCount ?: post.repostCount,
                    isLiked = engagement?.isLiked ?: post.isLiked,
                    isSaved = engagement?.isSaved ?: false,
                    saveCount = engagement?.saveCount ?: post.saveCount,
                    saveCountEnabled = viewModel.remoteConfig.saveCountEnabled,
                    currentUser = currentUserProfile,
                    isPreviewLoading = post.isTrack && PostPlaybackHighlight.isAlbumArtLoading(
                        loadingTrackId = loadingTrackId,
                        loadingSourcePostId = loadingSourcePostId,
                        fullSongTrackId = nowPlayingState.trackId,
                        fullSongSourcePostId = nowPlayingState.sourcePostId,
                        isResolvingFullSong = isResolvingSpotify,
                        postTrackId = post.track.id,
                        postId = post.id,
                    ),
                    isPreviewPlaying = post.isTrack && PostPlaybackHighlight.shouldShowPlayingOverlay(
                        activeTrackId = nowPlayingState.trackId,
                        activeSourcePostId = nowPlayingState.sourcePostId,
                        isPlaying = nowPlayingState.isPlaying,
                        isResolvingFullSong = isResolvingSpotify,
                        postTrackId = post.track.id,
                        postId = post.id,
                    ),
                    onLikeTap = { viewModel.toggleLike(post.id) },
                    onSaveTap = { viewModel.toggleSave(post.id) },
                    onUserTap = { onNavigateToUser(post.user.id) },
                    onPostTap = { /* Already viewing post in feed */ },
                    onPreviewTap = { viewModel.playPreview(post) },
                    isFullSongPlaying = PostPlaybackHighlight.shouldHighlight(
                        activeTrackId = nowPlayingState.trackId,
                        activeSourcePostId = nowPlayingState.sourcePostId,
                        playbackActive = viewModel.nowPlayingManager.showsFullSongPlayingOverlay(musicService),
                        postTrackId = post.track.id,
                        postId = post.id,
                    ),
                    isFullSongLoading = PostPlaybackHighlight.shouldHighlight(
                        activeTrackId = nowPlayingState.trackId,
                        activeSourcePostId = nowPlayingState.sourcePostId,
                        playbackActive = isResolvingSpotify,
                        postTrackId = post.track.id,
                        postId = post.id,
                    ),
                    onTrailerTap = {
                        post.trailerURL?.let { url ->
                            viewModel.nowPlayingManager.pause()
                            try { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) } catch (_: Exception) { }
                        }
                    },
                    onCommentTap = { onNavigateToComments(post.id) },
                    onLikesTap = { onNavigateToLikes(post.id) },
                    onLikerTap = { liker -> onNavigateToUser(liker.id) },
                    onRepostTap = { onRepost(post) },
                    onRepostLongPress = if (viewModel.remoteConfig.repostersListEnabled && post.repostCount > 0) {
                        { onShowReposters(post.id) }
                    } else null,
                    onShareTap = { sharePost = post },
                    onMenuTap = { menuPost = post },
                    onFilmPageTap = { onNavigateToFilm(post.movieId ?: "") },
                    onSpotifyTap = {
                        if (post.isMovie) {
                            filmInfoPost = post
                        } else if (post.track.source == fm.corus.android.data.model.TrackSource.SOUNDCLOUD) {
                            // SoundCloud tracks aren't on Spotify — open the SC permalink instead.
                            val permalink = post.track.soundcloudPermalinkUrl
                            if (!permalink.isNullOrBlank()) {
                                runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(permalink))) }
                            }
                        } else if (musicService == fm.corus.android.data.model.MusicService.SPOTIFY) {
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
                        } else {
                            // Apple Music / TIDAL / Deezer: resolve + open (network, cached).
                            scope.launch {
                                val url = viewModel.resolveServiceLinkUrl(post.track)
                                if (!url.isNullOrBlank()) {
                                    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
                                }
                            }
                        }
                    },
                    musicService = musicService,
                    onMentionTap = { mentionUsername -> onNavigateToUserByUsername(mentionUsername) },
                    onRepostedFromUserTap = { userId, username ->
                        if (userId != null) onNavigateToUser(userId)
                        else onNavigateToUserByUsername(username)
                    },
                    onCommentUserTap = { user -> onNavigateToUser(user.id) },
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
                    onSubtitleTap = fm.corus.android.ui.components.postSubtitleTap(
                        context = context,
                        post = post,
                        onNavigateToArtist = onNavigateToArtist,
                        onNavigateToDirector = onNavigateToDirector,
                        scope = scope,
                        resolveArtistId = viewModel::resolveArtistIdForTrack,
                        onArtistNotFound = { ToastManager.show(subtitleArtistNotFound) },
                        onResolvingChange = { isResolvingSubtitle = it },
                    ),
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
        PullToRefreshDefaults.Indicator(
            state = pullState,
            isRefreshing = isRefreshing,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(
                    // Keep the spinner below the frosted bar + status strip.
                    top = if (immersive) frost.contentTopPadding
                    else padding.calculateTopPadding(),
                ),
        )
        if (immersive) {
            ImmersiveFrostedBar(
                hazeState = frost.hazeState,
                title = barTitle,
                onBack = onBack,
                topInset = frost.statusBarPadding,
            )
        }
        }
    }

    fm.corus.android.ui.components.DestinationResolvingHud(isResolvingSubtitle)

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
        musicService = musicService,
        backCoverStateFor = ::backCoverStateFor,
        onNavigateToSong = onNavigateToSong,
        onNavigateToFilm = onNavigateToFilm,
        onRepost = onRepost,
        onPostDeleted = { if (posts.size <= 1) onBack() },
        artistPagesEnabled = onNavigateToArtist != null,
        onNavigateToArtist = onNavigateToArtist,
        onNavigateToAlbum = onNavigateToAlbum,
        onNavigateToDirector = onNavigateToDirector,
    )

    filmInfoPost?.let { post ->
        FilmInfoSheet(
            post = post,
            onDismiss = { filmInfoPost = null },
            fetchMovieDetails = { movieId -> viewModel.fetchMovieDetails(movieId) },
        )
    }
}
