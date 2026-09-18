package fm.corus.android.domain

import fm.corus.android.data.model.CymbalPost
import fm.corus.android.data.model.CymbalTrack
import fm.corus.android.data.model.CymbalUser
import fm.corus.android.data.model.MediaType
import org.junit.Assert.assertEquals
import org.junit.Test

class PosterCorusQueueTest {

    private fun user() = CymbalUser(id = "u1", username = "gabe", displayName = "Gabe")

    private fun post(id: String) = CymbalPost(
        id = id,
        user = user(),
        track = CymbalTrack(id = "t-$id", name = id, artistName = "Artist", albumName = "Album"),
        mediaType = MediaType.TRACK,
    )

    @Test
    fun firstPageContainingPlayingPostIsTheQueue() {
        val recent = post("recent")
        val older = post("older")
        val oldest = post("oldest")
        val assembled = PosterCorusQueue.assemblePosts(
            playing = recent,
            firstPage = listOf(recent, older, oldest),
            olderThanPlaying = emptyList(),
        )
        assertEquals(listOf("recent", "older", "oldest"), assembled.map { it.id })
    }

    @Test
    fun missedFirstPageQueuesPlayingThenOlder() {
        val playing = post("older-like")
        val assembled = PosterCorusQueue.assemblePosts(
            playing = playing,
            firstPage = listOf(post("newer-a"), post("newer-b")),
            olderThanPlaying = listOf(post("even-older")),
        )
        assertEquals(listOf("older-like", "even-older"), assembled.map { it.id })
    }
}
