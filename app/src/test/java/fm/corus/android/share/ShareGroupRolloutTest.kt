package fm.corus.android.share

import android.app.Application
import com.google.android.gms.tasks.Tasks
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.functions.FirebaseFunctionsException
import com.google.firebase.functions.HttpsCallableReference
import com.google.firebase.functions.HttpsCallableResult
import fm.corus.android.data.remote.CloudFunctionsDataSource
import fm.corus.android.service.RemoteConfigService
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class ShareGroupRolloutTest {
    private fun missing(code: FirebaseFunctionsException.Code): FirebaseFunctionsException {
        val error = mock<FirebaseFunctionsException>()
        whenever(error.code).thenReturn(code)
        return error
    }

    @Test fun oldDeploymentFallsBackAndDropsMessageOnlyGroups() = runTest {
        val newCall = mock<HttpsCallableReference>()
        val oldCall = mock<HttpsCallableReference>()
        val notFound = missing(FirebaseFunctionsException.Code.NOT_FOUND)
        whenever(newCall.call(any())).thenReturn(Tasks.forException(notFound))
        val response = mock<HttpsCallableResult> {
            on { getData() } doReturn mapOf("threads" to listOf(
                mapOf("id" to "member", "type" to "group", "name" to "Friends", "memberIds" to listOf("isa"),
                    "members" to mapOf("isa" to mapOf("username" to "isa", "displayName" to "Isabela"))),
                mapOf("id" to "body", "type" to "group", "name" to "Other", "lastMessageText" to "isa"),
            ))
        }
        whenever(oldCall.call(any())).thenReturn(Tasks.forResult(response))
        val functions = mock<FirebaseFunctions> {
            on { getHttpsCallable("searchShareGroups") } doReturn newCall
            on { getHttpsCallable("searchThreads") } doReturn oldCall
        }
        val user = mock<FirebaseUser> { on { uid } doReturn "self" }
        val auth = mock<FirebaseAuth> { on { currentUser } doReturn user }
        val source = CloudFunctionsDataSource(functions, mock<RemoteConfigService>(), auth)
        assertEquals(listOf("member"), source.searchShareGroups("isa").map { it.id })
        verify(functions).getHttpsCallable("searchThreads")
    }

    @Test fun authorizationFailureNeverUsesFallback() = runTest {
        val call = mock<HttpsCallableReference>()
        val denied = missing(FirebaseFunctionsException.Code.PERMISSION_DENIED)
        whenever(call.call(any())).thenReturn(Tasks.forException(denied))
        val functions = mock<FirebaseFunctions> { on { getHttpsCallable("searchShareGroups") } doReturn call }
        val source = CloudFunctionsDataSource(functions, mock(), mock())
        try {
            source.searchShareGroups("isa")
            fail("Permission denied must propagate")
        } catch (error: FirebaseFunctionsException) {
            assertEquals(FirebaseFunctionsException.Code.PERMISSION_DENIED, error.code)
        }
        verify(functions, never()).getHttpsCallable("searchThreads")
    }
}
