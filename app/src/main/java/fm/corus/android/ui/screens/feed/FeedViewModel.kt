package fm.corus.android.ui.screens.feed

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import fm.corus.android.data.model.CymbalPost
import fm.corus.android.data.model.CymbalUser
import fm.corus.android.data.model.MediaType
import fm.corus.android.data.remote.TMDBApiService
import fm.corus.android.data.remote.TMDBMovieDetails
import fm.corus.android.data.repository.AuthRepository
import fm.corus.android.data.repository.MessageRepository
import fm.corus.android.data.repository.PostRepository
import fm.corus.android.data.repository.UserRepository
import fm.corus.android.domain.NowPlayingManager
import fm.corus.android.domain.PostEngagementManager
import fm.corus.android.service.AnalyticsService
import fm.corus.android.service.RemoteConfigService
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
    private val tmdbApiService: TMDBApiService,
    val nowPlayingManager: NowPlayingManager,
    val remoteConfig: RemoteConfigService,
    val analyticsService: AnalyticsService,
) : ViewModel() {

    private val _posts = MutableStateFlow<List<CymbalPost>>(emptyList())
    val posts: StateFlow<List<CymbalPost>> = _posts.asStateFlow()

    private val _feedMediaFilter = MutableStateFlow<MediaType?>(null)
    val feedMediaFilter: StateFlow<MediaType?> = _feedMediaFilter.asStateFlow()

    val filteredPosts: StateFlow<List<CymbalPost>> =
        combine(_posts, _feedMediaFilter) { posts, filter ->
            when (filter) {
                null -> posts
                MediaType.TRACK -> posts.filter { it.mediaType == MediaType.TRACK }
                MediaType.MOVIE -> posts.filter { it.mediaType == MediaType.MOVIE }
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

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
    val shareSearchResults: StateFlow<List<CymbalUser>> = _shareSearchResults.asStateFlow()

    private val _recentShareContacts = MutableStateFlow<List<CymbalUser>>(emptyList())
    val recentShareContacts: StateFlow<List<CymbalUser>> = _recentShareContacts.asStateFlow()

    private val _isShareSearching = MutableStateFlow(false)
    val isShareSearching: StateFlow<Boolean> = _isShareSearching.asStateFlow()

    private val _isLoadingShareContacts = MutableStateFlow(true)
    val isLoadingShareContacts: StateFlow<Boolean> = _isLoadingShareContacts.asStateFlow()

    private var shareSearchJob: Job? = null

    fun loadFeed(refresh: Boolean = false) {
        val userId = authRepository.currentUserId ?: return

        viewModelScope.launch {
            if (refresh) {
                _isRefreshing.value = true
                lastTimestamp = null
            } else {
                if (_isLoading.value) return@launch
                _isLoading.value = true
            }

            try {
                val page = postRepository.getFeedPage(
                    userId = userId,
                    pageSize = 7,
                    lastTimestamp = if (refresh) null else lastTimestamp,
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

                // Check actual like status from Firestore (backend doesn't return isLiked)
                engagementManager.checkLikeStatuses(newPosts.map { it.id }, userId)
            } catch (_: Exception) { }

            _isLoading.value = false
            _isRefreshing.value = false
            _hasLoaded.value = true
        }
    }

    fun setFeedMediaFilter(filter: MediaType?) {
        _feedMediaFilter.value = filter
    }

    fun playPreview(post: fm.corus.android.data.model.CymbalPost) {
        viewModelScope.launch {
            nowPlayingManager.play(
                trackId = post.track.id,
                trackName = post.track.name,
                artistName = post.track.artistName,
                albumArtURL = post.track.albumArtURL,
                previewUrl = post.track.previewUrl,
                spotifyURI = post.track.spotifyURI,
                spotifyWebURL = post.track.spotifyWebURL,
                isrc = post.track.isrc,
                sourcePostId = post.id,
            )
        }
    }

    fun generateFeedPlaylist() {
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

    fun repostPost(post: CymbalPost) {
        val userId = authRepository.currentUserId ?: return
        engagementManager.repostPost(post, userId)
    }

    // ── Share contacts & search ──

    fun loadRecentShareContacts() {
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

    fun searchShareUsers(query: String) {
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

    fun sendPostToUser(userId: String, post: CymbalPost, message: String) {
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
                ToastManager.show("Failed to send post")
            }
        }
    }

    // ── Report / Block / Mute ──

    fun reportPost(postId: String, postUserId: String) {
        val currentUserId = authRepository.currentUserId ?: return
        viewModelScope.launch {
            try {
                userRepository.submitReport(
                    reporterId = currentUserId,
                    targetUserId = postUserId,
                    postId = postId,
                    reason = "reported_from_feed",
                    details = "",
                )
                ToastManager.show("Post reported")
            } catch (_: Exception) {
                ToastManager.show("Failed to report post")
            }
        }
    }

    fun blockUser(targetUserId: String) {
        val currentUserId = authRepository.currentUserId ?: return
        viewModelScope.launch {
            try {
                userRepository.blockUser(currentUserId, targetUserId)
                _posts.value = _posts.value.filter { it.user.id != targetUserId }
                ToastManager.show("User blocked")
            } catch (_: Exception) {
                ToastManager.show("Failed to block user")
            }
        }
    }

    fun muteUser(targetUserId: String) {
        val currentUserId = authRepository.currentUserId ?: return
        viewModelScope.launch {
            try {
                userRepository.muteUser(currentUserId, targetUserId)
                _posts.value = _posts.value.filter { it.user.id != targetUserId }
                ToastManager.show("User muted")
            } catch (_: Exception) {
                ToastManager.show("Failed to mute user")
            }
        }
    }

    fun deletePost(postId: String) {
        val userId = authRepository.currentUserId ?: return
        viewModelScope.launch {
            try {
                postRepository.deletePost(postId, userId)
                _posts.value = _posts.value.filter { it.id != postId }
                ToastManager.show("Post deleted")
            } catch (_: Exception) {
                ToastManager.show("Failed to delete post")
            }
        }
    }

    fun isOwnPost(post: CymbalPost): Boolean {
        return post.user.id == authRepository.currentUserId
    }

    fun isPostSaved(postId: String): Boolean {
        return engagementManager.getState(postId)?.isSaved ?: false
    }

    suspend fun fetchMovieDetails(movieId: Int): TMDBMovieDetails? {
        return try {
            tmdbApiService.getMovieDetails(movieId)
        } catch (_: Exception) {
            null
        }
    }
}
