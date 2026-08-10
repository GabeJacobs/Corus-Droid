package fm.corus.android.ui.player

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.AnchoredDraggableState
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.anchoredDraggable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import fm.corus.android.domain.NowPlayingManager
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * Full-window expanding now-playing sheet.
 *
 * Geometry (iOS PlayerSlideContainer):
 * ```
 * travel = fullH - parkInset - miniH
 * ty(expansion) = (1 - t) * travel
 * ```
 * Collapsed: mini strip parked above the tab bar. Expanded: full screen.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ExpandingPlayerHost(
    expansionState: PlayerExpansionState,
    parkInsetPx: Float,
    artworkUrl: String?,
    nowPlayingManager: NowPlayingManager,
    onMiniHeightPxChanged: (Float) -> Unit,
    miniContent: @Composable (miniInteractive: Boolean) -> Unit,
    fullContent: @Composable (fullInteractive: Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val minMiniPx = with(density) { 56.dp.toPx() }
    var miniHeightPx by remember { mutableFloatStateOf(minMiniPx) }

    BackHandler(enabled = expansionState.isExpandedOrExpanding) {
        scope.launch { expansionState.collapse() }
    }

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val fullH = constraints.maxHeight.toFloat().coerceAtLeast(1f)
        val travel = (fullH - parkInsetPx.coerceAtLeast(0f) - miniHeightPx.coerceAtLeast(minMiniPx))
            .coerceAtLeast(1f)

        LaunchedEffect(travel) {
            expansionState.updateTravel(travel)
        }

        val state = expansionState.draggableState
        // Read offset every frame for live fades / tab-bar slide.
        @Suppress("UNUSED_EXPRESSION")
        state.offset
        val expansion = expansionState.expansionFraction(travel)
        val isMoving = expansionState.isMoving
        // iOS: mini hit-testing off while dragging.
        val allowsMiniInteraction = expansion < 0.05f && !isMoving

        val nestedScroll = remember(state) {
            playerSheetNestedScrollConnection(state)
        }

        val offsetY = when {
            state.offset.isNaN() -> travel
            else -> state.offset.coerceIn(0f, travel)
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .offset { IntOffset(0, offsetY.roundToInt()) }
                .nestedScroll(nestedScroll)
                .anchoredDraggable(state, Orientation.Vertical),
        ) {
            ExpandingPlayerScaffold(
                expansion = expansion,
                travelPx = travel,
                isMoving = isMoving,
                artworkUrl = artworkUrl,
                allowsMiniInteraction = allowsMiniInteraction,
                nowPlayingManager = nowPlayingManager,
                onMiniHeightChanged = { h ->
                    if (h > 0f && kotlin.math.abs(h - miniHeightPx) > 0.5f) {
                        miniHeightPx = h
                        onMiniHeightPxChanged(h)
                    }
                },
                miniContent = miniContent,
                fullContent = fullContent,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/**
 * When fully open, downward drag at the top of the scrollable collapses the sheet
 * (iOS scroll handoff). Upward drag while collapsed/mid expands first.
 */
@OptIn(ExperimentalFoundationApi::class)
private fun playerSheetNestedScrollConnection(
    state: AnchoredDraggableState<PlayerSheetValue>,
): NestedScrollConnection = object : NestedScrollConnection {
    override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
        val delta = available.y
        // Dragging up: expand sheet before scrolling content.
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
        val currentOffset = runCatching { state.requireOffset() }.getOrNull() ?: return Velocity.Zero
        return if (toFling < 0f && currentOffset > state.anchors.minAnchor()) {
            state.settle(toFling)
            available
        } else {
            Velocity.Zero
        }
    }

    override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
        state.settle(available.y)
        return available
    }
}
