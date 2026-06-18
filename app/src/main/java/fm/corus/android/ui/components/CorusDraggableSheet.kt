package fm.corus.android.ui.components

import android.os.Build
import android.view.WindowManager
import androidx.core.view.WindowCompat
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.rememberSplineBasedDecay
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.AnchoredDraggableState
import androidx.compose.foundation.gestures.DraggableAnchors
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.anchoredDraggable
import androidx.compose.foundation.gestures.animateTo
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import fm.corus.android.ui.theme.CorusColors
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

/**
 * A two-detent bottom sheet that mirrors the iOS comments sheet
 * (`.presentationDetents([.medium, .large])`).
 *
 * Why this exists instead of Material3 [androidx.compose.material3.ModalBottomSheet]:
 * a ModalBottomSheet measures its content once and slides it from the top, so a
 * bottom-pinned composer is pushed off-screen at the half-height (PartiallyExpanded)
 * detent. Here the panel's HEIGHT tracks the drag offset (panel = availableHeight -
 * offset, anchored to the screen bottom), so content laid out as
 * `Column { list.weight(1f); composer }` keeps the composer glued to the visible
 * bottom at EVERY detent — exactly like iOS.
 *
 * Behaviors matched:
 *  - opens at the half peek ([CorusSheetValue.PartiallyExpanded]); drag up to
 *    [CorusSheetValue.Expanded]; drag down to dismiss
 *  - swipe-to-expand-then-scroll: an upward drag on the list first expands the sheet,
 *    then scrolls (Material3's nested-scroll handoff, replicated here)
 *  - keyboard: hosted in a [Dialog] window configured edge-to-edge so [imePadding]
 *    lifts the panel (and its pinned composer) above the IME; call
 *    [CorusSheetState.expand] on input focus to grow to full height
 *  - works on every screen size (detents are screen fractions, like iOS)
 */

enum class CorusSheetValue { Hidden, PartiallyExpanded, Expanded }

@OptIn(ExperimentalFoundationApi::class)
@Stable
class CorusSheetState internal constructor(
    val draggableState: AnchoredDraggableState<CorusSheetValue>,
) {
    val currentValue: CorusSheetValue get() = draggableState.currentValue
    val targetValue: CorusSheetValue get() = draggableState.targetValue
    val isExpanded: Boolean get() = draggableState.currentValue == CorusSheetValue.Expanded

    /** Grow the sheet to full height (mirrors iOS focus -> `selectedDetent = .large`). */
    suspend fun expand() {
        if (draggableState.anchors.hasAnchorFor(CorusSheetValue.Expanded)) {
            draggableState.animateTo(CorusSheetValue.Expanded)
        }
    }

    suspend fun partialExpand() {
        if (draggableState.anchors.hasAnchorFor(CorusSheetValue.PartiallyExpanded)) {
            draggableState.animateTo(CorusSheetValue.PartiallyExpanded)
        }
    }

    internal suspend fun hide() {
        draggableState.animateTo(CorusSheetValue.Hidden)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun rememberCorusSheetState(): CorusSheetState {
    val density = LocalDensity.current
    val decay = rememberSplineBasedDecay<Float>()
    val velocityThresholdPx = with(density) { 125.dp.toPx() }
    val draggable = remember {
        AnchoredDraggableState(
            initialValue = CorusSheetValue.Hidden,
            positionalThreshold = { distance -> distance * 0.5f },
            velocityThreshold = { velocityThresholdPx },
            snapAnimationSpec = tween(durationMillis = 300, easing = FastOutSlowInEasing),
            decayAnimationSpec = decay,
        )
    }
    return remember { CorusSheetState(draggable) }
}

/**
 * Scrim opacity as a function of how far the sheet is open. Pulled out as a pure
 * function so it can be unit-tested without composing the sheet.
 *
 * @param offset current sheet offset (px from the top); [Float.NaN] before layout
 * @param minAnchor offset at the most-expanded anchor (smallest)
 * @param maxAnchor offset at the hidden anchor (largest)
 */
fun corusSheetScrimAlpha(
    offset: Float,
    minAnchor: Float,
    maxAnchor: Float,
    maxAlpha: Float = 0.5f,
): Float {
    if (offset.isNaN() || maxAnchor <= minAnchor) return 0f
    val openFraction = ((maxAnchor - offset) / (maxAnchor - minAnchor)).coerceIn(0f, 1f)
    return openFraction * maxAlpha
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun CorusDraggableSheet(
    onDismiss: () -> Unit,
    sheetState: CorusSheetState,
    modifier: Modifier = Modifier,
    containerColor: Color = CorusColors.Background,
    content: @Composable () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val state = sheetState.draggableState

    // Rendered as an in-window overlay, NOT a Dialog. A Compose Dialog reports the nav-bar
    // and IME insets as 0 inside its window (verified via logging), which kept landing the
    // composer under the nav bar / keyboard. In the host (main) window those insets are
    // real, so navigationBarsPadding()/imePadding() work. The caller MUST place this on top
    // of everything (it is hosted at the app root so it covers the tab bar).

    var hasShown by remember { mutableStateOf(false) }
    LaunchedEffect(state.currentValue) {
        if (state.currentValue != CorusSheetValue.Hidden) {
            hasShown = true
        } else if (hasShown) {
            onDismiss()
        }
    }

    BackHandler(enabled = true) {
        scope.launch {
            // Collapse full -> peek before dismissing (predictive-back-ish).
            if (state.currentValue == CorusSheetValue.Expanded &&
                state.anchors.hasAnchorFor(CorusSheetValue.PartiallyExpanded)
            ) {
                sheetState.partialExpand()
            } else {
                sheetState.hide()
            }
        }
    }

    val density = LocalDensity.current
    val statusTopPx = WindowInsets.statusBars.getTop(density).toFloat()

    Box(modifier = Modifier.fillMaxSize()) {
        // Scrim — full screen (dims behind the nav bar too); fades with how open the sheet
        // is; tap to dismiss.
        val scrimAlpha = corusSheetScrimAlpha(
            offset = state.offset,
            minAnchor = state.anchors.minAnchor(),
            maxAnchor = state.anchors.maxAnchor(),
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = scrimAlpha))
                .pointerInput(Unit) {
                    detectTapGestures { scope.launch { sheetState.hide() } }
                },
        )

        // The panel fills to the very bottom of the screen so the sheet's surface (its
        // background) extends under the 3-button nav bar — one continuous sheet, with no
        // dark scrim strip showing below it. The composer (inside `content`) insets itself
        // above the nav bar + keyboard via WindowInsets.ime.union(navigationBars), so only
        // the sheet's own background sits behind the nav bar.
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val fullHeightPx = constraints.maxHeight.toFloat()

            // (Re)build anchors when the available height changes. PartiallyExpanded is the
            // half peek; Expanded leaves a top gap (like iOS .large); Hidden is off-bottom.
            LaunchedEffect(fullHeightPx, statusTopPx) {
                if (fullHeightPx > 0f) {
                    val expandedTopPx = maxOf(statusTopPx, fullHeightPx * 0.06f)
                    state.updateAnchors(
                        DraggableAnchors {
                            CorusSheetValue.Expanded at expandedTopPx
                            CorusSheetValue.PartiallyExpanded at fullHeightPx / 2f
                            CorusSheetValue.Hidden at fullHeightPx
                        }
                    )
                }
            }

            // First show: slide up from Hidden to the half peek.
            LaunchedEffect(fullHeightPx) {
                if (fullHeightPx > 0f && state.currentValue == CorusSheetValue.Hidden && !hasShown) {
                    sheetState.partialExpand()
                }
            }

            // A downward fling faster than this skips the peek and dismisses outright,
            // matching iOS where a quick flick from the large detent closes the sheet
            // instead of stopping at medium. Slower drags still settle to the nearest anchor.
            val dismissVelocityThresholdPx = with(density) { 300.dp.toPx() }
            val nestedScroll = remember(state, dismissVelocityThresholdPx) {
                sheetNestedScrollConnection(state, dismissVelocityThresholdPx)
            }

            Surface(
                color = containerColor,
                shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
                modifier = modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .nestedScroll(nestedScroll)
                    .anchoredDraggable(state, Orientation.Vertical)
                    // Panel height = visible region (containerHeight - offset), bottom-
                    // aligned, so it grows upward and the composer (last child) stays pinned
                    // to the visible bottom at every detent. Offset is read in measure, so
                    // dragging relayouts without recomposing the comment list each frame.
                    .sheetPanelHeight(state),
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    // Compact grabber (Material3's DragHandle reserves ~44dp, too tall).
                    Box(
                        modifier = Modifier
                            .align(Alignment.CenterHorizontally)
                            .padding(top = 10.dp, bottom = 4.dp)
                            .size(width = 36.dp, height = 4.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(CorusColors.Tertiary.copy(alpha = 0.4f)),
                    )
                    Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
                        content()
                    }
                }
            }
        }
    }
}

/**
 * Sizes the (bottom-aligned) panel to (containerHeight - offset) so it grows upward
 * from the screen bottom. Reading the offset in the measure phase relayouts on drag
 * without recomposing the panel content.
 */
@OptIn(ExperimentalFoundationApi::class)
private fun Modifier.sheetPanelHeight(
    state: AnchoredDraggableState<CorusSheetValue>,
): Modifier = this.layout { measurable, constraints ->
    val raw = state.offset
    val maxH = constraints.maxHeight
    val h = (if (raw.isNaN()) 0 else maxH - raw.roundToInt()).coerceIn(0, maxH)
    val placeable = measurable.measure(constraints.copy(minHeight = h, maxHeight = h))
    layout(placeable.width, h) { placeable.place(0, 0) }
}

/**
 * Nested-scroll handoff so an upward drag first expands the sheet, then scrolls the
 * list, and a downward drag at the top of the list collapses then dismisses the
 * sheet. Adapted from Material3's ConsumeSwipeWithinBottomSheetBounds connection.
 */
@OptIn(ExperimentalFoundationApi::class)
private fun sheetNestedScrollConnection(
    state: AnchoredDraggableState<CorusSheetValue>,
    dismissVelocityThresholdPx: Float,
): NestedScrollConnection = object : NestedScrollConnection {
    override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
        val delta = available.y
        return if (delta < 0f && source == NestedScrollSource.UserInput) {
            Offset(0f, state.dispatchRawDelta(delta))
        } else {
            Offset.Zero
        }
    }

    override fun onPostScroll(
        consumed: Offset,
        available: Offset,
        source: NestedScrollSource,
    ): Offset {
        return if (source == NestedScrollSource.UserInput) {
            Offset(0f, state.dispatchRawDelta(available.y))
        } else {
            Offset.Zero
        }
    }

    override suspend fun onPreFling(available: Velocity): Velocity {
        val toFling = available.y
        val currentOffset = state.requireOffset()
        return if (toFling < 0f && currentOffset > state.anchors.minAnchor()) {
            state.settle(toFling)
            available
        } else {
            Velocity.Zero
        }
    }

    override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
        // Fast downward flick → dismiss the whole sheet, skipping the peek detent (iOS
        // parity). A slower fling falls through to settle() and lands on the nearest anchor.
        if (available.y > dismissVelocityThresholdPx &&
            state.currentValue != CorusSheetValue.Hidden &&
            state.anchors.hasAnchorFor(CorusSheetValue.Hidden)
        ) {
            state.animateTo(CorusSheetValue.Hidden)
        } else {
            state.settle(available.y)
        }
        return available
    }
}
