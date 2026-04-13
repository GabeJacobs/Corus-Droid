package fm.corus.android.ui.screens.feed

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import fm.corus.android.data.model.CymbalComment
import fm.corus.android.data.model.CymbalPost
import fm.corus.android.ui.components.GifPickerSheet
import fm.corus.android.ui.components.UserAvatarView
import fm.corus.android.ui.theme.CorusColors
import fm.corus.android.ui.theme.CorusFont
import fm.corus.android.ui.theme.CorusSpacing
import fm.corus.android.ui.util.DateUtils

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SinglePostCommentsScreen(
    postId: String,
    viewModel: CommentsViewModel = hiltViewModel(),
    onBack: () -> Unit = {},
    onNavigateToUser: (String) -> Unit = {},
) {
    val comments by viewModel.comments.collectAsState()
    val repliesByParent by viewModel.repliesByParent.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val isSending by viewModel.isSending.collectAsState()
    val replyingTo by viewModel.replyingTo.collectAsState()
    val likedCommentIds by viewModel.likedCommentIds.collectAsState()
    val post by viewModel.post.collectAsState()

    var commentText by remember { mutableStateOf("") }
    val focusRequester = remember { FocusRequester() }
    var showGifPicker by remember { mutableStateOf(false) }
    val maxChars = 700

    LaunchedEffect(postId) {
        viewModel.loadComments(postId)
        viewModel.loadPost(postId)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Comments", style = CorusFont.screenTitle, color = CorusColors.Text) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = CorusColors.Text)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White),
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
                        .padding(horizontal = CorusSpacing.lg, vertical = CorusSpacing.sm),
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
                    IconButton(onClick = { showGifPicker = true }) {
                        Icon(
                            Icons.Filled.Gif,
                            contentDescription = "Send GIF",
                            tint = CorusColors.Accent,
                            modifier = Modifier.size(28.dp),
                        )
                    }
                    IconButton(
                        onClick = {
                            if (commentText.isNotBlank() && !isSending) {
                                viewModel.addComment(postId, commentText.trim())
                                commentText = ""
                            }
                        },
                        enabled = commentText.isNotBlank() && !isSending,
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.Send,
                            contentDescription = "Send",
                            tint = if (commentText.isNotBlank()) CorusColors.Accent else CorusColors.Tertiary,
                        )
                    }
                }
            }
        },
    ) { padding ->
        if (showGifPicker) {
            GifPickerSheet(
                onGifSelected = { gif ->
                    viewModel.sendGifComment(gif.gifURL)
                    showGifPicker = false
                },
                onDismiss = { showGifPicker = false },
            )
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            // Post header section
            post?.let { p ->
                item {
                    PostHeaderSection(
                        post = p,
                        onUserTap = { onNavigateToUser(p.user.id) },
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
                        onLike = { viewModel.toggleCommentLike(postId, comment.id) },
                        onReply = { viewModel.setReplyTo(comment) },
                        onUserTap = { onNavigateToUser(comment.user.id) },
                        onReplyLike = { replyId -> viewModel.toggleCommentLike(postId, replyId) },
                    )
                }
            }
        }
    }
}

@Composable
private fun PostHeaderSection(
    post: CymbalPost,
    onUserTap: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onUserTap)
            .padding(CorusSpacing.lg),
        verticalAlignment = Alignment.Top,
    ) {
        UserAvatarView(avatarURL = post.user.avatarURL, size = CorusSpacing.avatarMedium)
        Spacer(modifier = Modifier.width(CorusSpacing.md))
        Column(modifier = Modifier.weight(1f)) {
            Text(post.user.displayName, style = CorusFont.bodyMedium, color = CorusColors.Text)
            if (!post.caption.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(CorusSpacing.xs))
                Text(post.caption, style = CorusFont.body, color = CorusColors.Text)
            }
            Spacer(modifier = Modifier.height(CorusSpacing.xs))
            Text(DateUtils.relativeTime(post.timestamp), style = CorusFont.caption, color = CorusColors.Tertiary)
        }
        if (post.displayImageURL != null) {
            Spacer(modifier = Modifier.width(CorusSpacing.md))
            AsyncImage(
                model = post.displayImageURL,
                contentDescription = null,
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(4.dp)),
                contentScale = ContentScale.Crop,
            )
        }
    }
}

@Composable
private fun SingleCommentRow(
    comment: CymbalComment,
    isLiked: Boolean,
    replies: List<CymbalComment>,
    likedCommentIds: Set<String>,
    onLike: () -> Unit,
    onReply: () -> Unit,
    onUserTap: () -> Unit,
    onReplyLike: (String) -> Unit,
) {
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = CorusSpacing.lg, vertical = CorusSpacing.sm),
        ) {
            UserAvatarView(
                avatarURL = comment.user.avatarURL,
                size = 32.dp,
                modifier = Modifier.clickable(onClick = onUserTap),
            )
            Spacer(modifier = Modifier.width(CorusSpacing.sm))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(comment.user.username, style = CorusFont.captionMedium, color = CorusColors.Text)
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
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 56.dp, end = CorusSpacing.lg, top = CorusSpacing.xxs, bottom = CorusSpacing.xxs),
            ) {
                UserAvatarView(avatarURL = reply.user.avatarURL, size = 24.dp)
                Spacer(modifier = Modifier.width(CorusSpacing.sm))
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(reply.user.username, style = CorusFont.captionMedium, color = CorusColors.Text)
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
