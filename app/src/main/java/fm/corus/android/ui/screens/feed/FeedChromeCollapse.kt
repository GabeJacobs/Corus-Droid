package fm.corus.android.ui.screens.feed

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import fm.corus.android.ui.theme.CorusColors
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

/** Scroll-linked hide for the tabbed feed chrome (wordmark + tabs). */
class FeedChromeCollapse {
    var hiddenPx by mutableFloatStateOf(0f)
    var heightPx by mutableFloatStateOf(0f)
    private var lastIndex = 0
    private var lastOffset = 0
    private var lastScrollY = 0f
    private var lastHideDelta = 0f
    private var isRestingAtTop = true
    private var anchored = false

    val currentVisibleChrome: Float
        get() = max(0f, heightPx - hiddenPx)

    /**
     * In-feed pad under the overlaid chrome. Before the first measure,
     * [heightPx] is 0 so we use [unmeasuredEstimatePx]. After measure,
     * a fully hidden bar must report 0 — using the estimate there was
     * snapping a ~110dp black gap back in and jumping the list.
     */
    fun contentPadPx(unmeasuredEstimatePx: Float): Float =
        FeedChromeCollapseMath.contentPadPx(heightPx, hiddenPx, unmeasuredEstimatePx)

    fun applyListScroll(index: Int, offset: Int) {
        val header = heightPx.coerceAtLeast(1f)
        val scrollTop = if (index == 0) offset.toFloat() else header + offset.toFloat()
        if (!anchored) {
            lastIndex = index
            lastOffset = offset
            lastScrollY = scrollTop
            anchored = true
            if (index == 0 && offset <= 0) snapOpen()
            return
        }
        lastIndex = index
        lastOffset = offset
        applyScroll(scrollTop)
    }

    fun applyScroll(offsetY: Float) {
        val dy = offsetY - lastScrollY
        lastScrollY = offsetY
        if (FeedChromeCollapseMath.isOverscrolling(offsetY)) {
            isRestingAtTop = true
            lastHideDelta = 0f
            snapOpen()
            return
        }
        if (isRestingAtTop) {
            if (offsetY < FeedChromeCollapseMath.HIDE_ARM_THRESHOLD) {
                lastHideDelta = 0f
                return
            }
            isRestingAtTop = false
            lastHideDelta = 0f
            return
        }
        if (FeedChromeCollapseMath.isLayoutEcho(dy, lastHideDelta)) {
            lastHideDelta = 0f
            return
        }
        val next = FeedChromeCollapseMath.nextHiddenPx(hiddenPx, heightPx, offsetY, dy)
        if (next == 0f) isRestingAtTop = true
        lastHideDelta = next - hiddenPx
        hiddenPx = next
    }

    fun resetAnchor() {
        anchored = false
        isRestingAtTop = true
        lastHideDelta = 0f
    }

    fun reveal() {
        lastHideDelta = 0f
        isRestingAtTop = true
        hiddenPx = 0f
    }

    fun snapOpen() {
        lastHideDelta = 0f
        if (hiddenPx == 0f) return
        hiddenPx = 0f
    }

    /**
     * Intrinsic chrome height. [onSizeChanged] reports the *placed* size,
     * which shrinks with the clip box — writing that back made the bar
     * desync from the sliding contents. Only accept a new height when
     * fully open, or when it grew (font / tab-count change).
     */
    fun recordMeasuredHeight(h: Float) {
        if (h <= 0f) return
        if (hiddenPx < 1f || h > heightPx) heightPx = h
    }

    /**
     * Finger-driven hide/reveal. [dy] > 0 hides (feed scrolled down);
     * [dy] < 0 reveals. Independent of LazyList item offsets so a pull
     * at the top can bring the bar back after it has fully hidden.
     */
    fun applyFingerDelta(dy: Float) {
        if (dy == 0f) return
        if (isRestingAtTop && hiddenPx == 0f) {
            if (dy > 0f) {
                lastScrollY += dy
                if (lastScrollY < FeedChromeCollapseMath.HIDE_ARM_THRESHOLD) {
                    lastHideDelta = 0f
                    return
                }
                isRestingAtTop = false
                lastHideDelta = 0f
            } else {
                lastScrollY = max(0f, lastScrollY + dy)
                lastHideDelta = 0f
                return
            }
        }
        val header = heightPx.coerceAtLeast(0f)
        val next = min(header, max(0f, hiddenPx + dy))
        if (next == 0f) {
            isRestingAtTop = true
            lastScrollY = 0f
        }
        lastHideDelta = next - hiddenPx
        hiddenPx = next
    }

    companion object {
        fun nextHiddenPx(
            hiddenPx: Float,
            headerHeight: Float,
            scrollTop: Float,
            dy: Float,
        ): Float = FeedChromeCollapseMath.nextHiddenPx(hiddenPx, headerHeight, scrollTop, dy)

        fun swipeTravel(pageOffsetFraction: Float): Float =
            FeedChromeCollapseMath.swipeTravel(pageOffsetFraction)
    }
}

object FeedChromeCollapseMath {
    const val HIDE_ARM_THRESHOLD = 16f

    fun isOverscrolling(scrollTop: Float): Boolean = scrollTop <= 0f

    fun nextHiddenPx(
        hiddenPx: Float,
        headerHeight: Float,
        scrollTop: Float,
        dy: Float,
    ): Float {
        if (headerHeight <= 0f) return 0f
        if (isOverscrolling(scrollTop)) return 0f
        if (hiddenPx == 0f && scrollTop < HIDE_ARM_THRESHOLD) return 0f
        return min(headerHeight, max(0f, hiddenPx + dy))
    }

    fun swipeTravel(pageOffsetFraction: Float): Float =
        min(1f, abs(pageOffsetFraction) * 2f)

    fun isLayoutEcho(dy: Float, lastHideDelta: Float): Boolean =
        lastHideDelta != 0f && abs(dy + lastHideDelta) < 2.5f

    fun committedPage(page: Float): Int = floor(page + 0.5f).toInt()

    fun crossedMidpoint(from: Float, to: Float): Boolean =
        committedPage(from) != committedPage(to)

    fun tabActivation(page: Float, index: Int): Float =
        min(1f, max(0f, 1f - abs(page - index)))

    fun contentPadPx(heightPx: Float, hiddenPx: Float, unmeasuredEstimatePx: Float): Float =
        if (heightPx <= 0f) unmeasuredEstimatePx else max(0f, heightPx - hiddenPx)
}

@Composable
fun FeedCollapsingChrome(
    collapse: FeedChromeCollapse,
    modifier: Modifier = Modifier,
    topInset: Dp = 0.dp,
    content: @Composable () -> Unit,
) {
    val density = LocalDensity.current
    val hidden = collapse.hiddenPx
    val measured = collapse.heightPx
    val fade = if (measured > 0f) (1f - hidden / measured).coerceIn(0f, 1f) else 1f
    val visiblePx = if (measured <= 0f) 0f else (measured - hidden).coerceAtLeast(0f)
    Box(
        modifier
            .fillMaxWidth()
            .then(
                if (measured > 0f) {
                    Modifier.height(with(density) { visiblePx.toDp() })
                } else {
                    Modifier
                },
            )
            .clipToBounds(),
    ) {
        // Same structure as iOS `FeedCollapsingChrome`: background lives on
        // the sliding slab so the bar and its contents move as one piece.
        // A background on this clip box lagged behind the offset tabs and
        // left a black gap above the feed.
        Column(
            Modifier
                .fillMaxWidth()
                .wrapContentHeight(unbounded = true, align = Alignment.Top)
                .onSizeChanged { size -> collapse.recordMeasuredHeight(size.height.toFloat()) }
                .offset { IntOffset(0, -hidden.toInt()) }
                .background(CorusColors.Background),
        ) {
            // iOS: `.opacity(fade).background(cymbalBackground)` — opacity
            // hits the labels, the fill stays opaque so the feed never
            // shows through the sliding bar.
            Column(Modifier.graphicsLayer { alpha = fade }) {
                if (topInset > 0.dp) Spacer(Modifier.height(topInset))
                content()
                HorizontalDivider(thickness = 1.dp, color = CorusColors.Divider)
            }
        }
    }
}
