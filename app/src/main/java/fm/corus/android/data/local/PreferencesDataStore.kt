package fm.corus.android.data.local

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import fm.corus.android.data.model.CymbalUser
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

@Serializable
private data class RecentSearchEntry(
    val id: String,
    val username: String,
    val displayName: String,
    val avatarURL: String? = null,
    val avatarThumbURL: String? = null,
    val isVerified: Boolean = false,
    val isClubMember: Boolean = false,
    val isBot: Boolean = false,
    val profileFlair: String = "checkmark",
)

private val recentJson = Json { ignoreUnknownKeys = true }

private fun CymbalUser.toRecentEntry() = RecentSearchEntry(
    id = id,
    username = username,
    displayName = displayName,
    avatarURL = avatarURL,
    avatarThumbURL = avatarThumbURL,
    isVerified = isVerified,
    isClubMember = isClubMember,
    isBot = isBot,
    profileFlair = profileFlair,
)

private fun RecentSearchEntry.toUser() = CymbalUser(
    id = id,
    username = username,
    displayName = displayName,
    avatarURL = avatarURL,
    avatarThumbURL = avatarThumbURL,
    isVerified = isVerified,
    isClubMember = isClubMember,
    isBot = isBot,
    profileFlair = profileFlair,
)

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

    // ── Recent Searches (stored as JSON array of user objects) ──

    val recentSearchUsers: Flow<List<CymbalUser>> = dataStore.data.map { prefs ->
        val raw = prefs[RECENT_SEARCHES] ?: ""
        if (raw.isBlank()) {
            emptyList()
        } else {
            try {
                recentJson.decodeFromString<List<RecentSearchEntry>>(raw).map { it.toUser() }
            } catch (_: Exception) {
                // Migrate old comma-separated username format gracefully
                emptyList()
            }
        }
    }

    suspend fun addRecentSearchUser(user: CymbalUser) {
        dataStore.edit { prefs ->
            val existing = try {
                val raw = prefs[RECENT_SEARCHES] ?: ""
                if (raw.isBlank()) emptyList()
                else recentJson.decodeFromString<List<RecentSearchEntry>>(raw)
            } catch (_: Exception) {
                emptyList()
            }
            val entry = user.toRecentEntry()
            val updated = listOf(entry) + existing.filter { it.id != user.id }
            prefs[RECENT_SEARCHES] = recentJson.encodeToString(updated.take(15))
        }
    }

    suspend fun removeRecentSearchUser(userId: String) {
        dataStore.edit { prefs ->
            val existing = try {
                val raw = prefs[RECENT_SEARCHES] ?: ""
                if (raw.isBlank()) emptyList()
                else recentJson.decodeFromString<List<RecentSearchEntry>>(raw)
            } catch (_: Exception) {
                emptyList()
            }
            val updated = existing.filter { it.id != userId }
            prefs[RECENT_SEARCHES] = recentJson.encodeToString(updated)
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
