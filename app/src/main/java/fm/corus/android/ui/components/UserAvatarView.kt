package fm.corus.android.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import fm.corus.android.ui.theme.CorusColors
import fm.corus.android.ui.theme.CorusSpacing

@Composable
fun UserAvatarView(
    avatarURL: String?,
    avatarThumbURL: String? = null,
    size: Dp = CorusSpacing.avatarMedium,
    modifier: Modifier = Modifier,
) {
    // Use thumbnail for small avatars (feed circles, likes, comments), full res for larger displays
    val resolvedURL = if (size <= 36.dp && !avatarThumbURL.isNullOrBlank()) avatarThumbURL else avatarURL

    if (resolvedURL.isNullOrBlank()) {
        Box(
            modifier = modifier
                .size(size)
                .clip(CircleShape)
                .background(CorusColors.CardBackground),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Filled.Person,
                contentDescription = "Avatar placeholder",
                modifier = Modifier.size(size * 0.5f),
                tint = CorusColors.Secondary,
            )
        }
    } else {
        AsyncImage(
            model = resolvedURL,
            contentDescription = "User avatar",
            modifier = modifier
                .size(size)
                .clip(CircleShape),
            contentScale = ContentScale.Crop,
        )
    }
}
