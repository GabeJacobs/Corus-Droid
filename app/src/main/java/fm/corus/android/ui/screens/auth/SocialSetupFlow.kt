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
import androidx.compose.ui.graphics.ColorFilter
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
import fm.corus.android.ui.theme.LocalCorusDarkTheme
import fm.corus.android.ui.util.PushNotificationPermission

// Order mirrors iOS: find friends → curate feed → choose player (last).
private enum class SetupStep { SYNC_CONTACTS, FOLLOW_FRIENDS, MUSIC_SERVICE, NOTIFICATIONS }

@Composable
fun SocialSetupFlow(
    onFinished: () -> Unit,
    viewModel: SocialSetupViewModel = hiltViewModel(),
) {
    // Read the flag once per flow entry so a mid-flow Remote Config activation
    // can't restructure the steps under the user's feet. Flag OFF is the
    // contacts → follow → player chain, plus the notification primer last.
    val tasteFlowEnabled = remember { viewModel.onboardingTasteMatchEnabled }
    if (tasteFlowEnabled) {
        TasteOnboardingFlow(onFinished = onFinished, viewModel = viewModel)
        return
    }

    var step by remember { mutableStateOf(SetupStep.SYNC_CONTACTS) }
    val context = LocalContext.current

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
                onContinue = { step = SetupStep.MUSIC_SERVICE },
            )
            // Choose Your Player was the final step; the notification primer
            // now sits after it so GET STARTED no longer fires a cold system
            // dialog.
            SetupStep.MUSIC_SERVICE -> MusicServiceScreen(
                viewModel = viewModel,
                onFinished = {
                    viewModel.applyPostOnboardingFeedDefault()
                    if (PushNotificationPermission.shouldRequestPushPermission(context)) {
                        step = SetupStep.NOTIFICATIONS
                    } else {
                        viewModel.markPushPermissionRequested()
                        onFinished()
                    }
                },
            )
            SetupStep.NOTIFICATIONS -> NotificationPermissionScreen(
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
internal fun MusicServiceScreen(
    viewModel: SocialSetupViewModel,
    onFinished: () -> Unit,
    // Flag-on taste flow moves this step to #2, where finishing language
    // would be wrong: the CTA reads CONTINUE. Defaults preserve the legacy
    // (flag-off) GET STARTED label. The system push dialog no longer fires
    // here — the notification primer is the real last step.
    ctaLabelRes: Int = R.string.music_service_get_started,
) {
    var selected by remember { mutableStateOf(MusicService.SPOTIFY) }
    val tidalEnabled = viewModel.tidalEnabled
    val youtubeMusicEnabled = viewModel.youtubeMusicEnabled
    val deezerEnabled = viewModel.deezerEnabled
    val fullPlaybackSubtitle = stringResource(R.string.music_service_full_playback)
    val context = LocalContext.current

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

        // Featured tier — Spotify + TIDAL as large side-by-side cards
        // (mirrors the iOS two-tier layout). Spotify shows the full-playback subtitle.
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(CorusSpacing.lg),
        ) {
            MusicServiceCard(
                modifier = Modifier.weight(1f),
                logoRes = R.drawable.spotify_logo,
                label = MusicService.SPOTIFY.displayLabel,
                subtitle = fullPlaybackSubtitle,
                accent = CorusColors.SpotifyGreen,
                selected = selected == MusicService.SPOTIFY,
                onClick = { selected = MusicService.SPOTIFY },
            )
            // TIDAL only appears when its Remote Config gate is on (and the
            // integration has shipped on all clients).
            if (tidalEnabled) {
                MusicServiceCard(
                    modifier = Modifier.weight(1f),
                    logoRes = R.drawable.tidal_logo,
                    label = MusicService.TIDAL.displayLabel,
                    accent = CorusColors.Tidal,
                    selected = selected == MusicService.TIDAL,
                    onClick = { selected = MusicService.TIDAL },
                )
            }
        }

        // Secondary tier — full-width list rows beneath the featured pair:
        // YouTube Music (below TIDAL, above Deezer), then Deezer, then Apple Music.
        // These link out to their own app.
        Spacer(modifier = Modifier.height(CorusSpacing.lg))
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(CorusSpacing.sm),
        ) {
            // YouTube Music only appears when its Remote Config gate is on.
            if (youtubeMusicEnabled) {
                MusicServiceRow(
                    logoRes = R.drawable.youtube_music_logo,
                    label = MusicService.YOUTUBE_MUSIC.displayLabel,
                    accent = CorusColors.YouTubeMusicRed,
                    selected = selected == MusicService.YOUTUBE_MUSIC,
                    onClick = { selected = MusicService.YOUTUBE_MUSIC },
                )
            }
            // Deezer only appears when its Remote Config gate is on.
            if (deezerEnabled) {
                MusicServiceRow(
                    logoRes = R.drawable.deezer_logo,
                    label = MusicService.DEEZER.displayLabel,
                    accent = CorusColors.DeezerPurple,
                    selected = selected == MusicService.DEEZER,
                    onClick = { selected = MusicService.DEEZER },
                )
            }
            MusicServiceRow(
                logoRes = R.drawable.apple_music_logo,
                label = MusicService.APPLE_MUSIC.displayLabel,
                accent = CorusColors.AppleMusicPink,
                selected = selected == MusicService.APPLE_MUSIC,
                onClick = { selected = MusicService.APPLE_MUSIC },
            )
        }

        Spacer(modifier = Modifier.weight(1f))

        Button(
            onClick = {
                viewModel.saveMusicService(
                    selected,
                    fm.corus.android.domain.SpotifyPlaybackService.isSpotifyAppInstalled(context),
                )
                onFinished()
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(CorusSpacing.touchTarget),
            colors = ButtonDefaults.buttonColors(containerColor = CorusColors.Accent),
        ) {
            Text(
                stringResource(id = ctaLabelRes),
                style = CorusFont.button,
                color = Color.White,
            )
        }

        // Secondary links always sit BELOW the primary (matches every other
        // onboarding step); lg + the ~40dp link = ONBOARDING_CTA_BOTTOM_ZONE.
        TextButton(onClick = {
            viewModel.saveMusicService(
                MusicService.SPOTIFY,
                fm.corus.android.domain.SpotifyPlaybackService.isSpotifyAppInstalled(context),
            )
            onFinished()
        }) {
            Text(
                stringResource(id = R.string.music_service_skip),
                style = CorusFont.caption,
                color = CorusColors.Tertiary,
            )
        }

        Spacer(modifier = Modifier.height(CorusSpacing.lg))
    }
}

@Composable
private fun MusicServiceCard(
    modifier: Modifier = Modifier,
    logoRes: Int,
    label: String,
    subtitle: String? = null,
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
            .padding(vertical = CorusSpacing.xxxl, horizontal = CorusSpacing.sm),
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
        // Always reserve two lines so cards with and without a subtitle
        // (Spotify vs TIDAL) stay the same height.
        Text(
            text = subtitle.orEmpty(),
            style = CorusFont.caption,
            color = CorusColors.Secondary,
            textAlign = TextAlign.Center,
            minLines = 2,
            maxLines = 2,
        )
    }
}

/**
 * Full-width row for the secondary tier (services that only link out to their
 * own app). Logo + name on the left, selection checkmark on the right — mirrors
 * the iOS `secondaryServiceRow`.
 */
@Composable
private fun MusicServiceRow(
    logoRes: Int,
    label: String,
    accent: Color,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(CorusSpacing.cornerRadiusMedium))
            .background(if (selected) accent.copy(alpha = 0.08f) else CorusColors.CardBackground)
            .border(
                width = if (selected) 2.dp else 1.dp,
                color = if (selected) accent else CorusColors.Divider,
                shape = RoundedCornerShape(CorusSpacing.cornerRadiusMedium),
            )
            .clickable(onClick = onClick)
            .padding(horizontal = CorusSpacing.lg, vertical = CorusSpacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Image(
            painter = painterResource(id = logoRes),
            contentDescription = label,
            modifier = Modifier.size(28.dp),
        )
        Spacer(modifier = Modifier.width(CorusSpacing.md))
        Text(
            label,
            style = CorusFont.bodyMedium,
            color = CorusColors.Text,
        )
        Spacer(modifier = Modifier.weight(1f))
        if (selected) {
            Icon(
                Icons.Filled.CheckCircle,
                contentDescription = null,
                tint = accent,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

// ═══════════════════════════════════════════════
// SYNC CONTACTS SCREEN
// ═══════════════════════════════════════════════

@Composable
internal fun SyncContactsScreen(
    viewModel: SocialSetupViewModel,
    onContinue: () -> Unit,
    // The taste flow retitles this page to the umbrella "Find People to Follow"
    // (contacts + taste quiz + suggestions are all one people-finding arc there).
    titleRes: Int = R.string.social_setup_find_friends_title,
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
            stringResource(id = titleRes),
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

        // lg + the ~40dp link above = ONBOARDING_CTA_BOTTOM_ZONE baseline.
        Spacer(modifier = Modifier.height(CorusSpacing.lg))
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

        // Center logo. Light mode keeps the native black logo; dark mode tints it accent
        // blue so it reads against the dark radar (the bare black logo is near-invisible there).
        Image(
            painter = painterResource(R.drawable.logo_no_background),
            contentDescription = stringResource(id = R.string.social_setup_cd_corus_logo),
            modifier = Modifier.size(64.dp),
            colorFilter = if (LocalCorusDarkTheme.current) {
                ColorFilter.tint(CorusColors.Accent)
            } else {
                null
            },
        )
    }
}

// ═══════════════════════════════════════════════
// FOLLOW FRIENDS SCREEN
// ═══════════════════════════════════════════════

@Composable
private fun FollowFriendsScreen(
    viewModel: SocialSetupViewModel,
    onContinue: () -> Unit,
) {
    // Curate Your Feed is a middle step now; just log completion and advance to
    // the music-service picker. The push prompt fires on that final step.
    val advanceToMusicService: () -> Unit = {
        viewModel.logFollowFriendsOnboardingCompleted()
        onContinue()
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
                onFinished = advanceToMusicService,
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
            //
            // When contact sync was skipped there's no friends section, so pass a
            // null topContent (no empty leading row) and zero header padding. The
            // "POPULAR ON CORUS" header then sits 16dp below the search bar (the
            // Spacer above) and 12dp above the first card (grid spacing) — matching
            // the iOS Curate Your Feed layout.
            val hasFriendsSection = contactMatches.isNotEmpty() || contactsSynced
            PopularUsersInfiniteGrid(
                excludeIds = emptySet(),
                followedIds = followedIds,
                onUserTap = { user -> viewModel.openUserPreview(user) },
                onFollowTap = { user -> viewModel.toggleFollow(user.id) },
                modifier = Modifier.weight(1f),
                headerVerticalPadding = 0.dp,
                topContent = if (!hasFriendsSection) null else {
                    {
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
                    }
                },
            )
        }

        // Breathing room so the scrolling grid doesn't butt against the button
        Spacer(modifier = Modifier.height(CorusSpacing.lg))

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
internal fun OnboardingSearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit = {},
    modifier: Modifier = Modifier,
    placeholderRes: Int = R.string.search_placeholder_users,
) {
    TextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(CorusSpacing.cornerRadiusMedium))
            .background(CorusColors.CardBackground)
            .border(1.dp, CorusColors.Divider, RoundedCornerShape(CorusSpacing.cornerRadiusMedium)),
        placeholder = { Text(stringResource(id = placeholderRes), style = CorusFont.body, color = CorusColors.Tertiary) },
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
internal fun OnboardingSectionHeader(
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
internal fun OnboardingUserRow(
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
            // Lead with the @username to match search/follow lists; the subtitle
            // (or display name) sits muted below.
            Text(
                "@${user.username}",
                style = CorusFont.bodyMedium,
                color = CorusColors.Text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                subtitle ?: user.displayName,
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
internal fun SkeletonUserRow() {
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

