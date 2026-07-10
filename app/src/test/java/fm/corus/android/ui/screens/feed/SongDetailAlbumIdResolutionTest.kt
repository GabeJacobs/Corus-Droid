package fm.corus.android.ui.screens.feed

import fm.corus.android.data.model.CymbalPost
import fm.corus.android.data.model.CymbalTrack
import fm.corus.android.data.model.CymbalUser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Regression coverage for [resolveSongAlbumId] — the fallback that keeps the
 * song page's "Go to Album" row + tappable album line visible when the seed
 * track carries no album id (e.g. an artist-page "Popular" tap, which the
 * backend serves Apple-sourced with `albumId = null`). The album id is then
 * recovered from the loaded posts, which denormalize it.
 */
class SongDetailAlbumIdResolutionTest {

    private fun post(id: String, albumId: String?): CymbalPost = CymbalPost(
        id = id,
        user = CymbalUser(id = "u_$id", username = "user_$id", displayName = "User $id"),
        track = CymbalTrack(
            id = "t_$id",
            name = "Float On",
            artistName = "Modest Mouse",
            albumName = "Good News for People Who Love Bad News",
            albumId = albumId,
        ),
    )

    @Test
    fun `route album id wins when present`() {
        val posts = listOf(post("1", "postAlbum"))
        assertEquals("routeAlbum", resolveSongAlbumId("routeAlbum", posts))
    }

    @Test
    fun `falls back to the first post carrying an album id`() {
        val posts = listOf(post("1", "album123"))
        assertEquals("album123", resolveSongAlbumId(null, posts))
    }

    @Test
    fun `scans past posts missing an album id`() {
        // The core artist-page fix: the first post (older / am: / sc:) has no
        // album id, but a later post does — the link must still resolve.
        val posts = listOf(
            post("1", null),
            post("2", ""),
            post("3", "album789"),
        )
        assertEquals("album789", resolveSongAlbumId(null, posts))
    }

    @Test
    fun `blank route album id is treated as absent`() {
        val posts = listOf(post("1", "albumFromPost"))
        assertEquals("albumFromPost", resolveSongAlbumId("", posts))
    }

    @Test
    fun `returns null when no source carries an album id`() {
        val posts = listOf(post("1", null), post("2", ""))
        assertNull(resolveSongAlbumId(null, posts))
        assertNull(resolveSongAlbumId(null, emptyList()))
    }
}
