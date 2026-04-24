package fm.corus.android.ui.screens.feed

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Gif
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.activity.compose.BackHandler
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import kotlinx.coroutines.launch
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import fm.corus.android.data.model.CymbalComment
import fm.corus.android.data.model.CymbalUser
import fm.corus.android.ui.components.MentionSuggestionsList
import fm.corus.android.ui.components.ReportContentType
import fm.corus.android.ui.components.ReportSheet
import fm.corus.android.ui.components.SkeletonCommentRow
import fm.corus.android.ui.components.GifPickerSheet
import fm.corus.android.ui.components.TappableMentionText
import fm.corus.android.ui.components.ToastManager
import fm.corus.android.ui.components.UserAvatarView
import fm.corus.android.ui.components.UsernameWithFlair
import fm.corus.android.ui.components.applyMention
import fm.corus.android.ui.components.buildMentionAnnotatedString
import fm.corus.android.ui.components.parseMentionQuery
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
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
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    // Explicitly handle system back to dismiss the sheet
    BackHandler(enabled = true) { onDismiss() }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color.White,
        dragHandle = {
            // Add status bar padding so the drag handle stays below the camera cutout
            Column(modifier = Modifier.statusBarsPadding()) {
                BottomSheetDefaults.DragHandle()
            }
        },
    ) {
        CommentsSheetContent(
            postId = postId,
            viewModel = viewModel,
            onDismiss = onDismiss,
            onNavigateToUser = onNavigateToUser,
            autoFocusInput = true,
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
            CommentsSheetContent(
                postId = postId,
                viewModel = viewModel,
                onDismiss = onDismiss,
                onNavigateToUser = onNavigateToUser,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CommentsSheetContent(
    postId: String,
    viewModel: CommentsViewModel = hiltViewModel(),
    onDismiss: () -> Unit = {},
    onNavigateToUser: (String) -> Unit = {},
    autoFocusInput: Boolean = false,
) {
    val comments by viewModel.comments.collectAsState()
    val repliesByParent by viewModel.repliesByParent.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val isSending by viewModel.isSending.collectAsState()
    val replyingTo by viewModel.replyingTo.collectAsState()
    val likedCommentIds by viewModel.likedCommentIds.collectAsState()
    val post by viewModel.post.collectAsState()
    val sendError by viewModel.sendError.collectAsState()
    val editingComment by viewModel.editingComment.collectAsState()

    var commentText by remember { mutableStateOf(TextFieldValue("")) }
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current
    var showGifPicker by remember { mutableStateOf(false) }
    var reportingComment by remember { mutableStateOf<CymbalComment?>(null) }
    var viewingLikesCommentId by remember { mutableStateOf<String?>(null) }
    val maxChars = 700
    val showCounter = commentText.text.length >= 650
    val mentionSuggestions by viewModel.mentionSuggestions.collectAsState()
    val isSearchingMentions by viewModel.isSearchingMentions.collectAsState()
    var mentionSearchJob by remember { mutableStateOf<Job?>(null) }

    val coroutineScope = rememberCoroutineScope()
    val handleMentionTap: (String) -> Unit = { username ->
        coroutineScope.launch {
            val userId = viewModel.resolveUsernameToId(username)
            if (userId != null) onNavigateToUser(userId)
        }
    }

    LaunchedEffect(sendError) {
        if (sendError != null) {
            kotlinx.coroutines.delay(3000)
            viewModel.clearSendError()
        }
    }

    LaunchedEffect(postId) {
        viewModel.loadComments(postId)
        viewModel.loadPost(postId)
    }

    LaunchedEffect(replyingTo) {
        if (replyingTo != null) {
            focusRequester.requestFocus()
        }
    }

    LaunchedEffect(editingComment?.id) {
        val editing = editingComment
        if (editing != null) {
            commentText = TextFieldValue(editing.text, selection = TextRange(editing.text.length))
            focusRequester.requestFocus()
        } else {
            commentText = TextFieldValue("")
        }
    }

    // Auto-focus input to open keyboard immediately (Instagram-style)
    if (autoFocusInput) {
        LaunchedEffect(Unit) {
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

    reportingComment?.let { comment ->
        val reportSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { reportingComment = null },
            sheetState = reportSheetState,
            containerColor = CorusColors.Background,
        ) {
            ReportSheet(
                contentType = ReportContentType.COMMENT,
                contentId = comment.id,
                authRepository = viewModel.authRepository,
                userRepository = viewModel.userRepository,
                analyticsService = viewModel.analyticsService,
                onDismiss = { reportingComment = null },
            )
        }
    }

    val likesCommentId = viewingLikesCommentId
    if (likesCommentId != null) {
        BackHandler(enabled = true) { viewingLikesCommentId = null }
        Column(modifier = Modifier.fillMaxWidth().imePadding()) {
            Box(modifier = Modifier.fillMaxWidth()) {
                IconButton(
                    onClick = { viewingLikesCommentId = null },
                    modifier = Modifier.align(Alignment.CenterStart),
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back to comments",
                        tint = CorusColors.Text,
                    )
                }
                Text(
                    text = "Likes",
                    style = CorusFont.screenTitle,
                    color = CorusColors.Text,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(vertical = CorusSpacing.sm),
                    textAlign = TextAlign.Center,
                )
            }
            HorizontalDivider(color = CorusColors.Divider, thickness = 0.5.dp)
            CommentLikesContent(
                postId = postId,
                commentId = likesCommentId,
                onNavigateToUser = { userId ->
                    viewingLikesCommentId = null
                    onNavigateToUser(userId)
                    onDismiss()
                },
                fillContainer = true,
            )
        }
        return
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .imePadding(),
    ) {
        // Centered title (Instagram-style)
        Text(
            text = "Comments",
            style = CorusFont.screenTitle,
            color = CorusColors.Text,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = CorusSpacing.sm),
            textAlign = TextAlign.Center,
        )

        HorizontalDivider(color = CorusColors.Divider, thickness = 0.5.dp)

        // Single LazyColumn for all states. weight(1f) so the input bar pins below.
        // The bottom spacer guarantees content always overflows (needed for drag-to-expand).
        LazyColumn(
            modifier = Modifier.fillMaxWidth().weight(1f),
            contentPadding = PaddingValues(vertical = CorusSpacing.sm),
        ) {
            if (isLoading && comments.isEmpty()) {
                val skeletonCount = post?.let { maxOf(minOf(it.commentCount, 5), 2) } ?: 3
                items(skeletonCount) { SkeletonCommentRow() }
            } else if (comments.isEmpty() && !isLoading && (post?.caption.isNullOrEmpty())) {
                item(key = "empty") {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 200.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("No comments yet", style = CorusFont.bodyMedium, color = CorusColors.Secondary)
                            Spacer(modifier = Modifier.height(CorusSpacing.xs))
                            Text("Start the conversation", style = CorusFont.caption, color = CorusColors.Tertiary)
                        }
                    }
                }
            } else {
                val captionText = post?.caption
                if (!captionText.isNullOrEmpty()) {
                    item(key = "caption") {
                        CaptionRow(
                            user = post?.user,
                            caption = captionText,
                            timestamp = post?.timestamp,
                            onUserTap = { post?.user?.id?.let { onNavigateToUser(it) } },
                            onMentionTap = handleMentionTap,
                        )
                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = CorusSpacing.lg),
                            color = CorusColors.Divider,
                        )
                    }
                }

                comments.forEach { comment ->
                    item(key = comment.id) {
                        CommentRow(
                            comment = comment,
                            isOwnComment = comment.user.id == viewModel.currentUserId,
                            isLiked = likedCommentIds.contains(comment.id),
                            onUserTap = { onNavigateToUser(comment.user.id) },
                            onReplyTap = {
                                viewModel.setReplyingTo(comment)
                                focusRequester.requestFocus()
                            },
                            onLikeTap = { viewModel.toggleCommentLike(comment.id) },
                            onLikeLongPress = { viewingLikesCommentId = comment.id },
                            onDeleteTap = { viewModel.deleteComment(comment.id) },
                            onEditTap = { viewModel.startEditing(comment) },
                            onReportTap = { reportingComment = comment },
                            onMentionTap = handleMentionTap,
                        )
                    }
                    val replies = repliesByParent[comment.id] ?: emptyList()
                    replies.forEach { reply ->
                        item(key = reply.id) {
                            CommentRow(
                                comment = reply,
                                isOwnComment = reply.user.id == viewModel.currentUserId,
                                isReply = true,
                                isLiked = likedCommentIds.contains(reply.id),
                                onUserTap = { onNavigateToUser(reply.user.id) },
                                onReplyTap = {
                                    viewModel.setReplyingTo(reply)
                                    focusRequester.requestFocus()
                                },
                                onLikeTap = { viewModel.toggleCommentLike(reply.id) },
                                onLikeLongPress = { viewingLikesCommentId = reply.id },
                                onDeleteTap = { viewModel.deleteComment(reply.id) },
                                onEditTap = { viewModel.startEditing(reply) },
                                onReportTap = { reportingComment = reply },
                                onMentionTap = handleMentionTap,
                            )
                        }
                    }
                }
            }

        }

        // ── Mention suggestions (above the input bar) ──
        MentionSuggestionsList(
            users = mentionSuggestions.take(4),
            onSelect = { user ->
                commentText = applyMention(commentText, user.username)
                viewModel.clearMentions()
            },
            isSearching = isSearchingMentions,
        )

        // ── Input bar pinned at the bottom ──

        if (sendError != null) {
            Text(
                text = sendError ?: "",
                style = CorusFont.caption,
                color = CorusColors.Error,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(CorusColors.Error.copy(alpha = 0.1f))
                    .padding(horizontal = CorusSpacing.lg, vertical = CorusSpacing.sm),
            )
        }

        if (editingComment != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(CorusColors.CardBackground)
                    .padding(horizontal = CorusSpacing.lg, vertical = CorusSpacing.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Editing comment", style = CorusFont.caption, color = CorusColors.Secondary, modifier = Modifier.weight(1f))
                Icon(Icons.Filled.Close, contentDescription = "Cancel edit", modifier = Modifier.size(16.dp).clickable { viewModel.cancelEditing(); commentText = TextFieldValue(""); viewModel.clearMentions() }, tint = CorusColors.Secondary)
            }
        } else if (replyingTo != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(CorusColors.CardBackground)
                    .padding(horizontal = CorusSpacing.lg, vertical = CorusSpacing.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Replying to @${replyingTo?.user?.username}", style = CorusFont.caption, color = CorusColors.Secondary, modifier = Modifier.weight(1f))
                Icon(Icons.Filled.Close, contentDescription = "Cancel reply", modifier = Modifier.size(16.dp).clickable { viewModel.setReplyingTo(null) }, tint = CorusColors.Secondary)
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.White)
                .navigationBarsPadding()
                .padding(horizontal = CorusSpacing.lg, vertical = CorusSpacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextField(
                value = commentText,
                onValueChange = { newValue ->
                    if (newValue.text.length <= maxChars) {
                        val textChanged = newValue.text != commentText.text
                        commentText = newValue
                        if (textChanged) {
                            mentionSearchJob?.cancel()
                            mentionSearchJob = coroutineScope.launch {
                                delay(200)
                                val query = parseMentionQuery(newValue.text, newValue.selection.start)
                                if (query != null) viewModel.searchMentions(query) else viewModel.clearMentions()
                            }
                        }
                    }
                },
                modifier = Modifier
                    .weight(1f)
                    .focusRequester(focusRequester),
                placeholder = {
                    Text(
                        when {
                            editingComment != null -> "Edit your comment..."
                            replyingTo != null -> "Reply..."
                            else -> "Add a comment..."
                        },
                        style = CorusFont.body,
                        color = CorusColors.Tertiary,
                    )
                },
                singleLine = false,
                maxLines = 4,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(
                    onDone = { keyboardController?.hide() },
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
                        Text("${maxChars - commentText.text.length}", style = CorusFont.caption, color = if (commentText.text.length >= maxChars) CorusColors.Error else CorusColors.Secondary)
                    }
                },
            )

            if (viewModel.giphySupport && editingComment == null) {
                IconButton(onClick = { showGifPicker = true }) {
                    Icon(Icons.Filled.Gif, contentDescription = "Send GIF", tint = CorusColors.Accent, modifier = Modifier.size(28.dp))
                }
            }

            val canSend = commentText.text.isNotBlank() && !isSending
            Spacer(modifier = Modifier.width(CorusSpacing.sm))
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(if (canSend) CorusColors.Accent else CorusColors.Divider)
                    .clickable(enabled = canSend) {
                        if (editingComment != null) viewModel.editComment(editingComment!!.id, commentText.text) else viewModel.sendComment(commentText.text)
                        commentText = TextFieldValue("")
                        viewModel.clearMentions()
                    },
                contentAlignment = Alignment.Center,
            ) {
                if (isSending) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), color = Color.White, strokeWidth = 2.dp)
                } else {
                    Icon(
                        imageVector = Icons.Filled.ArrowUpward,
                        contentDescription = "Send",
                        modifier = Modifier.size(18.dp),
                        tint = if (canSend) Color.White else CorusColors.Tertiary,
                    )
                }
            }
        }
    }
}

@Composable
private fun CaptionRow(
    user: CymbalUser?,
    caption: String,
    timestamp: java.util.Date?,
    onUserTap: () -> Unit = {},
    onMentionTap: (String) -> Unit = {},
) {
    if (user == null) return
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = CorusSpacing.lg, vertical = CorusSpacing.sm),
    ) {
        UserAvatarView(
            avatarURL = user.avatarURL,
            displayName = user.displayName,
            size = CorusSpacing.avatarSmall,
            modifier = Modifier.clickable(onClick = onUserTap),
        )

        Spacer(modifier = Modifier.width(CorusSpacing.sm))

        Column(modifier = Modifier.weight(1f)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(CorusSpacing.sm),
            ) {
                UsernameWithFlair(
                    username = user.username,
                    isVerified = user.isVerified,
                    isClubMember = user.isClubMember,
                    flairStyle = user.flairStyle,
                    isBot = user.isBot,
                    modifier = Modifier.clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onUserTap,
                    ),
                )
                if (timestamp != null) {
                    Text(
                        text = DateUtils.relativeTime(timestamp),
                        style = CorusFont.caption,
                        color = CorusColors.Tertiary,
                    )
                }
            }
            val annotatedCaption = remember(caption) {
                buildMentionAnnotatedString(
                    text = caption,
                    baseStyle = SpanStyle(
                        fontFamily = CorusFont.body.fontFamily,
                        fontWeight = FontWeight.Normal,
                        fontSize = 15.sp,
                    ),
                )
            }
            TappableMentionText(
                text = annotatedCaption,
                style = CorusFont.body.copy(fontSize = 15.sp),
                onMentionTap = onMentionTap,
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CommentRow(
    comment: CymbalComment,
    isOwnComment: Boolean = false,
    isReply: Boolean = false,
    isLiked: Boolean = false,
    onUserTap: () -> Unit = {},
    onReplyTap: () -> Unit = {},
    onLikeTap: () -> Unit = {},
    onLikeLongPress: () -> Unit = {},
    onDeleteTap: () -> Unit = {},
    onEditTap: () -> Unit = {},
    onReportTap: () -> Unit = {},
    onMentionTap: (String) -> Unit = {},
) {
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showContextMenu by remember { mutableStateOf(false) }
    val clipboardManager = LocalClipboardManager.current
    val canCopy = comment.text.isNotEmpty()
    val canLongPress = canCopy || !isOwnComment

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
            .then(
                if (canLongPress) Modifier.combinedClickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {},
                    onLongClick = { showContextMenu = true },
                ) else Modifier
            )
            .padding(
                start = if (isReply) CorusSpacing.xxxl + CorusSpacing.lg else CorusSpacing.lg,
                end = CorusSpacing.lg,
                top = CorusSpacing.sm,
                bottom = CorusSpacing.sm,
            ),
    ) {
        DropdownMenu(
            expanded = showContextMenu,
            onDismissRequest = { showContextMenu = false },
        ) {
            if (canCopy) {
                DropdownMenuItem(
                    text = { Text("Copy", style = CorusFont.body, color = CorusColors.Text) },
                    onClick = {
                        showContextMenu = false
                        clipboardManager.setText(AnnotatedString(comment.text))
                        ToastManager.show("Copied")
                    },
                )
            }
            if (!isOwnComment) {
                DropdownMenuItem(
                    text = { Text("Report", style = CorusFont.body, color = CorusColors.Error) },
                    onClick = {
                        showContextMenu = false
                        onReportTap()
                    },
                )
            }
        }

        UserAvatarView(
            avatarURL = comment.user.avatarURL,
            displayName = comment.user.displayName,
            size = if (isReply) 24.dp else CorusSpacing.avatarSmall,
            modifier = Modifier.clickable(onClick = onUserTap),
        )

        Spacer(modifier = Modifier.width(CorusSpacing.sm))

        Column(modifier = Modifier.weight(1f)) {
            // Username with flair badges
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(CorusSpacing.sm),
            ) {
                UsernameWithFlair(
                    username = comment.user.username,
                    isVerified = comment.user.isVerified,
                    isClubMember = comment.user.isClubMember,
                    flairStyle = comment.user.flairStyle,
                    isBot = comment.user.isBot,
                    modifier = Modifier.clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onUserTap,
                    ),
                )
                Text(
                    text = DateUtils.relativeTime(comment.timestamp),
                    style = CorusFont.caption,
                    color = CorusColors.Tertiary,
                )
                if (comment.isEdited) {
                    Text(
                        text = "edited",
                        style = CorusFont.caption,
                        color = CorusColors.Tertiary,
                    )
                }
            }

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
                val annotatedText = remember(comment.text) {
                    buildMentionAnnotatedString(
                        text = comment.text,
                        baseStyle = SpanStyle(
                            fontFamily = CorusFont.body.fontFamily,
                            fontWeight = FontWeight.Normal,
                            fontSize = 15.sp,
                        ),
                    )
                }
                TappableMentionText(
                    text = annotatedText,
                    style = CorusFont.body.copy(fontSize = 15.sp),
                    onMentionTap = onMentionTap,
                )
            }

            Spacer(modifier = Modifier.height(CorusSpacing.xs))

            // Reply button
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

        // Like button + menu — vertically centered on the row
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .align(Alignment.CenterVertically)
                .padding(start = CorusSpacing.sm),
        ) {
            val haptic = LocalHapticFeedback.current
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.combinedClickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onLikeTap,
                    onLongClick = {
                        if (comment.likeCount > 0) {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onLikeLongPress()
                        }
                    },
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
                    if (comment.gifURL == null) {
                        DropdownMenuItem(
                            text = {
                                Text(
                                    "Edit",
                                    style = CorusFont.body,
                                    color = CorusColors.Text,
                                )
                            },
                            onClick = {
                                showMenu = false
                                onEditTap()
                            },
                        )
                    }
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
}
