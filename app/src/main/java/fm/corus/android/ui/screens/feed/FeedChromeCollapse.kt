package fm.corus.android.ui.screens.feed

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/** Scroll-linked hide for the tabbed feed chrome (wordmark + tabs). */
class FeedChromeCollapse {
    var hiddenPx by mutableFloatStateOf(0f)
    var heightPx by mutableFloatStateOf(0f)
    /** Frozen in-feed pad while the overlay hides — same as iOS `pinFrozenSpacer`. */
    var frozenPadPx by mutableStateOf<Float?>(null)
        private set
    var isSettling = false
        private set
    private var lastIndex = 0
    private var lastOffset = 0
    var lastScrollY = 0f
        private set
    private var lastHideDelta = 0f
    private var isRestingAtTop = true
    private var anchored = false
    private var isDragging = false
    private var needsSettle = false
    private var scope: CoroutineScope? = null
    private var snapJob: Job? = null
    private var snapTarget = Float.NaN

    var isProgrammaticPin = false
        private set

    val currentVisibleChrome: Float
        get() = max(0f, heightPx - hiddenPx)

    val isSnappingToOpen: Boolean
        get() = snapJob?.isActive == true && snapTarget == 0f

    /**
     * Freeze the in-feed pad so overlay hide cannot shrink it mid-jump
     * (that tucked the username under the status bar on iOS).
     */
    fun startProgrammaticPin() {
        isProgrammaticPin = true
        if (frozenPadPx == null) {
            frozenPadPx = currentVisibleChrome
        }
    }

    fun finishProgrammaticPin() {
        isProgrammaticPin = false
    }

    /** Slide the overlay chrome fully away. Pad stays frozen. */
    fun hideCompletely(animated: Boolean = true) {
        lastHideDelta = 0f
        isRestingAtTop = false
        val target = heightPx
        if (target <= 0f || hiddenPx >= target - 0.5f) return
        if (frozenPadPx == null) frozenPadPx = currentVisibleChrome
        if (animated) {
            startSnap(target)
        } else {
            snapJob?.cancel()
            snapJob = null
            hiddenPx = target
            isSettling = false
        }
    }

    /**
     * In-feed pad under the overlaid chrome. Before the first measure,
     * [heightPx] is 0 so we use [unmeasuredEstimatePx]. After measure,
     * a fully hidden bar must report 0 — using the estimate there was
     * snapping a ~110dp black gap back in and jumping the list.
     * While hidden, the pad stays frozen so the list does not outrun the bar.
     */
    fun contentPadPx(unmeasuredEstimatePx: Float): Float {
        frozenPadPx?.let { return it }
        return FeedChromeCollapseMath.contentPadPx(heightPx, hiddenPx, unmeasuredEstimatePx)
    }

    fun attach(scope: CoroutineScope) {
        this.scope = scope
    }

    fun applyListScroll(index: Int, offset: Int) {
        val header = heightPx.coerceAtLeast(1f)
        val scrollTop = if (index == 0) offset.toFloat() else header + offset.toFloat()
        if (!anchored) {
            lastIndex = index
            lastOffset = offset
            lastScrollY = scrollTop
            anchored = true
            syncSpacerForTopOfFeed(scrollTop)
            return
        }
        lastIndex = index
        lastOffset = offset
        applyScroll(scrollTop)
    }

    fun applyScroll(offsetY: Float) {
        if (isProgrammaticPin || isSettling) {
            lastScrollY = offsetY
            return
        }
        val dy = offsetY - lastScrollY
        lastScrollY = offsetY
        if (FeedChromeCollapseMath.isOverscrolling(offsetY)) {
            isRestingAtTop = true
            lastHideDelta = 0f
            val next = FeedChromeCollapseMath.nextHiddenPx(hiddenPx, heightPx, offsetY, dy)
            if (frozenPadPx == null && hiddenPx > 0.5f && dy > 0f) {
                frozenPadPx = currentVisibleChrome
            }
            lastHideDelta = next - hiddenPx
            hiddenPx = next
            return
        }
        if (isRestingAtTop) {
            if (offsetY < FeedChromeCollapseMath.HIDE_ARM_THRESHOLD) {
                lastHideDelta = 0f
                return
            }
            isRestingAtTop = false
            lastHideDelta = 0f
            if (dy > 40f) return
        }
        if (frozenPadPx == null && FeedChromeCollapseMath.isLayoutEcho(dy, lastHideDelta)) {
            lastHideDelta = 0f
            return
        }
        val next = FeedChromeCollapseMath.nextHiddenPx(hiddenPx, heightPx, offsetY, dy)
        if (frozenPadPx == null && next > 0.5f) {
            frozenPadPx = currentVisibleChrome
        }
        if (next == 0f) isRestingAtTop = true
        lastHideDelta = next - hiddenPx
        hiddenPx = next
    }

    fun resetAnchor() {
        anchored = false
        isRestingAtTop = true
        lastHideDelta = 0f
    }

    fun beginDrag() {
        if (!isDragging) {
            snapJob?.cancel()
            isSettling = false
        }
        isDragging = true
        needsSettle = false
    }

    fun endDrag() {
        isDragging = false
        needsSettle = true
    }

    fun trySettleIfIdle(isScrollInProgress: Boolean, scrollTop: Float = lastScrollY) {
        if (!needsSettle || isDragging || isScrollInProgress || isSettling || isProgrammaticPin) return
        needsSettle = false
        settleAfterDrag(scrollTop)
    }

    /** Blinds latch after the feed is idle. Fully down stays down. */
    fun settleAfterDrag(scrollTop: Float = lastScrollY) {
        if (isProgrammaticPin) return
        lastScrollY = scrollTop
        val target = FeedChromeCollapseMath.settledHiddenPx(hiddenPx, heightPx, scrollTop)
        if (abs(target - hiddenPx) < 0.5f) {
            isRestingAtTop = target == 0f
            if (target == 0f) syncSpacerForTopOfFeed(scrollTop)
            return
        }
        if (frozenPadPx == null) frozenPadPx = currentVisibleChrome
        startSnap(target)
    }

    fun reveal() {
        lastHideDelta = 0f
        isRestingAtTop = true
        if (isSnappingToOpen) return
        if (hiddenPx < 0.5f) {
            isSettling = false
            return
        }
        startSnap(0f)
    }

    /**
     * Full in-feed pad so a tab at the top sits below the open slab.
     * An unloaded neighbor inherits the previous tab's collapsed pad.
     */
    fun expandSpacerToOpenChrome(unmeasuredEstimatePx: Float = 0f) {
        val open = if (heightPx > 0f) heightPx else unmeasuredEstimatePx
        if (open <= 0f) return
        if (frozenPadPx == open) return
        frozenPadPx = open
    }

    fun syncSpacerForTopOfFeed(scrollTop: Float, unmeasuredEstimatePx: Float = 0f) {
        val atTop = scrollTop <= FeedChromeCollapseMath.HIDE_ARM_THRESHOLD
        val openOrOpening = hiddenPx < 0.5f || isSettling
        if (atTop && openOrOpening) expandSpacerToOpenChrome(unmeasuredEstimatePx)
    }

    fun snapOpen() {
        if (isSettling) return
        lastHideDelta = 0f
        frozenPadPx = null
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
        if (dy == 0f || isSettling || isProgrammaticPin) return
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
        if (frozenPadPx == null && next > 0.5f && dy > 0f) {
            frozenPadPx = currentVisibleChrome
        }
        if (next == 0f) {
            isRestingAtTop = true
            lastScrollY = 0f
        }
        lastHideDelta = next - hiddenPx
        hiddenPx = next
    }

    private fun startSnap(target: Float) {
        if (snapJob?.isActive == true && snapTarget == target) return
        val s = scope
        if (s == null) {
            hiddenPx = target
            isSettling = false
            isRestingAtTop = target == 0f
            return
        }
        snapJob?.cancel()
        snapTarget = target
        isSettling = true
        isRestingAtTop = target == 0f
        val from = hiddenPx
        snapJob = s.launch {
            val durationNs = (FeedChromeCollapseMath.CHROME_SNAP_DURATION_MS * 1_000_000L)
            val start = withFrameNanos { it }
            while (true) {
                val now = withFrameNanos { it }
                val t = ((now - start).toFloat() / durationNs).coerceIn(0f, 1f)
                val eased = 1f - (1f - t).pow(3)
                hiddenPx = from + (target - from) * eased
                if (t >= 1f) break
            }
            isSettling = false
            snapJob = null
        }
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
    /** Tiny dead zone so hide is 1:1 with the finger. Matches iOS. */
    const val HIDE_ARM_THRESHOLD = 2f
    const val CHROME_LATCH_OPEN_FRACTION = 0.18f
    const val CHROME_CONTENT_FADE_RANGE = 0.65f
    const val CHROME_SNAP_DURATION_MS = 340

    fun isOverscrolling(scrollTop: Float): Boolean = scrollTop <= 0f

    fun nextHiddenPx(
        hiddenPx: Float,
        headerHeight: Float,
        scrollTop: Float,
        dy: Float,
    ): Float {
        if (headerHeight <= 0f) return 0f
        if (isOverscrolling(scrollTop)) {
            // Pulling down at the top reveals 1:1. Bounce-back must not hide.
            if (dy >= 0f) return hiddenPx
            return min(headerHeight, max(0f, hiddenPx + dy))
        }
        if (hiddenPx == 0f && scrollTop < HIDE_ARM_THRESHOLD) return 0f
        return min(headerHeight, max(0f, hiddenPx + dy))
    }

    /**
     * Resting hide after the feed is idle. Fully (or almost fully) down
     * stays down — even mid-feed. Halfway or more snaps up.
     */
    fun settledHiddenPx(
        hiddenPx: Float,
        headerHeight: Float,
        scrollTop: Float,
    ): Float {
        if (headerHeight <= 0f) return 0f
        if (isOverscrolling(scrollTop)) return 0f
        val latch = headerHeight * CHROME_LATCH_OPEN_FRACTION
        if (scrollTop <= latch) return 0f
        if (hiddenPx <= latch) return 0f
        return headerHeight
    }

    fun contentFade(hiddenPx: Float, measuredPx: Float): Float {
        if (measuredPx <= 0f) return 1f
        val range = measuredPx * CHROME_CONTENT_FADE_RANGE
        if (range <= 0f) return 1f
        return (1f - hiddenPx / range).coerceIn(0f, 1f)
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

    fun scrollTop(firstIndex: Int, firstOffset: Int, headerHeight: Float): Float =
        if (firstIndex == 0) firstOffset.toFloat() else headerHeight + firstOffset.toFloat()

    /**
     * Next / follow-song pin. Overlay chrome hides on the same jump, so
     * land under the status bar — not under the (still-open) tab slab.
     */
    fun programmaticPinInset(
        visibleChrome: Float,
        statusBarPx: Float,
        chromeWillHide: Boolean = false,
    ): Float {
        val bar = statusBarPx.coerceAtLeast(0f)
        if (chromeWillHide) return bar
        return max(visibleChrome, bar)
    }
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
    val fade = FeedChromeCollapseMath.contentFade(hidden, measured)
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
            Column(Modifier.fillMaxWidth().graphicsLayer { alpha = fade }) {
                if (topInset > 0.dp) Spacer(Modifier.height(topInset))
                content()
            }
        }
    }
}
