package fm.corus.android.domain

/** Remote Config owns rollout visibility. Counts/preferences never grant access. */
object ProfileCollectionPolicy {
    fun visible(enabled: Boolean, viewer: String?, profile: String) = enabled && profile.isNotEmpty() && !viewer.isNullOrEmpty()
    fun hasGifts(count: Int?) = count != null && count > 0
    fun empty(trophies: Int?, gifts: Int?) = trophies == 0 && gifts == 0
    fun count(value: Any?): Int? = (value as? Number)?.toDouble()?.takeIf { it >= 0 && it <= Int.MAX_VALUE && it % 1 == 0.0 }?.toInt()
    private val giftTypes = setOf("corus_heart", "flowers", "mind_blown", "boombox", "disco_ball", "madvillain_mask")
    fun giftLineup(recent: List<String>, summary: List<String> = emptyList()) = (recent + summary).filter { it in giftTypes }.distinct().take(2)
}
