package fm.corus.android.ui.screens.destination

import fm.corus.android.data.model.CymbalTrack
import fm.corus.android.data.model.TrackCorusStats
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for the catalog row's metadata split (matching web + iOS):
 *
 *  - [catalogRowSubtitle] keeps the FULL "{album} · {year}" on art rows (the
 *    share count no longer lives on the subtitle line), and the artist name on
 *    album-tracklist numbered rows.
 *  - [catalogRowSharedCount] surfaces the Corus share count for the trailing slot
 *    on any shared row (artist Popular art rows AND album tracklist numbered
 *    rows), and null when the track has no shares.
 *  - [catalogRowShowsDuration] decides the fallback: art rows show the duration
 *    when unshared; numbered (album) rows stay blank instead.
 */
class CatalogTrackRowSharedCountTest {

    private fun track(
        albumName: String = "Rubber Soul",
        releaseDate: String? = "2024-05-01",
    ) = CymbalTrack(
        id = "t1",
        name = "In My Life",
        artistName = "The Beatles",
        albumName = albumName,
        releaseDate = releaseDate,
        durationMs = 198_000,
    )

    private fun stats(count: Int) = TrackCorusStats(count = count, posters = emptyList())

    @Test
    fun `art row subtitle is full album and year`() {
        assertEquals("Rubber Soul · 2024", catalogRowSubtitle(track(), number = null))
    }

    @Test
    fun `art row subtitle falls back to whichever of album or year exists`() {
        assertEquals("Rubber Soul", catalogRowSubtitle(track(releaseDate = null), number = null))
        assertEquals("2024", catalogRowSubtitle(track(albumName = ""), number = null))
    }

    @Test
    fun `numbered row subtitle is the artist name`() {
        assertEquals("The Beatles", catalogRowSubtitle(track(), number = 0))
    }

    @Test
    fun `a shared row surfaces the count for the trailing slot`() {
        // Both artist Popular (art) and album tracklist (numbered) rows now show
        // the count when the track has shares.
        assertEquals(13, catalogRowSharedCount(stats(13)))
    }

    @Test
    fun `an unshared track has no count`() {
        assertNull(catalogRowSharedCount(null))
        assertNull(catalogRowSharedCount(stats(0)))
    }

    @Test
    fun `art rows fall back to the duration when unshared`() {
        assertTrue(catalogRowShowsDuration(number = null, durationMs = 198_000))
        assertFalse(catalogRowShowsDuration(number = null, durationMs = 0))
    }

    @Test
    fun `album numbered rows never show the duration (blank when unshared)`() {
        assertFalse(catalogRowShowsDuration(number = 2, durationMs = 198_000))
    }
}
