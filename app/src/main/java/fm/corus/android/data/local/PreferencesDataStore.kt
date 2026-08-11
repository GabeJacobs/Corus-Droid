package fm.corus.android.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import dagger.hilt.android.qualifiers.ApplicationContext
import fm.corus.android.data.model.CymbalUser
import fm.corus.android.data.model.MusicMatchData
import fm.corus.android.data.model.RecentSearchItem
import fm.corus.android.data.model.SharedMoviePreview
import fm.corus.android.data.model.SharedTrackPreview
import fm.corus.android.data.model.SuggestedUserMatch
import fm.corus.android.data.model.SuggestionReason
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
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

/**
 * Persisted first page of the taste-matches rail for stale-while-revalidate.
 * Reuses [PersistedSuggestedMatch] (identical shape) but also carries the
 * cursor + hasMore so the rail can resume paging from the cached page. Mirrors
 * iOS `CachedTasteMatchesPage`.
 */
@Serializable
private data class PersistedTasteMatchesPageWrapper(
    val fetchedAt: Long,
    val nextCursor: String? = null,
    val hasMore: Boolean = false,
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

// ── Recent search persistence ──
// Recents are stored as a `kind`-discriminated polymorphic JSON array (see
// [RecentSearchItem]). Old builds stored a users-only array with NO discriminator;
// [LegacyRecentUserEntry] decodes that shape as a fallback so existing recents
// survive the upgrade instead of being wiped.

@Serializable
private data class LegacyRecentUserEntry(
    val id: String,
    val username: String = "",
    val displayName: String = "",
    val avatarURL: String? = null,
    val avatarThumbURL: String? = null,
    val isVerified: Boolean = false,
    val isClubMember: Boolean = false,
    val isBot: Boolean = false,
    val profileFlair: String = "checkmark",
) {
    fun toItem() = RecentSearchItem.UserEntry(
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
}

private val recentJson = Json { ignoreUnknownKeys = true }

/** Polymorphic codec for the recents array — writes/reads the `kind` tag. */
private val recentSearchJson = Json {
    ignoreUnknownKeys = true
    classDiscriminator = "kind"
}

@Singleton
class PreferencesDataStore @Inject constructor(
    private val dataStore: DataStore<Preferences>,
    @ApplicationContext private val context: Context,
) {
    companion object {
        val FEED_ONE_PER_FOLLOWER = booleanPreferencesKey("feed_one_per_follower")
        val LAST_COMPOSE_MEDIA_TYPE = stringPreferencesKey("last_compose_media_type")
        val CONTACTS_SYNC_STATUS = stringPreferencesKey("contacts_sync_status")
        val APPEARANCE_MODE = stringPreferencesKey("appearance_mode")
        val HAS_SEEN_FIRST_POST_PAYWALL = booleanPreferencesKey("has_seen_first_post_paywall")
        val HAS_SEEN_TENTH_POST_PAYWALL = booleanPreferencesKey("has_seen_tenth_post_paywall")
        val HAS_REQUESTED_PUSH_PERMISSION = booleanPreferencesKey("has_requested_push_permission")
        val HAS_TAPPED_ALBUM_ART = booleanPreferencesKey("has_tapped_album_art")
        val HAS_CONFIRMED_FEED_PLAYLIST = booleanPreferencesKey("has_confirmed_feed_playlist")
        val RECENT_SEARCHES = stringPreferencesKey("recent_searches")
        val AUTOPLAY_NEXT_SONG = booleanPreferencesKey("autoplay_next_song")
        val PLAY_FULL_SONGS = booleanPreferencesKey("play_full_songs")
        val ALWAYS_PLAY_FULL_SONGS = booleanPreferencesKey("always_play_full_songs")
        val PLAYBACK_MODE_CHOSEN = booleanPreferencesKey("playback_mode_chosen")
        val AUTO_ADD_SPOTIFY = booleanPreferencesKey("auto_add_saved_to_spotify")
        val FEED_FOLLOWS_NOW_PLAYING = booleanPreferencesKey("feed_follows_now_playing")
        val TRENDING_SONGS_WINDOW = stringPreferencesKey("trending_songs_window")
        val TRENDING_FILMS_WINDOW = stringPreferencesKey("trending_films_window")
        val TRENDING_HASHTAGS_WINDOW = stringPreferencesKey("trending_hashtags_window")
        // For You feed mode + seen-IDs ring buffer (cap 500, JSON-encoded).
        val FEED_MODE = stringPreferencesKey("feed_mode")
        // Synchronous mirror of FEED_MODE's raw value, in the same launch-critical
        // SharedPreferences store as AppearanceDefaultMigration, so the header icon
        // can be seeded without awaiting DataStore (avoids a Following→ranked flash).
        private const val SYNC_PREFS_NAME = "corus_prefs"
        private const val FEED_MODE_SYNC_KEY = "feed_mode"
        val FOR_YOU_SEEN_IDS = stringPreferencesKey("for_you_seen_ids")
        val PENDING_SPOTIFY_LIBRARY_URIS = stringPreferencesKey("pending_spotify_library_uris")
        // Active home-feed content filter (FeedFilter enum name). Mirrors iOS
        // @AppStorage("feedFilter"); persists the music/film/new-releases choice
        // across app restarts.
        val FEED_FILTER = stringPreferencesKey("feed_filter")
        // Synchronous mirror of FEED_FILTER's raw value, in the same
        // launch-critical SYNC_PREFS_NAME store as the feed mode seed, so the
        // ViewModel can seed the active filter without awaiting DataStore
        // (avoids an all/music → film flash on cold launch).
        private const val FEED_FILTER_SYNC_KEY = "feed_filter"
        val FEED_DECADE = stringPreferencesKey("feed_decade")
        private const val FEED_DECADE_SYNC_KEY = "feed_decade"
        // Synchronous mirror of LAST_COMPOSE_MEDIA_TYPE, same store/reasoning as
        // the two above: the unified compose picker orders its trending pair by
        // the last-posted medium, and that order must be right on the picker's
        // first frame or the sections visibly reshuffle under the user.
        private const val LAST_COMPOSE_MEDIA_TYPE_SYNC_KEY = "last_compose_media_type"
    }

    val trendingSongsWindow: Flow<String> = dataStore.data.map { prefs ->
        prefs[TRENDING_SONGS_WINDOW] ?: "week"
    }

    suspend fun setTrendingSongsWindow(value: String) {
        dataStore.edit { it[TRENDING_SONGS_WINDOW] = value }
    }

    val trendingFilmsWindow: Flow<String> = dataStore.data.map { prefs ->
        prefs[TRENDING_FILMS_WINDOW] ?: "week"
    }

    suspend fun setTrendingFilmsWindow(value: String) {
        dataStore.edit { it[TRENDING_FILMS_WINDOW] = value }
    }

    val trendingHashtagsWindow: Flow<String> = dataStore.data.map { prefs ->
        prefs[TRENDING_HASHTAGS_WINDOW] ?: "month"
    }

    suspend fun setTrendingHashtagsWindow(value: String) {
        dataStore.edit { it[TRENDING_HASHTAGS_WINDOW] = value }
    }

    val autoplayNextSong: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[AUTOPLAY_NEXT_SONG] ?: true
    }

    suspend fun setAutoplayNextSong(value: Boolean) {
        dataStore.edit { it[AUTOPLAY_NEXT_SONG] = value }
    }

    /** Mirrors iOS @AppStorage("playFullSongs") default (`false`). */
    val playFullSongs: Flow<Boolean> = dataStore.data.map { prefs ->
        (prefs[PLAY_FULL_SONGS] ?: false).also { mirrorPlayFullSongsSync(it) }
    }

    suspend fun setPlayFullSongs(value: Boolean) {
        mirrorPlayFullSongsSync(value)
        dataStore.edit {
            it[PLAY_FULL_SONGS] = value
            it[PLAYBACK_MODE_CHOSEN] = true
        }
    }

    fun playbackModeChosenSync(): Boolean =
        context.getSharedPreferences(SYNC_PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean("playback_mode_chosen", false)

    suspend fun setPlaybackModeChosen(value: Boolean) {
        context.getSharedPreferences(SYNC_PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putBoolean("playback_mode_chosen", value).apply()
        dataStore.edit { it[PLAYBACK_MODE_CHOSEN] = value }
    }

    private fun mirrorPlayFullSongsSync(value: Boolean) {
        context.getSharedPreferences(SYNC_PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putBoolean("play_full_songs", value).apply()
    }

    fun playFullSongsSync(): Boolean =
        context.getSharedPreferences(SYNC_PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean("play_full_songs", false)

    /**
     * Mirrors iOS `@AppStorage("alwaysPlayFullSongs")`.
     * Fresh installs default on (no mini-player toggle); existing installs are
     * pinned off by [AlwaysPlayFullSongsDefaultMigration].
     */
    val alwaysPlayFullSongs: Flow<Boolean> = dataStore.data.map { prefs ->
        (prefs[ALWAYS_PLAY_FULL_SONGS] ?: true).also { mirrorAlwaysPlayFullSongsSync(it) }
    }

    /**
     * Settings "Always Play Full Songs". Turning off also resets the mini-player
     * 30s/Full preference to 30s (iOS parity). Turning on does not rewrite
     * [playFullSongs] — routing uses [effectivePlayFullSongsSync].
     */
    suspend fun setAlwaysPlayFullSongs(value: Boolean) {
        mirrorAlwaysPlayFullSongsSync(value)
        dataStore.edit {
            it[ALWAYS_PLAY_FULL_SONGS] = value
            it[PLAYBACK_MODE_CHOSEN] = true
        }
        context.getSharedPreferences(SYNC_PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putBoolean("playback_mode_chosen", true).apply()
        if (!value) {
            setPlayFullSongs(false)
        }
    }

    private fun mirrorAlwaysPlayFullSongsSync(value: Boolean) {
        context.getSharedPreferences(SYNC_PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putBoolean("always_play_full_songs", value).apply()
    }

    fun alwaysPlayFullSongsSync(): Boolean =
        context.getSharedPreferences(SYNC_PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean("always_play_full_songs", true)

    /** Full playback on feed posts — always-on setting or mini-player toggle. */
    fun effectivePlayFullSongsSync(): Boolean =
        alwaysPlayFullSongsSync() || playFullSongsSync()

    /**
     * Opt-in: once the user turns on the Settings "Add Saved Songs to Library"
     * toggle for Spotify, every subsequent song save also adds the track to
     * their Spotify library over App Remote. Default OFF (opt-in). Mirrored to
     * SharedPreferences so Settings can seed the Switch without awaiting
     * DataStore (avoids an off→on flash when the user has it enabled).
     */
    val autoAddSavedToSpotify: Flow<Boolean> = dataStore.data.map { prefs ->
        (prefs[AUTO_ADD_SPOTIFY] ?: false).also { mirrorAutoAddSavedToSpotifySync(it) }
    }

    suspend fun setAutoAddSavedToSpotify(value: Boolean) {
        mirrorAutoAddSavedToSpotifySync(value)
        dataStore.edit { it[AUTO_ADD_SPOTIFY] = value }
    }

    private fun mirrorAutoAddSavedToSpotifySync(value: Boolean) {
        context.getSharedPreferences(SYNC_PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putBoolean("auto_add_saved_to_spotify", value).apply()
    }

    /** Synchronous seed for Settings — same default as the DataStore flow. */
    fun autoAddSavedToSpotifySync(): Boolean =
        context.getSharedPreferences(SYNC_PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean("auto_add_saved_to_spotify", false)

    /**
     * Spotify library saves that couldn't be delivered yet (JSON-encoded array
     * of track URIs, oldest first, capped by
     * [fm.corus.android.domain.SpotifyLibraryQueue.MAX_DEPTH]). App Remote can
     * only write the library while its IPC session is live, so saves made with
     * Spotify closed park here and drain on the next connect.
     */
    val pendingSpotifyLibraryUrisJson: Flow<String> = dataStore.data.map { prefs ->
        prefs[PENDING_SPOTIFY_LIBRARY_URIS] ?: "[]"
    }

    suspend fun setPendingSpotifyLibraryUrisJson(value: String) {
        dataStore.edit { it[PENDING_SPOTIFY_LIBRARY_URIS] = value }
    }

    /**
     * When enabled, the feed (and a profile/hashtag feed if currently visible)
     * scrolls to the post for the song that just started playing. Mirrors iOS
     * @AppStorage("feedFollowsNowPlaying"). Default ON.
     */
    val feedFollowsNowPlaying: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[FEED_FOLLOWS_NOW_PLAYING] ?: true
    }

    suspend fun setFeedFollowsNowPlaying(value: Boolean) {
        dataStore.edit { it[FEED_FOLLOWS_NOW_PLAYING] = value }
    }

    val feedOnePerFollower: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[FEED_ONE_PER_FOLLOWER] ?: false
    }

    suspend fun setFeedOnePerFollower(value: Boolean) {
        dataStore.edit { it[FEED_ONE_PER_FOLLOWER] = value }
    }

    /**
     * Per-device feed mode toggle ("following" | "trending" | "favorites" |
     * "tasteMatches"). Empty string = the user has never explicitly picked a
     * mode, in which case the ViewModel opens on Following. Once the user taps
     * a mode it's persisted here and sticks.
     *
     * The emitted raw value is also write-through mirrored to the synchronous
     * `corus_prefs` store so [feedModeSyncSeed] can seed the header icon on the
     * launch-critical path without awaiting DataStore — otherwise the icon
     * flashes Following before the persisted ranked mode loads. Same store and
     * rationale as [AppearanceDefaultMigration].
     */
    val feedMode: Flow<String> = dataStore.data.map { prefs ->
        (prefs[FEED_MODE] ?: "").also { mirrorFeedModeSync(it) }
    }

    suspend fun setFeedMode(value: String) {
        mirrorFeedModeSync(value)
        dataStore.edit { it[FEED_MODE] = value }
    }

    /**
     * Synchronously seed the launch-critical feed-mode mirror (and queue the
     * DataStore write) so a post-onboarding default can take effect before the
     * next frame's FeedViewModel seed — without awaiting the suspend write.
     * Prefer [setFeedMode] for normal interactive switches.
     */
    fun setFeedModeImmediate(value: String, scope: CoroutineScope) {
        mirrorFeedModeSync(value)
        scope.launch { dataStore.edit { it[FEED_MODE] = value } }
    }

    private fun mirrorFeedModeSync(raw: String) {
        context.getSharedPreferences(SYNC_PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putString(FEED_MODE_SYNC_KEY, raw).apply()
    }

    /**
     * Synchronous best-effort read of the persisted raw feed mode, used to seed
     * UI before the async [feedMode] flow emits. Empty string = unknown (the
     * user never picked a mode, or an existing install whose value hasn't been
     * mirrored from DataStore yet); the caller resolves it the same way the flow
     * does. Mirror is kept current by [feedMode] / [setFeedMode].
     */
    fun feedModeSyncSeed(): String =
        context.getSharedPreferences(SYNC_PREFS_NAME, Context.MODE_PRIVATE)
            .getString(FEED_MODE_SYNC_KEY, null) ?: ""

    /**
     * Ring buffer of recently-served For You post IDs (JSON-encoded array
     * of strings, capped at 500). Sent to `getForYouFeed` so a fresh
     * session can suppress already-shown posts.
     */
    val forYouSeenIdsJson: Flow<String> = dataStore.data.map { prefs ->
        prefs[FOR_YOU_SEEN_IDS] ?: "[]"
    }

    suspend fun setForYouSeenIdsJson(value: String) {
        dataStore.edit { it[FOR_YOU_SEEN_IDS] = value }
    }

    /**
     * Persisted home-feed content filter, stored as the FeedFilter enum name
     * ("ALL" | "MUSIC" | "FILM" | "MUSIC_NEW_RELEASES" | "FILM_NEW_RELEASES").
     * Defaults to "ALL". Mirrors iOS @AppStorage("feedFilter").
     *
     * The emitted raw value is also write-through mirrored to the synchronous
     * `corus_prefs` store so [feedFilterSyncSeed] can seed the active filter on
     * the launch-critical path without awaiting DataStore — otherwise a saved
     * film/music selection flashes all-content posts before the persisted
     * filter loads. Same store and rationale as the feed mode seed.
     */
    val feedFilter: Flow<String> = dataStore.data.map { prefs ->
        (prefs[FEED_FILTER] ?: "ALL").also { mirrorFeedFilterSync(it) }
    }

    suspend fun setFeedFilter(value: String) {
        mirrorFeedFilterSync(value)
        dataStore.edit { it[FEED_FILTER] = value }
    }

    private fun mirrorFeedFilterSync(raw: String) {
        context.getSharedPreferences(SYNC_PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putString(FEED_FILTER_SYNC_KEY, raw).apply()
    }

    /**
     * Synchronous best-effort read of the persisted raw feed filter, used to
     * seed the ViewModel's active filter before the async [feedFilter] flow
     * emits. Returns "ALL" when unknown (fresh install, or an existing install
     * whose value hasn't been mirrored from DataStore yet) — the same default
     * the flow uses. Mirror is kept current by [feedFilter] / [setFeedFilter].
     */
    fun feedFilterSyncSeed(): String =
        context.getSharedPreferences(SYNC_PREFS_NAME, Context.MODE_PRIVATE)
            .getString(FEED_FILTER_SYNC_KEY, null) ?: "ALL"

    val feedDecade: Flow<String> = dataStore.data.map { prefs ->
        (prefs[FEED_DECADE] ?: "").also { mirrorFeedDecadeSync(it) }
    }

    suspend fun setFeedDecade(value: String) {
        mirrorFeedDecadeSync(value)
        dataStore.edit { it[FEED_DECADE] = value }
    }

    private fun mirrorFeedDecadeSync(raw: String) {
        context.getSharedPreferences(SYNC_PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putString(FEED_DECADE_SYNC_KEY, raw).apply()
    }

    fun feedDecadeSyncSeed(): String =
        context.getSharedPreferences(SYNC_PREFS_NAME, Context.MODE_PRIVATE)
            .getString(FEED_DECADE_SYNC_KEY, null) ?: ""

    /**
     * The medium ("track" / "movie") of the user's last pick in the compose
     * picker. Mirrored into the synchronous `corus_prefs` store on every read
     * and write so [lastComposeMediaTypeSyncSeed] can order the unified
     * picker's trending pair without awaiting DataStore.
     */
    val lastComposeMediaType: Flow<String> = dataStore.data.map { prefs ->
        (prefs[LAST_COMPOSE_MEDIA_TYPE] ?: "track").also { mirrorLastComposeMediaTypeSync(it) }
    }

    suspend fun setLastComposeMediaType(value: String) {
        // Mirror FIRST: the picker reads the seed synchronously on its next
        // open, which can easily beat the DataStore write's commit.
        mirrorLastComposeMediaTypeSync(value)
        dataStore.edit { it[LAST_COMPOSE_MEDIA_TYPE] = value }
    }

    private fun mirrorLastComposeMediaTypeSync(raw: String) {
        context.getSharedPreferences(SYNC_PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putString(LAST_COMPOSE_MEDIA_TYPE_SYNC_KEY, raw).apply()
    }

    /**
     * Synchronous best-effort read of the last-posted medium, used to order the
     * unified compose picker's TRENDING SONGS / TRENDING FILMS pair before the
     * async [lastComposeMediaType] flow emits. Returns "track" when unknown
     * (fresh install, or an existing install whose value hasn't been mirrored
     * from DataStore yet) — the same default the flow uses.
     */
    fun lastComposeMediaTypeSyncSeed(): String =
        context.getSharedPreferences(SYNC_PREFS_NAME, Context.MODE_PRIVATE)
            .getString(LAST_COMPOSE_MEDIA_TYPE_SYNC_KEY, null) ?: "track"

    val contactsSyncStatus: Flow<String> = dataStore.data.map { prefs ->
        prefs[CONTACTS_SYNC_STATUS] ?: "notAsked"
    }

    suspend fun setContactsSyncStatus(value: String) {
        dataStore.edit { it[CONTACTS_SYNC_STATUS] = value }
    }

    val appearanceMode: Flow<String> = dataStore.data.map { prefs ->
        prefs[APPEARANCE_MODE] ?: AppearanceDefaultMigration.unsetThemeDefault(context)
    }

    suspend fun setAppearanceMode(value: String) {
        dataStore.edit { it[APPEARANCE_MODE] = value }
    }

    /**
     * Synchronous read of the appearance default for a user who hasn't explicitly
     * chosen a theme — used to seed UI before the [appearanceMode] flow emits, so
     * the theme doesn't flash on cold launch. Returns "light" or "system".
     */
    fun unsetThemeDefaultStorageValue(): String =
        AppearanceDefaultMigration.unsetThemeDefault(context)

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

    val hasTappedAlbumArt: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[HAS_TAPPED_ALBUM_ART] ?: false
    }

    suspend fun setHasTappedAlbumArt() {
        dataStore.edit { it[HAS_TAPPED_ALBUM_ART] = true }
    }

    // The feed playlist button leaves Corus to open the user's music service.
    // The first generation shows a one-shot explainer + confirm; this flag is
    // only set once the user confirms, so a cancel re-offers it next time.
    val hasConfirmedFeedPlaylist: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[HAS_CONFIRMED_FEED_PLAYLIST] ?: false
    }

    suspend fun setHasConfirmedFeedPlaylist() {
        dataStore.edit { it[HAS_CONFIRMED_FEED_PLAYLIST] = true }
    }

    // ── Recent Searches (polymorphic JSON array of RecentSearchItem) ──

    /** Decode the stored recents, falling back to the legacy users-only shape so
     *  recents saved by older builds aren't lost on upgrade. Capped defensively. */
    private fun decodeRecents(raw: String?): List<RecentSearchItem> {
        if (raw.isNullOrBlank()) return emptyList()
        return try {
            recentSearchJson.decodeFromString<List<RecentSearchItem>>(raw)
        } catch (_: Exception) {
            // Legacy format has no `kind` discriminator — decode as users.
            try {
                recentJson.decodeFromString<List<LegacyRecentUserEntry>>(raw).map { it.toItem() }
            } catch (_: Exception) {
                emptyList()
            }
        }
    }

    val recentSearches: Flow<List<RecentSearchItem>> = dataStore.data.map { prefs ->
        decodeRecents(prefs[RECENT_SEARCHES])
    }

    /** Record a tapped result. Moves an existing (kind,id) to the front,
     *  refreshing its stored payload; capped at 15 most-recent-first. */
    suspend fun addRecentSearch(item: RecentSearchItem) {
        dataStore.edit { prefs ->
            val existing = decodeRecents(prefs[RECENT_SEARCHES])
            val updated = listOf(item) + existing.filter { it.dedupeKey != item.dedupeKey }
            prefs[RECENT_SEARCHES] = recentSearchJson.encodeToString(updated.take(15))
        }
    }

    suspend fun removeRecentSearch(dedupeKey: String) {
        dataStore.edit { prefs ->
            val existing = decodeRecents(prefs[RECENT_SEARCHES])
            val updated = existing.filter { it.dedupeKey != dedupeKey }
            prefs[RECENT_SEARCHES] = recentSearchJson.encodeToString(updated)
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

    // ── Taste Matches Rail (first page, persisted for instant cold-start SWR) ──
    // Mirrors the suggested-matches persistence above but stores only the FIRST
    // page (matches + cursor + hasMore + fetchedAt) so the rail can paint and
    // resume paging instantly on open. Matches iOS CachedTasteMatchesPage.

    private fun tasteMatchesPageKey(userId: String) = stringPreferencesKey("tasteMatchesPage_$userId")

    suspend fun persistTasteMatchesPage(
        matches: List<SuggestedUserMatch>,
        nextCursor: String?,
        hasMore: Boolean,
        userId: String,
    ) {
        val wrapper = PersistedTasteMatchesPageWrapper(
            fetchedAt = System.currentTimeMillis(),
            nextCursor = nextCursor,
            hasMore = hasMore,
            matches = matches.map { it.toPersisted() },
        )
        dataStore.edit { it[tasteMatchesPageKey(userId)] = recentJson.encodeToString(wrapper) }
    }

    /** One entry in the persisted taste-matches first-page cache. */
    data class PersistedTasteMatchesPage(
        val matches: List<SuggestedUserMatch>,
        val nextCursor: String?,
        val hasMore: Boolean,
        val fetchedAt: Long,
    )

    /**
     * Returns the persisted first page with its original fetchedAt timestamp, or
     * null if nothing is persisted / it fails to decode. Caller owns TTL / max-age
     * validation. Uses `first()` (not `collect`) so it reads one value and returns
     * — collecting the infinite `dataStore.data` flow would suspend forever.
     */
    suspend fun loadTasteMatchesPageAsync(userId: String): PersistedTasteMatchesPage? {
        val raw = dataStore.data.first()[tasteMatchesPageKey(userId)]
        if (raw.isNullOrBlank()) return null
        return try {
            val wrapper = recentJson.decodeFromString<PersistedTasteMatchesPageWrapper>(raw)
            PersistedTasteMatchesPage(
                matches = wrapper.matches.map { it.toModel() },
                nextCursor = wrapper.nextCursor,
                hasMore = wrapper.hasMore,
                fetchedAt = wrapper.fetchedAt,
            )
        } catch (_: Exception) {
            null
        }
    }

    suspend fun clearTasteMatchesPage(userId: String) {
        dataStore.edit { it.remove(tasteMatchesPageKey(userId)) }
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
        // first() reads one value and terminates; collecting dataStore.data
        // would suspend forever (infinite flow — `return@collect` returns from
        // the lambda, not from collect, so the fresh network refresh that
        // follows in prefetchMutedSet would never run).
        val raw = dataStore.data.first()[key]
        return if (raw.isNullOrBlank()) null else raw.split(",").toSet()
    }

    // ── Following IDs (persisted for offline access, matching iOS UserDefaults pattern) ──
    // Seeds the cached following set instantly on launch so the Trending inline
    // "Follow" pill is correct on first composition instead of flashing in then out.

    suspend fun persistFollowingIds(ids: Set<String>, userId: String) {
        val key = stringPreferencesKey("followingIds_$userId")
        dataStore.edit { it[key] = ids.joinToString(",") }
    }

    suspend fun loadFollowingIdsAsync(userId: String): Set<String>? {
        val key = stringPreferencesKey("followingIds_$userId")
        // first() reads one value and terminates. Collecting dataStore.data
        // would suspend forever — it's an infinite flow, and `return@collect`
        // only returns from the lambda, not from collect.
        val raw = dataStore.data.first()[key]
        return if (raw.isNullOrBlank()) null else raw.split(",").toSet()
    }
}
