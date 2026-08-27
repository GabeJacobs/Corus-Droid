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
        featuredTab: String = "music",
    ) = CymbalUser(
        id = "u1",
        username = "alice",
        displayName = "Alice",
        trackCount = trackCount,
        movieCount = movieCount,
        isBot = isBot,
        botType = botType,
        featuredTab = featuredTab,
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

    // ── featuredTab=film: returns the logical Film index, falls back to Music ──

    @Test
    fun `featuredTab film selects Film when films exist`() {
        assertEquals(1, user(trackCount = 5, movieCount = 1, featuredTab = "film").preferredProfileSegment)
        assertEquals(1, user(trackCount = 0, movieCount = 1, featuredTab = "film").preferredProfileSegment)
        assertEquals(1, user(trackCount = 0, movieCount = 0, featuredTab = "film").preferredProfileSegment)
    }

    @Test
    fun `featuredTab film falls back to Music when no films but tracks exist`() {
        assertEquals(0, user(trackCount = 3, movieCount = 0, featuredTab = "film").preferredProfileSegment)
    }

    @Test
    fun `featuredTab film bots still default to 0`() {
        assertEquals(
            0,
            user(trackCount = 5, movieCount = 0, isBot = true, botType = "music", featuredTab = "film")
                .preferredProfileSegment,
        )
    }

    @Test
    fun `derived path with featuredTab film selects Music when only tracks loaded`() {
        val u = user(trackCount = null, movieCount = null, featuredTab = "film").copy(cymbalCount = 1)
        val posts = listOf(post(MediaType.TRACK))
        assertEquals(0, u.preferredProfileSegmentFromPosts(posts, hasMore = false))
    }

    @Test
    fun `derived path with featuredTab film selects Film when films are present`() {
        val u = user(trackCount = null, movieCount = null, featuredTab = "film").copy(cymbalCount = 2)
        val posts = listOf(post(MediaType.TRACK, "p1"), post(MediaType.MOVIE, "p2"))
        assertEquals(1, u.preferredProfileSegmentFromPosts(posts, hasMore = false))
    }

    @Test
    fun `hidden Film is skipped even when it is featured`() {
        val u = user(trackCount = 4, movieCount = 2, featuredTab = "film").copy(
            hiddenProfileTabs = listOf("film"),
        )
        assertEquals(0, u.preferredProfileSegment)
        assertEquals(listOf(0), u.visibleMediaTabIndices())
    }

    // ── fromMap defaulting ──

    @Test
    fun `fromMap defaults featuredTab to music when missing`() {
        val u = CymbalUser.fromMap("u1", mapOf("username" to "alice", "displayName" to "Alice"))
        assertEquals("music", u.featuredTab)
    }

    @Test
    fun `fromMap accepts film and rejects garbage`() {
        val film = CymbalUser.fromMap("u1", mapOf("username" to "a", "displayName" to "A", "featuredTab" to "film"))
        assertEquals("film", film.featuredTab)
        val junk = CymbalUser.fromMap("u1", mapOf("username" to "a", "displayName" to "A", "featuredTab" to "wat"))
        assertEquals("music", junk.featuredTab)
    }

    @Test
    fun `fromMap reads tab order and hidden tabs`() {
        val u = CymbalUser.fromMap(
            "u1",
            mapOf(
                "username" to "a",
                "displayName" to "A",
                "featuredTab" to "film",
                "profileTabOrder" to listOf("film", "music", "wat"),
                "hiddenProfileTabs" to listOf("music"),
            ),
        )
        assertEquals(listOf("film", "music", "wat"), u.profileTabOrder)
        assertEquals(listOf("music"), u.hiddenProfileTabs)
        assertEquals(listOf(1), u.visibleMediaTabIndices())
    }
}
