package fm.corus.android.data.model

enum class NotificationType(val value: String) {
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
    TRENDING("trending");

    val supportsCommentActions: Boolean
        get() = this in listOf(COMMENT, REPLY, MENTION)

    companion object {
        fun from(value: String?): NotificationType =
            entries.firstOrNull { it.value == value } ?: LIKE
    }
}
