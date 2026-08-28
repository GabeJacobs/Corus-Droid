package fm.corus.android.ui.screens.feed

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
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
}

@Composable
fun FeedCollapsingChrome(
    collapse: FeedChromeCollapse,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val density = LocalDensity.current
    val hidden = collapse.hiddenPx
    val measured = collapse.heightPx
    val fade = if (measured > 0f) 1f - (hidden / measured) else 1f
    Box(
        modifier
            .fillMaxWidth()
            .background(CorusColors.Background)
            .then(
                if (measured > 0f) {
                    Modifier.height(with(density) { (measured - hidden).coerceAtLeast(0f).toDp() })
                } else {
                    Modifier
                },
            )
            .clipToBounds(),
    ) {
        Column(
            Modifier
                .onSizeChanged { size ->
                    if (size.height > 0) collapse.heightPx = size.height.toFloat()
                }
                .offset { IntOffset(0, -hidden.toInt()) },
        ) {
            Box(Modifier.graphicsLayer { alpha = fade }) {
                content()
            }
        }
    }
}
