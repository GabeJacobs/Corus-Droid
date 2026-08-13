package fm.corus.android.domain

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import fm.corus.android.data.local.PreferencesDataStore
import fm.corus.android.data.remote.CloudFunctionsDataSource
import fm.corus.android.data.repository.UserRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Queue-sheet reorder / Add to Queue must survive feed [NowPlayingManager.updateFeedQueue]
 * syncs. Otherwise Next skips tracks the user moved or inserted earlier in feed order.
 * Mirrors iOS `NowPlayingManagerQueueReorderTests`.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class NowPlayingManagerQueueReorderTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var context: Context
    private val preferencesDataStore = mock<PreferencesDataStore> {
        on { autoplayNextSong } doReturn MutableStateFlow(true)
    }
    private val unfollowEvents = MutableSharedFlow<String>(extraBufferCapacity = 16)
    private val userRepository = mock<UserRepository> {
        on { this.unfollowEvents } doReturn unfollowEvents
    }

    private fun newManager(): NowPlayingManager =
        NowPlayingManager(
            context,
            mock<CloudFunctionsDataSource>(),
            preferencesDataStore,
            userRepository,
            mock(),
            mock(),
            mock(),
            mock(),
            mock(),
            mock(),
            mock(),
            mock(),
            mock(),
            mock(),
            mock(),
        )

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        context = ApplicationProvider.getApplicationContext()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun track(id: String, name: String = id, postId: String = "p-$id") = QueuedTrack(
        trackId = id,
        trackName = name,
        artistName = "Artist",
        albumArtURL = null,
        previewUrl = null,
        spotifyURI = null,
        spotifyWebURL = null,
        isrc = null,
        sourcePostId = postId,
    )

    private fun NowPlayingManager.fakeActiveTrack(trackId: String, sourcePostId: String?) {
        val field = NowPlayingManager::class.java.getDeclaredField("_state").apply { isAccessible = true }
        @Suppress("UNCHECKED_CAST")
        val flow = field.get(this) as MutableStateFlow<NowPlayingState>
        flow.value = flow.value.copy(trackId = trackId, sourcePostId = sourcePostId)
    }

    @Suppress("UNCHECKED_CAST")
    private val NowPlayingManager.queueIds: List<String>
        get() = NowPlayingManager::class.java.getDeclaredField("queue")
            .apply { isAccessible = true }
            .get(this)
            .let { (it as List<QueuedTrack>).map { t -> t.trackId } }

    private val NowPlayingManager.queueIndex: Int?
        get() = NowPlayingManager::class.java.getDeclaredField("currentQueueIndex")
            .apply { isAccessible = true }
            .get(this) as Int?

    @Test
    fun `feed sync preserves user reorder so Next does not skip`() = runTest(testDispatcher) {
        val manager = newManager()
        val feedOrder = listOf(
            track("t1", "Dhangalim"),
            track("t2", "Roses"),
            track("t3", "Free the Ruler"),
            track("t4", "The End Has No End"),
        )
        manager.fakeActiveTrack("t1", "p-t1")
        manager.updateFeedQueue(feedOrder, hasMore = true, loadMore = {})

        // Move Free the Ruler above Roses (indices 2 → before 1).
        manager.moveQueueItem(fromIndex = 2, toIndex = 1)
        assertEquals(listOf("t1", "t3", "t2", "t4"), manager.queueIds)

        manager.updateFeedQueue(feedOrder, hasMore = true, loadMore = {})
        assertEquals(listOf("t1", "t3", "t2", "t4"), manager.queueIds)
        assertEquals(0, manager.queueIndex)

        // After advancing to Free the Ruler, Next must be Roses — not End.
        manager.fakeActiveTrack("t3", "p-t3")
        manager.updateFeedQueue(feedOrder, hasMore = true, loadMore = {})
        assertEquals(1, manager.queueIndex)
        assertEquals("t2", manager.queueIds[manager.queueIndex!! + 1])
    }

    @Test
    fun `feed sync while pinned appends new posts only`() = runTest(testDispatcher) {
        val manager = newManager()
        val firstPage = listOf(
            track("t1", "A"),
            track("t2", "B"),
            track("t3", "C"),
        )
        manager.fakeActiveTrack("t1", "p-t1")
        manager.updateFeedQueue(firstPage, hasMore = true, loadMore = {})
        manager.moveQueueItem(fromIndex = 2, toIndex = 1)
        assertEquals(listOf("t1", "t3", "t2"), manager.queueIds)

        val secondPage = firstPage + track("t4", "D")
        manager.updateFeedQueue(secondPage, hasMore = false, loadMore = {})
        assertEquals(listOf("t1", "t3", "t2", "t4"), manager.queueIds)
    }

    @Test
    fun `add to queue then feed sync preserves following Up Next`() = runTest(testDispatcher) {
        val manager = newManager()
        val feedOrder = listOf(
            track("alumio", "Alumiô"),
            track("baby", "Baby, You're a Rich Man"),
            track("careless", "Careless"),
            track("forever", "Forever"),
            track("onefine", "One Fine Day"),
        )
        manager.fakeActiveTrack("alumio", "p-alumio")
        manager.updateFeedQueue(feedOrder.take(3), hasMore = true, loadMore = {})

        assertTrue(manager.addToUserQueue(feedOrder[4]))
        assertEquals(listOf("alumio", "onefine", "baby", "careless"), manager.queueIds)

        // First Next consumes the user-queued row; sync must keep Baby next.
        manager.fakeActiveTrack("onefine", "p-onefine")
        val queueField = NowPlayingManager::class.java.getDeclaredField("queue").apply { isAccessible = true }
        @Suppress("UNCHECKED_CAST")
        val q = (queueField.get(manager) as List<QueuedTrack>).toMutableList()
        val idx = q.indexOfFirst { it.trackId == "onefine" }
        q[idx] = q[idx].copy(isUserQueued = false)
        queueField.set(manager, q)

        manager.updateFeedQueue(feedOrder, hasMore = true, loadMore = {})

        assertEquals(listOf("alumio", "onefine", "baby", "careless", "forever"), manager.queueIds)
        assertEquals(1, manager.queueIndex)
        assertEquals("baby", manager.queueIds[manager.queueIndex!! + 1])
    }
}
