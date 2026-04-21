package fm.corus.android.ui.screens.compose

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import fm.corus.android.data.model.CymbalMovie
import fm.corus.android.data.model.CymbalPost
import fm.corus.android.data.model.CymbalTrack
import fm.corus.android.data.model.CymbalUser
import fm.corus.android.data.model.MediaType
import fm.corus.android.data.model.TrendingMovie
import fm.corus.android.data.model.TrendingSong
import fm.corus.android.data.repository.AuthRepository
import fm.corus.android.data.repository.ExploreRepository
import fm.corus.android.data.repository.PostRepository
import fm.corus.android.data.repository.SpotifyRepository
import fm.corus.android.data.repository.SubscriptionRepository
import fm.corus.android.data.repository.TMDBRepository
import fm.corus.android.data.repository.UserRepository
import fm.corus.android.domain.HapticManager
import fm.corus.android.domain.NowPlayingManager
import fm.corus.android.domain.PostCreationEvent
import fm.corus.android.domain.PostEngagementManager
import fm.corus.android.service.AnalyticsService
import fm.corus.android.ui.components.extractMentions
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SearchResultItem(
    val id: String,
    val imageURL: String?,
    val title: String,
    val subtitle: String,
    val trailingText: String? = null,
    val showPlayOverlay: Boolean = false,
)

@HiltViewModel
class ComposeViewModel @Inject constructor(
    private val postRepository: PostRepository,
    private val spotifyRepository: SpotifyRepository,
    private val tmdbRepository: TMDBRepository,
    private val authRepository: AuthRepository,
    val analyticsService: AnalyticsService,
    private val userRepository: UserRepository,
    private val subscriptionRepository: SubscriptionRepository,
    private val exploreRepository: ExploreRepository,
    private val nowPlayingManager: NowPlayingManager,
    private val postCreationEvent: PostCreationEvent,
    private val engagementManager: PostEngagementManager,
    private val hapticManager: HapticManager,
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

    // Search results
    private val _searchResults = MutableStateFlow<List<SearchResultItem>>(emptyList())
    val searchResults: StateFlow<List<SearchResultItem>> = _searchResults.asStateFlow()

    private val _filmResults = MutableStateFlow<List<CymbalMovie>>(emptyList())
    val filmResults: StateFlow<List<CymbalMovie>> = _filmResults.asStateFlow()

    private val _isSearching = MutableStateFlow(false)
    val isSearching: StateFlow<Boolean> = _isSearching.asStateFlow()

    private val _mentionSuggestions = MutableStateFlow<List<CymbalUser>>(emptyList())
    val mentionSuggestions: StateFlow<List<CymbalUser>> = _mentionSuggestions.asStateFlow()

    // Trending songs & movies
    private val _trendingSongs = MutableStateFlow<List<TrendingSong>>(emptyList())
    val trendingSongs: StateFlow<List<TrendingSong>> = _trendingSongs.asStateFlow()

    private val _trendingMovies = MutableStateFlow<List<TrendingMovie>>(emptyList())
    val trendingMovies: StateFlow<List<TrendingMovie>> = _trendingMovies.asStateFlow()

    private val _isLoadingTrending = MutableStateFlow(true)
    val isLoadingTrending: StateFlow<Boolean> = _isLoadingTrending.asStateFlow()

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
                    _searchResults.value = cachedTracks.map { track ->
                        SearchResultItem(
                            id = track.id,
                            imageURL = track.albumArtURL,
                            title = track.name,
                            subtitle = track.artistName,
                            trailingText = track.formattedDuration,
                            showPlayOverlay = true,
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
            } catch (_: Exception) {
                _searchResults.value = emptyList()
                _filmResults.value = emptyList()
            }
            _isSearching.value = false
        }
    }

    fun selectResult(result: SearchResultItem, mediaType: MediaType) {
        // Mirrors iOS SongSearchView.selectSong haptic.
        hapticManager.impact(HapticManager.ImpactStyle.LIGHT)
        _selectedTrack.value = cachedTracks.firstOrNull { it.id == result.id }
        _searchResults.value = emptyList()
    }

    fun selectFilmResult(movie: CymbalMovie) {
        // Mirrors iOS FilmSearchView.selectMovie haptic.
        hapticManager.impact(HapticManager.ImpactStyle.LIGHT)
        _selectedMovie.value = movie
        _filmResults.value = emptyList()
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
    }

    fun selectTrendingSong(song: TrendingSong) {
        // Mirrors iOS SongSearchView.selectTrendingSong haptic.
        hapticManager.impact(HapticManager.ImpactStyle.LIGHT)
        _selectedTrack.value = song.track
        _searchResults.value = emptyList()
    }

    fun selectTrendingMovie(movie: TrendingMovie) {
        // Mirrors iOS FilmSearchView.selectTrendingMovie haptic.
        hapticManager.impact(HapticManager.ImpactStyle.LIGHT)
        _selectedMovie.value = movie.asCymbalMovie()
        _filmResults.value = emptyList()
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

        viewModelScope.launch {
            // Safety net: verify post limit with the server before submitting.
            // Mirrors iOS ComposeView.postCymbal — if the count hasn't been loaded yet,
            // ask the server now; otherwise trust the cached count.
            if (!subscriptionRepository.postCountLoaded.value) {
                val allowed = subscriptionRepository.checkCanPostFromServer()
                if (!allowed) {
                    _showPostLimitPaywall.value = true
                    return@launch
                }
            } else if (!subscriptionRepository.canPost) {
                _showPostLimitPaywall.value = true
                return@launch
            }

            _isPosting.value = true
            _error.value = null

            try {
                val data = mutableMapOf<String, Any>()
                data["caption"] = caption.ifBlank { "" }
                data["mediaType"] = mediaType.value

                // Parse hashtags from caption
                val hashtagRegex = Regex("#(\\w+)")
                val hashtags = hashtagRegex.findAll(caption).map { it.groupValues[1] }.toList()
                data["hashtags"] = hashtags

                // Repost metadata — only included when the attribution toggle is on.
                // Mirrors iOS ComposeView: toggling attribution off drops the repost
                // relationship entirely, creating an independent post.
                val includeRepost = _showRepostAttribution.value
                val originalPostId = _repostedFromPostId.value
                val originalUserId = _repostedFromUserId.value
                val originalUsername = _repostedFromUsername.value
                if (includeRepost && originalPostId != null) {
                    data["repostedFromPostId"] = originalPostId
                    data["repostedFromUserId"] = originalUserId ?: ""
                    data["repostedFromUsername"] = originalUsername ?: ""
                }

                var isFirstPoster = false

                // The stored isFirstPoster is also recomputed authoritatively by the
                // assignFirstPosterOnPostCreate Firestore trigger (see backend/functions/index.js)
                // so a racy client-side count here is self-correcting within seconds.
                if (mediaType == MediaType.TRACK) {
                    val track = _selectedTrack.value ?: throw Exception("No track selected")

                    val priorPostCount = try {
                        postRepository.fetchUniquePosterCountByTrack(track)
                    } catch (_: Exception) { 0 }
                    isFirstPoster = priorPostCount == 0

                    data["trackId"] = track.id
                    data["trackName"] = track.name
                    data["artistName"] = track.artistName
                    data["albumName"] = track.albumName
                    data["albumArtURL"] = track.albumArtURL ?: ""
                    data["albumArtLargeURL"] = track.albumArtLargeURL ?: ""
                    data["spotifyURI"] = track.spotifyURI
                    data["spotifyWebURL"] = track.spotifyWebURL
                    data["durationMs"] = track.durationMs
                    data["isFirstPoster"] = isFirstPoster
                    data["previewUrl"] = track.previewUrl ?: ""
                    data["isrc"] = track.isrc ?: ""
                    data["trackReleaseDate"] = track.releaseDate ?: ""
                    data["trackReleaseDatePrecision"] = track.releaseDatePrecision ?: ""
                } else {
                    val movie = _selectedMovie.value ?: throw Exception("No movie selected")

                    val priorMoviePostCount = try {
                        postRepository.fetchUniquePosterCountByMovie(movie.id)
                    } catch (_: Exception) { 0 }
                    isFirstPoster = priorMoviePostCount == 0

                    data["movieId"] = movie.id
                    data["movieTitle"] = movie.title
                    data["directorName"] = movie.directorName
                    data["releaseYear"] = movie.year
                    data["movieReleaseDate"] = movie.releaseDate ?: ""
                    data["posterURL"] = movie.posterURL ?: ""
                    data["posterLargeURL"] = movie.posterLargeURL ?: ""
                    data["tmdbWebURL"] = movie.tmdbWebURL
                    data["movieOverview"] = movie.overview
                    data["movieRating"] = movie.rating
                    data["movieCast"] = movie.cast
                    data["isFirstPoster"] = isFirstPoster
                    data["trailerURL"] = movie.trailerURL ?: ""
                    // Also set albumArtURL to posterURL for backwards compat (notifications use this field)
                    data["albumArtURL"] = movie.posterURL ?: ""
                }

                val newPostId = postRepository.createPost(userId, data, voiceNoteData)
                analyticsService.logPostCreated(mediaType.value)
                subscriptionRepository.incrementPostCount()
                authRepository.bumpCymbalCount(1)
                postCreationEvent.notifyPostCreated()

                // Mirrors iOS ComposeView post-created haptic (track & film branches).
                hapticManager.notification(HapticManager.NotificationType.SUCCESS)

                // Repost side-effects — optimistic repostCount bump on the
                // original post card + notification to the original poster.
                if (includeRepost && originalPostId != null) {
                    engagementManager.incrementRepostCount(originalPostId)
                    if (!originalUserId.isNullOrEmpty() && originalUserId != userId) {
                        try {
                            postRepository.createNotification(
                                type = "repost",
                                fromUserId = userId,
                                toUserId = originalUserId,
                                postId = newPostId,
                                postAlbumArtURL = data["albumArtURL"] as? String,
                            )
                        } catch (_: Exception) { }
                    }
                }

                // Send tag notifications for @mentions in caption (matches iOS)
                val albumArtURL = data["albumArtURL"] as? String
                    ?: data["albumArtThumbnailURL"] as? String
                val mentionedUsernames = extractMentions(caption)
                for (username in mentionedUsernames) {
                    try {
                        val mentionedUser = userRepository.fetchUserByUsername(username) ?: continue
                        if (mentionedUser.id == userId) continue
                        postRepository.createNotification(
                            type = "tag",
                            fromUserId = userId,
                            toUserId = mentionedUser.id,
                            postId = newPostId,
                            postAlbumArtURL = albumArtURL,
                        )
                    } catch (_: Exception) { }
                }

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
                previewUrl = track.previewUrl,
                isrc = track.isrc,
            )
        }
    }

    fun stopPreview() {
        nowPlayingManager.stop()
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
        _isSearching.value = false
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
        cachedTracks = emptyList()
        cachedMovies = emptyList()
    }
}
