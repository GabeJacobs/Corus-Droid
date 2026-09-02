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
    // Group fields (present when isGroup). Direct threads leave these defaulted,
    // so existing 1:1 constructors/parsers are unaffected.
    val isGroup: Boolean = false,
    val groupName: String? = null,
    val groupPhotoURL: String? = null,
    val memberIds: List<String> = emptyList(),
    /** Resolved member profiles when available (callable rows); the live mirror
     *  doc carries only `memberIds`, resolved by the consumer. */
    val members: List<CymbalUser> = emptyList(),
    val createdBy: String? = null,
    /** True while the caller has the correspondent blocked. Stamped on the
     *  caller's own mirror row for as long as the block exists, so the row
     *  itself says whether it may be shown; callable rows never arrive blocked
     *  because the server drops them. */
    val blocked: Boolean = false,
    /** When the row itself last changed — a new message, or the caller reading
     *  the conversation. The live listener still windows by this; the inbox
     *  list itself is ordered and paged by [lastMessageAt]. Null when unknown:
     *  the callables return only the message time. */
    val updatedAt: Date? = null,
) {
    companion object {
        @Suppress("UNCHECKED_CAST")
        fun fromMap(id: String, data: Map<String, Any?>): CymbalThread {
            val otherUserData = data["otherUser"] as? Map<String, Any?>
            val otherUserId = data["otherUserId"] as? String ?: ""
            val otherUser = otherUserData?.let { CymbalUser.fromMap(otherUserId, it) }

            val timestampMs = data["lastMessageAt"] as? Number
            val lastMessageAt = if (timestampMs != null) Date(timestampMs.toLong()) else Date()

            val isGroup = data["type"] == "group"
            val memberIds = (data["memberIds"] as? List<*>)?.filterIsInstance<String>() ?: emptyList()
            // Callables hand back a resolved `members` map (uid -> profile);
            // mirror docs carry only ids.
            val members = (data["members"] as? Map<String, Any?>)?.mapNotNull { (uid, raw) ->
                (raw as? Map<String, Any?>)?.let { CymbalUser.fromMap(uid, it) }
            } ?: emptyList()

            return CymbalThread(
                id = id,
                otherUser = otherUser,
                otherUserId = otherUserId,
                lastMessageText = data["lastMessageText"] as? String ?: "",
                lastMessageType = MessageType.from(data["lastMessageType"] as? String),
                lastMessageAt = lastMessageAt,
                lastMessageFromUserId = data["lastMessageFromUserId"] as? String,
                unreadCount = (data["unreadCount"] as? Number)?.toInt() ?: 0,
                isGroup = isGroup,
                // Callables use name/photoURL; mirror docs use groupName/groupPhotoURL.
                groupName = (data["name"] ?: data["groupName"]) as? String,
                groupPhotoURL = (data["photoURL"] ?: data["groupPhotoURL"]) as? String,
                memberIds = memberIds,
                members = members,
                createdBy = data["createdBy"] as? String,
            )
        }
    }
}
