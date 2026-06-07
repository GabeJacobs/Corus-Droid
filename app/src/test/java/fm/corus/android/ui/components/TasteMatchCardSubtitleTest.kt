package fm.corus.android.ui.components

import fm.corus.android.data.model.MusicMatchData
import fm.corus.android.data.model.SharedMoviePreview
import fm.corus.android.data.model.SharedTrackPreview
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Regression coverage for the Taste Match card subtitle.
 *
 * The bug: the subtitle listed artists the viewer never posted, because it was
 * derived from `sharedTrackPreviews`/`sharedMoviePreviews` — and the backend
 * pads those preview tiles with the candidate's recent (non-shared) posts to
 * fill the 2x2 art grid. The fix reads the authoritative `sharedArtistNames` /
 * `sharedDirectorNames` arrays instead.
 */
class TasteMatchCardSubtitleTest {

    @Test
    fun `subtitle ignores non-shared filler artists from preview tiles`() {
        val data = MusicMatchData(
            sharedArtists = 2,
            // Authoritative overlap: only these are genuinely shared.
            sharedArtistNames = listOf("Vampire Weekend", "Braid"),
            // Preview tiles include padding the viewer never posted (e.g. used
            // only to fill the album-art grid). These must NOT leak into the text.
            sharedTrackPreviews = listOf(
                preview("Vampire Weekend"),
                preview("The White Octave"),
                preview("Braid"),
                preview("Modest Mouse"),
            ),
        )

        assertEquals("Vampire Weekend, Braid", buildSharedNamesSubtitle(data))
    }

    @Test
    fun `subtitle includes shared directors after artists`() {
        val data = MusicMatchData(
            sharedArtists = 1,
            sharedDirectors = 1,
            sharedArtistNames = listOf("Vampire Weekend"),
            sharedDirectorNames = listOf("Pedro Almodóvar"),
            sharedMoviePreviews = listOf(
                SharedMoviePreview(
                    movieId = "1",
                    movieTitle = "Volver",
                    directorName = "Christopher Nolan", // filler — not shared
                ),
            ),
        )

        assertEquals("Vampire Weekend, Pedro Almodóvar", buildSharedNamesSubtitle(data))
    }

    @Test
    fun `subtitle dedupes and trims authoritative names`() {
        val data = MusicMatchData(
            sharedArtists = 2,
            sharedArtistNames = listOf("Braid", " braid ", "Vampire Weekend"),
        )

        assertEquals("Braid, Vampire Weekend", buildSharedNamesSubtitle(data))
    }

    @Test
    fun `returns null when there are no similarity signals`() {
        assertNull(buildSharedNamesSubtitle(null))
        assertNull(buildSharedNamesSubtitle(MusicMatchData()))
    }

    @Test
    fun `returns null when names are absent so caller falls back to count label`() {
        // Real overlap exists (sharedArtists > 0) but names weren't precomputed.
        // Subtitle should be null so the card shows "N artist matches" instead
        // of inventing names from the padded preview tiles.
        val data = MusicMatchData(
            sharedArtists = 3,
            sharedTrackPreviews = listOf(preview("The White Octave"), preview("Modest Mouse")),
        )

        assertNull(buildSharedNamesSubtitle(data))
    }

    private fun preview(artistName: String) = SharedTrackPreview(
        trackId = artistName,
        trackName = "track",
        artistName = artistName,
        albumArtURL = "https://example.com/$artistName.jpg",
    )
}
