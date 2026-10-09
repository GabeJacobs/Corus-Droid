package fm.corus.android.domain

enum class ForYouTuningMode(val value: String) {
    ECLECTIC("tasteMatches"), BALANCED("balanced"), STAY_CLOSE("close");

    val playlistFeedMode: String
        get() = when (this) {
            ECLECTIC -> "forYouEclectic"
            BALANCED -> "forYouBalanced"
            STAY_CLOSE -> "forYouClose"
        }

    val playlistName: String
        get() = when (this) {
            ECLECTIC -> "Corus For You · Eclectic"
            BALANCED -> "Corus For You · Balanced"
            STAY_CLOSE -> "Corus For You · Stay Close"
        }

    val callableName: String
        get() = if (this == ECLECTIC) "getForYouEclecticFeed" else "getForYouPrototypeFeed"

    companion object {
        fun configured(value: String?): ForYouTuningMode =
            entries.firstOrNull { it.value == value?.trim() } ?: BALANCED

        fun initial(saved: String?, default: ForYouTuningMode): ForYouTuningMode =
            entries.firstOrNull { it.value == saved } ?: default
    }
}

data class ForYouStayCloseProgress(val postCount: Int = 0, val threshold: Int = 5,
    val hasFullAccess: Boolean = false, val serverPaywallLocked: Boolean = false,
    val trialEndsAt: Long? = null) {
    val paywallLocked: Boolean get() = !hasFullAccess &&
        (serverPaywallLocked || trialEndsAt?.let { System.currentTimeMillis() >= it } == true)
    val canAccess: Boolean get() = unlocked && !paywallLocked
    val canAccessBalanced: Boolean get() = canAccess
    val unlocked: Boolean get() = postCount >= threshold
    val remaining: Int get() = (threshold - postCount.coerceAtLeast(0)).coerceAtLeast(0)

    companion object {
        fun parse(data: Map<*, *>?): ForYouStayCloseProgress? = data?.let {
            ForYouStayCloseProgress((it["postCount"] as? Number)?.toInt()?.coerceAtLeast(0) ?: 0,
                (it["threshold"] as? Number)?.toInt()?.coerceAtLeast(1) ?: 5,
                it["hasFullAccess"] == true, it["paywallLocked"] == true,
                (it["trialEndsAt"] as? Number)?.toLong())
        }
    }
}

/** Presentation is settled once per account session. Late replies only warm the cache. */
data class ForYouPrototypeState(
    val uid: String? = null,
    val enabled: Boolean = false,
    val hasPresentation: Boolean = true,
    val generation: Int = 0,
    val mode: ForYouTuningMode = ForYouTuningMode.ECLECTIC,
    val defaultMode: ForYouTuningMode = ForYouTuningMode.BALANCED,
    val stayCloseProgress: ForYouStayCloseProgress? = null,
    val previewEndedNoticeHandled: Boolean = false,
) {
    val needsPreviewEndedNotice: Boolean
        get() = !previewEndedNoticeHandled && stayCloseProgress?.let {
            !it.hasFullAccess && it.trialEndsAt != null && it.paywallLocked
        } == true

    fun resolve(enabled: Boolean, generation: Int): ForYouPrototypeState =
        if (this.generation != generation || hasPresentation) this
        else copy(enabled = enabled, hasPresentation = true)

    fun finish(generation: Int): ForYouPrototypeState =
        if (this.generation != generation) this else copy(hasPresentation = true)
}
