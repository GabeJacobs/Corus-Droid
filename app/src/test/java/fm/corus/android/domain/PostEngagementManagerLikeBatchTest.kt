package fm.corus.android.domain

import fm.corus.android.data.remote.FirestoreDataSource
import fm.corus.android.data.repository.PostRepository
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verifyBlocking

/**
 * Guards the read-cost contract of checkLikeStatuses: it must resolve a whole
 * page of like-state in ONE batched FirestoreDataSource.fetchLikedStates call
 * (a `whereIn` query that bills only for liked matches) — never a per-post
 * isPostLiked read — and must not clobber optimistic flips.
 */
class PostEngagementManagerLikeBatchTest {

    private val firestore = mock<FirestoreDataSource> {
        onBlocking { fetchLikedStates(any()) } doReturn setOf("p1", "p3")
    }
    private val postRepo = mock<PostRepository>()

    private fun newManager() = PostEngagementManager(
        postRepository = postRepo,
        firestoreDataSource = firestore,
        hapticManager = mock(),
        subscriptionRepository = mock(),
        remoteConfig = mock(),
        analyticsService = mock(),
        saveChangedEvent = mock(),
        spotifySaveAutoAdd = mock(),
    )

    /** checkLikeStatuses launches on Dispatchers.IO; poll briefly for the result. */
    private fun awaitState(manager: PostEngagementManager, postId: String, predicate: (EngagementState) -> Boolean) {
        runBlocking {
            repeat(50) {
                val s = manager.getState(postId)
                if (s != null && predicate(s)) return@runBlocking
                kotlinx.coroutines.delay(20)
            }
        }
    }

    @Test
    fun `resolves a page in one batched query, not per-post reads`() {
        val manager = newManager()
        listOf("p1", "p2", "p3").forEach { manager.initState(it, 0, 0, 0, isLiked = false, isSaved = false) }

        manager.checkLikeStatuses(listOf("p1", "p2", "p3"), "viewer")
        awaitState(manager, "p1") { it.isLiked }

        assertEquals(true, manager.getState("p1")?.isLiked)
        assertEquals(false, manager.getState("p2")?.isLiked)
        assertEquals(true, manager.getState("p3")?.isLiked)
        // One batched call for the whole page; zero per-post isPostLiked reads.
        verifyBlocking(firestore, times(1)) { fetchLikedStates(any()) }
        verifyBlocking(postRepo, never()) { isPostLiked(any(), any()) }
    }

    @Test
    fun `does not clobber an in-flight optimistic like`() {
        val manager = newManager()
        manager.initState("p1", likeCount = 5, commentCount = 0, repostCount = 0, isLiked = false, isSaved = false)
        // User optimistically likes p1 (marks it userModified + in-flight). The
        // server set returned by fetchLikedStates omits the just-liked state.
        manager.toggleLike("p1", "viewer")

        manager.checkLikeStatuses(listOf("p1"), "viewer")
        // Give the reconcile a chance to (wrongly) run.
        runBlocking { kotlinx.coroutines.delay(120) }

        // Optimistic liked state must survive the reconcile.
        assertEquals(true, manager.getState("p1")?.isLiked)
    }
}
