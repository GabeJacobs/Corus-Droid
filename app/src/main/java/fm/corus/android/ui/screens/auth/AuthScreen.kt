package fm.corus.android.ui.screens.auth

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.filled.KeyboardArrowDown
import kotlinx.coroutines.delay
import fm.corus.android.R
import fm.corus.android.ui.screens.settings.CountryCode
import fm.corus.android.ui.theme.CorusColors
import fm.corus.android.ui.theme.CorusFont
import fm.corus.android.ui.theme.CorusSpacing

@Composable
fun AuthScreen(
    viewModel: AuthViewModel = hiltViewModel(),
) {
    val isLoading by viewModel.isLoading.collectAsState()
    val error by viewModel.error.collectAsState()
    val verificationSent by viewModel.verificationSent.collectAsState()
    val context = LocalContext.current
    val activity = context as Activity

    var phoneNumber by remember { mutableStateOf("") }
    var verificationCode by remember { mutableStateOf("") }
    var showPhoneInput by remember { mutableStateOf(false) }
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
                viewModel.setError("Google sign-in failed: no ID token received.")
            }
        } catch (e: ApiException) {
            android.util.Log.e("AuthScreen", "Google sign-in failed: status=${e.statusCode}", e)
            viewModel.setError("Google sign-in failed (code ${e.statusCode}). Please try again.")
        }
    }

    if (showPhoneInput || verificationSent) {
        // Phone auth flow
        PhoneAuthContent(
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
            onBack = { showPhoneInput = false },
            onUseDifferentNumber = {
                verificationCode = ""
                viewModel.resetVerification()
            },
        )
    } else {
        // Main auth screen — matches iOS AuthView layout exactly
        Column(
            modifier = Modifier
                .fillMaxSize()
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
                    contentDescription = "Corus Logo",
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
                    text = "Share what you love",
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
                // Google Sign-In button
                AuthButton(
                    text = "Continue with Google",
                    icon = {
                        Image(
                            painter = painterResource(id = R.drawable.google_logo),
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                    },
                    isLoading = isLoading,
                    onClick = {
                        val webClientId = context.getString(R.string.default_web_client_id)
                        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                            .requestIdToken(webClientId)
                            .requestEmail()
                            .build()
                        val client = GoogleSignIn.getClient(context, gso)
                        googleSignInLauncher.launch(client.signInIntent)
                    },
                )

                // Phone button
                AuthButton(
                    text = "Continue with Phone",
                    icon = {
                        Icon(
                            Icons.Filled.Phone,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                            tint = CorusColors.Text,
                        )
                    },
                    isLoading = false,
                    onClick = { showPhoneInput = true },
                )
            }

            Spacer(modifier = Modifier.weight(1f))
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
    Column(
        modifier = Modifier
            .fillMaxSize()
            .imePadding()
            .padding(CorusSpacing.xxl)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        // Back button
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Start,
        ) {
            TextButton(onClick = onBack) {
                Text("← Back", style = CorusFont.button, color = CorusColors.Accent)
            }
        }

        Spacer(modifier = Modifier.height(CorusSpacing.xxxl))

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
                text = "Enter your phone number",
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
                    placeholder = { Text("Phone number") },
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
                    Text("SEND CODE", style = CorusFont.button, color = Color.White)
                }
            }
        } else {
            Text(
                text = "Enter the 6-digit code sent to\n${selectedCountry.dialCode} $phoneNumber",
                style = CorusFont.bodyMedium,
                color = CorusColors.Text,
                textAlign = TextAlign.Center,
            )

            Spacer(modifier = Modifier.height(CorusSpacing.lg))

            OutlinedTextField(
                value = verificationCode,
                onValueChange = onVerificationCodeChange,
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("123456") },
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
                    Text("VERIFY", style = CorusFont.button, color = Color.White)
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
                    if (resendCooldown > 0) "Resend in ${resendCooldown}s" else "Resend Code",
                    style = CorusFont.captionMedium,
                    color = if (resendCooldown > 0) CorusColors.Tertiary else CorusColors.Accent,
                )
            }

            // Use a different number — matches iOS
            TextButton(onClick = onUseDifferentNumber) {
                Text(
                    "Use a different number",
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
                title = { Text("Select Country", style = CorusFont.songTitleLarge) },
                text = {
                    Column {
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
}
