package fm.corus.android.data.model

enum class NotificationType(val value: String) {
    GIFT("gift"),
    GIFT_THANKS("gift_thanks"),
    LIKE("like"),
    COMMENT("comment"),
    COMMENT_LIKE("comment_like"),
    MENTION("mention"),
    TAG("tag"),
    SAVE("save"),
    FOLLOW("follow"),
    NEW_POST("new_post"),
    REPOST("repost"),
    REPLY("reply"),
    CONTACT_JOINED("contact_joined"),
    TASTE_MATCH("taste_match"),
    FAVORITE("favorite"),
    PLAY_MILESTONE("play_milestone"),
    TRENDING("trending"),
    NEW_RELEASE("new_release"),
    CONCERT_ANNOUNCEMENT("concert_announcement"),
    CONCERT_GOING("concert_going");

    val isDiscovery: Boolean get() = this == NEW_RELEASE || this == CONCERT_ANNOUNCEMENT

    val supportsCommentActions: Boolean
        get() = this in listOf(COMMENT, REPLY, MENTION)

    val isGift: Boolean get() = this == GIFT || this == GIFT_THANKS

    companion object {
        fun from(value: String?): NotificationType =
            entries.firstOrNull { it.value == value } ?: LIKE
    }
}
