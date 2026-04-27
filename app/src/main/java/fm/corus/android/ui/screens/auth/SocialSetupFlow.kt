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
import fm.corus.android.data.model.SuggestedUserMatch
import fm.corus.android.ui.components.SkeletonSectionHeader
import fm.corus.android.ui.components.TasteMatchCard
import fm.corus.android.ui.components.UserAvatarView
import fm.corus.android.ui.components.UsernameWithFlair
import fm.corus.android.ui.theme.CorusColors
import fm.corus.android.ui.theme.CorusFont
import fm.corus.android.ui.theme.CorusSpacing
import fm.corus.android.ui.util.PushNotificationPermission

private enum class SetupStep { SYNC_CONTACTS, FOLLOW_FRIENDS }

@Composable
fun SocialSetupFlow(
    onFinished: () -> Unit,
    viewModel: SocialSetupViewModel = hiltViewModel(),
) {
    var step by remember { mutableStateOf(SetupStep.SYNC_CONTACTS) }

    AnimatedContent(
        targetState = step,
        transitionSpec = { fadeIn() togetherWith fadeOut() },
        label = "social-setup",
    ) { currentStep ->
        when (currentStep) {
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
    val popularUsers by viewModel.popularUsers.collectAsState()
    val musicBots by viewModel.musicBotMatches.collectAsState()
    val filmBots by viewModel.filmBotMatches.collectAsState()
    val isLoading by viewModel.isLoadingSuggestions.collectAsState()
    val followedIds by viewModel.followedIds.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val searchResults by viewModel.searchResults.collectAsState()
    val isSearching by viewModel.isSearching.collectAsState()
    val isFinishing by viewModel.isFinishing.collectAsState()
    val previewingUserId by viewModel.previewingUserId.collectAsState()
    val isPreviewLoading by viewModel.isPreviewLoading.collectAsState()
    val nowPlayingState by viewModel.nowPlayingState.collectAsState()
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
    var filmBotPreviewMatch by remember { mutableStateOf<SuggestedUserMatch?>(null) }

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
                popularUsers = popularUsers,
                musicBots = musicBots,
                filmBots = filmBots,
                followedIds = followedIds,
                previewingUserId = previewingUserId,
                isPreviewLoading = isPreviewLoading,
                isPreviewPlaying = nowPlayingState.isPlaying,
                onFollow = { viewModel.toggleFollow(it) },
                onMusicBotTap = { userId -> viewModel.playUserPreview(userId) },
                onFilmBotTap = { userId -> filmBotPreviewMatch = filmBots.find { it.user.id == userId } },
                onBack = { showSeeAll = null },
            )
        } else {
            FollowFriendsMainContent(
                viewModel = viewModel,
                contactMatches = contactMatches,
                contactsSynced = contactsSynced,
                popularUsers = popularUsers,
                musicBots = musicBots,
                filmBots = filmBots,
                isLoading = isLoading,
                followedIds = followedIds,
                searchQuery = searchQuery,
                searchResults = searchResults,
                isSearching = isSearching,
                isFinishing = isFinishing,
                previewingUserId = previewingUserId,
                isPreviewLoading = isPreviewLoading,
                nowPlayingState = nowPlayingState,
                keyboardController = keyboardController,
                scrollDismissConnection = scrollDismissConnection,
                onSeeAll = { destination ->
                    viewModel.analyticsService.logOnboardingSeeAllTapped(destination.analyticsName)
                    showSeeAll = destination
                },
                onFilmBotTap = { userId -> filmBotPreviewMatch = filmBots.find { it.user.id == userId } },
                onFinished = finishWithPushPrompt,
            )
        }
    }

    if (filmBotPreviewMatch != null) {
        FilmBotPreviewSheet(
            match = filmBotPreviewMatch!!,
            isFollowed = followedIds.contains(filmBotPreviewMatch!!.user.id),
            onFollow = { viewModel.toggleFollow(filmBotPreviewMatch!!.user.id) },
            onDismiss = { filmBotPreviewMatch = null },
            viewModel = viewModel,
        )
    }
}

@Composable
private fun FollowFriendsMainContent(
    viewModel: SocialSetupViewModel,
    contactMatches: List<CymbalUser>,
    contactsSynced: Boolean,
    popularUsers: List<CymbalUser>,
    musicBots: List<SuggestedUserMatch>,
    filmBots: List<SuggestedUserMatch>,
    isLoading: Boolean,
    followedIds: Set<String>,
    searchQuery: String,
    searchResults: List<CymbalUser>,
    isSearching: Boolean,
    isFinishing: Boolean,
    previewingUserId: String?,
    isPreviewLoading: Boolean,
    nowPlayingState: fm.corus.android.domain.NowPlayingState,
    keyboardController: androidx.compose.ui.platform.SoftwareKeyboardController?,
    scrollDismissConnection: NestedScrollConnection,
    onSeeAll: (SeeAllDestination) -> Unit,
    onFilmBotTap: (String) -> Unit,
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
        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(bottom = CorusSpacing.lg),
        ) {
            if (searchQuery.length >= 2) {
                // Search results mode
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
                            isPreviewLoading = previewingUserId == user.id && isPreviewLoading,
                            isPreviewPlaying = previewingUserId == user.id && nowPlayingState.isPlaying,
                            onFollow = { viewModel.toggleFollow(user.id) },
                            onTap = { viewModel.playUserPreview(user.id) },
                        )
                    }
                }
            } else {
                // Suggestion sections

                // Friends on Corus — only show after loading completes (matching iOS)
                if (!isLoading) {
                    if (contactMatches.isNotEmpty()) {
                        item {
                            OnboardingSectionHeader(
                                title = stringResource(id = R.string.social_setup_section_friends),
                                showSeeAll = contactMatches.size > 5,
                                onSeeAll = { onSeeAll(SeeAllDestination.FRIENDS) },
                            )
                        }
                        items(contactMatches.take(5), key = { "contact-${it.id}" }) { user ->
                            OnboardingUserRow(
                                user = user,
                                subtitle = stringResource(id = R.string.search_subtitle_from_contacts),
                                isFollowed = followedIds.contains(user.id),
                                isPreviewLoading = previewingUserId == user.id && isPreviewLoading,
                                isPreviewPlaying = previewingUserId == user.id && nowPlayingState.isPlaying,
                                onFollow = { viewModel.toggleFollow(user.id) },
                                onTap = { viewModel.playUserPreview(user.id) },
                            )
                        }
                    } else if (contactsSynced) {
                        // Friends empty state — only show if user chose to sync contacts
                        item {
                            OnboardingSectionHeader(
                                title = stringResource(id = R.string.social_setup_section_friends),
                            )
                        }
                        item {
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

                // Popular on Corus — with skeleton header + rows matching iOS
                if (isLoading) {
                    item { SkeletonSectionHeader() }
                    items(4) { SkeletonUserRow() }
                } else if (popularUsers.isNotEmpty()) {
                    item {
                        OnboardingSectionHeader(
                            title = stringResource(id = R.string.social_setup_section_popular),
                            showSeeAll = popularUsers.size > 4,
                            onSeeAll = { onSeeAll(SeeAllDestination.POPULAR) },
                        )
                    }
                    items(popularUsers.take(4), key = { "popular-${it.id}" }) { user ->
                        OnboardingUserRow(
                            user = user,
                            subtitle = stringResource(id = R.string.search_followers_count_format, user.followerCount),
                            isFollowed = followedIds.contains(user.id),
                            isPreviewLoading = previewingUserId == user.id && isPreviewLoading,
                            isPreviewPlaying = previewingUserId == user.id && nowPlayingState.isPlaying,
                            onFollow = { viewModel.toggleFollow(user.id) },
                            onTap = { viewModel.playUserPreview(user.id) },
                        )
                    }
                }

                // Music Bots
                if (musicBots.isNotEmpty() || isLoading) {
                    item {
                        Spacer(modifier = Modifier.height(CorusSpacing.lg))
                        Text(
                            stringResource(id = R.string.feed_empty_curated_music),
                            style = CorusFont.caption,
                            color = CorusColors.Tertiary,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth().padding(vertical = CorusSpacing.md),
                        )
                    }
                    item {
                        OnboardingBotGrid(
                            bots = musicBots.take(6),
                            isLoading = isLoading,
                            followedIds = followedIds,
                            onFollow = { viewModel.toggleFollow(it) },
                            onUserTap = { userId -> viewModel.playUserPreview(userId) },
                            showPreviewButton = true,
                            previewingUserId = previewingUserId,
                            isPreviewLoading = isPreviewLoading,
                            isPreviewPlaying = nowPlayingState.isPlaying,
                        )
                        if (musicBots.size > 6) {
                            Box(
                                modifier = Modifier.fillMaxWidth(),
                                contentAlignment = Alignment.Center,
                            ) {
                                TextButton(
                                    onClick = { onSeeAll(SeeAllDestination.MUSIC_BOTS) },
                                ) {
                                    Text(stringResource(id = R.string.feed_empty_see_all), style = CorusFont.captionMedium, color = CorusColors.Accent)
                                }
                            }
                        }
                    }
                }

                // Film Bots
                if (filmBots.isNotEmpty() || isLoading) {
                    item {
                        Spacer(modifier = Modifier.height(CorusSpacing.lg))
                        Text(
                            stringResource(id = R.string.feed_empty_curated_film),
                            style = CorusFont.caption,
                            color = CorusColors.Tertiary,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth().padding(vertical = CorusSpacing.md),
                        )
                    }
                    item {
                        OnboardingBotGrid(
                            bots = filmBots.take(6),
                            isLoading = isLoading,
                            followedIds = followedIds,
                            onFollow = { viewModel.toggleFollow(it) },
                            onUserTap = { userId -> onFilmBotTap(userId) },
                        )
                        if (filmBots.size > 6) {
                            Box(
                                modifier = Modifier.fillMaxWidth(),
                                contentAlignment = Alignment.Center,
                            ) {
                                TextButton(
                                    onClick = { onSeeAll(SeeAllDestination.FILM_BOTS) },
                                ) {
                                    Text(stringResource(id = R.string.feed_empty_see_all), style = CorusFont.captionMedium, color = CorusColors.Accent)
                                }
                            }
                        }
                    }
                }
            }
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
    POPULAR(R.string.social_setup_seeall_popular, "popular"),
    MUSIC_BOTS(R.string.social_setup_seeall_music_bots, "musicBots"),
    FILM_BOTS(R.string.social_setup_seeall_film_bots, "filmBots"),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun OnboardingSeeAllScreen(
    destination: SeeAllDestination,
    contactMatches: List<CymbalUser>,
    popularUsers: List<CymbalUser>,
    musicBots: List<SuggestedUserMatch>,
    filmBots: List<SuggestedUserMatch>,
    followedIds: Set<String>,
    previewingUserId: String?,
    isPreviewLoading: Boolean,
    isPreviewPlaying: Boolean,
    onFollow: (String) -> Unit,
    onMusicBotTap: (String) -> Unit,
    onFilmBotTap: (String) -> Unit,
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
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White),
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
            SeeAllDestination.POPULAR -> {
                LazyColumn(modifier = Modifier.padding(padding)) {
                    items(popularUsers, key = { it.id }) { user ->
                        OnboardingUserRow(
                            user = user,
                            subtitle = stringResource(id = R.string.search_followers_count_format, user.followerCount),
                            isFollowed = followedIds.contains(user.id),
                            onFollow = { onFollow(user.id) },
                        )
                    }
                }
            }
            SeeAllDestination.MUSIC_BOTS, SeeAllDestination.FILM_BOTS -> {
                val bots = if (destination == SeeAllDestination.MUSIC_BOTS) musicBots else filmBots
                val isMusic = destination == SeeAllDestination.MUSIC_BOTS
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    modifier = Modifier.padding(padding),
                    contentPadding = PaddingValues(horizontal = CorusSpacing.xxl, vertical = CorusSpacing.sm),
                    horizontalArrangement = Arrangement.spacedBy(CorusSpacing.md),
                    verticalArrangement = Arrangement.spacedBy(CorusSpacing.md),
                ) {
                    items(bots, key = { it.user.id }) { match ->
                        TasteMatchCard(
                            match = match,
                            isFollowing = followedIds.contains(match.user.id),
                            onFollowTap = { onFollow(match.user.id) },
                            onUserTap = {
                                if (isMusic) onMusicBotTap(match.user.id)
                                else onFilmBotTap(match.user.id)
                            },
                            showPreviewButton = isMusic,
                            isPreviewLoading = isMusic && isPreviewLoading && previewingUserId == match.user.id,
                            isPreviewing = isMusic && isPreviewPlaying && previewingUserId == match.user.id,
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
    isPreviewLoading: Boolean = false,
    isPreviewPlaying: Boolean = false,
    onFollow: () -> Unit,
    onTap: (() -> Unit)? = null,
) {
    val overlayAlpha by animateFloatAsState(
        targetValue = if (isPreviewLoading || isPreviewPlaying) 0.4f else 0f,
        animationSpec = tween(200),
        label = "overlay-alpha",
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onTap != null) Modifier.clickable { onTap() } else Modifier)
            .padding(horizontal = CorusSpacing.xxl, vertical = CorusSpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Avatar with play/loading overlay (matching iOS)
        Box(contentAlignment = Alignment.Center) {
            UserAvatarView(avatarURL = user.avatarURL, displayName = user.displayName, size = CorusSpacing.avatarMedium)
            // Dark scrim
            Box(
                modifier = Modifier
                    .size(CorusSpacing.avatarMedium)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = overlayAlpha)),
            )
            // Loading spinner or pause icon
            if (isPreviewLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    color = Color.White,
                    strokeWidth = 2.dp,
                )
            } else if (isPreviewPlaying) {
                Icon(
                    imageVector = Icons.Filled.Pause,
                    contentDescription = stringResource(id = R.string.post_detail_cd_pause),
                    tint = Color.White,
                    modifier = Modifier.size(14.dp),
                )
            }
        }
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
private fun OnboardingBotGrid(
    bots: List<SuggestedUserMatch>,
    isLoading: Boolean,
    followedIds: Set<String>,
    onFollow: (String) -> Unit,
    onUserTap: (String) -> Unit,
    showPreviewButton: Boolean = false,
    previewingUserId: String? = null,
    isPreviewLoading: Boolean = false,
    isPreviewPlaying: Boolean = false,
) {
    val columns = 2
    val rows = if (isLoading) 2 else (bots.size + columns - 1) / columns
    Column(modifier = Modifier.padding(horizontal = CorusSpacing.xxl)) {
        for (row in 0 until rows) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(CorusSpacing.md),
            ) {
                for (col in 0 until columns) {
                    val idx = row * columns + col
                    if (isLoading) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .aspectRatio(0.75f)
                                .clip(RoundedCornerShape(CorusSpacing.cornerRadiusMedium))
                                .background(CorusColors.CardBackground),
                        )
                    } else if (idx < bots.size) {
                        Box(modifier = Modifier.weight(1f)) {
                            val botUserId = bots[idx].user.id
                            val isThisPreviewing = previewingUserId == botUserId
                            TasteMatchCard(
                                match = bots[idx],
                                isFollowing = followedIds.contains(botUserId),
                                onFollowTap = { onFollow(botUserId) },
                                onUserTap = { onUserTap(botUserId) },
                                showPreviewButton = showPreviewButton,
                                isPreviewLoading = isThisPreviewing && isPreviewLoading,
                                isPreviewing = isThisPreviewing && isPreviewPlaying && !isPreviewLoading,
                            )
                        }
                    } else {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
            if (row < rows - 1) {
                Spacer(modifier = Modifier.height(CorusSpacing.md))
            }
        }
    }
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

// ═══════════════════════════════════════════════
// FILM BOT PREVIEW SHEET
// ═══════════════════════════════════════════════

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FilmBotPreviewSheet(
    match: SuggestedUserMatch,
    isFollowed: Boolean,
    onFollow: () -> Unit,
    onDismiss: () -> Unit,
    viewModel: SocialSetupViewModel,
) {
    val posts by viewModel.filmBotPosts.collectAsState()
    val isLoadingPosts by viewModel.isLoadingFilmBotPosts.collectAsState()

    LaunchedEffect(match.user.id) {
        viewModel.loadFilmBotPosts(match.user.id)
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false),
        containerColor = CorusColors.Background,
        dragHandle = { BottomSheetDefaults.DragHandle() },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = CorusSpacing.xxxl),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Avatar
            UserAvatarView(avatarURL = match.user.avatarURL, displayName = match.user.displayName, size = 64.dp)
            Spacer(modifier = Modifier.height(CorusSpacing.md))

            // Username + flair badge
            UsernameWithFlair(
                username = match.user.username,
                isVerified = match.user.isVerified,
                isClubMember = match.user.isClubMember,
                flairStyle = match.user.flairStyle,
                isBot = match.user.isBot,
                style = CorusFont.bodyMedium,
            )

            // Bio
            if (match.user.bio.isNotBlank()) {
                Spacer(modifier = Modifier.height(CorusSpacing.sm))
                Text(
                    match.user.bio,
                    style = CorusFont.caption,
                    color = CorusColors.Secondary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = CorusSpacing.xxl),
                )
            }

            // Follow button
            Spacer(modifier = Modifier.height(CorusSpacing.md))
            Button(
                onClick = onFollow,
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isFollowed) CorusColors.CardBackground else CorusColors.Accent,
                    contentColor = if (isFollowed) CorusColors.Text else Color.White,
                ),
                shape = RoundedCornerShape(50),
                contentPadding = PaddingValues(horizontal = CorusSpacing.xl, vertical = CorusSpacing.sm),
            ) {
                Text(if (isFollowed) stringResource(id = R.string.search_button_following) else stringResource(id = R.string.search_button_follow), style = CorusFont.buttonSmall)
            }

            Spacer(modifier = Modifier.height(CorusSpacing.xxl))

            // Poster grid
            if (isLoadingPosts) {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 400.dp),
                    contentPadding = PaddingValues(horizontal = CorusSpacing.sm),
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    items(9) {
                        Box(
                            modifier = Modifier
                                .aspectRatio(2f / 3f)
                                .clip(RoundedCornerShape(4.dp))
                                .background(CorusColors.CardBackground),
                        )
                    }
                }
            } else if (posts.isEmpty()) {
                Text(
                    stringResource(id = R.string.other_profile_empty_no_films),
                    style = CorusFont.body,
                    color = CorusColors.Secondary,
                    modifier = Modifier.padding(CorusSpacing.xxl),
                )
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 400.dp),
                    contentPadding = PaddingValues(horizontal = CorusSpacing.sm),
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    items(posts) { post ->
                        AsyncImage(
                            model = post.posterURL ?: post.displayImageURL,
                            contentDescription = null,
                            modifier = Modifier
                                .aspectRatio(2f / 3f)
                                .clip(RoundedCornerShape(4.dp)),
                            contentScale = ContentScale.Crop,
                        )
                    }
                }
            }
        }
    }
}
