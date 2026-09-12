package fm.corus.android.data.remote

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngineConfig
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.StandardTestDispatcher
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.*
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class BandcampPlaybackServiceTest {
    private fun TestScope.client(handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData) =
        HttpClient(MockEngine(MockEngineConfig().apply {
            dispatcher = StandardTestDispatcher(testScheduler)
            addHandler(handler)
        }))

    private val page = "https://ingridsuperstar.bandcamp.com/track/duet"
    private val html = """<div data-tralbum="{&quot;trackinfo&quot;:[{&quot;streaming&quot;:1,&quot;file&quot;:{&quot;mp3-128&quot;:&quot;//t4.bcbits.com/stream/song?x=1&amp;y=2&quot;}}]}">"""

    @Test fun `parses stream and HTML entities`() {
        assertEquals("https://t4.bcbits.com/stream/song?x=1&y=2", BandcampPlaybackService.streamUrl(html))
        assertNull(BandcampPlaybackService.streamUrl(html.replace("streaming&quot;:1", "streaming&quot;:0")))
        assertNull(BandcampPlaybackService.streamUrl("<html>just a moment</html>"))
        assertNull(BandcampPlaybackService.streamUrl("data-tralbum=\"broken\""))
    }

    @Test fun `direct page success never calls backend`() = runTest {
        client { respond(html) }.use { client ->
            val result = BandcampPlaybackService(client).resolve(page, "1", "duet", "frankie cosmos") { error("backend called") }
            assertEquals(page, result?.second)
            assertTrue(result!!.first.startsWith("https://t4.bcbits.com/"))
        }
    }

    @Test fun `legacy ID recovers exact page on phone before playback`() = runTest {
        var calls = 0
        client { request ->
            calls++
            if (request.url.host == "bandcamp.com") respond("""{"auto":{"results":[{"id":2,"item_url_path":"https://wrong.bandcamp.com/track/duet"},{"id":1,"item_url_path":"$page"}]}}""")
            else { assertEquals(page, request.url.toString()); respond(html) }
        }.use { client ->
            assertEquals(page, BandcampPlaybackService(client).resolve(null, "1", "duet", "frankie cosmos") { error("backend called") }?.second)
            assertEquals(2, calls)
        }
    }

    @Test fun `challenge invokes backend with known page`() = runTest {
        client { respond("blocked", HttpStatusCode.Forbidden) }.use { client ->
            val result = BandcampPlaybackService(client).resolve(page, "1", "duet", "artist") {
                assertEquals(page, it); "https://t4.bcbits.com/fallback" to it
            }
            assertEquals("https://t4.bcbits.com/fallback", result?.first)
        }
    }

    @Test fun `slow direct request times out and falls back`() = runTest {
        client { delay(9_000); respond(html) }.use { client ->
            assertEquals("fallback", BandcampPlaybackService(client).resolve(page, "1", "duet", "artist") { "fallback" to it }?.first)
        }
    }

    @Test fun `cancellation does not start fallback`() = runTest {
        var fallback = false
        client { awaitCancellation() }.use { client ->
            val job = launch {
                BandcampPlaybackService(client).resolve(page, "1", "duet", "artist") { fallback = true; null }
            }
            yield()
            job.cancelAndJoin()
            assertFalse(fallback)
        }
    }

    @Test fun `failed recovery still tries backend and backend timeout is bounded`() = runTest {
        client { respond("{}") }.use { client ->
            var attempted = false
            val result = BandcampPlaybackService(client).resolve(null, "1", "duet", "artist") {
                assertNull(it); attempted = true; delay(11_000); "late" to null
            }
            assertTrue(attempted)
            assertNull(result)
        }
    }

    @Test fun `only genuine Bandcamp track pages accepted`() {
        assertNull(BandcampPlaybackService.validPage("https://artist.bandcamp.com.evil.test/track/x"))
        assertNull(BandcampPlaybackService.validPage("http://artist.bandcamp.com/track/x"))
        assertEquals(page, BandcampPlaybackService.validPage(page))
    }
}
