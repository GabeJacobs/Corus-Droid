package fm.corus.android.data.remote

import com.google.android.gms.tasks.Tasks
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.FirebaseFirestore
import fm.corus.android.service.RemoteConfigService
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

/**
 * Regression: phone numbers must be written to the dedicated `phoneNumber`
 * field, not overloaded into `email`. The contact-sync onboarding screen
 * queries by phoneNumber, so this is what unbreaks Android's "friends from
 * your contacts" list. See ~/.claude/plans/starry-zooming-panda.md.
 */
class FirestoreDataSourceCreateUserProfileTest {

    @Test
    fun `createUserProfile writes phoneNumber field separately from email`() = runTest {
        val firestore = mock<FirebaseFirestore>()
        val usersCollection = mock<CollectionReference>()
        val userDoc = mock<DocumentReference>()

        whenever(firestore.collection("users_v2")).thenReturn(usersCollection)
        whenever(usersCollection.document("user-1")).thenReturn(userDoc)
        whenever(userDoc.set(any<Map<String, Any>>())).thenReturn(Tasks.forResult(null))

        val ds = FirestoreDataSource(
            firestore = firestore,
            remoteConfigService = mock<RemoteConfigService>(),
            firebaseAuth = mock<FirebaseAuth>(),
        )

        ds.createUserProfile(
            uid = "user-1",
            username = "alice",
            displayName = "Alice",
            email = "",
            phoneNumber = "+14155551234",
        )

        val captor = argumentCaptor<Map<String, Any>>()
        verify(userDoc).set(captor.capture())
        val data = captor.firstValue

        assertEquals("", data["email"])
        assertEquals("+14155551234", data["phoneNumber"])
    }
}
