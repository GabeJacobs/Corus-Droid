package fm.corus.android.ui.screens.profile

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.hilt.navigation.compose.hiltViewModel
import fm.corus.android.R
import fm.corus.android.data.model.ProfileMediaTab
import fm.corus.android.ui.theme.CorusColors
import fm.corus.android.ui.theme.CorusFont
import fm.corus.android.ui.theme.CorusSpacing
import kotlin.math.roundToInt

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
    val tabPreferences by viewModel.tabPreferences.collectAsState()
    val usernameState by viewModel.usernameState.collectAsState()
    val usernameInvalidReason by viewModel.usernameInvalidReason.collectAsState()
    val isSaving by viewModel.isSaving.collectAsState()
    val saveError by viewModel.saveError.collectAsState()

    val profile by viewModel.profile.collectAsState()

    // Derive canSave reactively from collected states so Compose can observe changes
    val canSave = remember(displayName, username, bio, website, tabPreferences, profile, usernameState, isSaving) {
        val p = profile ?: return@remember false
        val booksEnabled = viewModel.booksEnabled
        val original = p.tabPreferences(booksEnabled)
        val originalEditor = original.editorTabs(booksEnabled)
        val currentEditor = tabPreferences.editorTabs(booksEnabled)
        val tabsChanged = originalEditor != currentEditor ||
            original.hidden.intersect(originalEditor.toSet()) !=
            tabPreferences.hidden.intersect(currentEditor.toSet())
        val hasChanges = displayName != p.displayName ||
                username != p.username ||
                bio != p.bio ||
                website != (p.website ?: "") ||
                tabsChanged
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
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.common_back),
                            tint = CorusColors.Text,
                        )
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
                capitalization = KeyboardCapitalization.Words,
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
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None),
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

            // Bio field — counter lives inside the same box as iOS so it
            // never overlaps the typed text and doesn't add extra section space.
            BioEditField(
                value = bio,
                onValueChange = { viewModel.updateBio(it) },
                maxLength = maxOf(EditProfileViewModel.BIO_MAX_LENGTH, profile?.bio?.length ?: 0),
            )

            // Website field
            EditField(
                label = stringResource(R.string.edit_profile_field_website),
                value = website,
                onValueChange = { viewModel.updateWebsite(it) },
                singleLine = true,
                capitalization = KeyboardCapitalization.None,
                keyboardType = KeyboardType.Uri,
                placeholder = stringResource(R.string.edit_profile_placeholder_website),
            )

            // Profile tabs — drag to reorder, eye to hide. At least one must stay visible.
            val booksEnabled = viewModel.booksEnabled
            ProfileTabsEditor(
                tabs = tabPreferences.editorTabs(booksEnabled = booksEnabled),
                hidden = tabPreferences.hidden,
                canHide = { tabPreferences.canHide(it, booksEnabled) },
                onToggleHidden = { viewModel.toggleHiddenTab(it) },
                onMove = { from, to -> viewModel.moveTab(from, to) },
            )

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
    capitalization: KeyboardCapitalization = KeyboardCapitalization.Sentences,
    keyboardType: KeyboardType = KeyboardType.Text,
    placeholder: String? = null,
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
            keyboardOptions = KeyboardOptions(
                capitalization = capitalization,
                keyboardType = keyboardType,
            ),
            singleLine = singleLine,
            minLines = minLines,
            maxLines = if (singleLine) 1 else maxLines,
            shape = RoundedCornerShape(CorusSpacing.cornerRadiusMedium),
            placeholder = placeholder?.let {
                {
                    Text(it, style = CorusFont.body, color = CorusColors.Tertiary)
                }
            },
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

@Composable
private fun BioEditField(
    value: String,
    onValueChange: (String) -> Unit,
    maxLength: Int,
) {
    val shape = RoundedCornerShape(CorusSpacing.cornerRadiusMedium)
    Column {
        Text(
            stringResource(R.string.edit_profile_field_bio),
            style = CorusFont.sectionHeader,
            color = CorusColors.Secondary,
        )
        Spacer(modifier = Modifier.height(CorusSpacing.sm))
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(CorusColors.CardBackground, shape)
                .border(1.dp, CorusColors.Divider, shape)
                .padding(CorusSpacing.md),
            verticalArrangement = Arrangement.spacedBy(CorusSpacing.sm),
        ) {
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier.fillMaxWidth(),
                textStyle = CorusFont.body.copy(color = CorusColors.Text),
                cursorBrush = SolidColor(CorusColors.Accent),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                minLines = 3,
                maxLines = 6,
                decorationBox = { innerTextField ->
                    Box {
                        if (value.isEmpty()) {
                            Text(
                                stringResource(R.string.edit_profile_placeholder_bio),
                                style = CorusFont.body,
                                color = CorusColors.Tertiary,
                            )
                        }
                        innerTextField()
                    }
                },
            )
            Text(
                text = "${value.length}/$maxLength",
                style = CorusFont.caption,
                color = CorusColors.Tertiary,
                modifier = Modifier.align(Alignment.End),
            )
        }
    }
}

@Composable
private fun ProfileTabsEditor(
    tabs: List<ProfileMediaTab>,
    hidden: Set<ProfileMediaTab>,
    canHide: (ProfileMediaTab) -> Boolean,
    onToggleHidden: (ProfileMediaTab) -> Unit,
    onMove: (from: ProfileMediaTab, to: ProfileMediaTab) -> Unit,
) {
    var draggingTab by remember { mutableStateOf<ProfileMediaTab?>(null) }
    var dragOffsetY by remember { mutableFloatStateOf(0f) }
    var rowHeightPx by remember { mutableFloatStateOf(0f) }

    val moveUpLabel = stringResource(R.string.edit_profile_tab_move_up)
    val moveDownLabel = stringResource(R.string.edit_profile_tab_move_down)

    Column {
        Text(
            stringResource(R.string.edit_profile_field_profile_tabs),
            style = CorusFont.sectionHeader,
            color = CorusColors.Secondary,
        )
        Spacer(modifier = Modifier.height(CorusSpacing.sm))
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, CorusColors.Divider, RoundedCornerShape(CorusSpacing.cornerRadiusMedium))
        ) {
            tabs.forEachIndexed { index, tab ->
                key(tab) {
                    val isHidden = tab in hidden
                    val tabCanHide = canHide(tab)
                    val isDragging = draggingTab == tab
                    val labelRes = when (tab) {
                        ProfileMediaTab.MUSIC -> R.string.edit_profile_tab_music
                        ProfileMediaTab.FILM -> R.string.edit_profile_tab_film
                        ProfileMediaTab.BOOKS -> R.string.edit_profile_tab_books
                    }
                    val label = stringResource(labelRes)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .onSizeChanged { size ->
                                if (size.height > 0) rowHeightPx = size.height.toFloat()
                            }
                            .zIndex(if (isDragging) 1f else 0f)
                            .offset { IntOffset(0, if (isDragging) dragOffsetY.roundToInt() else 0) }
                            .then(
                                if (isDragging) {
                                    Modifier
                                        .shadow(4.dp)
                                        .background(CorusColors.CardBackground)
                                } else {
                                    Modifier
                                }
                            )
                            .padding(horizontal = CorusSpacing.md, vertical = CorusSpacing.xs)
                            .semantics {
                                customActions = buildList {
                                    if (index > 0) {
                                        add(
                                            CustomAccessibilityAction(moveUpLabel) {
                                                onMove(tab, tabs[index - 1])
                                                true
                                            }
                                        )
                                    }
                                    if (index < tabs.lastIndex) {
                                        add(
                                            CustomAccessibilityAction(moveDownLabel) {
                                                onMove(tab, tabs[index + 1])
                                                true
                                            }
                                        )
                                    }
                                }
                            },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .draggable(
                                    state = rememberDraggableState { delta ->
                                        dragOffsetY += delta
                                        val height = rowHeightPx
                                        if (height <= 0f) return@rememberDraggableState
                                        val currentIndex = tabs.indexOf(tab)
                                        if (currentIndex < 0) return@rememberDraggableState
                                        if (dragOffsetY > height * 0.55f && currentIndex < tabs.lastIndex) {
                                            onMove(tab, tabs[currentIndex + 1])
                                            dragOffsetY -= height
                                        } else if (dragOffsetY < -height * 0.55f && currentIndex > 0) {
                                            onMove(tab, tabs[currentIndex - 1])
                                            dragOffsetY += height
                                        }
                                    },
                                    orientation = Orientation.Vertical,
                                    startDragImmediately = true,
                                    onDragStarted = { draggingTab = tab },
                                    onDragStopped = {
                                        draggingTab = null
                                        dragOffsetY = 0f
                                    },
                                ),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Menu,
                                contentDescription = stringResource(R.string.edit_profile_tab_reorder),
                                tint = CorusColors.Tertiary,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                        Text(
                            text = label,
                            style = CorusFont.body,
                            color = if (isHidden) CorusColors.Tertiary else CorusColors.Text,
                            modifier = Modifier
                                .weight(1f)
                                .padding(horizontal = CorusSpacing.sm),
                        )
                        IconButton(
                            onClick = { onToggleHidden(tab) },
                            enabled = tabCanHide || isHidden,
                            modifier = Modifier.size(40.dp),
                        ) {
                            Icon(
                                imageVector = if (isHidden) {
                                    Icons.Outlined.VisibilityOff
                                } else {
                                    Icons.Outlined.Visibility
                                },
                                contentDescription = stringResource(
                                    if (isHidden) R.string.edit_profile_tab_show
                                    else R.string.edit_profile_tab_hide,
                                    label,
                                ),
                                tint = if (isHidden || !tabCanHide) CorusColors.Tertiary else CorusColors.Text,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    }
                    if (index < tabs.lastIndex) {
                        HorizontalDivider(color = CorusColors.Divider)
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(CorusSpacing.xs))
        Text(
            stringResource(R.string.edit_profile_tabs_hint),
            style = CorusFont.caption,
            color = CorusColors.Tertiary,
        )
    }
}
