package fm.corus.android.ui.player

import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil3.BitmapImage
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.request.crossfade
import coil3.size.Size
import coil3.toBitmap
import fm.corus.android.ui.theme.CorusColors
import fm.corus.android.ui.theme.LocalCorusDarkTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.max
import kotlin.math.min

/** Matches iOS `UIView.animate` `.curveEaseInOut` for the plane crossfade. */
private val BackdropEaseInOut = CubicBezierEasing(0.42f, 0f, 0.58f, 1f)
private const val BackdropCrossfadeMs = 500

/**
 * Spotify-style art wash + frosted glass behind mini + full player.
 *
 * Mirrors iOS `PlayerArtworkTintedMaterialBackground`:
 * solid base → material veil → dual-plane blurred art + tint gradient (one
 * crossfade) → black/white veil. Expansion only drives live opacities; track
 * changes hold the previous plane until the next bake is ready, then run a
 * single 0.5s plane alpha crossfade (never clear-to-empty mid-transition).
 */
@Composable
fun PlayerArtworkBackdrop(
    artworkUrl: String?,
    expansion: Float,
    modifier: Modifier = Modifier,
) {
    val darkTheme = LocalCorusDarkTheme.current
    val context = LocalContext.current
    val imageLoader = remember(context) { SingletonImageLoader.get(context) }
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

    var planeA by remember { mutableStateOf<PreparedBackdrop?>(null) }
    var planeB by remember { mutableStateOf<PreparedBackdrop?>(null) }
    val alphaA = remember { Animatable(0f) }
    val alphaB = remember { Animatable(0f) }
    var frontIsA by remember { mutableStateOf(true) }
    var committedIdentity by remember { mutableStateOf<String?>(null) }
    var loadGeneration by remember { mutableIntStateOf(0) }

    LaunchedEffect(artworkUrl, darkTheme, heavyChromeReady) {
        if (!heavyChromeReady) return@LaunchedEffect
        val identity = backdropIdentity(artworkUrl, darkTheme)
        if (identity == committedIdentity && (planeA != null || planeB != null)) {
            return@LaunchedEffect
        }

        val generation = loadGeneration + 1
        loadGeneration = generation

        // Coil shares memory/disk with the album-art carousel so Next doesn't
        // open a second network stream on the main path.
        val prepared = withContext(Dispatchers.IO) {
            prepareBackdrop(artworkUrl, darkTheme, imageLoader, context)
        }
        if (generation != loadGeneration) return@LaunchedEffect

        val incomingIsA = !frontIsA
        if (incomingIsA) {
            planeA = prepared
        } else {
            planeB = prepared
        }

        val outgoingContent = if (frontIsA) planeA else planeB
        val outgoingAlpha = if (frontIsA) alphaA.value else alphaB.value
        val hasOutgoing = outgoingContent != null || outgoingAlpha > 0.01f

        val fadeSpec = tween<Float>(
            durationMillis = BackdropCrossfadeMs,
            easing = BackdropEaseInOut,
        )

        if (!hasOutgoing) {
            if (incomingIsA) {
                alphaA.snapTo(1f)
                alphaB.snapTo(0f)
                planeB = null
            } else {
                alphaB.snapTo(1f)
                alphaA.snapTo(0f)
                planeA = null
            }
            frontIsA = incomingIsA
        } else {
            // iOS: apply on back plane at alpha 0, then animate both plane alphas.
            if (incomingIsA) {
                alphaA.snapTo(0f)
            } else {
                alphaB.snapTo(0f)
            }
            frontIsA = incomingIsA
            try {
                coroutineScope {
                    launch {
                        if (incomingIsA) alphaA.animateTo(1f, fadeSpec)
                        else alphaB.animateTo(1f, fadeSpec)
                    }
                    launch {
                        if (incomingIsA) alphaB.animateTo(0f, fadeSpec)
                        else alphaA.animateTo(0f, fadeSpec)
                    }
                }
                if (generation == loadGeneration) {
                    if (incomingIsA) planeB = null else planeA = null
                }
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                // Next skip owns the planes; do not clear mid-fade.
                throw cancelled
            }
        }
        if (generation == loadGeneration) {
            committedIdentity = identity
        }
    }

    Box(modifier = modifier.fillMaxSize().background(CorusColors.Background)) {
        if (heavyChromeReady) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(materialTint),
            )
        }

        if (heavyChromeReady) {
            // Do NOT read Animatable.value here — that recomposed the blurred
            // planes every crossfade frame and hitching Next. Plane alpha is
            // applied in graphicsLayer (draw-only invalidation).
            planeA?.let { plane ->
                BackdropPlane(
                    plane = plane,
                    planeAlpha = alphaA,
                    artOpacity = frostedArtOpacity,
                    gradientOpacity = gradientOpacity,
                )
            }
            planeB?.let { plane ->
                BackdropPlane(
                    plane = plane,
                    planeAlpha = alphaB,
                    artOpacity = frostedArtOpacity,
                    gradientOpacity = gradientOpacity,
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
    planeAlpha: Animatable<Float, AnimationVector1D>,
    artOpacity: Float,
    gradientOpacity: Float,
) {
    // Expansion opacities are read inside graphicsLayer / draw so sheet-drag
    // and track crossfades invalidate draw, not composition+blur rebuild.
    val artOpacityState = rememberUpdatedState(artOpacity)
    val gradientOpacityState = rememberUpdatedState(gradientOpacity)
    val imageBitmap = remember(plane.bitmap) { plane.bitmap?.asImageBitmap() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer { alpha = planeAlpha.value.coerceIn(0f, 1f) },
    ) {
        if (imageBitmap != null) {
            Image(
                bitmap = imageBitmap,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        scaleX = 1.09f
                        scaleY = 1.09f
                        alpha = artOpacityState.value.coerceIn(0f, 1f)
                    }
                    .blur(42.dp),
            )
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .drawWithContent {
                    val g = gradientOpacityState.value.coerceIn(0f, 1f)
                    drawRect(
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                plane.topTint.copy(alpha = g),
                                plane.bottomTint.copy(alpha = g * 0.85f),
                            ),
                        ),
                    )
                },
        )
    }
}

internal fun backdropIdentity(artworkUrl: String?, darkTheme: Boolean): String {
    val url = artworkUrl ?: "nil"
    return "$url|${if (darkTheme) "d" else "l"}"
}

private data class PreparedBackdrop(
    val bitmap: Bitmap?,
    val topTint: Color,
    val bottomTint: Color,
)

private suspend fun prepareBackdrop(
    artworkUrl: String?,
    darkTheme: Boolean,
    imageLoader: ImageLoader,
    context: android.content.Context,
): PreparedBackdrop {
    val fallback = if (darkTheme) {
        Color(0xFF1F1F1F)
    } else {
        Color(0xFFEBEBEB)
    }
    if (artworkUrl.isNullOrBlank()) {
        return PreparedBackdrop(null, fallback, fallback)
    }
    val bitmap = loadDownsampledBitmap(imageLoader, context, artworkUrl, maxSide = 280) ?: run {
        return PreparedBackdrop(null, fallback, fallback)
    }
    val avg = averageColor(bitmap)
    val boosted = boostForPlayerWash(avg, darkTheme)
    val (top, bottom) = gradientPair(boosted, darkTheme)
    return PreparedBackdrop(bitmap, top, bottom)
}

private suspend fun loadDownsampledBitmap(
    imageLoader: ImageLoader,
    context: android.content.Context,
    url: String,
    maxSide: Int,
): Bitmap? {
    return runCatching {
        val request = ImageRequest.Builder(context)
            .data(url)
            .size(Size(maxSide, maxSide))
            .crossfade(false)
            // averageColor reads pixels — hardware bitmaps can't.
            .allowHardware(false)
            .build()
        val result = imageLoader.execute(request) as? SuccessResult ?: return@runCatching null
        val image = result.image
        (image as? BitmapImage)?.bitmap
            ?: image.toBitmap(image.width.coerceAtLeast(1), image.height.coerceAtLeast(1))
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
