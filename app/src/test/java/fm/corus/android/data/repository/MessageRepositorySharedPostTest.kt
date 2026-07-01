package fm.corus.android.data.repository

import com.google.firebase.firestore.FirebaseFirestore
import fm.corus.android.data.remote.CloudFunctionsDataSource
import fm.corus.android.data.remote.FirebaseStorageDataSource
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verifyBlocking

@OptIn(ExperimentalCoroutinesApi::class)
class MessageRepositorySharedPostTest {

    private val cloudFunctions = mock<CloudFunctionsDataSource>()
    private val storage = mock<FirebaseStorageDataSource>()
    private val firestore = mock<FirebaseFirestore>()

    private val repo = MessageRepository(cloudFunctions, storage, firestore)

    // Regression: sharing a post to a DM must send a `sharedPost` message
    // carrying the post id, NOT a `sharedTrack` message. The old code sent
    // sharedTrack and dropped post.id, so the recipient's tap could only open
    // the song page instead of the post. Mirrors iOS sendSharedPostMessage.
    @Test
    fun sendSharedPostMessage_sendsSharedPostTypeWithPostId() = runTest {
        repo.sendSharedPostMessage(
            threadId = "thread1",
            fromUserId = "sender1",
            postId = "post1",
            text = "check this out",
        )

        // No argument matchers → Mockito matches by equality, and the unspecified
        // params fall back to the same defaults the repository passes (null).
        verifyBlocking(cloudFunctions) {
            sendMessage(
                threadId = "thread1",
                fromUserId = "sender1",
                text = "check this out",
                type = "sharedPost",
                sharedPostId = "post1",
            )
        }
    }
}
