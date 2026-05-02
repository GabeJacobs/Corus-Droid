package fm.corus.android.data.model

enum class MessageType(val value: String) {
    TEXT("text"),
    IMAGE("image"),
    GIF("gif"),
    SHARED_POST("sharedPost"),
    SHARED_TRACK("sharedTrack"),
    SHARED_FILM("sharedFilm");

    companion object {
        fun from(value: String?): MessageType =
            entries.firstOrNull { it.value == value } ?: TEXT
    }
}

enum class MessageSendStatus {
    SENDING, SENT, FAILED
}

/**
 * Per-message delivery state shown to the sender as a check / double-check
 * inside the bubble meta. Mirrors the web `deliveryStatus` discriminant.
 */
enum class MessageDeliveryStatus {
    SENDING, SENT, READ
}

enum class MessageFailureReason {
    GENERIC, MESSAGING_DISABLED
}

enum class MessagePrivacyOption(val value: String) {
    EVERYONE("everyone"),
    FOLLOWERS("followers"),
    FOLLOWING("following"),
    NOBODY("nobody");

    val displayLabel: String
        get() = when (this) {
            EVERYONE -> "Everyone"
            FOLLOWERS -> "My Followers"
            FOLLOWING -> "People I Follow"
            NOBODY -> "Nobody"
        }

    companion object {
        fun from(value: String?): MessagePrivacyOption =
            entries.firstOrNull { it.value == value } ?: EVERYONE
    }
}
