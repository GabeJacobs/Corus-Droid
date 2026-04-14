package fm.corus.android.ui.screens.feed

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import fm.corus.android.data.model.CymbalComment
import fm.corus.android.data.model.CymbalUser
import fm.corus.android.ui.components.GifPickerSheet
import fm.corus.android.ui.components.UserAvatarView
import fm.corus.android.ui.theme.CorusColors
import fm.corus.android.ui.theme.CorusFont
import fm.corus.android.ui.theme.CorusSpacing
import fm.corus.android.ui.util.DateUtils

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CommentsBottomSheet(
    postId: String,
    onDismiss: () -> Unit,
    onNavigateToUser: (String) -> Unit = {},
) {
    val viewModel: CommentsViewModel = hiltViewModel()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color.White,
    ) {
        Text(
            "Comments",
            style = CorusFont.screenTitle,
            color = CorusColors.Text,
            modifier = Modifier.padding(horizontal = CorusSpacing.lg, vertical = CorusSpacing.sm),
        )
        CommentsBodyContent(
            postId = postId,
            viewModel = viewModel,
            onNavigateToUser = onNavigateToUser,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CommentsSheet(
    postId: String,
    viewModel: CommentsViewModel = hiltViewModel(),
    onDismiss: () -> Unit = {},
    onNavigateToUser: (String) -> Unit = {},
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Comments", style = CorusFont.screenTitle, color = CorusColors.Text) },
                navigationIcon = {
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = CorusColors.Text)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White),
            )
        },
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            CommentsBodyContent(
                postId = postId,
                viewModel = viewModel,
                onNavigateToUser = onNavigateToUser,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ColumnScope.CommentsBodyContent(
    postId: String,
    viewModel: CommentsViewModel = hiltViewModel(),
    onNavigateToUser: (String) -> Unit = {},
) {
    val comments by viewModel.comments.collectAsState()
    val repliesByParent by viewModel.repliesByParent.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val isSending by viewModel.isSending.collectAsState()
    val replyingTo by viewModel.replyingTo.collectAsState()
    val likedCommentIds by viewModel.likedCommentIds.collectAsState()

    var commentText by remember { mutableStateOf("") }
    val focusRequester = remember { FocusRequester() }
    var showGifPicker by remember { mutableStateOf(false) }

    val maxChars = 700
    val showCounter = commentText.length >= 650

    LaunchedEffect(postId) {
        viewModel.loadComments(postId)
    }

    LaunchedEffect(replyingTo) {
        if (replyingTo != null) {
            focusRequester.requestFocus()
        }
    }

    if (viewModel.giphySupport && showGifPicker) {
        GifPickerSheet(
            onGifSelected = { gif ->
                viewModel.sendGifComment(gif.gifURL)
                showGifPicker = false
            },
            onDismiss = { showGifPicker = false },
        )
    }

    // Comments list — takes remaining space
    Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
        when {
            isLoading && comments.isEmpty() -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(color = CorusColors.Accent)
                }
            }
            comments.isEmpty() && !isLoading -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("No comments yet", style = CorusFont.bodyMedium, color = CorusColors.Secondary)
                        Spacer(modifier = Modifier.height(CorusSpacing.xs))
                        Text("Start the conversation", style = CorusFont.caption, color = CorusColors.Tertiary)
                    }
                }
            }
            else -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(vertical = CorusSpacing.sm),
                ) {
                    comments.forEach { comment ->
                        item(key = comment.id) {
                            CommentRow(
                                comment = comment,
                                isOwnComment = comment.user.id == viewModel.currentUserId,
                                isLiked = likedCommentIds.contains(comment.id),
                                onUserTap = { onNavigateToUser(comment.user.id) },
                                onReplyTap = { viewModel.setReplyingTo(comment) },
                                onLikeTap = { viewModel.toggleCommentLike(comment.id) },
                                onDeleteTap = { viewModel.deleteComment(comment.id) },
                            )
                        }
                        // Replies
                        val replies = repliesByParent[comment.id] ?: emptyList()
                        replies.forEach { reply ->
                            item(key = reply.id) {
                                CommentRow(
                                    comment = reply,
                                    isOwnComment = reply.user.id == viewModel.currentUserId,
                                    isReply = true,
                                    isLiked = likedCommentIds.contains(reply.id),
                                    onUserTap = { onNavigateToUser(reply.user.id) },
                                    onReplyTap = { viewModel.setReplyingTo(reply) },
                                    onLikeTap = { viewModel.toggleCommentLike(reply.id) },
                                    onDeleteTap = { viewModel.deleteComment(reply.id) },
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // Reply-to banner
    if (replyingTo != null) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(CorusColors.CardBackground)
                .padding(horizontal = CorusSpacing.lg, vertical = CorusSpacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Replying to @${replyingTo?.user?.username}",
                style = CorusFont.caption,
                color = CorusColors.Secondary,
                modifier = Modifier.weight(1f),
            )
            Icon(
                Icons.Filled.Close,
                contentDescription = "Cancel reply",
                modifier = Modifier
                    .size(16.dp)
                    .clickable { viewModel.setReplyingTo(null) },
                tint = CorusColors.Secondary,
            )
        }
    }

    // Comment input
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.White)
            .padding(horizontal = CorusSpacing.lg, vertical = CorusSpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TextField(
            value = commentText,
            onValueChange = {
                if (it.length <= maxChars) commentText = it
            },
            modifier = Modifier
                .weight(1f)
                .focusRequester(focusRequester),
            placeholder = {
                Text("Add a comment...", style = CorusFont.body, color = CorusColors.Tertiary)
            },
            singleLine = false,
            maxLines = 4,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
            keyboardActions = KeyboardActions(
                onSend = {
                    if (commentText.isNotBlank() && !isSending) {
                        viewModel.sendComment(commentText)
                        commentText = ""
                    }
                },
            ),
            shape = RoundedCornerShape(CorusSpacing.cornerRadiusMedium),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = CorusColors.CardBackground,
                unfocusedContainerColor = CorusColors.CardBackground,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
                cursorColor = CorusColors.Accent,
            ),
            textStyle = CorusFont.body.copy(color = CorusColors.Text),
            trailingIcon = {
                if (showCounter) {
                    Text(
                        text = "${maxChars - commentText.length}",
                        style = CorusFont.caption,
                        color = if (commentText.length >= maxChars) CorusColors.Error else CorusColors.Secondary,
                    )
                }
            },
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

        IconButton(
            onClick = {
                if (commentText.isNotBlank() && !isSending) {
                    viewModel.sendComment(commentText)
                    commentText = ""
                }
            },
            enabled = commentText.isNotBlank() && !isSending,
        ) {
            if (isSending) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    color = CorusColors.Accent,
                    strokeWidth = 2.dp,
                )
            } else {
                Icon(
                    Icons.AutoMirrored.Filled.Send,
                    contentDescription = "Send",
                    tint = if (commentText.isNotBlank()) CorusColors.Accent else CorusColors.Tertiary,
                )
            }
        }
    }
}

@Composable
private fun CommentRow(
    comment: CymbalComment,
    isOwnComment: Boolean = false,
    isReply: Boolean = false,
    isLiked: Boolean = false,
    onUserTap: () -> Unit = {},
    onReplyTap: () -> Unit = {},
    onLikeTap: () -> Unit = {},
    onDeleteTap: () -> Unit = {},
) {
    var showDeleteConfirm by remember { mutableStateOf(false) }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete Comment?", style = CorusFont.songTitleLarge) },
            text = { Text("This comment will be permanently deleted.", style = CorusFont.body) },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteConfirm = false
                    onDeleteTap()
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
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                start = if (isReply) CorusSpacing.xxxl + CorusSpacing.lg else CorusSpacing.lg,
                end = CorusSpacing.lg,
                top = CorusSpacing.sm,
                bottom = CorusSpacing.sm,
            ),
    ) {
        UserAvatarView(
            avatarURL = comment.user.avatarURL,
            size = if (isReply) 24.dp else CorusSpacing.avatarSmall,
            modifier = Modifier.clickable(onClick = onUserTap),
        )

        Spacer(modifier = Modifier.width(CorusSpacing.sm))

        Column(modifier = Modifier.weight(1f)) {
            // Username
            Text(
                text = comment.user.username,
                style = CorusFont.body.copy(fontWeight = FontWeight.ExtraBold, fontSize = 14.sp),
                color = CorusColors.Text,
            )

            // GIF or text content
            if (comment.gifURL != null) {
                Spacer(modifier = Modifier.height(CorusSpacing.xs))
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(comment.gifURL)
                        .build(),
                    contentDescription = "GIF",
                    modifier = Modifier
                        .widthIn(max = 200.dp)
                        .heightIn(max = 150.dp)
                        .clip(RoundedCornerShape(12.dp)),
                    contentScale = ContentScale.Fit,
                )
            } else if (comment.text.isNotEmpty()) {
                Text(
                    text = comment.text,
                    style = CorusFont.body.copy(fontWeight = FontWeight.Normal, fontSize = 15.sp),
                    color = CorusColors.Text,
                )
            }

            Spacer(modifier = Modifier.height(CorusSpacing.xs))

            // Timestamp + Reply + Delete (own) buttons
            Row(
                horizontalArrangement = Arrangement.spacedBy(CorusSpacing.lg),
            ) {
                Text(
                    text = DateUtils.relativeTime(comment.timestamp),
                    style = CorusFont.caption,
                    color = CorusColors.Secondary,
                )
                Text(
                    text = "Reply",
                    style = CorusFont.captionMedium,
                    color = CorusColors.Secondary,
                    modifier = Modifier.clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onReplyTap,
                    ),
                )
            }
        }

        // Like button
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onLikeTap,
            ),
        ) {
            Icon(
                imageVector = if (isLiked) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                contentDescription = "Like comment",
                modifier = Modifier.size(14.dp),
                tint = if (isLiked) CorusColors.Like else CorusColors.Secondary,
            )
            if (comment.likeCount > 0) {
                Text(
                    text = "${comment.likeCount}",
                    style = CorusFont.caption,
                    color = CorusColors.Secondary,
                )
            }
        }

        // "..." menu button for own comments (matching iOS)
        if (isOwnComment) {
            var showMenu by remember { mutableStateOf(false) }
            Spacer(modifier = Modifier.width(CorusSpacing.xs))
            Box {
                Icon(
                    imageVector = Icons.Filled.MoreHoriz,
                    contentDescription = "Comment options",
                    modifier = Modifier
                        .size(16.dp)
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
                        text = {
                            Text(
                                "Delete",
                                style = CorusFont.body,
                                color = CorusColors.Error,
                            )
                        },
                        onClick = {
                            showMenu = false
                            showDeleteConfirm = true
                        },
                    )
                }
            }
        }
    }
}
