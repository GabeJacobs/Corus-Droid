package fm.corus.android.domain

/**
 * Visibility for the feed Favorites tab. Once unlocked, a later 0 from
 * profile hydration must not hide the tab — only logout clears it.
 * Mirrors iOS `FavoritesTabGate`.
 */
object FavoritesTabGate {
    fun showsTab(featureEnabled: Boolean, count: Int, unlocked: Boolean): Boolean =
        featureEnabled && (unlocked || count > 0)

    fun apply(
        incoming: Int,
        current: Int,
        unlocked: Boolean,
        allowZero: Boolean,
    ): Pair<Int, Boolean> {
        val next = incoming.coerceAtLeast(0)
        val nowUnlocked = unlocked || next > 0
        if (next == 0 && !allowZero && nowUnlocked) {
            return current to nowUnlocked
        }
        return next to nowUnlocked
    }
}
