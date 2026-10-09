package fm.corus.android.ui.screens.feed

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fm.corus.android.localization.CorusStrings
import fm.corus.android.service.ForYouPreviewEndedAnalytics
import fm.corus.android.service.ForYouPreviewEndedDismissReason
import fm.corus.android.service.ForYouPreviewEndedEvent
import fm.corus.android.ui.components.CorusModalBottomSheet
import fm.corus.android.ui.components.rememberGuardedSheetState
import fm.corus.android.ui.theme.CorusColors
import fm.corus.android.ui.theme.CorusFont
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ForYouPreviewEndedSheet(
    hasClubIntroTrial: Boolean,
    onDismiss: () -> Unit,
    onClub: () -> Unit,
    onTrack: (ForYouPreviewEndedEvent) -> Unit = {},
) {
    val sheetState = rememberGuardedSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    var closing by remember { mutableStateOf(false) }
    val analytics = remember { ForYouPreviewEndedAnalytics() }
    val track by rememberUpdatedState(onTrack)
    DisposableEffect(Unit) {
        analytics.shown(hasClubIntroTrial)?.let(track)
        onDispose { analytics.dismissed(ForYouPreviewEndedDismissReason.DISMISS)?.let(track) }
    }
    fun close(joinClub: Boolean) {
        if (closing) return
        closing = true
        val event = if (joinClub) analytics.clubTapped() else analytics.dismissed(ForYouPreviewEndedDismissReason.CONTINUE)
        event?.let(track)
        scope.launch {
            sheetState.hide()
            onDismiss()
            if (joinClub) onClub()
        }
    }
    CorusModalBottomSheet(onDismissRequest = {
        analytics.dismissed(ForYouPreviewEndedDismissReason.DISMISS)?.let(track)
        onDismiss()
    }, sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 30.dp, topEnd = 30.dp)) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())
            .padding(horizontal = 25.dp).padding(top = 8.dp, bottom = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.size(52.dp).background(CorusColors.Accent.copy(alpha = .12f), RoundedCornerShape(16.dp)),
                contentAlignment = Alignment.Center) {
                Icon(Icons.Outlined.Tune, null, tint = CorusColors.Accent, modifier = Modifier.size(27.dp))
            }
            Spacer(Modifier.height(20.dp))
            Text(stringResource(CorusStrings.for_you_preview_ended_title),
                style = CorusFont.songTitleLarge.copy(fontSize = 24.sp, lineHeight = 29.sp),
                color = CorusColors.Text, textAlign = TextAlign.Center, modifier = Modifier.semantics { heading() })
            Spacer(Modifier.height(13.dp))
            Text(stringResource(CorusStrings.for_you_preview_ended_body), style = CorusFont.body.copy(fontSize = 16.sp),
                color = CorusColors.Secondary, textAlign = TextAlign.Center)
            Spacer(Modifier.height(23.dp))
            Text(stringResource(CorusStrings.for_you_preview_ended_club_body), style = CorusFont.body,
                color = CorusColors.Secondary, textAlign = TextAlign.Center)
            Spacer(Modifier.height(23.dp))
            Button(onClick = { close(true) }, enabled = !closing,
                modifier = Modifier.fillMaxWidth().heightIn(min = 54.dp).testTag("for_you_preview_ended_club"),
                shape = CircleShape, colors = ButtonDefaults.buttonColors(containerColor = CorusColors.Accent),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp)) {
                Text(stringResource(if (hasClubIntroTrial) CorusStrings.for_you_preview_ended_trial_cta else CorusStrings.for_you_preview_ended_join_cta),
                    style = CorusFont.bodyMedium.copy(fontSize = 17.sp), color = Color.White,
                    textAlign = TextAlign.Center, modifier = Modifier.weight(1f, fill = false))
                Spacer(Modifier.width(9.dp))
                Icon(Icons.AutoMirrored.Outlined.ArrowForward, null, modifier = Modifier.size(18.dp))
            }
            Spacer(Modifier.height(5.dp))
            TextButton(onClick = { close(false) }, enabled = !closing,
                modifier = Modifier.fillMaxWidth().heightIn(min = 57.dp).testTag("for_you_preview_ended_continue")) {
                Text(stringResource(CorusStrings.for_you_preview_ended_continue), style = CorusFont.bodyMedium.copy(fontSize = 16.sp),
                    color = CorusColors.Secondary, textAlign = TextAlign.Center)
            }
        }
    }
}
