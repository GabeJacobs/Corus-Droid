package fm.corus.android.ui.screens.feed

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.Crossfade
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.pullToRefresh
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.text.ClickableText
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.content.Intent
import android.net.Uri
import androidx.hilt.navigation.compose.hiltViewModel
import coil3.compose.AsyncImage
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import fm.corus.android.data.model.CymbalComment
import fm.corus.android.data.model.CymbalTrack
import fm.corus.android.data.model.CymbalPost
import fm.corus.android.data.model.TrackSource
import fm.corus.android.ui.components.AlbumArtPlaybackOverlayGlyph
import fm.corus.android.ui.components.SoundCloudAdaptiveLogo
import fm.corus.android.ui.components.LocalBottomBarHeight
import fm.corus.android.ui.components.contentHazeSource
import fm.corus.android.domain.HapticManager
import fm.corus.android.domain.PostPlaybackHighlight
import fm.corus.android.domain.TrailerPlaybackCoordinator
import fm.corus.android.R
import fm.corus.android.ui.LocalHapticManager
import fm.corus.android.ui.components.CorusHeaderIconButton
import fm.corus.android.ui.components.ImmersiveFrostedBar
import fm.corus.android.ui.components.InlineYouTubePlayer
import fm.corus.android.ui.components.rememberImmersiveHeaderState
import fm.corus.android.ui.components.youTubeVideoID
import fm.corus.android.ui.components.LikedBySection
import fm.corus.android.ui.components.PostMenuSheets
import fm.corus.android.ui.components.launchBackCoverFlip
import fm.corus.android.ui.components.rememberBackCoverFlipState
import fm.corus.android.ui.components.VennDiagramIcon
import fm.corus.android.ui.components.UserAvatarView
import fm.corus.android.ui.components.UsernameWithFlair
import fm.corus.android.ui.components.VoiceNotePlayerView
import fm.corus.android.ui.components.buildMentionAnnotatedString
import fm.corus.android.ui.theme.CorusColors
import fm.corus.android.ui.theme.CorusFont
import fm.corus.android.ui.theme.CorusSpacing
import fm.corus.android.ui.theme.NunitoFamily
import fm.corus.android.ui.util.DateUtils

/** Grace period before the album-art loading spinner is revealed. Fast / cached
 *  previews start playing before this elapses, so they never flash a spinner —
 *  the overlay fades straight to the pause icon. Tune here if the spinner still
 *  peeks through on quick loads (raise) or takes too long to appear (lower). */
private const val PREVIEW_SPINNER_DELAY_MS = 300L

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PostDetailScreen(
    postId: String,
    viewModel: PostDetailViewModel = hiltViewModel(),
    onBack: () -> Unit = {},
    onNavigateToUser: (String) -> Unit = {},
    onNavigateToUserByUsername: (String) -> Unit = {},
    onNavigateToComments: (String) -> Unit = {},
    onNavigateToLikes: (String) -> Unit = {},
    onNavigateToSong: (CymbalTrack) -> Unit = {},
    onNavigateToFilm: (String) -> Unit = {},
    onNavigateToHashtag: (String) -> Unit = {},
    onRepost: (CymbalPost) -> Unit = {},
    onShowReposters: (String) -> Unit = {},
    /** Artist/director pages (artist_pages_enabled) — null while the flag is
     *  off, which keeps the artist/director subtitle as plain text. */
    onNavigateToArtist: ((fm.corus.android.ui.navigation.ArtistPageRoute) -> Unit)? = null,
    onNavigateToDirector: ((fm.corus.android.ui.navigation.DirectorPageRoute) -> Unit)? = null,
    /** Album page (artist_pages_enabled) — null while the flag is off, which
     *  hides the "…" menu's "Go to Album" row. */
    onNavigateToAlbum: ((fm.corus.android.ui.navigation.AlbumPageRoute) -> Unit)? = null,
) {
    val post by viewModel.post.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val isRefreshing by viewModel.isRefreshing.collectAsState()
    val comments by viewModel.comments.collectAsState()
    val engagementStates by viewModel.engagementStates.collectAsState()
    val currentUserProfile by viewModel.currentUserProfile.collectAsState()
    val nowPlayingState by viewModel.nowPlayingManager.state.collectAsState()
    val loadingTrackId by viewModel.nowPlayingManager.loadingTrackId.collectAsState()
    val loadingSourcePostId by viewModel.nowPlayingManager.loadingSourcePostId.collectAsState()
    val isResolvingSpotify by viewModel.nowPlayingManager.isResolvingSpotifyFlow.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val musicService by viewModel.musicServicePreference.current.collectAsState()
    // Resolve-on-tap state for the tappable artist name (subtitle) — shared HUD +
    // miss toast, matching the "…" menu's Go to Artist row.
    var isResolvingSubtitle by remember { mutableStateOf(false) }
    val subtitleArtistNotFound = stringResource(R.string.song_detail_artist_not_found)
    var menuPost by remember { mutableStateOf<CymbalPost?>(null) }
    var sharePost by remember { mutableStateOf<CymbalPost?>(null) }
    var editCaptionPost by remember { mutableStateOf<CymbalPost?>(null) }
    var deleteConfirmPost by remember { mutableStateOf<CymbalPost?>(null) }
    val backCoverFlipState = rememberBackCoverFlipState()

    LaunchedEffect(postId) {
        viewModel.loadPost(postId)
    }

    val immersive = viewModel.remoteConfig.immersiveArtistHeaderEnabled
    val frost = rememberImmersiveHeaderState(immersive)

    Scaffold(
        modifier = frost.scaffoldModifier,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            // Immersive draws the shared frosted bar over the post below instead.
            if (!immersive) {
                TopAppBar(
                    title = {
                        Text(stringResource(R.string.feed_screen_title_Corus), style = CorusFont.screenTitle, color = CorusColors.Text)
                    },
                    navigationIcon = {
                        CorusHeaderIconButton(
                            onClick = onBack,
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.feed_cd_back),
                        )
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = CorusColors.Background),
                    windowInsets = WindowInsets(0, 0, 0, 0),
                )
            }
        },
    ) { padding ->
        val pullState = rememberPullToRefreshState()
        Box(modifier = Modifier.fillMaxSize()) {
        when {
            isLoading && post == null -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(color = CorusColors.Accent)
                }
            }
            post == null -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(stringResource(R.string.post_detail_not_found), style = CorusFont.body, color = CorusColors.Secondary)
                }
            }
            else -> {
                val currentPost = post!!
                val engagement = engagementStates[currentPost.id]
                val likeCount = engagement?.likeCount ?: currentPost.likeCount
                val commentCount = engagement?.commentCount ?: currentPost.commentCount
                val isLiked = engagement?.isLiked ?: currentPost.isLiked
                val isSaved = engagement?.isSaved ?: false
                val saveCount = engagement?.saveCount ?: currentPost.saveCount

                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .then(frost.hazeSourceModifier())
                        .contentHazeSource()
                        .pullToRefresh(
                            isRefreshing = isRefreshing,
                            state = pullState,
                            onRefresh = { viewModel.refresh(postId) },
                        ),
                    contentPadding = PaddingValues(
                        top = if (immersive) frost.contentTopPadding else padding.calculateTopPadding(),
                        bottom = padding.calculateBottomPadding() + LocalBottomBarHeight.current,
                    ),
                ) {
                    // Post header
                    item {
                        PostDetailHeader(
                            post = currentPost,
                            onUserTap = { onNavigateToUser(currentPost.user.id) },
                            onRepostedFromUserTap = { userId, username ->
                                if (userId != null) onNavigateToUser(userId)
                                else onNavigateToUserByUsername(username)
                            },
                            onMenuTap = { menuPost = currentPost },
                        )
                    }

                    // Album art with double-tap to like
                    item {
                        val haptics = LocalHapticManager.current
                        PostDetailAlbumArt(
                            post = currentPost,
                            isPreviewLoading = currentPost.isTrack && PostPlaybackHighlight.isAlbumArtLoading(
                                loadingTrackId = loadingTrackId,
                                loadingSourcePostId = loadingSourcePostId,
                                fullSongTrackId = nowPlayingState.trackId,
                                fullSongSourcePostId = nowPlayingState.sourcePostId,
                                isResolvingFullSong = isResolvingSpotify,
                                postTrackId = currentPost.track.id,
                                postId = currentPost.id,
                            ),
                            isPreviewPlaying = currentPost.isTrack && PostPlaybackHighlight.shouldShowPlayingOverlay(
                                activeTrackId = nowPlayingState.trackId,
                                activeSourcePostId = nowPlayingState.sourcePostId,
                                isPlaying = nowPlayingState.isPlaying,
                                isResolvingFullSong = isResolvingSpotify,
                                postTrackId = currentPost.track.id,
                                postId = currentPost.id,
                            ),
                            flipState = backCoverFlipState,
                            onDoubleTap = {
                                // Mirrors iOS PostDetailView.doubleTapLike haptic.
                                haptics.impact(HapticManager.ImpactStyle.MEDIUM)
                                if (!isLiked) viewModel.toggleLike(currentPost.id)
                            },
                            onSongTap = {
                                if (currentPost.isMovie) {
                                    currentPost.movieId?.let { onNavigateToFilm(it) }
                                } else {
                                    viewModel.playPreview(currentPost)
                                }
                            },
                        )
                    }

                    // Song info row
                    item {
                        PostDetailSongInfo(
                            post = currentPost,
                            onSongTap = {
                                if (currentPost.isMovie) {
                                    currentPost.movieId?.let { onNavigateToFilm(it) }
                                } else {
                                    onNavigateToSong(currentPost.track)
                                }
                            },
                            onSubtitleTap = fm.corus.android.ui.components.postSubtitleTap(
                                post = currentPost,
                                onNavigateToArtist = onNavigateToArtist,
                                onNavigateToDirector = onNavigateToDirector,
                                scope = scope,
                                resolveArtistId = viewModel::resolveArtistIdForTrack,
                                onArtistNotFound = { fm.corus.android.ui.components.ToastManager.show(subtitleArtistNotFound) },
                                onResolvingChange = { isResolvingSubtitle = it },
                            ),
                            onSpotifyTap = {
                                if (currentPost.track.source == fm.corus.android.data.model.TrackSource.SOUNDCLOUD) {
                                    val permalink = currentPost.track.soundcloudPermalinkUrl
                                    if (!permalink.isNullOrBlank()) {
                                        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(permalink))) }
                                    }
                                } else if (currentPost.track.source == fm.corus.android.data.model.TrackSource.APPLEMUSIC) {
                                    val url = currentPost.track.appleMusicURL
                                    if (!url.isNullOrBlank()) {
                                        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
                                    }
                                } else if (musicService == fm.corus.android.data.model.MusicService.SPOTIFY) {
                                    val spotifyUri = currentPost.track.spotifyURI
                                    val spotifyWeb = currentPost.track.spotifyWebURL
                                    val uri = spotifyUri.ifBlank { spotifyWeb }
                                    if (uri.isNotBlank()) {
                                        try { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(uri))) } catch (_: Exception) { }
                                    }
                                } else {
                                    // Apple Music / TIDAL / Deezer: resolve catalog URL
                                    // via backend (network, cached), then open. The
                                    // global overlay (MainTabScreen) shows the spinner.
                                    scope.launch {
                                        val url = viewModel.resolveServiceLinkUrl(currentPost.track)
                                        if (!url.isNullOrBlank()) {
                                            runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
                                        }
                                    }
                                }
                            },
                            musicService = musicService,
                            onTrailerTap = {
                                currentPost.trailerURL?.let { url ->
                                    viewModel.nowPlayingManager.pause()
                                    try { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) } catch (_: Exception) { }
                                }
                            },
                        )
                    }

                    // Engagement row
                    item {
                        PostDetailEngagementRow(
                            likeCount = likeCount,
                            commentCount = commentCount,
                            repostCount = engagement?.repostCount ?: currentPost.repostCount,
                            isLiked = isLiked,
                            isSaved = isSaved,
                            saveCount = saveCount,
                            saveCountEnabled = viewModel.remoteConfig.saveCountEnabled,
                            trackPostCount = currentPost.trackPostCount ?: 0,
                            onLikeTap = { viewModel.toggleLike(currentPost.id) },
                            onCommentTap = { onNavigateToComments(currentPost.id) },
                            onRepostTap = { onRepost(currentPost) },
                            onRepostLongPress = if (viewModel.remoteConfig.repostersListEnabled && currentPost.repostCount > 0) {
                                { onShowReposters(currentPost.id) }
                            } else null,
                            onShareTap = { sharePost = currentPost },
                            onSaveTap = { viewModel.toggleSave(currentPost.id) },
                            onSongCountTap = {
                                if (currentPost.isMovie) {
                                    currentPost.movieId?.let { onNavigateToFilm(it) }
                                } else {
                                    onNavigateToSong(currentPost.track)
                                }
                            },
                        )
                    }

                    // Liked by section
                    if (likeCount > 0) {
                        item {
                            LikedBySection(
                                likers = currentPost.likers,
                                likeCount = likeCount,
                                onLikesTap = {
                                    viewModel.analyticsService.logLikesListViewed(currentPost.id)
                                    onNavigateToLikes(currentPost.id)
                                },
                                onLikerTap = { liker -> onNavigateToUser(liker.id) },
                                currentUser = currentUserProfile,
                                isLiked = isLiked,
                            )
                        }
                    }

                    // Caption or Voice Note
                    if (!currentPost.voiceNoteURL.isNullOrBlank()) {
                        item {
                            VoiceNotePlayerView(
                                voiceNoteURL = currentPost.voiceNoteURL!!,
                                username = currentPost.user.username,
                                onUsernameTap = { onNavigateToUser(currentPost.user.id) },
                                onPlaybackStarted = { viewModel.analyticsService.logVoiceNotePlayed() },
                                modifier = Modifier
                                    .padding(horizontal = CorusSpacing.lg)
                                    .padding(bottom = CorusSpacing.xs),
                            )
                        }
                    } else if (!currentPost.caption.isNullOrBlank()) {
                        item {
                            PostDetailCaption(
                                username = currentPost.user.username,
                                caption = currentPost.caption,
                                onHashtagTap = { hashtag ->
                                    viewModel.analyticsService.logTrendingHashtagTapped(hashtag)
                                    onNavigateToHashtag(hashtag)
                                },
                                onMentionTap = { username ->
                                    scope.launch {
                                        val userId = viewModel.resolveUsernameToId(username.removePrefix("@"))
                                        if (userId != null) {
                                            onNavigateToUser(userId)
                                        }
                                    }
                                },
                            )
                        }
                    }

                    // Inline comments (max 3)
                    val previewComments = (comments.ifEmpty { currentPost.comments }).take(3)
                    if (previewComments.isNotEmpty()) {
                        items(previewComments, key = { it.id }) { comment ->
                            InlineCommentRow(
                                comment = comment,
                                onUserTap = { onNavigateToUser(comment.user.id) },
                                onCommentTap = { onNavigateToComments(currentPost.id) },
                                onMentionTap = { username ->
                                    scope.launch {
                                        val userId = viewModel.resolveUsernameToId(username.removePrefix("@"))
                                        if (userId != null) {
                                            onNavigateToUser(userId)
                                        }
                                    }
                                },
                                onHashtagTap = { hashtag ->
                                    viewModel.analyticsService.logTrendingHashtagTapped(hashtag)
                                    onNavigateToHashtag(hashtag)
                                },
                            )
                        }
                        item {
                            if (commentCount > previewComments.size) {
                                Text(
                                    text = stringResource(R.string.post_detail_view_all_comments_format, commentCount),
                                    style = CorusFont.body,
                                    color = CorusColors.Secondary,
                                    modifier = Modifier
                                        .clickable { onNavigateToComments(currentPost.id) }
                                        .padding(horizontal = CorusSpacing.lg)
                                        .padding(top = CorusSpacing.xs),
                                )
                            }
                        }
                    }

                    // Timestamp
                    item {
                        Text(
                            text = DateUtils.relativeTimeLong(context, currentPost.timestamp),
                            style = CorusFont.caption,
                            color = CorusColors.Secondary,
                            modifier = Modifier
                                .padding(horizontal = CorusSpacing.lg)
                                .padding(top = CorusSpacing.xs, bottom = CorusSpacing.sm),
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
                .padding(top = if (immersive) frost.contentTopPadding else padding.calculateTopPadding()),
        )
        if (immersive) {
            ImmersiveFrostedBar(
                hazeState = frost.hazeState,
                title = stringResource(R.string.feed_screen_title_Corus),
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
        deleteConfirmPost = deleteConfirmPost,
        onMenuPostChange = { menuPost = it },
        onSharePostChange = { sharePost = it },
        onEditCaptionPostChange = { editCaptionPost = it },
        onDeleteConfirmPostChange = { deleteConfirmPost = it },
        actions = viewModel,
        musicService = musicService,
        backCoverStateFor = { backCoverFlipState },
        onNavigateToSong = onNavigateToSong,
        onNavigateToFilm = onNavigateToFilm,
        onRepost = onRepost,
        onPostDeleted = { onBack() },
        onCaptionSaved = { viewModel.loadPost(postId) },
        artistPagesEnabled = onNavigateToArtist != null,
        onNavigateToArtist = onNavigateToArtist,
        onNavigateToAlbum = onNavigateToAlbum,
        onNavigateToDirector = onNavigateToDirector,
    )
}

@Composable
private fun PostDetailHeader(
    post: CymbalPost,
    onUserTap: () -> Unit,
    onRepostedFromUserTap: (userId: String?, username: String) -> Unit = { _, _ -> },
    onMenuTap: () -> Unit = {},
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = CorusSpacing.lg, vertical = CorusSpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        UserAvatarView(
            avatarURL = post.user.avatarURL,
            displayName = post.user.displayName,
            size = CorusSpacing.avatarMedium,
            modifier = Modifier.clickable(onClick = onUserTap),
        )
        Spacer(modifier = Modifier.width(CorusSpacing.sm))

        Column(
            modifier = Modifier
                .weight(1f)
                .clickable(onClick = onUserTap),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            UsernameWithFlair(
                username = post.user.username,
                isBot = post.user.isBot,
                isVerified = post.user.isVerified,
                isClubMember = post.user.isClubMember,
                flairStyle = post.user.flairStyle,
                style = CorusFont.username,
                color = CorusColors.Text,
            )

            // Repost indicator — tappable row that jumps to the original poster's profile
            val repostedFromUsername = post.repostedFromUsername
            if (!repostedFromUsername.isNullOrEmpty()) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(3.dp),
                ) {
                    Icon(
                        imageVector = Icons.Filled.Repeat,
                        contentDescription = null,
                        modifier = Modifier.size(10.dp),
                        tint = CorusColors.Secondary,
                    )
                    Text(
                        text = stringResource(R.string.post_detail_reposted_from),
                        style = CorusFont.caption,
                        color = CorusColors.Secondary,
                    )
                    Text(
                        text = stringResource(R.string.post_detail_reposted_from_username_format, repostedFromUsername),
                        style = CorusFont.caption.copy(fontWeight = FontWeight.SemiBold),
                        color = CorusColors.Secondary,
                        modifier = Modifier.clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = {
                                onRepostedFromUserTap(
                                    post.repostedFromUserId?.takeIf { it.isNotEmpty() },
                                    repostedFromUsername,
                                )
                            },
                        ),
                    )
                }
            }
        }

        Box(
            modifier = Modifier
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onMenuTap,
                )
                .padding(CorusSpacing.sm),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Filled.MoreHoriz,
                contentDescription = stringResource(R.string.feed_cd_more_options),
                modifier = Modifier.size(14.dp),
                tint = CorusColors.Secondary,
            )
        }
    }
}

@Composable
private fun PostDetailAlbumArt(
    post: CymbalPost,
    isPreviewLoading: Boolean = false,
    isPreviewPlaying: Boolean = false,
    flipState: fm.corus.android.ui.components.BackCoverFlipState =
        fm.corus.android.ui.components.rememberBackCoverFlipState(),
    onDoubleTap: () -> Unit,
    onSongTap: () -> Unit,
) {
    var showHeart by remember { mutableStateOf(false) }
    val heartAlpha by animateFloatAsState(
        targetValue = if (showHeart) 1f else 0f,
        animationSpec = tween(durationMillis = if (showHeart) 100 else 400),
        label = "heartAlpha",
    )

    LaunchedEffect(showHeart) {
        if (showHeart) {
            kotlinx.coroutines.delay(600)
            showHeart = false
        }
    }

    // Inline trailer playback for film posts (mirrors the feed's PostCard): a
    // single tap plays the trailer in place — letterboxed 16:9 inside the portrait
    // poster box — instead of navigating to the film page. Posters whose trailer
    // isn't a YouTube URL fall back to onSongTap (the film page).
    var inlineTrailerVideoID by remember(post.id) { mutableStateOf<String?>(null) }
    // When another post (or music) claims the single trailer slot, tear this one
    // down so at most one trailer plays app-wide.
    val activeTrailerPostId by TrailerPlaybackCoordinator.activePostId.collectAsState()
    LaunchedEffect(activeTrailerPostId) {
        if (activeTrailerPostId != post.id && inlineTrailerVideoID != null) {
            inlineTrailerVideoID = null
        }
    }
    DisposableEffect(post.id) {
        onDispose { TrailerPlaybackCoordinator.stop(post.id) }
    }

    val aspectRatio = if (post.isMovie) 2f / 3f else 1f
    val flipRotation by animateFloatAsState(
        targetValue = if (flipState.isFlipped) 180f else 0f,
        animationSpec = tween(durationMillis = 700),
        label = "detailAlbumFlip",
    )
    val density = androidx.compose.ui.platform.LocalDensity.current
    val cameraDistancePx = with(density) { 12.dp.toPx() } * 100f
    val showFront = flipRotation <= 90f

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(aspectRatio)
            .graphicsLayer {
                rotationY = flipRotation
                cameraDistance = cameraDistancePx
            },
        contentAlignment = Alignment.Center,
    ) {
        if (showFront) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(flipState.isLoading, post.id, post.isMovie) {
                        detectTapGestures(
                            onDoubleTap = {
                                if (flipState.isLoading) return@detectTapGestures
                                if (inlineTrailerVideoID != null) return@detectTapGestures
                                onDoubleTap()
                                showHeart = true
                            },
                            onTap = {
                                if (flipState.isLoading) return@detectTapGestures
                                if (inlineTrailerVideoID != null) return@detectTapGestures
                                // Film posts: play the trailer inline when it's a
                                // YouTube URL; otherwise fall back to onSongTap (the
                                // film page). Track posts always defer to onSongTap.
                                val videoID = if (post.isMovie) youTubeVideoID(post.trailerURL) else null
                                if (videoID != null) {
                                    inlineTrailerVideoID = videoID
                                    // Claim the single slot: stops any other trailer
                                    // and pauses music.
                                    TrailerPlaybackCoordinator.play(post.id)
                                } else {
                                    onSongTap()
                                }
                            },
                        )
                    },
                contentAlignment = Alignment.Center,
            ) {
                AsyncImage(
                    model = post.displayImageLargeURL ?: post.displayImageURL,
                    contentDescription = post.displayTitle,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )

                // Back-cover loading overlay
                if (flipState.isLoading) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.35f))
                            .padding(CorusSpacing.lg),
                        verticalArrangement = Arrangement.Bottom,
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(
                            text = stringResource(R.string.post_detail_finding_back_cover),
                            color = Color.White,
                            style = CorusFont.caption.copy(fontWeight = FontWeight.Medium),
                            modifier = Modifier.padding(bottom = CorusSpacing.xs),
                        )
                        LinearProgressIndicator(
                            progress = { flipState.progress.value.coerceIn(0f, 1f) },
                            color = Color.White,
                            trackColor = Color.White.copy(alpha = 0.25f),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }

                // "No back cover available" banner
                if (flipState.showNotFound) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.5f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = stringResource(R.string.post_detail_no_back_cover),
                            color = Color.White,
                            style = CorusFont.body.copy(fontWeight = FontWeight.Medium),
                        )
                    }
                }

                // Preview loading/playing overlay (track posts only).
                // Fades in/out to match iOS's .easeOut(duration: 0.2) so pausing
                // doesn't make the scrim + pause icon vanish abruptly.
                //
                // The loading spinner is delayed by a short grace period: fast /
                // cached previews begin playing before it elapses, so the overlay
                // fades straight in to the pause icon with no spinner flash (like the
                // already-loaded case). The spinner only appears for genuinely slow
                // loads. Gating visibility on playing || loading keeps the scrim up
                // while resolving; the glyph prefers loading over pause (mirrors iOS).
                var showPreviewSpinner by remember { mutableStateOf(false) }
                LaunchedEffect(isPreviewLoading) {
                    if (isPreviewLoading) {
                        showPreviewSpinner = false
                        delay(PREVIEW_SPINNER_DELAY_MS)
                        showPreviewSpinner = true // still loading after the grace period
                    } else {
                        showPreviewSpinner = false
                    }
                }
                androidx.compose.animation.AnimatedVisibility(
                    visible = post.isTrack && (isPreviewPlaying || isPreviewLoading) && !flipState.isLoading,
                    enter = fadeIn(animationSpec = tween(200)),
                    exit = fadeOut(animationSpec = tween(200)),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.4f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        when {
                            isPreviewLoading && (showPreviewSpinner || isPreviewPlaying) ->
                                AlbumArtPlaybackOverlayGlyph(
                                    loading = true,
                                    pauseContentDescription = stringResource(R.string.post_detail_cd_pause),
                                )
                            isPreviewPlaying ->
                                AlbumArtPlaybackOverlayGlyph(
                                    loading = false,
                                    pauseContentDescription = stringResource(R.string.post_detail_cd_pause),
                                )
                        }
                    }
                }

                // Inline trailer player — letterboxed 16:9 against black inside the
                // portrait poster box so the layout never reflows (mirrors the feed).
                inlineTrailerVideoID?.let { videoID ->
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black),
                        contentAlignment = Alignment.Center,
                    ) {
                        InlineYouTubePlayer(
                            videoID = videoID,
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(16f / 9f),
                            // Controls shown so the scrub bar is available; they
                            // auto-hide during playback, so it stays clean.
                            showControls = true,
                            onEnded = {
                                inlineTrailerVideoID = null
                                TrailerPlaybackCoordinator.stop(post.id)
                            },
                        )
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(CorusSpacing.md)
                                .size(32.dp)
                                .background(Color.Black.copy(alpha = 0.55f), CircleShape)
                                .clickable {
                                    inlineTrailerVideoID = null
                                    TrailerPlaybackCoordinator.stop(post.id)
                                },
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Close,
                                contentDescription = stringResource(R.string.post_card_cd_close_trailer),
                                tint = Color.White,
                                modifier = Modifier.size(16.dp),
                            )
                        }
                    }
                }

                // Heart animation overlay
                Icon(
                    imageVector = Icons.Filled.Favorite,
                    contentDescription = null,
                    modifier = Modifier
                        .size(80.dp)
                        .alpha(heartAlpha),
                    tint = Color.White,
                )
            }
        } else {
            // BACK FACE
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { rotationY = 180f }
                    .pointerInput(post.id) {
                        detectTapGestures(onTap = { flipState.flipBack() })
                    },
            ) {
                AsyncImage(
                    model = flipState.backCoverURL,
                    contentDescription = stringResource(R.string.post_detail_cd_album_back_cover),
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
            }
        }
    }
}

@Composable
private fun PostDetailSongInfo(
    post: CymbalPost,
    onSongTap: () -> Unit,
    onSpotifyTap: () -> Unit = {},
    musicService: fm.corus.android.data.model.MusicService = fm.corus.android.data.model.MusicService.SPOTIFY,
    onTrailerTap: () -> Unit = {},
    /** Tappable artist/director name (artist_pages_enabled). Same style as
     *  plain text; the clickable consumes the tap so onSongTap doesn't fire. */
    onSubtitleTap: (() -> Unit)? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = CorusSpacing.lg, end = CorusSpacing.lg, top = CorusSpacing.md, bottom = CorusSpacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .clickable(onClick = onSongTap),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = post.displayTitle,
                style = CorusFont.songTitle,
                color = CorusColors.Text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = post.displaySubtitle,
                style = CorusFont.artistName,
                color = CorusColors.Secondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = if (onSubtitleTap != null) {
                    Modifier.clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onSubtitleTap,
                    )
                } else Modifier,
            )
        }

        Spacer(modifier = Modifier.width(CorusSpacing.sm))

        // Spotify or trailer button — YouTube red play icon, matching iOS
        // Trailer button is only shown when a URL exists (matches iOS).
        if (post.isMovie) {
            if (!post.trailerURL.isNullOrBlank()) {
                Image(
                    painter = painterResource(R.drawable.ic_play_rectangle_fill),
                    contentDescription = stringResource(R.string.post_detail_cd_watch_trailer),
                    modifier = Modifier
                        .size(22.dp)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = onTrailerTap,
                        ),
                )
            }
        } else {
            // Non-Spotify sources lock to their own brand badge. The track
            // isn't on Spotify and a Spotify icon would be misleading
            // (SoundCloud → cloudmark; Apple Music → Apple glyph).
            if (post.track.source == TrackSource.SOUNDCLOUD) {
                SoundCloudAdaptiveLogo(
                    modifier = Modifier
                        .size(28.dp)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = onSpotifyTap,
                        ),
                    size = 28.dp,
                )
            } else {
                // Glyph reflects the service the tap opens. Apple-only tracks
                // aren't on Spotify, so a Spotify viewer is routed to Apple
                // Music — show that. TIDAL/Deezer viewers keep their own glyph.
                // Mirrors iOS.
                val isAppleMusic = post.track.source == TrackSource.APPLEMUSIC
                // Flip to the source on a confirmed "" OR when the Apple id is in
                // a storefront the viewer can't open. null = unknown -> keep the
                // preference. Mirrors PostCard/iOS.
                val hasAppleMusicEquivalent = when {
                    isAppleMusic -> true
                    post.track.appleMusicId == "" -> false
                    post.track.appleMusicId == null -> true
                    else -> post.track.appleMusicReachable(
                        from = fm.corus.android.domain.MusicServiceLinkOut.deviceStorefront(),
                    )
                }
                val displayedService = when {
                    isAppleMusic && musicService == fm.corus.android.data.model.MusicService.SPOTIFY ->
                        fm.corus.android.data.model.MusicService.APPLE_MUSIC
                    musicService == fm.corus.android.data.model.MusicService.APPLE_MUSIC && !hasAppleMusicEquivalent ->
                        fm.corus.android.data.model.MusicService.SPOTIFY
                    else -> musicService
                }
                Image(
                    painter = painterResource(fm.corus.android.domain.MusicServiceLinkOut.logoRes(displayedService)),
                    contentDescription = stringResource(R.string.post_detail_cd_play_spotify),
                    modifier = Modifier
                        .size(28.dp)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = onSpotifyTap,
                        ),
                    contentScale = ContentScale.Fit,
                )
            }
        }
    }
}

@Composable
private fun PostDetailEngagementRow(
    likeCount: Int,
    commentCount: Int,
    repostCount: Int,
    isLiked: Boolean,
    isSaved: Boolean,
    saveCount: Int,
    saveCountEnabled: Boolean,
    trackPostCount: Int,
    onLikeTap: () -> Unit,
    onCommentTap: () -> Unit,
    onRepostTap: () -> Unit,
    onRepostLongPress: (() -> Unit)? = null,
    onShareTap: () -> Unit,
    onSaveTap: () -> Unit,
    onSongCountTap: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = CorusSpacing.lg)
            .padding(vertical = CorusSpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(CorusSpacing.lg),
    ) {
        // Like
        EngagementButton(
            icon = if (isLiked) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
            count = likeCount,
            tint = if (isLiked) CorusColors.Like else CorusColors.Text,
            onClick = onLikeTap,
        )

        // Comment
        EngagementButton(
            icon = Icons.Outlined.ChatBubbleOutline,
            count = commentCount,
            tint = CorusColors.Text,
            onClick = onCommentTap,
        )

        // Repost — tap composes a repost; when wired, press-and-hold opens the
        // "who reposted" list.
        EngagementButton(
            icon = Icons.Filled.Repeat,
            count = repostCount,
            tint = CorusColors.Text,
            onClick = onRepostTap,
            onLongClick = onRepostLongPress,
        )

        // Share
        EngagementButton(
            icon = Icons.AutoMirrored.Filled.Send,
            count = 0,
            tint = CorusColors.Text,
            onClick = onShareTap,
        )

        // Track post count — only show when 2+ people posted the same song/film
        if (trackPostCount > 1) {
            Row(
                modifier = Modifier.clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onSongCountTap,
                ),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
            ) {
                VennDiagramIcon(
                    size = 22.dp,
                    color = CorusColors.Text,
                )
                Spacer(modifier = Modifier.width(CorusSpacing.xs))
                Text(
                    text = trackPostCount.toString(),
                    style = CorusFont.bodyMedium,
                    color = CorusColors.Text,
                )
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        // Save. Floor the count to 1 while optimistically saved so a just-saved
        // post never shows 0 before the server trigger commits. Gated by the
        // save_count_enabled flag — 0 (hidden) when off.
        val displaySaveCount = when {
            !saveCountEnabled -> 0
            isSaved -> maxOf(saveCount, 1)
            else -> saveCount
        }
        Row(
            modifier = Modifier.clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onSaveTap,
            ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(CorusSpacing.xs),
        ) {
            // Count to the LEFT of the right-anchored bookmark.
            if (displaySaveCount > 0) {
                Text(
                    text = displaySaveCount.toString(),
                    style = CorusFont.bodyMedium,
                    color = CorusColors.Text,
                )
            }
            Icon(
                imageVector = if (isSaved) Icons.Filled.Bookmark else Icons.Outlined.BookmarkBorder,
                contentDescription = stringResource(R.string.post_detail_cd_save),
                modifier = Modifier.size(20.dp),
                tint = CorusColors.Text,
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun EngagementButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    count: Int,
    tint: Color,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
) {
    val interaction = remember { MutableInteractionSource() }
    Row(
        modifier = if (onLongClick != null) {
            Modifier.combinedClickable(
                interactionSource = interaction,
                indication = null,
                onClick = onClick,
                onLongClick = onLongClick,
            )
        } else {
            Modifier.clickable(
                interactionSource = interaction,
                indication = null,
                onClick = onClick,
            )
        },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(22.dp),
            tint = tint,
        )
        if (count > 0) {
            Spacer(modifier = Modifier.width(CorusSpacing.xs))
            Text(
                text = count.toString(),
                style = CorusFont.bodyMedium,
                color = CorusColors.Text,
            )
        }
    }
}


@Composable
private fun PostDetailCaption(
    username: String,
    caption: String,
    onHashtagTap: (String) -> Unit,
    onMentionTap: (String) -> Unit,
) {
    val captionText = buildAnnotatedString {
        withStyle(
            SpanStyle(
                fontFamily = NunitoFamily,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 14.sp,
                color = CorusColors.Text,
            )
        ) {
            append(username)
        }
        append(" ")

        val regex = Regex("(@\\w+)|(#\\w+)")
        var lastIndex = 0
        regex.findAll(caption).forEach { match ->
            if (match.range.first > lastIndex) {
                withStyle(SpanStyle(color = CorusColors.Text)) {
                    append(caption.substring(lastIndex, match.range.first))
                }
            }
            val token = match.value
            val tag = if (token.startsWith("@")) "mention" else "hashtag"
            pushStringAnnotation(tag = tag, annotation = token)
            if (token.startsWith("@")) {
                withStyle(SpanStyle(color = CorusColors.Accent, fontWeight = FontWeight.ExtraBold)) {
                    append(token)
                }
            } else {
                withStyle(SpanStyle(color = CorusColors.Accent)) {
                    append(token)
                }
            }
            pop()
            lastIndex = match.range.last + 1
        }
        if (lastIndex < caption.length) {
            withStyle(SpanStyle(color = CorusColors.Text)) {
                append(caption.substring(lastIndex))
            }
        }
    }

    @Suppress("DEPRECATION")
    ClickableText(
        text = captionText,
        style = CorusFont.body,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = CorusSpacing.lg)
            .padding(bottom = CorusSpacing.xs),
        onClick = { offset ->
            captionText.getStringAnnotations("mention", offset, offset).firstOrNull()?.let {
                onMentionTap(it.item)
            }
            captionText.getStringAnnotations("hashtag", offset, offset).firstOrNull()?.let {
                onHashtagTap(it.item.removePrefix("#"))
            }
        },
    )
}

@Composable
private fun InlineCommentRow(
    comment: CymbalComment,
    onUserTap: () -> Unit,
    onCommentTap: () -> Unit,
    onMentionTap: (String) -> Unit,
    onHashtagTap: (String) -> Unit,
) {
    val commentText = remember(comment.user.username, comment.text) {
        buildAnnotatedString {
            pushStringAnnotation(tag = "user", annotation = comment.user.username)
            withStyle(
                SpanStyle(
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 14.sp,
                )
            ) {
                append(comment.user.username)
            }
            pop()
            append(" ")
            append(
                buildMentionAnnotatedString(
                    text = comment.text,
                    baseStyle = SpanStyle(
                        fontWeight = FontWeight.Normal,
                        fontSize = 15.sp,
                    ),
                )
            )
        }
    }

    @Suppress("DEPRECATION")
    ClickableText(
        text = commentText,
        style = CorusFont.body.copy(color = CorusColors.Text),
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = CorusSpacing.lg, vertical = CorusSpacing.xxs),
        onClick = { offset ->
            commentText.getStringAnnotations("mention", offset, offset).firstOrNull()?.let {
                onMentionTap(it.item)
                return@ClickableText
            }
            commentText.getStringAnnotations("hashtag", offset, offset).firstOrNull()?.let {
                onHashtagTap(it.item)
                return@ClickableText
            }
            commentText.getStringAnnotations("user", offset, offset).firstOrNull()?.let {
                onUserTap()
                return@ClickableText
            }
            onCommentTap()
        },
    )
}
