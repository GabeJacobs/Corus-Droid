package fm.corus.android.data.remote

import com.google.android.gms.tasks.Tasks
import com.google.firebase.functions.*
import fm.corus.android.data.model.FeedFilter
import fm.corus.android.domain.FeedNewTabPolicy
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import org.mockito.kotlin.*

class NewFeedRequestsTest {
    @Test fun `New first and later pages send release restriction spacing and live cursor`() = runTest {
        val functions = mock<FirebaseFunctions>()
        val callable = mock<HttpsCallableReference>()
        whenever(functions.getHttpsCallable("getForYouFeed")).thenReturn(callable)
        val result = mock<HttpsCallableResult> { on { getData() } doReturn mapOf(
            "posts" to emptyList<Any>(), "sessionToken" to "new-session", "hasMore" to true) }
        whenever(callable.call(any())).thenReturn(Tasks.forResult(result))
        val source = CloudFunctionsDataSource(functions, mock(), mock())
        for (page in 0..1) source.getForYouFeed("viewer", scope = "trending", pageIndex = page,
            sessionToken = if (page == 1) "new-session" else null,
            mediaType = FeedFilter.FILM.mediaType,
            newReleasesOnly = FeedNewTabPolicy.newReleasesOnly("newReleases", true, FeedFilter.FILM),
            spaceRepeatedSongs = FeedNewTabPolicy.isNew("newReleases", true))
        val params = argumentCaptor<Map<String, Any>>()
        verify(callable, times(2)).call(params.capture())
        params.allValues.forEach {
            assertEquals("trending", it["scope"])
            assertEquals("movie", it["mediaType"])
            assertEquals(true, it["newReleasesOnly"])
            assertEquals(true, it["spaceRepeatedSongs"])
        }
        assertEquals("new-session", params.allValues[1]["sessionToken"])
    }
    @Test fun `both export endpoints carry dedup only when requested`() = runTest {
        val functions = mock<FirebaseFunctions>()
        val callable = mock<HttpsCallableReference>()
        whenever(functions.getHttpsCallable("generateFeedPlaylist")).thenReturn(callable)
        val result = mock<HttpsCallableResult> { on { getData() } doReturn mapOf("playlistURI" to "spotify:playlist:test", "playlistWebURL" to "https://open.spotify.com/playlist/test", "tracks" to emptyList<Any>()) }
        whenever(callable.call(any())).thenReturn(Tasks.forResult(result))
        val source = CloudFunctionsDataSource(functions, mock(), mock())
        source.generateFeedPlaylist(true, "trending", "new-session", deduplicateSongs = true)
        source.generateFeedPlaylistTracks(true, "trending", "new-session", deduplicateSongs = true)
        source.generateFeedPlaylist(true, "trending", "old-session")
        val params = argumentCaptor<Map<String, Any>>()
        verify(callable, times(3)).call(params.capture())
        params.allValues.take(2).forEach {
            assertEquals(true, it["deduplicateSongs"])
            assertEquals(true, it["newReleasesOnly"])
            assertEquals("new-session", it["sessionToken"])
        }
        assertFalse(params.allValues[2].containsKey("deduplicateSongs"))
    }
    @Test fun `ordinary release-filtered Trending omits song spacing`() = runTest {
        val functions = mock<FirebaseFunctions>()
        val callable = mock<HttpsCallableReference>()
        whenever(functions.getHttpsCallable("getForYouFeed")).thenReturn(callable)
        val result = mock<HttpsCallableResult> { on { getData() } doReturn mapOf("posts" to emptyList<Any>()) }
        whenever(callable.call(any())).thenReturn(Tasks.forResult(result))
        CloudFunctionsDataSource(functions, mock(), mock()).getForYouFeed("viewer", newReleasesOnly = true)
        val params = argumentCaptor<Map<String, Any>>()
        verify(callable).call(params.capture())
        assertFalse(params.firstValue.containsKey("spaceRepeatedSongs"))
    }
}
