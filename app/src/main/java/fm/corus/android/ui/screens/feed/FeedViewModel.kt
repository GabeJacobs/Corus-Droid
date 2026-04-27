package fm.corus.android.ui.screens.feed

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import android.util.Log
import fm.corus.android.R
import fm.corus.android.data.model.CymbalPost
import fm.corus.android.data.model.CymbalUser
import fm.corus.android.data.model.MediaType
import fm.corus.android.data.model.SuggestedUserMatch
import fm.corus.android.data.remote.CloudFunctionsDataSource
import fm.corus.android.data.remote.TMDBApiService
import fm.corus.android.data.remote.TMDBMovieDetails
import fm.corus.android.data.repository.AuthRepository
import fm.corus.android.data.repository.MessageRepository
import fm.corus.android.data.repository.PostRepository
import fm.corus.android.data.repository.UserRepository
import fm.corus.android.domain.NowPlayingManager
import fm.corus.android.domain.PostCreationEvent
import fm.corus.android.domain.PostEngagementManager
import fm.corus.android.domain.QueuedTrack
import fm.corus.android.service.AnalyticsService
import fm.corus.android.service.RemoteConfigService
import fm.corus.android.ui.components.PostMenuActions
import fm.corus.android.ui.components.ToastManager
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class FeedViewModel @Inject constructor(
    private val postRepository: PostRepository,
    private val authRepository: AuthRepository,
    private val engagementManager: PostEngagementManager,
    private val userRepository: UserRepository,
    private val messageRepository: MessageRepository,
    private val cloudFunctions: CloudFunctionsDataSource,
    private val tmdbApiService: TMDBApiService,
    val nowPlayingManager: NowPlayingManager,
    override val remoteConfig: RemoteConfigService,
    override val analyticsService: AnalyticsService,
    private val postCreationEvent: PostCreationEvent,
    @ApplicationContext private val context: Context,
) : ViewModel(), PostMenuActions {

    private val _posts = MutableStateFlow<List<CymbalPost>>(emptyList())
    val posts: StateFlow<List<CymbalPost>> = _posts.asStateFlow()

    private val _feedMediaFilter = MutableStateFlow<MediaType?>(null)
    val feedMediaFilter: StateFlow<MediaType?> = _feedMediaFilter.asStateFlow()

    // Filtering happens server-side in getFeedPage; _posts already reflects the active filter.
    // Kept as a StateFlow so existing UI observers don't need to change.
    val filteredPosts: StateFlow<List<CymbalPost>> = _posts.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    private val _hasLoaded = MutableStateFlow(false)
    val hasLoaded: StateFlow<Boolean> = _hasLoaded.asStateFlow()
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    private val _hasMore = MutableStateFlow(true)
    val hasMore: StateFlow<Boolean> = _hasMore.asStateFlow()

    private var lastTimestamp: Long? = null

    val engagementStates = engagementManager.states
    val currentUserProfile = authRepository.userProfile

    // ── Share search state ──
    private val _shareSearchResults = MutableStateFlow<List<CymbalUser>>(emptyList())
    override val shareSearchResults: StateFlow<List<CymbalUser>> = _shareSearchResults.asStateFlow()

    private val _recentShareContacts = MutableStateFlow<List<CymbalUser>>(emptyList())
    override val recentShareContacts: StateFlow<List<CymbalUser>> = _recentShareContacts.asStateFlow()

    private val _isShareSearching = MutableStateFlow(false)
    override val isShareSearching: StateFlow<Boolean> = _isShareSearching.asStateFlow()

    private val _isLoadingShareContacts = MutableStateFlow(true)
    override val isLoadingShareContacts: StateFlow<Boolean> = _isLoadingShareContacts.asStateFlow()

    private var shareSearchJob: Job? = null

    // ── Curated bots (empty feed) ──
    private val _curatedMusicBots = MutableStateFlow<List<SuggestedUserMatch>>(emptyList())
    val curatedMusicBots: StateFlow<List<SuggestedUserMatch>> = _curatedMusicBots.asStateFlow()

    private val _curatedFilmBots = MutableStateFlow<List<SuggestedUserMatch>>(emptyList())
    val curatedFilmBots: StateFlow<List<SuggestedUserMatch>> = _curatedFilmBots.asStateFlow()

    private val _isBotsLoading = MutableStateFlow(true)
    val isBotsLoading: StateFlow<Boolean> = _isBotsLoading.asStateFlow()

    // Follow state for bot cards
    private val _followingIds = MutableStateFlow<Set<String>>(emptySet())
    private val _localFollowedIds = MutableStateFlow<Set<String>>(emptySet())

    // Union of remote-observed + local-optimistic follow IDs, observable from Compose.
    val followedBotIds: StateFlow<Set<String>> =
        combine(_followingIds, _localFollowedIds) { remote, local -> remote + local }
            .stateIn(viewModelScope, SharingStarted.Eagerly, emptySet())

    // Track which posts have active real-time listeners (matching iOS PostEngagementStore)
    private val activeListenerPostIds = mutableSetOf<String>()

    init {
        // Auto-refresh feed when a new post is created
        viewModelScope.launch {
            postCreationEvent.events.collect {
                delay(500) // brief delay for Firestore propagation
                loadFeed(refresh = true)
            }
        }
        // Keep NowPlayingManager's queue in sync with the paginated feed so the
        // mini-player next button stays enabled (and functional) past the first page.
        viewModelScope.launch {
            combine(_posts, _hasMore) { posts, hasMore -> posts to hasMore }.collect { (posts, hasMore) ->
                val tracks = posts
                    .filter { it.mediaType == MediaType.TRACK }
                    .map { it.toQueuedTrack() }
                if (tracks.isEmpty()) return@collect
                nowPlayingManager.updateFeedQueue(
                    newQueue = tracks,
                    hasMore = hasMore,
                    loadMore = { loadFeedSuspending(refresh = false) },
                )
            }
        }
    }

    fun loadFeed(refresh: Boolean = false) {
        viewModelScope.launch { loadFeedSuspending(refresh) }
    }

    private suspend fun loadFeedSuspending(refresh: Boolean) {
        val userId = authRepository.currentUserId ?: return

        if (refresh) {
            _isRefreshing.value = true
            lastTimestamp = null
        } else {
            if (_isLoading.value) return
            _isLoading.value = true
        }

        try {
            val page = postRepository.getFeedPage(
                userId = userId,
                pageSize = 7,
                lastTimestamp = if (refresh) null else lastTimestamp,
                mediaType = _feedMediaFilter.value,
            )

            val newPosts = page.posts
            newPosts.forEach { post ->
                engagementManager.initState(
                    postId = post.id,
                    likeCount = post.likeCount,
                    commentCount = post.commentCount,
                    repostCount = post.repostCount,
                    isLiked = post.isLiked,
                    isSaved = false,
                )
            }

            if (refresh) {
                _posts.value = newPosts
            } else {
                _posts.value = (_posts.value + newPosts).distinctBy { it.id }
            }

            _hasMore.value = page.hasMore
            if (newPosts.isNotEmpty()) {
                lastTimestamp = newPosts.last().timestamp.time
            }

            // Start real-time listeners for new posts (matching iOS PostEngagementStore)
            newPosts.forEach { post ->
                if (activeListenerPostIds.add(post.id)) {
                    engagementManager.startListening(post.id)
                }
            }

            // Check actual like status from Firestore (backend doesn't return isLiked)
            engagementManager.checkLikeStatuses(newPosts.map { it.id }, userId)
        } catch (_: Exception) { }

        _isLoading.value = false
        _isRefreshing.value = false
        _hasLoaded.value = true
    }

    fun setFeedMediaFilter(filter: MediaType?) {
        if (_feedMediaFilter.value == filter) return
        _feedMediaFilter.value = filter
        // Server-side filter changed — reset the paginated feed and re-fetch
        // so the returned page matches the new filter.
        lastTimestamp = null
        _posts.value = emptyList()
        _hasMore.value = true
        loadFeed(refresh = true)
    }

    fun playPreview(post: fm.corus.android.data.model.CymbalPost) {
        viewModelScope.launch {
            val trackPosts = filteredPosts.value.filter { it.mediaType == MediaType.TRACK }
            val queue = trackPosts.map { it.toQueuedTrack() }
            val track = post.toQueuedTrack()
            if (queue.any { it.trackId == track.trackId }) {
                nowPlayingManager.play(track = track, queue = queue)
                // Re-wire the paginated-feed hook that `play` resets, so the next
                // button stays live past the first page without waiting for
                // _posts/_hasMore to change.
                nowPlayingManager.updateFeedQueue(
                    newQueue = queue,
                    hasMore = _hasMore.value,
                    loadMore = { loadFeedSuspending(refresh = false) },
                )
            } else {
                nowPlayingManager.play(track = track, queue = listOf(track))
            }
        }
    }

    private fun fm.corus.android.data.model.CymbalPost.toQueuedTrack() = QueuedTrack(
        trackId = track.id,
        trackName = track.name,
        artistName = track.artistName,
        albumArtURL = track.albumArtURL,
        previewUrl = track.previewUrl,
        spotifyURI = track.spotifyURI,
        spotifyWebURL = track.spotifyWebURL,
        isrc = track.isrc,
        sourcePostId = id,
    )

    fun generateFeedPlaylist() {
        analyticsService.logFeedPlaylistTapped()
        viewModelScope.launch {
            nowPlayingManager.generateFeedPlaylist()
        }
    }

    fun toggleLike(postId: String) {
        val userId = authRepository.currentUserId ?: return
        engagementManager.toggleLike(postId, userId)
    }

    fun toggleSave(postId: String) {
        val userId = authRepository.currentUserId ?: return
        engagementManager.toggleSave(postId, userId)
    }

    // ── Share contacts & search ──

    override fun loadRecentShareContacts() {
        val userId = authRepository.currentUserId ?: return
        _isLoadingShareContacts.value = true
        viewModelScope.launch {
            try {
                val threads = messageRepository.listThreads(userId)
                val contacts = threads.mapNotNull { it.otherUser }
                if (contacts.isNotEmpty()) {
                    _recentShareContacts.value = contacts.take(20)
                } else {
                    val following = userRepository.fetchFollowingPaginated(userId, limit = 20).users
                    val followers = userRepository.fetchFollowersPaginated(userId, limit = 20).users
                    val seen = mutableSetOf<String>()
                    val combined = mutableListOf<CymbalUser>()
                    for (user in following + followers) {
                        if (seen.add(user.id)) combined.add(user)
                    }
                    _recentShareContacts.value = combined.take(20)
                }
            } catch (_: Exception) { }
            _isLoadingShareContacts.value = false
        }
    }

    override fun searchShareUsers(query: String) {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) {
            shareSearchJob?.cancel()
            _shareSearchResults.value = emptyList()
            _isShareSearching.value = false
            return
        }

        shareSearchJob?.cancel()
        shareSearchJob = viewModelScope.launch {
            _isShareSearching.value = true
            delay(250)
            try {
                _shareSearchResults.value = userRepository.searchUsers(trimmed)
            } catch (_: Exception) {
                _shareSearchResults.value = emptyList()
            }
            _isShareSearching.value = false
        }
    }

    override fun sendPostToUser(userId: String, post: CymbalPost, message: String) {
        val currentUserId = authRepository.currentUserId ?: return
        viewModelScope.launch {
            try {
                val threadId = messageRepository.getOrCreateThread(currentUserId, userId)
                val text = message.trim()
                if (post.isMovie) {
                    messageRepository.sendSharedFilmMessage(
                        threadId = threadId,
                        fromUserId = currentUserId,
                        text = text,
                        movieTitle = post.movieTitle ?: post.displayTitle,
                        directorName = post.directorName ?: post.displaySubtitle,
                        posterURL = post.posterURL,
                        tmdbWebURL = post.tmdbWebURL,
                    )
                } else {
                    messageRepository.sendSharedTrackMessage(
                        threadId = threadId,
                        fromUserId = currentUserId,
                        text = text,
                        trackName = post.track.name,
                        artistName = post.track.artistName,
                        albumArtURL = post.track.albumArtURL,
                        spotifyURL = post.track.spotifyWebURL.ifBlank { null },
                    )
                }
            } catch (_: Exception) {
                ToastManager.show(context.getString(R.string.feed_toast_failed_send_post))
            }
        }
    }

    // ── Report / Block / Mute ──

    override fun reportPost(postId: String, postUserId: String) {
        val currentUserId = authRepository.currentUserId ?: return
        analyticsService.logReportPost(postId, "reported_from_feed")
        viewModelScope.launch {
            try {
                userRepository.submitReport(
                    reporterId = currentUserId,
                    targetUserId = postUserId,
                    postId = postId,
                    reason = "reported_from_feed",
                    details = "",
                )
                ToastManager.show(context.getString(R.string.feed_toast_post_reported))
            } catch (_: Exception) {
                ToastManager.show(context.getString(R.string.feed_toast_failed_report))
            }
        }
    }

    override fun blockUser(targetUserId: String) {
        val currentUserId = authRepository.currentUserId ?: return
        viewModelScope.launch {
            try {
                userRepository.blockUser(currentUserId, targetUserId)
                _posts.value = _posts.value.filter { it.user.id != targetUserId }
                ToastManager.show(context.getString(R.string.feed_toast_user_blocked))
            } catch (_: Exception) {
                ToastManager.show(context.getString(R.string.feed_toast_failed_block))
            }
        }
    }

    fun muteUser(targetUserId: String) {
        val currentUserId = authRepository.currentUserId ?: return
        viewModelScope.launch {
            try {
                userRepository.muteUser(currentUserId, targetUserId)
                _posts.value = _posts.value.filter { it.user.id != targetUserId }
                ToastManager.show(context.getString(R.string.feed_toast_user_muted))
            } catch (_: Exception) {
                ToastManager.show(context.getString(R.string.feed_toast_failed_mute))
            }
        }
    }

    override fun deletePost(postId: String) {
        val userId = authRepository.currentUserId ?: return
        viewModelScope.launch {
            try {
                postRepository.deletePost(postId, userId)
                _posts.value = _posts.value.filter { it.id != postId }
                authRepository.bumpCymbalCount(-1)
                ToastManager.show(context.getString(R.string.feed_toast_post_deleted))
            } catch (_: Exception) {
                ToastManager.show(context.getString(R.string.feed_toast_failed_delete))
            }
        }
    }

    override fun isOwnPost(post: CymbalPost): Boolean {
        return post.user.id == authRepository.currentUserId
    }

    override suspend fun fetchBackCover(postId: String): String? {
        return postRepository.fetchBackCover(postId)
    }

    fun isPostSaved(postId: String): Boolean {
        return engagementManager.getState(postId)?.isSaved ?: false
    }

    // ── Curated bots ──

    fun loadBotSuggestions() {
        val uid = authRepository.currentUserId ?: return
        viewModelScope.launch {
            userRepository.followingIds.collect { ids ->
                _followingIds.value = ids
            }
        }
        viewModelScope.launch {
            try {
                val musicBots = cloudFunctions.getBotSuggestions(uid, botType = "music")
                _curatedMusicBots.value = musicBots
            } catch (e: Exception) {
                Log.e("FeedVM", "Failed to load music bots", e)
            }
            try {
                val filmBots = cloudFunctions.getBotSuggestions(uid, botType = "film")
                _curatedFilmBots.value = filmBots
            } catch (e: Exception) {
                Log.e("FeedVM", "Failed to load film bots", e)
            }
            _isBotsLoading.value = false
        }
    }

    fun toggleBotFollow(user: CymbalUser) {
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

    suspend fun fetchMovieDetails(movieId: Int): TMDBMovieDetails? {
        return try {
            tmdbApiService.getMovieDetails(movieId)
        } catch (_: Exception) {
            null
        }
    }

    override fun onCleared() {
        super.onCleared()
        activeListenerPostIds.forEach { engagementManager.stopListening(it) }
        activeListenerPostIds.clear()
    }
}
