package fm.corus.android.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import fm.corus.android.data.model.RainIntensity
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random

internal enum class RainLayer { FOREGROUND, BACKGROUND }

internal data class Raindrop(
    var x: Float,
    var y: Float,
    var speed: Float,
    var length: Float,
    var opacity: Float,
    var layer: RainLayer,
)

internal data class Splash(
    val x: Float,
    val y: Float,
    val startTick: Int,
    val particleCount: Int,
    val baseAngle: Float,
    val maxSpread: Float,
)

private const val SPLASH_LIFETIME_TICKS = 18 // ~0.3s at 60fps

@Composable
fun RainEffectView(
    intensity: RainIntensity,
    modifier: Modifier = Modifier,
    windAngleDegrees: Float = 15f,
    particleDensity: Float = 1f,
    fallSpeedScale: Float = 1f,
    startsFilled: Boolean = false,
) {
    if (intensity == RainIntensity.OFF) return

    Box(modifier = modifier.fillMaxSize()) {
        RainCanvas(intensity, windAngleDegrees, particleDensity, fallSpeedScale, startsFilled, Modifier.fillMaxSize())
        if (intensity == RainIntensity.HEAVY) {
            LightningFlashOverlay(subtle = false)
        }
    }
}

@Composable
private fun RainCanvas(
    intensity: RainIntensity,
    windAngleDegrees: Float,
    particleDensity: Float,
    fallSpeedScale: Float,
    startsFilled: Boolean,
    modifier: Modifier = Modifier,
) {
    val windRadians = Math.toRadians(windAngleDegrees.toDouble()).toFloat()
    val windDx = sin(windRadians)
    val windDy = cos(windRadians)
    val dropCount = (intensity.dropCount * particleDensity).roundToInt()
    var drops by remember(intensity, dropCount, startsFilled) {
        mutableStateOf(
            List(dropCount) { createRaindrop(1f, 1f, intensity) }
        )
    }
    var splashes by remember(intensity, dropCount, startsFilled) { mutableStateOf(listOf<Splash>()) }
    var tick by remember(intensity, dropCount, startsFilled) { mutableIntStateOf(0) }

    LaunchedEffect(intensity, dropCount, startsFilled) {
        while (true) {
            delay(16) // ~60fps
            tick++
        }
    }

    Canvas(modifier = modifier.clipToBounds()) {
        val w = size.width
        val h = size.height
        val scale = density

        // Soft fade-in band at the top edge so drops don't visibly "pop in"
        // when they cross y=0. Mirrors the snow fix — Canvas clips its draws,
        // so a head at y<0 is invisible until it suddenly draws at y=0.
        // Wider than snow's band since rain falls much faster.
        val fadeBand = 50f * scale
        fun topFade(y: Float): Float =
            if (y >= fadeBand) 1f else (y / fadeBand).coerceIn(0f, 1f)

        if (tick == 1) {
            drops = List(dropCount) { createRaindrop(w, h, intensity, scale, startsFilled = startsFilled) }
        }

        val dt = fallSpeedScale / 60f
        val newSplashes = mutableListOf<Splash>()

        val updated = drops.map { drop ->
            var newX = drop.x + windDx * drop.speed * dt
            val newY = drop.y + windDy * drop.speed * dt

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
                return@map createRaindrop(w, h, intensity, scale, fullHeight = false, startsFilled = startsFilled)
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
            val alpha = drop.opacity * 0.5f * topFade(drop.y)
            val length = drop.length * 0.7f
            val dx = windDx * length
            val dy = windDy * length

            drawLine(
                color = Color.White.copy(alpha = alpha),
                start = Offset(drop.x, drop.y),
                end = Offset(drop.x - dx, drop.y - dy),
                strokeWidth = 1f * scale,
            )
        }

        // Draw foreground drops
        updated.filter { it.layer == RainLayer.FOREGROUND }.forEach { drop ->
            val dx = windDx * drop.length
            val dy = windDy * drop.length

            drawLine(
                color = Color.White.copy(alpha = drop.opacity * topFade(drop.y)),
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

internal fun createRaindrop(
    width: Float,
    height: Float,
    intensity: RainIntensity,
    scale: Float = 1f,
    fullHeight: Boolean = true,
    startsFilled: Boolean = false,
): Raindrop {
    val isForeground = Random.nextFloat() > 0.35f
    val layer = if (isForeground) RainLayer.FOREGROUND else RainLayer.BACKGROUND

    val speed: Float
    val length: Float
    val opacity: Float

    when {
        layer == RainLayer.FOREGROUND && intensity == RainIntensity.LIGHT -> {
            speed = Random.nextFloat() * 64f + 112f     // 112-176
            length = Random.nextFloat() * 8f + 12f      // 12-20
            opacity = Random.nextFloat() * 0.2f + 0.3f  // 0.3-0.5
        }
        layer == RainLayer.FOREGROUND && intensity == RainIntensity.MEDIUM -> {
            speed = Random.nextFloat() * 94f + 166f     // 166-260
            length = Random.nextFloat() * 10f + 16f      // 16-26
            opacity = Random.nextFloat() * 0.25f + 0.4f  // 0.4-0.65
        }
        layer == RainLayer.FOREGROUND -> { // HEAVY
            speed = Random.nextFloat() * 130f + 230f    // 230-360
            length = Random.nextFloat() * 12f + 18f      // 18-30
            opacity = Random.nextFloat() * 0.3f + 0.4f   // 0.4-0.7
        }
        layer == RainLayer.BACKGROUND && intensity == RainIntensity.LIGHT -> {
            speed = Random.nextFloat() * 56f + 64f      // 64-120
            length = Random.nextFloat() * 6f + 8f        // 8-14
            opacity = Random.nextFloat() * 0.15f + 0.15f // 0.15-0.3
        }
        layer == RainLayer.BACKGROUND && intensity == RainIntensity.MEDIUM -> {
            speed = Random.nextFloat() * 72f + 101f     // 101-173
            length = Random.nextFloat() * 8f + 10f       // 10-18
            opacity = Random.nextFloat() * 0.15f + 0.2f  // 0.2-0.35
        }
        else -> { // BACKGROUND HEAVY
            speed = Random.nextFloat() * 100f + 130f    // 130-230
            length = Random.nextFloat() * 8f + 12f       // 12-20
            opacity = Random.nextFloat() * 0.2f + 0.2f   // 0.2-0.4
        }
    }

    val x = Random.nextFloat() * (width + 40f) - 20f
    val y = when {
        fullHeight && startsFilled -> Random.nextFloat() * height
        fullHeight -> Random.nextFloat() * height * 1.5f - height * 0.5f
        // Keep filled overlays populated by recycling just above the viewport.
        startsFilled -> (-60f + Random.nextFloat() * 50f) * scale
        // Spawn well above the canvas so drops stagger in over time instead
        // of clustering at the top edge — pairs with the topFade band so
        // they fade in smoothly as they descend into view.
        else -> Random.nextFloat() * -210f - 40f
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
