package fm.corus.android.ui.navigation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
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
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import fm.corus.android.ui.components.MiniPlayerBar
import fm.corus.android.ui.screens.compose.ComposeScreen
import fm.corus.android.ui.screens.compose.ComposeViewModel
import fm.corus.android.ui.screens.subscription.PostLimitPaywallSheet
import fm.corus.android.service.DeepLinkDestination
import fm.corus.android.ui.theme.CorusColors
import fm.corus.android.ui.theme.CorusFont
import fm.corus.android.ui.theme.CorusSpacing
import kotlinx.coroutines.flow.StateFlow

@Composable
fun MainTabScreen(
    viewModel: MainTabViewModel = hiltViewModel(),
    pendingNotificationDestination: StateFlow<DeepLinkDestination?>? = null,
    onNotificationDestinationConsumed: () -> Unit = {},
) {
    var selectedTab by rememberSaveable { mutableStateOf(CorusTab.FEED) }
    var showCompose by rememberSaveable { mutableStateOf(false) }
    var showPostLimitPaywall by remember { mutableStateOf(false) }
    val composeViewModel: ComposeViewModel = hiltViewModel()
    val showMilestonePaywall by viewModel.showMilestonePaywall.collectAsState()

    // Observe pre-selected media IDs for compose-with-preselection flow.
    val preSelectedTrackId by viewModel.preSelectedTrackId.collectAsState()
    val preSelectedMovieId by viewModel.preSelectedMovieId.collectAsState()

    // When a pre-selected media ID becomes non-null, open compose overlay.
    LaunchedEffect(preSelectedTrackId) {
        if (preSelectedTrackId != null) {
            if (viewModel.subscriptionRepository.canPost) {
                composeViewModel.reset()
                showCompose = true
            } else showPostLimitPaywall = true
        }
    }
    LaunchedEffect(preSelectedMovieId) {
        if (preSelectedMovieId != null) {
            if (viewModel.subscriptionRepository.canPost) {
                composeViewModel.reset()
                showCompose = true
            } else showPostLimitPaywall = true
        }
    }

    // Each tab gets its own NavController to preserve back stack
    val feedNavController = rememberNavController()
    val exploreNavController = rememberNavController()
    val notificationsNavController = rememberNavController()
    val profileNavController = rememberNavController()

    // Map for tab-based navigation from overlay sheets
    val navControllers = remember(feedNavController, exploreNavController, notificationsNavController, profileNavController) {
        mapOf(
            CorusTab.FEED to feedNavController,
            CorusTab.EXPLORE to exploreNavController,
            CorusTab.NOTIFICATIONS to notificationsNavController,
            CorusTab.PROFILE to profileNavController,
        )
    }

    // Scroll-to-top triggers: increment to signal a root screen should scroll up
    val feedScrollToTop = remember { mutableIntStateOf(0) }
    val exploreScrollToTop = remember { mutableIntStateOf(0) }
    val notificationsScrollToTop = remember { mutableIntStateOf(0) }
    val profileScrollToTop = remember { mutableIntStateOf(0) }

    // Handle notification tap navigation
    val notificationDestination = pendingNotificationDestination?.collectAsState()?.value
    LaunchedEffect(notificationDestination) {
        if (notificationDestination == null) return@LaunchedEffect
        val navController = feedNavController
        selectedTab = CorusTab.FEED
        when (notificationDestination) {
            is DeepLinkDestination.Post -> navController.navigate(PostDetailRoute(notificationDestination.postId))
            is DeepLinkDestination.PostComment -> navController.navigate(
                SinglePostCommentsRoute(notificationDestination.postId, notificationDestination.commentId)
            )
            is DeepLinkDestination.Profile -> navController.navigate(OtherProfileRoute(notificationDestination.userId))
            is DeepLinkDestination.Thread -> navController.navigate(MessageThreadRoute(
                threadId = notificationDestination.threadId,
                otherUserId = notificationDestination.otherUserId,
            ))
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
                            trackId != null -> navController.navigate(SongDetailRoute(
                                trackId = trackId,
                                albumArtURL = state.albumArtURL,
                                songName = state.trackName,
                                artistName = state.artistName,
                                spotifyURI = state.spotifyURI,
                                spotifyWebURL = state.spotifyWebURL,
                            ))
                        }
                    },
                )
                CorusBottomBar(
                selectedTab = selectedTab,
                onTabSelected = { tab ->
                    if (tab == CorusTab.COMPOSE) {
                        if (viewModel.subscriptionRepository.canPost) {
                            composeViewModel.reset()
                            showCompose = true
                        } else showPostLimitPaywall = true
                    } else {
                        if (tab == selectedTab) {
                            // Re-tap: pop to root if deep, scroll to top if already at root
                            val navController = navControllers[tab]!!
                            val popped = navController.popToStart()
                            if (!popped) {
                                // Already at root — scroll to top
                                when (tab) {
                                    CorusTab.FEED -> feedScrollToTop.intValue++
                                    CorusTab.EXPLORE -> exploreScrollToTop.intValue++
                                    CorusTab.NOTIFICATIONS -> notificationsScrollToTop.intValue++
                                    CorusTab.PROFILE -> profileScrollToTop.intValue++
                                    else -> {}
                                }
                            }
                        }
                        selectedTab = tab
                    }
                },
                onComposeTapped = {
                    if (viewModel.subscriptionRepository.canPost) {
                        composeViewModel.reset()
                        showCompose = true
                    } else showPostLimitPaywall = true
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
                ExploreNavGraph(navController = exploreNavController, mainTabViewModel = viewModel, scrollToTopTrigger = exploreScrollToTop.intValue)
            }
            TabContent(visible = selectedTab == CorusTab.NOTIFICATIONS) {
                NotificationsNavGraph(navController = notificationsNavController, mainTabViewModel = viewModel, scrollToTopTrigger = notificationsScrollToTop.intValue)
            }
            TabContent(visible = selectedTab == CorusTab.PROFILE) {
                ProfileNavGraph(navController = profileNavController, mainTabViewModel = viewModel, scrollToTopTrigger = profileScrollToTop.intValue)
            }

            // Toast overlay (inside padded Box so it renders above the bottom bar)
            fm.corus.android.ui.components.ToastHost()
        }

        // Post limit paywall (shown when compose is gated)
        if (showPostLimitPaywall) {
            PostLimitPaywallSheet(
                onDismiss = { showPostLimitPaywall = false },
                onNavigateToClub = {
                    showPostLimitPaywall = false
                    navControllers[selectedTab]?.navigate(CymbalClubOfferRoute)
                },
            )
        }

        // Milestone paywall (shown after successful post milestones)
        if (showMilestonePaywall) {
            PostLimitPaywallSheet(
                onDismiss = { viewModel.dismissMilestonePaywall() },
                onNavigateToClub = {
                    viewModel.dismissMilestonePaywall()
                    navControllers[selectedTab]?.navigate(CymbalClubOfferRoute)
                },
            )
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
                viewModel.clearPreSelectedMedia()
                viewModel.checkPostMilestonePaywall()
            },
            movieModeEnabled = preSelectedMovieId != null,
            preSelectedTrackId = preSelectedTrackId,
            preSelectedMovieId = preSelectedMovieId,
        )
    }
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

@Composable
private fun CorusBottomBar(
    selectedTab: CorusTab,
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
                .background(Color.White)
                .padding(top = CorusSpacing.sm)
                .navigationBarsPadding(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.Top,
        ) {
            TabItem(
                icon = if (selectedTab == CorusTab.FEED) CorusTab.FEED.selectedIcon else CorusTab.FEED.unselectedIcon,
                label = CorusTab.FEED.label,
                isSelected = selectedTab == CorusTab.FEED,
                onClick = { onTabSelected(CorusTab.FEED) },
            )
            TabItem(
                icon = if (selectedTab == CorusTab.EXPLORE) CorusTab.EXPLORE.selectedIcon else CorusTab.EXPLORE.unselectedIcon,
                label = CorusTab.EXPLORE.label,
                isSelected = selectedTab == CorusTab.EXPLORE,
                onClick = { onTabSelected(CorusTab.EXPLORE) },
            )
            ComposeButton(onClick = onComposeTapped)
            TabItem(
                icon = if (selectedTab == CorusTab.NOTIFICATIONS) CorusTab.NOTIFICATIONS.selectedIcon else CorusTab.NOTIFICATIONS.unselectedIcon,
                label = CorusTab.NOTIFICATIONS.label,
                isSelected = selectedTab == CorusTab.NOTIFICATIONS,
                onClick = { onTabSelected(CorusTab.NOTIFICATIONS) },
            )
            TabItem(
                icon = if (selectedTab == CorusTab.PROFILE) CorusTab.PROFILE.selectedIcon else CorusTab.PROFILE.unselectedIcon,
                label = CorusTab.PROFILE.label,
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
                contentDescription = "Compose",
                modifier = Modifier.size(25.dp),
                tint = Color.White,
            )
        }
    }
}
