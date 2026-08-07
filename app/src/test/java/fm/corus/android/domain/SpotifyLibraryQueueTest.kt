package fm.corus.android.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Coverage for [SpotifyLibraryQueue], the parking lot behind "Add Saved Songs
 * to Library".
 *
 * Context: the feature used to hit the Spotify Web API, which development mode
 * caps at 25 users. It now goes through App Remote's user API instead, where
 * the Spotify app does the write — no cap, but no session either when Spotify
 * is closed. Saves made in that window queue here and drain on the next
 * connect, so the invariants worth pinning are that a save is never silently
 * lost, never delivered twice, and can't grow without bound.
 */
class SpotifyLibraryQueueTest {

    @Test fun `absent queue decodes to empty`() {
        assertTrue(SpotifyLibraryQueue.decode("[]").isEmpty())
    }

    @Test fun `malformed json decodes to empty rather than throwing`() {
        assertTrue(SpotifyLibraryQueue.decode("not json").isEmpty())
        assertTrue(SpotifyLibraryQueue.decode("").isEmpty())
        assertTrue(SpotifyLibraryQueue.decode("{\"a\":1}").isEmpty())
    }

    @Test fun `encode then decode round-trips order`() {
        val uris = listOf("spotify:track:a", "spotify:track:b", "spotify:track:c")
        assertEquals(uris, SpotifyLibraryQueue.decode(SpotifyLibraryQueue.encode(uris)))
    }

    @Test fun `enqueue appends oldest first`() {
        var queue = emptyList<String>()
        queue = SpotifyLibraryQueue.enqueue(queue, "spotify:track:a")
        queue = SpotifyLibraryQueue.enqueue(queue, "spotify:track:b")

        assertEquals(listOf("spotify:track:a", "spotify:track:b"), queue)
    }

    /**
     * Save, unsave, save again while Spotify is closed should be one add, not
     * two — the drain is not idempotent from the user's point of view.
     */
    @Test fun `enqueue does not duplicate a pending uri`() {
        var queue = SpotifyLibraryQueue.enqueue(emptyList(), "spotify:track:a")
        queue = SpotifyLibraryQueue.enqueue(queue, "spotify:track:a")

        assertEquals(listOf("spotify:track:a"), queue)
    }

    @Test fun `enqueue ignores blank uris`() {
        assertTrue(SpotifyLibraryQueue.enqueue(emptyList(), "").isEmpty())
        assertTrue(SpotifyLibraryQueue.enqueue(emptyList(), "   ").isEmpty())
    }

    /**
     * The drain removes each URI as it lands, so a disconnect halfway through
     * has to leave the rest intact.
     */
    @Test fun `remove takes out one entry and leaves the rest`() {
        val queue = listOf("spotify:track:a", "spotify:track:b", "spotify:track:c")

        assertEquals(
            listOf("spotify:track:a", "spotify:track:c"),
            SpotifyLibraryQueue.remove(queue, "spotify:track:b"),
        )
    }

    @Test fun `remove of an unknown uri is a no-op`() {
        val queue = listOf("spotify:track:a")

        assertEquals(queue, SpotifyLibraryQueue.remove(queue, "spotify:track:zzz"))
    }

    /**
     * Someone who saves for months without opening Spotify shouldn't carry an
     * unbounded list around. Newest wins.
     */
    @Test fun `overflow drops the oldest entries and keeps the newest`() {
        val overflow = 5
        var queue = emptyList<String>()
        repeat(SpotifyLibraryQueue.MAX_DEPTH + overflow) { i ->
            queue = SpotifyLibraryQueue.enqueue(queue, "spotify:track:$i")
        }

        assertEquals(SpotifyLibraryQueue.MAX_DEPTH, queue.size)
        assertEquals("spotify:track:$overflow", queue.first())
        assertEquals(
            "spotify:track:${SpotifyLibraryQueue.MAX_DEPTH + overflow - 1}",
            queue.last(),
        )
    }
}
