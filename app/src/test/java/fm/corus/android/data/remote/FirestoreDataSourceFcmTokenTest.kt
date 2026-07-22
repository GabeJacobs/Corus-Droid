package fm.corus.android.data.remote

import com.google.android.gms.tasks.Tasks
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import fm.corus.android.service.RemoteConfigService
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class FirestoreDataSourceFcmTokenTest {

    private val userDoc = mock<DocumentReference>()

    private fun dataSource(): FirestoreDataSource {
        val firestore = mock<FirebaseFirestore>()
        val usersCollection = mock<CollectionReference>()
        whenever(firestore.collection("users_v2")).thenReturn(usersCollection)
        whenever(usersCollection.document("user-1")).thenReturn(userDoc)
        whenever(userDoc.update(any<Map<String, Any>>())).thenReturn(Tasks.forResult(null))
        return FirestoreDataSource(
            firestore = firestore,
            remoteConfigService = mock<RemoteConfigService>(),
            firebaseAuth = mock<FirebaseAuth>(),
        )
    }

    private fun capturedUpdate(): Map<String, Any> {
        val captor = argumentCaptor<Map<String, Any>>()
        verify(userDoc).update(captor.capture())
        return captor.firstValue
    }

    @Test
    fun `updateFCMToken stamps the registration time alongside the token`() = runTest {
        dataSource().updateFCMToken("user-1", "device-token")

        val data = capturedUpdate()
        assertEquals(setOf("fcmToken", "fcmTokenUpdatedAt"), data.keys)
        assertEquals("device-token", data["fcmToken"])
        assertEquals(FieldValue.serverTimestamp(), data["fcmTokenUpdatedAt"])
    }

    @Test
    fun `removeFCMToken clears the registration time alongside the token`() = runTest {
        dataSource().removeFCMToken("user-1")

        val data = capturedUpdate()
        assertEquals(setOf("fcmToken", "fcmTokenUpdatedAt"), data.keys)
        assertEquals(FieldValue.delete(), data["fcmToken"])
        assertEquals(FieldValue.delete(), data["fcmTokenUpdatedAt"])
    }
}
