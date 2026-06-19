package fm.corus.android.domain

import fm.corus.android.data.remote.CloudFunctionsDataSource
import fm.corus.android.data.repository.PostRepository
import org.junit.Assert.assertEquals
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock

/**
 * Guards the denormalized per-post `saveCount` plumbed through the engagement
 * store. The number rendered to the left of the bookmark reads from
 * EngagementState.saveCount, which initState seeds from the post doc and which
 * the post-update listener refreshes.
 *
 * Like the comment count, saveCount IS optimistic: toggleSave bumps it the
 * instant the user taps so an unsaved sole-saver post hides its count (0)
 * immediately instead of lingering at 1 until the onSaveDeleted trigger
 * reconciles the post doc. A save in-flight marker protects that optimistic
 * value from a stale refresh fetched before the trigger committed.
 */
class PostEngagementManagerSaveCountTest {

    private fun newManager(
        postRepository: PostRepository = mock(),
    ): PostEngagementManager = PostEngagementManager(
        postRepository = postRepository,
        firestoreDataSource = mock(),
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
    fun `re-init refreshes saveCount to the latest server value when no toggle is in flight`() {
        val manager = newManager()
        manager.initState("p1", 0, 0, 0, isLiked = false, isSaved = false, saveCount = 4)

        // A later feed payload reports a higher count. With no in-flight save
        // toggle, the optimistic guard is inactive, so the new server value wins.
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

    // ── Optimistic toggle behavior ──

    @Test
    fun `toggle to saved optimistically increments the count`() {
        val repo = mock<PostRepository> {
            onBlocking { savePost(any(), any()) } doReturn
                CloudFunctionsDataSource.SavePostResult(savesCount = 1, alreadySaved = false)
        }
        val manager = newManager(postRepository = repo)
        manager.initState("p1", 0, 0, 0, isLiked = false, isSaved = false, saveCount = 5)

        manager.toggleSave("p1", "u1")

        val state = manager.getState("p1")
        assertEquals(6, state?.saveCount)
        assertEquals(true, state?.isSaved)
    }

    @Test
    fun `unsaving the sole save optimistically drops the count to 0 so the UI hides it`() {
        // The reported bug: a post you are the only saver of (count = 1) must
        // drop to 0 (hidden) the moment you unsave, not stay at 1 until the
        // server trigger reconciles.
        val repo = mock<PostRepository> {
            onBlocking { unsavePost(any(), any()) } doReturn 0
        }
        val manager = newManager(postRepository = repo)
        manager.initState("p1", 0, 0, 0, isLiked = false, isSaved = true, saveCount = 1)

        manager.toggleSave("p1", "u1")

        val state = manager.getState("p1")
        assertEquals(0, state?.saveCount)
        assertEquals(false, state?.isSaved)
    }

    @Test
    fun `unsave never drives the count below 0`() {
        val repo = mock<PostRepository> {
            onBlocking { unsavePost(any(), any()) } doReturn 0
        }
        val manager = newManager(postRepository = repo)
        manager.initState("p1", 0, 0, 0, isLiked = false, isSaved = true, saveCount = 0)

        manager.toggleSave("p1", "u1")

        assertEquals(0, manager.getState("p1")?.saveCount)
    }

    @Test
    fun `a stale refresh during an in-flight unsave does not resurrect the count`() {
        val repo = mock<PostRepository> {
            onBlocking { unsavePost(any(), any()) } doReturn 0
        }
        val manager = newManager(postRepository = repo)
        manager.initState("p1", 0, 0, 0, isLiked = false, isSaved = true, saveCount = 1)

        // User unsaves — optimistic count drops to 0 and a save in-flight marker is set.
        manager.toggleSave("p1", "u1")
        assertEquals(0, manager.getState("p1")?.saveCount)

        // A feed payload fetched before the onSaveDeleted trigger committed still
        // reports the pre-unsave count of 1 — it must NOT clobber the optimistic 0.
        manager.initState("p1", 0, 0, 0, isLiked = false, isSaved = true, saveCount = 1)

        assertEquals(0, manager.getState("p1")?.saveCount)
    }
}
