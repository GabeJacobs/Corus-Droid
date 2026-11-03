package fm.corus.android.domain

import fm.corus.android.data.model.CymbalPost
import fm.corus.android.data.model.CymbalTrack
import fm.corus.android.data.model.CymbalUser
import fm.corus.android.data.model.MediaType
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class ProfileShuffleQueueTest {
    private fun post(id: String) = CymbalPost(
        id = id, user = CymbalUser(id = "owner", username = "owner", displayName = "Owner"),
        track = CymbalTrack(id = "track-$id", name = id, artistName = "Artist", albumName = "Album"),
        mediaType = MediaType.TRACK,
    )

    @Test fun `loads lazily skips empty pages and retries a failed page without losing order`() = runTest {
        val ids = (0 until 65).map(Int::toString)
        val calls = mutableListOf<List<String>>()
        var shouldFail = false
        val queue = ProfileShuffleQueue(ids) { batch ->
            calls.add(batch.toList())
            if (shouldFail) { shouldFail = false; error("offline") }
            batch.filter { it.toInt() >= 30 }.map(::post)
        }
        queue.loadMore()
        assertEquals(listOf(30, 30), calls.map { it.size })
        assertEquals(ids.subList(30, 60), queue.tracks.map { it.sourcePostId })
        assertTrue(queue.hasMore)
        shouldFail = true
        try { queue.loadMore(); fail("expected failure") } catch (_: IllegalStateException) { }
        assertTrue(queue.hasMore)
        queue.loadMore()
        assertEquals(calls[2], calls[3])
        assertEquals(ids.drop(30), queue.tracks.map { it.sourcePostId })
        assertFalse(queue.hasMore)
    }

    @Test fun `session routes first track and later pages through the owned queue`() = runTest {
        val ids = (0 until 40).map(Int::toString)
        val player = mock<NowPlayingManager>()
        val session = ProfileShuffleSession("owner", ids, player) { batch -> batch.map(::post) }
        assertTrue(session.start())
        val origin = argumentCaptor<PlaybackOrigin.ProfileCollection>()
        val loadMore = argumentCaptor<suspend () -> Unit>()
        verify(player).playProfileShuffle(
            any(), any(), origin.capture(), eq(true), loadMore.capture(),
        )
        whenever(player.activeContext).thenReturn(origin.firstValue)
        loadMore.firstValue.invoke()
        verify(player).updateFeedQueue(any(), eq(false), any())
    }
}
