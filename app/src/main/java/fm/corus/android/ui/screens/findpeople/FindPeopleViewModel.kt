package fm.corus.android.ui.screens.findpeople

import android.content.ContentResolver
import android.provider.ContactsContract
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import fm.corus.android.data.local.PreferencesDataStore
import fm.corus.android.data.model.CymbalMovie
import fm.corus.android.data.model.CymbalTrack
import fm.corus.android.data.model.CymbalUser
import fm.corus.android.data.model.SuggestedUserMatch
import fm.corus.android.data.model.SuggestionReason
import fm.corus.android.data.model.TrendingMovie
import fm.corus.android.data.model.TrendingSong
import fm.corus.android.data.remote.CloudFunctionsDataSource
import fm.corus.android.data.remote.FirestoreDataSource
import fm.corus.android.data.repository.AuthRepository
import fm.corus.android.data.repository.ExploreRepository
import fm.corus.android.data.repository.SpotifyRepository
import fm.corus.android.data.repository.TMDBRepository
import fm.corus.android.data.repository.UserRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class FindPeopleViewModel @Inject constructor(
    private val userRepository: UserRepository,
    private val authRepository: AuthRepository,
    private val exploreRepository: ExploreRepository,
    private val cloudFunctions: CloudFunctionsDataSource,
    private val spotifyRepository: SpotifyRepository,
    private val tmdbRepository: TMDBRepository,
    private val preferencesDataStore: PreferencesDataStore,
    private val firestoreDataSource: FirestoreDataSource,
) : ViewModel() {

    // Search
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _userSearchResults = MutableStateFlow<List<CymbalUser>>(emptyList())
    val userSearchResults: StateFlow<List<CymbalUser>> = _userSearchResults.asStateFlow()

    private val _songSearchResults = MutableStateFlow<List<CymbalTrack>>(emptyList())
    val songSearchResults: StateFlow<List<CymbalTrack>> = _songSearchResults.asStateFlow()

    private val _filmSearchResults = MutableStateFlow<List<CymbalMovie>>(emptyList())
    val filmSearchResults: StateFlow<List<CymbalMovie>> = _filmSearchResults.asStateFlow()

    private val _isSearching = MutableStateFlow(false)
    val isSearching: StateFlow<Boolean> = _isSearching.asStateFlow()

    // Trending
    private val _trendingSongs = MutableStateFlow<List<TrendingSong>>(emptyList())
    val trendingSongs: StateFlow<List<TrendingSong>> = _trendingSongs.asStateFlow()

    private val _trendingMovies = MutableStateFlow<List<TrendingMovie>>(emptyList())
    val trendingMovies: StateFlow<List<TrendingMovie>> = _trendingMovies.asStateFlow()

    private val _isTrendingLoading = MutableStateFlow(true)
    val isTrendingLoading: StateFlow<Boolean> = _isTrendingLoading.asStateFlow()

    private val _isTrendingMoviesLoading = MutableStateFlow(true)
    val isTrendingMoviesLoading: StateFlow<Boolean> = _isTrendingMoviesLoading.asStateFlow()

    // Suggestions
    private val _suggestedMatches = MutableStateFlow<List<SuggestedUserMatch>>(emptyList())
    val suggestedMatches: StateFlow<List<SuggestedUserMatch>> = _suggestedMatches.asStateFlow()

    private val _isSuggestedLoading = MutableStateFlow(true)
    val isSuggestedLoading: StateFlow<Boolean> = _isSuggestedLoading.asStateFlow()

    // Bots
    private val _curatedMusicBots = MutableStateFlow<List<SuggestedUserMatch>>(emptyList())
    val curatedMusicBots: StateFlow<List<SuggestedUserMatch>> = _curatedMusicBots.asStateFlow()

    private val _curatedFilmBots = MutableStateFlow<List<SuggestedUserMatch>>(emptyList())
    val curatedFilmBots: StateFlow<List<SuggestedUserMatch>> = _curatedFilmBots.asStateFlow()

    private val _isBotsLoading = MutableStateFlow(true)
    val isBotsLoading: StateFlow<Boolean> = _isBotsLoading.asStateFlow()

    // Follow state
    private val _followingIds = MutableStateFlow<Set<String>>(emptySet())
    val followingIds: StateFlow<Set<String>> = _followingIds.asStateFlow()

    private val _localFollowedIds = MutableStateFlow<Set<String>>(emptySet())
    val localFollowedIds: StateFlow<Set<String>> = _localFollowedIds.asStateFlow()

    // Recent searches (persisted in DataStore)
    val recentSearches: StateFlow<List<String>> = preferencesDataStore.recentSearches
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Contact sync
    private val _contactMatches = MutableStateFlow<List<CymbalUser>>(emptyList())
    val contactMatches: StateFlow<List<CymbalUser>> = _contactMatches.asStateFlow()

    private val _isSyncingContacts = MutableStateFlow(false)
    val isSyncingContacts: StateFlow<Boolean> = _isSyncingContacts.asStateFlow()

    val contactsSyncStatus: StateFlow<String> = preferencesDataStore.contactsSyncStatus
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "notAsked")

    // Popular users
    private val _popularUsers = MutableStateFlow<List<CymbalUser>>(emptyList())
    val popularUsers: StateFlow<List<CymbalUser>> = _popularUsers.asStateFlow()

    private val _isPopularLoading = MutableStateFlow(true)
    val isPopularLoading: StateFlow<Boolean> = _isPopularLoading.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    val currentUserId: String? get() = authRepository.currentUserId

    private var searchJob: Job? = null

    fun refresh() {
        viewModelScope.launch {
            _isRefreshing.value = true
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
                    Log.e("FindPeopleVM", "Failed to load suggested users", e)
                    emptyList()
                }
            }
            val mutualConnectionsDeferred = async {
                try {
                    val mutuals = firestoreDataSource.fetchPrecomputedMutualConnections(uid, limit = 20)
                    mutuals.map { (user, names) ->
                        SuggestedUserMatch(
                            user = user,
                            matchData = null,
                            suggestionReason = SuggestionReason(mutualNames = names),
                        )
                    }
                } catch (e: Exception) {
                    Log.e("FindPeopleVM", "Failed to load mutual connections", e)
                    emptyList()
                }
            }

            val musicMatches = musicMatchesDeferred.await()
            val socialMatches = mutualConnectionsDeferred.await()
            Log.d("FindPeopleVM", "Music matches: ${musicMatches.size}, Social matches: ${socialMatches.size}")
            for (m in musicMatches) {
                Log.d("FindPeopleVM", "  CF user: ${m.user.username} cymbal=${m.user.cymbalCount} hasSim=${m.matchData?.hasSimilarityData} mutualNames=${m.suggestionReason?.mutualNames} artistsInCommon=${m.user.artistsInCommonCount}")
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
                _trendingSongs.value = exploreRepository.fetchTrendingSongs()
            } catch (e: Exception) {
                Log.e("FindPeopleVM", "Failed to load trending songs", e)
            }
            _isTrendingLoading.value = false
        }
        viewModelScope.launch {
            try {
                _trendingMovies.value = exploreRepository.fetchTrendingMovies()
            } catch (e: Exception) {
                Log.e("FindPeopleVM", "Failed to load trending movies", e)
            }
            _isTrendingMoviesLoading.value = false
        }
        viewModelScope.launch {
            try {
                val musicBots = cloudFunctions.getBotSuggestions(uid, botType = "music")
                Log.d("FindPeopleVM", "Music bots loaded: ${musicBots.size}")
                _curatedMusicBots.value = musicBots
            } catch (e: Exception) {
                Log.e("FindPeopleVM", "Failed to load music bots", e)
            }
            try {
                val filmBots = cloudFunctions.getBotSuggestions(uid, botType = "film")
                Log.d("FindPeopleVM", "Film bots loaded: ${filmBots.size}")
                _curatedFilmBots.value = filmBots
            } catch (e: Exception) {
                Log.e("FindPeopleVM", "Failed to load film bots", e)
            }
            _isBotsLoading.value = false
        }
        viewModelScope.launch {
            try {
                val popular = userRepository.fetchPopularUsers(
                    limit = 10,
                    excludeIds = setOf(uid),
                )
                Log.d("FindPeopleVM", "Popular users loaded: ${popular.size}")
                _popularUsers.value = popular
            } catch (e: Exception) {
                Log.e("FindPeopleVM", "Failed to load popular users", e)
            }
            _isPopularLoading.value = false
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
        searchJob = viewModelScope.launch {
            delay(if (query.length <= 2) 500L else 300L)
            _isSearching.value = true
            try {
                when (tab) {
                    0 -> _userSearchResults.value = userRepository.searchUsers(query.lowercase().trim())
                    1 -> _songSearchResults.value = spotifyRepository.search(query)
                    2 -> _filmSearchResults.value = tmdbRepository.searchMovies(query)
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
        _isSearching.value = false
    }

    fun onUserSelected(user: CymbalUser) {
        viewModelScope.launch {
            preferencesDataStore.addRecentSearch(user.username)
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

    fun syncContacts(contentResolver: ContentResolver) {
        val userId = authRepository.currentUserId ?: return
        viewModelScope.launch {
            _isSyncingContacts.value = true
            try {
                val phoneNumbers = readContactPhoneNumbers(contentResolver)
                if (phoneNumbers.isNotEmpty()) {
                    // Fire-and-forget: store contacts and notify (non-fatal if they fail)
                    launch {
                        try { firestoreDataSource.storeSyncedContacts(userId, phoneNumbers) }
                        catch (e: Exception) { Log.w("FindPeopleVM", "storeSyncedContacts failed", e) }
                    }
                    launch {
                        try { cloudFunctions.notifyContactsOnSync() }
                        catch (e: Exception) { Log.w("FindPeopleVM", "notifyContactsOnSync failed", e) }
                    }
                    // Only the match lookup is essential for the UI
                    try {
                        _contactMatches.value =
                            firestoreDataSource.fetchUsersByPhoneNumbers(phoneNumbers, setOf(userId))
                    } catch (e: Exception) {
                        Log.e("FindPeopleVM", "fetchUsersByPhoneNumbers failed", e)
                    }
                }
                preferencesDataStore.setContactsSyncStatus("synced")
            } catch (_: Exception) { }
            _isSyncingContacts.value = false
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
