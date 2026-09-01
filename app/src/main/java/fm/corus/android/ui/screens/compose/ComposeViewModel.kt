package fm.corus.android.ui.screens.compose

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import fm.corus.android.data.model.HashtagSuggestion
import fm.corus.android.data.model.CymbalMovie
import fm.corus.android.data.model.CymbalPost
import fm.corus.android.data.model.CymbalTrack
import fm.corus.android.data.model.hasStubMetadata
import fm.corus.android.data.model.mergeMissing
import fm.corus.android.data.model.CymbalUser
import fm.corus.android.data.model.MediaType
import fm.corus.android.data.model.TrendingMovie
import fm.corus.android.data.model.TrendingSong
import fm.corus.android.data.remote.CloudFunctionsDataSource
import fm.corus.android.data.repository.AuthRepository
import fm.corus.android.data.repository.ExploreRepository
import fm.corus.android.data.model.PostDraft
import fm.corus.android.data.repository.PostDraftRepository
import fm.corus.android.data.repository.PostRepository
import fm.corus.android.data.repository.MusicSearchRepository
import fm.corus.android.data.repository.SpotifyRepository
import fm.corus.android.data.repository.SubscriptionRepository
import fm.corus.android.data.repository.TMDBRepository
import fm.corus.android.data.repository.UserRepository
import fm.corus.android.domain.HapticManager
import fm.corus.android.domain.NowPlayingManager
import fm.corus.android.domain.PostCreationEvent
import fm.corus.android.service.AnalyticsService
import fm.corus.android.service.NetworkMonitor
import fm.corus.android.service.RemoteConfigService
import fm.corus.android.ui.components.extractMentions
import fm.corus.android.ui.components.parseHashtagQuery
import fm.corus.android.ui.components.parseMentionQuery
import fm.corus.android.domain.PostSuccessOthers
import fm.corus.android.domain.PostSuccessOthersMediaInfo
import fm.corus.android.domain.PostSuccessOthersPayload
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Date
import javax.inject.Inject

data class SearchResultItem(
    val id: String,
    val imageURL: String?,
    val title: String,
    val subtitle: String,
    val trailingText: String? = null,
    val showPlayOverlay: Boolean = false,
    val isSoundCloud: Boolean = false,
    val isAudiomack: Boolean = false,
    val isBandcamp: Boolean = false,
)

/**
 * Result filter for the unified compose picker
 * (`compose_unified_search_enabled`) — the chips shown once a query is typed,
 * mirroring the Search tab's unified chips. Songs and films only: artists,
 * albums and directors aren't postable, so compose never offers them.
 */
enum class ComposeUnifiedFilter(val value: String) {
    ALL("all"),
    SONGS("songs"),
    FILMS("films"),
}

/**
 * A song or film lifted out of the viewer's saved posts, flattened for the
 * unified picker's RECENTLY SAVED section.
 */
sealed class SavedPickerItem {
    abstract val id: String

    data class Song(override val id: String, val track: CymbalTrack) : SavedPickerItem()
    data class Film(override val id: String, val movie: CymbalMovie) : SavedPickerItem()
}

/**
 * Flattens saved posts into pickable songs/films, newest first, one entry per
 * song/film (the same song saved from several posts collapses). Top-level +
 * pure so it can be unit-tested without the ViewModel. Mirrors iOS
 * `SongSearchView.savedPickerItems`.
 */
internal fun savedPickerItems(posts: List<CymbalPost>): List<SavedPickerItem> {
    val seen = mutableSetOf<String>()
    val items = mutableListOf<SavedPickerItem>()
    for (post in posts) {
        if (post.isMovie) {
            val movieId = post.movieId
            val title = post.movieTitle
            if (movieId.isNullOrEmpty() || title.isNullOrEmpty()) continue
            val key = "movie:$movieId"
            if (!seen.add(key)) continue
            items.add(
                SavedPickerItem.Film(
                    id = key,
                    movie = CymbalMovie(
                        id = movieId,
                        title = title,
                        directorName = post.directorName ?: "",
                        directorIds = post.directorIds,
                        year = post.releaseYear ?: "",
                        posterURL = post.posterURL,
                        posterLargeURL = post.posterLargeURL,
                        tmdbWebURL = post.tmdbWebURL ?: "",
                        overview = post.movieOverview ?: "",
                        rating = post.movieRating ?: 0.0,
                        cast = post.movieCast ?: emptyList(),
                        trailerURL = post.trailerURL,
                        releaseDate = post.movieReleaseDate,
                    ),
                )
            )
        } else {
            if (post.track.id.isEmpty()) continue
            val key = "track:${post.track.id}"
            if (!seen.add(key)) continue
            items.add(SavedPickerItem.Song(id = key, track = post.track))
        }
    }
    return items
}

/**
 * Session cache for RECENTLY SAVED. Compose opens are frequent and a saved list
 * changes rarely, so a short TTL keeps the picker instant and off the network —
 * `getSavedPosts` is a callable plus a post read per row, and it sits on the
 * critical path since the zero state paints in one go. Mirrors iOS
 * `SavedPickerCache`.
 */
internal object SavedPickerCache {
    private const val TTL_MS = 5 * 60 * 1000L

    private var items: List<SavedPickerItem> = emptyList()
    private var fetchedAt: Long? = null
    /**
     * Whose saves these are. The cache is process-global (that's the point — it
     * has to outlive the ViewModel), so an account switch inside one process
     * would otherwise serve the previous account's saved songs for up to the
     * TTL. A uid mismatch is treated as a miss.
     */
    private var ownerUid: String? = null

    @Synchronized
    fun fresh(uid: String, nowMs: Long = System.currentTimeMillis()): List<SavedPickerItem>? {
        if (ownerUid != uid) return null
        val at = fetchedAt ?: return null
        return if (nowMs - at < TTL_MS) items else null
    }

    @Synchronized
    fun store(uid: String, newItems: List<SavedPickerItem>, nowMs: Long = System.currentTimeMillis()) {
        ownerUid = uid
        items = newItems
        fetchedAt = nowMs
    }

    /** Test hook — the cache outlives any single ViewModel by design. */
    @Synchronized
    fun clear() {
        ownerUid = null
        items = emptyList()
        fetchedAt = null
    }
}

/**
 * Stable fingerprint of the draft-relevant composer state. We only re-prompt to
 * save on exit when this changes from the last saved/resumed draft. Uses the
 * track/movie id (not full metadata) so a resume-time metadata refresh doesn't
 * register as an edit; fixed field order keeps it deterministic. Top-level +
 * pure so it can be unit-tested without the ViewModel. Mirrors web's
 * draftSignature in compose/page.tsx.
 *
 * @param voiceState "text" | "new" | "saved" | "none".
 */
internal fun draftSignature(
    mediaType: MediaType,
    trackId: String?,
    movieId: String?,
    caption: String,
    captionMode: String,
    commentsAudienceWire: String?,
    voiceState: String,
): String = listOf(
    mediaType.value,
    trackId ?: "",
    movieId ?: "",
    caption,
    captionMode,
    commentsAudienceWire ?: "",
    voiceState,
).joinToString("")

@HiltViewModel
class ComposeViewModel @Inject constructor(
    private val postRepository: PostRepository,
    private val postDraftRepository: PostDraftRepository,
    private val spotifyRepository: SpotifyRepository,
    private val musicSearchRepository: MusicSearchRepository,
    private val tmdbRepository: TMDBRepository,
    private val authRepository: AuthRepository,
    val analyticsService: AnalyticsService,
    private val userRepository: UserRepository,
    private val subscriptionRepository: SubscriptionRepository,
    private val exploreRepository: ExploreRepository,
    val nowPlayingManager: NowPlayingManager,
    private val postCreationEvent: PostCreationEvent,
    private val hapticManager: HapticManager,
    private val remoteConfigService: RemoteConfigService,
    private val networkMonitor: NetworkMonitor,
    private val cloudFunctions: CloudFunctionsDataSource,
    private val preferencesDataStore: fm.corus.android.data.local.PreferencesDataStore,
) : ViewModel() {

    /** Live flag read — the unified compose picker (one field, All/Songs/Films
     *  chips, blended zero state) instead of the Songs/Films segmented toggle. */
    val composeUnifiedSearchEnabled: Boolean get() = remoteConfigService.composeUnifiedSearchEnabled

    // Post limit / Cymbal Club
    private val _showPostLimitPaywall = MutableStateFlow(false)
    val showPostLimitPaywall: StateFlow<Boolean> = _showPostLimitPaywall.asStateFlow()

    fun dismissPostLimitPaywall() {
        _showPostLimitPaywall.value = false
    }

    // Hard-cap "Cooldown time" dialog (6h ceiling — applies to subscribers too,
    // so we don't open the upgrade paywall here).
    private val _showHardCapAlert = MutableStateFlow(false)
    val showHardCapAlert: StateFlow<Boolean> = _showHardCapAlert.asStateFlow()

    fun dismissHardCapAlert() {
        _showHardCapAlert.value = false
    }

    // "Approaching cap" warning dialog, fired once per cooldown window when a
    // paid user crosses DAILY_POST_LIMIT_WARN_AT so they aren't blindsided by
    // the hard cap.
    private val _showApproachingCapAlert = MutableStateFlow(false)
    val showApproachingCapAlert: StateFlow<Boolean> = _showApproachingCapAlert.asStateFlow()

    private val _approachingCapRemaining = MutableStateFlow(0)
    val approachingCapRemaining: StateFlow<Int> = _approachingCapRemaining.asStateFlow()

    fun dismissApproachingCapAlert() {
        _showApproachingCapAlert.value = false
    }

    // Trending songs & movies — declared before `init` because the init block
    // launches coroutines that write to these flows. With Dispatchers.Main.immediate,
    // a suspend repo call that completes without truly suspending will run the
    // assignment synchronously inside the constructor, so the backing fields must
    // already exist by then.
    private val _trendingSongs = MutableStateFlow<List<TrendingSong>>(emptyList())
    val trendingSongs: StateFlow<List<TrendingSong>> = _trendingSongs.asStateFlow()

    private val _trendingMovies = MutableStateFlow<List<TrendingMovie>>(emptyList())
    val trendingMovies: StateFlow<List<TrendingMovie>> = _trendingMovies.asStateFlow()

    private val _isLoadingTrending = MutableStateFlow(true)
    val isLoadingTrending: StateFlow<Boolean> = _isLoadingTrending.asStateFlow()

    // ── Unified picker state (compose_unified_search_enabled) ──
    // Declared before `init` for the same reason as the trending flows above:
    // the init block writes to them and Main.immediate can run that write
    // synchronously inside the constructor.

    /** Songs/films from the viewer's saved posts, newest first, de-duplicated. */
    private val _savedItems = MutableStateFlow<List<SavedPickerItem>>(emptyList())
    val savedItems: StateFlow<List<SavedPickerItem>> = _savedItems.asStateFlow()

    /**
     * Whether the saved-posts fetch is still in flight. Together with
     * [isLoadingTrending] this gates the zero state's SINGLE skeleton: the
     * sections paint together or not at all, never section-by-section.
     */
    private val _isLoadingSaved = MutableStateFlow(true)
    val isLoadingSaved: StateFlow<Boolean> = _isLoadingSaved.asStateFlow()

    /** Active chip. Reset to [ComposeUnifiedFilter.ALL] whenever the query is
     *  cleared so each search starts on the blended view. */
    private val _unifiedFilter = MutableStateFlow(ComposeUnifiedFilter.ALL)
    val unifiedFilter: StateFlow<ComposeUnifiedFilter> = _unifiedFilter.asStateFlow()

    /** Song rows for the unified picker. Kept separate from [searchResults] (the
     *  classic picker's pre-mapped rows) so the flag-off path is untouched, and
     *  typed as tracks because the picker row previews and posts the track. */
    private val _unifiedSongResults = MutableStateFlow<List<CymbalTrack>>(emptyList())
    val unifiedSongResults: StateFlow<List<CymbalTrack>> = _unifiedSongResults.asStateFlow()

    /**
     * The query each vertical last COMMITTED results for. Written on success
     * only, so a cancelled fan-out never marks a vertical as served. Two jobs:
     * a chip switch reuses results already fetched instead of refetching (no
     * skeleton flash), and the blended view derives its per-section loading
     * state from it — which also absorbs the debounce lag, so a half-typed
     * query never flashes an empty state.
     */
    private val _settledQueries = MutableStateFlow<Map<ComposeUnifiedFilter, String>>(emptyMap())
    val settledQueries: StateFlow<Map<ComposeUnifiedFilter, String>> = _settledQueries.asStateFlow()

    /**
     * The last-posted medium ("track" / "movie"), seeded SYNCHRONOUSLY at
     * construction so the zero state's trending pair is ordered correctly on its
     * very first frame — awaiting the async DataStore flow instead would paint
     * the wrong order and then visibly reshuffle. Kept current in-process by
     * [rememberComposeMediaType], which writes both this and DataStore; compose
     * is the only writer, so there is nothing else to observe.
     */
    private val _lastComposeMediaType = MutableStateFlow(preferencesDataStore.lastComposeMediaTypeSyncSeed())
    val lastComposeMediaType: StateFlow<String> = _lastComposeMediaType.asStateFlow()

    init {
        val userId = authRepository.currentUserId
        if (userId != null) {
            viewModelScope.launch {
                // Refresh the rolling 24h post count from the server
                subscriptionRepository.refreshPostLimit()

                // Load user profile to update verified status and total post count
                try {
                    val user = userRepository.fetchUserProfile(userId)
                    if (user != null) {
                        subscriptionRepository.updateVerifiedStatus(user.isVerified)
                        subscriptionRepository.setTotalPostCount(user.cymbalCount)
                    }
                } catch (_: Exception) { }
            }
        }

        // Load trending songs and movies
        viewModelScope.launch {
            try {
                val songs = exploreRepository.fetchTrendingSongs()
                android.util.Log.d("ComposeVM", "Loaded ${songs.size} trending songs")
                _trendingSongs.value = songs
            } catch (e: Exception) {
                android.util.Log.e("ComposeVM", "Failed to load trending songs", e)
            }
            try {
                val movies = exploreRepository.fetchTrendingMovies()
                android.util.Log.d("ComposeVM", "Loaded ${movies.size} trending movies")
                _trendingMovies.value = movies
            } catch (e: Exception) {
                android.util.Log.e("ComposeVM", "Failed to load trending movies", e)
            }
            _isLoadingTrending.value = false
        }

        // RECENTLY SAVED. Only the unified picker shows it, so the classic
        // picker never pays for the callable. A miss stays silent — the section
        // simply doesn't render.
        loadRecentlySaved()
    }

    private fun loadRecentlySaved() {
        if (!remoteConfigService.composeUnifiedSearchEnabled) {
            _isLoadingSaved.value = false
            return
        }
        val uid = authRepository.currentUserId
        if (uid == null) {
            _isLoadingSaved.value = false
            return
        }
        SavedPickerCache.fresh(uid)?.let { cached ->
            _savedItems.value = cached
            _isLoadingSaved.value = false
            return
        }
        viewModelScope.launch {
            try {
                // Over-fetch a little: saves collapse by song/film, so duplicates
                // of the same track shouldn't starve the capped section.
                val items = savedPickerItems(cloudFunctions.getSavedPosts(uid, limit = 12))
                SavedPickerCache.store(uid, items)
                _savedItems.value = items
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                android.util.Log.e("ComposeVM", "Failed to load saved posts", e)
            }
            _isLoadingSaved.value = false
        }
    }

    // ── Post drafts ──
    // Cloud-synced snapshots of a post-in-progress (cross-platform via
    // users_v2/{uid}/postDrafts). The drafts entry only surfaces when the user
    // has >=1 draft, so the feature is unobtrusive and needs no flag.
    private val _drafts = MutableStateFlow<List<PostDraft>>(emptyList())
    val drafts: StateFlow<List<PostDraft>> = _drafts.asStateFlow()

    /** Non-null when the composer was resumed from a saved draft — the same doc
     *  is updated on re-save and deleted on a successful post. */
    private val _editingDraftId = MutableStateFlow<String?>(null)
    val editingDraftId: StateFlow<String?> = _editingDraftId.asStateFlow()
    private var draftCreatedAt: Long? = null

    /** A voice memo already uploaded when this draft was saved; reused at post
     *  time if the user hasn't recorded a fresh one after resuming. */
    private val _resumedVoiceNoteURL = MutableStateFlow<String?>(null)
    val resumedVoiceNoteURL: StateFlow<String?> = _resumedVoiceNoteURL.asStateFlow()

    /** Fingerprint of the last saved/resumed draft; null = never-saved compose
     *  (always dirty). Used to skip the save-on-exit prompt when nothing changed. */
    private var savedSignature: String? = null

    /** Set when a resumed draft's track/film 404s on refresh — posting is blocked
     *  until the user picks a replacement. */
    private val _attachmentUnavailable = MutableStateFlow(false)
    val attachmentUnavailable: StateFlow<Boolean> = _attachmentUnavailable.asStateFlow()

    private val _savingDraft = MutableStateFlow(false)
    val savingDraft: StateFlow<Boolean> = _savingDraft.asStateFlow()

    /** One-shot toast events (string res id) for draft save/delete outcomes. */
    private val _draftToast = kotlinx.coroutines.flow.MutableSharedFlow<Int>(extraBufferCapacity = 4)
    val draftToast: kotlinx.coroutines.flow.SharedFlow<Int> = _draftToast

    private val _selectedTrack = MutableStateFlow<CymbalTrack?>(null)
    val selectedTrack: StateFlow<CymbalTrack?> = _selectedTrack.asStateFlow()

    private val _selectedMovie = MutableStateFlow<CymbalMovie?>(null)
    val selectedMovie: StateFlow<CymbalMovie?> = _selectedMovie.asStateFlow()

    private val _isPosting = MutableStateFlow(false)
    val isPosting: StateFlow<Boolean> = _isPosting.asStateFlow()

    private val _postSuccess = MutableStateFlow(false)
    val postSuccess: StateFlow<Boolean> = _postSuccess.asStateFlow()

    private val _postSuccessOthers = MutableStateFlow<PostSuccessOthersPayload?>(null)
    val postSuccessOthers: StateFlow<PostSuccessOthersPayload?> = _postSuccessOthers.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    // Search results
    private val _searchResults = MutableStateFlow<List<SearchResultItem>>(emptyList())
    val searchResults: StateFlow<List<SearchResultItem>> = _searchResults.asStateFlow()

    private val _filmResults = MutableStateFlow<List<CymbalMovie>>(emptyList())
    val filmResults: StateFlow<List<CymbalMovie>> = _filmResults.asStateFlow()

    private val _isSearching = MutableStateFlow(false)
    val isSearching: StateFlow<Boolean> = _isSearching.asStateFlow()

    /**
     * True when the most recent [search] threw. Lets ComposeScreen render a
     * "something's off on our end" empty state instead of silently showing no
     * results, which is what happens when a `searchSongs` 500 reaches us.
     */
    private val _searchHasError = MutableStateFlow(false)
    val searchHasError: StateFlow<Boolean> = _searchHasError.asStateFlow()

    /** Re-export of networkMonitor.isConnected so ComposeScreen can branch
     *  between "you're offline" and "our service is down" empty-state copy. */
    val isConnected: StateFlow<Boolean> = networkMonitor.isConnected

    private val _mentionSuggestions = MutableStateFlow<List<CymbalUser>>(emptyList())
    val mentionSuggestions: StateFlow<List<CymbalUser>> = _mentionSuggestions.asStateFlow()

    private val _hashtagSuggestions = MutableStateFlow<List<HashtagSuggestion>>(emptyList())
    val hashtagSuggestions: StateFlow<List<HashtagSuggestion>> = _hashtagSuggestions.asStateFlow()

    // Pre-selection loading (hides search mode while fetching track/movie by ID)
    private val _isLoadingPreSelection = MutableStateFlow(false)
    val isLoadingPreSelection: StateFlow<Boolean> = _isLoadingPreSelection.asStateFlow()

    // Repost context — set when the user taps "Repost" on an existing post.
    // When repostedFromUsername != null, ComposeScreen shows an attribution
    // toggle and locks the track/movie to the original post's media.
    private val _repostedFromPostId = MutableStateFlow<String?>(null)
    val repostedFromPostId: StateFlow<String?> = _repostedFromPostId.asStateFlow()

    private val _repostedFromUserId = MutableStateFlow<String?>(null)
    val repostedFromUserId: StateFlow<String?> = _repostedFromUserId.asStateFlow()

    private val _repostedFromUsername = MutableStateFlow<String?>(null)
    val repostedFromUsername: StateFlow<String?> = _repostedFromUsername.asStateFlow()

    private val _showRepostAttribution = MutableStateFlow(true)
    val showRepostAttribution: StateFlow<Boolean> = _showRepostAttribution.asStateFlow()

    // Comments-audience picker state. Defaults to EVERYONE so the first
    // post a user composes after the flag flips on doesn't accidentally
    // ship restricted — they have to actively choose to lock it down.
    private val _commentsAudience = MutableStateFlow(fm.corus.android.data.model.CommentsAudience.EVERYONE)
    val commentsAudience: StateFlow<fm.corus.android.data.model.CommentsAudience> = _commentsAudience.asStateFlow()
    fun setCommentsAudience(audience: fm.corus.android.data.model.CommentsAudience) {
        _commentsAudience.value = audience
    }
    /// Exposed so the compose screen can decide whether to render the picker.
    val commentControlsOnPosts: Boolean get() = remoteConfigService.commentControlsOnPosts

    // Trophy celebration state
    private val _showTrophy = MutableStateFlow(false)
    val showTrophy: StateFlow<Boolean> = _showTrophy.asStateFlow()

    private val _trophyPost = MutableStateFlow<CymbalPost?>(null)
    val trophyPost: StateFlow<CymbalPost?> = _trophyPost.asStateFlow()

    private var searchJob: Job? = null
    private var mentionJob: Job? = null
    private var movieDetailJob: Job? = null
    private var hashtagJob: Job? = null
    private var cachedTracks: List<CymbalTrack> = emptyList()
    private var cachedMovies: List<CymbalMovie> = emptyList()

    fun search(query: String, mediaType: MediaType) {
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            delay(300) // debounce
            _isSearching.value = true
            _searchHasError.value = false
            try {
                if (mediaType == MediaType.TRACK) {
                    cachedTracks = musicSearchRepository.search(
                        query,
                        includeSoundCloud = remoteConfigService.soundcloudEnabled,
                        // Collapse stays at the "recording" default, same as the
                        // Search tab: one row per recording. See searchSongsVertical.
                    ).tracks
                    _searchResults.value = cachedTracks.map { track ->
                        SearchResultItem(
                            id = track.id,
                            imageURL = track.albumArtURL,
                            title = track.name,
                            subtitle = track.artistName,
                            trailingText = track.formattedDuration,
                            showPlayOverlay = true,
                            isSoundCloud = track.source == fm.corus.android.data.model.TrackSource.SOUNDCLOUD,
                            isAudiomack = track.source == fm.corus.android.data.model.TrackSource.AUDIOMACK,
                            isBandcamp = track.isBandcampCatalog,
                        )
                    }
                } else {
                    cachedMovies = tmdbRepository.searchMovies(query)
                    val withDirectors = try {
                        tmdbRepository.prefetchDirectors(cachedMovies)
                    } catch (_: Exception) { cachedMovies }
                    cachedMovies = withDirectors
                    _filmResults.value = withDirectors
                }
            } catch (e: CancellationException) {
                // The search was superseded by a newer keystroke (searchJob.cancel()),
                // not a real failure. Rethrow so the cancelled coroutine unwinds
                // cleanly instead of flashing the "something's off" error state.
                // Mirrors iOS, which guards superseded searches with Task.isCancelled.
                throw e
            } catch (_: Exception) {
                _searchResults.value = emptyList()
                _filmResults.value = emptyList()
                _searchHasError.value = true
            }
            _isSearching.value = false
        }
    }

    // ── Unified picker search (compose_unified_search_enabled) ──

    /**
     * One debounce for the query, then the active chip decides which verticals
     * fetch: ALL fans songs + films out concurrently, a narrowed chip runs just
     * its vertical. Verticals that already committed results for this exact
     * query are skipped (see [settledQueries]), so a chip switch on the same
     * query renders instantly with no refetch. One vertical failing must not
     * blank the other — the error state only shows when nothing has results.
     */
    fun searchUnified(query: String) {
        searchJob?.cancel()
        if (query.isBlank()) return
        val filter = _unifiedFilter.value
        val verticals = if (filter == ComposeUnifiedFilter.ALL) {
            listOf(ComposeUnifiedFilter.SONGS, ComposeUnifiedFilter.FILMS)
        } else {
            listOf(filter)
        }

        searchJob = viewModelScope.launch {
            // Already served this exact query everywhere visible (chip switch
            // after the ALL fan-out): nothing to fetch.
            val pending = verticals.filter { _settledQueries.value[it] != query }
            if (pending.isEmpty()) {
                _isSearching.value = false
                return@launch
            }
            delay(unifiedDebounceMs(query))
            _isSearching.value = true
            _searchHasError.value = false
            var anyFailure = false
            kotlinx.coroutines.coroutineScope {
                pending.forEach { vertical ->
                    launch {
                        try {
                            when (vertical) {
                                ComposeUnifiedFilter.SONGS -> searchSongsVertical(query)
                                ComposeUnifiedFilter.FILMS -> searchFilmsVertical(query)
                                ComposeUnifiedFilter.ALL -> Unit
                            }
                        } catch (e: CancellationException) {
                            // A newer keystroke cancelling the fan-out is normal
                            // editing, never a server error (same reasoning as
                            // the classic path above).
                            throw e
                        } catch (_: Exception) {
                            anyFailure = true
                        }
                    }
                }
            }
            _searchHasError.value = anyFailure && verticals.none { unifiedVerticalHasResults(it) }
            _isSearching.value = false
        }
    }

    /**
     * Chip tap: narrows/widens the rendered verticals. The caller re-runs
     * [searchUnified] so any vertical that hasn't served the current query yet
     * fetches; already-served verticals are no-ops there.
     */
    fun setUnifiedFilter(filter: ComposeUnifiedFilter) {
        if (_unifiedFilter.value == filter) return
        _unifiedFilter.value = filter
        // Chip switches swap the visible rows out from under a playing preview —
        // stop it, same as the classic segmented mode switch. Mirrors iOS.
        stopPreview()
    }

    /** Query cleared: drop both verticals and start the next search on ALL. */
    fun clearUnifiedSearch() {
        searchJob?.cancel()
        _unifiedSongResults.value = emptyList()
        _filmResults.value = emptyList()
        _settledQueries.value = emptyMap()
        _isSearching.value = false
        _searchHasError.value = false
        _unifiedFilter.value = ComposeUnifiedFilter.ALL
    }

    private fun unifiedVerticalHasResults(vertical: ComposeUnifiedFilter): Boolean = when (vertical) {
        ComposeUnifiedFilter.SONGS -> _unifiedSongResults.value.isNotEmpty()
        ComposeUnifiedFilter.FILMS -> _filmResults.value.isNotEmpty()
        ComposeUnifiedFilter.ALL ->
            _unifiedSongResults.value.isNotEmpty() || _filmResults.value.isNotEmpty()
    }

    /**
     * Adaptive debounce matching iOS SongSearchView: shorter delays as the query
     * grows, since longer queries are more specific and the user has already
     * committed to them.
     */
    private fun unifiedDebounceMs(query: String): Long = when (query.length) {
        1 -> 400L
        2 -> 300L
        3 -> 200L
        else -> 150L
    }

    private suspend fun searchSongsVertical(query: String) {
        val tracks = musicSearchRepository.search(
            query,
            includeSoundCloud = remoteConfigService.soundcloudEnabled,
            // Collapse stays at the "recording" default, same as the Search tab:
            // one row per recording. This picker used to ask for "cover" so a
            // poster could choose which pressing's artwork their post wore, but
            // the catalog rarely offers a meaningful choice — one Vampire Weekend
            // query returned five rows (standard, deluxe, three compilation
            // repackagings) with identical or junk covers, pushing the songs
            // people actually wanted off the first screen.
        ).tracks
        cachedTracks = tracks
        _unifiedSongResults.value = tracks
        _settledQueries.value = _settledQueries.value + (ComposeUnifiedFilter.SONGS to query)
    }

    private suspend fun searchFilmsVertical(query: String) {
        val movies = tmdbRepository.searchMovies(query)
        val withDirectors = try {
            tmdbRepository.prefetchDirectors(movies)
        } catch (_: Exception) { movies }
        cachedMovies = withDirectors
        _filmResults.value = withDirectors
        _settledQueries.value = _settledQueries.value + (ComposeUnifiedFilter.FILMS to query)
    }

    /**
     * Records the medium the user just picked. The unified picker has no tabs to
     * remember, so the last-posted medium is captured at SELECTION (not on chip
     * changes) and decides which trending block leads the zero state next time.
     * Mirrors iOS, which writes `lastComposeMediaType` in its select* helpers.
     */
    private fun rememberComposeMediaType(mediaType: MediaType) {
        if (!remoteConfigService.composeUnifiedSearchEnabled) return
        val raw = if (mediaType == MediaType.MOVIE) "movie" else "track"
        _lastComposeMediaType.value = raw
        viewModelScope.launch {
            runCatching { preferencesDataStore.setLastComposeMediaType(raw) }
        }
    }

    fun selectResult(result: SearchResultItem, mediaType: MediaType) {
        // Mirrors iOS SongSearchView.selectSong haptic.
        hapticManager.impact(HapticManager.ImpactStyle.LIGHT)
        _selectedTrack.value = cachedTracks.firstOrNull { it.id == result.id }
        rememberComposeMediaType(MediaType.TRACK)
    }

    /** Unified-picker pick: the row already holds the full track. */
    fun selectTrack(track: CymbalTrack) {
        hapticManager.impact(HapticManager.ImpactStyle.LIGHT)
        _error.value = null
        _selectedTrack.value = track
        rememberComposeMediaType(MediaType.TRACK)
    }

    fun selectFilmResult(movie: CymbalMovie) {
        // Mirrors iOS FilmSearchView.selectMovie haptic.
        hapticManager.impact(HapticManager.ImpactStyle.LIGHT)
        _error.value = null
        _selectedMovie.value = movie
        rememberComposeMediaType(MediaType.MOVIE)
        // Search rows have director + poster but no videos. Fetch details in
        // the background (same as iOS movieDetailTask) so createPost can wait
        // and stamp the TMDB trailer.
        movieDetailJob?.cancel()
        val idInt = movie.id.removePrefix("tmdb_").toIntOrNull() ?: return
        movieDetailJob = viewModelScope.launch {
            try {
                val fresh = tmdbRepository.getMovieDetails(idInt)
                if (_selectedMovie.value?.id == movie.id) {
                    _selectedMovie.value = fresh
                }
            } catch (_: Exception) {
                // Keep the search snapshot; the post-create trigger fills
                // trailerURL when this fetch misses.
            }
        }
    }

    /** Unified-picker pick from RECENTLY SAVED. */
    fun selectSavedItem(item: SavedPickerItem) {
        when (item) {
            is SavedPickerItem.Song -> selectTrack(item.track)
            is SavedPickerItem.Film -> selectFilmResult(item.movie)
        }
    }

    fun toggleSearchResultPreview(trackId: String) {
        val track = cachedTracks.firstOrNull { it.id == trackId } ?: return
        togglePreview(track)
    }

    fun clearSelection() {
        stopPreview()
        _selectedTrack.value = null
        _selectedMovie.value = null
        _searchResults.value = emptyList()
        _filmResults.value = emptyList()
        _unifiedSongResults.value = emptyList()
        // Drop the served-query marks with the rows they belong to, or the next
        // search for the same query would be skipped as "already served" and
        // leave the picker empty.
        _settledQueries.value = emptyMap()
    }

    /**
     * Clears the current track/movie selection but preserves the in-progress
     * search query results. Used when the user taps the back chevron from
     * compose mode and expects to land on the search list they had open.
     * A fresh entry into Compose still goes through [reset], which wipes
     * everything.
     */
    fun clearSelectionKeepingResults() {
        stopPreview()
        _selectedTrack.value = null
        _selectedMovie.value = null
        // Returning to the picker abandons the resumed / just-saved draft's editing
        // context, so the NEXT pick saves as a NEW draft instead of overwriting the
        // previous one in place (and never leaks its voice-note URL). Mirrors the
        // fresh-compose reset; keeps search results/caches (the "KeepingResults").
        _editingDraftId.value = null
        draftCreatedAt = null
        _resumedVoiceNoteURL.value = null
        savedSignature = null
    }

    fun selectTrendingSong(song: TrendingSong) {
        // Mirrors iOS SongSearchView.selectTrendingSong haptic.
        hapticManager.impact(HapticManager.ImpactStyle.LIGHT)
        _selectedTrack.value = song.track
        _searchResults.value = emptyList()
        rememberComposeMediaType(MediaType.TRACK)
    }

    fun selectTrendingMovie(movie: TrendingMovie) {
        // Mirrors iOS FilmSearchView.selectTrendingMovie haptic.
        hapticManager.impact(HapticManager.ImpactStyle.LIGHT)
        _selectedMovie.value = movie.asCymbalMovie()
        _filmResults.value = emptyList()
        rememberComposeMediaType(MediaType.MOVIE)
    }

    fun loadAndSelectTrack(trackId: String) {
        _isLoadingPreSelection.value = true
        viewModelScope.launch {
            try {
                val track = spotifyRepository.getTrack(trackId)
                if (track != null) {
                    _selectedTrack.value = track
                    _searchResults.value = emptyList()
                }
            } catch (_: Exception) {
                _error.value = "Could not load track."
            }
            _isLoadingPreSelection.value = false
        }
    }

    /**
     * Pre-select a track when the caller already has a full CymbalTrack (preserves
     * source = SoundCloud and the SC-specific identifiers, which a Spotify-by-id
     * lookup would silently drop).
     */
    fun selectPreloadedTrack(track: CymbalTrack) {
        hapticManager.impact(HapticManager.ImpactStyle.LIGHT)
        _selectedTrack.value = track
        _searchResults.value = emptyList()
        _isLoadingPreSelection.value = false
    }

    /**
     * Seeds the compose screen with a post that's being reposted.
     * Locks the selected track/movie to the original's media so the user
     * can't change it, and shows the attribution toggle.
     */
    fun setRepostContext(original: CymbalPost) {
        _repostedFromPostId.value = original.id
        _repostedFromUserId.value = original.user.id
        _repostedFromUsername.value = original.user.username
        _showRepostAttribution.value = true
        if (original.isMovie) {
            _selectedMovie.value = CymbalMovie(
                id = original.movieId ?: "",
                title = original.movieTitle ?: "",
                directorName = original.directorName ?: "",
                directorIds = original.directorIds,
                year = original.releaseYear ?: "",
                posterURL = original.posterURL,
                posterLargeURL = original.posterLargeURL,
                tmdbWebURL = original.tmdbWebURL ?: "",
                overview = original.movieOverview ?: "",
                rating = original.movieRating ?: 0.0,
                cast = original.movieCast ?: emptyList(),
                trailerURL = original.trailerURL,
            )
        } else {
            _selectedTrack.value = original.track
        }
    }

    fun setShowRepostAttribution(show: Boolean) {
        _showRepostAttribution.value = show
    }

    fun loadAndSelectMovie(movieId: String) {
        _isLoadingPreSelection.value = true
        viewModelScope.launch {
            try {
                val movie = tmdbRepository.getMovieDetails(movieId.toInt())
                _selectedMovie.value = movie
                _filmResults.value = emptyList()
            } catch (_: Exception) {
                _error.value = "Could not load movie."
            }
            _isLoadingPreSelection.value = false
        }
    }

    fun createPost(caption: String, mediaType: MediaType, voiceNoteData: ByteArray? = null) {
        val userId = authRepository.currentUserId ?: return

        // Synchronous re-entry guard. The Compose UI's enabled-state binding
        // is ineffective until recomposition runs, so a fast double-tap can
        // fire createPost twice and write two posts to Firestore. Setting
        // isPosting before the launched coroutine closes that window — both
        // calls enter, only the first proceeds.
        if (!_isPosting.compareAndSet(expect = false, update = true)) return
        _error.value = null

        viewModelScope.launch {
            // Flag-off never starts this, so today's toast/dismiss path stays
            // unchanged. Flag-on overlaps the others fetch with createPost so
            // the haptic can fire when dismiss + sheet are ready.
            val othersPrefetch = if (remoteConfigService.postSuccessOthersEnabled) {
                async {
                    loadPostSuccessOthersPayload(
                        mediaType,
                        // +1: incrementPostCount hasn't run yet.
                        totalPostCount = subscriptionRepository.totalPostCount.value + 1,
                    )
                }
            } else {
                null
            }
            try {
                movieDetailJob?.join()
                // Build the callable payload. The server validates the rolling
                // 24h limit atomically inside `createPost`, so no separate
                // pre-flight is needed — over-limit throws
                // PostLimitReachedException below.
                val payload = mutableMapOf<String, Any?>()
                payload["mediaType"] = mediaType.value
                payload["caption"] = caption

                // Hashtags parsed from caption; server normalizes again on its
                // side so this is best-effort (and matches iOS / Web parity).
                val hashtagRegex = Regex("#(\\w+)")
                payload["hashtags"] = hashtagRegex.findAll(caption).map { it.groupValues[1] }.toList()

                // Comments-audience: only stamp restrictive values; "everyone"
                // is the implicit default and we omit the field for legacy
                // doc-shape compatibility.
                val pickedAudience = _commentsAudience.value
                if (remoteConfigService.commentControlsOnPosts &&
                    pickedAudience != fm.corus.android.data.model.CommentsAudience.EVERYONE) {
                    payload["commentsAudience"] = pickedAudience.wire
                }

                // Repost metadata — only included when the attribution toggle
                // is on. If the user toggled attribution off, the post is
                // independent: no repost subobject is sent and the server
                // doesn't stamp the parent.
                val includeRepost = _showRepostAttribution.value
                val originalPostId = _repostedFromPostId.value
                val originalUserId = _repostedFromUserId.value
                if (includeRepost && originalPostId != null) {
                    payload["repost"] = mapOf(
                        "originalPostId" to originalPostId,
                        "showAttribution" to true,
                    )
                }

                if (mediaType == MediaType.TRACK) {
                    val rawTrack = _selectedTrack.value ?: throw Exception("No track selected")
                    // Stub-track entry points (DM share, comment attachment, Now
                    // Playing) hand us a CymbalTrack with empty albumName /
                    // durationMs / isrc / releaseDate. Re-fetch the canonical
                    // Spotify metadata before posting so the doc lands complete.
                    // Network failures fall back to the raw track — the server-
                    // side stampNewReleaseFieldOnPostCreate trigger is the
                    // final backstop.
                    val track = if (rawTrack.hasStubMetadata()) {
                        runCatching { spotifyRepository.getTrack(rawTrack.id) }
                            .getOrNull()
                            ?.let { rawTrack.mergeMissing(it) }
                            ?: rawTrack
                    } else {
                        rawTrack
                    }
                    val trackMap = mutableMapOf<String, Any?>(
                        "trackId" to track.id,
                        "trackName" to track.name,
                        "artistName" to track.artistName,
                        // Per-artist Spotify IDs from the search response. Persisted on
                        // the post doc so the backend matcher can intersect by stable
                        // ID rather than the credit string ("Sufjan Stevens" vs
                        // "Sufjan Stevens, My Brightest Diamond"). Empty for SoundCloud.
                        "artistIds" to track.artistIds,
                        "albumName" to track.albumName,
                        "albumArtURL" to (track.albumArtURL ?: ""),
                        "albumArtLargeURL" to (track.albumArtLargeURL ?: ""),
                        "durationMs" to track.durationMs,
                        "trackSource" to track.source.raw,
                    )
                    track.isrc?.let { trackMap["isrc"] = it }
                    track.releaseDate?.let { trackMap["trackReleaseDate"] = it }
                    track.releaseDatePrecision?.let { trackMap["trackReleaseDatePrecision"] = it }
                    track.previewUrl?.let { trackMap["previewUrl"] = it }
                    if (track.source == fm.corus.android.data.model.TrackSource.SOUNDCLOUD) {
                        trackMap["soundcloudId"] = track.soundcloudId ?: ""
                        trackMap["soundcloudPermalinkUrl"] = track.soundcloudPermalinkUrl ?: ""
                    }
                    // Audiomack-only posts carry the id + canonical page url so the
                    // feed card can render the badge and link out (no in-app play).
                    if (track.source == fm.corus.android.data.model.TrackSource.AUDIOMACK) {
                        trackMap["audiomackId"] = track.audiomackId ?: ""
                        trackMap["audiomackUrl"] = track.audiomackUrl ?: ""
                        // Carry the artist/album page URLs so the posted card's "…"
                        // menu can link out to Audiomack (no Corus artist/album page).
                        trackMap["audiomackArtistUrl"] = track.audiomackArtistUrl ?: ""
                        trackMap["audiomackAlbumUrl"] = track.audiomackAlbumUrl ?: ""
                    }
                    if (track.source == fm.corus.android.data.model.TrackSource.BANDCAMP) {
                        trackMap["bandcampId"] = track.bandcampId
                            ?: track.id.removePrefix("bc:")
                        trackMap["bandcampUrl"] = track.bandcampUrl ?: ""
                        trackMap["bandcampArtistUrl"] = track.bandcampArtistUrl ?: ""
                        trackMap["bandcampAlbumUrl"] = track.bandcampAlbumUrl ?: ""
                    }
                    payload["track"] = trackMap
                } else {
                    val movie = _selectedMovie.value ?: throw Exception("No movie selected")
                    val movieMap = mutableMapOf<String, Any?>(
                        "movieId" to movie.id,
                        "movieTitle" to movie.title,
                        "directorName" to movie.directorName,
                        // Per-director TMDB IDs (when available on the search result).
                        "directorIds" to movie.directorIds,
                        "releaseYear" to movie.year,
                        "posterURL" to (movie.posterURL ?: ""),
                        "posterLargeURL" to (movie.posterLargeURL ?: ""),
                        "movieOverview" to movie.overview,
                        "movieRating" to movie.rating,
                        "movieCast" to movie.cast,
                        "tmdbWebURL" to movie.tmdbWebURL,
                    )
                    movie.releaseDate?.let { movieMap["movieReleaseDate"] = it }
                    movie.trailerURL?.let { movieMap["trailerURL"] = it }
                    payload["movie"] = movieMap
                }

                // Carry voice-uploader uid through the repository so it can
                // upload to Storage before invoking the callable. Sentinel key
                // is stripped by the repository.
                if (voiceNoteData != null) {
                    payload["__voiceUserId"] = userId
                } else {
                    // Resumed voice draft with no fresh recording: reuse the memo
                    // already uploaded when the draft was saved so the posted
                    // corus keeps its voice note (mirrors web's resumedVoiceNoteURL).
                    _resumedVoiceNoteURL.value?.let { payload["voiceNoteURL"] = it }
                }

                val result = postRepository.createPost(payload, voiceNoteData)
                analyticsService.logPostCreated(mediaType.value)
                subscriptionRepository.incrementPostCount()
                authRepository.bumpCymbalCount(1)
                val hashtags = (payload["hashtags"] as? List<*>)?.filterIsInstance<String>().orEmpty()
                postCreationEvent.notifyPostCreated(
                    mediaType,
                    buildOptimisticPost(
                        postId = result.postId,
                        mediaType = mediaType,
                        caption = caption,
                        hashtags = hashtags,
                        isFirstPoster = result.isFirstPoster,
                    ),
                )

                // A resumed draft that just posted should disappear from Drafts.
                val postedDraftId = _editingDraftId.value
                if (postedDraftId != null) {
                    runCatching { postDraftRepository.deleteDraft(userId, postedDraftId) }
                    _editingDraftId.value = null
                    draftCreatedAt = null
                    _drafts.value = _drafts.value.filterNot { it.id == postedDraftId }
                }

                // Paid users: warn when they're approaching the 6h hard cap so
                // it doesn't come out of nowhere. Throttled inside the
                // repository so subsequent posts in the same window are silent.
                if (subscriptionRepository.hasFullAccess &&
                    subscriptionRepository.shouldShowApproachingCapWarning()
                ) {
                    _approachingCapRemaining.value = subscriptionRepository.approachingCapRemaining
                    _showApproachingCapAlert.value = true
                }

                // Repost notification + tag notifications are created by the
                // `onPostCreatedNotifyTagsAndRepost` Cloud Function trigger,
                // not the client — they would double-fire if we also wrote
                // them here. Same on iOS.

                if (result.isFirstPoster) {
                    othersPrefetch?.cancel()
                    hapticManager.notification(HapticManager.NotificationType.SUCCESS)
                    val track = _selectedTrack.value
                    val movie = _selectedMovie.value
                    _trophyPost.value = CymbalPost(
                        id = "",
                        user = CymbalUser(id = userId, username = "", displayName = ""),
                        track = track ?: CymbalTrack(id = "", name = "", artistName = "", albumName = ""),
                        mediaType = mediaType,
                        movieId = movie?.id,
                        movieTitle = movie?.title,
                        directorName = movie?.directorName,
                        posterURL = movie?.posterURL,
                        posterLargeURL = movie?.posterLargeURL,
                        movieReleaseDate = movie?.releaseDate,
                        isFirstPoster = true,
                    )
                    _showTrophy.value = true
                } else {
                    val shown = maybeShowPostSuccessOthers(
                        mediaType,
                        prefetched = othersPrefetch?.await(),
                    )
                    hapticManager.notification(HapticManager.NotificationType.SUCCESS)
                    if (!shown) {
                        _postSuccess.value = true
                    }
                }
            } catch (e: CloudFunctionsDataSource.PostLimitReachedException) {
                othersPrefetch?.cancel()
                // Server hit. The callable returned recentCount + dailyLimit,
                // but the SubscriptionRepository already tracks these via its
                // own refresh; just open the paywall (or hard-cap dialog).
                if (e.hardCap) {
                    _showHardCapAlert.value = true
                } else {
                    _showPostLimitPaywall.value = true
                }
            } catch (e: CloudFunctionsDataSource.RepostOriginalMissingException) {
                othersPrefetch?.cancel()
                _error.value = "That post is no longer available."
            } catch (e: CloudFunctionsDataSource.PostingBannedException) {
                othersPrefetch?.cancel()
                _error.value = "Posting is blocked by your account permissions right now."
            } catch (e: CloudFunctionsDataSource.CaptionBlockedException) {
                othersPrefetch?.cancel()
                _error.value = "Your caption may go against our community guidelines. Please edit it and try again."
            } catch (e: Exception) {
                othersPrefetch?.cancel()
                Log.e("ComposeViewModel", "createPost failed", e)
                _error.value = "Something went wrong. Please try again."
            }
            _isPosting.value = false
        }
    }

    fun dismissTrophy() {
        _showTrophy.value = false
        _trophyPost.value = null
        _postSuccess.value = true
    }

    /** Compose is about to slide away; MainTabScreen owns the sheet. */
    fun consumePostSuccessOthersHandoff() {
        _postSuccessOthers.value = null
    }

    /**
     * Flag-off (or no eligible others / slow fetch) returns false so the
     * caller keeps today's dismiss path. Flag-on with people stashes a
     * payload so ComposeScreen can dismiss first; MainTabScreen presents
     * the others sheet over the feed.
     */
    private suspend fun maybeShowPostSuccessOthers(
        mediaType: MediaType,
        prefetched: PostSuccessOthersPayload? = null,
    ): Boolean {
        val payload = prefetched ?: loadPostSuccessOthersPayload(mediaType) ?: return false
        _postSuccessOthers.value = payload
        return true
    }

    private suspend fun loadPostSuccessOthersPayload(
        mediaType: MediaType,
        totalPostCount: Int = subscriptionRepository.totalPostCount.value,
    ): PostSuccessOthersPayload? {
        if (!remoteConfigService.postSuccessOthersEnabled) return null
        if (!PostSuccessOthers.shouldAttempt(
                isFirstPoster = false,
                totalPostCount = totalPostCount,
            )
        ) {
            return null
        }
        val userId = authRepository.currentUserId ?: return null
        val media = when (mediaType) {
            MediaType.TRACK -> {
                val track = _selectedTrack.value ?: return null
                PostSuccessOthersMediaInfo(
                    title = track.name,
                    subtitle = track.artistName,
                    artURL = track.albumArtURL ?: track.albumArtLargeURL,
                    isPoster = false,
                    track = track,
                )
            }
            MediaType.MOVIE -> {
                val movie = _selectedMovie.value ?: return null
                PostSuccessOthersMediaInfo(
                    title = movie.title,
                    subtitle = movie.directorName,
                    artURL = movie.posterURL ?: movie.posterLargeURL,
                    isPoster = true,
                    movie = movie,
                )
            }
        }
        val page = withTimeoutOrNull(PostSuccessOthers.FETCH_TIMEOUT_MS) {
            when (mediaType) {
                MediaType.TRACK -> {
                    val track = _selectedTrack.value ?: return@withTimeoutOrNull null
                    postRepository.fetchSongPostsFromCloud(
                        trackId = track.id,
                        spotifyURI = track.spotifyURI.ifBlank { null },
                        isrc = track.isrc,
                        trackName = track.name,
                        artistName = track.artistName,
                        pageSize = 15,
                    ).let { it.posts to it.uniquePosterCount }
                }
                MediaType.MOVIE -> {
                    val movie = _selectedMovie.value ?: return@withTimeoutOrNull null
                    postRepository.fetchMoviePostsFromCloud(
                        movieId = movie.id,
                        movieTitle = movie.title,
                        pageSize = 15,
                    ).let { it.posts to it.uniquePosterCount }
                }
            }
        } ?: return null
        val people = PostSuccessOthers.pickEligible(
            posts = page.first,
            currentUserId = userId,
            followingIds = userRepository.followingIds.value,
        )
        if (people.isEmpty()) return null
        return PostSuccessOthersPayload(
            people = people,
            otherCount = PostSuccessOthers.otherCount(page.second, people.size),
            media = media,
        )
    }

    fun checkForMention(caption: String, caret: Int = caption.length) {
        val query = parseMentionQuery(caption, caret)
        if (query != null) {
            clearHashtagSuggestions()
            mentionJob?.cancel()
            mentionJob = viewModelScope.launch {
                delay(200)
                try {
                    val results = userRepository.searchUsers(query, limit = 4)
                    _mentionSuggestions.value = results
                } catch (_: Exception) {
                    _mentionSuggestions.value = emptyList()
                }
            }
            return
        }

        mentionJob?.cancel()
        _mentionSuggestions.value = emptyList()
        checkForHashtag(caption, caret)
    }

    private fun checkForHashtag(caption: String, caret: Int) {
        val query = parseHashtagQuery(caption, caret)
        if (query == null) {
            clearHashtagSuggestions()
            return
        }
        hashtagJob?.cancel()
        hashtagJob = viewModelScope.launch {
            delay(200)
            try {
                _hashtagSuggestions.value = exploreRepository.fetchHashtagSuggestions(query, limit = 3)
            } catch (_: Exception) {
                _hashtagSuggestions.value = emptyList()
            }
        }
    }

    fun clearMentionSuggestions() {
        mentionJob?.cancel()
        _mentionSuggestions.value = emptyList()
        clearHashtagSuggestions()
    }

    fun clearHashtagSuggestions() {
        hashtagJob?.cancel()
        _hashtagSuggestions.value = emptyList()
    }

    // ── Preview playback ──

    val nowPlayingState = nowPlayingManager.state
    val previewLoadingTrackId = nowPlayingManager.loadingTrackId

    fun togglePreview(track: CymbalTrack) {
        viewModelScope.launch {
            nowPlayingManager.play(
                trackId = track.id,
                trackName = track.name,
                artistName = track.artistName,
                albumArtURL = track.albumArtURL,
                albumArtLargeURL = track.albumArtLargeURL,
                previewUrl = track.previewUrl,
                isrc = track.isrc,
                source = track.source,
                soundcloudId = track.soundcloudId,
                soundcloudPermalinkUrl = track.soundcloudPermalinkUrl,
            )
        }
    }

    /**
     * Stop a compose-picker preview only. Mirrors iOS
     * `stopPickerPreviewIfNeeded`: feed / post-sourced playback (has a
     * `sourcePostId`) keeps playing under the compose sheet so opening
     * compose or switching chips doesn't kill the mini-player.
     */
    fun stopPreview() {
        if (nowPlayingManager.state.value.sourcePostId != null) return
        nowPlayingManager.stop()
    }

    // ── Post drafts ──────────────────────────────────────────────────────────

    /** Fetch the user's drafts newest-first so the "Drafts (N)" entry and sheet
     *  can render. Best-effort; failures leave the list empty (entry hidden). */
    fun loadDrafts() {
        val uid = authRepository.currentUserId ?: return
        viewModelScope.launch {
            runCatching { postDraftRepository.listDrafts(uid) }
                .onSuccess { _drafts.value = it }
        }
    }

    /**
     * Whether the current composer state differs from the last saved/resumed
     * draft signature. A fresh, never-saved compose ([savedSignature] null) is
     * always dirty. Compared by track/movie id (not full metadata) so a
     * resume-time metadata refresh isn't mistaken for an edit.
     *
     * @param voiceState "text" | "new" (fresh recording) | "saved" (reusing the
     *   uploaded memo) | "none".
     */
    fun isDraftDirty(
        mediaType: MediaType,
        trackId: String?,
        movieId: String?,
        caption: String,
        captionMode: String,
        voiceState: String,
    ): Boolean {
        val sig = draftSignature(
            mediaType, trackId, movieId, caption.trim(), captionMode,
            if (commentControlsOnPosts) _commentsAudience.value.wire else null,
            voiceState,
        )
        return savedSignature == null || sig != savedSignature
    }

    /**
     * Persist the current composer as a draft. If a fresh voice memo was
     * recorded, upload it first; otherwise reuse the resumed URL. Updates the
     * existing doc in place when resuming (preserving createdAt), else creates
     * a new one (trimming to the 30-cap). Emits a toast and returns success.
     */
    suspend fun saveCurrentDraft(
        mediaType: MediaType,
        caption: String,
        captionMode: String,
        voiceNoteData: ByteArray?,
    ): Boolean {
        val uid = authRepository.currentUserId ?: return false
        _savingDraft.value = true
        try {
            var voiceURL: String? = null
            if (captionMode == "voice") {
                voiceURL = if (voiceNoteData != null) {
                    postDraftRepository.uploadVoiceNote(uid, voiceNoteData)
                } else {
                    _resumedVoiceNoteURL.value
                }
            }
            val input = PostDraft(
                id = _editingDraftId.value ?: "",
                mediaType = mediaType,
                caption = if (captionMode == "text") caption else "",
                captionMode = captionMode,
                commentsAudience = if (commentControlsOnPosts &&
                    _commentsAudience.value != fm.corus.android.data.model.CommentsAudience.EVERYONE
                ) _commentsAudience.value else null,
                track = if (mediaType == MediaType.TRACK) _selectedTrack.value else null,
                movie = if (mediaType == MediaType.MOVIE) _selectedMovie.value else null,
                voiceNoteURL = if (captionMode == "voice") voiceURL else null,
                createdAt = 0L,
                updatedAt = 0L,
            )
            val saved = postDraftRepository.saveDraft(
                uid, input, _editingDraftId.value, draftCreatedAt,
            )
            _editingDraftId.value = saved.id
            draftCreatedAt = saved.createdAt
            _resumedVoiceNoteURL.value = saved.voiceNoteURL
            // Refresh the signature so an immediate re-exit doesn't re-prompt.
            savedSignature = draftSignature(
                mediaType,
                if (mediaType == MediaType.TRACK) _selectedTrack.value?.id else null,
                if (mediaType == MediaType.MOVIE) _selectedMovie.value?.id else null,
                saved.caption.trim(),
                captionMode,
                if (commentControlsOnPosts) _commentsAudience.value.wire else null,
                if (captionMode == "voice") {
                    if (voiceNoteData != null) "new" else if (saved.voiceNoteURL != null) "saved" else "none"
                } else "text",
            )
            // Reflect the change in the in-memory list so the count is fresh.
            _drafts.value = (listOf(saved) + _drafts.value.filterNot { it.id == saved.id })
                .sortedByDescending { it.updatedAt }
            // "Draft saved" is shown by the screen via the app-wide ToastManager
            // so it survives the composer closing (mirrors iOS's top toast). The
            // failure toast below is fine on the in-screen channel — it only
            // matters while the composer is still open.
            return true
        } catch (e: Exception) {
            Log.e("ComposeViewModel", "saveCurrentDraft failed", e)
            _draftToast.tryEmit(fm.corus.android.R.string.compose_draft_save_failed)
            return false
        } finally {
            _savingDraft.value = false
        }
    }

    /**
     * Load a saved draft into the composer. Maps canonical → native state, seeds
     * an [editingDraftId] so re-save updates in place, and best-effort re-resolves
     * the attachment by id to freshen art/preview and catch a delisted item.
     * Returns the caption/captionMode so the screen can seed its local UI state.
     */
    data class ResumeState(val caption: String, val captionMode: String, val mediaType: MediaType)

    fun resumeDraft(draft: PostDraft): ResumeState {
        _attachmentUnavailable.value = false
        _selectedTrack.value = if (draft.mediaType == MediaType.TRACK) draft.track else null
        _selectedMovie.value = if (draft.mediaType == MediaType.MOVIE) draft.movie else null
        _searchResults.value = emptyList()
        _filmResults.value = emptyList()
        if (draft.commentsAudience != null) _commentsAudience.value = draft.commentsAudience
        _resumedVoiceNoteURL.value = if (draft.captionMode == "voice") draft.voiceNoteURL else null
        _editingDraftId.value = draft.id
        draftCreatedAt = draft.createdAt
        savedSignature = draftSignature(
            draft.mediaType,
            if (draft.mediaType == MediaType.TRACK) draft.track?.id else null,
            if (draft.mediaType == MediaType.MOVIE) draft.movie?.id else null,
            draft.caption.trim(),
            draft.captionMode,
            if (commentControlsOnPosts) (draft.commentsAudience?.wire ?: fm.corus.android.data.model.CommentsAudience.EVERYONE.wire) else null,
            if (draft.captionMode == "voice") {
                if (draft.voiceNoteURL != null) "saved" else "none"
            } else "text",
        )
        // Best-effort staleness refresh.
        if (draft.mediaType == MediaType.TRACK) {
            draft.track?.let { refreshTrackAttachment(it) }
        } else {
            draft.movie?.let { refreshMovieAttachment(it) }
        }
        return ResumeState(draft.caption, draft.captionMode, draft.mediaType)
    }

    private fun refreshTrackAttachment(track: CymbalTrack) {
        val id = track.id
        // No clean per-id resolve for SoundCloud/Apple ids — keep the snapshot.
        if (id.isEmpty() || id.startsWith("sc:") || id.startsWith("am:")) return
        viewModelScope.launch {
            try {
                val fresh = spotifyRepository.getTrack(id)
                if (fresh == null) {
                    // Confirmed not-found → block posting until re-pick.
                    _attachmentUnavailable.value = true
                } else if (fresh.id.isNotEmpty()) {
                    // Only freshen if we're still on this draft's track.
                    if (_selectedTrack.value?.id == id) _selectedTrack.value = fresh
                }
            } catch (_: Exception) {
                // Transient (rate-limit / network) — keep the stored snapshot.
            }
        }
    }

    private fun refreshMovieAttachment(movie: CymbalMovie) {
        val idInt = movie.id.toIntOrNull() ?: return
        viewModelScope.launch {
            try {
                val fresh = tmdbRepository.getMovieDetails(idInt)
                if (fresh.id.isNotEmpty() && _selectedMovie.value?.id == movie.id) {
                    _selectedMovie.value = fresh
                }
            } catch (_: Exception) {
                // TMDB detail throws for both transient and not-found; we can't
                // distinguish cleanly, so keep the snapshot (never falsely block).
            }
        }
    }

    /** User tapped "Choose another" after a delisted attachment. */
    fun clearUnavailableAttachment() {
        _attachmentUnavailable.value = false
        _selectedTrack.value = null
        _selectedMovie.value = null
        _editingDraftId.value = null
        draftCreatedAt = null
        savedSignature = null
    }

    fun deleteDraft(draftId: String) {
        val uid = authRepository.currentUserId ?: return
        viewModelScope.launch {
            try {
                postDraftRepository.deleteDraft(uid, draftId)
                if (_editingDraftId.value == draftId) {
                    _editingDraftId.value = null
                    draftCreatedAt = null
                }
                _drafts.value = _drafts.value.filterNot { it.id == draftId }
                _draftToast.tryEmit(fm.corus.android.R.string.compose_draft_deleted)
            } catch (e: Exception) {
                Log.e("ComposeViewModel", "deleteDraft failed", e)
                _draftToast.tryEmit(fm.corus.android.R.string.compose_draft_delete_failed)
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        stopPreview()
    }

    /** Reset all transient state so the screen opens fresh. */
    fun reset() {
        searchJob?.cancel()
        mentionJob?.cancel()
        stopPreview()
        _selectedTrack.value = null
        _selectedMovie.value = null
        _searchResults.value = emptyList()
        _filmResults.value = emptyList()
        _unifiedSongResults.value = emptyList()
        _settledQueries.value = emptyMap()
        _unifiedFilter.value = ComposeUnifiedFilter.ALL
        _isSearching.value = false
        _searchHasError.value = false
        _isPosting.value = false
        _postSuccess.value = false
        _error.value = null
        _mentionSuggestions.value = emptyList()
        _isLoadingPreSelection.value = false
        _showTrophy.value = false
        _trophyPost.value = null
        _showPostLimitPaywall.value = false
        _repostedFromPostId.value = null
        _repostedFromUserId.value = null
        _repostedFromUsername.value = null
        _showRepostAttribution.value = true
        _commentsAudience.value = fm.corus.android.data.model.CommentsAudience.EVERYONE
        cachedTracks = emptyList()
        cachedMovies = emptyList()
        // Draft state — a fresh compose entry must not carry a resumed draft's
        // editing context or the delete-on-post would target the wrong doc.
        _editingDraftId.value = null
        draftCreatedAt = null
        _resumedVoiceNoteURL.value = null
        savedSignature = null
        _attachmentUnavailable.value = false
        _savingDraft.value = false
    }

    private fun buildOptimisticPost(
        postId: String,
        mediaType: MediaType,
        caption: String,
        hashtags: List<String>,
        isFirstPoster: Boolean,
    ): CymbalPost? {
        if (postId.isBlank()) return null
        val userId = authRepository.currentUserId ?: return null
        val user = authRepository.userProfile.value
            ?: CymbalUser(id = userId, username = "", displayName = "")
        val trimmed = caption.trim().ifEmpty { null }
        val voiceUrl = _resumedVoiceNoteURL.value
        val attributed = _showRepostAttribution.value
        return if (mediaType == MediaType.TRACK) {
            val track = _selectedTrack.value ?: return null
            CymbalPost(
                id = postId,
                user = user,
                track = track,
                caption = trimmed,
                voiceNoteURL = voiceUrl,
                hashtags = hashtags,
                timestamp = Date(),
                isFirstPoster = isFirstPoster,
                mediaType = MediaType.TRACK,
                repostedFromPostId = if (attributed) _repostedFromPostId.value else null,
                repostedFromUserId = if (attributed) _repostedFromUserId.value else null,
                repostedFromUsername = if (attributed) _repostedFromUsername.value else null,
            )
        } else {
            val movie = _selectedMovie.value ?: return null
            CymbalPost(
                id = postId,
                user = user,
                track = CymbalTrack(id = "", name = "", artistName = "", albumName = ""),
                caption = trimmed,
                voiceNoteURL = voiceUrl,
                hashtags = hashtags,
                timestamp = Date(),
                isFirstPoster = isFirstPoster,
                mediaType = MediaType.MOVIE,
                movieId = movie.id,
                movieTitle = movie.title,
                directorName = movie.directorName,
                directorIds = movie.directorIds,
                releaseYear = movie.year,
                posterURL = movie.posterURL,
                posterLargeURL = movie.posterLargeURL,
                tmdbWebURL = movie.tmdbWebURL,
                trailerURL = movie.trailerURL,
                movieOverview = movie.overview,
                movieRating = movie.rating,
                movieCast = movie.cast,
                movieReleaseDate = movie.releaseDate,
                repostedFromPostId = if (attributed) _repostedFromPostId.value else null,
                repostedFromUserId = if (attributed) _repostedFromUserId.value else null,
                repostedFromUsername = if (attributed) _repostedFromUsername.value else null,
            )
        }
    }
}
