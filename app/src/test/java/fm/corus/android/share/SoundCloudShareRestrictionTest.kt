package fm.corus.android.share

import com.google.android.gms.tasks.Tasks
import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.functions.HttpsCallableReference
import com.google.firebase.functions.HttpsCallableResult
import fm.corus.android.data.repository.AuthRepository
import fm.corus.android.data.repository.PostRepository
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class SoundCloudShareRestrictionTest {
    @Test fun restrictionShowsExplanationWhileUnknownFailureKeepsRetryAndNeitherPosts() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val functions = mock<FirebaseFunctions>()
        val callable = mock<HttpsCallableReference>()
        val result = mock<HttpsCallableResult>()
        whenever(functions.getHttpsCallable("shareResolveSoundCloudLink")).thenReturn(callable)
        whenever(callable.call(any())).thenReturn(Tasks.forResult(result))
        whenever(result.getData()).thenReturn(mapOf("track" to null, "unavailableReason" to "soundcloud_restricted"))
        val client = HttpClient(MockEngine { error("Unexpected fetch") })
        val posts = mock<PostRepository>()
        val auth = mock<AuthRepository>()
        whenever(auth.currentUserId).thenReturn("fixture-user")
        try {
            val resolver = ShareResolver(mock(), mock(), mock(), functions, client)
            val model = ShareComposerViewModel(resolver, posts, auth, mock(), mock(), mock(), mock())
            model.start("https://on.soundcloud.com/xuu6Uvu4rxPU1zXb0D")
            advanceUntilIdle()
            assertEquals(ShareComposerViewModel.Phase.Blocked(ShareComposerViewModel.BlockedReason.SOUNDCLOUD_RESTRICTED), model.phase.value)
            assertFalse(ShareComposerViewModel.BlockedReason.SOUNDCLOUD_RESTRICTED.canRetry)
            whenever(result.getData()).thenReturn(mapOf("track" to null))
            model.retry()
            advanceUntilIdle()
            assertEquals(ShareComposerViewModel.Phase.Blocked(ShareComposerViewModel.BlockedReason.SONG_UNAVAILABLE), model.phase.value)
            assertTrue(ShareComposerViewModel.BlockedReason.SONG_UNAVAILABLE.canRetry)
            verifyNoInteractions(posts)
        } finally {
            client.close()
            Dispatchers.resetMain()
        }
    }
}
