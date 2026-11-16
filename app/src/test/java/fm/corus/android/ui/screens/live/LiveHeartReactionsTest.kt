package fm.corus.android.ui.screens.live

import org.junit.Assert.assertEquals
import org.junit.Test

class LiveHeartReactionsTest {
    @Test fun roomUpdatesDoNotReplayLocalTapsAsIncomingHearts() {
        val previous = room("heartReactions" to 11L)
        val next = room(
            "heartReactions" to 16L,
            "recentHeartBatches" to listOf(
                mapOf("sender" to "local", "count" to 3L, "total" to 13L),
                mapOf("sender" to "other", "count" to 2L, "total" to 15L),
                mapOf("sender" to "local", "count" to 1L, "total" to 16L),
            ),
        )

        assertEquals(2L, next.remoteHeartsSince(previous, "local"))
        assertEquals(5L, next.remoteHeartsSince(previous, "host"))
    }

    @Test fun olderRoomSnapshotsUseLikesOnlyForTheSameTrackRevision() {
        val previous = room("revision" to 1L, "likes" to 2L)
        assertEquals(1L, room("revision" to 1L, "likes" to 3L).remoteHeartsSince(previous, "host"))
        assertEquals(0L, room("revision" to 2L, "likes" to 3L).remoteHeartsSince(previous, "host"))
    }

    private fun room(vararg fields: Pair<String, Any>) = LiveRoom("room", mapOf(*fields))
}
