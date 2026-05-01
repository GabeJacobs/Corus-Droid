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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import fm.corus.android.R
import fm.corus.android.data.model.TrackSource
import fm.corus.android.ui.components.MiniPlayerBar
import fm.corus.android.ui.screens.compose.ComposeScreen
import fm.corus.android.ui.screens.compose.ComposeViewModel
import fm.corus.android.service.DeepLinkDestination
import fm.corus.android.ui.screens.subscription.CymbalClubOfferSheet
import fm.corus.android.ui.screens.subscription.PaywallSource
import fm.corus.android.ui.theme.CorusColors
import fm.corus.android.ui.theme.CorusFont
import fm.corus.android.ui.theme.CorusSpacing
import fm.corus.android.ui.theme.CorusSystemBars
import fm.corus.android.ui.util.PushNotificationPermission
import kotlinx.coroutines.flow.StateFlow

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

    // Fallback push-permission prompt for users who signed up before the
    // onboarding ask shipped. Matches iOS MainTabView.requestNotificationPermissionIfNeeded.
    val context = LocalContext.current
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
                    viewModel.logPaywallShown("save_cap")
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
                        viewModel.logPaywallShown("save_cap")
                        clubOfferSource = PaywallSource.SAVE_LIMIT
                        showClubOffer = true
                    }
                }
            }
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

    // Handle notification tap navigation
    val notificationDestination = pendingNotificationDestination?.collectAsState()?.value
    LaunchedEffect(notificationDestination) {
        if (notificationDestination == null) return@LaunchedEffect
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
        }
        onNotificationDestinationConsumed()
    }

    Box(modifier = Modifier.fillMaxSize()) {
    Scaffold(
        bottomBar = {
            Column {
                MiniPlayerBar(
                    nowPlayingManager = viewModel.nowPlayingManager,
                    onTrackTap = {
                        val state = viewModel.nowPlayingManager.state.value
                        val navController = navControllers[selectedTab] ?: return@MiniPlayerBar
                        val postId = state.sourcePostId
                        val trackId = state.trackId
                        when {
                            postId != null -> navController.navigate(PostDetailRoute(postId))
                            trackId != null -> {
                                // Round-trip SoundCloud fields. Without these, opening
                                // SongDetail from the mini-player while a SoundCloud
                                // track is playing would default the route's source to
                                // null/spotify, causing the detail screen to render
                                // Apple Music / Spotify CTAs and "Post Song" to write
                                // a Spotify-shaped post — feed renders the wrong badge
                                // and playback fails. SC trackIds are formatted
                                // `sc:<numeric>` so we can derive the SC id directly.
                                val isSoundCloud = state.source == TrackSource.SOUNDCLOUD
                                val soundcloudId = if (isSoundCloud) trackId.removePrefix("sc:") else null
                                navController.navigate(SongDetailRoute(
                                    trackId = trackId,
                                    albumArtURL = state.albumArtURL,
                                    songName = state.trackName,
                                    artistName = state.artistName,
                                    spotifyURI = state.spotifyURI,
                                    spotifyWebURL = state.spotifyWebURL,
                                    source = state.source.raw,
                                    soundcloudId = soundcloudId,
                                    soundcloudPermalinkUrl = state.soundcloudPermalinkUrl,
                                ))
                            }
                        }
                    },
                )
                CorusBottomBar(
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
                        } else { clubOfferSource = PaywallSource.POST_LIMIT; showClubOffer = true }
                    } else {
                        if (tab == selectedTab) {
                            // Re-tap: pop to root if deep, scroll to top if already at root
                            val navController = navControllers[tab]!!
                            val popped = navController.popToStart()
                            if (!popped) {
                                // Already at root — scroll to top
                                when (tab) {
                                    CorusTab.FEED -> feedScrollToTop.intValue++
                                    CorusTab.EXPLORE -> searchScrollToTop.intValue++
                                    CorusTab.NOTIFICATIONS -> notificationsScrollToTop.intValue++
                                    CorusTab.PROFILE -> profileScrollToTop.intValue++
                                    else -> {}
                                }
                            }
                        }
                        if (tab == CorusTab.NOTIFICATIONS && selectedTab != CorusTab.NOTIFICATIONS) {
                            viewModel.onActivityTabEntered()
                        }
                        selectedTab = tab
                    }
                },
                onComposeTapped = {
                    if (viewModel.subscriptionRepository.canPost) {
                        composeViewModel.reset()
                        showCompose = true
                    } else { clubOfferSource = PaywallSource.POST_LIMIT; showClubOffer = true }
                },
            )
            }
        },
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .consumeWindowInsets(padding)
        ) {
            // Keep all tab NavHosts alive but only show the selected one.
            // This preserves scroll position and back stack per tab.
            TabContent(visible = selectedTab == CorusTab.FEED) {
                FeedNavGraph(navController = feedNavController, mainTabViewModel = viewModel, scrollToTopTrigger = feedScrollToTop.intValue)
            }
            TabContent(visible = selectedTab == CorusTab.EXPLORE) {
                SearchNavGraph(navController = searchNavController, mainTabViewModel = viewModel, scrollToTopTrigger = searchScrollToTop.intValue)
            }
            TabContent(visible = selectedTab == CorusTab.NOTIFICATIONS) {
                NotificationsNavGraph(navController = notificationsNavController, mainTabViewModel = viewModel, scrollToTopTrigger = notificationsScrollToTop.intValue)
            }
            TabContent(visible = selectedTab == CorusTab.PROFILE) {
                ProfileNavGraph(
                    navController = profileNavController,
                    mainTabViewModel = viewModel,
                    scrollToTopTrigger = profileScrollToTop.intValue,
                    onOpenCompose = { mediaType ->
                        if (viewModel.subscriptionRepository.canPost) {
                            composeViewModel.reset()
                            composeMovieMode = mediaType == "movie"
                            showCompose = true
                        } else { clubOfferSource = PaywallSource.POST_LIMIT; showClubOffer = true }
                    },
                )
            }

            // Toast overlay (inside padded Box so it renders above the bottom bar)
            fm.corus.android.ui.components.ToastHost()
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

    // Compose screen as full-screen overlay OVER everything (including bottom bar & mini player)
    AnimatedVisibility(
        visible = showCompose,
        enter = slideInVertically { it },
        exit = slideOutVertically { it },
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

    } // end outer Box
}

@Composable
private fun TabContent(visible: Boolean, content: @Composable () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .then(if (!visible) Modifier.offset(x = 9999.dp) else Modifier)
    ) {
        content()
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

@Composable
private fun CorusBottomBar(
    selectedTab: CorusTab,
    notificationTabBadgeCount: Int,
    onTabSelected: (CorusTab) -> Unit,
    onComposeTapped: () -> Unit,
) {
    Column {
        // Top divider line
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(0.5.dp)
                .background(CorusColors.Divider)
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(CorusColors.Background)
                .padding(top = CorusSpacing.sm)
                .navigationBarsPadding(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.Top,
        ) {
            TabItem(
                icon = if (selectedTab == CorusTab.FEED) CorusTab.FEED.selectedIcon else CorusTab.FEED.unselectedIcon,
                label = stringResource(CorusTab.FEED.labelRes),
                isSelected = selectedTab == CorusTab.FEED,
                onClick = { onTabSelected(CorusTab.FEED) },
            )
            TabItem(
                icon = if (selectedTab == CorusTab.EXPLORE) CorusTab.EXPLORE.selectedIcon else CorusTab.EXPLORE.unselectedIcon,
                label = stringResource(CorusTab.EXPLORE.labelRes),
                isSelected = selectedTab == CorusTab.EXPLORE,
                onClick = { onTabSelected(CorusTab.EXPLORE) },
            )
            ComposeButton(onClick = onComposeTapped)
            TabItem(
                icon = if (selectedTab == CorusTab.NOTIFICATIONS) CorusTab.NOTIFICATIONS.selectedIcon else CorusTab.NOTIFICATIONS.unselectedIcon,
                label = stringResource(CorusTab.NOTIFICATIONS.labelRes),
                isSelected = selectedTab == CorusTab.NOTIFICATIONS,
                badgeCount = notificationTabBadgeCount,
                onClick = { onTabSelected(CorusTab.NOTIFICATIONS) },
            )
            TabItem(
                icon = if (selectedTab == CorusTab.PROFILE) CorusTab.PROFILE.selectedIcon else CorusTab.PROFILE.unselectedIcon,
                label = stringResource(CorusTab.PROFILE.labelRes),
                isSelected = selectedTab == CorusTab.PROFILE,
                onClick = { onTabSelected(CorusTab.PROFILE) },
            )
        }
    }
}

@Composable
private fun TabItem(
    icon: ImageVector,
    label: String,
    isSelected: Boolean,
    badgeCount: Int = 0,
    onClick: () -> Unit,
) {
    val color = if (isSelected) CorusColors.Accent else CorusColors.Secondary

    Column(
        modifier = Modifier
            .height(CorusSpacing.touchTarget)
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

@Composable
private fun ComposeButton(onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .height(CorusSpacing.touchTarget)
            .offset(y = (-4).dp),
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
