package fm.corus.android.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

/**
 * Feed PostCard double-tap-to-like heart burst.
 *
 * Shared by the feed card, featured poster, and full-player album art so the
 * scale / fade timing stays identical.
 */
suspend fun playDoubleTapLikeHeartAnimation(
    scale: Animatable<Float, AnimationVector1D>,
    alpha: Animatable<Float, AnimationVector1D>,
) {
    scale.snapTo(0f)
    alpha.snapTo(1f)
    scale.animateTo(1f, animationSpec = tween(300))
    delay(400)
    alpha.animateTo(0f, animationSpec = tween(300))
}

@Composable
fun DoubleTapLikeHeartIcon(
    scale: Float,
    alpha: Float,
    modifier: Modifier = Modifier,
) {
    Icon(
        imageVector = Icons.Filled.Favorite,
        contentDescription = null,
        tint = Color.White,
        modifier = modifier
            .size(80.dp)
            .scale(scale)
            .alpha(alpha),
    )
}
