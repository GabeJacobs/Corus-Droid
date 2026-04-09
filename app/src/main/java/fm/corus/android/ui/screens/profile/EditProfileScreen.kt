package fm.corus.android.ui.screens.profile

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import fm.corus.android.ui.theme.CorusColors
import fm.corus.android.ui.theme.CorusFont
import fm.corus.android.ui.theme.CorusSpacing

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditProfileScreen(
    viewModel: EditProfileViewModel = hiltViewModel(),
    onBack: () -> Unit = {},
    onNavigateToClub: () -> Unit = {},
) {
    val context = LocalContext.current

    val displayName by viewModel.displayName.collectAsState()
    val username by viewModel.username.collectAsState()
    val bio by viewModel.bio.collectAsState()
    val website by viewModel.website.collectAsState()
    val usernameState by viewModel.usernameState.collectAsState()
    val isSaving by viewModel.isSaving.collectAsState()
    val saveError by viewModel.saveError.collectAsState()

    // Style picker state
    val styleSelections by viewModel.styleSelections.collectAsState()
    val latestTrackPost by viewModel.latestTrackPost.collectAsState()
    val latestMoviePost by viewModel.latestMoviePost.collectAsState()
    val hasTrackPosts by viewModel.hasTrackPosts.collectAsState()
    val hasMoviePosts by viewModel.hasMoviePosts.collectAsState()
    val isStyleSaving by viewModel.isStyleSaving.collectAsState()
    val isClubMember by viewModel.subscriptionRepository.isClubMember.collectAsState()

    var showStylePicker by remember { mutableStateOf(false) }
    var showDiscardDialog by remember { mutableStateOf(false) }

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
            title = { Text("Error", style = CorusFont.songTitle, color = CorusColors.Text) },
            text = { Text(saveError!!, style = CorusFont.body, color = CorusColors.Text) },
            confirmButton = {
                TextButton(onClick = { viewModel.clearSaveError() }) {
                    Text("OK", style = CorusFont.button, color = CorusColors.Accent)
                }
            },
            containerColor = CorusColors.Background,
        )
    }

    // Discard changes dialog
    if (showDiscardDialog) {
        AlertDialog(
            onDismissRequest = { showDiscardDialog = false },
            title = { Text("Discard Changes?", style = CorusFont.songTitle, color = CorusColors.Text) },
            text = { Text("You have unsaved changes that will be lost.", style = CorusFont.body, color = CorusColors.Text) },
            confirmButton = {
                TextButton(onClick = {
                    showDiscardDialog = false
                    onBack()
                }) {
                    Text("Discard", style = CorusFont.button, color = CorusColors.Error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDiscardDialog = false }) {
                    Text("Keep Editing", style = CorusFont.button, color = CorusColors.Accent)
                }
            },
            containerColor = CorusColors.Background,
        )
    }

    // Style picker bottom sheet
    if (showStylePicker) {
        ModalBottomSheet(
            onDismissRequest = { showStylePicker = false },
            containerColor = CorusColors.Background,
        ) {
            StylePickerSheet(
                currentSelections = styleSelections,
                username = username,
                latestTrackPost = latestTrackPost,
                latestMoviePost = latestMoviePost,
                hasTrackPosts = hasTrackPosts,
                hasMoviePosts = hasMoviePosts,
                isClubMember = isClubMember,
                isSaving = isStyleSaving,
                onSave = { selections ->
                    viewModel.saveStyleSelections(selections) {
                        showStylePicker = false
                    }
                },
                onNavigateToClub = {
                    showStylePicker = false
                    onNavigateToClub()
                },
                onDismiss = { showStylePicker = false },
            )
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Edit Profile", style = CorusFont.screenTitle, color = CorusColors.Text) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.Close, contentDescription = "Cancel", tint = CorusColors.Text)
                    }
                },
                actions = {
                    TextButton(
                        onClick = { viewModel.save(onSuccess = onBack) },
                        enabled = viewModel.canSave,
                    ) {
                        if (isSaving) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = CorusColors.Accent)
                        } else {
                            Text(
                                "Save",
                                style = CorusFont.button,
                                color = if (viewModel.canSave) CorusColors.Accent else CorusColors.Tertiary,
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White),
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = CorusSpacing.xxl, vertical = CorusSpacing.md),
            verticalArrangement = Arrangement.spacedBy(CorusSpacing.xxl),
        ) {
            // Name field
            EditField(
                label = "NAME",
                value = displayName,
                onValueChange = { viewModel.updateDisplayName(it) },
                singleLine = true,
            )

            // Username field
            Column {
                Text("USERNAME", style = CorusFont.sectionHeader, color = CorusColors.Secondary)
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
                                Icon(Icons.Filled.Check, contentDescription = "Available", tint = CorusColors.Verified)
                            }
                            EditProfileViewModel.UsernameState.TAKEN -> {
                                Icon(Icons.Filled.Close, contentDescription = "Taken", tint = CorusColors.Error)
                            }
                            EditProfileViewModel.UsernameState.INVALID -> {
                                Icon(Icons.Filled.Close, contentDescription = "Invalid", tint = CorusColors.Error)
                            }
                            else -> {}
                        }
                    },
                )

                when (usernameState) {
                    EditProfileViewModel.UsernameState.TAKEN -> {
                        Spacer(modifier = Modifier.height(CorusSpacing.xs))
                        Text("Username is taken", style = CorusFont.caption, color = CorusColors.Error)
                    }
                    EditProfileViewModel.UsernameState.INVALID -> {
                        Spacer(modifier = Modifier.height(CorusSpacing.xs))
                        Text("Letters, numbers, underscores, periods only", style = CorusFont.caption, color = CorusColors.Error)
                    }
                    else -> {}
                }
            }

            // Bio field
            EditField(
                label = "BIO",
                value = bio,
                onValueChange = { viewModel.updateBio(it) },
                singleLine = false,
                minLines = 3,
                maxLines = 6,
            )

            // Website field
            EditField(
                label = "WEBSITE",
                value = website,
                onValueChange = { viewModel.updateWebsite(it) },
                singleLine = true,
            )

            // Customize Profile button
            Button(
                onClick = { showStylePicker = true },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                shape = RoundedCornerShape(CorusSpacing.cornerRadiusMedium),
                colors = ButtonDefaults.buttonColors(
                    containerColor = CorusColors.CardBackground,
                    contentColor = CorusColors.Text,
                ),
                border = androidx.compose.foundation.BorderStroke(1.dp, CorusColors.Divider),
            ) {
                Text("Customize Profile", style = CorusFont.button, color = CorusColors.Text)
            }

            // Share Profile Link button
            Button(
                onClick = {
                    val shareText = "Check out my profile on Corus: https://corus.fm/user/$username"
                    val intent = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, shareText)
                    }
                    context.startActivity(Intent.createChooser(intent, "Share Profile"))
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                shape = RoundedCornerShape(CorusSpacing.cornerRadiusMedium),
                colors = ButtonDefaults.buttonColors(
                    containerColor = CorusColors.CardBackground,
                    contentColor = CorusColors.Text,
                ),
                border = androidx.compose.foundation.BorderStroke(1.dp, CorusColors.Divider),
            ) {
                Text("Share Profile Link", style = CorusFont.button, color = CorusColors.Text)
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
