package fm.corus.android.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import fm.corus.android.R
import fm.corus.android.data.model.TasteDiscoveryAccess
import fm.corus.android.ui.theme.CorusColors

@Composable
fun TasteDiscoveryClubCard(onClick: () -> Unit, modifier: Modifier = Modifier, access: TasteDiscoveryAccess = TasteDiscoveryAccess()) {
    Box(modifier.clip(RoundedCornerShape(20.dp))) {
        Column(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface).padding(8.dp).blur(6.dp).clearAndSetSemantics { },
            verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Column(Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(12.dp)), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                repeat(2) { row ->
                    Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                        repeat(2) { col ->
                            ShimmerAsyncImage(model = access.teaserArtwork.getOrNull(row * 2 + col), contentDescription = null,
                                modifier = Modifier.weight(1f).fillMaxHeight().background(CorusColors.Secondary.copy(alpha = 0.15f)), usesSolidLoadingPlaceholder = true)
                        }
                    }
                }
            }
            Row(Modifier.height(52.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ShimmerAsyncImage(model = access.teaserAvatarURL, contentDescription = null, modifier = Modifier.size(28.dp).clip(CircleShape), usesSolidLoadingPlaceholder = true)
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Spacer(Modifier.size(66.dp, 10.dp).background(CorusColors.Secondary.copy(alpha = 0.4f), CircleShape))
                    Spacer(Modifier.size(90.dp, 8.dp).background(CorusColors.Secondary.copy(alpha = 0.15f), CircleShape))
                }
            }
            Spacer(Modifier.fillMaxWidth().height(32.dp).background(CorusColors.Accent.copy(alpha = 0.5f), CircleShape))
        }
        Column(Modifier.matchParentSize().clickable(onClick = onClick).background(MaterialTheme.colorScheme.surface.copy(alpha = 0.50f)).padding(14.dp),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            val count = access.remainingCount?.takeIf { it > 0 }
            Text(if (count != null) pluralStringResource(R.plurals.taste_discovery_remaining, count, count) else stringResource(R.string.taste_discovery_title),
                style = MaterialTheme.typography.titleSmall, textAlign = TextAlign.Center)
            Spacer(Modifier.height(16.dp))
            Text(stringResource(R.string.taste_discovery_cta), color = Color.White, style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.background(CorusColors.Accent, RoundedCornerShape(50)).padding(horizontal = 14.dp, vertical = 10.dp))
        }
    }
}
