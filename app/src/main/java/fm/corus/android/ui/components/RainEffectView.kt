package fm.corus.android.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import fm.corus.android.data.model.RainIntensity
import kotlinx.coroutines.delay
import kotlin.math.tan
import kotlin.random.Random

private data class Raindrop(
    var x: Float,
    var y: Float,
    var speed: Float,
    var length: Float,
    var opacity: Float,
    var isForeground: Boolean,
)

private const val WIND_ANGLE = 15f // degrees
private val WIND_DX = tan(Math.toRadians(WIND_ANGLE.toDouble())).toFloat()

@Composable
fun RainEffectView(
    intensity: RainIntensity,
    modifier: Modifier = Modifier,
) {
    if (intensity == RainIntensity.OFF) return

    var drops by remember(intensity) {
        mutableStateOf(
            List(intensity.dropCount) { createRaindrop(1f, 1f) }
        )
    }
    var tick by remember { mutableIntStateOf(0) }

    LaunchedEffect(intensity) {
        while (true) {
            delay(16) // ~60fps
            tick++
        }
    }

    Canvas(modifier = modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height

        // Re-init drops with actual dimensions on first meaningful tick
        if (tick == 1) {
            drops = List(intensity.dropCount) { createRaindrop(w, h) }
        }

        val updated = drops.map { drop ->
            var newY = drop.y + drop.speed
            var newX = drop.x + drop.speed * WIND_DX * 0.3f

            if (newY > h) {
                newY = Random.nextFloat() * -100f
                newX = Random.nextFloat() * w
            }
            if (newX > w) newX -= w
            if (newX < 0) newX += w

            drop.copy(x = newX, y = newY)
        }
        drops = updated

        updated.forEach { drop ->
            val alpha = if (drop.isForeground) drop.opacity else drop.opacity * 0.5f
            val endX = drop.x + drop.length * WIND_DX * 0.3f
            val endY = drop.y + drop.length

            drawLine(
                color = Color.White.copy(alpha = alpha),
                start = Offset(drop.x, drop.y),
                end = Offset(endX, endY),
                strokeWidth = if (drop.isForeground) 1.5f else 1f,
            )
        }
    }
}

private fun createRaindrop(width: Float, height: Float): Raindrop {
    return Raindrop(
        x = Random.nextFloat() * width,
        y = Random.nextFloat() * height * 1.5f - height * 0.5f,
        speed = Random.nextFloat() * 8f + 6f,
        length = Random.nextFloat() * 15f + 10f,
        opacity = Random.nextFloat() * 0.3f + 0.2f,
        isForeground = Random.nextBoolean(),
    )
}
