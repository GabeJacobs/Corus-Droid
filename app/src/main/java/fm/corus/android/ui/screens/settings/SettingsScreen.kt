package fm.corus.android.ui.screens.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.HowToReg
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PersonSearch
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Feedback
import androidx.compose.material.icons.outlined.Group
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.PersonAdd
import androidx.compose.material.icons.outlined.Policy
import androidx.compose.material.icons.outlined.Vibration
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import fm.corus.android.ui.screens.auth.AuthViewModel
import fm.corus.android.ui.theme.CorusColors
import fm.corus.android.ui.theme.CorusFont
import fm.corus.android.ui.theme.CorusSpacing

@Composable
fun SettingsScreen(
    onBack: () -> Unit = {},
    onAppearance: () -> Unit = {},
    onEditProfile: () -> Unit = {},
    onChangeUsername: () -> Unit = {},
    onChangePhoneNumber: () -> Unit = {},
    onBlockedUsers: () -> Unit = {},
    onMutedUsers: () -> Unit = {},
    onSendFeedback: () -> Unit = {},
    authViewModel: AuthViewModel = hiltViewModel(),
) {
    var showDeleteConfirm by remember { mutableStateOf(false) }

    // General toggles
    var onePerFollower by remember { mutableStateOf(false) }
    var hapticsEnabled by remember { mutableStateOf(true) }

    // Notification toggles
    var notifyLikes by remember { mutableStateOf(true) }
    var notifyComments by remember { mutableStateOf(true) }
    var notifyNewFollowers by remember { mutableStateOf(true) }
    var notifyFollowRequests by remember { mutableStateOf(true) }
    var notifyContactJoined by remember { mutableStateOf(true) }

    // Messaging
    var messagePushNotifications by remember { mutableStateOf(true) }
    var whoCanMessageMe by remember { mutableStateOf("Everyone") }
    var showMessageMenu by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize()) {
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
            Text("Settings", style = CorusFont.screenTitle, color = CorusColors.Text)
        }

        HorizontalDivider(color = CorusColors.Divider)

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState()),
        ) {
            // ── Section: General ──
            SectionHeader("GENERAL")

            SettingsToggleRow(
                icon = Icons.Filled.FilterList,
                title = "One Per Follower",
                subtitle = "Show only each person's latest corus",
                checked = onePerFollower,
                onCheckedChange = { onePerFollower = it },
            )

            SettingsToggleRow(
                icon = Icons.Outlined.Vibration,
                title = "Haptics",
                subtitle = "Vibration feedback on interactions",
                checked = hapticsEnabled,
                onCheckedChange = { hapticsEnabled = it },
            )

            SettingsNavRow(
                icon = Icons.Filled.Palette,
                title = "Appearance",
                onClick = onAppearance,
            )

            // ── Section: Notifications ──
            SectionHeader("NOTIFICATIONS")

            SettingsToggleRow(
                icon = Icons.Filled.Favorite,
                title = "Likes",
                checked = notifyLikes,
                onCheckedChange = { notifyLikes = it },
            )

            SettingsToggleRow(
                icon = Icons.Outlined.ChatBubbleOutline,
                title = "Comments & Replies",
                checked = notifyComments,
                onCheckedChange = { notifyComments = it },
            )

            SettingsToggleRow(
                icon = Icons.Outlined.PersonAdd,
                title = "New Followers",
                checked = notifyNewFollowers,
                onCheckedChange = { notifyNewFollowers = it },
            )

            SettingsToggleRow(
                icon = Icons.Filled.PersonSearch,
                title = "Follow Requests",
                checked = notifyFollowRequests,
                onCheckedChange = { notifyFollowRequests = it },
            )

            SettingsToggleRow(
                icon = Icons.Filled.HowToReg,
                title = "Contact Joined",
                checked = notifyContactJoined,
                onCheckedChange = { notifyContactJoined = it },
            )

            // ── Section: Messaging ──
            SectionHeader("MESSAGING")

            SettingsToggleRow(
                icon = Icons.Filled.Notifications,
                title = "Message Push Notifications",
                checked = messagePushNotifications,
                onCheckedChange = { messagePushNotifications = it },
            )

            // "Who Can Message Me" menu row
            Box {
                SettingsNavRow(
                    icon = Icons.Outlined.Group,
                    title = "Who Can Message Me",
                    trailingText = whoCanMessageMe,
                    onClick = { showMessageMenu = true },
                )
                DropdownMenu(
                    expanded = showMessageMenu,
                    onDismissRequest = { showMessageMenu = false },
                ) {
                    listOf("Everyone", "Followers", "No One").forEach { option ->
                        DropdownMenuItem(
                            text = {
                                Text(
                                    text = option,
                                    style = CorusFont.body,
                                    color = if (option == whoCanMessageMe) CorusColors.Accent else CorusColors.Text,
                                )
                            },
                            onClick = {
                                whoCanMessageMe = option
                                showMessageMenu = false
                            },
                        )
                    }
                }
            }

            // ── Section: Account ──
            SectionHeader("ACCOUNT")

            SettingsNavRow(
                icon = Icons.Filled.Edit,
                title = "Edit Profile",
                onClick = onEditProfile,
            )

            SettingsNavRow(
                icon = Icons.Filled.Person,
                title = "Change Username",
                onClick = onChangeUsername,
            )

            SettingsNavRow(
                icon = Icons.Filled.Phone,
                title = "Phone Number",
                onClick = onChangePhoneNumber,
            )

            SettingsNavRow(
                icon = Icons.Filled.Block,
                title = "Blocked Users",
                onClick = onBlockedUsers,
            )

            SettingsNavRow(
                icon = Icons.Filled.VolumeOff,
                title = "Muted Users",
                onClick = onMutedUsers,
            )

            // ── Section: Support ──
            SectionHeader("SUPPORT")

            val context = LocalContext.current

            SettingsNavRow(
                icon = Icons.Filled.Share,
                title = "Share App",
                onClick = {
                    val sendIntent = android.content.Intent().apply {
                        action = android.content.Intent.ACTION_SEND
                        putExtra(android.content.Intent.EXTRA_TEXT, "Check out Corus — share your music & movie taste! https://corus.fm")
                        type = "text/plain"
                    }
                    context.startActivity(android.content.Intent.createChooser(sendIntent, "Share Corus"))
                },
            )

            SettingsNavRow(
                icon = Icons.Outlined.Feedback,
                title = "Send Feedback",
                onClick = {
                    val intent = android.content.Intent(android.content.Intent.ACTION_SENDTO).apply {
                        data = android.net.Uri.parse("mailto:help@corus.fm?subject=Feedback")
                    }
                    try { context.startActivity(intent) } catch (_: Exception) { }
                },
            )

            SettingsNavRow(
                icon = Icons.AutoMirrored.Outlined.HelpOutline,
                title = "Contact Us",
                onClick = {
                    val intent = android.content.Intent(android.content.Intent.ACTION_SENDTO).apply {
                        data = android.net.Uri.parse("mailto:help@corus.fm?subject=Support%20Request")
                    }
                    try { context.startActivity(intent) } catch (_: Exception) { }
                },
            )

            SettingsNavRow(
                icon = Icons.Filled.Star,
                title = "Rate App",
                onClick = {
                    try {
                        val intent = android.content.Intent(
                            android.content.Intent.ACTION_VIEW,
                            android.net.Uri.parse("market://details?id=fm.corus.android")
                        )
                        context.startActivity(intent)
                    } catch (_: Exception) {
                        val intent = android.content.Intent(
                            android.content.Intent.ACTION_VIEW,
                            android.net.Uri.parse("https://play.google.com/store/apps/details?id=fm.corus.android")
                        )
                        context.startActivity(intent)
                    }
                },
            )

            SettingsNavRow(
                icon = Icons.Outlined.Policy,
                title = "Privacy Policy",
                onClick = {
                    context.startActivity(
                        android.content.Intent(
                            android.content.Intent.ACTION_VIEW,
                            android.net.Uri.parse("https://corus.fm/privacy")
                        )
                    )
                },
            )

            SettingsNavRow(
                icon = Icons.Outlined.Info,
                title = "Terms of Service",
                onClick = {
                    context.startActivity(
                        android.content.Intent(
                            android.content.Intent.ACTION_VIEW,
                            android.net.Uri.parse("https://corus.fm/terms")
                        )
                    )
                },
            )

            // ── Bottom actions ──
            Spacer(modifier = Modifier.height(CorusSpacing.xxl))

            TextButton(
                onClick = { authViewModel.signOut() },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = CorusSpacing.lg),
            ) {
                Text("Sign Out", style = CorusFont.button, color = CorusColors.Text)
            }

            TextButton(
                onClick = { showDeleteConfirm = true },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = CorusSpacing.lg),
            ) {
                Text(
                    "Delete Account",
                    style = CorusFont.button,
                    color = CorusColors.Error,
                )
            }

            Spacer(modifier = Modifier.height(CorusSpacing.lg))

            Text(
                text = "Corus v1.0.0",
                style = CorusFont.caption,
                color = CorusColors.Tertiary,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = CorusSpacing.xxl),
            )
        }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete Account?", style = CorusFont.songTitleLarge) },
            text = {
                Text(
                    "This will permanently delete your account, all your coruses, likes, comments, and followers. This action cannot be undone.",
                    style = CorusFont.body,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    // authViewModel.deleteAccount()
                    showDeleteConfirm = false
                }) {
                    Text("Delete", color = CorusColors.Error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text("Cancel")
                }
            },
        )
    }
}

// ── Section Header ──

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = CorusFont.sectionHeader,
        color = CorusColors.Secondary,
        modifier = Modifier.padding(
            start = CorusSpacing.lg,
            end = CorusSpacing.lg,
            top = CorusSpacing.xxl,
            bottom = CorusSpacing.sm,
        ),
    )
    HorizontalDivider(color = CorusColors.Divider)
}

// ── Toggle Row ──

@Composable
private fun SettingsToggleRow(
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

// ── Navigation Row ──

@Composable
private fun SettingsNavRow(
    icon: ImageVector,
    title: String,
    trailingText: String? = null,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = CorusSpacing.lg, vertical = CorusSpacing.lg),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = CorusColors.Secondary,
            modifier = Modifier.size(20.dp),
        )

        Spacer(modifier = Modifier.width(CorusSpacing.md))

        Text(
            text = title,
            style = CorusFont.body,
            color = CorusColors.Text,
            modifier = Modifier.weight(1f),
        )

        if (trailingText != null) {
            Text(
                text = trailingText,
                style = CorusFont.caption,
                color = CorusColors.Secondary,
            )
            Spacer(modifier = Modifier.width(CorusSpacing.xs))
        }

        Icon(
            Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = CorusColors.Tertiary,
            modifier = Modifier.size(20.dp),
        )
    }
    HorizontalDivider(
        color = CorusColors.Divider,
        modifier = Modifier.padding(horizontal = CorusSpacing.lg),
    )
}
