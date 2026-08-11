package fm.corus.android.ui.player

import android.graphics.Bitmap
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.max

/** Matches iOS `UIViewPropertyAnimator(duration: 0.38, dampingRatio: 0.90)` settle feel. */
private val ArtSlideEasing = CubicBezierEasing(0.22f, 1f, 0.36f, 1f)
private const val ArtSlideMs = 380

/**
 * Full-player album art with iOS-style Next carousel:
 * current card slides out left, preloaded next card slides in from the right.
 *
 * Cards hold decoded [Bitmap]s (like iOS `UIImageView`) so promotion after the
 * slide is a pointer swap — never an AsyncImage reload that flashes empty/black.
 */
@Composable
fun FullPlayerAlbumArt(
    trackId: String?,
    url: String?,
    upcomingTrackId: String?,
    upcomingUrl: String?,
    side: Dp,
    artPx: Int,
    slideForward: Boolean = true,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val imageLoader = remember(context) { SingletonImageLoader.get(context) }
    val imageSize = remember(artPx) { Size(artPx, artPx) }
    val shape = remember { RoundedCornerShape(10.dp) }

    var frontTrackId by remember { mutableStateOf(trackId) }
    var frontUrl by remember { mutableStateOf(url) }
    var frontBitmap by remember { mutableStateOf<Bitmap?>(null) }

    var nextTrackId by remember { mutableStateOf<String?>(null) }
    var nextUrl by remember { mutableStateOf<String?>(null) }
    var nextBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var isSliding by remember { mutableStateOf(false) }

    val frontOffset = remember { Animatable(0f) }
    val nextOffset = remember { Animatable(0f) }
    val nextAlpha = remember { Animatable(0f) }

    // Initial / non-slide front load.
    LaunchedEffect(frontUrl, imageSize) {
        val target = frontUrl
        if (target.isNullOrBlank()) {
            frontBitmap = null
            return@LaunchedEffect
        }
        // Keep showing the current bitmap while a same-card URL upgrades (large art).
        val loaded = loadAlbumArtBitmap(imageLoader, context, target, imageSize)
        if (frontUrl == target) {
            frontBitmap = loaded
        }
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .height(side),
    ) {
        val hostWidthPx = constraints.maxWidth.toFloat().coerceAtLeast(1f)
        val artSidePx = with(density) { side.toPx() }
        val padPx = with(density) { 8.dp.toPx() }
        val travelPx = remember(hostWidthPx, artSidePx, padPx) {
            albumArtSlideTravelPx(hostWidthPx, artSidePx, padPx)
        }

        // Prefetch / park Up Next into nextBitmap (iOS prepareUpcoming).
        LaunchedEffect(upcomingTrackId, upcomingUrl, travelPx, isSliding, frontTrackId, trackId) {
            if (isSliding) return@LaunchedEffect
            if (frontTrackId != trackId) return@LaunchedEffect
            nextTrackId = upcomingTrackId
            nextUrl = upcomingUrl
            nextAlpha.snapTo(0f)
            nextOffset.snapTo(travelPx)
            if (upcomingUrl.isNullOrBlank()) {
                nextBitmap = null
                return@LaunchedEffect
            }
            val loaded = loadAlbumArtBitmap(imageLoader, context, upcomingUrl, imageSize)
            // Still the same parked target?
            if (!isSliding && nextUrl == upcomingUrl) {
                nextBitmap = loaded
            }
        }

        // React to now-playing identity changes.
        LaunchedEffect(trackId, url, travelPx) {
            val trackChanged = trackId != frontTrackId
            val urlChanged = url != frontUrl

            if (!trackChanged && !urlChanged && frontUrl != null) return@LaunchedEffect

            if (!trackChanged) {
                frontUrl = url
                return@LaunchedEffect
            }

            val preparedId = nextTrackId
            val preparedUrl = nextUrl
            val preparedBitmap = nextBitmap
            val prepared = shouldSlideAlbumArt(
                previousTrackId = frontTrackId,
                newTrackId = trackId,
                preparedNextTrackId = preparedId,
                newUrl = url,
                preparedNextUrl = preparedUrl,
            )

            if (!prepared) {
                isSliding = false
                nextAlpha.snapTo(0f)
                frontTrackId = trackId
                frontUrl = url
                frontOffset.snapTo(0f)
                nextOffset.snapTo(travelPx)
                return@LaunchedEffect
            }

            val outgoing = if (slideForward) -travelPx else travelPx
            val incoming = if (slideForward) travelPx else -travelPx
            val promotedUrl = preparedUrl ?: url

            try {
                isSliding = true
                // Warm bitmap if prefetch hasn't finished yet.
                val incomingBitmap = preparedBitmap
                    ?: loadAlbumArtBitmap(imageLoader, context, preparedUrl, imageSize)
                nextBitmap = incomingBitmap
                nextTrackId = preparedId
                nextUrl = preparedUrl

                frontOffset.snapTo(0f)
                nextOffset.snapTo(incoming)
                nextAlpha.snapTo(1f)

                val spec = tween<Float>(durationMillis = ArtSlideMs, easing = ArtSlideEasing)
                coroutineScope {
                    launch { frontOffset.animateTo(outgoing, spec) }
                    launch { nextOffset.animateTo(0f, spec) }
                }

                // iOS finishSlidePromotion: copy bitmap onto front, then park next.
                // Never clear next before front already holds the same pixels.
                frontTrackId = trackId
                frontUrl = promotedUrl
                frontBitmap = incomingBitmap ?: frontBitmap
                frontOffset.snapTo(0f)
                nextOffset.snapTo(travelPx)
                nextAlpha.snapTo(0f)
                nextTrackId = null
                nextUrl = null
                nextBitmap = null
            } finally {
                isSliding = false
            }
        }

        // Outgoing front under incoming next (iOS bringSubviewToFront(nextCard)).
        AlbumArtCard(
            bitmap = frontBitmap,
            shape = shape,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .graphicsLayer { translationX = frontOffset.value }
                .size(side),
        )

        AlbumArtCard(
            bitmap = nextBitmap,
            shape = shape,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .graphicsLayer {
                    translationX = nextOffset.value
                    alpha = nextAlpha.value
                }
                .size(side),
        )
    }
}

@Composable
private fun AlbumArtCard(
    bitmap: Bitmap?,
    shape: RoundedCornerShape,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .clip(shape)
            .background(CorusColors.Divider),
    ) {
        if (bitmap != null) {
            val imageBitmap = remember(bitmap) { bitmap.asImageBitmap() }
            Image(
                bitmap = imageBitmap,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

/** Decode album art off the main thread into a software [Bitmap]. */
private suspend fun loadAlbumArtBitmap(
    imageLoader: ImageLoader,
    context: android.content.Context,
    url: String?,
    size: Size,
): Bitmap? {
    if (url.isNullOrBlank()) return null
    return withContext(Dispatchers.IO) {
        runCatching {
            val request = ImageRequest.Builder(context)
                .data(url)
                .size(size)
                .crossfade(false)
                .allowHardware(false)
                .build()
            val result = imageLoader.execute(request) as? SuccessResult ?: return@runCatching null
            val image = result.image
            (image as? BitmapImage)?.bitmap
                ?: image.toBitmap(image.width.coerceAtLeast(1), image.height.coerceAtLeast(1))
        }.getOrNull()
    }
}

/** Distance to move a card fully off the leading/trailing edge — iOS `slideTravel`. */
internal fun albumArtSlideTravelPx(
    hostWidthPx: Float,
    artSidePx: Float,
    padPx: Float = 8f,
): Float = max(hostWidthPx, artSidePx) * 0.5f + artSidePx * 0.5f + padPx

/**
 * Whether a track change should run the Next carousel slide (vs snap).
 * Mirrors iOS `isPreparedNext` gating in `FullPlayerAlbumArtSlideView.apply`.
 */
internal fun shouldSlideAlbumArt(
    previousTrackId: String?,
    newTrackId: String?,
    preparedNextTrackId: String?,
    newUrl: String?,
    preparedNextUrl: String?,
): Boolean {
    if (previousTrackId == null || newTrackId == null) return false
    if (previousTrackId == newTrackId) return false
    val trackMatch = preparedNextTrackId != null && newTrackId == preparedNextTrackId
    val urlMatch = !newUrl.isNullOrBlank() && newUrl == preparedNextUrl
    return trackMatch || urlMatch
}
