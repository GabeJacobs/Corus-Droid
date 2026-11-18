package fm.corus.android.ui.screens.live

import android.app.Application
import com.google.android.gms.tasks.Tasks
import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.functions.HttpsCallableReference
import com.google.firebase.functions.HttpsCallableResult
import fm.corus.android.data.remote.CloudFunctionsDataSource
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class LiveSpotifyLookupTest {
    @Test fun crossCatalogLookupCanMatchAnAlternatePressingUsingAlbumAndDuration() = runTest {
        val album = "The Bootleg Series Vol. 4: Live 1966"
        val duration = 532_467
        val uri = "spotify:track:0123456789abcdefghijkl"
        val track = requireNotNull(LiveTrack.parse(mapOf(
            "id" to "am:123", "source" to "applemusic", "title" to "Mr. Tambourine Man (Live)",
            "artist" to "Bob Dylan", "album" to album, "durationMs" to duration,
        )))
        val call = mock<HttpsCallableReference>()
        whenever(call.call(any())).thenAnswer { invocation ->
            val request = invocation.getArgument<Map<String, Any>>(0)
            val data = if (request["albumName"] == album && request["durationMs"] == duration)
                mapOf("found" to true, "spotifyURI" to uri)
            else mapOf("found" to false)
            Tasks.forResult(mock<HttpsCallableResult> { on { getData() } doReturn data })
        }
        val functions = mock<FirebaseFunctions> { on { getHttpsCallable("spotifyTrackLookup") } doReturn call }
        val repository = LiveRepository(
            mock(), functions, mock(), CloudFunctionsDataSource(functions, mock(), mock()),
            mock(), mock(), mock(), mock(), mock(), mock(), mock(),
        )

        assertEquals(uri, repository.spotifyUri(track))
    }
}
