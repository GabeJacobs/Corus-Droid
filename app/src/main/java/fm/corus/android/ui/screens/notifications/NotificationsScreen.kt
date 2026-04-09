package fm.corus.android.ui.screens.notifications

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
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
import fm.corus.android.ui.components.SkeletonUserRow
import fm.corus.android.ui.components.UserAvatarView
import fm.corus.android.ui.theme.CorusColors
import fm.corus.android.ui.theme.CorusFont
import fm.corus.android.ui.theme.CorusSpacing
import fm.corus.android.ui.util.DateUtils

@Composable
fun NotificationsScreen(
    viewModel: NotificationsViewModel = hiltViewModel(),
    unreadMessageCount: Int = 0,
    onNavigateToMessages: () -> Unit = {},
    onNavigateToUser: (String) -> Unit = {},
    onNavigateToPost: (String) -> Unit = {},
) {
    val notifications by viewModel.notifications.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val isRefreshing by viewModel.isRefreshing.collectAsState()

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
                            SkeletonUserRow()
                        }
                    }
                }
                notifications.isEmpty() && !isLoading -> {
                    // Empty state
                    NotificationsEmptyState()
                }
                else -> {
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        items(notifications, key = { it.id }) { notification ->
                            NotificationRow(
                                notification = notification,
                                onClick = {
                                    val postId = notification.postId
                                    if (postId != null) {
                                        onNavigateToPost(postId)
                                    } else {
                                        onNavigateToUser(notification.fromUser.id)
                                    }
                                },
                            )
                            HorizontalDivider(
                                color = CorusColors.Divider,
                                modifier = Modifier.padding(
                                    start = CorusSpacing.lg + CorusSpacing.avatarMedium + CorusSpacing.md,
                                ),
                            )
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
                modifier = Modifier.size(14.dp),
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
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = CorusSpacing.lg, vertical = CorusSpacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Left: User avatar (36dp — matches iOS avatarMedium)
        UserAvatarView(
            avatarURL = notification.fromUser.avatarURL,
            size = CorusSpacing.avatarMedium,
        )

        Spacer(modifier = Modifier.width(CorusSpacing.md))

        // Middle: username + message + timestamp — matches iOS inline Text concat
        Text(
            text = buildAnnotatedString {
                withStyle(
                    SpanStyle(
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 14.sp,
                    )
                ) {
                    append(notification.fromUser.username)
                }
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
                    append(DateUtils.relativeTime(notification.timestamp))
                }
            },
            style = CorusFont.body,
            color = CorusColors.Text,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )

        // Right: Post album art thumbnail (44dp square, rounded 6dp — matches iOS)
        if (notification.postAlbumArtURL != null) {
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
