package fm.corus.android.ui.screens.messaging

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Reply
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Gif
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Photo
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import fm.corus.android.R
import fm.corus.android.ui.components.ShimmerAsyncImage
import fm.corus.android.data.model.CymbalMessage
import fm.corus.android.data.model.MessageFailureReason
import fm.corus.android.data.model.MessageSendStatus
import fm.corus.android.data.model.MessageType
import fm.corus.android.domain.HapticManager
import fm.corus.android.ui.LocalHapticManager
import fm.corus.android.ui.components.FullScreenImageView
import fm.corus.android.ui.components.GifPickerSheet
import fm.corus.android.ui.components.PickerMode
import fm.corus.android.ui.components.SongFilmPickerSheet
import fm.corus.android.ui.theme.CorusColors
import fm.corus.android.ui.theme.CorusFont
import fm.corus.android.ui.theme.CorusSpacing

private val REACTION_EMOJIS = listOf("❤️", "😂", "👍", "😮", "😢", "🔥")
private val REACTION_KEYS = listOf("heart", "laugh", "thumbsup", "wow", "cry", "fire")

private val URL_REGEX = Regex("""https?://\S+""", RegexOption.IGNORE_CASE)

/** Short preview text for the message being replied to. Mirrors iOS replySnippet. */
internal fun replyPreviewText(msg: CymbalMessage, context: android.content.Context): String {
    val text = msg.text
    if (!text.isNullOrBlank()) return text.take(100)
    return when (msg.type) {
        MessageType.IMAGE -> context.getString(R.string.messaging_thread_attachment_photo)
        MessageType.GIF -> context.getString(R.string.comments_cd_gif)
        MessageType.SHARED_TRACK -> msg.trackName?.takeIf { it.isNotBlank() } ?: context.getString(R.string.messaging_thread_attachment_song)
        MessageType.SHARED_FILM -> msg.movieTitle?.takeIf { it.isNotBlank() } ?: context.getString(R.string.messaging_thread_attachment_film)
        else -> context.getString(R.string.messaging_thread_message_fallback)
    }
}

/** Returns true when [text] contains only 1-3 emoji (with optional modifiers/ZWJ). */
private fun isEmojiOnly(text: String): Boolean {
    val trimmed = text.trim()
    if (trimmed.isEmpty()) return false
    var emojiCount = 0
    for (codePoint in trimmed.codePoints()) {
        val type = Character.getType(codePoint)
        if (type == Character.OTHER_SYMBOL.toInt() ||
            type == Character.SURROGATE.toInt() ||
            codePoint == 0xFE0F || // variation selector
            codePoint == 0x200D || // ZWJ
            codePoint in 0x1F3FB..0x1F3FF || // skin tones
            codePoint in 0x1F1E0..0x1F1FF // regional indicators (flags)
        ) {
            if (type == Character.OTHER_SYMBOL.toInt() || codePoint in 0x1F1E0..0x1F1FF) {
                emojiCount++
            }
            continue
        }
        return false
    }
    return emojiCount in 1..3
}

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
    var showAttachmentMenu by remember { mutableStateOf(false) }
    var mediaPickerMode by remember { mutableStateOf<PickerMode?>(null) }
    var fullScreenImageUrl by remember { mutableStateOf<String?>(null) }
    val context = LocalContext.current
    val keyboardController = LocalSoftwareKeyboardController.current

    // Dismiss the keyboard while the long-press menu is shown so it doesn't occlude the action card
    LaunchedEffect(reactionTarget) {
        if (reactionTarget != null) keyboardController?.hide()
    }

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

    // Auto-scroll to newest message when the list grows (reverseLayout: index 0 = bottom)
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(0)
        }
    }

    Column(modifier = Modifier.fillMaxSize().background(CorusColors.Background).imePadding()) {
        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = CorusSpacing.sm, vertical = CorusSpacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(id = R.string.common_back))
            }
            Text(otherUsername, style = CorusFont.screenTitle, color = CorusColors.Text)
        }

        HorizontalDivider(color = CorusColors.Divider)

        val bubbleHaptics = LocalHapticManager.current
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
                    otherUsername = otherUsername,
                    onLongPress = {
                        // Mirrors iOS NotificationsView message long-press haptic.
                        bubbleHaptics.impact(HapticManager.ImpactStyle.MEDIUM)
                        reactionTarget = message
                    },
                    onDoubleTap = {
                        // Mirrors iOS NotificationsView toggleReaction haptic.
                        bubbleHaptics.impact(HapticManager.ImpactStyle.MEDIUM)
                        viewModel.toggleReaction(threadId, message.id, "heart")
                    },
                    onReactionTap = { emoji ->
                        // Mirrors iOS NotificationsView toggleReaction haptic.
                        bubbleHaptics.impact(HapticManager.ImpactStyle.MEDIUM)
                        viewModel.toggleReaction(threadId, message.id, emoji)
                    },
                    onImageTap = { url -> fullScreenImageUrl = url },
                    onRetry = { viewModel.retrySendMessage(message.id) },
                )
            }
        }

        // Reply-to bar (iOS parity: thin accent bar + "Replying to [username]" + preview + X)
        if (replyToMessage != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = CorusSpacing.md, vertical = CorusSpacing.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .width(3.dp)
                        .height(36.dp)
                        .background(CorusColors.Accent),
                )
                Spacer(modifier = Modifier.width(CorusSpacing.sm))
                Column(modifier = Modifier.weight(1f)) {
                    val replyAuthorLabel =
                        if (replyToMessage?.fromUserId == viewModel.currentUserId) stringResource(id = R.string.messaging_thread_yourself)
                        else otherUsername.ifBlank { stringResource(id = R.string.messaging_thread_message_fallback) }
                    Text(
                        text = stringResource(id = R.string.messaging_thread_replying_to_format, replyAuthorLabel),
                        style = CorusFont.caption,
                        color = CorusColors.Text,
                    )
                    Text(
                        text = replyPreviewText(replyToMessage!!, context),
                        style = CorusFont.caption,
                        color = CorusColors.Secondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                IconButton(onClick = { viewModel.setReplyTo(null) }) {
                    Icon(Icons.Filled.Close, contentDescription = stringResource(id = R.string.comments_cd_cancel_reply), modifier = Modifier.size(18.dp), tint = CorusColors.Secondary)
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
            // Attachment plus button (Photo / Song / Film)
            Box {
                IconButton(onClick = { showAttachmentMenu = true }) {
                    Icon(
                        imageVector = Icons.Filled.AddCircle,
                        contentDescription = stringResource(id = R.string.messaging_thread_cd_add_attachment),
                        tint = CorusColors.Accent,
                        modifier = Modifier.size(28.dp),
                    )
                }
                DropdownMenu(
                    expanded = showAttachmentMenu,
                    onDismissRequest = { showAttachmentMenu = false },
                ) {
                    DropdownMenuItem(
                        text = { Text(stringResource(id = R.string.messaging_thread_attachment_photo)) },
                        leadingIcon = { Icon(Icons.Filled.Photo, contentDescription = null) },
                        onClick = {
                            showAttachmentMenu = false
                            photoPickerLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(id = R.string.messaging_thread_attachment_song)) },
                        leadingIcon = { Icon(Icons.Filled.MusicNote, contentDescription = null) },
                        onClick = {
                            showAttachmentMenu = false
                            mediaPickerMode = PickerMode.SONG
                        },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(id = R.string.messaging_thread_attachment_film)) },
                        leadingIcon = { Icon(Icons.Filled.Movie, contentDescription = null) },
                        onClick = {
                            showAttachmentMenu = false
                            mediaPickerMode = PickerMode.FILM
                        },
                    )
                }
            }

            if (viewModel.giphySupport) {
                IconButton(onClick = { showGifPicker = true }) {
                    Icon(
                        Icons.Filled.Gif,
                        contentDescription = stringResource(id = R.string.comments_cd_send_gif),
                        tint = CorusColors.Accent,
                        modifier = Modifier.size(28.dp),
                    )
                }
            }

            OutlinedTextField(
                value = messageText,
                onValueChange = { messageText = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text(stringResource(id = R.string.messaging_thread_placeholder), style = CorusFont.body) },
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
                    contentDescription = stringResource(id = R.string.comments_cd_send),
                    tint = if (messageText.isNotBlank()) CorusColors.Accent else CorusColors.Tertiary,
                )
            }
        }
    }

    if (viewModel.giphySupport && showGifPicker) {
        GifPickerSheet(
            onGifSelected = { gif ->
                viewModel.sendGifMessage(threadId, gif.gifURL)
                showGifPicker = false
            },
            onDismiss = { showGifPicker = false },
        )
    }

    mediaPickerMode?.let { mode ->
        SongFilmPickerSheet(
            initialMode = mode,
            onSongSelected = { track ->
                viewModel.sendSongMessage(threadId, track)
                mediaPickerMode = null
            },
            onFilmSelected = { movie ->
                viewModel.sendFilmMessage(threadId, movie)
                mediaPickerMode = null
            },
            onDismiss = { mediaPickerMode = null },
        )
    }

    // Full-screen image viewer
    FullScreenImageView(
        imageUrl = fullScreenImageUrl,
        visible = fullScreenImageUrl != null,
        onDismiss = { fullScreenImageUrl = null },
    )

    // Reaction overlay
    if (reactionTarget != null) {
        val overlayHaptics = LocalHapticManager.current
        ReactionOverlay(
            message = reactionTarget!!,
            isFromCurrentUser = reactionTarget!!.fromUserId == viewModel.currentUserId,
            onReaction = { emojiKey ->
                // Mirrors iOS NotificationsView toggleReaction haptic.
                overlayHaptics.impact(HapticManager.ImpactStyle.MEDIUM)
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
        Column(
            modifier = Modifier
                .padding(horizontal = CorusSpacing.lg)
                .widthIn(max = 300.dp)
                // Prevent taps on the floating content from dismissing the overlay
                .clickable(enabled = false, onClick = {}),
            horizontalAlignment = Alignment.Start,
            verticalArrangement = Arrangement.spacedBy(CorusSpacing.sm),
        ) {
            // Floating emoji reaction pill
            Surface(
                shape = RoundedCornerShape(50),
                color = CorusColors.Background,
                shadowElevation = 6.dp,
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = CorusSpacing.sm, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    REACTION_EMOJIS.forEachIndexed { index, emoji ->
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .clickable { onReaction(REACTION_EMOJIS[index]) },
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = emoji,
                                fontSize = 26.sp,
                            )
                        }
                    }
                }
            }

            // Floating action card
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = CorusColors.Background,
                shadowElevation = 6.dp,
                modifier = Modifier.widthIn(min = 220.dp),
            ) {
                Column {
                    ActionMenuItem(
                        icon = Icons.AutoMirrored.Filled.Reply,
                        label = stringResource(id = R.string.comments_reply),
                        onClick = onReply,
                    )
                    if (!message.text.isNullOrBlank()) {
                        HorizontalDivider(color = CorusColors.Divider)
                        ActionMenuItem(
                            icon = Icons.Filled.ContentCopy,
                            label = stringResource(id = R.string.comments_menu_copy),
                            onClick = onCopy,
                        )
                    }
                    if (!isFromCurrentUser) {
                        HorizontalDivider(color = CorusColors.Divider)
                        ActionMenuItem(
                            icon = Icons.Filled.Flag,
                            label = stringResource(id = R.string.comments_menu_report),
                            tint = CorusColors.Error,
                            onClick = onReport,
                        )
                        HorizontalDivider(color = CorusColors.Divider)
                        ActionMenuItem(
                            icon = Icons.Filled.Block,
                            label = stringResource(id = R.string.comments_menu_block),
                            tint = CorusColors.Error,
                            onClick = onBlock,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ActionMenuItem(
    icon: ImageVector,
    label: String,
    tint: Color = CorusColors.Text,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = CorusSpacing.lg, vertical = CorusSpacing.md),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(CorusSpacing.md),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(20.dp),
        )
        Text(text = label, style = CorusFont.body, color = tint)
    }
}

@Composable
private fun MessageBubble(
    message: CymbalMessage,
    isFromCurrentUser: Boolean,
    currentUserId: String,
    otherUsername: String,
    onLongPress: () -> Unit,
    onDoubleTap: () -> Unit,
    onReactionTap: (String) -> Unit,
    onImageTap: (String) -> Unit = {},
    onRetry: () -> Unit = {},
) {
    val context = LocalContext.current
    val isSending = message.sendStatus == MessageSendStatus.SENDING
    val isFailed = message.sendStatus == MessageSendStatus.FAILED
    val hasMedia = message.type != MessageType.TEXT
    val emojiOnly = !hasMedia && message.replyToText == null &&
        !message.text.isNullOrBlank() && isEmojiOnly(message.text!!)

    val annotatedText = remember(message.text, isFromCurrentUser, emojiOnly) {
        if (!message.text.isNullOrBlank() && !emojiOnly)
            buildLinkifiedText(message.text!!, isFromCurrentUser)
        else null
    }
    var textLayout by remember { mutableStateOf<TextLayoutResult?>(null) }
    var bubbleCoords by remember { mutableStateOf<LayoutCoordinates?>(null) }
    var textCoords by remember { mutableStateOf<LayoutCoordinates?>(null) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = CorusSpacing.md, vertical = CorusSpacing.xxs),
        horizontalAlignment = if (isFromCurrentUser) Alignment.End else Alignment.Start,
    ) {
        // Message bubble — quoted reply context is rendered inside
        Box(
            modifier = Modifier
                .widthIn(max = 280.dp)
                .then(if (isSending) Modifier.alpha(0.7f) else Modifier)
                .onGloballyPositioned { bubbleCoords = it }
                .pointerInput(message.id, annotatedText) {
                    detectTapGestures(
                        onLongPress = { onLongPress() },
                        onDoubleTap = { onDoubleTap() },
                        onTap = { offset ->
                            val at = annotatedText
                            val layout = textLayout
                            val bubble = bubbleCoords
                            val textLC = textCoords
                            if (at != null && layout != null && bubble != null && textLC != null) {
                                try {
                                    val textOrigin = bubble.localPositionOf(textLC, Offset.Zero)
                                    val relative = offset - textOrigin
                                    if (relative.x >= 0 && relative.y >= 0 &&
                                        relative.x <= layout.size.width &&
                                        relative.y <= layout.size.height) {
                                        val charOffset = layout.getOffsetForPosition(relative)
                                        at.getStringAnnotations("URL", charOffset, charOffset)
                                            .firstOrNull()?.let { ann ->
                                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(ann.item))
                                                context.startActivity(intent)
                                            }
                                    }
                                } catch (_: Exception) {}
                            }
                        },
                    )
                }
                .background(
                    color = if (emojiOnly) Color.Transparent
                            else if (isFromCurrentUser) CorusColors.Accent
                            else CorusColors.CardBackground,
                    shape = RoundedCornerShape(CorusSpacing.cornerRadiusMedium),
                )
                .padding(
                    horizontal = if (emojiOnly) 0.dp else CorusSpacing.md,
                    vertical = if (emojiOnly) 0.dp else CorusSpacing.sm,
                ),
        ) {
            Column {
                // Quoted reply context, inside the bubble
                if (message.replyToText != null) {
                    val isOwnQuote = message.replyToUserId == currentUserId
                    val authorName = if (isOwnQuote) stringResource(id = R.string.messaging_thread_you) else otherUsername
                    val accentBarColor = if (isFromCurrentUser) Color.White.copy(alpha = 0.6f)
                                         else CorusColors.Accent.copy(alpha = 0.6f)
                    val quotedAuthorColor = if (isFromCurrentUser) Color.White else CorusColors.Accent
                    val quotedTextColor = if (isFromCurrentUser) Color.White.copy(alpha = 0.85f)
                                          else CorusColors.Secondary
                    Row(
                        modifier = Modifier.padding(bottom = CorusSpacing.xs),
                        verticalAlignment = Alignment.Top,
                    ) {
                        Box(
                            modifier = Modifier
                                .width(2.dp)
                                .heightIn(min = 24.dp)
                                .background(accentBarColor, shape = RoundedCornerShape(1.dp)),
                        )
                        Spacer(modifier = Modifier.width(CorusSpacing.xs))
                        Column {
                            if (authorName.isNotBlank()) {
                                Text(
                                    text = authorName,
                                    style = CorusFont.caption,
                                    color = quotedAuthorColor,
                                )
                            }
                            Text(
                                text = message.replyToText ?: "",
                                style = CorusFont.caption,
                                color = quotedTextColor,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }

                // Image content
                if (message.type == MessageType.IMAGE && message.mediaURL != null) {
                    ShimmerAsyncImage(
                        model = message.mediaURL,
                        contentDescription = stringResource(id = R.string.messaging_thread_cd_shared_image),
                        modifier = Modifier
                            .widthIn(max = 240.dp)
                            .heightIn(max = 300.dp)
                            .clip(RoundedCornerShape(CorusSpacing.cornerRadius))
                            .clickable { onImageTap(message.mediaURL!!) },
                        contentScale = androidx.compose.ui.layout.ContentScale.Fit,
                    )
                    if (!message.text.isNullOrBlank()) {
                        Spacer(modifier = Modifier.height(CorusSpacing.xs))
                    }
                }

                // GIF content
                if (message.type == MessageType.GIF && message.mediaURL != null) {
                    ShimmerAsyncImage(
                        model = message.mediaURL,
                        contentDescription = stringResource(id = R.string.comments_cd_gif),
                        modifier = Modifier
                            .widthIn(max = 240.dp)
                            .heightIn(max = 300.dp)
                            .clip(RoundedCornerShape(CorusSpacing.cornerRadius)),
                        contentScale = androidx.compose.ui.layout.ContentScale.Fit,
                    )
                }

                // Shared track content
                if (message.type == MessageType.SHARED_TRACK) {
                    SharedTrackContent(
                        trackName = message.trackName.orEmpty(),
                        artistName = message.artistName.orEmpty(),
                        albumArtURL = message.albumArtURL,
                        spotifyURL = message.spotifyURL,
                        isFromCurrentUser = isFromCurrentUser,
                    )
                    if (!message.text.isNullOrBlank()) {
                        Spacer(modifier = Modifier.height(CorusSpacing.xs))
                    }
                }

                // Shared film content
                if (message.type == MessageType.SHARED_FILM) {
                    SharedFilmContent(
                        movieTitle = message.movieTitle.orEmpty(),
                        directorName = message.directorName.orEmpty(),
                        posterURL = message.posterURL,
                        tmdbWebURL = message.tmdbWebURL,
                        isFromCurrentUser = isFromCurrentUser,
                    )
                    if (!message.text.isNullOrBlank()) {
                        Spacer(modifier = Modifier.height(CorusSpacing.xs))
                    }
                }

                // Text content
                if (!message.text.isNullOrBlank()) {
                    if (emojiOnly) {
                        Text(
                            text = message.text ?: "",
                            fontSize = 40.sp,
                        )
                    } else if (annotatedText != null) {
                        Text(
                            text = annotatedText,
                            style = CorusFont.body.copy(
                                color = if (isFromCurrentUser) Color.White else CorusColors.Text,
                            ),
                            onTextLayout = { textLayout = it },
                            modifier = Modifier.onGloballyPositioned { textCoords = it },
                        )
                    }
                }

            }
        }

        // Send status indicators
        if (isSending) {
            Text(
                text = stringResource(id = R.string.messaging_thread_sending),
                style = CorusFont.caption,
                color = CorusColors.Tertiary,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
        if (isFailed) {
            Row(
                modifier = Modifier
                    .padding(top = 2.dp)
                    .then(
                        if (message.failureReason != MessageFailureReason.MESSAGING_DISABLED)
                            Modifier.clickable(onClick = onRetry)
                        else Modifier
                    ),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Icon(
                    imageVector = Icons.Filled.ErrorOutline,
                    contentDescription = null,
                    modifier = Modifier.size(12.dp),
                    tint = Color.Red,
                )
                if (message.failureReason == MessageFailureReason.MESSAGING_DISABLED) {
                    Text(
                        text = stringResource(id = R.string.messaging_thread_user_messaging_disabled),
                        style = CorusFont.caption,
                        color = Color.Red,
                    )
                } else {
                    Text(
                        text = stringResource(id = R.string.messaging_thread_failed_to_deliver),
                        style = CorusFont.caption,
                        color = Color.Red,
                    )
                    Text(
                        text = stringResource(id = R.string.messaging_thread_tap_to_retry),
                        style = CorusFont.caption.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Medium),
                        color = Color.Red,
                    )
                }
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

@Composable
private fun SharedTrackContent(
    trackName: String,
    artistName: String,
    albumArtURL: String?,
    spotifyURL: String?,
    isFromCurrentUser: Boolean,
) {
    val context = LocalContext.current
    val textColor = if (isFromCurrentUser) Color.White else CorusColors.Text
    val subtitleColor = if (isFromCurrentUser) Color.White.copy(alpha = 0.85f) else CorusColors.Secondary
    Row(
        modifier = Modifier
            .widthIn(max = 240.dp)
            .then(
                if (!spotifyURL.isNullOrBlank()) Modifier.clickable {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(spotifyURL))
                    context.startActivity(intent)
                } else Modifier
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ShimmerAsyncImage(
            model = albumArtURL,
            contentDescription = trackName,
            modifier = Modifier
                .size(56.dp)
                .clip(RoundedCornerShape(CorusSpacing.cornerRadius)),
            contentScale = androidx.compose.ui.layout.ContentScale.Crop,
        )
        Spacer(modifier = Modifier.width(CorusSpacing.sm))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = trackName,
                style = CorusFont.body,
                color = textColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = artistName,
                style = CorusFont.caption,
                color = subtitleColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(modifier = Modifier.width(CorusSpacing.xs))
        Icon(
            imageVector = Icons.Filled.MusicNote,
            contentDescription = null,
            tint = subtitleColor,
            modifier = Modifier.size(14.dp),
        )
    }
}

@Composable
private fun SharedFilmContent(
    movieTitle: String,
    directorName: String,
    posterURL: String?,
    tmdbWebURL: String?,
    isFromCurrentUser: Boolean,
) {
    val context = LocalContext.current
    val textColor = if (isFromCurrentUser) Color.White else CorusColors.Text
    val subtitleColor = if (isFromCurrentUser) Color.White.copy(alpha = 0.85f) else CorusColors.Secondary
    Row(
        modifier = Modifier
            .widthIn(max = 240.dp)
            .then(
                if (!tmdbWebURL.isNullOrBlank()) Modifier.clickable {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(tmdbWebURL))
                    context.startActivity(intent)
                } else Modifier
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ShimmerAsyncImage(
            model = posterURL,
            contentDescription = movieTitle,
            modifier = Modifier
                .width(50.dp)
                .height(75.dp)
                .clip(RoundedCornerShape(CorusSpacing.cornerRadius)),
            contentScale = androidx.compose.ui.layout.ContentScale.Crop,
        )
        Spacer(modifier = Modifier.width(CorusSpacing.sm))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = movieTitle,
                style = CorusFont.body,
                color = textColor,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (directorName.isNotBlank()) {
                Text(
                    text = directorName,
                    style = CorusFont.caption,
                    color = subtitleColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Spacer(modifier = Modifier.width(CorusSpacing.xs))
        Icon(
            imageVector = Icons.Filled.Movie,
            contentDescription = null,
            tint = subtitleColor,
            modifier = Modifier.size(14.dp),
        )
    }
}
