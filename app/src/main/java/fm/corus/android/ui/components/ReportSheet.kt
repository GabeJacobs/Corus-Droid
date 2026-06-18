package fm.corus.android.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import fm.corus.android.R
import fm.corus.android.data.repository.AuthRepository
import fm.corus.android.data.repository.UserRepository
import fm.corus.android.service.AnalyticsService
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

@Composable
private fun reportReasons(): List<String> = listOf(
    stringResource(R.string.report_reason_spam),
    stringResource(R.string.report_reason_harassment),
    stringResource(R.string.report_reason_hate),
    stringResource(R.string.report_reason_nudity),
    stringResource(R.string.report_reason_violence),
    stringResource(R.string.report_reason_false_info),
    stringResource(R.string.report_reason_ip_violation),
    stringResource(R.string.report_reason_other),
)

@Composable
fun ReportSheet(
    contentType: ReportContentType,
    contentId: String,
    authRepository: AuthRepository,
    userRepository: UserRepository,
    analyticsService: AnalyticsService,
    onDismiss: () -> Unit,
) {
    var selectedReason by remember { mutableStateOf<String?>(null) }
    var details by remember { mutableStateOf("") }
    var isSubmitting by remember { mutableStateOf(false) }
    var showSuccess by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val reasons = reportReasons()
    val whyTextRes = when (contentType) {
        ReportContentType.POST -> R.string.report_why_post
        ReportContentType.COMMENT -> R.string.report_why_comment
        ReportContentType.USER -> R.string.report_why_user
        ReportContentType.MESSAGE -> R.string.report_why_message
    }

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
                    Text(stringResource(R.string.report_cancel), style = CorusFont.body, color = CorusColors.Accent)
                }
                Spacer(modifier = Modifier.weight(1f))
                Text(stringResource(R.string.report_title), style = CorusFont.screenTitle, color = CorusColors.Text)
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
                                when (contentType) {
                                    ReportContentType.POST -> analyticsService.logReportPost(contentId, reason)
                                    ReportContentType.COMMENT -> analyticsService.logReportComment(contentId, reason)
                                    ReportContentType.MESSAGE -> analyticsService.logReportMessage(contentId, reason)
                                    ReportContentType.USER -> analyticsService.logReportUser(contentId)
                                }
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
                    Text(stringResource(R.string.report_submit), style = CorusFont.button, color = CorusColors.Accent)
                }
            }

            HorizontalDivider(color = CorusColors.Divider)

            // Reason list
            Column(modifier = Modifier.padding(horizontal = CorusSpacing.lg)) {
                Spacer(modifier = Modifier.height(CorusSpacing.md))
                Text(
                    text = stringResource(whyTextRes),
                    style = CorusFont.caption,
                    color = CorusColors.Secondary,
                )
                Spacer(modifier = Modifier.height(CorusSpacing.sm))

                reasons.forEach { reason ->
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
                        placeholder = { Text(stringResource(R.string.report_details_placeholder)) },
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
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
                    Text(stringResource(R.string.report_thanks_title), style = CorusFont.songTitleLarge, color = CorusColors.Text)
                    Text(
                        stringResource(R.string.report_thanks_message),
                        style = CorusFont.body,
                        color = CorusColors.Secondary,
                    )
                }
            }
        }
    }
}
