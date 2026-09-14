package fm.corus.android.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.border
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.ui.semantics.Role
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.style.TextAlign
import fm.corus.android.ui.theme.CorusFont
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import fm.corus.android.R
import fm.corus.android.data.model.TasteDiscoveryAccess
import fm.corus.android.data.repository.SubscriptionRepository
import fm.corus.android.ui.theme.CorusColors
import javax.inject.Inject

@Composable
fun TasteDiscoveryClubCard(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    access: TasteDiscoveryAccess = TasteDiscoveryAccess(),
    viewModel: TasteDiscoveryClubCardViewModel = hiltViewModel(),
) {
    val hasIntroTrial by viewModel.hasIntroTrial.collectAsState()
    val cardShape = RoundedCornerShape(20.dp)
    Box(
        modifier
            .shadow(elevation = 2.dp, shape = cardShape, ambientColor = Color.Black.copy(alpha = 0.04f))
            .clip(cardShape)
            .border(0.5.dp, CorusColors.Secondary.copy(alpha = 0.15f), cardShape),
    ) {
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
        Column(Modifier.matchParentSize().background(MaterialTheme.colorScheme.surface.copy(alpha = 0.88f)).clickable(
            interactionSource = remember { MutableInteractionSource() }, indication = null, role = Role.Button, onClick = onClick).padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Column(Modifier.fillMaxWidth().weight(1f).padding(horizontal = 4.dp), horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterVertically)) {
            Box(Modifier.size(48.dp).background(MaterialTheme.colorScheme.surface, CircleShape), contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.Lock, contentDescription = null, tint = CorusColors.Accent, modifier = Modifier.size(22.dp))
            }
            Text(stringResource(R.string.taste_discovery_locked_card_label),
                style = CorusFont.buttonSmall, textAlign = TextAlign.Center)
            if (hasIntroTrial) {
                Text(
                    stringResource(R.string.taste_discovery_locked_card_trial),
                    color = CorusColors.Secondary,
                    style = CorusFont.captionMedium,
                    textAlign = TextAlign.Center,
                )
            }
            }
            Text(stringResource(R.string.taste_discovery_cta), color = Color.White, style = CorusFont.buttonSmall, textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().height(32.dp).background(CorusColors.Accent, RoundedCornerShape(50)).wrapContentHeight(Alignment.CenterVertically))
        }
    }
}

@HiltViewModel
class TasteDiscoveryClubCardViewModel @Inject constructor(
    subscriptionRepository: SubscriptionRepository,
) : ViewModel() {
    val hasIntroTrial = subscriptionRepository.hasClubIntroTrial

    init {
        subscriptionRepository.fetchOfferings()
    }
}
