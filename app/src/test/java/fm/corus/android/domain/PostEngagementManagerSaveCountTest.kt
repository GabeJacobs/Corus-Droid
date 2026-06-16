package fm.corus.android.domain

import fm.corus.android.data.remote.FirestoreDataSource
import fm.corus.android.data.repository.PostRepository
import org.junit.Assert.assertEquals
import org.junit.Test
import org.mockito.kotlin.mock

/**
 * Guards the denormalized per-post `saveCount` plumbed through the engagement
 * store. The number rendered to the left of the bookmark reads from
 * EngagementState.saveCount, which initState seeds from the post doc and which
 * the post-update listener refreshes. Unlike like/comment/repost, saveCount has
 * NO optimistic-bump path (the bookmark fill flips via isSaved instead), so a
 * refresh always takes the latest server value.
 */
class PostEngagementManagerSaveCountTest {

    private fun newManager(): PostEngagementManager = PostEngagementManager(
        postRepository = mock<PostRepository>(),
        firestoreDataSource = mock<FirestoreDataSource>(),
        hapticManager = mock<HapticManager>(),
        subscriptionRepository = mock(),
        remoteConfig = mock(),
        analyticsService = mock(),
        saveChangedEvent = mock(),
    )

    @Test
    fun `initState seeds saveCount from the post`() {
        val manager = newManager()

        manager.initState(
            postId = "p1",
            likeCount = 0,
            commentCount = 0,
            repostCount = 0,
            isLiked = false,
            isSaved = false,
            saveCount = 7,
        )

        assertEquals(7, manager.getState("p1")?.saveCount)
    }

    @Test
    fun `saveCount defaults to zero when not provided`() {
        val manager = newManager()

        manager.initState("p1", 0, 0, 0, isLiked = false, isSaved = false)

        assertEquals(0, manager.getState("p1")?.saveCount)
    }

    @Test
    fun `re-init refreshes saveCount to the latest server value`() {
        val manager = newManager()
        manager.initState("p1", 0, 0, 0, isLiked = false, isSaved = false, saveCount = 4)

        // A later feed payload reports a higher count — saveCount has no
        // optimistic protection, so the new server value wins.
        manager.initState("p1", 0, 0, 0, isLiked = false, isSaved = false, saveCount = 9)

        assertEquals(9, manager.getState("p1")?.saveCount)
    }

    @Test
    fun `saveCount is isolated per post`() {
        val manager = newManager()
        manager.initState("p1", 0, 0, 0, isLiked = false, isSaved = false, saveCount = 2)
        manager.initState("p2", 0, 0, 0, isLiked = false, isSaved = false, saveCount = 5)

        assertEquals(2, manager.getState("p1")?.saveCount)
        assertEquals(5, manager.getState("p2")?.saveCount)
    }
}
