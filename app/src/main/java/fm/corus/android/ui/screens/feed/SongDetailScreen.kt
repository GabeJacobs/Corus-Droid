package fm.corus.android.ui.screens.feed

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import fm.corus.android.data.model.CymbalPost
import fm.corus.android.ui.components.SkeletonUserRow
import fm.corus.android.ui.components.UserAvatarView
import fm.corus.android.ui.components.UsernameWithFlair
import fm.corus.android.ui.theme.CorusColors
import fm.corus.android.ui.theme.CorusFont
import fm.corus.android.ui.theme.CorusSpacing
import fm.corus.android.ui.util.DateUtils

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SongDetailScreen(
    trackId: String,
    albumArtURL: String? = null,
    albumArtLargeURL: String? = null,
    songName: String? = null,
    artistName: String? = null,
    spotifyURI: String? = null,
    spotifyWebURL: String? = null,
    previewUrl: String? = null,
    viewModel: SongDetailViewModel = hiltViewModel(),
    onBack: () -> Unit = {},
    onNavigateToUser: (String) -> Unit = {},
    onNavigateToCompose: (String) -> Unit = {},
) {
    val posts by viewModel.posts.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val isLoadingMore by viewModel.isLoadingMore.collectAsState()
    val hasMore by viewModel.hasMore.collectAsState()
    val loadError by viewModel.loadError.collectAsState()
    val uniquePosterCount by viewModel.uniquePosterCount.collectAsState()
    val nowPlayingState by viewModel.nowPlayingState.collectAsState()
    val previewLoadingTrackId by viewModel.previewLoadingTrackId.collectAsState()
    val context = LocalContext.current

    // Use route metadata immediately, upgrade to post data when available
    val songInfo = posts.firstOrNull()
    val displayName = songInfo?.displayTitle?.takeIf { it.isNotBlank() } ?: songName
    val displayArtist = songInfo?.displaySubtitle?.takeIf { it.isNotBlank() } ?: artistName
    val artUrl = songInfo?.track?.albumArtLargeURL ?: songInfo?.track?.albumArtURL ?: albumArtLargeURL ?: albumArtURL
    val effectiveSpotifyURI = songInfo?.track?.spotifyURI ?: spotifyURI ?: ""
    val effectiveSpotifyWebURL = songInfo?.track?.spotifyWebURL ?: spotifyWebURL ?: ""
    val effectivePreviewUrl = songInfo?.track?.previewUrl ?: previewUrl
    val effectiveIsrc = songInfo?.track?.isrc

    val isPlayingThisTrack = nowPlayingState.trackId == trackId && nowPlayingState.isPlaying
    val isLoadingThisTrack = previewLoadingTrackId == trackId
    val showScrim = isPlayingThisTrack || isLoadingThisTrack

    LaunchedEffect(trackId) {
        viewModel.loadSongPosts(
            trackId = trackId,
            spotifyURI = spotifyURI,
            trackName = songName,
            artistName = artistName,
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {},
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = CorusColors.Text)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White),
                windowInsets = WindowInsets(0, 0, 0, 0),
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Song header — always shown using route metadata
            item {
                Spacer(modifier = Modifier.height(CorusSpacing.xl))

                if (artUrl != null) {
                    val scrimAlpha by animateFloatAsState(
                        targetValue = if (showScrim) 1f else 0f,
                        animationSpec = tween(durationMillis = 200),
                        label = "previewScrim",
                    )
                    Box(
                        modifier = Modifier
                            .size(200.dp)
                            .shadow(4.dp, RoundedCornerShape(8.dp))
                            .clip(RoundedCornerShape(8.dp))
                            .clickable {
                                viewModel.togglePreview(
                                    trackId = trackId,
                                    trackName = displayName ?: "",
                                    artistName = displayArtist ?: "",
                                    albumArtURL = artUrl,
                                    previewUrl = effectivePreviewUrl,
                                    spotifyURI = effectiveSpotifyURI.ifBlank { null },
                                    spotifyWebURL = effectiveSpotifyWebURL.ifBlank { null },
                                    isrc = effectiveIsrc,
                                )
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        AsyncImage(
                            model = ImageRequest.Builder(LocalContext.current)
                                .data(artUrl)
                                .crossfade(true)
                                .build(),
                            contentDescription = displayName,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop,
                        )

                        if (scrimAlpha > 0f) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(Color.Black.copy(alpha = 0.4f * scrimAlpha)),
                            )
                            if (isLoadingThisTrack) {
                                CircularProgressIndicator(
                                    color = Color.White,
                                    strokeWidth = 3.dp,
                                    modifier = Modifier.size(40.dp),
                                )
                            } else if (isPlayingThisTrack) {
                                Icon(
                                    imageVector = Icons.Filled.Pause,
                                    contentDescription = "Pause preview",
                                    tint = Color.White,
                                    modifier = Modifier.size(40.dp),
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(CorusSpacing.md))
                }

                // Song title + artist — show immediately from route data
                if (displayName != null) {
                    Text(
                        text = displayName,
                        style = CorusFont.songTitleLarge,
                        color = CorusColors.Text,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = CorusSpacing.lg),
                    )
                }
                if (displayArtist != null) {
                    Spacer(modifier = Modifier.height(CorusSpacing.xxs))
                    Text(
                        text = displayArtist,
                        style = CorusFont.artistNameLarge,
                        color = CorusColors.Secondary,
                    )
                }

                Spacer(modifier = Modifier.height(CorusSpacing.md))

                // Capsule buttons row — Post Song + Play in Spotify (matching iOS order)
                Row(
                    horizontalArrangement = Arrangement.spacedBy(CorusSpacing.md),
                ) {
                    // Post Song capsule
                    Button(
                        onClick = {
                            viewModel.analyticsService.logPostThisSongTapped(trackId)
                            onNavigateToCompose(trackId)
                        },
                        shape = RoundedCornerShape(50),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = CorusColors.Accent,
                            contentColor = Color.White,
                        ),
                        contentPadding = PaddingValues(horizontal = CorusSpacing.lg, vertical = CorusSpacing.sm),
                    ) {
                        Text("+ Post Song", style = CorusFont.buttonSmall)
                    }

                    // Play in Spotify capsule
                    Button(
                        onClick = {
                            val uri = effectiveSpotifyURI.ifBlank { effectiveSpotifyWebURL }
                            if (uri.isNotBlank()) {
                                try { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(uri))) } catch (_: Exception) { }
                            }
                        },
                        shape = RoundedCornerShape(50),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = CorusColors.SpotifyGreen,
                            contentColor = Color.White,
                        ),
                        contentPadding = PaddingValues(horizontal = CorusSpacing.lg, vertical = CorusSpacing.sm),
                    ) {
                        Text("Play in Spotify", style = CorusFont.buttonSmall)
                    }
                }

                Spacer(modifier = Modifier.height(CorusSpacing.md))

                HorizontalDivider(color = CorusColors.Divider, thickness = 0.5.dp)
            }

            // Posted by section
            if (isLoading) {
                item {
                    Text(
                        text = "Posted by",
                        style = CorusFont.sectionHeader,
                        color = CorusColors.Secondary,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = CorusSpacing.lg)
                            .padding(top = CorusSpacing.lg, bottom = CorusSpacing.md),
                    )
                }
                items(6) { index ->
                    SkeletonUserRow()
                    if (index < 5) {
                        HorizontalDivider(
                            modifier = Modifier.padding(start = 72.dp),
                            color = CorusColors.Divider,
                            thickness = 0.5.dp,
                        )
                    }
                }
            } else if (loadError != null) {
                item {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(top = 40.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text("Couldn't load posts", style = CorusFont.bodyMedium, color = CorusColors.Secondary)
                        Spacer(modifier = Modifier.height(CorusSpacing.md))
                        TextButton(onClick = { viewModel.loadSongPosts(trackId) }) {
                            Text("Try Again", style = CorusFont.buttonSmall, color = CorusColors.Accent)
                        }
                    }
                }
            } else if (posts.isEmpty()) {
                item {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(top = 40.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text("No one has posted this song yet", style = CorusFont.body, color = CorusColors.Secondary)
                        Spacer(modifier = Modifier.height(CorusSpacing.md))
                        Button(
                            onClick = { onNavigateToCompose(trackId) },
                            shape = RoundedCornerShape(50),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = CorusColors.Accent,
                                contentColor = Color.White,
                            ),
                            contentPadding = PaddingValues(horizontal = CorusSpacing.lg, vertical = CorusSpacing.sm),
                        ) {
                            Text("Be the first!", style = CorusFont.buttonSmall)
                        }
                    }
                }
            } else {
                // Header with count
                item {
                    val count = uniquePosterCount ?: posts.map { it.user.id }.toSet().size
                    Text(
                        text = "Posted by ${formatUserCount(count)} user${if (count != 1) "s" else ""}",
                        style = CorusFont.sectionHeader,
                        color = CorusColors.Secondary,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = CorusSpacing.lg)
                            .padding(top = CorusSpacing.lg, bottom = CorusSpacing.md),
                    )
                }

                items(posts, key = { it.id }) { post ->
                    PostedByRow(
                        post = post,
                        onUserTap = { onNavigateToUser(post.user.id) },
                    )
                    if (post.id != posts.lastOrNull()?.id) {
                        HorizontalDivider(
                            modifier = Modifier.padding(start = 72.dp),
                            color = CorusColors.Divider,
                            thickness = 0.5.dp,
                        )
                    }

                    // Pagination trigger
                    if (post.id == posts.lastOrNull()?.id && hasMore && !isLoadingMore) {
                        LaunchedEffect(post.id) { viewModel.loadMore() }
                    }
                }

                if (isLoadingMore) {
                    item {
                        CircularProgressIndicator(
                            color = CorusColors.Accent,
                            modifier = Modifier.padding(CorusSpacing.lg),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PostedByRow(
    post: CymbalPost,
    onUserTap: () -> Unit = {},
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onUserTap)
            .padding(horizontal = CorusSpacing.lg, vertical = CorusSpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        UserAvatarView(
            avatarURL = post.user.avatarURL,
            displayName = post.user.displayName,
            size = CorusSpacing.avatarMedium,
        )

        Spacer(modifier = Modifier.width(CorusSpacing.md))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = post.user.displayName,
                style = CorusFont.bodyMedium,
                color = CorusColors.Text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            UsernameWithFlair(
                username = post.user.username,
                isVerified = post.user.isVerified,
                isClubMember = post.user.isClubMember,
                flairStyle = post.user.flairStyle,
                isBot = post.user.isBot,
                isFirstPoster = post.isFirstPoster,
                isNewRelease = post.isNewRelease(),
                showAtPrefix = true,
                style = CorusFont.caption,
                color = CorusColors.Secondary,
            )
        }

        Text(
            text = DateUtils.relativeTime(post.timestamp),
            style = CorusFont.caption,
            color = CorusColors.Tertiary,
        )
    }
}

private fun formatUserCount(count: Int): String {
    return when {
        count < 1000 -> "$count"
        count < 1_000_000 -> String.format("%.2fK", count / 1000.0)
        else -> String.format("%.2fM", count / 1_000_000.0)
    }
}
