package fm.corus.android.ui.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import fm.corus.android.R
import fm.corus.android.ui.theme.CorusColors
import fm.corus.android.ui.theme.CorusFont
import fm.corus.android.ui.theme.CorusSpacing

@Composable
fun FeedbackFormScreen(
    viewModel: FeedbackFormViewModel = hiltViewModel(),
    onBack: () -> Unit = {},
) {
    val feedbackType by viewModel.feedbackType.collectAsState()
    val subject by viewModel.subject.collectAsState()
    val description by viewModel.description.collectAsState()
    val isSubmitting by viewModel.isSubmitting.collectAsState()
    val showSuccess by viewModel.showSuccess.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()

    Box(modifier = Modifier.fillMaxSize().background(CorusColors.Background)) {
        Column(modifier = Modifier.fillMaxSize()) {
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
                Text(stringResource(R.string.feedback_screen_title), style = CorusFont.screenTitle, color = CorusColors.Text)

                Spacer(modifier = Modifier.weight(1f))

                TextButton(
                    onClick = { viewModel.submit(onDismiss = onBack) },
                    enabled = viewModel.canSubmit,
                ) {
                    Text(stringResource(R.string.common_submit), style = CorusFont.button, color = CorusColors.Accent)
                }
            }

            HorizontalDivider(color = CorusColors.Divider)

            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(CorusSpacing.lg),
                verticalArrangement = Arrangement.spacedBy(CorusSpacing.lg),
            ) {
                // Type picker (segmented)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(CorusSpacing.sm),
                ) {
                    FeedbackFormViewModel.FeedbackType.entries.forEach { type ->
                        val selected = feedbackType == type
                        FilterChip(
                            selected = selected,
                            onClick = { viewModel.onTypeChanged(type) },
                            label = { Text(type.label, style = CorusFont.body) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = CorusColors.Accent,
                                selectedLabelColor = Color.White,
                                containerColor = CorusColors.CardBackground,
                                labelColor = CorusColors.Text,
                            ),
                            modifier = Modifier.weight(1f),
                        )
                    }
                }

                // Subject
                OutlinedTextField(
                    value = subject,
                    onValueChange = { viewModel.onSubjectChanged(it) },
                    label = { Text(stringResource(R.string.feedback_subject_label)) },
                    placeholder = { Text(stringResource(R.string.feedback_subject_placeholder), color = CorusColors.Tertiary) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(CorusSpacing.cornerRadiusMedium),
                )

                // Description
                OutlinedTextField(
                    value = description,
                    onValueChange = { viewModel.onDescriptionChanged(it) },
                    label = { Text(stringResource(R.string.feedback_description_label)) },
                    placeholder = { Text(stringResource(R.string.feedback_description_placeholder), color = CorusColors.Tertiary) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 150.dp),
                    shape = RoundedCornerShape(CorusSpacing.cornerRadiusMedium),
                    maxLines = 10,
                )

                // Device info note
                Text(
                    text = stringResource(R.string.feedback_device_info_note),
                    style = CorusFont.caption,
                    color = CorusColors.Tertiary,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        // Submitting overlay
        if (isSubmitting) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.3f)),
                contentAlignment = Alignment.Center,
            ) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = CorusColors.Text.copy(alpha = 0.85f),
                ) {
                    Column(
                        modifier = Modifier.padding(CorusSpacing.xxl),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(CorusSpacing.md),
                    ) {
                        CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp)
                        Text(stringResource(R.string.feedback_submitting), style = CorusFont.bodyMedium, color = Color.White)
                    }
                }
            }
        }

        // Success overlay
        if (showSuccess) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.3f)),
                contentAlignment = Alignment.Center,
            ) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = CorusColors.Text.copy(alpha = 0.85f),
                ) {
                    Column(
                        modifier = Modifier.padding(CorusSpacing.xxl),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(CorusSpacing.md),
                    ) {
                        Icon(
                            Icons.Filled.CheckCircle,
                            contentDescription = null,
                            tint = CorusColors.Verified,
                            modifier = Modifier.size(48.dp),
                        )
                        Text(stringResource(R.string.feedback_success), style = CorusFont.bodyMedium, color = Color.White)
                    }
                }
            }
        }

        // Error dialog
        if (errorMessage != null) {
            AlertDialog(
                onDismissRequest = { viewModel.clearError() },
                title = { Text(stringResource(R.string.common_error), style = CorusFont.songTitleLarge) },
                text = { Text(errorMessage ?: "", style = CorusFont.body) },
                confirmButton = {
                    TextButton(onClick = { viewModel.clearError() }) {
                        Text(stringResource(R.string.common_ok))
                    }
                },
            )
        }
    }
}
