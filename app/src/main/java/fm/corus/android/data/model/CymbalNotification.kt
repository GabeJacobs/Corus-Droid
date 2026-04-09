package fm.corus.android.data.model

import java.util.Date

data class CymbalNotification(
    val id: String,
    val type: NotificationType,
    val fromUser: CymbalUser,
    val postId: String? = null,
    val postAlbumArtURL: String? = null,
    val commentText: String? = null,
    val commentId: String? = null,
    val timestamp: Date = Date(),
    val isRead: Boolean = false,
) {
    val supportsCommentActions: Boolean
        get() = commentId != null && postId != null && type.supportsCommentActions

    val message: String
        get() = when (type) {
            NotificationType.LIKE -> "liked your corus."
            NotificationType.COMMENT -> if (commentText != null) "commented: $commentText" else "commented on your corus."
            NotificationType.COMMENT_LIKE -> "liked your comment."
            NotificationType.MENTION -> if (commentText != null) "mentioned you: $commentText" else "mentioned you in a comment."
            NotificationType.TAG -> "tagged you in a corus."
            NotificationType.SAVE -> "saved your corus."
            NotificationType.FOLLOW -> "started following you."
            NotificationType.NEW_POST -> "posted a new corus."
            NotificationType.REPLY -> if (commentText != null) "replied to your comment: $commentText" else "replied to your comment."
            NotificationType.CONTACT_JOINED -> "joined Corus!"
        }

    companion object {
        @Suppress("UNCHECKED_CAST")
        fun fromMap(id: String, data: Map<String, Any?>): CymbalNotification {
            val fromUserData = data["fromUser"] as? Map<String, Any?> ?: emptyMap()
            val fromUserId = fromUserData["id"] as? String ?: data["fromUserId"] as? String ?: ""
            val fromUser = CymbalUser.fromMap(fromUserId, fromUserData)

            val timestampMs = data["timestamp"] as? Number
            val timestamp = if (timestampMs != null) Date(timestampMs.toLong()) else Date()

            return CymbalNotification(
                id = id,
                type = NotificationType.from(data["type"] as? String),
                fromUser = fromUser,
                postId = data["postId"] as? String,
                postAlbumArtURL = data["postAlbumArtURL"] as? String,
                commentText = data["commentText"] as? String,
                commentId = data["commentId"] as? String,
                timestamp = timestamp,
                isRead = data["isRead"] as? Boolean ?: false,
            )
        }
    }
}
