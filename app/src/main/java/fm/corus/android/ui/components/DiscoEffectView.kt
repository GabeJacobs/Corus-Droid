package fm.corus.android.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import fm.corus.android.data.model.DiscoIntensity
import fm.corus.android.ui.theme.LocalCorusDarkTheme

@Composable
fun DiscoEffectView(
    intensity: DiscoIntensity,
    modifier: Modifier = Modifier,
) {
    if (intensity == DiscoIntensity.OFF) return

    val renderer = remember(intensity) { DiscoBallRenderer(intensity) }
    val appearanceGain = if (LocalCorusDarkTheme.current) 1f else DiscoBallRenderer.LIGHT_MODE_GAIN

    var time by remember { mutableDoubleStateOf(0.0) }
    LaunchedEffect(intensity) {
        val start = withFrameNanos { it }
        while (true) {
            val now = withFrameNanos { it }
            time = (now - start) / 1_000_000_000.0
        }
    }

    val density = LocalDensity.current
    val topFadePx = with(density) {
        when (intensity) {
            DiscoIntensity.DISCO_BALL -> 3
            DiscoIntensity.DANCE_PARTY -> 16
            else -> 56
        }.dp.toPx()
    }
    val edgeFadePx = with(density) { 16.dp.toPx() }

    Canvas(
        modifier = modifier
            .fillMaxSize()
            .clipToBounds()
            .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen },
    ) {
        renderer.draw(this, time, appearanceGain)
        val w = size.width
        val h = size.height
        if (w <= 0f || h <= 0f) return@Canvas
        val topT = (topFadePx / h).coerceIn(
            if (intensity == DiscoIntensity.DISCO_BALL) 0.005f else 0.02f,
            0.4f,
        )
        val bottomT = (edgeFadePx / h).coerceIn(0.01f, 0.2f)
        val sideT = (edgeFadePx / w).coerceIn(0.01f, 0.2f)
        drawRect(
            brush = Brush.verticalGradient(
                colorStops = arrayOf(
                    0f to Color.Transparent,
                    topT to Color.White,
                    1f - bottomT to Color.White,
                    1f to Color.Transparent,
                ),
            ),
            blendMode = BlendMode.DstIn,
        )
        drawRect(
            brush = Brush.horizontalGradient(
                colorStops = arrayOf(
                    0f to Color.Transparent,
                    sideT to Color.White,
                    1f - sideT to Color.White,
                    1f to Color.Transparent,
                ),
            ),
            blendMode = BlendMode.DstIn,
        )
    }
}

@Composable
fun DiscoScrim(
    intensity: DiscoIntensity,
    modifier: Modifier = Modifier,
) {
    if (intensity == DiscoIntensity.OFF) return
    var time by remember { mutableDoubleStateOf(0.0) }
    LaunchedEffect(intensity) {
        if (intensity != DiscoIntensity.DANCE_PARTY) return@LaunchedEffect
        val start = withFrameNanos { it }
        while (true) {
            val now = withFrameNanos { it }
            time = (now - start) / 1_000_000_000.0
        }
    }
    Box(
        modifier = modifier.background(
            intensity.scrimColor(time).copy(alpha = intensity.scrimOpacity),
        ),
    )
}
