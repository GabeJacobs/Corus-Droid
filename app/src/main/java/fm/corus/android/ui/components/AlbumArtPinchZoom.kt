package fm.corus.android.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculateZoom
import kotlinx.coroutines.launch

internal const val ALBUM_ART_PINCH_MIN_SCALE = 1f
internal const val ALBUM_ART_PINCH_MAX_SCALE = 4f
internal const val ALBUM_ART_PINCH_SNAP_THRESHOLD = 1.01f

internal fun albumArtShouldSnapBack(scale: Float): Boolean =
    scale > ALBUM_ART_PINCH_SNAP_THRESHOLD

internal fun clampAlbumArtOffset(
    scale: Float,
    offset: Offset,
    container: Size,
): Offset {
    if (container == Size.Zero || scale <= ALBUM_ART_PINCH_SNAP_THRESHOLD) {
        return Offset.Zero
    }
    val limitX = ((container.width * scale - container.width) / 2f).coerceAtLeast(0f)
    val limitY = ((container.height * scale - container.height) / 2f).coerceAtLeast(0f)
    return Offset(
        offset.x.coerceIn(-limitX, limitX),
        offset.y.coerceIn(-limitY, limitY),
    )
}

/**
 * Keeps the content under the previous centroid locked to [centroid], so a
 * pinch can zoom and slide in the same two-finger gesture.
 */
internal fun albumArtAnchoredZoom(
    scale: Float,
    offset: Offset,
    zoomChange: Float,
    pan: Offset,
    centroid: Offset,
    container: Size,
): Pair<Float, Offset> {
    val newScale = (scale * zoomChange).coerceIn(
        ALBUM_ART_PINCH_MIN_SCALE,
        ALBUM_ART_PINCH_MAX_SCALE,
    )
    if (newScale <= ALBUM_ART_PINCH_SNAP_THRESHOLD || container == Size.Zero) {
        return ALBUM_ART_PINCH_MIN_SCALE to Offset.Zero
    }
    val center = Offset(container.width / 2f, container.height / 2f)
    val current = centroid - center
    val previous = current - pan
    val factor = newScale / scale
    val newOffset = current - (previous - offset) * factor
    return newScale to clampAlbumArtOffset(newScale, newOffset, container)
}

/**
 * Pinch-to-inspect album art, matching iOS back-cover [ZoomableImageView]:
 * pinch up to 4×, pan while zoomed, snap back to 1× on release.
 *
 * One-finger taps and vertical scrolls are left unconsumed so tap-to-play
 * and the feed still work.
 */
fun Modifier.albumArtPinchZoom(): Modifier = composed {
    val scale = remember { Animatable(1f) }
    val offsetX = remember { Animatable(0f) }
    val offsetY = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    var container by remember { mutableStateOf(Size.Zero) }

    onSizeChanged { container = Size(it.width.toFloat(), it.height.toFloat()) }
        .graphicsLayer {
            scaleX = scale.value
            scaleY = scale.value
            translationX = offsetX.value
            translationY = offsetY.value
        }
        .pointerInput(Unit) {
            awaitEachGesture {
                awaitFirstDown(requireUnconsumed = false)
                var currentScale = scale.value
                var currentOffset = Offset(offsetX.value, offsetY.value)
                var previousCentroid: Offset? = null
                var didPinch = false

                do {
                    val event = awaitPointerEvent()
                    val pressedCount = event.changes.count { it.pressed }
                    if (pressedCount >= 2) {
                        didPinch = true
                        val box = if (container != Size.Zero) {
                            container
                        } else {
                            Size(size.width.toFloat(), size.height.toFloat())
                        }
                        val centroid = event.calculateCentroid()
                        val pan = previousCentroid?.let { centroid - it } ?: Offset.Zero
                        previousCentroid = centroid
                        val (nextScale, nextOffset) = albumArtAnchoredZoom(
                            scale = currentScale,
                            offset = currentOffset,
                            zoomChange = event.calculateZoom(),
                            pan = pan,
                            centroid = centroid,
                            container = box,
                        )
                        currentScale = nextScale
                        currentOffset = nextOffset
                        scope.launch {
                            scale.snapTo(currentScale)
                            offsetX.snapTo(currentOffset.x)
                            offsetY.snapTo(currentOffset.y)
                        }
                        event.changes.forEach { it.consume() }
                    } else {
                        previousCentroid = null
                    }
                } while (event.changes.any { it.pressed })

                if (didPinch && albumArtShouldSnapBack(currentScale)) {
                    val snapSpec = spring<Float>(
                        dampingRatio = Spring.DampingRatioLowBouncy,
                        stiffness = Spring.StiffnessMedium,
                    )
                    scope.launch {
                        launch { scale.animateTo(1f, snapSpec) }
                        launch { offsetX.animateTo(0f, snapSpec) }
                        launch { offsetY.animateTo(0f, snapSpec) }
                    }
                }
            }
        }
}
