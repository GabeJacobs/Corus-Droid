package fm.corus.android.ui.player

import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import androidx.compose.animation.core.Animatable
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
import android.util.LruCache
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
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
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.max
import kotlin.math.min

/** Matches iOS `UIView.animate` `.curveEaseInOut` for the plane crossfade. */
private val BackdropEaseInOut = CubicBezierEasing(0.42f, 0f, 0.58f, 1f)
private const val BackdropCrossfadeMs = 500

/** Soft cache of pre-baked frosted washes — mirrors iOS `blurCache`. */
private val frostBitmapCache = object : LruCache<String, Bitmap>(8) {}

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
    /** Fired once the first frosted wash is on screen — used to hide the white flash. */
    onSurfaceReady: (() -> Unit)? = null,
) {
    val darkTheme = LocalCorusDarkTheme.current
    val context = LocalContext.current
    val imageLoader = remember(context) { SingletonImageLoader.get(context) }
    val t = expansion.coerceIn(0f, 1f)
    val onReadyState = rememberUpdatedState(onSurfaceReady)

    // Becomes true once the first bake commits — no artificial 280ms delay
    // (that was painting a solid white mini+tab before any album color).
    var heavyChromeReady by remember { mutableStateOf(false) }

    val frostedArtOpacity = playerFrostedArtOpacity(darkTheme, t, heavyChromeReady)
    val materialOpacity = playerMaterialOpacity(darkTheme, heavyChromeReady)
    val veilOpacity = playerVeilOpacity(darkTheme, t)
    val gradientOpacity = playerGradientOpacity(darkTheme, t)

    val veilColor = if (darkTheme) Color.Black else Color.White
    // iOS uses real `.ultraThinMaterial` over a solid base. Android has no
    // equivalent — a near-opaque Background tint (old light 0.94) read as a
    // flat white mini-player slab. Use a translucent white frost instead so
    // the album wash underneath stays visible.
    val materialTint = if (darkTheme) {
        CorusColors.Background.copy(alpha = materialOpacity)
    } else {
        Color.White.copy(alpha = materialOpacity)
    }

    var planeA by remember { mutableStateOf<PreparedBackdrop?>(null) }
    var planeB by remember { mutableStateOf<PreparedBackdrop?>(null) }
    val alphaA = remember { Animatable(0f) }
    val alphaB = remember { Animatable(0f) }
    var frontIsA by remember { mutableStateOf(true) }
    var committedIdentity by remember { mutableStateOf<String?>(null) }
    var loadGeneration by remember { mutableIntStateOf(0) }

    LaunchedEffect(artworkUrl, darkTheme) {
        val identity = backdropIdentity(artworkUrl, darkTheme)
        if (identity == committedIdentity && (planeA != null || planeB != null)) {
            if (!heavyChromeReady) {
                heavyChromeReady = true
                onReadyState.value?.invoke()
            }
            return@LaunchedEffect
        }

        val generation = loadGeneration + 1
        loadGeneration = generation

        // Bake on IO immediately — Coil usually hits the feed memory cache so
        // the first wash is ready before we reveal the mini player.
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
                    // Clear outgoing content after the fade (keep slot for next skip).
                    if (incomingIsA) planeB = null else planeA = null
                }
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                // Next skip owns the planes; do not clear mid-fade.
                throw cancelled
            }
        }
        if (generation == loadGeneration) {
            committedIdentity = identity
            if (!heavyChromeReady) {
                heavyChromeReady = true
                onReadyState.value?.invoke()
            }
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
            // Frost is pre-baked into the bitmap (iOS CI blur), so reading
            // Animatable.value here is cheap and keeps the 0.5s crossfade smooth.
            planeA?.let { plane ->
                BackdropPlane(
                    plane = plane,
                    planeAlpha = alphaA.value,
                    artOpacity = frostedArtOpacity,
                    gradientOpacity = gradientOpacity,
                )
            }
            planeB?.let { plane ->
                BackdropPlane(
                    plane = plane,
                    planeAlpha = alphaB.value,
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
    planeAlpha: Float,
    artOpacity: Float,
    gradientOpacity: Float,
) {
    val imageBitmap = remember(plane.bitmap) { plane.bitmap?.asImageBitmap() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer { alpha = planeAlpha.coerceIn(0f, 1f) },
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
                        alpha = artOpacity.coerceIn(0f, 1f)
                    },
            )
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .drawWithContent {
                    val g = gradientOpacity.coerceIn(0f, 1f)
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

/**
 * Blurred album-art bloom. Light stays close to iOS (0.08 → 0.66) with a
 * slightly stronger mini bloom. Dark mini is a hair above iOS 0.12 so Android's
 * black-on-black material stand-in still reads a bit of album color.
 */
internal fun playerFrostedArtOpacity(
    darkTheme: Boolean,
    expansion: Float,
    heavyChromeReady: Boolean,
): Float {
    if (!heavyChromeReady) return 0f
    val t = expansion.coerceIn(0f, 1f)
    return if (darkTheme) {
        // Mini 0.18 → full 1.0 (iOS mini is 0.12; Android needs more bloom).
        0.18f + 0.82f * t
    } else {
        // Slightly above iOS 0.08 so a bit more album color reads on the mini.
        0.16f + 0.50f * t
    }
}

/**
 * Glass-strength stand-in. Light uses a translucent white frost. Dark is under
 * iOS's 0.72 so the art wash isn't crushed to near-black on the mini/tab bar.
 */
internal fun playerMaterialOpacity(darkTheme: Boolean, heavyChromeReady: Boolean): Float {
    if (!heavyChromeReady) return 0f
    return if (darkTheme) 0.60f else 0.55f
}

/**
 * Top veil. Light/dark mini stay under iOS so the mini bar keeps color.
 * Expanded is a hair heavier than iOS (0.42 / 0.27) — Android's downsample
 * frost leaves more color structure than CI blur, so posts need a bit more
 * scrim for the same readability.
 */
internal fun playerVeilOpacity(darkTheme: Boolean, expansion: Float): Float {
    val t = expansion.coerceIn(0f, 1f)
    return if (darkTheme) {
        0.50f + (0.34f - 0.50f) * t
    } else {
        0.74f + (0.50f - 0.74f) * t
    }
}

internal fun playerGradientOpacity(darkTheme: Boolean, expansion: Float): Float {
    val t = expansion.coerceIn(0f, 1f)
    return if (darkTheme) {
        // Slightly stronger mini tint wash for a hint of album color.
        0.62f + (0.30f - 0.62f) * t
    } else {
        0.34f + (0.36f - 0.34f) * t
    }
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
    // Bake frost off the UI thread (iOS `cymbalPlayerFrostedBackdrop`) so track
    // changes can crossfade two stills instead of rebuilding Modifier.blur.
    // v2 = stronger multi-pass frost; bump when bake recipe changes so the
    // in-process LRU doesn't serve an older softer wash.
    val frostKey = "$artworkUrl|v2"
    val frost = frostBitmapCache.get(frostKey)
        ?: bakeFrostedWashBitmap(bitmap).also { frostBitmapCache.put(frostKey, it) }
    return PreparedBackdrop(frost, top, bottom)
}

/**
 * Approximate iOS's pre-baked CI gaussian frost (σ ≈ 42): downsample hard,
 * bounce through a mid size for a softer falloff, then scale back up with
 * filtering. Cheap enough to run on every skip without hitching the crossfade.
 *
 * [tinySide] used to be 36 — that left blocky color patches under translucent
 * post cards. 20 + mid pass softens structure closer to real CI blur.
 */
internal fun bakeFrostedWashBitmap(source: Bitmap, washSide: Int = 280): Bitmap {
    val srcW = source.width.coerceAtLeast(1)
    val srcH = source.height.coerceAtLeast(1)
    val tinySide = 20
    val midSide = 48
    fun dimsFor(side: Int): Pair<Int, Int> {
        return if (srcW >= srcH) {
            side to max(1, (side.toFloat() * srcH / srcW).toInt())
        } else {
            max(1, (side.toFloat() * srcW / srcH).toInt()) to side
        }
    }
    val (tinyW, tinyH) = dimsFor(tinySide)
    val (midW, midH) = dimsFor(midSide)
    val (outW, outH) = dimsFor(washSide)
    val tiny = Bitmap.createScaledBitmap(source, tinyW, tinyH, true)
    val mid = Bitmap.createScaledBitmap(tiny, midW, midH, true)
    val frosted = Bitmap.createScaledBitmap(mid, outW, outH, true)
    if (tiny !== source && tiny !== mid && tiny !== frosted) tiny.recycle()
    if (mid !== source && mid !== frosted) mid.recycle()
    return frosted
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
