package fm.corus.android.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import fm.corus.android.ui.theme.CorusColors
import fm.corus.android.ui.theme.CorusSpacing

@Composable
fun UserAvatarView(
    avatarURL: String?,
    avatarThumbURL: String? = null,
    displayName: String? = null,
    size: Dp = CorusSpacing.avatarMedium,
    modifier: Modifier = Modifier,
) {
    // Use thumbnail for small avatars (feed circles, likes, comments), full res for larger displays
    val resolvedURL = if (size <= 36.dp && !avatarThumbURL.isNullOrBlank()) avatarThumbURL else avatarURL

    if (resolvedURL.isNullOrBlank()) {
        val initial = displayName?.firstOrNull()?.uppercaseChar()?.toString()
        Box(
            modifier = modifier
                .size(size)
                .clip(CircleShape)
                .background(CorusColors.CardBackground),
            contentAlignment = Alignment.Center,
        ) {
            val fontSize = with(LocalDensity.current) { (size * 0.4f).toSp() }
            Text(
                text = initial ?: "?",
                color = CorusColors.Secondary,
                fontSize = fontSize,
                fontWeight = FontWeight.Bold,
            )
        }
    } else {
        ShimmerAsyncImage(
            model = resolvedURL,
            contentDescription = "User avatar",
            modifier = modifier.size(size),
            shape = CircleShape,
        )
    }
}
