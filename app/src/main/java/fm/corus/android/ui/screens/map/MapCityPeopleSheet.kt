package fm.corus.android.ui.screens.map

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.rememberSplineBasedDecay
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.AnchoredDraggableState
import androidx.compose.foundation.gestures.DraggableAnchors
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.anchoredDraggable
import androidx.compose.foundation.gestures.animateTo
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import fm.corus.android.ui.theme.CorusColors
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

internal const val MAP_CITY_SHEET_PEEK_FRACTION = 0.52f
/** Large detent matches iOS `.large`: covers the map chrome, but leaves a top
 * strip of map so the grabber stays reachable and the sheet is not full-screen. */
internal const val MAP_CITY_SHEET_EXPANDED_FRACTION = 0.88f

internal enum class MapCitySheetValue { Hidden, Peek, Expanded }

/**
 * Residual fling after the people list scrolls should not collapse the sheet.
 * iOS `.scrolls` at `.large` rubber-bands at the top; only a fling that starts
 * with the list already at an edge is a sheet gesture.
 */
internal fun mapCitySheetTakesLeftoverFling(listConsumedVelocityY: Float): Boolean =
    kotlin.math.abs(listConsumedVelocityY) <= 1f

/** The city sheet has exactly two visible detents; a downward fling advances one. */
internal fun mapCitySheetNextDown(value: MapCitySheetValue): MapCitySheetValue = when (value) {
    MapCitySheetValue.Expanded -> MapCitySheetValue.Peek
    MapCitySheetValue.Peek -> MapCitySheetValue.Hidden
    MapCitySheetValue.Hidden -> MapCitySheetValue.Hidden
}

/**
 * Height of the bottom-aligned city sheet for a drag [offset] from the top of
 * the map. NaN (anchors not ready) uses the peek fraction so the first frame
 * does not flash a zero-height panel.
 */
internal fun mapCitySheetExpandedOffsetPx(
    maxHeight: Float,
    expandedFraction: Float = MAP_CITY_SHEET_EXPANDED_FRACTION,
): Float {
    if (maxHeight <= 0f) return 0f
    return (maxHeight * (1f - expandedFraction)).coerceIn(0f, maxHeight)
}

internal fun mapCitySheetHeightPx(
    offset: Float,
    maxHeight: Int,
    peekFraction: Float = MAP_CITY_SHEET_PEEK_FRACTION,
    expandedFraction: Float = MAP_CITY_SHEET_EXPANDED_FRACTION,
): Int {
    if (maxHeight <= 0) return 0
    val maxExpandedHeight = (maxHeight - mapCitySheetExpandedOffsetPx(maxHeight.toFloat(), expandedFraction))
        .roundToInt()
        .coerceIn(0, maxHeight)
    val height = if (offset.isNaN()) {
        (maxHeight * peekFraction).roundToInt()
    } else {
        maxHeight - offset.roundToInt()
    }
    return height.coerceIn(0, maxExpandedHeight)
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun MapCityPeopleSheet(
    onDismiss: () -> Unit,
    onHeightChanged: (Int) -> Unit,
    initialValue: MapCitySheetValue = MapCitySheetValue.Peek,
    onDetentChanged: (MapCitySheetValue) -> Unit = {},
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val density = LocalDensity.current
    val decay = rememberSplineBasedDecay<Float>()
    val velocityThresholdPx = with(density) { 125.dp.toPx() }
    val dismissVelocityThresholdPx = with(density) { 300.dp.toPx() }
    val state = remember {
        AnchoredDraggableState(
            initialValue = initialValue,
            positionalThreshold = { distance -> distance * 0.5f },
            velocityThreshold = { velocityThresholdPx },
            snapAnimationSpec = tween(durationMillis = 300, easing = FastOutSlowInEasing),
            decayAnimationSpec = decay,
        )
    }
    val scope = rememberCoroutineScope()
    val nestedScroll = remember(state, dismissVelocityThresholdPx) {
        mapCitySheetNestedScrollConnection(state, dismissVelocityThresholdPx)
    }

    val current = state.currentValue
    LaunchedEffect(current) {
        if (current == MapCitySheetValue.Hidden) onDismiss()
        else onDetentChanged(current)
    }

    BoxWithConstraints(modifier.fillMaxWidth()) {
        val fullHeightPx = constraints.maxHeight.toFloat()
        LaunchedEffect(fullHeightPx) {
            if (fullHeightPx > 0f) {
                state.updateAnchors(
                    DraggableAnchors {
                        MapCitySheetValue.Expanded at mapCitySheetExpandedOffsetPx(fullHeightPx)
                        MapCitySheetValue.Peek at fullHeightPx * (1f - MAP_CITY_SHEET_PEEK_FRACTION)
                        MapCitySheetValue.Hidden at fullHeightPx
                    },
                )
            }
        }

        Surface(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .nestedScroll(nestedScroll)
                .anchoredDraggable(state, Orientation.Vertical)
                .mapCitySheetPanelHeight(state)
                .onSizeChanged { onHeightChanged(it.height) }
                .clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() },
                ) {},
            color = CorusColors.Background,
            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
            shadowElevation = 12.dp,
        ) {
            Column(Modifier.fillMaxSize()) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(28.dp)
                        .clickable {
                            scope.launch {
                                val target = if (state.currentValue == MapCitySheetValue.Expanded) {
                                    MapCitySheetValue.Peek
                                } else {
                                    MapCitySheetValue.Expanded
                                }
                                if (state.anchors.hasAnchorFor(target)) {
                                    state.animateTo(target)
                                }
                            }
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        Modifier
                            .size(36.dp, 4.dp)
                            .clip(CircleShape)
                            .background(CorusColors.Secondary.copy(alpha = .4f)),
                    )
                }
                content()
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
private fun Modifier.mapCitySheetPanelHeight(
    state: AnchoredDraggableState<MapCitySheetValue>,
): Modifier = this.layout { measurable, constraints ->
    val maxH = constraints.maxHeight
    val h = mapCitySheetHeightPx(state.offset, maxH)
    val placeable = measurable.measure(constraints.copy(minHeight = h, maxHeight = h))
    layout(placeable.width, h) { placeable.place(0, 0) }
}

/**
 * Nested-scroll handoff matching iOS city-sheet detents:
 * - Upward drag expands the sheet before the list scrolls.
 * - Downward drag at the top of the list collapses, then dismisses.
 * - A flick that scrolls the list does not collapse/dismiss when it reaches
 *   the top; leftover velocity is ignored.
 */
@OptIn(ExperimentalFoundationApi::class)
private fun mapCitySheetNestedScrollConnection(
    state: AnchoredDraggableState<MapCitySheetValue>,
    dismissVelocityThresholdPx: Float,
): NestedScrollConnection = object : NestedScrollConnection {
    private var listFlingActive = false

    override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
        val delta = available.y
        if (delta >= 0f || source != NestedScrollSource.UserInput) return Offset.Zero
        if (!state.anchors.hasAnchorFor(MapCitySheetValue.Peek)) return Offset.Zero
        return Offset(0f, state.dispatchRawDelta(delta))
    }

    override fun onPostScroll(
        consumed: Offset,
        available: Offset,
        source: NestedScrollSource,
    ): Offset {
        if (source != NestedScrollSource.UserInput) return Offset.Zero
        if (!state.anchors.hasAnchorFor(MapCitySheetValue.Peek)) return Offset.Zero
        // Leftover downward deltas from a list fling would collapse the sheet
        // as soon as the list hits the top. Keep those on the list.
        if (listFlingActive && available.y > 0f) return Offset.Zero
        return Offset(0f, state.dispatchRawDelta(available.y))
    }

    override suspend fun onPreFling(available: Velocity): Velocity {
        val toFling = available.y
        val currentOffset = runCatching { state.requireOffset() }.getOrNull() ?: return Velocity.Zero
        return if (toFling < 0f && currentOffset > state.anchors.minAnchor()) {
            state.settle(toFling)
            available
        } else {
            if (toFling > 0f) listFlingActive = true
            Velocity.Zero
        }
    }

    override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
        listFlingActive = false
        // List consumed this fling — leftover velocity is rubber-banding, not a
        // sheet dismiss. A fling that started at the top has consumed ≈ 0.
        if (!mapCitySheetTakesLeftoverFling(consumed.y) && available.y > 0f) {
            // Never leave the panel stranded between its expanded and peek
            // anchors when part of the drag reached the sheet before the fling.
            state.animateTo(state.currentValue)
            return available
        }
        if (available.y > dismissVelocityThresholdPx &&
            state.currentValue != MapCitySheetValue.Hidden &&
            state.anchors.hasAnchorFor(MapCitySheetValue.Hidden)
        ) {
            state.animateTo(mapCitySheetNextDown(state.currentValue))
        } else {
            state.settle(available.y)
        }
        return available
    }
}
