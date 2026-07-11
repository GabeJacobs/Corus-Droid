package fm.corus.android.ui.screens.destination

import fm.corus.android.data.model.CymbalTrack
import fm.corus.android.data.model.TrackCorusStats
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Unit tests for the artist "Popular" row's metadata split (matching web + iOS):
 *
 *  - [catalogRowSubtitle] keeps the FULL "{album} · {year}" on art rows (the
 *    share count no longer lives on the subtitle line), and the artist name on
 *    album-tracklist numbered rows.
 *  - [catalogRowSharedCount] surfaces the Corus share count for the trailing
 *    slot ONLY on shared art rows — it returns null for unshared tracks and for
 *    numbered rows, which is how the row decides to keep showing the duration.
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
    fun `shared art row surfaces the count for the trailing slot`() {
        assertEquals(13, catalogRowSharedCount(number = null, corusStats = stats(13)))
    }

    @Test
    fun `unshared art row keeps the duration (null count)`() {
        assertNull(catalogRowSharedCount(number = null, corusStats = null))
        assertNull(catalogRowSharedCount(number = null, corusStats = stats(0)))
    }

    @Test
    fun `numbered row never shows the shared count`() {
        assertNull(catalogRowSharedCount(number = 2, corusStats = stats(13)))
    }
}
