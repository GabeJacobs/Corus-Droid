package fm.corus.android.ui.player

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color as AndroidColor
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import fm.corus.android.ui.theme.CorusColors
import fm.corus.android.ui.theme.LocalCorusDarkTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.net.URL
import kotlin.math.max
import kotlin.math.min

/**
 * Spotify-style art wash + frosted glass behind mini + full player.
 *
 * Mirrors iOS `PlayerArtworkTintedMaterialBackground`:
 * solid base → material veil → dual-plane blurred art + tint gradient (crossfade)
 * → black/white veil. Expansion only drives alphas.
 */
@Composable
fun PlayerArtworkBackdrop(
    artworkUrl: String?,
    expansion: Float,
    modifier: Modifier = Modifier,
) {
    val darkTheme = LocalCorusDarkTheme.current
    val t = expansion.coerceIn(0f, 1f)

    var heavyChromeReady by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(280)
        heavyChromeReady = true
    }

    val frostedArtOpacity = when {
        !heavyChromeReady -> 0f
        darkTheme -> 0.12f + 0.88f * t
        else -> 0.08f + 0.58f * t
    }
    val materialOpacity = when {
        !heavyChromeReady -> 0f
        darkTheme -> 0.72f
        else -> 0.94f
    }
    val veilOpacity = if (darkTheme) {
        0.62f + (0.27f - 0.62f) * t
    } else {
        0.88f + (0.42f - 0.88f) * t
    }
    val gradientOpacity = if (darkTheme) {
        0.55f + (0.30f - 0.55f) * t
    } else {
        0.28f + (0.36f - 0.28f) * t
    }

    val veilColor = if (darkTheme) Color.Black else Color.White
    val materialTint = CorusColors.Background.copy(alpha = materialOpacity)

    // Dual-plane crossfade state (iOS PlayerFrostedArtBackdropView).
    var planeA by remember { mutableStateOf<PreparedBackdrop?>(null) }
    var planeB by remember { mutableStateOf<PreparedBackdrop?>(null) }
    var frontIsA by remember { mutableStateOf(true) }
    val crossfade = remember { Animatable(1f) }
    var committedIdentity by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(artworkUrl, darkTheme, heavyChromeReady) {
        if (!heavyChromeReady) return@LaunchedEffect
        val identity = artworkUrl ?: "nil"
        if (identity == committedIdentity && (planeA != null || planeB != null)) return@LaunchedEffect

        val prepared = withContext(Dispatchers.IO) {
            prepareBackdrop(artworkUrl, darkTheme)
        }
        if (identity != (artworkUrl ?: "nil")) return@LaunchedEffect

        val hasOutgoing = (if (frontIsA) planeA else planeB) != null
        if (frontIsA) {
            planeB = prepared
            if (hasOutgoing) {
                crossfade.snapTo(0f)
                crossfade.animateTo(1f, tween(durationMillis = 500))
            } else {
                crossfade.snapTo(1f)
            }
            frontIsA = false
            // Drop outgoing after crossfade settles.
            planeA = null
        } else {
            planeA = prepared
            if (hasOutgoing) {
                crossfade.snapTo(0f)
                crossfade.animateTo(1f, tween(durationMillis = 500))
            } else {
                crossfade.snapTo(1f)
            }
            frontIsA = true
            planeB = null
        }
        committedIdentity = identity
    }

    val front = if (frontIsA) planeA else planeB
    val back = if (frontIsA) planeB else planeA
    val frontAlpha = crossfade.value
    val backAlpha = 1f - crossfade.value

    Box(modifier = modifier.fillMaxSize().background(CorusColors.Background)) {
        if (heavyChromeReady) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(materialTint),
            )
        }

        if (heavyChromeReady) {
            if (back != null && backAlpha > 0.01f) {
                BackdropPlane(
                    plane = back,
                    artOpacity = frostedArtOpacity * backAlpha,
                    gradientOpacity = gradientOpacity * backAlpha,
                )
            }
            if (front != null && frontAlpha > 0.01f) {
                BackdropPlane(
                    plane = front,
                    artOpacity = frostedArtOpacity * frontAlpha,
                    gradientOpacity = gradientOpacity * frontAlpha,
                )
            }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(veilColor.copy(alpha = veilOpacity)),
        )
    }
}

@Composable
private fun BackdropPlane(
    plane: PreparedBackdrop,
    artOpacity: Float,
    gradientOpacity: Float,
) {
    Box(modifier = Modifier.fillMaxSize().alpha(artOpacity.coerceIn(0f, 1f))) {
        if (plane.bitmap != null) {
            Image(
                bitmap = plane.bitmap.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    // 9% overscan so blur edges don’t vignette (iOS).
                    .graphicsLayer {
                        scaleX = 1.09f
                        scaleY = 1.09f
                    }
                    .blur(42.dp),
            )
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .drawWithContent {
                    drawRect(
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                plane.topTint.copy(alpha = gradientOpacity.coerceIn(0f, 1f)),
                                plane.bottomTint.copy(alpha = gradientOpacity.coerceIn(0f, 1f) * 0.85f),
                            ),
                        ),
                    )
                },
        )
    }
}

private data class PreparedBackdrop(
    val bitmap: Bitmap?,
    val topTint: Color,
    val bottomTint: Color,
)

private fun prepareBackdrop(
    artworkUrl: String?,
    darkTheme: Boolean,
): PreparedBackdrop {
    val fallback = if (darkTheme) {
        Color(0xFF1F1F1F)
    } else {
        Color(0xFFEBEBEB)
    }
    if (artworkUrl.isNullOrBlank()) {
        return PreparedBackdrop(null, fallback, fallback)
    }
    val bitmap = loadDownsampledBitmap(artworkUrl, maxSide = 280) ?: run {
        return PreparedBackdrop(null, fallback, fallback)
    }
    val avg = averageColor(bitmap)
    val boosted = boostForPlayerWash(avg, darkTheme)
    val (top, bottom) = gradientPair(boosted, darkTheme)
    // Keep a soft copy for the plane; blur is applied at draw time.
    return PreparedBackdrop(bitmap, top, bottom)
}

private fun loadDownsampledBitmap(url: String, maxSide: Int): Bitmap? {
    return runCatching {
        URL(url).openStream().use { stream ->
            val bytes = stream.readBytes()
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
            var sample = 1
            val largest = max(bounds.outWidth, bounds.outHeight)
            while (largest / sample > maxSide * 2) sample *= 2
            val opts = BitmapFactory.Options().apply { inSampleSize = sample }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
        }
    }.getOrNull()
}

private fun averageColor(bitmap: Bitmap): Color {
    val w = bitmap.width
    val h = bitmap.height
    if (w <= 0 || h <= 0) return Color.Gray
    val stepX = max(1, w / 24)
    val stepY = max(1, h / 24)
    var r = 0L
    var g = 0L
    var b = 0L
    var count = 0L
    var y = 0
    while (y < h) {
        var x = 0
        while (x < w) {
            val c = bitmap.getPixel(x, y)
            r += AndroidColor.red(c)
            g += AndroidColor.green(c)
            b += AndroidColor.blue(c)
            count++
            x += stepX
        }
        y += stepY
    }
    if (count == 0L) return Color.Gray
    return Color(
        red = (r / count) / 255f,
        green = (g / count) / 255f,
        blue = (b / count) / 255f,
        alpha = 1f,
    )
}

/** iOS `cymbalBoostedForPlayerWash`. */
private fun boostForPlayerWash(color: Color, darkTheme: Boolean): Color {
    val hsv = FloatArray(3)
    AndroidColor.RGBToHSV(
        (color.red * 255).toInt().coerceIn(0, 255),
        (color.green * 255).toInt().coerceIn(0, 255),
        (color.blue * 255).toInt().coerceIn(0, 255),
        hsv,
    )
    if (darkTheme) {
        hsv[1] = min(1f, hsv[1] * 1.45f)
        hsv[2] = min(1f, hsv[2] * 1.05f)
    } else {
        var s = hsv[1] * 1.35f
        if (hsv[1] > 0.02f) s = max(0.24f, s)
        hsv[1] = min(1f, s)
        hsv[2] = (hsv[2] * 0.96f).coerceIn(0.40f, 0.72f)
    }
    val argb = AndroidColor.HSVToColor(hsv)
    return Color(argb)
}

/** iOS `gradientPair`. */
private fun gradientPair(boosted: Color, darkTheme: Boolean): Pair<Color, Color> {
    return if (darkTheme) {
        val bottom = Color(
            red = boosted.red * 0.22f,
            green = boosted.green * 0.22f,
            blue = boosted.blue * 0.22f,
            alpha = 1f,
        )
        boosted to bottom
    } else {
        val bottomMix = 0.34f
        val bottom = Color(
            red = boosted.red * (1f - bottomMix) + 0.97f * bottomMix,
            green = boosted.green * (1f - bottomMix) + 0.97f * bottomMix,
            blue = boosted.blue * (1f - bottomMix) + 0.98f * bottomMix,
            alpha = 1f,
        )
        boosted to bottom
    }
}
