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
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock

/**
 * Regression tests for the "two posts, same song" playback bug.
 *
 * Bug: on the Trending feed a single song can appear on several posts. Tapping
 * post A played it; tapping post B (a *different* post of the *same* song) then
 * PAUSED the music instead of switching to B — because the play/pause toggle
 * keyed on track id alone, so B looked like a re-tap of the already-playing
 * track. The visual highlight ([PostPlaybackHighlight]) and the queue-index
 * resolver were already post-aware; the toggle decision was the last holdout.
 *
 * Fix: [isReTapOfActiveEntry] gates the toggle on the source post id (falling
 * back to a track-id match only when a post id is unknown), and the queue slot
 * for a duplicate song resolves to the tapped post rather than the first copy.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class NowPlayingManagerSameSongDifferentPostTest {

    // ── Pure toggle-decision logic (the reported bug) ──

    @Test
    fun `re-tapping the same post of the playing song toggles`() {
        assertTrue(
            isReTapOfActiveEntry(
                activeTrackId = "song-x",
                activeSourcePostId = "post-A",
                tappedTrackId = "song-x",
                tappedSourcePostId = "post-A",
            ),
        )
    }

    @Test
    fun `tapping a different post of the same song does NOT toggle`() {
        // The bug: pre-fix this returned true → tapping post B paused the music.
        assertFalse(
            "a different post of the same song must switch, not pause",
            isReTapOfActiveEntry(
                activeTrackId = "song-x",
                activeSourcePostId = "post-A",
                tappedTrackId = "song-x",
                tappedSourcePostId = "post-B",
            ),
        )
    }

    @Test
    fun `tapping a genuinely different song does NOT toggle`() {
        assertFalse(
            isReTapOfActiveEntry(
                activeTrackId = "song-x",
                activeSourcePostId = "post-A",
                tappedTrackId = "song-y",
                tappedSourcePostId = "post-B",
            ),
        )
    }

    @Test
    fun `falls back to track-id match when the tap carries no post id`() {
        // Single-track / search / detail playback: no originating post, so the
        // pre-fix track-id toggle behavior must be preserved.
        assertTrue(
            isReTapOfActiveEntry(
                activeTrackId = "song-x",
                activeSourcePostId = "post-A",
                tappedTrackId = "song-x",
                tappedSourcePostId = null,
            ),
        )
    }

    @Test
    fun `falls back to track-id match when the active entry has no post id`() {
        assertTrue(
            isReTapOfActiveEntry(
                activeTrackId = "song-x",
                activeSourcePostId = null,
                tappedTrackId = "song-x",
                tappedSourcePostId = "post-B",
            ),
        )
    }

    @Test
    fun `nothing active never counts as a re-tap`() {
        assertFalse(
            isReTapOfActiveEntry(
                activeTrackId = null,
                activeSourcePostId = null,
                tappedTrackId = "song-x",
                tappedSourcePostId = "post-A",
            ),
        )
    }

    // ── Queue-index resolution for duplicate songs ──

    private val testDispatcher = StandardTestDispatcher()
    private val context = mock<Context>()
    private val cloudFunctions = mock<CloudFunctionsDataSource>()
    private val preferencesDataStore = mock<PreferencesDataStore> {
        on { autoplayNextSong } doReturn MutableStateFlow(true)
    }
    private val userRepository = mock<UserRepository> {
        on { this.unfollowEvents } doReturn MutableSharedFlow<String>(extraBufferCapacity = 16)
    }

    private fun newManager(): NowPlayingManager =
        NowPlayingManager(context, cloudFunctions, preferencesDataStore, userRepository, mock(), mock(), mock(), mock(), mock(), mock(), mock())

    @Before
    fun setUp() { Dispatchers.setMain(testDispatcher) }

    @After
    fun tearDown() { Dispatchers.resetMain() }

    private fun track(id: String, sourcePostId: String?) = QueuedTrack(
        trackId = id,
        trackName = "Track $id",
        artistName = "Artist",
        albumArtURL = null,
        previewUrl = null,
        spotifyURI = null,
        spotifyWebURL = null,
        isrc = null,
        sourcePostId = sourcePostId,
    )

    /** Force the manager into a queued-playback state without touching ExoPlayer. */
    private fun NowPlayingManager.fakeActive(trackId: String, sourcePostId: String?) {
        val field = NowPlayingManager::class.java.getDeclaredField("_state").apply { isAccessible = true }
        @Suppress("UNCHECKED_CAST")
        val flow = field.get(this) as MutableStateFlow<NowPlayingState>
        flow.value = flow.value.copy(trackId = trackId, sourcePostId = sourcePostId)
    }

    private val NowPlayingManager.queueIndex: Int?
        get() = NowPlayingManager::class.java.getDeclaredField("currentQueueIndex")
            .apply { isAccessible = true }
            .get(this) as Int?

    @Test
    fun `updateFeedQueue anchors the current index on the tapped post, not the first copy`() =
        runTest(testDispatcher) {
            val manager = newManager()
            // The user tapped post B (the second copy of song-x), so playback is
            // attributed to post B.
            manager.fakeActive(trackId = "song-x", sourcePostId = "post-B")

            manager.updateFeedQueue(
                newQueue = listOf(
                    track("song-x", sourcePostId = "post-A"), // idx 0 — same song, different post
                    track("other", sourcePostId = "post-C"),  // idx 1
                    track("song-x", sourcePostId = "post-B"), // idx 2 — the post that's playing
                ),
                hasMore = false,
                loadMore = { },
            )

            // Pre-fix this resolved to idx 0 (first trackId match), so "next"
            // would have advanced from post A's slot instead of post B's.
            assertEquals(2, manager.queueIndex)
        }

    @Test
    fun `updateFeedQueue falls back to track-id when the active entry has no post id`() =
        runTest(testDispatcher) {
            val manager = newManager()
            manager.fakeActive(trackId = "song-x", sourcePostId = null)

            manager.updateFeedQueue(
                newQueue = listOf(
                    track("other", sourcePostId = "post-C"),
                    track("song-x", sourcePostId = "post-A"),
                ),
                hasMore = false,
                loadMore = { },
            )

            assertEquals(1, manager.queueIndex)
        }
}
