package fm.corus.android.data.model

import fm.corus.android.data.repository.parseUnifiedTrack
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Search / compose art badges key off [CymbalTrack.isBandcampCatalog], not
 * only `source == BANDCAMP`, so a Bandcamp exclusive still gets the aqua
 * mark if the discriminator was dropped. These tests pin that contract.
 */
class BandcampTrackTest {

    @Test
    fun `TrackSource fromRaw maps bandcamp case-insensitively`() {
        assertEquals(TrackSource.BANDCAMP, TrackSource.fromRaw("bandcamp"))
        assertEquals(TrackSource.BANDCAMP, TrackSource.fromRaw("Bandcamp"))
        assertEquals("bandcamp", TrackSource.BANDCAMP.raw)
    }

    @Test
    fun `parseUnifiedTrack maps a Bandcamp searchSongs result`() {
        val track = parseUnifiedTrack(
            mapOf(
                "id" to "bc:424242",
                "name" to "smartly",
                "artistName" to "frankie cosmos",
                "source" to "bandcamp",
                "albumName" to "Zentropy",
                "albumArtURL" to "https://f4.bcbits.com/img/a.jpg",
                "durationMs" to 0,
                "bandcampId" to "424242",
                "bandcampUrl" to "https://frankiecosmos.bandcamp.com/track/smartly",
            )
        )
        requireNotNull(track)
        assertEquals(TrackSource.BANDCAMP, track.source)
        assertTrue(track.isBandcampCatalog)
        assertEquals("bc:424242", track.id)
        assertEquals("424242", track.bandcampId)
        assertEquals(
            "https://frankiecosmos.bandcamp.com/track/smartly",
            track.bandcampLinkOutUrl,
        )
    }

    @Test
    fun `parseUnifiedTrack heals a bc id without source`() {
        val track = parseUnifiedTrack(
            mapOf(
                "id" to "bc:99",
                "name" to "smartly",
                "artistName" to "frankie cosmos",
            )
        )
        requireNotNull(track)
        assertEquals(TrackSource.BANDCAMP, track.source)
        assertTrue(track.isBandcampCatalog)
        assertEquals("99", track.bandcampId)
    }

    @Test
    fun `parseUnifiedTrack heals a bandcampUrl without source`() {
        val track = parseUnifiedTrack(
            mapOf(
                "id" to "orphan-1",
                "name" to "smartly",
                "artistName" to "frankie cosmos",
                "bandcampUrl" to "https://frankiecosmos.bandcamp.com/track/smartly",
            )
        )
        requireNotNull(track)
        assertEquals(TrackSource.BANDCAMP, track.source)
        assertTrue(track.isBandcampCatalog)
    }

    @Test
    fun `isBandcampCatalog is true from source, bc id, or page url`() {
        val bySource = CymbalTrack(
            id = "spot1",
            name = "smartly",
            artistName = "frankie cosmos",
            albumName = "",
            source = TrackSource.BANDCAMP,
        )
        val byId = CymbalTrack(
            id = "bc:1",
            name = "smartly",
            artistName = "frankie cosmos",
            albumName = "",
            source = TrackSource.SPOTIFY,
        )
        val byUrl = CymbalTrack(
            id = "spot2",
            name = "smartly",
            artistName = "frankie cosmos",
            albumName = "",
            source = TrackSource.SPOTIFY,
            bandcampUrl = "https://frankiecosmos.bandcamp.com/track/smartly",
        )
        val spotify = CymbalTrack(
            id = "spot3",
            name = "smartly",
            artistName = "frankie cosmos",
            albumName = "",
            source = TrackSource.SPOTIFY,
        )
        assertTrue(bySource.isBandcampCatalog)
        assertTrue(byId.isBandcampCatalog)
        assertTrue(byUrl.isBandcampCatalog)
        assertFalse(spotify.isBandcampCatalog)
    }

    @Test
    fun `bandcampLinkOutUrl falls back to search when the page url is missing`() {
        val track = CymbalTrack(
            id = "bc:2795236384",
            name = "Geeneus - In To The Future",
            artistName = "Geeneus",
            albumName = "Geeneus - Volumes: One",
            source = TrackSource.BANDCAMP,
        )
        val url = requireNotNull(track.bandcampLinkOutUrl)
        assertTrue(url.startsWith("https://bandcamp.com/search?"))
        assertTrue(url.contains("Geeneus"))
    }

    @Test
    fun `toSongDetailRoute carries bandcampUrl so the listen CTA is correct on first frame`() {
        val track = CymbalTrack(
            id = "bc:424242",
            name = "smartly",
            artistName = "frankie cosmos",
            albumName = "donutes",
            source = TrackSource.BANDCAMP,
            bandcampUrl = "https://frankiecosmos.bandcamp.com/track/smartly",
        )
        val route = track.toSongDetailRoute()
        assertEquals("bc:424242", route.trackId)
        assertEquals("bandcamp", route.source)
        assertEquals(
            "https://frankiecosmos.bandcamp.com/track/smartly",
            route.bandcampUrl,
        )
    }

    @Test
    fun `TrackSource resolve heals bc id and bandcampUrl before posts load`() {
        assertEquals(
            TrackSource.BANDCAMP,
            TrackSource.resolve("bc:1", raw = null, bandcampUrl = null),
        )
        assertEquals(
            TrackSource.BANDCAMP,
            TrackSource.resolve("spot1", raw = null, bandcampUrl = "https://x.bandcamp.com/track/y"),
        )
    }

    @Test
    fun `fromMap heals a bc trackId to BANDCAMP`() {
        val track = CymbalTrack.fromMap(
            mapOf(
                "trackId" to "bc:424242",
                "trackName" to "smartly",
                "artistName" to "frankie cosmos",
                "bandcampUrl" to "https://frankiecosmos.bandcamp.com/track/smartly",
            )
        )
        assertEquals(TrackSource.BANDCAMP, track.source)
        assertTrue(track.isBandcampCatalog)
        assertEquals("424242", track.bandcampId)
    }
}
