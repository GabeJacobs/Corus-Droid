package fm.corus.android.ui.screens.messaging

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Reply
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Gif
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Photo
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import fm.corus.android.service.ActiveThreadTracker
import fm.corus.android.service.CorusFirebaseMessagingService
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
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
import com.valentinilk.shimmer.shimmer
import fm.corus.android.ui.components.ShimmerAsyncImage
import fm.corus.android.data.model.CymbalMessage
import fm.corus.android.data.model.MessageDeliveryStatus
import fm.corus.android.data.model.MessageFailureReason
import fm.corus.android.data.model.MessageSendStatus
import fm.corus.android.data.model.MessageType
import fm.corus.android.data.model.CymbalMovie
import fm.corus.android.data.model.CymbalTrack
import fm.corus.android.data.model.TrackSource
import fm.corus.android.domain.NowPlayingManager
import fm.corus.android.ui.components.SoundCloudAdaptiveLogo
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.Schedule
import java.text.SimpleDateFormat
import java.util.Locale
import kotlinx.coroutines.launch
import fm.corus.android.domain.HapticManager
import fm.corus.android.ui.LocalHapticManager
import fm.corus.android.ui.components.FullScreenImageView
import fm.corus.android.ui.components.GifPickerSheet
import fm.corus.android.ui.components.PickerMode
import fm.corus.android.ui.components.SongFilmPickerSheet
import fm.corus.android.ui.components.UserAvatarView
import fm.corus.android.ui.theme.CorusColors
import fm.corus.android.ui.theme.CorusFont
import fm.corus.android.ui.theme.CorusSpacing

private val REACTION_EMOJIS = listOf("❤️", "😂", "👍", "😮", "😢", "🔥")

/** Messages can be edited for 15 minutes after sending (mirrors the server gate). */
private const val EDIT_WINDOW_MS = 15 * 60 * 1000L
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

/**
 * Compute the visible delivery status (sending / sent / read) for a message.
 * Mirrors the web `deliveryStatus` in
 * `Corus-Web/app/app/(main)/messages/[threadId]/page.tsx`.
 *
 * Note: `messages` arrives newest-first (LazyColumn reverseLayout), so we
 * iterate `asReversed()` to get chronological order before computing the read
 * boundary.
 */
internal fun computeDeliveryStatus(
    message: CymbalMessage,
    currentUserId: String?,
    messages: List<CymbalMessage>,
    recipientUnread: Int,
    myReadReceiptsEnabled: Boolean,
): MessageDeliveryStatus {
    if (message.sendStatus == MessageSendStatus.SENDING) return MessageDeliveryStatus.SENDING
    if (currentUserId == null || message.fromUserId != currentUserId) return MessageDeliveryStatus.SENT
    if (!myReadReceiptsEnabled) return MessageDeliveryStatus.SENT
    val mySent = messages.asReversed()
        .filter { it.fromUserId == currentUserId && it.sendStatus == MessageSendStatus.SENT }
    val readBoundary = (mySent.size - recipientUnread).coerceAtLeast(0)
    val idx = mySent.indexOfFirst { it.id == message.id }
    if (idx < 0) return MessageDeliveryStatus.SENT
    return if (idx < readBoundary) MessageDeliveryStatus.READ else MessageDeliveryStatus.SENT
}

private val bubbleTimeFormatter: SimpleDateFormat by lazy {
    SimpleDateFormat("h:mm a", Locale.getDefault())
}

@Composable
private fun BubbleMeta(
    message: CymbalMessage,
    isFromCurrentUser: Boolean,
    deliveryStatus: MessageDeliveryStatus,
    modifier: Modifier = Modifier,
) {
    if (message.sendStatus == MessageSendStatus.FAILED) return
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (message.isEdited) {
            Text(
                text = stringResource(id = R.string.messaging_thread_edited),
                fontSize = 10.sp,
                color = CorusColors.Tertiary,
            )
        }
        Text(
            text = bubbleTimeFormatter.format(message.createdAt).lowercase(),
            fontSize = 10.sp,
            color = CorusColors.Tertiary,
        )
        if (isFromCurrentUser) {
            when (deliveryStatus) {
                MessageDeliveryStatus.SENDING -> Icon(
                    imageVector = Icons.Filled.Schedule,
                    contentDescription = null,
                    modifier = Modifier.size(10.dp),
                    tint = CorusColors.Tertiary,
                )
                MessageDeliveryStatus.SENT -> Icon(
                    imageVector = Icons.Filled.Check,
                    contentDescription = null,
                    modifier = Modifier.size(10.dp),
                    tint = CorusColors.Tertiary,
                )
                MessageDeliveryStatus.READ -> Icon(
                    imageVector = Icons.Filled.DoneAll,
                    contentDescription = null,
                    modifier = Modifier.size(12.dp),
                    tint = CorusColors.Accent,
                )
            }
        }
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
    onNavigateToProfile: (String) -> Unit = {},
    onNavigateToSong: (CymbalTrack) -> Unit = {},
    onNavigateToFilm: (CymbalMovie) -> Unit = {},
    viewModel: MessageThreadViewModel = hiltViewModel(),
) {
    val nowPlayingManager = viewModel.nowPlayingManager
    val messages by viewModel.messages.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val otherUsername by viewModel.otherUsername.collectAsState()
    val otherDisplayName by viewModel.otherDisplayName.collectAsState()
    val otherAvatarURL by viewModel.otherAvatarURL.collectAsState()
    val otherAvatarThumbURL by viewModel.otherAvatarThumbURL.collectAsState()
    val replyToMessage by viewModel.replyToMessage.collectAsState()
    val editingMessage by viewModel.editingMessage.collectAsState()
    val recipientUnread by viewModel.recipientUnread.collectAsState()
    val myReadReceiptsEnabled by viewModel.myReadReceiptsEnabled.collectAsState()
    // TextFieldValue (not plain String) so we can place the cursor at the end of
    // the prefilled text when an edit starts; the String overload resets it to 0.
    var messageText by remember { mutableStateOf(TextFieldValue("")) }
    val listState = rememberLazyListState()
    var reactionTarget by remember { mutableStateOf<CymbalMessage?>(null) }
    // Drives the big-heart burst over a bubble on a fresh double-tap like (iOS parity).
    var heartBurstMessageId by remember { mutableStateOf<String?>(null) }
    var showGifPicker by remember { mutableStateOf(false) }
    var showAttachmentMenu by remember { mutableStateOf(false) }
    var mediaPickerMode by remember { mutableStateOf<PickerMode?>(null) }
    var fullScreenImageUrl by remember { mutableStateOf<String?>(null) }
    val context = LocalContext.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val composerFocusRequester = remember { FocusRequester() }

    // Auto-clear the heart burst after it plays (mirrors iOS's 0.8s hold).
    LaunchedEffect(heartBurstMessageId) {
        if (heartBurstMessageId != null) {
            kotlinx.coroutines.delay(800)
            heartBurstMessageId = null
        }
    }

    // Dismiss the keyboard while the long-press menu is shown so it doesn't occlude the action card
    LaunchedEffect(reactionTarget) {
        if (reactionTarget != null) keyboardController?.hide()
    }

    // When an edit begins, focus the composer and raise the keyboard (parity with
    // iOS/web, which focus the field on edit).
    LaunchedEffect(editingMessage) {
        if (editingMessage != null) {
            composerFocusRequester.requestFocus()
            keyboardController?.show()
        }
    }

    // Likewise when a reply begins, focus the composer and raise the keyboard
    // (iOS focuses the field on reply; Android didn't).
    LaunchedEffect(replyToMessage) {
        if (replyToMessage != null) {
            composerFocusRequester.requestFocus()
            keyboardController?.show()
        }
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

    // Mark this thread as the one being actively viewed while the screen is in
    // the foreground: suppresses its push notifications, excludes it from the
    // unread badge, and clears any notification that arrived before opening it.
    // Cleared on STOP (app backgrounded / screen left) so notifications resume.
    val resolvedThreadId by viewModel.resolvedThreadId.collectAsState()
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(resolvedThreadId, lifecycleOwner) {
        val tid = resolvedThreadId
        fun activate() {
            if (tid != null) {
                ActiveThreadTracker.activeThreadId = tid
                NotificationManagerCompat.from(context)
                    .cancel(CorusFirebaseMessagingService.dmNotificationId(tid))
            }
        }
        fun deactivate() {
            if (ActiveThreadTracker.activeThreadId == tid) {
                ActiveThreadTracker.activeThreadId = null
            }
        }
        // The screen is already resumed when this effect runs, so activate now;
        // the observer then handles later background/foreground transitions.
        activate()
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> activate()
                Lifecycle.Event.ON_STOP -> deactivate()
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            deactivate()
        }
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
            // Avatar + username — tap to open the other user's profile (iOS parity)
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(CorusSpacing.cornerRadius))
                    .clickable(enabled = otherUsername.isNotBlank()) { onNavigateToProfile(otherUserId) }
                    .padding(vertical = CorusSpacing.xxs, horizontal = CorusSpacing.xs),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(CorusSpacing.sm),
            ) {
                UserAvatarView(
                    avatarURL = otherAvatarURL,
                    avatarThumbURL = otherAvatarThumbURL,
                    displayName = otherDisplayName,
                    username = otherUsername,
                    size = CorusSpacing.avatarSmall,
                )
                Text(otherUsername, style = CorusFont.screenTitle, color = CorusColors.Text)
            }
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
                    deliveryStatus = computeDeliveryStatus(
                        message = message,
                        currentUserId = viewModel.currentUserId,
                        messages = messages,
                        recipientUnread = recipientUnread,
                        myReadReceiptsEnabled = myReadReceiptsEnabled,
                    ),
                    nowPlayingManager = nowPlayingManager,
                    onLongPress = {
                        // Mirrors iOS NotificationsView message long-press haptic.
                        bubbleHaptics.impact(HapticManager.ImpactStyle.MEDIUM)
                        reactionTarget = message
                    },
                    onDoubleTap = {
                        // Mirrors iOS NotificationsView toggleReaction haptic.
                        bubbleHaptics.impact(HapticManager.ImpactStyle.MEDIUM)
                        // Backend's toggleMessageReaction only accepts the raw emoji
                        // (ALLOWED_REACTIONS), not key strings like "heart". The long-press
                        // picker already sends REACTION_EMOJIS; double-tap must match it.
                        val heart = REACTION_EMOJIS[0]
                        // Only burst on a fresh like (adding the heart), not when un-liking.
                        val isFreshLike = viewModel.currentUserId
                            ?.let { it !in (message.reactions[heart] ?: emptyList()) } ?: false
                        viewModel.toggleReaction(threadId, message.id, heart)
                        if (isFreshLike) heartBurstMessageId = message.id
                    },
                    showHeartBurst = heartBurstMessageId == message.id,
                    onReactionTap = { emoji ->
                        // Mirrors iOS NotificationsView toggleReaction haptic.
                        bubbleHaptics.impact(HapticManager.ImpactStyle.MEDIUM)
                        viewModel.toggleReaction(threadId, message.id, emoji)
                    },
                    onImageTap = { url ->
                        if (reactionTarget == null) {
                            fullScreenImageUrl = url
                        }
                    },
                    onRetry = { viewModel.retrySendMessage(message.id) },
                    onNavigateToSong = onNavigateToSong,
                    onNavigateToFilm = onNavigateToFilm,
                )
            }
        }

        // Edit banner (mirrors the reply bar). Editing and replying are mutually exclusive.
        if (editingMessage != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = CorusSpacing.md, vertical = CorusSpacing.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Filled.Edit,
                    contentDescription = null,
                    tint = CorusColors.Accent,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(modifier = Modifier.width(CorusSpacing.sm))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(id = R.string.messaging_thread_editing),
                        style = CorusFont.caption,
                        color = CorusColors.Text,
                    )
                    Text(
                        text = editingMessage?.text ?: "",
                        style = CorusFont.caption,
                        color = CorusColors.Secondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                IconButton(onClick = {
                    viewModel.cancelEditing()
                    messageText = TextFieldValue("")
                }) {
                    Icon(
                        Icons.Filled.Close,
                        contentDescription = stringResource(id = R.string.comments_cd_cancel_edit),
                        modifier = Modifier.size(18.dp),
                        tint = CorusColors.Secondary,
                    )
                }
            }
        } else
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
                    if (viewModel.gifSupport) {
                        DropdownMenuItem(
                            text = { Text(stringResource(id = R.string.comment_attachment_gif)) },
                            leadingIcon = { Icon(Icons.Filled.Gif, contentDescription = null) },
                            onClick = {
                                showAttachmentMenu = false
                                showGifPicker = true
                            },
                        )
                    }
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

            OutlinedTextField(
                value = messageText,
                onValueChange = { messageText = it },
                modifier = Modifier
                    .weight(1f)
                    .focusRequester(composerFocusRequester),
                placeholder = { Text(stringResource(id = R.string.messaging_thread_placeholder), style = CorusFont.body) },
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                singleLine = false,
                maxLines = 4,
                shape = RoundedCornerShape(CorusSpacing.pillCornerRadius),
            )
            Spacer(modifier = Modifier.width(CorusSpacing.sm))
            IconButton(
                onClick = {
                    if (messageText.text.isNotBlank()) {
                        if (editingMessage != null) {
                            viewModel.editMessage(threadId, messageText.text)
                        } else {
                            viewModel.sendMessage(threadId, messageText.text)
                        }
                        messageText = TextFieldValue("")
                    }
                },
                enabled = messageText.text.isNotBlank(),
            ) {
                Icon(
                    if (editingMessage != null) Icons.Filled.Check else Icons.AutoMirrored.Filled.Send,
                    contentDescription = stringResource(id = R.string.comments_cd_send),
                    tint = if (messageText.text.isNotBlank()) CorusColors.Accent else CorusColors.Tertiary,
                )
            }
        }
    }

    if (viewModel.gifSupport && showGifPicker) {
        GifPickerSheet(
            onGifSelected = { gif ->
                viewModel.sendGifMessage(threadId, gif.fullURL, gif.slug)
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
                    // Burst on a fresh heart from the picker too (iOS parity).
                    val isFreshLike = emojiKey == REACTION_EMOJIS[0] &&
                        (viewModel.currentUserId?.let { it !in (msg.reactions[emojiKey] ?: emptyList()) } ?: false)
                    viewModel.toggleReaction(threadId, msg.id, emojiKey)
                    if (isFreshLike) heartBurstMessageId = msg.id
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
            onEdit = if (
                reactionTarget!!.fromUserId == viewModel.currentUserId &&
                reactionTarget!!.type == MessageType.TEXT &&
                (System.currentTimeMillis() - reactionTarget!!.createdAt.time) < EDIT_WINDOW_MS
            ) {
                {
                    reactionTarget?.let { msg ->
                        val current = msg.text ?: ""
                        messageText = TextFieldValue(text = current, selection = TextRange(current.length))
                        viewModel.startEditing(msg)
                    }
                    reactionTarget = null
                }
            } else null,
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
    onEdit: (() -> Unit)? = null,
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
                    if (onEdit != null) {
                        ActionMenuItem(
                            icon = Icons.Filled.Edit,
                            label = stringResource(id = R.string.comments_menu_edit),
                            onClick = onEdit,
                        )
                        HorizontalDivider(color = CorusColors.Divider)
                    }
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
    deliveryStatus: MessageDeliveryStatus,
    nowPlayingManager: NowPlayingManager,
    showHeartBurst: Boolean = false,
    onLongPress: () -> Unit,
    onDoubleTap: () -> Unit,
    onReactionTap: (String) -> Unit,
    onImageTap: (String) -> Unit = {},
    onRetry: () -> Unit = {},
    onNavigateToSong: (CymbalTrack) -> Unit = {},
    onNavigateToFilm: (CymbalMovie) -> Unit = {},
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

    // One-shot "pop" for the heart reaction pill on a fresh double-tap like: the
    // pill itself springs in from large so the like reads as a single heart
    // landing in its place (Instagram-style), instead of a big burst heart that
    // fades and leaves a separate small pill behind.
    val heartPopScale = remember { Animatable(1f) }
    LaunchedEffect(showHeartBurst) {
        if (showHeartBurst) {
            // Pop up to the top of the scale, hold there for a beat, then ease
            // slowly down into the locked-in resting size.
            heartPopScale.snapTo(2.2f)
            kotlinx.coroutines.delay(160)
            heartPopScale.animateTo(
                targetValue = 1f,
                animationSpec = spring(dampingRatio = 0.6f, stiffness = 140f),
            )
        }
    }

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
                if (message.type == MessageType.IMAGE) {
                    if (message.mediaURL != null) {
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
                    } else {
                        // Pending upload — show a shimmering skeleton so the bubble has
                        // image-like dimensions while the photo finishes uploading.
                        Box(
                            modifier = Modifier
                                .size(width = 200.dp, height = 240.dp)
                                .clip(RoundedCornerShape(CorusSpacing.cornerRadius))
                                .shimmer()
                                .background(CorusColors.Skeleton),
                        )
                    }
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
                        message = message,
                        nowPlayingManager = nowPlayingManager,
                        isFromCurrentUser = isFromCurrentUser,
                        onNavigate = {
                            val song = message.attachedSong
                            if (song != null) {
                                val source = message.attachedSongSource ?: TrackSource.SPOTIFY
                                onNavigateToSong(
                                    song.asCymbalTrack().copy(
                                        source = source,
                                        soundcloudId = message.soundcloudId,
                                        soundcloudPermalinkUrl = message.soundcloudPermalinkUrl,
                                    )
                                )
                            } else if (!message.spotifyURL.isNullOrBlank()) {
                                // Legacy fallback: pre-trackId messages with no parseable Spotify URL.
                                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(message.spotifyURL)))
                            }
                        },
                    )
                    if (!message.text.isNullOrBlank()) {
                        Spacer(modifier = Modifier.height(CorusSpacing.xs))
                    }
                }

                // Shared film content
                if (message.type == MessageType.SHARED_FILM) {
                    SharedFilmContent(
                        message = message,
                        isFromCurrentUser = isFromCurrentUser,
                        onNavigate = {
                            val film = message.attachedFilm
                            if (film != null) {
                                onNavigateToFilm(film.asCymbalMovie())
                            } else if (!message.tmdbWebURL.isNullOrBlank()) {
                                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(message.tmdbWebURL)))
                            }
                        },
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

        // Reaction pills share the metadata line (opposite the timestamp) so
        // reacting never pushes the timestamp down or overlaps text. The row is
        // sized to the measured bubble width; SpaceBetween pins the timestamp to
        // the message's own side and the pills to the opposite side. The heart
        // springs in via heartPopScale on a fresh like.
        if (message.reactions.isNotEmpty()) {
            val density = LocalDensity.current
            val bubbleWidthDp = bubbleCoords?.size?.width?.let { with(density) { it.toDp() } }
            val pills: @Composable () -> Unit = {
                // Lift the pills up a few dp so the heart's top overlaps the
                // bubble's bottom edge (over its padding, never text), WhatsApp-style.
                Row(
                    modifier = Modifier.offset(y = (-8).dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    message.reactions.forEach { (emojiKey, userIds) ->
                        if (userIds.isNotEmpty()) {
                            val emojiChar = REACTION_EMOJIS.getOrNull(REACTION_KEYS.indexOf(emojiKey)) ?: emojiKey
                            val reactedByMe = currentUserId in userIds
                            val isHeart = emojiChar == REACTION_EMOJIS[0]
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = if (reactedByMe) CorusColors.Accent.copy(alpha = 0.15f) else CorusColors.Background,
                                shadowElevation = 2.dp,
                                modifier = Modifier
                                    .then(
                                        if (isHeart) Modifier.graphicsLayer {
                                            scaleX = heartPopScale.value
                                            scaleY = heartPopScale.value
                                        } else Modifier
                                    )
                                    .clickable { onReactionTap(emojiKey) },
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
                                            color = if (reactedByMe) CorusColors.Accent else CorusColors.Secondary,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
            Row(
                modifier = Modifier
                    .padding(top = 2.dp)
                    .then(if (bubbleWidthDp != null) Modifier.width(bubbleWidthDp) else Modifier),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (isFromCurrentUser) {
                    pills()
                    BubbleMeta(message = message, isFromCurrentUser = isFromCurrentUser, deliveryStatus = deliveryStatus)
                } else {
                    BubbleMeta(message = message, isFromCurrentUser = isFromCurrentUser, deliveryStatus = deliveryStatus)
                    pills()
                }
            }
        } else {
            BubbleMeta(
                message = message,
                isFromCurrentUser = isFromCurrentUser,
                deliveryStatus = deliveryStatus,
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

/**
 * Shared-song message bubble. The artwork plays a preview (auto-routes
 * SoundCloud vs Spotify via NowPlayingManager); tapping the rest of the row
 * navigates to the in-app song page — same split as `CommentAttachmentCard`.
 */
@Composable
private fun SharedTrackContent(
    message: CymbalMessage,
    nowPlayingManager: NowPlayingManager,
    isFromCurrentUser: Boolean,
    onNavigate: () -> Unit,
) {
    val trackName = message.trackName.orEmpty()
    val artistName = message.artistName.orEmpty()
    val textColor = if (isFromCurrentUser) Color.White else CorusColors.Text
    val subtitleColor = if (isFromCurrentUser) Color.White.copy(alpha = 0.85f) else CorusColors.Secondary

    val song = message.attachedSong
    val source = message.attachedSongSource ?: TrackSource.SPOTIFY
    val isSoundCloud = source == TrackSource.SOUNDCLOUD

    val nowPlayingState by nowPlayingManager.state.collectAsState()
    val loadingTrackId by nowPlayingManager.loadingTrackId.collectAsState()
    val scope = rememberCoroutineScope()
    val targetTrackId = song?.trackId ?: message.trackId
    val artworkIsActive = targetTrackId != null && nowPlayingState.trackId == targetTrackId
    val showPause = artworkIsActive && nowPlayingState.isPlaying
    val showLoading = targetTrackId != null && loadingTrackId == targetTrackId

    Row(
        modifier = Modifier
            .widthIn(max = 240.dp)
            .clickable { onNavigate() },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(RoundedCornerShape(CorusSpacing.cornerRadius))
                .clickable(enabled = song != null) {
                    if (song == null) return@clickable
                    if (artworkIsActive) {
                        nowPlayingManager.togglePlayPause()
                    } else {
                        scope.launch {
                            nowPlayingManager.play(
                                trackId = song.trackId,
                                trackName = song.trackName,
                                artistName = song.artistName,
                                albumArtURL = song.albumArtURL,
                                albumArtLargeURL = song.albumArtLargeURL,
                                previewUrl = song.previewUrl,
                                spotifyURI = song.spotifyURI,
                                spotifyWebURL = song.spotifyWebURL,
                                isrc = song.isrc,
                            )
                        }
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            ShimmerAsyncImage(
                model = message.albumArtURL,
                contentDescription = trackName,
                modifier = Modifier.fillMaxSize(),
                contentScale = androidx.compose.ui.layout.ContentScale.Crop,
            )
            if (song != null) {
                Box(
                    modifier = Modifier
                        .size(26.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.4f)),
                    contentAlignment = Alignment.Center,
                ) {
                    when {
                        showLoading -> CircularProgressIndicator(
                            modifier = Modifier.size(14.dp),
                            strokeWidth = 1.5.dp,
                            color = Color.White,
                        )
                        else -> Icon(
                            imageVector = if (showPause) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(14.dp),
                        )
                    }
                }
            }
        }
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
        if (isSoundCloud) {
            SoundCloudAdaptiveLogo(size = 14.dp)
        } else {
            Icon(
                imageVector = Icons.Filled.MusicNote,
                contentDescription = null,
                tint = subtitleColor,
                modifier = Modifier.size(14.dp),
            )
        }
    }
}

/**
 * Shared-film message bubble. Tapping anywhere navigates to the in-app film
 * page (films don't preview-play, mirroring `CommentAttachmentCard`).
 */
@Composable
private fun SharedFilmContent(
    message: CymbalMessage,
    isFromCurrentUser: Boolean,
    onNavigate: () -> Unit,
) {
    val movieTitle = message.movieTitle.orEmpty()
    val directorName = message.directorName.orEmpty()
    val textColor = if (isFromCurrentUser) Color.White else CorusColors.Text
    val subtitleColor = if (isFromCurrentUser) Color.White.copy(alpha = 0.85f) else CorusColors.Secondary
    Row(
        modifier = Modifier
            .widthIn(max = 240.dp)
            .clickable { onNavigate() },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ShimmerAsyncImage(
            model = message.posterURL,
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
