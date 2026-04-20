package fm.corus.android.data.model

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.util.Date

class CymbalPostIsNewReleaseTest {

    private val user = CymbalUser(id = "u1", username = "alice", displayName = "Alice")
    private val today = LocalDate.of(2026, 4, 20)

    private fun post(
        releaseDate: String?,
        precision: String? = "day",
        mediaType: MediaType = MediaType.TRACK,
        movieReleaseDate: String? = null,
    ): CymbalPost = CymbalPost(
        id = "p1",
        user = user,
        track = CymbalTrack(
            id = "t1",
            name = "Track",
            artistName = "Artist",
            albumName = "Album",
            releaseDate = releaseDate,
            releaseDatePrecision = precision,
        ),
        timestamp = Date(),
        mediaType = mediaType,
        movieReleaseDate = movieReleaseDate,
    )

    @Test
    fun `released today is new`() {
        assertTrue(post("2026-04-20").isNewRelease(today))
    }

    @Test
    fun `released 29 days ago is still new`() {
        assertTrue(post("2026-03-22").isNewRelease(today))
    }

    @Test
    fun `released exactly 30 days ago is no longer new`() {
        assertFalse(post("2026-03-21").isNewRelease(today))
    }

    @Test
    fun `released 31 days ago is not new`() {
        assertFalse(post("2026-03-20").isNewRelease(today))
    }

    @Test
    fun `null release date is not new`() {
        assertFalse(post(releaseDate = null).isNewRelease(today))
    }

    @Test
    fun `month precision matching current month is new`() {
        // Today is 2026-04-20; a release with precision=month, value=2026-04 qualifies.
        assertTrue(post("2026-04", precision = "month").isNewRelease(today))
    }

    @Test
    fun `month precision previous month is not new`() {
        // Release in March even though today is April 20 — we don't know the day.
        assertFalse(post("2026-03", precision = "month").isNewRelease(today))
    }

    @Test
    fun `month precision future month is not new`() {
        assertFalse(post("2026-05", precision = "month").isNewRelease(today))
    }

    @Test
    fun `year precision is not new`() {
        assertFalse(post("2026", precision = "year").isNewRelease(today))
    }

    @Test
    fun `null precision is not new`() {
        assertFalse(post("2026-04-20", precision = null).isNewRelease(today))
    }

    @Test
    fun `malformed date is not new`() {
        assertFalse(post("not-a-date").isNewRelease(today))
    }

    @Test
    fun `future release date is not new`() {
        // Defensive: Spotify shouldn't return future dates, but guard anyway.
        assertFalse(post("2026-05-01").isNewRelease(today))
    }

    @Test
    fun `movie with release date within 30 days is new`() {
        assertTrue(
            post(releaseDate = null, mediaType = MediaType.MOVIE, movieReleaseDate = "2026-04-10")
                .isNewRelease(today)
        )
    }

    @Test
    fun `movie released 31 days ago is not new`() {
        assertFalse(
            post(releaseDate = null, mediaType = MediaType.MOVIE, movieReleaseDate = "2026-03-20")
                .isNewRelease(today)
        )
    }

    @Test
    fun `movie without release date is not new`() {
        assertFalse(
            post(releaseDate = null, mediaType = MediaType.MOVIE, movieReleaseDate = null)
                .isNewRelease(today)
        )
    }

    @Test
    fun `movie with malformed release date is not new`() {
        assertFalse(
            post(releaseDate = null, mediaType = MediaType.MOVIE, movieReleaseDate = "not-a-date")
                .isNewRelease(today)
        )
    }
}
