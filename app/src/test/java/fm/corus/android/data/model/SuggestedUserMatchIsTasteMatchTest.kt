package fm.corus.android.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A taste match = the viewer shares >=3 DISTINCT artists (or directors) with the
 * user, gated on the NAMED shared artists the card lists. Songs/films alone do
 * not qualify, and the gate guarantees a taste-match cell always has >=3 artists
 * to show. Mirrors web isTasteMatch + iOS SuggestedUserMatch.isTasteMatch.
 */
class SuggestedUserMatchIsTasteMatchTest {

    private fun match(
        artists: List<String> = emptyList(),
        directors: List<String> = emptyList(),
        data: MusicMatchData? = MusicMatchData(),
    ) = SuggestedUserMatch(
        user = CymbalUser(id = "u", username = "u", displayName = "U"),
        matchData = data?.copy(sharedArtistNames = artists, sharedDirectorNames = directors),
    )

    @Test
    fun `threshold is 3`() {
        assertEquals(3, SuggestedUserMatch.TASTE_MATCH_MIN_ARTISTS)
    }

    @Test
    fun `three shared artists is a taste match`() {
        assertTrue(match(artists = listOf("A", "B", "C")).isTasteMatch)
    }

    @Test
    fun `directors count toward the three`() {
        assertTrue(match(artists = listOf("A", "B"), directors = listOf("D")).isTasteMatch)
    }

    @Test
    fun `two shared artists is not a taste match`() {
        assertFalse(match(artists = listOf("A", "B")).isTasteMatch)
    }

    @Test
    fun `songs and films alone are not a taste match`() {
        val songsOnly = MusicMatchData(similarityScore = 0.9, sharedPostedTracks = 5, sharedPostedMovies = 2)
        assertFalse(match(data = songsOnly).isTasteMatch)
    }

    @Test
    fun `null matchData is not a taste match`() {
        assertFalse(match(data = null).isTasteMatch)
    }
}
