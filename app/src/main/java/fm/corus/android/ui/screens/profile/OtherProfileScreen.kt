package fm.corus.android.ui.screens.profile

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.EaseInOut
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.StartOffset
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
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
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
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
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.pullToRefresh
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
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
import androidx.compose.ui.platform.LocalConfiguration
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
import android.net.Uri
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import fm.corus.android.data.model.CymbalPost
import fm.corus.android.data.model.CymbalUser
import fm.corus.android.data.model.MediaType
import fm.corus.android.data.remote.CloudFunctionsDataSource
import fm.corus.android.domain.HapticManager
import fm.corus.android.ui.LocalHapticManager
import fm.corus.android.ui.components.CorusHeaderIcon
import fm.corus.android.ui.components.CorusHeaderIconButton
import fm.corus.android.ui.components.ExpandableBioText
import fm.corus.android.ui.components.FullScreenAvatarOverlay
import fm.corus.android.ui.components.ImmersiveFrostedBar
import fm.corus.android.ui.components.rememberImmersiveHeaderState
import fm.corus.android.ui.components.FeaturedCymbalView
import fm.corus.android.ui.components.FeaturedMoviePosterView
import fm.corus.android.ui.components.ShareMediaSheet
import fm.corus.android.ui.components.ShareMediaSubject
import fm.corus.android.ui.components.ShareProfileSubject
import fm.corus.android.ui.components.ShimmerAsyncImage
import fm.corus.android.ui.components.SkeletonProfileGrid
import fm.corus.android.ui.components.SkeletonProfileView
import fm.corus.android.ui.components.SkeletonProfileWithAvatar
import fm.corus.android.ui.components.TasteMatchSheet
import fm.corus.android.ui.components.TasteMatchTeaser
import fm.corus.android.ui.components.ToastManager
import fm.corus.android.ui.components.UserAvatarView
import fm.corus.android.ui.components.UsernameWithFlair
import fm.corus.android.ui.theme.CorusColors
import fm.corus.android.ui.theme.CorusFont
import fm.corus.android.ui.theme.CorusSpacing
import fm.corus.android.ui.theme.CorusSystemBars
import fm.corus.android.ui.util.formattedCount

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
    onNavigateToFollowList: (String, Boolean, String, Int, Int) -> Unit = { _, _, _, _, _ -> },
    onNavigateToMessages: (String, String) -> Unit = { _, _ -> },
    onNavigateToPost: (postId: String) -> Unit = {},
) {
    // Responsive header spacing: wider phones (~Pixel 9 Pro) get a more generous
    // inset; narrower phones (~Galaxy S) keep the original tighter layout so the
    // PLAYLIST/FOLLOW labels never truncate and the avatar aligns with the
    // taste-match pill below it.
    val isWideHeader = LocalConfiguration.current.screenWidthDp >= 400
    val headerHPad = if (isWideHeader) 28.dp else CorusSpacing.xl
    // The FOLLOWING pill no longer carries its own horizontal padding: it's
    // weighted to fill the leftover row width with a shrink-to-fit label, so
    // only the unweighted PLAYLIST pill needs an explicit inset.
    val playlistHPad = if (isWideHeader) CorusSpacing.xxl else CorusSpacing.md
    val headerAvatarSize = if (isWideHeader) CorusSpacing.avatarLarge else 68.dp
    // Avatar + username + bio sit slightly inside the taste-match pill's left
    // edge — matches iOS.
    val avatarHPad = headerHPad + 8.dp
    val usernameStartPad = avatarHPad
    val usernameEndPad = avatarHPad
    val pillHPad = headerHPad - 4.dp

    val profile by viewModel.profile.collectAsState()
    val posts by viewModel.posts.collectAsState()
    val musicService by viewModel.musicServicePreference.current.collectAsState()
    var showPlaylistAlert by remember { mutableStateOf(false) }
    var showPlaylistChooser by remember { mutableStateOf(false) }
    // Playlist export isn't available for YouTube Music yet, so a YT Music viewer
    // gets an explainer that offers a Spotify playlist instead (mirrors the feed).
    var showYouTubeMusicPlaylistExplainer by remember { mutableStateOf(false) }
    val isLoading by viewModel.isLoading.collectAsState()
    val profileUnavailable by viewModel.profileUnavailable.collectAsState()
    val isFollowing by viewModel.isFollowing.collectAsState()
    val followsMe by viewModel.followsMe.collectAsState()
    val isBlocked by viewModel.isBlocked.collectAsState()
    val isMuted by viewModel.isMuted.collectAsState()
    val isSubscribedToNotifications by viewModel.isSubscribedToNotifications.collectAsState()
    val isFavorite by viewModel.isFavorite.collectAsState()
    val matchData by viewModel.matchData.collectAsState()
    var showMatchSheet by remember { mutableStateOf(false) }
    val isOwnProfile = viewModel.currentUserId == userId
    val isLoadingMore by viewModel.isLoadingMore.collectAsState()
    val hasMore by viewModel.hasMore.collectAsState()
    val isRefreshing by viewModel.isRefreshing.collectAsState()
    val isLoadingFilms by viewModel.isLoadingFilms.collectAsState()
    val hasFetchedFilmPage by viewModel.hasFetchedFilmPage.collectAsState()
    val isLoadingSongs by viewModel.isLoadingSongs.collectAsState()
    val hasFetchedSongPage by viewModel.hasFetchedSongPage.collectAsState()
    val likedPosts by viewModel.likedPosts.collectAsState()
    val isLoadingLiked by viewModel.isLoadingLiked.collectAsState()
    val likedHasMore by viewModel.likedHasMore.collectAsState()
    val hasFetchedLikedPage by viewModel.hasFetchedLikedPage.collectAsState()
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
    var showShareSheet by remember { mutableStateOf(false) }
    val recentShareContacts by viewModel.recentShareContacts.collectAsState()
    val shareSearchResults by viewModel.shareSearchResults.collectAsState()
    val isShareSearching by viewModel.isShareSearching.collectAsState()
    val isLoadingShareContacts by viewModel.isLoadingShareContacts.collectAsState()
    var showAvatarFullScreen by remember { mutableStateOf(false) }
    var showClubOffer by remember { mutableStateOf(false) }
    var clubOfferSource by remember {
        mutableStateOf(fm.corus.android.ui.screens.subscription.PaywallSource.PLAYLIST_LIMIT)
    }
    val gridState = rememberLazyGridState()

    val paywallRequested by viewModel.nowPlayingManager.paywallRequested.collectAsState()
    LaunchedEffect(paywallRequested) {
        if (paywallRequested) {
            clubOfferSource = fm.corus.android.ui.screens.subscription.PaywallSource.PLAYLIST_LIMIT
            showClubOffer = true
            viewModel.nowPlayingManager.clearPaywallRequested()
        }
    }

    val favoriteCapPaywallRequested by viewModel.favoriteCapPaywallRequested.collectAsState()
    LaunchedEffect(favoriteCapPaywallRequested) {
        if (favoriteCapPaywallRequested) {
            clubOfferSource = fm.corus.android.ui.screens.subscription.PaywallSource.FAVORITE_LIMIT
            showClubOffer = true
            viewModel.clearFavoriteCapPaywallRequested()
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
        if (shouldLoadMore && !isLoadingMore && !isLoading) {
            // The LIKES tab paginates its own dedicated list; Music/Film share
            // the owner's mixed-media posts cursor.
            if (selectedSegment == 2 && profile?.isBot == false) {
                if (likedHasMore) viewModel.loadMoreLiked(userId)
            } else if (hasMore) {
                viewModel.loadMore(userId)
            }
        }
    }

    LaunchedEffect(userId) {
        viewModel.start(userId, initialIsFollowing)
    }

    // Lazy-load the owner's liked posts the first time the LIKES tab is shown.
    LaunchedEffect(selectedSegment, userId, profile?.isBot) {
        if (profile?.isBot == false && selectedSegment == 2) {
            viewModel.loadLikedPosts(userId)
        }
    }

    // When the user switches to the FILM tab, fetch movie-only posts so films
    // older than the recency-sorted first page still appear without a manual refresh.
    LaunchedEffect(selectedSegment, userId, profile?.isBot) {
        val isFilmsTab = profile?.isBot == false && selectedSegment == 1
        if (isFilmsTab) {
            viewModel.loadFilmPageIfNeeded(userId)
        }
    }

    // When the user switches to the MUSIC tab, backfill song-only posts so songs
    // older than the recency-sorted first page still appear (and the empty state
    // is held off behind the skeleton). Symmetric to the FILM tab fetch above.
    LaunchedEffect(selectedSegment, userId, profile?.isBot) {
        val isMusicTab = profile?.isBot == false && selectedSegment == 0
        if (isMusicTab) {
            viewModel.loadSongPageIfNeeded(userId)
        }
    }

    val immersive = viewModel.immersiveArtistHeaderEnabled
    val frost = rememberImmersiveHeaderState(immersive)

    // Profile action icons (message / notify / favorite / menu) — shared between
    // the plain TopAppBar (non-immersive) and the frosted bar (immersive) so there
    // is a single definition.
    val profileActions: @Composable RowScope.() -> Unit = {
                    // Dedicated message button (matching iOS outlined envelope icon)
                    IconButton(onClick = { onNavigateToMessages("", userId) }) {
                        Icon(Icons.Outlined.Email, contentDescription = stringResource(fm.corus.android.R.string.other_profile_cd_message), tint = CorusColors.Text, modifier = Modifier.size(20.dp))
                    }

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

                    // Favorites star button (matching iOS), gated by remote config
                    if (viewModel.favoritesEnabled) {
                        IconButton(onClick = {
                            val username = profile?.username ?: ""
                            val nowFavorite = viewModel.toggleFavorite(userId)
                            ToastManager.show(
                                notifContext.getString(
                                    if (nowFavorite) fm.corus.android.R.string.other_profile_toast_favorite_added_format
                                    else fm.corus.android.R.string.other_profile_toast_favorite_removed_format,
                                    username
                                )
                            )
                        }) {
                            Icon(
                                imageVector = if (isFavorite) Icons.Filled.Star else Icons.Filled.StarBorder,
                                contentDescription = stringResource(
                                    if (isFavorite) fm.corus.android.R.string.other_profile_cd_remove_favorite
                                    else fm.corus.android.R.string.other_profile_cd_add_favorite
                                ),
                                tint = if (isFavorite) CorusColors.Accent else CorusColors.Text,
                                modifier = Modifier.size(22.dp),
                            )
                        }
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
                                        if (musicService == fm.corus.android.data.model.MusicService.YOUTUBE_MUSIC) {
                                            // No YouTube Music playlist export yet — explain, then
                                            // offer a Spotify playlist (explainer keeps quick vs all).
                                            showYouTubeMusicPlaylistExplainer = true
                                        } else if (fm.corus.android.domain.shouldOfferProfileFullExport(
                                                selectedSegment, profile?.trackCount, profile?.likesCount, profile?.savesCount ?: 0,
                                            )
                                        ) {
                                            showPlaylistChooser = true
                                        } else {
                                            // TIDAL generates directly (own account); Apple Music /
                                            // Deezer and SoundCloud-on-Spotify get the alert.
                                            val hasSoundCloud = playlistSource == CloudFunctionsDataSource.ProfilePlaylistSource.Posts
                                                && posts.any { it.isTrack && it.track.source == fm.corus.android.data.model.TrackSource.SOUNDCLOUD }
                                            if (fm.corus.android.domain.shouldShowSpotifyPlaylistAlert(musicService, hasSoundCloud)) {
                                                showPlaylistAlert = true
                                            } else {
                                                viewModel.generatePlaylist(userId, playlistSource)
                                            }
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
                                    // Launch-dark: with profile_share_enabled ON, open the
                                    // in-app Corus share sheet (DM a profile + external
                                    // actions); OFF falls back to the native Android sheet.
                                    if (viewModel.profileShareEnabled) {
                                        showShareSheet = true
                                    } else {
                                        val username = profile?.username ?: ""
                                        val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                            type = "text/plain"
                                            putExtra(Intent.EXTRA_TEXT, "https://corus.fm/u/$username")
                                        }
                                        menuContext.startActivity(Intent.createChooser(shareIntent, null))
                                    }
                                },
                            )
                            // Mute/Unmute (hidden for the official @corusteam account,
                            // which can never be muted — also enforced server-side)
                            if (!fm.corus.android.data.OfficialAccounts.isOfficial(userId)) {
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
                            }
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
                                // Block/Unblock (hidden for the official @corusteam account,
                                // which can never be blocked — also enforced server-side)
                                if (!fm.corus.android.data.OfficialAccounts.isOfficial(userId)) {
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
                    }
    }

    Scaffold(
        modifier = frost.scaffoldModifier,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            // Immersive draws the shared frosted bar over the content below instead.
            if (!immersive) {
                TopAppBar(
                    title = { },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(fm.corus.android.R.string.common_back), tint = CorusColors.Text)
                        }
                    },
                    actions = profileActions,
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = CorusColors.Background),
                    windowInsets = WindowInsets(0, 0, 0, 0),
                )
            }
        },
    ) { padding ->
        val haptics = LocalHapticManager.current
        val pullState = rememberPullToRefreshState()
        // One Box so the frosted bar (below) overlays whatever state renders; `run`
        // keeps the existing early-`return`s while still reaching the bar afterwards.
        Box(modifier = Modifier.fillMaxSize()) {
        run {
        // Banned (shadow or hard) or deleted account: getProfileData returned
        // NOT_FOUND, so the ViewModel bounced instead of loading. Show a neutral
        // "unavailable" state — never the stale header — and stop here.
        if (profileUnavailable) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(horizontal = CorusSpacing.md),
                ) {
                    Text(
                        stringResource(fm.corus.android.R.string.other_profile_unavailable_title),
                        style = CorusFont.bodyMedium,
                        color = CorusColors.Text,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(modifier = Modifier.height(CorusSpacing.sm))
                    Text(
                        stringResource(fm.corus.android.R.string.other_profile_unavailable_subtitle),
                        style = CorusFont.bodyMedium,
                        color = CorusColors.Secondary,
                        textAlign = TextAlign.Center,
                    )
                }
            }
            return@run
        }
        val hasInitialData = initialDisplayName != null && initialUsername != null
        if (isLoading && profile == null) {
            if (hasInitialData) {
                // Show real header with initial data from the feed; only shimmer the posts grid
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(top = if (immersive) frost.contentTopPadding else padding.calculateTopPadding()),
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
                                    .padding(horizontal = avatarHPad, vertical = CorusSpacing.md),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                UserAvatarView(
                                    avatarURL = initialAvatarURL,
                                    avatarThumbURL = initialAvatarThumbURL,
                                    displayName = initialDisplayName,
                                    size = headerAvatarSize,
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
                                        StatItemOrSkeleton(count = initialCymbalCount, label = stringResource(fm.corus.android.R.string.profile_stat_coruses))
                                        StatItemOrSkeleton(count = initialFollowerCount, label = stringResource(fm.corus.android.R.string.profile_stat_followers))
                                        StatItemOrSkeleton(count = initialFollowingCount, label = stringResource(fm.corus.android.R.string.profile_stat_following))
                                    }

                                    Spacer(modifier = Modifier.height(CorusSpacing.sm))

                                    // Follow + Playlist buttons (non-interactive placeholders)
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(CorusSpacing.sm),
                                    ) {
                                        val hintFollowing = initialIsFollowing == true
                                        // Non-interactive placeholder; shares the live pill so
                                        // the skeleton matches it and never clips.
                                        ProfileFollowPill(isFollowing = hintFollowing)

                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(50))
                                                .border(1.dp, CorusColors.Divider, RoundedCornerShape(50))
                                                .padding(vertical = 6.dp, horizontal = playlistHPad),
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
                                                    maxLines = 1,
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(6.dp))

                            // Username + Bio
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(start = usernameStartPad, end = usernameEndPad),
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
                                    // Transient skeleton header shown until the live profile
                                    // lands; cap at 3 lines (no expand) to match the real
                                    // ExpandableBioText below so the bio doesn't jump on swap.
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
                return@run
            }

            // No initial data — show full skeleton
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(top = if (immersive) frost.contentTopPadding else 0.dp),
            ) {
                if (initialAvatarURL != null || initialAvatarThumbURL != null) {
                    SkeletonProfileWithAvatar(
                        avatarURL = initialAvatarURL,
                        avatarThumbURL = initialAvatarThumbURL,
                    )
                } else {
                    // OtherProfileScreen keeps the nav icons in the TopAppBar, so
                    // the body header is just a centered name (no 40dp icon band).
                    SkeletonProfileView(showIconHeaderRow = false)
                }
                SkeletonProfileGrid()
            }
            return@run
        }

        val currentProfile = profile ?: return@run

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
            return@run
        }

        // Helper: populate cache and navigate to profile feed
        val navigateToFeed: (String) -> Unit = { postId ->
            val filteredForNav = when {
                currentProfile.isMusicBot -> posts.filter { it.mediaType == MediaType.TRACK }
                currentProfile.isFilmBot -> posts.filter { it.mediaType == MediaType.MOVIE }
                else -> when (selectedSegment) {
                    0 -> posts.filter { it.mediaType == MediaType.TRACK }
                    1 -> posts.filter { it.mediaType == MediaType.MOVIE }
                    2 -> likedPosts
                    else -> posts
                }
            }
            ProfileFeedCache.posts = filteredForNav
            ProfileFeedCache.hasMore = if (selectedSegment == 2) likedHasMore else hasMore
            ProfileFeedCache.profileUser = currentProfile
            onNavigateToProfileFeed(
                currentProfile.id,
                currentProfile.username,
                postId,
                selectedSegment,
            )
        }

        LazyVerticalGrid(
            state = gridState,
            columns = GridCells.Fixed(3),
            // Unclipped haze source (see ProfileFeedScreen) + pull-to-refresh as a
            // modifier so the grid renders behind the frosted status strip.
            modifier = Modifier
                .fillMaxSize()
                .then(frost.hazeSourceModifier())
                .pullToRefresh(
                    isRefreshing = isRefreshing,
                    state = pullState,
                    onRefresh = {
                        // Mirrors iOS OtherProfileView.refreshable haptic.
                        haptics.impact(HapticManager.ImpactStyle.LIGHT)
                        val onFilmsTab = !currentProfile.isBot && selectedSegment == 1
                        val onLikesTab = !currentProfile.isBot && selectedSegment == 2
                        viewModel.refresh(userId, includeFilms = onFilmsTab, includeLikes = onLikesTab)
                    },
                ),
            contentPadding = PaddingValues(
                top = if (immersive) frost.contentTopPadding else 0.dp,
                bottom = padding.calculateBottomPadding(),
            ),
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
                            .padding(horizontal = avatarHPad, vertical = CorusSpacing.md),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        UserAvatarView(
                            avatarURL = currentProfile.avatarURL,
                            displayName = currentProfile.displayName,
                            size = headerAvatarSize,
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
                                    modifier = Modifier.clickable { onNavigateToFollowList(userId, true, currentProfile.username, currentProfile.followerCount, currentProfile.followingCount) },
                                )
                                StatItem(
                                    count = currentProfile.followingCount,
                                    label = stringResource(fm.corus.android.R.string.profile_stat_following),
                                    modifier = Modifier.clickable { onNavigateToFollowList(userId, false, currentProfile.username, currentProfile.followerCount, currentProfile.followingCount) },
                                )
                            }

                            Spacer(modifier = Modifier.height(CorusSpacing.sm))

                            // Follow button + Playlist button — hidden on own profile (matches iOS)
                            if (!isOwnProfile) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(CorusSpacing.sm),
                            ) {
                                // Follow button — matching iOS Capsule with fill/stroke
                                ProfileFollowPill(
                                    isFollowing = isFollowing,
                                    followsMe = followsMe,
                                    onClick = { viewModel.toggleFollow(userId) },
                                )

                                // Playlist button
                                val playlistContext = LocalContext.current
                                // A playlist is music, so the Film tab (non-bot segment 1) can
                                // never build one — disable it there. Music builds from posted
                                // tracks; Likes is lazy-loaded so the backend responds when empty.
                                val isFilmTab = profile?.isBot == false && selectedSegment == 1
                                val hasSongs = when (playlistSource) {
                                    CloudFunctionsDataSource.ProfilePlaylistSource.Posts ->
                                        !isFilmTab && posts.any { it.mediaType == MediaType.TRACK }
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
                                            } else if (musicService == fm.corus.android.data.model.MusicService.YOUTUBE_MUSIC) {
                                                // No YouTube Music playlist export yet — explain, then
                                                // offer a Spotify playlist (explainer keeps quick vs all).
                                                showYouTubeMusicPlaylistExplainer = true
                                            } else if (fm.corus.android.domain.shouldOfferProfileFullExport(
                                                    selectedSegment, profile?.trackCount, profile?.likesCount, profile?.savesCount ?: 0,
                                                )
                                            ) {
                                                showPlaylistChooser = true
                                            } else {
                                                // TIDAL generates directly (own account); Apple Music /
                                                // Deezer and SoundCloud-on-Spotify get the alert.
                                                val hasSoundCloud = playlistSource == CloudFunctionsDataSource.ProfilePlaylistSource.Posts
                                                    && posts.any { it.isTrack && it.track.source == fm.corus.android.data.model.TrackSource.SOUNDCLOUD }
                                                if (fm.corus.android.domain.shouldShowSpotifyPlaylistAlert(musicService, hasSoundCloud)) {
                                                    showPlaylistAlert = true
                                                } else {
                                                    viewModel.generatePlaylist(userId, playlistSource)
                                                }
                                            }
                                        }
                                        .padding(vertical = 6.dp, horizontal = playlistHPad),
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
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    // Username + Bio + Website
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = usernameStartPad, end = usernameEndPad),
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
                            ExpandableBioText(
                                bio = currentProfile.bio,
                                maxCollapsedLines = 3,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }

                        if (!currentProfile.website.isNullOrBlank()) {
                            Spacer(modifier = Modifier.height(CorusSpacing.xxs))
                            val context = androidx.compose.ui.platform.LocalContext.current
                            Text(
                                text = currentProfile.website!!.removePrefix("https://").removePrefix("http://"),
                                // Bio size, not caption — the whole block reads as one paragraph (matches iOS).
                                style = CorusFont.bio,
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

                    val match = matchData
                    if (!isOwnProfile && match != null) {
                        Spacer(modifier = Modifier.height(CorusSpacing.md))
                        TasteMatchTeaser(
                            match = match,
                            onClick = { showMatchSheet = true },
                            modifier = Modifier.padding(horizontal = pillHPad),
                        )
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
                            2 -> likedPosts // Likes — the owner's liked posts, not their own
                            else -> posts
                        }
                    }

                    // Whether we're on a "featured" tab (music/film, not likes)
                    val isFeaturedTab = when {
                        currentProfile.isBot -> true // Bots always show featured
                        else -> selectedSegment <= 1
                    }

                    // Featured post — only for Music/Film tabs (matching iOS)
                    // FILM tab uses a single render path so view identity stays
                    // stable through loading→loaded (no remount, no shimmer
                    // restart, no frame Image re-decode).
                    val filmFetchPending = !currentProfile.isBot && selectedSegment == 1 && (isLoadingFilms || !hasFetchedFilmPage)
                    val filmFeaturedPost = if (selectedSegment == 1) filteredPosts.firstOrNull() else null
                    // Hold the MUSIC tab on the skeleton (never the "No songs
                    // yet" empty state) while songs the recency window missed
                    // are still being backfilled. Resolves to the empty state
                    // only once we've confirmed there are no songs: trackCount
                    // == 0, or the backfill returned nothing. The trackCount
                    // null-or-positive check keeps the skeleton up while the
                    // counter is still unknown so it can't flash empty. Mirrors
                    // iOS OtherProfileView's songsPending in segmentAndGrid.
                    val songsPending = !currentProfile.isBot && selectedSegment == 0 && filteredPosts.isEmpty() &&
                        (isLoadingSongs || (!hasFetchedSongPage && (currentProfile.trackCount ?: 1) > 0 && hasMore))
                    if (selectedSegment == 0 && songsPending) {
                        // Backfill pending — show skeleton (covers featured +
                        // grid) instead of the empty state. The grid block below
                        // emits nothing for it.
                        SkeletonProfileGrid(isFilmStyle = false)
                    } else if (selectedSegment == 1 && (filmFetchPending || filmFeaturedPost != null)) {
                        val userProfile = profile
                        val featuredEngagement = filmFeaturedPost?.let { engagementStates[it.id] }
                        FeaturedMoviePosterView(
                            post = filmFeaturedPost,
                            frameStyle = userProfile?.frameStyle ?: fm.corus.android.data.model.FrameStyle.BLACK,
                            rainIntensity = if (filmFeaturedPost == null || userProfile == null) fm.corus.android.data.model.RainIntensity.OFF else userProfile.rainIntensity,
                            snowIntensity = if (filmFeaturedPost == null || userProfile == null) fm.corus.android.data.model.SnowIntensity.OFF else userProfile.snowIntensity,
                            discoIntensity = if (filmFeaturedPost == null || userProfile == null) fm.corus.android.data.model.DiscoIntensity.OFF else userProfile.discoIntensityLevel,
                            likeCount = featuredEngagement?.likeCount ?: (filmFeaturedPost?.likeCount ?: 0),
                            isLiked = featuredEngagement?.isLiked ?: (filmFeaturedPost?.isLiked ?: false),
                            onLikeTap = { filmFeaturedPost?.let { viewModel.toggleLike(it.id) } },
                            onPostTap = { filmFeaturedPost?.let { navigateToFeed(it.id) } },
                        )
                    } else if (filteredPosts.isNotEmpty() && isFeaturedTab) {
                        // Music tab path (segment 0 or bot): unchanged.
                        val featured = filteredPosts.first()
                        val userProfile = profile
                        if (userProfile != null && !isFeaturedArtReady) {
                            Box {
                                Box(modifier = Modifier
                                    .fillMaxWidth()
                                    .alpha(0f)
                                ) {
                                    FeaturedCymbalView(
                                        post = featured,
                                        vinylStyle = userProfile.vinylStyle,
                                        musicService = musicService,
                                        onArtReady = { isFeaturedArtReady = true },
                                    )
                                }
                                SkeletonProfileGrid(isFilmStyle = false)
                            }
                        } else if (userProfile != null) {
                            val featuredEngagement = engagementStates[featured.id]
                            val shouldStagger = !didRevealFromSkeleton
                            LaunchedEffect(Unit) { didRevealFromSkeleton = true }
                            val featuredCtx = LocalContext.current
                            val featuredScope = rememberCoroutineScope()
                            FeaturedCymbalView(
                                post = featured,
                                vinylStyle = userProfile.vinylStyle,
                                musicService = musicService,
                                rainIntensity = userProfile.rainIntensity,
                                snowIntensity = userProfile.snowIntensity,
                                discoIntensity = userProfile.discoIntensityLevel,
                                likeCount = featuredEngagement?.likeCount ?: featured.likeCount,
                                isLiked = featuredEngagement?.isLiked ?: featured.isLiked,
                                onLikeTap = { viewModel.toggleLike(featured.id) },
                                onSpotifyTap = {
                                    val track = featured.track
                                    if (track.source == fm.corus.android.data.model.TrackSource.SOUNDCLOUD) {
                                        track.soundcloudPermalinkUrl?.takeIf { it.isNotBlank() }?.let {
                                            runCatching { featuredCtx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(it))) }
                                        }
                                    } else if (track.source == fm.corus.android.data.model.TrackSource.APPLEMUSIC) {
                                        track.appleMusicURL?.takeIf { it.isNotBlank() }?.let {
                                            runCatching { featuredCtx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(it))) }
                                        }
                                    } else if (musicService == fm.corus.android.data.model.MusicService.SPOTIFY) {
                                        val uri = track.spotifyURI.ifBlank { track.spotifyWebURL }
                                        if (uri.isNotBlank()) runCatching { featuredCtx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(uri))) }
                                    } else {
                                        featuredScope.launch {
                                            val url = viewModel.resolveServiceLinkUrl(track)
                                            if (!url.isNullOrBlank()) runCatching { featuredCtx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
                                        }
                                    }
                                },
                                onPostTap = { navigateToFeed(featured.id) },
                                staggerVinyl = shouldStagger,
                            )
                        }
                    } else if (filteredPosts.isEmpty() && !isLoading
                        && !(selectedSegment == 2 && (isLoadingLiked || !hasFetchedLikedPage))) {
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
                    2 -> likedPosts
                    else -> posts
                }
            }
            // For Likes, show all posts in grid (no featured);
            // for Music/Film (and bots), skip the first post (already shown as featured)
            @Suppress("NAME_SHADOWING")
            val isFeaturedTab = currentProfile.isBot || selectedSegment <= 1
            // distinctBy guards the LazyGrid against duplicate ids (paginated
            // server data can occasionally overlap) — duplicates would crash
            // SubcomposeLayout with "Key … was already used".
            val gridPosts = (if (isFeaturedTab) filteredPosts.drop(1) else filteredPosts)
                .distinctBy { it.id }
            val filmGridLoading = !currentProfile.isBot && selectedSegment == 1 && (isLoadingFilms || !hasFetchedFilmPage)
            val likesGridLoading = !currentProfile.isBot && selectedSegment == 2 &&
                (isLoadingLiked || !hasFetchedLikedPage) && likedPosts.isEmpty()
            // Recomputed here (different scope than the featured block). When
            // pending, the featured block renders SkeletonProfileGrid which
            // already covers the grid region, so this block emits nothing —
            // exactly like the music isFeaturedArtReady path below.
            val songGridPending = !currentProfile.isBot && selectedSegment == 0 &&
                filteredPosts.isEmpty() &&
                (isLoadingSongs || (!hasFetchedSongPage && (currentProfile.trackCount ?: 1) > 0 && hasMore))
            // Hide grid while featured art is loading (skeleton in header covers both areas)
            if (isFeaturedTab && selectedSegment == 0 && !isFeaturedArtReady && filteredPosts.isNotEmpty()) {
                // Music featured uses SkeletonProfileGrid in the header; emit nothing.
            } else if (songGridPending) {
                // Music backfill pending — featured block shows SkeletonProfileGrid; emit nothing.
            } else if (likesGridLoading) {
                // Skeleton grid cells while the owner's likes load (matching iOS + self profile).
                items(15) { index ->
                    val transition = rememberInfiniteTransition(label = "likesSkeletonPulse")
                    val alpha by transition.animateFloat(
                        initialValue = 0.85f,
                        targetValue = 0.3f,
                        animationSpec = infiniteRepeatable(
                            animation = tween(durationMillis = 750, easing = EaseInOut),
                            repeatMode = RepeatMode.Reverse,
                            initialStartOffset = StartOffset(offsetMillis = index * 80),
                        ),
                        label = "likesSkeletonAlpha",
                    )
                    Box(
                        modifier = Modifier
                            .aspectRatio(1f)
                            .background(CorusColors.Skeleton.copy(alpha = alpha)),
                    )
                }
            } else if (filmGridLoading) {
                // FILM tab now uses the in-place FeaturedMoviePosterView shell, so we
                // need to render grid skeleton cells here.
                items(6) { index ->
                    val transition = rememberInfiniteTransition(label = "filmSkeletonPulse")
                    val alpha by transition.animateFloat(
                        initialValue = 0.85f,
                        targetValue = 0.3f,
                        animationSpec = infiniteRepeatable(
                            animation = tween(durationMillis = 750, easing = EaseInOut),
                            repeatMode = RepeatMode.Reverse,
                            initialStartOffset = StartOffset(offsetMillis = (index + 3) * 80),
                        ),
                        label = "filmSkeletonAlpha",
                    )
                    Box(
                        modifier = Modifier
                            .aspectRatio(2f / 3f)
                            .background(CorusColors.Skeleton.copy(alpha = alpha)),
                    )
                }
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
        // Refresh spinner + frosted bar overlay the whole content area (all states).
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
                title = null,
                onBack = onBack,
                topInset = frost.statusBarPadding,
                actions = profileActions,
            )
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
                source = clubOfferSource,
                onDismiss = { showClubOffer = false },
            )
        }
    }

    // Profile share sheet (in-app Corus DM share), gated by profile_share_enabled.
    if (showShareSheet) {
        val p = profile
        if (p == null) {
            showShareSheet = false
        } else {
            val shareSheetState = androidx.compose.material3.rememberModalBottomSheetState(skipPartiallyExpanded = true)
            val sentMsg = stringResource(fm.corus.android.R.string.profile_share_toast_profile_sent)
            LaunchedEffect(Unit) { viewModel.loadRecentShareContacts() }
            androidx.compose.material3.ModalBottomSheet(
                onDismissRequest = { showShareSheet = false },
                sheetState = shareSheetState,
                containerColor = CorusColors.Background,
                dragHandle = null,
            ) {
                CorusSystemBars()
                BackHandler { showShareSheet = false }
                ShareMediaSheet(
                    subject = ShareMediaSubject.Profile(
                        ShareProfileSubject(
                            id = p.id,
                            username = p.username,
                            displayName = p.displayName,
                            avatarUrl = p.avatarURL,
                        )
                    ),
                    recentContacts = recentShareContacts,
                    searchResults = shareSearchResults,
                    isSearching = isShareSearching,
                    isLoadingContacts = isLoadingShareContacts,
                    onSearchQueryChange = { query -> viewModel.searchShareUsers(query) },
                    onSendToUser = { userId, message ->
                        viewModel.sendProfileToUser(userId, p.id, p.username, p.displayName, p.avatarURL, message)
                        ToastManager.show(sentMsg)
                        showShareSheet = false
                    },
                    onDismiss = { showShareSheet = false },
                )
            }
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

    if (showPlaylistChooser) {
        val count = fm.corus.android.domain.profilePlaylistEligibleCount(
            selectedSegment, profile?.trackCount, profile?.likesCount, profile?.savesCount ?: 0,
        )
        val hasSoundCloud = playlistSource == CloudFunctionsDataSource.ProfilePlaylistSource.Posts
            && posts.any { it.isTrack && it.track.source == fm.corus.android.data.model.TrackSource.SOUNDCLOUD }
        // Fold the service caveat into the one dialog (no stacked popups). Deezer /
        // Apple Music can't build playlists on Android and fall back to Spotify —
        // name the service so the substitution is clear.
        val showSpotifyFallbackNote = fm.corus.android.domain.usesSpotifyFallback(musicService)
        val showSoundCloudNote = hasSoundCloud &&
            (musicService == fm.corus.android.data.model.MusicService.SPOTIFY || showSpotifyFallbackNote)
        val caveat = buildString {
            if (showSpotifyFallbackNote) {
                append("${musicService.displayLabel} can't build playlists, so this creates a Spotify playlist.")
            }
            if (showSoundCloudNote) {
                if (isNotEmpty()) append(" ")
                append("SoundCloud tracks are skipped.")
            }
        }
        PlaylistExportChooserDialog(
            count = count,
            caveat = caveat,
            onQuick = {
                showPlaylistChooser = false
                viewModel.generatePlaylist(userId, playlistSource, fullExport = false)
            },
            onAll = {
                showPlaylistChooser = false
                viewModel.generatePlaylist(userId, playlistSource, fullExport = true)
            },
            onDismiss = { showPlaylistChooser = false },
        )
    }

    if (showYouTubeMusicPlaylistExplainer) {
        val count = fm.corus.android.domain.profilePlaylistEligibleCount(
            selectedSegment, profile?.trackCount, profile?.likesCount, profile?.savesCount ?: 0,
        )
        val offersFull = fm.corus.android.domain.shouldOfferProfileFullExport(
            selectedSegment, profile?.trackCount, profile?.likesCount, profile?.savesCount ?: 0,
        )
        YouTubeMusicPlaylistDialog(
            count = count,
            offersFullExport = offersFull,
            onQuick = {
                showYouTubeMusicPlaylistExplainer = false
                viewModel.generatePlaylist(userId, playlistSource, fullExport = false)
            },
            onAll = {
                showYouTubeMusicPlaylistExplainer = false
                viewModel.generatePlaylist(userId, playlistSource, fullExport = true)
            },
            onDismiss = { showYouTubeMusicPlaylistExplainer = false },
        )
    }

    // Taste-match detail sheet. Tapping a tile dismisses the sheet and routes
    // to the target's post via the nav-graph-supplied onNavigateToPost handler.
    val match = matchData
    if (showMatchSheet && match != null) {
        TasteMatchSheet(
            username = profile?.username ?: initialUsername.orEmpty(),
            match = match,
            onDismiss = { showMatchSheet = false },
            onSelectPost = { postId -> onNavigateToPost(postId) },
        )
    }
}

@Composable
private fun StatItem(count: Int, label: String, modifier: Modifier = Modifier) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = modifier) {
        Text(text = formattedCount(count), style = CorusFont.stat, color = CorusColors.Text)
        Text(text = label, style = CorusFont.statLabel, color = CorusColors.Secondary)
    }
}

/**
 * Loading-header stat: shows the number when it's known, or a shimmer pill in the
 * number's place when it isn't. Counts are unknown when the profile is opened from
 * a feed post (the denormalized author preview carries no counts) — shimmering
 * avoids flashing a wrong "0" until the live profile lands. The label stays visible
 * so the row keeps its shape and doesn't jump on swap.
 */
@Composable
private fun StatItemOrSkeleton(count: Int?, label: String, modifier: Modifier = Modifier) {
    if (count != null) {
        StatItem(count = count, label = label, modifier = modifier)
        return
    }
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = modifier) {
        Box(
            modifier = Modifier
                .padding(vertical = 4.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(CorusColors.Divider)
                .shimmer()
                .size(width = 24.dp, height = 14.dp),
        )
        Text(text = label, style = CorusFont.statLabel, color = CorusColors.Secondary)
    }
}

@Composable
private fun StatDivider() {
    Spacer(modifier = Modifier.width(CorusSpacing.sm))
    Text("|", style = CorusFont.statLabel, color = CorusColors.Tertiary)
    Spacer(modifier = Modifier.width(CorusSpacing.sm))
}

/**
 * The FOLLOW / FOLLOW BACK / FOLLOWING capsule on another user's profile, sitting
 * next to the PLAYLIST pill. It shows FOLLOW BACK when [followsMe] (the viewed user
 * follows the local user) and we don't follow them yet, matching iOS. It is weighted
 * to absorb the row width left over by the (unweighted, measured-first) PLAYLIST
 * pill, carries no horizontal padding, and uses a shrink-to-fit label so the text
 * centers and never clips or wraps (the label is single-line, softWrap=false, and
 * steps its font down to fit). "FOLLOW BACK" is the longest label it carries; on
 * narrow phones it renders a touch smaller rather than truncating. This mirrors the
 * own-profile EDIT button. Regression history: the row clipped "PLAYLI" when neither
 * pill was weighted, then "FOLLOWING" -> "FOLLOWI" when this pill was weighted but
 * kept its padding + a fixed-size label. Shared by the live header and the loading
 * placeholder (which passes followsMe=false) so both stay in sync. Call inside a Row.
 */
@Composable
internal fun RowScope.ProfileFollowPill(
    isFollowing: Boolean,
    followsMe: Boolean = false,
    onClick: (() -> Unit)? = null,
) {
    val followShape = RoundedCornerShape(50)
    Box(
        modifier = Modifier
            .weight(1f)
            .clip(followShape)
            .then(
                if (isFollowing) Modifier.border(1.dp, CorusColors.Divider, followShape)
                else Modifier.background(CorusColors.Accent)
            )
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(vertical = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        ShrinkToFitText(
            text = when {
                isFollowing -> stringResource(fm.corus.android.R.string.other_profile_button_following)
                followsMe -> stringResource(fm.corus.android.R.string.other_profile_button_follow_back)
                else -> stringResource(fm.corus.android.R.string.other_profile_button_follow)
            },
            style = CorusFont.button,
            color = if (isFollowing) CorusColors.Secondary else Color.White,
        )
    }
}
