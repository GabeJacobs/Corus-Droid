package fm.corus.android.data.remote

import com.google.android.gms.tasks.Tasks
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.FirebaseFirestore
import fm.corus.android.service.RemoteConfigService
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

/**
 * Regression: Firestore `createPost` must NOT bump `repostCount` on the
 * original post. The `onPostCreated` cloud function already increments it
 * server-side, so a client-side `update("repostCount", ...)` here was
 * double-counting (+2 per repost) — same bug iOS fixed in Cymbal-Remake.
 */
class FirestoreDataSourceRepostTest {

    @Test
    fun `createPost with repostedFromPostId does not bump original post repostCount`() = runTest {
        val firestore = mock<FirebaseFirestore>()
        val postsCollection = mock<CollectionReference>()
        val newPostDoc = mock<DocumentReference> { on { id } doReturn "new-post-id" }
        val originalPostDoc = mock<DocumentReference>()

        whenever(firestore.collection("posts")).thenReturn(postsCollection)
        whenever(postsCollection.document()).thenReturn(newPostDoc)
        whenever(postsCollection.document("orig-post-id")).thenReturn(originalPostDoc)
        whenever(newPostDoc.set(any<Map<String, Any>>())).thenReturn(Tasks.forResult(null))

        val ds = FirestoreDataSource(
            firestore = firestore,
            remoteConfigService = mock<RemoteConfigService>(),
            firebaseAuth = mock<FirebaseAuth>(),
        )
        ds.createPost(
            userId = "user-1",
            data = mapOf(
                "trackId" to "t1",
                "repostedFromPostId" to "orig-post-id",
            ),
        )

        // The original post must not be touched by the client — only the
        // `onPostCreated` cloud function should bump its repostCount.
        verify(originalPostDoc, never()).update(any<String>(), any())
    }
}
