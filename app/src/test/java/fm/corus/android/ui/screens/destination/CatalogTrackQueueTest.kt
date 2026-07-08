package fm.corus.android.ui.screens.destination

import fm.corus.android.data.model.CymbalTrack
import fm.corus.android.data.model.TrackSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Unit tests for [toQueuedTrack] — the CymbalTrack → QueuedTrack mapping that
 * lets tapping a row on the artist ("Popular") and album pages queue up the
 * rest of the list. Mirrors the single-track play args in CatalogTrackRow so
 * behaviour is identical whether or not a queue is supplied.
 */
class CatalogTrackQueueTest {

    private fun track(
        id: String = "t1",
        spotifyURI: String = "spotify:track:t1",
        spotifyWebURL: String = "https://open.spotify.com/track/t1",
    ) = CymbalTrack(
        id = id,
        name = "In My Life",
        artistName = "The Beatles",
        albumName = "Rubber Soul",
        albumArtURL = "https://img/small.jpg",
        albumArtLargeURL = "https://img/large.jpg",
        spotifyURI = spotifyURI,
        spotifyWebURL = spotifyWebURL,
        previewUrl = "https://preview/t1.m4a",
        isrc = "GBAYE0601477",
        source = TrackSource.SPOTIFY,
        soundcloudId = null,
        soundcloudPermalinkUrl = null,
    )

    @Test
    fun `maps catalog fields onto the queued track`() {
        val queued = track().toQueuedTrack()

        assertEquals("t1", queued.trackId)
        assertEquals("In My Life", queued.trackName)
        assertEquals("The Beatles", queued.artistName)
        assertEquals("https://img/small.jpg", queued.albumArtURL)
        assertEquals("https://img/large.jpg", queued.albumArtLargeURL)
        assertEquals("https://preview/t1.m4a", queued.previewUrl)
        assertEquals("spotify:track:t1", queued.spotifyURI)
        assertEquals("https://open.spotify.com/track/t1", queued.spotifyWebURL)
        assertEquals("GBAYE0601477", queued.isrc)
        assertEquals(TrackSource.SPOTIFY, queued.source)
    }

    @Test
    fun `blank spotify uri and url collapse to null`() {
        // CymbalTrack carries "" for absent Spotify ids; QueuedTrack expects
        // null so downstream link-outs don't build a dead spotify: URI.
        val queued = track(spotifyURI = "", spotifyWebURL = "").toQueuedTrack()

        assertNull(queued.spotifyURI)
        assertNull(queued.spotifyWebURL)
    }

    @Test
    fun `catalog tracks carry no source post or poster`() {
        // Catalog rows aren't feed posts: the queue must resolve and advance by
        // track id, and nothing should be pruned by an unfollow.
        val queued = track().toQueuedTrack()

        assertNull(queued.sourcePostId)
        assertNull(queued.posterUserId)
    }

    @Test
    fun `mapping a list preserves order so the tapped track resolves in place`() {
        val tracks = listOf(track(id = "a"), track(id = "b"), track(id = "c"))

        val queue = tracks.map { it.toQueuedTrack() }

        assertEquals(listOf("a", "b", "c"), queue.map { it.trackId })
        // The manager finds the tapped track by id, so the mapped queue must
        // keep the same ids in the same positions.
        assertEquals(1, queue.indexOfFirst { it.trackId == "b" })
    }

    @Test
    fun `tapping the album cover plays track 1 with the whole album queued`() {
        // AlbumPageScreen wires the cover tap to
        //   play(track = albumQueue.first(), queue = albumQueue)
        // so playback must start at track 1 and roll through the rest of the
        // album in order.
        val tracks = listOf(track(id = "a"), track(id = "b"), track(id = "c"))

        val albumQueue = tracks.map { it.toQueuedTrack() }
        val first = albumQueue.first()

        assertEquals("a", first.trackId)
        assertEquals(listOf("a", "b", "c"), albumQueue.map { it.trackId })
        // The cover always starts the album from the top; the tapped track is at
        // index 0 of its own queue.
        assertEquals(0, albumQueue.indexOfFirst { it.trackId == first.trackId })
    }
}
