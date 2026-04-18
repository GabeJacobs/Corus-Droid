package fm.corus.android.ui.screens.feed

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Gif
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import fm.corus.android.data.model.CymbalComment
import fm.corus.android.data.model.CymbalPost
import fm.corus.android.data.model.CymbalTrack
import fm.corus.android.ui.components.GifPickerSheet
import fm.corus.android.ui.components.PostCard
import fm.corus.android.ui.components.UserAvatarView
import fm.corus.android.ui.theme.CorusColors
import fm.corus.android.ui.theme.CorusFont
import fm.corus.android.ui.theme.CorusSpacing
import fm.corus.android.ui.util.DateUtils
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SinglePostCommentsScreen(
    postId: String,
    highlightCommentId: String? = null,
    viewModel: CommentsViewModel = hiltViewModel(),
    onBack: () -> Unit = {},
    onNavigateToUser: (String) -> Unit = {},
    onNavigateToSong: (CymbalTrack) -> Unit = {},
    onNavigateToFilm: (String) -> Unit = {},
    onNavigateToHashtag: (String) -> Unit = {},
    onNavigateToLikes: (String) -> Unit = {},
) {
    val comments by viewModel.comments.collectAsState()
    val repliesByParent by viewModel.repliesByParent.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val isSending by viewModel.isSending.collectAsState()
    val replyingTo by viewModel.replyingTo.collectAsState()
    val likedCommentIds by viewModel.likedCommentIds.collectAsState()
    val post by viewModel.post.collectAsState()
    val engagementStates by viewModel.engagementStates.collectAsState()
    val currentUserProfile by viewModel.currentUserProfile.collectAsState()
    val nowPlayingState by viewModel.nowPlayingManager.state.collectAsState()
    val loadingTrackId by viewModel.nowPlayingManager.loadingTrackId.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var commentText by remember { mutableStateOf("") }
    val focusRequester = remember { FocusRequester() }
    var showGifPicker by remember { mutableStateOf(false) }
    val maxChars = 700
    val listState = rememberLazyListState()

    // Highlight flash state — matches iOS 1.5s flash then fade
    var activeHighlightId by remember { mutableStateOf<String?>(null) }
    var hasScrolledToTarget by remember { mutableStateOf(false) }

    LaunchedEffect(postId) {
        viewModel.loadComments(postId)
        viewModel.loadPost(postId)
    }

    // Scroll to target comment once comments are loaded
    LaunchedEffect(comments, highlightCommentId) {
        if (highlightCommentId == null || comments.isEmpty() || hasScrolledToTarget) return@LaunchedEffect
        // Build flat list of IDs in display order to find the index
        val flatIds = mutableListOf<String>()
        // Index 0 is the post header item
        flatIds.add("header")
        for (comment in comments) {
            flatIds.add(comment.id)
            repliesByParent[comment.id]?.forEach { reply -> flatIds.add(reply.id) }
        }
        val targetIndex = flatIds.indexOf(highlightCommentId)
        if (targetIndex >= 0) {
            listState.animateScrollToItem(targetIndex)
            hasScrolledToTarget = true
            activeHighlightId = highlightCommentId
            delay(1500)
            activeHighlightId = null
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Corus", style = CorusFont.screenTitle, color = CorusColors.Text) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = CorusColors.Text)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White),
                windowInsets = WindowInsets(0, 0, 0, 0),
            )
        },
        bottomBar = {
            Column {
                // Reply indicator
                if (replyingTo != null) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(CorusColors.CardBackground)
                            .padding(horizontal = CorusSpacing.lg, vertical = CorusSpacing.sm),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            "Replying to @${replyingTo?.user?.username}",
                            style = CorusFont.caption,
                            color = CorusColors.Secondary,
                            modifier = Modifier.weight(1f),
                        )
                        IconButton(
                            onClick = { viewModel.clearReply() },
                            modifier = Modifier.size(20.dp),
                        ) {
                            Icon(Icons.Filled.Close, contentDescription = "Cancel reply", tint = CorusColors.Tertiary, modifier = Modifier.size(14.dp))
                        }
                    }
                }

                // Input bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color.White)
                        .navigationBarsPadding()
                        .padding(horizontal = CorusSpacing.lg, vertical = CorusSpacing.sm)
                        .imePadding(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextField(
                        value = commentText,
                        onValueChange = { if (it.length <= maxChars) commentText = it },
                        placeholder = { Text("Add a comment...", style = CorusFont.body, color = CorusColors.Tertiary) },
                        modifier = Modifier
                            .weight(1f)
                            .focusRequester(focusRequester),
                        textStyle = CorusFont.body,
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent,
                        ),
                        singleLine = false,
                        maxLines = 4,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                        keyboardActions = KeyboardActions(onSend = {
                            if (commentText.isNotBlank() && !isSending) {
                                viewModel.addComment(postId, commentText.trim())
                                commentText = ""
                            }
                        }),
                    )
                    if (viewModel.giphySupport) {
                        IconButton(onClick = { showGifPicker = true }) {
                            Icon(
                                Icons.Filled.Gif,
                                contentDescription = "Send GIF",
                                tint = CorusColors.Accent,
                                modifier = Modifier.size(28.dp),
                            )
                        }
                    }
                    val canSend = commentText.isNotBlank() && !isSending
                    Spacer(modifier = Modifier.width(CorusSpacing.sm))
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(if (canSend) CorusColors.Accent else CorusColors.Divider)
                            .clickable(enabled = canSend) {
                                viewModel.addComment(postId, commentText.trim())
                                commentText = ""
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Filled.ArrowUpward,
                            contentDescription = "Send",
                            modifier = Modifier.size(18.dp),
                            tint = if (canSend) Color.White else CorusColors.Tertiary,
                        )
                    }
                }
            }
        },
    ) { padding ->
        if (viewModel.giphySupport && showGifPicker) {
            GifPickerSheet(
                onGifSelected = { gif ->
                    viewModel.sendGifComment(gif.gifURL)
                    showGifPicker = false
                },
                onDismiss = { showGifPicker = false },
            )
        }

        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            // Full post card (matches iOS SinglePostCommentsView)
            post?.let { p ->
                item(key = "header") {
                    val engagement = engagementStates[p.id]
                    val likeCount = engagement?.likeCount ?: p.likeCount
                    val commentCount = engagement?.commentCount ?: p.commentCount
                    val isLiked = engagement?.isLiked ?: p.isLiked
                    val isSaved = engagement?.isSaved ?: false

                    PostCard(
                        post = p,
                        likeCount = likeCount,
                        commentCount = commentCount,
                        isLiked = isLiked,
                        isSaved = isSaved,
                        currentUser = currentUserProfile,
                        isPreviewLoading = p.isTrack && loadingTrackId == p.track.id,
                        isPreviewPlaying = p.isTrack && nowPlayingState.trackId == p.track.id && nowPlayingState.isPlaying,
                        hideComments = true,
                        onLikeTap = { viewModel.togglePostLike(p.id) },
                        onSaveTap = { viewModel.togglePostSave(p.id) },
                        onUserTap = { onNavigateToUser(p.user.id) },
                        onPreviewTap = {
                            if (p.isMovie) {
                                p.movieId?.let { onNavigateToFilm(it) }
                            } else {
                                viewModel.playPreview(p)
                            }
                        },
                        onTrailerTap = {
                            p.trailerURL?.let { url ->
                                try { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) } catch (_: Exception) { }
                            }
                        },
                        onCommentTap = {
                            scope.launch { listState.animateScrollToItem(1) }
                        },
                        onShareTap = {
                            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_TEXT, "https://corus.fm/post/${p.id}")
                            }
                            try { context.startActivity(Intent.createChooser(shareIntent, "Share")) } catch (_: Exception) { }
                        },
                        onSpotifyTap = {
                            val spotifyUri = p.track.spotifyURI
                            val spotifyWeb = p.track.spotifyWebURL
                            val uri = spotifyUri.ifBlank { spotifyWeb }
                            if (uri.isNotBlank()) {
                                try { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(uri))) } catch (_: Exception) { }
                            }
                        },
                        onLikesTap = { onNavigateToLikes(p.id) },
                        onMentionTap = { username ->
                            scope.launch {
                                val userId = viewModel.resolveUsernameToId(username.removePrefix("@"))
                                if (userId != null) onNavigateToUser(userId)
                            }
                        },
                        onHashtagTap = { hashtag -> onNavigateToHashtag(hashtag.removePrefix("#")) },
                        onSongCountTap = {
                            if (p.isMovie) {
                                p.movieId?.let { onNavigateToFilm(it) }
                            } else {
                                onNavigateToSong(p.track)
                            }
                        },
                        onFilmPageTap = { p.movieId?.let { onNavigateToFilm(it) } },
                    )
                    HorizontalDivider(color = CorusColors.Divider, thickness = 0.5.dp)
                }
            }

            if (isLoading) {
                item {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(CorusSpacing.xxl),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator(color = CorusColors.Accent)
                    }
                }
            } else if (comments.isEmpty()) {
                item {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(CorusSpacing.xxl),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text("No comments yet", style = CorusFont.bodyMedium, color = CorusColors.Secondary)
                        Spacer(modifier = Modifier.height(CorusSpacing.xs))
                        Text("Be the first to comment!", style = CorusFont.caption, color = CorusColors.Tertiary)
                    }
                }
            } else {
                items(comments, key = { it.id }) { comment ->
                    SingleCommentRow(
                        comment = comment,
                        isLiked = likedCommentIds.contains(comment.id),
                        replies = repliesByParent[comment.id] ?: emptyList(),
                        likedCommentIds = likedCommentIds,
                        highlightedCommentId = activeHighlightId,
                        onLike = { viewModel.toggleCommentLike(postId, comment.id) },
                        onReply = {
                            viewModel.setReplyTo(comment)
                            focusRequester.requestFocus()
                        },
                        onUserTap = { onNavigateToUser(comment.user.id) },
                        onReplyUserTap = { userId -> onNavigateToUser(userId) },
                        onReplyReply = { reply ->
                            viewModel.setReplyTo(reply)
                            focusRequester.requestFocus()
                        },
                        onReplyLike = { replyId -> viewModel.toggleCommentLike(postId, replyId) },
                    )
                }
            }
        }
    }
}

@Composable
private fun SingleCommentRow(
    comment: CymbalComment,
    isLiked: Boolean,
    replies: List<CymbalComment>,
    likedCommentIds: Set<String>,
    highlightedCommentId: String? = null,
    onLike: () -> Unit,
    onReply: () -> Unit,
    onUserTap: () -> Unit,
    onReplyUserTap: (String) -> Unit,
    onReplyReply: (CymbalComment) -> Unit,
    onReplyLike: (String) -> Unit,
) {
    val commentHighlightColor by animateColorAsState(
        targetValue = if (highlightedCommentId == comment.id) CorusColors.Accent.copy(alpha = 0.1f) else Color.Transparent,
        animationSpec = tween(durationMillis = if (highlightedCommentId == comment.id) 200 else 500),
        label = "commentHighlight",
    )
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(commentHighlightColor)
                .padding(horizontal = CorusSpacing.lg, vertical = CorusSpacing.sm),
        ) {
            UserAvatarView(
                avatarURL = comment.user.avatarURL,
                displayName = comment.user.displayName,
                size = 32.dp,
                modifier = Modifier.clickable(onClick = onUserTap),
            )
            Spacer(modifier = Modifier.width(CorusSpacing.sm))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        comment.user.username,
                        style = CorusFont.captionMedium,
                        color = CorusColors.Text,
                        modifier = Modifier.clickable(onClick = onUserTap),
                    )
                    Spacer(modifier = Modifier.width(CorusSpacing.sm))
                    Text(DateUtils.relativeTime(comment.timestamp), style = CorusFont.caption, color = CorusColors.Tertiary)
                }
                Spacer(modifier = Modifier.height(CorusSpacing.xxs))
                if (comment.gifURL != null) {
                    AsyncImage(
                        model = ImageRequest.Builder(androidx.compose.ui.platform.LocalContext.current)
                            .data(comment.gifURL).build(),
                        contentDescription = "GIF",
                        modifier = Modifier
                            .widthIn(max = 200.dp)
                            .heightIn(max = 150.dp)
                            .clip(RoundedCornerShape(12.dp)),
                        contentScale = ContentScale.Fit,
                    )
                } else {
                    Text(comment.text, style = CorusFont.body, color = CorusColors.Text)
                }
                Spacer(modifier = Modifier.height(CorusSpacing.xs))
                Row(horizontalArrangement = Arrangement.spacedBy(CorusSpacing.lg)) {
                    Text(
                        "Reply",
                        style = CorusFont.captionMedium,
                        color = CorusColors.Secondary,
                        modifier = Modifier.clickable(onClick = onReply),
                    )
                }
            }
            IconButton(onClick = onLike, modifier = Modifier.size(32.dp)) {
                Icon(
                    if (isLiked) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                    contentDescription = "Like",
                    tint = if (isLiked) CorusColors.Like else CorusColors.Tertiary,
                    modifier = Modifier.size(16.dp),
                )
            }
        }

        // Replies
        replies.forEach { reply ->
            val replyHighlightColor by animateColorAsState(
                targetValue = if (highlightedCommentId == reply.id) CorusColors.Accent.copy(alpha = 0.1f) else Color.Transparent,
                animationSpec = tween(durationMillis = if (highlightedCommentId == reply.id) 200 else 500),
                label = "replyHighlight",
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(replyHighlightColor)
                    .padding(start = 56.dp, end = CorusSpacing.lg, top = CorusSpacing.xxs, bottom = CorusSpacing.xxs),
            ) {
                UserAvatarView(
                    avatarURL = reply.user.avatarURL,
                    displayName = reply.user.displayName,
                    size = 24.dp,
                    modifier = Modifier.clickable { onReplyUserTap(reply.user.id) },
                )
                Spacer(modifier = Modifier.width(CorusSpacing.sm))
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            reply.user.username,
                            style = CorusFont.captionMedium,
                            color = CorusColors.Text,
                            modifier = Modifier.clickable { onReplyUserTap(reply.user.id) },
                        )
                        Spacer(modifier = Modifier.width(CorusSpacing.sm))
                        Text(DateUtils.relativeTime(reply.timestamp), style = CorusFont.caption, color = CorusColors.Tertiary)
                    }
                    Spacer(modifier = Modifier.height(CorusSpacing.xxs))
                    if (reply.gifURL != null) {
                        AsyncImage(
                            model = ImageRequest.Builder(androidx.compose.ui.platform.LocalContext.current)
                                .data(reply.gifURL).build(),
                            contentDescription = "GIF",
                            modifier = Modifier
                                .widthIn(max = 160.dp)
                                .heightIn(max = 120.dp)
                                .clip(RoundedCornerShape(8.dp)),
                            contentScale = ContentScale.Fit,
                        )
                    } else {
                        Text(reply.text, style = CorusFont.body, color = CorusColors.Text)
                    }
                    Spacer(modifier = Modifier.height(CorusSpacing.xs))
                    Row(horizontalArrangement = Arrangement.spacedBy(CorusSpacing.lg)) {
                        Text(
                            "Reply",
                            style = CorusFont.captionMedium,
                            color = CorusColors.Secondary,
                            modifier = Modifier.clickable { onReplyReply(reply) },
                        )
                    }
                }
                IconButton(onClick = { onReplyLike(reply.id) }, modifier = Modifier.size(24.dp)) {
                    Icon(
                        if (likedCommentIds.contains(reply.id)) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                        contentDescription = "Like",
                        tint = if (likedCommentIds.contains(reply.id)) CorusColors.Like else CorusColors.Tertiary,
                        modifier = Modifier.size(12.dp),
                    )
                }
            }
        }
    }
}
