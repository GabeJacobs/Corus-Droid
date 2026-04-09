package fm.corus.android.ui.screens.compose

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import fm.corus.android.data.model.CymbalMovie
import fm.corus.android.data.model.CymbalPost
import fm.corus.android.data.model.CymbalTrack
import fm.corus.android.data.model.CymbalUser
import fm.corus.android.data.model.MediaType
import fm.corus.android.data.repository.AuthRepository
import fm.corus.android.data.repository.PostRepository
import fm.corus.android.data.repository.SpotifyRepository
import fm.corus.android.data.repository.SubscriptionRepository
import fm.corus.android.data.repository.TMDBRepository
import fm.corus.android.data.repository.UserRepository
import fm.corus.android.service.AnalyticsService
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ComposeViewModel @Inject constructor(
    private val postRepository: PostRepository,
    private val spotifyRepository: SpotifyRepository,
    private val tmdbRepository: TMDBRepository,
    private val authRepository: AuthRepository,
    private val analyticsService: AnalyticsService,
    private val userRepository: UserRepository,
    private val subscriptionRepository: SubscriptionRepository,
) : ViewModel() {

    // Post limit / Cymbal Club
    private val _showPostLimitPaywall = MutableStateFlow(false)
    val showPostLimitPaywall: StateFlow<Boolean> = _showPostLimitPaywall.asStateFlow()

    fun dismissPostLimitPaywall() {
        _showPostLimitPaywall.value = false
    }

    init {
        val userId = authRepository.currentUserId
        if (userId != null) {
            viewModelScope.launch {
                // Refresh today's post count
                subscriptionRepository.refreshTodayPostCount(userId)

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
    }

    private val _selectedTrack = MutableStateFlow<CymbalTrack?>(null)
    val selectedTrack: StateFlow<CymbalTrack?> = _selectedTrack.asStateFlow()

    private val _selectedMovie = MutableStateFlow<CymbalMovie?>(null)
    val selectedMovie: StateFlow<CymbalMovie?> = _selectedMovie.asStateFlow()

    private val _isPosting = MutableStateFlow(false)
    val isPosting: StateFlow<Boolean> = _isPosting.asStateFlow()

    private val _postSuccess = MutableStateFlow(false)
    val postSuccess: StateFlow<Boolean> = _postSuccess.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    // Search results as Triple(imageURL, title, subtitle)
    private val _searchResults = MutableStateFlow<List<Triple<String?, String, String>>>(emptyList())
    val searchResults: StateFlow<List<Triple<String?, String, String>>> = _searchResults.asStateFlow()

    private val _isSearching = MutableStateFlow(false)
    val isSearching: StateFlow<Boolean> = _isSearching.asStateFlow()

    private val _mentionSuggestions = MutableStateFlow<List<CymbalUser>>(emptyList())
    val mentionSuggestions: StateFlow<List<CymbalUser>> = _mentionSuggestions.asStateFlow()

    // Trophy celebration state
    private val _showTrophy = MutableStateFlow(false)
    val showTrophy: StateFlow<Boolean> = _showTrophy.asStateFlow()

    private val _trophyPost = MutableStateFlow<CymbalPost?>(null)
    val trophyPost: StateFlow<CymbalPost?> = _trophyPost.asStateFlow()

    private var searchJob: Job? = null
    private var mentionJob: Job? = null
    private var cachedTracks: List<CymbalTrack> = emptyList()
    private var cachedMovies: List<CymbalMovie> = emptyList()

    fun search(query: String, mediaType: MediaType) {
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            delay(300) // debounce
            _isSearching.value = true
            try {
                if (mediaType == MediaType.TRACK) {
                    cachedTracks = spotifyRepository.search(query)
                    _searchResults.value = cachedTracks.map { Triple(it.albumArtURL, it.name, it.artistName) }
                } else {
                    cachedMovies = tmdbRepository.searchMovies(query)
                    _searchResults.value = cachedMovies.map { Triple(it.posterURL, it.title, it.directorName) }
                }
            } catch (_: Exception) {
                _searchResults.value = emptyList()
            }
            _isSearching.value = false
        }
    }

    fun selectResult(result: Triple<String?, String, String>, mediaType: MediaType) {
        if (mediaType == MediaType.TRACK) {
            _selectedTrack.value = cachedTracks.firstOrNull { it.name == result.second && it.artistName == result.third }
        } else {
            _selectedMovie.value = cachedMovies.firstOrNull { it.title == result.second }
        }
        _searchResults.value = emptyList()
    }

    fun clearSelection() {
        _selectedTrack.value = null
        _selectedMovie.value = null
        _searchResults.value = emptyList()
    }

    fun loadAndSelectTrack(trackId: String) {
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
        }
    }

    fun loadAndSelectMovie(movieId: String) {
        viewModelScope.launch {
            try {
                val movie = tmdbRepository.getMovieDetails(movieId.toInt())
                _selectedMovie.value = movie
                _searchResults.value = emptyList()
            } catch (_: Exception) {
                _error.value = "Could not load movie."
            }
        }
    }

    fun createPost(caption: String, mediaType: MediaType, voiceNoteData: ByteArray? = null) {
        val userId = authRepository.currentUserId ?: return

        // Check post limit before allowing post
        if (!subscriptionRepository.canPost) {
            _showPostLimitPaywall.value = true
            return
        }

        viewModelScope.launch {
            _isPosting.value = true
            _error.value = null

            try {
                val data = mutableMapOf<String, Any?>()
                data["caption"] = caption.ifBlank { null }
                data["mediaType"] = mediaType.value
                data["timestamp"] = com.google.firebase.firestore.FieldValue.serverTimestamp()

                // Parse hashtags from caption
                val hashtagRegex = Regex("#(\\w+)")
                val hashtags = hashtagRegex.findAll(caption).map { it.groupValues[1] }.toList()
                if (hashtags.isNotEmpty()) data["hashtags"] = hashtags

                var isFirstPoster = false

                if (mediaType == MediaType.TRACK) {
                    val track = _selectedTrack.value ?: throw Exception("No track selected")

                    // Check first poster BEFORE creating the post
                    val priorPostCount = try {
                        postRepository.fetchUniquePosterCountByTrack(track)
                    } catch (_: Exception) { 0 }
                    isFirstPoster = priorPostCount == 0

                    data["trackId"] = track.id
                    data["trackName"] = track.name
                    data["artistName"] = track.artistName
                    data["albumName"] = track.albumName
                    data["albumArtThumbnailURL"] = track.albumArtURL
                    data["albumArtLargeURL"] = track.albumArtLargeURL
                    data["spotifyURI"] = track.spotifyURI
                    data["spotifyWebURL"] = track.spotifyWebURL
                    data["durationMs"] = track.durationMs
                    data["isFirstPoster"] = isFirstPoster
                    track.previewUrl?.let { data["previewUrl"] = it }
                } else {
                    val movie = _selectedMovie.value ?: throw Exception("No movie selected")

                    // Check first poster BEFORE creating the post
                    val priorMoviePostCount = try {
                        postRepository.fetchUniquePosterCountByMovie(movie.id)
                    } catch (_: Exception) { 0 }
                    isFirstPoster = priorMoviePostCount == 0

                    data["movieId"] = movie.id
                    data["movieTitle"] = movie.title
                    data["directorName"] = movie.directorName
                    data["releaseYear"] = movie.year
                    data["posterURL"] = movie.posterURL
                    data["posterLargeURL"] = movie.posterLargeURL
                    data["tmdbWebURL"] = movie.tmdbWebURL
                    data["movieOverview"] = movie.overview
                    data["movieRating"] = movie.rating
                    data["movieCast"] = movie.cast
                    data["isFirstPoster"] = isFirstPoster
                    movie.trailerURL?.let { data["trailerURL"] = it }
                    // Empty track fields for movie posts
                    data["trackId"] = ""
                    data["trackName"] = ""
                    data["artistName"] = ""
                    data["albumName"] = ""
                }

                postRepository.createPost(userId, data, voiceNoteData)
                analyticsService.logPostCreated(mediaType.value)
                subscriptionRepository.incrementPostCount()

                if (isFirstPoster) {
                    // Build a lightweight CymbalPost for the trophy celebration
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
                        isFirstPoster = true,
                    )
                    _showTrophy.value = true
                } else {
                    _postSuccess.value = true
                }
            } catch (e: Exception) {
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

    fun checkForMention(caption: String) {
        val lastWord = caption.split(" ").lastOrNull().orEmpty()
        if (lastWord.startsWith("@") && lastWord.length > 1) {
            val query = lastWord.drop(1)
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
        } else {
            mentionJob?.cancel()
            _mentionSuggestions.value = emptyList()
        }
    }

    fun clearMentionSuggestions() {
        mentionJob?.cancel()
        _mentionSuggestions.value = emptyList()
    }
}
