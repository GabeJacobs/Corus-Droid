package fm.corus.android.ui.screens.auth

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import coil3.size.Size
import fm.corus.android.R
import fm.corus.android.data.model.CymbalPost
import fm.corus.android.data.model.CymbalUser
import fm.corus.android.domain.NowPlayingManager
import fm.corus.android.ui.components.UserAvatarView
import fm.corus.android.ui.components.UsernameWithFlair
import fm.corus.android.ui.theme.CorusColors
import fm.corus.android.ui.theme.CorusFont
import fm.corus.android.ui.theme.CorusSpacing
import kotlinx.coroutines.launch

/**
 * Half-sheet preview shown when tapping a user row during onboarding.
 * Mirrors iOS `UserPreviewSheet`: avatar, @handle, bio, follow button,
 * and a paginated edge-to-edge 3-column grid of the user's posts.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UserPreviewSheet(
    user: CymbalUser,
    posts: List<CymbalPost>,
    isLoading: Boolean,
    isLoadingMore: Boolean,
    hasMore: Boolean,
    isFollowed: Boolean,
    nowPlaying: NowPlayingManager,
    onFollow: () -> Unit,
    onLoadMore: () -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = CorusColors.Background,
        dragHandle = { BottomSheetDefaults.DragHandle() },
    ) {
        val gridState = rememberLazyGridState()

        // Trigger pagination when the user nears the end of the loaded posts.
        val shouldLoadMore by remember(posts.size, hasMore, isLoadingMore) {
            derivedStateOf {
                val lastVisible = gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1
                hasMore && !isLoadingMore && posts.isNotEmpty() &&
                    lastVisible >= posts.size - PRELOAD_THRESHOLD
            }
        }
        LaunchedEffect(shouldLoadMore) {
            if (shouldLoadMore) onLoadMore()
        }

        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            state = gridState,
            modifier = Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = 480.dp),
            contentPadding = PaddingValues(bottom = CorusSpacing.xxxl),
        ) {
            // Header — full-width, padded.
            item(span = { GridItemSpan(3) }) {
                PreviewHeader(
                    user = user,
                    isFollowed = isFollowed,
                    onFollow = onFollow,
                )
            }

            if (isLoading) {
                items(9) {
                    Box(
                        modifier = Modifier
                            .aspectRatio(1f)
                            .background(CorusColors.Skeleton),
                    )
                }
            } else if (posts.isEmpty()) {
                item(span = { GridItemSpan(3) }) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(CorusSpacing.xxl),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            "No posts yet",
                            style = CorusFont.body,
                            color = CorusColors.Tertiary,
                        )
                    }
                }
            } else {
                items(posts, key = { it.id }) { post ->
                    GridTile(post = post, nowPlaying = nowPlaying)
                }
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
    }
}

@Composable
private fun PreviewHeader(
    user: CymbalUser,
    isFollowed: Boolean,
    onFollow: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = CorusSpacing.xxl)
            .padding(top = CorusSpacing.md, bottom = CorusSpacing.lg),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(CorusSpacing.md),
    ) {
        UserAvatarView(
            avatarURL = user.avatarURL,
            displayName = user.displayName,
            size = 72.dp,
        )

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(CorusSpacing.xxs),
        ) {
            UsernameWithFlair(
                username = user.username,
                isVerified = user.isVerified,
                isClubMember = user.isClubMember,
                isBot = user.isBot,
                botType = user.botType,
                showAtPrefix = true,
                style = CorusFont.custom(weight = 900, size = 18),
                color = CorusColors.Text,
            )
            if (user.bio.isNotEmpty()) {
                Text(
                    user.bio,
                    style = CorusFont.body,
                    color = CorusColors.Secondary,
                    textAlign = TextAlign.Center,
                )
            }
        }

        TextButton(
            onClick = onFollow,
            shape = RoundedCornerShape(CorusSpacing.pillCornerRadius),
            colors = ButtonDefaults.textButtonColors(
                containerColor = if (isFollowed) CorusColors.CardBackground else CorusColors.Accent,
                contentColor = if (isFollowed) CorusColors.Secondary else Color.White,
            ),
            border = if (isFollowed) BorderStroke(1.dp, CorusColors.Divider) else null,
            contentPadding = PaddingValues(horizontal = CorusSpacing.xl, vertical = CorusSpacing.sm),
        ) {
            Text(
                if (isFollowed) stringResource(id = R.string.search_button_following)
                else stringResource(id = R.string.search_button_follow),
                style = CorusFont.buttonSmall,
            )
        }
    }
}

@Composable
private fun GridTile(post: CymbalPost, nowPlaying: NowPlayingManager) {
    val state by nowPlaying.state.collectAsState()
    val loadingTrackId by nowPlaying.loadingTrackId.collectAsState()
    val scope = rememberCoroutineScope()

    val isPlayingThis = post.isTrack && state.trackId == post.track.id && state.isPlaying
    val isLoadingThis = post.isTrack && loadingTrackId == post.track.id

    Box(
        modifier = Modifier
            .aspectRatio(1f)
            .background(CorusColors.CardBackground)
            .then(
                if (post.isTrack) Modifier.clickable {
                    val track = post.track
                    scope.launch {
                        nowPlaying.play(
                            trackId = track.id,
                            trackName = track.name,
                            artistName = track.artistName,
                            albumArtURL = track.albumArtURL,
                            previewUrl = track.previewUrl,
                            spotifyURI = track.spotifyURI,
                            spotifyWebURL = track.spotifyWebURL,
                            isrc = track.isrc,
                            sourcePostId = post.id,
                            source = track.source,
                            soundcloudId = track.soundcloudId,
                            soundcloudPermalinkUrl = track.soundcloudPermalinkUrl,
                        )
                    }
                } else Modifier,
            ),
    ) {
        AsyncImage(
            model = ImageRequest.Builder(LocalContext.current)
                .data(post.displayImageLargeURL ?: post.displayImageURL)
                .crossfade(true)
                .size(Size(360, 360))
                .build(),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
        )

        // Bottom-left badge: play/pause for tracks, film icon for movies.
        Box(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(6.dp)
                .size(28.dp)
                .clip(CircleShape)
                .background(Color.Black.copy(alpha = 0.5f)),
            contentAlignment = Alignment.Center,
        ) {
            when {
                post.isMovie -> Icon(
                    Icons.Filled.Movie,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(14.dp),
                )
                isLoadingThis -> CircularProgressIndicator(
                    modifier = Modifier.size(12.dp),
                    strokeWidth = 1.5.dp,
                    color = Color.White,
                )
                isPlayingThis -> Icon(
                    Icons.Filled.Pause,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(12.dp),
                )
                else -> Icon(
                    Icons.Filled.PlayArrow,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(12.dp),
                )
            }
        }
    }
}

private const val PRELOAD_THRESHOLD = 6
