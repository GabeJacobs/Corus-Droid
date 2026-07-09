package fm.corus.android.data.model

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * The [CymbalPost.isTrackNewRelease] / [CymbalPost.isMovieNewRelease] companion
 * helpers back the song/film detail screens' first-frame NEW RELEASE tag: they
 * badge from a seed release date carried on the route (search/catalog/trending
 * taps) BEFORE the page's posts load. They must apply the same rule as the
 * instance [CymbalPost.isNewRelease] (which now delegates to them), so a trending
 * tap and a loaded post never disagree.
 */
class CymbalPostSeedFreshnessTest {

    private val today = LocalDate.of(2026, 4, 20)

    @Test
    fun `track day precision within 30 days is new`() {
        assertTrue(CymbalPost.isTrackNewRelease("2026-04-10", "day", today))
    }

    @Test
    fun `track day precision exactly 30 days ago is not new`() {
        assertFalse(CymbalPost.isTrackNewRelease("2026-03-21", "day", today))
    }

    @Test
    fun `track month precision matching current month is new`() {
        assertTrue(CymbalPost.isTrackNewRelease("2026-04", "month", today))
    }

    @Test
    fun `track month precision previous month is not new`() {
        assertFalse(CymbalPost.isTrackNewRelease("2026-03", "month", today))
    }

    @Test
    fun `track year precision is not new`() {
        assertFalse(CymbalPost.isTrackNewRelease("2026", "year", today))
    }

    @Test
    fun `track null date or null precision is not new`() {
        assertFalse(CymbalPost.isTrackNewRelease(null, "day", today))
        assertFalse(CymbalPost.isTrackNewRelease("2026-04-10", null, today))
    }

    @Test
    fun `movie within 30 days is new`() {
        assertTrue(CymbalPost.isMovieNewRelease("2026-04-10", today))
    }

    @Test
    fun `movie 31 days ago is not new`() {
        assertFalse(CymbalPost.isMovieNewRelease("2026-03-20", today))
    }

    @Test
    fun `movie null or malformed date is not new`() {
        assertFalse(CymbalPost.isMovieNewRelease(null, today))
        assertFalse(CymbalPost.isMovieNewRelease("not-a-date", today))
    }
}
