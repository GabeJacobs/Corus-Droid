package fm.corus.android.data.repository

import fm.corus.android.data.model.CymbalHashtag
import org.junit.Assert.assertEquals
import org.junit.Test

class HashtagSuggestionMergeTest {

    private fun tag(name: String, cymbal: Int, recent: Int = 0) =
        CymbalHashtag(id = name, name = name, cymbalCount = cymbal, recentCount = recent)

    @Test
    fun `trending matches come first, flagged, before prefix matches, deduped`() {
        val out = mergeHashtagSuggestions(
            query = "jaz",
            trending = listOf(tag("jazztuesday", cymbal = 69)),
            prefix = listOf(
                tag("jazz", cymbal = 400, recent = 4),
                tag("jazztuesday", cymbal = 999, recent = 60), // dup of trending row
            ),
            limit = 6,
        )
        assertEquals(listOf("jazztuesday", "jazz"), out.map { it.name })
        assertEquals(true, out[0].trending)
        assertEquals(69, out[0].count) // weekly windowed count from trending
        assertEquals(false, out[1].trending)
        assertEquals(4, out[1].count) // recentCount, not the all-time 400
    }

    @Test
    fun `non-trending matches rank by recentCount, not all-time count`() {
        val out = mergeHashtagSuggestions(
            query = "h",
            trending = emptyList(),
            prefix = listOf(
                tag("hugeold", cymbal = 5000, recent = 0),
                tag("hotnow", cymbal = 30, recent = 25),
            ),
            limit = 6,
        )
        assertEquals(listOf("hotnow", "hugeold"), out.map { it.name })
        assertEquals(listOf(25, 0), out.map { it.count })
    }

    @Test
    fun `one-off likely-typo tags are hidden`() {
        val out = mergeHashtagSuggestions(
            query = "jazz",
            trending = emptyList(),
            prefix = listOf(tag("jazz", cymbal = 12, recent = 3), tag("jazzz", cymbal = 1, recent = 0)),
            limit = 6,
        )
        assertEquals(listOf("jazz"), out.map { it.name })
    }

    @Test
    fun `an exact-name match survives the floor even as a one-off`() {
        val out = mergeHashtagSuggestions(
            query = "tufts",
            trending = emptyList(),
            prefix = listOf(tag("tufts", cymbal = 1, recent = 0)),
            limit = 6,
        )
        assertEquals(listOf("tufts"), out.map { it.name })
    }

    @Test
    fun `merged list is capped at limit`() {
        val out = mergeHashtagSuggestions(
            query = "a",
            trending = listOf(tag("aa", cymbal = 5), tag("ab", cymbal = 4)),
            prefix = listOf(
                tag("ac", cymbal = 10, recent = 10),
                tag("ad", cymbal = 10, recent = 9),
                tag("ae", cymbal = 10, recent = 8),
            ),
            limit = 3,
        )
        assertEquals(3, out.size)
        assertEquals(listOf("aa", "ab", "ac"), out.map { it.name })
    }
}
