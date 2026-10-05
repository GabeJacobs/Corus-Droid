package fm.corus.android.domain

enum class ForYouTuningMode(val value: String) {
    ECLECTIC("tasteMatches"), BALANCED("balanced"), STAY_CLOSE("close");

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
    val paywallLocked: Boolean get() = unlocked && !hasFullAccess &&
        (serverPaywallLocked || trialEndsAt?.let { System.currentTimeMillis() >= it } == true)
    val canAccess: Boolean get() = unlocked && !paywallLocked
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
    val mode: ForYouTuningMode = ForYouTuningMode.BALANCED,
    val defaultMode: ForYouTuningMode = ForYouTuningMode.BALANCED,
    val stayCloseProgress: ForYouStayCloseProgress? = null,
) {
    fun resolve(enabled: Boolean, generation: Int): ForYouPrototypeState =
        if (this.generation != generation || hasPresentation) this
        else copy(enabled = enabled, hasPresentation = true)

    fun finish(generation: Int): ForYouPrototypeState =
        if (this.generation != generation) this else copy(hasPresentation = true)
}
