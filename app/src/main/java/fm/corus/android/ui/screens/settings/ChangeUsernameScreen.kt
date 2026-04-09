package fm.corus.android.ui.screens.settings

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
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
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
    val isSaving by viewModel.isSaving.collectAsState()

    Column(modifier = Modifier.fillMaxSize()) {
        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = CorusSpacing.sm, vertical = CorusSpacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
            Text("Change Username", style = CorusFont.screenTitle, color = CorusColors.Text)

            Spacer(modifier = Modifier.weight(1f))

            TextButton(
                onClick = {
                    viewModel.save {
                        ToastManager.show("Username updated!")
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
                    Text("Save", style = CorusFont.button, color = CorusColors.Accent)
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
                label = { Text("Username") },
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
                    ChangeUsernameViewModel.ValidationState.Available -> "Username is available"
                    ChangeUsernameViewModel.ValidationState.Taken -> "Username is already taken"
                    ChangeUsernameViewModel.ValidationState.Invalid -> "Letters, numbers, underscores, and periods only"
                    ChangeUsernameViewModel.ValidationState.Checking -> "Checking availability..."
                    else -> "Letters, numbers, underscores, and periods only"
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
