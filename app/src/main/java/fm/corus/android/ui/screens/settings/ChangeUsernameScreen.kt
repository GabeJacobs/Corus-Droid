package fm.corus.android.ui.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import fm.corus.android.R
import fm.corus.android.ui.components.ToastManager
import fm.corus.android.ui.theme.CorusColors
import fm.corus.android.ui.theme.CorusFont
import fm.corus.android.ui.theme.CorusSpacing

@Composable
fun ChangeUsernameScreen(
    viewModel: ChangeUsernameViewModel = hiltViewModel(),
    onBack: () -> Unit = {},
) {
    val username by viewModel.username.collectAsState()
    val validationState by viewModel.validationState.collectAsState()
    val invalidReason by viewModel.invalidReason.collectAsState()
    val isSaving by viewModel.isSaving.collectAsState()
    val usernameUpdatedMsg = stringResource(R.string.change_username_username_updated_toast)

    Column(modifier = Modifier.fillMaxSize().background(CorusColors.Background)) {
        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = CorusSpacing.sm, vertical = CorusSpacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.common_back))
            }
            Text(stringResource(R.string.change_username_screen_title), style = CorusFont.screenTitle, color = CorusColors.Text)

            Spacer(modifier = Modifier.weight(1f))

            TextButton(
                onClick = {
                    viewModel.save {
                        ToastManager.show(usernameUpdatedMsg)
                        onBack()
                    }
                },
                enabled = validationState == ChangeUsernameViewModel.ValidationState.Available && !isSaving,
            ) {
                if (isSaving) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = CorusColors.Accent,
                    )
                } else {
                    Text(stringResource(R.string.common_save), style = CorusFont.button, color = CorusColors.Accent)
                }
            }
        }

        HorizontalDivider(color = CorusColors.Divider)

        Column(
            modifier = Modifier.padding(CorusSpacing.lg),
        ) {
            OutlinedTextField(
                value = username,
                onValueChange = { viewModel.onUsernameChanged(it) },
                label = { Text(stringResource(R.string.change_username_field_label)) },
                prefix = { Text("@", style = CorusFont.body, color = CorusColors.Secondary) },
                trailingIcon = {
                    when (validationState) {
                        ChangeUsernameViewModel.ValidationState.Checking -> {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color = CorusColors.Secondary,
                            )
                        }
                        ChangeUsernameViewModel.ValidationState.Available -> {
                            Icon(Icons.Filled.CheckCircle, null, tint = CorusColors.Verified)
                        }
                        ChangeUsernameViewModel.ValidationState.Taken,
                        ChangeUsernameViewModel.ValidationState.Invalid -> {
                            Icon(Icons.Filled.Cancel, null, tint = CorusColors.Error)
                        }
                        else -> {}
                    }
                },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(CorusSpacing.cornerRadiusMedium),
            )

            Spacer(modifier = Modifier.height(CorusSpacing.sm))

            Text(
                text = when (validationState) {
                    ChangeUsernameViewModel.ValidationState.Available -> stringResource(R.string.change_username_status_available)
                    ChangeUsernameViewModel.ValidationState.Taken -> stringResource(R.string.change_username_status_taken)
                    ChangeUsernameViewModel.ValidationState.Invalid ->
                        invalidReason ?: stringResource(R.string.change_username_status_invalid_default)
                    ChangeUsernameViewModel.ValidationState.Checking -> stringResource(R.string.change_username_status_checking)
                    else -> stringResource(R.string.change_username_status_invalid_default)
                },
                style = CorusFont.caption,
                color = when (validationState) {
                    ChangeUsernameViewModel.ValidationState.Available -> CorusColors.Verified
                    ChangeUsernameViewModel.ValidationState.Taken,
                    ChangeUsernameViewModel.ValidationState.Invalid -> CorusColors.Error
                    else -> CorusColors.Secondary
                },
            )
        }
    }
}
