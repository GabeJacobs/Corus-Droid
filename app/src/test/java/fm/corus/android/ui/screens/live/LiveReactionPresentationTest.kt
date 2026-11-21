package fm.corus.android.ui.screens.live

import org.junit.Assert.*
import org.junit.Test

class LiveReactionPresentationTest {
    @Test fun pairsBothOrdersAndNeverReplaysAReaction() {
        for (roomFirst in listOf(true, false)) {
            val sync = LiveReactionPresentation()
            if (roomFirst) sync.reaction("a", 3, true, 0) else sync.messages(listOf("a"), 0)
            assertEquals(0L, sync.flush(0))
            assertFalse(sync.allows(listOf("a")))
            if (roomFirst) sync.messages(listOf("a"), 20) else sync.reaction("a", 3, true, 20)
            assertEquals(3L, sync.flush(20))
            assertTrue(sync.allows(listOf("a")))
            sync.reaction("a", 3, true, 30)
            assertEquals(0L, sync.flush(30))
        }
    }
    @Test fun groupedNoticesWaitForEveryRoomBatch() {
        val sync = LiveReactionPresentation()
        sync.messageGroups(listOf(listOf("a", "b")), 0)
        sync.reaction("a", 1, true, 10)
        assertEquals(0L, sync.flush(10))
        sync.reaction("b", 2, true, 20)
        assertEquals(3L, sync.flush(20))
        assertTrue(sync.allows(listOf("a", "b")))
    }
    @Test fun historyIsQuietAndChatOffHeartsDoNotWait() {
        val sync = LiveReactionPresentation()
        sync.baseline(listOf("history"))
        sync.reaction("history", 12, true, 0)
        sync.reaction("quiet", 2, false, 0)
        assertEquals(2L, sync.flush(0))
    }
    @Test fun boundsMissingStreamsAndPreservesLateHearts() {
        val sync = LiveReactionPresentation()
        sync.reaction("room-only", 2, true, 0)
        sync.messages(listOf("message-only"), 0)
        assertEquals(0L, sync.flush(499))
        assertEquals(2L, sync.flush(500))
        assertTrue(sync.allows(listOf("room-only", "message-only")))
        sync.reaction("message-only", 3, true, 700)
        assertEquals(3L, sync.flush(700))
        assertEquals(0L, sync.flush(1000))
    }
}
