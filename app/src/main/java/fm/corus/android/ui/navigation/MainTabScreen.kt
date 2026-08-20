package fm.corus.android.ui.navigation

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Scaffold
import fm.corus.android.ui.components.PlaybackModePromptOverlay
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import androidx.compose.ui.platform.LocalDensity
import fm.corus.android.R
import fm.corus.android.domain.HapticManager
import fm.corus.android.ui.LocalHapticManager
import fm.corus.android.ui.components.ExpandedPhoto
import fm.corus.android.ui.components.FullScreenPhotoViewer
import fm.corus.android.ui.components.MiniPlayerBar
import fm.corus.android.ui.components.LocalBottomBarHeight
import fm.corus.android.ui.components.LocalContentHaze
import fm.corus.android.ui.components.blockTouchPassthrough
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect
import fm.corus.android.ui.player.ExpandingPlayerHost
import fm.corus.android.ui.player.FullPlayerQueueSheet
import fm.corus.android.ui.player.FullPlayerScreen
import fm.corus.android.ui.player.liveExpansion
import fm.corus.android.ui.player.rememberPlayerExpansionState
import fm.corus.android.ui.screens.compose.ComposeScreen
import fm.corus.android.ui.screens.compose.ComposeViewModel
import fm.corus.android.ui.screens.feed.CommentsBottomSheet
import fm.corus.android.ui.screens.feed.LikesBottomSheet
import fm.corus.android.data.model.MusicService
import fm.corus.android.service.DeepLinkDestination
import fm.corus.android.ui.screens.subscription.CymbalClubOfferSheet
import fm.corus.android.ui.screens.subscription.PaywallSource
import fm.corus.android.ui.theme.CorusColors
import fm.corus.android.ui.theme.CorusFont
import fm.corus.android.ui.components.LocalContainingTabSelected
import fm.corus.android.ui.components.currentStatusBarTopPx
import fm.corus.android.ui.theme.CorusSpacing
import fm.corus.android.ui.theme.CorusSystemBars
import fm.corus.android.ui.theme.LocalCorusDarkTheme
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.zIndex
import androidx.core.view.WindowInsetsControllerCompat
import android.app.Activity
import fm.corus.android.ui.util.PushNotificationPermission
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private const val FrostedBottomBar = true

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun MainTabScreen(
    viewModel: MainTabViewModel = hiltViewModel(),
    pendingNotificationDestination: StateFlow<DeepLinkDestination?>? = null,
    onNotificationDestinationConsumed: () -> Unit = {},
) {
    var selectedTab by rememberSaveable { mutableStateOf(CorusTab.FEED) }
    var showCompose by rememberSaveable { mutableStateOf(false) }
    var composeMovieMode by rememberSaveable { mutableStateOf(false) }
    val composeViewModel: ComposeViewModel = hiltViewModel()
    val showMilestonePaywall by viewModel.showMilestonePaywall.collectAsState()
    val milestonePaywallSource by viewModel.milestonePaywallSource.collectAsState()
    val notificationCount by viewModel.notificationCount.collectAsState()
    val unreadMessageCount by viewModel.unreadMessageCount.collectAsState()
    val hasRequestedPushPermission by viewModel.hasRequestedPushPermission.collectAsState()

    // Fallback for users who signed up before the onboarding primer shipped.
    // hasRequestedPushPermission is also set on Not now, so the feed does not
    // immediately fire the system dialog after they skip. Matches iOS
    // MainTabView.requestNotificationPermissionIfNeeded.
    val context = LocalContext.current

    // Re-assert the app's theme-default system-bar appearance on every tab switch.
    // All tab NavHosts stay composed at once (see TabContent), so an immersive
    // destination page (artist/song/album/director/film) on the tab we're leaving
    // can't restore the status bar itself — it isn't disposed — and the arriving
    // root screen only sets bar appearance from a one-shot CorusSystemBars
    // SideEffect that already fired at launch. Without this, the immersive
    // white-over-hero icons leak onto a light Feed/Profile as invisible
    // white-on-white. The active immersive page (if any) re-applies its own
    // override right after, keyed on its tab becoming selected again.
    val darkTheme = LocalCorusDarkTheme.current
    val rootView = LocalView.current
    LaunchedEffect(selectedTab, darkTheme) {
        (rootView.context as? Activity)?.window?.let { window ->
            val controller = WindowInsetsControllerCompat(window, window.decorView)
            controller.isAppearanceLightStatusBars = !darkTheme
            controller.isAppearanceLightNavigationBars = !darkTheme
        }
    }
    val musicService by viewModel.musicServicePreference.current.collectAsState()
    val playFullSongs by viewModel.playFullSongs.collectAsState()
    val alwaysPlayFullSongs by viewModel.alwaysPlayFullSongs.collectAsState()
    val playbackModePending by viewModel.playbackModePromptManager.pending.collectAsState()
    val isResolvingLinkOut by fm.corus.android.domain.MusicServiceLinkOut.isResolving.collectAsState()
    val pushPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { _ ->
        viewModel.markPushPermissionRequested()
    }
    LaunchedEffect(hasRequestedPushPermission) {
        if (!hasRequestedPushPermission &&
            PushNotificationPermission.shouldRequestPushPermission(context)
        ) {
            pushPermissionLauncher.launch(PushNotificationPermission.permission)
        } else if (!hasRequestedPushPermission) {
            // No system prompt needed (already granted or pre-Android-13);
            // still record so we don't re-check every launch.
            viewModel.markPushPermissionRequested()
        }
    }

    // Club offer sheet state
    var showClubOffer by remember { mutableStateOf(false) }
    var clubOfferSource by remember { mutableStateOf(PaywallSource.DEFAULT) }

    // Save cap snackbar
    val saveCapSnackbarHost = remember { androidx.compose.material3.SnackbarHostState() }
    LaunchedEffect(Unit) {
        viewModel.postEngagementManager.saveCapEvents.collect { event ->
            when (event) {
                is fm.corus.android.domain.SaveCapEvent.PaywallRequested -> {
                    viewModel.logPaywallShown("save_limit")
                    clubOfferSource = PaywallSource.SAVE_LIMIT
                    showClubOffer = true
                }
                is fm.corus.android.domain.SaveCapEvent.WarningToast -> {
                    val result = saveCapSnackbarHost.showSnackbar(
                        message = event.message,
                        actionLabel = if (event.tappable) "Upgrade" else null,
                        duration = androidx.compose.material3.SnackbarDuration.Short,
                    )
                    if (event.tappable && result == androidx.compose.material3.SnackbarResult.ActionPerformed) {
                        viewModel.logSaveWarningTapped(event.savesRemaining)
                        viewModel.logPaywallShown("save_limit")
                        clubOfferSource = PaywallSource.SAVE_LIMIT
                        showClubOffer = true
                    }
                }
            }
        }
    }

    // Surface a toast when a tapped song has no playable preview. The event is
    // emitted by the shared NowPlayingManager, so this single collector covers
    // every surface (feed, compose search, profile) without per-screen wiring.
    LaunchedEffect(Unit) {
        viewModel.nowPlayingManager.previewUnavailable.collect {
            fm.corus.android.ui.components.ToastManager.show(
                context.getString(R.string.now_playing_no_preview)
            )
        }
    }

    // Observe pre-selected media IDs for compose-with-preselection flow.
    val preSelectedTrackId by viewModel.preSelectedTrackId.collectAsState()
    val preSelectedTrack by viewModel.preSelectedTrack.collectAsState()
    val preSelectedMovieId by viewModel.preSelectedMovieId.collectAsState()
    val repostOriginalPost by viewModel.repostOriginalPost.collectAsState()

    // Each tab gets its own NavController to preserve back stack
    val feedNavController = rememberNavController()
    val searchNavController = rememberNavController()
    val notificationsNavController = rememberNavController()
    val profileNavController = rememberNavController()

    // Map for tab-based navigation from overlay sheets
    val navControllers = remember(feedNavController, searchNavController, notificationsNavController, profileNavController) {
        mapOf(
            CorusTab.FEED to feedNavController,
            CorusTab.EXPLORE to searchNavController,
            CorusTab.NOTIFICATIONS to notificationsNavController,
            CorusTab.PROFILE to profileNavController,
        )
    }

    // Gates the mini-player "return to origin" navigation on the same flag that
    // surfaces the artist/album pages themselves.
    val artistPagesEnabled = rememberArtistPagesEnabled()

    // Comments sheet is hosted here at the root (not inside a tab nav-graph) so it covers
    // the tab bar like iOS and gets real window insets (the nav-graph content consumes
    // them). Tabs open it via onShowComments; navigation routes through the active tab.
    var commentPostId by remember { mutableStateOf<String?>(null) }
    var playerLikesPostId by remember { mutableStateOf<String?>(null) }
    var commentSheetAutoFocus by remember { mutableStateOf(false) }
    var commentsRefreshSignal by remember { mutableIntStateOf(0) }
    var showPlayerQueue by remember { mutableStateOf(false) }
    var playerSharePost by remember { mutableStateOf<fm.corus.android.data.model.CymbalPost?>(null) }

    var expandedPhoto by remember { mutableStateOf<ExpandedPhoto?>(null) }

    // When a pre-selected media ID becomes non-null, open compose overlay.
    // Reset then immediately start the load so isLoadingPreSelection is true
    // before ComposeScreen enters composition (avoids a flash of the search view).
    LaunchedEffect(preSelectedTrackId) {
        val trackId = preSelectedTrackId ?: return@LaunchedEffect
        if (viewModel.subscriptionRepository.canPost) {
            composeViewModel.reset()
            composeViewModel.loadAndSelectTrack(trackId)
            showCompose = true
        } else { clubOfferSource = PaywallSource.POST_LIMIT; showClubOffer = true }
    }
    LaunchedEffect(preSelectedTrack) {
        val track = preSelectedTrack ?: return@LaunchedEffect
        if (viewModel.subscriptionRepository.canPost) {
            composeViewModel.reset()
            composeViewModel.selectPreloadedTrack(track)
            showCompose = true
        } else { clubOfferSource = PaywallSource.POST_LIMIT; showClubOffer = true }
    }
    LaunchedEffect(preSelectedMovieId) {
        val movieId = preSelectedMovieId ?: return@LaunchedEffect
        if (viewModel.subscriptionRepository.canPost) {
            composeViewModel.reset()
            composeViewModel.loadAndSelectMovie(movieId)
            showCompose = true
        } else { clubOfferSource = PaywallSource.POST_LIMIT; showClubOffer = true }
    }
    LaunchedEffect(repostOriginalPost) {
        val original = repostOriginalPost ?: return@LaunchedEffect
        if (viewModel.subscriptionRepository.canPost) {
            composeViewModel.reset()
            composeViewModel.setRepostContext(original)
            composeMovieMode = original.isMovie
            showCompose = true
        } else { clubOfferSource = PaywallSource.POST_LIMIT; showClubOffer = true }
    }

    // Scroll-to-top triggers: increment to signal a root screen should scroll up
    val feedScrollToTop = remember { mutableIntStateOf(0) }
    val searchScrollToTop = remember { mutableIntStateOf(0) }
    val notificationsScrollToTop = remember { mutableIntStateOf(0) }
    val profileScrollToTop = remember { mutableIntStateOf(0) }

    // Tab-activation trigger: increments every time the profile tab is selected
    // (including re-taps) so the profile screen can fire a cheap, throttled
    // refresh of the featured post(s)' engagement counts.
    val profileTabActivation = remember { mutableIntStateOf(0) }

    // Tab-activation trigger for the Activity tab: increments every time it's
    // selected so the notifications screen can mark the feed as viewed
    // (stamp lastSeen + mark all read) on every visit. Required because all tab
    // screens stay composed, so the screen's own LaunchedEffect(Unit) only fires
    // once at launch and can't re-trigger the mark-read on later visits.
    val notificationsTabActivation = remember { mutableIntStateOf(0) }

    // Frosted tab bar (Haze). The expanding player owns its own art-wash
    // surface above the tab bar — matching iOS UnifiedPlayerChrome.
    // Declared before notification deep-link handling so a lock-screen tap can
    // collapse an open full player before Activity navigation lands underneath.
    val bottomHaze = remember { HazeState() }
    val bottomFrost = CorusColors.Background
    val playerScope = rememberCoroutineScope()
    val playerHaptics = LocalHapticManager.current
    val playerExpansion = rememberPlayerExpansionState()

    // Handle notification tap navigation
    val notificationDestination = pendingNotificationDestination?.collectAsState()?.value
    LaunchedEffect(notificationDestination) {
        if (notificationDestination == null) return@LaunchedEffect
        // Collapse the full player if it was left open when the app went to
        // background (e.g. lock-screen notification tap). Navigation lands on
        // the Activity tab underneath the player overlay otherwise.
        if (playerExpansion.isExpandedOrExpanding) {
            playerExpansion.collapse()
        }
        val navController = notificationsNavController
        selectedTab = CorusTab.NOTIFICATIONS
        when (notificationDestination) {
            is DeepLinkDestination.Post -> navController.navigate(PostDetailRoute(notificationDestination.postId))
            is DeepLinkDestination.PostComment -> navController.navigate(
                SinglePostCommentsRoute(notificationDestination.postId, notificationDestination.commentId)
            )
            is DeepLinkDestination.Profile -> navController.navigate(OtherProfileRoute(notificationDestination.userId))
            is DeepLinkDestination.Thread -> {
                navController.navigate(ThreadListRoute)
                navController.navigate(MessageThreadRoute(
                    threadId = notificationDestination.threadId,
                    otherUserId = notificationDestination.otherUserId,
                ))
            }
            is DeepLinkDestination.Hashtag -> navController.navigate(HashtagFeedRoute(notificationDestination.tag))
            is DeepLinkDestination.ProfileByUsername -> navController.navigate(ProfileByUsernameRoute(notificationDestination.username))
            // Corus Club paywall (app.corus.fm/settings/club, corus://club). The
            // offer screen renders a member-specific CTA when the user is already a
            // Club member, so the offer route is the correct target either way.
            is DeepLinkDestination.Club -> navController.navigate(CymbalClubOfferRoute())
            // The key a public catalog URL carried, which may be an id or a
            // clean-URL slug. EntityLinkRoute reads it and replaces itself with
            // the entity's own page.
            is DeepLinkDestination.Entity -> navController.navigate(
                EntityLinkRoute(notificationDestination.segment.segment, notificationDestination.key)
            )
        }
        onNotificationDestinationConsumed()
    }

    val density = LocalDensity.current
    // Seed with a typical bar height so park math is sane before first measure.
    var tabBarHeightPx by remember { mutableStateOf(with(density) { 90.dp.toPx() }) }
    var miniHeightPx by remember { mutableStateOf(with(density) { 56.dp.toPx() }) }

    val nowPlayingState by viewModel.nowPlayingManager.state.collectAsState()
    val isHydratingExternalSpotify by viewModel.nowPlayingManager.isHydratingExternalSpotify.collectAsState()
    val isExternalSpotifyListening by viewModel.nowPlayingManager.isExternalSpotifyListeningFlow.collectAsState()
    val showsMiniPlayer = nowPlayingState.hasActiveTrack && !isHydratingExternalSpotify &&
        (!isExternalSpotifyListening ||
            (nowPlayingState.trackName.isNotBlank() && nowPlayingState.trackName != "Unknown Track"))
    // Hold the white empty shell invisible until the art wash has painted —
    // otherwise first play flashes a solid-white mini + tab bar.
    var playerSurfaceReady by remember { mutableStateOf(false) }

    LaunchedEffect(showsMiniPlayer) {
        if (!showsMiniPlayer) {
            playerSurfaceReady = false
            playerExpansion.collapse()
        }
    }

    val livePlayerExpansion = if (showsMiniPlayer) playerExpansion.liveExpansion() else 0f
    val bottomChromeHeight = with(density) {
        tabBarHeightPx.toDp() +
            if (showsMiniPlayer && playerSurfaceReady) miniHeightPx.toDp() else 0.dp
    }

    Box(modifier = Modifier.fillMaxSize()) {
    // Tab bar + mini player live as overlays (iOS MainTabView): player sheet
    // under the tab bar; tab bar slides away as the player expands.
    Scaffold(
        bottomBar = {},
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .then(
                    if (FrostedBottomBar) {
                        Modifier.padding(
                            top = padding.calculateTopPadding(),
                            bottom = tabContentBottomPadding(
                                frosted = true,
                                chromeHeight = bottomChromeHeight,
                                scaffoldBottom = padding.calculateBottomPadding(),
                            ),
                        )
                    } else {
                        Modifier.padding(padding)
                    }
                )
                .consumeWindowInsets(padding)
        ) {
          CompositionLocalProvider(
              LocalBottomBarHeight provides
                  (if (FrostedBottomBar) bottomChromeHeight else 0.dp),
              LocalContentHaze provides (if (FrostedBottomBar) bottomHaze else null),
          ) {
            // Keep all tab NavHosts alive but only show the selected one.
            // This preserves scroll position and back stack per tab.
            TabContent(visible = selectedTab == CorusTab.FEED) {
                FeedNavGraph(
                    navController = feedNavController,
                    mainTabViewModel = viewModel,
                    scrollToTopTrigger = feedScrollToTop.intValue,
                    isFeedTabSelected = selectedTab == CorusTab.FEED,
                    onShowComments = { commentPostId = it },
                    onShowPhoto = { expandedPhoto = it },
                    onNavigateToCompose = {
                        if (viewModel.subscriptionRepository.canPost) {
                            composeViewModel.reset()
                            showCompose = true
                        } else {
                            clubOfferSource = PaywallSource.POST_LIMIT
                            showClubOffer = true
                        }
                    },
                )
            }
            TabContent(visible = selectedTab == CorusTab.EXPLORE) {
                SearchNavGraph(
                    navController = searchNavController,
                    mainTabViewModel = viewModel,
                    scrollToTopTrigger = searchScrollToTop.intValue,
                    isContainingTabSelected = selectedTab == CorusTab.EXPLORE,
                    onShowComments = { commentPostId = it },
                    onShowPhoto = { expandedPhoto = it },
                )
            }
            TabContent(visible = selectedTab == CorusTab.NOTIFICATIONS) {
                NotificationsNavGraph(
                    navController = notificationsNavController,
                    mainTabViewModel = viewModel,
                    scrollToTopTrigger = notificationsScrollToTop.intValue,
                    tabActivationTrigger = notificationsTabActivation.intValue,
                    isContainingTabSelected = selectedTab == CorusTab.NOTIFICATIONS,
                    onShowComments = { commentPostId = it },
                    onShowPhoto = { expandedPhoto = it },
                )
            }
            TabContent(visible = selectedTab == CorusTab.PROFILE) {
                ProfileNavGraph(
                    navController = profileNavController,
                    mainTabViewModel = viewModel,
                    scrollToTopTrigger = profileScrollToTop.intValue,
                    tabActivationTrigger = profileTabActivation.intValue,
                    isContainingTabSelected = selectedTab == CorusTab.PROFILE,
                    onShowComments = { commentPostId = it },
                    onShowPhoto = { expandedPhoto = it },
                    onOpenCompose = { mediaType ->
                        if (viewModel.subscriptionRepository.canPost) {
                            composeViewModel.reset()
                            composeMovieMode = mediaType == "movie"
                            showCompose = true
                        } else { clubOfferSource = PaywallSource.POST_LIMIT; showClubOffer = true }
                    },
                )
            }

          }
        }

        // Milestone paywall — show club offer sheet
        if (showMilestonePaywall) {
            LaunchedEffect(Unit) {
                clubOfferSource = when (milestonePaywallSource) {
                    MilestonePaywallSource.FIRST_POST -> PaywallSource.FIRST_POST
                    MilestonePaywallSource.TENTH_POST -> PaywallSource.TENTH_POST
                    null -> PaywallSource.DEFAULT
                }
                viewModel.dismissMilestonePaywall()
                showClubOffer = true
            }
        }
    }

    // Expanding now-playing sheet (above tab content, below tab bar).
    if (showsMiniPlayer) {
        ExpandingPlayerHost(
            expansionState = playerExpansion,
            parkInsetPx = tabBarHeightPx.coerceAtLeast(1f),
            artworkUrl = nowPlayingState.albumArtLargeURL ?: nowPlayingState.albumArtURL,
            nowPlayingManager = viewModel.nowPlayingManager,
            onMiniHeightPxChanged = { miniHeightPx = it },
            onSurfaceReady = { playerSurfaceReady = true },
            modifier = Modifier
                .fillMaxSize()
                .zIndex(1f)
                .alpha(if (playerSurfaceReady) 1f else 0f),
            miniContent = { miniInteractive ->
                MiniPlayerBar(
                    nowPlayingManager = viewModel.nowPlayingManager,
                    engagementManager = viewModel.postEngagementManager,
                    onLikeTap = { viewModel.toggleLikeForCurrentTrack() },
                    musicService = musicService,
                    playFullSongs = playFullSongs,
                    alwaysPlayFullSongs = alwaysPlayFullSongs,
                    onPlaybackModeChange = { viewModel.setPlayFullSongs(it) },
                    remoteConfig = viewModel.remoteConfigService,
                    resolveLinkOut = { viewModel.resolveCurrentServiceLinkUrl() },
                    embeddedInExpandingShell = true,
                    interactive = miniInteractive,
                    onTrackTap = {
                        // iOS openFullPlayer — light impact on expand.
                        playerHaptics.impact(HapticManager.ImpactStyle.LIGHT)
                        viewModel.logFullPlayerOpened()
                        playerScope.launch { playerExpansion.expand() }
                    },
                )
            },
            fullContent = { fullInteractive ->
                FullPlayerScreen(
                    nowPlayingManager = viewModel.nowPlayingManager,
                    engagementManager = viewModel.postEngagementManager,
                    musicService = musicService,
                    playFullSongs = playFullSongs,
                    alwaysPlayFullSongs = alwaysPlayFullSongs,
                    onPlaybackModeChange = { viewModel.setPlayFullSongs(it) },
                    remoteConfig = viewModel.remoteConfigService,
                    interactive = fullInteractive,
                    onContentAtTopChange = { playerExpansion.isContentAtTop = it },
                    onDismiss = {
                        viewModel.logFullPlayerDismissed("chevron")
                        playerScope.launch { playerExpansion.collapse() }
                    },
                    onLikeTap = { viewModel.toggleLikeForCurrentTrack() },
                    onOpenQueue = {
                        viewModel.logFullPlayerQueueOpened()
                        showPlayerQueue = true
                    },
                    onOpenComments = { postId, _ ->
                        commentSheetAutoFocus = true
                        commentPostId = postId
                    },
                    onOpenUser = { userId ->
                        playerScope.launch {
                            playerExpansion.collapse()
                            navControllers[selectedTab]?.navigate(OtherProfileRoute(userId))
                        }
                    },
                    onOpenPost = { postId ->
                        playerScope.launch {
                            playerExpansion.collapse()
                            navControllers[selectedTab]?.navigate(PostDetailRoute(postId))
                        }
                    },
                    onRepost = { post ->
                        playerHaptics.impact(HapticManager.ImpactStyle.LIGHT)
                        if (viewModel.subscriptionRepository.canPost) {
                            viewModel.setRepostOriginalPost(post)
                            composeViewModel.reset()
                            showCompose = true
                            playerScope.launch { playerExpansion.collapse() }
                        } else {
                            clubOfferSource = PaywallSource.POST_LIMIT
                            showClubOffer = true
                        }
                    },
                    onSharePost = { post ->
                        playerHaptics.impact(HapticManager.ImpactStyle.LIGHT)
                        playerSharePost = post
                    },
                    onSavePost = { postId -> viewModel.toggleSaveForPost(postId) },
                    onOpenSongDetail = { track ->
                        playerScope.launch {
                            playerExpansion.collapse()
                            navControllers[selectedTab]?.navigate(track.toSongDetailRoute())
                        }
                    },
                    onOpenFilmDetail = { post ->
                        post.movieId?.let { movieId ->
                            playerScope.launch {
                                playerExpansion.collapse()
                                navControllers[selectedTab]?.navigate(
                                    FilmDetailRoute(
                                        movieId = movieId,
                                        movieTitle = post.movieTitle,
                                        directorName = post.directorName,
                                        releaseYear = post.releaseYear,
                                        posterURL = post.posterURL,
                                    ),
                                )
                            }
                        }
                    },
                    onOpenFilmMovie = { movie ->
                        playerScope.launch {
                            playerExpansion.collapse()
                            navControllers[selectedTab]?.navigate(movie.toFilmDetailRoute())
                        }
                    },
                    onOpenArtist = if (artistPagesEnabled) {
                        { route ->
                            playerScope.launch {
                                playerExpansion.collapse()
                                navControllers[selectedTab]?.navigate(route)
                            }
                        }
                    } else {
                        null
                    },
                    onOpenAlbum = if (artistPagesEnabled) {
                        { route ->
                            playerScope.launch {
                                playerExpansion.collapse()
                                navControllers[selectedTab]?.navigate(route)
                            }
                        }
                    } else {
                        null
                    },
                    onOpenDirector = if (artistPagesEnabled) {
                        { route ->
                            playerScope.launch {
                                playerExpansion.collapse()
                                navControllers[selectedTab]?.navigate(route)
                            }
                        }
                    } else {
                        null
                    },
                    onComposeTrack = {
                        val track = viewModel.nowPlayingManager.state.value.let { s ->
                            s.trackId?.let { id ->
                                fm.corus.android.data.model.CymbalTrack(
                                    id = id,
                                    name = s.trackName,
                                    artistName = s.artistName,
                                    albumName = "",
                                    albumArtURL = s.albumArtURL,
                                    albumArtLargeURL = s.albumArtLargeURL,
                                    spotifyURI = s.spotifyURI.orEmpty(),
                                    spotifyWebURL = s.spotifyWebURL.orEmpty(),
                                    isrc = s.isrc,
                                    source = s.source,
                                )
                            }
                        }
                        if (track != null) {
                            viewModel.logFullPlayerComposeTapped()
                            if (viewModel.subscriptionRepository.canPost) {
                                viewModel.setPreSelectedTrack(track)
                                playerScope.launch { playerExpansion.collapse() }
                            } else {
                                clubOfferSource = PaywallSource.POST_LIMIT
                                showClubOffer = true
                            }
                        }
                    },
                    onHashtagTap = { tag ->
                        playerScope.launch {
                            playerExpansion.collapse()
                            navControllers[selectedTab]?.navigate(HashtagFeedRoute(tag))
                        }
                    },
                    commentsRefreshSignal = commentsRefreshSignal,
                    onOpenLikes = { postId -> playerLikesPostId = postId },
                    onLikeLongPress = { postId -> playerLikesPostId = postId },
                    onOpenInService = {
                        // Same path as the mini-player logo — Spotify preference
                        // opens the track URI/web URL (resolveLinkOut returns null).
                        viewModel.logFullPlayerOpenInServiceTapped(musicService.value)
                        fm.corus.android.domain.MusicServiceLinkOut.openNowPlayingInPreferredService(
                            context = context,
                            scope = playerScope,
                            state = viewModel.nowPlayingManager.state.value,
                            musicService = musicService,
                            resolveLinkOut = { viewModel.resolveCurrentServiceLinkUrl() },
                        )
                    },
                )
            },
        )
    }

    // Tab bar — topmost chrome when collapsed; slides off as the player expands.
    Column(
        modifier = Modifier
            .align(Alignment.BottomCenter)
            .fillMaxWidth()
            .zIndex(2f)
            .offset {
                IntOffset(
                    0,
                    (livePlayerExpansion.coerceIn(0f, 1f) * tabBarHeightPx.coerceAtLeast(0f))
                        .roundToInt(),
                )
            }
            .onSizeChanged { tabBarHeightPx = it.height.toFloat() }
            .then(
                // With a ready mini-player wash, skip Haze so mini + tabs share
                // one frosted surface. Keep Haze until the wash is ready so the
                // tab bar doesn't flash solid white on first play.
                if (FrostedBottomBar && !(showsMiniPlayer && playerSurfaceReady)) {
                    Modifier.hazeEffect(state = bottomHaze) {
                        blurRadius = 30.dp
                        backgroundColor = bottomFrost
                        tints = listOf(HazeTint(bottomFrost.copy(alpha = 0.88f)))
                    }
                } else {
                    Modifier
                },
            )
            .blockTouchPassthrough(),
    ) {
        CorusBottomBar(
            // Stay "frosted" (no solid fill / hairline) whenever glass chrome is
            // on — including when the mini-player wash is the shared background.
            frosted = FrostedBottomBar,
            selectedTab = selectedTab,
            notificationTabBadgeCount = notificationTabBadge(
                selectedTab = selectedTab,
                notificationCount = notificationCount,
                unreadMessageCount = unreadMessageCount,
            ),
            onTabSelected = { tab ->
                if (tab == CorusTab.COMPOSE) {
                    if (viewModel.subscriptionRepository.canPost) {
                        composeViewModel.reset()
                        showCompose = true
                    } else {
                        clubOfferSource = PaywallSource.POST_LIMIT
                        showClubOffer = true
                    }
                } else {
                    if (tab == selectedTab) {
                        val navController = navControllers[tab]!!
                        val popped = navController.popToStart()
                        if (!popped) {
                            when (tab) {
                                CorusTab.FEED -> feedScrollToTop.intValue++
                                CorusTab.EXPLORE -> searchScrollToTop.intValue++
                                CorusTab.NOTIFICATIONS -> notificationsScrollToTop.intValue++
                                CorusTab.PROFILE -> profileScrollToTop.intValue++
                                else -> {}
                            }
                        }
                    }
                    if (tab == CorusTab.NOTIFICATIONS) {
                        if (selectedTab != CorusTab.NOTIFICATIONS) {
                            viewModel.onActivityTabEntered()
                        }
                        notificationsTabActivation.intValue++
                    }
                    if (tab == CorusTab.PROFILE) {
                        profileTabActivation.intValue++
                    }
                    selectedTab = tab
                }
            },
            onComposeTapped = {
                if (viewModel.subscriptionRepository.canPost) {
                    composeViewModel.reset()
                    showCompose = true
                } else {
                    clubOfferSource = PaywallSource.POST_LIMIT
                    showClubOffer = true
                }
            },
        )
    }

    playbackModePending?.let { pendingChoice ->
        PlaybackModePromptOverlay(
            kind = pendingChoice.kind,
            onSecondary = {
                if (pendingChoice.kind ==
                    fm.corus.android.domain.SpotifyFtuePromptKind.LINK_SPOTIFY
                ) {
                    viewModel.playbackModePromptManager.chooseNotNow()
                } else {
                    viewModel.playbackModePromptManager.choosePreviews()
                }
            },
            onLinkSpotify = { viewModel.playbackModePromptManager.chooseLinkSpotify() },
            modifier = Modifier
                .fillMaxSize()
                .zIndex(3f),
        )
    }

    // Status-bar strip cover. The immersive detail pages (song/artist/album/director/
    // film) paint their blurred backdrop UP into the shared, unclipped status-bar strip
    // (see extendIntoStatusBar). When a non-immersive page (post/profile) is pushed on
    // top, that strip paint can linger behind it (the strip isn't part of the pushed
    // page's layout). Paint the theme surface over the strip whenever the visible tab's
    // TOP screen isn't one of the immersive pages, so the strip reads as plain
    // white/black there. Drawn above the tab content but below the full-screen overlays
    // (compose/comments/photo below), which handle their own status bar.
    run {
        val activeNavController = when (selectedTab) {
            CorusTab.FEED -> feedNavController
            CorusTab.EXPLORE -> searchNavController
            CorusTab.NOTIFICATIONS -> notificationsNavController
            CorusTab.PROFILE -> profileNavController
            CorusTab.COMPOSE -> feedNavController
        }
        // Gate on any VISIBLE entry (the top one AND any mid-transition one), not just
        // the current top: when you tap back off an immersive page, the top flips to
        // the non-immersive page instantly while the immersive page is still sliding
        // out — reacting to the top alone snapped the white cover on over it (a flash).
        // `visibleEntries` keeps the immersive entry until its exit animation finishes,
        // so the cover only appears once no immersive page is on screen at all.
        val visibleEntries by activeNavController.visibleEntries.collectAsState()
        val immersiveRoutes = buildList {
            add("SongDetailRoute"); add("FilmDetailRoute"); add("ArtistPageRoute")
            add("AlbumPageRoute"); add("DirectorPageRoute")
            // The profile feed + other-profile pages paint their own frosted status
            // strip (no-hero immersive bar) only while the flag is on; otherwise
            // they're plain pages that still want the cover.
            if (viewModel.immersiveArtistHeaderEnabled) {
                add("ProfileFeedRoute")
                add("OtherProfileRoute")
                add("ProfileByUsernameRoute")
                add("PostDetailRoute")
                add("SinglePostCommentsRoute")
                // Tab roots (frosted status strip): feed + own profile.
                add("FeedTabRoute")
                add("ProfileTabRoute")
                // Activity (frosted header overlay).
                add("NotificationsTabRoute")
            }
        }
        val anyImmersiveVisible = visibleEntries.any { entry ->
            val route = entry.destination.route
            route != null && immersiveRoutes.any { route.contains(it) }
        }
        val stripTopPx = currentStatusBarTopPx()
        if (!anyImmersiveVisible && stripTopPx > 0) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(with(LocalDensity.current) { stripTopPx.toDp() })
                    .background(CorusColors.Background),
            )
        }
    }

    // Compose screen as full-screen overlay OVER everything (including bottom bar & mini player).
    // Same stacking as comments: player is z=1, tab bar is z=2 — compose must sit above both
    // so the Set Corus button and trophy celebration aren't clipped by the menu bar (iOS parity).
    AnimatedVisibility(
        visible = showCompose,
        enter = slideInVertically { it },
        exit = slideOutVertically { it },
        modifier = Modifier
            .fillMaxSize()
            .zIndex(3f),
    ) {
        ComposeScreen(
            onDismiss = {
                showCompose = false
                composeMovieMode = false
                viewModel.clearPreSelectedMedia()
                viewModel.checkPostMilestonePaywall()
            },
            movieModeEnabled = preSelectedMovieId != null || composeMovieMode,
            preSelectedTrackId = preSelectedTrackId,
            preSelectedMovieId = preSelectedMovieId,
        )
    }
    // ── Club Offer Sheet ──
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
            CymbalClubOfferSheet(
                source = clubOfferSource,
                onDismiss = { showClubOffer = false },
            )
        }
    }

    // Save cap toast/snackbar host (overlay above content, inside outer Box).
    androidx.compose.material3.SnackbarHost(
        hostState = saveCapSnackbarHost,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 56.dp)
            .align(Alignment.TopCenter),
    )

    // Global link-out loading overlay (parity with iOS MainTabView): a centered
    // spinner while an Apple Music / TIDAL / Deezer URL resolves from ANY surface
    // (feed, song page, mini-player). Cache hits / Spotify never trip it.
    if (isResolvingLinkOut) {
        androidx.compose.material3.Surface(
            modifier = Modifier.fillMaxSize(),
            color = androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.25f),
        ) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                androidx.compose.material3.Surface(
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(18.dp),
                    color = androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.85f),
                ) {
                    androidx.compose.material3.CircularProgressIndicator(
                        color = androidx.compose.ui.graphics.Color.White,
                        modifier = Modifier.padding(28.dp),
                    )
                }
            }
        }
    }

    if (showPlayerQueue) {
        FullPlayerQueueSheet(
            nowPlayingManager = viewModel.nowPlayingManager,
            onDismiss = { showPlayerQueue = false },
        )
    }

    LaunchedEffect(playerSharePost?.id) {
        val post = playerSharePost ?: return@LaunchedEffect
        val send = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(android.content.Intent.EXTRA_TEXT, "https://corus.fm/post/${post.id}")
        }
        runCatching {
            context.startActivity(android.content.Intent.createChooser(send, null))
        }
        playerSharePost = null
    }

    playerLikesPostId?.let { postId ->
        LikesBottomSheet(
            postId = postId,
            onDismiss = { playerLikesPostId = null },
            onNavigateToUser = { userId ->
                playerLikesPostId = null
                playerScope.launch {
                    if (playerExpansion.isExpandedOrExpanding) {
                        playerExpansion.collapse()
                    }
                    navControllers[selectedTab]?.navigate(OtherProfileRoute(userId))
                }
            },
        )
    }

    // Comments sheet hosted at the root so it covers the tab bar (full iOS parity) and
    // sees the real window insets. Navigation routes through the active tab's controller.
    commentPostId?.let { postId ->
        val navController = navControllers[selectedTab]
        // CorusDraggableSheet is an in-window overlay (not a Dialog). The expanding
        // player (z=1) and tab bar (z=2) must sit underneath it, or "Add a comment"
        // from the full player appears to do nothing.
        //
        // Destinations opened from a comment must also collapse the expanding
        // player — otherwise navigation lands under the full-player chrome and
        // song/film attachment taps look inert (iOS dismisses the player too).
        fun navigateFromComments(navigate: () -> Unit) {
            playerScope.launch {
                if (playerExpansion.isExpandedOrExpanding) {
                    playerExpansion.collapse()
                }
                navigate()
            }
        }
        Box(modifier = Modifier.fillMaxSize().zIndex(3f)) {
            CommentsBottomSheet(
                postId = postId,
                // onDismiss is what removes the sheet (commentPostId = null). The nav callbacks
                // below only navigate — CommentsBottomSheet animates the sheet closed first and
                // the close reaching Hidden fires onDismiss, so they must NOT clear it here (that
                // would yank the sheet instantly and skip the animation).
                onDismiss = {
                    commentPostId = null
                    commentSheetAutoFocus = false
                    commentsRefreshSignal += 1
                },
                onNavigateToUser = { userId ->
                    navigateFromComments { navController?.navigate(OtherProfileRoute(userId)) }
                },
                onNavigateToSong = { track ->
                    navigateFromComments { navController?.navigate(track.toSongDetailRoute()) }
                },
                onNavigateToFilm = { movie ->
                    navigateFromComments { navController?.navigate(movie.toFilmDetailRoute()) }
                },
                onNavigateToHashtag = { hashtag ->
                    navigateFromComments { navController?.navigate(HashtagFeedRoute(hashtag)) }
                },
                // Same gating as the DM entity bubbles: with artist pages off the
                // comment attachment card renders but its tap stays inert.
                onNavigateToArtist = if (artistPagesEnabled) {
                    { artist ->
                        navigateFromComments {
                            navController?.navigate(
                                ArtistPageRoute(artist.artistId, artist.artistName, artist.artistImageURL),
                            )
                        }
                    }
                } else null,
                onNavigateToAlbum = if (artistPagesEnabled) {
                    { album ->
                        navigateFromComments {
                            navController?.navigate(
                                AlbumPageRoute(
                                    album.albumId, album.albumTitle, album.albumArtistName,
                                    album.albumCoverURL, album.albumYear?.toIntOrNull(),
                                ),
                            )
                        }
                    }
                } else null,
                onNavigateToDirector = if (artistPagesEnabled) {
                    { director ->
                        navigateFromComments {
                            navController?.navigate(
                                DirectorPageRoute(director.directorId, director.directorName, director.directorImageURL),
                            )
                        }
                    }
                } else null,
                autoFocusInput = commentSheetAutoFocus,
            )
        }
    }

    FullScreenPhotoViewer(
        photo = expandedPhoto,
        onDismiss = { expandedPhoto = null },
    )

    // Windowed toast host (Popup) — above player / tab bar / in-window sheets.
    fm.corus.android.ui.components.ToastHost(
        modifier = Modifier
            .fillMaxSize()
            .zIndex(10f),
    )

    } // end outer Box
}

@Composable
private fun TabContent(visible: Boolean, content: @Composable () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .then(if (!visible) Modifier.offset(x = 9999.dp) else Modifier)
    ) {
        // Tell descendants (notably an immersive header's status-bar control) whether
        // this tab is the one on screen — every tab NavHost stays composed even while
        // parked off-screen, so composition alone can't tell them apart.
        CompositionLocalProvider(LocalContainingTabSelected provides visible) {
            content()
        }
    }
}

private fun NavHostController.popToStart(): Boolean {
    val startId = graph.startDestinationId
    return popBackStack(startId, inclusive = false)
}

/**
 * Combined badge count to display on the Activity tab icon.
 *
 * Matches iOS MainTabView:
 *  - When the user is already on the Activity tab, only unread DMs count (notifications
 *    were just marked read on screen entry, so surfacing them would re-appear a badge
 *    that the user considers dismissed).
 *  - Otherwise, sum of unread notifications + unread DMs.
 */
internal fun notificationTabBadge(
    selectedTab: CorusTab,
    notificationCount: Int,
    unreadMessageCount: Int,
): Int = if (selectedTab == CorusTab.NOTIFICATIONS) {
    unreadMessageCount
} else {
    notificationCount + unreadMessageCount
}

/**
 * Floor for the gesture-navigation strip under the tab bar. 24dp is what AOSP
 * (and Pixel) report for `navigationBars` in gesture mode; several OEMs report a
 * thinner strip, which leaves the tab labels crowding the gesture handle.
 */
private val MinGestureNavPadding = 24.dp

/**
 * Bottom padding for the tab bar, given the system-reported `navigationBars` inset.
 *
 * `navigationBarsPadding()` alone hands the spacing to the OEM, and the reported
 * gesture inset varies per manufacturer, so the same build looks roomy on Pixel and
 * cramped elsewhere. Flooring it normalizes gesture mode without touching anything
 * else:
 *  - 3-button nav reports ~48dp, comfortably above the floor, so it is unchanged.
 *  - A 0 inset means no gesture handle is drawn at all (e.g. Samsung with the
 *    gesture hint hidden). There is nothing to clear, so we add nothing rather
 *    than opening a dead 24dp gap.
 */
internal fun gestureNavBottomPadding(navInset: Dp): Dp =
    if (navInset <= 0.dp) 0.dp else maxOf(navInset, MinGestureNavPadding)

internal fun tabContentBottomPadding(frosted: Boolean, chromeHeight: Dp, scaffoldBottom: Dp): Dp =
    if (frosted) chromeHeight else scaffoldBottom

@Composable
internal fun CorusBottomBar(
    selectedTab: CorusTab,
    notificationTabBadgeCount: Int,
    onTabSelected: (CorusTab) -> Unit,
    onComposeTapped: () -> Unit,
    frosted: Boolean = false,
    // Overridable so tests can drive the nav strip; Robolectric always reports 0.
    navInset: Dp = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding(),
) {
    Column {
        // Top divider line. Hidden while frosted so the glass reads as one
        // continuous surface with content blurring under it (no hard hairline).
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(0.5.dp)
                .background(if (frosted) Color.Transparent else CorusColors.Divider)
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                // Transparent while frosted so the shared Haze layer shows through the
                // whole bar AND the Android nav-bar strip (navigationBarsPadding keeps
                // this background extended down into that strip).
                .background(if (frosted) Color.Transparent else CorusColors.Background)
                .padding(top = CorusSpacing.sm)
                // Same job navigationBarsPadding() did (extend the bar down through
                // the nav strip and consume that inset), but with a floor so the
                // gesture handle gets Pixel-like clearance on every OEM. See
                // [gestureNavBottomPadding].
                .consumeWindowInsets(WindowInsets.navigationBars)
                .padding(bottom = gestureNavBottomPadding(navInset)),
            // Center every slot against the full bar height so the "+" lines up with
            // the icon+label stack of the other tabs (the tab items are the tallest
            // children, so centering doesn't move them — it only drops the "+" to
            // their shared vertical center instead of floating above the labels).
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Each slot gets an equal weight(1f) so the bar is divided into five
            // identical columns. This keeps the center "+" mathematically centered
            // regardless of how wide each label renders (e.g. "Activity"/"Profile"
            // are wider than "Feed"/"Search"). Arrangement.SpaceEvenly was sizing
            // slots to their intrinsic label width, which pushed "+" off-center.
            TabItem(
                icon = if (selectedTab == CorusTab.FEED) CorusTab.FEED.selectedIcon else CorusTab.FEED.unselectedIcon,
                label = stringResource(CorusTab.FEED.labelRes),
                isSelected = selectedTab == CorusTab.FEED,
                onClick = { onTabSelected(CorusTab.FEED) },
                modifier = Modifier.weight(1f),
            )
            TabItem(
                icon = if (selectedTab == CorusTab.EXPLORE) CorusTab.EXPLORE.selectedIcon else CorusTab.EXPLORE.unselectedIcon,
                label = stringResource(CorusTab.EXPLORE.labelRes),
                isSelected = selectedTab == CorusTab.EXPLORE,
                onClick = { onTabSelected(CorusTab.EXPLORE) },
                modifier = Modifier.weight(1f),
            )
            ComposeButton(
                onClick = onComposeTapped,
                modifier = Modifier.weight(1f),
            )
            TabItem(
                icon = if (selectedTab == CorusTab.NOTIFICATIONS) CorusTab.NOTIFICATIONS.selectedIcon else CorusTab.NOTIFICATIONS.unselectedIcon,
                label = stringResource(CorusTab.NOTIFICATIONS.labelRes),
                isSelected = selectedTab == CorusTab.NOTIFICATIONS,
                badgeCount = notificationTabBadgeCount,
                onClick = { onTabSelected(CorusTab.NOTIFICATIONS) },
                modifier = Modifier.weight(1f),
            )
            TabItem(
                icon = if (selectedTab == CorusTab.PROFILE) CorusTab.PROFILE.selectedIcon else CorusTab.PROFILE.unselectedIcon,
                label = stringResource(CorusTab.PROFILE.labelRes),
                isSelected = selectedTab == CorusTab.PROFILE,
                onClick = { onTabSelected(CorusTab.PROFILE) },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun TabItem(
    icon: ImageVector,
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    badgeCount: Int = 0,
) {
    val color = if (isSelected) CorusColors.Accent else CorusColors.Secondary

    Column(
        modifier = modifier
            .defaultMinSize(minHeight = CorusSpacing.touchTarget)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            ),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier.size(30.dp),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                modifier = Modifier.size(24.dp),
                tint = color,
            )
            if (badgeCount > 0) {
                val badgeText = if (badgeCount > 99) "99+" else "$badgeCount"
                val isWide = badgeText.length > 1
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .offset(x = 8.dp, y = (-4).dp)
                        .defaultMinSize(minWidth = 18.dp, minHeight = 18.dp)
                        .background(Color.Red, CircleShape)
                        .border(1.5.dp, Color.White, CircleShape)
                        .padding(horizontal = if (isWide) 5.dp else 0.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = badgeText,
                        color = Color.White,
                        fontSize = 11.sp,
                        lineHeight = 11.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        textAlign = TextAlign.Center,
                        style = LocalTextStyle.current.copy(
                            platformStyle = PlatformTextStyle(includeFontPadding = false),
                        ),
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(CorusSpacing.xxs))

        Text(
            text = label,
            style = CorusFont.tabLabel,
            color = color,
        )
    }
}

// The button is centered on the icon+label stack (CenterVertically in the Row),
// then lifted 2dp. A solid filled disc optically reads low next to the lightweight
// outline icons, so this small nudge balances it against the icon row by eye.
private val ComposeButtonOpticalLift = 2.dp

@Composable
private fun ComposeButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.offset(y = -ComposeButtonOpticalLift),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .shadow(
                    elevation = 4.dp,
                    shape = CircleShape,
                    ambientColor = CorusColors.Accent.copy(alpha = 0.3f),
                    spotColor = CorusColors.Accent.copy(alpha = 0.3f),
                )
                .background(CorusColors.Accent, CircleShape)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onClick,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Rounded.Add,
                contentDescription = stringResource(R.string.tab_cd_compose),
                modifier = Modifier.size(25.dp),
                tint = Color.White,
            )
        }
    }
}
