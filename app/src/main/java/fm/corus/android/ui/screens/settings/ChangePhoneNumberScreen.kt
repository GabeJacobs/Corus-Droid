package fm.corus.android.ui.screens.settings

import android.app.Activity
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import fm.corus.android.R
import fm.corus.android.ui.components.CorusHeaderIconButton
import fm.corus.android.ui.theme.CorusColors
import fm.corus.android.ui.theme.CorusFont
import fm.corus.android.ui.theme.CorusSpacing

@Composable
fun ChangePhoneNumberScreen(
    viewModel: ChangePhoneNumberViewModel = hiltViewModel(),
    onBack: () -> Unit = {},
) {
    val phoneNumber by viewModel.phoneNumber.collectAsState()
    val verificationCode by viewModel.verificationCode.collectAsState()
    val selectedCountry by viewModel.selectedCountry.collectAsState()
    val codeSent by viewModel.codeSent.collectAsState()
    val isSendingCode by viewModel.isSendingCode.collectAsState()
    val isVerifying by viewModel.isVerifying.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()
    val showSuccess by viewModel.showSuccess.collectAsState()

    var showCountryPicker by remember { mutableStateOf(false) }
    var resendSeconds by remember { mutableIntStateOf(0) }

    val activity = LocalContext.current as Activity

    // Update resend timer
    LaunchedEffect(codeSent) {
        if (codeSent) {
            while (true) {
                resendSeconds = viewModel.resendSecondsRemaining()
                kotlinx.coroutines.delay(1000)
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize().background(CorusColors.Background)) {
        // Header
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
            Text(stringResource(R.string.change_phone_screen_title), style = CorusFont.screenTitle, color = CorusColors.Text)
        }

        HorizontalDivider(color = CorusColors.Divider)

        // Error message
        if (errorMessage != null) {
            Text(
                text = errorMessage ?: "",
                style = CorusFont.caption,
                color = CorusColors.Error,
                modifier = Modifier.padding(horizontal = CorusSpacing.lg, vertical = CorusSpacing.sm),
            )
        }

        if (codeSent) {
            // Code entry section
            Column(
                modifier = Modifier.padding(CorusSpacing.lg),
                verticalArrangement = Arrangement.spacedBy(CorusSpacing.md),
            ) {
                Text(stringResource(R.string.change_phone_section_verification), style = CorusFont.sectionHeader, color = CorusColors.Secondary)

                OutlinedTextField(
                    value = verificationCode,
                    onValueChange = { viewModel.onCodeChanged(it) },
                    placeholder = { Text(stringResource(R.string.change_phone_code_placeholder)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(CorusSpacing.cornerRadiusMedium),
                )

                // Verify button
                Button(
                    onClick = { viewModel.verify() },
                    enabled = !isVerifying,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(CorusSpacing.cornerRadiusMedium),
                    colors = ButtonDefaults.buttonColors(containerColor = CorusColors.Accent),
                ) {
                    if (isVerifying) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = Color.White,
                        )
                    } else {
                        Text(stringResource(R.string.common_verify), style = CorusFont.button, color = Color.White)
                    }
                }

                // Resend code
                val canResend = viewModel.canResend()
                TextButton(
                    onClick = { viewModel.sendCode(activity) },
                    enabled = canResend,
                ) {
                    Text(
                        text = if (canResend) stringResource(R.string.change_phone_resend_code) else stringResource(R.string.change_phone_resend_in_format, resendSeconds),
                        style = CorusFont.body,
                        color = if (canResend) CorusColors.Accent else CorusColors.Tertiary,
                    )
                }

                // Use different number
                TextButton(onClick = { viewModel.goBack() }) {
                    Text(
                        stringResource(R.string.change_phone_use_different_number),
                        style = CorusFont.body,
                        color = CorusColors.Secondary,
                    )
                }

                Spacer(modifier = Modifier.height(CorusSpacing.sm))

                Text(
                    text = stringResource(R.string.change_phone_code_sent_message_format, selectedCountry.dialCode, phoneNumber),
                    style = CorusFont.caption,
                    color = CorusColors.Tertiary,
                )
            }
        } else {
            // Phone entry section
            Column(
                modifier = Modifier.padding(CorusSpacing.lg),
                verticalArrangement = Arrangement.spacedBy(CorusSpacing.md),
            ) {
                Text(stringResource(R.string.change_phone_section_new_number), style = CorusFont.sectionHeader, color = CorusColors.Secondary)

                // Country code + phone input
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(CorusSpacing.sm),
                ) {
                    // Country code button
                    OutlinedButton(
                        onClick = { showCountryPicker = true },
                        shape = RoundedCornerShape(CorusSpacing.cornerRadiusMedium),
                        contentPadding = PaddingValues(horizontal = CorusSpacing.md, vertical = CorusSpacing.md),
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
                        onValueChange = { viewModel.onPhoneChanged(it) },
                        placeholder = { Text(stringResource(R.string.change_phone_placeholder)) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(CorusSpacing.cornerRadiusMedium),
                    )
                }

                // Send code button
                Button(
                    onClick = { viewModel.sendCode(activity) },
                    enabled = !isSendingCode,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(CorusSpacing.cornerRadiusMedium),
                    colors = ButtonDefaults.buttonColors(containerColor = CorusColors.Accent),
                ) {
                    if (isSendingCode) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = Color.White,
                        )
                    } else {
                        Text(stringResource(R.string.change_phone_send_code), style = CorusFont.button, color = Color.White)
                    }
                }

                Text(
                    text = stringResource(R.string.change_phone_send_code_explainer),
                    style = CorusFont.caption,
                    color = CorusColors.Tertiary,
                )
            }
        }
    }

    // Country picker bottom sheet
    if (showCountryPicker) {
        AlertDialog(
            onDismissRequest = { showCountryPicker = false },
            title = { Text(stringResource(R.string.change_phone_select_country_title), style = CorusFont.songTitleLarge) },
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
                                    viewModel.onCountrySelected(country)
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

    // Success alert
    if (showSuccess) {
        AlertDialog(
            onDismissRequest = { onBack() },
            title = { Text(stringResource(R.string.change_phone_updated_title), style = CorusFont.songTitleLarge) },
            text = { Text(stringResource(R.string.change_phone_updated_message), style = CorusFont.body) },
            confirmButton = {
                TextButton(onClick = { onBack() }) {
                    Text(stringResource(R.string.common_ok))
                }
            },
        )
    }
}
