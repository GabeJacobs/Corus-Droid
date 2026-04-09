package fm.corus.android.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import fm.corus.android.data.model.SnowIntensity
import kotlinx.coroutines.delay
import kotlin.math.sin
import kotlin.random.Random

private data class Snowflake(
    var x: Float,
    var y: Float,
    var radius: Float,
    var speed: Float,
    var opacity: Float,
    var swayAmount: Float,
    var swaySpeed: Float,
    var swayPhase: Float,
    var drift: Float,
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

        if (tick == 1) {
            flakes = List(intensity.flakeCount) { createSnowflake(w, h, isBlizzard) }
        }

        val updated = flakes.map { flake ->
            var newY = flake.y + flake.speed
            var newX = flake.x + sin((time * flake.swaySpeed + flake.swayPhase).toDouble()).toFloat() * flake.swayAmount + flake.drift

            if (newY > h + flake.radius) {
                newY = -flake.radius
                newX = Random.nextFloat() * w
            }
            // Horizontal wrapping
            if (newX > w + flake.radius) newX -= w + flake.radius * 2
            if (newX < -flake.radius) newX += w + flake.radius * 2

            flake.copy(x = newX, y = newY)
        }
        flakes = updated

        updated.forEach { flake ->
            drawCircle(
                color = Color.White.copy(alpha = flake.opacity),
                radius = flake.radius,
                center = Offset(flake.x, flake.y),
            )
            // Glow
            drawCircle(
                color = Color.White.copy(alpha = flake.opacity * 0.3f),
                radius = flake.radius * 1.8f,
                center = Offset(flake.x, flake.y),
            )
        }
    }
}

private fun createSnowflake(width: Float, height: Float, isBlizzard: Boolean): Snowflake {
    val speedMultiplier = if (isBlizzard) 2.5f else 1f
    val driftMultiplier = if (isBlizzard) 3f else 1f
    val swayMultiplier = if (isBlizzard) 2f else 1f

    return Snowflake(
        x = Random.nextFloat() * width,
        y = Random.nextFloat() * height * 1.5f - height * 0.5f,
        radius = Random.nextFloat() * 3f + 1.5f,
        speed = (Random.nextFloat() * 1.5f + 0.5f) * speedMultiplier,
        opacity = Random.nextFloat() * 0.4f + 0.3f,
        swayAmount = (Random.nextFloat() * 2f + 0.5f) * swayMultiplier,
        swaySpeed = Random.nextFloat() * 2f + 1f,
        swayPhase = Random.nextFloat() * 6.28f,
        drift = (Random.nextFloat() * 0.5f - 0.25f) * driftMultiplier,
    )
}
