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
import androidx.compose.ui.draw.shadow
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
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
import androidx.compose.material.icons.filled.Person
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
import androidx.compose.ui.zIndex
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import fm.corus.android.data.local.DMDraftStore
import fm.corus.android.service.ActiveThreadTracker
import fm.corus.android.service.CorusFirebaseMessagingService
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
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
import fm.corus.android.data.model.MessagingRestriction
import fm.corus.android.data.model.CymbalMovie
import fm.corus.android.data.model.CymbalPost
import fm.corus.android.data.model.CymbalTrack
import fm.corus.android.data.model.TrackSource
import fm.corus.android.domain.NowPlayingManager
import fm.corus.android.ui.components.SoundCloudAdaptiveLogo
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import java.text.SimpleDateFormat
import java.util.Locale
import androidx.compose.ui.text.font.FontWeight
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import fm.corus.android.domain.HapticManager
import fm.corus.android.ui.LocalHapticManager
import fm.corus.android.ui.components.FullScreenImageView
import fm.corus.android.ui.components.GifPickerSheet
import fm.corus.android.ui.components.HashtagSuggestionsList
import fm.corus.android.ui.components.EntityPickerSheet
import fm.corus.android.ui.components.LocalBottomBarHeight
import fm.corus.android.ui.components.MentionSuggestionsList
import fm.corus.android.ui.components.OfflineRetryState
import fm.corus.android.ui.components.ToastManager
import fm.corus.android.ui.components.applyHashtag
import fm.corus.android.ui.components.applyMention
import fm.corus.android.ui.components.liftAboveReservedChrome
import fm.corus.android.ui.components.mentionHandle
import fm.corus.android.ui.components.parseHashtagQuery
import fm.corus.android.ui.components.parseMentionQuery
import fm.corus.android.ui.components.PickerMode
import fm.corus.android.ui.components.SongFilmPickerSheet
import fm.corus.android.ui.components.UserAvatarView
import fm.corus.android.ui.theme.CorusColors
import fm.corus.android.ui.theme.CorusFont
import fm.corus.android.ui.theme.CorusSpacing

// internal (not private) so the sibling MessageReactionsSheet.kt can share the
// emoji ↔ legacy-key mapping for the group "Reactions" list.
internal val REACTION_EMOJIS = listOf("❤️", "😂", "👍", "😮", "😢", "🔥")

/** Messages can be edited for 15 minutes after sending (mirrors the server gate). */
private const val EDIT_WINDOW_MS = 15 * 60 * 1000L
internal val REACTION_KEYS = listOf("heart", "laugh", "thumbsup", "wow", "cry", "fire")

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
        MessageType.SHARED_ARTIST -> msg.artistName?.takeIf { it.isNotBlank() } ?: context.getString(R.string.messaging_thread_attachment_artist)
        MessageType.SHARED_ALBUM -> msg.albumTitle?.takeIf { it.isNotBlank() } ?: context.getString(R.string.messaging_thread_attachment_album)
        MessageType.SHARED_DIRECTOR -> msg.directorName?.takeIf { it.isNotBlank() } ?: context.getString(R.string.messaging_thread_attachment_director)
        MessageType.SHARED_PROFILE -> msg.sharedUsername?.takeIf { it.isNotBlank() }?.let { "@$it" } ?: context.getString(R.string.messaging_thread_shared_profile)
        MessageType.SHARED_POST -> context.getString(R.string.messaging_thread_attachment_post)
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

/**
 * Key that drives the "scroll to newest" effect.
 *
 * `messages.size` alone is too coarse: the newest bubble also changes height
 * in place when its [MessageSendStatus] flips (SENDING → SENT drops the clock
 * icon; SENDING → FAILED adds the error + retry affordance) without the count
 * changing. Keying on the newest message's id AND send status keeps the list
 * pinned to the bottom across those transitions too.
 *
 * Note this is a safety net, not the fix for the send-jump bug — that was the
 * optimistic copy being dropped on ack, fixed in `MessageThreadViewModel`.
 *
 * Returns null for an empty thread (no scroll target).
 */
internal fun autoScrollKey(messages: List<CymbalMessage>): String? =
    messages.firstOrNull()?.let { "${it.id}:${it.sendStatus}" }

private val bubbleTimeFormatter: SimpleDateFormat by lazy {
    SimpleDateFormat("h:mm a", Locale.getDefault())
}
private val separatorWeekdayFormatter: SimpleDateFormat by lazy { SimpleDateFormat("EEEE", Locale.getDefault()) }
private val separatorDateFormatter: SimpleDateFormat by lazy { SimpleDateFormat("MMM d", Locale.getDefault()) }
private val separatorDateYearFormatter: SimpleDateFormat by lazy { SimpleDateFormat("MMM d, yyyy", Locale.getDefault()) }

private const val SEPARATOR_GAP_MS = 60 * 60 * 1000L
private const val DAY_MS = 24 * 60 * 60 * 1000L

private fun startOfDayMs(date: java.util.Date): Long {
    val cal = java.util.Calendar.getInstance()
    cal.time = date
    cal.set(java.util.Calendar.HOUR_OF_DAY, 0)
    cal.set(java.util.Calendar.MINUTE, 0)
    cal.set(java.util.Calendar.SECOND, 0)
    cal.set(java.util.Calendar.MILLISECOND, 0)
    return cal.timeInMillis
}

/** Mirror of iOS shouldShowTimeSeparator: separator before the first message,
 *  on a calendar-day change, or after a gap of an hour or more. */
internal fun shouldShowSeparator(prev: java.util.Date?, cur: java.util.Date): Boolean {
    if (prev == null) return true
    if (startOfDayMs(prev) != startOfDayMs(cur)) return true
    return cur.time - prev.time >= SEPARATOR_GAP_MS
}

/** Whether the separator marks a new calendar day (vs. an intra-day gap). Day
 *  boundaries always carry a day label; intra-day gaps show only the time, so a
 *  multi-day "leap" is always obvious. */
internal fun isDayBoundary(prev: java.util.Date?, cur: java.util.Date): Boolean {
    if (prev == null) return true
    return startOfDayMs(prev) != startOfDayMs(cur)
}

/** Centered separator text (matches iOS timeSeparatorText). Intra-day gaps show
 *  only the time; day boundaries prepend Today / Yesterday / weekday / date. */
internal fun separatorText(date: java.util.Date, dayBoundary: Boolean, context: android.content.Context): String {
    val time = bubbleTimeFormatter.format(date)
    if (!dayBoundary) return time.uppercase()
    val daysAgo = ((startOfDayMs(java.util.Date()) - startOfDayMs(date)) / DAY_MS).toInt()
    val label = when {
        daysAgo == 0 -> context.getString(R.string.messaging_separator_today)
        daysAgo == 1 -> context.getString(R.string.messaging_separator_yesterday)
        daysAgo in 2..6 -> separatorWeekdayFormatter.format(date)
        else -> {
            val cal = java.util.Calendar.getInstance().also { it.time = date }
            val sameYear = cal.get(java.util.Calendar.YEAR) == java.util.Calendar.getInstance().get(java.util.Calendar.YEAR)
            if (sameYear) separatorDateFormatter.format(date) else separatorDateYearFormatter.format(date)
        }
    }
    return "$label $time".uppercase()
}

@Composable
private fun DateSeparatorRow(text: String) {
    Text(
        text = text,
        fontSize = 11.sp,
        fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
        color = CorusColors.Tertiary,
        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        modifier = Modifier.fillMaxWidth().padding(vertical = CorusSpacing.sm),
    )
}

@Composable
private fun GroupSystemRow(text: String) {
    if (text.isEmpty()) return
    Text(
        text = text,
        style = CorusFont.caption,
        color = CorusColors.Secondary,
        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        modifier = Modifier.fillMaxWidth().padding(horizontal = CorusSpacing.lg, vertical = CorusSpacing.xs),
    )
}

/** Title for a group thread: custom name, else up to 3 other members' names with
 *  "and N others", else a generic fallback (mirrors iOS displayTitle). */
internal fun groupDisplayTitle(
    name: String?,
    otherMembers: List<fm.corus.android.data.model.CymbalUser>,
    context: android.content.Context,
): String {
    if (!name.isNullOrBlank()) return name
    val names = otherMembers.take(3).map { it.displayName.ifBlank { it.username } }
    if (names.isEmpty()) return context.getString(R.string.messaging_group_fallback_title)
    if (otherMembers.size <= 3) return names.joinToString(", ")
    return names.joinToString(", ") + " " +
        context.getString(R.string.messaging_group_and_others, otherMembers.size - 3)
}

/** Two overlapping member avatars for a group with no custom photo (iOS StackedAvatarView). */
@Composable
internal fun StackedGroupAvatar(
    members: List<fm.corus.android.data.model.CymbalUser>,
    size: androidx.compose.ui.unit.Dp,
) {
    val inner = size * 0.66f
    when {
        members.isEmpty() -> Box(
            modifier = Modifier.size(size).clip(CircleShape).background(CorusColors.CardBackground)
        )
        members.size == 1 -> {
            val u = members[0]
            UserAvatarView(
                avatarURL = u.avatarURL, avatarThumbURL = u.avatarThumbURL,
                displayName = u.displayName, username = u.username, size = size,
            )
        }
        else -> Box(modifier = Modifier.size(size)) {
            val a = members[0]
            val b = members[1]
            Box(modifier = Modifier.align(Alignment.TopStart)) {
                UserAvatarView(
                    avatarURL = a.avatarURL, avatarThumbURL = a.avatarThumbURL,
                    displayName = a.displayName, username = a.username, size = inner,
                )
            }
            Box(
                modifier = Modifier.align(Alignment.BottomEnd)
                    .clip(CircleShape).background(CorusColors.Background).padding(1.dp)
            ) {
                UserAvatarView(
                    avatarURL = b.avatarURL, avatarThumbURL = b.avatarThumbURL,
                    displayName = b.displayName, username = b.username, size = inner,
                )
            }
        }
    }
}

@Composable
private fun BubbleMeta(
    message: CymbalMessage,
    isFromCurrentUser: Boolean,
    deliveryStatus: MessageDeliveryStatus,
    modifier: Modifier = Modifier,
) {
    if (message.sendStatus == MessageSendStatus.FAILED) return
    // Delivery status (the check marks) shows only on your own sent messages. The
    // "edited" label now lives ABOVE the bubble (see MessageBubble) so it never
    // collides with the reaction badge overlapping the bubble's bottom edge, and a
    // received message carries no meta line at all (its time is in the separator).
    if (!isFromCurrentUser) return
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
    ) {
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

/**
 * The reaction badge that overlays a bubble's bottom edge. Group threads collapse
 * every reaction into one tappable badge (the distinct emojis, up to 3, plus the
 * total reactor count) that opens the "Reactions" list; 1:1 threads keep the inline
 * per-emoji pills. The heart springs in via [heartScale] on a fresh double-tap like
 * (read inside graphicsLayer so it only redraws, never recomposes, per frame).
 */
@Composable
private fun ReactionBadge(
    message: CymbalMessage,
    isGroup: Boolean,
    currentUserId: String,
    heartScale: () -> Float,
    onReactionTap: (String) -> Unit,
    onShowReactions: () -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        if (isGroup) {
            val total = message.reactions.values.sumOf { it.size }
            val emojiChars = orderedReactionEmojiChars(message.reactions)
            val heartPresent = message.reactions.any { (k, v) ->
                reactionEmojiChar(k) == REACTION_EMOJIS[0] && v.isNotEmpty()
            }
            Box(
                modifier = Modifier
                    .then(
                        if (heartPresent) Modifier.graphicsLayer {
                            val s = heartScale(); scaleX = s; scaleY = s
                        } else Modifier
                    )
                    .shadow(
                        elevation = 2.dp,
                        shape = CircleShape,
                        spotColor = Color.Black.copy(alpha = 0.08f),
                        ambientColor = Color.Black.copy(alpha = 0.08f),
                    )
                    .clip(CircleShape)
                    .background(CorusColors.Background)
                    .clickable { onShowReactions() }
                    .padding(horizontal = 6.dp, vertical = 2.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    emojiChars.take(3).forEach { Text(text = it, fontSize = 12.sp) }
                    if (total > 1) {
                        Text(
                            text = "$total",
                            style = CorusFont.caption,
                            color = CorusColors.Secondary,
                        )
                    }
                }
            }
        } else {
            message.reactions.forEach { (emojiKey, userIds) ->
                if (userIds.isNotEmpty()) {
                    val emojiChar = REACTION_EMOJIS.getOrNull(REACTION_KEYS.indexOf(emojiKey)) ?: emojiKey
                    val reactedByMe = currentUserId in userIds
                    val isHeart = emojiChar == REACTION_EMOJIS[0]
                    Box(
                        modifier = Modifier
                            .then(
                                if (isHeart) Modifier.graphicsLayer {
                                    val s = heartScale(); scaleX = s; scaleY = s
                                } else Modifier
                            )
                            .shadow(
                                elevation = 2.dp,
                                shape = CircleShape,
                                spotColor = Color.Black.copy(alpha = 0.08f),
                                ambientColor = Color.Black.copy(alpha = 0.08f),
                            )
                            .clip(CircleShape)
                            .background(
                                if (reactedByMe) CorusColors.Accent.copy(alpha = 0.15f)
                                else CorusColors.Background
                            )
                            .clickable { onReactionTap(emojiKey) }
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                    ) {
                        Row(
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
}

/**
 * Stacks the reaction [badge] so it straddles the [bubble]'s bottom edge —
 * overlapping it by [overlap], Instagram-style — instead of floating on a detached
 * line below. The badge is a pure overlay: it never changes the bubble's width, and
 * the stack reserves only the slice of the badge hanging past the bubble so the next
 * message isn't shoved down. The badge tucks into the bottom corner toward screen
 * center: [edgeInset] in from the leading corner of a sent bubble (on the right),
 * the trailing corner of a received one (on the left).
 */
@Composable
private fun BubbleWithReactionBadge(
    isFromCurrentUser: Boolean,
    overlap: Dp,
    edgeInset: Dp,
    badge: (@Composable () -> Unit)?,
    modifier: Modifier = Modifier,
    bubble: @Composable () -> Unit,
) {
    Layout(
        modifier = modifier,
        content = {
            bubble()
            if (badge != null) badge()
        },
    ) { measurables, constraints ->
        val bubblePlaceable = measurables[0].measure(constraints)
        // Measure the badge unbounded so it keeps its intrinsic size and never
        // stretches or compresses the bubble it sits on.
        val badgePlaceable = measurables.getOrNull(1)
            ?.measure(constraints.copy(minWidth = 0, minHeight = 0))
        val overlapPx = overlap.roundToPx()
        // Reserve only the part of the badge that hangs below the bubble.
        val overhang = badgePlaceable?.let { maxOf(0, it.height - overlapPx) } ?: 0
        layout(bubblePlaceable.width, bubblePlaceable.height + overhang) {
            bubblePlaceable.place(0, 0)
            if (badgePlaceable != null) {
                val inset = edgeInset.roundToPx()
                val x = if (isFromCurrentUser) inset
                        else (bubblePlaceable.width - badgePlaceable.width - inset)
                badgePlaceable.place(x, bubblePlaceable.height - overlapPx)
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

/**
 * Stands in for a conversation that is not (or not yet) the caller's to see: a
 * way back, and either the wait for the answer or the answer itself. The wording
 * is the one the other clients already use for this.
 */
@Composable
private fun ClosedThread(
    access: ThreadAccess,
    restriction: MessagingRestriction?,
    name: String?,
    onBack: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(CorusColors.Background),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = CorusSpacing.sm, vertical = CorusSpacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(id = R.string.common_back),
                )
            }
        }
        HorizontalDivider(color = CorusColors.Divider)
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentAlignment = Alignment.Center,
        ) {
            if (access == ThreadAccess.RESOLVING) {
                CircularProgressIndicator(color = CorusColors.Accent)
            } else {
                val closedText = if (restriction != null) {
                    val displayName = name?.takeIf { it.isNotBlank() }
                        ?: stringResource(id = R.string.messaging_restriction_name_fallback)
                    when (restriction) {
                        MessagingRestriction.NOBODY ->
                            stringResource(id = R.string.messaging_restriction_nobody, displayName)
                        MessagingRestriction.FOLLOWERS ->
                            stringResource(id = R.string.messaging_restriction_followers, displayName)
                        MessagingRestriction.FOLLOWING ->
                            stringResource(id = R.string.messaging_restriction_following, displayName)
                    }
                } else {
                    stringResource(id = R.string.messaging_thread_unavailable)
                }
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(CorusSpacing.md),
                    modifier = Modifier.padding(horizontal = CorusSpacing.xl),
                ) {
                    Icon(
                        imageVector = Icons.Outlined.ChatBubbleOutline,
                        contentDescription = null,
                        tint = CorusColors.Tertiary,
                        modifier = Modifier.size(40.dp),
                    )
                    Text(
                        text = closedText,
                        style = CorusFont.bodyMedium,
                        color = CorusColors.Secondary,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.widthIn(max = 280.dp),
                    )
                }
            }
        }
    }
}

@Composable
fun MessageThreadScreen(
    threadId: String,
    otherUserId: String,
    isVisible: Boolean = true,
    onBack: () -> Unit = {},
    onNavigateToProfile: (String) -> Unit = {},
    onNavigateToSong: (CymbalTrack) -> Unit = {},
    onNavigateToFilm: (CymbalMovie) -> Unit = {},
    onNavigateToPost: (String) -> Unit = {},
    onNavigateToArtist: (artistId: String, name: String?, imageUrl: String?) -> Unit = { _, _, _ -> },
    onNavigateToAlbum: (albumId: String, title: String?, artist: String?, coverUrl: String?, year: Int?) -> Unit = { _, _, _, _, _ -> },
    onNavigateToDirector: (directorId: String, name: String?, imageUrl: String?) -> Unit = { _, _, _ -> },
    onNavigateToHashtag: (String) -> Unit = {},
    viewModel: MessageThreadViewModel = hiltViewModel(),
) {
    val nowPlayingManager = viewModel.nowPlayingManager
    val messages by viewModel.messages.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val hasLoadError by viewModel.hasLoadError.collectAsState()
    val otherUsername by viewModel.otherUsername.collectAsState()
    val otherDisplayName by viewModel.otherDisplayName.collectAsState()
    val otherAvatarURL by viewModel.otherAvatarURL.collectAsState()
    val otherAvatarThumbURL by viewModel.otherAvatarThumbURL.collectAsState()
    val artistsInCommonCount by viewModel.artistsInCommonCount.collectAsState()
    val replyToMessage by viewModel.replyToMessage.collectAsState()
    val editingMessage by viewModel.editingMessage.collectAsState()
    val recipientUnread by viewModel.recipientUnread.collectAsState()
    val myReadReceiptsEnabled by viewModel.myReadReceiptsEnabled.collectAsState()
    val groupInfo by viewModel.groupInfo.collectAsState()
    val membersById by viewModel.membersById.collectAsState()
    val isGroup = groupInfo?.isGroup == true
    var showGroupInfo by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val draftStore = remember { DMDraftStore(context) }
    val composerUid = viewModel.currentUserId
    // TextFieldValue (not plain String) so we can place the cursor at the end of
    // the prefilled text when an edit starts; the String overload resets it to 0.
    // Seeded from the local draft store so tapping back doesn't lose unsent text.
    var messageText by remember(threadId, composerUid) {
        mutableStateOf(TextFieldValue(draftStore.load(composerUid, threadId) ?: ""))
    }
    val listState = rememberLazyListState()
    var reactionTarget by remember { mutableStateOf<CymbalMessage?>(null) }
    // The group message whose "Reactions" list bottom sheet is open, if any.
    var reactionsSheetMessage by remember { mutableStateOf<CymbalMessage?>(null) }
    // Drives the big-heart burst over a bubble on a fresh double-tap like (iOS parity).
    var heartBurstMessageId by remember { mutableStateOf<String?>(null) }
    var showGifPicker by remember { mutableStateOf(false) }
    var showAttachmentMenu by remember { mutableStateOf(false) }
    var mediaPickerMode by remember { mutableStateOf<PickerMode?>(null) }
    var fullScreenImageUrl by remember { mutableStateOf<String?>(null) }
    val keyboardController = LocalSoftwareKeyboardController.current
    val composerFocusRequester = remember { FocusRequester() }
    val mentionSuggestions by viewModel.mentionSuggestions.collectAsState()
    val isSearchingMentions by viewModel.isSearchingMentions.collectAsState()
    val hashtagSuggestions by viewModel.hashtagSuggestions.collectAsState()
    val coroutineScope = rememberCoroutineScope()
    var mentionSearchJob by remember { mutableStateOf<Job?>(null) }
    val profileUnavailable = stringResource(R.string.other_profile_unavailable_title)
    val handleMentionTap: (String) -> Unit = { username ->
        keyboardController?.hide()
        coroutineScope.launch {
            val userId = viewModel.resolveUsernameToId(username)
            if (userId != null) onNavigateToProfile(userId)
            else ToastManager.show(profileUnavailable)
        }
    }
    val handleHashtagTap: (String) -> Unit = { hashtag ->
        keyboardController?.hide()
        val tag = hashtag.removePrefix("#")
        viewModel.logHashtagTapped(tag)
        onNavigateToHashtag(tag)
    }

    val latestDraftText = rememberUpdatedState(messageText.text)
    val isEditingDraft = rememberUpdatedState(editingMessage != null)
    DisposableEffect(threadId, composerUid) {
        onDispose {
            if (!isEditingDraft.value) {
                draftStore.save(composerUid, threadId, latestDraftText.value)
            }
            viewModel.clearMentions()
            viewModel.clearHashtags()
        }
    }

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

    // Nothing of the conversation is drawn until it is known to be the caller's
    // to see, and it stops being drawn the moment it isn't — a block landing
    // while the thread is open takes it away.
    val access by viewModel.threadAccess.collectAsState()
    val messagingRestriction by viewModel.messagingRestriction.collectAsState()
    if (access != ThreadAccess.OPEN) {
        ClosedThread(
            access = access,
            restriction = messagingRestriction,
            name = otherUsername,
            onBack = onBack,
        )
        return
    }

    // NavHosts for every tab remain composed, and Navigation Compose can retain
    // a thread underneath a pushed destination. Only the top, selected thread is
    // allowed to auto-read new messages; returning to it clears anything that
    // arrived while it was covered.
    DisposableEffect(isVisible, viewModel) {
        viewModel.setActivelyViewing(isVisible)
        onDispose { viewModel.setActivelyViewing(false) }
    }

    // Mark this thread as the one being actively viewed while the screen is in
    // the foreground: suppresses its push notifications, excludes it from the
    // unread badge, and clears any notification that arrived before opening it.
    // Cleared on STOP (app backgrounded / screen left) so notifications resume.
    val resolvedThreadId by viewModel.resolvedThreadId.collectAsState()
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(resolvedThreadId, lifecycleOwner, isVisible) {
        val tid = resolvedThreadId
        fun activate() {
            if (isVisible && tid != null) {
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

    // Keep the newest message pinned to the bottom (reverseLayout: index 0 = bottom).
    // Keyed on autoScrollKey (newest id + send status), NOT messages.size, so it
    // also re-pins when the newest bubble changes height in place on a send-status
    // transition.
    LaunchedEffect(autoScrollKey(messages)) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(0)
        }
    }

    // This thread renders inside the tab NavHost, UNDERNEATH the persistent global
    // bottom bar (mini player + tab bar). Lift the whole column above whichever is
    // taller: the on-screen keyboard, or that global bar when the keyboard is
    // closed. Without this the composer bar hides behind the global bottom bar.
    val composerBottomInset = with(LocalDensity.current) {
        liftAboveReservedChrome(WindowInsets.ime.getBottom(this).toDp(), LocalBottomBarHeight.current)
    }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(CorusColors.Background)
            .padding(bottom = composerBottomInset),
    ) {
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
            if (isGroup) {
                val otherMembers = (groupInfo?.memberIds ?: emptyList())
                    .filter { it != viewModel.currentUserId }
                    .mapNotNull { membersById[it] }
                // Group avatar + title — tap to open Group Info.
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(CorusSpacing.cornerRadius))
                        .clickable { showGroupInfo = true }
                        .padding(vertical = CorusSpacing.xxs, horizontal = CorusSpacing.xs),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(CorusSpacing.sm),
                ) {
                    val photo = groupInfo?.photoURL
                    if (photo != null) {
                        UserAvatarView(
                            avatarURL = photo, avatarThumbURL = photo,
                            displayName = "", username = "",
                            size = CorusSpacing.avatarSmall,
                        )
                    } else {
                        StackedGroupAvatar(members = otherMembers, size = CorusSpacing.avatarSmall)
                    }
                    Text(
                        text = groupDisplayTitle(groupInfo?.name, otherMembers, context),
                        style = CorusFont.screenTitle,
                        color = CorusColors.Text,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            } else {
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
        }

        HorizontalDivider(color = CorusColors.Divider)

        val bubbleHaptics = LocalHapticManager.current
        // Messages
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
        ) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            reverseLayout = true,
            contentPadding = PaddingValues(vertical = CorusSpacing.sm),
        ) {
            itemsIndexed(messages, key = { _, m -> m.id }) { index, message ->
                // messages is newest-first (reverseLayout). The chronologically
                // older message is at index+1; the newer one at index-1.
                val older = messages.getOrNull(index + 1)
                val newer = messages.getOrNull(index - 1)
                val mine = message.fromUserId == viewModel.currentUserId
                val incomingInGroup = isGroup && !mine && !message.isSystem
                val sender = if (incomingInGroup) membersById[message.fromUserId] else null
                // A sender no longer in memberIds (deleted their account / was
                // removed) gets a neutral "Deleted account" placeholder instead
                // of a blank name + avatar. Gated on groupInfo being loaded so a
                // still-current sender doesn't flash the placeholder mid-load.
                val senderMissing = incomingInGroup && groupInfo != null &&
                    message.fromUserId !in (groupInfo?.memberIds ?: emptyList())
                val showSenderLabel = incomingInGroup &&
                    (older == null || older.isSystem || older.fromUserId != message.fromUserId)
                val showAvatar = incomingInGroup &&
                    (newer == null || newer.isSystem || newer.fromUserId != message.fromUserId)
                val deletedAccountLabel = stringResource(id = R.string.messaging_thread_deleted_account)
                val replyName = if (isGroup && message.replyToUserId != null && !mine)
                    membersById[message.replyToUserId]?.username
                        ?: if (groupInfo != null &&
                            message.replyToUserId !in (groupInfo?.memberIds ?: emptyList())
                        ) deletedAccountLabel else null
                else null

                Column(modifier = Modifier.fillMaxWidth()) {
                    if (shouldShowSeparator(older?.createdAt, message.createdAt)) {
                        DateSeparatorRow(
                            separatorText(message.createdAt, isDayBoundary(older?.createdAt, message.createdAt), context)
                        )
                    }
                    if (message.isSystem) {
                        GroupSystemRow(GroupSystemMessages.localize(message.text ?: "", context))
                    } else {
                        MessageBubble(
                            message = message,
                            isFromCurrentUser = mine,
                            currentUserId = viewModel.currentUserId ?: "",
                            otherUsername = otherUsername,
                            messagingRestriction = messagingRestriction,
                            deliveryStatus = computeDeliveryStatus(
                                message = message,
                                currentUserId = viewModel.currentUserId,
                                messages = messages,
                                recipientUnread = recipientUnread,
                                myReadReceiptsEnabled = if (isGroup) false else myReadReceiptsEnabled,
                            ),
                            nowPlayingManager = nowPlayingManager,
                            isGroup = isGroup,
                            sender = sender,
                            senderMissing = senderMissing,
                            showSenderLabel = showSenderLabel,
                            showAvatar = showAvatar,
                            replyName = replyName,
                            onSenderTap = { sender?.id?.let { onNavigateToProfile(it) } },
                            onLongPress = {
                                bubbleHaptics.impact(HapticManager.ImpactStyle.MEDIUM)
                                reactionTarget = message
                            },
                            onDoubleTap = {
                                bubbleHaptics.impact(HapticManager.ImpactStyle.MEDIUM)
                                val heart = REACTION_EMOJIS[0]
                                val isFreshLike = viewModel.currentUserId
                                    ?.let { it !in (message.reactions[heart] ?: emptyList()) } ?: false
                                viewModel.toggleReaction(threadId, message.id, heart)
                                if (isFreshLike) heartBurstMessageId = message.id
                            },
                            showHeartBurst = heartBurstMessageId == message.id,
                            onReactionTap = { emoji ->
                                bubbleHaptics.impact(HapticManager.ImpactStyle.MEDIUM)
                                viewModel.toggleReaction(threadId, message.id, emoji)
                            },
                            onShowReactions = { reactionsSheetMessage = message },
                            onImageTap = { url ->
                                if (reactionTarget == null) {
                                    fullScreenImageUrl = url
                                }
                            },
                            onRetry = { viewModel.retrySendMessage(message.id) },
                            onNavigateToSong = onNavigateToSong,
                            onNavigateToFilm = onNavigateToFilm,
                            onNavigateToPost = onNavigateToPost,
                            onNavigateToArtist = onNavigateToArtist,
                            onNavigateToAlbum = onNavigateToAlbum,
                            onNavigateToDirector = onNavigateToDirector,
                            onNavigateToProfile = onNavigateToProfile,
                            onMentionTap = handleMentionTap,
                            onHashtagTap = handleHashtagTap,
                            resolvePost = { viewModel.fetchSharedPost(it) },
                        )
                    }
                }
            }
            // reverseLayout: last item sits at the chronological start (visual top).
            val hasOpenerIdentity = otherUsername.isNotBlank() || otherDisplayName.isNotBlank()
            if (!isGroup && messages.isNotEmpty() && hasOpenerIdentity) {
                item(key = "thread-opener") {
                    ThreadOpener(
                        username = otherUsername,
                        displayName = otherDisplayName,
                        avatarURL = otherAvatarURL,
                        avatarThumbURL = otherAvatarThumbURL,
                        artistsInCommon = artistsInCommonCount,
                        onViewProfile = { onNavigateToProfile(otherUserId) },
                    )
                }
            }
        }

            val showThreadOpenerOverlay = !isGroup
                && messages.isEmpty()
                && !isLoading
                && !hasLoadError
                && (otherUsername.isNotBlank() || otherDisplayName.isNotBlank())
            if (showThreadOpenerOverlay) {
                ThreadOpener(
                    username = otherUsername,
                    displayName = otherDisplayName,
                    avatarURL = otherAvatarURL,
                    avatarThumbURL = otherAvatarThumbURL,
                    artistsInCommon = artistsInCommonCount,
                    onViewProfile = { onNavigateToProfile(otherUserId) },
                    modifier = Modifier.align(Alignment.TopCenter),
                )
            }

            // Initial-load spinner: the message listener hasn't delivered a
            // publishable snapshot yet, so `messages` is empty and the list
            // would otherwise be a blank page. A listener failure with nothing
            // on screen swaps to the same retry state the other tabs use.
            if (isLoading && messages.isEmpty()) {
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center),
                    color = CorusColors.Accent,
                )
            } else if (hasLoadError && messages.isEmpty()) {
                OfflineRetryState(
                    modifier = Modifier.align(Alignment.Center),
                    onRetry = { viewModel.loadMessages(threadId, otherUserId) },
                )
            }
        }

        if (messagingRestriction != null) {
            val displayName = otherUsername.takeIf { it.isNotBlank() }
                ?: stringResource(id = R.string.messaging_restriction_name_fallback)
            val restrictionText = when (messagingRestriction) {
                MessagingRestriction.NOBODY ->
                    stringResource(id = R.string.messaging_restriction_nobody, displayName)
                MessagingRestriction.FOLLOWERS ->
                    stringResource(id = R.string.messaging_restriction_followers, displayName)
                MessagingRestriction.FOLLOWING ->
                    stringResource(id = R.string.messaging_restriction_following, displayName)
                null -> ""
            }
            Text(
                text = restrictionText,
                style = CorusFont.bodyMedium,
                color = CorusColors.Secondary,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = CorusSpacing.md, vertical = CorusSpacing.lg),
            )
        } else {
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
                    val draft = draftStore.load(composerUid, threadId) ?: ""
                    messageText = TextFieldValue(text = draft, selection = TextRange(draft.length))
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

        MentionSuggestionsList(
            users = mentionSuggestions.take(4),
            onSelect = { user ->
                messageText = applyMention(messageText, user.username)
                viewModel.clearMentions()
            },
            isSearching = isSearchingMentions,
        )
        HashtagSuggestionsList(
            hashtags = hashtagSuggestions.take(3),
            onSelect = { tag ->
                messageText = applyHashtag(messageText, tag.name)
                viewModel.clearHashtags()
            },
        )

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
                    HorizontalDivider(color = CorusColors.Divider)
                    DropdownMenuItem(
                        text = { Text(stringResource(id = R.string.comment_attachment_music)) },
                        leadingIcon = { Icon(Icons.Filled.MusicNote, contentDescription = null) },
                        onClick = {
                            showAttachmentMenu = false
                            mediaPickerMode = PickerMode.MUSIC_ALL
                        },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(id = R.string.messaging_thread_attachment_film)) },
                        leadingIcon = { Icon(Icons.Filled.Movie, contentDescription = null) },
                        onClick = {
                            showAttachmentMenu = false
                            mediaPickerMode = PickerMode.FILM_ALL
                        },
                    )
                }
            }

            OutlinedTextField(
                value = messageText,
                onValueChange = { newValue ->
                    val textChanged = newValue.text != messageText.text
                    messageText = newValue
                    if (editingMessage == null) {
                        draftStore.save(composerUid, threadId, newValue.text)
                    }
                    if (textChanged) {
                        mentionSearchJob?.cancel()
                        mentionSearchJob = coroutineScope.launch {
                            delay(200)
                            val caret = newValue.selection.start
                            val mention = parseMentionQuery(newValue.text, caret)
                            if (mention != null) {
                                viewModel.clearHashtags()
                                viewModel.searchMentions(mention)
                            } else {
                                viewModel.clearMentions()
                                val hashtag = parseHashtagQuery(newValue.text, caret)
                                if (hashtag != null) viewModel.searchHashtags(hashtag)
                                else viewModel.clearHashtags()
                            }
                        }
                    }
                },
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
                        viewModel.clearMentions()
                        viewModel.clearHashtags()
                        if (editingMessage != null) {
                            viewModel.editMessage(threadId, messageText.text)
                            val draft = draftStore.load(composerUid, threadId) ?: ""
                            messageText = TextFieldValue(text = draft, selection = TextRange(draft.length))
                        } else {
                            viewModel.sendMessage(threadId, messageText.text)
                            draftStore.clear(composerUid, threadId)
                            messageText = TextFieldValue("")
                        }
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
        if (mode == PickerMode.SONG || mode == PickerMode.FILM
            || mode == PickerMode.MUSIC_ALL || mode == PickerMode.FILM_ALL) {
            SongFilmPickerSheet(
                initialMode = mode,
                modes = listOf(mode),
                title = when (mode) {
                    PickerMode.MUSIC_ALL -> stringResource(R.string.comment_attachment_music)
                    PickerMode.FILM_ALL -> stringResource(R.string.messaging_thread_attachment_film)
                    PickerMode.SONG -> stringResource(R.string.messaging_thread_attachment_song)
                    else -> stringResource(R.string.messaging_thread_attachment_film)
                },
                onSongSelected = { track ->
                    viewModel.sendSongMessage(threadId, track)
                    mediaPickerMode = null
                },
                onFilmSelected = { movie ->
                    viewModel.sendFilmMessage(threadId, movie)
                    mediaPickerMode = null
                },
                onArtistSelected = { id, name, image ->
                    viewModel.sendArtistMessage(threadId, id, name, image)
                    mediaPickerMode = null
                },
                onAlbumSelected = { id, title, artist, cover, year ->
                    viewModel.sendAlbumMessage(threadId, id, title, artist, cover, year)
                    mediaPickerMode = null
                },
                onDirectorSelected = { id, name, image ->
                    viewModel.sendDirectorMessage(threadId, id, name, image)
                    mediaPickerMode = null
                },
                onDismiss = { mediaPickerMode = null },
            )
        } else {
            EntityPickerSheet(
                kind = mode,
                onArtistSelected = { id, name, image ->
                    viewModel.sendArtistMessage(threadId, id, name, image)
                    mediaPickerMode = null
                },
                onAlbumSelected = { id, title, artist, cover, year ->
                    viewModel.sendAlbumMessage(threadId, id, title, artist, cover, year)
                    mediaPickerMode = null
                },
                onDirectorSelected = { id, name, image ->
                    viewModel.sendDirectorMessage(threadId, id, name, image)
                    mediaPickerMode = null
                },
                onDismiss = { mediaPickerMode = null },
            )
        }
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

    // Group info sheet
    val gi = groupInfo
    if (showGroupInfo && gi != null && gi.isGroup) {
        GroupInfoSheet(
            viewModel = viewModel,
            groupInfo = gi,
            membersById = membersById,
            onDismiss = { showGroupInfo = false },
            onLeft = {
                showGroupInfo = false
                onBack()
            },
            onNavigateToProfile = onNavigateToProfile,
        )
    }

    // Group "Reactions" list sheet — who reacted with what; tap your own row to
    // remove. Opened by tapping a group message's collapsed reaction badge.
    val reactionsMsg = reactionsSheetMessage
    if (reactionsMsg != null) {
        // Re-resolve from the live message list so the sheet reflects reactions
        // that arrive (or are removed) while it's open.
        val liveMsg = messages.firstOrNull { it.id == reactionsMsg.id } ?: reactionsMsg
        MessageReactionsBottomSheet(
            message = liveMsg,
            membersById = membersById,
            currentUserId = viewModel.currentUserId ?: "",
            onRemove = { emojiKey ->
                viewModel.toggleReaction(threadId, liveMsg.id, emojiKey)
                reactionsSheetMessage = null
            },
            onUserClick = { userId ->
                reactionsSheetMessage = null
                onNavigateToProfile(userId)
            },
            onDismiss = { reactionsSheetMessage = null },
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
    messagingRestriction: MessagingRestriction? = null,
    deliveryStatus: MessageDeliveryStatus,
    nowPlayingManager: NowPlayingManager,
    showHeartBurst: Boolean = false,
    isGroup: Boolean = false,
    sender: fm.corus.android.data.model.CymbalUser? = null,
    // Sender is no longer a participant (deleted account / removed); render a
    // neutral placeholder since `sender` will be null.
    senderMissing: Boolean = false,
    showSenderLabel: Boolean = false,
    showAvatar: Boolean = false,
    replyName: String? = null,
    onSenderTap: () -> Unit = {},
    onLongPress: () -> Unit,
    onDoubleTap: () -> Unit,
    onReactionTap: (String) -> Unit,
    onShowReactions: () -> Unit = {},
    onImageTap: (String) -> Unit = {},
    onRetry: () -> Unit = {},
    onNavigateToSong: (CymbalTrack) -> Unit = {},
    onNavigateToFilm: (CymbalMovie) -> Unit = {},
    onNavigateToPost: (String) -> Unit = {},
    onNavigateToArtist: (artistId: String, name: String?, imageUrl: String?) -> Unit = { _, _, _ -> },
    onNavigateToAlbum: (albumId: String, title: String?, artist: String?, coverUrl: String?, year: Int?) -> Unit = { _, _, _, _, _ -> },
    onNavigateToDirector: (directorId: String, name: String?, imageUrl: String?) -> Unit = { _, _, _ -> },
    onNavigateToProfile: (String) -> Unit = {},
    onMentionTap: (String) -> Unit = {},
    onHashtagTap: (String) -> Unit = {},
    resolvePost: suspend (String) -> CymbalPost? = { null },
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

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = CorusSpacing.md, vertical = CorusSpacing.xxs),
        horizontalArrangement = if (isFromCurrentUser) Arrangement.End else Arrangement.Start,
        verticalAlignment = Alignment.Bottom,
    ) {
      // Incoming group messages get a left avatar gutter: the sender's avatar at
      // the end of a run, an empty spacer otherwise so bubbles stay aligned.
      if (isGroup && !isFromCurrentUser) {
        if (showAvatar && sender != null) {
            UserAvatarView(
                avatarURL = sender.avatarURL,
                avatarThumbURL = sender.avatarThumbURL,
                displayName = sender.displayName,
                username = sender.username,
                size = 26.dp,
                modifier = Modifier.clickable(onClick = onSenderTap),
            )
        } else if (showAvatar && senderMissing) {
            // Neutral avatar for a former member (blank name renders the "?"
            // placeholder), not tappable since there's no profile to open.
            UserAvatarView(
                avatarURL = null,
                avatarThumbURL = null,
                displayName = "",
                username = "",
                size = 26.dp,
            )
        } else {
            Spacer(modifier = Modifier.width(26.dp))
        }
        Spacer(modifier = Modifier.width(6.dp))
      }
      Column(
        horizontalAlignment = if (isFromCurrentUser) Alignment.End else Alignment.Start,
      ) {
        // Sender name at the start of a run (incoming group only).
        if (showSenderLabel && sender != null) {
            Text(
                text = sender.displayName.ifBlank { sender.username },
                style = CorusFont.caption,
                color = CorusColors.Secondary,
                modifier = Modifier
                    .clickable(onClick = onSenderTap)
                    .padding(start = 2.dp, bottom = 2.dp),
            )
        } else if (showSenderLabel && senderMissing) {
            Text(
                text = stringResource(id = R.string.messaging_thread_deleted_account),
                style = CorusFont.caption,
                color = CorusColors.Secondary,
                modifier = Modifier.padding(start = 2.dp, bottom = 2.dp),
            )
        }
        // The "edited" label sits ABOVE the bubble (on the message's own side) so it
        // never competes with the reaction badge that overlaps the bubble's bottom
        // edge below. Kept to one line so a narrow bubble can't wrap it to slivers.
        if (message.isEdited && message.sendStatus != MessageSendStatus.FAILED) {
            Text(
                text = stringResource(id = R.string.messaging_thread_edited),
                fontSize = 10.sp,
                color = CorusColors.Tertiary,
                maxLines = 1,
                softWrap = false,
                modifier = Modifier.padding(start = 2.dp, end = 2.dp, bottom = 1.dp),
            )
        }
        // Message bubble — the reaction badge overlaps its bottom edge so it reads as
        // attached (Instagram-style) instead of floating on a detached line below.
        BubbleWithReactionBadge(
            isFromCurrentUser = isFromCurrentUser,
            overlap = 8.dp,
            edgeInset = 8.dp,
            badge = if (message.reactions.isNotEmpty()) {
                {
                    ReactionBadge(
                        message = message,
                        isGroup = isGroup,
                        currentUserId = currentUserId,
                        heartScale = { heartPopScale.value },
                        onReactionTap = onReactionTap,
                        onShowReactions = onShowReactions,
                    )
                }
            } else null,
        ) {
        // Message bubble — quoted reply context is rendered inside
        Box(
            modifier = Modifier
                .widthIn(max = 280.dp)
                .then(if (isSending) Modifier.alpha(0.7f) else Modifier)
                .onGloballyPositioned { bubbleCoords = it }
                .pointerInput(message.id, annotatedText, onMentionTap, onHashtagTap) {
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
                                        val mention = at.getStringAnnotations("mention", charOffset, charOffset)
                                            .firstOrNull()
                                        val hashtag = at.getStringAnnotations("hashtag", charOffset, charOffset)
                                            .firstOrNull()
                                        val url = at.getStringAnnotations("URL", charOffset, charOffset)
                                            .firstOrNull()
                                        when {
                                            mention != null -> onMentionTap(mention.item)
                                            hashtag != null -> onHashtagTap(hashtag.item)
                                            url != null -> {
                                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url.item))
                                                context.startActivity(intent)
                                            }
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
                    val authorName = if (isOwnQuote) stringResource(id = R.string.messaging_thread_you)
                                     else replyName ?: otherUsername
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

                // Shared artist / album / director content
                if (message.type == MessageType.SHARED_ARTIST) {
                    SharedArtistContent(
                        message = message,
                        isFromCurrentUser = isFromCurrentUser,
                        onNavigate = {
                            message.artistId?.takeIf { it.isNotBlank() }?.let {
                                onNavigateToArtist(it, message.artistName, message.artistImageURL)
                            }
                        },
                    )
                    if (!message.text.isNullOrBlank()) {
                        Spacer(modifier = Modifier.height(CorusSpacing.xs))
                    }
                }
                if (message.type == MessageType.SHARED_ALBUM) {
                    SharedAlbumContent(
                        message = message,
                        isFromCurrentUser = isFromCurrentUser,
                        onNavigate = {
                            message.albumId?.takeIf { it.isNotBlank() }?.let {
                                onNavigateToAlbum(
                                    it, message.albumTitle, message.albumArtistName,
                                    message.albumCoverURL, message.albumYear?.toIntOrNull(),
                                )
                            }
                        },
                    )
                    if (!message.text.isNullOrBlank()) {
                        Spacer(modifier = Modifier.height(CorusSpacing.xs))
                    }
                }
                if (message.type == MessageType.SHARED_DIRECTOR) {
                    SharedDirectorContent(
                        message = message,
                        isFromCurrentUser = isFromCurrentUser,
                        onNavigate = {
                            message.directorId?.takeIf { it.isNotBlank() }?.let {
                                onNavigateToDirector(it, message.directorName, message.directorImageURL)
                            }
                        },
                    )
                    if (!message.text.isNullOrBlank()) {
                        Spacer(modifier = Modifier.height(CorusSpacing.xs))
                    }
                }
                if (message.type == MessageType.SHARED_PROFILE) {
                    SharedProfileContent(
                        message = message,
                        isFromCurrentUser = isFromCurrentUser,
                        onNavigate = {
                            message.sharedUserId?.takeIf { it.isNotBlank() }?.let {
                                onNavigateToProfile(it)
                            }
                        },
                    )
                    if (!message.text.isNullOrBlank()) {
                        Spacer(modifier = Modifier.height(CorusSpacing.xs))
                    }
                }

                // Shared post content — resolves the post by id (mirrors iOS)
                // and deep-links to post detail on tap.
                if (message.type == MessageType.SHARED_POST) {
                    SharedPostContent(
                        postId = message.sharedPostId.orEmpty(),
                        isFromCurrentUser = isFromCurrentUser,
                        resolvePost = resolvePost,
                        onNavigate = { message.sharedPostId?.takeIf { it.isNotBlank() }?.let(onNavigateToPost) },
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
        }

        // Delivery status (the check marks) sits below the bubble on your own sent
        // messages; reactions now overlap the bubble above (not this line), so it
        // never gets squeezed. Received messages render nothing here.
        BubbleMeta(
            message = message,
            isFromCurrentUser = isFromCurrentUser,
            deliveryStatus = deliveryStatus,
            modifier = Modifier.padding(top = 2.dp),
        )

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
                    val displayName = otherUsername.takeIf { it.isNotBlank() }
                        ?: stringResource(id = R.string.messaging_restriction_name_fallback)
                    val disabledText = when (messagingRestriction) {
                        MessagingRestriction.FOLLOWERS ->
                            stringResource(id = R.string.messaging_restriction_followers, displayName)
                        MessagingRestriction.FOLLOWING ->
                            stringResource(id = R.string.messaging_restriction_following, displayName)
                        else ->
                            stringResource(id = R.string.messaging_restriction_nobody, displayName)
                    }
                    Text(
                        text = disabledText,
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
}

private val MENTION_HASHTAG_REGEX = Regex("(@[\\w.]+)|(#\\w+)")

internal fun buildLinkifiedText(
    text: String,
    isFromCurrentUser: Boolean,
): AnnotatedString {
    val linkColor = if (isFromCurrentUser) Color.White else CorusColors.Accent
    data class Span(val range: IntRange, val kind: String, val value: String)
    val spans = mutableListOf<Span>()
    URL_REGEX.findAll(text).forEach { match ->
        spans.add(Span(match.range, "URL", match.value))
    }
    MENTION_HASHTAG_REGEX.findAll(text).forEach { match ->
        val overlapsUrl = spans.any { it.kind == "URL" && it.range.first <= match.range.last && match.range.first <= it.range.last }
        if (overlapsUrl) return@forEach
        val token = match.value
        if (token.startsWith("@")) {
            val handle = mentionHandle(token)
            if (handle.isNotEmpty()) {
                spans.add(Span(match.range, "mention", handle))
            }
        } else {
            spans.add(Span(match.range, "hashtag", token.removePrefix("#")))
        }
    }
    spans.sortBy { it.range.first }

    return buildAnnotatedString {
        var lastIndex = 0
        for (span in spans) {
            if (span.range.first > lastIndex) {
                append(text.substring(lastIndex, span.range.first))
            }
            val token = text.substring(span.range.first, span.range.last + 1)
            when (span.kind) {
                "URL" -> {
                    pushStringAnnotation("URL", span.value)
                    withStyle(
                        SpanStyle(
                            textDecoration = TextDecoration.Underline,
                            color = linkColor,
                        )
                    ) {
                        append(token)
                    }
                    pop()
                }
                "mention" -> {
                    pushStringAnnotation("mention", span.value)
                    withStyle(SpanStyle(color = linkColor, fontWeight = FontWeight.ExtraBold)) {
                        append(token)
                    }
                    pop()
                }
                "hashtag" -> {
                    pushStringAnnotation("hashtag", span.value)
                    // ExtraBold like @mentions so a tag stays readable on a
                    // white-on-accent own bubble (regular weight looked like
                    // plain body text). Incoming bubbles already use accent.
                    withStyle(SpanStyle(color = linkColor, fontWeight = FontWeight.ExtraBold)) {
                        append(token)
                    }
                    pop()
                }
            }
            lastIndex = span.range.last + 1
        }
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
                        val cymbalTrack = song.asCymbalTrack().copy(source = source)
                        nowPlayingManager.routePlayTap(
                            track = cymbalTrack,
                            onPreview = {
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
                                    source = source,
                                )
                            },
                        )
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
 * Shared-post message bubble. The `sharedPost` message stores only the post id
 * (mirrors iOS and the backend, which does not denormalize track/film fields for
 * this type), so we resolve the post to render artwork/title. Tapping anywhere
 * deep-links to the in-app post detail page — NOT the song/film page.
 */
@Composable
private fun SharedPostContent(
    postId: String,
    isFromCurrentUser: Boolean,
    resolvePost: suspend (String) -> CymbalPost?,
    onNavigate: () -> Unit,
) {
    var post by remember(postId) { mutableStateOf<CymbalPost?>(null) }
    LaunchedEffect(postId) {
        if (postId.isNotBlank()) post = resolvePost(postId)
    }

    val textColor = if (isFromCurrentUser) Color.White else CorusColors.Text
    val subtitleColor = if (isFromCurrentUser) Color.White.copy(alpha = 0.85f) else CorusColors.Secondary
    val title = post?.displayTitle.orEmpty()
    val subtitle = post?.displaySubtitle.orEmpty()

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
                .background(CorusColors.Secondary.copy(alpha = 0.15f)),
        ) {
            ShimmerAsyncImage(
                model = post?.displayImageLargeURL ?: post?.displayImageURL,
                contentDescription = title,
                modifier = Modifier.fillMaxSize(),
                contentScale = androidx.compose.ui.layout.ContentScale.Crop,
            )
        }
        Spacer(modifier = Modifier.width(CorusSpacing.sm))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = CorusFont.body,
                color = textColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = subtitle,
                style = CorusFont.caption,
                color = subtitleColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(modifier = Modifier.width(CorusSpacing.xs))
        Icon(
            painter = painterResource(R.drawable.ic_stat_corus),
            contentDescription = null,
            tint = subtitleColor,
            modifier = Modifier.size(14.dp),
        )
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

@Composable
private fun SharedArtistContent(
    message: CymbalMessage,
    isFromCurrentUser: Boolean,
    onNavigate: () -> Unit,
) {
    val name = message.artistName.orEmpty()
    val textColor = if (isFromCurrentUser) Color.White else CorusColors.Text
    val subtitleColor = if (isFromCurrentUser) Color.White.copy(alpha = 0.85f) else CorusColors.Secondary
    Row(
        modifier = Modifier
            .widthIn(max = 240.dp)
            .clickable { onNavigate() },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ShimmerAsyncImage(
            model = message.artistImageURL,
            contentDescription = name,
            modifier = Modifier
                .size(56.dp)
                .clip(RoundedCornerShape(50)),
            contentScale = androidx.compose.ui.layout.ContentScale.Crop,
        )
        Spacer(modifier = Modifier.width(CorusSpacing.sm))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = name,
                style = CorusFont.body,
                color = textColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = stringResource(R.string.messaging_thread_attachment_artist),
                style = CorusFont.caption,
                color = subtitleColor,
                maxLines = 1,
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
private fun SharedAlbumContent(
    message: CymbalMessage,
    isFromCurrentUser: Boolean,
    onNavigate: () -> Unit,
) {
    val title = message.albumTitle.orEmpty()
    val artist = message.albumArtistName.orEmpty()
    val textColor = if (isFromCurrentUser) Color.White else CorusColors.Text
    val subtitleColor = if (isFromCurrentUser) Color.White.copy(alpha = 0.85f) else CorusColors.Secondary
    Row(
        modifier = Modifier
            .widthIn(max = 240.dp)
            .clickable { onNavigate() },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ShimmerAsyncImage(
            model = message.albumCoverURL,
            contentDescription = title,
            modifier = Modifier
                .size(56.dp)
                .clip(RoundedCornerShape(CorusSpacing.cornerRadius)),
            contentScale = androidx.compose.ui.layout.ContentScale.Crop,
        )
        Spacer(modifier = Modifier.width(CorusSpacing.sm))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = CorusFont.body,
                color = textColor,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = artist.ifBlank { stringResource(R.string.messaging_thread_attachment_album) },
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
private fun SharedDirectorContent(
    message: CymbalMessage,
    isFromCurrentUser: Boolean,
    onNavigate: () -> Unit,
) {
    val name = message.directorName.orEmpty()
    val textColor = if (isFromCurrentUser) Color.White else CorusColors.Text
    val subtitleColor = if (isFromCurrentUser) Color.White.copy(alpha = 0.85f) else CorusColors.Secondary
    Row(
        modifier = Modifier
            .widthIn(max = 240.dp)
            .clickable { onNavigate() },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ShimmerAsyncImage(
            model = message.directorImageURL,
            contentDescription = name,
            modifier = Modifier
                .size(56.dp)
                .clip(RoundedCornerShape(50)),
            contentScale = androidx.compose.ui.layout.ContentScale.Crop,
        )
        Spacer(modifier = Modifier.width(CorusSpacing.sm))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = name,
                style = CorusFont.body,
                color = textColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = stringResource(R.string.messaging_thread_attachment_director),
                style = CorusFont.caption,
                color = subtitleColor,
                maxLines = 1,
            )
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

/**
 * Shared-profile message bubble — tapping opens the shared user's profile.
 * Username-first per the app's user-row convention: `@username` leads, the
 * display name is the muted subtitle.
 */
@Composable
private fun SharedProfileContent(
    message: CymbalMessage,
    isFromCurrentUser: Boolean,
    onNavigate: () -> Unit,
) {
    val username = message.sharedUsername.orEmpty()
    val displayName = message.sharedDisplayName?.takeIf { it.isNotBlank() }
    val textColor = if (isFromCurrentUser) Color.White else CorusColors.Text
    val subtitleColor = if (isFromCurrentUser) Color.White.copy(alpha = 0.85f) else CorusColors.Secondary
    Row(
        modifier = Modifier
            .widthIn(max = 240.dp)
            .clickable { onNavigate() },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ShimmerAsyncImage(
            model = message.sharedAvatarURL,
            contentDescription = username,
            modifier = Modifier
                .size(56.dp)
                .clip(RoundedCornerShape(50)),
            contentScale = androidx.compose.ui.layout.ContentScale.Crop,
        )
        Spacer(modifier = Modifier.width(CorusSpacing.sm))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = if (username.isNotBlank()) "@$username"
                    else stringResource(R.string.messaging_thread_shared_profile),
                style = CorusFont.body,
                color = textColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = displayName ?: stringResource(R.string.messaging_thread_shared_profile),
                style = CorusFont.caption,
                color = subtitleColor,
                maxLines = 1,
            )
        }
        Spacer(modifier = Modifier.width(CorusSpacing.xs))
        Icon(
            imageVector = Icons.Filled.Person,
            contentDescription = null,
            tint = subtitleColor,
            modifier = Modifier.size(14.dp),
        )
    }
}
