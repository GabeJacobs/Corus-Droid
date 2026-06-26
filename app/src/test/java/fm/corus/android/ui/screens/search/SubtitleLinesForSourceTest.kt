package fm.corus.android.ui.screens.search

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Regression test for the see-all (SuggestedUsersListScreen) cards reserving the
 * right number of subtitle lines.
 *
 * The bug: the "Popular on Corus" grid called [TasteMatchCard] without
 * `subtitleLines`, so it fell back to the default of 2. "X followers" is always a
 * single line, so every card carried a blank second line above the Follow button —
 * inconsistent with the Popular rail, which passes `subtitleLines = 1`. Club
 * Members ("member since …") have the same single-line subtitle.
 *
 * [subtitleLinesForSource] mirrors [subtitleForRow]'s branch split: the fixed
 * single-line sources (popular, new, clubMembers) get 1 line; the name-listing
 * sources keep 2 so a row of cards stays the same height.
 *
 * Pure JUnit (no Robolectric) so it runs from the CLI — the Robolectric-based
 * subtitle-content checks live in SuggestedUsersListSubtitleTest.
 */
class SubtitleLinesForSourceTest {

    @Test
    fun `popular reserves a single subtitle line`() {
        assertEquals(1, subtitleLinesForSource("popular"))
    }

    @Test
    fun `new reserves a single subtitle line`() {
        assertEquals(1, subtitleLinesForSource("new"))
    }

    @Test
    fun `club members reserve a single subtitle line for member-since`() {
        assertEquals(1, subtitleLinesForSource("clubMembers"))
    }

    @Test
    fun `taste matches reserve two subtitle lines for wrapping names`() {
        assertEquals(2, subtitleLinesForSource("tasteMatches"))
    }

    @Test
    fun `unknown source falls back to two lines`() {
        assertEquals(2, subtitleLinesForSource("somethingElse"))
    }
}
