package fm.corus.android.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class FeedModeOrderTest {

    private val all = listOf("tasteMatches", "trending", "following", "favorites")

    @Test
    fun `default raw parses to the historical order`() {
        assertEquals(
            listOf("tasteMatches", "trending", "following", "favorites"),
            FeedModeOrder.parse(FeedModeOrder.DEFAULT_RAW),
        )
    }

    @Test
    fun `explicit reorder is honored`() {
        assertEquals(
            listOf("following", "trending", "tasteMatches", "favorites"),
            FeedModeOrder.parse("following,trending,tasteMatches,favorites"),
        )
    }

    @Test
    fun `null falls back to default order`() {
        assertEquals(all, FeedModeOrder.parse(null))
    }

    @Test
    fun `empty string falls back to default order`() {
        assertEquals(all, FeedModeOrder.parse(""))
    }

    @Test
    fun `missing modes are appended in default order so none can be hidden`() {
        // Only "following" listed → it leads, the rest follow in default order.
        assertEquals(
            listOf("following", "tasteMatches", "trending", "favorites"),
            FeedModeOrder.parse("following"),
        )
    }

    @Test
    fun `unknown tokens are dropped but real modes still all appear`() {
        assertEquals(
            listOf("following", "trending", "tasteMatches", "favorites"),
            FeedModeOrder.parse("following,foo,trending,bar"),
        )
    }

    @Test
    fun `duplicates collapse to first occurrence`() {
        assertEquals(
            listOf("following", "trending", "tasteMatches", "favorites"),
            FeedModeOrder.parse("following,following,trending,following"),
        )
    }

    @Test
    fun `matching ignores case, underscores, spaces, and surrounding whitespace`() {
        // "Taste Matches" (display form), "FOLLOWING" (caps), padded commas.
        assertEquals(
            listOf("tasteMatches", "following", "trending", "favorites"),
            FeedModeOrder.parse("  Taste Matches , FOLLOWING "),
        )
    }

    @Test
    fun `snake_case taste_matches resolves to canonical token`() {
        assertEquals(
            listOf("favorites", "tasteMatches", "trending", "following"),
            FeedModeOrder.parse("favorites, taste_matches"),
        )
    }

    @Test
    fun `result always contains exactly the four modes once each`() {
        val parsed = FeedModeOrder.parse("favorites, favorites, nonsense")
        assertEquals(all.toSet(), parsed.toSet())
        assertEquals(4, parsed.size)
        assertEquals("favorites", parsed.first())
    }
}
