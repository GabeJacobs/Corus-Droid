package fm.corus.android.ui.player

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
import androidx.compose.runtime.mutableStateOf
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

    /**
     * Whether the full-player scroll content is at (or past) its top edge.
     * Updated by [FullPlayerScreen] so nested-scroll can claim pull-down
     * immediately — matching iOS scroll-top handoff.
     */
    var isContentAtTop by mutableStateOf(true)

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
    // Match iOS PlayerSlideContainer: velocity snap at 550 pt/s; close at/under
    // ~50% expansion (iOS `shouldOpen = projected > 0.5`). Use 0.45 so a slow
    // drag that reaches visual halfway still dismisses instead of springing open.
    val velocityThresholdPx = with(density) { 550.dp.toPx() }
    val decay = rememberSplineBasedDecay<Float>()
    val draggable = remember {
        AnchoredDraggableState(
            initialValue = if (initiallyExpanded) {
                PlayerSheetValue.Expanded
            } else {
                PlayerSheetValue.Collapsed
            },
            positionalThreshold = { distance -> distance * 0.45f },
            velocityThreshold = { velocityThresholdPx },
            // iOS settle: dampingRatio 0.90, ~0.44s.
            snapAnimationSpec = spring(
                dampingRatio = 0.90f,
                stiffness = 350f,
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

/**
 * Shared fade for every full-player layer (art, title, controls, comments).
 *
 * Hits 1 at 55% expansion and 0 at 5% so chrome stays readable until the
 * sheet is near the mini-player park — the old (t − 0.2) / 0.45 curve went
 * fully transparent at 20%, leaving a large empty slab mid-dismiss.
 */
internal fun fullOpacity(expansion: Float): Float {
    val t = expansion.coerceIn(0f, 1f)
    return ((t - 0.05f) / 0.50f).coerceIn(0f, 1f)
}

/**
 * Full-player chrome receives hits when mostly open, and also for the whole
 * drag/settle so scroll → sheet handoff isn't cut off at ~45% travel (that used
 * to disable [verticalScroll] before the collapse threshold and snap the sheet
 * back open).
 */
internal fun fullPlayerInteractive(expansion: Float, isMoving: Boolean = false): Boolean =
    expansion > 0.55f || isMoving

internal fun miniPlayerInteractive(
    allowsMiniInteraction: Boolean,
    expansion: Float,
    travelPx: Float,
): Boolean {
    val alpha = miniOpacity(expansion, travelPx)
    return allowsMiniInteraction && alpha > 0.05f
}

internal fun fullPlayerLayerAboveMini(expansion: Float, isMoving: Boolean = false): Boolean =
    fullPlayerInteractive(expansion, isMoving)

internal fun playerCornerRadiusDp(expansion: Float, isMoving: Boolean): Float {
    val t = expansion.coerceIn(0f, 1f)
    if (t < 0.35f || t > 0.98f) return 0f
    if (isMoving) return 10f
    return (14.0 * kotlin.math.sin(t * Math.PI)).toFloat()
}
