package fm.corus.android.ui.screens.feed

import androidx.compose.ui.geometry.Rect
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tab-row mode list is a fixed order with per-mode gates — not `feed_mode_order`.
 * Mirrors iOS `tabBarModes`.
 */
class FeedModeTabsTest {

    @Test
    fun `following always appears`() {
        assertEquals(
            listOf("following"),
            visibleFeedModeTabs(
                trendingEnabled = false,
                tasteMatchesAvailable = false,
                favoritesEnabled = false,
                favoritesCount = 0,
            ),
        )
    }

    @Test
    fun `order is following trending matches favorites`() {
        assertEquals(
            listOf("following", "trending", "tasteMatches", "favorites"),
            visibleFeedModeTabs(
                trendingEnabled = true,
                tasteMatchesAvailable = true,
                favoritesEnabled = true,
                favoritesCount = 2,
            ),
        )
    }

    @Test
    fun `trending omitted when flag off`() {
        assertEquals(
            listOf("following", "tasteMatches"),
            visibleFeedModeTabs(
                trendingEnabled = false,
                tasteMatchesAvailable = true,
                favoritesEnabled = false,
                favoritesCount = 0,
            ),
        )
    }

    @Test
    fun `favorites omitted when count is zero`() {
        assertEquals(
            listOf("following", "trending"),
            visibleFeedModeTabs(
                trendingEnabled = true,
                tasteMatchesAvailable = false,
                favoritesEnabled = true,
                favoritesCount = 0,
            ),
        )
    }

    @Test
    fun `favorites omitted when feature flag off even with count`() {
        assertEquals(
            listOf("following"),
            visibleFeedModeTabs(
                trendingEnabled = false,
                tasteMatchesAvailable = false,
                favoritesEnabled = false,
                favoritesCount = 4,
            ),
        )
    }

    @Test
    fun `favorites omitted when unlocked is stale and count is zero`() {
        assertEquals(
            listOf("following", "trending"),
            visibleFeedModeTabs(
                trendingEnabled = true,
                tasteMatchesAvailable = false,
                favoritesEnabled = true,
                favoritesCount = 0,
                favoritesUnlocked = false,
            ),
        )
    }

    @Test
    fun `favorites stays once unlocked`() {
        assertEquals(
            listOf("following", "favorites"),
            visibleFeedModeTabs(
                trendingEnabled = false,
                tasteMatchesAvailable = false,
                favoritesEnabled = true,
                favoritesCount = 0,
                favoritesUnlocked = true,
            ),
        )
    }

    @Test
    fun `underline follows each label instead of the longest`() {
        val frames = mapOf(
            0 to Rect(0f, 0f, 40f, 2f),
            1 to Rect(80f, 0f, 125f, 2f),
            2 to Rect(165f, 0f, 195f, 2f),
            3 to Rect(235f, 0f, 310f, 2f),
        )
        val extra = underlineOvershootPx(frames, maxExtraPx = 24f)
        val users = underlinePlacement(4, 0f, frames, extraPx = extra)!!
        val hashtags = underlinePlacement(4, 3f, frames, extraPx = extra)!!
        assertEquals(40f + extra * 2f, users.width, 0.01f)
        assertEquals(75f + extra * 2f, hashtags.width, 0.01f)
        assertTrue(users.width < hashtags.width)
    }

    @Test
    fun `underline shrinks when a fourth tab tightens the gaps`() {
        val three = mapOf(
            0 to Rect(0f, 0f, 60f, 2f),
            1 to Rect(140f, 0f, 200f, 2f),
            2 to Rect(280f, 0f, 340f, 2f),
        )
        val four = mapOf(
            0 to Rect(0f, 0f, 60f, 2f),
            1 to Rect(88f, 0f, 148f, 2f),
            2 to Rect(176f, 0f, 236f, 2f),
            3 to Rect(264f, 0f, 324f, 2f),
        )
        val threeExtra = underlineOvershootPx(three, 24f)
        val fourExtra = underlineOvershootPx(four, 24f)
        val threeWidth = underlinePlacement(3, 0f, three, extraPx = threeExtra)!!.width
        val fourWidth = underlinePlacement(4, 0f, four, extraPx = fourExtra)!!.width
        assertEquals(24f, threeExtra, 0.01f)
        assertEquals(14f, fourExtra, 0.01f)
        assertEquals(108f, threeWidth, 0.01f)
        assertEquals(88f, fourWidth, 0.01f)
    }

    @Test
    fun `content pad is zero when chrome is fully hidden`() {
        assertEquals(110f, FeedChromeCollapseMath.contentPadPx(0f, 0f, 110f))
        assertEquals(80f, FeedChromeCollapseMath.contentPadPx(80f, 0f, 110f))
        assertEquals(0f, FeedChromeCollapseMath.contentPadPx(80f, 80f, 110f))
        assertEquals(20f, FeedChromeCollapseMath.contentPadPx(80f, 60f, 110f))
    }

    @Test
    fun `chrome hides one to one with scroll`() {
        assertEquals(12f, FeedChromeCollapse.nextHiddenPx(0f, 80f, 50f, 12f))
        assertEquals(0f, FeedChromeCollapse.nextHiddenPx(12f, 80f, 40f, -12f))
        assertEquals(80f, FeedChromeCollapse.nextHiddenPx(70f, 80f, 200f, 40f))
        assertEquals(0f, FeedChromeCollapse.nextHiddenPx(80f, 80f, 0f, 10f))
        assertEquals(0f, FeedChromeCollapse.nextHiddenPx(40f, 80f, -20f, 80f))
        assertEquals(0f, FeedChromeCollapse.nextHiddenPx(0f, 80f, 12f, 12f))
        assertEquals(14f, FeedChromeCollapse.nextHiddenPx(8f, 80f, 12f, 6f))
    }

    @Test
    fun `swipe travel peaks at mid page`() {
        assertEquals(0f, FeedChromeCollapse.swipeTravel(0f))
        assertEquals(1f, FeedChromeCollapse.swipeTravel(0.5f))
        assertEquals(0.5f, FeedChromeCollapse.swipeTravel(0.25f))
    }

    @Test
    fun `finger delta hides and reveals without list offsets`() {
        val collapse = FeedChromeCollapse()
        collapse.heightPx = 80f
        collapse.applyFingerDelta(20f)
        assertEquals(20f, collapse.hiddenPx, 0.01f)
        collapse.applyFingerDelta(-20f)
        assertEquals(0f, collapse.hiddenPx, 0.01f)
    }

    @Test
    fun `pull down at top reveals a fully hidden bar`() {
        val collapse = FeedChromeCollapse()
        collapse.heightPx = 80f
        collapse.hiddenPx = 80f
        collapse.applyFingerDelta(-30f)
        assertEquals(50f, collapse.hiddenPx, 0.01f)
        collapse.applyFingerDelta(-50f)
        assertEquals(0f, collapse.hiddenPx, 0.01f)
    }

    @Test
    fun `swipe haptic fires at midpoint`() {
        assertTrue(FeedChromeCollapseMath.crossedMidpoint(0.49f, 0.51f))
        assertTrue(FeedChromeCollapseMath.crossedMidpoint(1.51f, 1.49f))
        assertFalse(FeedChromeCollapseMath.crossedMidpoint(0.2f, 0.4f))
        assertEquals(1, FeedChromeCollapseMath.committedPage(0.5f))
        assertEquals(0, FeedChromeCollapseMath.committedPage(0.49f))
    }

    @Test
    fun `midpoint haptic only during a user swipe`() {
        val tracker = PagerMidpointHapticTracker()
        assertFalse(tracker.onPage(0.6f, scrolling = true))
        assertTrue(tracker.onDragStart(settledPage = 0, currentPage = 0.51f))
        assertFalse(tracker.onPage(0.8f, scrolling = true))
        assertTrue(tracker.onPage(0.49f, scrolling = true))
        tracker.onPage(1f, scrolling = false)
        assertFalse(tracker.userSwipe)
        assertFalse(tracker.onPage(1.6f, scrolling = true))
    }
}
