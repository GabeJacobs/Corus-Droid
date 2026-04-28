package fm.corus.android.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import fm.corus.android.R
import fm.corus.android.ui.theme.LocalCorusDarkTheme

// Mirrors iOS SoundCloudAdaptiveLogo: black on light, white on dark.
// Reads the app's theme (LocalCorusDarkTheme) rather than the system setting,
// since Corus's theme may differ from the OS-wide dark-mode flag.
@Composable
fun SoundCloudAdaptiveLogo(
    modifier: Modifier = Modifier,
    size: Dp = 28.dp,
) {
    val res = if (LocalCorusDarkTheme.current) R.drawable.soundcloud_white else R.drawable.soundcloud_black
    Image(
        painter = painterResource(res),
        contentDescription = null,
        modifier = modifier.size(size),
        contentScale = ContentScale.Fit,
    )
}

// Mirrors iOS song-search badge: 14pt logo on a frosted circle, 3pt inner + 3pt outer padding.
@Composable
fun SoundCloudBadgeOverlay(modifier: Modifier = Modifier) {
    val isDark = LocalCorusDarkTheme.current
    // Approximation of SwiftUI .ultraThinMaterial — light blur tint that contrasts the cloud.
    val frosted = if (isDark) Color.Black.copy(alpha = 0.35f) else Color.White.copy(alpha = 0.55f)
    androidx.compose.foundation.layout.Box(
        modifier = modifier.padding(3.dp),
    ) {
        androidx.compose.foundation.layout.Box(
            modifier = Modifier
                .clip(CircleShape)
                .background(frosted)
                .padding(3.dp),
        ) {
            SoundCloudAdaptiveLogo(size = 14.dp)
        }
    }
}
