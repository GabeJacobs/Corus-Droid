package fm.corus.android.ui.screens.messaging

import fm.corus.android.data.model.CymbalMessage
import fm.corus.android.data.model.MessageSendStatus
import fm.corus.android.data.model.MessageType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.Date

/**
 * Tests for the DM thread auto-scroll trigger.
 *
 * The newest bubble changes height in place when its send status flips (the
 * SENDING clock icon is dropped on SENT; an error + retry affordance appears on
 * FAILED) while the message count stays the same, so keying the auto-scroll on
 * `messages.size` alone would not re-pin the list. [autoScrollKey] must change
 * across those transitions and stay stable when only older messages change.
 *
 * The send "jump" Gabe reported on device was a separate root cause — the
 * optimistic copy being dropped on ack — covered by
 * `MessageThreadViewModelTest.optimistic message stays visible…`.
 */
class AutoScrollKeyTest {

    private fun msg(
        id: String,
        status: MessageSendStatus,
        createdAt: Date = Date(1_000L),
    ) = CymbalMessage(
        id = id,
        threadId = "t1",
        fromUserId = "user1",
        text = "hi",
        type = MessageType.TEXT,
        createdAt = createdAt,
        sendStatus = status,
    )

    @Test
    fun `key changes when the newest message is confirmed even though size is unchanged`() {
        val older = msg("old", MessageSendStatus.SENT)
        // Newest-first (reverseLayout order): the just-sent message at index 0.
        // The server reuses our client id as the doc id, so the id is unchanged.
        val sending = listOf(msg("c1", MessageSendStatus.SENDING), older)
        val confirmed = listOf(msg("c1", MessageSendStatus.SENT), older)

        // A size-keyed trigger would NOT fire across the confirm...
        assertEquals(sending.size, confirmed.size)
        // ...but the bubble's height changes, so the scroll key must.
        assertNotEquals(autoScrollKey(sending), autoScrollKey(confirmed))
    }

    @Test
    fun `key changes when a failed send is reflected`() {
        val sending = listOf(msg("c1", MessageSendStatus.SENDING))
        val failed = listOf(msg("c1", MessageSendStatus.FAILED))
        assertNotEquals(autoScrollKey(sending), autoScrollKey(failed))
    }

    @Test
    fun `key changes when a new newest message arrives`() {
        val before = listOf(msg("a", MessageSendStatus.SENT))
        val after = listOf(msg("b", MessageSendStatus.SENT), msg("a", MessageSendStatus.SENT))
        assertNotEquals(autoScrollKey(before), autoScrollKey(after))
    }

    @Test
    fun `key is stable when only older messages change`() {
        // Same newest message; an older bubble gains a reaction / edit. No re-scroll.
        val newest = msg("z", MessageSendStatus.SENT, createdAt = Date(5_000L))
        val before = listOf(newest, msg("y", MessageSendStatus.SENT, createdAt = Date(1_000L)))
        val after = listOf(newest, msg("y", MessageSendStatus.SENT, createdAt = Date(1_000L)).copy(text = "edited"))
        assertEquals(autoScrollKey(before), autoScrollKey(after))
    }

    @Test
    fun `key is null for an empty thread`() {
        assertNull(autoScrollKey(emptyList()))
    }
}
