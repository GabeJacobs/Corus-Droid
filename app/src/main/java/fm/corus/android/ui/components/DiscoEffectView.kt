package fm.corus.android.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.rotate
import fm.corus.android.data.model.DiscoIntensity
import kotlinx.coroutines.delay
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

private val DISCO_COLORS = listOf(
    Color(0xFFFF69B4), // pink
    Color(0xFF6495ED), // blue
    Color(0xFF9B59B6), // purple
    Color(0xFF2ED573), // green
    Color(0xFFFFD700), // gold
)

private data class DiscoBeam(
    val baseAngle: Float,
    val sweepRange: Float,
    val sweepSpeed: Float,
    val sweepPhase: Float,
    val width: Float,
    val pulseSpeed: Float,
    val pulsePhase: Float,
    val color: Color,
)

private data class MirrorPatch(
    val orbitRadiusX: Float,
    val orbitRadiusY: Float,
    val orbitSpeed: Float,
    val orbitPhase: Float,
    val size: Float,
    val twinkleSpeed: Float,
    val twinklePhase: Float,
    val color: Color,
)

@Composable
fun DiscoEffectView(
    intensity: DiscoIntensity,
    modifier: Modifier = Modifier,
) {
    if (intensity == DiscoIntensity.OFF) return

    val isHeavy = intensity == DiscoIntensity.HEAVY
    val beamCount = intensity.beamCount
    val lightCount = intensity.lightCount

    val beams = remember(intensity) {
        List(beamCount) {
            DiscoBeam(
                baseAngle = Random.nextFloat() * 360f,
                sweepRange = Random.nextFloat() * 30f + 15f,
                sweepSpeed = (Random.nextFloat() * 0.5f + 0.3f) * if (isHeavy) 1.5f else 1f,
                sweepPhase = Random.nextFloat() * 6.28f,
                width = Random.nextFloat() * 8f + 4f,
                pulseSpeed = Random.nextFloat() * 2f + 1f,
                pulsePhase = Random.nextFloat() * 6.28f,
                color = if (isHeavy) DISCO_COLORS[Random.nextInt(DISCO_COLORS.size)] else Color.White,
            )
        }
    }

    val patches = remember(intensity) {
        List(lightCount) {
            MirrorPatch(
                orbitRadiusX = Random.nextFloat() * 0.4f + 0.1f,
                orbitRadiusY = Random.nextFloat() * 0.4f + 0.1f,
                orbitSpeed = (Random.nextFloat() * 1f + 0.5f) * if (isHeavy) 1.3f else 1f,
                orbitPhase = Random.nextFloat() * 6.28f,
                size = Random.nextFloat() * 4f + 2f,
                twinkleSpeed = Random.nextFloat() * 3f + 1f,
                twinklePhase = Random.nextFloat() * 6.28f,
                color = if (isHeavy) DISCO_COLORS[Random.nextInt(DISCO_COLORS.size)] else Color.White,
            )
        }
    }

    var time by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(intensity) {
        while (true) {
            delay(16)
            time += 0.016f
        }
    }

    Canvas(modifier = modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        val ballCenter = Offset(w / 2f, -h * 0.15f)

        // Draw beams
        beams.forEach { beam ->
            val angle = beam.baseAngle + sin((time * beam.sweepSpeed + beam.sweepPhase).toDouble()).toFloat() * beam.sweepRange
            val pulse = (sin((time * beam.pulseSpeed + beam.pulsePhase).toDouble()).toFloat() + 1f) / 2f
            val alpha = 0.05f + pulse * 0.1f

            val length = maxOf(w, h) * 1.5f
            val rad = Math.toRadians(angle.toDouble())
            val endX = ballCenter.x + cos(rad).toFloat() * length
            val endY = ballCenter.y + sin(rad).toFloat() * length

            drawLine(
                brush = Brush.linearGradient(
                    colors = listOf(beam.color.copy(alpha = alpha), beam.color.copy(alpha = 0f)),
                    start = ballCenter,
                    end = Offset(endX, endY),
                ),
                start = ballCenter,
                end = Offset(endX, endY),
                strokeWidth = beam.width,
            )
        }

        // Draw mirror patches
        patches.forEach { patch ->
            val cx = w / 2f + cos((time * patch.orbitSpeed + patch.orbitPhase).toDouble()).toFloat() * w * patch.orbitRadiusX
            val cy = h / 2f + sin((time * patch.orbitSpeed + patch.orbitPhase).toDouble()).toFloat() * h * patch.orbitRadiusY
            val twinkle = (sin((time * patch.twinkleSpeed + patch.twinklePhase).toDouble()).toFloat() + 1f) / 2f
            val alpha = 0.15f + twinkle * 0.4f

            drawCircle(
                color = patch.color.copy(alpha = alpha),
                radius = patch.size,
                center = Offset(cx, cy),
            )
            // Glow
            drawCircle(
                color = patch.color.copy(alpha = alpha * 0.3f),
                radius = patch.size * 2.5f,
                center = Offset(cx, cy),
            )
        }
    }
}
