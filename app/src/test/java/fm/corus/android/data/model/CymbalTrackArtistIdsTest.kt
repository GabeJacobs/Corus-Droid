package fm.corus.android.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Coverage for the artistIds field added to CymbalTrack so the backend can
 * intersect by per-artist Spotify ID rather than the credit string. The
 * original bug ("Sufjan Stevens" never matched "Sufjan Stevens, My Brightest
 * Diamond" because the strings differ exactly) is sidestepped only if Android
 * round-trips artistIds through CymbalTrack.fromMap.
 */
class CymbalTrackArtistIdsTest {

    @Test
    fun `artistIds defaults to empty list when missing`() {
        val track = CymbalTrack.fromMap(
            mapOf(
                "trackId" to "abc",
                "trackName" to "Track",
                "artistName" to "Artist",
                "albumName" to "Album",
            )
        )
        assertEquals(emptyList<String>(), track.artistIds)
    }

    @Test
    fun `reads artistIds when present`() {
        val track = CymbalTrack.fromMap(
            mapOf(
                "trackId" to "abc",
                "trackName" to "Impossible Soul",
                "artistName" to "Sufjan Stevens, My Brightest Diamond",
                "albumName" to "The Age of Adz",
                "artistIds" to listOf("sufjan_id", "mbd_id"),
            )
        )
        assertEquals(listOf("sufjan_id", "mbd_id"), track.artistIds)
    }

    @Test
    fun `drops empty and non-string entries from artistIds`() {
        val track = CymbalTrack.fromMap(
            mapOf(
                "trackId" to "abc",
                "trackName" to "x",
                "artistName" to "y",
                "albumName" to "",
                "artistIds" to listOf("real_id", "", null, 42, "another_id"),
            )
        )
        assertEquals(listOf("real_id", "another_id"), track.artistIds)
    }

    @Test
    fun `tolerates non-list artistIds value`() {
        val track = CymbalTrack.fromMap(
            mapOf(
                "trackId" to "abc",
                "trackName" to "x",
                "artistName" to "y",
                "albumName" to "",
                "artistIds" to "not-a-list",
            )
        )
        assertTrue(track.artistIds.isEmpty())
    }
}
