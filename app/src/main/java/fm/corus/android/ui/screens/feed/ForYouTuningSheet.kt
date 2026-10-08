package fm.corus.android.ui.screens.feed

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import fm.corus.android.R
import fm.corus.android.domain.ForYouTuningMode
import fm.corus.android.domain.ForYouStayCloseProgress
import fm.corus.android.domain.HapticManager
import fm.corus.android.ui.LocalHapticManager
import fm.corus.android.ui.components.CorusModalBottomSheet
import fm.corus.android.ui.components.VennDiagramIcon
import fm.corus.android.ui.components.rememberGuardedSheetState
import fm.corus.android.ui.theme.CorusColors
import fm.corus.android.ui.theme.CorusFont

internal val ForYouTuningMode.titleResource: Int
    get() = when (this) {
        ForYouTuningMode.ECLECTIC -> R.string.for_you_eclectic
        ForYouTuningMode.BALANCED -> R.string.for_you_balanced
        ForYouTuningMode.STAY_CLOSE -> R.string.for_you_stay_close
    }

private val ForYouTuningMode.subtitleResource: Int
    get() = when (this) {
        ForYouTuningMode.ECLECTIC -> R.string.for_you_eclectic_description
        ForYouTuningMode.BALANCED -> R.string.for_you_balanced_description
        ForYouTuningMode.STAY_CLOSE -> R.string.for_you_stay_close_description
    }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ForYouTuningSheet(
    current: ForYouTuningMode,
    defaultMode: ForYouTuningMode,
    onApply: (ForYouTuningMode) -> Unit,
    onDismiss: () -> Unit,
    progress: ForYouStayCloseProgress? = null,
    hasClubIntroTrial: Boolean = false,
    onClub: (ForYouTuningMode) -> Unit = {},
) {
    var selection by remember { mutableStateOf(current) }
    var showUnlockExplanation by remember { mutableStateOf(false) }
    var unlockMode by remember { mutableStateOf(ForYouTuningMode.BALANCED) }
    var edited by remember { mutableStateOf(false) }
    val haptics = LocalHapticManager.current
    LaunchedEffect(current) { if (!edited) selection = current }
    CorusModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberGuardedSheetState(skipPartiallyExpanded = true),
    ) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)) {
            Box(Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.for_you_tune), style = CorusFont.screenTitle,
                    color = CorusColors.Text, modifier = Modifier.align(Alignment.Center))
            }
            Spacer(Modifier.height(12.dp))
            Text(stringResource(R.string.for_you_choose), style = CorusFont.body, color = CorusColors.Secondary)
            Spacer(Modifier.height(20.dp))
            ForYouTuningMode.entries.forEach { mode ->
                val checked = mode == selection
                val postingLocked = mode != ForYouTuningMode.ECLECTIC && progress?.unlocked != true
                val clubLocked = !postingLocked && mode != ForYouTuningMode.ECLECTIC && progress?.paywallLocked == true
                val locked = postingLocked || clubLocked
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
                        .background(if (checked) CorusColors.Accent.copy(alpha = .08f) else CorusColors.Secondary.copy(alpha = .06f))
                        .border(if (checked) 1.5.dp else 0.dp, if (checked) CorusColors.Accent else androidx.compose.ui.graphics.Color.Transparent, RoundedCornerShape(16.dp))
                        .clickable(role = if (locked) Role.Button else Role.RadioButton) {
                            if (postingLocked) {
                                unlockMode = mode
                                showUnlockExplanation = true
                            }
                            else { edited = true; selection = mode; haptics.impact(HapticManager.ImpactStyle.LIGHT) }
                        }
                        .semantics { selected = checked }.padding(16.dp),
                    verticalAlignment = Alignment.Top,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Box(Modifier.width(26.dp)) {
                        when (mode) {
                            ForYouTuningMode.ECLECTIC -> VennDiagramIcon(color = CorusColors.Text, shadedIntersection = true)
                            else -> Icon(if (mode == ForYouTuningMode.BALANCED) Icons.Outlined.Tune else Icons.Outlined.FavoriteBorder,
                                contentDescription = null, tint = CorusColors.Text, modifier = Modifier.size(20.dp))
                        }
                    }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(stringResource(mode.titleResource), style = CorusFont.bodyMedium, color = CorusColors.Text)
                            if (locked) Text(stringResource(R.string.for_you_locked), style = CorusFont.caption, color = CorusColors.Secondary)
                            if (clubLocked) Text("Corus Club", style = CorusFont.caption, color = CorusColors.Accent)
                        }
                        Text(stringResource(mode.subtitleResource), style = CorusFont.caption, color = CorusColors.Secondary)
                        if (postingLocked) {
                            Text(stringResource(fm.corus.android.localization.CorusStrings.for_you_post_requirement), style = CorusFont.caption, color = CorusColors.Secondary)
                        }
                    }
                    Icon(if (checked) Icons.Outlined.CheckCircle else if (locked) Icons.Outlined.Lock else Icons.Outlined.RadioButtonUnchecked,
                        contentDescription = null, tint = if (checked) CorusColors.Accent else CorusColors.Secondary, modifier = Modifier.size(22.dp))
                }
                Spacer(Modifier.height(12.dp))
            }
            Spacer(Modifier.height(8.dp))
            Text(stringResource(R.string.for_you_posting_hint), style = CorusFont.caption, color = CorusColors.Secondary)
            Spacer(Modifier.height(20.dp))
            Button(onClick = {
                if (selection != ForYouTuningMode.ECLECTIC && progress?.paywallLocked == true) onClub(selection)
                else onApply(selection)
                onDismiss()
            }, modifier = Modifier.fillMaxWidth().testTag("for_you_apply"),
                enabled = selection == ForYouTuningMode.ECLECTIC || progress?.unlocked == true,
                shape = CircleShape, colors = ButtonDefaults.buttonColors(containerColor = CorusColors.Accent),
                contentPadding = PaddingValues(vertical = 15.dp)) {
                Text(stringResource(if (selection != ForYouTuningMode.ECLECTIC && progress?.paywallLocked == true)
                    if (hasClubIntroTrial) R.string.gift_club_offer_cta_trial else R.string.for_you_unlock_club
                    else R.string.for_you_apply), style = CorusFont.bodyMedium)
            }
            Spacer(Modifier.height(20.dp))
        }
    }
    if (showUnlockExplanation) {
        AlertDialog(onDismissRequest = { showUnlockExplanation = false },
            title = { Text(stringResource(fm.corus.android.localization.CorusStrings.for_you_post_unlock_title,
                stringResource(unlockMode.titleResource))) },
            text = { Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(fm.corus.android.localization.CorusStrings.for_you_post_unlock_body))
                if (progress != null) Text(stringResource(R.string.for_you_post_progress,
                    minOf(progress.postCount, progress.threshold), progress.threshold))
            } },
            confirmButton = { TextButton(onClick = { showUnlockExplanation = false }) {
                Text(stringResource(R.string.feed_energy_got_it))
            } })
    }
}
