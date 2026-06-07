package fm.corus.android.data.repository

import android.app.Activity
import android.content.Context
import com.google.android.gms.tasks.Tasks
import com.google.firebase.auth.AdditionalUserInfo
import com.google.firebase.auth.AuthResult
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.messaging.FirebaseMessaging
import fm.corus.android.data.local.OnboardingLocalStore
import fm.corus.android.data.remote.CloudFunctionsDataSource
import fm.corus.android.data.remote.FirebaseStorageDataSource
import fm.corus.android.data.remote.FirestoreDataSource
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

/**
 * Resume-from-pending path uses [FirebaseAuth.pendingAuthResult] so the unit
 * test doesn't need to mock the static [com.google.firebase.auth.OAuthProvider]
 * builder — that path runs only when no resume is available.
 */
class AuthRepositoryAppleTest {

    private val context = mock<Context>()
    private val auth = mock<FirebaseAuth>()
    private val firestoreDataSource = mock<FirestoreDataSource>()
    private val storageDataSource = mock<FirebaseStorageDataSource>()
    private val messaging = mock<FirebaseMessaging>()
    private val cloudFunctions = mock<CloudFunctionsDataSource>()
    private val onboardingLocalStore = mock<OnboardingLocalStore>()
    private val activity = mock<Activity>()

    private fun newRepo() = AuthRepository(
        context,
        auth,
        firestoreDataSource,
        storageDataSource,
        messaging,
        cloudFunctions,
        onboardingLocalStore,
    )

    @Test
    fun `signInWithApple resumes pending auth result for new user`() = runTest {
        val user = mock<FirebaseUser>()
        val additionalUserInfo = mock<AdditionalUserInfo> { on { isNewUser } doReturn true }
        val authResult = mock<AuthResult> {
            on { it.user } doReturn user
            on { it.additionalUserInfo } doReturn additionalUserInfo
        }
        whenever(auth.pendingAuthResult).thenReturn(Tasks.forResult(authResult))

        val isNewUser = newRepo().signInWithApple(activity)

        assertEquals(true, isNewUser)
        // Resuming uses the pending task — startActivityForSignInWithProvider must NOT be called.
        verify(auth, org.mockito.kotlin.never())
            .startActivityForSignInWithProvider(eq(activity), org.mockito.kotlin.any())
    }

    @Test
    fun `signInWithApple resumes pending auth result for returning user`() = runTest {
        val user = mock<FirebaseUser>()
        val additionalUserInfo = mock<AdditionalUserInfo> { on { isNewUser } doReturn false }
        val authResult = mock<AuthResult> {
            on { it.user } doReturn user
            on { it.additionalUserInfo } doReturn additionalUserInfo
        }
        whenever(auth.pendingAuthResult).thenReturn(Tasks.forResult(authResult))

        val repo = newRepo()
        val isNewUser = repo.signInWithApple(activity)

        assertEquals(false, isNewUser)
        assertSame(user, repo.currentUser.value)
    }

    @Test
    fun `signInWithApple defaults to not-new-user when additionalUserInfo is missing`() = runTest {
        val user = mock<FirebaseUser>()
        val authResult = mock<AuthResult> {
            on { it.user } doReturn user
            on { it.additionalUserInfo } doReturn null
        }
        whenever(auth.pendingAuthResult).thenReturn(Tasks.forResult(authResult))

        val isNewUser = newRepo().signInWithApple(activity)

        assertEquals(false, isNewUser)
    }
}
