package fm.corus.android.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Coverage for artistIds round-tripping through CommentAttachedSong (and
 * through CommentAttachedFilm.directorIds). Comment attachments are written
 * directly to Firestore from the client (no Cloud Function gate), so if the
 * model strips the field, ID-based matching never sees the data.
 */
class CommentAttachedSongArtistIdsTest {

    @Test
    fun `fromMap reads artistIds`() {
        val song = CommentAttachedSong.fromMap(
            mapOf(
                "trackId" to "abc",
                "trackName" to "Impossible Soul",
                "artistName" to "Sufjan Stevens, My Brightest Diamond",
                "artistIds" to listOf("sufjan_id", "mbd_id"),
            )
        )
        assertNotNull(song)
        assertEquals(listOf("sufjan_id", "mbd_id"), song!!.artistIds)
    }

    @Test
    fun `fromMap defaults artistIds to empty list`() {
        val song = CommentAttachedSong.fromMap(
            mapOf(
                "trackId" to "abc",
                "trackName" to "x",
                "artistName" to "y",
            )
        )
        assertNotNull(song)
        assertTrue(song!!.artistIds.isEmpty())
    }

    @Test
    fun `toFirestoreMap includes artistIds when populated`() {
        val song = CommentAttachedSong(
            trackId = "abc",
            trackName = "x",
            artistName = "y",
            artistIds = listOf("a", "b"),
        )
        val map = song.toFirestoreMap()
        assertEquals(listOf("a", "b"), map["artistIds"])
    }

    @Test
    fun `toFirestoreMap omits artistIds when empty`() {
        val song = CommentAttachedSong(
            trackId = "abc",
            trackName = "x",
            artistName = "y",
        )
        val map = song.toFirestoreMap()
        assertTrue("artistIds key absent when empty", !map.containsKey("artistIds"))
    }

    @Test
    fun `film directorIds round-trip`() {
        val film = CommentAttachedFilm.fromMap(
            mapOf(
                "movieId" to "tmdb_1",
                "movieTitle" to "Burn After Reading",
                "directorName" to "Joel Coen, Ethan Coen",
                "directorIds" to listOf("joel_coen_id", "ethan_coen_id"),
            )
        )
        assertNotNull(film)
        assertEquals(listOf("joel_coen_id", "ethan_coen_id"), film!!.directorIds)

        val map = film.toFirestoreMap()
        assertEquals(listOf("joel_coen_id", "ethan_coen_id"), map["directorIds"])
    }
}
