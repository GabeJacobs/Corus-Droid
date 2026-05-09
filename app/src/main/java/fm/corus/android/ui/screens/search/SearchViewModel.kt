package fm.corus.android.ui.screens.search

import android.content.ContentResolver
import android.provider.ContactsContract
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import fm.corus.android.data.local.PreferencesDataStore
import fm.corus.android.data.model.CymbalHashtag
import fm.corus.android.data.model.CymbalMovie
import fm.corus.android.data.model.CymbalTrack
import fm.corus.android.data.model.CymbalUser
import fm.corus.android.data.model.SuggestedUserMatch
import fm.corus.android.data.model.SuggestionReason
import fm.corus.android.data.model.TrendingMovie
import fm.corus.android.data.model.TrendingSong
import fm.corus.android.data.model.TrendingWindow
import fm.corus.android.data.remote.CloudFunctionsDataSource
import fm.corus.android.data.remote.FirestoreDataSource
import fm.corus.android.data.repository.AuthRepository
import fm.corus.android.data.repository.ExploreRepository
import fm.corus.android.data.repository.MusicSearchRepository
import fm.corus.android.data.repository.TMDBRepository
import fm.corus.android.data.repository.UserRepository
import fm.corus.android.domain.NowPlayingManager
import fm.corus.android.service.RemoteConfigService
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

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
    val nowPlayingManager: NowPlayingManager,
) : ViewModel() {

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
                1 -> _songSearchResults.value = emptyList()
                2 -> _filmSearchResults.value = emptyList()
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

    private val _hashtagSearchResults = MutableStateFlow<List<CymbalHashtag>>(emptyList())
    val hashtagSearchResults: StateFlow<List<CymbalHashtag>> = _hashtagSearchResults.asStateFlow()

    private val _isSearching = MutableStateFlow(false)
    val isSearching: StateFlow<Boolean> = _isSearching.asStateFlow()

    // Trending hashtags + cache for prefix search
    private val _trendingHashtags = MutableStateFlow<List<CymbalHashtag>>(emptyList())
    val trendingHashtags: StateFlow<List<CymbalHashtag>> = _trendingHashtags.asStateFlow()

    private val _isTrendingHashtagsLoading = MutableStateFlow(true)
    val isTrendingHashtagsLoading: StateFlow<Boolean> = _isTrendingHashtagsLoading.asStateFlow()

    private var hasLoadedTrendingHashtags = false
    private val hashtagSearchCache = mutableMapOf<String, List<CymbalHashtag>>()
    private val hashtagPreviewCache = mutableMapOf<String, FirestoreDataSource.HashtagPreview>()

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

    companion object {
        private const val TRENDING_WINDOW_MIN_DISPLAY_MS = 280L
    }

    // Suggestions
    private val _suggestedMatches = MutableStateFlow<List<SuggestedUserMatch>>(emptyList())
    val suggestedMatches: StateFlow<List<SuggestedUserMatch>> = _suggestedMatches.asStateFlow()

    private val _isSuggestedLoading = MutableStateFlow(true)
    val isSuggestedLoading: StateFlow<Boolean> = _isSuggestedLoading.asStateFlow()

    // Follow state
    private val _followingIds = MutableStateFlow<Set<String>>(emptySet())
    val followingIds: StateFlow<Set<String>> = _followingIds.asStateFlow()

    private val _localFollowedIds = MutableStateFlow<Set<String>>(emptySet())
    val localFollowedIds: StateFlow<Set<String>> = _localFollowedIds.asStateFlow()

    // Recent searches (persisted as full user objects in DataStore)
    val recentSearchUsers: StateFlow<List<CymbalUser>> = preferencesDataStore.recentSearchUsers
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

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    val currentUserId: String? get() = authRepository.currentUserId

    private var searchJob: Job? = null

    fun refresh() {
        viewModelScope.launch {
            _isRefreshing.value = true
            // Explicit user refresh should bypass the in-memory TTL on trending.
            exploreRepository.clearCaches()
            loadInitialData()
            // Give data a moment to load before hiding the indicator
            delay(500)
            _isRefreshing.value = false
        }
    }

    fun loadInitialData() {
        val uid = authRepository.currentUserId ?: return
        viewModelScope.launch {
            userRepository.followingIds.collect { ids ->
                _followingIds.value = ids
            }
        }
        // Fetch taste matches (cloud function) and mutual connections (Firestore) in parallel,
        // then merge them — matching how iOS loads suggestions.
        viewModelScope.launch {
            val musicMatchesDeferred = async {
                try {
                    cloudFunctions.getSuggestedUsers(uid)
                } catch (e: Exception) {
                    Log.e("SearchVM", "Failed to load suggested users", e)
                    emptyList()
                }
            }
            val mutualConnectionsDeferred = async {
                try {
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
                } catch (e: Exception) {
                    Log.e("SearchVM", "Failed to load mutual connections", e)
                    emptyList()
                }
            }

            val musicMatches = musicMatchesDeferred.await()
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
            _isSuggestedLoading.value = false
        }
        viewModelScope.launch {
            try {
                _trendingSongs.value = exploreRepository.fetchTrendingSongs(trendingSongsWindow.value)
            } catch (e: Exception) {
                Log.e("SearchVM", "Failed to load trending songs", e)
            }
            _isTrendingLoading.value = false
        }
        viewModelScope.launch {
            try {
                _trendingMovies.value = exploreRepository.fetchTrendingMovies(trendingFilmsWindow.value)
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
        }
        refreshFollowedHashtags()
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
            } catch (e: Exception) {
                Log.e("SearchVM", "Failed to load club members", e)
            }
            _isClubMembersLoading.value = false
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

        searchJob = viewModelScope.launch {
            delay(if (query.length <= 2) 500L else 300L)
            _isSearching.value = true
            try {
                when (tab) {
                    0 -> _userSearchResults.value = userRepository.searchUsers(query.lowercase().trim())
                    1 -> _songSearchResults.value = musicSearchRepository.search(
                        query,
                        includeSoundCloud = remoteConfigService.soundcloudEnabled,
                    ).tracks
                    2 -> {
                        val results = tmdbRepository.searchMovies(query)
                        val withDirectors = try {
                            tmdbRepository.prefetchDirectors(results)
                        } catch (_: Exception) { results }
                        _filmSearchResults.value = withDirectors
                    }
                    3 -> {
                        val key = query.trim().removePrefix("#").lowercase()
                        val cached = hashtagSearchCache[key]
                        if (cached != null) {
                            _hashtagSearchResults.value = cached
                        } else {
                            val results = firestoreDataSource.searchHashtagsByPrefix(query)
                            hashtagSearchCache[key] = results
                            _hashtagSearchResults.value = results
                        }
                    }
                }
            } catch (_: Exception) { }
            _isSearching.value = false
        }
    }

    fun clearSearch() {
        searchJob?.cancel()
        _userSearchResults.value = emptyList()
        _songSearchResults.value = emptyList()
        _filmSearchResults.value = emptyList()
        _hashtagSearchResults.value = emptyList()
        _isSearching.value = false
    }

    private fun loadTrendingHashtagsIfNeeded() {
        if (hasLoadedTrendingHashtags) return
        hasLoadedTrendingHashtags = true
        viewModelScope.launch {
            try {
                _trendingHashtags.value = firestoreDataSource.fetchTrendingHashtags(limit = 20)
            } catch (e: Exception) {
                Log.e("SearchVM", "Failed to load trending hashtags", e)
                hasLoadedTrendingHashtags = false
            }
            _isTrendingHashtagsLoading.value = false
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

    fun toggleHashtagFollow(tag: CymbalHashtag) {
        val uid = authRepository.currentUserId ?: return
        val key = tag.name.lowercase()
        val current = _followedHashtagNames.value
        val wasFollowing = current.contains(key)
        // Optimistic update.
        _followedHashtagNames.value = if (wasFollowing) current - key else current + key
        viewModelScope.launch {
            try {
                if (wasFollowing) {
                    firestoreDataSource.unfollowHashtag(uid, tag.name)
                } else {
                    firestoreDataSource.followHashtag(uid, tag.name)
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
        viewModelScope.launch {
            preferencesDataStore.addRecentSearchUser(user)
        }
    }

    fun removeRecentSearch(userId: String) {
        viewModelScope.launch {
            preferencesDataStore.removeRecentSearchUser(userId)
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
                        _contactMatches.value =
                            firestoreDataSource.fetchUsersByPhoneNumbers(phoneNumbers, setOf(userId))
                    } catch (e: Exception) {
                        Log.e("SearchVM", "fetchUsersByPhoneNumbers failed", e)
                    }
                }
            } catch (_: Exception) { }
            _isSyncingContacts.value = false
            if (_contactMatches.value.isEmpty()) {
                _showNoContactMatches.value = true
            }
        }
    }

    private fun readContactPhoneNumbers(contentResolver: ContentResolver): List<String> {
        val numbers = mutableSetOf<String>()
        try {
            val cursor = contentResolver.query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                arrayOf(ContactsContract.CommonDataKinds.Phone.NUMBER),
                null, null, null,
            )
            cursor?.use {
                val numberIndex = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                while (it.moveToNext()) {
                    val number = it.getString(numberIndex)?.replace(Regex("[^+\\d]"), "")
                    if (!number.isNullOrBlank()) numbers.add(number)
                }
            }
        } catch (_: Exception) { }
        return numbers.toList()
    }

    // ── Follow ──

    fun toggleFollow(user: CymbalUser) {
        val uid = authRepository.currentUserId ?: return
        val isFollowed = _localFollowedIds.value.contains(user.id) || _followingIds.value.contains(user.id)
        viewModelScope.launch {
            if (isFollowed) {
                _localFollowedIds.value = _localFollowedIds.value - user.id
                _followingIds.value = _followingIds.value - user.id
                try { userRepository.unfollowUser(uid, user.id) } catch (_: Exception) {
                    _followingIds.value = _followingIds.value + user.id
                }
            } else {
                _localFollowedIds.value = _localFollowedIds.value + user.id
                try { userRepository.followUser(uid, user.id) } catch (_: Exception) {
                    _localFollowedIds.value = _localFollowedIds.value - user.id
                }
            }
        }
    }

    fun isFollowed(userId: String): Boolean {
        return _localFollowedIds.value.contains(userId) || _followingIds.value.contains(userId)
    }
}
