package fm.corus.android.data.repository

import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import fm.corus.android.data.remote.CloudFunctionsDataSource
import fm.corus.android.data.remote.FirebaseStorageDataSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.kotlin.mock
import java.util.Date

/**
 * The inbox row itself carries whether the caller has the correspondent blocked
 * — it is stamped on the row for as long as the block exists, so the listener,
 * the callables and the unread badge all answer from one fact. Reading it off
 * the raw document is where that contract enters this client.
 */
class MessageRepositoryThreadSummaryTest {

    private val repo = MessageRepository(
        mock<CloudFunctionsDataSource>(),
        mock<FirebaseStorageDataSource>(),
        mock<FirebaseFirestore>(),
    )

    private fun doc(vararg extra: Pair<String, Any?>) = mapOf(
        "otherUserId" to "u1",
        "lastMessageText" to "hey",
        "lastMessageFromUserId" to "u1",
        *extra,
    )

    @Test
    fun `a stamped row reports the block`() {
        assertTrue(repo.parseThreadSummary("t1", doc("blocked" to true)).blocked)
    }

    @Test
    fun `an unstamped row and a cleared one are both showable`() {
        assertFalse(repo.parseThreadSummary("t1", doc()).blocked)
        assertFalse(repo.parseThreadSummary("t1", doc("blocked" to false)).blocked)
    }

    @Test
    fun `when the row last changed is kept apart from when it was last messaged`() {
        // The inbox is ordered and windowed by `updatedAt`, which reading a
        // conversation bumps on its own. Folding the two together is what let a
        // just-read old thread pass for a recent one.
        val row = repo.parseThreadSummary(
            "t1",
            doc("lastMessageAt" to Timestamp(Date(100)), "updatedAt" to Timestamp(Date(20_000))),
        )

        assertEquals(Date(20_000), row.updatedAt)
        assertEquals(Date(100), row.lastMessageAt)
    }

    @Test
    fun `a row with no recorded change time does not invent one`() {
        assertNull(repo.parseThreadSummary("t1", doc()).updatedAt)
    }
}
