package fm.corus.android.ui.screens.profile

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.core.EaseInOut
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.StartOffset
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Settings
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.ui.draw.alpha
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
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
import fm.corus.android.data.remote.CloudFunctionsDataSource
import fm.corus.android.domain.HapticManager
import fm.corus.android.ui.LocalHapticManager
import android.graphics.Bitmap
import fm.corus.android.ui.components.AvatarCropView
import fm.corus.android.ui.components.FrostedStatusStrip
import fm.corus.android.ui.components.LocalBottomBarHeight
import fm.corus.android.ui.components.contentHazeSource
import fm.corus.android.ui.components.rememberImmersiveHeaderState
import fm.corus.android.ui.components.ExpandableBioText
import fm.corus.android.ui.components.FullScreenAvatarOverlay
import fm.corus.android.ui.components.SelfieCaptureScreen
import fm.corus.android.ui.components.ShimmerAsyncImage
import fm.corus.android.ui.components.uriToBitmap
import fm.corus.android.ui.components.ProfileShareAnalytics
import fm.corus.android.ui.components.ShareMediaSheet
import fm.corus.android.ui.components.ShareMediaSubject
import fm.corus.android.ui.components.ShareProfileSubject
import fm.corus.android.ui.components.shareCardPreviewUrl
import fm.corus.android.ui.components.ToastManager
import fm.corus.android.ui.components.UserAvatarView
import fm.corus.android.ui.components.UsernameWithFlair
import fm.corus.android.ui.theme.CorusColors
import fm.corus.android.ui.theme.CorusFont
import fm.corus.android.ui.theme.CorusSpacing
import fm.corus.android.ui.theme.CorusSystemBars
import fm.corus.android.ui.util.formattedCount
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Whether the profile PLAYLIST button should be enabled for the given tab.
 * A playlist is a music playlist, so:
 *  - Music (0): enabled when the user has posted at least one track.
 *  - Film (1): always disabled — films can't go in a music playlist.
 *  - Likes (2) / Saves (3): enabled only once that list has loaded and is
 *    non-empty, so the button doesn't sit enabled over an empty tab and
 *    doesn't flicker while the list is still loading.
 */
/**
 * Own-profile share placement trial (matches iOS `ProfileSharePlacementTrial`).
 * `true`  → Edit + Share pills; playlist icon left of settings in the title row.
 * `false` → restore Edit + Playlist labeled row; settings-only title trailing.
 */
private const val USE_ACTION_ROW_SHARE = true

internal fun profilePlaylistEnabled(
    selectedSegment: Int,
    hasTrackPosts: Boolean,
    likedCount: Int,
    savedCount: Int,
    isLoadingLiked: Boolean,
    isLoadingSaved: Boolean,
): Boolean = when (selectedSegment) {
    0 -> hasTrackPosts
    1 -> false
    2 -> !isLoadingLiked && likedCount > 0
    3 -> !isLoadingSaved && savedCount > 0
    else -> false
}

@Composable
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
fun ProfileScreen(
    viewModel: ProfileViewModel = hiltViewModel(),
    scrollToTopTrigger: Int = 0,
    tabActivationTrigger: Int = 0,
    openStylePicker: Boolean = false,
    onStylePickerConsumed: () -> Unit = {},
    onNavigateToSettings: () -> Unit = {},
    onNavigateToEditProfile: (String) -> Unit = {},
    onNavigateToFollowList: (String, Boolean, String, Int, Int) -> Unit = { _, _, _, _, _ -> },
    onNavigateToProfileFeed: (userId: String, username: String, postId: String, segment: Int) -> Unit = { _, _, _, _ -> },
    onNavigateToClub: () -> Unit = {},
    onOpenCompose: (String) -> Unit = {},
) {
    val context = androidx.compose.ui.platform.LocalContext.current

    // Responsive header spacing — see OtherProfileScreen for rationale.
    val isWideHeader = LocalConfiguration.current.screenWidthDp >= 400
    val headerHPad = if (isWideHeader) 28.dp else CorusSpacing.xl
    val playlistHPad = if (isWideHeader) CorusSpacing.xxl else CorusSpacing.md
    val headerAvatarSize = if (isWideHeader) CorusSpacing.avatarLarge else 68.dp
    // Avatar + username sit slightly inside the screen's outer margin —
    // matches OtherProfileScreen for visual consistency.
    val avatarHPad = headerHPad + 8.dp
    val usernameStartPad = avatarHPad
    val usernameEndPad = avatarHPad

    val profile by viewModel.profile.collectAsState()
    val pendingAvatarBytes by viewModel.pendingAvatarBytes.collectAsState()
    val posts by viewModel.posts.collectAsState()
    val musicService by viewModel.musicServicePreference.current.collectAsState()
    var showPlaylistAlert by remember { mutableStateOf(false) }
    var showPlaylistChooser by remember { mutableStateOf(false) }
    // Playlist export isn't available for YouTube Music yet, so a YT Music viewer
    // gets an explainer that offers a Spotify playlist instead (mirrors the feed).
    var showYouTubeMusicPlaylistExplainer by remember { mutableStateOf(false) }
    val likedPosts by viewModel.likedPosts.collectAsState()
    val savedPosts by viewModel.savedPosts.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val hasLoadError by viewModel.hasLoadError.collectAsState()
    val isRefreshing by viewModel.isRefreshing.collectAsState()
    val isLoadingLiked by viewModel.isLoadingLiked.collectAsState()
    val isLoadingSaved by viewModel.isLoadingSaved.collectAsState()
    val isLoadingFilms by viewModel.isLoadingFilms.collectAsState()
    val hasFetchedFilmPage by viewModel.hasFetchedFilmPage.collectAsState()
    val isLoadingSongs by viewModel.isLoadingSongs.collectAsState()
    val hasFetchedSongPage by viewModel.hasFetchedSongPage.collectAsState()
    val isClubMember by viewModel.isClubMember.collectAsState()
    val hasFullAccess by viewModel.hasFullAccess.collectAsState()
    val isSavingStyle by viewModel.isSavingStyle.collectAsState()
    val engagementStates by viewModel.engagementStates.collectAsState()
    val likesSavesFilter by viewModel.likesSavesFilter.collectAsState()
    // Whether the Likes/Saves filter dropdown is open.
    var filterMenuExpanded by remember { mutableStateOf(false) }
    // The user's explicit choice once they've tapped a tab. While null, the
    // selected tab is derived synchronously from the profile data so the
    // first frame after load already lands on the right tab — no flicker
    // from MUSIC to FILM after a recomposition.
    var userSelectedSegment by rememberSaveable { mutableStateOf<Int?>(null) }
    val rawHasMore by viewModel.hasMore.collectAsState()
    val hasMoreMixedPosts = rawHasMore[0] ?: true
    val selectedSegment = userSelectedSegment
        ?: profile?.preferredProfileSegmentFromPosts(posts, hasMoreMixedPosts)
        ?: 0
    // Render full 2:3 posters (not square crops) when the grid is films-only:
    // the Film tab, or a Likes/Saves grid filtered to Film.
    val showPosterGrid = selectedSegment == 1 ||
        ((selectedSegment == 2 || selectedSegment == 3) && likesSavesFilter == ProfileMediaFilter.FILM)
    var isFeaturedArtReady by rememberSaveable { mutableStateOf(false) }
    var didRevealFromSkeleton by remember { mutableStateOf(false) }
    var showStylePicker by remember { mutableStateOf(false) }
    var showClubOffer by remember { mutableStateOf(false) }
    var clubOfferSource by remember { mutableStateOf(fm.corus.android.ui.screens.subscription.PaywallSource.DEFAULT) }
    var clubPlaylistTrialContext by remember {
        mutableStateOf<fm.corus.android.domain.PlaylistTrialField?>(null)
    }

    val paywallRequested by viewModel.nowPlayingManager.paywallRequested.collectAsState()
    val playlistPaywallContext by viewModel.nowPlayingManager.playlistPaywallContext.collectAsState()
    LaunchedEffect(paywallRequested) {
        if (paywallRequested) {
            clubOfferSource = fm.corus.android.ui.screens.subscription.PaywallSource.PLAYLIST_LIMIT
            clubPlaylistTrialContext = playlistPaywallContext
            showClubOffer = true
            viewModel.nowPlayingManager.clearPaywallRequested()
        }
    }

    // After the user creates a post, jump to the tab that surfaces it (Music or
    // Film) so they actually see what they just posted — e.g. posting a film
    // while on the Music tab now switches to Film instead of looking like nothing
    // happened.
    LaunchedEffect(Unit) {
        viewModel.switchToSegment.collect { segment ->
            userSelectedSegment = segment
            isFeaturedArtReady = false
            viewModel.loadSegment(segment)
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
    var showShareSheet by remember { mutableStateOf(false) }
    var profileShareEntryPoint by remember { mutableStateOf("unknown") }
    val recentShareContacts by viewModel.recentShareContacts.collectAsState()
    val shareSearchResults by viewModel.shareSearchResults.collectAsState()
    val isShareSearching by viewModel.isShareSearching.collectAsState()
    val isLoadingShareContacts by viewModel.isLoadingShareContacts.collectAsState()
    var cropBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var showSelfieCapture by remember { mutableStateOf(false) }
    val decodeScope = rememberCoroutineScope()

    // Playlist affordance — shared by the title-row icon (trial) and the
    // action-row capsule (legacy). Hoisted so the header can call the same tap
    // path the old PLAYLIST button used.
    val playlistSource = when (selectedSegment) {
        2 -> CloudFunctionsDataSource.ProfilePlaylistSource.Likes
        3 -> CloudFunctionsDataSource.ProfilePlaylistSource.Saves
        else -> CloudFunctionsDataSource.ProfilePlaylistSource.Posts
    }
    val hasSongs = profilePlaylistEnabled(
        selectedSegment = selectedSegment,
        hasTrackPosts = posts.any { it.mediaType == MediaType.TRACK },
        likedCount = likedPosts.size,
        savedCount = savedPosts.size,
        isLoadingLiked = isLoadingLiked,
        isLoadingSaved = isLoadingSaved,
    )
    val isGeneratingPlaylist by viewModel.nowPlayingManager.isGeneratingPlaylist.collectAsState()
    val playlistError by viewModel.nowPlayingManager.playlistError.collectAsState()
    LaunchedEffect(playlistError) {
        if (playlistError != null) {
            ToastManager.show(playlistError!!)
            viewModel.nowPlayingManager.clearPlaylistError()
        }
    }

    fun presentProfileShare(entryPoint: String = "action_row") {
        profileShareEntryPoint = entryPoint
        if (viewModel.profileShareEnabled) {
            showShareSheet = true
        } else {
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, "https://corus.fm/u/${profile?.username.orEmpty()}")
            }
            try {
                context.startActivity(Intent.createChooser(shareIntent, null))
            } catch (_: Exception) { }
        }
    }

    fun handlePlaylistTap() {
        if (!hasSongs) {
            ToastManager.show(context.getString(fm.corus.android.R.string.profile_toast_no_songs_for_playlist))
        } else if (viewModel.shouldPaywallOwnProfilePlaylist()) {
            clubOfferSource = fm.corus.android.ui.screens.subscription.PaywallSource.PLAYLIST_LIMIT
            clubPlaylistTrialContext = fm.corus.android.domain.PlaylistTrialField.OwnProfile
            showClubOffer = true
        } else if (musicService == fm.corus.android.data.model.MusicService.YOUTUBE_MUSIC) {
            showYouTubeMusicPlaylistExplainer = true
        } else if (fm.corus.android.domain.shouldOfferProfileFullExport(
                selectedSegment, profile?.trackCount, profile?.likesCount, profile?.savesCount ?: 0,
            )
        ) {
            showPlaylistChooser = true
        } else {
            val hasSoundCloud = playlistSource == CloudFunctionsDataSource.ProfilePlaylistSource.Posts
                && posts.any { it.isTrack && it.track.source == fm.corus.android.data.model.TrackSource.SOUNDCLOUD }
            if (fm.corus.android.domain.shouldShowSpotifyPlaylistAlert(musicService, hasSoundCloud)) {
                showPlaylistAlert = true
            } else {
                viewModel.generatePlaylist(playlistSource)
            }
        }
    }

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

    val gridState = rememberLazyGridState()

    if (isLoading && profile == null) {
        Column(modifier = Modifier.fillMaxSize()) {
            fm.corus.android.ui.components.SkeletonProfileView()
            fm.corus.android.ui.components.SkeletonProfileGrid()
        }
        return
    }

    if (profile == null && hasLoadError) {
        fm.corus.android.ui.components.OfflineRetryState(
            modifier = Modifier.fillMaxSize(),
            onRetry = { viewModel.retryLoad() },
        )
        return
    }

    val currentProfile = profile ?: return

    val isLoadingMore by viewModel.isLoadingMore.collectAsState()
    val hasMore = rawHasMore

    var lastScrollTrigger by rememberSaveable { mutableIntStateOf(0) }
    LaunchedEffect(scrollToTopTrigger) {
        if (scrollToTopTrigger > lastScrollTrigger) {
            gridState.animateScrollToItem(0)
            lastScrollTrigger = scrollToTopTrigger
        }
    }

    // Featured-post like-count refresh on tab activation. The trigger is bumped
    // by MainTabScreen each time the profile tab is selected; the viewModel
    // applies a 60s throttle so frequent tab-switching doesn't spam reads.
    LaunchedEffect(tabActivationTrigger) {
        if (tabActivationTrigger > 0) {
            viewModel.refreshFeaturedPostsIfStale()
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

    val immersive = viewModel.immersiveArtistHeaderEnabled
    val frost = rememberImmersiveHeaderState(immersive)
    val haptics = LocalHapticManager.current
    val pullState = rememberPullToRefreshState()
    Box(modifier = frost.scaffoldModifier) {
    PullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = {
            // Mirrors iOS ProfileView.refreshable haptic.
            haptics.impact(HapticManager.ImpactStyle.LIGHT)
            viewModel.refreshProfile()
        },
        state = pullState,
        modifier = Modifier.fillMaxSize().then(frost.hazeSourceModifier()).contentHazeSource(),
        indicator = {
            PullToRefreshDefaults.Indicator(
                state = pullState,
                isRefreshing = isRefreshing,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = if (immersive) frost.statusBarPadding else 0.dp),
            )
        },
    ) {
    LazyVerticalGrid(
        state = gridState,
        columns = GridCells.Fixed(3),
        modifier = Modifier.fillMaxSize(),
        // Clear the frosted bottom bar; the grid still scrolls under it.
        contentPadding = PaddingValues(bottom = LocalBottomBarHeight.current),
    ) {
        // All header content spans full width
        item(span = { GridItemSpan(3) }, key = "header_row") {
            Column {
                // Clear the frosted status strip so the header row sits below it.
                if (immersive) Spacer(Modifier.height(frost.statusBarPadding))
                // ── Header Row: icon / display name / settings ──
                // Matched leading/trailing widths keep the display name centered
                // when the trial adds a second trailing icon (playlist + settings).
                val titleSideWidth = if (USE_ACTION_ROW_SHARE) 60.dp else 40.dp
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = CorusSpacing.lg, vertical = CorusSpacing.md),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // Profile customization icon (matching iOS CorusClub icon).
                    Box(
                        modifier = Modifier.width(titleSideWidth),
                        contentAlignment = Alignment.CenterStart,
                    ) {
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
                    }

                    Text(
                        text = currentProfile.displayName,
                        style = CorusFont.displayName,
                        color = CorusColors.Text,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                        textAlign = TextAlign.Center,
                    )

                    // Trailing chrome: trial puts playlist left of settings
                    // (same 24dp size / Secondary tint so they share weight).
                    Row(
                        modifier = Modifier.width(titleSideWidth),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(CorusSpacing.md, Alignment.End),
                    ) {
                        if (USE_ACTION_ROW_SHARE) {
                            Box(
                                modifier = Modifier.size(24.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                if (isGeneratingPlaylist) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(16.dp),
                                        strokeWidth = 2.dp,
                                        color = CorusColors.Secondary,
                                    )
                                } else {
                                    Icon(
                                        painter = painterResource(fm.corus.android.R.drawable.ic_music_note_list),
                                        contentDescription = stringResource(fm.corus.android.R.string.profile_cd_playlist),
                                        tint = CorusColors.Secondary,
                                        modifier = Modifier
                                            .size(24.dp)
                                            .alpha(if (!hasSongs) 0.35f else 1f)
                                            .clickable(enabled = !isGeneratingPlaylist) {
                                                handlePlaylistTap()
                                            },
                                    )
                                }
                            }
                        }
                        Icon(
                            Icons.Filled.Settings,
                            contentDescription = stringResource(fm.corus.android.R.string.profile_cd_settings),
                            tint = CorusColors.Secondary,
                            modifier = Modifier
                                .size(24.dp)
                                .clickable(onClick = onNavigateToSettings),
                        )
                    }
                }
            }
        }

        item(span = { GridItemSpan(3) }, key = "avatar_stats") {
            Column {
                // ── Avatar + Stats Row ──
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = avatarHPad),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // Large circular avatar with long-press context menu
                    Box {
                        UserAvatarView(
                            avatarURL = currentProfile.avatarURL,
                            displayName = currentProfile.displayName,
                            size = headerAvatarSize,
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
                            // Letter placeholder is not a photo — match iOS (`avatarURL != nil`).
                            // Include pending bytes so View Photo stays available right after upload.
                            if (!currentProfile.avatarURL.isNullOrBlank() || pendingAvatarBytes != null) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(fm.corus.android.R.string.profile_avatar_view_photo), style = CorusFont.body, color = CorusColors.Text) },
                                    onClick = {
                                        showAvatarMenu = false
                                        showFullScreenAvatar = true
                                    },
                                )
                            }
                            DropdownMenuItem(
                                text = { Text(stringResource(fm.corus.android.R.string.profile_avatar_share_link), style = CorusFont.body, color = CorusColors.Text) },
                                onClick = {
                                    showAvatarMenu = false
                                    // Launch-dark: with profile_share_enabled ON, open the
                                    // in-app Corus share sheet (DM your profile + external
                                    // actions); OFF falls back to the native Android sheet.
                                    presentProfileShare("avatar_menu")
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
                                onClick = { onNavigateToFollowList(currentProfile.id, true, currentProfile.username, currentProfile.followerCount, currentProfile.followingCount) },
                            )
                            StatItem(
                                count = currentProfile.followingCount,
                                label = stringResource(fm.corus.android.R.string.profile_stat_following),
                                onClick = { onNavigateToFollowList(currentProfile.id, false, currentProfile.username, currentProfile.followerCount, currentProfile.followingCount) },
                            )
                        }

                        Spacer(modifier = Modifier.height(CorusSpacing.sm))

                        // Action pills — trial: Edit + Share (playlist lives in
                        // the title row). Legacy: Edit + Playlist capsule.
                        // Narrow phones (≤375dp) start at 11sp; wider at 13sp
                        // (one step under CorusFont.button). Shared fitted size
                        // + horizontal inset keeps labels off the pill edges.
                        val editLabel = stringResource(fm.corus.android.R.string.profile_button_edit)
                        val shareLabel = stringResource(fm.corus.android.R.string.profile_button_share)
                        val actionButtonBaseStyle = profileActionButtonBaseStyle(
                            LocalConfiguration.current.screenWidthDp,
                        )
                        val actionPillHPad = CorusSpacing.sm
                        BoxWithConstraints(
                            modifier = Modifier.padding(horizontal = CorusSpacing.xs),
                        ) {
                            val capsuleMaxWidth = if (USE_ACTION_ROW_SHARE) {
                                (maxWidth - CorusSpacing.sm) / 2
                            } else {
                                maxWidth
                            }
                            // Fit against the text slot inside the pill (after h-pad).
                            val labelMaxWidth = (capsuleMaxWidth - actionPillHPad * 2)
                                .coerceAtLeast(0.dp)
                            val actionButtonStyle = if (USE_ACTION_ROW_SHARE) {
                                rememberSharedProfileActionButtonStyle(
                                    texts = listOf(editLabel, shareLabel),
                                    baseStyle = actionButtonBaseStyle,
                                    maxWidth = labelMaxWidth,
                                )
                            } else {
                                actionButtonBaseStyle
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                // Edit Profile — matching iOS Capsule with stroke border
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(50))
                                        .border(1.dp, CorusColors.Divider, RoundedCornerShape(50))
                                        .clickable { onNavigateToEditProfile(currentProfile.id) }
                                        .padding(
                                            horizontal = actionPillHPad,
                                            vertical = CorusSpacing.sm - 2.dp,
                                        ),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(
                                        text = editLabel,
                                        style = actionButtonStyle,
                                        color = CorusColors.Secondary,
                                        maxLines = 1,
                                        softWrap = false,
                                    )
                                }

                                Spacer(modifier = Modifier.width(CorusSpacing.sm))

                                if (USE_ACTION_ROW_SHARE) {
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .clip(RoundedCornerShape(50))
                                            .border(1.dp, CorusColors.Divider, RoundedCornerShape(50))
                                            .clickable { presentProfileShare("action_row") }
                                            .padding(
                                                horizontal = actionPillHPad,
                                                vertical = CorusSpacing.sm - 2.dp,
                                            ),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Text(
                                            text = shareLabel,
                                            style = actionButtonStyle,
                                            color = CorusColors.Secondary,
                                            maxLines = 1,
                                            softWrap = false,
                                        )
                                    }
                                } else {
                                    // Legacy PLAYLIST capsule (matching iOS music.note.list)
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
                                                handlePlaylistTap()
                                            }
                                            .padding(horizontal = playlistHPad),
                                        contentAlignment = Alignment.Center,
                                    ) {
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
                    }
                }

                Spacer(modifier = Modifier.height(CorusSpacing.md))
            }
        }

        item(span = { GridItemSpan(3) }, key = "bio") {
            Column {
                // ── Username + Bio + Website ──
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = usernameStartPad, end = usernameEndPad),
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
                        ExpandableBioText(
                            bio = currentProfile.bio,
                            maxCollapsedLines = 3,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }

                    // Website
                    if (!currentProfile.website.isNullOrBlank()) {
                        Spacer(modifier = Modifier.height(CorusSpacing.xs))
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

                Spacer(modifier = Modifier.height(CorusSpacing.lg))
            }
        }

        item(span = { GridItemSpan(3) }, key = "tabs") {
            Column {
                // ── Segment Control ──
                // Tabs are stored in *displayed* order. When the profile owner
                // has chosen Film as their featured tab, Film leads and Music
                // is second. selectedSegment stays in *logical* coords
                // (0=Music, 1=Film, 2=Likes, 3=Saves) so the existing
                // when-on-segment dispatch downstream is unchanged. We map
                // through `tabsOrder` here to translate displayed-tap → logical.
                val musicLabel = stringResource(fm.corus.android.R.string.profile_tab_music)
                val filmLabel = stringResource(fm.corus.android.R.string.profile_tab_film)
                val likesLabel = stringResource(fm.corus.android.R.string.profile_tab_likes)
                val savesLabel = stringResource(fm.corus.android.R.string.profile_tab_saves)
                val isFilmFirst = profile?.featuredTab == "film"
                val tabsOrder = if (isFilmFirst) listOf(1, 0, 2, 3) else listOf(0, 1, 2, 3)
                val tabs = tabsOrder.map { logical ->
                    when (logical) {
                        0 -> musicLabel
                        1 -> filmLabel
                        2 -> likesLabel
                        else -> savesLabel
                    }
                }
                val tabSelectedColor = CorusColors.Text
                val tabUnselectedColor = CorusColors.Divider
                // Always show the Likes/Saves filter. Keeping the chevron
                // permanently present avoids a flash on bare profiles, where a
                // "does a second type exist" signal only settles after the page
                // loads. It's fine to offer it even when a type has zero items.
                val lensUseful = selectedSegment == 2 || selectedSegment == 3
                Row(
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    tabs.forEachIndexed { index, title ->
                        val logicalSegment = tabsOrder[index]
                        val isSelected = selectedSegment == logicalSegment
                        val isFilterable = logicalSegment == 2 || logicalSegment == 3
                        val showFilter = isFilterable && isSelected && lensUseful
                        Box(modifier = Modifier.weight(1f)) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    if (showFilter) {
                                        // The whole active Likes/Saves tab is the
                                        // filter trigger.
                                        filterMenuExpanded = true
                                    } else {
                                        filterMenuExpanded = false
                                        viewModel.resetLikesSavesLens()
                                        userSelectedSegment = logicalSegment
                                        isFeaturedArtReady = false
                                        viewModel.loadSegment(logicalSegment)
                                    }
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
                            ProfileTabLabel(
                                title = title,
                                isSelected = isSelected,
                                reservesChevron = isFilterable,
                                showChevron = showFilter,
                                activeIcon = when {
                                    !showFilter -> null
                                    likesSavesFilter == ProfileMediaFilter.MUSIC -> Icons.Filled.MusicNote
                                    likesSavesFilter == ProfileMediaFilter.FILM -> Icons.Filled.Movie
                                    else -> null
                                },
                            )
                        }
                        if (showFilter) {
                            DropdownMenu(
                                expanded = filterMenuExpanded,
                                onDismissRequest = { filterMenuExpanded = false },
                                modifier = Modifier.background(CorusColors.Background),
                            ) {
                                ProfileFilterMenuItem("All", ProfileMediaFilter.ALL, null, likesSavesFilter) {
                                    filterMenuExpanded = false
                                    viewModel.setLikesSavesFilter(it, logicalSegment)
                                }
                                ProfileFilterMenuItem("Music", ProfileMediaFilter.MUSIC, Icons.Filled.MusicNote, likesSavesFilter) {
                                    filterMenuExpanded = false
                                    viewModel.setLikesSavesFilter(it, logicalSegment)
                                }
                                ProfileFilterMenuItem("Film", ProfileMediaFilter.FILM, Icons.Filled.Movie, likesSavesFilter) {
                                    filterMenuExpanded = false
                                    viewModel.setLikesSavesFilter(it, logicalSegment)
                                }
                            }
                        }
                        }
                    }
                }
            }
        }

        item(span = { GridItemSpan(3) }, key = "featured") {
            Column {
                // Filter posts by segment
                val filteredPosts = when (selectedSegment) {
                    0 -> posts.filter { it.mediaType == fm.corus.android.data.model.MediaType.TRACK }
                    1 -> posts.filter { it.mediaType == fm.corus.android.data.model.MediaType.MOVIE }
                    2 -> likedPosts
                    3 -> savedPosts
                    else -> posts
                }

                // While the dedicated movie-only fetch is in flight, always
                // render the FeaturedMoviePosterView shell on the Film tab —
                // even if one film is already cached from the recency-sorted
                // initial page — so the view identity stays stable through
                // the loading→loaded transition (no remount, no shimmer
                // restart, no frame Image re-decode).
                val filmFetchPending = selectedSegment == 1 && (isLoadingFilms || !hasFetchedFilmPage)
                val filmFeaturedPost = if (selectedSegment == 1) filteredPosts.firstOrNull() else null

                // Hold the MUSIC tab on the skeleton (never the "No songs yet"
                // empty prompt) while songs the recency window missed are still
                // being backfilled. Resolves to the empty state only once we've
                // confirmed there are no songs: trackCount == 0, or the backfill
                // returned nothing (hasFetchedSongPage set, isLoadingSongs
                // cleared). The trackCount null-or-positive check keeps the
                // skeleton up while the counter is still unknown so it can't
                // flash empty. Mirrors iOS ProfileViewModel.isSegmentLoading.
                val songFetchPending = selectedSegment == 0 && filteredPosts.isEmpty() &&
                    (isLoadingSongs || (!hasFetchedSongPage && (currentProfile.trackCount ?: 1) > 0 && hasMoreMixedPosts))

                // ── Featured Post — only for Music/Film tabs (matching iOS) ──
                if (selectedSegment == 0 && songFetchPending) {
                    // No songs in the recency window yet — backfill is pending.
                    // Show the skeleton (covers featured + grid) instead of the
                    // empty prompt; the grid block below emits nothing for it.
                    fm.corus.android.ui.components.SkeletonProfileGrid(isFilmStyle = false)
                } else if (selectedSegment == 1 && (filmFetchPending || filmFeaturedPost != null)) {
                    val featuredEngagement = filmFeaturedPost?.let { engagementStates[it.id] }
                    fm.corus.android.ui.components.FeaturedMoviePosterView(
                        post = filmFeaturedPost,
                        frameStyle = currentProfile.frameStyle,
                        rainIntensity = if (filmFeaturedPost == null) fm.corus.android.data.model.RainIntensity.OFF else currentProfile.rainIntensity,
                        snowIntensity = if (filmFeaturedPost == null) fm.corus.android.data.model.SnowIntensity.OFF else currentProfile.snowIntensity,
                        discoIntensity = if (filmFeaturedPost == null) fm.corus.android.data.model.DiscoIntensity.OFF else currentProfile.discoIntensityLevel,
                        likeCount = featuredEngagement?.likeCount ?: (filmFeaturedPost?.likeCount ?: 0),
                        isLiked = featuredEngagement?.isLiked ?: (filmFeaturedPost?.isLiked ?: false),
                        onLikeTap = { filmFeaturedPost?.let { viewModel.toggleLike(it.id) } },
                        onPostTap = { filmFeaturedPost?.let { navigateToFeed(it.id) } },
                    )
                } else if (filteredPosts.isNotEmpty() && selectedSegment == 0) {
                    val featuredPost = filteredPosts.first()
                    if (!isFeaturedArtReady) {
                        // Music featured: render off-screen to trigger image
                        // load, show skeleton on top (matches iOS .hidden() + ZStack).
                        Box {
                            Box(modifier = Modifier
                                .fillMaxWidth()
                                .alpha(0f)
                            ) {
                                fm.corus.android.ui.components.FeaturedCymbalView(
                                    post = featuredPost,
                                    vinylStyle = currentProfile.vinylStyle,
                                    musicService = musicService,
                                    onArtReady = { isFeaturedArtReady = true },
                                )
                            }
                            fm.corus.android.ui.components.SkeletonProfileGrid(
                                isFilmStyle = false,
                            )
                        }
                    } else {
                        val featuredEngagement = engagementStates[featuredPost.id]
                        val shouldStagger = !didRevealFromSkeleton
                        LaunchedEffect(Unit) { didRevealFromSkeleton = true }
                        fm.corus.android.ui.components.FeaturedCymbalView(
                            post = featuredPost,
                            vinylStyle = currentProfile.vinylStyle,
                            musicService = musicService,
                            rainIntensity = currentProfile.rainIntensity,
                            snowIntensity = currentProfile.snowIntensity,
                            discoIntensity = currentProfile.discoIntensityLevel,
                            likeCount = featuredEngagement?.likeCount ?: featuredPost.likeCount,
                            isLiked = featuredEngagement?.isLiked ?: featuredPost.isLiked,
                            onLikeTap = { viewModel.toggleLike(featuredPost.id) },
                            onSpotifyTap = {
                                val track = featuredPost.track
                                if (track.source == fm.corus.android.data.model.TrackSource.SOUNDCLOUD) {
                                    track.soundcloudPermalinkUrl?.takeIf { it.isNotBlank() }?.let {
                                        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(it))) }
                                    }
                                } else if (track.source == fm.corus.android.data.model.TrackSource.APPLEMUSIC) {
                                    track.appleMusicURL?.takeIf { it.isNotBlank() }?.let {
                                        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(it))) }
                                    }
                                } else if (musicService == fm.corus.android.data.model.MusicService.SPOTIFY) {
                                    val uri = track.spotifyURI.ifBlank { track.spotifyWebURL }
                                    if (uri.isNotBlank()) runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(uri))) }
                                } else {
                                    decodeScope.launch {
                                        val url = viewModel.resolveServiceLinkUrl(track)
                                        if (!url.isNullOrBlank()) runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
                                    }
                                }
                            },
                            onPostTap = { navigateToFeed(featuredPost.id) },
                            staggerVinyl = shouldStagger,
                        )
                    }
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
                            message = stringResource(
                                when (likesSavesFilter) {
                                    ProfileMediaFilter.MUSIC -> fm.corus.android.R.string.profile_empty_likes_music
                                    ProfileMediaFilter.FILM -> fm.corus.android.R.string.profile_empty_likes_films
                                    ProfileMediaFilter.ALL -> fm.corus.android.R.string.profile_empty_likes
                                }
                            ),
                        )
                        else -> ProfileEmptyPlaceholder(
                            icon = Icons.Filled.Bookmark,
                            message = stringResource(
                                when (likesSavesFilter) {
                                    ProfileMediaFilter.MUSIC -> fm.corus.android.R.string.profile_empty_saves_music
                                    ProfileMediaFilter.FILM -> fm.corus.android.R.string.profile_empty_saves_films
                                    ProfileMediaFilter.ALL -> fm.corus.android.R.string.profile_empty_saves
                                }
                            ),
                        )
                    }
                }
            }
        }

        // ── Album Art Grid (filtered) ──
        // Hide grid while featured art is loading (skeleton in header covers
        // both areas). Music-only: the FeaturedCymbalView wires onArtReady to
        // flip isFeaturedArtReady. The film featured view (FeaturedMoviePosterView)
        // has no such callback, so gating Film on this flag would leave the grid
        // permanently blank below the featured poster.
        val isFeaturedArtLoading = selectedSegment == 0 && !isFeaturedArtReady && posts.any {
            it.mediaType == fm.corus.android.data.model.MediaType.TRACK
        }
        val filmFetchPending = selectedSegment == 1 && (isLoadingFilms || !hasFetchedFilmPage)
        // Recomputed here (different LazyGrid scope than the featured block).
        // When pending, the featured block renders SkeletonProfileGrid which
        // already covers the grid region, so this block emits nothing — exactly
        // like the music isFeaturedArtLoading path above it.
        val songFetchPending = selectedSegment == 0 &&
            posts.none { it.mediaType == fm.corus.android.data.model.MediaType.TRACK } &&
            (isLoadingSongs || (!hasFetchedSongPage && (currentProfile.trackCount ?: 1) > 0 && hasMoreMixedPosts))
        val isSegmentLoading = (selectedSegment == 2 && isLoadingLiked && likedPosts.isEmpty())
            || (selectedSegment == 3 && isLoadingSaved && savedPosts.isEmpty())

        if (isFeaturedArtLoading || songFetchPending) {
            // Music featured uses SkeletonProfileGrid in the header which covers the grid; emit nothing.
        } else if (filmFetchPending) {
            // FILM tab now uses the in-place FeaturedMoviePosterView shell, so we
            // need to render grid skeleton cells here (header doesn't cover them).
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
        } else if (isSegmentLoading) {
            // Skeleton grid cells while likes/saves load (matching iOS)
            items(15) { index ->
                // Per-cell pulse staggered by index, matching iOS SkeletonAlbumGridCell.
                // Adjacent cells sit at different opacities so grid boundaries stay visible
                // without any inter-cell spacing.
                val transition = rememberInfiniteTransition(label = "skeletonPulse")
                val alpha by transition.animateFloat(
                    initialValue = 0.85f,
                    targetValue = 0.3f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(durationMillis = 750, easing = EaseInOut),
                        repeatMode = RepeatMode.Reverse,
                        initialStartOffset = StartOffset(offsetMillis = index * 80),
                    ),
                    label = "skeletonAlpha",
                )
                Box(
                    modifier = Modifier
                        .aspectRatio(if (showPosterGrid) 2f / 3f else 1f)
                        .background(CorusColors.Skeleton.copy(alpha = alpha)),
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
                        isFilmPoster = showPosterGrid,
                        onClick = { navigateToFeed(post.id) },
                    )
                }
            } else if (selectedSegment <= 1 && filteredPosts.isNotEmpty()) {
                // Featured post is showing but the grid below would be empty —
                // mirror iOS and prompt the user to share another (matches addAnotherCymbalPrompt / addAnotherFilmPrompt).
                item(span = { GridItemSpan(3) }) {
                    ShareAnotherPrompt(
                        isFilm = selectedSegment == 1,
                        onClick = { onOpenCompose(if (selectedSegment == 1) "movie" else "track") },
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
    if (immersive) {
        FrostedStatusStrip(
            hazeState = frost.hazeState,
            topInset = frost.statusBarPadding,
        )
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
                stylePack1Enabled = viewModel.stylePack1Enabled,
                isStaff = currentProfile.isStaff,
                corusFlairOpen = viewModel.corusFlairOpen,
                isSaving = isSavingStyle,
                initialPage = styleInitialPage,
                onSave = { selections ->
                    val current = StyleSelections(
                        vinylColor = currentProfile.vinylStyle,
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
                playlistTrialContext = clubPlaylistTrialContext,
                onDismiss = { showClubOffer = false },
            )
        }
    }

    // ── Profile Share Sheet (in-app Corus DM share), gated by profile_share_enabled ──
    if (showShareSheet) {
        val shareSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        val sentMsg = stringResource(fm.corus.android.R.string.profile_share_toast_profile_sent)
        val shareCardVersion = remember(currentProfile.id, currentProfile.cymbalCount, posts.firstOrNull()?.id) {
            "${currentProfile.cymbalCount}-${posts.firstOrNull()?.id ?: "none"}"
        }
        val shareProfileSubject = remember(currentProfile, posts, shareCardVersion) {
            ShareProfileSubject(
                id = currentProfile.id,
                username = currentProfile.username,
                displayName = currentProfile.displayName,
                avatarUrl = currentProfile.avatarURL ?: currentProfile.avatarThumbURL,
                bio = currentProfile.bio.takeIf { it.isNotBlank() },
                artworkUrls = posts.take(9).mapNotNull { it.displayImageLargeURL ?: it.displayImageURL },
                previewVersion = shareCardVersion,
            )
        }
        LaunchedEffect(shareCardVersion) {
            val previewUrl = shareCardPreviewUrl(
                shareableLink = "https://corus.fm/u/${currentProfile.username}",
                version = shareCardVersion,
            )
            if (previewUrl.isNotBlank()) {
                coil3.SingletonImageLoader.get(context).enqueue(
                    coil3.request.ImageRequest.Builder(context).data(previewUrl).build(),
                )
            }
        }
        LaunchedEffect(Unit) { viewModel.loadRecentShareContacts() }
        ModalBottomSheet(
            onDismissRequest = { showShareSheet = false },
            sheetState = shareSheetState,
            containerColor = CorusColors.Background,
            dragHandle = null,
        ) {
            CorusSystemBars()
            ShareMediaSheet(
                subject = ShareMediaSubject.Profile(shareProfileSubject),
                recentContacts = recentShareContacts,
                searchResults = shareSearchResults,
                isSearching = isShareSearching,
                isLoadingContacts = isLoadingShareContacts,
                onSearchQueryChange = { query -> viewModel.searchShareUsers(query) },
                onSendToUser = { userId, message ->
                    viewModel.sendProfileToUser(
                        userId, currentProfile.id, currentProfile.username,
                        currentProfile.displayName, currentProfile.avatarURL, message,
                    )
                    ToastManager.show(sentMsg)
                    showShareSheet = false
                },
                onDismiss = { showShareSheet = false },
                isOwnProfile = true,
                instagramShareEnabled = viewModel.instagramShareEnabled,
                profileShareAnalytics = ProfileShareAnalytics(
                    profileUserId = currentProfile.id,
                    isOwnProfile = true,
                    entryPoint = profileShareEntryPoint,
                    onShared = { method, theme ->
                        viewModel.logProfileShared(
                            profileUserId = currentProfile.id,
                            method = method,
                            isOwnProfile = true,
                            cardTheme = theme,
                        )
                    },
                    onSheetOpened = {
                        viewModel.logProfileShareSheetOpened(
                            profileUserId = currentProfile.id,
                            isOwnProfile = true,
                            entryPoint = profileShareEntryPoint,
                        )
                    },
                    onThemeChanged = { theme ->
                        viewModel.logProfileShareThemeChanged(currentProfile.id, theme)
                    },
                ),
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
        val alertSource = when (selectedSegment) {
            2 -> CloudFunctionsDataSource.ProfilePlaylistSource.Likes
            3 -> CloudFunctionsDataSource.ProfilePlaylistSource.Saves
            else -> CloudFunctionsDataSource.ProfilePlaylistSource.Posts
        }
        val hasSoundCloud = alertSource == CloudFunctionsDataSource.ProfilePlaylistSource.Posts
            && posts.any { it.isTrack && it.track.source == fm.corus.android.data.model.TrackSource.SOUNDCLOUD }
        val message = if (hasSoundCloud) {
            "Playlist generation creates a Spotify playlist. Any SoundCloud tracks will be skipped."
        } else {
            "Playlist generation creates a Spotify playlist. Would you like to generate it anyway?"
        }
        fm.corus.android.ui.components.CorusPromptOverlay(
            visible = true,
            title = "Spotify Feature",
            message = message,
            iconRes = fm.corus.android.R.drawable.spotify_logo,
            onDismiss = { showPlaylistAlert = false },
            buttons = listOf(
                fm.corus.android.ui.components.CorusPromptButton(
                    label = "Generate Spotify Playlist",
                    emphasized = true,
                    onClick = { viewModel.generatePlaylist(alertSource) },
                ),
                fm.corus.android.ui.components.CorusPromptButton(
                    label = "Cancel",
                    onClick = {},
                ),
            ),
        )
    }

    if (showPlaylistChooser) {
        val chooserSource = when (selectedSegment) {
            2 -> CloudFunctionsDataSource.ProfilePlaylistSource.Likes
            3 -> CloudFunctionsDataSource.ProfilePlaylistSource.Saves
            else -> CloudFunctionsDataSource.ProfilePlaylistSource.Posts
        }
        val count = fm.corus.android.domain.profilePlaylistEligibleCount(
            selectedSegment, profile?.trackCount, profile?.likesCount, profile?.savesCount ?: 0,
        )
        val hasSoundCloud = chooserSource == CloudFunctionsDataSource.ProfilePlaylistSource.Posts
            && posts.any { it.isTrack && it.track.source == fm.corus.android.data.model.TrackSource.SOUNDCLOUD }
        // Fold the service caveat into this one dialog so we never stack a second
        // popup on the chooser. Deezer / Apple Music can't build playlists on
        // Android, so they always fall back to Spotify — name the service so the
        // substitution is clear, not a surprise.
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
                viewModel.generatePlaylist(chooserSource, fullExport = false)
            },
            onAll = {
                viewModel.generatePlaylist(chooserSource, fullExport = true)
            },
            onDismiss = { showPlaylistChooser = false },
        )
    }

    if (showYouTubeMusicPlaylistExplainer) {
        val ytSource = when (selectedSegment) {
            2 -> CloudFunctionsDataSource.ProfilePlaylistSource.Likes
            3 -> CloudFunctionsDataSource.ProfilePlaylistSource.Saves
            else -> CloudFunctionsDataSource.ProfilePlaylistSource.Posts
        }
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
                viewModel.generatePlaylist(ytSource, fullExport = false)
            },
            onAll = {
                viewModel.generatePlaylist(ytSource, fullExport = true)
            },
            onDismiss = { showYouTubeMusicPlaylistExplainer = false },
        )
    }
}

/**
 * Base type for the own-profile EDIT / SHARE pills. One step under the usual
 * button tokens so labels keep a little air inside the capsules:
 * narrow (≤375dp) → 11sp; wider → 13sp.
 */
internal fun profileActionButtonBaseStyle(widthDp: Int): TextStyle {
    val size = if (CorusSpacing.isNarrowProfileActionRow(widthDp)) 11.sp else 13.sp
    return CorusFont.button.copy(fontSize = size)
}

/**
 * One shared style for equal-width EDIT + SHARE capsules: start from [baseStyle]
 * and step the font down until every [texts] label fits in [maxWidth]. Both
 * pills render at that size so Share never shrinks alone.
 */
@Composable
internal fun rememberSharedProfileActionButtonStyle(
    texts: List<String>,
    baseStyle: TextStyle,
    maxWidth: Dp,
    minFontSizeSp: Float = 9f,
): TextStyle {
    val measurer = rememberTextMeasurer()
    val maxWidthPx = with(LocalDensity.current) { maxWidth.toPx() }
    return remember(texts, baseStyle, maxWidthPx, minFontSizeSp) {
        var fontSize = baseStyle.fontSize
        while (fontSize.value > minFontSizeSp) {
            val overflows = texts.any { label ->
                measurer.measure(
                    text = label,
                    style = baseStyle.copy(fontSize = fontSize),
                    maxLines = 1,
                    softWrap = false,
                ).size.width > maxWidthPx
            }
            if (!overflows) break
            fontSize *= 0.92f
        }
        baseStyle.copy(fontSize = fontSize)
    }
}

/**
 * A single-line [Text] that shrinks its font size to fit the available width
 * rather than wrapping. It starts at [style]'s font size, so on devices where the
 * text already fits the rendering is identical to a plain [Text]; only when the
 * text would overflow (narrower screens) does it step the size down until it fits.
 *
 * Uses the pre-Compose-1.8 `onTextLayout` + `didOverflowWidth` pattern because the
 * stable `autoSize` parameter isn't available on our Compose BOM (1.7.x).
 *
 * Prefer [rememberSharedProfileActionButtonStyle] for the equal-width EDIT + SHARE
 * row so both labels stay the same size; this remains for single pills (e.g. FOLLOW).
 */
@Composable
// Shared with OtherProfileScreen (same package) so the FOLLOWING pill can shrink
// to fit on one line instead of clipping, exactly like the EDIT button here.
internal fun ShrinkToFitText(
    text: String,
    style: TextStyle,
    color: Color,
    modifier: Modifier = Modifier,
) {
    var resized by remember(text, style) { mutableStateOf(style) }
    var readyToDraw by remember(text, style) { mutableStateOf(false) }
    Text(
        text = text,
        style = resized,
        color = color,
        maxLines = 1,
        softWrap = false,
        overflow = TextOverflow.Visible,
        // Hide until a size that fits is found, to avoid a one-frame flash of the
        // oversized text before it's stepped down.
        modifier = modifier.drawWithContent { if (readyToDraw) drawContent() },
        onTextLayout = { result ->
            if (result.didOverflowWidth && resized.fontSize.value > 9f) {
                resized = resized.copy(fontSize = resized.fontSize * 0.92f)
            } else {
                readyToDraw = true
            }
        },
    )
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
private fun ShareAnotherPrompt(
    isFilm: Boolean,
    onClick: () -> Unit,
) {
    val accent = CorusColors.Accent
    val cornerRadius = 16.dp
    val cornerRadiusPx = with(LocalDensity.current) { cornerRadius.toPx() }
    val strokeWidthPx = with(LocalDensity.current) { 1.5.dp.toPx() }
    val dashOnPx = with(LocalDensity.current) { 8.dp.toPx() }
    val dashOffPx = with(LocalDensity.current) { 6.dp.toPx() }
    val dashEffect = remember(dashOnPx, dashOffPx) {
        androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(dashOnPx, dashOffPx), 0f)
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = CorusSpacing.lg, vertical = CorusSpacing.xl)
            .clip(RoundedCornerShape(cornerRadius))
            .background(accent.copy(alpha = 0.04f))
            .clickable(onClick = onClick)
            .drawBehind {
                drawRoundRect(
                    color = accent.copy(alpha = 0.3f),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(cornerRadiusPx, cornerRadiusPx),
                    style = androidx.compose.ui.graphics.drawscope.Stroke(
                        width = strokeWidthPx,
                        pathEffect = dashEffect,
                    ),
                )
            }
            .padding(horizontal = CorusSpacing.xl, vertical = CorusSpacing.xxxl + CorusSpacing.lg),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(accent),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Filled.Add,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(20.dp),
                )
            }
            Spacer(modifier = Modifier.height(CorusSpacing.md))
            Text(
                text = stringResource(
                    if (isFilm) fm.corus.android.R.string.profile_share_another_film
                    else fm.corus.android.R.string.profile_share_another_track
                ),
                style = CorusFont.bodyMedium,
                color = CorusColors.Text,
            )
            Spacer(modifier = Modifier.height(CorusSpacing.xs))
            Text(
                text = stringResource(fm.corus.android.R.string.profile_share_another_subtitle),
                style = CorusFont.caption,
                color = CorusColors.Secondary,
            )
        }
    }
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

/**
 * Tab label for the profile segment strip. Likes/Saves tabs ([reservesChevron])
 * always reserve the chevron's width — a hidden balancing chevron on the leading
 * side keeps the title centered, and the visible chevron is toggled by opacity —
 * so the label never reflows when the chevron appears after load or when
 * switching between the two tabs.
 */
@Composable
private fun ProfileTabLabel(
    title: String,
    isSelected: Boolean,
    reservesChevron: Boolean,
    showChevron: Boolean,
    activeIcon: ImageVector?,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (reservesChevron) {
            Icon(
                Icons.Filled.KeyboardArrowDown,
                contentDescription = null,
                modifier = Modifier.size(14.dp).alpha(0f),
            )
        }
        Text(
            text = title,
            style = CorusFont.tabLabel,
            color = if (isSelected) CorusColors.Text else CorusColors.Tertiary,
        )
        if (activeIcon != null) {
            Icon(
                activeIcon,
                contentDescription = null,
                tint = CorusColors.Accent,
                modifier = Modifier.size(14.dp),
            )
        }
        if (reservesChevron) {
            Icon(
                Icons.Filled.KeyboardArrowDown,
                contentDescription = null,
                tint = if (activeIcon != null) CorusColors.Accent else CorusColors.Tertiary,
                modifier = Modifier
                    .size(14.dp)
                    .alpha(if (showChevron) 1f else 0f),
            )
        }
    }
}

@Composable
private fun ProfileFilterMenuItem(
    label: String,
    value: ProfileMediaFilter,
    icon: ImageVector?,
    current: ProfileMediaFilter,
    onSelect: (ProfileMediaFilter) -> Unit,
) {
    DropdownMenuItem(
        text = { Text(label, color = CorusColors.Text) },
        leadingIcon = icon?.let {
            { Icon(it, contentDescription = null, tint = CorusColors.Text, modifier = Modifier.size(18.dp)) }
        },
        trailingIcon = if (value == current) {
            { Icon(Icons.Filled.Check, contentDescription = null, tint = CorusColors.Accent, modifier = Modifier.size(18.dp)) }
        } else {
            null
        },
        onClick = { onSelect(value) },
    )
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
