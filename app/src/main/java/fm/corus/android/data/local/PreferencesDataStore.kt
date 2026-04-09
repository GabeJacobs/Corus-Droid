package fm.corus.android.data.local

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PreferencesDataStore @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) {
    companion object {
        val FEED_ONE_PER_FOLLOWER = booleanPreferencesKey("feed_one_per_follower")
        val LAST_COMPOSE_MEDIA_TYPE = stringPreferencesKey("last_compose_media_type")
        val CONTACTS_SYNC_STATUS = stringPreferencesKey("contacts_sync_status")
        val DARK_MODE = booleanPreferencesKey("dark_mode")
        val HAS_SEEN_FIRST_POST_PAYWALL = booleanPreferencesKey("has_seen_first_post_paywall")
        val HAS_SEEN_TENTH_POST_PAYWALL = booleanPreferencesKey("has_seen_tenth_post_paywall")
        val RECENT_SEARCHES = stringPreferencesKey("recent_searches")
    }

    val feedOnePerFollower: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[FEED_ONE_PER_FOLLOWER] ?: false
    }

    suspend fun setFeedOnePerFollower(value: Boolean) {
        dataStore.edit { it[FEED_ONE_PER_FOLLOWER] = value }
    }

    val lastComposeMediaType: Flow<String> = dataStore.data.map { prefs ->
        prefs[LAST_COMPOSE_MEDIA_TYPE] ?: "track"
    }

    suspend fun setLastComposeMediaType(value: String) {
        dataStore.edit { it[LAST_COMPOSE_MEDIA_TYPE] = value }
    }

    val contactsSyncStatus: Flow<String> = dataStore.data.map { prefs ->
        prefs[CONTACTS_SYNC_STATUS] ?: "notAsked"
    }

    suspend fun setContactsSyncStatus(value: String) {
        dataStore.edit { it[CONTACTS_SYNC_STATUS] = value }
    }

    val darkMode: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[DARK_MODE] ?: false
    }

    suspend fun setDarkMode(value: Boolean) {
        dataStore.edit { it[DARK_MODE] = value }
    }

    val hasSeenFirstPostPaywall: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[HAS_SEEN_FIRST_POST_PAYWALL] ?: false
    }

    suspend fun setHasSeenFirstPostPaywall() {
        dataStore.edit { it[HAS_SEEN_FIRST_POST_PAYWALL] = true }
    }

    val hasSeenTenthPostPaywall: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[HAS_SEEN_TENTH_POST_PAYWALL] ?: false
    }

    suspend fun setHasSeenTenthPostPaywall() {
        dataStore.edit { it[HAS_SEEN_TENTH_POST_PAYWALL] = true }
    }

    // ── Recent Searches ──

    val recentSearches: Flow<List<String>> = dataStore.data.map { prefs ->
        val raw = prefs[RECENT_SEARCHES] ?: ""
        if (raw.isBlank()) emptyList() else raw.split(",")
    }

    suspend fun addRecentSearch(query: String) {
        val trimmed = query.trim()
        if (trimmed.isBlank()) return
        dataStore.edit { prefs ->
            val existing = (prefs[RECENT_SEARCHES] ?: "")
                .split(",")
                .filter { it.isNotBlank() }
            val updated = listOf(trimmed) + existing.filter { it != trimmed }
            prefs[RECENT_SEARCHES] = updated.take(10).joinToString(",")
        }
    }

    suspend fun clearRecentSearches() {
        dataStore.edit { it.remove(RECENT_SEARCHES) }
    }

    // ── Muted IDs (persisted for offline access, matching iOS UserDefaults pattern) ──

    suspend fun persistMutedIds(ids: Set<String>, userId: String) {
        val key = stringPreferencesKey("mutedIds_$userId")
        dataStore.edit { it[key] = ids.joinToString(",") }
    }

    fun loadMutedIds(userId: String): Set<String>? {
        // Synchronous read not available in DataStore — use runBlocking-free approach
        // This returns null on first call; prefetchMutedSet handles the async load
        return null
    }

    suspend fun loadMutedIdsAsync(userId: String): Set<String>? {
        val key = stringPreferencesKey("mutedIds_$userId")
        var result: Set<String>? = null
        dataStore.data.collect { prefs ->
            val raw = prefs[key]
            result = if (raw.isNullOrBlank()) null else raw.split(",").toSet()
            return@collect
        }
        return result
    }
}
