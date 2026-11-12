package fm.corus.android.ui.screens.messaging

import fm.corus.android.data.model.CymbalMessage
import fm.corus.android.data.model.CymbalThread
import fm.corus.android.data.model.MessageSendStatus
import fm.corus.android.data.model.MessageType
import fm.corus.android.data.repository.MessageRepository
import java.util.Date
import org.junit.Assert.assertEquals
import org.junit.Test

class OutgoingSharesTest {
    private val share = MessageRepository.OutgoingShare(
        destinationId = "peer",
        row = CymbalThread(id = "outgoing:peer", otherUserId = "peer", lastMessageText = "Song"),
        message = CymbalMessage(
            id = "send-id", threadId = "", fromUserId = "me", type = MessageType.SHARED_TRACK,
            createdAt = Date(100), sendStatus = MessageSendStatus.SENDING,
        ),
        deliver = { _, _ -> },
        onStart = {},
        onSent = {},
        onFailure = {},
    )

    @Test fun firstShareShowsBeforeTheThreadExists() {
        val rows = mergeOutgoingShares(emptyList(), listOf(share), "me")
        assertEquals(listOf("peer"), rows.map { it.otherUserId })
        assertEquals(MessageType.SHARED_TRACK, rows.single().lastMessageType)
        assertEquals(emptyList<CymbalThread>(), mergeOutgoingShares(emptyList(), listOf(share), "other"))
    }

    @Test fun provisionalShareMergesWithTheCanonicalThread() {
        val row = CymbalThread(id = "canonical", otherUserId = "peer", lastMessageAt = Date(50), isPinned = true, unreadCount = 2)
        val merged = mergeOutgoingShares(listOf(row), listOf(share), "me").single()
        assertEquals("canonical", merged.id)
        assertEquals(true, merged.isPinned)
        assertEquals(2, merged.unreadCount)
        assertEquals("Song", merged.lastMessageText)
        val newer = row.copy(lastMessageAt = Date(120))
        assertEquals(listOf(newer), mergeOutgoingShares(listOf(newer), listOf(share), "me"))
    }
}
