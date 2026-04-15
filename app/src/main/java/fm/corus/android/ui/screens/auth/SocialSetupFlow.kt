package fm.corus.android.ui.screens.auth

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import coil3.compose.AsyncImage
import fm.corus.android.data.model.CymbalUser
import fm.corus.android.data.model.SuggestedUserMatch
import fm.corus.android.ui.components.TasteMatchCard
import fm.corus.android.ui.components.UserAvatarView
import fm.corus.android.ui.components.UsernameWithFlair
import fm.corus.android.ui.theme.CorusColors
import fm.corus.android.ui.theme.CorusFont
import fm.corus.android.ui.theme.CorusSpacing

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
        }
        // Navigate forward regardless
        onContinue()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = CorusSpacing.xxl),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(modifier = Modifier.height(60.dp))

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

        // Radar illustration placeholder
        Box(
            modifier = Modifier.size(240.dp),
            contentAlignment = Alignment.Center,
        ) {
            // Outer ring
            Box(
                modifier = Modifier
                    .size(210.dp)
                    .clip(CircleShape)
                    .background(CorusColors.Accent.copy(alpha = 0.08f)),
            )
            // Inner ring
            Box(
                modifier = Modifier
                    .size(140.dp)
                    .clip(CircleShape)
                    .background(CorusColors.Accent.copy(alpha = 0.12f)),
            )
            // Center avatar placeholders
            val angles = listOf(0f, 72f, 144f, 216f, 288f)
            angles.forEachIndexed { index, angle ->
                val radians = Math.toRadians(angle.toDouble())
                val radius = 72.dp
                Box(
                    modifier = Modifier
                        .offset(
                            x = (radius.value * kotlin.math.cos(radians)).dp,
                            y = (radius.value * kotlin.math.sin(radians)).dp,
                        )
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(CorusColors.Accent.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Filled.Person,
                        contentDescription = null,
                        tint = CorusColors.Accent,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
            // Center logo
            Text(
                "c",
                style = CorusFont.appTitle,
                color = CorusColors.Accent,
            )
        }

        Spacer(modifier = Modifier.weight(1f))

        Text(
            "Sync your contacts to discover friends who might be on Corus.",
            style = CorusFont.bodyMedium,
            color = CorusColors.Secondary,
            textAlign = TextAlign.Center,
        )

        Spacer(modifier = Modifier.height(CorusSpacing.xxl))

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
                Text("SYNC CONTACTS", style = CorusFont.button, color = Color.White)
            }
        }

        TextButton(onClick = onContinue) {
            Text("sync later", style = CorusFont.caption, color = CorusColors.Tertiary)
        }

        Spacer(modifier = Modifier.height(CorusSpacing.xxxl))
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

    Column(modifier = Modifier.fillMaxSize()) {
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

                // Friends on Corus
                if (contactMatches.isNotEmpty()) {
                    item {
                        OnboardingSectionHeader(
                            title = "Friends on Corus",
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
                }

                // Popular on Corus
                item {
                    OnboardingSectionHeader(
                        title = "Popular on Corus",
                        showSeeAll = popularUsers.size > 3,
                        onSeeAll = { showSeeAll = SeeAllDestination.POPULAR },
                    )
                }
                if (isLoading) {
                    items(3) { SkeletonUserRow() }
                } else {
                    items(popularUsers.take(3), key = { "popular-${it.id}" }) { user ->
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

        // Get Started button
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
                Text("GET STARTED", style = CorusFont.button, color = Color.White)
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
