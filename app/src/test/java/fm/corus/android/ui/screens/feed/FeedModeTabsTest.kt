package fm.corus.android.ui.screens.feed

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
    fun `swipe haptic fires at midpoint`() {
        assertTrue(FeedChromeCollapseMath.crossedMidpoint(0.49f, 0.51f))
        assertTrue(FeedChromeCollapseMath.crossedMidpoint(1.51f, 1.49f))
        assertFalse(FeedChromeCollapseMath.crossedMidpoint(0.2f, 0.4f))
        assertEquals(1, FeedChromeCollapseMath.committedPage(0.5f))
        assertEquals(0, FeedChromeCollapseMath.committedPage(0.49f))
    }
}
