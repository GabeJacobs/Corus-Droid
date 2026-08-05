package fm.corus.android.domain

/** Server-side `users_v2.playlistTrialUsed` keys — one free try per context. */
enum class PlaylistTrialField {
    Feed,
    OwnProfile,
    OtherProfile,
    Hashtag,
}

data class PlaylistTrialUsed(
    val feed: Boolean = false,
    val ownProfile: Boolean = false,
    val otherProfile: Boolean = false,
    val hashtag: Boolean = false,
) {
    fun isUsed(field: PlaylistTrialField): Boolean = when (field) {
        PlaylistTrialField.Feed -> feed
        PlaylistTrialField.OwnProfile -> ownProfile
        PlaylistTrialField.OtherProfile -> otherProfile
        PlaylistTrialField.Hashtag -> hashtag
    }

    fun markUsed(field: PlaylistTrialField): PlaylistTrialUsed = when (field) {
        PlaylistTrialField.Feed -> copy(feed = true)
        PlaylistTrialField.OwnProfile -> copy(ownProfile = true)
        PlaylistTrialField.OtherProfile -> copy(otherProfile = true)
        PlaylistTrialField.Hashtag -> copy(hashtag = true)
    }

    companion object {
        fun fromFirestore(data: Map<String, Any?>?): PlaylistTrialUsed {
            val trial = data?.get("playlistTrialUsed") as? Map<*, *> ?: return PlaylistTrialUsed()
            return PlaylistTrialUsed(
                feed = trial["feed"] as? Boolean ?: false,
                ownProfile = trial["ownProfile"] as? Boolean ?: false,
                otherProfile = trial["otherProfile"] as? Boolean ?: false,
                hashtag = trial["hashtag"] as? Boolean ?: false,
            )
        }

        fun profileField(isOwnProfile: Boolean): PlaylistTrialField =
            if (isOwnProfile) PlaylistTrialField.OwnProfile else PlaylistTrialField.OtherProfile
    }
}

object PlaylistGatingUX {
    fun shouldPaywallPlaylist(
        used: PlaylistTrialUsed,
        field: PlaylistTrialField,
        hasFullAccess: Boolean,
    ): Boolean {
        if (hasFullAccess) return false
        return used.isUsed(field)
    }

    fun shouldShowFirstTimeConfirmation(confirmed: Boolean, hasFullAccess: Boolean): Boolean =
        !confirmed && !hasFullAccess

    fun appendTrialNoteIfAvailable(
        base: String,
        field: PlaylistTrialField,
        trialSuffix: String,
        used: PlaylistTrialUsed,
        hasFullAccess: Boolean,
    ): String {
        if (hasFullAccess || shouldPaywallPlaylist(used, field, hasFullAccess)) return base
        return "$base $trialSuffix"
    }
}
