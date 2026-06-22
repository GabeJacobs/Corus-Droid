package fm.corus.android.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Regression coverage for the Taste Match teaser pill label.
 *
 * The bug: names and the "+N" overflow count were one string fed to a single
 * ellipsizing Text, so a long name chopped the "+N" off too (".. Mac DeMarco
 * +..." instead of "+5"). The fix ([chooseTeaserLabel]) shows as many whole
 * names as fit, drops from two names to one (folding the hidden name into the
 * count) before truncating, and always keeps the count.
 *
 * Width is injected as a char-budget measure ({ it.length }) so the layout
 * decision is exercised without Compose/Robolectric.
 */
class TasteMatchTeaserLabelTest {

    private val byCharCount: (String) -> Int = { it.length }

    @Test
    fun `keeps both names when they fit`() {
        val (names, extra) = chooseTeaserLabel(
            names = listOf("Connan Mockasin", "Mac DeMarco"),
            totalSheetItems = 7,
            maxWidthPx = 1000,
            measure = byCharCount,
        )
        assertEquals("Connan Mockasin, Mac DeMarco", names)
        assertEquals("+5", extra)
    }

    @Test
    fun `drops to one name and bumps the count when two do not fit`() {
        // "Connan Mockasin, Mac DeMarco +5" = 31 chars (too wide);
        // "Connan Mockasin +6" = 18 chars (fits). The dropped name rolls into
        // the count, so +5 becomes +6.
        val (names, extra) = chooseTeaserLabel(
            names = listOf("Connan Mockasin", "Mac DeMarco"),
            totalSheetItems = 7,
            maxWidthPx = 20,
            measure = byCharCount,
        )
        assertEquals("Connan Mockasin", names)
        assertEquals("+6", extra)
    }

    @Test
    fun `last resort keeps the single name and count even when it must truncate`() {
        // Nothing fits the tiny budget, but we never fall below one name: the UI
        // Text ellipsizes the name while the pinned "+N" survives.
        val (names, extra) = chooseTeaserLabel(
            names = listOf("Godspeed You! Black Emperor", "Swans"),
            totalSheetItems = 8,
            maxWidthPx = 3,
            measure = byCharCount,
        )
        assertEquals("Godspeed You! Black Emperor", names)
        assertEquals("+7", extra)
    }

    @Test
    fun `no extra suffix when nothing overflows`() {
        val (names, extra) = chooseTeaserLabel(
            names = listOf("A", "B"),
            totalSheetItems = 2,
            maxWidthPx = 1000,
            measure = byCharCount,
        )
        assertEquals("A, B", names)
        assertNull(extra)
    }

    @Test
    fun `single available name still produces a count`() {
        val (names, extra) = chooseTeaserLabel(
            names = listOf("Solo Artist"),
            totalSheetItems = 3,
            maxWidthPx = 1000,
            measure = byCharCount,
        )
        assertEquals("Solo Artist", names)
        assertEquals("+2", extra)
    }

    @Test
    fun `never shows more than two names even if given more`() {
        val (names, extra) = chooseTeaserLabel(
            names = listOf("A", "B", "C"),
            totalSheetItems = 10,
            maxWidthPx = 1000,
            measure = byCharCount,
        )
        assertEquals("A, B", names)
        assertEquals("+8", extra)
    }
}
