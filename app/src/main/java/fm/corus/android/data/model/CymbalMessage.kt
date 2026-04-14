package fm.corus.android.data.model

import com.google.firebase.Timestamp
import java.util.Date

data class CymbalMessage(
    val id: String,
    val threadId: String,
    val fromUserId: String,
    val text: String? = null,
    val type: MessageType = MessageType.TEXT,
    val mediaURL: String? = null,
    val createdAt: Date = Date(),
    val sendStatus: MessageSendStatus = MessageSendStatus.SENT,
    val sharedPostId: String? = null,
    val trackName: String? = null,
    val artistName: String? = null,
    val albumArtURL: String? = null,
    val spotifyURL: String? = null,
    val movieTitle: String? = null,
    val directorName: String? = null,
    val posterURL: String? = null,
    val tmdbWebURL: String? = null,
    val likedByUserIds: List<String> = emptyList(),
    val reactions: Map<String, List<String>> = emptyMap(),
    val replyToMessageId: String? = null,
    val replyToText: String? = null,
    val replyToUserId: String? = null,
    val failureReason: MessageFailureReason = MessageFailureReason.GENERIC,
) {
    companion object {
        @Suppress("UNCHECKED_CAST")
        fun fromMap(id: String, data: Map<String, Any?>): CymbalMessage {
            val timestampMs = data["createdAt"] as? Number
            val createdAt = if (timestampMs != null) Date(timestampMs.toLong()) else Date()

            return CymbalMessage(
                id = id,
                threadId = data["threadId"] as? String ?: "",
                fromUserId = data["fromUserId"] as? String ?: "",
                text = data["text"] as? String,
                type = MessageType.from(data["type"] as? String),
                mediaURL = data["mediaURL"] as? String,
                createdAt = createdAt,
                sharedPostId = data["sharedPostId"] as? String,
                trackName = data["trackName"] as? String,
                artistName = data["artistName"] as? String,
                albumArtURL = data["albumArtURL"] as? String,
                spotifyURL = data["spotifyURL"] as? String,
                movieTitle = data["movieTitle"] as? String,
                directorName = data["directorName"] as? String,
                posterURL = data["posterURL"] as? String,
                tmdbWebURL = data["tmdbWebURL"] as? String,
                likedByUserIds = data["likedByUserIds"] as? List<String> ?: emptyList(),
                reactions = (data["reactions"] as? Map<String, Any?>)?.mapValues { (_, v) ->
                    (v as? List<*>)?.filterIsInstance<String>() ?: emptyList()
                } ?: emptyMap(),
                replyToMessageId = data["replyToMessageId"] as? String,
                replyToText = data["replyToText"] as? String,
                replyToUserId = data["replyToUserId"] as? String,
            )
        }

        @Suppress("UNCHECKED_CAST")
        fun fromFirestoreDoc(id: String, threadId: String, data: Map<String, Any?>): CymbalMessage {
            val ts = data["createdAt"] as? Timestamp
            val createdAt = ts?.toDate() ?: Date()

            return CymbalMessage(
                id = id,
                threadId = threadId,
                fromUserId = data["fromUserId"] as? String ?: "",
                text = data["text"] as? String,
                type = MessageType.from(data["type"] as? String),
                mediaURL = data["mediaURL"] as? String,
                createdAt = createdAt,
                sharedPostId = data["sharedPostId"] as? String,
                trackName = data["trackName"] as? String,
                artistName = data["artistName"] as? String,
                albumArtURL = data["albumArtURL"] as? String,
                spotifyURL = data["spotifyURL"] as? String,
                movieTitle = data["movieTitle"] as? String,
                directorName = data["directorName"] as? String,
                posterURL = data["posterURL"] as? String,
                tmdbWebURL = data["tmdbWebURL"] as? String,
                likedByUserIds = data["likedByUserIds"] as? List<String> ?: emptyList(),
                reactions = (data["reactions"] as? Map<String, Any?>)?.mapValues { (_, v) ->
                    (v as? List<*>)?.filterIsInstance<String>() ?: emptyList()
                } ?: emptyMap(),
                replyToMessageId = data["replyToMessageId"] as? String,
                replyToText = data["replyToText"] as? String,
                replyToUserId = data["replyToUserId"] as? String,
            )
        }
    }
}
