package fm.corus.android.data.repository

internal data class CacheEntry<T>(
    val value: T,
    val fetchedAt: Long = System.currentTimeMillis(),
) {
    fun isValid(ttlMs: Long): Boolean = System.currentTimeMillis() - fetchedAt < ttlMs
}
