package fm.corus.android.ui.screens.auth

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import coil3.compose.AsyncImage
import fm.corus.android.R
import fm.corus.android.data.model.CymbalUser
import fm.corus.android.data.model.MusicService
import fm.corus.android.data.model.SuggestedUserMatch
import fm.corus.android.ui.components.PopularUsersInfiniteGrid
import fm.corus.android.ui.components.UserAvatarView
import fm.corus.android.ui.components.UsernameWithFlair
import fm.corus.android.ui.theme.CorusColors
import fm.corus.android.ui.theme.CorusFont
import fm.corus.android.ui.theme.CorusSpacing
import fm.corus.android.ui.theme.CorusSystemBars
import fm.corus.android.ui.util.PushNotificationPermission

private enum class SetupStep { MUSIC_SERVICE, SYNC_CONTACTS, FOLLOW_FRIENDS }

@Composable
fun SocialSetupFlow(
    onFinished: () -> Unit,
    viewModel: SocialSetupViewModel = hiltViewModel(),
) {
    var step by remember { mutableStateOf(SetupStep.MUSIC_SERVICE) }

    AnimatedContent(
        targetState = step,
        transitionSpec = { fadeIn() togetherWith fadeOut() },
        label = "social-setup",
    ) { currentStep ->
        when (currentStep) {
            SetupStep.MUSIC_SERVICE -> MusicServiceScreen(
                viewModel = viewModel,
                onContinue = { step = SetupStep.SYNC_CONTACTS },
            )
            SetupStep.SYNC_CONTACTS -> SyncContactsScreen(
                viewModel = viewModel,
                onContinue = {
                    viewModel.loadSuggestions()
                    step = SetupStep.FOLLOW_FRIENDS
                },
            )
            SetupStep.FOLLOW_FRIENDS -> FollowFriendsScreen(
                viewModel = viewModel,
                onFinished = onFinished,
            )
        }
    }
}

// ═══════════════════════════════════════════════
// MUSIC SERVICE SCREEN (Choose Your Player)
// ═══════════════════════════════════════════════

@Composable
private fun MusicServiceScreen(
    viewModel: SocialSetupViewModel,
    onContinue: () -> Unit,
) {
    var selected by remember { mutableStateOf(MusicService.SPOTIFY) }
    val tidalEnabled = viewModel.tidalEnabled

    Column(
        modifier = Modifier
            .fillMaxSize()
            .navigationBarsPadding()
            .padding(horizontal = CorusSpacing.xxl),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(modifier = Modifier.height(80.dp))

        Text(
            stringResource(id = R.string.music_service_title),
            style = CorusFont.appTitle,
            color = CorusColors.Text,
        )
        Spacer(modifier = Modifier.height(CorusSpacing.sm))
        Text(
            stringResource(id = R.string.music_service_subtitle),
            style = CorusFont.body,
            color = CorusColors.Secondary,
        )

        Spacer(modifier = Modifier.weight(1f))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(CorusSpacing.lg),
        ) {
            MusicServiceCard(
                modifier = Modifier.weight(1f),
                logoRes = R.drawable.spotify_logo,
                label = MusicService.SPOTIFY.displayLabel,
                subtitle = null,
                accent = CorusColors.SpotifyGreen,
                selected = selected == MusicService.SPOTIFY,
                onClick = { selected = MusicService.SPOTIFY },
            )
            MusicServiceCard(
                modifier = Modifier.weight(1f),
                logoRes = R.drawable.apple_music_logo,
                label = MusicService.APPLE_MUSIC.displayLabel,
                subtitle = stringResource(id = R.string.music_service_full_playback),
                accent = CorusColors.AppleMusicPink,
                selected = selected == MusicService.APPLE_MUSIC,
                onClick = { selected = MusicService.APPLE_MUSIC },
            )
            // TIDAL only appears when its Remote Config gate is on (and the
            // integration has shipped on all clients).
            if (tidalEnabled) {
                MusicServiceCard(
                    modifier = Modifier.weight(1f),
                    logoRes = R.drawable.tidal_logo,
                    label = MusicService.TIDAL.displayLabel,
                    // No "full-length playback" subtitle — TIDAL is preview-only for
                    // third-party apps; that claim is Apple Music only.
                    subtitle = null,
                    accent = CorusColors.TidalTeal,
                    selected = selected == MusicService.TIDAL,
                    onClick = { selected = MusicService.TIDAL },
                )
            }
        }

        Spacer(modifier = Modifier.height(CorusSpacing.lg))
        Text(
            stringResource(id = R.string.music_service_more_soon),
            style = CorusFont.caption,
            color = CorusColors.Secondary,
            textAlign = TextAlign.Center,
        )

        Spacer(modifier = Modifier.weight(1f))

        TextButton(onClick = {
            viewModel.saveMusicService(MusicService.SPOTIFY)
            onContinue()
        }) {
            Text(
                stringResource(id = R.string.music_service_skip),
                style = CorusFont.caption,
                color = CorusColors.Tertiary,
            )
        }

        Button(
            onClick = {
                viewModel.saveMusicService(selected)
                onContinue()
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(CorusSpacing.touchTarget),
            colors = ButtonDefaults.buttonColors(containerColor = CorusColors.Accent),
        ) {
            Text(
                stringResource(id = R.string.music_service_get_started),
                style = CorusFont.button,
                color = Color.White,
            )
        }

        Spacer(modifier = Modifier.height(CorusSpacing.xxxl))
    }
}

@Composable
private fun MusicServiceCard(
    modifier: Modifier = Modifier,
    logoRes: Int,
    label: String,
    subtitle: String?,
    accent: Color,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(CorusSpacing.cornerRadiusMedium))
            .background(if (selected) accent.copy(alpha = 0.08f) else CorusColors.CardBackground)
            .border(
                width = if (selected) 2.dp else 1.dp,
                color = if (selected) accent else CorusColors.Divider,
                shape = RoundedCornerShape(CorusSpacing.cornerRadiusMedium),
            )
            .clickable(onClick = onClick)
            .padding(vertical = CorusSpacing.xxl, horizontal = CorusSpacing.sm),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Image(
            painter = painterResource(id = logoRes),
            contentDescription = label,
            modifier = Modifier.size(48.dp),
        )
        Spacer(modifier = Modifier.height(CorusSpacing.lg))
        Text(
            label,
            style = CorusFont.bodyMedium,
            color = CorusColors.Text,
            textAlign = TextAlign.Center,
        )
        if (subtitle != null) {
            Spacer(modifier = Modifier.height(CorusSpacing.xs))
            Text(
                subtitle,
                style = CorusFont.caption,
                color = CorusColors.Secondary,
                textAlign = TextAlign.Center,
            )
        }
    }
}

// ═══════════════════════════════════════════════
// SYNC CONTACTS SCREEN
// ═══════════════════════════════════════════════

@Composable
private fun SyncContactsScreen(
    viewModel: SocialSetupViewModel,
    onContinue: () -> Unit,
) {
    val context = LocalContext.current
    val isSyncing by viewModel.isSyncing.collectAsState()

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            viewModel.syncContacts(context.contentResolver)
        } else {
            viewModel.analyticsService.logContactsSyncSkipped()
        }
        // Navigate forward regardless
        onContinue()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .navigationBarsPadding()
            .padding(horizontal = CorusSpacing.xxl),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(modifier = Modifier.height(80.dp))

        Text(
            stringResource(id = R.string.social_setup_find_friends_title),
            style = CorusFont.appTitle,
            color = CorusColors.Text,
        )
        Spacer(modifier = Modifier.height(CorusSpacing.sm))
        Text(
            stringResource(id = R.string.social_setup_find_friends_subtitle),
            style = CorusFont.body,
            color = CorusColors.Secondary,
        )

        Spacer(modifier = Modifier.weight(1f))

        // Animated radar illustration + description — matches iOS layout
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            RadarAnimation()

            Spacer(modifier = Modifier.height(CorusSpacing.xxl))

            Text(
                stringResource(id = R.string.social_setup_sync_explainer),
                style = CorusFont.bodyMedium,
                color = CorusColors.Secondary,
                textAlign = TextAlign.Center,
                lineHeight = 22.sp,
            )
        }

        Spacer(modifier = Modifier.weight(1f))

        // Sync button with icon — matches iOS: person.crop.circle.badge.plus
        Button(
            onClick = {
                viewModel.analyticsService.logSyncContactsTapped()
                permissionLauncher.launch(Manifest.permission.READ_CONTACTS)
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(CorusSpacing.touchTarget),
            colors = ButtonDefaults.buttonColors(containerColor = CorusColors.Accent),
            enabled = !isSyncing,
        ) {
            if (isSyncing) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    color = Color.White,
                    strokeWidth = 2.dp,
                )
            } else {
                Icon(
                    Icons.Filled.PersonAdd,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = Color.White,
                )
                Spacer(modifier = Modifier.width(CorusSpacing.sm))
                Text(stringResource(id = R.string.social_setup_sync_button), style = CorusFont.button, color = Color.White)
            }
        }

        TextButton(onClick = {
            viewModel.analyticsService.logContactsSyncSkipped()
            onContinue()
        }) {
            Text(stringResource(id = R.string.social_setup_sync_later), style = CorusFont.caption, color = CorusColors.Tertiary)
        }

        Spacer(modifier = Modifier.height(CorusSpacing.xxxl))
    }
}

/**
 * Animated radar illustration matching iOS:
 * - Expanding radar ring (2.4s duration)
 * - Person avatars highlight sequentially as radar passes
 */
@Composable
private fun RadarAnimation() {
    val avatarCount = 5
    val radarDuration = 2400L     // ms — ring expansion
    val pauseDuration = 800L      // ms — hold after last avatar

    // Phase-based state matching iOS: 0 = reset, 1 = ring expanding,
    // 2..6 = avatars 0..4 highlighted (stay highlighted until reset).
    var radarPhase by remember { mutableIntStateOf(0) }

    LaunchedEffect(Unit) {
        while (true) {
            // Snap to reset — ring is already invisible so this is seamless
            radarPhase = 0
            kotlinx.coroutines.delay(100)

            radarPhase = 1  // start ring expansion

            // Highlight each avatar sequentially as ring passes
            for (i in 0 until avatarCount) {
                kotlinx.coroutines.delay((580 + i * 50).toLong())
                radarPhase = 2 + i
            }

            kotlinx.coroutines.delay(pauseDuration)
        }
    }

    // Ring expansion: animate outward with easeOut, snap back instantly on reset
    val ringSize by animateFloatAsState(
        targetValue = if (radarPhase > 0) 220f else 20f,
        animationSpec = if (radarPhase > 0) {
            tween(durationMillis = radarDuration.toInt(), easing = FastOutSlowInEasing)
        } else {
            snap()  // instant reset — ring is already transparent so no visible jump
        },
        label = "ring-size",
    )
    val ringAlpha by animateFloatAsState(
        targetValue = if (radarPhase > 0) 0f else 0.25f,
        animationSpec = if (radarPhase > 0) {
            tween(durationMillis = radarDuration.toInt())
        } else {
            snap()  // instant reset
        },
        label = "ring-alpha",
    )

    Box(
        modifier = Modifier.size(240.dp),
        contentAlignment = Alignment.Center,
    ) {
        // Outer static ring
        Box(
            modifier = Modifier
                .size(210.dp)
                .clip(CircleShape)
                .background(CorusColors.Accent.copy(alpha = 0.08f)),
        )
        // Inner static ring
        Box(
            modifier = Modifier
                .size(140.dp)
                .clip(CircleShape)
                .background(CorusColors.Accent.copy(alpha = 0.12f)),
        )

        // Animated expanding radar ring
        Box(
            modifier = Modifier
                .size(ringSize.dp)
                .clip(CircleShape)
                .border(2.dp, CorusColors.Accent.copy(alpha = ringAlpha), CircleShape),
        )

        // Avatar placeholders at 72° intervals — highlight when radar passes, stay lit
        for (i in 0 until avatarCount) {
            val angle = i * (2.0 * Math.PI / avatarCount) - Math.PI / 2
            val isHighlighted = radarPhase >= 2 + i

            val avatarScale by animateFloatAsState(
                targetValue = if (isHighlighted) 1.15f else 1f,
                animationSpec = tween(300, easing = FastOutSlowInEasing),
                label = "avatar-scale-$i",
            )
            val bgAlpha by animateFloatAsState(
                targetValue = if (isHighlighted) 0.5f else 0.2f,
                animationSpec = tween(300, easing = FastOutSlowInEasing),
                label = "avatar-bg-$i",
            )
            val iconAlpha by animateFloatAsState(
                targetValue = if (isHighlighted) 1f else 0.6f,
                animationSpec = tween(300, easing = FastOutSlowInEasing),
                label = "avatar-icon-$i",
            )

            Box(
                modifier = Modifier
                    .offset(
                        x = (72 * kotlin.math.cos(angle)).dp,
                        y = (72 * kotlin.math.sin(angle)).dp,
                    )
                    .scale(avatarScale)
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(CorusColors.Accent.copy(alpha = bgAlpha)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Filled.Person,
                    contentDescription = null,
                    tint = CorusColors.Accent.copy(alpha = iconAlpha),
                    modifier = Modifier.size(18.dp),
                )
            }
        }

        // Center logo — matches iOS: actual logo image
        Image(
            painter = painterResource(R.drawable.logo_no_background),
            contentDescription = stringResource(id = R.string.social_setup_cd_corus_logo),
            modifier = Modifier.size(64.dp),
        )
    }
}

// ═══════════════════════════════════════════════
// FOLLOW FRIENDS SCREEN
// ═══════════════════════════════════════════════

@Composable
private fun FollowFriendsScreen(
    viewModel: SocialSetupViewModel,
    onFinished: () -> Unit,
) {
    val context = LocalContext.current
    val pushPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        viewModel.analyticsService.logNotificationPermissionResult(granted)
        // Finish regardless — the permission prompt is best-effort.
        viewModel.markPushPermissionRequested()
        onFinished()
    }

    val finishWithPushPrompt: () -> Unit = {
        viewModel.logFollowFriendsOnboardingCompleted()
        if (PushNotificationPermission.shouldRequestPushPermission(context)) {
            pushPermissionLauncher.launch(PushNotificationPermission.permission)
        } else {
            viewModel.markPushPermissionRequested()
            onFinished()
        }
    }

    val contactMatches by viewModel.contactMatches.collectAsState()
    val contactsSynced by viewModel.contactsSynced.collectAsState()
    val isLoading by viewModel.isLoadingSuggestions.collectAsState()
    val followedIds by viewModel.followedIds.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val searchResults by viewModel.searchResults.collectAsState()
    val isSearching by viewModel.isSearching.collectAsState()
    val isFinishing by viewModel.isFinishing.collectAsState()
    val previewSheetUser by viewModel.previewSheetUser.collectAsState()
    val previewSheetPosts by viewModel.previewSheetPosts.collectAsState()
    val previewSheetIsLoading by viewModel.previewSheetIsLoading.collectAsState()
    val previewSheetIsLoadingMore by viewModel.previewSheetIsLoadingMore.collectAsState()
    val previewSheetHasMore by viewModel.previewSheetHasMore.collectAsState()
    val keyboardController = LocalSoftwareKeyboardController.current
    val scrollDismissConnection = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (source == NestedScrollSource.UserInput && available.y != 0f) {
                    keyboardController?.hide()
                }
                return Offset.Zero
            }
        }
    }

    var showSeeAll by remember { mutableStateOf<SeeAllDestination?>(null) }

    AnimatedContent(
        targetState = showSeeAll,
        transitionSpec = {
            if (targetState != null) {
                slideInHorizontally(tween(400), initialOffsetX = { it }) togetherWith
                    slideOutHorizontally(tween(400), targetOffsetX = { -it / 3 })
            } else {
                slideInHorizontally(tween(400), initialOffsetX = { -it / 3 }) togetherWith
                    slideOutHorizontally(tween(400), targetOffsetX = { it })
            }
        },
        label = "seeAllTransition",
    ) { destination ->
        if (destination != null) {
            OnboardingSeeAllScreen(
                destination = destination,
                contactMatches = contactMatches,
                followedIds = followedIds,
                onFollow = { viewModel.toggleFollow(it) },
                onBack = { showSeeAll = null },
            )
        } else {
            FollowFriendsMainContent(
                viewModel = viewModel,
                contactMatches = contactMatches,
                contactsSynced = contactsSynced,
                isLoading = isLoading,
                followedIds = followedIds,
                searchQuery = searchQuery,
                searchResults = searchResults,
                isSearching = isSearching,
                isFinishing = isFinishing,
                keyboardController = keyboardController,
                scrollDismissConnection = scrollDismissConnection,
                onSeeAll = { destination ->
                    viewModel.analyticsService.logOnboardingSeeAllTapped(destination.analyticsName)
                    showSeeAll = destination
                },
                onFinished = finishWithPushPrompt,
            )
        }
    }

    previewSheetUser?.let { sheetUser ->
        UserPreviewSheet(
            user = sheetUser,
            posts = previewSheetPosts,
            isLoading = previewSheetIsLoading,
            isLoadingMore = previewSheetIsLoadingMore,
            hasMore = previewSheetHasMore,
            isFollowed = followedIds.contains(sheetUser.id),
            nowPlaying = viewModel.nowPlayingManagerInstance,
            onFollow = { viewModel.toggleFollow(sheetUser.id) },
            onLoadMore = { viewModel.loadMorePreviewPosts() },
            onDismiss = { viewModel.closeUserPreview() },
        )
    }
}

@Composable
private fun FollowFriendsMainContent(
    viewModel: SocialSetupViewModel,
    contactMatches: List<CymbalUser>,
    contactsSynced: Boolean,
    isLoading: Boolean,
    followedIds: Set<String>,
    searchQuery: String,
    searchResults: List<CymbalUser>,
    isSearching: Boolean,
    isFinishing: Boolean,
    keyboardController: androidx.compose.ui.platform.SoftwareKeyboardController?,
    scrollDismissConnection: NestedScrollConnection,
    onSeeAll: (SeeAllDestination) -> Unit,
    onFinished: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize().navigationBarsPadding().nestedScroll(scrollDismissConnection)) {
        Spacer(modifier = Modifier.height(60.dp))

        // Header
        Column(
            modifier = Modifier.padding(horizontal = CorusSpacing.xxl),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                stringResource(id = R.string.social_setup_curate_title),
                style = CorusFont.appTitle,
                color = CorusColors.Text,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
            )
            Spacer(modifier = Modifier.height(CorusSpacing.sm))
            Text(
                stringResource(id = R.string.social_setup_curate_subtitle),
                style = CorusFont.body,
                color = CorusColors.Secondary,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
            )
        }

        Spacer(modifier = Modifier.height(CorusSpacing.xxxl))

        // Search bar
        OnboardingSearchBar(
            query = searchQuery,
            onQueryChange = { viewModel.searchUsers(it) },
            onSearch = { keyboardController?.hide() },
            modifier = Modifier.padding(horizontal = CorusSpacing.xxl),
        )

        Spacer(modifier = Modifier.height(CorusSpacing.lg))

        // Content
        if (searchQuery.length >= 2) {
            // Search results mode
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(bottom = CorusSpacing.lg),
            ) {
                if (isSearching) {
                    items(4) { SkeletonUserRow() }
                } else if (searchResults.isEmpty()) {
                    item {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(CorusSpacing.xxl),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Text(stringResource(id = R.string.search_no_users_found), style = CorusFont.bodyMedium, color = CorusColors.Secondary)
                        }
                    }
                } else {
                    items(searchResults, key = { it.id }) { user ->
                        OnboardingUserRow(
                            user = user,
                            isFollowed = followedIds.contains(user.id),
                            onFollow = { viewModel.toggleFollow(user.id) },
                            onTap = { viewModel.openUserPreview(user) },
                        )
                    }
                }
            }
        } else {
            // Suggestion mode — vertical, paginated grid of popular users.
            // Friends section (when present) renders as topContent above the grid
            // so the entire surface scrolls together.
            PopularUsersInfiniteGrid(
                excludeIds = emptySet(),
                followedIds = followedIds,
                onUserTap = { user -> viewModel.openUserPreview(user) },
                onFollowTap = { user -> viewModel.toggleFollow(user.id) },
                modifier = Modifier.weight(1f),
                topContent = {
                    if (!isLoading) {
                        if (contactMatches.isNotEmpty()) {
                            Column(modifier = Modifier.fillMaxWidth()) {
                                OnboardingSectionHeader(
                                    title = stringResource(id = R.string.social_setup_section_friends),
                                    showSeeAll = contactMatches.size > 5,
                                    onSeeAll = { onSeeAll(SeeAllDestination.FRIENDS) },
                                )
                                contactMatches.take(5).forEach { user ->
                                    OnboardingUserRow(
                                        user = user,
                                        subtitle = stringResource(id = R.string.search_subtitle_from_contacts),
                                        isFollowed = followedIds.contains(user.id),
                                        onFollow = { viewModel.toggleFollow(user.id) },
                                        onTap = { viewModel.openUserPreview(user) },
                                    )
                                }
                            }
                        } else if (contactsSynced) {
                            Column(modifier = Modifier.fillMaxWidth()) {
                                OnboardingSectionHeader(
                                    title = stringResource(id = R.string.social_setup_section_friends),
                                )
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = CorusSpacing.xxl, vertical = CorusSpacing.lg),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                ) {
                                    Text(
                                        stringResource(id = R.string.search_no_contact_matches_title),
                                        style = CorusFont.bodyMedium,
                                        color = CorusColors.Secondary,
                                    )
                                    Spacer(modifier = Modifier.height(CorusSpacing.xs))
                                    Text(
                                        stringResource(id = R.string.social_setup_will_notify),
                                        style = CorusFont.caption,
                                        color = CorusColors.Tertiary,
                                    )
                                }
                            }
                        }
                    }
                },
            )
        }

        // Continue button — matches iOS button text
        Button(
            onClick = onFinished,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = CorusSpacing.xxl)
                .padding(bottom = CorusSpacing.xxxl)
                .height(CorusSpacing.touchTarget),
            colors = ButtonDefaults.buttonColors(containerColor = CorusColors.Accent),
            enabled = !isFinishing,
        ) {
            if (isFinishing) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
            } else {
                Text(stringResource(id = R.string.onboarding_button_continue), style = CorusFont.button, color = Color.White)
            }
        }
    }
}


// ═══════════════════════════════════════════════
// SEE ALL SCREEN
// ═══════════════════════════════════════════════

enum class SeeAllDestination(val titleRes: Int, val analyticsName: String) {
    FRIENDS(R.string.social_setup_seeall_friends, "friends"),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun OnboardingSeeAllScreen(
    destination: SeeAllDestination,
    contactMatches: List<CymbalUser>,
    followedIds: Set<String>,
    onFollow: (String) -> Unit,
    onBack: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(id = destination.titleRes), style = CorusFont.screenTitle, color = CorusColors.Text) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.Close, contentDescription = stringResource(id = R.string.common_back), tint = CorusColors.Text)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = CorusColors.Background),
            )
        },
    ) { padding ->
        when (destination) {
            SeeAllDestination.FRIENDS -> {
                LazyColumn(modifier = Modifier.padding(padding)) {
                    items(contactMatches, key = { it.id }) { user ->
                        OnboardingUserRow(
                            user = user,
                            subtitle = stringResource(id = R.string.search_subtitle_from_contacts),
                            isFollowed = followedIds.contains(user.id),
                            onFollow = { onFollow(user.id) },
                        )
                    }
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════
// SHARED COMPOSABLES
// ═══════════════════════════════════════════════

@Composable
private fun OnboardingSearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    TextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(CorusSpacing.cornerRadiusMedium))
            .background(CorusColors.CardBackground)
            .border(1.dp, CorusColors.Divider, RoundedCornerShape(CorusSpacing.cornerRadiusMedium)),
        placeholder = { Text(stringResource(id = R.string.search_placeholder_users), style = CorusFont.body, color = CorusColors.Tertiary) },
        leadingIcon = {
            Icon(Icons.Filled.Search, contentDescription = null, tint = CorusColors.Tertiary)
        },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = { onQueryChange("") }) {
                    Icon(Icons.Filled.Close, contentDescription = stringResource(id = R.string.search_cd_clear), tint = CorusColors.Tertiary)
                }
            }
        },
        singleLine = true,
        textStyle = CorusFont.body,
        colors = TextFieldDefaults.colors(
            focusedContainerColor = Color.Transparent,
            unfocusedContainerColor = Color.Transparent,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
        ),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { onSearch() }),
    )
}

@Composable
private fun OnboardingSectionHeader(
    title: String,
    showSeeAll: Boolean = false,
    onSeeAll: () -> Unit = {},
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = CorusSpacing.xxl, vertical = CorusSpacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, style = CorusFont.bodyMedium, color = CorusColors.Text, modifier = Modifier.weight(1f))
        if (showSeeAll) {
            Text(
                "See all",
                style = CorusFont.captionMedium,
                color = CorusColors.Accent,
                modifier = Modifier.clickable(onClick = onSeeAll),
            )
        }
    }
}

@Composable
private fun OnboardingUserRow(
    user: CymbalUser,
    subtitle: String? = null,
    isFollowed: Boolean,
    onFollow: () -> Unit,
    onTap: (() -> Unit)? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onTap != null) Modifier.clickable { onTap() } else Modifier)
            .padding(horizontal = CorusSpacing.xxl, vertical = CorusSpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        UserAvatarView(avatarURL = user.avatarURL, displayName = user.displayName, size = CorusSpacing.avatarMedium)
        Spacer(modifier = Modifier.width(CorusSpacing.md))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                user.displayName,
                style = CorusFont.bodyMedium,
                color = CorusColors.Text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                subtitle ?: "@${user.username}",
                style = CorusFont.caption,
                color = CorusColors.Secondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(modifier = Modifier.width(CorusSpacing.sm))
        Button(
            onClick = onFollow,
            shape = RoundedCornerShape(CorusSpacing.pillCornerRadius),
            colors = ButtonDefaults.buttonColors(
                containerColor = if (isFollowed) CorusColors.CardBackground else CorusColors.Accent,
                contentColor = if (isFollowed) CorusColors.Secondary else Color.White,
            ),
            border = if (isFollowed) androidx.compose.foundation.BorderStroke(1.dp, CorusColors.Divider) else null,
            contentPadding = PaddingValues(horizontal = CorusSpacing.lg, vertical = CorusSpacing.xs),
        ) {
            Text(
                if (isFollowed) stringResource(id = R.string.search_button_following) else stringResource(id = R.string.search_button_follow),
                style = CorusFont.buttonSmall,
            )
        }
    }
    HorizontalDivider(
        color = CorusColors.Divider,
        modifier = Modifier.padding(start = 72.dp, end = CorusSpacing.xxl),
    )
}

@Composable
private fun SkeletonUserRow() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = CorusSpacing.xxl, vertical = CorusSpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(CorusSpacing.avatarMedium)
                .clip(CircleShape)
                .background(CorusColors.CardBackground),
        )
        Spacer(modifier = Modifier.width(CorusSpacing.md))
        Column(modifier = Modifier.weight(1f)) {
            Box(
                modifier = Modifier
                    .width(120.dp)
                    .height(14.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(CorusColors.CardBackground),
            )
            Spacer(modifier = Modifier.height(CorusSpacing.xs))
            Box(
                modifier = Modifier
                    .width(80.dp)
                    .height(12.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(CorusColors.CardBackground),
            )
        }
        Box(
            modifier = Modifier
                .width(72.dp)
                .height(32.dp)
                .clip(RoundedCornerShape(50))
                .background(CorusColors.CardBackground),
        )
    }
}

