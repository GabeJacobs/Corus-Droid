package fm.corus.android.ui.components

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.withTransform
import fm.corus.android.data.model.DiscoIntensity
import fm.corus.android.data.model.DiscoMark
import fm.corus.android.data.model.DiscoPalette
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Draws the light a mirror ball throws, as a pure function of time.
 * Port of the iOS `DiscoBallRenderer` — same geometry, same recipes.
 */
class DiscoBallRenderer(
    private val intensity: DiscoIntensity,
) {
    private val facets: List<Facet> = makeFacets(intensity)
    private val lights: List<Spotlight> = makeLights(intensity)

    fun draw(scope: DrawScope, time: Double, appearanceGain: Float = 1f) {
        val width = scope.size.width
        val height = scope.size.height
        if (intensity == DiscoIntensity.OFF || width <= 0f || height <= 0f) return
        if (facets.isEmpty()) return

        val radius = width * BALL_RADIUS_FRACTION
        val center = Offset(width * BALL_CENTER_X_FRACTION, radius * BALL_CENTER_Y_RADII)
        val spin = time * intensity.rotationSpeed
        val needsDots = intensity.mark != DiscoMark.NONE || intensity.beamShare > 0
        val dots = if (needsDots) {
            projectDots(spin, time, center, radius, width, height, appearanceGain)
        } else {
            emptyList()
        }

        scope.drawSpill(time, width, height, appearanceGain)
        if (intensity.hasSearchlights) scope.drawSearchlights(time, time, width, height, appearanceGain)
        scope.drawBeams(dots, center, radius, height)
        if (intensity.mark != DiscoMark.NONE) {
            for (dot in dots) scope.drawTile(dot)
        }
    }

    private fun DrawScope.drawSpill(time: Double, width: Float, height: Float, appearanceGain: Float) {
        val strength = intensity.spillStrength
        if (strength <= 0) return
        val origin = Offset(width * 0.52f, height * 0.18f)
        val reach = min(width, height) * 0.50f
        val tint = intensity.spillTint(time)
        val lift = (strength * VISIBILITY * appearanceGain).toFloat()
        drawOval(
            brush = Brush.radialGradient(
                colorStops = arrayOf(
                    0f to tint.copy(alpha = lift),
                    0.4f to tint.copy(alpha = lift * 0.35f),
                    1f to tint.copy(alpha = 0f),
                ),
                center = origin,
                radius = reach,
            ),
            topLeft = Offset(origin.x - reach, origin.y - reach * 0.7f),
            size = Size(reach * 2f, reach * 1.6f),
            blendMode = ADD,
        )
    }

    private data class ProjectedDot(
        val point: Offset,
        val size: Float,
        val rotation: Double,
        val alpha: Double,
        val color: Color,
        val castsBeam: Boolean,
    )

    private fun projectDots(
        spin: Double,
        time: Double,
        center: Offset,
        radius: Float,
        width: Float,
        height: Float,
        appearanceGain: Float,
    ): List<ProjectedDot> {
        val dots = ArrayList<ProjectedDot>(facets.size)
        val fadeBand = radius * 3.4f
        val minX = -fadeBand
        val maxX = width + fadeBand
        val minY = -fadeBand
        val maxY = height + fadeBand

        for (facet in facets) {
            if (dots.size >= MAX_DOTS) break
            val normal = facet.normal(spin)
            for (light in lights) {
                val incidence = light.direction.dot(normal)
                val facing = smoothStep(0.03, 0.22, -incidence)
                if (facing <= 0.01) continue

                val reflected = light.direction - normal * (2 * incidence)
                val towardWall = smoothStep(0.03, 0.20, -reflected.z)
                if (towardWall <= 0.01) continue

                val origin = DiscoVec3(normal.x, normal.y, WALL_DISTANCE + normal.z)
                val travel = -origin.z / reflected.z
                if (travel <= 0) continue
                val travelFade = 1.0 - smoothStep(MAX_BEAM_TRAVEL * 0.65, MAX_BEAM_TRAVEL, travel)
                if (travelFade <= 0.01) continue

                val hit = Offset(
                    center.x + ((origin.x + travel * reflected.x) * radius).toFloat(),
                    center.y - ((origin.y + travel * reflected.y) * radius).toFloat(),
                )
                if (hit.x !in minX..maxX || hit.y !in minY..maxY) continue

                val edge = edgeFade(hit, fadeBand, width, height)
                val falloff = 1.0 / (1.0 + (travel / BEAM_REACH).pow(2.0))
                val pulse = light.pulse(time, intensity.pulseRate)
                val gain = (if (intensity.palette == DiscoPalette.RAINBOW) DOT_GAIN * 1.15 else DOT_GAIN) * appearanceGain
                val alpha = (-incidence).pow(0.22) * falloff * light.intensity *
                    pulse * facet.polish * gain *
                    facing * towardWall * travelFade * edge
                if (alpha <= 0.012) continue

                dots += ProjectedDot(
                    point = hit,
                    size = (facet.width * radius * intensity.dotScale * facet.spread *
                        (1 + travel * BEAM_DIVERGENCE)).toFloat(),
                    rotation = atan2(
                        (hit.y - center.y).toDouble(),
                        (hit.x - center.x).toDouble(),
                    ),
                    alpha = min(alpha, 1.0),
                    color = light.color,
                    castsBeam = facet.castsBeam,
                )
            }
        }
        return dots
    }

    private fun DrawScope.drawTile(dot: ProjectedDot) {
        val core = max(dot.size, 2f)
        val alpha = dot.alpha.toFloat()
        val rainbow = intensity.palette == DiscoPalette.RAINBOW
        val degrees = Math.toDegrees(dot.rotation).toFloat()

        withTransform({
            translate(dot.point.x, dot.point.y)
            rotate(degrees, pivot = Offset.Zero)
        }) {
            val halo = core * if (rainbow) 5.2f else 6.5f
            drawOval(
                brush = Brush.radialGradient(
                    colorStops = arrayOf(
                        0f to dot.color.copy(alpha = alpha * if (rainbow) 0.32f else 0.28f),
                        0.45f to dot.color.copy(alpha = alpha * if (rainbow) 0.14f else 0.12f),
                        1f to dot.color.copy(alpha = 0f),
                    ),
                    center = Offset.Zero,
                    radius = halo * 0.6f,
                ),
                topLeft = Offset(-halo * 0.7f, -halo * 0.5f),
                size = Size(halo * 1.4f, halo),
                blendMode = ADD,
            )

            val body = core * 3.0f
            drawOval(
                brush = Brush.radialGradient(
                    colorStops = arrayOf(
                        0f to dot.color.copy(alpha = alpha * if (rainbow) 0.58f else 0.52f),
                        0.45f to dot.color.copy(alpha = alpha * if (rainbow) 0.34f else 0.30f),
                        1f to dot.color.copy(alpha = 0f),
                    ),
                    center = Offset.Zero,
                    radius = body * 0.58f,
                ),
                topLeft = Offset(-body * 0.62f, -body * 0.5f),
                size = Size(body * 1.24f, body),
                blendMode = ADD,
            )

            val plate = core * if (rainbow) 1.65f else 1.9f
            drawRoundRect(
                brush = Brush.radialGradient(
                    colorStops = arrayOf(
                        0f to Color.White.copy(alpha = min(alpha * if (rainbow) 0.94f else 0.92f, if (rainbow) 0.94f else 0.92f)),
                        0.5f to dot.color.copy(alpha = min(alpha * if (rainbow) 0.84f else 0.78f, if (rainbow) 0.88f else 0.85f)),
                        0.85f to dot.color.copy(alpha = alpha * if (rainbow) 0.40f else 0.36f),
                        1f to dot.color.copy(alpha = alpha * if (rainbow) 0.18f else 0.16f),
                    ),
                    center = Offset.Zero,
                    radius = plate * 0.8f,
                ),
                topLeft = Offset(-plate * 0.5f, -plate * 0.5f),
                size = Size(plate, plate),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(plate * if (rainbow) 0.22f else 0.3f),
                blendMode = ADD,
            )
        }
    }

    private fun DrawScope.drawBeams(
        dots: List<ProjectedDot>,
        center: Offset,
        radius: Float,
        height: Float,
    ) {
        if (intensity.beamShare <= 0) return
        val entryY = height * when (intensity) {
            DiscoIntensity.DISCO_BALL -> 0f
            DiscoIntensity.DANCE_PARTY -> 5f / 350f
            else -> 36f / 350f
        }
        for (dot in dots) {
            if (!dot.castsBeam) continue
            val dx = dot.point.x - center.x
            val dy = dot.point.y - center.y
            val length = hypot(dx, dy)
            if (length <= radius) continue

            val reach = smoothStep((radius * 1.4f).toDouble(), (radius * 4.2f).toDouble(), length.toDouble())
            val fade = smoothStep(0.08, 0.34, dot.alpha) * reach
            if (fade <= 0.01) continue

            val px = -dy / length
            val py = dx / length
            var start = center
            if (center.y < entryY && dot.point.y > entryY) {
                val t = (entryY - center.y) / (dot.point.y - center.y)
                start = Offset(center.x + dx * t, entryY)
            }
            val widthBoost = if (intensity == DiscoIntensity.DISCO_BALL || intensity == DiscoIntensity.DANCE_PARTY) 1.35f else 1f
            val baseHalf = radius * 0.16f * widthBoost
            val tipHalf = max(dot.size * 1.9f, baseHalf) * widthBoost
            val path = Path().apply {
                moveTo(start.x + px * baseHalf, start.y + py * baseHalf)
                lineTo(dot.point.x + px * tipHalf, dot.point.y + py * tipHalf)
                lineTo(dot.point.x - px * tipHalf, dot.point.y - py * tipHalf)
                lineTo(start.x - px * baseHalf, start.y - py * baseHalf)
                close()
            }
            val peak = (dot.alpha * fade).toFloat()
            val shaft = when (intensity) {
                DiscoIntensity.DISCO_BALL -> 0.42f
                DiscoIntensity.DANCE_PARTY -> 0.28f
                else -> 0.26f
            }
            drawPath(
                path = path,
                brush = Brush.linearGradient(
                    colors = listOf(
                        dot.color.copy(alpha = if (intensity == DiscoIntensity.DISCO_BALL) peak * shaft * 0.38f else 0f),
                        dot.color.copy(alpha = peak * shaft),
                        dot.color.copy(alpha = peak * shaft * 0.35f),
                        dot.color.copy(alpha = 0f),
                    ),
                    start = start,
                    end = dot.point,
                ),
                blendMode = ADD,
            )
        }
    }

    private fun edgeFade(point: Offset, band: Float, width: Float, height: Float): Double {
        if (band <= 0f) return 1.0
        val nearest = min(
            min(point.x, width - point.x),
            min(point.y, height - point.y),
        )
        return smoothStep((-band * 0.2f).toDouble(), band.toDouble(), nearest.toDouble())
    }

    private fun DrawScope.drawSearchlights(
        time: Double,
        age: Double,
        width: Float,
        height: Float,
        appearanceGain: Float,
    ) {
        val intro = smoothStep(0.0, 0.85, age)
        if (intro <= 0.01) return

        data class Cone(val x: Float, val phase: Double, val color: Color)
        val cones = listOf(
            Cone(0.22f, 0.0, Color(1.0f, 0.97f, 0.88f)),
            Cone(0.50f, 2.1, Color(1.0f, 0.94f, 0.78f)),
            Cone(0.78f, 4.2, Color(0.92f, 0.95f, 1.0f)),
        )
        val reach = hypot(width, height) * 1.2f
        val clock = time + 1.35
        for (cone in cones) {
            val pan = (sin(clock * 0.11 + cone.phase * 1.3) * width * 0.045).toFloat()
            val origin = Offset(width * cone.x + pan, -height * 0.06f)
            val sweep = sin(clock * 0.19 + cone.phase) * 0.48 +
                sin(clock * 0.41 + cone.phase * 0.65) * 0.07
            val angle = PI / 2 + sweep
            val end = Offset(
                origin.x + (cos(angle) * reach).toFloat(),
                origin.y + (sin(angle) * reach).toFloat(),
            )
            val dx = end.x - origin.x
            val dy = end.y - origin.y
            val length = max(hypot(dx, dy), 1f)
            val px = -dy / length
            val py = dx / length
            val breathe = (0.84 + 0.16 * sin(clock * 0.33 + cone.phase * 1.1)).toFloat()
            val tipScale = (1.0 + 0.08 * sin(clock * 0.27 + cone.phase * 0.9)).toFloat()
            val baseHalf = 8f
            val tipHalf = width * 0.22f * tipScale
            val path = Path().apply {
                moveTo(origin.x + px * baseHalf, origin.y + py * baseHalf)
                lineTo(end.x + px * tipHalf, end.y + py * tipHalf)
                lineTo(end.x - px * tipHalf, end.y - py * tipHalf)
                lineTo(origin.x - px * baseHalf, origin.y - py * baseHalf)
                close()
            }
            drawPath(
                path = path,
                brush = Brush.linearGradient(
                    colors = listOf(
                        cone.color.copy(alpha = 0.42f * breathe * intro.toFloat() * appearanceGain),
                        cone.color.copy(alpha = 0.16f * breathe * intro.toFloat() * appearanceGain),
                        cone.color.copy(alpha = 0f),
                    ),
                    start = origin,
                    end = end,
                ),
                blendMode = ADD,
            )
        }
    }

    private data class Facet(
        val sinLat: Double,
        val cosLat: Double,
        val longitude: Double,
        val width: Double,
        val polish: Double,
        val spread: Double,
        val castsBeam: Boolean,
    ) {
        fun normal(spin: Double): DiscoVec3 {
            val theta = longitude + spin
            return DiscoVec3(cosLat * sin(theta), sinLat, cosLat * cos(theta))
        }
    }

    private data class Spotlight(
        val direction: DiscoVec3,
        val color: Color,
        val intensity: Double,
        val phase: Double = 0.0,
    ) {
        fun pulse(time: Double, rate: Double): Double {
            if (rate <= 0) return 1.0
            return 0.62 + 0.38 * (0.5 + 0.5 * sin(time * rate * 2 * PI + phase))
        }
    }

    companion object {
        private val ADD = androidx.compose.ui.graphics.BlendMode.Plus
        private const val BALL_RADIUS_FRACTION = 0.082f
        private const val BALL_CENTER_X_FRACTION = 0.50f
        private const val BALL_CENTER_Y_RADII = -1.2f
        private const val WALL_DISTANCE = 7.0
        private const val BEAM_REACH = 16.0
        private const val MAX_BEAM_TRAVEL = 34.0
        // Compose Plus on an offscreen DstIn layer reads dimmer than iOS Canvas
        // plus, so Android lifts draw alphas without changing stored recipes.
        private const val VISIBILITY = 1.45
        private const val DOT_GAIN = 1.45
        const val LIGHT_MODE_GAIN = 1.4f
        private const val MAX_DOTS = 300
        private const val BEAM_DIVERGENCE = 0.06
        private const val FACET_FILL = 0.86
        private val MAX_LATITUDE = 76.0 * PI / 180.0

        private fun makeFacets(intensity: DiscoIntensity): List<Facet> {
            val rows = intensity.facetRows
            val equator = intensity.equatorFacetCount
            if (rows <= 1 || equator <= 0) return emptyList()

            val rng = DiscoRandom(0x0D15C0BA11uL)
            val facets = ArrayList<Facet>()
            val rowStep = (2 * MAX_LATITUDE) / (rows - 1)

            for (row in 0 until rows) {
                val latitude = -MAX_LATITUDE + rowStep * row
                val cosLat = cos(latitude)
                val count = max(5, (equator * cosLat).roundToInt())
                val stagger = if (row % 2 == 0) 0.0 else PI / count
                for (column in 0 until count) {
                    val longitude = (2 * PI / count) * column + stagger
                    facets += Facet(
                        sinLat = sin(latitude),
                        cosLat = cosLat,
                        longitude = longitude,
                        width = cosLat * (2 * PI / count) * FACET_FILL,
                        polish = rng.nextDouble(0.5, 1.0),
                        spread = rng.nextDouble(0.7, 1.45),
                        castsBeam = rng.nextDouble(0.0, 1.0) < intensity.beamShare,
                    )
                }
            }
            return facets
        }

        private fun makeLights(intensity: DiscoIntensity): List<Spotlight> {
            if (intensity == DiscoIntensity.OFF) return emptyList()
            fun lamp(x: Double, y: Double, z: Double, color: Color, level: Double, phase: Double = 0.0) =
                Spotlight(DiscoVec3(x, y, z).normalized, color, level, phase)

            return when (intensity.palette) {
                DiscoPalette.SILVER -> listOf(
                    lamp(-0.48, -0.70, -1.0, Color(1.0f, 0.97f, 0.92f), 0.95),
                    lamp(0.55, -0.60, -1.0, Color(0.92f, 0.95f, 1.0f), 0.6),
                )
                DiscoPalette.WARM -> listOf(
                    lamp(-0.48, -0.70, -1.0, Color(1.0f, 0.97f, 0.92f), 0.95),
                    lamp(0.62, -0.52, -1.0, Color(1.0f, 0.52f, 0.60f), 0.70),
                    lamp(-0.16, 0.46, -1.05, Color(1.0f, 0.76f, 0.50f), 0.55),
                )
                DiscoPalette.RAINBOW -> listOf(
                    lamp(-0.62, -0.62, -1.0, Color.hsv(0f, 0.95f, 1f), 0.82),
                    lamp(-0.20, -0.82, -1.0, Color.hsv(29f, 0.95f, 1f), 0.78),
                    lamp(0.22, -0.80, -1.0, Color.hsv(119f, 0.95f, 1f), 0.82),
                    lamp(0.55, -0.58, -1.0, Color.hsv(180f, 0.95f, 1f), 0.80),
                    lamp(0.08, 0.38, -1.05, Color.hsv(300f, 0.92f, 1f), 0.70),
                )
            }
        }
    }
}

fun smoothStep(from: Double, to: Double, value: Double): Double {
    if (to <= from) return if (value >= to) 1.0 else 0.0
    val t = ((value - from) / (to - from)).coerceIn(0.0, 1.0)
    return t * t * (3 - 2 * t)
}

data class DiscoVec3(val x: Double, val y: Double, val z: Double) {
    val length: Double get() = sqrt(x * x + y * y + z * z)
    val normalized: DiscoVec3
        get() {
            val l = length
            if (l <= 0) return this
            return DiscoVec3(x / l, y / l, z / l)
        }

    fun dot(other: DiscoVec3): Double = x * other.x + y * other.y + z * other.z
    operator fun minus(other: DiscoVec3) = DiscoVec3(x - other.x, y - other.y, z - other.z)
    operator fun times(rhs: Double) = DiscoVec3(x * rhs, y * rhs, z * rhs)
}

class DiscoRandom(seed: ULong) {
    private var state = seed

    fun next(): ULong {
        state += 0x9E3779B97F4A7C15uL
        var z = state
        z = (z xor (z shr 30)) * 0xBF58476D1CE4E5B9uL
        z = (z xor (z shr 27)) * 0x94D049BB133111EBuL
        return z xor (z shr 31)
    }

    fun nextDouble(): Double = (next() shr 11).toDouble() / (1L shl 53).toDouble()

    fun nextDouble(from: Double, until: Double): Double = from + nextDouble() * (until - from)
}
