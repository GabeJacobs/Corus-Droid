package fm.corus.android.ui.util

import org.junit.Assert.assertEquals
import org.junit.Test

class LazyListScrollTest {

    @Test
    fun `feed post index skips the header`() {
        assertEquals(1, feedPostLazyIndex(postIndex = 0, prefixItemCount = 1))
        assertEquals(6, feedPostLazyIndex(postIndex = 5, prefixItemCount = 1))
    }

    @Test
    fun `feed post index skips header and trial banner`() {
        assertEquals(2, feedPostLazyIndex(postIndex = 0, prefixItemCount = 2))
        assertEquals(7, feedPostLazyIndex(postIndex = 5, prefixItemCount = 2))
    }

    @Test
    fun `delta leaves room for the status-bar inset so the username stays visible`() {
        assertEquals(1720, scrollDeltaToAlignItemTop(itemOffset = 1800, topInsetPx = 80))
        assertEquals(0, scrollDeltaToAlignItemTop(itemOffset = 80, topInsetPx = 80))
        // Landed at y=0 (under the frost) — scroll back to reveal the author row.
        assertEquals(-80, scrollDeltaToAlignItemTop(itemOffset = 0, topInsetPx = 80))
    }

    @Test
    fun `follow inset lifts 60pt and will not go above the viewport`() {
        assertEquals(1, computeFollowScrollTopInset(statusBarPx = 48, peekPx = 13, liftPx = 60))
        assertEquals(61, computeFollowScrollTopInset(statusBarPx = 48, peekPx = 13, liftPx = 0))
        assertEquals(11, computeFollowScrollTopInset(statusBarPx = 48, peekPx = 13, liftPx = 50))
        // Short device + large lift stops at the top of the list, not above it.
        assertEquals(0, computeFollowScrollTopInset(statusBarPx = 20, peekPx = 10, liftPx = 60))
    }
}
