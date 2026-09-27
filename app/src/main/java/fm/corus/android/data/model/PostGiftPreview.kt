package fm.corus.android.data.model

import com.google.firebase.Timestamp

data class PostGiftPreview(
    val senderId: String,
    val senderUsername: String,
    val senderDisplayName: String,
    val senderAvatarUrl: String?,
    val giftType: String,
    val note: String?,
    val sentAtMs: Long,
) {
    companion object {
        fun fromMap(raw: Map<*, *>): PostGiftPreview? {
            val senderId = (raw["senderId"] as? String)?.takeIf { it.isNotBlank() } ?: return null
            val giftType = (raw["giftType"] as? String)?.takeIf { it.isNotBlank() } ?: return null
            val sentAtMs = when (val value = raw["sentAt"]) {
                is Number -> value.toLong()
                is Timestamp -> value.toDate().time
                else -> System.currentTimeMillis()
            }
            return PostGiftPreview(
                senderId = senderId,
                senderUsername = raw["senderUsername"] as? String ?: "",
                senderDisplayName = raw["senderDisplayName"] as? String ?: "",
                senderAvatarUrl = (raw["senderAvatarURL"] as? String)?.takeIf { it.isNotBlank() },
                giftType = giftType,
                note = (raw["note"] as? String)?.trim()?.takeIf { it.isNotEmpty() },
                sentAtMs = sentAtMs,
            )
        }
    }
}
