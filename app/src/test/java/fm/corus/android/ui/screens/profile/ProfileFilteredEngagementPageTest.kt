package fm.corus.android.ui.screens.profile

import fm.corus.android.data.model.CymbalPost
import fm.corus.android.data.model.CymbalTrack
import fm.corus.android.data.model.CymbalUser
import fm.corus.android.data.model.MediaType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Date

/**
 * Unit tests for [computeFilteredEngagementPage] — the pure offset/match math
 * behind the media-filtered Likes/Saves pagination. The key guarantee is that
 * paging by the returned [FilteredEngagementPage.nextOffset] surfaces every
 * matching post exactly once: no skips, no duplicates.
 */
class ProfileFilteredEngagementPageTest {

    private val user = CymbalUser(id = "u1", username = "alice", displayName = "Alice")

    private fun post(id: String, movie: Boolean) = CymbalPost(
        id = id,
        user = user,
        track = CymbalTrack(id = "t-$id", name = "n", artistName = "a", albumName = "al"),
        mediaType = if (movie) MediaType.MOVIE else MediaType.TRACK,
        timestamp = Date(0),
    )

    /** [T M M T M M] filtered to movies, page size 2. */
    private fun mixedRows() = listOf(
        post("0", movie = false),
        post("1", movie = true),
        post("2", movie = true),
        post("3", movie = false),
        post("4", movie = true),
        post("5", movie = true),
    )

    @Test
    fun `overflow page resumes after the last returned match`() {
        val page = computeFilteredEngagementPage(
            rawRows = mixedRows(),
            startOffset = 0,
            mediaType = "movie",
            pageSize = 2,
            moreAfterScan = false,
        )
        assertEquals(listOf("1", "2"), page.posts.map { it.id })
        // 4 matches > pageSize 2 → resume right after raw index of "2" (=2) → 3.
        assertEquals(3, page.nextOffset)
        assertTrue(page.hasMore)
    }

    @Test
    fun `paging by nextOffset surfaces every match once, no skips or dupes`() {
        val rows = mixedRows()
        val collected = mutableListOf<String>()
        var offset = 0
        // Page 1.
        val p1 = computeFilteredEngagementPage(rows, offset, "movie", 2, moreAfterScan = false)
        collected += p1.posts.map { it.id }
        offset = p1.nextOffset
        // Page 2 scans the remainder (raw rows from `offset`).
        val p2 = computeFilteredEngagementPage(rows.drop(offset), offset, "movie", 2, moreAfterScan = false)
        collected += p2.posts.map { it.id }
        assertEquals(listOf("1", "2", "4", "5"), collected)
        assertEquals(collected.distinct(), collected) // no duplicates
        assertFalse(p2.hasMore)
    }

    @Test
    fun `exact page with source still open reports more`() {
        val page = computeFilteredEngagementPage(
            rawRows = listOf(post("a", true), post("b", true)),
            startOffset = 0,
            mediaType = "movie",
            pageSize = 2,
            moreAfterScan = true,
        )
        assertEquals(listOf("a", "b"), page.posts.map { it.id })
        assertEquals(2, page.nextOffset) // no overflow → past everything scanned
        assertTrue(page.hasMore)
    }

    @Test
    fun `no matches in an exhausted scan reports done`() {
        val page = computeFilteredEngagementPage(
            rawRows = listOf(post("a", false), post("b", false), post("c", false)),
            startOffset = 7,
            mediaType = "movie",
            pageSize = 2,
            moreAfterScan = false,
        )
        assertTrue(page.posts.isEmpty())
        assertEquals(10, page.nextOffset) // 7 + 3 scanned
        assertFalse(page.hasMore)
    }

    @Test
    fun `partial page keeps paging when the source is not exhausted`() {
        val page = computeFilteredEngagementPage(
            rawRows = listOf(post("a", false), post("b", true), post("c", false)),
            startOffset = 0,
            mediaType = "movie",
            pageSize = 2,
            moreAfterScan = true,
        )
        assertEquals(listOf("b"), page.posts.map { it.id })
        assertEquals(3, page.nextOffset)
        assertTrue(page.hasMore) // fewer than a page, but more rows remain
    }
}
