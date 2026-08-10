package fm.corus.android.domain

import fm.corus.android.data.model.CymbalPost
import fm.corus.android.data.model.CymbalTrack
import fm.corus.android.data.model.CymbalUser
import fm.corus.android.data.model.TrackSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Feed/profile plays go through [CymbalPost.toQueuedTrack]. Dropping
 * [CymbalTrack.albumArtLargeURL] leaves the full player on a thumbnail and
 * looks soft on high-DPI devices.
 */
class PostQueuedTrackTest {

    @Test
    fun `feed post queue mapping keeps large album art for the full player`() {
        val post = CymbalPost(
            id = "post1",
            user = CymbalUser(
                id = "u1",
                username = "jonnyapple",
                displayName = "Jonny",
            ),
            track = CymbalTrack(
                id = "t1",
                name = "Donkey Kong",
                artistName = "EHLE",
                albumName = "Donkey Kong",
                albumArtURL = "https://img/small.jpg",
                albumArtLargeURL = "https://img/large.jpg",
                source = TrackSource.SPOTIFY,
            ),
        )

        val queued = post.toQueuedTrack()

        assertEquals("https://img/small.jpg", queued.albumArtURL)
        assertEquals("https://img/large.jpg", queued.albumArtLargeURL)
        assertEquals("post1", queued.sourcePostId)
        assertEquals("u1", queued.posterUserId)
        assertNull(queued.catalogOrigin)
    }
}
