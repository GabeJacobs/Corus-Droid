package fm.corus.android.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import kotlinx.coroutines.launch

/**
 * Full-screen zoomable image viewer with pan gestures.
 * Used for viewing images shared in messages and avatars at full resolution.
 */
@Composable
fun FullScreenImageView(
    imageUrl: String?,
    visible: Boolean,
    onDismiss: () -> Unit,
) {
    val animScale = remember { Animatable(1f) }
    val animOffsetX = remember { Animatable(0f) }
    val animOffsetY = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()

    // Mutable state for live gesture tracking (snapped to Animatable values)
    var scale by remember { mutableFloatStateOf(1f) }
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }

    // Sync animated values to render state
    scale = animScale.value
    offsetX = animOffsetX.value
    offsetY = animOffsetY.value

    // Reset transforms when visibility changes
    LaunchedEffect(visible) {
        if (visible) {
            animScale.snapTo(1f)
            animOffsetX.snapTo(0f)
            animOffsetY.snapTo(0f)
        }
    }

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(),
        exit = fadeOut(),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() },
                    onClick = {
                        if (scale <= 1.1f) onDismiss()
                    },
                ),
            contentAlignment = Alignment.Center,
        ) {
            AsyncImage(
                model = imageUrl,
                contentDescription = "Full screen image",
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer(
                        scaleX = scale,
                        scaleY = scale,
                        translationX = offsetX,
                        translationY = offsetY,
                    )
                    .pointerInput(Unit) {
                        awaitEachGesture {
                            // Wait for first finger down
                            awaitFirstDown(requireUnconsumed = false)
                            var currentScale = animScale.value
                            var currentOffsetX = animOffsetX.value
                            var currentOffsetY = animOffsetY.value

                            do {
                                val event = awaitPointerEvent()
                                val zoomChange = event.calculateZoom()
                                val panChange = event.calculatePan()

                                currentScale = (currentScale * zoomChange).coerceIn(1f, 5f)
                                if (currentScale > 1f) {
                                    currentOffsetX += panChange.x
                                    currentOffsetY += panChange.y
                                } else {
                                    currentOffsetX = 0f
                                    currentOffsetY = 0f
                                }

                                // Snap animatables to live values during gesture
                                scope.launch {
                                    animScale.snapTo(currentScale)
                                    animOffsetX.snapTo(currentOffsetX)
                                    animOffsetY.snapTo(currentOffsetY)
                                }

                                // Consume changes to prevent parent scroll interference
                                event.changes.forEach { change ->
                                    if (change.positionChanged()) {
                                        change.consume()
                                    }
                                }
                            } while (event.changes.any { it.pressed })

                            // Gesture ended — always snap back to 1x (matching iOS behavior)
                            if (currentScale > 1.01f) {
                                val snapSpec = spring<Float>(
                                    dampingRatio = Spring.DampingRatioLowBouncy,
                                    stiffness = Spring.StiffnessMedium,
                                )
                                scope.launch {
                                    launch { animScale.animateTo(1f, snapSpec) }
                                    launch { animOffsetX.animateTo(0f, snapSpec) }
                                    launch { animOffsetY.animateTo(0f, snapSpec) }
                                }
                            }
                        }
                    },
                contentScale = ContentScale.Fit,
            )

            // Close button
            IconButton(
                onClick = onDismiss,
                modifier = Modifier.align(Alignment.TopEnd),
            ) {
                Icon(Icons.Filled.Close, contentDescription = "Close", tint = Color.White)
            }
        }
    }
}
