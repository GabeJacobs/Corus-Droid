package fm.corus.android.ui.screens.settings

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.People
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import fm.corus.android.localization.CorusStrings as S
import fm.corus.android.ui.theme.CorusColors
import fm.corus.android.ui.theme.CorusFont

@Composable
internal fun ReferralHistoryEmptyState(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
    ) {
        Icon(Icons.Default.People, null, Modifier.size(48.dp), tint = CorusColors.Tertiary)
        Text(
            stringResource(S.referral_empty),
            color = CorusColors.Secondary,
            style = CorusFont.bodyMedium,
        )
        Text(
            stringResource(S.referral_empty_body),
            modifier = Modifier.widthIn(max = 260.dp),
            textAlign = TextAlign.Center,
            color = CorusColors.Tertiary,
            style = CorusFont.body,
        )
    }
}
