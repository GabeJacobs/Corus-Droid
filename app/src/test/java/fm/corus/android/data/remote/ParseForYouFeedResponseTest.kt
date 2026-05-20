package fm.corus.android.data.remote

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure parser tests for the `getForYouFeed` Cloud Function payload. Kept
 * lean — we only verify the shape-mapping and the defensive defaults for
 * older/forked server responses. Heavy mocks against FirebaseFunctions are
 * intentionally avoided per the project's "Mockito tests fail from CLI"
 * note in auto-memory.
 */
class ParseForYouFeedResponseTest {

    @Test
    fun `null payload yields empty defaults`() {
        val (posts, hasMore, token, fellBack) = parseForYouFeedResponse(null)
        assertTrue(posts.isEmpty())
        assertFalse(hasMore)
        assertEquals("", token)
        assertFalse(fellBack)
    }

    @Test
    fun `empty map yields empty posts and false hasMore`() {
        val (posts, hasMore, token, fellBack) = parseForYouFeedResponse(emptyMap())
        assertTrue(posts.isEmpty())
        assertFalse(hasMore)
        assertEquals("", token)
        assertFalse(fellBack)
    }

    @Test
    fun `fully populated payload round-trips`() {
        val payload = mapOf(
            "posts" to listOf(mapOf("id" to "p1"), mapOf("id" to "p2")),
            "hasMore" to true,
            "sessionToken" to "sess-abc",
            "fellBackToFollowing" to false,
        )
        val (posts, hasMore, token, fellBack) = parseForYouFeedResponse(payload)
        assertEquals(2, posts.size)
        assertEquals("p1", posts[0]["id"])
        assertTrue(hasMore)
        assertEquals("sess-abc", token)
        assertFalse(fellBack)
    }

    @Test
    fun `cold-start fallback flag is parsed`() {
        val payload = mapOf(
            "posts" to emptyList<Map<String, Any?>>(),
            "hasMore" to false,
            "sessionToken" to "",
            "fellBackToFollowing" to true,
        )
        val (_, _, _, fellBack) = parseForYouFeedResponse(payload)
        assertTrue(fellBack)
    }

    @Test
    fun `posts field of wrong type degrades to empty rather than throwing`() {
        val payload = mapOf<String, Any?>(
            "posts" to "not-a-list",
            "hasMore" to true,
            "sessionToken" to "sess",
        )
        val (posts, hasMore, token, _) = parseForYouFeedResponse(payload)
        assertTrue(posts.isEmpty())
        assertTrue(hasMore)
        assertEquals("sess", token)
    }
}
