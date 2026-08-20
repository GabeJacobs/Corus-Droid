package fm.corus.android.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import kotlinx.coroutines.delay
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import fm.corus.android.R
import fm.corus.android.data.model.CommentAttachedAlbum
import fm.corus.android.data.model.CommentAttachedArtist
import fm.corus.android.data.model.CommentAttachedDirector
import fm.corus.android.data.model.CymbalComment
import fm.corus.android.data.model.CymbalMovie
import fm.corus.android.data.model.CymbalPost
import fm.corus.android.data.model.CymbalTrack
import fm.corus.android.data.model.CymbalUser
import fm.corus.android.domain.NowPlayingManager
import fm.corus.android.domain.PostEngagementManager
import fm.corus.android.ui.components.CommentAttachmentCard
import fm.corus.android.ui.components.CommentAttachmentSurface
import fm.corus.android.ui.components.LikedBySection
import fm.corus.android.ui.components.TappableMentionText
import fm.corus.android.ui.components.UserAvatarView
import fm.corus.android.ui.components.VennDiagramIcon
import fm.corus.android.ui.components.buildMentionAnnotatedString
import fm.corus.android.ui.screens.feed.PostedByRow
import fm.corus.android.ui.theme.CorusColors
import fm.corus.android.ui.theme.CorusFont
import fm.corus.android.ui.theme.CorusSpacing
import fm.corus.android.ui.util.DateUtils

@Composable
fun FullPlayerSocialSection(
    viewModel: FullPlayerViewModel,
    engagementManager: PostEngagementManager?,
    nowPlayingManager: NowPlayingManager,
    sourcePostId: String?,
    trackId: String?,
    spotifyURI: String?,
    isrc: String?,
    trackName: String,
    artistName: String,
    interactive: Boolean,
    saveCountEnabled: Boolean,
    onOpenPost: (String) -> Unit,
    onOpenUser: (String) -> Unit,
    onOpenComments: (postId: String, replyToCommentId: String?) -> Unit,
    onLikeTap: () -> Unit,
    onRepostTap: (CymbalPost) -> Unit,
    onShareTap: (CymbalPost) -> Unit,
    onSaveTap: (String) -> Unit,
    onOpenSongDetail: (CymbalTrack) -> Unit,
    onOpenFilmDetail: (CymbalPost) -> Unit,
    onComposeTrack: () -> Unit,
    onMentionTap: (String) -> Unit = {},
    onHashtagTap: (String) -> Unit = {},
    onNavigateToSong: (CymbalTrack) -> Unit = {},
    onNavigateToFilm: (CymbalMovie) -> Unit = {},
    onNavigateToArtist: ((CommentAttachedArtist) -> Unit)? = null,
    onNavigateToAlbum: ((CommentAttachedAlbum) -> Unit)? = null,
    onNavigateToDirector: ((CommentAttachedDirector) -> Unit)? = null,
    onLikeLongPress: ((String) -> Unit)? = null,
    onOpenLikes: (String) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val sourcePost by viewModel.sourcePost.collectAsState()
    val isLoadingSourcePost by viewModel.isLoadingSourcePost.collectAsState()
    val comments by viewModel.comments.collectAsState()
    val repliesByParent by viewModel.repliesByParent.collectAsState()
    val isLoadingComments by viewModel.isLoadingComments.collectAsState()
    val isTransitioningComments by viewModel.isTransitioningComments.collectAsState()
    val likedCommentIds by viewModel.likedCommentIds.collectAsState()
    val commentLikeCounts by viewModel.commentLikeCounts.collectAsState()
    val catalogPosts by viewModel.catalogPosts.collectAsState()
    val isLoadingCatalogPosts by viewModel.isLoadingCatalogPosts.collectAsState()
    val isLoadingMoreCatalogPosts by viewModel.isLoadingMoreCatalogPosts.collectAsState()
    val catalogPostsError by viewModel.catalogPostsError.collectAsState()
    val catalogHasMorePages by viewModel.catalogHasMorePages.collectAsState()
    val catalogUniquePosterCount by viewModel.catalogUniquePosterCount.collectAsState()
    val currentUser by viewModel.currentUserProfile.collectAsState()
    val engagementStates = engagementManager?.states?.collectAsState()?.value ?: emptyMap()

    LaunchedEffect(sourcePostId, trackId) {
        viewModel.onPlaybackIdentityChanged(sourcePostId, trackId)
        if (sourcePostId.isNullOrBlank() && !trackId.isNullOrBlank()) {
            viewModel.loadCatalogPostsIfNeeded(
                trackId = trackId,
                spotifyURI = spotifyURI,
                isrc = isrc,
                trackName = trackName,
                artistName = artistName,
            )
        }
    }

    when {
        isLoadingSourcePost && sourcePost == null && !sourcePostId.isNullOrBlank() -> {
            FullPlayerPostLoadingPlaceholder(modifier = modifier.padding(horizontal = 16.dp))
        }
        sourcePost != null -> {
            val post = sourcePost!!
            val engagement = engagementStates[post.id]
            Column(modifier = modifier) {
                FullPlayerSourcePostCard(
                    post = post,
                    isLiked = engagement?.isLiked ?: post.isLiked,
                    likeCount = engagement?.likeCount ?: post.likeCount,
                    commentCount = engagement?.commentCount ?: post.commentCount,
                    repostCount = engagement?.repostCount ?: post.repostCount,
                    isSaved = engagement?.isSaved ?: false,
                    saveCount = engagement?.saveCount ?: post.saveCount,
                    saveCountEnabled = saveCountEnabled,
                    interactive = interactive,
                    onOpenPost = { onOpenPost(post.id) },
                    onOpenUser = { onOpenUser(post.user.id) },
                    onLikeTap = onLikeTap,
                    onCommentTap = { onOpenComments(post.id, null) },
                    onRepostTap = { onRepostTap(post) },
                    onShareTap = { onShareTap(post) },
                    onSaveTap = { onSaveTap(post.id) },
                    onVennTap = {
                        if (post.isMovie) onOpenFilmDetail(post) else onOpenSongDetail(post.track)
                    },
                    onMentionTap = onMentionTap,
                    onHashtagTap = onHashtagTap,
                    onLikeLongPress = onLikeLongPress?.let { { it(post.id) } },
                    currentUser = currentUser,
                    onLikerTap = { onOpenUser(it.id) },
                    onLikesTap = { onOpenLikes(post.id) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                )
                Spacer(modifier = Modifier.height(8.dp))
                FullPlayerCommentsSection(
                    comments = comments,
                    repliesByParent = repliesByParent,
                    headerCount = when {
                        isTransitioningComments || (isLoadingComments && comments.isEmpty()) ->
                            maxOf(engagement?.commentCount ?: post.commentCount, 0)
                        else -> comments.size
                    },
                    isBusy = isLoadingComments || isTransitioningComments,
                    contentVisible = !(isLoadingComments || isTransitioningComments) || comments.isNotEmpty(),
                    likedCommentIds = likedCommentIds,
                    commentLikeCounts = commentLikeCounts,
                    interactive = interactive,
                    nowPlayingManager = nowPlayingManager,
                    onOpenUser = onOpenUser,
                    onReply = { comment -> onOpenComments(post.id, comment.id) },
                    onLikeComment = { viewModel.toggleCommentLike(it) },
                    onAddComment = { onOpenComments(post.id, null) },
                    onMentionTap = onMentionTap,
                    onHashtagTap = onHashtagTap,
                    onNavigateToSong = onNavigateToSong,
                    onNavigateToFilm = onNavigateToFilm,
                    onNavigateToArtist = onNavigateToArtist,
                    onNavigateToAlbum = onNavigateToAlbum,
                    onNavigateToDirector = onNavigateToDirector,
                )
            }
        }
        sourcePostId.isNullOrBlank() -> {
            FullPlayerCatalogPostedBySection(
                posts = catalogPosts,
                isLoading = isLoadingCatalogPosts,
                isLoadingMore = isLoadingMoreCatalogPosts,
                error = catalogPostsError,
                hasMore = catalogHasMorePages,
                uniquePosterCount = catalogUniquePosterCount,
                interactive = interactive,
                onRetry = {
                    viewModel.loadCatalogPostsIfNeeded(
                        trackId = trackId,
                        spotifyURI = spotifyURI,
                        isrc = isrc,
                        trackName = trackName,
                        artistName = artistName,
                        force = true,
                    )
                },
                onLoadMore = { viewModel.loadMoreCatalogPosts() },
                onOpenUser = onOpenUser,
                onOpenPost = onOpenPost,
                onComposeTrack = onComposeTrack,
                modifier = modifier,
            )
        }
        else -> {
            Spacer(modifier = modifier.height(24.dp))
        }
    }
}

@Composable
private fun FullPlayerPostLoadingPlaceholder(modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(16.dp)
    val bone = CorusColors.Text.copy(alpha = 0.16f)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(CorusColors.Text.copy(alpha = 0.08f))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.size(CorusSpacing.avatarMedium).clip(CircleShape).background(bone))
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Box(modifier = Modifier.width(110.dp).height(12.dp).clip(RoundedCornerShape(4.dp)).background(bone))
                Box(modifier = Modifier.width(64.dp).height(10.dp).clip(RoundedCornerShape(4.dp)).background(bone))
            }
        }
        Box(modifier = Modifier.fillMaxWidth().height(12.dp).clip(RoundedCornerShape(4.dp)).background(bone))
        Box(modifier = Modifier.width(180.dp).height(12.dp).clip(RoundedCornerShape(4.dp)).background(bone))
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FullPlayerSourcePostCard(
    post: CymbalPost,
    isLiked: Boolean,
    likeCount: Int,
    commentCount: Int,
    repostCount: Int,
    isSaved: Boolean,
    saveCount: Int,
    saveCountEnabled: Boolean,
    interactive: Boolean,
    onOpenPost: () -> Unit,
    onOpenUser: () -> Unit,
    onLikeTap: () -> Unit,
    onCommentTap: () -> Unit,
    onRepostTap: () -> Unit,
    onShareTap: () -> Unit,
    onSaveTap: () -> Unit,
    onVennTap: () -> Unit,
    onMentionTap: (String) -> Unit,
    onHashtagTap: (String) -> Unit,
    onLikeLongPress: (() -> Unit)?,
    currentUser: CymbalUser?,
    onLikerTap: (CymbalUser) -> Unit,
    onLikesTap: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val shape = RoundedCornerShape(16.dp)
    val displaySaveCount = when {
        !saveCountEnabled -> 0
        isSaved -> maxOf(saveCount, 1)
        else -> saveCount
    }
    Column(
        modifier = modifier
            .clip(shape)
            // Slightly above iOS 0.06 — Android wash still shows more structure
            // than CI frost, so the card needs a hair more fill for body text.
            .background(CorusColors.Text.copy(alpha = 0.09f))
            .border(1.dp, CorusColors.Text.copy(alpha = 0.10f), shape)
            .clickable(
                enabled = interactive,
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onOpenPost,
            )
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .clickable(
                        enabled = interactive,
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onOpenUser,
                    ),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                UserAvatarView(
                    avatarURL = post.user.avatarURL,
                    avatarThumbURL = post.user.avatarThumbURL,
                    displayName = post.user.displayName,
                    username = post.user.username,
                    size = CorusSpacing.avatarMedium,
                    usesSolidLoadingPlaceholder = true,
                )
                Column {
                    Text(
                        text = post.user.username,
                        style = CorusFont.username,
                        color = CorusColors.Text,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = DateUtils.relativeTimeLong(context, post.timestamp),
                        style = CorusFont.caption,
                        color = CorusColors.Text.copy(alpha = 0.55f),
                        maxLines = 1,
                    )
                }
            }
            // iOS: 44pt hit target with trailing alignment so the glyph lines up
            // with the save bookmark. Centering left the chevron inset.
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clickable(
                        enabled = interactive,
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onOpenPost,
                    ),
                contentAlignment = Alignment.CenterEnd,
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = CorusColors.Text.copy(alpha = 0.35f),
                    modifier = Modifier.size(18.dp),
                )
            }
        }

        val caption = post.caption?.trim().orEmpty()
        if (caption.isNotEmpty()) {
            TappableMentionText(
                text = buildMentionAnnotatedString(caption, linkColor = CorusColors.PlayerLink),
                style = CorusFont.body,
                onMentionTap = onMentionTap,
                onHashtagTap = onHashtagTap,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(
                        enabled = interactive,
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onOpenPost,
                    ),
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(
                modifier = Modifier.then(
                    if (onLikeLongPress != null) {
                        Modifier.combinedClickable(
                            enabled = interactive,
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = onLikeTap,
                            onLongClick = onLikeLongPress,
                        )
                    } else {
                        Modifier.clickable(
                            enabled = interactive,
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = onLikeTap,
                        )
                    },
                ),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                Icon(
                    imageVector = if (isLiked) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                    contentDescription = null,
                    modifier = Modifier.size(15.dp),
                    tint = if (isLiked) CorusColors.Like else CorusColors.Text.copy(alpha = 0.85f),
                )
                if (likeCount > 0) {
                    Text("$likeCount", style = CorusFont.bodyMedium, color = CorusColors.Text.copy(alpha = 0.85f))
                }
            }
            EngagementChip(
                icon = { Icon(Icons.Outlined.ChatBubbleOutline, null, Modifier.size(15.dp), CorusColors.Text.copy(alpha = 0.85f)) },
                count = commentCount,
                enabled = interactive,
                onClick = onCommentTap,
            )
            EngagementChip(
                icon = { Icon(Icons.Filled.Repeat, null, Modifier.size(15.dp), CorusColors.Text.copy(alpha = 0.85f)) },
                count = repostCount,
                enabled = interactive,
                onClick = onRepostTap,
            )
            Icon(
                imageVector = Icons.Filled.Send,
                contentDescription = null,
                tint = CorusColors.Text.copy(alpha = 0.85f),
                modifier = Modifier
                    .size(15.dp)
                    .clickable(
                        enabled = interactive,
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onShareTap,
                    ),
            )
            val trackPostCount = post.trackPostCount ?: 0
            if (trackPostCount > 1) {
                Row(
                    modifier = Modifier.clickable(
                        enabled = interactive,
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onVennTap,
                    ),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                ) {
                    // Match heart/comment/repost/share (15.dp) — 18.dp read oversized.
                    VennDiagramIcon(size = 15.dp, color = CorusColors.Text.copy(alpha = 0.85f))
                    Text("$trackPostCount", style = CorusFont.bodyMedium, color = CorusColors.Text.copy(alpha = 0.85f))
                }
            }
            Spacer(modifier = Modifier.weight(1f))
            Row(
                modifier = Modifier.clickable(
                    enabled = interactive,
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onSaveTap,
                ),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                if (displaySaveCount > 0) {
                    Text("$displaySaveCount", style = CorusFont.bodyMedium, color = CorusColors.Text.copy(alpha = 0.85f))
                }
                Icon(
                    imageVector = if (isSaved) Icons.Filled.Bookmark else Icons.Outlined.BookmarkBorder,
                    contentDescription = null,
                    modifier = Modifier.size(15.dp),
                    tint = CorusColors.Text.copy(alpha = 0.85f),
                )
            }
        }

        if (likeCount > 0 || isLiked) {
            LikedBySection(
                likers = post.likers,
                likeCount = likeCount,
                onLikesTap = onLikesTap,
                onLikerTap = onLikerTap,
                currentUser = currentUser,
                isLiked = isLiked,
                embedded = true,
                enabled = interactive,
                textColor = CorusColors.Text,
            )
        }
    }
}

@Composable
private fun EngagementChip(
    icon: @Composable () -> Unit,
    count: Int,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier.clickable(
            enabled = enabled,
            interactionSource = remember { MutableInteractionSource() },
            indication = null,
            onClick = onClick,
        ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        icon()
        if (count > 0) {
            Text("$count", style = CorusFont.bodyMedium, color = CorusColors.Text.copy(alpha = 0.85f))
        }
    }
}

@Composable
private fun FullPlayerCommentsSection(
    comments: List<CymbalComment>,
    repliesByParent: Map<String, List<CymbalComment>>,
    headerCount: Int,
    isBusy: Boolean,
    contentVisible: Boolean,
    likedCommentIds: Set<String>,
    commentLikeCounts: Map<String, Int>,
    interactive: Boolean,
    nowPlayingManager: NowPlayingManager,
    onOpenUser: (String) -> Unit,
    onReply: (CymbalComment) -> Unit,
    onLikeComment: (CymbalComment) -> Unit,
    onAddComment: () -> Unit,
    onMentionTap: (String) -> Unit,
    onHashtagTap: (String) -> Unit,
    onNavigateToSong: (CymbalTrack) -> Unit,
    onNavigateToFilm: (CymbalMovie) -> Unit,
    onNavigateToArtist: ((CommentAttachedArtist) -> Unit)?,
    onNavigateToAlbum: ((CommentAttachedAlbum) -> Unit)?,
    onNavigateToDirector: ((CommentAttachedDirector) -> Unit)?,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(top = 8.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = stringResource(R.string.full_player_comments_header, headerCount),
                style = CorusFont.body.copy(fontSize = 15.sp, fontWeight = FontWeight.SemiBold),
                color = CorusColors.Text.copy(alpha = 0.9f),
            )
            if (isBusy) {
                CircularProgressIndicator(
                    modifier = Modifier.size(14.dp),
                    strokeWidth = 2.dp,
                    color = CorusColors.Text.copy(alpha = 0.7f),
                )
            }
        }

        Box(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 88.dp)
                    .alpha(if (contentVisible) 1f else 0f),
            ) {
                if (comments.isEmpty() && !isBusy) {
                    Text(
                        text = stringResource(R.string.comments_no_comments),
                        style = CorusFont.bodyMedium,
                        color = CorusColors.Text.copy(alpha = 0.7f),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 28.dp),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    )
                } else {
                    comments.forEach { comment ->
                        FullPlayerCommentRow(
                            comment = comment,
                            isReply = false,
                            liked = likedCommentIds.contains(comment.id),
                            likeCount = commentLikeCounts[comment.id] ?: comment.likeCount,
                            interactive = interactive,
                            nowPlayingManager = nowPlayingManager,
                            onOpenUser = { onOpenUser(comment.user.id) },
                            onReply = { onReply(comment) },
                            onLike = { onLikeComment(comment) },
                            onMentionTap = onMentionTap,
                            onHashtagTap = onHashtagTap,
                            onNavigateToSong = onNavigateToSong,
                            onNavigateToFilm = onNavigateToFilm,
                            onNavigateToArtist = onNavigateToArtist,
                            onNavigateToAlbum = onNavigateToAlbum,
                            onNavigateToDirector = onNavigateToDirector,
                        )
                        repliesByParent[comment.id].orEmpty().forEach { reply ->
                            FullPlayerCommentRow(
                                comment = reply,
                                isReply = true,
                                liked = likedCommentIds.contains(reply.id),
                                likeCount = commentLikeCounts[reply.id] ?: reply.likeCount,
                                interactive = interactive,
                                nowPlayingManager = nowPlayingManager,
                                onOpenUser = { onOpenUser(reply.user.id) },
                                onReply = { onReply(reply) },
                                onLike = { onLikeComment(reply) },
                                onMentionTap = onMentionTap,
                                onHashtagTap = onHashtagTap,
                                onNavigateToSong = onNavigateToSong,
                                onNavigateToFilm = onNavigateToFilm,
                                onNavigateToArtist = onNavigateToArtist,
                                onNavigateToAlbum = onNavigateToAlbum,
                                onNavigateToDirector = onNavigateToDirector,
                            )
                        }
                    }
                }
            }
            if (isBusy && !contentVisible) {
                CircularProgressIndicator(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(vertical = 36.dp)
                        .size(28.dp),
                    color = CorusColors.Text.copy(alpha = 0.7f),
                    strokeWidth = 2.dp,
                )
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(top = 12.dp, bottom = 8.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(CorusColors.Text.copy(alpha = 0.08f))
                .clickable(
                    enabled = interactive,
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onAddComment,
                )
                .padding(vertical = 14.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Outlined.ChatBubbleOutline,
                contentDescription = null,
                tint = CorusColors.Text.copy(alpha = 0.9f),
                modifier = Modifier.size(14.dp),
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = stringResource(R.string.full_player_add_comment),
                style = CorusFont.body.copy(fontSize = 14.sp, fontWeight = FontWeight.SemiBold),
                color = CorusColors.Text.copy(alpha = 0.9f),
            )
        }
    }
}

@Composable
private fun FullPlayerCommentRow(
    comment: CymbalComment,
    isReply: Boolean,
    liked: Boolean,
    likeCount: Int,
    interactive: Boolean,
    nowPlayingManager: NowPlayingManager,
    onOpenUser: () -> Unit,
    onReply: () -> Unit,
    onLike: () -> Unit,
    onMentionTap: (String) -> Unit,
    onHashtagTap: (String) -> Unit,
    onNavigateToSong: (CymbalTrack) -> Unit,
    onNavigateToFilm: (CymbalMovie) -> Unit,
    onNavigateToArtist: ((CommentAttachedArtist) -> Unit)?,
    onNavigateToAlbum: ((CommentAttachedAlbum) -> Unit)?,
    onNavigateToDirector: ((CommentAttachedDirector) -> Unit)?,
) {
    val context = LocalContext.current
    val renderedText = if (comment.textIsAttachmentFallback) "" else comment.text
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = if (isReply) 44.dp else 16.dp, end = 16.dp, top = 10.dp, bottom = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        UserAvatarView(
            avatarURL = comment.user.avatarURL,
            avatarThumbURL = comment.user.avatarThumbURL,
            displayName = comment.user.displayName,
            username = comment.user.username,
            size = if (isReply) 28.dp else 34.dp,
            usesSolidLoadingPlaceholder = true,
            modifier = Modifier.clickable(
                enabled = interactive,
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onOpenUser,
            ),
        )
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = comment.user.username,
                    style = CorusFont.body.copy(fontSize = 13.sp, fontWeight = FontWeight.SemiBold),
                    color = CorusColors.Text.copy(alpha = 0.92f),
                    modifier = Modifier.clickable(
                        enabled = interactive,
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onOpenUser,
                    ),
                )
                Text(
                    text = DateUtils.relativeTime(context, comment.timestamp),
                    style = CorusFont.caption.copy(fontSize = 12.sp, fontWeight = FontWeight.Medium),
                    color = CorusColors.Text.copy(alpha = 0.45f),
                )
            }
            if (renderedText.isNotEmpty()) {
                TappableMentionText(
                    text = buildMentionAnnotatedString(
                        renderedText,
                        linkColor = CorusColors.PlayerLink,
                    ),
                    style = CorusFont.body,
                    onMentionTap = onMentionTap,
                    onHashtagTap = onHashtagTap,
                )
            }
            // iOS FullPlayerCommentsSection: GIF via AnimatedGifView, else
            // song/film/entity via CommentAttachmentCard — never both, and
            // never route a GIF into the attachment card (empty wash bar).
            val gifURL = comment.gifURL
            if (gifURL != null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = if (renderedText.isEmpty()) 0.dp else CorusSpacing.xs),
                ) {
                    AsyncImage(
                        model = ImageRequest.Builder(context)
                            .data(gifURL)
                            .build(),
                        contentDescription = stringResource(R.string.comments_cd_gif),
                        modifier = Modifier
                            .widthIn(max = 200.dp)
                            .heightIn(max = 260.dp)
                            .clip(RoundedCornerShape(12.dp)),
                        contentScale = ContentScale.Fit,
                    )
                }
            } else if (
                comment.attachedSong != null ||
                comment.attachedFilm != null ||
                comment.attachedArtist != null ||
                comment.attachedAlbum != null ||
                comment.attachedDirector != null
            ) {
                CommentAttachmentCard(
                    attachedSong = comment.attachedSong,
                    attachedFilm = comment.attachedFilm,
                    nowPlaying = nowPlayingManager,
                    onNavigateToSong = onNavigateToSong,
                    onNavigateToFilm = onNavigateToFilm,
                    attachedArtist = comment.attachedArtist,
                    attachedAlbum = comment.attachedAlbum,
                    attachedDirector = comment.attachedDirector,
                    onNavigateToArtist = onNavigateToArtist,
                    onNavigateToAlbum = onNavigateToAlbum,
                    onNavigateToDirector = onNavigateToDirector,
                    surface = CommentAttachmentSurface.PLAYER,
                    modifier = Modifier.padding(top = if (renderedText.isEmpty()) 0.dp else CorusSpacing.xs),
                )
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(top = 2.dp),
            ) {
                Row(
                    modifier = Modifier.clickable(
                        enabled = interactive,
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onLike,
                    ),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Icon(
                        imageVector = if (liked) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                        contentDescription = null,
                        modifier = Modifier.size(13.dp),
                        tint = if (liked) CorusColors.Like else CorusColors.Text.copy(alpha = 0.7f),
                    )
                    if (likeCount > 0) {
                        Text(
                            "$likeCount",
                            style = CorusFont.caption,
                            color = CorusColors.Text.copy(alpha = 0.7f),
                        )
                    }
                }
                Text(
                    text = stringResource(R.string.comments_reply),
                    style = CorusFont.caption,
                    color = CorusColors.Text.copy(alpha = 0.7f),
                    modifier = Modifier.clickable(
                        enabled = interactive,
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onReply,
                    ),
                )
            }
        }
    }
}

@Composable
private fun FullPlayerCatalogPostedBySection(
    posts: List<CymbalPost>,
    isLoading: Boolean,
    isLoadingMore: Boolean,
    error: String?,
    hasMore: Boolean,
    uniquePosterCount: Int?,
    interactive: Boolean,
    onRetry: () -> Unit,
    onLoadMore: () -> Unit,
    onOpenUser: (String) -> Unit,
    onOpenPost: (String) -> Unit,
    onComposeTrack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Skeletons only after a short delay — fast empty hits skip the flash (iOS parity).
    var showSkeleton by remember { mutableStateOf(false) }
    LaunchedEffect(isLoading, posts.isEmpty()) {
        if (isLoading && posts.isEmpty()) {
            showSkeleton = false
            delay(220)
            if (isLoading && posts.isEmpty()) showSkeleton = true
        } else {
            showSkeleton = false
        }
    }

    Column(modifier = modifier.fillMaxWidth()) {
        when {
            showSkeleton && isLoading && posts.isEmpty() -> {
                Text(
                    text = stringResource(R.string.song_detail_posted_by),
                    style = CorusFont.sectionHeader,
                    color = CorusColors.Text.copy(alpha = 0.62f),
                    modifier = Modifier
                        .padding(horizontal = 20.dp)
                        .padding(top = 8.dp, bottom = 12.dp),
                )
                repeat(4) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = CorusSpacing.sm),
                        horizontalArrangement = Arrangement.spacedBy(CorusSpacing.md),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            modifier = Modifier
                                .size(CorusSpacing.avatarMedium)
                                .clip(CircleShape)
                                .background(CorusColors.Text.copy(alpha = 0.16f)),
                        )
                        Column(verticalArrangement = Arrangement.spacedBy(CorusSpacing.xxs)) {
                            Box(
                                modifier = Modifier
                                    .width(100.dp)
                                    .height(14.dp)
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(CorusColors.Text.copy(alpha = 0.16f)),
                            )
                            Box(
                                modifier = Modifier
                                    .width(70.dp)
                                    .height(11.dp)
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(CorusColors.Text.copy(alpha = 0.16f)),
                            )
                        }
                    }
                }
            }
            isLoading && posts.isEmpty() -> {
                Spacer(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(120.dp),
                )
            }
            error != null && posts.isEmpty() -> {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Spacer(modifier = Modifier.height(24.dp))
                    Text(
                        text = stringResource(R.string.song_detail_load_error),
                        style = CorusFont.bodyMedium,
                        color = CorusColors.Text.copy(alpha = 0.58f),
                    )
                    TextButton(onClick = onRetry, enabled = interactive) {
                        Text(
                            text = stringResource(R.string.song_detail_try_again),
                            style = CorusFont.buttonSmall,
                            color = CorusColors.Accent,
                        )
                    }
                }
            }
            posts.isEmpty() -> {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Spacer(modifier = Modifier.height(28.dp))
                    Text(
                        text = stringResource(R.string.song_detail_empty),
                        style = CorusFont.body,
                        color = CorusColors.Text.copy(alpha = 0.58f),
                    )
                    Spacer(modifier = Modifier.height(CorusSpacing.md))
                    Button(
                        onClick = onComposeTrack,
                        enabled = interactive,
                        shape = RoundedCornerShape(50),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = CorusColors.Accent,
                            contentColor = Color.White,
                        ),
                    ) {
                        Text(stringResource(R.string.song_detail_be_the_first), style = CorusFont.buttonSmall)
                    }
                }
            }
            else -> {
                val count = uniquePosterCount ?: posts.map { it.user.id }.toSet().size
                Text(
                    text = pluralStringResource(R.plurals.song_detail_posted_by_count, count, formatCatalogUserCount(count)),
                    style = CorusFont.sectionHeader,
                    color = CorusColors.Text.copy(alpha = 0.62f),
                    modifier = Modifier
                        .padding(horizontal = 20.dp)
                        .padding(top = 8.dp, bottom = 12.dp),
                )
                posts.forEachIndexed { index, post ->
                    PostedByRow(
                        post = post,
                        onUserTap = { if (interactive) onOpenUser(post.user.id) },
                        onPostTap = { if (interactive) onOpenPost(post.id) },
                        // Frosted player wash — match iOS catalog primary opacities.
                        frostReadable = true,
                    )
                    if (index == posts.lastIndex && hasMore && !isLoadingMore) {
                        LaunchedEffect(post.id) { onLoadMore() }
                    }
                }
                if (isLoadingMore) {
                    CircularProgressIndicator(
                        modifier = Modifier
                            .align(Alignment.CenterHorizontally)
                            .padding(CorusSpacing.lg)
                            .size(24.dp),
                        color = CorusColors.Accent,
                        strokeWidth = 2.dp,
                    )
                }
            }
        }
    }
}

private fun formatCatalogUserCount(count: Int): String {
    return when {
        count >= 1_000_000 -> String.format("%.1fM", count / 1_000_000.0).replace(".0M", "M")
        count >= 1_000 -> String.format("%.1fK", count / 1_000.0).replace(".0K", "K")
        else -> count.toString()
    }
}
