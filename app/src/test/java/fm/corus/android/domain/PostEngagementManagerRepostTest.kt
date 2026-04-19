package fm.corus.android.domain

import fm.corus.android.data.remote.FirestoreDataSource
import fm.corus.android.data.repository.PostRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.mockito.kotlin.mock

/**
 * Guards the optimistic repost-count bump used by the compose flow.
 *
 * Context: tapping "Repost" now opens the compose screen; the original
 * post's repostCount is bumped locally via incrementRepostCount() only
 * after the user confirms the post. If this stops updating, the share
 * icon's count on the original post card will lag until Firestore's
 * listener catches up.
 */
class PostEngagementManagerRepostTest {

    private fun newManager(): PostEngagementManager = PostEngagementManager(
        postRepository = mock<PostRepository>(),
        firestoreDataSource = mock<FirestoreDataSource>(),
    )

    @Test
    fun `incrementRepostCount bumps count for a known post`() {
        val manager = newManager()
        manager.initState(
            postId = "p1",
            likeCount = 0,
            commentCount = 0,
            repostCount = 3,
            isLiked = false,
            isSaved = false,
        )

        manager.incrementRepostCount("p1")

        assertEquals(4, manager.getState("p1")?.repostCount)
    }

    @Test
    fun `incrementRepostCount is a no-op for unknown posts`() {
        val manager = newManager()

        manager.incrementRepostCount("never-initialized")

        assertNull(
            "state must not be created from thin air — the card isn't on screen yet",
            manager.getState("never-initialized"),
        )
    }

    @Test
    fun `multiple increments stack`() {
        val manager = newManager()
        manager.initState("p1", 0, 0, 0, false, false)

        manager.incrementRepostCount("p1")
        manager.incrementRepostCount("p1")
        manager.incrementRepostCount("p1")

        assertEquals(3, manager.getState("p1")?.repostCount)
    }

    @Test
    fun `incrementRepostCount only affects the target post`() {
        val manager = newManager()
        manager.initState("p1", 0, 0, 5, false, false)
        manager.initState("p2", 0, 0, 10, false, false)

        manager.incrementRepostCount("p1")

        assertEquals(6, manager.getState("p1")?.repostCount)
        assertEquals(10, manager.getState("p2")?.repostCount)
    }
}
