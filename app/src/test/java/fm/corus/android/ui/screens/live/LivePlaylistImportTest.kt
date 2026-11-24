package fm.corus.android.ui.screens.live

import fm.corus.android.data.model.TrackSource
import fm.corus.android.data.remote.CloudFunctionsDataSource
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpHeaders
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class LivePlaylistImportTest {
    @Test
    fun `share links identify the provider and ignore tracking parameters`() {
        assertEquals(
            LivePlaylistLink.AppleMusic("pl.u-abc123", "gb"),
            LivePlaylistLink.parse(" https://music.apple.com/gb/playlist/my-mix/pl.u-abc123?ls=1 "),
        )
        assertEquals(
            LivePlaylistLink.Spotify("2CKnioFobYQRK3EEMf9Jr6"),
            LivePlaylistLink.parse("https://open.spotify.com/intl-pt/playlist/2CKnioFobYQRK3EEMf9Jr6?si=abc"),
        )
        assertEquals(LivePlaylistLink.AppleMusic("1234", "us", true),
            LivePlaylistLink.parse("https://music.apple.com/us/album/my-mix/1234?ls=1"))
        assertEquals(LivePlaylistLink.Spotify("55RsHwEbtpdYtFqwdNdKki", true),
            LivePlaylistLink.parse("https://open.spotify.com/album/55RsHwEbtpdYtFqwdNdKki?si=abc"))
        assertNull(LivePlaylistLink.parse("https://music.apple.com/us/album/my-mix/pl.u-abc"))
        assertNull(LivePlaylistLink.parse("https://music.apple.com/us/playlist/my-mix/p.private"))
        assertNull(LivePlaylistLink.parse("https://music.apple.com.example/gb/playlist/my-mix/pl.u-abc123"))
    }

    @Test
    fun `catalog import reads all pages in order and only returns queueable songs`() = runTest {
        for (isAlbum in listOf(false, true)) {
        val cloud = mock<CloudFunctionsDataSource>()
        whenever(cloud.getAppleMusicDeveloperToken())
            .thenReturn(CloudFunctionsDataSource.AppleMusicDeveloperToken("test-token", Long.MAX_VALUE))
        val id = if (isAlbum) "123456" else "pl.u-abc123"
        val path = "/v1/catalog/gb/${if (isAlbum) "albums" else "playlists"}/$id"
        val requests = mutableListOf<String>()
        val engine = MockEngine { request ->
            assertEquals("api.music.apple.com", request.url.host)
            assertEquals("Bearer test-token", request.headers[HttpHeaders.Authorization])
            val route = request.url.toString().removePrefix("https://api.music.apple.com")
            requests += route
            val body = when (route) {
                "$path?include=tracks" -> """
                    {"data":[{"attributes":{"name":"My Mix"},"relationships":{"tracks":{
                        "next":"$path/tracks?offset=3",
                        "data":[
                            {"type":"songs","id":"123","attributes":{"name":"First Song","artistName":"Artist",
                                "albumName":"Album","durationInMillis":150000,"isrc":"USABC1234567",
                                "artwork":{"url":"https://example.com/{w}x{h}.jpg"},
                                "previews":[{"url":"https://example.com/preview.m4a"}]}},
                            {"type":"music-videos","id":"122","attributes":{"name":"Video","durationInMillis":99000}},
                            {"type":"songs","id":"124","attributes":{"name":"Silent","durationInMillis":0}}
                        ]}}}]}
                """.trimIndent()
                "$path/tracks?offset=3" -> """
                    {"data":[{"type":"songs","id":"125","attributes":{"name":"Second Song","durationInMillis":1000}}]}
                """.trimIndent()
                else -> error("Unexpected catalog request: $route")
            }
            respond(body, headers = headersOf(HttpHeaders.ContentType, "application/json"))
        }
        HttpClient(engine) {
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
        }.use { client ->
            val (title, tracks) = LiveAppleMusicPlaylists(cloud, client)
                .load(LivePlaylistLink.AppleMusic(id, "gb", isAlbum))
            assertEquals("My Mix", title)
            assertEquals(listOf("am:123", "am:125"), tracks.map { it.id })
            val song = tracks.first().song
            assertEquals(TrackSource.APPLEMUSIC, song.source)
            assertEquals("123", song.appleMusicId)
            assertEquals("gb", song.appleMusicStorefront)
            assertEquals("USABC1234567", song.isrc)
            assertEquals("https://example.com/600x600.jpg", song.albumArtURL)
            assertEquals("https://example.com/preview.m4a", song.previewUrl)
        }
        assertEquals(listOf("$path?include=tracks", "$path/tracks?offset=3"), requests)
        }
    }
}
