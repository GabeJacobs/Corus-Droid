package fm.corus.android.ui.screens.profile

import com.google.android.gms.tasks.Tasks
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.functions.HttpsCallableReference
import com.google.firebase.functions.HttpsCallableResult
import fm.corus.android.service.RemoteConfigService
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import org.mockito.kotlin.*

class TrophyCaseRefreshTest {
    @Test fun `reopening after earning trophies fetches a fresh first page`() = runTest {
        val user = mock<FirebaseUser> { on { uid } doReturn "FUQZIrZR08T2Ux2vYpPzWx7B1rv1" }
        val auth = mock<FirebaseAuth> { on { currentUser } doReturn user }
        val flags = mock<RemoteConfigService> { on { trophyCaseDisabled } doReturn false }
        val empty = mock<HttpsCallableResult> { on { getData() } doReturn mapOf("total" to 0, "items" to emptyList<Any>()) }
        val earned = mock<HttpsCallableResult> { on { getData() } doReturn mapOf("total" to 2, "items" to listOf(mapOf("id" to "first"), mapOf("id" to "second"))) }
        val callable = mock<HttpsCallableReference>()
        whenever(callable.call(any())).thenReturn(Tasks.forResult(empty), Tasks.forResult(earned))
        val functions = mock<FirebaseFunctions> { on { getHttpsCallable("getProfileTrophies") } doReturn callable }
        val model = TrophyCaseViewModel(auth, flags, functions, mock(), mock())
        assertEquals(0, model.page("owner", "track")["total"])
        assertEquals("Reopening must not reuse the previously empty five-minute cache", 2, model.page("owner", "track")["total"])
        verify(callable, times(2)).call(any())
    }
}
