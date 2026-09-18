package fm.corus.android.ui.screens.map

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOff
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Public
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import fm.corus.android.ui.components.parityCopy
import fm.corus.android.ui.theme.CorusColors
import fm.corus.android.ui.theme.CorusFont

@Composable
fun MapIntroBenefitRow(icon: ImageVector, title: String, body: String) {
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(icon, contentDescription = null, tint = CorusColors.Accent, modifier = Modifier.size(22.dp).padding(top = 1.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(parityCopy(title), style = CorusFont.bodyMedium)
            Text(parityCopy(body), style = CorusFont.caption, color = CorusColors.Secondary)
        }
    }
}

@Composable
fun MapAudienceOption(value: String, title: String, subtitle: String, selected: Boolean, onSelect: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp))
            .background(if (selected) CorusColors.Accent.copy(alpha = .10f) else CorusColors.CardBackground)
            .clickable(onClick = onSelect).padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        val icon = when (value) {
            "everyone" -> Icons.Default.Public
            "following" -> Icons.Default.People
            else -> Icons.Default.LocationOff
        }
        Box(Modifier.size(42.dp).clip(CircleShape).background(CorusColors.Accent.copy(alpha = .14f)), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = CorusColors.Accent)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(parityCopy(title), style = CorusFont.bodyMedium)
            Text(parityCopy(subtitle), style = CorusFont.caption, color = CorusColors.Secondary)
        }
        RadioButton(selected = selected, onClick = onSelect)
    }
}

@Composable
fun MapSheetPrimaryButton(
    onClick: () -> Unit,
    enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth().height(56.dp).navigationBarsPadding(),
        shape = RoundedCornerShape(28.dp),
        content = content,
    )
}
