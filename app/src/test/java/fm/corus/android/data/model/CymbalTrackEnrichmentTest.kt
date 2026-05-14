package fm.corus.android.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Coverage for the stub-track enrichment helpers. Mirrors the server-side
 * spotifyEnrichment.test.mjs and the iOS PostService enrichment path —
 * these three must agree on what counts as a "stub track".
 */
class CymbalTrackEnrichmentTest {

    private fun complete(): CymbalTrack = CymbalTrack(
        id = "abc",
        name = "Track",
        artistName = "Artist",
        artistIds = listOf("artist_id"),
        albumName = "Album",
        albumArtURL = "https://i.scdn.co/x.jpg",
        albumArtLargeURL = "https://i.scdn.co/x-large.jpg",
        spotifyURI = "spotify:track:abc",
        spotifyWebURL = "https://open.spotify.com/track/abc",
        durationMs = 200_000,
        previewUrl = "https://p.scdn.co/y.mp3",
        isrc = "USABC1234567",
        releaseDate = "2026-04-15",
        releaseDatePrecision = "day",
        source = TrackSource.SPOTIFY,
    )

    // ── hasStubMetadata: negative (complete tracks) ────────────────────

    @Test
    fun `complete day-precision spotify track is not a stub`() {
        assertFalse(complete().hasStubMetadata())
    }

    @Test
    fun `complete month-precision spotify track is not a stub`() {
        val t = complete().copy(releaseDate = "2026-04", releaseDatePrecision = "month")
        assertFalse(t.hasStubMetadata())
    }

    @Test
    fun `year-precision counts as complete`() {
        val t = complete().copy(releaseDate = "2024", releaseDatePrecision = "year")
        assertFalse(t.hasStubMetadata())
    }

    @Test
    fun `soundcloud track is never treated as a stub`() {
        val t = complete().copy(
            id = "sc:123",
            source = TrackSource.SOUNDCLOUD,
            albumName = "",
            durationMs = 0,
            isrc = null,
            releaseDate = null,
            releaseDatePrecision = null,
        )
        assertFalse(t.hasStubMetadata())
    }

    @Test
    fun `empty trackId is never a stub`() {
        // Nothing to look up.
        val t = complete().copy(id = "")
        assertFalse(t.hasStubMetadata())
    }

    // ── hasStubMetadata: positive (incomplete tracks) ──────────────────

    @Test
    fun `empty albumName triggers stub`() {
        assertTrue(complete().copy(albumName = "").hasStubMetadata())
    }

    @Test
    fun `zero durationMs triggers stub`() {
        assertTrue(complete().copy(durationMs = 0).hasStubMetadata())
    }

    @Test
    fun `null releaseDate triggers stub`() {
        assertTrue(complete().copy(releaseDate = null).hasStubMetadata())
    }

    @Test
    fun `empty releaseDate triggers stub`() {
        // The legacy DatabaseService.createPost path wrote "" rather than null.
        // After fromMap, "" becomes null, but defensive coverage doesn't hurt.
        assertTrue(complete().copy(releaseDate = "").hasStubMetadata())
    }

    @Test
    fun `null releaseDatePrecision triggers stub`() {
        assertTrue(complete().copy(releaseDatePrecision = null).hasStubMetadata())
    }

    @Test
    fun `null isrc triggers stub`() {
        assertTrue(complete().copy(isrc = null).hasStubMetadata())
    }

    @Test
    fun `the DM-attachment failure mode is detected`() {
        // Real shape of a track reconstructed from CommentAttachedSong /
        // CymbalMessage.attachedSong: trackId/name/artist + maybe albumArt,
        // but no albumName, durationMs=0, no isrc, no releaseDate.
        val t = CymbalTrack(
            id = "abc",
            name = "So Twisted Fate",
            artistName = "Frog",
            albumName = "",
            durationMs = 0,
        )
        assertTrue(t.hasStubMetadata())
    }

    // ── mergeMissing ───────────────────────────────────────────────────

    @Test
    fun `mergeMissing fills every empty field`() {
        val stub = CymbalTrack(
            id = "abc",
            name = "So Twisted Fate",
            artistName = "Frog",
            albumName = "",
            durationMs = 0,
        )
        val full = complete().copy(
            albumName = "Grog",
            durationMs = 184_000,
            isrc = "USABC9999999",
            releaseDate = "2026-05-01",
            releaseDatePrecision = "day",
            albumArtURL = "https://i.scdn.co/grog.jpg",
            albumArtLargeURL = "https://i.scdn.co/grog-large.jpg",
            previewUrl = "https://p.scdn.co/grog.mp3",
            artistIds = listOf("frog_id"),
        )

        val merged = stub.mergeMissing(full)

        assertEquals("Grog", merged.albumName)
        assertEquals(184_000, merged.durationMs)
        assertEquals("USABC9999999", merged.isrc)
        assertEquals("2026-05-01", merged.releaseDate)
        assertEquals("day", merged.releaseDatePrecision)
        assertEquals("https://i.scdn.co/grog.jpg", merged.albumArtURL)
        assertEquals("https://i.scdn.co/grog-large.jpg", merged.albumArtLargeURL)
        assertEquals("https://p.scdn.co/grog.mp3", merged.previewUrl)
        assertEquals(listOf("frog_id"), merged.artistIds)
        // Caller-provided fields preserved.
        assertEquals("abc", merged.id)
        assertEquals("So Twisted Fate", merged.name)
        assertEquals("Frog", merged.artistName)
    }

    @Test
    fun `mergeMissing keeps existing values when present`() {
        // Idempotency / no-overwrite guarantee. If `this` has real data, the
        // Spotify side never wins.
        val good = complete()
        val different = complete().copy(
            albumName = "DIFFERENT ALBUM",
            durationMs = 999,
            isrc = "ZZZZZZZZZZZZ",
            releaseDate = "1999-01-01",
            releaseDatePrecision = "month",
            previewUrl = "https://bad.example/x.mp3",
        )

        val merged = good.mergeMissing(different)

        assertEquals(good.albumName, merged.albumName)
        assertEquals(good.durationMs, merged.durationMs)
        assertEquals(good.isrc, merged.isrc)
        assertEquals(good.releaseDate, merged.releaseDate)
        assertEquals(good.releaseDatePrecision, merged.releaseDatePrecision)
        assertEquals(good.previewUrl, merged.previewUrl)
    }

    @Test
    fun `mergeMissing fills only the missing field, leaves others alone`() {
        // Real-world partial: comment attachment has album art and name but
        // no release date.
        val partial = complete().copy(releaseDate = null, releaseDatePrecision = null)
        val full = complete().copy(albumName = "DIFFERENT ALBUM", durationMs = 999)

        val merged = partial.mergeMissing(full)

        // Filled the missing release-date pair.
        assertEquals(complete().releaseDate, merged.releaseDate)
        assertEquals(complete().releaseDatePrecision, merged.releaseDatePrecision)
        // Did NOT clobber the other fields with the (also valid) `full` values.
        assertEquals(complete().albumName, merged.albumName)
        assertEquals(complete().durationMs, merged.durationMs)
    }

    @Test
    fun `mergeMissing tolerates spotify side also being incomplete`() {
        // If Spotify returns partial data (shouldn't happen but defense in
        // depth), we just fill what we can.
        val stub = CymbalTrack(
            id = "abc",
            name = "x",
            artistName = "y",
            albumName = "",
            durationMs = 0,
        )
        val partialSpotify = complete().copy(isrc = null, previewUrl = null)

        val merged = stub.mergeMissing(partialSpotify)

        assertEquals(complete().albumName, merged.albumName)
        assertEquals(complete().durationMs, merged.durationMs)
        // null on spotify side, null on stub side → stays null. No empty
        // string written.
        assertNull(merged.isrc)
        assertNull(merged.previewUrl)
    }

    @Test
    fun `after mergeMissing the track is no longer a stub`() {
        // End-to-end idempotency proof: a merged track will skip future
        // enrichment attempts. Mirrors server-side shouldEnrichPost on the
        // merged data.
        val stub = CymbalTrack(
            id = "abc",
            name = "x",
            artistName = "y",
            albumName = "",
            durationMs = 0,
        )
        val merged = stub.mergeMissing(complete())
        assertFalse(merged.hasStubMetadata())
    }
}
