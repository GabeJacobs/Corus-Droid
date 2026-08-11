package fm.corus.android.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
 * Audiomack's official orange wave mark. Unlike [SoundCloudAdaptiveLogo], the
 * mark is full-color orange on transparency and reads on both light and dark,
 * so there is NO theme switch — always the same drawable. The mark is wider
 * than tall (~1.45:1), so it's sized by [height] and laid out at natural aspect
 * with [ContentScale.Fit] rather than squished into a square.
 */
private const val AUDIOMACK_MARK_ASPECT = 279f / 192f

@Composable
fun AudiomackLogo(
    modifier: Modifier = Modifier,
    height: Dp = 20.dp,
) {
    Image(
        painter = painterResource(R.drawable.audiomack_mark),
        contentDescription = null,
        modifier = modifier
            .height(height)
            .aspectRatio(AUDIOMACK_MARK_ASPECT),
        contentScale = ContentScale.Fit,
    )
}

/**
 * Search-row / album-art badge: the Audiomack mark on a small frosted chip,
 * mirroring [SoundCloudBadgeOverlay]'s 3dp outer + 3dp inner padding. Uses a
 * rounded-rect (not a circle) so the wide mark isn't shrunk to fit a diameter.
 */
@Composable
fun AudiomackBadgeOverlay(modifier: Modifier = Modifier) {
    val isDark = LocalCorusDarkTheme.current
    // Approximation of SwiftUI .ultraThinMaterial — a light blur tint that lifts
    // the orange mark off busy album art.
    val frosted = if (isDark) Color.Black.copy(alpha = 0.35f) else Color.White.copy(alpha = 0.55f)
    androidx.compose.foundation.layout.Box(
        modifier = modifier.padding(3.dp),
    ) {
        androidx.compose.foundation.layout.Box(
            modifier = Modifier
                .clip(RoundedCornerShape(4.dp))
                .background(frosted)
                .padding(horizontal = 4.dp, vertical = 3.dp),
        ) {
            AudiomackLogo(height = 12.dp)
        }
    }
}
