package fm.corus.android.ui.screens.profile

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import com.valentinilk.shimmer.shimmer
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material.icons.outlined.NotificationsNone
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.ui.res.painterResource
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import coil3.compose.AsyncImage
import coil3.compose.AsyncImagePainter
import coil3.request.ImageRequest
import coil3.request.crossfade
import coil3.size.Size
import android.content.Intent
import fm.corus.android.data.model.CymbalPost
import fm.corus.android.data.model.CymbalUser
import fm.corus.android.data.model.MediaType
import fm.corus.android.data.remote.CloudFunctionsDataSource
import fm.corus.android.domain.HapticManager
import fm.corus.android.ui.LocalHapticManager
import fm.corus.android.ui.components.CorusHeaderIcon
import fm.corus.android.ui.components.CorusHeaderIconButton
import fm.corus.android.ui.components.FullScreenAvatarOverlay
import fm.corus.android.ui.components.FeaturedCymbalView
import fm.corus.android.ui.components.FeaturedMoviePosterView
import fm.corus.android.ui.components.ShimmerAsyncImage
import fm.corus.android.ui.components.SkeletonProfileGrid
import fm.corus.android.ui.components.SkeletonProfileView
import fm.corus.android.ui.components.SkeletonProfileWithAvatar
import fm.corus.android.ui.components.ToastManager
import fm.corus.android.ui.components.UserAvatarView
import fm.corus.android.ui.components.UsernameWithFlair
import fm.corus.android.ui.theme.CorusColors
import fm.corus.android.ui.theme.CorusFont
import fm.corus.android.ui.theme.CorusSpacing
import fm.corus.android.ui.theme.CorusSystemBars

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OtherProfileScreen(
    userId: String,
    initialAvatarURL: String? = null,
    initialAvatarThumbURL: String? = null,
    initialDisplayName: String? = null,
    initialUsername: String? = null,
    initialBio: String? = null,
    initialCymbalCount: Int? = null,
    initialFollowerCount: Int? = null,
    initialFollowingCount: Int? = null,
    initialIsVerified: Boolean? = null,
    initialIsClubMember: Boolean? = null,
    initialIsFollowing: Boolean? = null,
    viewModel: OtherProfileViewModel = hiltViewModel(),
    onBack: () -> Unit = {},
    onNavigateToProfileFeed: (userId: String, username: String, postId: String, segment: Int) -> Unit = { _, _, _, _ -> },
    onNavigateToUser: (String) -> Unit = {},
    onNavigateToFollowList: (String, Boolean) -> Unit = { _, _ -> },
    onNavigateToMessages: (String, String) -> Unit = { _, _ -> },
) {
    val profile by viewModel.profile.collectAsState()
    val posts by viewModel.posts.collectAsState()
    val musicService by viewModel.musicServicePreference.current.collectAsState()
    var showPlaylistAlert by remember { mutableStateOf(false) }
    val isLoading by viewModel.isLoading.collectAsState()
    val isFollowing by viewModel.isFollowing.collectAsState()
    val isBlocked by viewModel.isBlocked.collectAsState()
    val isMuted by viewModel.isMuted.collectAsState()
    val isSubscribedToNotifications by viewModel.isSubscribedToNotifications.collectAsState()
    val isLoadingMore by viewModel.isLoadingMore.collectAsState()
    val hasMore by viewModel.hasMore.collectAsState()
    val isRefreshing by viewModel.isRefreshing.collectAsState()
    val isLoadingFilms by viewModel.isLoadingFilms.collectAsState()
    val hasFetchedFilmPage by viewModel.hasFetchedFilmPage.collectAsState()
    val engagementStates by viewModel.engagementStates.collectAsState()
    // The user's explicit choice once they've tapped a tab. While null, the
    // selected tab is derived synchronously from the profile data so the
    // first frame already lands on the right tab — no flicker from MUSIC to
    // FILM after a recomposition. Prefers the per-medium counters on the
    // user doc; falls back to deriving from the loaded posts once the page
    // is exhausted (the user doc's counters are often unpopulated since
    // Android doesn't go through the count() cloud function like iOS does).
    var userSelectedSegment by rememberSaveable { mutableStateOf<Int?>(null) }
    val selectedSegment = userSelectedSegment
        ?: profile?.preferredProfileSegmentFromPosts(posts, hasMore)
        ?: 0
    // Other-profile views show MUSIC/FILM/LIKES (no Saves). Bots only show
    // their single tab and always map to posts. Segment 2 == LIKES.
    val playlistSource: CloudFunctionsDataSource.ProfilePlaylistSource = when {
        profile?.isBot == true -> CloudFunctionsDataSource.ProfilePlaylistSource.Posts
        selectedSegment == 2 -> CloudFunctionsDataSource.ProfilePlaylistSource.Likes
        else -> CloudFunctionsDataSource.ProfilePlaylistSource.Posts
    }
    var isFeaturedArtReady by rememberSaveable { mutableStateOf(false) }
    var didRevealFromSkeleton by rememberSaveable { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }
    var showAvatarFullScreen by remember { mutableStateOf(false) }
    var showClubOffer by remember { mutableStateOf(false) }
    val gridState = rememberLazyGridState()

    val paywallRequested by viewModel.nowPlayingManager.paywallRequested.collectAsState()
    LaunchedEffect(paywallRequested) {
        if (paywallRequested) {
            showClubOffer = true
            viewModel.nowPlayingManager.clearPaywallRequested()
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
    LaunchedEffect(shouldLoadMore) {
        if (shouldLoadMore && hasMore && !isLoadingMore && !isLoading) {
            viewModel.loadMore(userId)
        }
    }

    LaunchedEffect(userId) {
        viewModel.start(userId, initialIsFollowing)
    }

    // When the user switches to the FILM tab, fetch movie-only posts so films
    // older than the recency-sorted first page still appear without a manual refresh.
    LaunchedEffect(selectedSegment, userId, profile?.isBot) {
        val isFilmsTab = profile?.isBot == false && selectedSegment == 1
        if (isFilmsTab) {
            viewModel.loadFilmPageIfNeeded(userId)
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = { },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(fm.corus.android.R.string.common_back), tint = CorusColors.Text)
                    }
                },
                actions = {
                    // Post notifications bell button (matching iOS)
                    val notifContext = LocalContext.current
                    IconButton(onClick = {
                        val username = profile?.username ?: ""
                        if (!isSubscribedToNotifications) {
                            ToastManager.show(notifContext.getString(fm.corus.android.R.string.other_profile_toast_will_notify_format, username))
                        } else {
                            ToastManager.show(notifContext.getString(fm.corus.android.R.string.other_profile_toast_notifications_off_format, username))
                        }
                        viewModel.togglePostNotifications(userId)
                    }) {
                        Icon(
                            imageVector = if (isSubscribedToNotifications)
                                Icons.Filled.Notifications
                            else
                                Icons.Outlined.NotificationsNone,
                            contentDescription = if (isSubscribedToNotifications) stringResource(fm.corus.android.R.string.other_profile_cd_stop_notifying) else stringResource(fm.corus.android.R.string.other_profile_cd_post_notifications),
                            tint = if (isSubscribedToNotifications) CorusColors.Accent else CorusColors.Text,
                            modifier = Modifier.size(22.dp),
                        )
                    }

                    // Dedicated message button (matching iOS outlined envelope icon)
                    IconButton(onClick = { onNavigateToMessages("", userId) }) {
                        Icon(Icons.Outlined.Email, contentDescription = stringResource(fm.corus.android.R.string.other_profile_cd_message), tint = CorusColors.Text, modifier = Modifier.size(20.dp))
                    }

                    Box {
                        val menuContext = LocalContext.current
                        val hasSongs = posts.any { it.mediaType == MediaType.TRACK }
                        val isGeneratingPlaylist by viewModel.nowPlayingManager.isGeneratingPlaylist.collectAsState()

                        IconButton(onClick = { showMenu = true }) {
                            Icon(Icons.Filled.MoreVert, contentDescription = stringResource(fm.corus.android.R.string.other_profile_cd_menu), tint = CorusColors.Text)
                        }
                        DropdownMenu(
                            expanded = showMenu,
                            onDismissRequest = { showMenu = false },
                        ) {
                            // View Spotify Playlist (only for non-film-bot profiles)
                            if (profile?.isFilmBot != true) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(fm.corus.android.R.string.other_profile_menu_view_playlist), style = CorusFont.body) },
                                    leadingIcon = {
                                        Icon(
                                            painter = painterResource(fm.corus.android.R.drawable.ic_music_note_list),
                                            contentDescription = null,
                                            modifier = Modifier.size(20.dp),
                                        )
                                    },
                                    enabled = hasSongs && !isGeneratingPlaylist,
                                    onClick = {
                                        showMenu = false
                                        val isApple = musicService == fm.corus.android.data.model.MusicService.APPLE_MUSIC
                                        val hasSoundCloud = playlistSource == CloudFunctionsDataSource.ProfilePlaylistSource.Posts
                                            && posts.any { it.isTrack && it.track.source == fm.corus.android.data.model.TrackSource.SOUNDCLOUD }
                                        if (isApple || hasSoundCloud) {
                                            showPlaylistAlert = true
                                        } else {
                                            viewModel.generatePlaylist(userId, playlistSource)
                                        }
                                    },
                                )
                            }
                            // Share Profile
                            DropdownMenuItem(
                                text = { Text(stringResource(fm.corus.android.R.string.other_profile_menu_share_profile), style = CorusFont.body) },
                                leadingIcon = {
                                    Icon(
                                        Icons.Filled.Share,
                                        contentDescription = null,
                                    )
                                },
                                onClick = {
                                    showMenu = false
                                    val username = profile?.username ?: ""
                                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                        type = "text/plain"
                                        putExtra(Intent.EXTRA_TEXT, "https://corus.fm/u/$username")
                                    }
                                    menuContext.startActivity(Intent.createChooser(shareIntent, null))
                                },
                            )
                            // Mute/Unmute
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        if (isMuted) stringResource(fm.corus.android.R.string.other_profile_menu_unmute) else stringResource(fm.corus.android.R.string.other_profile_menu_mute),
                                        style = CorusFont.body,
                                    )
                                },
                                leadingIcon = {
                                    Icon(
                                        if (isMuted) Icons.Filled.VolumeUp else Icons.Filled.VolumeOff,
                                        contentDescription = null,
                                    )
                                },
                                onClick = {
                                    viewModel.toggleMute(userId)
                                    showMenu = false
                                },
                            )
                            if (profile?.isBot != true) {
                                HorizontalDivider()
                                // Report
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            stringResource(fm.corus.android.R.string.other_profile_menu_report),
                                            style = CorusFont.body,
                                            color = CorusColors.Error,
                                        )
                                    },
                                    onClick = { showMenu = false },
                                )
                                // Block/Unblock
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            if (isBlocked) stringResource(fm.corus.android.R.string.other_profile_menu_unblock) else stringResource(fm.corus.android.R.string.other_profile_menu_block),
                                            style = CorusFont.body,
                                            color = CorusColors.Error,
                                        )
                                    },
                                    onClick = {
                                        showMenu = false
                                        if (isBlocked) viewModel.unblockUser(userId)
                                        else viewModel.blockUser(userId)
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
        val hasInitialData = initialDisplayName != null && initialUsername != null
        if (isLoading && profile == null) {
            if (hasInitialData) {
                // Show real header with initial data from the feed; only shimmer the posts grid
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(top = padding.calculateTopPadding()),
                    contentPadding = PaddingValues(bottom = padding.calculateBottomPadding()),
                ) {
                    item(span = { GridItemSpan(3) }) {
                        Column {
                            // Display name
                            Text(
                                text = initialDisplayName,
                                style = CorusFont.displayName,
                                color = CorusColors.Text,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth(),
                            )

                            // Avatar + Stats Row
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = CorusSpacing.lg, vertical = CorusSpacing.md),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                UserAvatarView(
                                    avatarURL = initialAvatarURL,
                                    avatarThumbURL = initialAvatarThumbURL,
                                    displayName = initialDisplayName,
                                    size = CorusSpacing.avatarLarge,
                                )

                                Spacer(modifier = Modifier.width(CorusSpacing.md))

                                Column(
                                    modifier = Modifier.weight(1f),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(CorusSpacing.xxl),
                                    ) {
                                        StatItem(count = initialCymbalCount ?: 0, label = stringResource(fm.corus.android.R.string.profile_stat_coruses))
                                        StatItem(count = initialFollowerCount ?: 0, label = stringResource(fm.corus.android.R.string.profile_stat_followers))
                                        StatItem(count = initialFollowingCount ?: 0, label = stringResource(fm.corus.android.R.string.profile_stat_following))
                                    }

                                    Spacer(modifier = Modifier.height(CorusSpacing.sm))

                                    // Follow + Playlist buttons (non-interactive placeholders)
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(CorusSpacing.sm),
                                    ) {
                                        val followShape = RoundedCornerShape(50)
                                        val hintFollowing = initialIsFollowing == true
                                        Box(
                                            modifier = Modifier
                                                .clip(followShape)
                                                .then(
                                                    if (hintFollowing) Modifier.border(1.dp, CorusColors.Divider, followShape)
                                                    else Modifier.background(CorusColors.Accent)
                                                )
                                                .padding(vertical = 6.dp, horizontal = 36.dp),
                                            contentAlignment = Alignment.Center,
                                        ) {
                                            Text(
                                                text = if (hintFollowing) stringResource(fm.corus.android.R.string.other_profile_button_following) else stringResource(fm.corus.android.R.string.other_profile_button_follow),
                                                style = CorusFont.button,
                                                color = if (hintFollowing) CorusColors.Secondary else Color.White,
                                            )
                                        }

                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(50))
                                                .border(1.dp, CorusColors.Divider, RoundedCornerShape(50))
                                                .padding(vertical = 6.dp, horizontal = CorusSpacing.md),
                                            contentAlignment = Alignment.Center,
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(CorusSpacing.xs),
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
                                        }
                                    }
                                }
                            }

                            // Username + Bio
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(start = 28.dp, end = CorusSpacing.lg),
                            ) {
                                UsernameWithFlair(
                                    username = initialUsername,
                                    isBot = false,
                                    isVerified = initialIsVerified ?: false,
                                    isClubMember = initialIsClubMember ?: false,
                                    showAtPrefix = true,
                                    style = CorusFont.username,
                                    color = CorusColors.Text,
                                )

                                if (!initialBio.isNullOrBlank()) {
                                    Spacer(modifier = Modifier.height(CorusSpacing.xs))
                                    Text(
                                        text = initialBio,
                                        style = CorusFont.bio,
                                        color = CorusColors.Secondary,
                                        maxLines = 3,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(CorusSpacing.lg))

                            // Segment tabs
                            val selectedLineColor = CorusColors.Text
                            val unselectedLineColor = CorusColors.Divider
                            Row(modifier = Modifier.fillMaxWidth()) {
                                listOf(
                                    stringResource(fm.corus.android.R.string.profile_tab_music),
                                    stringResource(fm.corus.android.R.string.profile_tab_film),
                                    stringResource(fm.corus.android.R.string.other_profile_tab_likes),
                                ).forEachIndexed { index, title ->
                                    val isSelected = index == 0
                                    Column(
                                        modifier = Modifier
                                            .weight(1f)
                                            .drawBehind {
                                                val strokeWidth = if (isSelected) 3.dp.toPx() else 0.5.dp.toPx()
                                                val lineColor = if (isSelected) selectedLineColor else unselectedLineColor
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

                            // Skeleton grid for posts
                            SkeletonProfileGrid()
                        }
                    }
                }
                return@Scaffold
            }

            // No initial data — show full skeleton
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
            ) {
                if (initialAvatarURL != null || initialAvatarThumbURL != null) {
                    SkeletonProfileWithAvatar(
                        avatarURL = initialAvatarURL,
                        avatarThumbURL = initialAvatarThumbURL,
                    )
                } else {
                    SkeletonProfileView()
                }
                SkeletonProfileGrid()
            }
            return@Scaffold
        }

        val currentProfile = profile ?: return@Scaffold

        if (isBlocked) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Filled.Block,
                        contentDescription = null,
                        modifier = Modifier.size(48.dp),
                        tint = CorusColors.Tertiary,
                    )
                    Spacer(modifier = Modifier.height(CorusSpacing.md))
                    Text(stringResource(fm.corus.android.R.string.other_profile_blocked_message), style = CorusFont.bodyMedium, color = CorusColors.Secondary)
                    Spacer(modifier = Modifier.height(CorusSpacing.sm))
                    TextButton(onClick = { viewModel.unblockUser(userId) }) {
                        Text(stringResource(fm.corus.android.R.string.common_unblock), style = CorusFont.button, color = CorusColors.Accent)
                    }
                }
            }
            return@Scaffold
        }

        // Helper: populate cache and navigate to profile feed
        val navigateToFeed: (String) -> Unit = { postId ->
            val filteredForNav = when {
                currentProfile.isMusicBot -> posts.filter { it.mediaType == MediaType.TRACK }
                currentProfile.isFilmBot -> posts.filter { it.mediaType == MediaType.MOVIE }
                else -> when (selectedSegment) {
                    0 -> posts.filter { it.mediaType == MediaType.TRACK }
                    1 -> posts.filter { it.mediaType == MediaType.MOVIE }
                    2 -> posts
                    else -> posts
                }
            }
            ProfileFeedCache.posts = filteredForNav
            ProfileFeedCache.hasMore = hasMore
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
                // Mirrors iOS OtherProfileView.refreshable haptic.
                haptics.impact(HapticManager.ImpactStyle.LIGHT)
                val onFilmsTab = !currentProfile.isBot && selectedSegment == 1
                viewModel.refresh(userId, includeFilms = onFilmsTab)
            },
            modifier = Modifier
                .fillMaxSize()
                .padding(top = padding.calculateTopPadding()),
        ) {
        LazyVerticalGrid(
            state = gridState,
            columns = GridCells.Fixed(3),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = padding.calculateBottomPadding()),
        ) {
            item(span = { GridItemSpan(3) }) {
                Column {
                    // Display name centered (matching iOS)
                    Text(
                        text = currentProfile.displayName,
                        style = CorusFont.displayName,
                        color = CorusColors.Text,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )

                    // Avatar + Stats Row
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = CorusSpacing.lg, vertical = CorusSpacing.md),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        UserAvatarView(
                            avatarURL = currentProfile.avatarURL,
                            displayName = currentProfile.displayName,
                            size = CorusSpacing.avatarLarge,
                            modifier = Modifier.clickable { showAvatarFullScreen = true },
                        )

                        Spacer(modifier = Modifier.width(CorusSpacing.md))

                        Column(
                            modifier = Modifier.weight(1f),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            // Stats
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(CorusSpacing.xxl),
                            ) {
                                StatItem(count = currentProfile.cymbalCount, label = stringResource(fm.corus.android.R.string.profile_stat_coruses))
                                StatItem(
                                    count = currentProfile.followerCount,
                                    label = stringResource(fm.corus.android.R.string.profile_stat_followers),
                                    modifier = Modifier.clickable { onNavigateToFollowList(userId, true) },
                                )
                                StatItem(
                                    count = currentProfile.followingCount,
                                    label = stringResource(fm.corus.android.R.string.profile_stat_following),
                                    modifier = Modifier.clickable { onNavigateToFollowList(userId, false) },
                                )
                            }

                            Spacer(modifier = Modifier.height(CorusSpacing.sm))

                            // Follow button + Playlist button
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(CorusSpacing.sm),
                            ) {
                                // Follow button — matching iOS Capsule with fill/stroke
                                val followShape = RoundedCornerShape(50)
                                Box(
                                    modifier = Modifier
                                        .clip(followShape)
                                        .then(
                                            if (isFollowing) Modifier.border(1.dp, CorusColors.Divider, followShape)
                                            else Modifier.background(CorusColors.Accent)
                                        )
                                        .clickable { viewModel.toggleFollow(userId) }
                                        .padding(vertical = 6.dp, horizontal = 20.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(
                                        text = if (isFollowing) stringResource(fm.corus.android.R.string.other_profile_button_following) else stringResource(fm.corus.android.R.string.other_profile_button_follow),
                                        style = CorusFont.button,
                                        color = if (isFollowing) CorusColors.Secondary else Color.White,
                                        maxLines = 1,
                                    )
                                }

                                // Playlist button
                                val playlistContext = LocalContext.current
                                // Likes are lazy-loaded into a separate flow; let the backend respond
                                // when the user has none.
                                val hasSongs = when (playlistSource) {
                                    CloudFunctionsDataSource.ProfilePlaylistSource.Posts ->
                                        posts.any { it.mediaType == MediaType.TRACK }
                                    else -> true
                                }
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
                                        .clip(RoundedCornerShape(50))
                                        .border(1.dp, CorusColors.Divider, RoundedCornerShape(50))
                                        .clickable(enabled = hasSongs && !isGeneratingPlaylist) {
                                            if (!hasSongs) {
                                                ToastManager.show(playlistContext.getString(fm.corus.android.R.string.profile_toast_no_songs_for_playlist))
                                            } else {
                                                val isApple = musicService == fm.corus.android.data.model.MusicService.APPLE_MUSIC
                                                val hasSoundCloud = playlistSource == CloudFunctionsDataSource.ProfilePlaylistSource.Posts
                                                    && posts.any { it.isTrack && it.track.source == fm.corus.android.data.model.TrackSource.SOUNDCLOUD }
                                                if (isApple || hasSoundCloud) {
                                                    showPlaylistAlert = true
                                                } else {
                                                    viewModel.generatePlaylist(userId, playlistSource)
                                                }
                                            }
                                        }
                                        .padding(vertical = 6.dp, horizontal = CorusSpacing.md),
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
                                            contentDescription = "Playlist",
                                            modifier = Modifier.size(14.dp),
                                            tint = CorusColors.Secondary,
                                        )
                                        Text(
                                            text = stringResource(fm.corus.android.R.string.profile_button_playlist),
                                            style = CorusFont.button,
                                            color = CorusColors.Secondary,
                                            maxLines = 1,
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

                    // Username + Bio + Website
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 28.dp, end = CorusSpacing.lg),
                    ) {
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

                        if (!currentProfile.website.isNullOrBlank()) {
                            Spacer(modifier = Modifier.height(CorusSpacing.xxs))
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

                    // Segment control — bots only show their content type (no tabs)
                    val tabMusic = stringResource(fm.corus.android.R.string.profile_tab_music)
                    val tabFilm = stringResource(fm.corus.android.R.string.profile_tab_film)
                    val tabLikes = stringResource(fm.corus.android.R.string.other_profile_tab_likes)
                    val tabs = when {
                        currentProfile.isMusicBot -> listOf(tabMusic)
                        currentProfile.isFilmBot -> listOf(tabFilm)
                        else -> listOf(tabMusic, tabFilm, tabLikes)
                    }
                    if (!currentProfile.isBot) {
                        val tabSelectedColor = CorusColors.Text
                        val tabUnselectedColor = CorusColors.Divider
                        Row(modifier = Modifier.fillMaxWidth()) {
                            tabs.forEachIndexed { index, title ->
                                val isSelected = selectedSegment == index
                                Column(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clickable {
                                            userSelectedSegment = index
                                            isFeaturedArtReady = false
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
                    }

                    // Filter posts by segment
                    val filteredPosts = when {
                        currentProfile.isMusicBot -> posts.filter { it.mediaType == MediaType.TRACK }
                        currentProfile.isFilmBot -> posts.filter { it.mediaType == MediaType.MOVIE }
                        else -> when (selectedSegment) {
                            0 -> posts.filter { it.mediaType == MediaType.TRACK }
                            1 -> posts.filter { it.mediaType == MediaType.MOVIE }
                            2 -> posts // Likes — show all (ViewModel would handle this)
                            else -> posts
                        }
                    }

                    // Whether we're on a "featured" tab (music/film, not likes)
                    val isFeaturedTab = when {
                        currentProfile.isBot -> true // Bots always show featured
                        else -> selectedSegment <= 1
                    }

                    // Featured post — only for Music/Film tabs (matching iOS)
                    // Show skeleton until the featured art image has loaded
                    if (filteredPosts.isNotEmpty() && isFeaturedTab) {
                        val featured = filteredPosts.first()
                        val userProfile = profile
                        if (userProfile != null && !isFeaturedArtReady) {
                            Box {
                                Box(modifier = Modifier
                                    .fillMaxWidth()
                                    .alpha(0f)
                                ) {
                                    if (featured.mediaType == MediaType.MOVIE) {
                                        FeaturedMoviePosterView(
                                            post = featured,
                                            frameStyle = userProfile.frameStyle,
                                            onArtReady = { isFeaturedArtReady = true },
                                        )
                                    } else {
                                        FeaturedCymbalView(
                                            post = featured,
                                            vinylStyle = userProfile.vinylStyle,
                                            onArtReady = { isFeaturedArtReady = true },
                                        )
                                    }
                                }
                                SkeletonProfileGrid(isFilmStyle = featured.mediaType == MediaType.MOVIE)
                            }
                        } else if (userProfile != null) {
                            val featuredEngagement = engagementStates[featured.id]
                            if (featured.mediaType == MediaType.MOVIE) {
                                FeaturedMoviePosterView(
                                    post = featured,
                                    frameStyle = userProfile.frameStyle,
                                    rainIntensity = userProfile.rainIntensity,
                                    snowIntensity = userProfile.snowIntensity,
                                    discoIntensity = userProfile.discoIntensityLevel,
                                    likeCount = featuredEngagement?.likeCount ?: featured.likeCount,
                                    isLiked = featuredEngagement?.isLiked ?: featured.isLiked,
                                    onLikeTap = { viewModel.toggleLike(featured.id) },
                                    onPostTap = { navigateToFeed(featured.id) },
                                )
                            } else {
                                val shouldStagger = !didRevealFromSkeleton
                                LaunchedEffect(Unit) { didRevealFromSkeleton = true }
                                FeaturedCymbalView(
                                    post = featured,
                                    vinylStyle = userProfile.vinylStyle,
                                    rainIntensity = userProfile.rainIntensity,
                                    snowIntensity = userProfile.snowIntensity,
                                    discoIntensity = userProfile.discoIntensityLevel,
                                    likeCount = featuredEngagement?.likeCount ?: featured.likeCount,
                                    isLiked = featuredEngagement?.isLiked ?: featured.isLiked,
                                    onLikeTap = { viewModel.toggleLike(featured.id) },
                                    onPostTap = { navigateToFeed(featured.id) },
                                    staggerVinyl = shouldStagger,
                                )
                            }
                        }
                    } else if (filteredPosts.isEmpty() && !currentProfile.isBot && selectedSegment == 1 && (isLoadingFilms || !hasFetchedFilmPage)) {
                        // Films pending — show skeleton until we've either fetched the
                        // movie-only page or determined (via the free guard) that there
                        // are none. Including !hasFetchedFilmPage covers the gap between
                        // the tab tap and LaunchedEffect firing the fetch.
                        SkeletonProfileGrid(isFilmStyle = true)
                    } else if (filteredPosts.isEmpty() && !isLoading) {
                        // Empty state per segment (matching iOS: icon + text, no emoji)
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(200.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            val noSongsMsg = stringResource(fm.corus.android.R.string.other_profile_empty_no_songs)
                            val noFilmsMsg = stringResource(fm.corus.android.R.string.other_profile_empty_no_films)
                            val noLikedMsg = stringResource(fm.corus.android.R.string.other_profile_empty_no_liked)
                            val (icon, message) = when {
                                currentProfile.isMusicBot || (!currentProfile.isBot && selectedSegment == 0) ->
                                    Icons.Filled.MusicNote to noSongsMsg
                                currentProfile.isFilmBot || (!currentProfile.isBot && selectedSegment == 1) ->
                                    Icons.Filled.Movie to noFilmsMsg
                                else -> Icons.Outlined.FavoriteBorder to noLikedMsg
                            }
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    imageVector = icon,
                                    contentDescription = null,
                                    modifier = Modifier.size(36.dp),
                                    tint = CorusColors.Tertiary,
                                )
                                Spacer(modifier = Modifier.height(CorusSpacing.sm))
                                Text(message, style = CorusFont.bodyMedium, color = CorusColors.Secondary)
                            }
                        }
                    }
                }
            }

            // Album art grid (filtered)
            @Suppress("NAME_SHADOWING")
            val filteredPosts = when {
                currentProfile.isMusicBot -> posts.filter { it.mediaType == MediaType.TRACK }
                currentProfile.isFilmBot -> posts.filter { it.mediaType == MediaType.MOVIE }
                else -> when (selectedSegment) {
                    0 -> posts.filter { it.mediaType == MediaType.TRACK }
                    1 -> posts.filter { it.mediaType == MediaType.MOVIE }
                    2 -> posts
                    else -> posts
                }
            }
            // For Likes, show all posts in grid (no featured);
            // for Music/Film (and bots), skip the first post (already shown as featured)
            @Suppress("NAME_SHADOWING")
            val isFeaturedTab = currentProfile.isBot || selectedSegment <= 1
            val gridPosts = if (isFeaturedTab) filteredPosts.drop(1) else filteredPosts
            // Hide grid while featured art is loading (skeleton in header covers both areas)
            if (isFeaturedTab && !isFeaturedArtReady && filteredPosts.isNotEmpty()) {
                // SkeletonProfileGrid in header already covers grid area
            } else if (gridPosts.isNotEmpty()) {
                val isFilmPoster = currentProfile.isFilmBot || (!currentProfile.isMusicBot && selectedSegment == 1)
                items(gridPosts, key = { it.id }) { post ->
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
                            .clickable { navigateToFeed(post.id) },
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

    // Full-screen avatar overlay (outside Scaffold to cover entire screen)
    FullScreenAvatarOverlay(
        avatarURL = profile?.avatarURL,
        visible = showAvatarFullScreen,
        onDismiss = { showAvatarFullScreen = false },
    )

    // Club offer sheet
    if (showClubOffer) {
        val clubSheetState = androidx.compose.material3.rememberModalBottomSheetState(skipPartiallyExpanded = true)
        androidx.compose.material3.ModalBottomSheet(
            onDismissRequest = { showClubOffer = false },
            sheetState = clubSheetState,
            containerColor = CorusColors.Background,
            dragHandle = { androidx.compose.material3.BottomSheetDefaults.DragHandle() },
        ) {
            CorusSystemBars()
            BackHandler { showClubOffer = false }
            fm.corus.android.ui.screens.subscription.CymbalClubOfferSheet(
                source = fm.corus.android.ui.screens.subscription.PaywallSource.PLAYLIST_LIMIT,
                onDismiss = { showClubOffer = false },
            )
        }
    }

    if (showPlaylistAlert) {
        val hasSoundCloud = playlistSource == CloudFunctionsDataSource.ProfilePlaylistSource.Posts
            && posts.any { it.isTrack && it.track.source == fm.corus.android.data.model.TrackSource.SOUNDCLOUD }
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
                    viewModel.generatePlaylist(userId, playlistSource)
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
private fun StatItem(count: Int, label: String, modifier: Modifier = Modifier) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = modifier) {
        Text(text = count.toString(), style = CorusFont.stat, color = CorusColors.Text)
        Text(text = label, style = CorusFont.statLabel, color = CorusColors.Secondary)
    }
}

@Composable
private fun StatDivider() {
    Spacer(modifier = Modifier.width(CorusSpacing.sm))
    Text("|", style = CorusFont.statLabel, color = CorusColors.Tertiary)
    Spacer(modifier = Modifier.width(CorusSpacing.sm))
}
