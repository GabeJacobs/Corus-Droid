package fm.corus.android.domain

import fm.corus.android.data.model.TrackSource
import org.junit.Assert.assertEquals
import org.junit.Test

class NowPlayingManagerUserQueueTest {

    private fun track(id: String, userQueued: Boolean = false) = QueuedTrack(
        trackId = id,
        trackName = id,
        artistName = "a",
        albumArtURL = null,
        previewUrl = null,
        spotifyURI = null,
        spotifyWebURL = null,
        isrc = null,
        sourcePostId = "p-$id",
        source = TrackSource.SPOTIFY,
        isUserQueued = userQueued,
    )

    @Test
    fun `inserts right after now playing when no user queue yet`() {
        val queue = listOf(track("a"), track("b"), track("c"))
        assertEquals(1, userQueueInsertionIndex(queue, currentIndex = 0))
    }

    @Test
    fun `inserts after contiguous user-queued block`() {
        val queue = listOf(
            track("now"),
            track("u1", userQueued = true),
            track("u2", userQueued = true),
            track("auto"),
        )
        assertEquals(3, userQueueInsertionIndex(queue, currentIndex = 0))
    }

    @Test
    fun `appends when nothing is playing`() {
        val queue = listOf(track("a"), track("b"))
        assertEquals(2, userQueueInsertionIndex(queue, currentIndex = null))
    }
}
