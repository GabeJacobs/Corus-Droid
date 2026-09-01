package fm.corus.android.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
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

/**
 * Official Bandcamp aqua-circle mark (white parallelogram). Full-color, no
 * theme switch — same asset iOS/web use. The mark is square.
 */
@Composable
fun BandcampLogo(
    modifier: Modifier = Modifier,
    size: Dp = 20.dp,
) {
    Image(
        painter = painterResource(R.drawable.bandcamp_mark),
        contentDescription = null,
        modifier = modifier.size(size),
        contentScale = ContentScale.Fit,
    )
}

/**
 * Search-row / album-art badge — mirrors iOS SearchView:
 * `BandcampLogo(size: 12, style: .logotype)` + 4pt pad + ultraThinMaterial
 * Capsule + 3pt outer.
 */
@Composable
fun BandcampBadgeOverlay(modifier: Modifier = Modifier) {
    val isDark = LocalCorusDarkTheme.current
    val frosted = if (isDark) Color.Black.copy(alpha = 0.35f) else Color.White.copy(alpha = 0.55f)
    Box(modifier = modifier.padding(3.dp)) {
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(percent = 50))
                .background(frosted)
                .padding(horizontal = 4.dp, vertical = 2.dp),
        ) {
            BandcampLogo(size = 12.dp)
        }
    }
}
