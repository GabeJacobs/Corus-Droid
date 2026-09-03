package fm.corus.android.ui.theme

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.valentinilk.shimmer.ShimmerTheme
import com.valentinilk.shimmer.shimmerSpec

/**
 * Overlay used by `.shimmer()` skeleton bones.
 *
 * compose-shimmer's default is `DstIn` at 0.25 alpha, then a 1.5s *hold* at
 * that dim frame between sweeps. Dark-mode bones are `#2C2C2E` on a black
 * background, so 25% opacity composites to nearly black — the placeholders
 * vanish for most of the loop (especially obvious on Search → Users).
 *
 * Dark mode keeps a higher floor so the bones stay readable. The hold is
 * dropped in both themes so the sweep doesn't park on the dimmest frame.
 */
internal fun corusShimmerTheme(darkTheme: Boolean): ShimmerTheme {
    val lowAlpha = if (darkTheme) DARK_SHIMMER_LOW_ALPHA else LIGHT_SHIMMER_LOW_ALPHA
    return ShimmerTheme(
        animationSpec = infiniteRepeatable(
            animation = shimmerSpec(
                durationMillis = SHIMMER_SWEEP_MS,
                easing = LinearEasing,
                delayMillis = 0,
            ),
            repeatMode = RepeatMode.Restart,
        ),
        blendMode = BlendMode.DstIn,
        rotation = 15.0f,
        shaderColors = listOf(
            Color.Unspecified.copy(alpha = lowAlpha),
            Color.Unspecified.copy(alpha = 1.00f),
            Color.Unspecified.copy(alpha = lowAlpha),
        ),
        shaderColorStops = listOf(0.0f, 0.5f, 1.0f),
        shimmerWidth = 400.dp,
    )
}

internal const val DARK_SHIMMER_LOW_ALPHA = 0.65f
internal const val LIGHT_SHIMMER_LOW_ALPHA = 0.25f
private const val SHIMMER_SWEEP_MS = 800
