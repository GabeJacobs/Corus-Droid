package fm.corus.android.share

import com.google.android.gms.tasks.Tasks
import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.functions.HttpsCallableReference
import com.google.firebase.functions.HttpsCallableResult
import fm.corus.android.data.remote.CloudFunctionsDataSource
import fm.corus.android.data.repository.MusicSearchRepository
import fm.corus.android.data.repository.SpotifyRepository
import fm.corus.android.data.model.TrackSource
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever

class DirectMusicShareResolverTest {
    @Test
    fun `Bandcamp album rows stay songs with their original track URLs`() = runTest {
        val functions = mock<FirebaseFunctions>()
        val callable = mock<HttpsCallableReference>()
        val result = mock<HttpsCallableResult>()
        val url = "https://gregfreeman.bandcamp.com/album/all-set-the-bone"
        whenever(functions.getHttpsCallable("shareResolveBandcampLink")).thenReturn(callable)
        whenever(callable.call(any())).thenReturn(Tasks.forResult(result))
        whenever(result.getData()).thenReturn(mapOf(
            "album" to mapOf("id" to "bca:854829041", "title" to "All Set The Bone", "artistName" to "Greg Freeman", "year" to "2026"),
            "tracks" to listOf(mapOf("id" to "bc:2862039091", "name" to "Cahokia", "artistName" to "Greg Freeman",
                "source" to "bandcamp", "bandcampUrl" to "https://gregfreeman.bandcamp.com/track/cahokia")),
        ))
        val client = HttpClient(MockEngine { error("Unexpected fetch") })
        try {
            val resolver = ShareResolver(mock(), mock(), mock(), functions, client)
            val album = resolver.fetchBandcampAlbum(url)
            assertEquals("All Set The Bone", album?.title)
            assertEquals(TrackSource.BANDCAMP, album?.tracks?.first()?.preResolved?.source)
            assertEquals("https://gregfreeman.bandcamp.com/track/cahokia", album?.tracks?.first()?.preResolved?.bandcampUrl)
        } finally { client.close() }
    }

    @Test
    fun `SoundCloud resolution uses the exact URL callable instead of search`() = runTest {
        val functions = mock<FirebaseFunctions>()
        val callable = mock<HttpsCallableReference>()
        val result = mock<HttpsCallableResult>()
        val musicSearch = mock<MusicSearchRepository>()
        val track = mapOf("id" to "sc:6649309", "name" to "Magic Man - Nest", "artistName" to "coolthanks.net",
            "source" to "soundcloud", "soundcloudId" to "6649309", "soundcloudPermalinkUrl" to "https://soundcloud.com/coolthanks/nest")
        whenever(functions.getHttpsCallable("shareResolveSoundCloudLink")).thenReturn(callable)
        whenever(callable.call(any())).thenReturn(Tasks.forResult(result))
        whenever(result.getData()).thenReturn(mapOf("track" to track))
        val client = HttpClient(MockEngine { error("Share must not fetch a web page on the client") })
        try {
            val resolver = ShareResolver(mock<CloudFunctionsDataSource>(), mock<SpotifyRepository>(), musicSearch, functions, client)
            val resolved = resolver.resolveSong(SharedMusicLink.SoundCloudTrack("https://soundcloud.com/coolthanks/nest"))
            assertNotNull(resolved)
            assertEquals("sc:6649309", resolved?.id)
            verify(callable).call(mapOf("soundcloudUrl" to "https://soundcloud.com/coolthanks/nest"))
            verifyNoInteractions(musicSearch)
        } finally { client.close() }
    }

    @Test
    fun `Bandcamp direct track keeps source and link out fields`() = runTest {
        val functions = mock<FirebaseFunctions>()
        val callable = mock<HttpsCallableReference>()
        val result = mock<HttpsCallableResult>()
        val url = "https://gregfreeman.bandcamp.com/track/cahokia"
        whenever(functions.getHttpsCallable("shareResolveBandcampLink")).thenReturn(callable)
        whenever(callable.call(any())).thenReturn(Tasks.forResult(result))
        whenever(result.getData()).thenReturn(mapOf("track" to mapOf("id" to "bc:2862039091", "name" to "Cahokia",
            "artistName" to "Greg Freeman", "source" to "bandcamp", "bandcampId" to "2862039091", "bandcampUrl" to url,
            "bandcampAlbumUrl" to "https://gregfreeman.bandcamp.com/album/all-set-the-bone")))
        val client = HttpClient(MockEngine { error("Unexpected fetch") })
        try {
            val resolver = ShareResolver(mock(), mock(), mock(), functions, client)
            val parsed = SharedMusicLink.parse(url)
            assertNotNull(parsed)
            val track = resolver.resolveSong(parsed!!)
            assertEquals(TrackSource.BANDCAMP, track?.source)
            assertEquals(url, track?.bandcampUrl)
            assertEquals("2862039091", track?.bandcampId)
        } finally { client.close() }
    }
}
