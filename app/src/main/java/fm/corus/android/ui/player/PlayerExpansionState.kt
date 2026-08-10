package fm.corus.android.ui.player

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.rememberSplineBasedDecay
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.AnchoredDraggableState
import androidx.compose.foundation.gestures.DraggableAnchors
import androidx.compose.foundation.gestures.animateTo
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp

/**
 * Collapsed = mini strip parked above the tab bar.
 * Expanded = full-screen player (iOS ExpandingNowPlayingPlayer).
 */
enum class PlayerSheetValue { Collapsed, Expanded }

/**
 * Continuous 0…1 expansion for the now-playing sheet.
 *
 * Mirrors iOS `playerExpansion` (settled) + `PlayerSlideLiveExpansion` (live):
 * [AnchoredDraggableState.offset] drives per-frame fades / tab-bar slide;
 * [currentValue] is the snapped settled detent.
 */
@OptIn(ExperimentalFoundationApi::class)
@Stable
class PlayerExpansionState internal constructor(
    val draggableState: AnchoredDraggableState<PlayerSheetValue>,
) {
    var travelPx by mutableFloatStateOf(0f)
        private set

    val currentValue: PlayerSheetValue get() = draggableState.currentValue
    val targetValue: PlayerSheetValue get() = draggableState.targetValue
    val isExpanded: Boolean get() = draggableState.currentValue == PlayerSheetValue.Expanded
    val isExpandedOrExpanding: Boolean
        get() = targetValue == PlayerSheetValue.Expanded ||
            currentValue == PlayerSheetValue.Expanded

    /**
     * Live expansion 0…1 from the drag offset.
     * `ty = (1 - t) * travel` → `t = 1 - ty / travel`.
     *
     * Callers that need recomposition on drag must also read
     * [draggableState.offset] (or use [liveExpansion]).
     */
    fun expansionFraction(travel: Float = travelPx): Float {
        if (travel <= 0f) {
            return if (currentValue == PlayerSheetValue.Expanded) 1f else 0f
        }
        val offset = draggableState.offset
        if (offset.isNaN()) {
            return if (currentValue == PlayerSheetValue.Expanded) 1f else 0f
        }
        return (1f - (offset / travel)).coerceIn(0f, 1f)
    }

    val isMoving: Boolean
        get() = draggableState.isAnimationRunning ||
            draggableState.offset.let {
                !it.isNaN() &&
                    kotlin.math.abs(it - draggableState.anchors.positionOf(currentValue)) > 0.5f
            }

    suspend fun expand() {
        if (draggableState.anchors.hasAnchorFor(PlayerSheetValue.Expanded)) {
            draggableState.animateTo(PlayerSheetValue.Expanded)
        }
    }

    suspend fun collapse() {
        if (draggableState.anchors.hasAnchorFor(PlayerSheetValue.Collapsed)) {
            draggableState.animateTo(PlayerSheetValue.Collapsed)
        }
    }

    fun updateTravel(travel: Float) {
        if (travel <= 0f) return
        travelPx = travel
        val previous = currentValue
        draggableState.updateAnchors(
            DraggableAnchors {
                PlayerSheetValue.Expanded at 0f
                PlayerSheetValue.Collapsed at travel
            },
            previous,
        )
    }
}

/** Reads drag offset so callers recompose while the sheet moves. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PlayerExpansionState.liveExpansion(): Float {
    // Subscribe to per-frame offset updates.
    @Suppress("UNUSED_EXPRESSION")
    draggableState.offset
    return expansionFraction()
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun rememberPlayerExpansionState(
    initiallyExpanded: Boolean = false,
): PlayerExpansionState {
    val density = LocalDensity.current
    // iOS: velocity snap at 850 pt/s; positional threshold ~0.35 of travel.
    val velocityThresholdPx = with(density) { 850.dp.toPx() }
    val decay = rememberSplineBasedDecay<Float>()
    val draggable = remember {
        AnchoredDraggableState(
            initialValue = if (initiallyExpanded) {
                PlayerSheetValue.Expanded
            } else {
                PlayerSheetValue.Collapsed
            },
            positionalThreshold = { distance -> distance * 0.35f },
            velocityThreshold = { velocityThresholdPx },
            // iOS drag settle: dampingRatio 0.88, ~0.36s.
            snapAnimationSpec = spring(
                dampingRatio = 0.88f,
                stiffness = Spring.StiffnessMediumLow,
            ),
            decayAnimationSpec = decay,
        )
    }
    return remember { PlayerExpansionState(draggable) }
}

internal fun miniOpacity(expansion: Float, travelPx: Float): Float {
    val dragged = expansion.coerceIn(0f, 1f) * travelPx.coerceAtLeast(1f)
    return (1f - (dragged / 100f).coerceIn(0f, 1f))
}

internal fun fullOpacity(expansion: Float): Float {
    val t = expansion.coerceIn(0f, 1f)
    return ((t - 0.2f) / 0.45f).coerceIn(0f, 1f)
}

internal fun fullPlayerInteractive(expansion: Float): Boolean = expansion > 0.55f

internal fun miniPlayerInteractive(
    allowsMiniInteraction: Boolean,
    expansion: Float,
    travelPx: Float,
): Boolean {
    val alpha = miniOpacity(expansion, travelPx)
    return allowsMiniInteraction && alpha > 0.05f
}

internal fun fullPlayerLayerAboveMini(expansion: Float): Boolean = fullPlayerInteractive(expansion)

internal fun playerCornerRadiusDp(expansion: Float, isMoving: Boolean): Float {
    val t = expansion.coerceIn(0f, 1f)
    if (t < 0.35f || t > 0.98f) return 0f
    if (isMoving) return 10f
    return (14.0 * kotlin.math.sin(t * Math.PI)).toFloat()
}
