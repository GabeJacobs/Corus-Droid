package fm.corus.android.ui.screens.profile

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import com.valentinilk.shimmer.shimmer
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.ui.res.painterResource
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Settings
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.ui.draw.alpha
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import coil3.size.Size

import fm.corus.android.data.model.CymbalPost
import fm.corus.android.data.model.CymbalUser
import fm.corus.android.data.model.MediaType
import fm.corus.android.domain.HapticManager
import fm.corus.android.ui.LocalHapticManager
import android.graphics.Bitmap
import fm.corus.android.ui.components.AvatarCropView
import fm.corus.android.ui.components.FullScreenAvatarOverlay
import fm.corus.android.ui.components.SelfieCaptureScreen
import fm.corus.android.ui.components.ShimmerAsyncImage
import fm.corus.android.ui.components.uriToBitmap
import fm.corus.android.ui.components.ToastManager
import fm.corus.android.ui.components.UserAvatarView
import fm.corus.android.ui.components.UsernameWithFlair
import fm.corus.android.ui.theme.CorusColors
import fm.corus.android.ui.theme.CorusFont
import fm.corus.android.ui.theme.CorusSpacing
import fm.corus.android.ui.theme.CorusSystemBars
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@Composable
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
fun ProfileScreen(
    viewModel: ProfileViewModel = hiltViewModel(),
    scrollToTopTrigger: Int = 0,
    openStylePicker: Boolean = false,
    onStylePickerConsumed: () -> Unit = {},
    onNavigateToSettings: () -> Unit = {},
    onNavigateToEditProfile: (String) -> Unit = {},
    onNavigateToFollowList: (String, Boolean) -> Unit = { _, _ -> },
    onNavigateToProfileFeed: (userId: String, username: String, postId: String, segment: Int) -> Unit = { _, _, _, _ -> },
    onNavigateToClub: () -> Unit = {},
    onOpenCompose: (String) -> Unit = {},
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val profile by viewModel.profile.collectAsState()
    val pendingAvatarBytes by viewModel.pendingAvatarBytes.collectAsState()
    val posts by viewModel.posts.collectAsState()
    val musicService by viewModel.musicServicePreference.current.collectAsState()
    var showPlaylistAlert by remember { mutableStateOf(false) }
    val likedPosts by viewModel.likedPosts.collectAsState()
    val savedPosts by viewModel.savedPosts.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val isRefreshing by viewModel.isRefreshing.collectAsState()
    val isLoadingLiked by viewModel.isLoadingLiked.collectAsState()
    val isLoadingSaved by viewModel.isLoadingSaved.collectAsState()
    val isLoadingFilms by viewModel.isLoadingFilms.collectAsState()
    val hasFetchedFilmPage by viewModel.hasFetchedFilmPage.collectAsState()
    val isClubMember by viewModel.isClubMember.collectAsState()
    val hasFullAccess by viewModel.hasFullAccess.collectAsState()
    val isSavingStyle by viewModel.isSavingStyle.collectAsState()
    val engagementStates by viewModel.engagementStates.collectAsState()
    var selectedSegment by remember { mutableIntStateOf(0) }
    var isFeaturedArtReady by remember { mutableStateOf(false) }
    var didRevealFromSkeleton by remember { mutableStateOf(false) }
    var showStylePicker by remember { mutableStateOf(false) }
    var showClubOffer by remember { mutableStateOf(false) }
    var clubOfferSource by remember { mutableStateOf(fm.corus.android.ui.screens.subscription.PaywallSource.DEFAULT) }

    val paywallRequested by viewModel.nowPlayingManager.paywallRequested.collectAsState()
    LaunchedEffect(paywallRequested) {
        if (paywallRequested) {
            clubOfferSource = fm.corus.android.ui.screens.subscription.PaywallSource.PLAYLIST_LIMIT
            showClubOffer = true
            viewModel.nowPlayingManager.clearPaywallRequested()
        }
    }

    // Open style picker when navigating back from EditProfile with the action
    LaunchedEffect(openStylePicker) {
        if (openStylePicker) {
            showStylePicker = true
            onStylePickerConsumed()
        }
    }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val clubSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    // Avatar context menu state
    var showAvatarMenu by remember { mutableStateOf(false) }
    var showFullScreenAvatar by remember { mutableStateOf(false) }
    var cropBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var showSelfieCapture by remember { mutableStateOf(false) }
    val decodeScope = rememberCoroutineScope()

    // In-app selfie camera — Samsung One UI ignores the front-camera intent extras,
    // so we capture in-app via CameraX with LENS_FACING_FRONT forced (matches onboarding).
    val cameraPhotoFile = remember { File(context.cacheDir, "profile_camera_avatar.jpg") }
    val cameraPhotoUri = remember {
        FileProvider.getUriForFile(context, "${context.packageName}.provider", cameraPhotoFile)
    }

    // Photo picker launcher — opens crop screen before uploading.
    // Bitmap decode + EXIF rotation runs on IO dispatcher to keep the UI responsive.
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            decodeScope.launch {
                val bmp = withContext(Dispatchers.IO) { uriToBitmap(context, uri) }
                cropBitmap = bmp
            }
        }
    }

    LaunchedEffect(Unit) {
        viewModel.loadProfile()
    }

    if (isLoading && profile == null) {
        Column(modifier = Modifier.fillMaxSize()) {
            fm.corus.android.ui.components.SkeletonProfileView()
            fm.corus.android.ui.components.SkeletonProfileGrid()
        }
        return
    }

    val currentProfile = profile ?: return

    val gridState = rememberLazyGridState()
    val isLoadingMore by viewModel.isLoadingMore.collectAsState()
    val hasMore by viewModel.hasMore.collectAsState()

    var lastScrollTrigger by rememberSaveable { mutableIntStateOf(0) }
    LaunchedEffect(scrollToTopTrigger) {
        if (scrollToTopTrigger > lastScrollTrigger) {
            gridState.animateScrollToItem(0)
            lastScrollTrigger = scrollToTopTrigger
        }
    }

    // Infinite scroll: load more when near the bottom
    val shouldLoadMore by remember {
        derivedStateOf {
            val layoutInfo = gridState.layoutInfo
            val totalItems = layoutInfo.totalItemsCount
            val lastVisibleItem = layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            totalItems > 0 && lastVisibleItem >= totalItems - 6
        }
    }
    LaunchedEffect(shouldLoadMore, selectedSegment) {
        if (shouldLoadMore && hasMore[selectedSegment] == true && !isLoadingMore && !isLoading) {
            viewModel.loadMoreForSegment(selectedSegment)
        }
    }

    // Helper: populate cache and navigate to profile feed
    val navigateToFeed: (String) -> Unit = { postId ->
        val filteredForNav = when (selectedSegment) {
            0 -> posts.filter { it.mediaType == fm.corus.android.data.model.MediaType.TRACK }
            1 -> posts.filter { it.mediaType == fm.corus.android.data.model.MediaType.MOVIE }
            2 -> likedPosts
            3 -> savedPosts
            else -> posts
        }
        ProfileFeedCache.posts = filteredForNav
        ProfileFeedCache.hasMore = hasMore[selectedSegment] == true
        ProfileFeedCache.profileUser = currentProfile
        onNavigateToProfileFeed(
            currentProfile.id,
            currentProfile.username,
            postId,
            selectedSegment,
        )
    }

    val haptics = LocalHapticManager.current
    PullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = {
            // Mirrors iOS ProfileView.refreshable haptic.
            haptics.impact(HapticManager.ImpactStyle.LIGHT)
            viewModel.refreshProfile()
        },
        modifier = Modifier.fillMaxSize(),
    ) {
    LazyVerticalGrid(
        state = gridState,
        columns = GridCells.Fixed(3),
        modifier = Modifier.fillMaxSize(),
    ) {
        // All header content spans full width
        item(span = { GridItemSpan(3) }) {
            Column {
                // ── Header Row: icon / display name / settings ──
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = CorusSpacing.lg, vertical = CorusSpacing.md),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    // Profile customization icon (matching iOS CorusClub icon)
                    if (posts.isNotEmpty()) {
                        Icon(
                            painter = painterResource(fm.corus.android.R.drawable.corus_club_vector),
                            contentDescription = stringResource(fm.corus.android.R.string.profile_cd_customize),
                            tint = CorusColors.Accent,
                            modifier = Modifier
                                .size(40.dp)
                                .clickable { showStylePicker = true },
                        )
                    } else {
                        Spacer(modifier = Modifier.size(40.dp))
                    }

                    Text(
                        text = currentProfile.displayName,
                        style = CorusFont.displayName,
                        color = CorusColors.Text,
                    )

                    Icon(
                        Icons.Filled.Settings,
                        contentDescription = stringResource(fm.corus.android.R.string.profile_cd_settings),
                        tint = CorusColors.Secondary,
                        modifier = Modifier
                            .size(24.dp)
                            .clickable(onClick = onNavigateToSettings),
                    )
                }

                // ── Avatar + Stats Row ──
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = CorusSpacing.lg),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // Large circular avatar with long-press context menu
                    Box {
                        UserAvatarView(
                            avatarURL = currentProfile.avatarURL,
                            displayName = currentProfile.displayName,
                            size = CorusSpacing.avatarLarge,
                            modifier = Modifier.combinedClickable(
                                onClick = { showAvatarMenu = true },
                                onLongClick = { showAvatarMenu = true },
                            ),
                            localAvatarOverride = pendingAvatarBytes,
                        )

                        DropdownMenu(
                            expanded = showAvatarMenu,
                            onDismissRequest = { showAvatarMenu = false },
                            containerColor = CorusColors.Background,
                        ) {
                            DropdownMenuItem(
                                text = { Text(stringResource(fm.corus.android.R.string.profile_avatar_take_photo), style = CorusFont.body, color = CorusColors.Text) },
                                onClick = {
                                    showAvatarMenu = false
                                    showSelfieCapture = true
                                },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(fm.corus.android.R.string.profile_avatar_choose_library), style = CorusFont.body, color = CorusColors.Text) },
                                onClick = {
                                    showAvatarMenu = false
                                    photoPickerLauncher.launch(
                                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                    )
                                },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(fm.corus.android.R.string.profile_avatar_view_photo), style = CorusFont.body, color = CorusColors.Text) },
                                onClick = {
                                    showAvatarMenu = false
                                    showFullScreenAvatar = true
                                },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(fm.corus.android.R.string.profile_avatar_share_link), style = CorusFont.body, color = CorusColors.Text) },
                                onClick = {
                                    showAvatarMenu = false
                                    val link = "https://corus.fm/u/${currentProfile.username}"
                                    clipboardManager.setText(AnnotatedString(link))
                                    ToastManager.show(context.getString(fm.corus.android.R.string.profile_toast_link_copied))
                                },
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(CorusSpacing.md))

                    // Right side: stats + edit button
                    Column(
                        modifier = Modifier.weight(1f),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        // Stats row
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(CorusSpacing.xxl),
                        ) {
                            StatItem(count = currentProfile.cymbalCount, label = stringResource(fm.corus.android.R.string.profile_stat_coruses))
                            StatItem(
                                count = currentProfile.followerCount,
                                label = stringResource(fm.corus.android.R.string.profile_stat_followers),
                                onClick = { onNavigateToFollowList(currentProfile.id, true) },
                            )
                            StatItem(
                                count = currentProfile.followingCount,
                                label = stringResource(fm.corus.android.R.string.profile_stat_following),
                                onClick = { onNavigateToFollowList(currentProfile.id, false) },
                            )
                        }

                        Spacer(modifier = Modifier.height(CorusSpacing.sm))

                        // Edit Profile button + playlist + vinyl picker
                        Row(
                            modifier = Modifier.padding(horizontal = CorusSpacing.xs),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            // Edit Profile — matching iOS Capsule with stroke border
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(50))
                                    .border(1.dp, CorusColors.Divider, RoundedCornerShape(50))
                                    .clickable { onNavigateToEditProfile(currentProfile.id) }
                                    .padding(vertical = CorusSpacing.sm - 2.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = stringResource(fm.corus.android.R.string.profile_button_edit),
                                    style = CorusFont.button,
                                    color = CorusColors.Secondary,
                                )
                            }

                            Spacer(modifier = Modifier.width(CorusSpacing.sm))

                            // Playlist button (matching iOS music.note.list)
                            val hasSongs = posts.any { it.mediaType == MediaType.TRACK }
                            val isGeneratingPlaylist by viewModel.nowPlayingManager.isGeneratingPlaylist.collectAsState()
                            val playlistError by viewModel.nowPlayingManager.playlistError.collectAsState()

                            LaunchedEffect(playlistError) {
                                if (playlistError != null) {
                                    ToastManager.show(playlistError!!)
                                    viewModel.nowPlayingManager.clearPlaylistError()
                                }
                            }

                            Box(
                                modifier = Modifier
                                    .height(30.dp)
                                    .clip(RoundedCornerShape(50))
                                    .background(Color.Transparent)
                                    .then(
                                        Modifier.border(
                                            1.dp,
                                            CorusColors.Divider,
                                            RoundedCornerShape(50),
                                        )
                                    )
                                    .clickable(enabled = !isGeneratingPlaylist) {
                                        if (!hasSongs) {
                                            ToastManager.show(context.getString(fm.corus.android.R.string.profile_toast_no_songs_for_playlist))
                                        } else {
                                            val isApple = musicService == fm.corus.android.data.model.MusicService.APPLE_MUSIC
                                            val hasSoundCloud = posts.any { it.isTrack && it.track.source == fm.corus.android.data.model.TrackSource.SOUNDCLOUD }
                                            if (isApple || hasSoundCloud) {
                                                showPlaylistAlert = true
                                            } else {
                                                viewModel.generatePlaylist()
                                            }
                                        }
                                    }
                                    .padding(horizontal = CorusSpacing.md),
                                contentAlignment = Alignment.Center,
                            ) {
                                // Always render label to preserve button width
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(CorusSpacing.xs),
                                    modifier = Modifier.alpha(
                                        if (!hasSongs) 0.35f
                                        else if (isGeneratingPlaylist) 0f
                                        else 1f
                                    ),
                                ) {
                                    Icon(
                                        painter = painterResource(fm.corus.android.R.drawable.ic_music_note_list),
                                        contentDescription = stringResource(fm.corus.android.R.string.profile_cd_playlist),
                                        modifier = Modifier.size(14.dp),
                                        tint = CorusColors.Secondary,
                                    )
                                    Text(
                                        text = stringResource(fm.corus.android.R.string.profile_button_playlist),
                                        style = CorusFont.button,
                                        color = CorusColors.Secondary,
                                    )
                                }
                                if (isGeneratingPlaylist) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(14.dp),
                                        strokeWidth = 2.dp,
                                        color = CorusColors.Secondary,
                                    )
                                }
                            }

                        }
                    }
                }

                Spacer(modifier = Modifier.height(CorusSpacing.md))

                // ── Username + Bio + Website ──
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 28.dp, end = CorusSpacing.lg),
                ) {
                    // @username with badges
                    UsernameWithFlair(
                        username = currentProfile.username,
                        isBot = currentProfile.isBot,
                        isVerified = currentProfile.isVerified,
                        isClubMember = currentProfile.isClubMember,
                        flairStyle = currentProfile.flairStyle,
                        showAtPrefix = true,
                        style = CorusFont.username,
                        color = CorusColors.Text,
                    )

                    // Bio
                    if (currentProfile.bio.isNotBlank()) {
                        Spacer(modifier = Modifier.height(CorusSpacing.xs))
                        Text(
                            text = currentProfile.bio,
                            style = CorusFont.bio,
                            color = CorusColors.Secondary,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }

                    // Website
                    if (!currentProfile.website.isNullOrBlank()) {
                        Spacer(modifier = Modifier.height(CorusSpacing.xs))
                        val context = androidx.compose.ui.platform.LocalContext.current
                        Text(
                            text = currentProfile.website!!.removePrefix("https://").removePrefix("http://"),
                            style = CorusFont.caption,
                            color = CorusColors.Accent,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.clickable {
                                val url = if (currentProfile.website!!.startsWith("http")) {
                                    currentProfile.website!!
                                } else {
                                    "https://${currentProfile.website}"
                                }
                                try {
                                    context.startActivity(
                                        android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(url))
                                    )
                                } catch (_: Exception) { }
                            },
                        )
                    }
                }

                Spacer(modifier = Modifier.height(CorusSpacing.lg))

                // ── Segment Control ──
                val tabs = listOf(
                    stringResource(fm.corus.android.R.string.profile_tab_music),
                    stringResource(fm.corus.android.R.string.profile_tab_film),
                    stringResource(fm.corus.android.R.string.profile_tab_likes),
                    stringResource(fm.corus.android.R.string.profile_tab_saves),
                )
                val tabSelectedColor = CorusColors.Text
                val tabUnselectedColor = CorusColors.Divider
                Row(
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    tabs.forEachIndexed { index, title ->
                        val isSelected = selectedSegment == index
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .clickable {
                                    selectedSegment = index
                                    isFeaturedArtReady = false
                                    viewModel.loadSegment(index)
                                }
                                .drawBehind {
                                    val strokeWidth = if (isSelected) 3.dp.toPx() else 0.5.dp.toPx()
                                    val lineColor = if (isSelected) tabSelectedColor else tabUnselectedColor
                                    drawLine(
                                        color = lineColor,
                                        start = Offset(0f, size.height),
                                        end = Offset(size.width, size.height),
                                        strokeWidth = strokeWidth,
                                    )
                                }
                                .padding(vertical = CorusSpacing.sm),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Text(
                                text = title,
                                style = CorusFont.tabLabel,
                                color = if (isSelected) CorusColors.Text else CorusColors.Tertiary,
                            )
                        }
                    }
                }

                // Filter posts by segment
                val filteredPosts = when (selectedSegment) {
                    0 -> posts.filter { it.mediaType == fm.corus.android.data.model.MediaType.TRACK }
                    1 -> posts.filter { it.mediaType == fm.corus.android.data.model.MediaType.MOVIE }
                    2 -> likedPosts
                    3 -> savedPosts
                    else -> posts
                }

                // ── Featured Post — only for Music/Film tabs (matching iOS) ──
                // Show skeleton until the featured art image has loaded,
                // matching the iOS pattern of hiding the real view until ready.
                if (filteredPosts.isNotEmpty() && selectedSegment <= 1) {
                    val featuredPost = filteredPosts.first()
                    if (!isFeaturedArtReady) {
                        // Render featured view off-screen to trigger image load,
                        // show skeleton on top (like iOS's .hidden() + ZStack)
                        Box {
                            Box(modifier = Modifier
                                .fillMaxWidth()
                                .alpha(0f)
                            ) {
                                if (featuredPost.mediaType == fm.corus.android.data.model.MediaType.MOVIE) {
                                    fm.corus.android.ui.components.FeaturedMoviePosterView(
                                        post = featuredPost,
                                        frameStyle = currentProfile.frameStyle,
                                        onArtReady = { isFeaturedArtReady = true },
                                    )
                                } else {
                                    fm.corus.android.ui.components.FeaturedCymbalView(
                                        post = featuredPost,
                                        vinylStyle = currentProfile.vinylStyle,
                                        onArtReady = { isFeaturedArtReady = true },
                                    )
                                }
                            }
                            fm.corus.android.ui.components.SkeletonProfileGrid(
                                isFilmStyle = featuredPost.mediaType == fm.corus.android.data.model.MediaType.MOVIE,
                            )
                        }
                    } else {
                        val featuredEngagement = engagementStates[featuredPost.id]
                        if (featuredPost.mediaType == fm.corus.android.data.model.MediaType.MOVIE) {
                            fm.corus.android.ui.components.FeaturedMoviePosterView(
                                post = featuredPost,
                                frameStyle = currentProfile.frameStyle,
                                rainIntensity = currentProfile.rainIntensity,
                                snowIntensity = currentProfile.snowIntensity,
                                discoIntensity = currentProfile.discoIntensityLevel,
                                likeCount = featuredEngagement?.likeCount ?: featuredPost.likeCount,
                                isLiked = featuredEngagement?.isLiked ?: featuredPost.isLiked,
                                onLikeTap = { viewModel.toggleLike(featuredPost.id) },
                                onPostTap = { navigateToFeed(featuredPost.id) },
                            )
                        } else {
                            val shouldStagger = !didRevealFromSkeleton
                            LaunchedEffect(Unit) { didRevealFromSkeleton = true }
                            fm.corus.android.ui.components.FeaturedCymbalView(
                                post = featuredPost,
                                vinylStyle = currentProfile.vinylStyle,
                                rainIntensity = currentProfile.rainIntensity,
                                snowIntensity = currentProfile.snowIntensity,
                                discoIntensity = currentProfile.discoIntensityLevel,
                                likeCount = featuredEngagement?.likeCount ?: featuredPost.likeCount,
                                isLiked = featuredEngagement?.isLiked ?: featuredPost.isLiked,
                                onLikeTap = { viewModel.toggleLike(featuredPost.id) },
                                onPostTap = { navigateToFeed(featuredPost.id) },
                                staggerVinyl = shouldStagger,
                            )
                        }
                    }
                } else if (filteredPosts.isEmpty() && selectedSegment == 1 && (isLoadingFilms || !hasFetchedFilmPage)) {
                    // Films pending — show skeleton until we've either fetched the
                    // movie-only page or determined there are none.
                    fm.corus.android.ui.components.SkeletonProfileGrid(isFilmStyle = true)
                } else if (filteredPosts.isEmpty() && !isLoading
                    && !(selectedSegment == 2 && isLoadingLiked)
                    && !(selectedSegment == 3 && isLoadingSaved)
                ) {
                    // Empty state per segment (matching iOS)
                    when (selectedSegment) {
                        0 -> ProfileEmptyPrompt(
                            icon = Icons.Filled.Headphones,
                            title = stringResource(fm.corus.android.R.string.profile_empty_music_title),
                            subtitle = stringResource(fm.corus.android.R.string.profile_empty_music_subtitle),
                            buttonText = stringResource(fm.corus.android.R.string.profile_empty_music_button),
                            onButtonClick = { onOpenCompose("track") },
                        )
                        1 -> ProfileEmptyPrompt(
                            icon = Icons.Filled.Movie,
                            title = stringResource(fm.corus.android.R.string.profile_empty_film_title),
                            subtitle = stringResource(fm.corus.android.R.string.profile_empty_film_subtitle),
                            buttonText = stringResource(fm.corus.android.R.string.profile_empty_film_button),
                            onButtonClick = { onOpenCompose("movie") },
                        )
                        2 -> ProfileEmptyPlaceholder(
                            icon = Icons.Filled.Favorite,
                            message = stringResource(fm.corus.android.R.string.profile_empty_likes),
                        )
                        else -> ProfileEmptyPlaceholder(
                            icon = Icons.Filled.Bookmark,
                            message = stringResource(fm.corus.android.R.string.profile_empty_saves),
                        )
                    }
                }
            }
        }

        // ── Album Art Grid (filtered) ──
        // Hide grid while featured art is loading (skeleton in header covers both areas)
        val isFeaturedTab = selectedSegment <= 1
        val isFeaturedArtLoading = isFeaturedTab && !isFeaturedArtReady && posts.any {
            it.mediaType == if (selectedSegment == 0) fm.corus.android.data.model.MediaType.TRACK
            else fm.corus.android.data.model.MediaType.MOVIE
        }
        val isSegmentLoading = (selectedSegment == 2 && isLoadingLiked && likedPosts.isEmpty())
            || (selectedSegment == 3 && isLoadingSaved && savedPosts.isEmpty())

        if (isFeaturedArtLoading) {
            // SkeletonProfileGrid in the header already covers grid area; emit nothing here
        } else if (isSegmentLoading) {
            // Skeleton grid cells while likes/saves load (matching iOS)
            items(15) {
                Box(
                    modifier = Modifier
                        .aspectRatio(1f)
                        .background(CorusColors.Skeleton)
                        .shimmer(),
                )
            }
        } else {
            @Suppress("NAME_SHADOWING")
            val filteredPosts = when (selectedSegment) {
                0 -> posts.filter { it.mediaType == fm.corus.android.data.model.MediaType.TRACK }
                1 -> posts.filter { it.mediaType == fm.corus.android.data.model.MediaType.MOVIE }
                2 -> likedPosts
                3 -> savedPosts
                else -> posts
            }
            // For Likes/Saves, show all posts in grid (no featured post);
            // for Music/Film, skip the first post (already shown as featured).
            // distinctBy guards the LazyGrid against duplicate ids (server pages
            // can occasionally overlap on Likes/Saves) — duplicates would crash
            // SubcomposeLayout with "Key … was already used".
            val gridPosts = (if (selectedSegment <= 1) filteredPosts.drop(1) else filteredPosts)
                .distinctBy { it.id }
            if (gridPosts.isNotEmpty()) {
                items(gridPosts, key = { it.id }, contentType = { "post_grid" }) { post ->
                    PostGridItem(
                        post = post,
                        isFilmPoster = selectedSegment == 1,
                        onClick = { navigateToFeed(post.id) },
                    )
                }
            }

            // Loading more indicator
            if (isLoadingMore) {
                item(span = { GridItemSpan(3) }) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(CorusSpacing.lg),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            strokeWidth = 2.dp,
                            color = CorusColors.Accent,
                        )
                    }
                }
            }
        }
    }
    }

    // ── Style Picker Bottom Sheet ──
    if (showStylePicker) {
        val trackPosts = posts.filter { it.mediaType == fm.corus.android.data.model.MediaType.TRACK }
        val moviePosts = posts.filter { it.mediaType == fm.corus.android.data.model.MediaType.MOVIE }

        ModalBottomSheet(
            onDismissRequest = { showStylePicker = false },
            sheetState = sheetState,
            containerColor = CorusColors.Background,
            dragHandle = null,
        ) {
            CorusSystemBars()
            // When on the FILM tab, open directly to the frame color page
            val styleInitialPage = if (selectedSegment == 1 && trackPosts.isNotEmpty() && moviePosts.isNotEmpty()) {
                1 // FRAME is at index 1 when both track and movie posts exist (VINYL=0, FRAME=1)
            } else if (selectedSegment == 1 && moviePosts.isNotEmpty()) {
                0 // FRAME is at index 0 when only movie posts exist
            } else {
                0
            }

            StylePickerSheet(
                currentSelections = StyleSelections(
                    vinylColor = currentProfile.vinylStyle,
                    vinylSpinning = currentProfile.vinylSpinning,
                    frameColor = currentProfile.frameStyle,
                    profileFlair = currentProfile.flairStyle,
                    rainEffect = currentProfile.rainIntensity,
                    snowEffect = currentProfile.snowIntensity,
                    discoEffect = currentProfile.discoIntensityLevel,
                ),
                username = currentProfile.username,
                latestTrackPost = trackPosts.firstOrNull(),
                latestMoviePost = moviePosts.firstOrNull(),
                hasTrackPosts = trackPosts.isNotEmpty(),
                hasMoviePosts = moviePosts.isNotEmpty(),
                isClubMember = hasFullAccess,
                isSaving = isSavingStyle,
                initialPage = styleInitialPage,
                onSave = { selections ->
                    val current = StyleSelections(
                        vinylColor = currentProfile.vinylStyle,
                        vinylSpinning = currentProfile.vinylSpinning,
                        frameColor = currentProfile.frameStyle,
                        profileFlair = currentProfile.flairStyle,
                        rainEffect = currentProfile.rainIntensity,
                        snowEffect = currentProfile.snowIntensity,
                        discoEffect = currentProfile.discoIntensityLevel,
                    )
                    val fields = selections.changedFields(current)
                    if (fields.isNotEmpty()) {
                        viewModel.saveStyleSelections(fields)
                        ToastManager.show(context.getString(fm.corus.android.R.string.profile_toast_style_updated))
                    }
                    showStylePicker = false
                },
                onNavigateToClub = {
                    showStylePicker = false
                    clubOfferSource = fm.corus.android.ui.screens.subscription.PaywallSource.STYLE_PICKER
                    showClubOffer = true
                },
                onDismiss = { showStylePicker = false },
            )
        }
    }

    // ── Club Offer Bottom Sheet ──
    if (showClubOffer) {
        ModalBottomSheet(
            onDismissRequest = { showClubOffer = false },
            sheetState = clubSheetState,
            containerColor = CorusColors.Background,
            dragHandle = { BottomSheetDefaults.DragHandle() },
        ) {
            CorusSystemBars()
            fm.corus.android.ui.screens.subscription.CymbalClubOfferSheet(
                source = clubOfferSource,
                onDismiss = { showClubOffer = false },
            )
        }
    }

    // ── Full Screen Avatar Overlay ──
    FullScreenAvatarOverlay(
        avatarURL = currentProfile.avatarURL,
        visible = showFullScreenAvatar,
        onDismiss = { showFullScreenAvatar = false },
    )

    // ── In-app Selfie Capture (CameraX, front-camera forced) ──
    if (showSelfieCapture) {
        SelfieCaptureScreen(
            outputFile = cameraPhotoFile,
            onCaptured = {
                showSelfieCapture = false
                decodeScope.launch {
                    val bmp = withContext(Dispatchers.IO) { uriToBitmap(context, cameraPhotoUri) }
                    cropBitmap = bmp
                }
            },
            onCancel = { showSelfieCapture = false },
        )
    }

    // ── Avatar Crop Overlay ──
    cropBitmap?.let { bitmap ->
        AvatarCropView(
            bitmap = bitmap,
            onConfirm = { croppedBytes ->
                cropBitmap = null
                viewModel.uploadAvatar(croppedBytes)
                ToastManager.show(context.getString(fm.corus.android.R.string.profile_toast_avatar_updated))
            },
            onCancel = { cropBitmap = null },
        )
    }

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
                    viewModel.generatePlaylist()
                }) { androidx.compose.material3.Text("Generate Spotify Playlist") }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = { showPlaylistAlert = false }) {
                    androidx.compose.material3.Text("Cancel")
                }
            },
        )
    }
}

@Composable
private fun StatItem(count: Int, label: String, onClick: (() -> Unit)? = null) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier,
    ) {
        Text(
            text = formattedCount(count),
            style = CorusFont.stat,
            color = CorusColors.Text,
        )
        Text(
            text = label,
            style = CorusFont.statLabel,
            color = CorusColors.Secondary,
        )
    }
}

private fun formattedCount(count: Int): String {
    return when {
        count >= 1_000_000 -> {
            val s = String.format("%.1f", count / 1_000_000.0)
            if (s.endsWith(".0")) "${s.dropLast(2)}M" else "${s}M"
        }
        count >= 10_000 -> "${count / 1000}K"
        count >= 1_000 -> {
            val s = String.format("%.1f", count / 1000.0)
            if (s.endsWith(".0")) "${s.dropLast(2)}K" else "${s}K"
        }
        else -> count.toString()
    }
}

@Composable
private fun StatDivider() {
    Spacer(modifier = Modifier.width(CorusSpacing.sm))
    Text(
        text = "|",
        style = CorusFont.statLabel,
        color = CorusColors.Tertiary,
    )
    Spacer(modifier = Modifier.width(CorusSpacing.sm))
}


@Composable
private fun PostGridItem(post: CymbalPost, isFilmPoster: Boolean = false, onClick: () -> Unit = {}) {
    val aspectRatio = if (isFilmPoster) 2f / 3f else 1f
    val imageSize = if (isFilmPoster) Size(360, 540) else Size(360, 360)
    ShimmerAsyncImage(
        model = ImageRequest.Builder(LocalContext.current)
            .data(post.displayImageLargeURL ?: post.displayImageURL)
            .crossfade(true)
            .size(imageSize)
            .build(),
        contentDescription = post.displayTitle,
        modifier = Modifier
            .aspectRatio(aspectRatio)
            .clickable(onClick = onClick),
    )
}

@Composable
private fun ProfileEmptyPrompt(
    icon: ImageVector,
    title: String,
    subtitle: String,
    buttonText: String,
    onButtonClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(48.dp),
            tint = CorusColors.Accent,
        )
        Spacer(modifier = Modifier.height(CorusSpacing.lg))
        Text(
            text = title,
            style = CorusFont.songTitle,
            color = CorusColors.Text,
        )
        Spacer(modifier = Modifier.height(CorusSpacing.sm))
        Text(
            text = subtitle,
            style = CorusFont.bodyMedium,
            color = CorusColors.Secondary,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(CorusSpacing.lg))
        Button(
            onClick = onButtonClick,
            shape = RoundedCornerShape(50),
            colors = ButtonDefaults.buttonColors(containerColor = CorusColors.Accent),
            contentPadding = PaddingValues(horizontal = CorusSpacing.xl, vertical = CorusSpacing.sm + 2.dp),
        ) {
            Text(
                text = buttonText,
                style = CorusFont.buttonSmall,
                color = Color.White,
            )
        }
    }
}

@Composable
private fun ProfileEmptyPlaceholder(
    icon: ImageVector,
    message: String,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(36.dp),
            tint = CorusColors.Tertiary,
        )
        Spacer(modifier = Modifier.height(CorusSpacing.md))
        Text(
            text = message,
            style = CorusFont.bodyMedium,
            color = CorusColors.Secondary,
        )
    }
}
