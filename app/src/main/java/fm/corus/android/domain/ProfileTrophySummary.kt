package fm.corus.android.domain

/** Caller-scoped summary from the already-loaded profile; never merged into a public user. */
object ProfileTrophySummary {
    data class Counts(val trophies: Int?, val gifts: Int?)

    private val values = java.util.concurrent.ConcurrentHashMap<String, Pair<Long, Counts>>()
    fun remember(viewer: String?, profile: String, count: Int?, giftCount: Int? = null) {
        if (viewer == null) return
        val key = "$viewer:$profile"
        val counts = Counts(count?.takeIf { it >= 0 }, giftCount?.takeIf { it >= 0 })
        if (counts.trophies == null && counts.gifts == null) values.remove(key)
        else values[key] = System.currentTimeMillis() to counts
    }
    fun counts(viewer: String?, profile: String): Counts? = values["$viewer:$profile"]
        ?.takeIf { System.currentTimeMillis() - it.first < 300_000 }?.second
    fun count(viewer: String?, profile: String): Int? = counts(viewer, profile)?.trophies
}
