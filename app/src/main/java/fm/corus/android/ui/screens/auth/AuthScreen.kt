package fm.corus.android.ui.screens.auth

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.auth.api.signin.GoogleSignInStatusCodes
import com.google.android.gms.common.api.ApiException
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.filled.KeyboardArrowDown
import kotlinx.coroutines.delay
import fm.corus.android.R
import fm.corus.android.domain.HapticManager
import fm.corus.android.ui.LocalHapticManager
import fm.corus.android.ui.screens.settings.CountryCode
import fm.corus.android.ui.theme.CorusColors
import fm.corus.android.ui.theme.CorusFont
import fm.corus.android.ui.theme.CorusSpacing

@Composable
fun AuthScreen(
    viewModel: AuthViewModel = hiltViewModel(),
) {
    val isLoading by viewModel.isLoading.collectAsState()
    val busyProvider by viewModel.busyProvider.collectAsState()
    val error by viewModel.error.collectAsState()
    val verificationSent by viewModel.verificationSent.collectAsState()
    val context = LocalContext.current
    val activity = context as Activity

    var phoneNumber by remember { mutableStateOf("") }
    var emailAddress by remember { mutableStateOf("") }
    var verificationCode by remember { mutableStateOf("") }
    var showPhoneInput by remember { mutableStateOf(false) }
    var showEmailInput by remember { mutableStateOf(false) }
    var selectedCountry by remember { mutableStateOf(CountryCode.US) }
    // Google Sign-In launcher
    val googleSignInLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        try {
            val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
            val account = task.getResult(ApiException::class.java)
            val idToken = account.idToken
            if (idToken != null) {
                viewModel.signInWithGoogle(idToken)
            } else {
                viewModel.setError(context.getString(R.string.auth_google_signin_error))
            }
        } catch (e: ApiException) {
            android.util.Log.e("AuthScreen", "Google sign-in failed: status=${e.statusCode}", e)
            if (e.statusCode == GoogleSignInStatusCodes.SIGN_IN_CANCELLED) {
                viewModel.setBusyProvider(null)
            } else {
                viewModel.setError(context.getString(R.string.auth_google_signin_error))
            }
        }
    }

    val showPhoneFlow = showPhoneInput || (verificationSent && !showEmailInput)
    val showEmailFlow = showEmailInput || (verificationSent && showEmailInput)
    val showSecondaryFlow = showPhoneFlow || showEmailFlow

    BackHandler(enabled = showSecondaryFlow) {
        if (verificationSent) {
            verificationCode = ""
            viewModel.resetVerification()
        }
        showPhoneInput = false
        showEmailInput = false
    }

    AnimatedContent(
        targetState = when {
            showEmailFlow -> "email"
            showPhoneFlow -> "phone"
            else -> "main"
        },
        transitionSpec = {
            if (targetState != "main") {
                slideInHorizontally(tween(400), initialOffsetX = { it }) togetherWith
                    slideOutHorizontally(tween(400), targetOffsetX = { -it / 3 })
            } else {
                slideInHorizontally(tween(400), initialOffsetX = { -it / 3 }) togetherWith
                    slideOutHorizontally(tween(400), targetOffsetX = { it })
            }
        },
        label = "AuthScreenTransition",
    ) { flow ->
        when (flow) {
            "phone" -> PhoneAuthContent(
                phoneNumber = phoneNumber,
                onPhoneNumberChange = { phoneNumber = it.filter { c -> c.isDigit() }.take(15) },
                selectedCountry = selectedCountry,
                onCountrySelected = { selectedCountry = it },
                verificationCode = verificationCode,
                onVerificationCodeChange = { verificationCode = it.filter { c -> c.isDigit() }.take(6) },
                verificationSent = verificationSent,
                isLoading = isLoading,
                error = error,
                onSendCode = { viewModel.sendVerificationCode(phoneNumber, selectedCountry.dialCode, activity) },
                onVerifyCode = { viewModel.verifyCode(verificationCode) },
                onBack = {
                    if (verificationSent) {
                        verificationCode = ""
                        viewModel.resetVerification()
                    }
                    showPhoneInput = false
                },
                onUseDifferentNumber = {
                    verificationCode = ""
                    viewModel.resetVerification()
                },
            )
            "email" -> EmailAuthContent(
                email = emailAddress,
                onEmailChange = { emailAddress = it.trim() },
                verificationCode = verificationCode,
                onVerificationCodeChange = { verificationCode = it.filter { c -> c.isDigit() }.take(6) },
                verificationSent = verificationSent,
                isLoading = isLoading,
                error = error,
                onSendCode = { viewModel.sendEmailOtpCode(emailAddress) },
                onVerifyCode = { viewModel.verifyEmailOtpCode(verificationCode) },
                onBack = {
                    if (verificationSent) {
                        verificationCode = ""
                        viewModel.resetVerification()
                    }
                    showEmailInput = false
                },
                onUseDifferentEmail = {
                    verificationCode = ""
                    viewModel.resetVerification()
                },
            )
            else -> {
            // Main auth screen — matches iOS AuthView layout exactly
            Column(
            modifier = Modifier
                .fillMaxSize()
                .background(CorusColors.Background)
                .padding(horizontal = CorusSpacing.xxl),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(modifier = Modifier.weight(1f))

            // Logo + Title + Tagline (matches iOS VStack with .xs spacing)
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                // Logo image — 90dp frame, tinted with cymbalText color (matching iOS)
                Image(
                    painter = painterResource(id = R.drawable.logo_no_background),
                    contentDescription = stringResource(id = R.string.auth_cd_corus_logo),
                    modifier = Modifier.size(90.dp),
                    colorFilter = ColorFilter.tint(CorusColors.Text),
                )

                Spacer(modifier = Modifier.height(CorusSpacing.xs))

                // "corus" — logoLarge: Nunito Black 40sp
                Text(
                    text = "corus",
                    style = CorusFont.logoLarge,
                    color = CorusColors.Text,
                )

                Spacer(modifier = Modifier.height(CorusSpacing.xs))

                // Tagline — bodyMedium: Nunito Medium 15sp
                Text(
                    text = stringResource(id = R.string.auth_tagline),
                    style = CorusFont.bodyMedium,
                    color = CorusColors.Secondary,
                )
            }

            Spacer(modifier = Modifier.weight(1f))

            // Error message
            if (error != null) {
                Text(
                    text = error ?: "",
                    style = CorusFont.caption,
                    color = CorusColors.Error,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(bottom = CorusSpacing.lg),
                )
            }

            // Auth buttons — matches iOS: VStack(spacing: .md), horizontal padding .xxl
            // iOS uses .xxl (24pt) on each side, and the VStack already has .xxl padding
            // So buttons are inset 24pt from screen edges total
            Column(
                verticalArrangement = Arrangement.spacedBy(CorusSpacing.md),
            ) {
                val haptics = LocalHapticManager.current
                // Google Sign-In button
                AuthButton(
                    text = stringResource(id = R.string.auth_button_google),
                    icon = {
                        Image(
                            painter = painterResource(id = R.drawable.google_logo),
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                    },
                    isLoading = busyProvider == "google",
                    onClick = {
                        // Mirrors iOS AuthView.signInWithGoogle / signInWithApple haptic.
                        haptics.impact(HapticManager.ImpactStyle.LIGHT)
                        viewModel.setBusyProvider("google")
                        val webClientId = context.getString(R.string.default_web_client_id)
                        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                            .requestIdToken(webClientId)
                            .requestEmail()
                            .build()
                        val client = GoogleSignIn.getClient(context, gso)
                        googleSignInLauncher.launch(client.signInIntent)
                    },
                )

                // Apple Sign-In button
                AuthButton(
                    text = stringResource(id = R.string.auth_button_apple),
                    icon = {
                        Icon(
                            painter = painterResource(id = R.drawable.apple_logo),
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                            tint = CorusColors.Text,
                        )
                    },
                    isLoading = busyProvider == "apple",
                    onClick = {
                        haptics.impact(HapticManager.ImpactStyle.LIGHT)
                        viewModel.signInWithApple(activity)
                    },
                )

                // Phone button
                AuthButton(
                    text = stringResource(id = R.string.auth_button_phone),
                    icon = {
                        Icon(
                            Icons.Filled.Phone,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                            tint = CorusColors.Text,
                        )
                    },
                    isLoading = false,
                    onClick = {
                        // Mirrors iOS AuthView button-tap haptic (Apple/Google).
                        haptics.impact(HapticManager.ImpactStyle.LIGHT)
                        showPhoneInput = true
                    },
                )

                // Always shown (no client RC gate) so auth paints without a
                // late pop-in. Server `email_otp_auth_enabled` remains the kill
                // switch — same approach as web.
                AuthButton(
                    text = stringResource(id = R.string.auth_button_email),
                    icon = {
                        Icon(
                            Icons.Filled.Email,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                            tint = CorusColors.Text,
                        )
                    },
                    isLoading = false,
                    onClick = {
                        haptics.impact(HapticManager.ImpactStyle.LIGHT)
                        showEmailInput = true
                    },
                )
            }

            Spacer(modifier = Modifier.weight(1f))
            }
        }
        }
    }
}

/**
 * Auth button matching iOS style exactly:
 * - Full-width
 * - cymbalCardBackground fill (#F8F8FA)
 * - 1dp cymbalDivider border (#EEEEEF)
 * - cornerRadiusMedium (12dp)
 * - vertical padding: lg (16dp)
 * - Icon 18dp + md (12dp) spacing + text in button font (ExtraBold 14sp)
 */
@Composable
private fun AuthButton(
    text: String,
    icon: @Composable () -> Unit,
    isLoading: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        enabled = !isLoading,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(CorusSpacing.cornerRadiusMedium),
        color = CorusColors.CardBackground,
        border = BorderStroke(1.dp, CorusColors.Divider),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = CorusSpacing.lg),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    color = CorusColors.Text,
                    strokeWidth = 2.dp,
                )
            } else {
                icon()
                Spacer(modifier = Modifier.width(CorusSpacing.md))
                Text(
                    text = text,
                    style = CorusFont.button,
                    color = CorusColors.Text,
                )
            }
        }
    }
}

/**
 * Email OTP auth flow — matches onboarding title/subtitle + phone OTP chrome.
 */
@Composable
private fun EmailAuthContent(
    email: String,
    onEmailChange: (String) -> Unit,
    verificationCode: String,
    onVerificationCodeChange: (String) -> Unit,
    verificationSent: Boolean,
    isLoading: Boolean,
    error: String?,
    onSendCode: () -> Unit,
    onVerifyCode: () -> Unit,
    onBack: () -> Unit,
    onUseDifferentEmail: () -> Unit = {},
) {
    var resendCooldown by remember { mutableIntStateOf(0) }
    LaunchedEffect(verificationSent) {
        if (verificationSent) {
            resendCooldown = 60
            while (resendCooldown > 0) {
                delay(1000)
                resendCooldown--
            }
        }
    }
    val emailLooksValid = email.contains("@") && email.contains(".")

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(CorusColors.Background)
            .imePadding(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(CorusSpacing.xxl)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            if (verificationSent) {
                Text(
                    text = stringResource(id = R.string.auth_email_check_title),
                    style = CorusFont.custom(900, 28),
                    color = CorusColors.Text,
                )
                Spacer(modifier = Modifier.height(CorusSpacing.sm))
                Text(
                    text = stringResource(id = R.string.auth_email_code_sent_format, email),
                    style = CorusFont.bodyMedium,
                    color = CorusColors.Secondary,
                    textAlign = TextAlign.Center,
                )
            } else {
                Text(
                    text = "corus",
                    style = CorusFont.logoLarge,
                    color = CorusColors.Text,
                )
            }

            Spacer(modifier = Modifier.height(CorusSpacing.xxxl))

            if (error != null) {
                Text(
                    text = error,
                    style = CorusFont.caption,
                    color = CorusColors.Error,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(bottom = CorusSpacing.lg),
                )
            }

            if (!verificationSent) {
                Text(
                    text = stringResource(id = R.string.auth_email_prompt),
                    style = CorusFont.bodyMedium,
                    color = CorusColors.Text,
                )
                Spacer(modifier = Modifier.height(CorusSpacing.lg))
                OutlinedTextField(
                    value = email,
                    onValueChange = onEmailChange,
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text(stringResource(id = R.string.auth_email_placeholder)) },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Email,
                        imeAction = ImeAction.Done,
                    ),
                    keyboardActions = KeyboardActions(
                        onDone = { if (emailLooksValid) onSendCode() }
                    ),
                    singleLine = true,
                    shape = RoundedCornerShape(CorusSpacing.cornerRadiusMedium),
                )
                Spacer(modifier = Modifier.height(CorusSpacing.lg))
                Button(
                    onClick = onSendCode,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    enabled = emailLooksValid && !isLoading,
                    shape = RoundedCornerShape(CorusSpacing.cornerRadiusMedium),
                    colors = ButtonDefaults.buttonColors(containerColor = CorusColors.Accent),
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = Color.White,
                            strokeWidth = 2.dp,
                        )
                    } else {
                        Text(stringResource(id = R.string.auth_button_send_code), style = CorusFont.button, color = Color.White)
                    }
                }
            } else {
                OutlinedTextField(
                    value = verificationCode,
                    onValueChange = onVerificationCodeChange,
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text(stringResource(id = R.string.auth_code_placeholder)) },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Number,
                        imeAction = ImeAction.Done,
                    ),
                    keyboardActions = KeyboardActions(
                        onDone = { if (verificationCode.length == 6) onVerifyCode() }
                    ),
                    singleLine = true,
                    shape = RoundedCornerShape(CorusSpacing.cornerRadiusMedium),
                )
                Spacer(modifier = Modifier.height(CorusSpacing.lg))
                Button(
                    onClick = onVerifyCode,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    enabled = verificationCode.length == 6 && !isLoading,
                    shape = RoundedCornerShape(CorusSpacing.cornerRadiusMedium),
                    colors = ButtonDefaults.buttonColors(containerColor = CorusColors.Accent),
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = Color.White,
                            strokeWidth = 2.dp,
                        )
                    } else {
                        Text(stringResource(id = R.string.auth_button_verify), style = CorusFont.button, color = Color.White)
                    }
                }
                Spacer(modifier = Modifier.height(CorusSpacing.md))
                TextButton(
                    onClick = {
                        resendCooldown = 60
                        onSendCode()
                    },
                    enabled = resendCooldown == 0 && !isLoading,
                ) {
                    Text(
                        if (resendCooldown > 0) {
                            stringResource(id = R.string.change_phone_resend_in_format, resendCooldown)
                        } else {
                            stringResource(id = R.string.change_phone_resend_code)
                        },
                        style = CorusFont.captionMedium,
                        color = if (resendCooldown > 0) CorusColors.Tertiary else CorusColors.Accent,
                    )
                }
                TextButton(onClick = onUseDifferentEmail) {
                    Text(
                        stringResource(id = R.string.auth_email_use_different),
                        style = CorusFont.captionMedium,
                        color = CorusColors.Accent,
                    )
                }
            }
        }

        IconButton(
            onClick = onBack,
            modifier = Modifier
                .align(Alignment.TopStart)
                .statusBarsPadding()
                .padding(CorusSpacing.xs),
        ) {
            Icon(
                Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = stringResource(id = R.string.common_back),
                tint = CorusColors.Text,
            )
        }
    }
}

/**
 * Phone auth input flow (separate screen-like view)
 */
@Composable
private fun PhoneAuthContent(
    phoneNumber: String,
    onPhoneNumberChange: (String) -> Unit,
    selectedCountry: CountryCode,
    onCountrySelected: (CountryCode) -> Unit,
    verificationCode: String,
    onVerificationCodeChange: (String) -> Unit,
    verificationSent: Boolean,
    isLoading: Boolean,
    error: String?,
    onSendCode: () -> Unit,
    onVerifyCode: () -> Unit,
    onBack: () -> Unit,
    onUseDifferentNumber: () -> Unit = {},
) {
    // Resend cooldown timer — matches iOS behavior
    var resendCooldown by remember { mutableIntStateOf(0) }
    LaunchedEffect(verificationSent) {
        if (verificationSent) {
            resendCooldown = 30
            while (resendCooldown > 0) {
                kotlinx.coroutines.delay(1000)
                resendCooldown--
            }
        }
    }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(CorusColors.Background)
            .imePadding(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(CorusSpacing.xxl)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
        Text(
            text = "corus",
            style = CorusFont.logoLarge,
            color = CorusColors.Text,
        )

        Spacer(modifier = Modifier.height(CorusSpacing.xxxl))

        var showCountryPicker by remember { mutableStateOf(false) }
        val isValidPhone = phoneNumber.length in 4..15

        if (!verificationSent) {
            Text(
                text = stringResource(id = R.string.auth_phone_prompt),
                style = CorusFont.bodyMedium,
                color = CorusColors.Text,
            )

            Spacer(modifier = Modifier.height(CorusSpacing.lg))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(CorusSpacing.sm),
            ) {
                OutlinedButton(
                    onClick = { showCountryPicker = true },
                    shape = RoundedCornerShape(CorusSpacing.cornerRadiusMedium),
                    modifier = Modifier.height(56.dp),
                    contentPadding = PaddingValues(horizontal = CorusSpacing.md),
                ) {
                    Text(selectedCountry.flag, style = CorusFont.body)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(selectedCountry.dialCode, style = CorusFont.body, color = CorusColors.Tertiary)
                    Icon(
                        Icons.Filled.KeyboardArrowDown,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = CorusColors.Tertiary,
                    )
                }

                OutlinedTextField(
                    value = phoneNumber,
                    onValueChange = onPhoneNumberChange,
                    modifier = Modifier.weight(1f),
                    placeholder = { Text(stringResource(id = R.string.change_phone_placeholder)) },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Phone,
                        imeAction = ImeAction.Done,
                    ),
                    keyboardActions = KeyboardActions(
                        onDone = { if (isValidPhone) onSendCode() }
                    ),
                    singleLine = true,
                    shape = RoundedCornerShape(CorusSpacing.cornerRadiusMedium),
                )
            }

            Spacer(modifier = Modifier.height(CorusSpacing.lg))

            Button(
                onClick = onSendCode,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                enabled = isValidPhone && !isLoading,
                shape = RoundedCornerShape(CorusSpacing.cornerRadiusMedium),
                colors = ButtonDefaults.buttonColors(containerColor = CorusColors.Accent),
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = Color.White,
                        strokeWidth = 2.dp,
                    )
                } else {
                    Text(stringResource(id = R.string.auth_button_send_code), style = CorusFont.button, color = Color.White)
                }
            }
        } else {
            Text(
                text = stringResource(id = R.string.auth_phone_code_sent_format, selectedCountry.dialCode, phoneNumber),
                style = CorusFont.bodyMedium,
                color = CorusColors.Text,
                textAlign = TextAlign.Center,
            )

            Spacer(modifier = Modifier.height(CorusSpacing.lg))

            OutlinedTextField(
                value = verificationCode,
                onValueChange = onVerificationCodeChange,
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text(stringResource(id = R.string.auth_code_placeholder)) },
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Number,
                    imeAction = ImeAction.Done,
                ),
                keyboardActions = KeyboardActions(
                    onDone = { if (verificationCode.length == 6) onVerifyCode() }
                ),
                singleLine = true,
                shape = RoundedCornerShape(CorusSpacing.cornerRadiusMedium),
            )

            Spacer(modifier = Modifier.height(CorusSpacing.lg))

            Button(
                onClick = onVerifyCode,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                enabled = verificationCode.length == 6 && !isLoading,
                shape = RoundedCornerShape(CorusSpacing.cornerRadiusMedium),
                colors = ButtonDefaults.buttonColors(containerColor = CorusColors.Accent),
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = Color.White,
                        strokeWidth = 2.dp,
                    )
                } else {
                    Text(stringResource(id = R.string.auth_button_verify), style = CorusFont.button, color = Color.White)
                }
            }

            // Resend Code — matches iOS: disabled during cooldown with timer text
            Spacer(modifier = Modifier.height(CorusSpacing.md))
            TextButton(
                onClick = {
                    resendCooldown = 30
                    onSendCode()
                },
                enabled = resendCooldown == 0 && !isLoading,
            ) {
                Text(
                    if (resendCooldown > 0) stringResource(id = R.string.change_phone_resend_in_format, resendCooldown) else stringResource(id = R.string.change_phone_resend_code),
                    style = CorusFont.captionMedium,
                    color = if (resendCooldown > 0) CorusColors.Tertiary else CorusColors.Accent,
                )
            }

            // Use a different number — matches iOS
            TextButton(onClick = onUseDifferentNumber) {
                Text(
                    stringResource(id = R.string.change_phone_use_different_number),
                    style = CorusFont.captionMedium,
                    color = CorusColors.Accent,
                )
            }
        }

        if (error != null) {
            Spacer(modifier = Modifier.height(CorusSpacing.md))
            Text(
                text = error,
                style = CorusFont.caption,
                color = CorusColors.Error,
                textAlign = TextAlign.Center,
            )
        }

        Spacer(modifier = Modifier.height(CorusSpacing.xxxl))

        // Country picker dialog
        if (showCountryPicker) {
            AlertDialog(
                onDismissRequest = { showCountryPicker = false },
                title = { Text(stringResource(id = R.string.change_phone_select_country_title), style = CorusFont.songTitleLarge) },
                text = {
                    Column(
                        modifier = Modifier
                            .heightIn(max = 400.dp)
                            .verticalScroll(rememberScrollState()),
                    ) {
                        CountryCode.all.forEach { country ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        onCountrySelected(country)
                                        showCountryPicker = false
                                    }
                                    .padding(vertical = CorusSpacing.md),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(country.flag, style = CorusFont.body)
                                Spacer(modifier = Modifier.width(CorusSpacing.md))
                                Text(country.name, style = CorusFont.body, color = CorusColors.Text, modifier = Modifier.weight(1f))
                                Text(country.dialCode, style = CorusFont.body, color = CorusColors.Secondary)
                            }
                        }
                    }
                },
                confirmButton = {},
            )
        }
        }

        IconButton(
            onClick = onBack,
            modifier = Modifier
                .align(Alignment.TopStart)
                .statusBarsPadding()
                .padding(CorusSpacing.xs),
        ) {
            Icon(
                Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = stringResource(id = R.string.common_back),
                tint = CorusColors.Text,
            )
        }
    }
}
