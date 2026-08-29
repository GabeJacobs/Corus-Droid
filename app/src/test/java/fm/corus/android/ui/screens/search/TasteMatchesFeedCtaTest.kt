package fm.corus.android.ui.screens.search

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Gate for the Taste Matches see-all → feed CTA. Mirrors iOS
 * TasteMatchesListView.showTasteMatchesFeedCTA: segmented Search, Taste
 * Matches feed available, first page loaded, and more than 3 matches.
 */
class TasteMatchesFeedCtaTest {

    @Test
    fun `shows when every iOS gate is met`() {
        assertTrue(
            shouldShowTasteMatchesFeedCta(
                source = "tasteMatches",
                segmentedSearchEnabled = true,
                tasteMatchesAvailable = true,
                didLoadFirstPage = true,
                matchCount = 4,
            ),
        )
    }

    @Test
    fun `hides on other see-all sources`() {
        assertFalse(
            shouldShowTasteMatchesFeedCta(
                source = "popular",
                segmentedSearchEnabled = true,
                tasteMatchesAvailable = true,
                didLoadFirstPage = true,
                matchCount = 4,
            ),
        )
    }

    @Test
    fun `hides before the first page loads`() {
        assertFalse(
            shouldShowTasteMatchesFeedCta(
                source = "tasteMatches",
                segmentedSearchEnabled = true,
                tasteMatchesAvailable = true,
                didLoadFirstPage = false,
                matchCount = 4,
            ),
        )
    }

    @Test
    fun `hides when the list is too small to promote the feed`() {
        assertFalse(
            shouldShowTasteMatchesFeedCta(
                source = "tasteMatches",
                segmentedSearchEnabled = true,
                tasteMatchesAvailable = true,
                didLoadFirstPage = true,
                matchCount = 3,
            ),
        )
    }

    @Test
    fun `hides when segmented search is off`() {
        assertFalse(
            shouldShowTasteMatchesFeedCta(
                source = "tasteMatches",
                segmentedSearchEnabled = false,
                tasteMatchesAvailable = true,
                didLoadFirstPage = true,
                matchCount = 4,
            ),
        )
    }

    @Test
    fun `hides when the Taste Matches feed is unavailable`() {
        assertFalse(
            shouldShowTasteMatchesFeedCta(
                source = "tasteMatches",
                segmentedSearchEnabled = true,
                tasteMatchesAvailable = false,
                didLoadFirstPage = true,
                matchCount = 4,
            ),
        )
    }
}
