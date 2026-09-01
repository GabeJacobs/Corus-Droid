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
    fun `scrollToItem offset subtracts the chrome pad instead of stacking it`() {
        // Frozen overlay pad still on the list; pin under the status bar.
        assertEquals(70, scrollOffsetToAlignItemTop(beforeContentPadding = 150, topInsetPx = 80))
        // No pad — same as a negative inset (item sits below y=0).
        assertEquals(-80, scrollOffsetToAlignItemTop(beforeContentPadding = 0, topInsetPx = 80))
        assertEquals(0, scrollOffsetToAlignItemTop(beforeContentPadding = 150, topInsetPx = 150))
        assertEquals(150, scrollOffsetToAlignItemTop(beforeContentPadding = 150, topInsetPx = 0))
    }
}
