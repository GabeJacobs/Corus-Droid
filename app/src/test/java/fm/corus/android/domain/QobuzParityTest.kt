package fm.corus.android.domain

import fm.corus.android.data.model.CymbalTrack
import fm.corus.android.data.model.CymbalPost
import fm.corus.android.data.model.MusicService
import fm.corus.android.data.model.TrackSource
import fm.corus.android.data.model.QobuzMatchInput
import org.junit.Assert.*
import org.junit.Test

class QobuzParityTest {
    private val proof = QobuzMatchInput("US1234567890", "Blue", "Topaz Jones", "Album", 200000)
    private val url = "https://open.qobuz.com/track/12345"
    private val track = CymbalTrack(id="id",name="Blue",artistName="Topaz Jones",albumName="Album",
        durationMs=200000,isrc="US1234567890",qobuzURL=url,qobuzMatchInput=proof,qobuzMatchStatus="matched")
    @Test fun `preference shows Qobuz only when its feature flag is enabled`() {
        for (source in listOf(TrackSource.SPOTIFY, TrackSource.APPLEMUSIC)) {
            assertEquals(MusicService.QOBUZ, SongPlayRouting.displayedLinkOutService(source,MusicService.QOBUZ,qobuzEnabled=true))
            assertNotEquals(MusicService.QOBUZ, SongPlayRouting.displayedLinkOutService(source,MusicService.QOBUZ,qobuzEnabled=false))
        }
    }
    @Test fun `stored proof rejects stale metadata and unsafe links`() {
        assertEquals(url,proof.confirmedUrl(track))
        for (wrong in listOf(track.copy(name="Other"),track.copy(artistName="Other"),track.copy(albumName="Other"),track.copy(durationMs=0),track.copy(isrc="OTHER"),track.copy(qobuzURL="https://evil.test/track/123"),track.copy(qobuzURL="https://open.qobuz.com/album/12345"),track.copy(qobuzURL=url+"?query=1"))) assertNull(proof.confirmedUrl(wrong))
    }
    @Test fun `catalog navigation retains the duration needed before any post loads`() {
        val route=track.toSongDetailRoute()
        assertEquals(track.durationMs,route.durationMs)
        assertEquals(track.albumName,route.albumName)
        assertEquals(track.isrc,route.isrc)
    }
    @Test fun `queue conversion retains metadata and validated destination`() {
        val restored = track.toQueuedTrack().toServiceLinkOutTrack()
        assertEquals(200000,restored.durationMs)
        assertEquals("Album",restored.albumName)
        assertEquals(url,restored.qobuzMatchInput?.confirmedUrl(restored))
    }
    @Test fun `server parsing preserves proof through feed and catalog models`() {
        val data=mapOf<String,Any?>("trackId" to "id","id" to "id","trackName" to "Blue","name" to "Blue","artistName" to "Topaz Jones","albumName" to "Album","isrc" to "US1234567890","durationMs" to 200000,"qobuzURL" to url,"qobuzMatchStatus" to "matched","qobuzMatchInput" to mapOf("isrc" to proof.isrc,"name" to proof.name,"artist" to proof.artist,"albumName" to proof.albumName,"durationMs" to proof.durationMs))
        val catalog=CymbalTrack.fromMap(data)
        assertEquals(url,catalog.qobuzMatchInput?.confirmedUrl(catalog))
        val post=CymbalPost.fromCloudData(data)
        val queued=post.toQueuedTrack().toServiceLinkOutTrack()
        assertEquals(url,queued.qobuzMatchInput?.confirmedUrl(queued))
    }
    @Test fun `pending and provider failures cannot become confirmed catalog misses`() {
        val now=1000L
        assertFalse(QobuzLinks.entryFromResponse(mapOf("qobuzURL" to null,"pending" to true),now).notFound)
        assertFalse(QobuzLinks.entryFromResponse(mapOf("qobuzURL" to null,"retryAtMs" to 5000L,"expiresAtMs" to 6000L),now).notFound)
        assertFalse(QobuzLinks.entryFromResponse(mapOf("qobuzURL" to "https://evil.test/track/1"),now).notFound)
        assertTrue(QobuzLinks.entryFromResponse(mapOf("qobuzURL" to null,"expiresAtMs" to 6000L),now).notFound)
        assertEquals(url,QobuzLinks.entryFromResponse(mapOf("qobuzURL" to url,"expiresAtMs" to 6000L),now).url)
    }
}
