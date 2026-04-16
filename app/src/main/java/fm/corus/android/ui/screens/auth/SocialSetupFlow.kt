package fm.corus.android.ui.screens.auth

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
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
import fm.corus.android.ui.components.SkeletonSectionHeader
import fm.corus.android.ui.components.TasteMatchCard
import fm.corus.android.ui.components.UserAvatarView
import fm.corus.android.ui.components.UsernameWithFlair
import fm.corus.android.ui.theme.CorusColors
import fm.corus.android.ui.theme.CorusFont
import fm.corus.android.ui.theme.CorusSpacing

private enum class SetupStep { SYNC_CONTACTS, FOLLOW_FRIENDS, MUSIC_SERVICE }

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
                onFinished = { step = SetupStep.MUSIC_SERVICE },
            )
            SetupStep.MUSIC_SERVICE -> MusicServiceScreen(
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
            "Find Your Friends",
            style = CorusFont.appTitle,
            color = CorusColors.Text,
        )
        Spacer(modifier = Modifier.height(CorusSpacing.sm))
        Text(
            "see who's already on Corus",
            style = CorusFont.body,
            color = CorusColors.Secondary,
        )

        Spacer(modifier = Modifier.weight(1f))

        // Animated radar illustration + description — matches iOS layout
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            RadarAnimation()

            Spacer(modifier = Modifier.height(CorusSpacing.xxl))

            Text(
                "Sync your contacts to discover\nfriends who might be on Corus.",
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
                Text("SYNC CONTACTS", style = CorusFont.button, color = Color.White)
            }
        }

        TextButton(onClick = onContinue) {
            Text("sync later", style = CorusFont.caption, color = CorusColors.Tertiary)
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
    val radarDuration = 2400L   // ms — ring expansion
    val pauseDuration = 800L    // ms — hold after last avatar

    // Phase state drives the sequential highlight: 0 = reset, 1 = ring expanding,
    // 2..6 = avatars 0..4 highlighted one-by-one (matching iOS radarPhase logic).
    var radarPhase by remember { mutableIntStateOf(0) }

    LaunchedEffect(Unit) {
        while (true) {
            radarPhase = 0
            kotlinx.coroutines.delay(100)

            radarPhase = 1  // start ring expansion

            // Highlight each avatar sequentially as ring passes it
            for (i in 0 until avatarCount) {
                val delayMs = (580 + i * 50).toLong()
                kotlinx.coroutines.delay(delayMs)
                radarPhase = 2 + i
            }

            kotlinx.coroutines.delay(pauseDuration)
        }
    }

    // Ring expansion driven by radarPhase > 0
    val ringSize by animateFloatAsState(
        targetValue = if (radarPhase > 0) 220f else 20f,
        animationSpec = tween(
            durationMillis = if (radarPhase > 0) radarDuration.toInt() else 0,
            easing = FastOutSlowInEasing,
        ),
        label = "ring-size",
    )
    val ringAlpha by animateFloatAsState(
        targetValue = if (radarPhase > 0) 0f else 0.25f,
        animationSpec = tween(
            durationMillis = if (radarPhase > 0) radarDuration.toInt() else 0,
        ),
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

        // Avatar placeholders at 72° intervals — highlight sequentially (matching iOS)
        for (i in 0 until avatarCount) {
            val angle = i * (2.0 * Math.PI / avatarCount) - Math.PI / 2
            val isHighlighted = radarPhase >= 2 + i

            val avatarScale by animateFloatAsState(
                targetValue = if (isHighlighted) 1.15f else 1f,
                animationSpec = tween(300),
                label = "avatar-scale-$i",
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
                    .background(CorusColors.Accent.copy(alpha = if (isHighlighted) 0.5f else 0.2f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Filled.Person,
                    contentDescription = null,
                    tint = CorusColors.Accent.copy(alpha = if (isHighlighted) 1f else 0.6f),
                    modifier = Modifier.size(18.dp),
                )
            }
        }

        // Center logo — matches iOS: actual logo image
        Image(
            painter = painterResource(R.drawable.logo_no_background),
            contentDescription = "Corus",
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
    val contactMatches by viewModel.contactMatches.collectAsState()
    val popularUsers by viewModel.popularUsers.collectAsState()
    val musicBots by viewModel.musicBotMatches.collectAsState()
    val filmBots by viewModel.filmBotMatches.collectAsState()
    val isLoading by viewModel.isLoadingSuggestions.collectAsState()
    val followedIds by viewModel.followedIds.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val searchResults by viewModel.searchResults.collectAsState()
    val isSearching by viewModel.isSearching.collectAsState()
    val isFinishing by viewModel.isFinishing.collectAsState()

    var showSeeAll by remember { mutableStateOf<SeeAllDestination?>(null) }
    var filmBotPreviewMatch by remember { mutableStateOf<SuggestedUserMatch?>(null) }

    if (showSeeAll != null) {
        OnboardingSeeAllScreen(
            destination = showSeeAll!!,
            contactMatches = contactMatches,
            popularUsers = popularUsers,
            musicBots = musicBots,
            filmBots = filmBots,
            followedIds = followedIds,
            onFollow = { viewModel.toggleFollow(it) },
            onFilmBotTap = { userId -> filmBotPreviewMatch = filmBots.find { it.user.id == userId } },
            onBack = { showSeeAll = null },
        )
        if (filmBotPreviewMatch != null) {
            FilmBotPreviewSheet(
                match = filmBotPreviewMatch!!,
                isFollowed = followedIds.contains(filmBotPreviewMatch!!.user.id),
                onFollow = { viewModel.toggleFollow(filmBotPreviewMatch!!.user.id) },
                onDismiss = { filmBotPreviewMatch = null },
                viewModel = viewModel,
            )
        }
        return
    }

    Column(modifier = Modifier.fillMaxSize().navigationBarsPadding()) {
        Spacer(modifier = Modifier.height(60.dp))

        // Header
        Column(
            modifier = Modifier.padding(horizontal = CorusSpacing.xxl),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                "Curate Your Feed",
                style = CorusFont.appTitle,
                color = CorusColors.Text,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
            )
            Spacer(modifier = Modifier.height(CorusSpacing.sm))
            Text(
                "follow users to build your feed",
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
                            Text("No users found", style = CorusFont.bodyMedium, color = CorusColors.Secondary)
                        }
                    }
                } else {
                    items(searchResults, key = { it.id }) { user ->
                        OnboardingUserRow(
                            user = user,
                            isFollowed = followedIds.contains(user.id),
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
                                title = "\uD83D\uDC65 Friends on Corus",
                                showSeeAll = contactMatches.size > 5,
                                onSeeAll = { showSeeAll = SeeAllDestination.FRIENDS },
                            )
                        }
                        items(contactMatches.take(5), key = { "contact-${it.id}" }) { user ->
                            OnboardingUserRow(
                                user = user,
                                subtitle = "From your contacts",
                                isFollowed = followedIds.contains(user.id),
                                onFollow = { viewModel.toggleFollow(user.id) },
                                onTap = { viewModel.playUserPreview(user.id) },
                            )
                        }
                    } else {
                        // Friends empty state — matches iOS
                        item {
                            OnboardingSectionHeader(
                                title = "\uD83D\uDC65 Friends on Corus",
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
                                    "None of your contacts are on Corus yet",
                                    style = CorusFont.bodyMedium,
                                    color = CorusColors.Secondary,
                                )
                                Spacer(modifier = Modifier.height(CorusSpacing.xs))
                                Text(
                                    "We'll notify you when they join",
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
                            title = "\uD83D\uDD25 Popular on Corus",
                            showSeeAll = popularUsers.size > 4,
                            onSeeAll = { showSeeAll = SeeAllDestination.POPULAR },
                        )
                    }
                    items(popularUsers.take(4), key = { "popular-${it.id}" }) { user ->
                        OnboardingUserRow(
                            user = user,
                            subtitle = "${user.followerCount} followers",
                            isFollowed = followedIds.contains(user.id),
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
                            "or follow some curated music bots",
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
                        )
                        if (musicBots.size > 6) {
                            TextButton(
                                onClick = { showSeeAll = SeeAllDestination.MUSIC_BOTS },
                                modifier = Modifier.padding(start = CorusSpacing.xxl),
                            ) {
                                Text("See all", style = CorusFont.captionMedium, color = CorusColors.Accent)
                            }
                        }
                    }
                }

                // Film Bots
                if (filmBots.isNotEmpty() || isLoading) {
                    item {
                        Spacer(modifier = Modifier.height(CorusSpacing.lg))
                        Text(
                            "or some curated film bots",
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
                            onUserTap = { userId -> filmBotPreviewMatch = filmBots.find { it.user.id == userId } },
                        )
                        if (filmBots.size > 6) {
                            TextButton(
                                onClick = { showSeeAll = SeeAllDestination.FILM_BOTS },
                                modifier = Modifier.padding(start = CorusSpacing.xxl),
                            ) {
                                Text("See all", style = CorusFont.captionMedium, color = CorusColors.Accent)
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
                Text("CONTINUE", style = CorusFont.button, color = Color.White)
            }
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

// ═══════════════════════════════════════════════
// MUSIC SERVICE SELECTION SCREEN
// ═══════════════════════════════════════════════

@Composable
private fun MusicServiceScreen(
    viewModel: SocialSetupViewModel,
    onFinished: () -> Unit,
) {
    var selectedService by remember { mutableStateOf(MusicService.SPOTIFY) }
    var isFinishing by remember { mutableStateOf(false) }

    // Request notification permission on this screen (matching iOS flow)
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { _ ->
        // Proceed regardless of permission result
        isFinishing = false
        onFinished()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .navigationBarsPadding()
            .padding(horizontal = CorusSpacing.xxl),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(modifier = Modifier.height(60.dp))

        // Header — matches iOS
        Text(
            "Choose Your Player",
            style = CorusFont.appTitle,
            color = CorusColors.Text,
        )
        Spacer(modifier = Modifier.height(CorusSpacing.sm))
        Text(
            "pick your preferred music service",
            style = CorusFont.bodyMedium,
            color = CorusColors.Secondary,
        )

        Spacer(modifier = Modifier.weight(1f))

        // Service selection cards — matches iOS: 2 cards side by side
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(CorusSpacing.lg),
        ) {
            // Spotify card
            MusicServiceCard(
                label = "Spotify",
                logoResId = R.drawable.spotify_logo,
                isSelected = selectedService == MusicService.SPOTIFY,
                selectedColor = CorusColors.SpotifyGreen,
                onClick = { selectedService = MusicService.SPOTIFY },
                modifier = Modifier.weight(1f),
            )

            // Apple Music card
            MusicServiceCard(
                label = "Apple Music",
                logoResId = R.drawable.apple_music_logo,
                isSelected = selectedService == MusicService.APPLE_MUSIC,
                selectedColor = CorusColors.AppleMusicPink,
                onClick = { selectedService = MusicService.APPLE_MUSIC },
                modifier = Modifier.weight(1f),
            )
        }

        Spacer(modifier = Modifier.height(CorusSpacing.xxl))

        Text(
            "Stay tuned for support for more streaming services.",
            style = CorusFont.caption,
            color = CorusColors.Secondary,
            textAlign = TextAlign.Center,
        )

        Spacer(modifier = Modifier.weight(1f))

        // Skip — defaults to Spotify (matching iOS)
        TextButton(onClick = {
            viewModel.saveMusicService(MusicService.SPOTIFY)
            finishWithNotification(
                notificationPermissionLauncher,
                onFinished,
                setFinishing = { isFinishing = it },
            )
        }) {
            Text("Skip", style = CorusFont.captionMedium, color = CorusColors.Secondary)
        }

        // GET STARTED button
        Button(
            onClick = {
                viewModel.saveMusicService(selectedService)
                finishWithNotification(
                    notificationPermissionLauncher,
                    onFinished,
                    setFinishing = { isFinishing = it },
                )
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = CorusSpacing.xxxl),
            colors = ButtonDefaults.buttonColors(containerColor = CorusColors.Accent),
            enabled = !isFinishing,
            contentPadding = PaddingValues(vertical = CorusSpacing.lg),
            shape = RoundedCornerShape(CorusSpacing.cornerRadiusMedium),
        ) {
            if (isFinishing) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
            } else {
                Text("GET STARTED", style = CorusFont.button, color = Color.White)
            }
        }
    }
}

private fun finishWithNotification(
    launcher: androidx.activity.result.ActivityResultLauncher<String>,
    onFinished: () -> Unit,
    setFinishing: (Boolean) -> Unit,
) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        setFinishing(true)
        launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
    } else {
        onFinished()
    }
}

@Composable
private fun MusicServiceCard(
    label: String,
    logoResId: Int,
    isSelected: Boolean,
    selectedColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val bgColor = if (isSelected) selectedColor.copy(alpha = 0.08f) else CorusColors.CardBackground
    val borderColor = if (isSelected) selectedColor else CorusColors.Divider
    val borderWidth = if (isSelected) 2.dp else 1.dp

    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(CorusSpacing.cornerRadiusLarge),
        color = bgColor,
        border = androidx.compose.foundation.BorderStroke(borderWidth, borderColor),
    ) {
        Box {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = CorusSpacing.xxl),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Image(
                    painter = painterResource(id = logoResId),
                    contentDescription = label,
                    modifier = Modifier.size(48.dp),
                )
                Spacer(modifier = Modifier.height(CorusSpacing.md))
                Text(
                    label,
                    style = CorusFont.bodyMedium,
                    color = CorusColors.Text,
                )
            }

            // Checkmark overlay — top-right when selected
            if (isSelected) {
                Icon(
                    Icons.Filled.CheckCircle,
                    contentDescription = "Selected",
                    tint = selectedColor,
                    modifier = Modifier
                        .size(22.dp)
                        .align(Alignment.TopEnd)
                        .offset(x = (-8).dp, y = 8.dp),
                )
            }
        }
    }
}

// ═══════════════════════════════════════════════
// SEE ALL SCREEN
// ═══════════════════════════════════════════════

enum class SeeAllDestination(val title: String) {
    FRIENDS("Friends on Corus"),
    POPULAR("Popular on Corus"),
    MUSIC_BOTS("Curated Music Bots"),
    FILM_BOTS("Curated Film Bots"),
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
    onFollow: (String) -> Unit,
    onFilmBotTap: (String) -> Unit,
    onBack: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(destination.title, style = CorusFont.screenTitle, color = CorusColors.Text) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.Close, contentDescription = "Back", tint = CorusColors.Text)
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
                            subtitle = "From your contacts",
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
                            subtitle = "${user.followerCount} followers",
                            isFollowed = followedIds.contains(user.id),
                            onFollow = { onFollow(user.id) },
                        )
                    }
                }
            }
            SeeAllDestination.MUSIC_BOTS, SeeAllDestination.FILM_BOTS -> {
                val bots = if (destination == SeeAllDestination.MUSIC_BOTS) musicBots else filmBots
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
                            onUserTap = { if (destination == SeeAllDestination.FILM_BOTS) onFilmBotTap(match.user.id) },
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
        placeholder = { Text("Search by username", style = CorusFont.body, color = CorusColors.Tertiary) },
        leadingIcon = {
            Icon(Icons.Filled.Search, contentDescription = null, tint = CorusColors.Tertiary)
        },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = { onQueryChange("") }) {
                    Icon(Icons.Filled.Close, contentDescription = "Clear", tint = CorusColors.Tertiary)
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
            colors = ButtonDefaults.buttonColors(
                containerColor = if (isFollowed) CorusColors.CardBackground else CorusColors.Accent,
                contentColor = if (isFollowed) CorusColors.Text else Color.White,
            ),
            contentPadding = PaddingValues(horizontal = CorusSpacing.lg, vertical = CorusSpacing.xs),
            shape = RoundedCornerShape(50),
        ) {
            Text(
                if (isFollowed) "Following" else "Follow",
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
                            TasteMatchCard(
                                match = bots[idx],
                                isFollowing = followedIds.contains(bots[idx].user.id),
                                onFollowTap = { onFollow(bots[idx].user.id) },
                                onUserTap = { onUserTap(bots[idx].user.id) },
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
                Text(if (isFollowed) "Following" else "Follow", style = CorusFont.buttonSmall)
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
                    "No films yet",
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
