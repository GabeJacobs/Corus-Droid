package fm.corus.android.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CymbalUserPreferredProfileSegmentTest {

    private fun user(
        trackCount: Int? = null,
        movieCount: Int? = null,
        isBot: Boolean = false,
        botType: String? = null,
    ) = CymbalUser(
        id = "u1",
        username = "alice",
        displayName = "Alice",
        trackCount = trackCount,
        movieCount = movieCount,
        isBot = isBot,
        botType = botType,
    )

    @Test
    fun `defaults to film tab when only films are posted`() {
        assertEquals(1, user(trackCount = 0, movieCount = 1).preferredProfileSegment)
        assertEquals(1, user(trackCount = 0, movieCount = 7).preferredProfileSegment)
    }

    @Test
    fun `defaults to music tab when songs exist`() {
        assertEquals(0, user(trackCount = 1, movieCount = 0).preferredProfileSegment)
        assertEquals(0, user(trackCount = 1, movieCount = 5).preferredProfileSegment)
        assertEquals(0, user(trackCount = 4, movieCount = 0).preferredProfileSegment)
    }

    @Test
    fun `defaults to music tab when neither has been posted`() {
        assertEquals(0, user(trackCount = 0, movieCount = 0).preferredProfileSegment)
    }

    @Test
    fun `returns null when counts are missing so callers can defer`() {
        assertNull(user(trackCount = null, movieCount = null).preferredProfileSegment)
        assertNull(user(trackCount = 0, movieCount = null).preferredProfileSegment)
        assertNull(user(trackCount = null, movieCount = 1).preferredProfileSegment)
    }

    @Test
    fun `bots always default to segment 0`() {
        assertEquals(
            0,
            user(trackCount = 0, movieCount = 5, isBot = true, botType = "film").preferredProfileSegment,
        )
        assertEquals(
            0,
            user(trackCount = null, movieCount = null, isBot = true, botType = "music").preferredProfileSegment,
        )
    }

    private fun post(mediaType: MediaType, id: String = "p"): CymbalPost = CymbalPost(
        id = id,
        user = CymbalUser(id = "u1", username = "alice", displayName = "Alice"),
        track = CymbalTrack(id = "t", name = "Track", artistName = "Artist", albumName = "Album"),
        mediaType = mediaType,
    )

    @Test
    fun `derives film tab from loaded posts when counters missing and films-only`() {
        val u = user(trackCount = null, movieCount = null).copy(cymbalCount = 1)
        val posts = listOf(post(MediaType.MOVIE))
        assertEquals(1, u.preferredProfileSegmentFromPosts(posts, hasMore = false))
    }

    @Test
    fun `derives music tab from loaded posts when counters missing and any song present`() {
        val u = user(trackCount = null, movieCount = null).copy(cymbalCount = 2)
        val posts = listOf(post(MediaType.MOVIE, "p1"), post(MediaType.TRACK, "p2"))
        assertEquals(0, u.preferredProfileSegmentFromPosts(posts, hasMore = false))
    }

    @Test
    fun `defers when posts page is incomplete and counters missing`() {
        val u = user(trackCount = null, movieCount = null).copy(cymbalCount = 50)
        val posts = listOf(post(MediaType.MOVIE))
        assertNull(u.preferredProfileSegmentFromPosts(posts, hasMore = true))
    }

    @Test
    fun `derived path treats fully-loaded set as authoritative even when hasMore stale`() {
        val u = user(trackCount = null, movieCount = null).copy(cymbalCount = 1)
        val posts = listOf(post(MediaType.MOVIE))
        assertEquals(1, u.preferredProfileSegmentFromPosts(posts, hasMore = true))
    }

    @Test
    fun `explicit counters win even if loaded posts disagree`() {
        val u = user(trackCount = 3, movieCount = 0).copy(cymbalCount = 3)
        // Posts page hasn't loaded yet but counters say music.
        assertEquals(0, u.preferredProfileSegmentFromPosts(emptyList(), hasMore = true))
    }
}
