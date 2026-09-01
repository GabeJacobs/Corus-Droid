package fm.corus.android.data.repository

import com.google.firebase.firestore.FirebaseFirestore
import fm.corus.android.data.remote.CloudFunctionsDataSource
import fm.corus.android.data.remote.FirebaseStorageDataSource
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.kotlin.mock

/**
 * Empty cache is "never fetched," not "this thread is empty." Publishing that
 * snapshot dropped the DM spinner and left a blank conversation when the
 * server fetch then hung or failed (cold open / emulator).
 */
class MessageSnapshotPublishTest {

    private val repo = MessageRepository(
        mock<CloudFunctionsDataSource>(),
        mock<FirebaseStorageDataSource>(),
        mock<FirebaseFirestore>(),
    )

    @Test
    fun `empty cache miss is not a finished load`() {
        assertFalse(repo.shouldPublishMessagesSnapshot(fromCache = true, isEmpty = true))
    }

    @Test
    fun `cached messages can paint immediately`() {
        assertTrue(repo.shouldPublishMessagesSnapshot(fromCache = true, isEmpty = false))
    }

    @Test
    fun `server snapshot always publishes even when empty`() {
        assertTrue(repo.shouldPublishMessagesSnapshot(fromCache = false, isEmpty = true))
        assertTrue(repo.shouldPublishMessagesSnapshot(fromCache = false, isEmpty = false))
    }
}
