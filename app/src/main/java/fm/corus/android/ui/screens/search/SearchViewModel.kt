package fm.corus.android.ui.screens.search

import android.content.ContentResolver
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import fm.corus.android.data.local.PreferencesDataStore
import fm.corus.android.data.local.readContactPhoneNumbers
import fm.corus.android.data.model.CymbalHashtag
import fm.corus.android.data.model.CymbalMovie
import fm.corus.android.data.model.CymbalTrack
import fm.corus.android.data.model.RecentSearchItem
import fm.corus.android.data.model.CymbalUser
import fm.corus.android.data.model.SuggestedUserMatch
import fm.corus.android.data.model.SuggestionReason
import fm.corus.android.data.model.AlbumSearchSummary
import fm.corus.android.data.model.TrendingAlbum
import fm.corus.android.data.model.TrendingAlbumDestinationCache
import fm.corus.android.data.model.TrendingAlbumOpen
import fm.corus.android.data.model.TrendingArtist
import fm.corus.android.data.model.TrendingDirector
import fm.corus.android.data.model.TrendingHashtag
import fm.corus.android.data.model.TrendingMovie
import fm.corus.android.data.model.TrendingSong
import fm.corus.android.data.model.TrendingWindow
import fm.corus.android.data.model.albumTitlesMatch
import fm.corus.android.data.model.resolveTrendingAlbumOpen
import fm.corus.android.data.remote.CloudFunctionsDataSource
import fm.corus.android.data.remote.FirestoreDataSource
import fm.corus.android.data.repository.AuthRepository
import fm.corus.android.data.repository.ExploreRepository
import fm.corus.android.data.repository.MusicSearchRepository
import fm.corus.android.data.repository.TMDBRepository
import fm.corus.android.data.repository.UserRepository
import fm.corus.android.domain.DestinationResolvingOverlay
import fm.corus.android.domain.NowPlayingManager
import fm.corus.android.service.AnalyticsService
import fm.corus.android.service.NetworkMonitor
import fm.corus.android.service.RemoteConfigService
import fm.corus.android.service.SearchSection
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject

/**
 * Result filter for unified search (`unified_search_enabled`) — the chips
 * shown once a query is typed. [ALL] is the blended view; the rest narrow to
 * one vertical and reuse the classic per-tab result renderers. Raw [value]s
 * match iOS/web's `search_filter_changed` analytics values.
 */
enum class UnifiedSearchFilter(val value: String) {
    ALL("all"),
    USERS("users"),
    MUSIC("songs"),
    FILM("films"),
    HASHTAGS("hashtags"),
}

@HiltViewModel
class SearchViewModel @Inject constructor(
    private val userRepository: UserRepository,
    private val authRepository: AuthRepository,
    private val exploreRepository: ExploreRepository,
    private val cloudFunctions: CloudFunctionsDataSource,
    private val musicSearchRepository: MusicSearchRepository,
    private val tmdbRepository: TMDBRepository,
    private val preferencesDataStore: PreferencesDataStore,
    private val firestoreDataSource: FirestoreDataSource,
    private val remoteConfigService: RemoteConfigService,
    private val analyticsService: AnalyticsService,
    val nowPlayingManager: NowPlayingManager,
    private val networkMonitor: NetworkMonitor,
) : ViewModel() {

    /** True when the most recent [search] threw AND the current tab is empty. */
    private val _searchHasError = MutableStateFlow(false)
    val searchHasError: StateFlow<Boolean> = _searchHasError.asStateFlow()

    /**
     * Surface connectivity to the UI so the empty-error state can pick between
     * "you're offline" copy and "our service is down" copy. We already had
     * the monitor available — this just re-exports it for SearchScreen.
     */
    val isConnected: StateFlow<Boolean> = networkMonitor.isConnected

    // Tab state
    private val _activeTab = MutableStateFlow(0) // 0=Users, 1=Songs, 2=Films, 3=Hashtags
    val activeTab: StateFlow<Int> = _activeTab.asStateFlow()

    fun setActiveTab(tabIndex: Int) {
        if (_activeTab.value == tabIndex) return
        _activeTab.value = tabIndex
        // Clear results for the new tab so stale data from a prior search doesn't flash
        if (_searchQuery.value.isNotBlank()) {
            when (tabIndex) {
                0 -> _userSearchResults.value = emptyList()
                1 -> {
                    _songSearchResults.value = emptyList()
                    _artistSearchResults.value = emptyList()
                    _albumSearchResults.value = emptyList()
                    _songsFirst.value = false
                }
                2 -> {
                    _filmSearchResults.value = emptyList()
                    _directorSearchResults.value = emptyList()
                }
                3 -> _hashtagSearchResults.value = emptyList()
            }
        }
        if (tabIndex == 3) {
            loadTrendingHashtagsIfNeeded()
            // Re-pull in case follow state was changed on HashtagFeedScreen.
            refreshFollowedHashtags()
        }
    }

    // Search
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _userSearchResults = MutableStateFlow<List<CymbalUser>>(emptyList())
    val userSearchResults: StateFlow<List<CymbalUser>> = _userSearchResults.asStateFlow()

    private val _songSearchResults = MutableStateFlow<List<CymbalTrack>>(emptyList())
    val songSearchResults: StateFlow<List<CymbalTrack>> = _songSearchResults.asStateFlow()

    private val _filmSearchResults = MutableStateFlow<List<CymbalMovie>>(emptyList())
    val filmSearchResults: StateFlow<List<CymbalMovie>> = _filmSearchResults.asStateFlow()

    // ── Artist / album / director search rows (artist_pages_enabled) ──
    // Populated only while the flag is on; always empty otherwise so the
    // search tabs render byte-identically to today with the flag off.

    private val _artistSearchResults = MutableStateFlow<List<fm.corus.android.data.model.ArtistSummary>>(emptyList())
    val artistSearchResults: StateFlow<List<fm.corus.android.data.model.ArtistSummary>> = _artistSearchResults.asStateFlow()

    private val _albumSearchResults = MutableStateFlow<List<fm.corus.android.data.model.AlbumSearchSummary>>(emptyList())
    val albumSearchResults: StateFlow<List<fm.corus.android.data.model.AlbumSearchSummary>> = _albumSearchResults.asStateFlow()

    /** True when the query is a song-title search; the UI then renders album
     *  rows below the songs so a direct song hit leads (see SearchScreen). */
    private val _songsFirst = MutableStateFlow(false)
    val songsFirst: StateFlow<Boolean> = _songsFirst.asStateFlow()

    private val _directorSearchResults = MutableStateFlow<List<fm.corus.android.data.model.ArtistSummary>>(emptyList())
    val directorSearchResults: StateFlow<List<fm.corus.android.data.model.ArtistSummary>> = _directorSearchResults.asStateFlow()

    /** Live flag read — gates the search-row fetches, the tab labels, and the
     *  placeholders. */
    val artistPagesEnabled: Boolean get() = remoteConfigService.artistPagesEnabled

    /** Live flag read — unified search (blended zero state + filter chips). */
    val unifiedSearchEnabled: Boolean get() = remoteConfigService.unifiedSearchEnabled

    /** Idle browse tabs + swipe. Typed query still uses unified chips. */
    val segmentedSearchEnabled: Boolean get() = remoteConfigService.segmentedSearchEnabled

    /** Typed query uses All + filter chips (unified OR segmented). */
    val unifiedQueryMode: Boolean get() = unifiedSearchEnabled || segmentedSearchEnabled

    val artistsOnCorusSectionEnabled: Boolean get() = remoteConfigService.artistsOnCorusSectionEnabled
    val trendingArtistsSectionEnabled: Boolean get() = remoteConfigService.trendingArtistsSectionEnabled

    // ── Unified search state ──

    private val _unifiedFilter = MutableStateFlow(UnifiedSearchFilter.ALL)
    val unifiedFilter: StateFlow<UnifiedSearchFilter> = _unifiedFilter.asStateFlow()

    /**
     * The query each vertical last COMMITTED results for. Written on success
     * only, so a cancelled fan-out never marks a vertical as served. Lets a
     * chip switch after the ALL fan-out reuse the results it just fetched
     * instead of clearing + refetching the same query (skeleton flash).
     *
     * Exposed as a flow because the blended view also reads it to decide when
     * Music/Film have BOTH settled — the point at which their relative order is
     * safe to compute (see [UnifiedSearchRanking]).
     */
    private val _lastFetchedQuery = MutableStateFlow<Map<UnifiedSearchFilter, String>>(emptyMap())
    val lastFetchedQuery: StateFlow<Map<UnifiedSearchFilter, String>> = _lastFetchedQuery.asStateFlow()

    private fun markVerticalFetched(vertical: UnifiedSearchFilter, query: String) {
        _lastFetchedQuery.value = _lastFetchedQuery.value + (vertical to query)
    }

    /**
     * Chip tap: narrows/widens the rendered verticals. Re-runs the search so
     * any vertical that hasn't served the current query yet fetches — the
     * per-vertical [lastFetchedQuery] guard makes already-served verticals
     * no-ops, so a same-query chip switch renders instantly.
     */
    fun setUnifiedFilter(filter: UnifiedSearchFilter) {
        if (_unifiedFilter.value == filter) return
        _unifiedFilter.value = filter
        analyticsService.logSearchFilterChanged(filter.value)
        val query = _searchQuery.value
        if (query.isNotBlank()) {
            search(query, _activeTab.value)
        }
    }

    private val _hashtagSearchResults = MutableStateFlow<List<CymbalHashtag>>(emptyList())
    val hashtagSearchResults: StateFlow<List<CymbalHashtag>> = _hashtagSearchResults.asStateFlow()

    private val _isSearching = MutableStateFlow(false)
    val isSearching: StateFlow<Boolean> = _isSearching.asStateFlow()

    // True once we positively know the viewer hasn't posted enough for taste
    // matches to be possible (count < TASTE_MATCH_MIN_POSTS). Drives skipping the
    // skeleton in the UI so a below-threshold viewer sees the explainer
    // immediately instead of a ~5s shimmer. Kept in sync by the init-block
    // collector below; the fetch and poll read the profile directly for a
    // guard that can't lag this flow.
    //
    // MUST be declared before `init`: the collector's first emission runs
    // synchronously during construction (viewModelScope uses Main.immediate and
    // userProfile is a StateFlow), so a later declaration leaves this field null
    // and the emit NPEs — crashing the app on launch.
    private val _belowTasteMatchThreshold = MutableStateFlow(false)
    val belowTasteMatchThreshold: StateFlow<Boolean> = _belowTasteMatchThreshold.asStateFlow()

    init {
        // Keep the below-threshold gate in sync with the viewer's post count so
        // the skeleton suppression flips live (e.g. the moment their 2nd post
        // lands while they're on Search).
        viewModelScope.launch {
            authRepository.userProfile.collect { profile ->
                _belowTasteMatchThreshold.value = isBelowTasteMatchThreshold(profile)
            }
        }
        viewModelScope.launch {
            networkMonitor.isConnected.collect { connected ->
                // Auto-retry the active search when the network returns if the
                // previous attempt errored and the current tab has no results.
                if (!connected || !_searchHasError.value) return@collect
                val query = _searchQuery.value
                if (query.isBlank()) return@collect
                val tab = _activeTab.value
                val empty = if (unifiedQueryMode) {
                    // Unified: "empty" = nothing rendered by the active filter.
                    !verticalHasResults(_unifiedFilter.value)
                } else {
                    when (tab) {
                        0 -> _userSearchResults.value.isEmpty()
                        1 -> _songSearchResults.value.isEmpty()
                        2 -> _filmSearchResults.value.isEmpty()
                        3 -> _hashtagSearchResults.value.isEmpty()
                        else -> false
                    }
                }
                if (empty) {
                    if (unifiedQueryMode) {
                        // retrySearch (not search) so the unified served-query
                        // skip can't swallow the reconnect refetch.
                        retrySearch()
                    } else {
                        search(query, tab)
                    }
                }
            }
        }
    }

    // Windowed trending hashtags + cache for prefix search. The values are
    // TrendingHashtag (with windowed cymbalCount + followerCount denormalized
    // off `trending_cache/hashtags`) so the row subtitle can render
    // "N coruses · M followers" without a per-row Firestore read.
    private val _trendingHashtags = MutableStateFlow<List<TrendingHashtag>>(emptyList())
    val trendingHashtags: StateFlow<List<TrendingHashtag>> = _trendingHashtags.asStateFlow()

    private val _isTrendingHashtagsLoading = MutableStateFlow(true)
    val isTrendingHashtagsLoading: StateFlow<Boolean> = _isTrendingHashtagsLoading.asStateFlow()

    private var hasLoadedTrendingHashtags = false
    private val hashtagSearchCache = mutableMapOf<String, List<CymbalHashtag>>()
    private val hashtagPreviewCache = mutableMapOf<String, FirestoreDataSource.HashtagPreview>()
    private val userSearchCache = mutableMapOf<String, List<CymbalUser>>()

    /** Synchronous read of the in-memory preview cache. Returned by reference
     *  so a row can seed its initial state without an extra suspend hop —
     *  prevents the shimmer flash when the user leaves the hashtags tab and
     *  returns (the row is recomposed but the cache survives). */
    fun cachedHashtagPreview(tag: String): FirestoreDataSource.HashtagPreview? =
        hashtagPreviewCache[tag.lowercase()]

    /** Cached fetch of `(album-art mosaic, live count)` for a hashtag.
     *  Used by the hashtag rows so each row only hits Firestore once per
     *  session. Mirrors iOS `DatabaseService.fetchHashtagPreview`. */
    suspend fun fetchHashtagPreview(tag: String): FirestoreDataSource.HashtagPreview {
        val key = tag.lowercase()
        hashtagPreviewCache[key]?.let { return it }
        val preview = firestoreDataSource.fetchHashtagPreview(tag)
        hashtagPreviewCache[key] = preview
        return preview
    }

    // Hashtags the current user follows (lowercased). Single source of truth
    // for the follow pills in both the trending list and search results.
    // Populated on init via `fetchFollowedHashtagNames`, updated optimistically
    // on toggle, and refreshed when the Search tab becomes active so it picks
    // up changes the user made on `HashtagFeedScreen`.
    private val _followedHashtagNames = MutableStateFlow<Set<String>>(emptySet())
    val followedHashtagNames: StateFlow<Set<String>> = _followedHashtagNames.asStateFlow()

    // Trending
    private val _trendingSongs = MutableStateFlow<List<TrendingSong>>(emptyList())
    val trendingSongs: StateFlow<List<TrendingSong>> = _trendingSongs.asStateFlow()

    private val _trendingMovies = MutableStateFlow<List<TrendingMovie>>(emptyList())
    val trendingMovies: StateFlow<List<TrendingMovie>> = _trendingMovies.asStateFlow()

    private val _isTrendingLoading = MutableStateFlow(true)
    val isTrendingLoading: StateFlow<Boolean> = _isTrendingLoading.asStateFlow()

    private val _isTrendingMoviesLoading = MutableStateFlow(true)
    val isTrendingMoviesLoading: StateFlow<Boolean> = _isTrendingMoviesLoading.asStateFlow()

    // Selected time window per tab. Persisted in DataStore so the user's
    // last choice survives app restarts. Songs and films are independent.
    val trendingSongsWindow: StateFlow<TrendingWindow> =
        preferencesDataStore.trendingSongsWindow
            .map { TrendingWindow.fromKey(it) }
            .stateIn(viewModelScope, SharingStarted.Eagerly, TrendingWindow.DEFAULT)

    val trendingFilmsWindow: StateFlow<TrendingWindow> =
        preferencesDataStore.trendingFilmsWindow
            .map { TrendingWindow.fromKey(it) }
            .stateIn(viewModelScope, SharingStarted.Eagerly, TrendingWindow.DEFAULT)

    val trendingHashtagsWindow: StateFlow<TrendingWindow> =
        preferencesDataStore.trendingHashtagsWindow
            .map { TrendingWindow.fromKey(it) }
            .stateIn(viewModelScope, SharingStarted.Eagerly, TrendingWindow.DEFAULT)

    val trendingArtistsWindow: StateFlow<TrendingWindow> =
        preferencesDataStore.trendingArtistsWindow
            .map { TrendingWindow.fromKey(it) }
            .stateIn(viewModelScope, SharingStarted.Eagerly, TrendingWindow.DEFAULT)

    private val _trendingArtists = MutableStateFlow<List<TrendingArtist>>(emptyList())
    val trendingArtists: StateFlow<List<TrendingArtist>> = _trendingArtists.asStateFlow()

    private val _isTrendingArtistsLoading = MutableStateFlow(true)
    val isTrendingArtistsLoading: StateFlow<Boolean> = _isTrendingArtistsLoading.asStateFlow()

    private var hasLoadedTrendingArtists = false

    private val _isResolvingArtist = MutableStateFlow(false)
    val isResolvingArtist: StateFlow<Boolean> = _isResolvingArtist.asStateFlow()

    val trendingAlbumsWindow: StateFlow<TrendingWindow> =
        preferencesDataStore.trendingAlbumsWindow
            .map { TrendingWindow.fromKey(it) }
            .stateIn(viewModelScope, SharingStarted.Eagerly, TrendingWindow.DEFAULT)

    private val _trendingAlbums = MutableStateFlow<List<TrendingAlbum>>(emptyList())
    val trendingAlbums: StateFlow<List<TrendingAlbum>> = _trendingAlbums.asStateFlow()

    private val _isTrendingAlbumsLoading = MutableStateFlow(true)
    val isTrendingAlbumsLoading: StateFlow<Boolean> = _isTrendingAlbumsLoading.asStateFlow()

    private val _newReleaseAlbums = MutableStateFlow<List<TrendingAlbum>>(emptyList())
    val newReleaseAlbums: StateFlow<List<TrendingAlbum>> = _newReleaseAlbums.asStateFlow()

    private val _isNewReleaseAlbumsLoading = MutableStateFlow(true)
    val isNewReleaseAlbumsLoading: StateFlow<Boolean> = _isNewReleaseAlbumsLoading.asStateFlow()

    private var hasLoadedTrendingAlbums = false
    private var hasLoadedNewReleaseAlbums = false

    private val _newReleaseMovies = MutableStateFlow<List<TrendingMovie>>(emptyList())
    val newReleaseMovies: StateFlow<List<TrendingMovie>> = _newReleaseMovies.asStateFlow()

    private val _isNewReleaseMoviesLoading = MutableStateFlow(true)
    val isNewReleaseMoviesLoading: StateFlow<Boolean> = _isNewReleaseMoviesLoading.asStateFlow()

    private var hasLoadedNewReleaseMovies = false

    val trendingDirectorsWindow: StateFlow<TrendingWindow> =
        preferencesDataStore.trendingDirectorsWindow
            .map { TrendingWindow.fromKey(it) }
            .stateIn(viewModelScope, SharingStarted.Eagerly, TrendingWindow.DEFAULT)

    private val _trendingDirectors = MutableStateFlow<List<TrendingDirector>>(emptyList())
    val trendingDirectors: StateFlow<List<TrendingDirector>> = _trendingDirectors.asStateFlow()

    private val _isTrendingDirectorsLoading = MutableStateFlow(true)
    val isTrendingDirectorsLoading: StateFlow<Boolean> = _isTrendingDirectorsLoading.asStateFlow()

    private var hasLoadedTrendingDirectors = false

    private val _isResolvingDirector = MutableStateFlow(false)
    val isResolvingDirector: StateFlow<Boolean> = _isResolvingDirector.asStateFlow()

    private val _isResolvingAlbum = MutableStateFlow(false)
    val isResolvingAlbum: StateFlow<Boolean> = _isResolvingAlbum.asStateFlow()

    fun setTrendingSongsWindow(window: TrendingWindow) {
        viewModelScope.launch {
            preferencesDataStore.setTrendingSongsWindow(window.key)
            // Flash the skeleton briefly so the user gets feedback that the
            // selection registered, even when the new list is in-cache. The
            // delay ensures Compose actually paints the loading state — without
            // it, the loading flag flips true→false in the same frame and the
            // skeleton never appears. Matches the iOS/web behavior.
            _isTrendingLoading.value = true
            _trendingSongs.value = emptyList()
            val start = System.currentTimeMillis()
            val fetched = try {
                exploreRepository.fetchTrendingSongs(window)
            } catch (e: Exception) {
                Log.e("SearchVM", "Failed to switch trending songs window", e)
                emptyList()
            }
            val elapsed = System.currentTimeMillis() - start
            if (elapsed < TRENDING_WINDOW_MIN_DISPLAY_MS) {
                kotlinx.coroutines.delay(TRENDING_WINDOW_MIN_DISPLAY_MS - elapsed)
            }
            _trendingSongs.value = fetched
            _isTrendingLoading.value = false
        }
    }

    fun setTrendingFilmsWindow(window: TrendingWindow) {
        viewModelScope.launch {
            preferencesDataStore.setTrendingFilmsWindow(window.key)
            _isTrendingMoviesLoading.value = true
            _trendingMovies.value = emptyList()
            val start = System.currentTimeMillis()
            val fetched = try {
                exploreRepository.fetchTrendingMovies(window)
            } catch (e: Exception) {
                Log.e("SearchVM", "Failed to switch trending films window", e)
                emptyList()
            }
            val elapsed = System.currentTimeMillis() - start
            if (elapsed < TRENDING_WINDOW_MIN_DISPLAY_MS) {
                kotlinx.coroutines.delay(TRENDING_WINDOW_MIN_DISPLAY_MS - elapsed)
            }
            _trendingMovies.value = fetched
            _isTrendingMoviesLoading.value = false
        }
    }

    fun setTrendingHashtagsWindow(window: TrendingWindow) {
        viewModelScope.launch {
            preferencesDataStore.setTrendingHashtagsWindow(window.key)
            _isTrendingHashtagsLoading.value = true
            _trendingHashtags.value = emptyList()
            val start = System.currentTimeMillis()
            val fetched = try {
                firestoreDataSource.fetchTrendingHashtagsWindowed(window, limit = 20)
            } catch (e: Exception) {
                Log.e("SearchVM", "Failed to switch trending hashtags window", e)
                emptyList()
            }
            val elapsed = System.currentTimeMillis() - start
            if (elapsed < TRENDING_WINDOW_MIN_DISPLAY_MS) {
                kotlinx.coroutines.delay(TRENDING_WINDOW_MIN_DISPLAY_MS - elapsed)
            }
            _trendingHashtags.value = fetched
            _isTrendingHashtagsLoading.value = false
        }
    }

    fun setTrendingArtistsWindow(window: TrendingWindow) {
        viewModelScope.launch {
            preferencesDataStore.setTrendingArtistsWindow(window.key)
            _isTrendingArtistsLoading.value = true
            _trendingArtists.value = emptyList()
            val start = System.currentTimeMillis()
            val fetched = try {
                exploreRepository.fetchTrendingArtists(window)
            } catch (e: Exception) {
                Log.e("SearchVM", "Failed to switch trending artists window", e)
                emptyList()
            }
            val elapsed = System.currentTimeMillis() - start
            if (elapsed < TRENDING_WINDOW_MIN_DISPLAY_MS) {
                kotlinx.coroutines.delay(TRENDING_WINDOW_MIN_DISPLAY_MS - elapsed)
            }
            _trendingArtists.value = fetched
            _isTrendingArtistsLoading.value = false
            viewModelScope.launch {
                cloudFunctions.prefetchArtistDestinations(fetched.map { it.artistName })
            }
        }
    }

    suspend fun resolveTrendingArtist(artist: TrendingArtist): fm.corus.android.ui.navigation.ArtistPageRoute? {
        cloudFunctions.cachedResolvedArtist(artist.artistName)?.let { cached ->
            return fm.corus.android.ui.navigation.ArtistPageRoute(
                artistId = cached.id,
                name = cached.name,
                imageUrl = cached.imageUrl,
            )
        }
        if (_isResolvingArtist.value) return null
        _isResolvingArtist.value = true
        DestinationResolvingOverlay.setResolving(true)
        return try {
            val resolved = cloudFunctions.resolveArtistByName(artist.artistName) ?: return null
            viewModelScope.launch {
                runCatching { cloudFunctions.fetchArtistDetail(resolved.id, resolved.name) }
            }
            fm.corus.android.ui.navigation.ArtistPageRoute(
                artistId = resolved.id,
                name = resolved.name,
                imageUrl = resolved.imageUrl,
            )
        } finally {
            _isResolvingArtist.value = false
            DestinationResolvingOverlay.setResolving(
                _isResolvingArtist.value || _isResolvingAlbum.value || _isResolvingDirector.value,
            )
        }
    }

    fun setTrendingAlbumsWindow(window: TrendingWindow) {
        viewModelScope.launch {
            preferencesDataStore.setTrendingAlbumsWindow(window.key)
            _isTrendingAlbumsLoading.value = true
            _trendingAlbums.value = emptyList()
            val start = System.currentTimeMillis()
            val fetched = try {
                exploreRepository.fetchTrendingAlbums(window)
            } catch (e: Exception) {
                Log.e("SearchVM", "Failed to switch trending albums window", e)
                emptyList()
            }
            val elapsed = System.currentTimeMillis() - start
            if (elapsed < TRENDING_WINDOW_MIN_DISPLAY_MS) {
                kotlinx.coroutines.delay(TRENDING_WINDOW_MIN_DISPLAY_MS - elapsed)
            }
            _trendingAlbums.value = fetched
            _isTrendingAlbumsLoading.value = false
        }
    }

    suspend fun resolveTrendingAlbum(album: TrendingAlbum): TrendingAlbumOpen? {
        TrendingAlbumDestinationCache.peek(album)?.let { return it }
        if (_isResolvingAlbum.value) return null
        _isResolvingAlbum.value = true
        DestinationResolvingOverlay.setResolving(true)
        return try {
            resolveTrendingAlbumOpen(
                album = album,
                fetchCatalog = { id, name, artist ->
                    runCatching { cloudFunctions.fetchAlbumCatalog(id, name, artist) }.getOrNull()
                },
                resolveByName = { name, artist -> resolveAlbumByName(name, artist) },
                today = java.time.LocalDate.now(java.time.ZoneOffset.UTC).toString(),
            )
        } finally {
            _isResolvingAlbum.value = false
            DestinationResolvingOverlay.setResolving(
                _isResolvingArtist.value || _isResolvingAlbum.value || _isResolvingDirector.value,
            )
        }
    }

    fun setTrendingDirectorsWindow(window: TrendingWindow) {
        viewModelScope.launch {
            preferencesDataStore.setTrendingDirectorsWindow(window.key)
            _isTrendingDirectorsLoading.value = true
            _trendingDirectors.value = emptyList()
            val start = System.currentTimeMillis()
            val fetched = try {
                exploreRepository.fetchTrendingDirectors(window)
            } catch (e: Exception) {
                Log.e("SearchVM", "Failed to switch trending directors window", e)
                emptyList()
            }
            val elapsed = System.currentTimeMillis() - start
            if (elapsed < TRENDING_WINDOW_MIN_DISPLAY_MS) {
                kotlinx.coroutines.delay(TRENDING_WINDOW_MIN_DISPLAY_MS - elapsed)
            }
            _trendingDirectors.value = fetched
            _isTrendingDirectorsLoading.value = false
        }
    }

    suspend fun resolveTrendingDirector(director: TrendingDirector): fm.corus.android.ui.navigation.DirectorPageRoute? {
        if (director.directorId.isNotEmpty()) {
            return fm.corus.android.ui.navigation.DirectorPageRoute(
                directorId = director.directorId,
                name = director.directorName.ifBlank { null },
                imageUrl = director.posterLargeURL ?: director.posterURL,
            )
        }
        tmdbRepository.cachedResolvedDirector(director.directorName)?.let { cached ->
            return fm.corus.android.ui.navigation.DirectorPageRoute(
                directorId = cached.id,
                name = cached.name,
                imageUrl = cached.imageUrl ?: director.posterLargeURL ?: director.posterURL,
            )
        }
        if (_isResolvingDirector.value) return null
        _isResolvingDirector.value = true
        DestinationResolvingOverlay.setResolving(true)
        return try {
            val resolved = tmdbRepository.resolveDirectorByName(director.directorName) ?: return null
            fm.corus.android.ui.navigation.DirectorPageRoute(
                directorId = resolved.id,
                name = resolved.name,
                imageUrl = resolved.imageUrl ?: director.posterLargeURL ?: director.posterURL,
            )
        } finally {
            _isResolvingDirector.value = false
            DestinationResolvingOverlay.setResolving(
                _isResolvingArtist.value || _isResolvingAlbum.value || _isResolvingDirector.value,
            )
        }
    }

    private suspend fun resolveAlbumByName(name: String, artist: String): AlbumSearchSummary? {
        val query = listOf(name, artist).filter { it.isNotBlank() }.joinToString(" ")
        if (query.isBlank()) return null
        val page = musicSearchRepository.search(
            query = query,
            limit = 5,
            includeAlbums = true,
            albumsMatchArtist = true,
        )
        return page.albums.firstOrNull { albumTitlesMatch(it.title, name) }
            ?: page.albums.firstOrNull()
    }

    companion object {
        private const val TRENDING_WINDOW_MIN_DISPLAY_MS = 280L
        // Upper bound on the taste-match load so the spinner can never latch on a
        // hung backend (mirrors iOS's 15s race; the callable itself is also
        // bounded at the data source).
        private const val SUGGESTIONS_LOAD_TIMEOUT_MS = 15_000L

        // Minimum posts before taste matches are worth fetching. A taste match
        // needs >=3 shared artists, which a user with one post can't clear, so
        // fetching for them only ever resolves to empty after a ~5s skeleton.
        // Below this we skip the fetch, the skeleton, and the cold-start poll.
        // Mirrors iOS `tasteMatchMinimumPosts` and web `TASTE_MATCH_MIN_POSTS`.
        const val TASTE_MATCH_MIN_POSTS = 2
    }

    // Suggestions
    private val _suggestedMatches = MutableStateFlow<List<SuggestedUserMatch>>(emptyList())
    val suggestedMatches: StateFlow<List<SuggestedUserMatch>> = _suggestedMatches.asStateFlow()

    // Quiz-seed fallback (web parity): onboarding shows matches at >=1 shared
    // artist, but the rail's card bar (isTasteMatch) is >=3 — so a quiz-taker
    // whose real list settles without a single qualifying match would see the
    // "post more" explainer minutes after being SHOWN their matches. When that
    // happens, re-run the onboarding matcher on the saved seed and render the
    // same list they saw during signup. Viewer-side only; clears naturally
    // once real (post-based) matches exist.
    private val _seedTasteMatches = MutableStateFlow<List<SuggestedUserMatch>>(emptyList())
    val seedTasteMatches: StateFlow<List<SuggestedUserMatch>> = _seedTasteMatches.asStateFlow()
    private var seedFallbackAttempted = false

    private val _isSuggestedLoading = MutableStateFlow(true)
    val isSuggestedLoading: StateFlow<Boolean> = _isSuggestedLoading.asStateFlow()

    // True while the cold-start poll (after an initial empty taste-match
    // response) is still running. Keeps the music-matches skeleton up for up
    // to ~3s in case the backend's eager recompute fired on a recent post is
    // still in flight.
    private val _isTasteMatchPolling = MutableStateFlow(false)
    val isTasteMatchPolling: StateFlow<Boolean> = _isTasteMatchPolling.asStateFlow()

    // True when the taste-match fetch failed/timed out with nothing to show, so
    // the UI hides the section entirely rather than rendering the "post more"
    // explainer (which would falsely imply the user has zero matches). Only a
    // successful fetch that genuinely returns no taste matches shows the explainer.
    private val _tasteMatchLoadFailed = MutableStateFlow(false)
    val tasteMatchLoadFailed: StateFlow<Boolean> = _tasteMatchLoadFailed.asStateFlow()

    // Follow state
    private val _followingIds = MutableStateFlow<Set<String>>(emptySet())
    val followingIds: StateFlow<Set<String>> = _followingIds.asStateFlow()

    private val _localFollowedIds = MutableStateFlow<Set<String>>(emptySet())
    val localFollowedIds: StateFlow<Set<String>> = _localFollowedIds.asStateFlow()

    /** Optimistic unfollows. The repo `followingIds` collector can overwrite
     *  `_followingIds` with a stale set while `unfollowUser` is in flight;
     *  this set keeps the pill on Follow until they follow again. */
    private val _localUnfollowedIds = MutableStateFlow<Set<String>>(emptySet())
    val localUnfollowedIds: StateFlow<Set<String>> = _localUnfollowedIds.asStateFlow()

    // Recent searches (persisted as a mixed-kind RecentSearchItem list in DataStore)
    val recentSearches: StateFlow<List<RecentSearchItem>> = preferencesDataStore.recentSearches
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Contact sync
    private val _contactMatches = MutableStateFlow<List<CymbalUser>>(emptyList())
    val contactMatches: StateFlow<List<CymbalUser>> = _contactMatches.asStateFlow()

    private val _isSyncingContacts = MutableStateFlow(false)
    val isSyncingContacts: StateFlow<Boolean> = _isSyncingContacts.asStateFlow()

    private val _showNoContactMatches = MutableStateFlow(false)
    val showNoContactMatches: StateFlow<Boolean> = _showNoContactMatches.asStateFlow()

    val contactsSyncStatus: StateFlow<String> = preferencesDataStore.contactsSyncStatus
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "notAsked")

    // New on Corus (recently joined)
    private val _newUsers = MutableStateFlow<List<CymbalUser>>(emptyList())
    val newUsers: StateFlow<List<CymbalUser>> = _newUsers.asStateFlow()

    private val _isNewUsersLoading = MutableStateFlow(true)
    val isNewUsersLoading: StateFlow<Boolean> = _isNewUsersLoading.asStateFlow()

    // Corus Club Members (most recently signed up first)
    private val _clubMembers = MutableStateFlow<List<CymbalUser>>(emptyList())
    val clubMembers: StateFlow<List<CymbalUser>> = _clubMembers.asStateFlow()

    private val _isClubMembersLoading = MutableStateFlow(true)
    val isClubMembersLoading: StateFlow<Boolean> = _isClubMembersLoading.asStateFlow()

    private val _artistsOnCorus = MutableStateFlow<List<CymbalUser>>(emptyList())
    val artistsOnCorus: StateFlow<List<CymbalUser>> = _artistsOnCorus.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    val currentUserId: String? get() = authRepository.currentUserId

    private var searchJob: Job? = null

    fun refresh() {
        viewModelScope.launch {
            _isRefreshing.value = true
            // Explicit user refresh should bypass the in-memory TTL on trending.
            exploreRepository.clearCaches()
            loadInitialData(forceRefresh = true)
            // Give data a moment to load before hiding the indicator
            delay(500)
            _isRefreshing.value = false
        }
    }

    private var hasSeededBrowse = false
    private var hasStartedInitialLoad = false

    /**
     * Last-session Search browse snapshot — no network. Call as soon as the
     * Search tab is selected so rails can paint while the live load waits
     * [fm.corus.android.ui.theme.CorusMotion.SEARCH_LIVE_LOAD_DELAY_MS].
     */
    fun seedBrowseCache() {
        val uid = authRepository.currentUserId ?: return
        if (hasSeededBrowse) return
        hasSeededBrowse = true
        viewModelScope.launch {
            userRepository.followingIds.collect { ids ->
                _followingIds.value = ids
            }
        }
        viewModelScope.launch {
            val cached = userRepository.peekPersistedSuggestions(uid)
            if (!cached.isNullOrEmpty() && _suggestedMatches.value.isEmpty()) {
                _suggestedMatches.value = cached
                _isSuggestedLoading.value = false
            }
        }
        viewModelScope.launch { hydrateBrowseSnapshot(uid) }
    }

    fun loadInitialData(forceRefresh: Boolean = false) {
        val uid = authRepository.currentUserId ?: return
        if (!forceRefresh && hasStartedInitialLoad) return
        hasStartedInitialLoad = true
        seedBrowseCache()
        // Fetch taste matches and mutual connections (Firestore) in parallel,
        // then merge them — matching how iOS loads suggestions. Taste matches go
        // through the repository's 4h cache (warmed from DataStore at sign-in) so
        // repeat opens render instantly; pull-to-refresh forces a fresh fetch.
        viewModelScope.launch {
            // Below the post threshold the viewer can't have taste matches yet, so
            // skip the expensive suggest scan and treat it as a successful empty
            // result (emptyList, not null) — the explainer renders, no skeleton.
            // Read the profile directly (not the derived flow, whose collection can
            // lag) so the guard is deterministic on the first load.
            val belowThreshold = isBelowTasteMatchThreshold(authRepository.userProfile.value)
            val musicMatchesDeferred = async {
                if (belowThreshold) {
                    emptyList<SuggestedUserMatch>()
                } else {
                    try {
                        // null (not empty) signals a failed/timed-out load so the UI can
                        // distinguish "couldn't load" from "loaded, genuinely no matches".
                        userRepository.getSuggestedUsers(uid, forceRefresh = forceRefresh)
                    } catch (e: Exception) {
                        Log.e("SearchVM", "Failed to load suggested users", e)
                        null
                    }
                }
            }
            val mutualConnectionsDeferred = async {
                try {
                    // Bound the Firestore fan-out so a hung read can't stall the
                    // loader (the music-match path is bounded at the data source).
                    withTimeoutOrNull(SUGGESTIONS_LOAD_TIMEOUT_MS) {
                        // Try precomputed mutual connections first
                        var mutuals = firestoreDataSource.fetchPrecomputedMutualConnections(uid, limit = 20)
                        // Fallback: client-side graph traversal (matching iOS)
                        if (mutuals.isEmpty()) {
                            val followingIds = firestoreDataSource.fetchFollowingIds(uid)
                            val excludeIds = followingIds + uid
                            mutuals = firestoreDataSource.fetchFriendsOfFriends(uid, excludeIds, limit = 20)
                        }
                        Log.d("SearchVM", "Mutual connections loaded: ${mutuals.size}")
                        mutuals.map { mc ->
                            SuggestedUserMatch(
                                user = mc.user,
                                matchData = null,
                                suggestionReason = SuggestionReason(
                                    mutualNames = mc.mutualUsernames,
                                    mutualCount = mc.mutualCount,
                                ),
                            )
                        }
                    } ?: emptyList()
                } catch (e: Exception) {
                    Log.e("SearchVM", "Failed to load mutual connections", e)
                    emptyList()
                }
            }

            try {
                val musicMatchesResult = musicMatchesDeferred.await()
                // On failure, keep any taste matches we're already showing so a
                // transient error never wipes the section (mirrors iOS's cache
                // fallback). On first load this is empty, so nothing changes.
                val musicMatches = musicMatchesResult
                    ?: _suggestedMatches.value.filter { it.hasTasteMatch() }
                val socialMatches = mutualConnectionsDeferred.await()
                Log.d("SearchVM", "Music matches: ${musicMatches.size}, Social matches: ${socialMatches.size}")
                for (m in musicMatches) {
                    Log.d("SearchVM", "  CF user: ${m.user.username} cymbal=${m.user.cymbalCount} hasSim=${m.matchData?.hasSimilarityData} mutualNames=${m.suggestionReason?.mutualNames} artistsInCommon=${m.user.artistsInCommonCount}")
                }

                // Merge: music matches first, then social suggestions (dedup by user ID).
                // Carry over suggestionReason from social onto music matches.
                val socialReasonById = socialMatches
                    .filter { it.suggestionReason != null }
                    .associateBy({ it.user.id }, { it.suggestionReason!! })

                val seenIds = mutableSetOf<String>()
                val merged = mutableListOf<SuggestedUserMatch>()

                for (match in musicMatches) {
                    if (!seenIds.add(match.user.id)) continue
                    val withReason = if (match.suggestionReason == null) {
                        socialReasonById[match.user.id]?.let { match.copy(suggestionReason = it) } ?: match
                    } else match
                    merged.add(withReason)
                }
                for (match in socialMatches) {
                    if (!seenIds.add(match.user.id)) continue
                    merged.add(match)
                }

                _suggestedMatches.value = merged
                // Hide the section only on an actual failed load with no taste
                // matches to show. A successful empty pull keeps this false so the
                // "post more" explainer renders instead.
                _tasteMatchLoadFailed.value = musicMatchesResult == null && merged.none { it.hasTasteMatch() }
                // Successful pull, zero qualifying matches, and the viewer has a
                // quiz seed → surface the onboarding matcher's list instead of
                // the explainer.
                if (musicMatchesResult != null && merged.none { it.hasTasteMatch() }) {
                    loadSeedFallbackMatchesIfNeeded()
                }
            } finally {
                // Always clear the spinner, even if an await is cancelled or a merge
                // step throws — the loader must never latch on (both fetches above
                // are time-bounded so the awaits cannot hang).
                _isSuggestedLoading.value = false
            }
            pollTasteMatchesIfMissing(uid)
        }
        viewModelScope.launch {
            try {
                val songs = exploreRepository.fetchTrendingSongs(trendingSongsWindow.value)
                _trendingSongs.value = songs
                if (songs.isNotEmpty()) preferencesDataStore.persistSearchTrendingSongs(songs)
            } catch (e: Exception) {
                Log.e("SearchVM", "Failed to load trending songs", e)
            }
            _isTrendingLoading.value = false
        }
        viewModelScope.launch {
            try {
                val movies = exploreRepository.fetchTrendingMovies(trendingFilmsWindow.value)
                _trendingMovies.value = movies
                if (movies.isNotEmpty()) preferencesDataStore.persistSearchTrendingMovies(movies)
            } catch (e: Exception) {
                Log.e("SearchVM", "Failed to load trending movies", e)
            }
            _isTrendingMoviesLoading.value = false
        }
        // Match iOS: seed following IDs before firing the new-users fetch so its
        // server-side excludeIds actually exclude already-followed users.
        viewModelScope.launch {
            try {
                userRepository.prefetchFollowingSet(uid)
                _followingIds.value = userRepository.followingIds.value
            } catch (e: Exception) {
                Log.e("SearchVM", "Failed to prefetch following set", e)
            }
            loadNewUsers()
            loadClubMembers()
            loadArtistsOnCorus()
        }
        refreshFollowedHashtags()
        // Classic mode loads trending hashtags lazily on the tab switch; the
        // unified blended zero state renders them on the first screen, so load
        // now. Pull-to-refresh routes here with forceRefresh, which must
        // bypass the once-per-session guard.
        if (unifiedSearchEnabled || segmentedSearchEnabled) {
            if (forceRefresh) hasLoadedTrendingHashtags = false
            loadTrendingHashtagsIfNeeded()
        }
        if (trendingArtistsSectionEnabled || segmentedSearchEnabled) {
            if (forceRefresh) hasLoadedTrendingArtists = false
            loadTrendingArtistsIfNeeded()
        }
        if (forceRefresh) {
            hasLoadedTrendingAlbums = false
            hasLoadedNewReleaseAlbums = false
            hasLoadedNewReleaseMovies = false
            hasLoadedTrendingDirectors = false
        }
        loadTrendingAlbumsIfNeeded()
        loadNewReleaseAlbumsIfNeeded()
        loadNewReleaseMoviesIfNeeded()
        loadTrendingDirectorsIfNeeded()
    }

    // Cold-start poll: if the initial fetch returned no taste matches, the
    // backend's eager recompute (fired by a recent post create) may still be
    // in flight. Refetch up to ~3s before giving up.
    private suspend fun pollTasteMatchesIfMissing(uid: String) {
        // Viewer below the post threshold: they can't have taste matches yet and
        // we skipped the fetch, so there's nothing in flight. Skip the poll
        // entirely so the skeleton never shows for them. Read the profile
        // directly (not the derived StateFlow, whose collection can lag) so the
        // guard is deterministic.
        if (isBelowTasteMatchThreshold(authRepository.userProfile.value)) return
        if (_suggestedMatches.value.any { it.hasTasteMatch() }) return

        _isTasteMatchPolling.value = true
        try {
            repeat(4) {
                delay(750L)
                val fresh = try {
                    // Bypass the cache: the backend recompute we're waiting on may
                    // still be in flight. A successful fetch also refreshes the cache.
                    userRepository.getSuggestedUsers(uid, forceRefresh = true)
                } catch (e: Exception) {
                    Log.w("SearchVM", "Taste-match poll attempt failed", e)
                    return@repeat
                }
                // A successful poll response (even an empty one) clears the failed
                // state: the section should show the explainer, not stay hidden.
                _tasteMatchLoadFailed.value = false
                if (fresh.none { it.hasTasteMatch() }) return@repeat

                // Merge fresh music matches over the existing social suggestions.
                val seenIds = mutableSetOf<String>()
                val merged = mutableListOf<SuggestedUserMatch>()
                for (match in fresh) {
                    if (!seenIds.add(match.user.id)) continue
                    merged.add(match)
                }
                for (match in _suggestedMatches.value) {
                    if (!seenIds.add(match.user.id)) continue
                    merged.add(match)
                }
                _suggestedMatches.value = merged
                return
            }
        } finally {
            _isTasteMatchPolling.value = false
        }
    }

    private fun SuggestedUserMatch.hasTasteMatch(): Boolean = isTasteMatch

    /** One attempt per session: read the saved quiz seed and re-run the
     *  onboarding matcher on it. Gated on users_v2.tasteSeedCount so the
     *  overwhelming majority of users (no quiz seed) never pay the read. */
    private fun loadSeedFallbackMatchesIfNeeded() {
        if (seedFallbackAttempted) return
        val uid = authRepository.currentUserId ?: return
        if ((authRepository.userProfile.value?.tasteSeedCount ?: 0) <= 0) return
        seedFallbackAttempted = true
        viewModelScope.launch {
            try {
                val picks = firestoreDataSource.fetchMyTasteSeedPicks(uid)
                if (picks.isEmpty()) return@launch
                val result = cloudFunctions.getOnboardingTasteMatches(picks)
                _seedTasteMatches.value = result.users
                Log.d("SearchVM", "Seed fallback matches: ${result.users.size}")
            } catch (e: Exception) {
                Log.e("SearchVM", "Seed fallback failed", e)
            }
        }
    }

    // Below the post threshold, taste matches can't exist yet (they need >=3
    // shared artists). A null profile (not loaded) is treated as "not below"
    // so we fall back to normal skeleton behavior, matching iOS.
    // A persisted onboarding-quiz seed (tasteSeedCount > 0) bypasses the
    // post-count check: the backend ranks matches from the quiz picks, so the
    // fetch is NOT pointless for a quiz-taker who hasn't posted yet.
    private fun isBelowTasteMatchThreshold(profile: CymbalUser?): Boolean {
        if (profile == null) return false
        if ((profile.tasteSeedCount ?: 0) > 0) return false
        return profile.cymbalCount < TASTE_MATCH_MIN_POSTS
    }

    private suspend fun hydrateBrowseSnapshot(uid: String) {
        val songs = preferencesDataStore.loadSearchTrendingSongs()
        if (!songs.isNullOrEmpty() && _trendingSongs.value.isEmpty()) {
            _trendingSongs.value = songs
            _isTrendingLoading.value = false
        }
        val movies = preferencesDataStore.loadSearchTrendingMovies()
        if (!movies.isNullOrEmpty() && _trendingMovies.value.isEmpty()) {
            _trendingMovies.value = movies
            _isTrendingMoviesLoading.value = false
        }
        val newUsers = preferencesDataStore.loadSearchNewUsers(uid)
        if (!newUsers.isNullOrEmpty() && _newUsers.value.isEmpty()) {
            _newUsers.value = newUsers
            _isNewUsersLoading.value = false
        }
        val club = preferencesDataStore.loadSearchClubMembers(uid)
        if (!club.isNullOrEmpty() && _clubMembers.value.isEmpty()) {
            _clubMembers.value = club
            _isClubMembersLoading.value = false
        }
        val albums = preferencesDataStore.loadSearchTrendingAlbums()
        if (!albums.isNullOrEmpty() && _trendingAlbums.value.isEmpty()) {
            _trendingAlbums.value = albums
            _isTrendingAlbumsLoading.value = false
        }
        val newReleases = preferencesDataStore.loadSearchNewReleaseAlbums()
        if (!newReleases.isNullOrEmpty() && _newReleaseAlbums.value.isEmpty()) {
            _newReleaseAlbums.value = newReleases
            _isNewReleaseAlbumsLoading.value = false
        }
        val newReleaseMovies = preferencesDataStore.loadSearchNewReleaseMovies()
        if (!newReleaseMovies.isNullOrEmpty() && _newReleaseMovies.value.isEmpty()) {
            _newReleaseMovies.value = newReleaseMovies
            _isNewReleaseMoviesLoading.value = false
        }
        val directors = preferencesDataStore.loadSearchTrendingDirectors()
        if (!directors.isNullOrEmpty() && _trendingDirectors.value.isEmpty()) {
            _trendingDirectors.value = directors
            _isTrendingDirectorsLoading.value = false
        }
    }

    private fun loadNewUsers() {
        val uid = authRepository.currentUserId ?: return
        viewModelScope.launch {
            try {
                val newOnes = userRepository.fetchNewUsers(
                    limit = 10,
                    excludeIds = _followingIds.value + _localFollowedIds.value + uid,
                )
                Log.d("SearchVM", "New users loaded: ${newOnes.size}")
                _newUsers.value = newOnes
                if (newOnes.isNotEmpty()) preferencesDataStore.persistSearchNewUsers(newOnes, uid)
            } catch (e: Exception) {
                Log.e("SearchVM", "Failed to load new users", e)
            }
            _isNewUsersLoading.value = false
        }
    }

    private fun loadClubMembers() {
        val uid = authRepository.currentUserId ?: return
        viewModelScope.launch {
            try {
                // Fetch a generous pool so the rail's "unfollowed first"
                // reorder still has unfollowed members to pull from when the
                // viewer follows most of the active club. Active members are
                // bounded server-side, so this is cheap.
                val members = userRepository.fetchClubMembers(
                    limit = 50,
                    excludeIds = setOf(uid),
                )
                Log.d("SearchVM", "Club members loaded: ${members.size}")
                _clubMembers.value = members
                if (members.isNotEmpty()) preferencesDataStore.persistSearchClubMembers(members, uid)
            } catch (e: Exception) {
                Log.e("SearchVM", "Failed to load club members", e)
            }
            _isClubMembersLoading.value = false
        }
    }

    private fun loadArtistsOnCorus() {
        if (!artistsOnCorusSectionEnabled) return
        viewModelScope.launch {
            try {
                _artistsOnCorus.value = userRepository.fetchArtistsOnCorus()
            } catch (e: Exception) {
                Log.e("SearchVM", "Failed to load artists on Corus", e)
            }
        }
    }

    fun onSearchQueryChange(query: String) {
        _searchQuery.value = query
        if (query.isBlank()) {
            clearSearch()
            return
        }
    }

    fun search(query: String, tab: Int) {
        searchJob?.cancel()
        if (query.isBlank()) {
            clearSearch()
            return
        }

        if (unifiedQueryMode) {
            searchUnified(query)
            return
        }

        // Check if the target tab already has results; if not, show loading immediately
        val hasResults = when (tab) {
            0 -> _userSearchResults.value.isNotEmpty()
            1 -> _songSearchResults.value.isNotEmpty()
            2 -> _filmSearchResults.value.isNotEmpty()
            3 -> _hashtagSearchResults.value.isNotEmpty()
            else -> false
        }
        if (!hasResults) {
            _isSearching.value = true
        }

        // Serve the Users tab from cache instantly on a hit and skip the network
        // round-trip — covers the common backspace-and-retype pattern (e.g.
        // "aiden" → "aide" → "aiden"). Matches iOS SearchView.userSearchCache.
        if (tab == 0) {
            val cached = userSearchCache[query.lowercase().trim()]
            if (cached != null) {
                _userSearchResults.value = cached
                _isSearching.value = false
                return
            }
        }

        searchJob = viewModelScope.launch {
            delay(debounceMs(query))
            _isSearching.value = true
            try {
                when (tab) {
                    0 -> searchUsersVertical(query)
                    1 -> searchMusicVertical(query)
                    2 -> searchFilmsVertical(query)
                    3 -> searchHashtagsVertical(query)
                }
                _searchHasError.value = false
            } catch (e: kotlinx.coroutines.CancellationException) {
                // A newer keystroke (or clearSearch) cancelled this job — that's
                // normal editing flow, not a failure. Rethrow so it's never
                // misread as a server error: swallowing it here flashed the
                // "service unavailable" state mid-type whenever the cancelled
                // request's tab happened to be empty. Far more visible since
                // movie search began routing through the slower tmdbProxy
                // callable, which widened the in-flight cancellation window.
                throw e
            } catch (_: Exception) {
                val empty = when (tab) {
                    0 -> _userSearchResults.value.isEmpty()
                    1 -> _songSearchResults.value.isEmpty()
                    2 -> _filmSearchResults.value.isEmpty()
                    3 -> _hashtagSearchResults.value.isEmpty()
                    else -> false
                }
                if (empty) _searchHasError.value = true
            }
            _isSearching.value = false
        }
    }

    /**
     * Unified search: the ALL filter fans out to every vertical concurrently
     * on one debounce; a narrowed chip runs just that vertical. Verticals that
     * already committed results for this exact query are skipped (see
     * [lastFetchedQuery]), so chip switches on the same query render instantly
     * with no refetch. One vertical failing must not blank the others — the
     * error state only shows when nothing rendered has results.
     */
    private fun searchUnified(query: String) {
        val filter = _unifiedFilter.value
        val verticals = if (filter == UnifiedSearchFilter.ALL) {
            listOf(
                UnifiedSearchFilter.USERS,
                UnifiedSearchFilter.MUSIC,
                UnifiedSearchFilter.FILM,
                UnifiedSearchFilter.HASHTAGS,
            )
        } else {
            listOf(filter)
        }

        if (verticals.none { verticalHasResults(it) }) {
            _isSearching.value = true
        }

        searchJob = viewModelScope.launch {
            // Already served this exact query everywhere visible (chip switch
            // after the ALL fan-out): nothing to fetch.
            val pending = verticals.filter { _lastFetchedQuery.value[it] != query }
            if (pending.isEmpty()) {
                _isSearching.value = false
                return@launch
            }
            delay(debounceMs(query))
            _isSearching.value = true
            var anyFailure = false
            coroutineScope {
                pending.forEach { vertical ->
                    launch {
                        try {
                            when (vertical) {
                                UnifiedSearchFilter.USERS -> searchUsersVertical(query)
                                UnifiedSearchFilter.MUSIC -> searchMusicVertical(query)
                                UnifiedSearchFilter.FILM -> searchFilmsVertical(query)
                                UnifiedSearchFilter.HASHTAGS -> searchHashtagsVertical(query)
                                UnifiedSearchFilter.ALL -> Unit
                            }
                        } catch (e: kotlinx.coroutines.CancellationException) {
                            // Same reasoning as the classic path: a newer
                            // keystroke cancelling the fan-out is normal
                            // editing, never a server error.
                            throw e
                        } catch (_: Exception) {
                            anyFailure = true
                        }
                    }
                }
            }
            _searchHasError.value = anyFailure && verticals.none { verticalHasResults(it) }
            _isSearching.value = false
        }
    }

    private fun verticalHasResults(vertical: UnifiedSearchFilter): Boolean = when (vertical) {
        UnifiedSearchFilter.USERS -> _userSearchResults.value.isNotEmpty()
        UnifiedSearchFilter.MUSIC ->
            _songSearchResults.value.isNotEmpty() || _artistSearchResults.value.isNotEmpty()
        UnifiedSearchFilter.FILM -> _filmSearchResults.value.isNotEmpty()
        UnifiedSearchFilter.HASHTAGS -> _hashtagSearchResults.value.isNotEmpty()
        UnifiedSearchFilter.ALL ->
            _userSearchResults.value.isNotEmpty() || _songSearchResults.value.isNotEmpty() ||
                _artistSearchResults.value.isNotEmpty() || _filmSearchResults.value.isNotEmpty() ||
                _hashtagSearchResults.value.isNotEmpty()
    }

    /**
     * Adaptive debounce matching iOS SearchView: shorter delays as the query
     * grows, since longer queries are more specific and the user has already
     * committed to them. Short queries get a longer wait so we don't fire on
     * every keystroke as the user types out the first few letters.
     */
    private fun debounceMs(query: String): Long = when (query.length) {
        1 -> 400L
        2 -> 300L
        in 3..5 -> 200L
        else -> 150L
    }

    // ── Per-vertical fetches, shared by the classic per-tab path and the
    // unified fan-out. Each commits results AND its lastFetchedQuery entry
    // only on success, so a cancelled/failed fetch never marks the vertical
    // as served. ──

    private suspend fun searchUsersVertical(query: String) {
        val key = query.lowercase().trim()
        val cached = userSearchCache[key]
        val results = cached ?: userRepository.searchUsers(key, includeFollowed = true)
        userSearchCache[key] = results
        _userSearchResults.value = results
        markVerticalFetched(UnifiedSearchFilter.USERS, query)
    }

    private suspend fun searchMusicVertical(query: String) {
        // Artist/album rows opt in ONLY while the flag is on —
        // with the params absent, the backend response (and
        // this tab) is byte-identical to today.
        val includeCatalogRows = remoteConfigService.artistPagesEnabled
        val page = musicSearchRepository.search(
            query,
            includeSoundCloud = remoteConfigService.soundcloudEnabled,
            includeArtists = includeCatalogRows,
            includeAlbums = includeCatalogRows,
        )
        _songSearchResults.value = page.tracks
        _artistSearchResults.value = page.artists
        _albumSearchResults.value = page.albums
        _songsFirst.value = page.songsFirst
        markVerticalFetched(UnifiedSearchFilter.MUSIC, query)
    }

    private suspend fun searchFilmsVertical(query: String) = coroutineScope {
        // Director row (flag on): fetched in parallel with the
        // film search and best-effort — a person-search failure
        // must never error the films tab, so it degrades to an
        // empty row. Fetched in the blended view too (where the rows don't
        // render) so narrowing to the Film chip shows them with no refetch.
        val directorsDeferred: kotlinx.coroutines.Deferred<List<fm.corus.android.data.model.ArtistSummary>>? =
            if (remoteConfigService.artistPagesEnabled) {
                async {
                    runCatching { tmdbRepository.searchDirectors(query) }
                        .getOrDefault(emptyList())
                }
            } else null
        val results = tmdbRepository.searchMovies(query)
        val withDirectors = try {
            tmdbRepository.prefetchDirectors(results)
        } catch (_: Exception) { results }
        _filmSearchResults.value = withDirectors
        _directorSearchResults.value = directorsDeferred?.await() ?: emptyList()
        markVerticalFetched(UnifiedSearchFilter.FILM, query)
    }

    private suspend fun searchHashtagsVertical(query: String) {
        val key = query.trim().removePrefix("#").lowercase()
        val cached = hashtagSearchCache[key]
        if (cached != null) {
            _hashtagSearchResults.value = cached
        } else {
            val results = firestoreDataSource.searchHashtagsByPrefix(query)
            hashtagSearchCache[key] = results
            _hashtagSearchResults.value = results
        }
        markVerticalFetched(UnifiedSearchFilter.HASHTAGS, query)
    }

    /** Manual retry from the offline empty state on SearchScreen. */
    fun retrySearch() {
        val query = _searchQuery.value
        if (query.isBlank()) return
        _searchHasError.value = false
        // Force every visible vertical to refetch — a retry must not be
        // swallowed by the served-query skip.
        _lastFetchedQuery.value = emptyMap()
        search(query, _activeTab.value)
    }

    fun clearSearch() {
        searchJob?.cancel()
        _userSearchResults.value = emptyList()
        _songSearchResults.value = emptyList()
        _filmSearchResults.value = emptyList()
        _hashtagSearchResults.value = emptyList()
        _artistSearchResults.value = emptyList()
        _albumSearchResults.value = emptyList()
        _songsFirst.value = false
        _directorSearchResults.value = emptyList()
        _isSearching.value = false
        _searchHasError.value = false
        // Each unified search starts fresh on the blended view.
        _lastFetchedQuery.value = emptyMap()
        _unifiedFilter.value = UnifiedSearchFilter.ALL
    }

    private fun loadTrendingHashtagsIfNeeded() {
        if (hasLoadedTrendingHashtags) return
        hasLoadedTrendingHashtags = true
        viewModelScope.launch {
            try {
                val window = trendingHashtagsWindow.value
                _trendingHashtags.value =
                    firestoreDataSource.fetchTrendingHashtagsWindowed(window, limit = 20)
            } catch (e: Exception) {
                Log.e("SearchVM", "Failed to load trending hashtags", e)
                hasLoadedTrendingHashtags = false
            }
            _isTrendingHashtagsLoading.value = false
        }
    }

    private fun loadTrendingArtistsIfNeeded() {
        if (hasLoadedTrendingArtists) return
        hasLoadedTrendingArtists = true
        viewModelScope.launch {
            try {
                val window = trendingArtistsWindow.value
                val loaded = exploreRepository.fetchTrendingArtists(window)
                _trendingArtists.value = loaded
                viewModelScope.launch {
                    cloudFunctions.prefetchArtistDestinations(loaded.map { it.artistName })
                }
            } catch (e: Exception) {
                Log.e("SearchVM", "Failed to load trending artists", e)
                hasLoadedTrendingArtists = false
            }
            _isTrendingArtistsLoading.value = false
        }
    }

    private fun loadTrendingAlbumsIfNeeded() {
        if (hasLoadedTrendingAlbums) return
        hasLoadedTrendingAlbums = true
        viewModelScope.launch {
            try {
                val loaded = exploreRepository.fetchTrendingAlbums(trendingAlbumsWindow.value)
                _trendingAlbums.value = loaded
                if (loaded.isNotEmpty()) preferencesDataStore.persistSearchTrendingAlbums(loaded)
            } catch (e: Exception) {
                Log.e("SearchVM", "Failed to load trending albums", e)
                hasLoadedTrendingAlbums = false
            }
            _isTrendingAlbumsLoading.value = false
        }
    }

    private fun loadNewReleaseAlbumsIfNeeded() {
        if (hasLoadedNewReleaseAlbums) return
        hasLoadedNewReleaseAlbums = true
        viewModelScope.launch {
            try {
                val loaded = exploreRepository.fetchNewReleaseAlbums()
                _newReleaseAlbums.value = loaded
                if (loaded.isNotEmpty()) preferencesDataStore.persistSearchNewReleaseAlbums(loaded)
            } catch (e: Exception) {
                Log.e("SearchVM", "Failed to load new-release albums", e)
                hasLoadedNewReleaseAlbums = false
            }
            _isNewReleaseAlbumsLoading.value = false
        }
    }

    private fun loadNewReleaseMoviesIfNeeded() {
        if (hasLoadedNewReleaseMovies) return
        hasLoadedNewReleaseMovies = true
        viewModelScope.launch {
            try {
                val loaded = exploreRepository.fetchNewReleaseMovies()
                _newReleaseMovies.value = loaded
                if (loaded.isNotEmpty()) preferencesDataStore.persistSearchNewReleaseMovies(loaded)
            } catch (e: Exception) {
                Log.e("SearchVM", "Failed to load new-release movies", e)
                hasLoadedNewReleaseMovies = false
            }
            _isNewReleaseMoviesLoading.value = false
        }
    }

    private fun loadTrendingDirectorsIfNeeded() {
        if (hasLoadedTrendingDirectors) return
        hasLoadedTrendingDirectors = true
        viewModelScope.launch {
            try {
                val loaded = exploreRepository.fetchTrendingDirectors(TrendingWindow.WEEK)
                _trendingDirectors.value = loaded
                if (loaded.isNotEmpty()) preferencesDataStore.persistSearchTrendingDirectors(loaded)
            } catch (e: Exception) {
                Log.e("SearchVM", "Failed to load trending directors", e)
                hasLoadedTrendingDirectors = false
            }
            _isTrendingDirectorsLoading.value = false
        }
    }

    fun refreshFollowedHashtags() {
        val uid = authRepository.currentUserId ?: return
        viewModelScope.launch {
            try {
                _followedHashtagNames.value = firestoreDataSource.fetchFollowedHashtagNames(uid)
            } catch (e: Exception) {
                Log.e("SearchVM", "Failed to load followed hashtags", e)
            }
        }
    }

    fun toggleHashtagFollow(tag: CymbalHashtag) = toggleHashtagFollowByName(tag.name)

    /** Name-only variant so trending rows (which carry `TrendingHashtag`, not
     *  `CymbalHashtag`) can share the same optimistic-toggle plumbing. */
    fun toggleHashtagFollowByName(name: String) {
        val uid = authRepository.currentUserId ?: return
        val key = name.lowercase()
        val current = _followedHashtagNames.value
        val wasFollowing = current.contains(key)
        // Optimistic update.
        _followedHashtagNames.value = if (wasFollowing) current - key else current + key
        viewModelScope.launch {
            try {
                if (wasFollowing) {
                    firestoreDataSource.unfollowHashtag(uid, name)
                } else {
                    firestoreDataSource.followHashtag(uid, name)
                }
            } catch (e: Exception) {
                Log.e("SearchVM", "toggleHashtagFollow failed for $key", e)
                // Revert.
                val rolled = _followedHashtagNames.value
                _followedHashtagNames.value =
                    if (wasFollowing) rolled + key else rolled - key
            }
        }
    }

    fun onUserSelected(user: CymbalUser) {
        recordRecent(RecentSearchItem.fromUser(user))
    }

    /** Persist any tapped search result into Recent (artist/album/song/film/
     *  director/hashtag/user). Re-tapping an entry bumps it to the front. */
    fun recordRecent(item: RecentSearchItem) {
        viewModelScope.launch {
            preferencesDataStore.addRecentSearch(item)
        }
    }

    fun removeRecentSearch(dedupeKey: String) {
        viewModelScope.launch {
            preferencesDataStore.removeRecentSearch(dedupeKey)
        }
    }

    fun clearRecentSearches() {
        viewModelScope.launch {
            preferencesDataStore.clearRecentSearches()
        }
    }

    fun populateSearchFromRecent(query: String) {
        onSearchQueryChange(query)
    }

    // ── Contact Sync ──

    fun dismissContactsSync() {
        viewModelScope.launch {
            preferencesDataStore.setContactsSyncStatus("skipped")
        }
    }

    fun syncContacts(contentResolver: ContentResolver) {
        val userId = authRepository.currentUserId ?: return
        viewModelScope.launch {
            _isSyncingContacts.value = true
            _showNoContactMatches.value = false
            preferencesDataStore.setContactsSyncStatus("synced")
            try {
                val phoneNumbers = readContactPhoneNumbers(contentResolver)
                if (phoneNumbers.isNotEmpty()) {
                    // Fire-and-forget: store contacts and notify (non-fatal if they fail)
                    launch {
                        try { firestoreDataSource.storeSyncedContacts(userId, phoneNumbers) }
                        catch (e: Exception) { Log.w("SearchVM", "storeSyncedContacts failed", e) }
                    }
                    launch {
                        try { cloudFunctions.notifyContactsOnSync() }
                        catch (e: Exception) { Log.w("SearchVM", "notifyContactsOnSync failed", e) }
                    }
                    // Only the match lookup is essential for the UI
                    try {
                        val ids = cloudFunctions.findContactMatches(phoneNumbers).filter { it != userId }
                        _contactMatches.value = userRepository.fetchUsersByIdsBatched(ids)
                    } catch (e: Exception) {
                        Log.e("SearchVM", "findContactMatches failed", e)
                    }
                }
            } catch (_: Exception) { }
            _isSyncingContacts.value = false
            if (_contactMatches.value.isEmpty()) {
                _showNoContactMatches.value = true
            }
        }
    }

    // ── Follow ──

    fun toggleFollow(user: CymbalUser, section: SearchSection? = null) {
        val uid = authRepository.currentUserId ?: return
        val isFollowed = _localFollowedIds.value.contains(user.id) || _followingIds.value.contains(user.id)
        // Fire analytics from the optimistic-flip side so the event matches what
        // the user just *did*, not what eventually persists if the network call
        // fails. The catch blocks below revert in-memory state, but we don't
        // double-fire an "undo" event — the original tap is what matters.
        if (section != null) {
            if (isFollowed) {
                analyticsService.logSearchSectionUserUnfollowed(section, user.id)
            } else {
                analyticsService.logSearchSectionUserFollowed(section, user.id)
            }
        }
        viewModelScope.launch {
            if (isFollowed) {
                _localUnfollowedIds.value = _localUnfollowedIds.value + user.id
                _localFollowedIds.value = _localFollowedIds.value - user.id
                _followingIds.value = _followingIds.value - user.id
                try { userRepository.unfollowUser(uid, user.id) } catch (_: Exception) {
                    _localUnfollowedIds.value = _localUnfollowedIds.value - user.id
                    _followingIds.value = _followingIds.value + user.id
                }
            } else {
                _localUnfollowedIds.value = _localUnfollowedIds.value - user.id
                _localFollowedIds.value = _localFollowedIds.value + user.id
                try { userRepository.followUser(uid, user.id) } catch (_: Exception) {
                    _localFollowedIds.value = _localFollowedIds.value - user.id
                }
            }
        }
    }

    fun isFollowed(userId: String): Boolean {
        if (_localUnfollowedIds.value.contains(userId)) return false
        return _localFollowedIds.value.contains(userId) || _followingIds.value.contains(userId)
    }

    // ── Search-page section analytics ──
    // Composable callers don't have direct access to AnalyticsService, so route
    // through the ViewModel. Keeps `SearchScreen.kt` free of service references.

    fun logSearchSectionUserTapped(section: SearchSection, userId: String) {
        analyticsService.logSearchSectionUserTapped(section, userId)
    }

    fun logSearchSectionSeeAllTapped(section: SearchSection) {
        analyticsService.logSearchSectionSeeAllTapped(section)
    }

    fun logSearchSectionItemTapped(section: SearchSection, itemId: String) {
        analyticsService.logSearchSectionItemTapped(section, itemId)
    }

    fun logMusicMatchTapped(userId: String, similarityScore: Double) {
        analyticsService.logMusicMatchTapped(userId, similarityScore)
    }
}
