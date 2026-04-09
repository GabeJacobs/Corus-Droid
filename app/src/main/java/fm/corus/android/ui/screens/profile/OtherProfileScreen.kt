package fm.corus.android.ui.screens.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import coil3.compose.AsyncImage
import fm.corus.android.data.model.CymbalPost
import fm.corus.android.data.model.CymbalUser
import fm.corus.android.data.model.MediaType
import fm.corus.android.ui.components.FeaturedCymbalView
import fm.corus.android.ui.components.FeaturedMoviePosterView
import fm.corus.android.ui.components.ToastManager
import fm.corus.android.ui.components.UserAvatarView
import fm.corus.android.ui.components.UsernameWithFlair
import fm.corus.android.ui.theme.CorusColors
import fm.corus.android.ui.theme.CorusFont
import fm.corus.android.ui.theme.CorusSpacing

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OtherProfileScreen(
    userId: String,
    viewModel: OtherProfileViewModel = hiltViewModel(),
    onBack: () -> Unit = {},
    onNavigateToPost: (String) -> Unit = {},
    onNavigateToUser: (String) -> Unit = {},
    onNavigateToFollowList: (String, Boolean) -> Unit = { _, _ -> },
    onNavigateToMessages: (String, String) -> Unit = { _, _ -> },
) {
    val profile by viewModel.profile.collectAsState()
    val posts by viewModel.posts.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val isFollowing by viewModel.isFollowing.collectAsState()
    val isFollowLoading by viewModel.isFollowLoading.collectAsState()
    val isBlocked by viewModel.isBlocked.collectAsState()
    val isMuted by viewModel.isMuted.collectAsState()
    var selectedSegment by remember { mutableIntStateOf(0) }
    var showMenu by remember { mutableStateOf(false) }

    LaunchedEffect(userId) {
        viewModel.loadProfile(userId)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = profile?.displayName ?: "",
                        style = CorusFont.displayName,
                        color = CorusColors.Text,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = CorusColors.Text)
                    }
                },
                actions = {
                    // Dedicated message button (matching iOS envelope icon)
                    IconButton(onClick = { onNavigateToMessages("", userId) }) {
                        Icon(Icons.Filled.Email, contentDescription = "Message", tint = CorusColors.Text, modifier = Modifier.size(20.dp))
                    }

                    Box {
                        IconButton(onClick = { showMenu = true }) {
                            Icon(Icons.Filled.MoreVert, contentDescription = "Menu", tint = CorusColors.Text)
                        }
                        DropdownMenu(
                            expanded = showMenu,
                            onDismissRequest = { showMenu = false },
                        ) {
                            DropdownMenuItem(
                                text = { Text("Report", style = CorusFont.body) },
                                onClick = { showMenu = false },
                            )
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        if (isMuted) "Unmute" else "Mute",
                                        style = CorusFont.body,
                                    )
                                },
                                leadingIcon = {
                                    Icon(
                                        if (isMuted) Icons.Filled.VolumeUp else Icons.Filled.VolumeOff,
                                        contentDescription = null,
                                    )
                                },
                                onClick = {
                                    viewModel.toggleMute(userId)
                                    showMenu = false
                                },
                            )
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        if (isBlocked) "Unblock" else "Block",
                                        style = CorusFont.body,
                                        color = CorusColors.Error,
                                    )
                                },
                                onClick = {
                                    showMenu = false
                                    if (isBlocked) viewModel.unblockUser(userId)
                                    else viewModel.blockUser(userId)
                                },
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = CorusColors.Background),
            )
        },
    ) { padding ->
        if (isLoading && profile == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(color = CorusColors.Accent)
            }
            return@Scaffold
        }

        val currentProfile = profile ?: return@Scaffold

        if (isBlocked) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Filled.Block,
                        contentDescription = null,
                        modifier = Modifier.size(48.dp),
                        tint = CorusColors.Tertiary,
                    )
                    Spacer(modifier = Modifier.height(CorusSpacing.md))
                    Text("You blocked this user", style = CorusFont.bodyMedium, color = CorusColors.Secondary)
                    Spacer(modifier = Modifier.height(CorusSpacing.sm))
                    TextButton(onClick = { viewModel.unblockUser(userId) }) {
                        Text("Unblock", style = CorusFont.button, color = CorusColors.Accent)
                    }
                }
            }
            return@Scaffold
        }

        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            item(span = { GridItemSpan(3) }) {
                Column {
                    // Avatar + Stats Row
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = CorusSpacing.lg, vertical = CorusSpacing.md),
                        verticalAlignment = Alignment.Top,
                    ) {
                        UserAvatarView(
                            avatarURL = currentProfile.avatarURL,
                            size = CorusSpacing.avatarLarge,
                        )

                        Spacer(modifier = Modifier.width(CorusSpacing.md))

                        Column(modifier = Modifier.weight(1f)) {
                            // Stats
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                StatItem(count = currentProfile.cymbalCount, label = "coruses")
                                StatDivider()
                                StatItem(
                                    count = currentProfile.followerCount,
                                    label = "followers",
                                    modifier = Modifier.clickable { onNavigateToFollowList(userId, true) },
                                )
                                StatDivider()
                                StatItem(
                                    count = currentProfile.followingCount,
                                    label = "following",
                                    modifier = Modifier.clickable { onNavigateToFollowList(userId, false) },
                                )
                            }

                            Spacer(modifier = Modifier.height(CorusSpacing.sm))

                            // Follow button + Playlist button
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                // Follow button — matching iOS Capsule with fill/stroke
                                val followShape = RoundedCornerShape(50)
                                Button(
                                    onClick = { viewModel.toggleFollow(userId) },
                                    modifier = Modifier
                                        .weight(1f)
                                        .then(
                                            if (isFollowing) Modifier.border(1.dp, CorusColors.Divider, followShape)
                                            else Modifier
                                        ),
                                    shape = followShape,
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (isFollowing) Color.Transparent else CorusColors.Accent,
                                        contentColor = if (isFollowing) CorusColors.Secondary else Color.White,
                                    ),
                                    enabled = !isFollowLoading,
                                    contentPadding = PaddingValues(vertical = CorusSpacing.sm - 2.dp),
                                ) {
                                    if (isFollowLoading) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(16.dp),
                                            strokeWidth = 2.dp,
                                        )
                                    } else {
                                        Text(
                                            text = if (isFollowing) "FOLLOWING" else "FOLLOW",
                                            style = CorusFont.button,
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.width(CorusSpacing.sm))

                                // Playlist button
                                val hasSongs = posts.any { it.mediaType == MediaType.TRACK }
                                val isGeneratingPlaylist by viewModel.nowPlayingManager.isGeneratingPlaylist.collectAsState()
                                val playlistError by viewModel.nowPlayingManager.playlistError.collectAsState()

                                LaunchedEffect(playlistError) {
                                    if (playlistError != null) {
                                        ToastManager.show(playlistError!!)
                                        viewModel.nowPlayingManager.clearPlaylistError()
                                    }
                                }

                                OutlinedButton(
                                    onClick = {
                                        if (hasSongs) {
                                            viewModel.generatePlaylist(userId)
                                        } else {
                                            ToastManager.show("No songs to make a playlist")
                                        }
                                    },
                                    enabled = hasSongs && !isGeneratingPlaylist,
                                    shape = RoundedCornerShape(50),
                                    contentPadding = PaddingValues(horizontal = CorusSpacing.md, vertical = CorusSpacing.sm),
                                    colors = ButtonDefaults.outlinedButtonColors(
                                        contentColor = CorusColors.Secondary,
                                    ),
                                    border = ButtonDefaults.outlinedButtonBorder(enabled = true).copy(
                                        brush = androidx.compose.ui.graphics.SolidColor(CorusColors.Divider),
                                    ),
                                    modifier = Modifier.height(34.dp),
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
                                        ) {
                                            Icon(
                                                imageVector = Icons.AutoMirrored.Filled.List,
                                                contentDescription = "Playlist",
                                                modifier = Modifier.size(14.dp),
                                            )
                                            Text(
                                                text = "PLAYLIST",
                                                style = CorusFont.buttonSmall,
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Username + Bio + Website
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 28.dp, end = CorusSpacing.lg),
                    ) {
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

                        if (!currentProfile.website.isNullOrBlank()) {
                            Spacer(modifier = Modifier.height(CorusSpacing.xxs))
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

                    // Segment control
                    val tabs = listOf("MUSIC", "FILM", "LIKES")
                    Row(modifier = Modifier.fillMaxWidth().padding(horizontal = CorusSpacing.lg)) {
                        tabs.forEachIndexed { index, title ->
                            val isSelected = selectedSegment == index
                            val accentColor = CorusColors.Accent
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable { selectedSegment = index }
                                    .drawBehind {
                                        if (isSelected) {
                                            val strokeWidth = 2.dp.toPx()
                                            drawLine(
                                                color = accentColor,
                                                start = Offset(0f, size.height),
                                                end = Offset(size.width, size.height),
                                                strokeWidth = strokeWidth,
                                            )
                                        }
                                    }
                                    .padding(vertical = CorusSpacing.md),
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
                        0 -> posts.filter { it.mediaType == MediaType.TRACK }
                        1 -> posts.filter { it.mediaType == MediaType.MOVIE }
                        2 -> posts // Likes — show all (ViewModel would handle this)
                        else -> posts
                    }

                    // Featured post with vinyl/frame + effects
                    if (filteredPosts.isNotEmpty()) {
                        val featured = filteredPosts.first()
                        val userProfile = profile
                        if (userProfile != null && featured.mediaType == MediaType.MOVIE) {
                            FeaturedMoviePosterView(
                                post = featured,
                                frameStyle = userProfile.frameStyle,
                                rainIntensity = userProfile.rainIntensity,
                                snowIntensity = userProfile.snowIntensity,
                                discoIntensity = userProfile.discoIntensityLevel,
                                onPostTap = { onNavigateToPost(featured.id) },
                            )
                        } else if (userProfile != null) {
                            FeaturedCymbalView(
                                post = featured,
                                vinylStyle = userProfile.vinylStyle,
                                rainIntensity = userProfile.rainIntensity,
                                snowIntensity = userProfile.snowIntensity,
                                discoIntensity = userProfile.discoIntensityLevel,
                                onPostTap = { onNavigateToPost(featured.id) },
                            )
                        }
                    } else if (!isLoading) {
                        // Empty state per segment
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(200.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            val (icon, message) = when (selectedSegment) {
                                0 -> "🎵" to "No music posts yet"
                                1 -> "🎬" to "No film posts yet"
                                else -> "❤️" to "No liked posts yet"
                            }
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(icon, fontSize = 32.sp)
                                Spacer(modifier = Modifier.height(CorusSpacing.sm))
                                Text(message, style = CorusFont.body, color = CorusColors.Secondary)
                            }
                        }
                    }
                }
            }

            // Album art grid (filtered)
            val filteredPosts = when (selectedSegment) {
                0 -> posts.filter { it.mediaType == MediaType.TRACK }
                1 -> posts.filter { it.mediaType == MediaType.MOVIE }
                2 -> posts
                else -> posts
            }
            if (filteredPosts.size > 1) {
                items(filteredPosts.drop(1), key = { it.id }) { post ->
                    AsyncImage(
                        model = post.displayImageURL,
                        contentDescription = post.displayTitle,
                        modifier = Modifier
                            .aspectRatio(1f)
                            .padding(CorusSpacing.xxs)
                            .clickable { onNavigateToPost(post.id) },
                        contentScale = ContentScale.Crop,
                    )
                }
            }
        }
    }
}

@Composable
private fun StatItem(count: Int, label: String, modifier: Modifier = Modifier) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = modifier) {
        Text(text = count.toString(), style = CorusFont.stat, color = CorusColors.Text)
        Text(text = label, style = CorusFont.statLabel, color = CorusColors.Secondary)
    }
}

@Composable
private fun StatDivider() {
    Spacer(modifier = Modifier.width(CorusSpacing.sm))
    Text("|", style = CorusFont.statLabel, color = CorusColors.Tertiary)
    Spacer(modifier = Modifier.width(CorusSpacing.sm))
}
