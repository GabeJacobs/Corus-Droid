package fm.corus.android.ui.screens.messaging

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Gif
import androidx.compose.material.icons.filled.Image
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import coil3.compose.AsyncImage
import fm.corus.android.data.model.CymbalMessage
import fm.corus.android.data.model.MessageType
import fm.corus.android.ui.components.GifPickerSheet
import fm.corus.android.ui.theme.CorusColors
import fm.corus.android.ui.theme.CorusFont
import fm.corus.android.ui.theme.CorusSpacing
import fm.corus.android.ui.util.DateUtils

private val REACTION_EMOJIS = listOf("❤️", "😂", "👍", "😮", "😢", "🔥")
private val REACTION_KEYS = listOf("heart", "laugh", "thumbsup", "wow", "cry", "fire")

private val URL_REGEX = Regex("""https?://\S+""", RegexOption.IGNORE_CASE)

@Composable
fun MessageThreadScreen(
    threadId: String,
    otherUserId: String,
    onBack: () -> Unit = {},
    viewModel: MessageThreadViewModel = hiltViewModel(),
) {
    val messages by viewModel.messages.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val otherUsername by viewModel.otherUsername.collectAsState()
    val replyToMessage by viewModel.replyToMessage.collectAsState()
    var messageText by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    var reactionTarget by remember { mutableStateOf<CymbalMessage?>(null) }
    var showGifPicker by remember { mutableStateOf(false) }
    val context = LocalContext.current

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            try {
                val inputStream = context.contentResolver.openInputStream(uri)
                val imageData = inputStream?.readBytes()
                inputStream?.close()
                if (imageData != null) {
                    viewModel.sendImageMessage(threadId, imageData)
                }
            } catch (_: Exception) { }
        }
    }

    LaunchedEffect(threadId) {
        viewModel.loadMessages(threadId, otherUserId)
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = CorusSpacing.sm, vertical = CorusSpacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
            Text(otherUsername, style = CorusFont.screenTitle, color = CorusColors.Text)
        }

        HorizontalDivider(color = CorusColors.Divider)

        // Messages
        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            reverseLayout = true,
            contentPadding = PaddingValues(vertical = CorusSpacing.sm),
        ) {
            items(messages, key = { it.id }) { message ->
                MessageBubble(
                    message = message,
                    isFromCurrentUser = message.fromUserId == viewModel.currentUserId,
                    currentUserId = viewModel.currentUserId ?: "",
                    onLongPress = { reactionTarget = message },
                    onReply = { viewModel.setReplyTo(message) },
                    onReactionTap = { emoji ->
                        viewModel.toggleReaction(threadId, message.id, emoji)
                    },
                )
            }
        }

        // Reply-to bar
        if (replyToMessage != null) {
            HorizontalDivider(color = CorusColors.Divider)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(CorusColors.CardBackground)
                    .padding(horizontal = CorusSpacing.lg, vertical = CorusSpacing.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Replying to message",
                        style = CorusFont.caption,
                        color = CorusColors.Accent,
                    )
                    Text(
                        text = replyToMessage?.text ?: "",
                        style = CorusFont.caption,
                        color = CorusColors.Secondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                IconButton(onClick = { viewModel.setReplyTo(null) }) {
                    Icon(Icons.Filled.Close, contentDescription = "Cancel reply", modifier = Modifier.size(16.dp), tint = CorusColors.Secondary)
                }
            }
        }

        // Compose bar
        HorizontalDivider(color = CorusColors.Divider)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = CorusSpacing.md, vertical = CorusSpacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Photo button
            IconButton(
                onClick = {
                    photoPickerLauncher.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                    )
                },
            ) {
                Icon(
                    Icons.Filled.Image,
                    contentDescription = "Send photo",
                    tint = CorusColors.Secondary,
                )
            }

            IconButton(onClick = { showGifPicker = true }) {
                Icon(
                    Icons.Filled.Gif,
                    contentDescription = "Send GIF",
                    tint = CorusColors.Accent,
                    modifier = Modifier.size(28.dp),
                )
            }

            OutlinedTextField(
                value = messageText,
                onValueChange = { messageText = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text("Message...", style = CorusFont.body) },
                singleLine = false,
                maxLines = 4,
                shape = RoundedCornerShape(CorusSpacing.pillCornerRadius),
            )
            Spacer(modifier = Modifier.width(CorusSpacing.sm))
            IconButton(
                onClick = {
                    if (messageText.isNotBlank()) {
                        viewModel.sendMessage(threadId, messageText)
                        messageText = ""
                    }
                },
                enabled = messageText.isNotBlank(),
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.Send,
                    contentDescription = "Send",
                    tint = if (messageText.isNotBlank()) CorusColors.Accent else CorusColors.Tertiary,
                )
            }
        }
    }

    if (showGifPicker) {
        GifPickerSheet(
            onGifSelected = { gif ->
                viewModel.sendGifMessage(threadId, gif.gifURL)
                showGifPicker = false
            },
            onDismiss = { showGifPicker = false },
        )
    }

    // Reaction overlay
    if (reactionTarget != null) {
        ReactionOverlay(
            message = reactionTarget!!,
            isFromCurrentUser = reactionTarget!!.fromUserId == viewModel.currentUserId,
            onReaction = { emojiKey ->
                reactionTarget?.let { msg ->
                    viewModel.toggleReaction(threadId, msg.id, emojiKey)
                }
                reactionTarget = null
            },
            onReply = {
                reactionTarget?.let { viewModel.setReplyTo(it) }
                reactionTarget = null
            },
            onCopy = {
                val clipboardManager = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                val clip = android.content.ClipData.newPlainText("message", reactionTarget?.text ?: "")
                clipboardManager.setPrimaryClip(clip)
                reactionTarget = null
            },
            onReport = { reactionTarget = null },
            onBlock = { reactionTarget = null },
            onDismiss = { reactionTarget = null },
        )
    }
}

@Composable
private fun ReactionOverlay(
    message: CymbalMessage,
    isFromCurrentUser: Boolean,
    onReaction: (String) -> Unit,
    onReply: () -> Unit,
    onCopy: () -> Unit,
    onReport: () -> Unit,
    onBlock: () -> Unit,
    onDismiss: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.3f))
            .clickable(onClick = onDismiss),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = CorusColors.Background,
            shadowElevation = 8.dp,
        ) {
            Column(
                modifier = Modifier.padding(CorusSpacing.lg),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                // Emoji row
                Row(
                    horizontalArrangement = Arrangement.spacedBy(CorusSpacing.md),
                ) {
                    REACTION_EMOJIS.forEachIndexed { index, emoji ->
                        Text(
                            text = emoji,
                            fontSize = 28.sp,
                            modifier = Modifier.clickable { onReaction(REACTION_KEYS[index]) },
                        )
                    }
                }

                Spacer(modifier = Modifier.height(CorusSpacing.md))

                // Message preview
                if (!message.text.isNullOrBlank()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                color = CorusColors.CardBackground,
                                shape = RoundedCornerShape(CorusSpacing.md),
                            )
                            .padding(CorusSpacing.md),
                    ) {
                        Text(
                            text = message.text ?: "",
                            style = CorusFont.caption,
                            color = CorusColors.Text,
                            maxLines = 2,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                        )
                    }

                    Spacer(modifier = Modifier.height(CorusSpacing.md))
                }

                // Action buttons
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    TextButton(onClick = onReply) {
                        Text("Reply", style = CorusFont.body, color = CorusColors.Text)
                    }
                    if (!message.text.isNullOrBlank()) {
                        TextButton(onClick = onCopy) {
                            Text("Copy", style = CorusFont.body, color = CorusColors.Text)
                        }
                    }
                    if (!isFromCurrentUser) {
                        TextButton(onClick = onReport) {
                            Text("Report", style = CorusFont.body, color = CorusColors.Text)
                        }
                        TextButton(onClick = onBlock) {
                            Text("Block", style = CorusFont.body, color = CorusColors.Text)
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MessageBubble(
    message: CymbalMessage,
    isFromCurrentUser: Boolean,
    currentUserId: String,
    onLongPress: () -> Unit,
    onReply: () -> Unit,
    onReactionTap: (String) -> Unit,
) {
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = CorusSpacing.md, vertical = CorusSpacing.xxs),
        horizontalAlignment = if (isFromCurrentUser) Alignment.End else Alignment.Start,
    ) {
        // Reply context
        if (message.replyToText != null) {
            Box(
                modifier = Modifier
                    .widthIn(max = 280.dp)
                    .padding(bottom = 2.dp)
                    .background(
                        color = CorusColors.CardBackground,
                        shape = RoundedCornerShape(CorusSpacing.cornerRadius),
                    )
                    .padding(horizontal = CorusSpacing.sm, vertical = CorusSpacing.xs),
            ) {
                Text(
                    text = message.replyToText ?: "",
                    style = CorusFont.caption,
                    color = CorusColors.Secondary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        // Message bubble
        Box(
            modifier = Modifier
                .widthIn(max = 280.dp)
                .combinedClickable(
                    onClick = {},
                    onLongClick = onLongPress,
                )
                .background(
                    color = if (isFromCurrentUser) CorusColors.Accent else CorusColors.CardBackground,
                    shape = RoundedCornerShape(CorusSpacing.cornerRadiusMedium),
                )
                .padding(horizontal = CorusSpacing.md, vertical = CorusSpacing.sm),
        ) {
            Column {
                // Image content
                if (message.type == MessageType.IMAGE && message.mediaURL != null) {
                    AsyncImage(
                        model = message.mediaURL,
                        contentDescription = "Shared image",
                        modifier = Modifier
                            .widthIn(max = 240.dp)
                            .heightIn(max = 300.dp)
                            .clip(RoundedCornerShape(CorusSpacing.cornerRadius)),
                        contentScale = androidx.compose.ui.layout.ContentScale.Fit,
                    )
                    if (!message.text.isNullOrBlank()) {
                        Spacer(modifier = Modifier.height(CorusSpacing.xs))
                    }
                }

                // GIF content
                if (message.type == MessageType.GIF && message.mediaURL != null) {
                    AsyncImage(
                        model = message.mediaURL,
                        contentDescription = "GIF",
                        modifier = Modifier
                            .widthIn(max = 240.dp)
                            .heightIn(max = 300.dp)
                            .clip(RoundedCornerShape(CorusSpacing.cornerRadius)),
                        contentScale = androidx.compose.ui.layout.ContentScale.Fit,
                    )
                }

                // Text content
                if (!message.text.isNullOrBlank()) {
                    val annotatedText = buildLinkifiedText(
                        message.text ?: "",
                        isFromCurrentUser,
                    )
                    androidx.compose.foundation.text.ClickableText(
                        text = annotatedText,
                        style = CorusFont.body.copy(
                            color = if (isFromCurrentUser) Color.White else CorusColors.Text,
                        ),
                        onClick = { offset ->
                            annotatedText.getStringAnnotations("URL", offset, offset).firstOrNull()?.let { annotation ->
                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(annotation.item))
                                context.startActivity(intent)
                            }
                        },
                    )
                }

                Text(
                    text = DateUtils.relativeTime(message.createdAt),
                    style = CorusFont.caption,
                    color = if (isFromCurrentUser) Color.White.copy(alpha = 0.7f) else CorusColors.Secondary,
                )
            }
        }

        // Reaction pills
        if (message.reactions.isNotEmpty()) {
            Row(
                modifier = Modifier.padding(top = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                message.reactions.forEach { (emojiKey, userIds) ->
                    if (userIds.isNotEmpty()) {
                        val emojiChar = REACTION_EMOJIS.getOrNull(REACTION_KEYS.indexOf(emojiKey)) ?: emojiKey
                        val isMine = currentUserId in userIds
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (isMine) CorusColors.Accent.copy(alpha = 0.15f) else CorusColors.CardBackground,
                            modifier = Modifier.clickable { onReactionTap(emojiKey) },
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(2.dp),
                            ) {
                                Text(text = emojiChar, fontSize = 12.sp)
                                if (userIds.size > 1) {
                                    Text(
                                        text = "${userIds.size}",
                                        style = CorusFont.caption,
                                        color = if (isMine) CorusColors.Accent else CorusColors.Secondary,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun buildLinkifiedText(
    text: String,
    isFromCurrentUser: Boolean,
): AnnotatedString {
    return buildAnnotatedString {
        var lastIndex = 0
        URL_REGEX.findAll(text).forEach { match ->
            // Text before URL
            append(text.substring(lastIndex, match.range.first))
            // URL with annotation
            pushStringAnnotation("URL", match.value)
            withStyle(
                SpanStyle(
                    textDecoration = TextDecoration.Underline,
                    color = if (isFromCurrentUser) Color.White else CorusColors.Accent,
                )
            ) {
                append(match.value)
            }
            pop()
            lastIndex = match.range.last + 1
        }
        // Remaining text
        if (lastIndex < text.length) {
            append(text.substring(lastIndex))
        }
    }
}
