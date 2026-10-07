package fm.corus.android.ui.screens.feed

import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.interaction.DragInteraction
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

/**
 * Tab taps show their destination immediately, like iOS, while the feed slides.
 * Finger swipes keep interpolating the chrome through drag and settling motion.
 */
@Composable
internal fun feedTabBarPagerOffset(pagerState: PagerState): Float {
    var userSwipe by remember(pagerState) { mutableStateOf(false) }
    val dragged by pagerState.interactionSource.collectIsDraggedAsState()

    LaunchedEffect(pagerState) {
        pagerState.interactionSource.interactions.collect { interaction ->
            if (interaction is DragInteraction.Start) userSwipe = true
        }
    }
    val scrolling = pagerState.isScrollInProgress
    SideEffect {
        if (dragged) userSwipe = true
        else if (!scrolling) userSwipe = false
    }

    return if (scrolling && !dragged && !userSwipe) {
        // Do not activate controls on pages crossed by a programmatic jump.
        pagerState.targetPage.toFloat()
    } else {
        pagerState.currentPage + pagerState.currentPageOffsetFraction
    }
}
