package fm.corus.android.ui.player

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.horizontalDrag
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.unit.dp
import fm.corus.android.domain.NowPlayingManager
import fm.corus.android.domain.ScrubberClock
import fm.corus.android.ui.theme.CorusColors
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs

/**
 * Progress hairline flush with the expanding sheet's top edge.
 * Mirrors iOS `PlayerShellProgressLine` — 2.5dp line, 22dp hit target, no knob.
 */
@Composable
fun PlayerShellProgressLine(
    nowPlayingManager: NowPlayingManager,
    interactive: Boolean,
    modifier: Modifier = Modifier,
) {
    val state by nowPlayingManager.state.collectAsState()
    val clockTimeMs by ScrubberClock.time.collectAsState()
    val clockDurationMs by ScrubberClock.duration.collectAsState()
    val snapCounter by ScrubberClock.snapCounter.collectAsState()
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()

    var isScrubbing by remember { mutableStateOf(false) }
    var scrubFraction by remember { mutableFloatStateOf(0f) }
    var pendingSeekFraction by remember { mutableStateOf<Float?>(null) }
    var trackWidthPx by remember { mutableFloatStateOf(0f) }
    var lastSnap by remember { mutableStateOf(snapCounter) }
    var pendingClearJob by remember { mutableStateOf<Job?>(null) }

    val showsLine = state.hasActiveTrack
    val duration = clockDurationMs.coerceAtLeast(0L)
    val durationState = rememberUpdatedState(duration)
    val canScrub = duration > 0L && state.hasActiveTrack && interactive

    val playbackFraction = if (duration > 0L) {
        (clockTimeMs.toFloat() / duration.toFloat()).coerceIn(0f, 1f)
    } else {
        0f
    }
    val displayed = when {
        isScrubbing -> scrubFraction
        pendingSeekFraction != null -> pendingSeekFraction!!
        else -> playbackFraction
    }

    LaunchedEffect(state.trackId) {
        pendingClearJob?.cancel()
        pendingSeekFraction = null
        isScrubbing = false
        lastSnap = snapCounter
    }
    LaunchedEffect(clockTimeMs) {
        val pending = pendingSeekFraction ?: return@LaunchedEffect
        if (duration > 0L && abs(playbackFraction - pending) < 0.02f) {
            pendingSeekFraction = null
        }
    }
    LaunchedEffect(snapCounter) {
        lastSnap = snapCounter
    }

    val lineScaleY by animateFloatAsState(
        targetValue = if (isScrubbing) 1.8f else 1f,
        animationSpec = tween(120),
        label = "shellProgressScale",
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(22.dp)
            .alpha(if (showsLine) 1f else 0f)
            .onSizeChanged { trackWidthPx = it.width.toFloat() }
            .pointerInput(canScrub, trackWidthPx) {
                if (!canScrub || trackWidthPx <= 0f) return@pointerInput
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    var dragging = false
                    horizontalDrag(down.id) { change ->
                        val dx = change.positionChange().x
                        val dy = change.positionChange().y
                        if (!dragging) {
                            // Prefer horizontal — let vertical expand/collapse pass through.
                            if (abs(dx) <= abs(dy) || abs(dx) < 6f) return@horizontalDrag
                            dragging = true
                            isScrubbing = true
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        }
                        change.consume()
                        scrubFraction = (change.position.x / trackWidthPx).coerceIn(0f, 1f)
                    }
                    if (dragging) {
                        val seekDuration = durationState.value
                        pendingSeekFraction = scrubFraction
                        if (seekDuration > 0L) {
                            nowPlayingManager.seek((scrubFraction * seekDuration).toLong())
                        }
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        isScrubbing = false
                        pendingClearJob?.cancel()
                        pendingClearJob = scope.launch {
                            delay(2_000)
                            pendingSeekFraction = null
                        }
                    }
                }
            },
        contentAlignment = Alignment.TopStart,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(2.5.dp)
                .graphicsLayer {
                    scaleY = lineScaleY
                    transformOrigin = TransformOrigin(0.5f, 0f)
                }
                .background(CorusColors.Divider),
        )
        Box(
            modifier = Modifier
                .fillMaxWidth(displayed.coerceIn(0f, 1f))
                .height(2.5.dp)
                .graphicsLayer {
                    scaleY = lineScaleY
                    transformOrigin = TransformOrigin(0.5f, 0f)
                }
                .background(CorusColors.Secondary),
        )
    }
}
