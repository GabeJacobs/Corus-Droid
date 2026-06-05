package fm.corus.android.ui.screens.profile

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import fm.corus.android.R
import fm.corus.android.ui.theme.CorusColors
import fm.corus.android.ui.theme.CorusFont
import fm.corus.android.ui.theme.CorusSpacing

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditProfileScreen(
    viewModel: EditProfileViewModel = hiltViewModel(),
    onBack: () -> Unit = {},
    onCustomizeProfile: () -> Unit = {},
) {
    val context = LocalContext.current

    val displayName by viewModel.displayName.collectAsState()
    val username by viewModel.username.collectAsState()
    val bio by viewModel.bio.collectAsState()
    val website by viewModel.website.collectAsState()
    val featuredTab by viewModel.featuredTab.collectAsState()
    val usernameState by viewModel.usernameState.collectAsState()
    val usernameInvalidReason by viewModel.usernameInvalidReason.collectAsState()
    val isSaving by viewModel.isSaving.collectAsState()
    val saveError by viewModel.saveError.collectAsState()

    val profile by viewModel.profile.collectAsState()

    // Derive canSave reactively from collected states so Compose can observe changes
    val canSave = remember(displayName, username, bio, website, featuredTab, profile, usernameState, isSaving) {
        val p = profile ?: return@remember false
        val hasChanges = displayName != p.displayName ||
                username != p.username ||
                bio != p.bio ||
                website != (p.website ?: "") ||
                featuredTab != p.featuredTab
        if (!hasChanges) return@remember false
        if (displayName.isBlank()) return@remember false
        if (username != p.username && usernameState != EditProfileViewModel.UsernameState.AVAILABLE) return@remember false
        if (isSaving) return@remember false
        true
    }

    var showDiscardDialog by remember { mutableStateOf(false) }
    // Track pending action when user has unsaved changes and taps Customize or Share
    var pendingAction by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        viewModel.loadProfile()
    }

    // Unsaved changes warning
    BackHandler(enabled = viewModel.hasUnsavedChanges) {
        showDiscardDialog = true
    }

    // Save error dialog
    if (saveError != null) {
        AlertDialog(
            onDismissRequest = { viewModel.clearSaveError() },
            title = { Text(stringResource(R.string.common_error), style = CorusFont.songTitle, color = CorusColors.Text) },
            text = { Text(saveError!!, style = CorusFont.body, color = CorusColors.Text) },
            confirmButton = {
                TextButton(onClick = { viewModel.clearSaveError() }) {
                    Text(stringResource(R.string.common_ok), style = CorusFont.button, color = CorusColors.Accent)
                }
            },
            containerColor = CorusColors.Background,
        )
    }

    // Unsaved changes dialog (for back, customize, or share actions)
    if (showDiscardDialog) {
        AlertDialog(
            onDismissRequest = {
                showDiscardDialog = false
                pendingAction = null
            },
            title = { Text(stringResource(R.string.edit_profile_dialog_unsaved_title), style = CorusFont.songTitle, color = CorusColors.Text) },
            text = { Text(stringResource(R.string.edit_profile_dialog_unsaved_message), style = CorusFont.body, color = CorusColors.Text) },
            confirmButton = {
                TextButton(onClick = {
                    showDiscardDialog = false
                    val action = pendingAction
                    pendingAction = null
                    when (action) {
                        "customize" -> onCustomizeProfile()
                        "share" -> {
                            val shareText = context.getString(R.string.edit_profile_share_text_format, username)
                            val intent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_TEXT, shareText)
                            }
                            context.startActivity(Intent.createChooser(intent, context.getString(R.string.edit_profile_share_chooser)))
                        }
                        else -> onBack()
                    }
                }) {
                    Text(stringResource(R.string.edit_profile_dialog_discard), style = CorusFont.button, color = CorusColors.Error)
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showDiscardDialog = false
                    pendingAction = null
                }) {
                    Text(stringResource(R.string.edit_profile_dialog_keep_editing), style = CorusFont.button, color = CorusColors.Accent)
                }
            },
            containerColor = CorusColors.Background,
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.edit_profile_screen_title), style = CorusFont.screenTitle, color = CorusColors.Text) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.edit_profile_cd_cancel), tint = CorusColors.Text)
                    }
                },
                actions = {
                    TextButton(
                        onClick = { viewModel.save(onSuccess = onBack) },
                        enabled = canSave,
                    ) {
                        if (isSaving) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = CorusColors.Accent)
                        } else {
                            Text(
                                stringResource(R.string.edit_profile_save),
                                style = CorusFont.button,
                                color = if (canSave) CorusColors.Accent else CorusColors.Tertiary,
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = CorusColors.Background),
                windowInsets = WindowInsets(0, 0, 0, 0),
            )
        },
    ) { padding ->
        val keyboardController = LocalSoftwareKeyboardController.current
        val dismissKeyboardOnScroll = remember {
            object : NestedScrollConnection {
                override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                    if (available.y > 4f && source == NestedScrollSource.UserInput) {
                        keyboardController?.hide()
                    }
                    return Offset.Zero
                }
            }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .nestedScroll(dismissKeyboardOnScroll)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = CorusSpacing.xxl, vertical = CorusSpacing.md),
            verticalArrangement = Arrangement.spacedBy(CorusSpacing.xxl),
        ) {
            // Name field
            EditField(
                label = stringResource(R.string.edit_profile_field_name),
                value = displayName,
                onValueChange = { viewModel.updateDisplayName(it) },
                singleLine = true,
            )

            // Username field
            Column {
                Text(stringResource(R.string.edit_profile_field_username), style = CorusFont.sectionHeader, color = CorusColors.Secondary)
                Spacer(modifier = Modifier.height(CorusSpacing.sm))

                val borderColor = when (usernameState) {
                    EditProfileViewModel.UsernameState.AVAILABLE -> CorusColors.Verified
                    EditProfileViewModel.UsernameState.TAKEN,
                    EditProfileViewModel.UsernameState.INVALID -> CorusColors.Error
                    else -> CorusColors.Divider
                }

                TextField(
                    value = username,
                    onValueChange = { viewModel.updateUsername(it) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, borderColor, RoundedCornerShape(CorusSpacing.cornerRadiusMedium)),
                    prefix = { Text("@", style = CorusFont.body, color = CorusColors.Secondary) },
                    singleLine = true,
                    shape = RoundedCornerShape(CorusSpacing.cornerRadiusMedium),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = CorusColors.CardBackground,
                        unfocusedContainerColor = CorusColors.CardBackground,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                        cursorColor = CorusColors.Accent,
                    ),
                    textStyle = CorusFont.body.copy(color = CorusColors.Text),
                    trailingIcon = {
                        when (usernameState) {
                            EditProfileViewModel.UsernameState.CHECKING -> {
                                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = CorusColors.Accent)
                            }
                            EditProfileViewModel.UsernameState.AVAILABLE -> {
                                Icon(Icons.Filled.Check, contentDescription = stringResource(R.string.edit_profile_cd_available), tint = CorusColors.Verified)
                            }
                            EditProfileViewModel.UsernameState.TAKEN -> {
                                Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.edit_profile_cd_taken), tint = CorusColors.Error)
                            }
                            EditProfileViewModel.UsernameState.INVALID -> {
                                Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.edit_profile_cd_invalid), tint = CorusColors.Error)
                            }
                            else -> {}
                        }
                    },
                )

                when (usernameState) {
                    EditProfileViewModel.UsernameState.TAKEN -> {
                        Spacer(modifier = Modifier.height(CorusSpacing.xs))
                        Text(stringResource(R.string.edit_profile_username_taken), style = CorusFont.caption, color = CorusColors.Error)
                    }
                    EditProfileViewModel.UsernameState.INVALID -> {
                        Spacer(modifier = Modifier.height(CorusSpacing.xs))
                        Text(usernameInvalidReason ?: stringResource(R.string.edit_profile_username_invalid), style = CorusFont.caption, color = CorusColors.Error)
                    }
                    else -> {}
                }
            }

            // Bio field
            EditField(
                label = stringResource(R.string.edit_profile_field_bio),
                value = bio,
                onValueChange = { viewModel.updateBio(it) },
                singleLine = false,
                minLines = 3,
                maxLines = 6,
            )

            // Website field
            EditField(
                label = stringResource(R.string.edit_profile_field_website),
                value = website,
                onValueChange = { viewModel.updateWebsite(it) },
                singleLine = true,
            )

            // Featured Tab picker — controls whether this user's profile leads
            // with Music or Film when viewed by anyone (including themselves).
            Column {
                Text(
                    stringResource(R.string.edit_profile_field_featured_tab),
                    style = CorusFont.sectionHeader,
                    color = CorusColors.Secondary,
                )
                Spacer(modifier = Modifier.height(CorusSpacing.sm))
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    val options = listOf("music" to R.string.profile_tab_music, "film" to R.string.profile_tab_film)
                    options.forEachIndexed { index, (value, labelRes) ->
                        SegmentedButton(
                            selected = featuredTab == value,
                            onClick = { viewModel.updateFeaturedTab(value) },
                            shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size),
                            colors = SegmentedButtonDefaults.colors(
                                activeContainerColor = CorusColors.Accent.copy(alpha = 0.15f),
                                activeContentColor = CorusColors.Accent,
                                activeBorderColor = CorusColors.Accent,
                                inactiveContainerColor = CorusColors.CardBackground,
                                inactiveContentColor = CorusColors.Text,
                                inactiveBorderColor = CorusColors.Divider,
                            ),
                        ) {
                            Text(stringResource(labelRes))
                        }
                    }
                }
            }

            // Divider before action rows
            HorizontalDivider(color = CorusColors.Divider)

            // Customize Profile Style row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        if (viewModel.hasUnsavedChanges) {
                            pendingAction = "customize"
                            showDiscardDialog = true
                        } else {
                            onCustomizeProfile()
                        }
                    }
                    .padding(vertical = CorusSpacing.md),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(CorusSpacing.md),
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_paintbrush),
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = CorusColors.Accent,
                )
                Text(
                    stringResource(R.string.edit_profile_row_customize),
                    style = CorusFont.bodyMedium,
                    color = CorusColors.Text,
                    modifier = Modifier.weight(1f),
                )
                Icon(
                    Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = CorusColors.Tertiary,
                    modifier = Modifier.size(20.dp),
                )
            }

            // Share Profile Link row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        val shareText = context.getString(R.string.edit_profile_share_text_format, username)
                        val intent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, shareText)
                        }
                        context.startActivity(Intent.createChooser(intent, context.getString(R.string.edit_profile_share_chooser)))
                    }
                    .padding(vertical = CorusSpacing.md),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(CorusSpacing.md),
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_link),
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = CorusColors.Accent,
                )
                Text(
                    stringResource(R.string.edit_profile_row_share_link),
                    style = CorusFont.bodyMedium,
                    color = CorusColors.Text,
                    modifier = Modifier.weight(1f),
                )
                Icon(
                    Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = CorusColors.Tertiary,
                    modifier = Modifier.size(20.dp),
                )
            }

        }
    }
}

@Composable
private fun EditField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    singleLine: Boolean = true,
    minLines: Int = 1,
    maxLines: Int = 1,
) {
    Column {
        Text(label, style = CorusFont.sectionHeader, color = CorusColors.Secondary)
        Spacer(modifier = Modifier.height(CorusSpacing.sm))
        TextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, CorusColors.Divider, RoundedCornerShape(CorusSpacing.cornerRadiusMedium)),
            singleLine = singleLine,
            minLines = minLines,
            maxLines = if (singleLine) 1 else maxLines,
            shape = RoundedCornerShape(CorusSpacing.cornerRadiusMedium),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = CorusColors.CardBackground,
                unfocusedContainerColor = CorusColors.CardBackground,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
                cursorColor = CorusColors.Accent,
            ),
            textStyle = CorusFont.body.copy(color = CorusColors.Text),
        )
    }
}
