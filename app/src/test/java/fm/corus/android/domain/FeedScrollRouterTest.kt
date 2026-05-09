package fm.corus.android.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Verifies the contract that backs the mini-player tap-to-scroll shortcut.
 * MainTabScreen invokes `handler?.invoke(postId)`; if it returns true, the
 * tap is consumed and the default "push PostDetail" navigation is skipped.
 *
 * Mirrors the iOS FeedView/.feedScrollToPost + ProfileFeedView/
 * .profileFeedScrollToPost dispatching, expressed as a single shared slot.
 */
class FeedScrollRouterTest {

    @Test
    fun `handler defaults to null when no screen has registered`() {
        val router = FeedScrollRouter()
        assertNull(router.handler)
    }

    @Test
    fun `handler returns true when registered screen claims the post`() {
        val router = FeedScrollRouter()
        var capturedPostId: String? = null
        router.handler = { postId ->
            capturedPostId = postId
            true
        }

        val result = router.handler?.invoke("post-123") == true

        assertTrue(result)
        assertEquals("post-123", capturedPostId)
    }

    @Test
    fun `handler returns false when registered screen cannot claim the post`() {
        val router = FeedScrollRouter()
        // Mirrors a feed whose loaded posts don't contain this id.
        router.handler = { false }

        val result = router.handler?.invoke("post-not-loaded") == true

        assertFalse(result)
    }

    @Test
    fun `last writer wins when multiple screens register`() {
        val router = FeedScrollRouter()
        val first: (String) -> Boolean = { false }
        val second: (String) -> Boolean = { true }

        router.handler = first
        router.handler = second

        assertSame(second, router.handler)
        assertTrue(router.handler?.invoke("any") == true)
    }

    @Test
    fun `clearing the handler restores the no-handler state`() {
        val router = FeedScrollRouter()
        router.handler = { true }
        router.handler = null

        assertNull(router.handler)
    }
}
