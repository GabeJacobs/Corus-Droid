package fm.corus.android.data.local

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import fm.corus.android.data.model.CymbalUser
import fm.corus.android.data.model.MusicMatchData
import fm.corus.android.data.model.SharedMoviePreview
import fm.corus.android.data.model.SharedTrackPreview
import fm.corus.android.data.model.SuggestedUserMatch
import fm.corus.android.data.model.SuggestionReason
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

// ── Serializable DTOs for suggested matches persistence (matching iOS UserDefaults) ──

@Serializable
private data class PersistedSuggestedMatch(
    val userId: String,
    val username: String,
    val displayName: String,
    val avatarURL: String? = null,
    val avatarThumbURL: String? = null,
    val isVerified: Boolean = false,
    val isClubMember: Boolean = false,
    val isBot: Boolean = false,
    val botType: String? = null,
    val profileFlair: String = "checkmark",
    val followerCount: Int = 0,
    val followingCount: Int = 0,
    val cymbalCount: Int = 0,
    val bio: String = "",
    // Match data
    val similarityScore: Double = 0.0,
    val sharedPostedTracks: Int = 0,
    val sharedLikedTracks: Int = 0,
    val sharedArtists: Int = 0,
    val adjacentArtists: Int = 0,
    val sharedPostedMovies: Int = 0,
    val sharedLikedMovies: Int = 0,
    val sharedDirectors: Int = 0,
    val sharedHashtags: Int = 0,
    val mutualFollows: Int = 0,
    val trackPreviews: List<PersistedTrackPreview> = emptyList(),
    val moviePreviews: List<PersistedMoviePreview> = emptyList(),
    // Suggestion reason
    val mutualNames: List<String> = emptyList(),
)

@Serializable
private data class PersistedTrackPreview(
    val trackId: String,
    val trackName: String,
    val artistName: String,
    val albumArtURL: String? = null,
    val posterURL: String? = null,
    val isMovie: Boolean = false,
)

@Serializable
private data class PersistedMoviePreview(
    val movieId: String,
    val movieTitle: String,
    val directorName: String,
    val posterURL: String? = null,
)

@Serializable
private data class PersistedSuggestedMatchesWrapper(
    val fetchedAt: Long,
    val matches: List<PersistedSuggestedMatch>,
)

private fun SuggestedUserMatch.toPersisted() = PersistedSuggestedMatch(
    userId = user.id,
    username = user.username,
    displayName = user.displayName,
    avatarURL = user.avatarURL,
    avatarThumbURL = user.avatarThumbURL,
    isVerified = user.isVerified,
    isClubMember = user.isClubMember,
    isBot = user.isBot,
    botType = user.botType,
    profileFlair = user.profileFlair,
    followerCount = user.followerCount,
    followingCount = user.followingCount,
    cymbalCount = user.cymbalCount,
    bio = user.bio,
    similarityScore = matchData?.similarityScore ?: 0.0,
    sharedPostedTracks = matchData?.sharedPostedTracks ?: 0,
    sharedLikedTracks = matchData?.sharedLikedTracks ?: 0,
    sharedArtists = matchData?.sharedArtists ?: 0,
    adjacentArtists = matchData?.adjacentArtists ?: 0,
    sharedPostedMovies = matchData?.sharedPostedMovies ?: 0,
    sharedLikedMovies = matchData?.sharedLikedMovies ?: 0,
    sharedDirectors = matchData?.sharedDirectors ?: 0,
    sharedHashtags = matchData?.sharedHashtags ?: 0,
    mutualFollows = matchData?.mutualFollows ?: 0,
    trackPreviews = matchData?.sharedTrackPreviews?.map {
        PersistedTrackPreview(it.trackId, it.trackName, it.artistName, it.albumArtURL, it.posterURL, it.isMovie)
    } ?: emptyList(),
    moviePreviews = matchData?.sharedMoviePreviews?.map {
        PersistedMoviePreview(it.movieId, it.movieTitle, it.directorName, it.posterURL)
    } ?: emptyList(),
    mutualNames = suggestionReason?.mutualNames ?: emptyList(),
)

private fun PersistedSuggestedMatch.toModel() = SuggestedUserMatch(
    user = CymbalUser(
        id = userId,
        username = username,
        displayName = displayName,
        avatarURL = avatarURL,
        avatarThumbURL = avatarThumbURL,
        isVerified = isVerified,
        isClubMember = isClubMember,
        isBot = isBot,
        botType = botType,
        profileFlair = profileFlair,
        followerCount = followerCount,
        followingCount = followingCount,
        cymbalCount = cymbalCount,
        bio = bio,
    ),
    matchData = MusicMatchData(
        similarityScore = similarityScore,
        sharedPostedTracks = sharedPostedTracks,
        sharedLikedTracks = sharedLikedTracks,
        sharedArtists = sharedArtists,
        adjacentArtists = adjacentArtists,
        sharedPostedMovies = sharedPostedMovies,
        sharedLikedMovies = sharedLikedMovies,
        sharedDirectors = sharedDirectors,
        sharedHashtags = sharedHashtags,
        mutualFollows = mutualFollows,
        sharedTrackPreviews = trackPreviews.map {
            SharedTrackPreview(it.trackId, it.trackName, it.artistName, it.albumArtURL, it.posterURL, it.isMovie)
        },
        sharedMoviePreviews = moviePreviews.map {
            SharedMoviePreview(it.movieId, it.movieTitle, it.directorName, it.posterURL)
        },
    ),
    suggestionReason = if (mutualNames.isNotEmpty()) SuggestionReason(mutualNames) else null,
)

// ── Serializable DTO for recent search persistence ──

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
        val HAS_REQUESTED_PUSH_PERMISSION = booleanPreferencesKey("has_requested_push_permission")
        val RECENT_SEARCHES = stringPreferencesKey("recent_searches")
        val AUTOPLAY_NEXT_SONG = booleanPreferencesKey("autoplay_next_song")
    }

    val autoplayNextSong: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[AUTOPLAY_NEXT_SONG] ?: true
    }

    suspend fun setAutoplayNextSong(value: Boolean) {
        dataStore.edit { it[AUTOPLAY_NEXT_SONG] = value }
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

    val hasRequestedPushPermission: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[HAS_REQUESTED_PUSH_PERMISSION] ?: false
    }

    suspend fun setHasRequestedPushPermission() {
        dataStore.edit { it[HAS_REQUESTED_PUSH_PERMISSION] = true }
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

    // ── Suggested Matches (persisted for instant cold-start, matching iOS UserDefaults) ──

    suspend fun persistSuggestedMatches(matches: List<SuggestedUserMatch>, userId: String) {
        val key = stringPreferencesKey("suggestedMatches_$userId")
        val wrapper = PersistedSuggestedMatchesWrapper(
            fetchedAt = System.currentTimeMillis(),
            matches = matches.map { it.toPersisted() },
        )
        dataStore.edit { it[key] = recentJson.encodeToString(wrapper) }
    }

    /**
     * Returns the persisted suggested matches with their original fetchedAt timestamp,
     * or null if nothing is persisted. Caller is responsible for TTL validation.
     */
    suspend fun loadSuggestedMatchesAsync(userId: String): Pair<List<SuggestedUserMatch>, Long>? {
        val key = stringPreferencesKey("suggestedMatches_$userId")
        var result: Pair<List<SuggestedUserMatch>, Long>? = null
        dataStore.data.collect { prefs ->
            val raw = prefs[key]
            if (!raw.isNullOrBlank()) {
                try {
                    val wrapper = recentJson.decodeFromString<PersistedSuggestedMatchesWrapper>(raw)
                    result = Pair(wrapper.matches.map { it.toModel() }, wrapper.fetchedAt)
                } catch (_: Exception) { }
            }
            return@collect
        }
        return result
    }

    suspend fun clearSuggestedMatches(userId: String) {
        val key = stringPreferencesKey("suggestedMatches_$userId")
        dataStore.edit { it.remove(key) }
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
