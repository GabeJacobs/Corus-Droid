package fm.corus.android.ui.screens.feed

import androidx.compose.foundation.interaction.DragInteraction
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.pager.PagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState

/**
 * iOS `TabPagerController.reportProgress`: light impact the instant a
 * finger-driven swipe crosses a page midpoint (0.5, 1.5, …). Tab taps
 * animate programmatically and do not go through this path.
 */
internal class PagerMidpointHapticTracker {
    var lastPage = 0f
        private set
    var userSwipe = false
        private set

    fun onDragStart(settledPage: Int, currentPage: Float): Boolean {
        if (!userSwipe) {
            userSwipe = true
            lastPage = settledPage.toFloat()
        }
        return onPage(currentPage, scrolling = true)
    }

    /** @return true when a midpoint haptic should play. */
    fun onPage(page: Float, scrolling: Boolean): Boolean {
        val fire = userSwipe && FeedChromeCollapseMath.crossedMidpoint(lastPage, page)
        lastPage = page
        if (userSwipe && !scrolling) userSwipe = false
        return fire
    }
}

@Composable
internal fun PagerMidpointHaptic(
    pagerState: PagerState,
    enabled: Boolean,
    onMidpoint: () -> Unit,
) {
    val tracker = remember { PagerMidpointHapticTracker() }
    val latestOnMidpoint by rememberUpdatedState(onMidpoint)
    val dragged by pagerState.interactionSource.collectIsDraggedAsState()

    LaunchedEffect(pagerState, enabled, dragged) {
        if (!enabled || !dragged) return@LaunchedEffect
        val page = pagerState.currentPage + pagerState.currentPageOffsetFraction
        if (tracker.onDragStart(pagerState.settledPage, page)) {
            latestOnMidpoint()
        }
    }

    LaunchedEffect(pagerState, enabled) {
        if (!enabled) return@LaunchedEffect
        pagerState.interactionSource.interactions.collect { interaction ->
            if (interaction is DragInteraction.Start) {
                val page = pagerState.currentPage + pagerState.currentPageOffsetFraction
                if (tracker.onDragStart(pagerState.settledPage, page)) {
                    latestOnMidpoint()
                }
            }
        }
    }

    val page = pagerState.currentPage + pagerState.currentPageOffsetFraction
    val scrolling = pagerState.isScrollInProgress
    SideEffect {
        if (!enabled) {
            tracker.onPage(page, scrolling = false)
            return@SideEffect
        }
        if (tracker.onPage(page, scrolling)) {
            latestOnMidpoint()
        }
    }
}
