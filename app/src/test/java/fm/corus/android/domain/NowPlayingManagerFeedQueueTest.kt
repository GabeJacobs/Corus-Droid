package fm.corus.android.domain

import android.content.Context
import fm.corus.android.data.local.PreferencesDataStore
import fm.corus.android.data.remote.CloudFunctionsDataSource
import fm.corus.android.data.repository.UserRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock

/**
 * Regression tests for the paginated-feed wiring in [NowPlayingManager].
 *
 * Bug: the mini-player next button disabled itself partway through the feed
 * because the queue was a one-shot snapshot taken at play() time. As the feed
 * paginated in more songs, the queue stayed stale and `hasNext` flipped to
 * false even though more tracks were available below.
 *
 * Fix: callers (FeedViewModel / ProfileFeedViewModel) keep the queue in sync
 * via [NowPlayingManager.updateFeedQueue] and supply a `hasMore` flag so the
 * button stays enabled while more pages can still be fetched.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class NowPlayingManagerFeedQueueTest {

    private val testDispatcher = StandardTestDispatcher()
    private val context = mock<Context>()
    private val cloudFunctions = mock<CloudFunctionsDataSource>()
    private val preferencesDataStore = mock<PreferencesDataStore> {
        on { autoplayNextSong } doReturn MutableStateFlow(true)
    }
    private val unfollowEvents = MutableSharedFlow<String>(extraBufferCapacity = 16)
    private val userRepository = mock<UserRepository> {
        on { this.unfollowEvents } doReturn unfollowEvents
    }

    private fun newManager(): NowPlayingManager =
        NowPlayingManager(context, cloudFunctions, preferencesDataStore, userRepository, mock(), mock(), mock(), mock(), mock(), mock(), mock(), mock(), mock(), mock(), mock())

    @Before
    fun setUp() { Dispatchers.setMain(testDispatcher) }

    @After
    fun tearDown() { Dispatchers.resetMain() }

    private fun track(id: String, posterUserId: String? = null) = QueuedTrack(
        trackId = id,
        trackName = "Track $id",
        artistName = "Artist",
        albumArtURL = null,
        previewUrl = null,
        spotifyURI = null,
        spotifyWebURL = null,
        isrc = null,
        sourcePostId = null,
        posterUserId = posterUserId,
    )

    /** Force the manager into a queued-playback state without touching ExoPlayer. */
    private fun NowPlayingManager.fakeActiveTrack(trackId: String) {
        val field = NowPlayingManager::class.java.getDeclaredField("_state").apply { isAccessible = true }
        @Suppress("UNCHECKED_CAST")
        val flow = field.get(this) as MutableStateFlow<NowPlayingState>
        flow.value = flow.value.copy(trackId = trackId)
    }

    @Suppress("UNCHECKED_CAST")
    private val NowPlayingManager.queueSnapshot: List<QueuedTrack>
        get() = NowPlayingManager::class.java.getDeclaredField("queue")
            .apply { isAccessible = true }
            .get(this) as List<QueuedTrack>

    @Test
    fun `hasNext stays true while more feed pages are available`() = runTest(testDispatcher) {
        val manager = newManager()
        manager.fakeActiveTrack("a")

        manager.updateFeedQueue(
            newQueue = listOf(track("a")),
            hasMore = true,
            loadMore = { /* not invoked in this test */ },
        )

        assertTrue(
            "even though only 1 track is loaded, hasMore=true means more songs exist below",
            manager.state.value.hasNext,
        )
    }

    @Test
    fun `hasNext is false when current is last and no more pages`() = runTest(testDispatcher) {
        val manager = newManager()
        manager.fakeActiveTrack("b")

        manager.updateFeedQueue(
            newQueue = listOf(track("a"), track("b")),
            hasMore = false,
            loadMore = { },
        )

        assertFalse(manager.state.value.hasNext)
    }

    // ── Unfollow → queue prune (regression) ──
    //
    // Bug: user starts playing the feed, then unfollows someone in the feed
    // and pull-to-refreshes. Their posts disappear from the visible feed,
    // but the in-memory playback queue still contains their tracks — so the
    // mini-player's next button keeps advancing into their songs.

    @Test
    fun `removeFromQueue prunes tracks posted by the unfollowed user`() = runTest(testDispatcher) {
        val manager = newManager()
        manager.fakeActiveTrack("a")
        manager.updateFeedQueue(
            newQueue = listOf(
                track("a", posterUserId = "u1"),
                track("b", posterUserId = "u2"),
                track("c", posterUserId = "u2"),
                track("d", posterUserId = "u3"),
            ),
            hasMore = false,
            loadMore = { },
        )

        manager.removeFromQueue(setOf("u2"))

        assertEquals(
            "u2's two tracks should be gone; u1 (current) and u3 remain",
            listOf("a", "d"),
            manager.queueSnapshot.map { it.trackId },
        )
        assertTrue("next should still advance to 'd'", manager.state.value.hasNext)
    }

    @Test
    fun `removeFromQueue stops playback when the current track is from an unfollowed user`() = runTest(testDispatcher) {
        val manager = newManager()
        manager.fakeActiveTrack("a")
        manager.updateFeedQueue(
            newQueue = listOf(
                track("a", posterUserId = "u1"),
                track("b", posterUserId = "u2"),
            ),
            hasMore = false,
            loadMore = { },
        )

        manager.removeFromQueue(setOf("u1"))

        // Currently-playing track was authored by the unfollowed user — the
        // user already signaled they don't want this person's content, so we
        // stop rather than letting the song finish.
        assertNull("playback should have been cleared", manager.state.value.trackId)
        assertFalse(manager.state.value.isPlaying)
        assertTrue(manager.queueSnapshot.isEmpty())
    }

    @Test
    fun `removeFromQueue is a no-op when no queued tracks belong to those users`() = runTest(testDispatcher) {
        val manager = newManager()
        manager.fakeActiveTrack("a")
        val original = listOf(
            track("a", posterUserId = "u1"),
            track("b", posterUserId = "u2"),
        )
        manager.updateFeedQueue(newQueue = original, hasMore = true, loadMore = { })

        // Unfollowing someone whose tracks aren't in the queue must not touch
        // queue contents or flip hasNext (which is gated on hasMore=true).
        manager.removeFromQueue(setOf("u-not-in-queue"))

        assertEquals(original.map { it.trackId }, manager.queueSnapshot.map { it.trackId })
        assertTrue(manager.state.value.hasNext)
    }

    @Test
    fun `unfollow event from UserRepository prunes the queue`() = runTest(testDispatcher) {
        val manager = newManager()
        manager.fakeActiveTrack("a")
        manager.updateFeedQueue(
            newQueue = listOf(
                track("a", posterUserId = "u1"),
                track("b", posterUserId = "u2"),
                track("c", posterUserId = "u2"),
            ),
            hasMore = false,
            loadMore = { },
        )
        // Let the init-block collector attach before we emit, otherwise the
        // event is dropped (SharedFlow has no replay buffer).
        advanceUntilIdle()

        // Simulates UserRepository.unfollowUser firing after the local user
        // taps "unfollow". The collector wired up in NowPlayingManager.init
        // must react and prune u2's tracks without an explicit caller.
        unfollowEvents.tryEmit("u2")
        advanceUntilIdle()

        assertEquals(listOf("a"), manager.queueSnapshot.map { it.trackId })
    }

    @Test
    fun `updateFeedQueue is a no-op when current track is not in the new queue`() = runTest(testDispatcher) {
        val manager = newManager()
        manager.fakeActiveTrack("from-search")

        // A feed observer fires for an unrelated screen — must not clobber the
        // search-originated playback context.
        manager.updateFeedQueue(
            newQueue = listOf(track("a"), track("b")),
            hasMore = true,
            loadMore = { },
        )

        // hasNext defaults to false in NowPlayingState; we never wired it up,
        // so the cross-screen sync should not have flipped it on.
        assertFalse(manager.state.value.hasNext)
    }
}
