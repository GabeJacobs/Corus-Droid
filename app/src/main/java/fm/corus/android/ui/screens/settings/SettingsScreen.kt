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
import androidx.compose.material.icons.filled.AllInclusive
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
import fm.corus.android.BuildConfig
import fm.corus.android.R
import androidx.compose.material.icons.filled.Language
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import fm.corus.android.ui.components.CymbalClubVinyl
import fm.corus.android.ui.components.ToastManager
import fm.corus.android.ui.screens.auth.AuthViewModel
import fm.corus.android.ui.screens.subscription.CymbalClubOfferSheet
import fm.corus.android.ui.screens.subscription.PaywallSource
import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import fm.corus.android.ui.theme.CorusColors
import fm.corus.android.ui.theme.CorusFont
import fm.corus.android.ui.theme.CorusSpacing

@OptIn(ExperimentalMaterial3Api::class)
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
    val context = LocalContext.current
    val activity = context as? Activity
    var showClubOffer by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    val isDeletingAccount by authViewModel.isDeletingAccount.collectAsState()
    val deleteError by authViewModel.error.collectAsState()
    val phoneReauthCodeSent by authViewModel.phoneReauthCodeSent.collectAsState()
    var phoneReauthCode by remember { mutableStateOf("") }

    // Google re-auth launcher for account deletion
    val googleReauthLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        try {
            val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
            val account = task.getResult(ApiException::class.java)
            val idToken = account.idToken
            if (idToken != null) {
                authViewModel.reauthenticateAndDelete(idToken)
            } else {
                ToastManager.show("Re-authentication failed. Please try again.")
            }
        } catch (e: ApiException) {
            ToastManager.show("Re-authentication failed. Please try again.")
        }
    }

    // Handle re-auth requests from the ViewModel
    LaunchedEffect(Unit) {
        authViewModel.needsReauth.collect { providerId ->
            when (providerId) {
                "google.com" -> {
                    val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                        .requestIdToken(context.getString(R.string.default_web_client_id))
                        .requestEmail()
                        .build()
                    val client = GoogleSignIn.getClient(context, gso)
                    googleReauthLauncher.launch(client.signInIntent)
                }
                "phone" -> {
                    activity?.let {
                        phoneReauthCode = ""
                        authViewModel.startPhoneReauth(it)
                    } ?: ToastManager.show("Could not start re-authentication.")
                }
                else -> ToastManager.show("Please sign out and sign back in, then try again.")
            }
        }
    }

    // Show toast on successful deletion
    LaunchedEffect(Unit) {
        authViewModel.accountDeleted.collect {
            ToastManager.show("Your account has been deleted.")
        }
    }

    // Show toast on delete error
    LaunchedEffect(deleteError) {
        deleteError?.let {
            ToastManager.show(it)
            authViewModel.clearError()
        }
    }

    // Club status
    val isClubMember by settingsViewModel.isClubMember.collectAsState()
    val isVerified by settingsViewModel.isVerified.collectAsState()
    val showJoinClub = !isClubMember && !isVerified

    // General toggles
    var hapticsEnabled by remember { mutableStateOf(true) }
    val autoplayNextSong by settingsViewModel.autoplayNextSong.collectAsState()

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
            // ── Section: Corus Club ──
            if (showJoinClub) {
                SectionHeader("CORUS CLUB")

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showClubOffer = true }
                        .padding(horizontal = CorusSpacing.lg, vertical = CorusSpacing.md),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CymbalClubVinyl(size = 44.dp)

                    Spacer(modifier = Modifier.width(CorusSpacing.md))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Join Corus Club",
                            style = CorusFont.body,
                            color = CorusColors.Text,
                        )
                        Text(
                            text = "Verified badge, unlimited posts & more",
                            style = CorusFont.caption,
                            color = CorusColors.Secondary,
                        )
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

            // ── Section: General ──
            SectionHeader("GENERAL")

            SettingsToggleRow(
                icon = Icons.Filled.AllInclusive,
                title = "Autoplay Next Song",
                subtitle = "Automatically play the next song when one finishes",
                checked = autoplayNextSong,
                onCheckedChange = { settingsViewModel.setAutoplayNextSong(it) },
            )

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
            Box(modifier = Modifier.fillMaxWidth()) {
                Column {
                    SettingsNavRow(
                        icon = Icons.Outlined.Group,
                        title = "Who Can Message Me",
                        trailingText = whoCanMessageMe,
                        onClick = { showMessageMenu = true },
                    )
                }
                Box(modifier = Modifier.align(Alignment.TopEnd)) {
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
                    val intent = android.content.Intent(
                        android.content.Intent.ACTION_VIEW,
                        android.net.Uri.parse("https://corus.fm/privacy"),
                    )
                    try { context.startActivity(intent) } catch (_: Exception) { }
                },
            )

            SettingsNavRow(
                icon = Icons.Outlined.Info,
                title = "Terms of Service",
                onClick = {
                    val intent = android.content.Intent(
                        android.content.Intent.ACTION_VIEW,
                        android.net.Uri.parse("https://corus.fm/terms"),
                    )
                    try { context.startActivity(intent) } catch (_: Exception) { }
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
                    url = "https://discord.gg/mXzt8NDCWD",
                    context = context,
                )
            }

            // ── Version ──
            Spacer(modifier = Modifier.height(CorusSpacing.lg))

            Text(
                text = "Corus v${BuildConfig.VERSION_NAME}",
                style = CorusFont.caption,
                color = CorusColors.Tertiary,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = CorusSpacing.xxl),
            )
        }
    }

    // ── Club Offer Sheet ──
    if (showClubOffer) {
        val clubSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { showClubOffer = false },
            sheetState = clubSheetState,
            containerColor = CorusColors.Background,
            dragHandle = { BottomSheetDefaults.DragHandle() },
        ) {
            CymbalClubOfferSheet(
                source = PaywallSource.SETTINGS,
                onDismiss = { showClubOffer = false },
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
                TextButton(
                    onClick = {
                        showDeleteConfirm = false
                        authViewModel.deleteAccount()
                    },
                ) {
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

    // Account deletion progress overlay — shown during the initial delete,
    // Google re-auth+delete, and phone re-auth+delete. Hidden while the phone
    // code-entry dialog is visible (so the user can still see and type in it).
    if (isDeletingAccount && !phoneReauthCodeSent) {
        AlertDialog(
            onDismissRequest = { /* non-dismissable */ },
            title = { Text("Deleting your account…", style = CorusFont.songTitleLarge) },
            text = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = CorusColors.Error,
                    )
                    Spacer(modifier = Modifier.width(CorusSpacing.md))
                    Text("This may take a moment.", style = CorusFont.body)
                }
            },
            confirmButton = {},
        )
    }

    // Phone re-auth code entry dialog
    if (phoneReauthCodeSent) {
        AlertDialog(
            onDismissRequest = {
                authViewModel.cancelPhoneReauth()
                phoneReauthCode = ""
            },
            title = { Text("Verify your number", style = CorusFont.songTitleLarge) },
            text = {
                Column {
                    Text(
                        "We sent a code to your phone. Enter it to confirm account deletion.",
                        style = CorusFont.body,
                    )
                    Spacer(modifier = Modifier.height(CorusSpacing.md))
                    OutlinedTextField(
                        value = phoneReauthCode,
                        onValueChange = {
                            phoneReauthCode = it.filter { c -> c.isDigit() }.take(6)
                        },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        label = { Text("Code") },
                    )
                }
            },
            confirmButton = {
                TextButton(
                    enabled = phoneReauthCode.length == 6 && !isDeletingAccount,
                    onClick = {
                        authViewModel.verifyPhoneReauthAndDelete(phoneReauthCode)
                    },
                ) {
                    if (isDeletingAccount) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = CorusColors.Error,
                        )
                    } else {
                        Text("Confirm", color = CorusColors.Error)
                    }
                }
            },
            dismissButton = {
                TextButton(
                    enabled = !isDeletingAccount,
                    onClick = {
                        authViewModel.cancelPhoneReauth()
                        phoneReauthCode = ""
                    },
                ) {
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
