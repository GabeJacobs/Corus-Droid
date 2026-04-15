package fm.corus.android.data.model

import java.util.Date

data class CymbalComment(
    val id: String,
    val user: CymbalUser,
    val text: String,
    val timestamp: Date = Date(),
    val likeCount: Int = 0,
    val parentCommentId: String? = null,
    val replyToUser: CymbalUser? = null,
    val replyCount: Int = 0,
    val gifURL: String? = null,
    val editedAt: Date? = null,
) {
    val isEdited: Boolean get() = editedAt != null

    companion object {
        @Suppress("UNCHECKED_CAST")
        fun fromMap(data: Map<String, Any?>): CymbalComment {
            val userData = data["user"] as? Map<String, Any?> ?: emptyMap()
            val userId = userData["id"] as? String ?: ""
            val user = CymbalUser.fromMap(userId, userData)

            val replyToUserData = data["replyToUser"] as? Map<String, Any?>
            val replyToUser = replyToUserData?.let {
                val rtId = it["id"] as? String ?: ""
                CymbalUser.fromMap(rtId, it)
            }

            val timestampMs = data["createdAt"] as? Number ?: data["timestamp"] as? Number  // server sends createdAt
            val timestamp = if (timestampMs != null) Date(timestampMs.toLong()) else Date()

            val editedAtMs = data["editedAt"] as? Number
            val editedAt = if (editedAtMs != null) Date(editedAtMs.toLong()) else null

            return CymbalComment(
                id = data["id"] as? String ?: "",
                user = user,
                text = data["text"] as? String ?: "",
                timestamp = timestamp,
                likeCount = (data["likeCount"] as? Number)?.toInt() ?: 0,
                parentCommentId = data["parentCommentId"] as? String,
                replyToUser = replyToUser,
                replyCount = (data["replyCount"] as? Number)?.toInt() ?: 0,
                gifURL = data["gifURL"] as? String,
                editedAt = editedAt,
            )
        }
    }
}
