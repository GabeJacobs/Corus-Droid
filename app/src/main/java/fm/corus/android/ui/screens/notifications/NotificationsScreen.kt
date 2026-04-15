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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Email
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
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import coil3.compose.AsyncImage
import fm.corus.android.data.model.CymbalNotification
import fm.corus.android.data.model.NotificationType
import fm.corus.android.ui.components.SkeletonNotificationRow
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
    val listState = rememberLazyListState()

    var lastScrollTrigger by rememberSaveable { mutableIntStateOf(0) }
    LaunchedEffect(scrollToTopTrigger) {
        if (scrollToTopTrigger > lastScrollTrigger) {
            listState.animateScrollToItem(0)
            lastScrollTrigger = scrollToTopTrigger
        }
    }

    LaunchedEffect(Unit) {
        viewModel.loadNotifications()
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Header
        NotificationsHeader(
            unreadMessageCount = unreadMessageCount,
            onMessagesTapped = onNavigateToMessages,
        )

        HorizontalDivider(color = CorusColors.Divider)

        @OptIn(ExperimentalMaterial3Api::class)
        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = { viewModel.refreshNotifications() },
            modifier = Modifier.fillMaxSize(),
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
                    LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                        items(notifications, key = { it.id }) { notification ->
                            NotificationRow(
                                notification = notification,
                                isFollowing = followingIds.contains(notification.fromUser.id),
                                onClick = {
                                    val postId = notification.postId
                                    if (notification.supportsCommentActions && postId != null && notification.commentId != null) {
                                        onNavigateToPostComments(postId, notification.commentId)
                                    } else if (postId != null) {
                                        onNavigateToPost(postId)
                                    } else {
                                        onNavigateToUser(notification.fromUser.id)
                                    }
                                },
                                onUserTap = {
                                    onNavigateToUser(notification.fromUser.id)
                                },
                                onFollowToggle = {
                                    viewModel.toggleFollow(notification.fromUser.id)
                                },
                            )
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
                                LaunchedEffect(Unit) {
                                    viewModel.loadMoreNotifications()
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
            text = "Activity",
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
                contentDescription = "Messages",
                modifier = Modifier.size(24.dp),
                tint = CorusColors.Secondary,
            )

            // Unread badge
            if (unreadMessageCount > 0) {
                val badgeText = if (unreadMessageCount > 99) "99+" else "$unreadMessageCount"
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .size(16.dp)
                        .border(2.dp, Color.White, CircleShape)
                        .background(Color.Red, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = badgeText,
                        color = Color.White,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
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
    onClick: () -> Unit,
    onUserTap: () -> Unit,
    onFollowToggle: () -> Unit,
) {
    val showFollowButton = notification.type == NotificationType.FOLLOW ||
            notification.type == NotificationType.CONTACT_JOINED

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = CorusSpacing.lg, vertical = CorusSpacing.md),
        verticalAlignment = Alignment.CenterVertically,
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
        val timeString = DateUtils.relativeTime(notification.timestamp)
        val timeSuffix = " $timeString"

        val fullAnnotatedText = buildAnnotatedString {
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

        ClickableText(
            text = displayText,
            style = CorusFont.body.copy(color = CorusColors.Text),
            maxLines = maxLines,
            overflow = if (didOverflow) TextOverflow.Clip else TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
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
                                color = CorusColors.Secondary,
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
                displayText.getStringAnnotations(tag = "USER", start = offset, end = offset)
                    .firstOrNull()?.let { onUserTap() }
            },
        )

        // Right: Follow button for follow/contact_joined, album art for others
        if (showFollowButton) {
            Spacer(modifier = Modifier.width(CorusSpacing.sm))
            val buttonText = when {
                isFollowing -> "Following"
                notification.type == NotificationType.CONTACT_JOINED -> "Follow"
                else -> "Follow back"
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
                text = "No activity yet",
                style = CorusFont.bodyMedium,
                color = CorusColors.Secondary,
            )

            Spacer(modifier = Modifier.height(CorusSpacing.xs))

            Text(
                text = "When people like, comment, or save your cymbals, you\u2019ll see it here",
                style = CorusFont.body,
                color = CorusColors.Tertiary,
                textAlign = TextAlign.Center,
                modifier = Modifier.widthIn(max = 260.dp),
            )
        }
    }
}
