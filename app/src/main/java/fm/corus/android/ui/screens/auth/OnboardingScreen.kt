package fm.corus.android.ui.screens.auth

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel
import coil3.compose.AsyncImage
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.ByteArrayOutputStream
import java.net.URL
import fm.corus.android.R
import fm.corus.android.domain.DisplayNameValidator
import fm.corus.android.domain.UsernameValidator
import fm.corus.android.ui.components.AvatarCropView
import fm.corus.android.ui.components.SelfieCaptureScreen
import fm.corus.android.ui.components.uriToBitmap
import fm.corus.android.ui.theme.CorusColors
import fm.corus.android.ui.theme.CorusFont
import fm.corus.android.ui.theme.CorusSpacing
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@Composable
fun OnboardingScreen(
    viewModel: AuthViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val isLoading by viewModel.isLoading.collectAsState()
    val error by viewModel.error.collectAsState()

    var avatarUri by remember { mutableStateOf<Uri?>(null) }
    var avatarData by remember { mutableStateOf<ByteArray?>(null) }
    var cropBitmap by remember { mutableStateOf<Bitmap?>(null) }
    val decodeScope = rememberCoroutineScope()

    // Pre-fill display name from OAuth provider (matching iOS behavior).
    // Always show the Full Name field so the user can edit it — iOS only hides
    // it for Apple sign-in, and Android doesn't support Apple sign-in.
    val oauthDisplayName = viewModel.oauthDisplayName
    var displayName by remember { mutableStateOf(oauthDisplayName ?: "") }

    var username by remember { mutableStateOf("") }
    var usernameAvailable by remember { mutableStateOf<Boolean?>(null) }
    var checkingUsername by remember { mutableStateOf(false) }
    var usernameError by remember { mutableStateOf<String?>(null) }
    var showAvatarNudge by remember { mutableStateOf(false) }
    var showPhotoDialog by remember { mutableStateOf(false) }

    // Gallery picker — opens crop screen before storing.
    // Bitmap decode + EXIF rotation runs on IO dispatcher to keep the UI responsive
    // (decoding a multi-megapixel JPEG on the main thread causes visible lag).
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            decodeScope.launch {
                val bmp = withContext(Dispatchers.IO) { uriToBitmap(context, uri) }
                cropBitmap = bmp
            }
        }
    }

    // In-app selfie camera — Samsung One UI ignores the front-camera intent extras,
    // so we capture in-app via CameraX with LENS_FACING_FRONT forced.
    val cameraPhotoFile = remember { File(context.cacheDir, "onboarding_avatar.jpg") }
    val cameraPhotoUri = remember {
        FileProvider.getUriForFile(context, "${context.packageName}.provider", cameraPhotoFile)
    }
    var showSelfieCapture by remember { mutableStateOf(false) }

    // Pre-fill avatar from the OAuth provider's profile photo (matching iOS
    // OnboardingView.loadProviderPhoto). Runs once on entry; user can replace
    // or remove it via the photo dialog. Skip if the user has already chosen
    // a photo this session.
    val providerPhotoUrl = viewModel.providerPhotoUrl
    LaunchedEffect(providerPhotoUrl) {
        if (providerPhotoUrl == null || avatarData != null) return@LaunchedEffect
        val (bytes, uri) = withContext(Dispatchers.IO) {
            try {
                val raw = URL(providerPhotoUrl).openStream().use { it.readBytes() }
                val bmp = BitmapFactory.decodeByteArray(raw, 0, raw.size) ?: return@withContext null
                val baos = ByteArrayOutputStream()
                bmp.compress(Bitmap.CompressFormat.JPEG, 80, baos)
                val jpeg = baos.toByteArray()
                val file = File(context.cacheDir, "provider_avatar_${System.currentTimeMillis()}.jpg")
                file.writeBytes(jpeg)
                val u = FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
                jpeg to u
            } catch (_: Exception) {
                null
            }
        } ?: return@LaunchedEffect
        if (avatarData == null) {
            avatarData = bytes
            avatarUri = uri
        }
    }

    // Username availability check with debounce (min 1 char, matching iOS)
    LaunchedEffect(username) {
        when (val result = UsernameValidator.validate(username)) {
            is UsernameValidator.Result.Empty -> {
                usernameAvailable = null
                usernameError = null
                checkingUsername = false
                return@LaunchedEffect
            }
            is UsernameValidator.Result.Invalid -> {
                usernameAvailable = false
                usernameError = result.message
                checkingUsername = false
                return@LaunchedEffect
            }
            UsernameValidator.Result.Valid -> {
                usernameError = null
            }
        }
        checkingUsername = true
        delay(400)
        try {
            val available = viewModel.checkUsernameAvailable(username)
            usernameAvailable = available
        } catch (_: Exception) {
            usernameAvailable = null
        }
        checkingUsername = false
    }

    val canSubmit = usernameAvailable == true
            && DisplayNameValidator.hasVisibleCharacter(displayName)
            && !isLoading

    val scrollState = rememberScrollState()

    // Detect keyboard visibility via IME insets
    val density = LocalDensity.current
    val imeBottom = WindowInsets.ime.getBottom(density)
    val keyboardVisible = imeBottom > 0

    // Scroll to bottom when keyboard appears so Continue button is visible.
    // Wait for layout to recompose with imePadding before reading maxValue.
    LaunchedEffect(keyboardVisible) {
        if (keyboardVisible) {
            delay(100)
            scrollState.animateScrollTo(scrollState.maxValue)
        }
    }

    // System back button / gesture mirrors the on-screen back arrow.
    // Without this, back is a no-op on this screen since it's the root of the auth graph.
    BackHandler { viewModel.signOut() }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .navigationBarsPadding()
                .imePadding()
                .verticalScroll(scrollState),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Title section — matches iOS: "Welcome" + "set up your profile"
            Column(
                modifier = Modifier.padding(top = 80.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = stringResource(id = R.string.onboarding_welcome),
                    style = CorusFont.custom(900, 28),
                    color = CorusColors.Text,
                )

                Spacer(modifier = Modifier.height(CorusSpacing.sm))

                Text(
                    text = stringResource(id = R.string.onboarding_subtitle),
                    style = CorusFont.bodyMedium,
                    color = CorusColors.Secondary,
                )
            }

            // Avatar section — matches iOS: 100dp circle + camera badge + "Add photo" label
            Spacer(modifier = Modifier.height(CorusSpacing.xxxl))

            Box(
                modifier = Modifier.clickable {
                    focusManager.clearFocus()
                    keyboardController?.hide()
                    showPhotoDialog = true
                },
            ) {
                Box(contentAlignment = Alignment.BottomEnd) {
                    if (avatarUri != null) {
                        AsyncImage(
                            model = avatarUri,
                            contentDescription = stringResource(id = R.string.onboarding_cd_avatar),
                            modifier = Modifier
                                .size(100.dp)
                                .clip(CircleShape),
                            contentScale = ContentScale.Crop,
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .size(100.dp)
                                .clip(CircleShape)
                                .background(CorusColors.CardBackground)
                                .border(1.dp, CorusColors.Divider, CircleShape),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                Icons.Filled.Person,
                                contentDescription = stringResource(id = R.string.onboarding_add_photo),
                                modifier = Modifier.size(40.dp),
                                tint = CorusColors.Tertiary,
                            )
                        }
                    }

                    // Camera badge — matches iOS: 32dp accent circle with camera icon
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(CorusColors.Accent),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.Filled.CameraAlt,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = Color.White,
                        )
                    }
                }
            }

            if (avatarUri == null) {
                Spacer(modifier = Modifier.height(CorusSpacing.sm))
                Text(
                    text = stringResource(id = R.string.onboarding_add_photo),
                    style = CorusFont.captionMedium,
                    color = CorusColors.Accent,
                )
            }

            // Name field — only shown if OAuth didn't provide a name (matching iOS)
            Spacer(modifier = Modifier.height(CorusSpacing.xxxl))

            Column(
                modifier = Modifier.padding(horizontal = CorusSpacing.xxl),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            CorusColors.CardBackground,
                            RoundedCornerShape(CorusSpacing.cornerRadiusMedium),
                        )
                        .border(
                            1.dp,
                            CorusColors.Divider,
                            RoundedCornerShape(CorusSpacing.cornerRadiusMedium),
                        )
                        .padding(CorusSpacing.md),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Filled.Person,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                        tint = CorusColors.Tertiary,
                    )
                    Spacer(modifier = Modifier.width(CorusSpacing.md))
                    Box(modifier = Modifier.weight(1f)) {
                        if (displayName.isEmpty()) {
                            Text(
                                stringResource(id = R.string.onboarding_full_name),
                                style = CorusFont.body,
                                color = CorusColors.Tertiary,
                            )
                        }
                        BasicTextField(
                            value = displayName,
                            onValueChange = { displayName = it.take(30) },
                            textStyle = CorusFont.body.copy(color = CorusColors.Text),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }

            // Username field spacing
            Spacer(modifier = Modifier.height(CorusSpacing.lg))

            // Username field — matches iOS: @ icon + text field + status indicator
            Column(
                modifier = Modifier.padding(horizontal = CorusSpacing.xxl),
            ) {
                val borderColor = when {
                    usernameAvailable == true -> CorusColors.Verified
                    usernameAvailable == false -> CorusColors.Error
                    else -> CorusColors.Divider
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            CorusColors.CardBackground,
                            RoundedCornerShape(CorusSpacing.cornerRadiusMedium),
                        )
                        .border(
                            1.dp,
                            borderColor,
                            RoundedCornerShape(CorusSpacing.cornerRadiusMedium),
                        )
                        .padding(CorusSpacing.md),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "@",
                        style = CorusFont.body.copy(fontSize = 15.sp),
                        color = CorusColors.Tertiary,
                        modifier = Modifier.width(20.dp),
                    )
                    Spacer(modifier = Modifier.width(CorusSpacing.md))
                    Box(modifier = Modifier.weight(1f)) {
                        if (username.isEmpty()) {
                            Text(
                                stringResource(id = R.string.change_username_field_label),
                                style = CorusFont.body,
                                color = CorusColors.Tertiary,
                            )
                        }
                        BasicTextField(
                            value = username,
                            onValueChange = { value ->
                                username = UsernameValidator.clean(value)
                            },
                            textStyle = CorusFont.body.copy(color = CorusColors.Text),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }

                    // Status indicator — matches iOS
                    if (checkingUsername) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            color = CorusColors.Accent,
                            strokeWidth = 1.5.dp,
                        )
                    } else if (usernameAvailable == true) {
                        Icon(
                            Icons.Filled.CheckCircle,
                            contentDescription = null,
                            tint = CorusColors.Verified,
                            modifier = Modifier.size(18.dp),
                        )
                    } else if (usernameAvailable == false) {
                        Icon(
                            Icons.Filled.Cancel,
                            contentDescription = null,
                            tint = CorusColors.Error,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }

                // Validation messages — matches iOS
                Spacer(modifier = Modifier.height(CorusSpacing.sm))
                when {
                    usernameError != null -> {
                        Text(
                            usernameError!!,
                            style = CorusFont.caption,
                            color = CorusColors.Error,
                        )
                    }
                    usernameAvailable == false -> {
                        Text(
                            stringResource(id = R.string.change_username_status_taken),
                            style = CorusFont.caption,
                            color = CorusColors.Error,
                        )
                    }
                }
            }

            // Error message
            if (error != null) {
                Spacer(modifier = Modifier.height(CorusSpacing.md))
                Text(
                    text = error ?: "",
                    style = CorusFont.caption,
                    color = CorusColors.Error,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = CorusSpacing.xxl),
                )
            }

            // Collapse spacer when keyboard is up so the button stays visible
            if (!keyboardVisible) {
                Spacer(modifier = Modifier.weight(1f))
            }

            // Terms of Use — fade out when keyboard is up (matching iOS)
            AnimatedVisibility(
                visible = !keyboardVisible,
                enter = fadeIn(),
                exit = fadeOut(),
            ) {
                Text(
                    text = stringResource(id = R.string.onboarding_terms),
                    style = CorusFont.caption,
                    color = CorusColors.Tertiary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .padding(horizontal = CorusSpacing.xxl)
                        .padding(bottom = CorusSpacing.sm),
                )
            }

            // Continue button — matches iOS: full-width accent capsule
            Button(
                onClick = {
                    viewModel.analyticsService.logOnboardingProfileSubmitted()
                    if (avatarUri == null && !showAvatarNudge) {
                        showAvatarNudge = true
                    } else {
                        viewModel.completeOnboarding(username, displayName, avatarData)
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = CorusSpacing.xxl)
                    .padding(top = CorusSpacing.lg)
                    .padding(bottom = CorusSpacing.md),
                enabled = canSubmit,
                shape = RoundedCornerShape(CorusSpacing.cornerRadiusMedium),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (canSubmit) CorusColors.Accent else CorusColors.Accent.copy(alpha = 0.4f),
                    disabledContainerColor = CorusColors.Accent.copy(alpha = 0.4f),
                ),
                contentPadding = PaddingValues(vertical = CorusSpacing.lg),
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = Color.White,
                        strokeWidth = 2.dp,
                    )
                } else {
                    Text(stringResource(id = R.string.onboarding_button_continue), style = CorusFont.button, color = Color.White)
                }
            }
        }

        // Back button rendered last so it sits on top of the scrollable Column and receives taps.
        IconButton(
            onClick = { viewModel.signOut() },
            modifier = Modifier
                .statusBarsPadding()
                .padding(start = CorusSpacing.sm, top = CorusSpacing.sm)
                .align(Alignment.TopStart),
        ) {
            Icon(
                Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = stringResource(id = R.string.common_back),
                tint = CorusColors.Text,
            )
        }
    }

    if (showSelfieCapture) {
        SelfieCaptureScreen(
            outputFile = cameraPhotoFile,
            onCaptured = {
                showSelfieCapture = false
                decodeScope.launch {
                    val bmp = withContext(Dispatchers.IO) { uriToBitmap(context, cameraPhotoUri) }
                    cropBitmap = bmp
                }
            },
            onCancel = { showSelfieCapture = false },
        )
    }

    // Photo source dialog — matches iOS action sheet: Take Photo / Choose from Library / Remove Photo
    if (showPhotoDialog) {
        AlertDialog(
            onDismissRequest = { showPhotoDialog = false },
            title = { Text(stringResource(id = R.string.onboarding_dialog_photo_title), style = CorusFont.songTitleLarge) },
            text = {
                Column {
                    TextButton(
                        onClick = {
                            showPhotoDialog = false
                            showSelfieCapture = true
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(stringResource(id = R.string.profile_avatar_take_photo), style = CorusFont.body, color = CorusColors.Text)
                    }
                    TextButton(
                        onClick = {
                            showPhotoDialog = false
                            photoPickerLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(stringResource(id = R.string.profile_avatar_choose_library), style = CorusFont.body, color = CorusColors.Text)
                    }
                    if (avatarUri != null) {
                        TextButton(
                            onClick = {
                                showPhotoDialog = false
                                avatarUri = null
                                avatarData = null
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(stringResource(id = R.string.onboarding_remove_photo), style = CorusFont.body, color = CorusColors.Error)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showPhotoDialog = false }) {
                    Text(stringResource(id = R.string.common_cancel), color = CorusColors.Secondary)
                }
            },
        )
    }

    // Avatar nudge dialog — matches iOS alert
    if (showAvatarNudge) {
        AlertDialog(
            onDismissRequest = { showAvatarNudge = false },
            title = { Text(stringResource(id = R.string.onboarding_avatar_nudge_title), style = CorusFont.songTitleLarge) },
            text = { Text(stringResource(id = R.string.onboarding_avatar_nudge_message), style = CorusFont.body) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.analyticsService.logOnboardingAvatarNudge("add_photo")
                    showAvatarNudge = false
                    showPhotoDialog = true
                }) {
                    Text(stringResource(id = R.string.onboarding_add_photo_button), color = CorusColors.Accent)
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    viewModel.analyticsService.logOnboardingAvatarNudge("skip")
                    showAvatarNudge = false
                    viewModel.completeOnboarding(username, displayName, avatarData)
                }) {
                    Text(stringResource(id = R.string.onboarding_skip), color = CorusColors.Secondary)
                }
            },
        )
    }

    // ── Avatar Crop Overlay ──
    cropBitmap?.let { bitmap ->
        AvatarCropView(
            bitmap = bitmap,
            onConfirm = { croppedBytes ->
                cropBitmap = null
                avatarData = croppedBytes
                // Unique filename per crop — reusing one path made Coil serve the previous
                // image from its URI-keyed cache even after the file was overwritten.
                val tempFile = File(context.cacheDir, "cropped_avatar_${System.currentTimeMillis()}.jpg")
                tempFile.writeBytes(croppedBytes)
                avatarUri = FileProvider.getUriForFile(
                    context, "${context.packageName}.provider", tempFile
                )
            },
            onCancel = { cropBitmap = null },
        )
    }
}
