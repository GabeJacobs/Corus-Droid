package fm.corus.android.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Verifies that CymbalTrack.fromMap correctly reads the SoundCloud-related
 * fields the backend (`serializePost`) writes for SC posts, and that it
 * doesn't synthesize a fake Spotify URL for SC tracks (which would break
 * the "Listen on Spotify" tap-through).
 */
class CymbalTrackSoundCloudTest {

    @Test
    fun `defaults to Spotify when trackSource is missing`() {
        val track = CymbalTrack.fromMap(
            mapOf(
                "trackId" to "abc",
                "trackName" to "Track",
                "artistName" to "Artist",
                "albumName" to "Album",
            )
        )
        assertEquals(TrackSource.SPOTIFY, track.source)
        assertNull(track.soundcloudId)
        assertNull(track.soundcloudPermalinkUrl)
        assertFalse(track.unavailable)
    }

    @Test
    fun `reads SoundCloud fields when trackSource is soundcloud`() {
        val track = CymbalTrack.fromMap(
            mapOf(
                "trackId" to "sc:123",
                "trackName" to "Boiler Room Mix",
                "artistName" to "Ryisfly",
                "albumName" to "",
                "trackSource" to "soundcloud",
                "soundcloudId" to "123",
                "soundcloudPermalinkUrl" to "https://soundcloud.com/ryisfly/boiler-room-mix",
            )
        )
        assertEquals(TrackSource.SOUNDCLOUD, track.source)
        assertEquals("123", track.soundcloudId)
        assertEquals("https://soundcloud.com/ryisfly/boiler-room-mix", track.soundcloudPermalinkUrl)
    }

    @Test
    fun `does not synthesize Spotify URL for SoundCloud tracks`() {
        val track = CymbalTrack.fromMap(
            mapOf(
                "trackId" to "sc:123",
                "trackName" to "X",
                "artistName" to "Y",
                "albumName" to "",
                "trackSource" to "soundcloud",
                // Note: spotifyURI / spotifyWebURL absent
            )
        )
        assertEquals("", track.spotifyURI)
        assertEquals("", track.spotifyWebURL)
    }

    @Test
    fun `reads unavailable flag when track has been deleted`() {
        val track = CymbalTrack.fromMap(
            mapOf(
                "trackId" to "sc:123",
                "trackName" to "X",
                "artistName" to "Y",
                "albumName" to "",
                "trackSource" to "soundcloud",
                "trackUnavailable" to true,
                "trackUnavailableReason" to "deleted",
            )
        )
        assertTrue(track.unavailable)
        assertEquals("deleted", track.unavailableReason)
    }
}
