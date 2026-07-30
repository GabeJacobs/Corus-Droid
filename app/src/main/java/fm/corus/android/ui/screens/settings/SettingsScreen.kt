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
import androidx.compose.material.icons.filled.GpsFixed
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Contacts
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Feedback
import androidx.compose.material.icons.outlined.Group
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Policy
import androidx.compose.material.icons.outlined.Vibration
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import fm.corus.android.BuildConfig
import fm.corus.android.R
import fm.corus.android.data.model.MusicService
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import fm.corus.android.ui.LocalHapticManager
import fm.corus.android.ui.components.CorusHeaderIconButton
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
import fm.corus.android.ui.theme.AppearanceMode
import fm.corus.android.ui.theme.CorusColors
import fm.corus.android.ui.theme.CorusFont
import fm.corus.android.ui.theme.CorusSpacing
import fm.corus.android.ui.theme.CorusSystemBars

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
    onSyncContacts: () -> Unit = {},
    authViewModel: AuthViewModel = hiltViewModel(),
    appearanceViewModel: AppearanceSettingsViewModel = hiltViewModel(),
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
                ToastManager.show(context.getString(R.string.settings_reauth_failed))
            }
        } catch (e: ApiException) {
            ToastManager.show(context.getString(R.string.settings_reauth_failed))
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
                    } ?: ToastManager.show(context.getString(R.string.settings_reauth_could_not_start))
                }
                else -> ToastManager.show(context.getString(R.string.settings_reauth_signout_prompt))
            }
        }
    }

    // Show toast on successful deletion
    LaunchedEffect(Unit) {
        authViewModel.accountDeleted.collect {
            ToastManager.show(context.getString(R.string.settings_account_deleted_toast))
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
    val restoreInProgress by settingsViewModel.restoreInProgress.collectAsState()
    val restoreResult by settingsViewModel.restoreResult.collectAsState()
    val restoreLoadingMessage = stringResource(R.string.club_restore_purchases)
    val restoreSuccessMessage = stringResource(R.string.settings_restore_toast_success)
    val restoreNoneMessage = stringResource(R.string.settings_restore_toast_none)
    val restoreFailedMessage = stringResource(R.string.settings_restore_toast_failed)
    var restoreLoadingToastId by remember { mutableStateOf<Long?>(null) }

    LaunchedEffect(restoreInProgress) {
        if (restoreInProgress && restoreLoadingToastId == null) {
            restoreLoadingToastId = ToastManager.showLoading(restoreLoadingMessage)
        }
    }

    LaunchedEffect(restoreResult) {
        val result = restoreResult ?: return@LaunchedEffect
        val message = when (result) {
            SettingsViewModel.RestoreResult.Success -> restoreSuccessMessage
            SettingsViewModel.RestoreResult.NoSubscription -> restoreNoneMessage
            SettingsViewModel.RestoreResult.Failed -> restoreFailedMessage
        }
        restoreLoadingToastId?.let { ToastManager.update(it, message) } ?: ToastManager.show(message)
        restoreLoadingToastId = null
        settingsViewModel.clearRestoreResult()
    }

    // General toggles
    var hapticsEnabled by remember { mutableStateOf(true) }
    val feedFollowsNowPlaying by settingsViewModel.feedFollowsNowPlaying.collectAsState()
    val playFullSongs by settingsViewModel.playFullSongs.collectAsState()

    // Messaging
    var whoCanMessageMe by remember { mutableStateOf("Everyone") }
    val messageOptionLabels: Map<String, String> = mapOf(
        "Everyone" to stringResource(R.string.settings_message_everyone),
        "My Followers" to stringResource(R.string.settings_message_my_followers),
        "People I Follow" to stringResource(R.string.settings_message_people_i_follow),
        "Nobody" to stringResource(R.string.settings_message_nobody),
    )

    Column(modifier = Modifier.fillMaxSize().background(CorusColors.Background)) {
        // ── Header ──
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
            Text(stringResource(R.string.settings_screen_title), style = CorusFont.screenTitle, color = CorusColors.Text)
        }

        HorizontalDivider(color = CorusColors.Divider)

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState()),
        ) {
            // ── Section: Corus Club ──
            if (showJoinClub) {
                SectionHeader(stringResource(R.string.settings_section_corus_club))

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
                            text = stringResource(R.string.settings_row_join_club),
                            style = CorusFont.body,
                            color = CorusColors.Text,
                        )
                        Text(
                            text = stringResource(R.string.settings_row_join_club_subtitle),
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

                SettingsNavRow(
                    icon = Icons.Filled.Refresh,
                    title = stringResource(R.string.club_restore_purchases),
                    subtitle = stringResource(R.string.settings_row_restore_purchases_subtitle),
                    onClick = {
                        if (!restoreInProgress) {
                            settingsViewModel.restorePurchases()
                        }
                    },
                )
            }

            // ── Section: Invite ──
            SectionHeader(stringResource(R.string.settings_section_invite))

            val inviteShareText = stringResource(R.string.settings_share_app_text)
            val inviteShareChooser = stringResource(R.string.settings_share_app_chooser)
            SettingsNavRow(
                icon = Icons.Filled.PersonAdd,
                title = stringResource(R.string.settings_row_invite_friends),
                subtitle = stringResource(R.string.settings_row_invite_friends_subtitle),
                onClick = {
                    val sendIntent = android.content.Intent().apply {
                        action = android.content.Intent.ACTION_SEND
                        putExtra(android.content.Intent.EXTRA_TEXT, inviteShareText)
                        type = "text/plain"
                    }
                    context.startActivity(android.content.Intent.createChooser(sendIntent, inviteShareChooser))
                },
            )

            SettingsNavRow(
                icon = Icons.Filled.Contacts,
                title = stringResource(R.string.settings_row_sync_contacts),
                subtitle = stringResource(R.string.settings_row_sync_contacts_subtitle),
                onClick = onSyncContacts,
            )

            // ── Section: Appearance ──
            SectionHeader(stringResource(R.string.settings_section_appearance))

            val appearanceMode by appearanceViewModel.appearanceMode.collectAsState()
            val appearanceOptionLabels: Map<AppearanceMode, String> = mapOf(
                AppearanceMode.LIGHT to stringResource(R.string.appearance_option_light),
                AppearanceMode.DARK to stringResource(R.string.appearance_option_dark),
                AppearanceMode.SYSTEM to stringResource(R.string.appearance_option_system),
            )
            DropdownSettingsRow(
                icon = Icons.Filled.DarkMode,
                title = stringResource(R.string.appearance_row_theme),
                subtitle = stringResource(R.string.appearance_row_theme_subtitle),
                selected = appearanceMode,
                options = AppearanceMode.values().toList(),
                labelFor = { appearanceOptionLabels[it] ?: it.displayLabel },
                onSelect = { appearanceViewModel.setAppearanceMode(it) },
            )

            // ── Section: General ──
            SectionHeader(stringResource(R.string.settings_section_general))

            val musicService by settingsViewModel.musicServicePreference.current.collectAsState()
            val musicServiceOptions = remember(
                settingsViewModel.tidalEnabled,
                settingsViewModel.youtubeMusicEnabled,
                settingsViewModel.deezerEnabled,
            ) {
                buildList {
                    add(MusicService.SPOTIFY)
                    // TIDAL / YouTube Music / Deezer only appear when their Remote
                    // Config gates are on. YouTube Music sits below TIDAL, above Deezer.
                    if (settingsViewModel.tidalEnabled) add(MusicService.TIDAL)
                    if (settingsViewModel.youtubeMusicEnabled) add(MusicService.YOUTUBE_MUSIC)
                    if (settingsViewModel.deezerEnabled) add(MusicService.DEEZER)
                    // Apple Music is intentionally last on Android.
                    add(MusicService.APPLE_MUSIC)
                }
            }
            DropdownSettingsRow(
                icon = Icons.Filled.MusicNote,
                title = stringResource(R.string.settings_row_music_service),
                selected = musicService,
                options = musicServiceOptions,
                labelFor = { it.displayLabel },
                onSelect = { settingsViewModel.setMusicService(it) },
            )

            if (musicService == MusicService.SPOTIFY && settingsViewModel.spotifyAuthExperimentEnabled) {
                SettingsToggleRow(
                    icon = Icons.Filled.QueueMusic,
                    title = stringResource(R.string.settings_row_play_full_songs_title),
                    subtitle = stringResource(R.string.settings_row_play_full_songs_spotify_subtitle),
                    checked = playFullSongs,
                    onCheckedChange = { settingsViewModel.setPlayFullSongs(it) },
                )
            }

            SettingsToggleRow(
                icon = Icons.Filled.GpsFixed,
                title = stringResource(R.string.settings_row_feed_follows_now_playing_title),
                subtitle = stringResource(R.string.settings_row_feed_follows_now_playing_subtitle),
                checked = feedFollowsNowPlaying,
                onCheckedChange = { settingsViewModel.setFeedFollowsNowPlaying(it) },
            )

            // Hide on devices without a vibrator (some tablets, Android TV, emulators) —
            // a toggle that controls non-existent hardware would be misleading.
            if (LocalHapticManager.current.hasVibrator()) {
                SettingsToggleRow(
                    icon = Icons.Outlined.Vibration,
                    title = stringResource(R.string.settings_row_haptics),
                    subtitle = stringResource(R.string.settings_row_haptics_subtitle),
                    checked = hapticsEnabled,
                    onCheckedChange = { hapticsEnabled = it },
                )
            }

            var currentLanguage by remember { mutableStateOf(fm.corus.android.i18n.LanguageManager.current(context)) }
            val languageOptionLabels: Map<fm.corus.android.i18n.AppLanguage, String> = mapOf(
                fm.corus.android.i18n.AppLanguage.SYSTEM to stringResource(R.string.language_option_system),
                fm.corus.android.i18n.AppLanguage.ENGLISH to stringResource(R.string.language_option_english),
                fm.corus.android.i18n.AppLanguage.PORTUGUESE_BR to stringResource(R.string.language_option_portuguese_br),
                fm.corus.android.i18n.AppLanguage.SPANISH to stringResource(R.string.language_option_spanish),
                fm.corus.android.i18n.AppLanguage.JAPANESE to stringResource(R.string.language_option_japanese),
                fm.corus.android.i18n.AppLanguage.CHINESE_SIMPLIFIED to stringResource(R.string.language_option_chinese_simplified),
                fm.corus.android.i18n.AppLanguage.GERMAN to stringResource(R.string.language_option_german),
                fm.corus.android.i18n.AppLanguage.FRENCH to stringResource(R.string.language_option_french),
                fm.corus.android.i18n.AppLanguage.KOREAN to stringResource(R.string.language_option_korean),
                fm.corus.android.i18n.AppLanguage.ITALIAN to stringResource(R.string.language_option_italian),
            )
            DropdownSettingsRow(
                icon = Icons.Filled.Language,
                title = stringResource(R.string.settings_row_language),
                selected = currentLanguage,
                options = fm.corus.android.i18n.AppLanguage.values().toList(),
                labelFor = { languageOptionLabels[it] ?: it.name },
                onSelect = { option ->
                    currentLanguage = option
                    settingsViewModel.syncLanguagePreference(option)
                    activity?.let { fm.corus.android.i18n.LanguageManager.set(it, option) }
                },
            )

            // ── Section: Notifications & Messaging ──
            SectionHeader(stringResource(R.string.settings_section_notifications_messaging))

            SettingsNavRow(
                icon = Icons.Filled.Notifications,
                title = stringResource(R.string.settings_row_notifications),
                subtitle = stringResource(R.string.settings_row_notifications_subtitle),
                onClick = onNotificationSettings,
            )

            // "Who Can Message Me" menu row
            DropdownSettingsRow(
                icon = Icons.Outlined.Group,
                title = stringResource(R.string.settings_row_who_can_message),
                selected = whoCanMessageMe,
                options = listOf("Everyone", "My Followers", "People I Follow", "Nobody"),
                labelFor = { messageOptionLabels[it] ?: it },
                onSelect = { whoCanMessageMe = it },
            )

            // ── Section: Account ──
            SectionHeader(stringResource(R.string.settings_section_account))

            SettingsNavRow(
                icon = Icons.Filled.Block,
                title = stringResource(R.string.settings_row_blocked_users),
                onClick = onBlockedUsers,
            )

            SettingsNavRow(
                icon = Icons.Filled.VolumeOff,
                title = stringResource(R.string.settings_row_muted_users),
                onClick = onMutedUsers,
            )

            SettingsNavRow(
                icon = Icons.Filled.Person,
                title = stringResource(R.string.settings_row_username),
                onClick = onChangeUsername,
            )

            SettingsNavRow(
                icon = Icons.Filled.Phone,
                title = stringResource(R.string.settings_row_phone_number),
                onClick = onChangePhoneNumber,
            )

            // Sign Out
            SettingsActionRow(
                icon = Icons.AutoMirrored.Filled.ExitToApp,
                title = stringResource(R.string.settings_row_sign_out),
                onClick = { authViewModel.signOut() },
            )

            // ── Section: Support ──
            SectionHeader(stringResource(R.string.settings_section_support))

            SettingsNavRow(
                icon = Icons.Outlined.Feedback,
                title = stringResource(R.string.settings_row_send_feedback),
                onClick = {
                    val intent = android.content.Intent(android.content.Intent.ACTION_SENDTO).apply {
                        data = android.net.Uri.parse("mailto:help@corus.fm?subject=Feedback")
                    }
                    try { context.startActivity(intent) } catch (_: Exception) { }
                },
            )

            SettingsNavRow(
                icon = Icons.AutoMirrored.Outlined.HelpOutline,
                title = stringResource(R.string.settings_row_contact_us),
                onClick = {
                    val intent = android.content.Intent(android.content.Intent.ACTION_SENDTO).apply {
                        data = android.net.Uri.parse("mailto:help@corus.fm?subject=Support%20Request")
                    }
                    try { context.startActivity(intent) } catch (_: Exception) { }
                },
            )

            SettingsNavRow(
                icon = Icons.Filled.Star,
                title = stringResource(R.string.settings_row_rate_app),
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
                title = stringResource(R.string.settings_row_privacy_policy),
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
                title = stringResource(R.string.settings_row_terms_of_service),
                onClick = {
                    val intent = android.content.Intent(
                        android.content.Intent.ACTION_VIEW,
                        android.net.Uri.parse("https://corus.fm/terms"),
                    )
                    try { context.startActivity(intent) } catch (_: Exception) { }
                },
            )

            // ── Section: Danger Zone ──
            // Kept separate from Sign Out (and pushed below Support) so it can't
            // be tapped by mistake while reaching for log out.
            SectionHeader(stringResource(R.string.settings_section_danger_zone))

            SettingsActionRow(
                icon = Icons.Filled.Delete,
                title = stringResource(R.string.settings_row_delete_account),
                color = CorusColors.Error,
                onClick = { showDeleteConfirm = true },
            )

            Text(
                text = stringResource(R.string.settings_dialog_delete_message),
                style = CorusFont.caption,
                color = CorusColors.Tertiary,
                modifier = Modifier.padding(
                    start = CorusSpacing.lg,
                    end = CorusSpacing.lg,
                    top = CorusSpacing.sm,
                ),
            )

            // ── Social links ──
            Spacer(modifier = Modifier.height(CorusSpacing.lg))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
            ) {
                SocialLinkButton(
                    icon = Icons.Filled.Language,
                    label = stringResource(R.string.settings_social_website),
                    url = "https://corus.fm",
                    context = context,
                )
                SocialLinkButton(
                    drawableRes = R.drawable.instagram_logo,
                    label = stringResource(R.string.settings_social_instagram),
                    url = "https://www.instagram.com/corusapp/",
                    context = context,
                )
                SocialLinkButton(
                    drawableRes = R.drawable.x_logo,
                    label = stringResource(R.string.settings_social_x),
                    url = "https://x.com/corusfm",
                    context = context,
                )
                SocialLinkButton(
                    drawableRes = R.drawable.discord_logo,
                    label = stringResource(R.string.settings_social_discord),
                    url = "https://discord.gg/mXzt8NDCWD",
                    context = context,
                )
            }

            // ── Version ──
            Spacer(modifier = Modifier.height(CorusSpacing.lg))

            Text(
                text = stringResource(R.string.settings_version_format, BuildConfig.VERSION_NAME),
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
            CorusSystemBars()
            CymbalClubOfferSheet(
                source = PaywallSource.SETTINGS,
                onDismiss = { showClubOffer = false },
            )
        }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text(stringResource(R.string.settings_dialog_delete_title), style = CorusFont.songTitleLarge) },
            text = {
                Text(
                    stringResource(R.string.settings_dialog_delete_message),
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
                    Text(stringResource(R.string.common_delete), color = CorusColors.Error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text(stringResource(R.string.common_cancel))
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
            title = { Text(stringResource(R.string.settings_dialog_deleting_title), style = CorusFont.songTitleLarge) },
            text = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = CorusColors.Error,
                    )
                    Spacer(modifier = Modifier.width(CorusSpacing.md))
                    Text(stringResource(R.string.settings_dialog_deleting_message), style = CorusFont.body)
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
            title = { Text(stringResource(R.string.settings_dialog_verify_title), style = CorusFont.songTitleLarge) },
            text = {
                Column {
                    Text(
                        stringResource(R.string.settings_dialog_verify_message),
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
                        label = { Text(stringResource(R.string.settings_dialog_verify_code_label)) },
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
                        Text(stringResource(R.string.common_confirm), color = CorusColors.Error)
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
                    Text(stringResource(R.string.common_cancel))
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

// ── Dropdown Settings Row ──
//
// Wraps SettingsNavRow + DropdownMenu in a Box so the popup anchors to the row
// itself. Use Modifier.fillMaxWidth() on the Box: a wrap-content Box would size
// to the menu's anchor bounds and break the row's full-width divider. Material3
// DropdownMenu does not anchor to its layout-flow position when its parent is a
// scrolling Column — it anchors to the parent layout's bounds — so an explicit
// Box wrapper is required.
@Composable
private fun <T> DropdownSettingsRow(
    icon: ImageVector,
    title: String,
    subtitle: String? = null,
    selected: T,
    options: List<T>,
    labelFor: (T) -> String,
    onSelect: (T) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier = Modifier.fillMaxWidth()) {
        // SettingsNavRow emits Row + HorizontalDivider as siblings. A bare Box
        // stacks children (TopStart by default), which puts the divider on top
        // of the row instead of below it. Wrap in Column to preserve the
        // intended vertical flow.
        Column(modifier = Modifier.fillMaxWidth()) {
            SettingsNavRow(
                icon = icon,
                title = title,
                subtitle = subtitle,
                trailingText = labelFor(selected),
                onClick = { expanded = true },
            )
        }
        Box(modifier = Modifier.align(Alignment.BottomEnd)) {
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
        options.forEach { option ->
            DropdownMenuItem(
                text = {
                    Text(
                        text = labelFor(option),
                        style = CorusFont.body,
                        color = if (option == selected) CorusColors.Accent else CorusColors.Text,
                    )
                },
                onClick = {
                    expanded = false
                    onSelect(option)
                },
            )
        }
        }
        }
    }
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
