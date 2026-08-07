package fm.corus.android.ui.screens.feed

import fm.corus.android.data.model.CymbalPost
import fm.corus.android.data.model.CymbalTrack
import fm.corus.android.data.model.CymbalUser
import fm.corus.android.data.model.MediaType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneOffset

class AlbumArtHintCohortTest {

    private fun epochMs(year: Int, month: Int, day: Int): Long =
        LocalDate.of(year, month, day)
            .atStartOfDay(ZoneOffset.UTC)
            .toInstant()
            .toEpochMilli()

    @Test
    fun `accounts created before cutoff are not in the hint cohort`() {
        assertFalse(isNewAlbumArtHintAccount(epochMs(2026, 5, 9)))
        assertFalse(isNewAlbumArtHintAccount(epochMs(2024, 1, 1)))
    }

    @Test
    fun `accounts created on cutoff are in the hint cohort`() {
        assertTrue(isNewAlbumArtHintAccount(ALBUM_ART_HINT_CUTOFF_MS))
        assertTrue(isNewAlbumArtHintAccount(epochMs(2026, 5, 10)))
    }

    @Test
    fun `accounts created after cutoff are in the hint cohort`() {
        assertTrue(isNewAlbumArtHintAccount(epochMs(2026, 5, 11)))
        assertTrue(isNewAlbumArtHintAccount(epochMs(2027, 1, 1)))
    }

    @Test
    fun `missing creation timestamp is treated as not in the hint cohort`() {
        // Avoids a flash of the hint while auth metadata is still resolving.
        assertFalse(isNewAlbumArtHintAccount(null))
    }

    private fun trackPost(
        id: String,
        unavailable: Boolean = false,
    ) = CymbalPost(
        id = id,
        user = CymbalUser(id = "u1", username = "u1", displayName = "u1"),
        track = CymbalTrack(
            id = "t-$id",
            name = "n",
            artistName = "a",
            albumName = "al",
            unavailable = unavailable,
        ),
    )

    private fun filmPost(id: String) = CymbalPost(
        id = id,
        user = CymbalUser(id = "u1", username = "u1", displayName = "u1"),
        track = CymbalTrack(id = "t-$id", name = "n", artistName = "a", albumName = "al"),
        mediaType = MediaType.MOVIE,
        movieId = "m-$id",
        movieTitle = "Film",
    )

    @Test
    fun `hint target skips films and picks the first playable track`() {
        val posts = listOf(
            filmPost("film-1"),
            trackPost("track-1"),
            trackPost("track-2"),
        )
        assertEquals("track-1", albumArtHintTargetPostId(posts))
    }

    @Test
    fun `hint target skips unavailable tracks`() {
        val posts = listOf(
            filmPost("film-1"),
            trackPost("unavail", unavailable = true),
            trackPost("playable"),
        )
        assertEquals("playable", albumArtHintTargetPostId(posts))
    }

    @Test
    fun `hint target is null when no playable tracks exist`() {
        assertNull(albumArtHintTargetPostId(listOf(filmPost("film-1"))))
    }
}
