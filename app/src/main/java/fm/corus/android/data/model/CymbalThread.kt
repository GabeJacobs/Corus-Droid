package fm.corus.android.data.model

import java.util.Date

data class CymbalThread(
    val id: String,
    val otherUser: CymbalUser? = null,
    val otherUserId: String = "",
    val lastMessageText: String = "",
    val lastMessageType: MessageType = MessageType.TEXT,
    val lastMessageAt: Date = Date(),
    val lastMessageFromUserId: String? = null,
    val unreadCount: Int = 0,
) {
    companion object {
        @Suppress("UNCHECKED_CAST")
        fun fromMap(id: String, data: Map<String, Any?>): CymbalThread {
            val otherUserData = data["otherUser"] as? Map<String, Any?>
            val otherUserId = data["otherUserId"] as? String ?: ""
            val otherUser = otherUserData?.let { CymbalUser.fromMap(otherUserId, it) }

            val timestampMs = data["lastMessageAt"] as? Number
            val lastMessageAt = if (timestampMs != null) Date(timestampMs.toLong()) else Date()

            return CymbalThread(
                id = id,
                otherUser = otherUser,
                otherUserId = otherUserId,
                lastMessageText = data["lastMessageText"] as? String ?: "",
                lastMessageType = MessageType.from(data["lastMessageType"] as? String),
                lastMessageAt = lastMessageAt,
                lastMessageFromUserId = data["lastMessageFromUserId"] as? String,
                unreadCount = (data["unreadCount"] as? Number)?.toInt() ?: 0,
            )
        }
    }
}
