package fm.corus.android.ui.screens.feed

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import coil3.compose.AsyncImage
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.People
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.outlined.TrendingUp
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.hilt.navigation.compose.hiltViewModel
import fm.corus.android.R
import fm.corus.android.data.model.CymbalPost
import fm.corus.android.data.model.CymbalTrack
import fm.corus.android.data.model.CymbalUser
import fm.corus.android.data.model.FeedDecade
import fm.corus.android.data.model.FeedFilter
import fm.corus.android.data.model.MediaType
import fm.corus.android.domain.FeedModeOrder
import fm.corus.android.domain.HapticManager
import fm.corus.android.domain.PostPlaybackHighlight
import fm.corus.android.ui.LocalHapticManager
import fm.corus.android.ui.components.FrostedStatusStrip
import fm.corus.android.ui.components.PopularUsersInfiniteGrid
import fm.corus.android.ui.components.rememberImmersiveHeaderState
import fm.corus.android.ui.components.PostCard
import fm.corus.android.ui.components.PostMenuSheets
import fm.corus.android.ui.components.SkeletonPostCard
import fm.corus.android.ui.components.ToastManager
import fm.corus.android.ui.components.VennDiagramIcon
import fm.corus.android.ui.theme.CorusColors
import fm.corus.android.ui.theme.CorusFont
import fm.corus.android.ui.theme.CorusSpacing
import fm.corus.android.ui.theme.CorusSystemBars

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FeedScreen(
    viewModel: FeedViewModel = hiltViewModel(),
    scrollToTopTrigger: Int = 0,
    /**
     * True when the user is currently looking at the feed root (i.e. the
     * feed tab is selected and no detail screen is on top — Navigation
     * Compose only composes this screen when at the start destination, so
     * upstream just forwards the selected-tab check). Gates auto-scroll
     * to now-playing.
     */
    isAtRoot: Boolean = true,
    onNavigateToPost: (String) -> Unit = {},
    onNavigateToUser: (CymbalUser) -> Unit = {},
    onNavigateToUserById: (String) -> Unit = {},
    onNavigateToUserByUsername: (String) -> Unit = {},
    onNavigateToComments: (String) -> Unit = {},
    onNavigateToLikes: (String) -> Unit = {},
    onNavigateToHashtag: (String) -> Unit = {},
    onNavigateToSong: (CymbalTrack) -> Unit = {},
    onNavigateToFilm: (String) -> Unit = {},
    onRepost: (CymbalPost) -> Unit = {},
    onShowReposters: (String) -> Unit = {},
    onNavigateToCompose: () -> Unit = {},
    /** Artist/director pages (artist_pages_enabled) — null while the flag is
     *  off, which keeps post-card artist/director names as plain text. */
    onNavigateToArtist: ((fm.corus.android.ui.navigation.ArtistPageRoute) -> Unit)? = null,
    onNavigateToDirector: ((fm.corus.android.ui.navigation.DirectorPageRoute) -> Unit)? = null,
    /** Album page (artist_pages_enabled) — null while the flag is off, which
     *  hides the "…" menu's "Go to Album" row. */
    onNavigateToAlbum: ((fm.corus.android.ui.navigation.AlbumPageRoute) -> Unit)? = null,
) {
    val posts by viewModel.filteredPosts.collectAsState()
    val allPosts by viewModel.posts.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val isGeneratingPlaylist by viewModel.nowPlayingManager.isGeneratingPlaylist.collectAsState()
    val playlistError by viewModel.nowPlayingManager.playlistError.collectAsState()
    val musicService by viewModel.musicServicePreference.current.collectAsState()
    var showPlaylistAlert by remember { mutableStateOf(false) }
    val hasConfirmedFeedPlaylist by viewModel.hasConfirmedFeedPlaylist.collectAsState()
    var showFirstTimePlaylistDialog by remember { mutableStateOf(false) }

    LaunchedEffect(playlistError) {
        if (playlistError != null) {
            ToastManager.show(playlistError!!)
            viewModel.nowPlayingManager.clearPlaylistError()
        }
    }
    val hasLoaded by viewModel.hasLoaded.collectAsState()
    val isRefreshing by viewModel.isRefreshing.collectAsState()
    val hasMore by viewModel.hasMore.collectAsState()
    val isConnected by viewModel.isConnected.collectAsState()
    val lastLoadFailed by viewModel.lastLoadFailed.collectAsState()
    val engagementStates by viewModel.engagementStates.collectAsState()
    val currentUserProfile by viewModel.currentUserProfile.collectAsState()
    val feedMediaFilter by viewModel.feedMediaFilter.collectAsState()
    val feedFilter by viewModel.feedFilter.collectAsState()
    val followedBotIds by viewModel.followedBotIds.collectAsState()
    val nowPlayingState by viewModel.nowPlayingManager.state.collectAsState()
    val hasTappedAlbumArt by viewModel.hasTappedAlbumArt.collectAsState()
    val isNewAccount by viewModel.isNewAccount.collectAsState()
    val loadingTrackId by viewModel.nowPlayingManager.loadingTrackId.collectAsState()
    val loadingSourcePostId by viewModel.nowPlayingManager.loadingSourcePostId.collectAsState()
    val feedFollowsNowPlaying by viewModel.feedFollowsNowPlaying.collectAsState()
    val feedMode by viewModel.feedMode.collectAsState()
    val feedDecade: Int? = viewModel.appliedFeedDecade.collectAsState().value
    val showDecadeFilter = viewModel.isDecadeFilterVisible(feedMode)
    val followingUserIds by viewModel.followingUserIds.collectAsState()
    val followingLoaded by viewModel.followingLoaded.collectAsState()
    val forYouLoadFailed by viewModel.forYouLoadFailed.collectAsState()
    val trendingFeedEnabled = viewModel.remoteConfig.trendingFeedEnabled
    val favoritesEnabled = viewModel.remoteConfig.favoritesEnabled
    val tasteMatchesAvailable =
        viewModel.remoteConfig.tasteMatchesEnabled || viewModel.remoteConfig.tasteMatchesTester
    val feedModeOrder = viewModel.remoteConfig.feedModeOrder
    val feedSwitchHintVisible by viewModel.feedSwitchHintVisible.collectAsState()
    // Evaluate the one-time feed-switch hint once the feed has loaded and the
    // switcher is actually shown (nothing to discover otherwise).
    val modeSwitcherEnabled = trendingFeedEnabled || favoritesEnabled || tasteMatchesAvailable
    LaunchedEffect(hasLoaded, modeSwitcherEnabled) {
        if (hasLoaded && modeSwitcherEnabled) viewModel.evaluateFeedSwitchHint()
    }
    val tasteMatchesGate by viewModel.tasteMatchesGate.collectAsState()
    val tasteMatchesSeeding by viewModel.tasteMatchesSeeding.collectAsState()
    val tasteMatchesSeedArt by viewModel.tasteMatchesSeedArt.collectAsState()
    val tasteMatchesSeedCount by viewModel.tasteMatchesSeedCount.collectAsState()
    val context = LocalContext.current
    var filterMenuExpanded by remember { mutableStateOf(false) }
    var sharePost by remember { mutableStateOf<CymbalPost?>(null) }
    var menuPost by remember { mutableStateOf<CymbalPost?>(null) }
    var editCaptionPost by remember { mutableStateOf<CymbalPost?>(null) }
    var showDeleteConfirm by remember { mutableStateOf<CymbalPost?>(null) }
    val backCoverStates = remember { mutableMapOf<String, fm.corus.android.ui.components.BackCoverFlipState>() }
    val scope = rememberCoroutineScope()
    // Resolve-on-tap state for the tappable artist name (subtitle). Shared HUD +
    // miss toast so tapping the name behaves like the "…" menu's Go to Artist row.
    var isResolvingSubtitle by remember { mutableStateOf(false) }
    val subtitleArtistNotFound = stringResource(R.string.song_detail_artist_not_found)
    fun backCoverStateFor(postId: String) =
        backCoverStates.getOrPut(postId) { fm.corus.android.ui.components.BackCoverFlipState() }
    var filmInfoPost by remember { mutableStateOf<CymbalPost?>(null) }
    var showClubOffer by remember { mutableStateOf(false) }
    var clubOfferSource by remember { mutableStateOf(fm.corus.android.ui.screens.subscription.PaywallSource.PLAYLIST_LIMIT) }

    val paywallRequested by viewModel.nowPlayingManager.paywallRequested.collectAsState()
    LaunchedEffect(paywallRequested) {
        if (paywallRequested) {
            clubOfferSource = fm.corus.android.ui.screens.subscription.PaywallSource.PLAYLIST_LIMIT
            showClubOffer = true
            viewModel.nowPlayingManager.clearPaywallRequested()
        }
    }

    val newReleaseFilterPaywall by viewModel.newReleaseFilterPaywall.collectAsState()
    LaunchedEffect(newReleaseFilterPaywall) {
        if (newReleaseFilterPaywall != null) {
            clubOfferSource = newReleaseFilterPaywall!!
            showClubOffer = true
            viewModel.clearNewReleaseFilterPaywall()
        }
    }

    // Taste Matches paywall when a non-member taps the menu → open the Club
    // sheet. (The rarer server gated:"paywall" backstop renders an in-feed state
    // with a "Learn more" button instead — see the gate branches below, mirroring
    // iOS tasteMatchesPaywallState.)
    val tasteMatchesPaywall by viewModel.tasteMatchesPaywall.collectAsState()
    LaunchedEffect(tasteMatchesPaywall) {
        if (tasteMatchesPaywall != null) {
            clubOfferSource = tasteMatchesPaywall!!
            showClubOffer = true
            viewModel.clearTasteMatchesPaywall()
        }
    }

    LaunchedEffect(Unit) {
        if (allPosts.isEmpty()) {
            viewModel.loadFeed()
        }
    }

    // Pagination handled per-item via onAppear (see itemsIndexed below)

    // Hoist list state above the when block so scroll position survives
    // navigation (rememberSaveable key stays stable across recompositions)
    val listState = rememberLazyListState()
    var lastScrollTrigger by rememberSaveable { mutableIntStateOf(0) }
    LaunchedEffect(scrollToTopTrigger) {
        if (scrollToTopTrigger > lastScrollTrigger) {
            listState.animateScrollToItem(0)
            lastScrollTrigger = scrollToTopTrigger
        }
    }

    // Mini-player tap shortcut: register a scroll handler so a tap on the
    // mini-player while the user is at feed root scrolls to the post here
    // instead of pushing a single-post detail page. Mirrors iOS
    // .feedScrollToPost. The handler is a stable closure that reads the
    // latest posts/listState via rememberUpdatedState; we only register
    // while `isAtRoot` (the FEED tab is selected and we're at the start
    // destination) and use reference equality to avoid clobbering another
    // screen's handler on cleanup.
    val currentPostsForRouter by rememberUpdatedState(posts)
    val routerScope = scope
    val feedScrollHandler = remember<(String) -> Boolean>(listState) {
        handler@ { postId ->
            val idx = currentPostsForRouter.indexOfFirst { it.id == postId }
            if (idx < 0) return@handler false
            // +1 because index 0 in the LazyColumn is the FeedHeader
            routerScope.launch { listState.animateScrollToItem(index = idx + 1) }
            true
        }
    }
    DisposableEffect(isAtRoot, feedScrollHandler) {
        if (isAtRoot) {
            viewModel.feedScrollRouter.handler = feedScrollHandler
        }
        onDispose {
            if (viewModel.feedScrollRouter.handler === feedScrollHandler) {
                viewModel.feedScrollRouter.handler = null
            }
        }
    }

    // Auto-scroll the feed to the now-playing post on song changes. Mirrors
    // iOS FeedView.onChange(of: nowPlaying.currentSourcePostId). Skip the
    // first composition (matches iOS .onChange semantics — only react to
    // changes, not the initial value).
    var hasInitializedFollowScroll by remember { mutableStateOf(false) }
    LaunchedEffect(nowPlayingState.sourcePostId) {
        val newPostId = nowPlayingState.sourcePostId
        if (!hasInitializedFollowScroll) {
            hasInitializedFollowScroll = true
            return@LaunchedEffect
        }
        if (newPostId == null) return@LaunchedEffect
        if (!feedFollowsNowPlaying) return@LaunchedEffect
        if (!isAtRoot) return@LaunchedEffect
        // Don't yank the feed out from under a finger that's actively
        // scrolling (or a fling that's still decelerating). Checked before
        // we kick off our own animateScrollToItem, so this only reflects
        // user-driven motion.
        if (listState.isScrollInProgress) return@LaunchedEffect
        // Skip when the user just tapped this card to play it — they're
        // already looking at it. The marker is a one-shot, so consume it.
        val tapMarker = viewModel.nowPlayingManager.lastUserInitiatedSourcePostId
        if (tapMarker == newPostId) {
            viewModel.nowPlayingManager.lastUserInitiatedSourcePostId = null
            return@LaunchedEffect
        }
        val index = posts.indexOfFirst { it.id == newPostId }
        if (index < 0) return@LaunchedEffect
        // +1 because index 0 in the LazyColumn is the FeedHeader (filter row)
        listState.animateScrollToItem(index = index + 1)
    }

    val immersive = viewModel.remoteConfig.immersiveArtistHeaderEnabled
    val frost = rememberImmersiveHeaderState(immersive)

    val header: @Composable () -> Unit = {
        // Clear the frosted status strip so the "corus" switcher sits just below it.
        if (immersive) Spacer(Modifier.height(frost.statusBarPadding))
        FeedHeader(
            showPlaylistButton = posts.isNotEmpty() && feedMediaFilter != MediaType.MOVIE,
            isGeneratingPlaylist = isGeneratingPlaylist,
            feedFilter = feedFilter,
            filterMenuExpanded = filterMenuExpanded,
            onFilterMenuExpandedChange = { filterMenuExpanded = it },
            onSetFilter = { viewModel.setFeedFilter(it) },
            showDecadeFilter = showDecadeFilter,
            feedDecade = feedDecade,
            onSetDecade = { viewModel.setFeedDecade(it) },
            onGeneratePlaylist = {
                val hasSoundCloud = posts.any { it.isTrack && it.track.source == fm.corus.android.data.model.TrackSource.SOUNDCLOUD }
                when {
                    // Apple Music / Deezer have no native playlist path on Android,
                    // so they always produce a Spotify playlist and show the Spotify
                    // warning every time (never the explainer).
                    fm.corus.android.domain.usesSpotifyFallback(musicService) ->
                        showPlaylistAlert = true
                    // TIDAL / Spotify export natively and leave Corus — the first
                    // such tap gets a one-time explainer. After that, Spotify still
                    // shows the SoundCloud-skip notice when applicable.
                    hasConfirmedFeedPlaylist -> {
                        if (fm.corus.android.domain.shouldShowSpotifyPlaylistAlert(musicService, hasSoundCloud)) {
                            showPlaylistAlert = true
                        } else {
                            viewModel.generateFeedPlaylist()
                        }
                    }
                    else -> showFirstTimePlaylistDialog = true
                }
            },
            trendingFeedEnabled = trendingFeedEnabled,
            favoritesEnabled = favoritesEnabled,
            tasteMatchesAvailable = tasteMatchesAvailable,
            feedModeOrder = feedModeOrder,
            feedMode = feedMode,
            onSetFeedMode = { viewModel.setFeedMode(it) },
            onSwitcherOpened = { viewModel.onFeedSwitcherOpened() },
            showFeedSwitchHint = feedSwitchHintVisible,
            onDismissFeedSwitchHint = { viewModel.dismissFeedSwitchHint() },
        )
    }

    val haptics = LocalHapticManager.current
    val pullState = rememberPullToRefreshState()
    Box(modifier = frost.scaffoldModifier) {
    PullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = {
            // Mirrors iOS FeedView.refreshable haptic.
            haptics.impact(HapticManager.ImpactStyle.LIGHT)
            viewModel.loadFeed(refresh = true)
        },
        state = pullState,
        modifier = Modifier.fillMaxSize().then(frost.hazeSourceModifier()),
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
        when {
            // Loading skeleton state — show until first load completes, while a
            // refresh (e.g. filter change) is in flight with no posts yet, or
            // during the Taste Matches seed hand-off (post 3 → loading the feed).
            posts.isEmpty() && (!hasLoaded || isLoading || isRefreshing || tasteMatchesSeeding) -> {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    item { header() }
                    items(3) {
                        SkeletonPostCard()
                        if (it < 2) {
                            HorizontalDivider(color = CorusColors.Divider)
                        }
                    }
                }
            }

            // Premium Taste Matches cold-start: a Club member / tester who hasn't
            // posted enough yet. Album-art seed slots invite the next post.
            posts.isEmpty() && hasLoaded && !isLoading && !isRefreshing &&
                feedMode == "tasteMatches" &&
                tasteMatchesGate is FeedViewModel.TasteMatchesGate.NeedMorePosts -> {
                val gate = tasteMatchesGate as FeedViewModel.TasteMatchesGate.NeedMorePosts
                Column(modifier = Modifier.fillMaxSize()) {
                    header()
                    // Center the cold-start in the space below the header (mirrors
                    // iOS, which brackets the content with flexible Spacers). The
                    // inner verticalScroll keeps it reachable on short screens and
                    // preserves pull-to-refresh; the Box centers it when it fits.
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(
                            modifier = Modifier.verticalScroll(rememberScrollState()),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            TasteMatchesColdStart(
                                postCount = gate.postCount,
                                threshold = gate.threshold,
                                seedArt = tasteMatchesSeedArt,
                                seedCount = tasteMatchesSeedCount,
                                onShown = { viewModel.analyticsService.logTasteMatchesColdstartShown() },
                                onPost = {
                                    viewModel.analyticsService.logTasteMatchesColdstartPostTapped()
                                    onNavigateToCompose()
                                },
                            )
                        }
                    }
                }
            }

            // Premium Taste Matches paywall backstop: a server gated:"paywall"
            // (tester / entitlement desync). Renders in-feed with a "Learn more"
            // CTA that opens the Club sheet (mirrors iOS tasteMatchesPaywallState).
            posts.isEmpty() && hasLoaded && !isLoading && !isRefreshing &&
                feedMode == "tasteMatches" &&
                tasteMatchesGate is FeedViewModel.TasteMatchesGate.Paywall -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState()),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    header()
                    TasteMatchesPaywallState(
                        onLearnMore = {
                            clubOfferSource = fm.corus.android.ui.screens.subscription.PaywallSource.TASTE_MATCHES
                            showClubOffer = true
                        },
                    )
                }
            }

            // Taste Matches served-empty / unavailable: a Club member / tester
            // whose cohort has nothing right now (gated:"unavailable"). A neutral
            // branded empty, NOT the generic invite grid (mirrors iOS
            // tasteMatchesNeutralEmptyState). Shown regardless of filter — an
            // unavailable cohort has no matches, so naming a filter would wrongly
            // imply you have taste matches.
            posts.isEmpty() && hasLoaded && !isLoading && !isRefreshing &&
                feedMode == "tasteMatches" &&
                tasteMatchesGate is FeedViewModel.TasteMatchesGate.Unavailable -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState()),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    header()
                    TasteMatchesNeutralEmpty()
                }
            }

            // Taste Matches "no matches yet": posted enough but no shared
            // artist/director with anyone yet (gated:"noMatchesYet"). Actionable
            // empty — post more (mirrors iOS tasteMatchesNoMatchesYetState). Shown
            // regardless of filter: the server returns this gate on a filtered
            // request only when you have NO taste matches at all, so it's the base
            // "no matches" state, not a filter-specific empty. (When you DO have
            // matches but none in this media type, the server returns an ungated
            // empty page, which the filter-aware branch below handles.)
            posts.isEmpty() && hasLoaded && !isLoading && !isRefreshing &&
                feedMode == "tasteMatches" &&
                tasteMatchesGate is FeedViewModel.TasteMatchesGate.NoMatchesYet -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState()),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    header()
                    TasteMatchesNoMatchesYet(onPost = onNavigateToCompose)
                }
            }

            // Offline / load-failed empty state. Two distinct cases share this
            // panel but must NOT share copy: when the device is genuinely offline
            // (!isConnected) we tell the user to check their connection; when the
            // device is online but the load failed (lastLoadFailed) the fault is
            // ours, so we say so instead of wrongly blaming their wifi. Mirrors
            // iOS FeedView.offlineEmptyState.
            posts.isEmpty() && hasLoaded && !isLoading && !isRefreshing && (lastLoadFailed || !isConnected) -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState()),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    header()
                    Spacer(modifier = Modifier.height(60.dp))
                    Icon(
                        imageVector = if (isConnected) Icons.Filled.CloudOff else Icons.Filled.WifiOff,
                        contentDescription = null,
                        tint = CorusColors.Tertiary,
                        modifier = Modifier.size(36.dp),
                    )
                    Spacer(modifier = Modifier.height(CorusSpacing.md))
                    Text(
                        text = stringResource(
                            if (isConnected) R.string.feed_error_title else R.string.feed_offline_title,
                        ),
                        style = CorusFont.bodyMedium,
                        color = CorusColors.Secondary,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(modifier = Modifier.height(CorusSpacing.xs))
                    Text(
                        text = stringResource(
                            if (isConnected) R.string.feed_error_subtitle else R.string.feed_offline_subtitle,
                        ),
                        style = CorusFont.caption,
                        color = CorusColors.Tertiary,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = CorusSpacing.xl),
                    )
                    Spacer(modifier = Modifier.height(CorusSpacing.lg))
                    Button(
                        onClick = {
                            haptics.impact(HapticManager.ImpactStyle.LIGHT)
                            viewModel.loadFeed(refresh = true)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = CorusColors.Accent),
                        shape = RoundedCornerShape(CorusSpacing.pillCornerRadius),
                    ) {
                        Text(
                            text = stringResource(R.string.feed_offline_retry),
                            style = CorusFont.button,
                            color = CorusColors.Background,
                        )
                    }
                    Spacer(modifier = Modifier.height(CorusSpacing.xxl))
                }
            }

            // Taste Matches with a filter active but no matching posts. The user
            // HAS taste matches (or the served feed is simply empty for this
            // narrowing) — they just filtered to a content type nobody's shared,
            // so name the filter and offer to clear it instead of falling through
            // to the generic invite grid. Mirrors the favorites filter-aware
            // empty below (and iOS tasteMatchesFilteredEmptyState).
            posts.isEmpty() && hasLoaded && !isLoading && !isRefreshing &&
                feedMode == "tasteMatches" && feedFilter != FeedFilter.ALL -> {
                val icon = when (feedFilter) {
                    FeedFilter.MUSIC -> Icons.Filled.MusicNote
                    FeedFilter.FILM -> Icons.Filled.Movie
                    FeedFilter.MUSIC_NEW_RELEASES, FeedFilter.FILM_NEW_RELEASES -> Icons.Filled.LocalFireDepartment
                    FeedFilter.ALL -> Icons.Filled.Movie
                }
                val titleRes = when (feedFilter) {
                    FeedFilter.MUSIC -> R.string.feed_empty_taste_matches_music_title
                    FeedFilter.FILM -> R.string.feed_empty_taste_matches_film_title
                    FeedFilter.MUSIC_NEW_RELEASES -> R.string.feed_empty_taste_matches_new_music_title
                    FeedFilter.FILM_NEW_RELEASES -> R.string.feed_empty_taste_matches_new_film_title
                    FeedFilter.ALL -> R.string.feed_empty_taste_matches_film_title
                }
                val subtitleRes = when (feedFilter) {
                    FeedFilter.MUSIC -> R.string.feed_empty_taste_matches_music_subtitle
                    FeedFilter.FILM -> R.string.feed_empty_taste_matches_film_subtitle
                    FeedFilter.MUSIC_NEW_RELEASES -> R.string.feed_empty_taste_matches_new_music_subtitle
                    FeedFilter.FILM_NEW_RELEASES -> R.string.feed_empty_taste_matches_new_film_subtitle
                    FeedFilter.ALL -> R.string.feed_empty_taste_matches_film_subtitle
                }
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState()),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    header()
                    Spacer(modifier = Modifier.height(60.dp))
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = CorusColors.Tertiary,
                        modifier = Modifier.size(36.dp),
                    )
                    Spacer(modifier = Modifier.height(CorusSpacing.md))
                    Text(
                        text = stringResource(titleRes),
                        style = CorusFont.songTitle,
                        color = CorusColors.Secondary,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(modifier = Modifier.height(CorusSpacing.xs))
                    Text(
                        text = stringResource(subtitleRes),
                        style = CorusFont.body,
                        color = CorusColors.Tertiary,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = CorusSpacing.xl),
                    )
                    Spacer(modifier = Modifier.height(CorusSpacing.lg))
                    Button(
                        onClick = {
                            haptics.impact(HapticManager.ImpactStyle.LIGHT)
                            viewModel.setFeedFilter(FeedFilter.ALL)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = CorusColors.Accent),
                        shape = RoundedCornerShape(CorusSpacing.pillCornerRadius),
                    ) {
                        Text(
                            text = stringResource(R.string.feed_empty_clear_filter),
                            style = CorusFont.button,
                            color = CorusColors.Background,
                        )
                    }
                    Spacer(modifier = Modifier.height(CorusSpacing.xxl))
                }
            }

            // Empty state — only after a load settles with no posts.
            // Favorites mode is handled by its own branch below (which is
            // filter-aware), so exclude it here to avoid the generic
            // "from people you follow" copy leaking into a favorites view.
            posts.isEmpty() && hasLoaded && !isLoading && !isRefreshing && feedFilter.newReleasesOnly && feedMode != "favorites" -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState()),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    header()
                    Spacer(modifier = Modifier.height(60.dp))
                    Icon(
                        imageVector = Icons.Filled.LocalFireDepartment,
                        contentDescription = null,
                        tint = CorusColors.Tertiary,
                        modifier = Modifier.size(36.dp),
                    )
                    Spacer(modifier = Modifier.height(CorusSpacing.md))
                    Text(
                        text = stringResource(R.string.feed_empty_new_releases_title),
                        style = CorusFont.body,
                        color = CorusColors.Secondary,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(modifier = Modifier.height(CorusSpacing.xs))
                    Text(
                        text = stringResource(R.string.feed_empty_new_releases_subtitle),
                        style = CorusFont.caption,
                        color = CorusColors.Tertiary,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = CorusSpacing.xl),
                    )
                    Spacer(modifier = Modifier.height(CorusSpacing.lg))
                    Button(
                        onClick = { viewModel.setFeedFilter(FeedFilter.ALL) },
                        colors = ButtonDefaults.buttonColors(containerColor = CorusColors.Accent),
                        shape = RoundedCornerShape(CorusSpacing.pillCornerRadius),
                    ) {
                        Text(
                            text = stringResource(R.string.feed_empty_new_releases_show_all),
                            style = CorusFont.button,
                            color = CorusColors.Background,
                        )
                    }
                    Spacer(modifier = Modifier.height(CorusSpacing.xxl))
                }
            }

            // Favorites mode with no posts. When a filter is active the user
            // DOES have favorites — they just haven't posted anything matching
            // the current film/music/new-releases narrowing — so name the
            // filter instead of saying "No favorites yet", and offer to clear
            // the filter rather than leave the feed. Only the unfiltered case
            // is a true "no favorites" state. Mirrors iOS favoritesFilteredEmptyState.
            posts.isEmpty() && hasLoaded && !isLoading && !isRefreshing && feedMode == "favorites" -> {
                val filtered = feedFilter != FeedFilter.ALL
                val icon = when (feedFilter) {
                    FeedFilter.MUSIC -> Icons.Filled.MusicNote
                    FeedFilter.FILM -> Icons.Filled.Movie
                    FeedFilter.MUSIC_NEW_RELEASES, FeedFilter.FILM_NEW_RELEASES -> Icons.Filled.LocalFireDepartment
                    FeedFilter.ALL -> Icons.Filled.Star
                }
                val titleRes = when (feedFilter) {
                    FeedFilter.MUSIC -> R.string.feed_empty_favorites_music_title
                    FeedFilter.FILM -> R.string.feed_empty_favorites_film_title
                    FeedFilter.MUSIC_NEW_RELEASES -> R.string.feed_empty_favorites_new_music_title
                    FeedFilter.FILM_NEW_RELEASES -> R.string.feed_empty_favorites_new_film_title
                    FeedFilter.ALL -> R.string.feed_empty_favorites_title
                }
                val subtitleRes = when (feedFilter) {
                    FeedFilter.MUSIC -> R.string.feed_empty_favorites_music_subtitle
                    FeedFilter.FILM -> R.string.feed_empty_favorites_film_subtitle
                    FeedFilter.MUSIC_NEW_RELEASES -> R.string.feed_empty_favorites_new_music_subtitle
                    FeedFilter.FILM_NEW_RELEASES -> R.string.feed_empty_favorites_new_film_subtitle
                    FeedFilter.ALL -> R.string.feed_empty_favorites_subtitle
                }
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState()),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    header()
                    Spacer(modifier = Modifier.height(60.dp))
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = CorusColors.Tertiary,
                        modifier = Modifier.size(36.dp),
                    )
                    Spacer(modifier = Modifier.height(CorusSpacing.md))
                    Text(
                        text = stringResource(titleRes),
                        style = CorusFont.songTitle,
                        color = CorusColors.Secondary,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(modifier = Modifier.height(CorusSpacing.xs))
                    Text(
                        text = stringResource(subtitleRes),
                        style = CorusFont.body,
                        color = CorusColors.Tertiary,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = CorusSpacing.xl),
                    )
                    Spacer(modifier = Modifier.height(CorusSpacing.lg))
                    Button(
                        onClick = {
                            haptics.impact(HapticManager.ImpactStyle.LIGHT)
                            if (filtered) {
                                viewModel.setFeedFilter(FeedFilter.ALL)
                            } else {
                                viewModel.setFeedMode("following")
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = CorusColors.Accent),
                        shape = RoundedCornerShape(CorusSpacing.pillCornerRadius),
                    ) {
                        Text(
                            text = stringResource(
                                if (filtered) R.string.feed_empty_clear_filter
                                else R.string.feed_empty_favorites_button,
                            ),
                            style = CorusFont.button,
                            color = CorusColors.Background,
                        )
                    }
                    Spacer(modifier = Modifier.height(CorusSpacing.xxl))
                }
            }

            posts.isEmpty() && hasLoaded && !isLoading && !isRefreshing && feedDecade != null -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState()),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    header()
                    FeedDecadeEmptyState(
                        decade = feedDecade,
                        onShowAllDecades = {
                            haptics.impact(HapticManager.ImpactStyle.LIGHT)
                            viewModel.setFeedDecade(null)
                        },
                    )
                }
            }

            posts.isEmpty() && hasLoaded && !isLoading && !isRefreshing -> {
                val inviteShareText = stringResource(R.string.feed_empty_invite_share_text)
                val inviteChooser = stringResource(R.string.feed_empty_invite_chooser)
                Column(modifier = Modifier.fillMaxSize()) {
                    header()
                    PopularUsersInfiniteGrid(
                        excludeIds = emptySet(),
                        followedIds = followedBotIds,
                        onUserTap = { user -> onNavigateToUser(user) },
                        onFollowTap = { user -> viewModel.toggleBotFollow(user) },
                        modifier = Modifier.weight(1f),
                        topContent = {
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                Spacer(modifier = Modifier.height(40.dp))
                                Text(
                                    text = stringResource(R.string.feed_empty_invite_title),
                                    style = CorusFont.songTitleLarge,
                                    color = CorusColors.Text,
                                    textAlign = TextAlign.Center,
                                )
                                Spacer(modifier = Modifier.height(CorusSpacing.sm))
                                Text(
                                    text = stringResource(R.string.feed_empty_invite_subtitle),
                                    style = CorusFont.body,
                                    color = CorusColors.Secondary,
                                    textAlign = TextAlign.Center,
                                )
                                Spacer(modifier = Modifier.height(CorusSpacing.lg))
                                Button(
                                    onClick = {
                                        val sendIntent = Intent().apply {
                                            action = Intent.ACTION_SEND
                                            putExtra(Intent.EXTRA_TEXT, inviteShareText)
                                            type = "text/plain"
                                        }
                                        context.startActivity(Intent.createChooser(sendIntent, inviteChooser))
                                    },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = CorusColors.Accent,
                                    ),
                                    shape = RoundedCornerShape(CorusSpacing.pillCornerRadius),
                                ) {
                                    Text(
                                        text = stringResource(R.string.feed_empty_invite_button),
                                        style = CorusFont.button,
                                        color = CorusColors.Background,
                                    )
                                }
                                Spacer(modifier = Modifier.height(CorusSpacing.xl))
                            }
                        },
                    )
                }
            }

            // Posts list
            else -> {
                LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                    item { header() }
                    itemsIndexed(
                        posts,
                        key = { _, post -> post.id },
                        contentType = { _, _ -> "post_card" },
                    ) { index, post ->
                        // Pagination trigger (mirrors iOS FeedView `.onAppear`):
                        // request the next page when a row within the last 3 of
                        // the rendered list is composed. Replaces the previous
                        // viewport `derivedStateOf` heuristic, which failed to
                        // re-arm when a client-filtered page (e.g. New Releases,
                        // whose `isNewRelease` re-filter thins each page) didn't
                        // fill the screen, leaving the feed stuck mid-list while
                        // the server still had pages. `loadFeed()` self-guards
                        // against concurrent loads, so the near-end rows collapse
                        // into one fetch; each appended row composes and
                        // re-triggers until the screen fills or `hasMore` is false.
                        if (hasMore && index >= posts.size - 3) {
                            LaunchedEffect(post.id) { viewModel.loadFeed() }
                        }
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
                            isPreviewLoading = PostPlaybackHighlight.shouldHighlight(
                                activeTrackId = loadingTrackId,
                                activeSourcePostId = loadingSourcePostId,
                                playbackActive = true,
                                postTrackId = post.track.id,
                                postId = post.id,
                            ),
                            isPreviewPlaying = PostPlaybackHighlight.shouldHighlight(
                                activeTrackId = nowPlayingState.trackId,
                                activeSourcePostId = nowPlayingState.sourcePostId,
                                playbackActive = nowPlayingState.isPlaying,
                                postTrackId = post.track.id,
                                postId = post.id,
                            ),
                            onLikeTap = { viewModel.toggleLike(post.id) },
                            onSaveTap = { viewModel.toggleSave(post.id) },
                            onUserTap = { onNavigateToUser(post.user) },
                            onPostTap = { onNavigateToPost(post.id) },
                            onPreviewTap = {
                                viewModel.playPreview(post)
                            },
                            onTrailerTap = {
                                post.trailerURL?.takeIf { it.isNotBlank() }?.let { url ->
                                    viewModel.nowPlayingManager.pause()
                                    try {
                                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                                    } catch (_: Exception) { }
                                }
                            },
                            onCommentTap = { onNavigateToComments(post.id) },
                            onLikesTap = { onNavigateToLikes(post.id) },
                            onLikerTap = { liker -> onNavigateToUser(liker) },
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
                                    val permalink = post.track.soundcloudPermalinkUrl
                                    if (!permalink.isNullOrBlank()) {
                                        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(permalink))) }
                                    }
                                } else if (post.track.source == fm.corus.android.data.model.TrackSource.APPLEMUSIC) {
                                    // Apple-only tracks always open in Apple
                                    // Music, regardless of the viewer's
                                    // preferred service. URL is derived from
                                    // the resolved appleMusicId or the
                                    // `am:`-prefixed trackId.
                                    viewModel.analyticsService.logMusicServiceLinkTapped(
                                        fm.corus.android.data.model.MusicService.APPLE_MUSIC.value, post.track.id
                                    )
                                    val url = post.track.appleMusicURL
                                    if (!url.isNullOrBlank()) {
                                        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
                                    }
                                } else if (musicService == fm.corus.android.data.model.MusicService.SPOTIFY) {
                                    viewModel.analyticsService.logSpotifyLinkTapped(post.track.id)
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
                                    // Apple Music / TIDAL / Deezer preference on a
                                    // Spotify-source post: resolve that service's
                                    // catalog URL via the backend (network, cached),
                                    // then open. Mirrors iOS openInMusicService.
                                    viewModel.analyticsService.logMusicServiceLinkTapped(musicService.value, post.track.id)
                                    scope.launch {
                                        val url = viewModel.resolveServiceLinkUrl(post.track)
                                        if (!url.isNullOrBlank()) {
                                            runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
                                        }
                                    }
                                }
                            },
                            musicService = musicService,
                            onMentionTap = { username -> onNavigateToUserByUsername(username) },
                            onRepostedFromUserTap = { userId, username ->
                                if (userId != null) onNavigateToUserById(userId)
                                else onNavigateToUserByUsername(username)
                            },
                            onCommentUserTap = { user -> onNavigateToUserById(user.id) },
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
                            showsTapHint = index == 0 && isNewAccount && !hasTappedAlbumArt,
                            onAlbumArtTap = { viewModel.markAlbumArtTapped() },
                            isTrendingFeed = feedMode == "trending",
                            isFollowingAuthor = followingUserIds.contains(post.user.id),
                            isFollowingKnown = followingLoaded,
                            onFollowAuthor = { viewModel.followAuthor(post.user.id) },
                            onSubtitleTap = fm.corus.android.ui.components.postSubtitleTap(
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

                    if (isLoading && posts.isNotEmpty()) {
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

                    // Taste Matches reached the end: this feed is finite (only
                    // people you share an artist/director with), so nudge to post
                    // more instead of the generic invite footer.
                    if (feedMode == "tasteMatches" && !hasMore && !isLoading && posts.isNotEmpty()) {
                        item {
                            TasteMatchesEndOfFeed(onPost = onNavigateToCompose)
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
        artistPagesEnabled = onNavigateToArtist != null,
        onNavigateToArtist = onNavigateToArtist,
        onNavigateToAlbum = onNavigateToAlbum,
        onNavigateToDirector = onNavigateToDirector,
    )

    // ── Film Info Sheet ──
    filmInfoPost?.let { post ->
        FilmInfoSheet(
            post = post,
            onDismiss = { filmInfoPost = null },
            fetchMovieDetails = { movieId -> viewModel.fetchMovieDetails(movieId) },
        )
    }

    // ── Spotify Playlist Confirmation ──
    // Surfaces when (a) the user prefers Apple Music (this is a Spotify-only
    // feature) or (b) the feed contains SoundCloud tracks that will be
    // skipped from the Spotify playlist.
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
                    viewModel.generateFeedPlaylist()
                }) { androidx.compose.material3.Text("Generate Spotify Playlist") }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = { showPlaylistAlert = false }) {
                    androidx.compose.material3.Text("Cancel")
                }
            },
        )
    }

    // First-time explainer: the playlist button leaves Corus to open the user's
    // music app, which surprises new users. Shown once, before the first
    // generation; confirming records the flag and proceeds to the normal flow.
    if (showFirstTimePlaylistDialog) {
        val destination = if (musicService == fm.corus.android.data.model.MusicService.TIDAL) "TIDAL" else "Spotify"
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showFirstTimePlaylistDialog = false },
            title = { androidx.compose.material3.Text("Generate a playlist?") },
            text = {
                androidx.compose.material3.Text(
                    "Corus will make a playlist from the songs in your feed and open it in $destination."
                )
            },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = {
                    showFirstTimePlaylistDialog = false
                    viewModel.markFeedPlaylistConfirmed()
                    // Reached only by TIDAL / Spotify (native). Spotify still warns
                    // about skipped SoundCloud tracks; TIDAL generates directly.
                    val hasSoundCloud = posts.any { it.isTrack && it.track.source == fm.corus.android.data.model.TrackSource.SOUNDCLOUD }
                    if (fm.corus.android.domain.shouldShowSpotifyPlaylistAlert(musicService, hasSoundCloud)) {
                        showPlaylistAlert = true
                    } else {
                        viewModel.generateFeedPlaylist()
                    }
                }) { androidx.compose.material3.Text("Export Playlist") }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = { showFirstTimePlaylistDialog = false }) {
                    androidx.compose.material3.Text("Cancel")
                }
            },
        )
    }

    // ── Club Offer Paywall ──
    if (showClubOffer) {
        val clubSheetState = androidx.compose.material3.rememberModalBottomSheetState(skipPartiallyExpanded = true)
        androidx.compose.material3.ModalBottomSheet(
            onDismissRequest = { showClubOffer = false },
            sheetState = clubSheetState,
            containerColor = fm.corus.android.ui.theme.CorusColors.Background,
            dragHandle = { androidx.compose.material3.BottomSheetDefaults.DragHandle() },
        ) {
            CorusSystemBars()
            fm.corus.android.ui.screens.subscription.CymbalClubOfferSheet(
                source = clubOfferSource,
                onDismiss = { showClubOffer = false },
                onPurchaseSuccess = {
                    // Complete the intent that opened this paywall: someone who
                    // tapped Taste Matches and then paid expects to land in that
                    // feed, not back where they were. Other sources keep the
                    // default behavior (stay put).
                    if (clubOfferSource == fm.corus.android.ui.screens.subscription.PaywallSource.TASTE_MATCHES) {
                        viewModel.onTasteMatchesUnlocked()
                    }
                },
            )
        }
    }
}

/**
 * Divider — text — divider header used in the empty feed bot sections.
 * Matches the iOS "or follow some curated music bots" style.
 */
@Composable
private fun DividerSectionHeader(text: String) {
    Row(
        modifier = Modifier.padding(horizontal = CorusSpacing.xxl),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        HorizontalDivider(modifier = Modifier.weight(1f), color = CorusColors.Divider)
        Text(
            text = text,
            style = CorusFont.caption,
            color = CorusColors.Tertiary,
            modifier = Modifier.padding(horizontal = CorusSpacing.sm),
        )
        HorizontalDivider(modifier = Modifier.weight(1f), color = CorusColors.Divider)
    }
}

/**
 * "corus ▾" — wordmark + chevron that opens a small menu to switch feed modes
 * (Taste Matches / Trending / Following / Favorites). Only the modes whose RC
 * gate is on are shown; when none are enabled the caller renders the plain
 * wordmark instead (so flag-off users see no chevron).
 */
@Composable
private fun FeedTitleWithModeMenu(
    feedMode: String,
    trendingFeedEnabled: Boolean,
    favoritesEnabled: Boolean,
    tasteMatchesAvailable: Boolean,
    feedModeOrder: List<String>,
    onSetFeedMode: (String) -> Unit,
    onSwitcherOpened: () -> Unit = {},
    showHint: Boolean = false,
    onDismissHint: () -> Unit = {},
) {
    var expanded by remember { mutableStateOf(false) }
    // Feed-switch hint geometry: capture the following-icon's on-screen center so
    // the coachmark's arrow points at the circle while the bubble body stays
    // centered on screen.
    val density = LocalDensity.current
    val configuration = LocalConfiguration.current
    var hintCircleCenterXPx by remember { mutableFloatStateOf(0f) }
    val hintScreenWidthPx = with(density) { configuration.screenWidthDp.dp.toPx() }
    val hintArrowOffset = with(density) { (hintCircleCenterXPx - hintScreenWidthPx / 2f).toDp() }
    Box {
        Row(
            modifier = Modifier.clickable {
                // Opening the switcher is the discovery signal — retires the hint
                // and logs feed_switcher_opened (even when the flag is off).
                onSwitcherOpened()
                expanded = true
            },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.feed_app_title),
                style = CorusFont.appTitle,
                color = CorusColors.Text,
            )
            // Small accent circle with the active mode's icon, so the user
            // always knows which feed they're viewing. Filled icon (white on
            // accent); the dropdown uses the outlined variants.
            Spacer(modifier = Modifier.width(6.dp))
            Box(
                modifier = Modifier
                    .size(22.dp)
                    .clip(CircleShape)
                    .background(CorusColors.Accent)
                    .onGloballyPositioned {
                        hintCircleCenterXPx = it.positionInWindow().x + it.size.width / 2f
                    },
                contentAlignment = Alignment.Center,
            ) {
                // Taste Matches uses the custom two-circle Venn (matching the
                // post action row and the Search header); other modes use their
                // Material icon.
                if (feedMode == "tasteMatches") {
                    VennDiagramIcon(size = 15.dp, color = Color.White, shadedIntersection = true)
                } else {
                    Icon(
                        imageVector = feedModeIcon(feedMode),
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(13.dp),
                    )
                }
            }
            Spacer(modifier = Modifier.width(4.dp))
            Icon(
                imageVector = Icons.Filled.ArrowDropDown,
                contentDescription = null,
                tint = CorusColors.Text.copy(alpha = 0.7f),
            )
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            val activeCheckmark: @Composable () -> Unit = {
                Icon(
                    imageVector = Icons.Filled.Check,
                    contentDescription = null,
                    tint = CorusColors.Accent,
                )
            }
            val leadingIcon: @Composable (String) -> Unit = { mode ->
                if (mode == "tasteMatches") {
                    VennDiagramIcon(size = 24.dp, color = CorusColors.Secondary, shadedIntersection = true)
                } else {
                    Icon(
                        imageVector = feedModeIcon(mode, filled = false),
                        contentDescription = null,
                        tint = CorusColors.Secondary,
                    )
                }
            }
            // Order comes from the `feed_mode_order` Remote Config string (see
            // FeedModeOrder); each row still renders only when its availability
            // gate is satisfied. Following has no gate, so it always shows.
            feedModeOrder.forEach { mode ->
                val available = when (mode) {
                    FeedModeOrder.TASTE_MATCHES -> tasteMatchesAvailable
                    FeedModeOrder.TRENDING -> trendingFeedEnabled
                    FeedModeOrder.FOLLOWING -> true
                    FeedModeOrder.FAVORITES -> favoritesEnabled
                    else -> false
                }
                if (!available) return@forEach
                val label = when (mode) {
                    FeedModeOrder.TASTE_MATCHES -> "Taste Matches"
                    FeedModeOrder.TRENDING -> "Trending"
                    FeedModeOrder.FOLLOWING -> "Following"
                    FeedModeOrder.FAVORITES -> "Favorites"
                    else -> return@forEach
                }
                DropdownMenuItem(
                    text = { Text(label) },
                    leadingIcon = { leadingIcon(mode) },
                    trailingIcon = if (feedMode == mode) activeCheckmark else null,
                    onClick = {
                        onSetFeedMode(mode)
                        expanded = false
                    },
                )
            }
        }

        // One-time discovery coachmark, anchored just below the logo (this Box)
        // and centered on it. A Popup so it isn't clipped by the LazyColumn and
        // tracks the logo as the feed scrolls.
        if (showHint) {
            FeedSwitchHintPopup(onDismiss = onDismissHint, arrowOffset = hintArrowOffset)
        }
    }
}

@Composable
private fun FeedSwitchHintPopup(onDismiss: () -> Unit, arrowOffset: Dp) {
    val positionProvider = remember {
        object : PopupPositionProvider {
            override fun calculatePosition(
                anchorBounds: IntRect,
                windowSize: IntSize,
                layoutDirection: LayoutDirection,
                popupContentSize: IntSize,
            ): IntOffset {
                // Center the bubble body horizontally on screen (not on the
                // anchor); the arrow inside the bubble is shifted to point at the
                // following-icon. Y sits just below the switcher row.
                val x = (windowSize.width - popupContentSize.width) / 2
                val y = anchorBounds.bottom
                return IntOffset(x, y)
            }
        }
    }
    Popup(
        popupPositionProvider = positionProvider,
        onDismissRequest = onDismiss,
        properties = PopupProperties(focusable = false),
    ) {
        FeedSwitchHintBubble(onDismiss = onDismiss, arrowOffset = arrowOffset)
    }
}

/**
 * The one-time discovery coachmark bubble that sits under the Corus logo (the
 * feed-mode switcher), teaching users the logo is tappable to switch feeds. The
 * whole bubble is the dismiss target — there's no separate close control.
 * Mirrors the web coachmark: accent fill, centered title + subtitle, an up-arrow
 * pointing at the logo, soft fade-in.
 */
@Composable
private fun FeedSwitchHintBubble(onDismiss: () -> Unit, arrowOffset: Dp = 0.dp) {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible = true }
    val alpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(durationMillis = 200),
        label = "feedSwitchHintAlpha",
    )
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .graphicsLayer { this.alpha = alpha }
            .padding(top = 2.dp)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ) { onDismiss() },
    ) {
        Box(
            modifier = Modifier
                .offset(x = arrowOffset)
                .size(width = 16.dp, height = 8.dp)
                .background(CorusColors.Accent, FeedSwitchHintArrowShape),
        )
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .clip(RoundedCornerShape(16.dp))
                .background(CorusColors.Accent)
                .padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            Text(
                text = stringResource(R.string.feed_switch_hint_title),
                style = CorusFont.body.copy(fontSize = 15.sp, fontWeight = FontWeight.SemiBold),
                color = Color.White,
                textAlign = TextAlign.Center,
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = stringResource(R.string.feed_switch_hint_subtitle),
                style = CorusFont.body.copy(fontSize = 13.sp),
                color = Color.White.copy(alpha = 0.85f),
                textAlign = TextAlign.Center,
            )
        }
    }
}

/** Small upward-pointing triangle for the hint bubble's tip. */
private val FeedSwitchHintArrowShape = GenericShape { size, _ ->
    moveTo(size.width / 2f, 0f)
    lineTo(0f, size.height)
    lineTo(size.width, size.height)
    close()
}

/**
 * Icon representing a feed mode (mirrors iOS / Web): Trending → trend line,
 * Following → people, Favorites → filled star. Taste Matches is special-cased
 * to the custom two-circle [VennDiagramIcon] at the call sites. Used for the
 * header accent pill and the mode-switcher menu rows.
 */
private fun feedModeIcon(mode: String, filled: Boolean = true): ImageVector = when (mode) {
    "trending" -> if (filled) Icons.Filled.TrendingUp else Icons.Outlined.TrendingUp
    "tasteMatches" -> if (filled) Icons.Filled.AutoAwesome else Icons.Outlined.AutoAwesome
    "favorites" -> if (filled) Icons.Filled.Star else Icons.Outlined.Star
    else -> if (filled) Icons.Filled.People else Icons.Outlined.People
}

/**
 * Cold-start screen for the premium Taste Matches feed: album-art seed slots
 * show progress (each filled slot is the cover of a post you've made — songs
 * AND films), the slot you're on pulses to invite the next post, and the
 * subtitle explains the payoff. Mirrors iOS / web. Posting fills the next slot
 * optimistically; the third hands off to the loading feed (handled in the VM).
 */
@Composable
private fun TasteMatchesColdStart(
    postCount: Int,
    threshold: Int,
    seedArt: List<String>,
    seedCount: Int,
    onShown: () -> Unit,
    onPost: () -> Unit,
) {
    // Fire once when the cold-start first appears (funnel: selection → shown).
    LaunchedEffect(Unit) { onShown() }
    val base = postCount.coerceIn(0, threshold)
    val filled = (base + seedCount).coerceIn(base, threshold)

    // Medium haptic each time a slot fills (skip first composition) — mirrors
    // iOS HapticManager.impact(.medium) on the seed-post handoff.
    val haptics = LocalHapticManager.current
    var prevFilled by remember { mutableIntStateOf(filled) }
    LaunchedEffect(filled) {
        if (filled > prevFilled) haptics.impact(HapticManager.ImpactStyle.MEDIUM)
        prevFilled = filled
    }

    // Active slot pulses BOTH opacity (0.5↔1.0) and scale (0.9↔1.08), 1.1s
    // ease-in-out, autoreversing — iOS parity (the old build only pulsed alpha).
    val infinite = rememberInfiniteTransition(label = "seedPulse")
    val pulseAlpha by infinite.animateFloat(
        initialValue = 0.5f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1100, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "seedPulseAlpha",
    )
    val pulseScale by infinite.animateFloat(
        initialValue = 0.9f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(1100, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "seedPulseScale",
    )
    val slotShape = RoundedCornerShape(16.dp)

    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        for (i in 0 until threshold) {
            val art = seedArt.getOrNull(i)
            val isFilled = i < filled
            val isActive = i == filled
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .clip(slotShape)
                    .then(
                        when {
                            isFilled && art != null -> Modifier
                            isFilled -> Modifier.background(CorusColors.Accent)
                            isActive -> Modifier
                                .background(CorusColors.Accent.copy(alpha = 0.10f))
                                .dashedBorder(CorusColors.Accent, 16.dp)
                            else -> Modifier.dashedBorder(CorusColors.Tertiary.copy(alpha = 0.45f), 16.dp)
                        }
                    )
                    // Open slots (active / empty) shortcut to the composer.
                    .then(if (!isFilled) Modifier.clickable { onPost() } else Modifier),
                contentAlignment = Alignment.Center,
            ) {
                when {
                    isFilled && art != null -> {
                        // Spring entrance: scale 0.7→1.0 + fade in. Mirrors iOS
                        // .spring(response: 0.45, dampingFraction: 0.7).
                        var shown by remember(art) { mutableStateOf(false) }
                        val enter by animateFloatAsState(
                            targetValue = if (shown) 1f else 0f,
                            animationSpec = spring(dampingRatio = 0.7f, stiffness = Spring.StiffnessLow),
                            label = "seedArtEnter",
                        )
                        LaunchedEffect(art) { shown = true }
                        AsyncImage(
                            model = art,
                            contentDescription = null,
                            modifier = Modifier
                                .fillMaxSize()
                                .graphicsLayer {
                                    val s = 0.7f + 0.3f * enter
                                    scaleX = s
                                    scaleY = s
                                    alpha = enter
                                },
                            contentScale = ContentScale.Crop,
                        )
                    }
                    isActive -> VennDiagramIcon(
                        size = 28.dp,
                        color = CorusColors.Accent.copy(alpha = pulseAlpha),
                        shadedIntersection = true,
                        modifier = Modifier
                            .graphicsLayer {
                                scaleX = pulseScale
                                scaleY = pulseScale
                            },
                    )
                    !isFilled -> Icon(
                        imageVector = Icons.Filled.Add,
                        contentDescription = null,
                        tint = CorusColors.Tertiary,
                        modifier = Modifier.size(24.dp),
                    )
                }
            }
        }
    }
    Spacer(modifier = Modifier.height(CorusSpacing.md))
    // Animated count roll mirrors iOS .contentTransition(.numericText()).
    AnimatedContent(
        targetState = filled,
        transitionSpec = {
            (fadeIn(tween(300)) + slideInVertically(tween(300)) { it / 2 }) togetherWith
                (fadeOut(tween(300)) + slideOutVertically(tween(300)) { -it / 2 })
        },
        label = "seedProgress",
    ) { value ->
        Text(
            text = stringResource(R.string.feed_taste_matches_coldstart_progress, threshold - value),
            style = CorusFont.caption,
            color = CorusColors.Tertiary,
        )
    }
    Spacer(modifier = Modifier.height(CorusSpacing.xxl))
    // Eyebrow naming the feature (reuses the already-translated label).
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        VennDiagramIcon(
            size = 15.dp,
            color = CorusColors.Accent,
            shadedIntersection = true,
        )
        Text(
            text = stringResource(R.string.search_section_taste_matches),
            style = CorusFont.sectionHeader,
            color = CorusColors.Accent,
        )
    }
    Spacer(modifier = Modifier.height(CorusSpacing.xs))
    Text(
        text = stringResource(R.string.feed_taste_matches_coldstart_title),
        style = CorusFont.songTitleLarge,
        color = CorusColors.Text,
        textAlign = TextAlign.Center,
        modifier = Modifier.padding(horizontal = CorusSpacing.xxxl),
    )
    Spacer(modifier = Modifier.height(CorusSpacing.sm))
    Text(
        text = stringResource(R.string.feed_taste_matches_coldstart_body),
        style = CorusFont.body,
        color = CorusColors.Secondary,
        textAlign = TextAlign.Center,
        modifier = Modifier.padding(horizontal = CorusSpacing.xxxl),
    )
    Spacer(modifier = Modifier.height(CorusSpacing.xl))
    Button(
        onClick = onPost,
        colors = ButtonDefaults.buttonColors(containerColor = CorusColors.Accent),
        shape = RoundedCornerShape(CorusSpacing.pillCornerRadius),
    ) {
        Text(
            text = stringResource(R.string.feed_taste_matches_coldstart_cta),
            style = CorusFont.button,
            color = Color.White,
        )
    }
}

/**
 * End-of-feed footer for Taste Matches: this feed is finite (only people you
 * share an artist/director with), so reaching the bottom is a natural nudge to
 * post more, which grows your matches. Replaces the generic invite footer here.
 */
@Composable
private fun TasteMatchesEndOfFeed(onPost: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = CorusSpacing.xl, vertical = CorusSpacing.xxxl),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(R.string.feed_taste_matches_end_more),
            style = CorusFont.bodyMedium,
            color = CorusColors.Secondary,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(CorusSpacing.lg))
        Button(
            onClick = onPost,
            colors = ButtonDefaults.buttonColors(containerColor = CorusColors.Accent),
            shape = RoundedCornerShape(CorusSpacing.pillCornerRadius),
        ) {
            Text(
                text = stringResource(R.string.feed_taste_matches_coldstart_cta),
                style = CorusFont.button,
                color = Color.White,
            )
        }
    }
}

/**
 * Neutral Taste Matches empty: a Club member / tester whose cohort has nothing
 * to show right now (server gated:"unavailable"). Branded, NOT the generic
 * invite grid. Mirrors iOS tasteMatchesNeutralEmptyState.
 */
@Composable
private fun TasteMatchesNeutralEmpty() {
    Spacer(modifier = Modifier.height(60.dp))
    VennDiagramIcon(
        size = 40.dp,
        color = CorusColors.Tertiary,
        shadedIntersection = true,
    )
    Spacer(modifier = Modifier.height(CorusSpacing.md))
    Text(
        text = stringResource(R.string.feed_taste_matches_unavailable_title),
        style = CorusFont.bodyMedium,
        color = CorusColors.Secondary,
        textAlign = TextAlign.Center,
    )
    Spacer(modifier = Modifier.height(CorusSpacing.xs))
    Text(
        text = stringResource(R.string.feed_taste_matches_unavailable_body),
        style = CorusFont.caption,
        color = CorusColors.Tertiary,
        textAlign = TextAlign.Center,
        modifier = Modifier.padding(horizontal = CorusSpacing.xl),
    )
    Spacer(modifier = Modifier.height(CorusSpacing.xxl))
}

/**
 * "No matches yet": posted enough to clear the cold-start gate, but no shared
 * artist/director with anyone yet (gated:"noMatchesYet"). Unlike the neutral
 * empty, this is actionable — keep posting and your matches fill in. Mirrors
 * iOS tasteMatchesNoMatchesYetState.
 */
@Composable
private fun TasteMatchesNoMatchesYet(onPost: () -> Unit) {
    Spacer(modifier = Modifier.height(60.dp))
    VennDiagramIcon(
        size = 36.dp,
        color = CorusColors.Tertiary,
        shadedIntersection = true,
    )
    Spacer(modifier = Modifier.height(CorusSpacing.md))
    Text(
        text = stringResource(R.string.feed_taste_matches_no_matches_title),
        style = CorusFont.songTitle,
        color = CorusColors.Secondary,
        textAlign = TextAlign.Center,
    )
    Spacer(modifier = Modifier.height(CorusSpacing.xs))
    Text(
        text = stringResource(R.string.feed_taste_matches_no_matches_body),
        style = CorusFont.body,
        color = CorusColors.Tertiary,
        textAlign = TextAlign.Center,
        modifier = Modifier.padding(horizontal = CorusSpacing.xl),
    )
    Spacer(modifier = Modifier.height(CorusSpacing.lg))
    Button(
        onClick = onPost,
        colors = ButtonDefaults.buttonColors(containerColor = CorusColors.Accent),
        shape = RoundedCornerShape(CorusSpacing.pillCornerRadius),
    ) {
        Text(
            text = stringResource(R.string.feed_taste_matches_coldstart_cta),
            style = CorusFont.button,
            color = Color.White,
        )
    }
    Spacer(modifier = Modifier.height(CorusSpacing.xxl))
}

/**
 * In-feed Taste Matches paywall backstop (server gated:"paywall"). A sparkles
 * mark + copy + "Learn more" CTA that opens the Club sheet. Mirrors iOS
 * tasteMatchesPaywallState.
 */
@Composable
private fun TasteMatchesPaywallState(onLearnMore: () -> Unit) {
    Spacer(modifier = Modifier.height(60.dp))
    VennDiagramIcon(
        size = 40.dp,
        color = CorusColors.Accent,
        shadedIntersection = true,
    )
    Spacer(modifier = Modifier.height(CorusSpacing.md))
    Text(
        text = stringResource(R.string.feed_taste_matches_paywall_title),
        style = CorusFont.bodyMedium,
        color = CorusColors.Text,
        textAlign = TextAlign.Center,
    )
    Spacer(modifier = Modifier.height(CorusSpacing.xs))
    Text(
        text = stringResource(R.string.feed_taste_matches_paywall_body),
        style = CorusFont.body,
        color = CorusColors.Secondary,
        textAlign = TextAlign.Center,
        modifier = Modifier.padding(horizontal = CorusSpacing.xl),
    )
    Spacer(modifier = Modifier.height(CorusSpacing.lg))
    Button(
        onClick = onLearnMore,
        colors = ButtonDefaults.buttonColors(containerColor = CorusColors.Accent),
        shape = RoundedCornerShape(CorusSpacing.pillCornerRadius),
    ) {
        Text(
            text = stringResource(R.string.feed_taste_matches_paywall_cta),
            style = CorusFont.button,
            color = Color.White,
        )
    }
    Spacer(modifier = Modifier.height(CorusSpacing.xxl))
}

/**
 * A dashed rounded-rect border (2dp stroke, 6/5 dash). Compose has no
 * first-class dashed border, so we stroke it in drawBehind, inset by half the
 * stroke so the rounded clip doesn't shave it. Mirrors the iOS seed-slot
 * StrokeStyle(lineWidth: 2, dash: [6, 5]).
 */
private fun Modifier.dashedBorder(color: Color, cornerRadius: Dp, strokeWidth: Dp = 2.dp): Modifier =
    this.drawBehind {
        val stroke = strokeWidth.toPx()
        val r = cornerRadius.toPx()
        drawRoundRect(
            color = color,
            topLeft = Offset(stroke / 2f, stroke / 2f),
            size = Size(size.width - stroke, size.height - stroke),
            cornerRadius = CornerRadius(r, r),
            style = Stroke(
                width = stroke,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 5.dp.toPx()), 0f),
            ),
        )
    }

@Composable
internal fun FeedDecadeEmptyState(decade: Int, onShowAllDecades: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(modifier = Modifier.height(60.dp))
        Icon(
            imageVector = Icons.Filled.History,
            contentDescription = null,
            tint = CorusColors.Tertiary,
            modifier = Modifier.size(36.dp),
        )
        Spacer(modifier = Modifier.height(CorusSpacing.md))
        Text(
            text = stringResource(R.string.feed_empty_decade_title, FeedDecade.label(decade)),
            style = CorusFont.songTitle,
            color = CorusColors.Secondary,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(CorusSpacing.xs))
        Text(
            text = stringResource(R.string.feed_empty_decade_subtitle),
            style = CorusFont.body,
            color = CorusColors.Tertiary,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = CorusSpacing.xl),
        )
        Spacer(modifier = Modifier.height(CorusSpacing.lg))
        Button(
            onClick = onShowAllDecades,
            colors = ButtonDefaults.buttonColors(containerColor = CorusColors.Accent),
            shape = RoundedCornerShape(CorusSpacing.pillCornerRadius),
        ) {
            Text(
                text = stringResource(R.string.feed_empty_decade_show_all),
                style = CorusFont.button,
                color = CorusColors.Background,
            )
        }
        Spacer(modifier = Modifier.height(CorusSpacing.xxl))
    }
}

/**
 * Feed top bar — centered "corus" logo, filter menu on the left,
 * playlist button on the right. Rendered as the first item of the
 * scrolling list so it scrolls away with content (matching iOS).
 */
@Composable
internal fun FeedHeader(
    showPlaylistButton: Boolean,
    isGeneratingPlaylist: Boolean,
    feedFilter: FeedFilter,
    filterMenuExpanded: Boolean,
    onFilterMenuExpandedChange: (Boolean) -> Unit,
    onSetFilter: (FeedFilter) -> Unit,
    onGeneratePlaylist: () -> Unit,
    showDecadeFilter: Boolean = false,
    feedDecade: Int? = null,
    onSetDecade: (Int?) -> Unit = {},
    trendingFeedEnabled: Boolean = false,
    favoritesEnabled: Boolean = false,
    tasteMatchesAvailable: Boolean = false,
    feedModeOrder: List<String> = FeedModeOrder.DEFAULT,
    feedMode: String = "following",
    onSetFeedMode: (String) -> Unit = {},
    onSwitcherOpened: () -> Unit = {},
    showFeedSwitchHint: Boolean = false,
    onDismissFeedSwitchHint: () -> Unit = {},
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = CorusSpacing.sm),
        contentAlignment = Alignment.Center,
    ) {
        if (trendingFeedEnabled || favoritesEnabled || tasteMatchesAvailable) {
            FeedTitleWithModeMenu(
                feedMode = feedMode,
                trendingFeedEnabled = trendingFeedEnabled,
                favoritesEnabled = favoritesEnabled,
                tasteMatchesAvailable = tasteMatchesAvailable,
                feedModeOrder = feedModeOrder,
                onSetFeedMode = onSetFeedMode,
                onSwitcherOpened = onSwitcherOpened,
                showHint = showFeedSwitchHint,
                onDismissHint = onDismissFeedSwitchHint,
            )
        } else {
            Text(
                text = stringResource(R.string.feed_app_title),
                style = CorusFont.appTitle,
                color = CorusColors.Text,
            )
        }

        if (showPlaylistButton) {
            IconButton(
                onClick = onGeneratePlaylist,
                modifier = Modifier.align(Alignment.CenterEnd),
                enabled = !isGeneratingPlaylist,
            ) {
                if (isGeneratingPlaylist) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = CorusColors.Secondary,
                        strokeWidth = 2.dp,
                    )
                } else {
                    Icon(
                        imageVector = Icons.Filled.QueueMusic,
                        contentDescription = stringResource(R.string.feed_cd_generate_playlist),
                        tint = CorusColors.Secondary,
                        modifier = Modifier.size(22.dp),
                    )
                }
            }
        }

        Row(
            modifier = Modifier.align(Alignment.CenterStart),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box {
                var decadeDrillIn by remember { mutableStateOf(false) }
                IconButton(
                    onClick = {
                        decadeDrillIn = false
                        onFilterMenuExpandedChange(true)
                    },
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.List,
                        contentDescription = stringResource(R.string.feed_cd_filter),
                        tint = if (!feedFilter.isAll || feedDecade != null) CorusColors.Accent else CorusColors.Secondary,
                    )
                }
                DropdownMenu(
                    expanded = filterMenuExpanded,
                    onDismissRequest = { onFilterMenuExpandedChange(false) },
                ) {
                    val activeCheckmark: @Composable () -> Unit = {
                        Icon(
                            imageVector = Icons.Filled.Check,
                            contentDescription = stringResource(R.string.common_selected),
                            tint = CorusColors.Accent,
                        )
                    }
                    val decadeName = stringResource(R.string.feed_filter_decade)
                    val decadeGroupLabel =
                        if (feedDecade == null) decadeName
                        else "$decadeName · ${FeedDecade.label(feedDecade)}"
                    if (decadeDrillIn && showDecadeFilter) {
                        DropdownMenuItem(
                            text = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                                        contentDescription = null,
                                        tint = CorusColors.Secondary,
                                        modifier = Modifier.size(18.dp),
                                    )
                                    Text(text = decadeGroupLabel, color = CorusColors.Secondary)
                                }
                            },
                            onClick = { decadeDrillIn = false },
                        )
                        HorizontalDivider()
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.feed_filter_decade_any)) },
                            trailingIcon = if (feedDecade == null) activeCheckmark else null,
                            onClick = {
                                onSetDecade(null)
                                onFilterMenuExpandedChange(false)
                            },
                        )
                        HorizontalDivider()
                        FeedDecade.OFFERED.forEach { decade ->
                            DropdownMenuItem(
                                text = { Text(FeedDecade.label(decade)) },
                                trailingIcon = if (feedDecade == decade) activeCheckmark else null,
                                onClick = {
                                    onSetDecade(decade)
                                    onFilterMenuExpandedChange(false)
                                },
                            )
                        }
                    } else {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.feed_filter_all)) },
                            trailingIcon = if (feedFilter == FeedFilter.ALL) activeCheckmark else null,
                            onClick = {
                                onSetFilter(FeedFilter.ALL)
                                onFilterMenuExpandedChange(false)
                            },
                        )
                        HorizontalDivider()
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.feed_filter_music)) },
                            trailingIcon = if (feedFilter == FeedFilter.MUSIC) activeCheckmark else null,
                            onClick = {
                                onSetFilter(FeedFilter.MUSIC)
                                onFilterMenuExpandedChange(false)
                            },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.feed_filter_film)) },
                            trailingIcon = if (feedFilter == FeedFilter.FILM) activeCheckmark else null,
                            onClick = {
                                onSetFilter(FeedFilter.FILM)
                                onFilterMenuExpandedChange(false)
                            },
                        )
                        HorizontalDivider()
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.feed_filter_music_new_releases)) },
                            trailingIcon = if (feedFilter == FeedFilter.MUSIC_NEW_RELEASES) activeCheckmark else null,
                            onClick = {
                                onSetFilter(FeedFilter.MUSIC_NEW_RELEASES)
                                onFilterMenuExpandedChange(false)
                            },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.feed_filter_film_new_releases)) },
                            trailingIcon = if (feedFilter == FeedFilter.FILM_NEW_RELEASES) activeCheckmark else null,
                            onClick = {
                                onSetFilter(FeedFilter.FILM_NEW_RELEASES)
                                onFilterMenuExpandedChange(false)
                            },
                        )
                        if (showDecadeFilter) {
                            HorizontalDivider()
                            DropdownMenuItem(
                                text = { Text(text = decadeGroupLabel) },
                                trailingIcon = {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        if (feedDecade != null) activeCheckmark()
                                        Icon(
                                            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                            contentDescription = null,
                                            tint = CorusColors.Secondary,
                                            modifier = Modifier.size(18.dp),
                                        )
                                    }
                                },
                                onClick = { decadeDrillIn = true },
                            )
                        }
                    }
                }
            }
            // Indicator icon next to the filter button. Same purple as the
            // existing NEW RELEASE badge for the new-releases filters; accent
            // blue for the plain media-type filters.
            when (feedFilter) {
                FeedFilter.MUSIC -> Icon(
                    imageVector = Icons.Filled.MusicNote,
                    contentDescription = null,
                    tint = CorusColors.Accent,
                    modifier = Modifier.size(20.dp),
                )
                FeedFilter.FILM -> Icon(
                    imageVector = Icons.Filled.Movie,
                    contentDescription = null,
                    tint = CorusColors.Accent,
                    modifier = Modifier.size(20.dp),
                )
                FeedFilter.MUSIC_NEW_RELEASES, FeedFilter.FILM_NEW_RELEASES -> Icon(
                    imageVector = Icons.Filled.LocalFireDepartment,
                    contentDescription = null,
                    tint = androidx.compose.ui.graphics.Color(0.62f, 0.35f, 0.95f),
                    modifier = Modifier.size(20.dp),
                )
                FeedFilter.ALL -> {}
            }
            if (feedDecade != null) {
                Spacer(modifier = Modifier.width(CorusSpacing.xs - 1.dp))
                Text(
                    text = FeedDecade.label(feedDecade),
                    style = CorusFont.button,
                    color = CorusColors.Accent,
                )
            }
        }
    }
}
