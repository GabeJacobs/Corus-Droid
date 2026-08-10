package fm.corus.android.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.sin

/**
 * Compact playback pill for feed album art — bottom-trailing corner.
 * Snaps up when loading/playing; drops down quickly when paused.
 * Mirrors iOS `FeedPlayingPill`.
 */
@Composable
fun FeedPlayingPill(
    isPlaying: Boolean,
    isLoading: Boolean,
    modifier: Modifier = Modifier,
) {
    val isVisible = isPlaying || isLoading
    var isPresented by remember { mutableStateOf(false) }
    // Keeps eq bars mounted through snap-out so the capsule isn't empty first.
    var showsEqBars by remember { mutableStateOf(false) }
    val offsetY = remember { Animatable(HIDDEN_OFFSET) }
    val opacity = remember { Animatable(0f) }
    val snapIn = spring<Float>(
        dampingRatio = 0.86f,
        stiffness = Spring.StiffnessMedium,
    )

    LaunchedEffect(isPlaying) {
        if (isPlaying) showsEqBars = true
    }

    LaunchedEffect(isVisible) {
        if (isVisible) {
            if (isPlaying) showsEqBars = true
            if (!isPresented) {
                isPresented = true
                coroutineScope {
                    launch { offsetY.animateTo(0f, animationSpec = snapIn) }
                    launch { opacity.animateTo(1f, animationSpec = snapIn) }
                }
            }
        } else {
            // Debounce hide so loading→playing handoff doesn't replay entrance.
            delay(HIDE_DEBOUNCE_MS)
            if (isPlaying || isLoading) return@LaunchedEffect
            isPresented = false
            coroutineScope {
                launch {
                    offsetY.animateTo(HIDDEN_OFFSET, animationSpec = tween(SNAP_OUT_MS))
                }
                launch {
                    opacity.animateTo(0f, animationSpec = tween(SNAP_OUT_MS))
                }
            }
            if (!(isPlaying || isLoading)) {
                showsEqBars = false
            }
        }
    }

    // First composition while already visible (e.g. recycled card mid-play).
    LaunchedEffect(Unit) {
        if (isVisible) {
            if (isPlaying) showsEqBars = true
            isPresented = true
            offsetY.snapTo(0f)
            opacity.snapTo(1f)
        }
    }

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.BottomEnd,
    ) {
        Box(
            modifier = Modifier
                .padding(CORNER_INSET)
                .offset(y = offsetY.value.dp)
                .alpha(opacity.value)
                .shadow(4.dp, CircleShape, ambientColor = Color.Black.copy(alpha = 0.45f))
                .shadow(10.dp, CircleShape, ambientColor = Color.Black.copy(alpha = 0.25f))
                .background(
                    color = Color.Black.copy(
                        alpha = if (isPlaying && !isLoading) 0.8f else 0.82f,
                    ),
                    shape = CircleShape,
                )
                .border(1.dp, Color.White.copy(alpha = 0.12f), CircleShape)
                .padding(horizontal = PILL_HORIZONTAL_PADDING)
                .size(PILL_MIN_SIZE),
            contentAlignment = Alignment.Center,
        ) {
            when {
                isLoading && !isPlaying -> {
                    CircularProgressIndicator(
                        color = Color.White,
                        trackColor = Color.White.copy(alpha = 0.22f),
                        modifier = Modifier.size(14.dp),
                        strokeWidth = 2.dp,
                    )
                }
                isPlaying || showsEqBars -> {
                    FeedEqBars(modifier = Modifier.size(14.dp))
                }
            }
        }
    }
}

@Composable
private fun FeedEqBars(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "feedEq")
    // Three bars with iOS periods / phase offsets, driven as 0→1 loops.
    val t0 by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 820, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "eq0",
    )
    val t1 by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1180, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "eq1",
    )
    val t2 by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1420, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "eq2",
    )

    Canvas(modifier = modifier) {
        val barWidth = 2.5.dp.toPx()
        val spacing = 3.dp.toPx()
        val total = 3 * barWidth + 2 * spacing
        var x = (size.width - total) / 2f
        val specs = listOf(
            EqSpec(t0, from = 0.22f, to = 1.0f, phase = 0f),
            EqSpec(t1, from = 0.5f, to = 0.82f, phase = 0.35f / 1.18f),
            EqSpec(t2, from = 0.12f, to = 0.68f, phase = 0.85f / 1.42f),
        )
        for (spec in specs) {
            val cycle = sin(((spec.t + spec.phase) % 1f) * 2.0 * PI).toFloat()
            val scale = spec.from + (spec.to - spec.from) * (0.5f + 0.5f * cycle)
            val h = size.height * scale
            drawRoundRect(
                color = Color.White,
                topLeft = Offset(x, size.height - h),
                size = Size(barWidth, h),
                cornerRadius = CornerRadius(1.25.dp.toPx()),
            )
            x += barWidth + spacing
        }
    }
}

private data class EqSpec(
    val t: Float,
    val from: Float,
    val to: Float,
    val phase: Float,
)

private const val HIDDEN_OFFSET = 8f
private val CORNER_INSET = 10.dp
private val PILL_MIN_SIZE = 32.dp
private val PILL_HORIZONTAL_PADDING = 12.dp
private const val HIDE_DEBOUNCE_MS = 100L
private const val SNAP_OUT_MS = 110
