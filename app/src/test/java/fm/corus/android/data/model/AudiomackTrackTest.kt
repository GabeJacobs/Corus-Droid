package fm.corus.android.data.model

import fm.corus.android.data.repository.parseUnifiedTrack
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Audiomack is a link-out-only music source (no in-app playback). These tests
 * pin the two load-bearing bits of that contract: the `source` discriminator
 * parses "audiomack" -> [TrackSource.AUDIOMACK], and the resolved link-out
 * target for an Audiomack track is its `audiomackUrl`.
 */
class AudiomackTrackTest {

    @Test
    fun `TrackSource fromRaw maps audiomack to AUDIOMACK`() {
        assertEquals(TrackSource.AUDIOMACK, TrackSource.fromRaw("audiomack"))
        assertEquals("audiomack", TrackSource.AUDIOMACK.raw)
    }

    @Test
    fun `parseUnifiedTrack maps an Audiomack searchSongs result`() {
        val track = parseUnifiedTrack(
            mapOf(
                "id" to "amk:12345",
                "name" to "Soundgasm",
                "artistName" to "Rema",
                "source" to "audiomack",
                "albumName" to "",
                "albumArtURL" to "https://assets.audiomack.com/x.jpg",
                "durationMs" to 132000,
                "audiomackId" to "12345",
                "audiomackUrl" to "https://audiomack.com/rema/song/soundgasm",
                // Backend contract: isrc + previewUrl are null for Audiomack.
            )
        )
        requireNotNull(track)
        assertEquals(TrackSource.AUDIOMACK, track.source)
        assertEquals("amk:12345", track.id)
        assertEquals("12345", track.audiomackId)
        assertEquals("https://audiomack.com/rema/song/soundgasm", track.audiomackUrl)
        // Link-out only: no preview / isrc, and no synthesized Spotify link.
        assertNull(track.previewUrl)
        assertNull(track.isrc)
        assertEquals("", track.spotifyURI)
    }

    @Test
    fun `link-out target for an Audiomack track is its audiomackUrl`() {
        val track = CymbalTrack(
            id = "amk:1",
            name = "Track",
            artistName = "Artist",
            albumName = "",
            source = TrackSource.AUDIOMACK,
            audiomackId = "1",
            audiomackUrl = "https://audiomack.com/artist/song/track",
        )
        assertEquals("https://audiomack.com/artist/song/track", track.audiomackLinkOutUrl)
    }

    @Test
    fun `link-out target is null for non-Audiomack sources and blank urls`() {
        // A Spotify track that happens to carry an audiomackUrl must NOT link out.
        val spotify = CymbalTrack(
            id = "spot1",
            name = "Track",
            artistName = "Artist",
            albumName = "",
            source = TrackSource.SPOTIFY,
            audiomackUrl = "https://audiomack.com/artist/song/track",
        )
        assertNull(spotify.audiomackLinkOutUrl)

        // Blank url on an Audiomack track is a graceful no-op, not a dead link.
        val blank = CymbalTrack(
            id = "amk:2",
            name = "Track",
            artistName = "Artist",
            albumName = "",
            source = TrackSource.AUDIOMACK,
            audiomackUrl = "",
        )
        assertNull(blank.audiomackLinkOutUrl)
    }

    @Test
    fun `toSongDetailRoute carries audiomackUrl so a postless search track keeps its link-out`() {
        // Regression: opening a not-yet-posted Audiomack track from search used to
        // reach the song page without its link-out url (it was sourced only from a
        // loaded post), so no "Listen on Audiomack" button showed. The route must
        // now carry audiomackUrl through navigation.
        val track = CymbalTrack(
            id = "amk:1649",
            name = "Stick Freestyle (Dreamville Remix)",
            artistName = "Des",
            albumName = "",
            source = TrackSource.AUDIOMACK,
            audiomackId = "1649",
            audiomackUrl = "https://audiomack.com/des/song/stick-freestyle-dreamville-remix",
        )
        val route = track.toSongDetailRoute()
        assertEquals("amk:1649", route.trackId)
        assertEquals("audiomack", route.source)
        assertEquals(
            "https://audiomack.com/des/song/stick-freestyle-dreamville-remix",
            route.audiomackUrl,
        )
    }
}
