package fm.corus.android.domain

import fm.corus.android.data.remote.CloudFunctionsDataSource
import fm.corus.android.data.remote.FirestoreDataSource
import fm.corus.android.data.repository.PostRepository
import fm.corus.android.data.repository.SubscriptionRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.doSuspendableAnswer
import org.mockito.kotlin.eq
import org.mockito.kotlin.isNull
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyBlocking

/**
 * Regression guard for the save/bookmark fixes:
 *  - checkSaveStatuses reconciles a whole page's saved-state in ONE batched
 *    FirestoreDataSource.fetchSavedStates call (so a saved post opened from the
 *    Saves tab shows a filled bookmark), without clobbering optimistic flips.
 *  - seedSavedState authoritatively marks the Saves-tab posts saved (no flash).
 *  - toggleSave emits SaveChangedEvent so the profile Saves grid stays live.
 */
class PostEngagementManagerSaveTest {

    private fun newManager(
        firestore: FirestoreDataSource = mock(),
        postRepo: PostRepository = mock(),
        subscriptionRepo: SubscriptionRepository = mock {
            on { savesCount } doReturn MutableStateFlow(0)
        },
        saveEvent: SaveChangedEvent = mock(),
    ) = PostEngagementManager(
        postRepository = postRepo,
        firestoreDataSource = firestore,
        hapticManager = mock(),
        subscriptionRepository = subscriptionRepo,
        remoteConfig = mock(),
        analyticsService = mock(),
        saveChangedEvent = saveEvent,
        spotifySaveAutoAdd = mock(),
        reviewPromptManager = mock(),
    )

    /** checkSaveStatuses launches on Dispatchers.IO; poll briefly for the result. */
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
    fun `checkSaveStatuses resolves a page in one batched query`() {
        val firestore = mock<FirestoreDataSource> {
            onBlocking { fetchSavedStates(any()) } doReturn setOf("p1", "p3")
        }
        val manager = newManager(firestore = firestore)
        listOf("p1", "p2", "p3").forEach { manager.initState(it, 0, 0, 0, isLiked = false, isSaved = false) }

        manager.checkSaveStatuses(listOf("p1", "p2", "p3"), "viewer")
        awaitState(manager, "p1") { it.isSaved }

        assertEquals(true, manager.getState("p1")?.isSaved)
        assertEquals(false, manager.getState("p2")?.isSaved)
        assertEquals(true, manager.getState("p3")?.isSaved)
        verifyBlocking(firestore, times(1)) { fetchSavedStates(any()) }
    }

    @Test
    fun `checkSaveStatuses does not clobber an in-flight optimistic save`() {
        // Server set omits the just-saved post (it hasn't synced yet).
        val firestore = mock<FirestoreDataSource> {
            onBlocking { fetchSavedStates(any()) } doReturn emptySet()
        }
        // Keep the save genuinely in-flight (userModified marker still set) while
        // checkSaveStatuses runs, so the test deterministically exercises the
        // anti-clobber guard rather than racing the toggle's completion.
        val postRepo = mock<PostRepository> {
            onBlocking { savePost(any(), any()) } doSuspendableAnswer {
                kotlinx.coroutines.delay(1_000)
                CloudFunctionsDataSource.SavePostResult(savesCount = 5, alreadySaved = false)
            }
        }
        val manager = newManager(firestore = firestore, postRepo = postRepo)
        manager.initState("p1", 0, 0, 0, isLiked = false, isSaved = false)
        manager.toggleSave("p1", "viewer") // optimistic save, still in-flight

        manager.checkSaveStatuses(listOf("p1"), "viewer")
        runBlocking { kotlinx.coroutines.delay(120) } // well before the 1s save completes

        assertEquals(true, manager.getState("p1")?.isSaved)
    }

    @Test
    fun `seedSavedState marks existing and brand-new entries as saved`() {
        val manager = newManager()
        manager.initState("p1", 0, 0, 0, isLiked = false, isSaved = false)

        manager.seedSavedState(listOf("p1", "p2"))

        assertEquals(true, manager.getState("p1")?.isSaved)
        // p2 had no prior entry — seed creates a minimal one flagged saved.
        assertEquals(true, manager.getState("p2")?.isSaved)
    }

    @Test
    fun `seedSavedState does not clobber an in-flight optimistic unsave`() {
        val manager = newManager()
        manager.initState("p1", 0, 0, 0, isLiked = false, isSaved = true)
        manager.toggleSave("p1", "viewer") // optimistic unsave -> userModified

        manager.seedSavedState(listOf("p1"))

        assertEquals(false, manager.getState("p1")?.isSaved)
    }

    @Test
    fun `toggleSave emits SaveChangedEvent true on save and false on unsave`() {
        val saveEvent = mock<SaveChangedEvent>()
        val postRepo = mock<PostRepository> {
            onBlocking { savePost(any(), any()) } doReturn
                CloudFunctionsDataSource.SavePostResult(savesCount = 5, alreadySaved = false)
            onBlocking { unsavePost(any(), any()) } doReturn 4
        }
        val manager = newManager(postRepo = postRepo, saveEvent = saveEvent)

        manager.initState("p1", 0, 0, 0, isLiked = false, isSaved = false)
        manager.toggleSave("p1", "viewer") // save
        runBlocking { kotlinx.coroutines.delay(150) }
        verify(saveEvent).notify(eq("p1"), eq(true), isNull())

        manager.toggleSave("p1", "viewer") // unsave
        runBlocking { kotlinx.coroutines.delay(150) }
        verify(saveEvent).notify(eq("p1"), eq(false), isNull())
    }
}
