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
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
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
import fm.corus.android.ui.components.FrostedHeaderOverlay
import fm.corus.android.ui.components.rememberImmersiveHeaderState
import fm.corus.android.ui.components.OfflineRetryState
import fm.corus.android.ui.components.PickerMode
import fm.corus.android.ui.components.SkeletonNotificationRow
import fm.corus.android.ui.components.SongFilmPickerSheet
import fm.corus.android.ui.components.GifPickerSheet
import fm.corus.android.ui.components.UserAvatarView
import fm.corus.android.ui.theme.CorusColors
import fm.corus.android.ui.theme.CorusFont
import fm.corus.android.ui.theme.CorusSpacing
import fm.corus.android.ui.util.DateUtils

@Composable
fun NotificationsScreen(
    viewModel: NotificationsViewModel = hiltViewModel(),
    scrollToTopTrigger: Int = 0,
    tabActivationTrigger: Int = 0,
    unreadMessageCount: Int = 0,
    onNavigateToMessages: () -> Unit = {},
    onNavigateToUser: (String) -> Unit = {},
    onNavigateToPost: (String) -> Unit = {},
    onNavigateToPostComments: (postId: String, commentId: String) -> Unit = { _, _ -> },
) {
    val notifications by viewModel.notifications.collectAsState()
    var showFavoriteInfo by remember { mutableStateOf(false) }
    val isLoading by viewModel.isLoading.collectAsState()
    val isRefreshing by viewModel.isRefreshing.collectAsState()
    val hasLoadError by viewModel.hasLoadError.collectAsState()
    val hasMoreNotifications by viewModel.hasMoreNotifications.collectAsState()
    val followingIds by viewModel.followingIds.collectAsState()
    val followsMeIds by viewModel.followsMeIds.collectAsState()
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

    if (showFavoriteInfo) {
        FavoriteInfoDialog(onDismiss = { showFavoriteInfo = false })
    }

    LaunchedEffect(Unit) {
        viewModel.loadNotifications()
    }

    // Mark the feed as viewed every time the user actually navigates to the
    // Activity tab. The trigger is bumped by MainTabScreen on each tab
    // selection; the initial value is 0 so this does NOT fire at app launch
    // (when all tab screens first compose) — only on real visits. This is what
    // makes the badge clear for real on each visit, instead of only at cold
    // start. Mirrors iOS reacting to `.notificationsTabBecameActive`.
    LaunchedEffect(tabActivationTrigger) {
        if (tabActivationTrigger > 0) {
            viewModel.markActivityViewed()
            // Clear any outstanding system-tray notifications so the launcher
            // icon dot clears too — matches iOS MainTabView clearing the OS badge.
            try { NotificationManagerCompat.from(context).cancelAll() } catch (_: Exception) { }
        }
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

    val immersive = viewModel.immersiveArtistHeaderEnabled
    val frost = rememberImmersiveHeaderState(immersive)
    // The header is now a frosted overlay (below) that the list scrolls under, so the
    // whole thing is a Box, not a Column. Its measured height sizes the list's top clearance.
    Box(modifier = Modifier.fillMaxSize().then(frost.scaffoldModifier)) {
        var headerOverlayPx by remember { mutableIntStateOf(0) }
        val contentTop = if (immersive) with(LocalDensity.current) { headerOverlayPx.toDp() } else 0.dp

        val haptics = LocalHapticManager.current
        @OptIn(ExperimentalMaterial3Api::class)
        val pullState = rememberPullToRefreshState()
        @OptIn(ExperimentalMaterial3Api::class)
        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = {
                // Mirrors iOS NotificationsView.refreshable haptic.
                haptics.impact(HapticManager.ImpactStyle.LIGHT)
                viewModel.refreshNotifications()
            },
            state = pullState,
            modifier = Modifier.fillMaxSize().then(frost.hazeSourceModifier()),
            indicator = {
                PullToRefreshDefaults.Indicator(
                    state = pullState,
                    isRefreshing = isRefreshing,
                    modifier = Modifier.align(Alignment.TopCenter).padding(top = contentTop),
                )
            },
        ) {
            when {
                isLoading && notifications.isEmpty() -> {
                    // Loading skeleton — 12 shimmer rows
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(top = contentTop),
                    ) {
                        items(12) {
                            SkeletonNotificationRow()
                        }
                    }
                }
                notifications.isEmpty() && !isLoading && hasLoadError -> {
                    OfflineRetryState(onRetry = { viewModel.retryLoad() })
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
                        contentPadding = PaddingValues(top = contentTop),
                    ) {
                        items(notifications, key = { it.id }) { notification ->
                            NotificationRow(
                                notification = notification,
                                isFollowing = followingIds.contains(notification.fromUser.id),
                                followsMe = notification.type == NotificationType.FOLLOW ||
                                        followsMeIds.contains(notification.fromUser.id),
                                isCommentLiked = notification.commentId != null &&
                                        likedCommentIds.contains(notification.commentId),
                                isNew = newNotificationIds.contains(notification.id),
                                onClick = {
                                    viewModel.markNotificationTapped(notification.id)
                                    if (notification.type == NotificationType.FAVORITE) {
                                        showFavoriteInfo = true
                                    } else if (notification.type == NotificationType.TASTE_MATCH) {
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
            val replyPendingGif by viewModel.replyPendingGif.collectAsState()
            var showReplySongFilmPicker by remember { mutableStateOf(false) }
            var replyPickerInitialMode by remember { mutableStateOf(PickerMode.SONG) }
            var showReplyAttachmentMenu by remember { mutableStateOf(false) }
            var showReplyGifPicker by remember { mutableStateOf(false) }

            if (showReplySongFilmPicker) {
                SongFilmPickerSheet(
                    initialMode = replyPickerInitialMode,
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

            if (viewModel.gifSupport && showReplyGifPicker) {
                GifPickerSheet(
                    onGifSelected = { gif ->
                        showReplyGifPicker = false
                        viewModel.attachReplyGif(gif)
                    },
                    onDismiss = { showReplyGifPicker = false },
                )
            }

            InlineReplyBar(
                replyingTo = replyingTo!!,
                isSending = isSendingReply,
                pendingSong = replyPendingSong,
                pendingFilm = replyPendingFilm,
                pendingGif = replyPendingGif,
                nowPlaying = viewModel.nowPlayingManager,
                gifSupport = viewModel.gifSupport,
                showAttachmentMenu = showReplyAttachmentMenu,
                onAttachmentClick = {
                    if (viewModel.gifSupport) {
                        showReplyAttachmentMenu = true
                    } else {
                        replyPickerInitialMode = PickerMode.SONG
                        showReplySongFilmPicker = true
                    }
                },
                onAttachmentMenuDismiss = { showReplyAttachmentMenu = false },
                onAttachSong = {
                    showReplyAttachmentMenu = false
                    replyPickerInitialMode = PickerMode.SONG
                    showReplySongFilmPicker = true
                },
                onAttachFilm = {
                    showReplyAttachmentMenu = false
                    replyPickerInitialMode = PickerMode.FILM
                    showReplySongFilmPicker = true
                },
                onAttachGif = {
                    showReplyAttachmentMenu = false
                    showReplyGifPicker = true
                },
                onClearAttachment = { viewModel.clearReplyAttachment() },
                onSend = { text -> viewModel.sendReply(text) },
                onCancel = {
                    viewModel.setReplyingToNotification(null)
                    viewModel.clearReplyAttachment()
                },
            )
        }

        // Frosted header overlay (status strip + Activity header) — the list scrolls
        // under it; its measured height sizes the list's top clearance above.
        FrostedHeaderOverlay(
            immersive = immersive,
            hazeState = frost.hazeState,
            topInset = frost.statusBarPadding,
            modifier = Modifier.onGloballyPositioned { headerOverlayPx = it.size.height },
        ) {
            NotificationsHeader(
                unreadMessageCount = unreadMessageCount,
                onMessagesTapped = onNavigateToMessages,
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
    followsMe: Boolean,
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
        // Left: avatar (taps to profile) — or, for anonymous favorites, a star,
        // or, for play milestones, a headphones glyph.
        if (notification.type == NotificationType.FAVORITE) {
            Box(
                modifier = Modifier
                    .size(CorusSpacing.avatarMedium)
                    .clip(CircleShape)
                    .background(CorusColors.Accent.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Filled.Star,
                    contentDescription = null,
                    tint = CorusColors.Accent,
                    modifier = Modifier.size(20.dp),
                )
            }
        } else if (notification.type == NotificationType.PLAY_MILESTONE) {
            Box(
                modifier = Modifier
                    .size(CorusSpacing.avatarMedium)
                    .clip(CircleShape)
                    .background(CorusColors.Accent.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Filled.Headphones,
                    contentDescription = null,
                    tint = CorusColors.Accent,
                    modifier = Modifier.size(20.dp),
                )
            }
        } else {
            Box(modifier = Modifier.clickable(onClick = onUserTap)) {
                UserAvatarView(
                    avatarURL = notification.fromUser.avatarURL,
                    avatarThumbURL = notification.fromUser.avatarThumbURL,
                    displayName = notification.fromUser.displayName,
                    size = CorusSpacing.avatarMedium,
                )
            }
        }

        Spacer(modifier = Modifier.width(CorusSpacing.md))

        // Middle: username + message + timestamp — matches iOS inline Text concat
        // Username portion is tappable to user profile via ClickableText
        // For notifications with comment text (COMMENT, MENTION, REPLY), we allow
        // 3 lines for the message and ensure the timestamp is always visible even
        // when truncated, matching the iOS .truncationMode(.middle) behavior.
        val hasCommentText = notification.commentText != null
        val maxLines = if (hasCommentText) 4 else 2
        val context = LocalContext.current
        val timeString = DateUtils.relativeTime(context, notification.timestamp)
        val timeSuffix = " $timeString"

        val fullAnnotatedText = if (notification.type == NotificationType.FAVORITE) {
            // Anonymous — "Someone" is NOT bolded here (special case): there's no
            // real actor to name, so it reads as normal prose, not a username.
            buildAnnotatedString {
                withStyle(SpanStyle(fontWeight = FontWeight.Normal, fontSize = 15.sp)) {
                    append(stringResource(id = R.string.notif_favorite_someone))
                }
                append(" ")
                withStyle(SpanStyle(fontWeight = FontWeight.Normal, fontSize = 15.sp)) {
                    append(localizedNotificationMessage(notification, context))
                }
                append(" ")
                withStyle(SpanStyle(fontWeight = FontWeight.Normal, fontSize = 12.sp, color = CorusColors.Secondary)) {
                    append(timeString)
                }
            }
        } else if (notification.type == NotificationType.PLAY_MILESTONE) {
            // Anonymous aggregate — the whole "{n} people played your corus."
            // sentence is one localized, count-formatted string (no actor name).
            buildAnnotatedString {
                withStyle(SpanStyle(fontWeight = FontWeight.Normal, fontSize = 15.sp)) {
                    append(localizedNotificationMessage(notification, context))
                }
                append(" ")
                withStyle(SpanStyle(fontWeight = FontWeight.Normal, fontSize = 12.sp, color = CorusColors.Secondary)) {
                    append(timeString)
                }
            }
        } else if (notification.type == NotificationType.TASTE_MATCH) {
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
                append(localizedNotificationMessage(notification, context))
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
                followsMe -> stringResource(id = R.string.likes_button_follow_back)
                else -> stringResource(id = R.string.search_button_follow)
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
    // Activity types render as one flowing sentence; discovery + milestones
    // use an em-dash because their body is its own complete clause.
    val (prefix, suffix, bodySeparator) = when (notification.subtype) {
        "discovery" -> Triple("New Taste Match: ", "", " — ")
        "activity_song" -> Triple("You and ", " both shared the song ", "")
        "activity_film" -> Triple("You and ", " both shared the film ", "")
        "milestone_song", "milestone_film", "milestone_artist" ->
            Triple("Taste match milestone with ", "", " — ")
        else -> Triple("Taste match with ", "", " — ")
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
                append(bodySeparator)
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
    pendingGif: fm.corus.android.data.model.KlipyGif? = null,
    nowPlaying: fm.corus.android.domain.NowPlayingManager? = null,
    gifSupport: Boolean = false,
    showAttachmentMenu: Boolean = false,
    onAttachmentClick: () -> Unit = {},
    onAttachmentMenuDismiss: () -> Unit = {},
    onAttachSong: () -> Unit = {},
    onAttachFilm: () -> Unit = {},
    onAttachGif: () -> Unit = {},
    onClearAttachment: () -> Unit = {},
    onSend: (String) -> Unit,
    onCancel: () -> Unit,
) {
    var text by rememberSaveable(replyingTo.id) { mutableStateOf("") }
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(replyingTo.id) {
        focusRequester.requestFocus()
    }

    val hasAttachment = pendingSong != null || pendingFilm != null || pendingGif != null
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
                    pendingGif = pendingGif,
                    onClear = onClearAttachment,
                    nowPlaying = nowPlaying,
                )
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = CorusSpacing.lg, vertical = CorusSpacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box {
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
                if (gifSupport) {
                    DropdownMenu(
                        expanded = showAttachmentMenu,
                        onDismissRequest = onAttachmentMenuDismiss,
                    ) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.comment_attachment_gif)) },
                            onClick = onAttachGif,
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.comment_attachment_song)) },
                            onClick = onAttachSong,
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.comment_attachment_film)) },
                            onClick = onAttachFilm,
                        )
                    }
                }
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
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Send),
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

/**
 * Build the user-facing notification message client-side from the notification type,
 * mirroring iOS CymbalNotification.localizedMessage. The server-side `notification.message`
 * field is English-only fallback content; we ignore it so the message respects the
 * user's selected app language.
 *
 * Comment-text excerpts are server-supplied user content, so we render them verbatim.
 */
private fun localizedNotificationMessage(
    notification: CymbalNotification,
    context: android.content.Context,
): String {
    val postNoun = context.getString(R.string.post_noun)
    val appName = context.getString(R.string.app_name)
    // Mirror iOS: when commentText is exactly 100 chars and not already truncated, append ellipsis.
    val commentExcerpt = notification.commentText?.let {
        if (it.length == 100 && !it.endsWith("…")) "$it…" else it
    }
    return when (notification.type) {
        NotificationType.LIKE -> context.getString(R.string.notif_msg_like, postNoun)
        NotificationType.COMMENT -> commentExcerpt
            ?.let { context.getString(R.string.notif_msg_comment_with_text, it) }
            ?: context.getString(R.string.notif_msg_comment_no_text, postNoun)
        NotificationType.COMMENT_LIKE -> context.getString(R.string.notif_msg_comment_like)
        NotificationType.MENTION -> commentExcerpt
            ?.let { context.getString(R.string.notif_msg_mention_with_text, it) }
            ?: context.getString(R.string.notif_msg_mention_no_text)
        NotificationType.TAG -> context.getString(R.string.notif_msg_tag, postNoun)
        NotificationType.SAVE -> context.getString(R.string.notif_msg_save, postNoun)
        NotificationType.FOLLOW -> context.getString(R.string.notif_msg_follow)
        NotificationType.NEW_POST -> context.getString(R.string.notif_msg_new_post, postNoun)
        NotificationType.REPOST -> context.getString(R.string.notif_msg_repost, postNoun)
        NotificationType.REPLY -> commentExcerpt
            ?.let { context.getString(R.string.notif_msg_reply_with_text, it) }
            ?: context.getString(R.string.notif_msg_reply_no_text)
        NotificationType.CONTACT_JOINED -> context.getString(R.string.notif_msg_contact_joined, appName)
        NotificationType.TASTE_MATCH -> notification.bodyText
            ?: context.getString(R.string.notif_msg_taste_match_default)
        NotificationType.FAVORITE -> context.getString(R.string.notif_msg_favorite)
        NotificationType.PLAY_MILESTONE -> context.getString(R.string.notif_msg_play_milestone, notification.playCount ?: 0)
    }
}

/**
 * Explainer shown when an anonymous "Someone added you to their favorites" row
 * is tapped. Reinforces that favorites are private and teaches the reciprocal
 * action. Mirrors the iOS FavoriteInfoSheet.
 */
@Composable
private fun FavoriteInfoDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(CorusColors.Accent.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Filled.Star,
                    contentDescription = null,
                    tint = CorusColors.Accent,
                    modifier = Modifier.size(30.dp),
                )
            }
        },
        title = {
            Text(
                text = stringResource(id = R.string.notif_favorite_sheet_title),
                style = CorusFont.displayName,
                color = CorusColors.Text,
            )
        },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = stringResource(id = R.string.notif_favorite_sheet_body),
                    style = CorusFont.body,
                    color = CorusColors.Secondary,
                    textAlign = TextAlign.Center,
                )
                Spacer(modifier = Modifier.height(CorusSpacing.md))
                Text(
                    text = stringResource(id = R.string.notif_favorite_sheet_nudge),
                    style = CorusFont.body.copy(fontSize = 12.sp),
                    color = CorusColors.Secondary,
                    textAlign = TextAlign.Center,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(
                    text = stringResource(id = R.string.notif_favorite_sheet_dismiss),
                    color = CorusColors.Accent,
                )
            }
        },
        containerColor = CorusColors.CardBackground,
    )
}
