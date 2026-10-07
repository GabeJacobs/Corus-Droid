package fm.corus.android.ui.components

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import fm.corus.android.data.model.RainIntensity
import fm.corus.android.data.model.SnowIntensity
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

internal enum class ProfileStoryWeather { NONE, RAIN, SNOW;
    fun toggling(value: ProfileStoryWeather) = if (this == value) NONE else value
    val analyticsValue get() = name.lowercase(java.util.Locale.ROOT)
}

/** Fixed 360x640 logical canvas, shared by preview and offline encoding.
 * Reuses the profile's actual particle factories, layers, speeds and Blizzard style.
 * Android's origin is top-left: positive Y always falls DOWN, never flip this canvas. */
internal class ProfileStoryWeatherScene(val weather: ProfileStoryWeather) {
    private val rain = if (weather == ProfileStoryWeather.RAIN)
        List(RainIntensity.MEDIUM.dropCount) { createRaindrop(360f, 640f, RainIntensity.MEDIUM) }.toMutableList() else mutableListOf()
    private val snow = if (weather == ProfileStoryWeather.SNOW)
        List(SnowIntensity.HEAVY.flakeCount) { createSnowflake(360f, 640f, true) }.toMutableList() else mutableListOf()
    private val splashes = mutableListOf<Splash>()
    private var time = 0f
    private var tick = 0
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
    private val dx = sin(Math.toRadians(15.0)).toFloat()
    private val dy = cos(Math.toRadians(15.0)).toFloat()

    init { repeat(120) { advance(1f / 60) } } // Already falling on the first preview/video frame.

    internal fun particlePositions() = if (weather == ProfileStoryWeather.RAIN) rain.map { it.x to it.y } else snow.map { it.x to it.y }

    fun advance(dt: Float) {
        time += dt; tick++
        rain.indices.forEach { index ->
            val drop = rain[index]
            val x = drop.x + dx * drop.speed * dt
            val y = drop.y + dy * drop.speed * dt
            if (y > 650) {
                if (Random.nextFloat() < .3f) splashes.add(Splash(drop.x, 640 - Random.nextFloat() * 4,
                    tick, Random.nextInt(2, 5), .314f, Random.nextFloat() * 4 + 4))
                rain[index] = createRaindrop(360f, 640f, RainIntensity.MEDIUM, fullHeight = false)
            } else rain[index] = drop.copy(x = if (x > 370) x - 380 else if (x < -10) x + 380 else x, y = y)
        }
        splashes.removeAll { tick - it.startTick >= 18 }
        snow.indices.forEach { index ->
            val flake = snow[index]
            val y = flake.y + flake.speed * dt
            val x = flake.x + flake.driftX * dt
            if (y > 650 + flake.radius) snow[index] = createSnowflake(360f, 640f, true, fullHeight = false)
            else snow[index] = flake.copy(x = if (x > 375) -10f else if (x < -15) 370f else x, y = y)
        }
    }

    fun draw(canvas: Canvas, width: Float, height: Float) {
        canvas.save(); canvas.scale(width / 360, height / 640); canvas.clipRect(0f, 0f, 360f, 640f)
        paint.style = Paint.Style.STROKE
        for (layer in listOf(RainLayer.BACKGROUND, RainLayer.FOREGROUND)) rain.filter { it.layer == layer }.forEach { drop ->
            val back = layer == RainLayer.BACKGROUND
            val length = drop.length * if (back) .7f else 1f
            paint.alpha = (255 * drop.opacity * (if (back) .5f else 1f) * (drop.y / 50).coerceIn(0f, 1f)).toInt()
            paint.strokeWidth = if (back) 1f else 1.5f
            canvas.drawLine(drop.x, drop.y, drop.x - dx * length, drop.y - dy * length, paint)
        }
        paint.style = Paint.Style.FILL
        splashes.forEach { splash ->
            val progress = (tick - splash.startTick) / 18f
            paint.alpha = (255 * (1 - progress) * .6f).toInt()
            repeat(splash.particleCount) { i ->
                val angle = splash.baseAngle + i * Math.PI.toFloat() / (splash.particleCount - 1).coerceAtLeast(1) - Math.PI.toFloat() / 2
                val spread = splash.maxSpread * progress
                canvas.drawCircle(splash.x + cos(angle) * spread, splash.y - sin(angle) * spread * .5f, 1.5f, paint)
            }
        }
        for (layer in listOf(SnowLayer.BACKGROUND, SnowLayer.FOREGROUND)) snow.filter { it.layer == layer }.forEach { flake ->
            val back = layer == SnowLayer.BACKGROUND
            val radius = flake.radius * if (back) .7f else 1f
            val alpha = flake.opacity * if (back) .55f else 1f
            val fade = (flake.y / 36).coerceIn(0f, 1f)
            val x = flake.x + sin(time * flake.swaySpeed + flake.swayPhase) * flake.swayAmount
            if (radius > 2.5) {
                paint.alpha = (255 * alpha * fade * .2f).toInt()
                canvas.drawCircle(x, flake.y, radius * 1.2f, paint)
            }
            paint.alpha = (255 * alpha * fade).toInt()
            canvas.drawCircle(x, flake.y, radius, paint)
            paint.alpha = (255 * min(alpha + .2f, .9f) * fade).toInt()
            canvas.drawCircle(x, flake.y, radius * .4f, paint)
        }
        canvas.restore()
    }
}
