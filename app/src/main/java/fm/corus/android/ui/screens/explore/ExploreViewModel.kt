package fm.corus.android.ui.screens.explore

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import dagger.hilt.android.lifecycle.HiltViewModel
import fm.corus.android.data.model.CymbalHashtag
import fm.corus.android.data.model.CymbalMovie
import fm.corus.android.data.model.CymbalTrack
import fm.corus.android.data.model.CymbalUser
import fm.corus.android.data.model.SuggestedUserMatch
import fm.corus.android.data.model.TrendingMovie
import fm.corus.android.data.model.TrendingSong
import fm.corus.android.data.repository.ExploreRepository
import fm.corus.android.data.repository.SpotifyRepository
import fm.corus.android.data.repository.TMDBRepository
import fm.corus.android.data.repository.UserRepository
import fm.corus.android.service.RemoteConfigService
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ExploreViewModel @Inject constructor(
    private val exploreRepository: ExploreRepository,
    private val userRepository: UserRepository,
    private val spotifyRepository: SpotifyRepository,
    private val tmdbRepository: TMDBRepository,
    private val remoteConfigService: RemoteConfigService,
    private val firebaseAuth: FirebaseAuth,
) : ViewModel() {

    private val _trendingHashtags = MutableStateFlow<List<CymbalHashtag>>(emptyList())
    val trendingHashtags: StateFlow<List<CymbalHashtag>> = _trendingHashtags.asStateFlow()

    private val _trendingSongs = MutableStateFlow<List<TrendingSong>>(emptyList())
    val trendingSongs: StateFlow<List<TrendingSong>> = _trendingSongs.asStateFlow()

    private val _trendingMovies = MutableStateFlow<List<TrendingMovie>>(emptyList())
    val trendingMovies: StateFlow<List<TrendingMovie>> = _trendingMovies.asStateFlow()

    private val _suggestedUsers = MutableStateFlow<List<SuggestedUserMatch>>(emptyList())
    val suggestedUsers: StateFlow<List<SuggestedUserMatch>> = _suggestedUsers.asStateFlow()

    private val _curatedBots = MutableStateFlow<List<SuggestedUserMatch>>(emptyList())
    val curatedBots: StateFlow<List<SuggestedUserMatch>> = _curatedBots.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    // ── Search state ──

    private val _isSearching = MutableStateFlow(false)
    val isSearching: StateFlow<Boolean> = _isSearching.asStateFlow()

    private val _userSearchResults = MutableStateFlow<List<CymbalUser>>(emptyList())
    val userSearchResults: StateFlow<List<CymbalUser>> = _userSearchResults.asStateFlow()

    private val _songSearchResults = MutableStateFlow<List<CymbalTrack>>(emptyList())
    val songSearchResults: StateFlow<List<CymbalTrack>> = _songSearchResults.asStateFlow()

    private val _filmSearchResults = MutableStateFlow<List<CymbalMovie>>(emptyList())
    val filmSearchResults: StateFlow<List<CymbalMovie>> = _filmSearchResults.asStateFlow()

    // ── Derived discovery sections ──

    private val _musicMatches = MutableStateFlow<List<SuggestedUserMatch>>(emptyList())
    val musicMatches: StateFlow<List<SuggestedUserMatch>> = _musicMatches.asStateFlow()

    private val _mutualConnections = MutableStateFlow<List<SuggestedUserMatch>>(emptyList())
    val mutualConnections: StateFlow<List<SuggestedUserMatch>> = _mutualConnections.asStateFlow()

    private val _popularUsers = MutableStateFlow<List<SuggestedUserMatch>>(emptyList())
    val popularUsers: StateFlow<List<SuggestedUserMatch>> = _popularUsers.asStateFlow()

    // ── Follow state for suggested user cards ──

    private val _followedIds = MutableStateFlow<Set<String>>(emptySet())
    val followedIds: StateFlow<Set<String>> = _followedIds.asStateFlow()

    private var searchJob: Job? = null

    fun loadTrending() {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val hashtags = exploreRepository.fetchTrendingHashtags()
                val songs = exploreRepository.fetchTrendingSongs()
                _trendingHashtags.value = hashtags
                _trendingSongs.value = songs

                if (remoteConfigService.movieModeEnabled) {
                    try {
                        val movies = exploreRepository.fetchTrendingMovies()
                        _trendingMovies.value = movies
                    } catch (_: Exception) { }
                }

                // Load suggested users and bots
                val uid = firebaseAuth.currentUser?.uid
                if (uid != null) {
                    try {
                        val suggestions = userRepository.getSuggestedUsers(uid)
                        val nonBots = suggestions.filter { !it.user.isBot }
                        _suggestedUsers.value = nonBots
                        _curatedBots.value = suggestions.filter { it.user.isBot }

                        // Split into discovery categories
                        splitSuggestions(nonBots)
                    } catch (_: Exception) { }

                    // Pre-load following set for follow button state
                    _followedIds.value = userRepository.followingIds.value
                }
            } catch (_: Exception) { }
            _isLoading.value = false
        }
    }

    fun refreshTrending() {
        viewModelScope.launch {
            _isRefreshing.value = true
            try {
                _trendingSongs.value = exploreRepository.fetchTrendingSongs()
                if (remoteConfigService.movieModeEnabled) {
                    _trendingMovies.value = try { exploreRepository.fetchTrendingMovies() } catch (_: Exception) { _trendingMovies.value }
                }
            } catch (_: Exception) { }
            _isRefreshing.value = false
        }
    }

    fun search(query: String, tab: Int) {
        searchJob?.cancel()
        if (query.isBlank()) {
            clearSearch()
            return
        }
        searchJob = viewModelScope.launch {
            delay(300) // debounce
            _isSearching.value = true
            try {
                when (tab) {
                    0 -> { // Users
                        val results = userRepository.searchUsers(query)
                        _userSearchResults.value = results
                    }
                    1 -> { // Songs
                        val results = spotifyRepository.search(query)
                        _songSearchResults.value = results
                    }
                    2 -> { // Films
                        val results = tmdbRepository.searchMovies(query)
                        _filmSearchResults.value = results
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
        _isSearching.value = false
    }

    fun followUser(targetUserId: String) {
        val uid = firebaseAuth.currentUser?.uid ?: return
        viewModelScope.launch {
            try {
                userRepository.followUser(uid, targetUserId)
                _followedIds.value = _followedIds.value + targetUserId
            } catch (_: Exception) { }
        }
    }

    private fun splitSuggestions(suggestions: List<SuggestedUserMatch>) {
        val followingIds = _followedIds.value

        // Music matches: users with taste similarity data, not already followed
        val music = suggestions
            .filter { !followingIds.contains(it.user.id) }
            .filter { it.matchData?.hasSimilarityData == true }
            .sortedByDescending { it.matchData?.similarityScore ?: 0.0 }
        _musicMatches.value = music

        // Social suggestions: no taste overlap, must have posted
        val social = suggestions
            .filter { !followingIds.contains(it.user.id) }
            .filter { it.matchData?.hasSimilarityData != true }
            .filter { it.user.cymbalCount > 0 }

        // Mutual connections: have mutual follower names
        val mutuals = social.filter { !it.suggestionReason?.mutualNames.isNullOrEmpty() }
        _mutualConnections.value = mutuals

        // Popular: remainder (not in mutuals)
        val mutualIds = mutuals.map { it.user.id }.toSet()
        _popularUsers.value = social.filter { it.user.id !in mutualIds }
    }

    fun unfollowUser(targetUserId: String) {
        val uid = firebaseAuth.currentUser?.uid ?: return
        viewModelScope.launch {
            try {
                userRepository.unfollowUser(uid, targetUserId)
                _followedIds.value = _followedIds.value - targetUserId
            } catch (_: Exception) { }
        }
    }
}
