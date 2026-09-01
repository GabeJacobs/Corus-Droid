package fm.corus.android.ui.util

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.ui.unit.dp
import fm.corus.android.ui.theme.CorusSpacing
import kotlin.math.abs
import kotlinx.coroutines.yield

/** iOS FeedView skip scroll: `.timingCurve(0.22, 0.82, 0.28, 1.0, duration: 0.42)`. */
private val FeedFollowScrollSpec = tween<Float>(
    durationMillis = 420,
    easing = CubicBezierEasing(0.22f, 0.82f, 0.28f, 1.0f),
)

/**
 * Each post's trailing divider is `padding(vertical = sm)` around a 1.dp line.
 * Follow-scroll peeks that line plus a sliver of space above it so the next
 * post doesn't sit flush under the status bar.
 */
val FeedFollowScrollPeekAbovePost = CorusSpacing.sm + 1.dp + CorusSpacing.xs

/**
 * LazyColumn index of a feed post sitting after [prefixItemCount] leading
 * items (header, optional trial banner, …).
 */
fun feedPostLazyIndex(postIndex: Int, prefixItemCount: Int): Int =
    postIndex + prefixItemCount

/**
 * Pixels to scroll so an item currently at [itemOffset] lands with its top
 * [topInsetPx] below the viewport top (clears a frosted status strip).
 */
fun scrollDeltaToAlignItemTop(itemOffset: Int, topInsetPx: Int): Int =
    itemOffset - topInsetPx.coerceAtLeast(0)

/**
 * [LazyListState.scrollToItem] offset that places the item [topInsetPx]
 * below the viewport top. Compose treats `0` as the content-padding edge,
 * not y=0 — passing `-inset` after a frozen chrome pad lands the row at
 * `pad + inset` (about a third of the screen).
 */
fun scrollOffsetToAlignItemTop(beforeContentPadding: Int, topInsetPx: Int): Int =
    beforeContentPadding.coerceAtLeast(0) - topInsetPx.coerceAtLeast(0)

/**
 * Scroll so [index] sits just below [topInsetPx] — the poster's username
 * visible under the status strip, matching a tapped feed card.
 *
 * Bare [LazyListState.animateScrollToItem] often no-ops or underscrolls when
 * the target is already partially visible (Next, with the next header peeking
 * above the miniplayer). Pinning to offset 0 is also wrong on the immersive
 * feed: the list extends under the status bar, so offset 0 hides the author
 * row behind the frost.
 *
 * When the item is on screen we scroll by [scrollDeltaToAlignItemTop].
 * Corrections use [scrollBy] against the visual offset — never
 * [scrollToItem] `-inset`, which double-counts [beforeContentPadding].
 */
suspend fun LazyListState.animateScrollItemToTop(index: Int, topInsetPx: Int = 0) {
    if (index < 0) return
    val inset = topInsetPx.coerceAtLeast(0)
    fun visualTop(): Int? =
        layoutInfo.visibleItemsInfo.firstOrNull { it.index == index }?.offset
    fun teleportOffset(): Int =
        scrollOffsetToAlignItemTop(layoutInfo.beforeContentPadding, inset)

    val visibleTop = visualTop()
    if (visibleTop != null) {
        val delta = scrollDeltaToAlignItemTop(visibleTop, inset).toFloat()
        if (abs(delta) > 1f) {
            animateScrollBy(delta, FeedFollowScrollSpec)
        }
    } else {
        animateScrollToItem(index = index, scrollOffset = teleportOffset())
    }
    repeat(8) {
        yield()
        val top = visualTop()
        if (top == null) {
            scrollToItem(index = index, scrollOffset = teleportOffset())
            return@repeat
        }
        val remaining = scrollDeltaToAlignItemTop(top, inset).toFloat()
        if (abs(remaining) <= 1f) return
        scrollBy(remaining)
    }
}
