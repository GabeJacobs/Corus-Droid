package fm.corus.android.ui.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Feedback
import androidx.compose.material.icons.outlined.Group
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Policy
import androidx.compose.material.icons.outlined.Vibration
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import fm.corus.android.R
import androidx.compose.material.icons.filled.Language
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import fm.corus.android.ui.screens.auth.AuthViewModel
import fm.corus.android.ui.theme.CorusColors
import fm.corus.android.ui.theme.CorusFont
import fm.corus.android.ui.theme.CorusSpacing

@Composable
fun SettingsScreen(
    onBack: () -> Unit = {},
    onChangeUsername: () -> Unit = {},
    onChangePhoneNumber: () -> Unit = {},
    onBlockedUsers: () -> Unit = {},
    onMutedUsers: () -> Unit = {},
    onSendFeedback: () -> Unit = {},
    onNotificationSettings: () -> Unit = {},
    authViewModel: AuthViewModel = hiltViewModel(),
    settingsViewModel: SettingsViewModel = hiltViewModel(),
) {
    var showDeleteConfirm by remember { mutableStateOf(false) }

    // General toggles
    var hapticsEnabled by remember { mutableStateOf(true) }

    // Messaging
    var whoCanMessageMe by remember { mutableStateOf("Everyone") }
    var showMessageMenu by remember { mutableStateOf(false) }

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
                icon = Icons.Outlined.Vibration,
                title = "Haptics",
                subtitle = "Vibration feedback on interactions",
                checked = hapticsEnabled,
                onCheckedChange = { hapticsEnabled = it },
            )

            // ── Section: Notifications & Messaging ──
            SectionHeader("NOTIFICATIONS & MESSAGING")

            SettingsNavRow(
                icon = Icons.Filled.Notifications,
                title = "Notifications",
                subtitle = "Manage push notification preferences",
                onClick = onNotificationSettings,
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
                    listOf("Everyone", "My Followers", "People I Follow", "Nobody").forEach { option ->
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
                icon = Icons.Filled.Block,
                title = "Blocked Users",
                onClick = onBlockedUsers,
            )

            SettingsNavRow(
                icon = Icons.Filled.VolumeOff,
                title = "Muted Users",
                onClick = onMutedUsers,
            )

            SettingsNavRow(
                icon = Icons.Filled.Person,
                title = "Username",
                onClick = onChangeUsername,
            )

            SettingsNavRow(
                icon = Icons.Filled.Phone,
                title = "Phone Number",
                onClick = onChangePhoneNumber,
            )

            // Sign Out
            SettingsActionRow(
                icon = Icons.AutoMirrored.Filled.ExitToApp,
                title = "Sign Out",
                onClick = { authViewModel.signOut() },
            )

            // Delete Account
            SettingsActionRow(
                icon = Icons.Filled.Delete,
                title = "Delete Account",
                color = CorusColors.Error,
                onClick = { showDeleteConfirm = true },
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

            // ── Social links ──
            Spacer(modifier = Modifier.height(CorusSpacing.lg))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
            ) {
                SocialLinkButton(
                    icon = Icons.Filled.Language,
                    label = "Website",
                    url = "https://corus.fm",
                    context = context,
                )
                SocialLinkButton(
                    drawableRes = R.drawable.instagram_logo,
                    label = "Instagram",
                    url = "https://www.instagram.com/corusapp/",
                    context = context,
                )
                SocialLinkButton(
                    drawableRes = R.drawable.x_logo,
                    label = "X",
                    url = "https://x.com/corusfm",
                    context = context,
                )
                SocialLinkButton(
                    drawableRes = R.drawable.discord_logo,
                    label = "Discord",
                    url = "https://discord.gg/4CJJ89YB",
                    context = context,
                )
            }

            // ── Version ──
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
    subtitle: String? = null,
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

        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = CorusFont.body, color = CorusColors.Text)
            if (subtitle != null) {
                Text(text = subtitle, style = CorusFont.caption, color = CorusColors.Secondary)
            }
        }

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

// ── Action Row (no chevron) ──

@Composable
private fun SettingsActionRow(
    icon: ImageVector,
    title: String,
    color: androidx.compose.ui.graphics.Color = CorusColors.Text,
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
            tint = color,
            modifier = Modifier.size(20.dp),
        )
        Spacer(modifier = Modifier.width(CorusSpacing.md))
        Text(text = title, style = CorusFont.body, color = color)
    }
    HorizontalDivider(
        color = CorusColors.Divider,
        modifier = Modifier.padding(horizontal = CorusSpacing.lg),
    )
}

// ── Social Link Button ──

@Composable
private fun SocialLinkButton(
    icon: ImageVector? = null,
    drawableRes: Int? = null,
    label: String,
    url: String,
    context: android.content.Context,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clickable {
                context.startActivity(
                    android.content.Intent(
                        android.content.Intent.ACTION_VIEW,
                        android.net.Uri.parse(url),
                    )
                )
            }
            .padding(horizontal = CorusSpacing.md, vertical = CorusSpacing.sm),
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = CorusColors.Secondary,
                modifier = Modifier.size(18.dp),
            )
        } else if (drawableRes != null) {
            Icon(
                painter = painterResource(id = drawableRes),
                contentDescription = label,
                tint = CorusColors.Secondary,
                modifier = Modifier.size(18.dp),
            )
        }
        Spacer(modifier = Modifier.height(CorusSpacing.xs))
        Text(text = label, style = CorusFont.caption, color = CorusColors.Secondary)
    }
}
