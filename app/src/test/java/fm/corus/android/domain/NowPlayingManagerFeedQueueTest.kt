package fm.corus.android.domain

import android.content.Context
import fm.corus.android.data.local.PreferencesDataStore
import fm.corus.android.data.remote.CloudFunctionsDataSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertFalse
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

    private fun newManager(): NowPlayingManager =
        NowPlayingManager(context, cloudFunctions, preferencesDataStore)

    @Before
    fun setUp() { Dispatchers.setMain(testDispatcher) }

    @After
    fun tearDown() { Dispatchers.resetMain() }

    private fun track(id: String) = QueuedTrack(
        trackId = id,
        trackName = "Track $id",
        artistName = "Artist",
        albumArtURL = null,
        previewUrl = null,
        spotifyURI = null,
        spotifyWebURL = null,
        isrc = null,
        sourcePostId = null,
    )

    /** Force the manager into a queued-playback state without touching ExoPlayer. */
    private fun NowPlayingManager.fakeActiveTrack(trackId: String) {
        val field = NowPlayingManager::class.java.getDeclaredField("_state").apply { isAccessible = true }
        @Suppress("UNCHECKED_CAST")
        val flow = field.get(this) as MutableStateFlow<NowPlayingState>
        flow.value = flow.value.copy(trackId = trackId)
    }

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
