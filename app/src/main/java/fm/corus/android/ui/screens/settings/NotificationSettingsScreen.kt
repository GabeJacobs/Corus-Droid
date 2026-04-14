package fm.corus.android.ui.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.HowToReg
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PersonSearch
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.PersonAdd
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import fm.corus.android.ui.theme.CorusColors
import fm.corus.android.ui.theme.CorusFont
import fm.corus.android.ui.theme.CorusSpacing

@Composable
fun NotificationSettingsScreen(
    onBack: () -> Unit = {},
) {
    // Notification toggles
    var notifyLikes by remember { mutableStateOf(true) }
    var notifyComments by remember { mutableStateOf(true) }
    var notifyNewFollowers by remember { mutableStateOf(true) }
    var notifyFollowRequests by remember { mutableStateOf(true) }
    var notifyContactJoined by remember { mutableStateOf(true) }
    var messagePushNotifications by remember { mutableStateOf(true) }

    Column(modifier = Modifier.fillMaxSize().background(CorusColors.Background)) {
        // ── Header ──
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = CorusSpacing.sm, vertical = CorusSpacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
            Text("Notifications", style = CorusFont.screenTitle, color = CorusColors.Text)
        }

        HorizontalDivider(color = CorusColors.Divider)

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState()),
        ) {
            NotifToggleRow(
                icon = Icons.Filled.Favorite,
                title = "Likes",
                checked = notifyLikes,
                onCheckedChange = { notifyLikes = it },
            )

            NotifToggleRow(
                icon = Icons.Outlined.ChatBubbleOutline,
                title = "Comments & Replies",
                checked = notifyComments,
                onCheckedChange = { notifyComments = it },
            )

            NotifToggleRow(
                icon = Icons.Outlined.PersonAdd,
                title = "New Followers",
                checked = notifyNewFollowers,
                onCheckedChange = { notifyNewFollowers = it },
            )

            NotifToggleRow(
                icon = Icons.Filled.PersonSearch,
                title = "Follow Requests",
                checked = notifyFollowRequests,
                onCheckedChange = { notifyFollowRequests = it },
            )

            NotifToggleRow(
                icon = Icons.Filled.HowToReg,
                title = "Contact Joined",
                checked = notifyContactJoined,
                onCheckedChange = { notifyContactJoined = it },
            )

            NotifToggleRow(
                icon = Icons.Filled.Notifications,
                title = "Messages",
                checked = messagePushNotifications,
                onCheckedChange = { messagePushNotifications = it },
            )
        }
    }
}

@Composable
private fun NotifToggleRow(
    icon: ImageVector,
    title: String,
    subtitle: String? = null,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(horizontal = CorusSpacing.lg, vertical = CorusSpacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = CorusColors.Secondary,
            modifier = Modifier.size(20.dp),
        )

        Spacer(modifier = Modifier.width(CorusSpacing.md))

        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = CorusFont.body, color = CorusColors.Text)
            if (subtitle != null) {
                Text(text = subtitle, style = CorusFont.caption, color = CorusColors.Secondary)
            }
        }

        Spacer(modifier = Modifier.width(CorusSpacing.sm))

        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = CorusColors.Background,
                checkedTrackColor = CorusColors.Accent,
                uncheckedThumbColor = CorusColors.Background,
                uncheckedTrackColor = CorusColors.Tertiary,
                uncheckedBorderColor = CorusColors.Tertiary,
            ),
        )
    }
    HorizontalDivider(
        color = CorusColors.Divider,
        modifier = Modifier.padding(horizontal = CorusSpacing.lg),
    )
}
