package fm.corus.android.ui.components

import fm.corus.android.data.model.CymbalPost
import fm.corus.android.data.model.CymbalTrack
import fm.corus.android.data.model.CymbalUser
import fm.corus.android.data.model.MediaType
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PostActionMenuTest {

    @Test
    fun `report and block shown for non-owner human-authored posts`() {
        assertTrue(showPostReportBlockActions(isMine = false, authorIsBot = false))
    }

    @Test
    fun `report and block hidden for bot-authored posts`() {
        assertFalse(showPostReportBlockActions(isMine = false, authorIsBot = true))
    }

    @Test
    fun `report and block hidden for own posts`() {
        assertFalse(showPostReportBlockActions(isMine = true, authorIsBot = false))
    }

    @Test
    fun `report and block hidden for own bot posts`() {
        assertFalse(showPostReportBlockActions(isMine = true, authorIsBot = true))
    }

    // ── Go to Artist / Go to Album gating ──

    private fun user() = CymbalUser(id = "u1", username = "amy", displayName = "Amy")

    private fun trackPost(
        artistIds: List<String> = listOf("artist1"),
        albumId: String? = "album1",
    ) = CymbalPost(
        id = "p1",
        user = user(),
        track = CymbalTrack(
            id = "t1",
            name = "Track",
            artistName = "Artist",
            albumName = "Album",
            artistIds = artistIds,
            albumId = albumId,
        ),
        mediaType = MediaType.TRACK,
    )

    private fun moviePost() = CymbalPost(
        id = "m1",
        user = user(),
        track = CymbalTrack(
            id = "t1",
            name = "Film",
            artistName = "Director",
            albumName = "Film",
            // Even if a movie post somehow carried these, films never get the rows.
            artistIds = listOf("artist1"),
            albumId = "album1",
        ),
        mediaType = MediaType.MOVIE,
    )

    @Test
    fun `go to artist shown for track with artist id and flag on`() {
        assertTrue(showGoToArtistRow(post = trackPost(), artistPagesEnabled = true))
    }

    @Test
    fun `go to artist hidden when flag off`() {
        assertFalse(showGoToArtistRow(post = trackPost(), artistPagesEnabled = false))
    }

    @Test
    fun `go to artist hidden when no artist id`() {
        assertFalse(showGoToArtistRow(post = trackPost(artistIds = emptyList()), artistPagesEnabled = true))
    }

    @Test
    fun `go to artist hidden when artist ids are blank`() {
        assertFalse(showGoToArtistRow(post = trackPost(artistIds = listOf("", " ")), artistPagesEnabled = true))
    }

    @Test
    fun `go to artist never shown for movie posts`() {
        assertFalse(showGoToArtistRow(post = moviePost(), artistPagesEnabled = true))
    }

    @Test
    fun `go to album shown for track with album id and flag on`() {
        assertTrue(showGoToAlbumRow(post = trackPost(), artistPagesEnabled = true))
    }

    @Test
    fun `go to album hidden when flag off`() {
        assertFalse(showGoToAlbumRow(post = trackPost(), artistPagesEnabled = false))
    }

    @Test
    fun `go to album hidden when album id is null`() {
        assertFalse(showGoToAlbumRow(post = trackPost(albumId = null), artistPagesEnabled = true))
    }

    @Test
    fun `go to album hidden when album id is blank`() {
        assertFalse(showGoToAlbumRow(post = trackPost(albumId = "   "), artistPagesEnabled = true))
    }

    @Test
    fun `go to album never shown for movie posts`() {
        assertFalse(showGoToAlbumRow(post = moviePost(), artistPagesEnabled = true))
    }

    @Test
    fun `go to album accepts apple-prefixed album id`() {
        assertTrue(showGoToAlbumRow(post = trackPost(albumId = "am:123456"), artistPagesEnabled = true))
    }

    // ── Tap builders no-op when the nav callback is null (flag off) ──

    @Test
    fun `artist tap builder is null when nav callback null`() {
        assertNull(onGoToArtistTap(trackPost(), onNavigateToArtist = null))
    }

    @Test
    fun `album tap builder is null when nav callback null`() {
        assertNull(onGoToAlbumTap(trackPost(), onNavigateToAlbum = null))
    }

    @Test
    fun `artist tap builder is null for movie posts`() {
        assertNull(onGoToArtistTap(moviePost(), onNavigateToArtist = {}))
    }

    @Test
    fun `album tap builder is null for movie posts`() {
        assertNull(onGoToAlbumTap(moviePost(), onNavigateToAlbum = {}))
    }

    @Test
    fun `artist tap builder routes to first artist id`() {
        var routed: fm.corus.android.ui.navigation.ArtistPageRoute? = null
        val tap = onGoToArtistTap(trackPost(artistIds = listOf("artistA", "artistB")), onNavigateToArtist = { routed = it })
        tap?.invoke()
        assertTrue(routed?.artistId == "artistA")
    }

    @Test
    fun `album tap builder passes album id through untouched`() {
        var routed: fm.corus.android.ui.navigation.AlbumPageRoute? = null
        val tap = onGoToAlbumTap(trackPost(albumId = "am:987"), onNavigateToAlbum = { routed = it })
        tap?.invoke()
        assertTrue(routed?.albumId == "am:987")
    }

    // ── albumId deserialization ──

    @Test
    fun `fromCloudData parses albumId`() {
        val post = CymbalPost.fromCloudData(
            mapOf(
                "id" to "p1",
                "mediaType" to "track",
                "trackId" to "t1",
                "albumId" to "album1",
            )
        )
        assertTrue(post.track.albumId == "album1")
    }

    @Test
    fun `fromCloudData missing albumId is null`() {
        val post = CymbalPost.fromCloudData(
            mapOf(
                "id" to "p1",
                "mediaType" to "track",
                "trackId" to "t1",
            )
        )
        assertNull(post.track.albumId)
    }

    @Test
    fun `fromCloudData empty albumId is null`() {
        val post = CymbalPost.fromCloudData(
            mapOf(
                "id" to "p1",
                "mediaType" to "track",
                "trackId" to "t1",
                "albumId" to "",
            )
        )
        assertNull(post.track.albumId)
    }
}
