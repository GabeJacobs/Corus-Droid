package fm.corus.android.data.remote

import com.google.android.gms.tasks.Tasks
import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.functions.HttpsCallableReference
import com.google.firebase.functions.HttpsCallableResult
import fm.corus.android.data.model.MediaType
import fm.corus.android.domain.ForYouTuningMode
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import org.mockito.kotlin.*

class ForYouPrototypeRequestTest {
    @Test fun `prototype requests preserve filters and cursor without sending released seen IDs`() = runTest {
        for (mode in ForYouTuningMode.entries) {
            val functions = mock<FirebaseFunctions>()
            val callable = mock<HttpsCallableReference>()
            whenever(functions.getHttpsCallable(mode.callableName)).thenReturn(callable)
            val result = mock<HttpsCallableResult> {
                on { getData() } doReturn mapOf("posts" to emptyList<Any>(), "sessionToken" to "prototype-session", "hasMore" to true)
            }
            whenever(callable.call(any())).thenReturn(Tasks.forResult(result))
            val source = CloudFunctionsDataSource(functions, mock(), mock())
            val page = source.getForYouFeed("gabe", pageSize = 7, sessionToken = "previous-session", pageIndex = 2,
                seenPostIds = listOf("released-seen"), mediaType = MediaType.TRACK, newReleasesOnly = true,
                scope = "tasteMatches", isRefresh = true, energyLevel = "high", prototypeMode = mode,
                viewedPostIds = (0 until 510).map { "viewed-$it" })
            val params = argumentCaptor<Map<String, Any>>()
            verify(callable).call(params.capture())
            assertEquals(mode.value, params.firstValue["tuningMode"])
            assertEquals("tasteMatches", params.firstValue["scope"])
            assertEquals("previous-session", params.firstValue["sessionToken"])
            assertEquals(2, params.firstValue["pageIndex"])
            assertEquals(MediaType.TRACK.value, params.firstValue["mediaType"])
            assertEquals(true, params.firstValue["newReleasesOnly"])
            assertEquals("high", params.firstValue["energyLevel"])
            assertFalse(params.firstValue.containsKey("seenPostIds"))
            assertEquals((10 until 510).map { "viewed-$it" }, params.firstValue["viewedPostIds"])
            assertEquals("prototype-session", page.sessionToken)
            assertTrue(page.hasMore)
        }
    }
}
