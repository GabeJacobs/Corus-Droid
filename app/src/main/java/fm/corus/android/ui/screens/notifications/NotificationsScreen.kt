package fm.corus.android.ui.screens.notifications

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.foundation.text.ClickableText
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.input.ImeAction
import android.widget.Toast
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationManagerCompat
import androidx.hilt.navigation.compose.hiltViewModel
import coil3.compose.AsyncImage
import fm.corus.android.R
import fm.corus.android.data.model.CommentAttachedFilm
import fm.corus.android.data.model.CommentAttachedSong
import fm.corus.android.data.model.CymbalNotification
import fm.corus.android.data.model.NotificationType
import fm.corus.android.domain.HapticManager
import fm.corus.android.ui.LocalHapticManager
import fm.corus.android.ui.components.CommentAttachmentPendingChip
import fm.corus.android.ui.components.PickerMode
import fm.corus.android.ui.components.SkeletonNotificationRow
import fm.corus.android.ui.components.SongFilmPickerSheet
import fm.corus.android.ui.components.UserAvatarView
import fm.corus.android.ui.theme.CorusColors
import fm.corus.android.ui.theme.CorusFont
import fm.corus.android.ui.theme.CorusSpacing
import fm.corus.android.ui.util.DateUtils

@Composable
fun NotificationsScreen(
    viewModel: NotificationsViewModel = hiltViewModel(),
    scrollToTopTrigger: Int = 0,
    unreadMessageCount: Int = 0,
    onNavigateToMessages: () -> Unit = {},
    onNavigateToUser: (String) -> Unit = {},
    onNavigateToPost: (String) -> Unit = {},
    onNavigateToPostComments: (postId: String, commentId: String) -> Unit = { _, _ -> },
) {
    val notifications by viewModel.notifications.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val isRefreshing by viewModel.isRefreshing.collectAsState()
    val hasMoreNotifications by viewModel.hasMoreNotifications.collectAsState()
    val followingIds by viewModel.followingIds.collectAsState()
    val likedCommentIds by viewModel.likedCommentIds.collectAsState()
    val newNotificationIds by viewModel.newNotificationIds.collectAsState()
    val replyingTo by viewModel.replyingToNotification.collectAsState()
    val isSendingReply by viewModel.isSendingReply.collectAsState()
    val listState = rememberLazyListState()

    var lastScrollTrigger by rememberSaveable { mutableIntStateOf(0) }
    LaunchedEffect(scrollToTopTrigger) {
        if (scrollToTopTrigger > lastScrollTrigger) {
            listState.animateScrollToItem(0)
            lastScrollTrigger = scrollToTopTrigger
        }
    }

    val context = LocalContext.current
    LaunchedEffect(Unit) {
        viewModel.loadNotifications()
        // Clear any outstanding system-tray notifications so the launcher icon
        // dot clears as well — matches iOS MainTabView clearing the OS badge.
        try { NotificationManagerCompat.from(context).cancelAll() } catch (_: Exception) { }
    }

    // One-shot toasts from sendReply() — success/failure feedback.
    LaunchedEffect(Unit) {
        viewModel.replyToastEvents.collect { message ->
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        }
    }

    // Dismiss the keyboard when the user swipes the list downward — matches
    // the common Android pattern of "swipe-to-dismiss" keyboard.
    val keyboardController = LocalSoftwareKeyboardController.current
    val dismissKeyboardOnScroll = remember(keyboardController) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                // Downward drag (positive y) while the user is dragging — hide IME.
                if (source == NestedScrollSource.UserInput && available.y > 0f) {
                    keyboardController?.hide()
                }
                return Offset.Zero
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Header
        NotificationsHeader(
            unreadMessageCount = unreadMessageCount,
            onMessagesTapped = onNavigateToMessages,
        )

        HorizontalDivider(color = CorusColors.Divider)

        val haptics = LocalHapticManager.current
        @OptIn(ExperimentalMaterial3Api::class)
        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = {
                // Mirrors iOS NotificationsView.refreshable haptic.
                haptics.impact(HapticManager.ImpactStyle.LIGHT)
                viewModel.refreshNotifications()
            },
            modifier = Modifier.weight(1f).fillMaxWidth(),
        ) {
            when {
                isLoading && notifications.isEmpty() -> {
                    // Loading skeleton — 12 shimmer rows
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        items(12) {
                            SkeletonNotificationRow()
                        }
                    }
                }
                notifications.isEmpty() && !isLoading -> {
                    // Empty state
                    NotificationsEmptyState()
                }
                else -> {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .fillMaxSize()
                            .nestedScroll(dismissKeyboardOnScroll),
                    ) {
                        items(notifications, key = { it.id }) { notification ->
                            NotificationRow(
                                notification = notification,
                                isFollowing = followingIds.contains(notification.fromUser.id),
                                isCommentLiked = notification.commentId != null &&
                                        likedCommentIds.contains(notification.commentId),
                                isNew = newNotificationIds.contains(notification.id),
                                onClick = {
                                    viewModel.markNotificationTapped(notification.id)
                                    if (notification.type == NotificationType.TASTE_MATCH) {
                                        val isActivity = notification.subtype == "activity_song" ||
                                                notification.subtype == "activity_film"
                                        val postId = notification.postId
                                        if (isActivity && postId != null) {
                                            onNavigateToPost(postId)
                                        } else {
                                            onNavigateToUser(notification.fromUser.id)
                                        }
                                    } else {
                                        val postId = notification.postId
                                        if (postId != null && notification.commentId != null) {
                                            onNavigateToPostComments(postId, notification.commentId)
                                        } else if (postId != null) {
                                            onNavigateToPost(postId)
                                        } else {
                                            onNavigateToUser(notification.fromUser.id)
                                        }
                                    }
                                },
                                onUserTap = {
                                    viewModel.markNotificationTapped(notification.id)
                                    onNavigateToUser(notification.fromUser.id)
                                },
                                onFollowToggle = {
                                    viewModel.toggleFollow(notification.fromUser.id)
                                },
                                onCommentLike = {
                                    viewModel.toggleCommentLike(notification)
                                },
                                onReplyTap = {
                                    viewModel.setReplyingToNotification(notification)
                                },
                            )
                            if (notification.type == NotificationType.TASTE_MATCH) {
                                LaunchedEffect(notification.id) {
                                    viewModel.markTasteMatchRowViewed(notification)
                                }
                            }
                            HorizontalDivider(
                                color = CorusColors.Divider,
                                modifier = Modifier.padding(
                                    start = CorusSpacing.lg + CorusSpacing.avatarMedium + CorusSpacing.md,
                                ),
                            )
                        }

                        if (hasMoreNotifications) {
                            item {
                                CircularProgressIndicator(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = CorusSpacing.xl)
                                        .wrapContentWidth(Alignment.CenterHorizontally),
                                    color = CorusColors.Secondary,
                                    strokeWidth = 2.dp,
                                )
                                LaunchedEffect(notifications.size) {
                                    viewModel.loadMoreNotifications()
                                }
                            }
                        }
                    }
                }
            }
        }

        if (replyingTo != null) {
            val replyPendingSong by viewModel.replyPendingSong.collectAsState()
            val replyPendingFilm by viewModel.replyPendingFilm.collectAsState()
            var showReplySongFilmPicker by remember { mutableStateOf(false) }

            if (showReplySongFilmPicker) {
                SongFilmPickerSheet(
                    initialMode = PickerMode.SONG,
                    onSongSelected = { track ->
                        viewModel.attachReplySong(track)
                        showReplySongFilmPicker = false
                    },
                    onFilmSelected = { movie ->
                        viewModel.attachReplyFilm(movie)
                        showReplySongFilmPicker = false
                    },
                    onDismiss = { showReplySongFilmPicker = false },
                )
            }

            InlineReplyBar(
                replyingTo = replyingTo!!,
                isSending = isSendingReply,
                pendingSong = replyPendingSong,
                pendingFilm = replyPendingFilm,
                onAttachmentClick = { showReplySongFilmPicker = true },
                onClearAttachment = { viewModel.clearReplyAttachment() },
                onSend = { text -> viewModel.sendReply(text) },
                onCancel = {
                    viewModel.setReplyingToNotification(null)
                    viewModel.clearReplyAttachment()
                },
            )
        }
    }
}

@Composable
private fun NotificationsHeader(
    unreadMessageCount: Int,
    onMessagesTapped: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = CorusSpacing.lg, vertical = CorusSpacing.md),
    ) {
        // Center: "Activity" title
        Text(
            text = stringResource(id = R.string.notifications_activity_title),
            style = CorusFont.displayName,
            color = CorusColors.Text,
            modifier = Modifier.align(Alignment.Center),
        )

        // Right: Message inbox button
        Box(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .size(34.dp)
                .clickable(onClick = onMessagesTapped),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Filled.Email,
                contentDescription = stringResource(id = R.string.notifications_cd_messages),
                modifier = Modifier.size(24.dp),
                tint = CorusColors.Secondary,
            )

            // Unread badge
            if (unreadMessageCount > 0) {
                val badgeText = if (unreadMessageCount > 99) "99+" else "$unreadMessageCount"
                val isWide = badgeText.length > 1
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .defaultMinSize(minWidth = 18.dp, minHeight = 18.dp)
                        .background(Color.Red, CircleShape)
                        .border(1.5.dp, Color.White, CircleShape)
                        .padding(horizontal = if (isWide) 5.dp else 0.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = badgeText,
                        color = Color.White,
                        fontSize = 11.sp,
                        lineHeight = 11.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        textAlign = TextAlign.Center,
                        style = LocalTextStyle.current.copy(
                            platformStyle = PlatformTextStyle(includeFontPadding = false),
                        ),
                    )
                }
            }
        }
    }
}

@Composable
private fun NotificationRow(
    notification: CymbalNotification,
    isFollowing: Boolean,
    isCommentLiked: Boolean,
    isNew: Boolean,
    onClick: () -> Unit,
    onUserTap: () -> Unit,
    onFollowToggle: () -> Unit,
    onCommentLike: () -> Unit,
    onReplyTap: () -> Unit,
) {
    val showFollowButton = notification.type == NotificationType.FOLLOW ||
            notification.type == NotificationType.CONTACT_JOINED
    val showCommentActions = notification.supportsCommentActions

    // Subtle accent tint for notifications arrived since last visit, matches
    // iOS `.background(Color.corusAccent.opacity(0.04))`.
    val rowBackground = if (isNew) {
        CorusColors.Accent.copy(alpha = 0.04f)
    } else {
        Color.Transparent
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(rowBackground)
            .clickable(onClick = onClick)
            .padding(horizontal = CorusSpacing.lg, vertical = CorusSpacing.md),
        verticalAlignment = if (showCommentActions) Alignment.Top else Alignment.CenterVertically,
    ) {
        // Left: User avatar (36dp — matches iOS avatarMedium) — taps to user profile
        Box(modifier = Modifier.clickable(onClick = onUserTap)) {
            UserAvatarView(
                avatarURL = notification.fromUser.avatarURL,
                avatarThumbURL = notification.fromUser.avatarThumbURL,
                displayName = notification.fromUser.displayName,
                size = CorusSpacing.avatarMedium,
            )
        }

        Spacer(modifier = Modifier.width(CorusSpacing.md))

        // Middle: username + message + timestamp — matches iOS inline Text concat
        // Username portion is tappable to user profile via ClickableText
        // For notifications with comment text (COMMENT, MENTION, REPLY), we allow
        // 3 lines for the message and ensure the timestamp is always visible even
        // when truncated, matching the iOS .truncationMode(.middle) behavior.
        val hasCommentText = notification.commentText != null
        val maxLines = if (hasCommentText) 4 else 2
        val timeString = DateUtils.relativeTime(LocalContext.current, notification.timestamp)
        val timeSuffix = " $timeString"

        val fullAnnotatedText = if (notification.type == NotificationType.TASTE_MATCH) {
            buildTasteMatchAnnotated(notification, timeString)
        } else buildAnnotatedString {
            pushStringAnnotation(tag = "USER", annotation = notification.fromUser.id)
            withStyle(
                SpanStyle(
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 14.sp,
                )
            ) {
                append(notification.fromUser.username)
            }
            pop()
            append(" ")
            withStyle(
                SpanStyle(
                    fontWeight = FontWeight.Normal,
                    fontSize = 15.sp,
                )
            ) {
                append(notification.message)
            }
            append(" ")
            withStyle(
                SpanStyle(
                    fontWeight = FontWeight.Normal,
                    fontSize = 12.sp,
                    color = CorusColors.Secondary,
                )
            ) {
                append(timeString)
            }
        }

        // When comment text overflows, we rebuild the string with the message
        // trimmed so that "... {timestamp}" always appears at the end.
        var displayText by remember(notification.id) { mutableStateOf(fullAnnotatedText) }
        var didOverflow by remember(notification.id) { mutableStateOf(false) }
        val secondaryColor = CorusColors.Secondary

        Column(modifier = Modifier.weight(1f)) {
            ClickableText(
                text = displayText,
                style = CorusFont.body.copy(color = CorusColors.Text),
                maxLines = maxLines,
                overflow = if (didOverflow) TextOverflow.Clip else TextOverflow.Ellipsis,
                onTextLayout = { layoutResult ->
                    if (hasCommentText && layoutResult.hasVisualOverflow && !didOverflow) {
                        // Text overflowed — find how many chars fit within the visible
                        // lines, then trim the message so "... {timestamp}" is appended.
                        val lastVisibleLine = (maxLines - 1).coerceAtMost(layoutResult.lineCount - 1)
                        val lastCharIndex = layoutResult.getLineEnd(lastVisibleLine, visibleEnd = true)
                        // Reserve space for the ellipsis + timestamp suffix
                        val suffixLen = timeSuffix.length + 1 // "…" + " 1d"
                        val trimEnd = (lastCharIndex - suffixLen).coerceAtLeast(0)

                        val trimmedText = buildAnnotatedString {
                            // Reuse the original up to the trim point
                            append(fullAnnotatedText, 0, trimEnd.coerceAtMost(fullAnnotatedText.length))
                            append("…")
                            withStyle(
                                SpanStyle(
                                    fontWeight = FontWeight.Normal,
                                    fontSize = 12.sp,
                                    color = secondaryColor,
                                )
                            ) {
                                append(timeSuffix)
                            }
                        }
                        displayText = trimmedText
                        didOverflow = true
                    }
                },
                onClick = { offset ->
                    val userAnnotation = displayText
                        .getStringAnnotations(tag = "USER", start = offset, end = offset)
                        .firstOrNull()
                    if (userAnnotation != null) onUserTap() else onClick()
                },
            )

            if (showCommentActions) {
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .clip(CircleShape)
                            .clickable(onClick = onCommentLike)
                            .padding(vertical = 4.dp, horizontal = 2.dp),
                    ) {
                        Icon(
                            imageVector = if (isCommentLiked) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                            contentDescription = if (isCommentLiked) stringResource(id = R.string.notifications_cd_unlike) else stringResource(id = R.string.notifications_cd_like),
                            modifier = Modifier.size(14.dp),
                            tint = if (isCommentLiked) CorusColors.Like else CorusColors.Tertiary,
                        )
                    }
                    Spacer(modifier = Modifier.width(CorusSpacing.lg))
                    Text(
                        text = stringResource(id = R.string.comments_reply),
                        style = CorusFont.body.copy(
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = CorusColors.Tertiary,
                        ),
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .clickable(onClick = onReplyTap)
                            .padding(vertical = 4.dp, horizontal = 2.dp),
                    )
                }
            }
        }

        // Right: Follow button for follow/contact_joined, album art for others
        if (showFollowButton) {
            Spacer(modifier = Modifier.width(CorusSpacing.sm))
            val buttonText = when {
                isFollowing -> stringResource(id = R.string.search_button_following)
                notification.type == NotificationType.CONTACT_JOINED -> stringResource(id = R.string.search_button_follow)
                else -> stringResource(id = R.string.likes_button_follow_back)
            }
            Button(
                onClick = onFollowToggle,
                shape = RoundedCornerShape(CorusSpacing.pillCornerRadius),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isFollowing) CorusColors.Divider else CorusColors.Accent,
                    contentColor = if (isFollowing) CorusColors.Secondary else Color.White,
                ),
                contentPadding = PaddingValues(horizontal = CorusSpacing.lg, vertical = CorusSpacing.sm),
                modifier = Modifier.height(32.dp),
            ) {
                Text(buttonText, style = CorusFont.buttonSmall)
            }
        } else if (notification.postAlbumArtURL != null) {
            Spacer(modifier = Modifier.width(CorusSpacing.sm))
            AsyncImage(
                model = notification.postAlbumArtURL,
                contentDescription = null,
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(CorusSpacing.cornerRadius)),
                contentScale = ContentScale.Crop,
            )
        }
    }
}

/**
 * Build the row text for a TASTE_MATCH notification — different layout from
 * other types: "<prefix>" (regular) + "username" (bold) + "<suffix>" (regular)
 * + " — " + bodyText + " " + relativeTime.
 */
@androidx.compose.runtime.Composable
@androidx.compose.runtime.ReadOnlyComposable
private fun buildTasteMatchAnnotated(
    notification: CymbalNotification,
    timeString: String,
): androidx.compose.ui.text.AnnotatedString {
    val (prefix, suffix) = when (notification.subtype) {
        "discovery" -> "New Taste Match: " to ""
        "activity_song" -> "You and " to " both shared a song"
        "activity_film" -> "You and " to " both shared a film"
        "milestone_song", "milestone_film", "milestone_artist" ->
            "Taste match milestone with " to ""
        else -> "Taste match with " to ""
    }
    val username = notification.fromUser.username
    val body = notification.bodyText.orEmpty()
    return buildAnnotatedString {
        withStyle(SpanStyle(fontWeight = FontWeight.Normal, fontSize = 15.sp)) {
            append(prefix)
        }
        pushStringAnnotation(tag = "USER", annotation = notification.fromUser.id)
        withStyle(SpanStyle(fontWeight = FontWeight.ExtraBold, fontSize = 14.sp)) {
            append(username)
        }
        pop()
        if (suffix.isNotEmpty()) {
            withStyle(SpanStyle(fontWeight = FontWeight.Normal, fontSize = 15.sp)) {
                append(suffix)
            }
        }
        if (body.isNotEmpty()) {
            withStyle(SpanStyle(fontWeight = FontWeight.Normal, fontSize = 15.sp)) {
                append(" — ")
                append(body)
            }
        }
        append(" ")
        withStyle(SpanStyle(fontWeight = FontWeight.Normal, fontSize = 12.sp, color = CorusColors.Secondary)) {
            append(timeString)
        }
    }
}

@Composable
private fun NotificationsEmptyState() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(
                imageVector = Icons.Outlined.Notifications,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = CorusColors.Tertiary,
            )

            Spacer(modifier = Modifier.height(CorusSpacing.md))

            Text(
                text = stringResource(id = R.string.notifications_empty_title),
                style = CorusFont.bodyMedium,
                color = CorusColors.Secondary,
            )

            Spacer(modifier = Modifier.height(CorusSpacing.xs))

            Text(
                text = stringResource(id = R.string.notifications_empty_subtitle),
                style = CorusFont.body,
                color = CorusColors.Tertiary,
                textAlign = TextAlign.Center,
                modifier = Modifier.widthIn(max = 260.dp),
            )
        }
    }
}

@Composable
private fun InlineReplyBar(
    replyingTo: CymbalNotification,
    isSending: Boolean,
    pendingSong: CommentAttachedSong? = null,
    pendingFilm: CommentAttachedFilm? = null,
    onAttachmentClick: () -> Unit = {},
    onClearAttachment: () -> Unit = {},
    onSend: (String) -> Unit,
    onCancel: () -> Unit,
) {
    var text by rememberSaveable(replyingTo.id) { mutableStateOf("") }
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(replyingTo.id) {
        focusRequester.requestFocus()
    }

    val hasAttachment = pendingSong != null || pendingFilm != null
    val canSend = (text.trim().isNotEmpty() || hasAttachment) && !isSending

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(CorusColors.Background)
            .imePadding(),
    ) {
        HorizontalDivider(color = CorusColors.Divider)

        if (hasAttachment) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = CorusSpacing.lg, vertical = CorusSpacing.xs),
            ) {
                CommentAttachmentPendingChip(
                    attachedSong = pendingSong,
                    attachedFilm = pendingFilm,
                    onClear = onClearAttachment,
                )
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = CorusSpacing.lg, vertical = CorusSpacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(CorusColors.CommentAttachmentPlus.copy(alpha = 0.15f))
                    .clickable(enabled = !hasAttachment) { onAttachmentClick() },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Filled.Add,
                    contentDescription = stringResource(R.string.comment_attachment_attach),
                    tint = CorusColors.CommentAttachmentPlus,
                    modifier = Modifier.size(20.dp),
                )
            }
            Spacer(Modifier.width(CorusSpacing.sm))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(id = R.string.notifications_replying_to_format, replyingTo.fromUser.username),
                    style = CorusFont.body.copy(
                        fontSize = 12.sp,
                        color = CorusColors.Secondary,
                    ),
                )
                Spacer(modifier = Modifier.height(2.dp))
                BasicTextField(
                    value = text,
                    onValueChange = { text = it },
                    textStyle = CorusFont.body.copy(color = CorusColors.Text),
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester),
                    cursorBrush = androidx.compose.ui.graphics.SolidColor(CorusColors.Accent),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = {
                        if (canSend) onSend(text)
                    }),
                    maxLines = 4,
                    decorationBox = { inner ->
                        if (text.isEmpty()) {
                            Text(
                                text = stringResource(id = R.string.notifications_reply_placeholder),
                                style = CorusFont.body.copy(color = CorusColors.Tertiary),
                            )
                        }
                        inner()
                    },
                )
            }
            Spacer(modifier = Modifier.width(CorusSpacing.md))
            Text(
                text = stringResource(id = R.string.common_cancel),
                style = CorusFont.body.copy(
                    fontSize = 14.sp,
                    color = CorusColors.Accent,
                ),
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .clickable(onClick = onCancel)
                    .padding(horizontal = CorusSpacing.sm, vertical = 4.dp),
            )
            Spacer(modifier = Modifier.width(CorusSpacing.sm))
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(if (canSend) CorusColors.Accent else CorusColors.Divider)
                    .clickable(enabled = canSend) { onSend(text) },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Filled.ArrowUpward,
                    contentDescription = stringResource(id = R.string.notifications_cd_send_reply),
                    modifier = Modifier.size(18.dp),
                    tint = if (canSend) Color.White else CorusColors.Tertiary,
                )
            }
        }
    }
}
