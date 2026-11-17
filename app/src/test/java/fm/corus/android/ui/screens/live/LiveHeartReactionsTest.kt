package fm.corus.android.ui.screens.live

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LiveHeartReactionsTest {
    @Test fun firstLikeShowsBeforeMembershipUpdatesAndAppliesOnlyToThatSong() {
        val playing = LiveUiState(room = room("revision" to 1L))
        assertFalse(playing.hasLikedCurrentSong)
        val tapped = playing.copy(optimisticLikedRevision = 1L)
        assertTrue(tapped.hasLikedCurrentSong)
        assertFalse(tapped.copy(room = room("revision" to 2L)).hasLikedCurrentSong)
        assertTrue(playing.copy(membership = mapOf("likedRevision" to 1L)).hasLikedCurrentSong)
    }

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

    @Test fun pendingLikeNoticeDoesNotMaskAnOutgoingMessageAtTheChatLimit() {
        fun message(index: Long) = LiveMessage("message-$index", mapOf("createdAt" to index, "text" to "chat"))
        val notice = LiveMessage("local-like", mapOf("kind" to "songLiked", "createdAt" to 201L, "revision" to 1L))
        val before = LiveUiState(
            uid = "listener",
            room = room("hostId" to "host", "revision" to 1L),
            messages = (1L..100L).map { message(it * 2) },
            pendingLikeNotice = PendingLiveLikeNotice(notice),
        )
        assertEquals("local-like", before.chatRows.last().id)
        val after = before.copy(messages = (2L..101L).map { message(it * 2) })
        assertEquals(listOf("local-like", "message-202"), after.chatRows.takeLast(2).map { it.id })
    }

    @Test fun confirmedSendCanFillAStaleFullHistoryWithoutAddingADuplicate() {
        fun message(index: Long) = LiveMessage("message-$index", mapOf("createdAt" to index, "text" to "chat"))
        val state = LiveUiState(messages = (1L..100L).map(::message))
        val sent = message(101L)
        val recovered = state.withConfirmedChatMessage(sent)
        assertEquals((2L..101L).map { message(it).id }, recovered.messages.map { it.id })
        assertEquals(recovered.messages, recovered.withConfirmedChatMessage(sent).messages)
    }

    private fun room(vararg fields: Pair<String, Any>) = LiveRoom("room", mapOf(*fields))
}
