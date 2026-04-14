package fm.corus.android.ui.screens.profile

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import com.valentinilk.shimmer.shimmer
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
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.ui.draw.alpha
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.size.Size
import fm.corus.android.data.model.CymbalPost
import fm.corus.android.data.model.CymbalUser
import fm.corus.android.data.model.MediaType
import fm.corus.android.ui.components.FullScreenAvatarOverlay
import fm.corus.android.ui.components.ShimmerAsyncImage
import fm.corus.android.ui.components.ToastManager
import fm.corus.android.ui.components.UserAvatarView
import fm.corus.android.ui.components.UsernameWithFlair
import fm.corus.android.ui.theme.CorusColors
import fm.corus.android.ui.theme.CorusFont
import fm.corus.android.ui.theme.CorusSpacing
import java.io.File

@Composable
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
fun ProfileScreen(
    viewModel: ProfileViewModel = hiltViewModel(),
    scrollToTopTrigger: Int = 0,
    onNavigateToSettings: () -> Unit = {},
    onNavigateToEditProfile: (String) -> Unit = {},
    onNavigateToFollowList: (String, Boolean) -> Unit = { _, _ -> },
    onNavigateToPost: (String) -> Unit = {},
    onNavigateToClub: () -> Unit = {},
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val profile by viewModel.profile.collectAsState()
    val posts by viewModel.posts.collectAsState()
    val likedPosts by viewModel.likedPosts.collectAsState()
    val savedPosts by viewModel.savedPosts.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val isLoadingLiked by viewModel.isLoadingLiked.collectAsState()
    val isLoadingSaved by viewModel.isLoadingSaved.collectAsState()
    val isClubMember by viewModel.isClubMember.collectAsState()
    val hasFullAccess by viewModel.hasFullAccess.collectAsState()
    val isSavingStyle by viewModel.isSavingStyle.collectAsState()
    var selectedSegment by remember { mutableIntStateOf(0) }
    var showStylePicker by remember { mutableStateOf(false) }
    var showClubOffer by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val clubSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    // Avatar context menu state
    var showAvatarMenu by remember { mutableStateOf(false) }
    var showFullScreenAvatar by remember { mutableStateOf(false) }

    // Camera photo URI
    val cameraPhotoUri = remember {
        val photoFile = File(context.cacheDir, "camera_avatar.jpg")
        FileProvider.getUriForFile(context, "${context.packageName}.provider", photoFile)
    }

    // Camera launcher
    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        if (success) {
            try {
                val inputStream = context.contentResolver.openInputStream(cameraPhotoUri)
                val bytes = inputStream?.readBytes()
                inputStream?.close()
                if (bytes != null) {
                    viewModel.uploadAvatar(bytes)
                    ToastManager.show("Avatar updated!")
                }
            } catch (_: Exception) { }
        }
    }

    // Photo picker launcher
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            try {
                val inputStream = context.contentResolver.openInputStream(uri)
                val bytes = inputStream?.readBytes()
                inputStream?.close()
                if (bytes != null) {
                    viewModel.uploadAvatar(bytes)
                    ToastManager.show("Avatar updated!")
                }
            } catch (_: Exception) { }
        }
    }

    LaunchedEffect(Unit) {
        viewModel.loadProfile()
    }

    if (isLoading && profile == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = CorusColors.Accent)
        }
        return
    }

    val currentProfile = profile ?: return

    val gridState = rememberLazyGridState()
    val isLoadingMore by viewModel.isLoadingMore.collectAsState()
    val hasMore by viewModel.hasMore.collectAsState()

    var lastScrollTrigger by rememberSaveable { mutableIntStateOf(0) }
    LaunchedEffect(scrollToTopTrigger) {
        if (scrollToTopTrigger > lastScrollTrigger) {
            gridState.animateScrollToItem(0)
            lastScrollTrigger = scrollToTopTrigger
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

    LazyVerticalGrid(
        state = gridState,
        columns = GridCells.Fixed(3),
        modifier = Modifier.fillMaxSize(),
    ) {
        // All header content spans full width
        item(span = { GridItemSpan(3) }) {
            Column {
                // ── Header Row: icon / display name / settings ──
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = CorusSpacing.lg, vertical = CorusSpacing.md),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    // Profile customization icon (matching iOS CorusClub icon)
                    if (posts.isNotEmpty()) {
                        Icon(
                            painter = painterResource(fm.corus.android.R.drawable.corus_club_vector),
                            contentDescription = "Customize Profile",
                            tint = CorusColors.Accent,
                            modifier = Modifier
                                .size(40.dp)
                                .clickable { showStylePicker = true },
                        )
                    } else {
                        Spacer(modifier = Modifier.size(40.dp))
                    }

                    Text(
                        text = currentProfile.displayName,
                        style = CorusFont.displayName,
                        color = CorusColors.Text,
                    )

                    Icon(
                        Icons.Filled.Settings,
                        contentDescription = "Settings",
                        tint = CorusColors.Secondary,
                        modifier = Modifier
                            .size(24.dp)
                            .clickable(onClick = onNavigateToSettings),
                    )
                }

                // ── Avatar + Stats Row ──
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = CorusSpacing.lg),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // Large circular avatar with long-press context menu
                    Box {
                        UserAvatarView(
                            avatarURL = currentProfile.avatarURL,
                            size = CorusSpacing.avatarLarge,
                            modifier = Modifier.combinedClickable(
                                onClick = { },
                                onLongClick = { showAvatarMenu = true },
                            ),
                        )

                        DropdownMenu(
                            expanded = showAvatarMenu,
                            onDismissRequest = { showAvatarMenu = false },
                            containerColor = CorusColors.Background,
                        ) {
                            DropdownMenuItem(
                                text = { Text("Take Photo", style = CorusFont.body, color = CorusColors.Text) },
                                onClick = {
                                    showAvatarMenu = false
                                    cameraLauncher.launch(cameraPhotoUri)
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("Choose from Library", style = CorusFont.body, color = CorusColors.Text) },
                                onClick = {
                                    showAvatarMenu = false
                                    photoPickerLauncher.launch(
                                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                    )
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("View Photo", style = CorusFont.body, color = CorusColors.Text) },
                                onClick = {
                                    showAvatarMenu = false
                                    showFullScreenAvatar = true
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("Share Profile Link", style = CorusFont.body, color = CorusColors.Text) },
                                onClick = {
                                    showAvatarMenu = false
                                    val link = "https://corus.fm/u/${currentProfile.username}"
                                    clipboardManager.setText(AnnotatedString(link))
                                    ToastManager.show("Profile link copied!")
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
                            StatItem(count = currentProfile.cymbalCount, label = "coruses")
                            StatItem(
                                count = currentProfile.followerCount,
                                label = "followers",
                                onClick = { onNavigateToFollowList(currentProfile.id, false) },
                            )
                            StatItem(
                                count = currentProfile.followingCount,
                                label = "following",
                                onClick = { onNavigateToFollowList(currentProfile.id, true) },
                            )
                        }

                        Spacer(modifier = Modifier.height(CorusSpacing.sm))

                        // Edit Profile button + playlist + vinyl picker
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            // Edit Profile — matching iOS Capsule with stroke border
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(50))
                                    .border(1.dp, CorusColors.Divider, RoundedCornerShape(50))
                                    .clickable { onNavigateToEditProfile(currentProfile.id) }
                                    .padding(vertical = CorusSpacing.sm - 2.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = "EDIT PROFILE",
                                    style = CorusFont.button,
                                    color = CorusColors.Secondary,
                                )
                            }

                            Spacer(modifier = Modifier.width(CorusSpacing.sm))

                            // Playlist button (matching iOS music.note.list)
                            val hasSongs = posts.any { it.mediaType == MediaType.TRACK }
                            val isGeneratingPlaylist by viewModel.nowPlayingManager.isGeneratingPlaylist.collectAsState()
                            val playlistError by viewModel.nowPlayingManager.playlistError.collectAsState()

                            LaunchedEffect(playlistError) {
                                if (playlistError != null) {
                                    ToastManager.show(playlistError!!)
                                    viewModel.nowPlayingManager.clearPlaylistError()
                                }
                            }

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
                                    .clickable(enabled = hasSongs && !isGeneratingPlaylist) {
                                        if (hasSongs) {
                                            viewModel.generatePlaylist()
                                        } else {
                                            ToastManager.show("No songs to make a playlist")
                                        }
                                    }
                                    .padding(horizontal = CorusSpacing.md),
                                contentAlignment = Alignment.Center,
                            ) {
                                if (isGeneratingPlaylist) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(14.dp),
                                        strokeWidth = 2.dp,
                                        color = CorusColors.Secondary,
                                    )
                                } else {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(CorusSpacing.xs),
                                        modifier = Modifier.alpha(if (hasSongs) 1f else 0.35f),
                                    ) {
                                        Icon(
                                            painter = painterResource(fm.corus.android.R.drawable.ic_music_note_list),
                                            contentDescription = "Playlist",
                                            modifier = Modifier.size(14.dp),
                                            tint = CorusColors.Secondary,
                                        )
                                        Text(
                                            text = "PLAYLIST",
                                            style = CorusFont.button,
                                            color = CorusColors.Secondary,
                                        )
                                    }
                                }
                            }

                        }
                    }
                }

                Spacer(modifier = Modifier.height(CorusSpacing.md))

                // ── Username + Bio + Website ──
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 28.dp, end = CorusSpacing.lg),
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
                        Text(
                            text = currentProfile.bio,
                            style = CorusFont.bio,
                            color = CorusColors.Secondary,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }

                    // Website
                    if (!currentProfile.website.isNullOrBlank()) {
                        Spacer(modifier = Modifier.height(CorusSpacing.xs))
                        val context = androidx.compose.ui.platform.LocalContext.current
                        Text(
                            text = currentProfile.website!!.removePrefix("https://").removePrefix("http://"),
                            style = CorusFont.caption,
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

                // ── Segment Control ──
                val tabs = listOf("MUSIC", "FILM", "LIKES", "SAVES")
                Row(
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    tabs.forEachIndexed { index, title ->
                        val isSelected = selectedSegment == index
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .clickable {
                                    selectedSegment = index
                                    viewModel.loadSegment(index)
                                }
                                .drawBehind {
                                    val strokeWidth = if (isSelected) 3.dp.toPx() else 0.5.dp.toPx()
                                    val lineColor = if (isSelected) CorusColors.Text else CorusColors.Divider
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
                            Text(
                                text = title,
                                style = CorusFont.tabLabel,
                                color = if (isSelected) CorusColors.Text else CorusColors.Tertiary,
                            )
                        }
                    }
                }

                // Filter posts by segment
                val filteredPosts = when (selectedSegment) {
                    0 -> posts.filter { it.mediaType == fm.corus.android.data.model.MediaType.TRACK }
                    1 -> posts.filter { it.mediaType == fm.corus.android.data.model.MediaType.MOVIE }
                    2 -> likedPosts
                    3 -> savedPosts
                    else -> posts
                }

                // ── Featured Post — only for Music/Film tabs (matching iOS) ──
                if (filteredPosts.isNotEmpty() && selectedSegment <= 1) {
                    val featuredPost = filteredPosts.first()
                    if (featuredPost.mediaType == fm.corus.android.data.model.MediaType.MOVIE) {
                        fm.corus.android.ui.components.FeaturedMoviePosterView(
                            post = featuredPost,
                            frameStyle = currentProfile.frameStyle,
                            rainIntensity = currentProfile.rainIntensity,
                            snowIntensity = currentProfile.snowIntensity,
                            discoIntensity = currentProfile.discoIntensityLevel,
                            onPostTap = { onNavigateToPost(featuredPost.id) },
                        )
                    } else {
                        fm.corus.android.ui.components.FeaturedCymbalView(
                            post = featuredPost,
                            vinylStyle = currentProfile.vinylStyle,
                            rainIntensity = currentProfile.rainIntensity,
                            snowIntensity = currentProfile.snowIntensity,
                            discoIntensity = currentProfile.discoIntensityLevel,
                            onPostTap = { onNavigateToPost(featuredPost.id) },
                        )
                    }
                } else if (filteredPosts.isEmpty() && !isLoading
                    && !(selectedSegment == 2 && isLoadingLiked)
                    && !(selectedSegment == 3 && isLoadingSaved)
                ) {
                    // Empty state per segment (matching iOS)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        val (icon, message, subtitle) = when (selectedSegment) {
                            0 -> Triple("🎵", "What are you listening to?", "Post your first song")
                            1 -> Triple("🎬", "Watch anything good lately?", "Post your first film")
                            2 -> Triple("❤️", "No liked posts yet", null)
                            else -> Triple("🔖", "No saved posts yet", null)
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(icon, fontSize = 32.sp)
                            Spacer(modifier = Modifier.height(CorusSpacing.sm))
                            Text(message, style = CorusFont.bodyMedium, color = CorusColors.Secondary)
                            if (subtitle != null) {
                                Spacer(modifier = Modifier.height(CorusSpacing.xs))
                                Text(subtitle, style = CorusFont.caption, color = CorusColors.Tertiary)
                            }
                        }
                    }
                }
            }
        }

        // ── Album Art Grid (filtered) ──
        val isSegmentLoading = (selectedSegment == 2 && isLoadingLiked && likedPosts.isEmpty())
            || (selectedSegment == 3 && isLoadingSaved && savedPosts.isEmpty())

        if (isSegmentLoading) {
            // Skeleton grid cells while likes/saves load (matching iOS)
            items(15) {
                Box(
                    modifier = Modifier
                        .aspectRatio(1f)
                        .background(CorusColors.CardBackground)
                        .shimmer(),
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
            // for Music/Film, skip the first post (already shown as featured)
            val gridPosts = if (selectedSegment <= 1) filteredPosts.drop(1) else filteredPosts
            if (gridPosts.isNotEmpty()) {
                items(gridPosts, key = { it.id }) { post ->
                    PostGridItem(post = post, onClick = { onNavigateToPost(post.id) })
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
            StylePickerSheet(
                currentSelections = StyleSelections(
                    vinylColor = currentProfile.vinylStyle,
                    vinylSpinning = currentProfile.vinylSpinning,
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
                isSaving = isSavingStyle,
                onSave = { selections ->
                    val current = StyleSelections(
                        vinylColor = currentProfile.vinylStyle,
                        vinylSpinning = currentProfile.vinylSpinning,
                        frameColor = currentProfile.frameStyle,
                        profileFlair = currentProfile.flairStyle,
                        rainEffect = currentProfile.rainIntensity,
                        snowEffect = currentProfile.snowIntensity,
                        discoEffect = currentProfile.discoIntensityLevel,
                    )
                    val fields = selections.changedFields(current)
                    if (fields.isNotEmpty()) {
                        viewModel.saveStyleSelections(fields)
                        ToastManager.show("Style updated!")
                    }
                    showStylePicker = false
                },
                onNavigateToClub = {
                    showStylePicker = false
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
            fm.corus.android.ui.screens.subscription.CymbalClubOfferSheet(
                onDismiss = { showClubOffer = false },
            )
        }
    }

    // ── Full Screen Avatar Overlay ──
    FullScreenAvatarOverlay(
        avatarURL = currentProfile.avatarURL,
        visible = showFullScreenAvatar,
        onDismiss = { showFullScreenAvatar = false },
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

private fun formattedCount(count: Int): String {
    return when {
        count >= 1_000_000 -> {
            val s = String.format("%.1f", count / 1_000_000.0)
            if (s.endsWith(".0")) "${s.dropLast(2)}M" else "${s}M"
        }
        count >= 10_000 -> "${count / 1000}K"
        count >= 1_000 -> {
            val s = String.format("%.1f", count / 1000.0)
            if (s.endsWith(".0")) "${s.dropLast(2)}K" else "${s}K"
        }
        else -> count.toString()
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
private fun PostGridItem(post: CymbalPost, onClick: () -> Unit = {}) {
    ShimmerAsyncImage(
        model = ImageRequest.Builder(androidx.compose.ui.platform.LocalContext.current)
            .data(post.displayImageLargeURL ?: post.displayImageURL)
            .size(Size.ORIGINAL)
            .build(),
        contentDescription = post.displayTitle,
        modifier = Modifier
            .aspectRatio(1f)
            .clickable(onClick = onClick),
    )
}
