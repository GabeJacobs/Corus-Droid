package fm.corus.android.ui.screens.feed

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.foundation.text.ClickableText
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.content.Intent
import android.net.Uri
import androidx.hilt.navigation.compose.hiltViewModel
import coil3.compose.AsyncImage
import kotlinx.coroutines.launch
import fm.corus.android.data.model.CymbalComment
import fm.corus.android.data.model.CymbalPost
import fm.corus.android.R
import fm.corus.android.ui.components.LikedBySection
import fm.corus.android.ui.components.UserAvatarView
import fm.corus.android.ui.components.UsernameWithFlair
import fm.corus.android.ui.components.VoiceNotePlayerView
import fm.corus.android.ui.theme.CorusColors
import fm.corus.android.ui.theme.CorusFont
import fm.corus.android.ui.theme.CorusSpacing
import fm.corus.android.ui.theme.NunitoFamily
import fm.corus.android.ui.util.DateUtils

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PostDetailScreen(
    postId: String,
    viewModel: PostDetailViewModel = hiltViewModel(),
    onBack: () -> Unit = {},
    onNavigateToUser: (String) -> Unit = {},
    onNavigateToComments: (String) -> Unit = {},
    onNavigateToLikes: (String) -> Unit = {},
    onNavigateToSong: (String, String?) -> Unit = { _, _ -> },
    onNavigateToFilm: (String) -> Unit = {},
    onNavigateToHashtag: (String) -> Unit = {},
) {
    val post by viewModel.post.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val comments by viewModel.comments.collectAsState()
    val engagementStates by viewModel.engagementStates.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showEditCaption by remember { mutableStateOf(false) }

    LaunchedEffect(postId) {
        viewModel.loadPost(postId)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text("corus", style = CorusFont.screenTitle, color = CorusColors.Text)
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = CorusColors.Text,
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White),
            )
        },
    ) { padding ->
        when {
            isLoading && post == null -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(color = CorusColors.Accent)
                }
            }
            post == null -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("Post not found", style = CorusFont.body, color = CorusColors.Secondary)
                }
            }
            else -> {
                val currentPost = post!!
                val engagement = engagementStates[currentPost.id]
                val likeCount = engagement?.likeCount ?: currentPost.likeCount
                val commentCount = engagement?.commentCount ?: currentPost.commentCount
                val isLiked = engagement?.isLiked ?: currentPost.isLiked
                val isSaved = engagement?.isSaved ?: false

                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(top = padding.calculateTopPadding()),
                    contentPadding = PaddingValues(bottom = padding.calculateBottomPadding()),
                ) {
                    // Post header
                    item {
                        PostDetailHeader(
                            post = currentPost,
                            isOwnPost = currentPost.user.id == viewModel.currentUserId,
                            onUserTap = { onNavigateToUser(currentPost.user.id) },
                            onEditCaption = { showEditCaption = true },
                            onDelete = { showDeleteConfirm = true },
                        )
                    }

                    // Album art with double-tap to like
                    item {
                        PostDetailAlbumArt(
                            post = currentPost,
                            onDoubleTap = { viewModel.toggleLike(currentPost.id) },
                            onSongTap = {
                                if (currentPost.isMovie) {
                                    currentPost.movieId?.let { onNavigateToFilm(it) }
                                } else {
                                    onNavigateToSong(currentPost.track.id, currentPost.track.albumArtURL)
                                }
                            },
                        )
                    }

                    // Song info row
                    item {
                        PostDetailSongInfo(
                            post = currentPost,
                            onSongTap = {
                                if (currentPost.isMovie) {
                                    currentPost.movieId?.let { onNavigateToFilm(it) }
                                } else {
                                    onNavigateToSong(currentPost.track.id, currentPost.track.albumArtURL)
                                }
                            },
                            onSpotifyTap = {
                                val spotifyUri = currentPost.track.spotifyURI
                                val spotifyWeb = currentPost.track.spotifyWebURL
                                val uri = spotifyUri.ifBlank { spotifyWeb }
                                if (uri.isNotBlank()) {
                                    try { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(uri))) } catch (_: Exception) { }
                                }
                            },
                            onTrailerTap = {
                                currentPost.trailerURL?.let { url ->
                                    try { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) } catch (_: Exception) { }
                                }
                            },
                        )
                    }

                    // Engagement row
                    item {
                        PostDetailEngagementRow(
                            likeCount = likeCount,
                            commentCount = commentCount,
                            repostCount = engagement?.repostCount ?: currentPost.repostCount,
                            isLiked = isLiked,
                            isSaved = isSaved,
                            trackPostCount = currentPost.trackPostCount ?: 0,
                            onLikeTap = { viewModel.toggleLike(currentPost.id) },
                            onCommentTap = { onNavigateToComments(currentPost.id) },
                            onShareTap = {
                                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(Intent.EXTRA_TEXT, "https://corus.fm/post/${currentPost.id}")
                                }
                                try { context.startActivity(Intent.createChooser(shareIntent, "Share")) } catch (_: Exception) { }
                            },
                            onSaveTap = { viewModel.toggleSave(currentPost.id) },
                            onSongCountTap = {
                                if (currentPost.isMovie) {
                                    currentPost.movieId?.let { onNavigateToFilm(it) }
                                } else {
                                    onNavigateToSong(currentPost.track.id, currentPost.track.albumArtURL)
                                }
                            },
                        )
                    }

                    // Liked by section
                    if (likeCount > 0 && currentPost.likers.isNotEmpty()) {
                        item {
                            LikedBySection(
                                likers = currentPost.likers,
                                likeCount = likeCount,
                                onLikesTap = { onNavigateToLikes(currentPost.id) },
                            )
                        }
                    }

                    // Caption or Voice Note
                    if (!currentPost.voiceNoteURL.isNullOrBlank()) {
                        item {
                            VoiceNotePlayerView(
                                voiceNoteURL = currentPost.voiceNoteURL!!,
                                username = currentPost.user.username,
                                onUsernameTap = { onNavigateToUser(currentPost.user.id) },
                                modifier = Modifier
                                    .padding(horizontal = CorusSpacing.lg)
                                    .padding(bottom = CorusSpacing.xs),
                            )
                        }
                    } else if (!currentPost.caption.isNullOrBlank()) {
                        item {
                            PostDetailCaption(
                                username = currentPost.user.username,
                                caption = currentPost.caption,
                                onHashtagTap = onNavigateToHashtag,
                                onMentionTap = { username ->
                                    scope.launch {
                                        val userId = viewModel.resolveUsernameToId(username.removePrefix("@"))
                                        if (userId != null) {
                                            onNavigateToUser(userId)
                                        }
                                    }
                                },
                            )
                        }
                    }

                    // Inline comments (max 3)
                    val previewComments = (comments.ifEmpty { currentPost.comments }).take(3)
                    if (previewComments.isNotEmpty()) {
                        item {
                            if (commentCount > previewComments.size) {
                                Text(
                                    text = "View all $commentCount comments",
                                    style = CorusFont.body,
                                    color = CorusColors.Secondary,
                                    modifier = Modifier
                                        .clickable { onNavigateToComments(currentPost.id) }
                                        .padding(horizontal = CorusSpacing.lg)
                                        .padding(top = CorusSpacing.xs),
                                )
                            }
                        }
                        items(previewComments, key = { it.id }) { comment ->
                            InlineCommentRow(
                                comment = comment,
                                onUserTap = { onNavigateToUser(comment.user.id) },
                            )
                        }
                    }

                    // Timestamp
                    item {
                        Text(
                            text = DateUtils.relativeTime(currentPost.timestamp),
                            style = CorusFont.caption,
                            color = CorusColors.Secondary,
                            modifier = Modifier
                                .padding(horizontal = CorusSpacing.lg)
                                .padding(top = CorusSpacing.xs, bottom = CorusSpacing.sm),
                        )
                    }
                }
            }
        }
    }

    // Delete post confirmation
    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete Post?", style = CorusFont.songTitleLarge) },
            text = {
                Text(
                    "This will permanently delete this post and all its likes and comments.",
                    style = CorusFont.body,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteConfirm = false
                    viewModel.deletePost(postId)
                    onBack()
                }) {
                    Text("Delete", color = CorusColors.Error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text("Cancel")
                }
            },
        )
    }

    // Edit caption sheet
    if (showEditCaption && post != null) {
        EditCaptionSheet(
            postId = postId,
            initialCaption = post!!.caption ?: "",
            albumArtURL = post!!.displayImageURL,
            onDismiss = { showEditCaption = false },
            onSaved = { _ ->
                showEditCaption = false
                viewModel.loadPost(postId)
            },
        )
    }
}

@Composable
private fun PostDetailHeader(
    post: CymbalPost,
    isOwnPost: Boolean,
    onUserTap: () -> Unit,
    onEditCaption: () -> Unit = {},
    onDelete: () -> Unit = {},
) {
    var showMenu by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = CorusSpacing.lg, vertical = CorusSpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        UserAvatarView(
            avatarURL = post.user.avatarURL,
            size = CorusSpacing.avatarMedium,
            modifier = Modifier.clickable(onClick = onUserTap),
        )
        Spacer(modifier = Modifier.width(CorusSpacing.sm))

        Column(
            modifier = Modifier
                .weight(1f)
                .clickable(onClick = onUserTap),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            UsernameWithFlair(
                username = post.user.username,
                isBot = post.user.isBot,
                isVerified = post.user.isVerified,
                isClubMember = post.user.isClubMember,
                flairStyle = post.user.flairStyle,
                style = CorusFont.username,
                color = CorusColors.Text,
            )

            // Repost indicator
            if (!post.repostedFromUsername.isNullOrEmpty()) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(3.dp),
                ) {
                    Icon(
                        imageVector = Icons.Filled.Repeat,
                        contentDescription = null,
                        modifier = Modifier.size(10.dp),
                        tint = CorusColors.Secondary,
                    )
                    Text(
                        text = "reposted from @${post.repostedFromUsername}",
                        style = CorusFont.caption,
                        color = CorusColors.Secondary,
                    )
                }
            }
        }

        // Menu for own posts
        if (isOwnPost) {
            Box {
                Icon(
                    Icons.Filled.MoreHoriz,
                    contentDescription = "More options",
                    modifier = Modifier
                        .size(14.dp)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = { showMenu = true },
                        ),
                    tint = CorusColors.Secondary,
                )
                DropdownMenu(
                    expanded = showMenu,
                    onDismissRequest = { showMenu = false },
                ) {
                    DropdownMenuItem(
                        text = { Text("Edit Caption", style = CorusFont.body) },
                        onClick = {
                            showMenu = false
                            onEditCaption()
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("Delete", style = CorusFont.body, color = CorusColors.Error) },
                        onClick = {
                            showMenu = false
                            onDelete()
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun PostDetailAlbumArt(
    post: CymbalPost,
    onDoubleTap: () -> Unit,
    onSongTap: () -> Unit,
) {
    var showHeart by remember { mutableStateOf(false) }
    val heartAlpha by animateFloatAsState(
        targetValue = if (showHeart) 1f else 0f,
        animationSpec = tween(durationMillis = if (showHeart) 100 else 400),
        label = "heartAlpha",
    )

    LaunchedEffect(showHeart) {
        if (showHeart) {
            kotlinx.coroutines.delay(600)
            showHeart = false
        }
    }

    val aspectRatio = if (post.isMovie) 2f / 3f else 1f

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(aspectRatio)
            .pointerInput(Unit) {
                detectTapGestures(
                    onDoubleTap = {
                        onDoubleTap()
                        showHeart = true
                    },
                    onTap = { onSongTap() },
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        AsyncImage(
            model = post.displayImageLargeURL ?: post.displayImageURL,
            contentDescription = post.displayTitle,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
        )

        // Heart animation overlay
        Icon(
            imageVector = Icons.Filled.Favorite,
            contentDescription = null,
            modifier = Modifier
                .size(80.dp)
                .alpha(heartAlpha),
            tint = Color.White,
        )
    }
}

@Composable
private fun PostDetailSongInfo(
    post: CymbalPost,
    onSongTap: () -> Unit,
    onSpotifyTap: () -> Unit = {},
    onTrailerTap: () -> Unit = {},
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = CorusSpacing.lg, end = CorusSpacing.lg, top = CorusSpacing.md, bottom = CorusSpacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .clickable(onClick = onSongTap),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = post.displayTitle,
                style = CorusFont.songTitle,
                color = CorusColors.Text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = post.displaySubtitle,
                style = CorusFont.artistName,
                color = CorusColors.Secondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        Spacer(modifier = Modifier.width(CorusSpacing.sm))

        // Spotify or trailer button — YouTube red play icon, matching iOS
        if (post.isMovie) {
            Image(
                painter = painterResource(R.drawable.ic_play_rectangle_fill),
                contentDescription = "Watch Trailer",
                modifier = Modifier
                    .size(22.dp)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onTrailerTap,
                    ),
            )
        } else {
            Box(
                modifier = Modifier
                    .size(22.dp)
                    .clip(CircleShape)
                    .background(CorusColors.SpotifyGreen)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onSpotifyTap,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Text("S", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun PostDetailEngagementRow(
    likeCount: Int,
    commentCount: Int,
    repostCount: Int,
    isLiked: Boolean,
    isSaved: Boolean,
    trackPostCount: Int,
    onLikeTap: () -> Unit,
    onCommentTap: () -> Unit,
    onShareTap: () -> Unit,
    onSaveTap: () -> Unit,
    onSongCountTap: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 12.dp, end = CorusSpacing.lg, top = CorusSpacing.sm, bottom = CorusSpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(CorusSpacing.lg),
    ) {
        // Like
        EngagementButton(
            icon = if (isLiked) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
            count = likeCount,
            tint = if (isLiked) CorusColors.Like else CorusColors.Secondary,
            onClick = onLikeTap,
        )

        // Comment
        EngagementButton(
            icon = Icons.Outlined.ChatBubbleOutline,
            count = commentCount,
            tint = CorusColors.Secondary,
            onClick = onCommentTap,
        )

        // Share/Repost
        EngagementButton(
            icon = Icons.AutoMirrored.Filled.Send,
            count = repostCount,
            tint = CorusColors.Secondary,
            onClick = onShareTap,
        )

        // Track post count
        if (trackPostCount > 0) {
            EngagementButton(
                icon = Icons.Filled.MusicNote,
                count = trackPostCount,
                tint = CorusColors.Secondary,
                onClick = onSongCountTap,
            )
        }

        Spacer(modifier = Modifier.weight(1f))

        // Save
        Box(
            modifier = Modifier
                .size(CorusSpacing.touchTarget)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onSaveTap,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = if (isSaved) Icons.Filled.Bookmark else Icons.Outlined.BookmarkBorder,
                contentDescription = "Save",
                modifier = Modifier.size(20.dp),
                tint = CorusColors.Text,
            )
        }
    }
}

@Composable
private fun EngagementButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    count: Int,
    tint: Color,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .height(CorusSpacing.touchTarget)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(22.dp),
            tint = tint,
        )
        if (count > 0) {
            Spacer(modifier = Modifier.width(CorusSpacing.xs))
            Text(
                text = count.toString(),
                style = CorusFont.bodyMedium,
                color = CorusColors.Text,
            )
        }
    }
}


@Composable
private fun PostDetailCaption(
    username: String,
    caption: String,
    onHashtagTap: (String) -> Unit,
    onMentionTap: (String) -> Unit,
) {
    val captionText = buildAnnotatedString {
        withStyle(
            SpanStyle(
                fontFamily = NunitoFamily,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 14.sp,
                color = CorusColors.Text,
            )
        ) {
            append(username)
        }
        append(" ")

        val regex = Regex("(@\\w+)|(#\\w+)")
        var lastIndex = 0
        regex.findAll(caption).forEach { match ->
            if (match.range.first > lastIndex) {
                withStyle(SpanStyle(color = CorusColors.Text)) {
                    append(caption.substring(lastIndex, match.range.first))
                }
            }
            val token = match.value
            val tag = if (token.startsWith("@")) "mention" else "hashtag"
            pushStringAnnotation(tag = tag, annotation = token)
            if (token.startsWith("@")) {
                withStyle(SpanStyle(color = CorusColors.Accent, fontWeight = FontWeight.ExtraBold)) {
                    append(token)
                }
            } else {
                withStyle(SpanStyle(color = CorusColors.Accent)) {
                    append(token)
                }
            }
            pop()
            lastIndex = match.range.last + 1
        }
        if (lastIndex < caption.length) {
            withStyle(SpanStyle(color = CorusColors.Text)) {
                append(caption.substring(lastIndex))
            }
        }
    }

    @Suppress("DEPRECATION")
    ClickableText(
        text = captionText,
        style = CorusFont.body,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = CorusSpacing.lg)
            .padding(bottom = CorusSpacing.xs),
        onClick = { offset ->
            captionText.getStringAnnotations("mention", offset, offset).firstOrNull()?.let {
                onMentionTap(it.item)
            }
            captionText.getStringAnnotations("hashtag", offset, offset).firstOrNull()?.let {
                onHashtagTap(it.item.removePrefix("#"))
            }
        },
    )
}

@Composable
private fun InlineCommentRow(
    comment: CymbalComment,
    onUserTap: () -> Unit,
) {
    val commentText = buildAnnotatedString {
        withStyle(
            SpanStyle(
                fontWeight = FontWeight.ExtraBold,
                fontSize = 14.sp,
            )
        ) {
            append(comment.user.username)
        }
        append(" ")
        withStyle(
            SpanStyle(
                fontWeight = FontWeight.Normal,
                fontSize = 15.sp,
            )
        ) {
            append(comment.text)
        }
    }

    Text(
        text = commentText,
        style = CorusFont.body,
        color = CorusColors.Text,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = CorusSpacing.lg, vertical = CorusSpacing.xxs)
            .clickable(onClick = onUserTap),
    )
}
