package fm.corus.android.ui.screens.messaging

import fm.corus.android.data.model.CymbalMessage
import fm.corus.android.data.model.MessageDeliveryStatus
import fm.corus.android.data.model.MessageSendStatus
import fm.corus.android.data.model.MessageType
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Date

class ComputeDeliveryStatusTest {

    private val me = "me"
    private val them = "them"

    private fun msg(
        id: String,
        from: String,
        status: MessageSendStatus = MessageSendStatus.SENT,
    ): CymbalMessage = CymbalMessage(
        id = id,
        threadId = "t",
        fromUserId = from,
        text = "hi",
        type = MessageType.TEXT,
        createdAt = Date(),
        sendStatus = status,
    )

    /** LazyColumn with reverseLayout = true: messages flow newest-first. */
    private fun newestFirst(vararg messages: CymbalMessage): List<CymbalMessage> = messages.reversed()

    @Test
    fun `pending message is sending`() {
        val m1 = msg("1", me, MessageSendStatus.SENDING)
        val status = computeDeliveryStatus(
            message = m1,
            currentUserId = me,
            messages = newestFirst(m1),
            recipientUnread = 0,
            myReadReceiptsEnabled = true,
        )
        assertEquals(MessageDeliveryStatus.SENDING, status)
    }

    @Test
    fun `incoming message is sent (no checkmark rendered for incoming)`() {
        val m1 = msg("1", them)
        val status = computeDeliveryStatus(
            message = m1,
            currentUserId = me,
            messages = newestFirst(m1),
            recipientUnread = 0,
            myReadReceiptsEnabled = true,
        )
        assertEquals(MessageDeliveryStatus.SENT, status)
    }

    @Test
    fun `recipientUnread zero means all my messages are read`() {
        val m1 = msg("1", me)
        val m2 = msg("2", me)
        val status = computeDeliveryStatus(
            message = m2,
            currentUserId = me,
            messages = newestFirst(m1, m2),
            recipientUnread = 0,
            myReadReceiptsEnabled = true,
        )
        assertEquals(MessageDeliveryStatus.READ, status)
    }

    @Test
    fun `most recent N messages are unread when recipientUnread is N`() {
        val m1 = msg("1", me)
        val m2 = msg("2", me)
        val m3 = msg("3", me)
        // Most recent 1 unread → m1, m2 = read; m3 = sent
        assertEquals(
            MessageDeliveryStatus.READ,
            computeDeliveryStatus(m1, me, newestFirst(m1, m2, m3), 1, true)
        )
        assertEquals(
            MessageDeliveryStatus.READ,
            computeDeliveryStatus(m2, me, newestFirst(m1, m2, m3), 1, true)
        )
        assertEquals(
            MessageDeliveryStatus.SENT,
            computeDeliveryStatus(m3, me, newestFirst(m1, m2, m3), 1, true)
        )
    }

    @Test
    fun `disabling read receipts forces all my messages to sent`() {
        val m1 = msg("1", me)
        val status = computeDeliveryStatus(
            message = m1,
            currentUserId = me,
            messages = newestFirst(m1),
            recipientUnread = 0,
            myReadReceiptsEnabled = false,
        )
        assertEquals(MessageDeliveryStatus.SENT, status)
    }

    @Test
    fun `incoming messages are excluded from read boundary calculation`() {
        val m1 = msg("1", me)
        val theirReply = msg("2", them)
        val m3 = msg("3", me)
        // Only my 2 messages count; recipientUnread=1 → m1 read, m3 sent
        assertEquals(
            MessageDeliveryStatus.READ,
            computeDeliveryStatus(m1, me, newestFirst(m1, theirReply, m3), 1, true)
        )
        assertEquals(
            MessageDeliveryStatus.SENT,
            computeDeliveryStatus(m3, me, newestFirst(m1, theirReply, m3), 1, true)
        )
    }

    @Test
    fun `null currentUserId returns sent`() {
        val m1 = msg("1", me)
        val status = computeDeliveryStatus(
            message = m1,
            currentUserId = null,
            messages = newestFirst(m1),
            recipientUnread = 0,
            myReadReceiptsEnabled = true,
        )
        assertEquals(MessageDeliveryStatus.SENT, status)
    }
}
