package fm.corus.android.ui.screens.feed

import android.animation.ValueAnimator
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import fm.corus.android.localization.CorusStrings
import fm.corus.android.ui.components.CorusModalBottomSheet
import fm.corus.android.ui.components.rememberGuardedSheetState
import fm.corus.android.ui.theme.CorusColors
import fm.corus.android.ui.theme.CorusFont
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FavoritesFeedGuideSheet(onDismiss: () -> Unit) {
    var stage by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        if (!ValueAnimator.areAnimatorsEnabled()) { stage = 2; return@LaunchedEffect }
        while (isActive) {
            delay(650); stage = 1
            delay(1100); stage = 2
            delay(900); stage = 3
            delay(1500); stage = 0
        }
    }
    CorusModalBottomSheet(onDismissRequest = onDismiss,
        sheetState = rememberGuardedSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp)) {
            Icon(Icons.Default.Star, null, Modifier.size(36.dp), tint = CorusColors.Accent)
            Text(stringResource(CorusStrings.favorites_feed_guide_title), style = CorusFont.custom(800, 23),
                textAlign = TextAlign.Center)
            Column(Modifier.fillMaxWidth().height(178.dp).background(CorusColors.Secondary.copy(alpha = .08f),
                RoundedCornerShape(20.dp)).padding(20.dp).clearAndSetSemantics {}, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        AnimatedContent(stage >= 2, label = "Favorites guide tab") { favorites ->
                            Text(stringResource(if (favorites) CorusStrings.feed_mode_favorites else CorusStrings.rail_following),
                                style = CorusFont.custom(800, 17))
                        }
                        Icon(Icons.Default.KeyboardArrowDown, null, Modifier.size(18.dp), tint = CorusColors.Accent)
                    }
                    Text(stringResource(CorusStrings.feed_mode_trending), style = CorusFont.custom(500, 17),
                        color = CorusColors.Secondary)
                }
                Box(Modifier.width(132.dp).height(3.dp).background(CorusColors.Accent, RoundedCornerShape(2.dp)))
                if (stage == 1 || stage == 2) {
                    Column(Modifier.widthIn(min = 182.dp).background(CorusColors.Background,
                        RoundedCornerShape(14.dp)).padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        for ((key, selected) in listOf(CorusStrings.rail_following to (stage == 1),
                            CorusStrings.feed_mode_favorites to (stage == 2))) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(stringResource(key), style = CorusFont.custom(700, 16))
                                Spacer(Modifier.width(12.dp))
                                if (selected) Icon(Icons.Default.Check, null, Modifier.size(18.dp), tint = CorusColors.Accent)
                            }
                        }
                    }
                }
            }
            Text(stringResource(CorusStrings.favorites_feed_guide_body), style = CorusFont.custom(500, 17),
                color = CorusColors.Secondary, textAlign = TextAlign.Center)
            Button(onClick = onDismiss, modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = CorusColors.Accent)) {
                Text(stringResource(CorusStrings.favorites_feed_guide_done), style = CorusFont.custom(800, 17))
            }
        }
    }
}
