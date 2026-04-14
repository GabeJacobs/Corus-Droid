package fm.corus.android.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import fm.corus.android.data.model.SnowIntensity
import kotlinx.coroutines.delay
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

private enum class SnowLayer { FOREGROUND, BACKGROUND }

private data class Snowflake(
    var x: Float,
    var y: Float,
    var radius: Float,
    var speed: Float,
    var opacity: Float,
    var layer: SnowLayer,
    var swayAmount: Float,
    var swaySpeed: Float,
    var swayPhase: Float,
    var driftX: Float,
)

@Composable
fun SnowEffectView(
    intensity: SnowIntensity,
    modifier: Modifier = Modifier,
) {
    if (intensity == SnowIntensity.OFF) return

    val isBlizzard = intensity == SnowIntensity.HEAVY

    var flakes by remember(intensity) {
        mutableStateOf(
            List(intensity.flakeCount) { createSnowflake(1f, 1f, isBlizzard) }
        )
    }
    var tick by remember { mutableIntStateOf(0) }
    var time by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(intensity) {
        while (true) {
            delay(16)
            tick++
            time += 0.016f
        }
    }

    Canvas(modifier = modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        val scale = density

        if (tick == 1) {
            flakes = List(intensity.flakeCount) { createSnowflake(w, h, isBlizzard, scale) }
        }

        val dt = 1f / 60f

        val updated = flakes.map { flake ->
            var newY = flake.y + flake.speed * dt
            var newX = flake.x + flake.driftX * dt

            if (newY > h + flake.radius + 10f) {
                return@map createSnowflake(w, h, isBlizzard, scale, fullHeight = false)
            }
            // Wrap horizontally
            if (newX < -15f) newX = w + 10f
            else if (newX > w + 15f) newX = -10f

            flake.copy(x = newX, y = newY)
        }
        flakes = updated

        // Draw background flakes first
        updated.filter { it.layer == SnowLayer.BACKGROUND }.forEach { flake ->
            val alpha = flake.opacity * 0.55f
            val radius = flake.radius * 0.7f
            val swayX = sin((time * flake.swaySpeed + flake.swayPhase).toDouble()).toFloat() * flake.swayAmount
            val x = flake.x + swayX
            val y = flake.y

            // Outer glow for larger flakes
            if (radius > 2.5f) {
                drawCircle(
                    color = Color.White.copy(alpha = alpha * 0.2f),
                    radius = radius * 1.2f,
                    center = Offset(x, y),
                )
            }

            // Main flake
            drawCircle(
                color = Color.White.copy(alpha = alpha),
                radius = radius,
                center = Offset(x, y),
            )

            // Center highlight
            drawCircle(
                color = Color.White.copy(alpha = min(alpha + 0.2f, 0.9f)),
                radius = radius * 0.4f,
                center = Offset(x, y),
            )
        }

        // Draw foreground flakes
        updated.filter { it.layer == SnowLayer.FOREGROUND }.forEach { flake ->
            val alpha = flake.opacity
            val radius = flake.radius
            val swayX = sin((time * flake.swaySpeed + flake.swayPhase).toDouble()).toFloat() * flake.swayAmount
            val x = flake.x + swayX
            val y = flake.y

            // Outer glow for larger flakes
            if (radius > 2.5f) {
                drawCircle(
                    color = Color.White.copy(alpha = alpha * 0.2f),
                    radius = radius * 1.2f,
                    center = Offset(x, y),
                )
            }

            // Main flake
            drawCircle(
                color = Color.White.copy(alpha = alpha),
                radius = radius,
                center = Offset(x, y),
            )

            // Center highlight
            drawCircle(
                color = Color.White.copy(alpha = min(alpha + 0.2f, 0.9f)),
                radius = radius * 0.4f,
                center = Offset(x, y),
            )
        }
    }
}

private fun createSnowflake(
    width: Float,
    height: Float,
    isBlizzard: Boolean,
    scale: Float = 1f,
    fullHeight: Boolean = true,
): Snowflake {
    val isForeground = Random.nextFloat() > 0.4f
    val layer = if (isForeground) SnowLayer.FOREGROUND else SnowLayer.BACKGROUND

    val speed: Float
    val radius: Float
    val opacity: Float

    when (layer) {
        SnowLayer.FOREGROUND -> {
            speed = if (isBlizzard) Random.nextFloat() * 80f + 120f else Random.nextFloat() * 40f + 40f  // 40-80 or 120-200
            radius = Random.nextFloat() * 2.5f + 2.0f  // 2.0-4.5
            opacity = Random.nextFloat() * 0.35f + 0.5f // 0.5-0.85
        }
        SnowLayer.BACKGROUND -> {
            speed = if (isBlizzard) Random.nextFloat() * 60f + 80f else Random.nextFloat() * 25f + 20f  // 20-45 or 80-140
            radius = Random.nextFloat() * 1.3f + 1.2f  // 1.2-2.5
            opacity = Random.nextFloat() * 0.25f + 0.25f // 0.25-0.5
        }
    }

    val x = Random.nextFloat() * (width + 20f) - 10f
    val y = if (fullHeight) {
        Random.nextFloat() * height * 1.5f - height * 0.5f
    } else {
        Random.nextFloat() * -35f - 5f
    }

    val driftX = if (isBlizzard) Random.nextFloat() * 40f + 40f else Random.nextFloat() * 12f - 6f  // -6..6 or 40-80
    val swayAmount = if (isBlizzard) Random.nextFloat() * 15f + 15f else Random.nextFloat() * 12f + 8f // 8-20 or 15-30
    val swaySpeed = if (isBlizzard) Random.nextFloat() * 2f + 2f else Random.nextFloat() * 1.2f + 0.8f // 0.8-2.0 or 2.0-4.0

    return Snowflake(
        x = x,
        y = y,
        radius = radius * scale,
        speed = speed * scale,
        opacity = opacity,
        layer = layer,
        swayAmount = swayAmount * scale,
        swaySpeed = swaySpeed,
        swayPhase = Random.nextFloat() * 6.28f,
        driftX = driftX * scale,
    )
}
