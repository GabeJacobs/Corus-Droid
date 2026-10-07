package fm.corus.android.ui.screens.auth

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
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
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Icon
import fm.corus.android.ui.components.CorusModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import fm.corus.android.ui.components.rememberGuardedSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import coil3.size.Size
import fm.corus.android.R
import fm.corus.android.data.model.CymbalPost
import fm.corus.android.data.model.MusicMatchData
import fm.corus.android.data.model.CymbalUser
import fm.corus.android.domain.ScrubberClock
import fm.corus.android.domain.NowPlayingManager
import fm.corus.android.domain.PostPlaybackHighlight
import fm.corus.android.domain.toQueuedTrack
import fm.corus.android.ui.components.VennDiagramIcon
import fm.corus.android.ui.components.UserAvatarView
import fm.corus.android.ui.components.FullScreenAvatarOverlay
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
    usesRevisedDesign: Boolean = false,
    matchData: MusicMatchData? = null,
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
    val sheetState = rememberGuardedSheetState(skipPartiallyExpanded = false)
    var selectedPreviewPost by remember(user.id) { mutableStateOf<CymbalPost?>(null) }
    val previewScope = rememberCoroutineScope()
    val density = androidx.compose.ui.platform.LocalDensity.current
    var playerHeight by remember(user.id) { mutableStateOf(72.dp) }
    var showFullScreenAvatar by remember(user.id) { mutableStateOf(false) }
    val avatarURL = user.avatarURL?.takeIf { it.isNotBlank() }
        ?: user.avatarThumbURL?.takeIf { it.isNotBlank() }
    CorusModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
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

        Box(modifier = Modifier.fillMaxWidth()) {
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                state = gridState,
                modifier = Modifier
                    .fillMaxWidth()
                    .defaultMinSize(minHeight = 480.dp),
                contentPadding = PaddingValues(bottom = CorusSpacing.xxxl + if (usesRevisedDesign && selectedPreviewPost != null) playerHeight else 0.dp),
            ) {
                // Header — full-width, padded.
                item(span = { GridItemSpan(3) }) {
                    PreviewHeader(
                        user = user,
                        usesRevisedDesign = usesRevisedDesign,
                        matchData = matchData,
                        avatarURL = avatarURL,
                        isFollowed = isFollowed,
                        onFollow = onFollow,
                        onViewAvatar = { showFullScreenAvatar = true },
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
                        GridTile(post = post, nowPlaying = nowPlaying) {
                            selectedPreviewPost = post
                            previewScope.launch { playUserPostPreview(post, nowPlaying) }
                        }
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
            if (usesRevisedDesign) {
                selectedPreviewPost?.let { post ->
                    PreviewMiniPlayer(post, nowPlaying, Modifier.align(Alignment.BottomCenter).onSizeChanged {
                        playerHeight = with(density) { it.height.toDp() }
                    }) {
                        previewScope.launch { playUserPostPreview(post, nowPlaying) }
                    }
                }
            }
        }
    }

    if (showFullScreenAvatar) {
        // A separate window keeps the existing viewer above the modal sheet.
        Dialog(
            onDismissRequest = { showFullScreenAvatar = false },
            properties = DialogProperties(
                usePlatformDefaultWidth = false,
                decorFitsSystemWindows = false,
            ),
        ) {
            FullScreenAvatarOverlay(
                avatarURL = avatarURL,
                visible = true,
                onDismiss = { showFullScreenAvatar = false },
            )
        }
    }
}

@Composable
private fun PreviewHeader(
    user: CymbalUser,
    usesRevisedDesign: Boolean,
    matchData: MusicMatchData?,
    avatarURL: String?,
    isFollowed: Boolean,
    onFollow: () -> Unit,
    onViewAvatar: () -> Unit,
) {
    if (usesRevisedDesign) {
        RevisedPreviewHeader(user, avatarURL, matchData, isFollowed, onFollow, onViewAvatar)
        return
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = CorusSpacing.xxl)
            .padding(top = CorusSpacing.md, bottom = CorusSpacing.lg),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(CorusSpacing.md),
    ) {
        UserAvatarView(
            avatarURL = avatarURL,
            avatarThumbURL = user.avatarThumbURL,
            displayName = user.displayName,
            size = 72.dp,
            modifier = Modifier.clickable(
                enabled = avatarURL != null,
                onClickLabel = stringResource(fm.corus.android.localization.CorusStrings.profile_view_photo),
                onClick = onViewAvatar,
            ),
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
                if (isFollowed) stringResource(id = fm.corus.android.localization.CorusStrings.following_status)
                else stringResource(id = fm.corus.android.localization.CorusStrings.follow_action),
                style = CorusFont.buttonSmall,
            )
        }
    }
}

@Composable
private fun GridTile(post: CymbalPost, nowPlaying: NowPlayingManager, onPreview: () -> Unit) {
    val state by nowPlaying.state.collectAsState()
    val loadingTrackId by nowPlaying.loadingTrackId.collectAsState()
    val loadingSourcePostId by nowPlaying.loadingSourcePostId.collectAsState()
    val isPlayingThis = post.isTrack && PostPlaybackHighlight.shouldHighlight(
        activeTrackId = state.trackId,
        activeSourcePostId = state.sourcePostId,
        playbackActive = state.isPlaying,
        postTrackId = post.track.id,
        postId = post.id,
    )
    val isLoadingThis = post.isTrack && PostPlaybackHighlight.shouldHighlight(
        activeTrackId = loadingTrackId,
        activeSourcePostId = loadingSourcePostId,
        playbackActive = true,
        postTrackId = post.track.id,
        postId = post.id,
    )

    Box(
        modifier = Modifier
            .aspectRatio(1f)
            .background(CorusColors.CardBackground)
            .then(
                if (post.isTrack) Modifier.clickable(onClick = onPreview) else Modifier,
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

/** User previews always sample in-app, independently of full-song settings and onboarding flags. */
internal suspend fun playUserPostPreview(post: CymbalPost, nowPlaying: NowPlayingManager) {
    if (!post.isTrack) return
    nowPlaying.play(post.toQueuedTrack(), emptyList(), previewOnly = true)
}

@Composable
private fun RevisedPreviewHeader(
    user: CymbalUser,
    avatarURL: String?,
    matchData: MusicMatchData?,
    isFollowed: Boolean,
    onFollow: () -> Unit,
    onViewAvatar: () -> Unit,
) {
    val names = remember(matchData) {
        (matchData?.sharedArtistNames.orEmpty() + matchData?.sharedDirectorNames.orEmpty())
            .map { it.trim() }.filter { it.isNotEmpty() }.distinctBy { it.lowercase() }
    }
    var expanded by remember(user.id) { mutableStateOf(false) }
    var bioOverflows by remember(user.id, user.bio) { mutableStateOf(false) }
    val largeText = androidx.compose.ui.platform.LocalDensity.current.fontScale > 1.3f
    val follow: @Composable () -> Unit = {
        TextButton(
            onClick = onFollow,
            shape = RoundedCornerShape(50),
            colors = ButtonDefaults.textButtonColors(
                containerColor = if (isFollowed) CorusColors.CardBackground else CorusColors.Accent,
                contentColor = if (isFollowed) CorusColors.Secondary else Color.White,
            ),
            border = if (isFollowed) BorderStroke(1.dp, CorusColors.Divider) else null,
            contentPadding = PaddingValues(horizontal = CorusSpacing.lg, vertical = CorusSpacing.sm),
        ) {
            Text(stringResource(if (isFollowed) fm.corus.android.localization.CorusStrings.following_status
                else fm.corus.android.localization.CorusStrings.follow_action), style = CorusFont.buttonSmall)
        }
    }
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = CorusSpacing.xxl)
            .padding(top = CorusSpacing.md, bottom = CorusSpacing.lg),
        verticalArrangement = Arrangement.spacedBy(CorusSpacing.sm),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(CorusSpacing.md)) {
            UserAvatarView(
                avatarURL = avatarURL, avatarThumbURL = user.avatarThumbURL,
                displayName = user.displayName, size = 58.dp,
                modifier = Modifier.clickable(enabled = avatarURL != null,
                    onClickLabel = stringResource(fm.corus.android.localization.CorusStrings.profile_view_photo),
                    onClick = onViewAvatar),
            )
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(CorusSpacing.xxs)) {
                Text(user.displayName.ifBlank { "@${user.username}" },
                    style = CorusFont.custom(weight = 900, size = 18), color = CorusColors.Text,
                    maxLines = 2, overflow = TextOverflow.Ellipsis)
                UsernameWithFlair(username = user.username, isVerified = user.isVerified,
                    isClubMember = user.isClubMember, showAtPrefix = true,
                    style = CorusFont.caption, color = CorusColors.Secondary)
            }
            if (!largeText) follow()
        }
        if (largeText) follow()
        if (user.bio.isNotBlank()) {
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(CorusSpacing.xs)) {
                Text(user.bio, modifier = Modifier.weight(1f), style = CorusFont.caption,
                    color = CorusColors.Secondary, maxLines = if (expanded) Int.MAX_VALUE else 2,
                    overflow = TextOverflow.Ellipsis,
                    onTextLayout = { if (!expanded) bioOverflows = it.hasVisualOverflow })
                if (expanded || bioOverflows) {
                    Box(modifier = Modifier.height(48.dp).clickable(role = Role.Button) { expanded = !expanded },
                        contentAlignment = Alignment.BottomCenter) {
                        Text(stringResource(if (expanded) fm.corus.android.localization.CorusStrings.concert_show_less
                            else fm.corus.android.localization.CorusStrings.full_player_cd_more),
                            style = CorusFont.caption, color = CorusColors.Accent)
                    }
                }
            }
        }
        if (names.isNotEmpty()) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(CorusSpacing.sm)) {
                VennDiagramIcon(size = 28.dp, color = CorusColors.Accent, shadedIntersection = true)
                Column(verticalArrangement = Arrangement.spacedBy(CorusSpacing.xxs)) {
                    Text(stringResource(R.string.user_preview_shared_taste), style = CorusFont.caption, color = CorusColors.Secondary)
                    Text(names.joinToString(" · "), style = CorusFont.captionMedium, color = CorusColors.Text)
                }
            }
        }
    }
}

/** Only this small bar subscribes to clock ticks; the grid does not. */
@Composable
private fun PreviewMiniPlayer(
    post: CymbalPost,
    nowPlaying: NowPlayingManager,
    modifier: Modifier = Modifier,
    onToggle: () -> Unit,
) {
    val state by nowPlaying.state.collectAsState()
    val loadingTrackId by nowPlaying.loadingTrackId.collectAsState()
    val loadingSourcePostId by nowPlaying.loadingSourcePostId.collectAsState()
    val time by ScrubberClock.time.collectAsState()
    val duration by ScrubberClock.duration.collectAsState()
    val ownsPlayback = PostPlaybackHighlight.shouldHighlight(state.trackId, state.sourcePostId, true, post.track.id, post.id)
    val playing = ownsPlayback && state.isPlaying
    val loading = PostPlaybackHighlight.shouldHighlight(loadingTrackId, loadingSourcePostId, true, post.track.id, post.id)
    val progress = if (ownsPlayback && duration > 0) (time.toFloat() / duration).coerceIn(0f, 1f) else 0f
    Row(
        modifier = modifier.fillMaxWidth().background(CorusColors.Background)
            .padding(horizontal = CorusSpacing.xxl, vertical = CorusSpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(CorusSpacing.md),
    ) {
        AsyncImage(model = post.displayImageURL, contentDescription = null, contentScale = ContentScale.Crop,
            modifier = Modifier.size(40.dp).clip(RoundedCornerShape(4.dp)))
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(CorusSpacing.xxs)) {
            Text(post.track.name, style = CorusFont.captionMedium, color = CorusColors.Text,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("${post.track.artistName} · ${stringResource(R.string.user_preview_playback_label)}",
                style = CorusFont.caption, color = CorusColors.Secondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth().height(2.dp),
                color = CorusColors.Accent, trackColor = CorusColors.Divider)
        }
        IconButton(onClick = onToggle) {
            if (loading) CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = CorusColors.Accent)
            else Icon(if (playing) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                contentDescription = stringResource(if (playing) R.string.mini_player_cd_pause else R.string.mini_player_cd_play),
                tint = CorusColors.Text)
        }
    }
}
