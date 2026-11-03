package fm.corus.android.data.repository

import com.google.android.gms.tasks.Tasks
import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.QuerySnapshot
import com.google.firebase.firestore.SetOptions
import fm.corus.android.data.model.CymbalTrack
import fm.corus.android.data.model.MediaType
import fm.corus.android.data.model.PostDraft
import fm.corus.android.data.model.TrackSource
import fm.corus.android.data.remote.FirebaseStorageDataSource
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

/**
 * Repository behavior against a mocked Firestore: the drafts path
 * (users_v2/{uid}/postDrafts), newest-first ordering, save writing the
 * canonical shape, edit using merge (no createdAt clobber), and delete.
 */
class PostDraftRepositoryTest {

    private val uid = "user-1"

    private fun track(id: String) = CymbalTrack(
        id = id, name = "Song $id", artistName = "Artist", albumName = "Album",
        source = TrackSource.SPOTIFY,
    )

    private class Harness {
        val firestore = mock<FirebaseFirestore>()
        val users = mock<CollectionReference>()
        val userDoc = mock<DocumentReference>()
        val drafts = mock<CollectionReference>()
        val storage = mock<FirebaseStorageDataSource>()

        init {
            whenever(firestore.collection("users_v2")).thenReturn(users)
            whenever(users.document(any())).thenReturn(userDoc)
            whenever(userDoc.collection("postDrafts")).thenReturn(drafts)
        }

        fun repo() = PostDraftRepository(firestore, storage)
    }

    @Test
    fun `listDrafts returns all drafts beyond 30 in updatedAt order`() = runTest {
        val h = Harness()
        val query = mock<Query>()
        val limited = mock<Query>()
        val snap = mock<QuerySnapshot>()
        val limitedSnap = mock<QuerySnapshot>()
        val docs = (75 downTo 1).map { i ->
            mock<DocumentSnapshot> {
                on { id } doReturn "d$i"
                on { data } doReturn mapOf(
                    "mediaType" to "track",
                    "caption" to "hi",
                    "captionMode" to "text",
                    "createdAt" to i.toLong(),
                    "updatedAt" to i.toLong(),
                    "track" to mapOf("id" to "t$i", "name" to "Song $i", "artist" to "A", "album" to "Al"),
                )
            }
        }
        whenever(h.drafts.orderBy("updatedAt", Query.Direction.DESCENDING)).thenReturn(query)
        whenever(query.get()).thenReturn(Tasks.forResult(snap))
        whenever(snap.documents).thenReturn(docs)
        // Model the previous capped query, so the regression fails before the change.
        whenever(query.limit(30L)).thenReturn(limited)
        whenever(limited.get()).thenReturn(Tasks.forResult(limitedSnap))
        whenever(limitedSnap.documents).thenReturn(docs.take(30))

        val result = h.repo().listDrafts(uid)

        assertEquals((75 downTo 1).map { "d$it" }, result.map { it.id })
        assertEquals(MediaType.TRACK, result[0].mediaType)
        assertEquals("Song 75", result[0].track?.name)
        assertEquals(75L, result[0].updatedAt)
    }

    @Test
    fun `saveDraft creates a new doc with the canonical shape and numeric millis`() = runTest {
        val h = Harness()
        val newDoc = mock<DocumentReference> { on { id } doReturn "new-id" }
        whenever(h.drafts.document()).thenReturn(newDoc)          // id allocation
        whenever(h.drafts.document("new-id")).thenReturn(newDoc)  // write target
        whenever(newDoc.set(any())).thenReturn(Tasks.forResult(null))
        // Support the old retention query to detect unwanted pruning.
        val query = mock<Query>()
        val snap = mock<QuerySnapshot>()
        whenever(h.drafts.orderBy("updatedAt", Query.Direction.DESCENDING)).thenReturn(query)
        whenever(query.get()).thenReturn(Tasks.forResult(snap))
        whenever(snap.documents).thenReturn(emptyList())

        val input = PostDraft(
            id = "", mediaType = MediaType.TRACK, caption = "hello", captionMode = "text",
            track = track("t1"), createdAt = 0L, updatedAt = 0L,
        )
        val before = System.currentTimeMillis()
        val saved = h.repo().saveDraft(uid, input)
        val after = System.currentTimeMillis()

        assertEquals("new-id", saved.id)
        assertTrue(saved.createdAt in before..after)
        assertTrue(saved.updatedAt in before..after)

        // Assert the written map: canonical field names + numeric timestamps.
        val captor = argumentCaptorMap()
        verify(newDoc).set(captor.capture())
        val written = captor.firstValue
        assertEquals("track", written["mediaType"])
        assertEquals("hello", written["caption"])
        assertTrue(written["createdAt"] is Long)
        assertTrue(written["updatedAt"] is Long)
        @Suppress("UNCHECKED_CAST")
        val trackMap = written["track"] as Map<String, Any?>
        assertEquals("t1", trackMap["id"])
        assertEquals("Artist", trackMap["artist"]) // WEB field name
    }

    @Test
    fun `saveDraft on an existing id merges and preserves the original createdAt`() = runTest {
        val h = Harness()
        val existingDoc = mock<DocumentReference> { on { id } doReturn "keep-id" }
        whenever(h.drafts.document("keep-id")).thenReturn(existingDoc)
        whenever(existingDoc.set(any(), any<SetOptions>())).thenReturn(Tasks.forResult(null))

        val input = PostDraft(
            id = "keep-id", mediaType = MediaType.TRACK, caption = "edited", captionMode = "text",
            track = track("t1"), createdAt = 0L, updatedAt = 0L,
        )
        val originalCreatedAt = 1_650_000_000_000L
        val saved = h.repo().saveDraft(uid, input, existingId = "keep-id", existingCreatedAt = originalCreatedAt)

        // createdAt is preserved from the original; updatedAt is bumped to now.
        assertEquals(originalCreatedAt, saved.createdAt)
        assertTrue(saved.updatedAt > originalCreatedAt)

        // An edit uses a merge write and does not scan other drafts.
        verify(existingDoc).set(any(), any<SetOptions>())
        verify(h.drafts, never()).orderBy(any<String>(), any())

        val captor = argumentCaptorMap()
        verify(existingDoc).set(captor.capture(), any<SetOptions>())
        assertEquals(originalCreatedAt, captor.firstValue["createdAt"])
    }

    @Test
    fun `saveDraft preserves all older drafts beyond 30 on a fresh save`() = runTest {
        val h = Harness()
        val newDoc = mock<DocumentReference> { on { id } doReturn "n" }
        whenever(h.drafts.document()).thenReturn(newDoc)
        whenever(h.drafts.document("n")).thenReturn(newDoc)
        whenever(newDoc.set(any())).thenReturn(Tasks.forResult(null))

        // All 75 existing drafts must survive saving another one.
        val docs = (0 until 75).map {
            val ref = mock<DocumentReference>()
            whenever(ref.delete()).thenReturn(Tasks.forResult(null))
            mock<DocumentSnapshot> { on { reference } doReturn ref }
        }
        val query = mock<Query>()
        val snap = mock<QuerySnapshot>()
        whenever(h.drafts.orderBy("updatedAt", Query.Direction.DESCENDING)).thenReturn(query)
        whenever(query.get()).thenReturn(Tasks.forResult(snap))
        whenever(snap.documents).thenReturn(docs)

        h.repo().saveDraft(
            uid,
            PostDraft(id = "", mediaType = MediaType.TRACK, caption = "", captionMode = "text",
                track = track("t1"), createdAt = 0L, updatedAt = 0L),
        )

        for (draft in docs) verify(draft.reference, never()).delete()
        verify(h.drafts, never()).orderBy(any<String>(), any())
    }

    @Test
    fun `deleteDraft removes the doc`() = runTest {
        val h = Harness()
        val doc = mock<DocumentReference>()
        whenever(h.drafts.document("d9")).thenReturn(doc)
        whenever(doc.delete()).thenReturn(Tasks.forResult(null))

        h.repo().deleteDraft(uid, "d9")

        verify(doc).delete()
    }

    @Test
    fun `deleteDraft is a no-op for blank ids`() = runTest {
        val h = Harness()
        h.repo().deleteDraft(uid, "")
        verify(h.drafts, never()).document(any())
    }

    // Small typed captor helper (Map<String, Any?>) to avoid nullability noise.
    private fun argumentCaptorMap() =
        org.mockito.kotlin.argumentCaptor<Map<String, Any?>>()
}
