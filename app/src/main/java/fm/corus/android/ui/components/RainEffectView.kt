package fm.corus.android.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import fm.corus.android.data.model.RainIntensity
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.tan
import kotlin.random.Random

private enum class RainLayer { FOREGROUND, BACKGROUND }

private data class Raindrop(
    var x: Float,
    var y: Float,
    var speed: Float,
    var length: Float,
    var opacity: Float,
    var layer: RainLayer,
)

private data class Splash(
    val x: Float,
    val y: Float,
    val startTick: Int,
    val particleCount: Int,
    val baseAngle: Float,
    val maxSpread: Float,
)

private const val WIND_ANGLE = 15f // degrees
private val WIND_RAD = Math.toRadians(WIND_ANGLE.toDouble()).toFloat()
private val WIND_DX = sin(WIND_RAD)
private val WIND_DY = cos(WIND_RAD)
private const val SPLASH_LIFETIME_TICKS = 18 // ~0.3s at 60fps

@Composable
fun RainEffectView(
    intensity: RainIntensity,
    modifier: Modifier = Modifier,
) {
    if (intensity == RainIntensity.OFF) return

    Box(modifier = modifier.fillMaxSize()) {
        RainCanvas(intensity = intensity, modifier = Modifier.fillMaxSize())
        if (intensity == RainIntensity.HEAVY) {
            LightningFlashOverlay(subtle = false)
        }
    }
}

@Composable
private fun RainCanvas(
    intensity: RainIntensity,
    modifier: Modifier = Modifier,
) {
    var drops by remember(intensity) {
        mutableStateOf(
            List(intensity.dropCount) { createRaindrop(1f, 1f, intensity) }
        )
    }
    var splashes by remember(intensity) { mutableStateOf(listOf<Splash>()) }
    var tick by remember(intensity) { mutableIntStateOf(0) }

    LaunchedEffect(intensity) {
        while (true) {
            delay(16) // ~60fps
            tick++
        }
    }

    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val scale = density

        if (tick == 1) {
            drops = List(intensity.dropCount) { createRaindrop(w, h, intensity, scale) }
        }

        val dt = 1f / 60f
        val newSplashes = mutableListOf<Splash>()

        val updated = drops.map { drop ->
            var newX = drop.x + WIND_DX * drop.speed * dt
            var newY = drop.y + WIND_DY * drop.speed * dt

            if (newY > h + 10f) {
                // Spawn splash with 30% chance
                if (Random.nextFloat() < 0.3f) {
                    newSplashes.add(
                        Splash(
                            x = drop.x,
                            y = h - Random.nextFloat() * 4f * scale,
                            startTick = tick,
                            particleCount = Random.nextInt(2, 5),
                            baseAngle = 0.314f, // ~pi * 0.1
                            maxSpread = (Random.nextFloat() * 4f + 4f) * scale,
                        )
                    )
                }
                return@map createRaindrop(w, h, intensity, scale, fullHeight = false)
            }
            if (newX > w + 10f) newX -= w + 20f
            if (newX < -10f) newX += w + 20f

            drop.copy(x = newX, y = newY)
        }
        drops = updated

        // Update splashes
        splashes = (splashes + newSplashes).filter { tick - it.startTick < SPLASH_LIFETIME_TICKS }

        // Draw background drops first
        updated.filter { it.layer == RainLayer.BACKGROUND }.forEach { drop ->
            val alpha = drop.opacity * 0.5f
            val length = drop.length * 0.7f
            val dx = WIND_DX * length
            val dy = WIND_DY * length

            drawLine(
                color = Color.White.copy(alpha = alpha),
                start = Offset(drop.x, drop.y),
                end = Offset(drop.x - dx, drop.y - dy),
                strokeWidth = 1f * scale,
            )
        }

        // Draw foreground drops
        updated.filter { it.layer == RainLayer.FOREGROUND }.forEach { drop ->
            val dx = WIND_DX * drop.length
            val dy = WIND_DY * drop.length

            drawLine(
                color = Color.White.copy(alpha = drop.opacity),
                start = Offset(drop.x, drop.y),
                end = Offset(drop.x - dx, drop.y - dy),
                strokeWidth = 1.5f * scale,
            )
        }

        // Draw splashes
        splashes.forEach { splash ->
            val age = tick - splash.startTick
            val progress = age.toFloat() / SPLASH_LIFETIME_TICKS
            val alpha = (1f - progress) * 0.6f

            for (i in 0 until splash.particleCount) {
                val angle = splash.baseAngle +
                    i.toFloat() * (Math.PI.toFloat() / (splash.particleCount - 1).coerceAtLeast(1)) -
                    Math.PI.toFloat() / 2f
                val spread = splash.maxSpread * progress
                val px = splash.x + cos(angle) * spread
                val py = splash.y - sin(angle) * spread * 0.5f

                drawCircle(
                    color = Color.White.copy(alpha = alpha),
                    radius = 1.5f * scale,
                    center = Offset(px, py),
                )
            }
        }
    }
}

@Composable
private fun LightningFlashOverlay(subtle: Boolean) {
    val flashAlpha = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(subtle) {
        while (true) {
            val wait = if (subtle) {
                Random.nextLong(8000, 16000)
            } else {
                Random.nextLong(3000, 8000)
            }
            delay(wait)

            // First flash
            scope.launch {
                flashAlpha.animateTo(
                    Random.nextFloat() * 0.2f + 0.25f,
                    animationSpec = tween(50)
                )
                flashAlpha.animateTo(0f, animationSpec = tween(100))
            }
            delay(220)
            // Second flash (dimmer)
            scope.launch {
                flashAlpha.animateTo(
                    Random.nextFloat() * 0.15f + 0.15f,
                    animationSpec = tween(40)
                )
                flashAlpha.animateTo(0f, animationSpec = tween(150))
            }
        }
    }

    Canvas(modifier = Modifier.fillMaxSize()) {
        if (flashAlpha.value > 0f) {
            drawRect(Color.White.copy(alpha = flashAlpha.value))
        }
    }
}

private fun createRaindrop(
    width: Float,
    height: Float,
    intensity: RainIntensity,
    scale: Float = 1f,
    fullHeight: Boolean = true,
): Raindrop {
    val isForeground = Random.nextFloat() > 0.35f
    val layer = if (isForeground) RainLayer.FOREGROUND else RainLayer.BACKGROUND

    val speed: Float
    val length: Float
    val opacity: Float

    when {
        layer == RainLayer.FOREGROUND && intensity == RainIntensity.LIGHT -> {
            speed = Random.nextFloat() * 80f + 140f    // 140-220
            length = Random.nextFloat() * 8f + 12f      // 12-20
            opacity = Random.nextFloat() * 0.2f + 0.3f  // 0.3-0.5
        }
        layer == RainLayer.FOREGROUND && intensity == RainIntensity.MEDIUM -> {
            speed = Random.nextFloat() * 130f + 230f    // 230-360
            length = Random.nextFloat() * 10f + 16f      // 16-26
            opacity = Random.nextFloat() * 0.25f + 0.4f  // 0.4-0.65
        }
        layer == RainLayer.FOREGROUND -> { // HEAVY
            speed = Random.nextFloat() * 180f + 320f    // 320-500
            length = Random.nextFloat() * 12f + 18f      // 18-30
            opacity = Random.nextFloat() * 0.3f + 0.4f   // 0.4-0.7
        }
        layer == RainLayer.BACKGROUND && intensity == RainIntensity.LIGHT -> {
            speed = Random.nextFloat() * 70f + 80f      // 80-150
            length = Random.nextFloat() * 6f + 8f        // 8-14
            opacity = Random.nextFloat() * 0.15f + 0.15f // 0.15-0.3
        }
        layer == RainLayer.BACKGROUND && intensity == RainIntensity.MEDIUM -> {
            speed = Random.nextFloat() * 100f + 140f    // 140-240
            length = Random.nextFloat() * 8f + 10f       // 10-18
            opacity = Random.nextFloat() * 0.15f + 0.2f  // 0.2-0.35
        }
        else -> { // BACKGROUND HEAVY
            speed = Random.nextFloat() * 140f + 180f    // 180-320
            length = Random.nextFloat() * 8f + 12f       // 12-20
            opacity = Random.nextFloat() * 0.2f + 0.2f   // 0.2-0.4
        }
    }

    val x = Random.nextFloat() * (width + 40f) - 20f
    val y = if (fullHeight) {
        Random.nextFloat() * height * 1.5f - height * 0.5f
    } else {
        Random.nextFloat() * -50f - 10f
    }

    return Raindrop(
        x = x,
        y = y,
        speed = speed * scale,
        length = length * scale,
        opacity = opacity,
        layer = layer,
    )
}
