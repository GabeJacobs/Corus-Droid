package fm.corus.android.ui.screens.live

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import fm.corus.android.localization.CorusStrings
import fm.corus.android.ui.components.parityCopy
import fm.corus.android.ui.components.rememberReducedMotion
import fm.corus.android.ui.theme.CorusColors
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.sin
import kotlin.random.Random

@Composable
internal fun LiveLikeButton(liked: Boolean, glyphSize: Dp, onLike: () -> Unit) {
    var reactions by remember { mutableLongStateOf(0) }
    Box(contentAlignment = Alignment.Center) {
        LiveIconButton(
            if (liked) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
            if (liked) parityCopy("Song liked") else stringResource(CorusStrings.corus_live_like_song),
            {
                reactions++
                onLike()
            },
            tint = if (liked) CorusColors.Like else CorusColors.Text,
            touchSize = 44.dp,
            glyphSize = glyphSize,
        )
        LiveHeartBurst(reactions)
    }
}

@Composable
internal fun LiveHeartBurst(reactions: Long, modifier: Modifier = Modifier) {
    if (rememberReducedMotion()) return

    var previous by remember { mutableLongStateOf(reactions) }
    val hearts = remember { mutableStateListOf<FloatingHeart>() }
    val tint = CorusColors.Like
    LaunchedEffect(reactions) {
        val count = (reactions - previous).coerceIn(0, 12).toInt() * 3
        previous = reactions
        if (count == 0) return@LaunchedEffect

        repeat((hearts.size + count - 36).coerceAtLeast(0)) { hearts.removeAt(0) }
        val stagger = minOf(40L, 400L / count)
        repeat(count) { index ->
            hearts.add(FloatingHeart(index * stagger + Random.nextLong(0, 41)))
        }
    }
    Box(modifier.size(24.dp), contentAlignment = Alignment.Center) {
        hearts.forEach { heart ->
            key(heart) {
                val progress = remember { Animatable(0f) }
                LaunchedEffect(heart) {
                    delay(heart.startDelay)
                    progress.animateTo(1f, tween(heart.duration, easing = LinearEasing))
                    hearts.remove(heart)
                }
                Icon(
                    Icons.Filled.Favorite, contentDescription = null, tint = tint,
                    modifier = Modifier.size(heart.size.dp).graphicsLayer {
                        val fraction = progress.value
                        val sway = sin(fraction * 2 * PI).toFloat() * fraction
                        translationX = (heart.drift * fraction + heart.sway * sway).dp.toPx()
                        translationY = (-heart.rise * fraction).dp.toPx()
                        rotationZ = heart.tilt * fraction
                        scaleX = .76f + .36f * fraction
                        scaleY = scaleX
                        alpha = minOf(fraction / .08f, 1f, (1f - fraction) / .42f)
                    },
                )
            }
        }
    }
}

private class FloatingHeart(val startDelay: Long) {
    val duration = Random.nextInt(1250, 1701)
    val size = Random.nextInt(13, 25)
    val rise = Random.nextInt(86, 149)
    val drift = Random.nextInt(-14, 33)
    val sway = Random.nextInt(-12, 13)
    val tilt = Random.nextInt(-25, 26)
}
