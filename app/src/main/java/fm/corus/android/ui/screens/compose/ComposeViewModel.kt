package fm.corus.android.ui.screens.compose

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
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
import fm.corus.android.ui.components.parseMentionQuery
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
    val isSoundCloud: Boolean = false,
)

@HiltViewModel
class ComposeViewModel @Inject constructor(
    private val postRepository: PostRepository,
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
) : ViewModel() {

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
                _searchHasError.value = true
            }
            _isSearching.value = false
        }
    }

    fun selectResult(result: SearchResultItem, mediaType: MediaType) {
        // Mirrors iOS SongSearchView.selectSong haptic.
        hapticManager.impact(HapticManager.ImpactStyle.LIGHT)
        _selectedTrack.value = cachedTracks.firstOrNull { it.id == result.id }
    }

    fun selectFilmResult(movie: CymbalMovie) {
        // Mirrors iOS FilmSearchView.selectMovie haptic.
        hapticManager.impact(HapticManager.ImpactStyle.LIGHT)
        _selectedMovie.value = movie
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
            try {
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
                }

                val result = postRepository.createPost(payload, voiceNoteData)
                analyticsService.logPostCreated(mediaType.value)
                subscriptionRepository.incrementPostCount()
                authRepository.bumpCymbalCount(1)
                postCreationEvent.notifyPostCreated(mediaType)

                // Paid users: warn when they're approaching the 6h hard cap so
                // it doesn't come out of nowhere. Throttled inside the
                // repository so subsequent posts in the same window are silent.
                if (subscriptionRepository.hasFullAccess &&
                    subscriptionRepository.shouldShowApproachingCapWarning()
                ) {
                    _approachingCapRemaining.value = subscriptionRepository.approachingCapRemaining
                    _showApproachingCapAlert.value = true
                }

                // Mirrors iOS ComposeView post-created haptic (track & film branches).
                hapticManager.notification(HapticManager.NotificationType.SUCCESS)

                // Repost notification + tag notifications are created by the
                // `onPostCreatedNotifyTagsAndRepost` Cloud Function trigger,
                // not the client — they would double-fire if we also wrote
                // them here. Same on iOS.

                if (result.isFirstPoster) {
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
                    _postSuccess.value = true
                }
            } catch (e: CloudFunctionsDataSource.PostLimitReachedException) {
                // Server hit. The callable returned recentCount + dailyLimit,
                // but the SubscriptionRepository already tracks these via its
                // own refresh; just open the paywall (or hard-cap dialog).
                if (e.hardCap) {
                    _showHardCapAlert.value = true
                } else {
                    _showPostLimitPaywall.value = true
                }
            } catch (e: CloudFunctionsDataSource.RepostOriginalMissingException) {
                _error.value = "That post is no longer available."
            } catch (e: CloudFunctionsDataSource.PostingBannedException) {
                _error.value = "Posting is blocked by your account permissions right now."
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

    fun checkForMention(caption: String, caret: Int = caption.length) {
        val query = parseMentionQuery(caption, caret)
        if (query != null) {
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
                albumArtLargeURL = track.albumArtLargeURL,
                previewUrl = track.previewUrl,
                isrc = track.isrc,
                source = track.source,
                soundcloudId = track.soundcloudId,
                soundcloudPermalinkUrl = track.soundcloudPermalinkUrl,
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
        _commentsAudience.value = fm.corus.android.data.model.CommentsAudience.EVERYONE
        cachedTracks = emptyList()
        cachedMovies = emptyList()
    }
}
