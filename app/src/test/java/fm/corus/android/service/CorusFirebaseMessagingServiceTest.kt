package fm.corus.android.service

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Covers the pure decision logic behind active-thread push suppression: a DM
 * push is hidden only when it targets the exact thread the user is viewing.
 */
class CorusFirebaseMessagingServiceTest {

    @After
    fun tearDown() {
        ActiveThreadTracker.activeThreadId = null
    }

    @Test
    fun `suppresses message push for the actively-viewed thread`() {
        assertTrue(
            CorusFirebaseMessagingService.shouldSuppress(
                type = "message",
                threadId = "dm_abc",
                activeThreadId = "dm_abc",
            )
        )
    }

    @Test
    fun `does not suppress when viewing a different thread`() {
        assertFalse(
            CorusFirebaseMessagingService.shouldSuppress(
                type = "message",
                threadId = "dm_abc",
                activeThreadId = "dm_xyz",
            )
        )
    }

    @Test
    fun `does not suppress when no thread is active`() {
        assertFalse(
            CorusFirebaseMessagingService.shouldSuppress(
                type = "message",
                threadId = "dm_abc",
                activeThreadId = null,
            )
        )
    }

    @Test
    fun `does not suppress non-message types even if ids collide`() {
        // A like/comment push must never be hidden by an open DM thread.
        assertFalse(
            CorusFirebaseMessagingService.shouldSuppress(
                type = "like",
                threadId = "dm_abc",
                activeThreadId = "dm_abc",
            )
        )
    }

    @Test
    fun `does not suppress when payload threadId is missing or blank`() {
        assertFalse(
            CorusFirebaseMessagingService.shouldSuppress("message", null, "dm_abc")
        )
        assertFalse(
            CorusFirebaseMessagingService.shouldSuppress("message", "", "dm_abc")
        )
    }

    @Test
    fun `dm notification id is stable per thread and distinct across threads`() {
        assertEquals(
            CorusFirebaseMessagingService.dmNotificationId("dm_abc"),
            CorusFirebaseMessagingService.dmNotificationId("dm_abc"),
        )
        assertTrue(
            CorusFirebaseMessagingService.dmNotificationId("dm_abc") !=
                CorusFirebaseMessagingService.dmNotificationId("dm_xyz"),
        )
    }

    @Test
    fun `active thread tracker round-trips and clears`() {
        assertNull(ActiveThreadTracker.activeThreadId)
        ActiveThreadTracker.activeThreadId = "dm_abc"
        assertEquals("dm_abc", ActiveThreadTracker.activeThreadId)
        ActiveThreadTracker.activeThreadId = null
        assertNull(ActiveThreadTracker.activeThreadId)
    }
}
