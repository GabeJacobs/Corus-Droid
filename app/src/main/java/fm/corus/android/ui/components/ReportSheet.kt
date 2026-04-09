package fm.corus.android.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import fm.corus.android.data.repository.AuthRepository
import fm.corus.android.data.repository.UserRepository
import fm.corus.android.ui.theme.CorusColors
import fm.corus.android.ui.theme.CorusFont
import fm.corus.android.ui.theme.CorusSpacing
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

enum class ReportContentType(val label: String) {
    POST("post"),
    COMMENT("comment"),
    USER("user"),
    MESSAGE("message"),
}

private val REPORT_REASONS = listOf(
    "Spam or scam",
    "Harassment or bullying",
    "Hate speech",
    "Nudity or sexual content",
    "Violence or threats",
    "False information",
    "Intellectual property violation",
    "Other",
)

@Composable
fun ReportSheet(
    contentType: ReportContentType,
    contentId: String,
    authRepository: AuthRepository,
    userRepository: UserRepository,
    onDismiss: () -> Unit,
) {
    var selectedReason by remember { mutableStateOf<String?>(null) }
    var details by remember { mutableStateOf("") }
    var isSubmitting by remember { mutableStateOf(false) }
    var showSuccess by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = CorusSpacing.sm, vertical = CorusSpacing.md),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = onDismiss) {
                    Text("Cancel", style = CorusFont.body, color = CorusColors.Accent)
                }
                Spacer(modifier = Modifier.weight(1f))
                Text("Report", style = CorusFont.screenTitle, color = CorusColors.Text)
                Spacer(modifier = Modifier.weight(1f))
                TextButton(
                    onClick = {
                        val reason = selectedReason ?: return@TextButton
                        val uid = authRepository.currentUserId ?: return@TextButton
                        isSubmitting = true
                        scope.launch {
                            try {
                                userRepository.submitReport(
                                    reporterId = uid,
                                    targetUserId = if (contentType == ReportContentType.USER) contentId else null,
                                    postId = if (contentType == ReportContentType.POST) contentId else null,
                                    reason = reason,
                                    details = details.ifBlank { "" },
                                )
                                showSuccess = true
                                delay(1500)
                                onDismiss()
                            } catch (_: Exception) {
                                isSubmitting = false
                            }
                        }
                    },
                    enabled = selectedReason != null && !isSubmitting,
                ) {
                    Text("Submit", style = CorusFont.button, color = CorusColors.Accent)
                }
            }

            HorizontalDivider(color = CorusColors.Divider)

            // Reason list
            Column(modifier = Modifier.padding(horizontal = CorusSpacing.lg)) {
                Spacer(modifier = Modifier.height(CorusSpacing.md))
                Text(
                    text = "Why are you reporting this ${contentType.label}?",
                    style = CorusFont.caption,
                    color = CorusColors.Secondary,
                )
                Spacer(modifier = Modifier.height(CorusSpacing.sm))

                REPORT_REASONS.forEach { reason ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { selectedReason = reason }
                            .padding(vertical = CorusSpacing.md),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = reason,
                            style = CorusFont.body,
                            color = CorusColors.Text,
                            modifier = Modifier.weight(1f),
                        )
                        if (selectedReason == reason) {
                            Icon(Icons.Filled.Check, contentDescription = null, tint = CorusColors.Accent, modifier = Modifier.size(18.dp))
                        }
                    }
                    HorizontalDivider(color = CorusColors.Divider)
                }

                // Additional details
                if (selectedReason != null) {
                    Spacer(modifier = Modifier.height(CorusSpacing.lg))
                    OutlinedTextField(
                        value = details,
                        onValueChange = { details = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("Additional details (optional)") },
                        minLines = 3,
                        maxLines = 6,
                        shape = RoundedCornerShape(CorusSpacing.cornerRadiusMedium),
                    )
                }
            }
        }

        // Success overlay
        if (showSuccess) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(CorusColors.Background),
                contentAlignment = Alignment.Center,
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(CorusSpacing.md),
                ) {
                    Icon(
                        Icons.Filled.CheckCircle,
                        contentDescription = null,
                        tint = CorusColors.Accent,
                        modifier = Modifier.size(48.dp),
                    )
                    Text("Thanks for reporting", style = CorusFont.songTitleLarge, color = CorusColors.Text)
                    Text(
                        "We'll review this and take action if needed.",
                        style = CorusFont.body,
                        color = CorusColors.Secondary,
                    )
                }
            }
        }
    }
}
