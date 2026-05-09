package fm.corus.android.ui.components

import android.provider.Settings
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.material3.Text
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

/**
 * Single-line text that auto-scrolls horizontally (ping-pong: scroll to the end,
 * pause, scroll back, pause, repeat) when it overflows the available width, and
 * renders normally with tail truncation when it fits. Honors the system
 * Reduce-Motion / animations-disabled setting.
 *
 * Mirrors the iOS [MarqueeText] component used in the now-playing mini player.
 */
@Composable
fun MarqueeText(
    text: String,
    style: TextStyle,
    color: Color,
    modifier: Modifier = Modifier,
    pointsPerSecond: Float = 12f,
    endpointPauseMs: Long = 2_500L,
    fadeWidth: Dp = 2.dp,
) {
    val density = LocalDensity.current
    val textMeasurer = rememberTextMeasurer()
    val reduceMotion = isSystemReduceMotionEnabled()

    val textWidthPx = remember(text, style) {
        textMeasurer.measure(
            text = text,
            style = style,
            softWrap = false,
        ).size.width.toFloat()
    }

    var containerWidthPx by remember { mutableFloatStateOf(0f) }
    val fadePx = with(density) { fadeWidth.toPx() }

    val overflows by remember {
        derivedStateOf { containerWidthPx > 0f && textWidthPx > containerWidthPx + 0.5f }
    }
    val shouldAnimate = overflows && !reduceMotion

    val offset = remember { Animatable(0f) }

    LaunchedEffect(text, shouldAnimate, textWidthPx, containerWidthPx) {
        if (!shouldAnimate) {
            offset.snapTo(0f)
            return@LaunchedEffect
        }
        val scrollDistance = textWidthPx - containerWidthPx
        if (scrollDistance <= 0f) {
            offset.snapTo(0f)
            return@LaunchedEffect
        }
        // Park the text past each fade so the leading/trailing characters sit
        // just outside the gradient at both endpoints.
        val startOffset = fadePx
        val endOffset = -(scrollDistance + fadePx)
        val travel = startOffset - endOffset
        val durationMs = (travel / pointsPerSecond * 1000f).roundToInt().coerceAtLeast(1)

        offset.snapTo(startOffset)
        delay(endpointPauseMs)
        while (true) {
            offset.animateTo(
                targetValue = endOffset,
                animationSpec = tween(durationMillis = durationMs, easing = LinearEasing),
            )
            delay(endpointPauseMs)
            offset.animateTo(
                targetValue = startOffset,
                animationSpec = tween(durationMillis = durationMs, easing = LinearEasing),
            )
            delay(endpointPauseMs)
        }
    }

    val edgeFadeModifier = if (shouldAnimate && fadePx > 0f) {
        Modifier
            .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
            .drawWithContent {
                drawContent()
                val w = size.width
                if (w <= 0f) return@drawWithContent
                val leadingFraction = (fadePx / w).coerceIn(0f, 0.5f)
                val trailingStart = (1f - leadingFraction).coerceIn(0.5f, 1f)
                drawRect(
                    brush = Brush.horizontalGradient(
                        0f to Color.Transparent,
                        leadingFraction to Color.Black,
                        trailingStart to Color.Black,
                        1f to Color.Transparent,
                    ),
                    blendMode = BlendMode.DstIn,
                )
            }
    } else {
        Modifier
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .onSizeChanged { containerWidthPx = it.width.toFloat() }
            .then(edgeFadeModifier),
    ) {
        if (shouldAnimate) {
            Text(
                text = text,
                style = style,
                color = color,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Visible,
                modifier = Modifier.offset { IntOffset(offset.value.roundToInt(), 0) },
            )
        } else {
            Text(
                text = text,
                style = style,
                color = color,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun isSystemReduceMotionEnabled(): Boolean {
    val context = LocalContext.current
    return remember(context) {
        runCatching {
            Settings.Global.getFloat(
                context.contentResolver,
                Settings.Global.ANIMATOR_DURATION_SCALE,
                1f,
            ) == 0f
        }.getOrDefault(false)
    }
}
