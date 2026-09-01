package fm.corus.android.data.model

import java.util.Date

/** One Messages-section hit from `searchThreads`. `messageId` is empty for the last-message preview fallback. */
data class InboxMessageHit(
    val id: String,
    val thread: CymbalThread,
    val messageId: String = "",
    val fromUserId: String = "",
    val snippet: String = "",
    val createdAt: Date = Date(),
) {
    companion object {
        fun preview(from: CymbalThread) = InboxMessageHit(
            id = "preview:${from.id}",
            thread = from,
            messageId = "",
            fromUserId = from.lastMessageFromUserId ?: "",
            snippet = from.lastMessageText,
            createdAt = from.lastMessageAt,
        )
    }
}

data class InboxSearchResult(
    val threads: List<CymbalThread> = emptyList(),
    val messages: List<InboxMessageHit> = emptyList(),
)
