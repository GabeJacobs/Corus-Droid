package fm.corus.android.data.remote

import fm.corus.android.data.remote.CloudFunctionsDataSource.PlaylistTracksOutcome
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [CloudFunctionsDataSource.parsePlaylistTracksResponse] turns the backend's
 * `appleMusicTracks` response into an outcome the TIDAL flow acts on. Mirrors
 * the iOS parser so paywall / SoundCloud / empty handling stays consistent.
 */
class PlaylistTracksResponseParseTest {

    @Test
    fun `paywall code maps to Paywall`() {
        val out = CloudFunctionsDataSource.parsePlaylistTracksResponse(
            mapOf("error" to true, "code" to "PAYWALL", "message" to "Club members only"),
        )
        assertEquals(PlaylistTracksOutcome.Paywall, out)
    }

    @Test
    fun `error with soundcloudSkipped maps to Failure carrying the count`() {
        val out = CloudFunctionsDataSource.parsePlaylistTracksResponse(
            mapOf("error" to true, "message" to "no tracks", "soundcloudSkipped" to 3.0),
        )
        assertEquals(PlaylistTracksOutcome.Failure(3), out)
    }

    @Test
    fun `empty or missing tracks maps to Failure`() {
        assertEquals(
            PlaylistTracksOutcome.Failure(0),
            CloudFunctionsDataSource.parsePlaylistTracksResponse(mapOf("tracks" to emptyList<Any>())),
        )
        assertEquals(
            PlaylistTracksOutcome.Failure(0),
            CloudFunctionsDataSource.parsePlaylistTracksResponse(emptyMap()),
        )
    }

    @Test
    fun `tracks are parsed with username and blank isrc nulled out`() {
        val out = CloudFunctionsDataSource.parsePlaylistTracksResponse(
            mapOf(
                "username" to "gabe",
                "soundcloudSkipped" to 1L,
                "tracks" to listOf(
                    mapOf("trackId" to "sp1", "isrc" to "US123", "name" to "Song A", "artist" to "X", "album" to "Alb"),
                    mapOf("trackId" to "sp2", "isrc" to "", "name" to "Song B", "artist" to "Y", "album" to ""),
                    mapOf("trackId" to "", "isrc" to "", "name" to "", "artist" to ""), // dropped: no name/isrc
                ),
            ),
        )
        assertTrue(out is PlaylistTracksOutcome.Tracks)
        out as PlaylistTracksOutcome.Tracks
        assertEquals("gabe", out.username)
        assertEquals(1, out.soundcloudSkipped)
        assertEquals(2, out.descriptors.size)
        assertEquals("US123", out.descriptors[0].isrc)
        assertNull(out.descriptors[1].isrc)
        assertEquals("Song B", out.descriptors[1].name)
    }
}
