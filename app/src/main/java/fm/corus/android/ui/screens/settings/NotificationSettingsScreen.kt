package fm.corus.android.ui.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AddReaction
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.HowToReg
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PersonSearch
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.PersonAdd
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import fm.corus.android.R
import fm.corus.android.ui.components.CorusHeaderIconButton
import fm.corus.android.ui.theme.CorusColors
import fm.corus.android.ui.theme.CorusFont
import fm.corus.android.ui.theme.CorusSpacing

@Composable
fun NotificationSettingsScreen(
    onBack: () -> Unit = {},
    viewModel: NotificationSettingsViewModel = hiltViewModel(),
) {
    val settings by viewModel.settings.collectAsState()

    LaunchedEffect(Unit) { viewModel.load() }

    Column(modifier = Modifier.fillMaxSize().background(CorusColors.Background)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = CorusSpacing.sm, vertical = CorusSpacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CorusHeaderIconButton(
                onClick = onBack,
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = stringResource(R.string.common_back),
            )
            Text(stringResource(R.string.notifications_screen_title), style = CorusFont.screenTitle, color = CorusColors.Text)
        }

        HorizontalDivider(color = CorusColors.Divider)

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState()),
        ) {
            NotifToggleRow(
                icon = Icons.Filled.Favorite,
                title = stringResource(R.string.notifications_row_likes),
                checked = settings.likes,
                onCheckedChange = viewModel::setLikes,
            )

            NotifToggleRow(
                icon = Icons.Outlined.ChatBubbleOutline,
                title = stringResource(R.string.notifications_row_comments_replies),
                checked = settings.commentsAndReplies,
                onCheckedChange = viewModel::setCommentsAndReplies,
            )

            NotifToggleRow(
                icon = Icons.Outlined.PersonAdd,
                title = stringResource(R.string.notifications_row_new_followers),
                checked = settings.newFollowers,
                onCheckedChange = viewModel::setNewFollowers,
            )

            NotifToggleRow(
                icon = Icons.Filled.PersonSearch,
                title = stringResource(R.string.notifications_row_follow_requests),
                checked = settings.followRequests,
                onCheckedChange = viewModel::setFollowRequests,
            )

            NotifToggleRow(
                icon = Icons.Filled.HowToReg,
                title = stringResource(R.string.notifications_row_contact_joined),
                checked = settings.contactJoined,
                onCheckedChange = viewModel::setContactJoined,
            )

            NotifToggleRow(
                icon = Icons.Filled.AutoAwesome,
                title = "Taste Matches",
                checked = settings.tasteMatches,
                onCheckedChange = viewModel::setTasteMatches,
            )

            // Gated behind the same flag as the backend play-milestone feature,
            // so the row only appears once play notifications are launched.
            if (viewModel.playMilestoneEnabled) {
                NotifToggleRow(
                    icon = Icons.Filled.PlayArrow,
                    title = stringResource(R.string.notifications_row_plays),
                    checked = settings.plays,
                    onCheckedChange = viewModel::setPlays,
                )
            }

            NotifToggleRow(
                icon = Icons.AutoMirrored.Filled.TrendingUp,
                title = stringResource(R.string.notifications_row_trending),
                checked = settings.trending,
                onCheckedChange = viewModel::setTrending,
            )

            NotifToggleRow(
                icon = Icons.Filled.Notifications,
                title = stringResource(R.string.notifications_row_messages),
                checked = settings.messagePush,
                onCheckedChange = viewModel::setMessagePush,
            )

            NotifToggleRow(
                icon = Icons.Filled.AddReaction,
                title = stringResource(R.string.notifications_row_reactions),
                checked = settings.reactions,
                onCheckedChange = viewModel::setReactions,
            )

            NotifToggleRow(
                icon = Icons.Filled.DoneAll,
                title = stringResource(R.string.notifications_row_read_receipts),
                checked = settings.readReceipts,
                onCheckedChange = viewModel::setReadReceipts,
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
