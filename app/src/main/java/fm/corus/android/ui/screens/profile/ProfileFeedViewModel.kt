package fm.corus.android.ui.screens.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import fm.corus.android.data.model.CymbalPost
import fm.corus.android.data.model.CymbalUser
import fm.corus.android.data.model.MediaType
import fm.corus.android.data.remote.CloudFunctionsDataSource
import fm.corus.android.data.repository.AuthRepository
import fm.corus.android.data.repository.MessageRepository
import fm.corus.android.data.repository.PostRepository
import fm.corus.android.data.repository.UserRepository
import fm.corus.android.domain.NowPlayingManager
import fm.corus.android.domain.PostEngagementManager
import fm.corus.android.domain.QueuedTrack
import fm.corus.android.service.AnalyticsService
import fm.corus.android.service.RemoteConfigService
import fm.corus.android.ui.components.ToastManager
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class ProfileFeedSource { SONGS, FILMS, LIKES, SAVES }

/**
 * In-memory cache for passing initial posts from profile grids to the
 * ProfileFeedScreen. Set just before navigating; consumed on init.
 */
object ProfileFeedCache {
    var posts: List<CymbalPost> = emptyList()
    var hasMore: Boolean = false
    var profileUser: CymbalUser? = null
}

@HiltViewModel
class ProfileFeedViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val cloudFunctions: CloudFunctionsDataSource,
    private val postRepository: PostRepository,
    private val userRepository: UserRepository,
    private val messageRepository: MessageRepository,
    private val engagementManager: PostEngagementManager,
    val nowPlayingManager: NowPlayingManager,
    val remoteConfig: RemoteConfigService,
    val analyticsService: AnalyticsService,
) : ViewModel() {

    suspend fun fetchBackCover(postId: String): String? {
        return postRepository.fetchBackCover(postId)
    }

    private val _posts = MutableStateFlow<List<CymbalPost>>(emptyList())
    val posts: StateFlow<List<CymbalPost>> = _posts.asStateFlow()

    private val _hasMore = MutableStateFlow(false)
    val hasMore: StateFlow<Boolean> = _hasMore.asStateFlow()

    private val _isLoadingMore = MutableStateFlow(false)
    val isLoadingMore: StateFlow<Boolean> = _isLoadingMore.asStateFlow()

    val engagementStates = engagementManager.states
    val currentUserProfile = authRepository.userProfile

    val currentUserId: String? get() = authRepository.currentUserId

    private var source: ProfileFeedSource = ProfileFeedSource.SONGS
    private var userId: String = ""
    private var initialized = false
    private var profileUser: CymbalUser? = null

    private val PAGE_SIZE = 15

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

    // Track which posts have active real-time listeners (matching iOS PostEngagementStore)
    private val activeListenerPostIds = mutableSetOf<String>()

    fun initFeed(userId: String, segment: Int) {
        if (initialized) return
        initialized = true
        this.userId = userId
        this.source = when (segment) {
            0 -> ProfileFeedSource.SONGS
            1 -> ProfileFeedSource.FILMS
            2 -> ProfileFeedSource.LIKES
            3 -> ProfileFeedSource.SAVES
            else -> ProfileFeedSource.SONGS
        }
        val cachedPosts = ProfileFeedCache.posts
        val profileUser = ProfileFeedCache.profileUser
        this.profileUser = profileUser
        _hasMore.value = ProfileFeedCache.hasMore
        // Clear cache after consuming
        ProfileFeedCache.posts = emptyList()
        ProfileFeedCache.hasMore = false
        ProfileFeedCache.profileUser = null

        // Profile API may return posts with empty user data since
        // the caller is already on the profile page. Enrich posts
        // with the profile user so PostCard can render avatar/username.
        _posts.value = if (profileUser != null) {
            cachedPosts.map { post ->
                if (post.user.username.isBlank() || post.user.avatarURL.isNullOrBlank()) {
                    post.copy(user = profileUser)
                } else {
                    post
                }
            }
        } else {
            cachedPosts
        }

        // Init engagement states for all posts
        val viewerId = authRepository.currentUserId ?: return
        _posts.value.forEach { post ->
            engagementManager.initState(
                postId = post.id,
                likeCount = post.likeCount,
                commentCount = post.commentCount,
                repostCount = post.repostCount,
                isLiked = post.isLiked,
                isSaved = false,
            )
            if (activeListenerPostIds.add(post.id)) {
                engagementManager.startListening(post.id)
            }
        }
        viewModelScope.launch {
            engagementManager.checkLikeStatuses(_posts.value.map { it.id }, viewerId)
        }
    }

    private fun enrichPost(post: CymbalPost): CymbalPost {
        val pu = profileUser ?: return post
        return if (post.user.username.isBlank() || post.user.avatarURL.isNullOrBlank()) {
            post.copy(user = pu)
        } else {
            post
        }
    }

    fun loadMore() {
        if (!_hasMore.value || _isLoadingMore.value) return
        val viewerId = authRepository.currentUserId ?: return
        _isLoadingMore.value = true
        viewModelScope.launch {
            try {
                when (source) {
                    ProfileFeedSource.SONGS, ProfileFeedSource.FILMS -> {
                        val lastTimestamp = _posts.value.lastOrNull()?.timestamp?.time ?: run {
                            _hasMore.value = false
                            _isLoadingMore.value = false
                            return@launch
                        }
                        val allNew = cloudFunctions.getProfilePosts(
                            userId = userId,
                            viewerId = viewerId,
                            limit = PAGE_SIZE,
                            lastTimestamp = lastTimestamp,
                        )
                        val mediaType = if (source == ProfileFeedSource.SONGS) MediaType.TRACK else MediaType.MOVIE
                        val filtered = allNew.filter { it.mediaType == mediaType }
                        val existingIds = _posts.value.map { it.id }.toSet()
                        val unique = filtered.filter { it.id !in existingIds }.map { enrichPost(it) }
                        _posts.value = _posts.value + unique
                        if (allNew.size < PAGE_SIZE) _hasMore.value = false
                        // Init engagement for new posts
                        unique.forEach { post ->
                            engagementManager.initState(
                                postId = post.id,
                                likeCount = post.likeCount,
                                commentCount = post.commentCount,
                                repostCount = post.repostCount,
                                isLiked = post.isLiked,
                                isSaved = false,
                            )
                            if (activeListenerPostIds.add(post.id)) {
                                engagementManager.startListening(post.id)
                            }
                        }
                        if (unique.isNotEmpty()) {
                            engagementManager.checkLikeStatuses(unique.map { it.id }, viewerId)
                        }
                    }
                    ProfileFeedSource.LIKES -> {
                        val offset = _posts.value.size
                        val fetched = cloudFunctions.getLikedPosts(
                            userId = userId,
                            viewerId = viewerId,
                            limit = PAGE_SIZE,
                            offset = offset,
                        )
                        val existingIds = _posts.value.map { it.id }.toSet()
                        val unique = fetched.filter { it.id !in existingIds }
                        _posts.value = _posts.value + unique
                        if (fetched.size < PAGE_SIZE) _hasMore.value = false
                        unique.forEach { post ->
                            engagementManager.initState(
                                postId = post.id,
                                likeCount = post.likeCount,
                                commentCount = post.commentCount,
                                repostCount = post.repostCount,
                                isLiked = post.isLiked,
                                isSaved = false,
                            )
                            if (activeListenerPostIds.add(post.id)) {
                                engagementManager.startListening(post.id)
                            }
                        }
                        if (unique.isNotEmpty()) {
                            engagementManager.checkLikeStatuses(unique.map { it.id }, viewerId)
                        }
                    }
                    ProfileFeedSource.SAVES -> {
                        val offset = _posts.value.size
                        val fetched = cloudFunctions.getSavedPosts(
                            userId = userId,
                            limit = PAGE_SIZE,
                            offset = offset,
                        )
                        val existingIds = _posts.value.map { it.id }.toSet()
                        val unique = fetched.filter { it.id !in existingIds }
                        _posts.value = _posts.value + unique
                        if (fetched.size < PAGE_SIZE) _hasMore.value = false
                        unique.forEach { post ->
                            engagementManager.initState(
                                postId = post.id,
                                likeCount = post.likeCount,
                                commentCount = post.commentCount,
                                repostCount = post.repostCount,
                                isLiked = post.isLiked,
                                isSaved = false,
                            )
                            if (activeListenerPostIds.add(post.id)) {
                                engagementManager.startListening(post.id)
                            }
                        }
                        if (unique.isNotEmpty()) {
                            engagementManager.checkLikeStatuses(unique.map { it.id }, viewerId)
                        }
                    }
                }
            } catch (_: Exception) { }
            _isLoadingMore.value = false
        }
    }

    fun playPreview(post: CymbalPost) {
        viewModelScope.launch {
            val trackPosts = _posts.value.filter { it.mediaType == MediaType.TRACK }
            val queue = trackPosts.map { it.toQueuedTrack() }
            val track = post.toQueuedTrack()
            if (queue.any { it.trackId == track.trackId }) {
                nowPlayingManager.play(track = track, queue = queue)
            } else {
                nowPlayingManager.play(track = track, queue = listOf(track))
            }
        }
    }

    private fun CymbalPost.toQueuedTrack() = QueuedTrack(
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

    fun toggleLike(postId: String) {
        val userId = authRepository.currentUserId ?: return
        engagementManager.toggleLike(postId, userId)
    }

    fun toggleSave(postId: String) {
        val userId = authRepository.currentUserId ?: return
        engagementManager.toggleSave(postId, userId)
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

    suspend fun resolveUsernameToId(username: String): String? {
        return try {
            userRepository.fetchUserByUsername(username)?.id
        } catch (_: Exception) { null }
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

    fun reportPost(postId: String, postUserId: String) {
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

    override fun onCleared() {
        super.onCleared()
        activeListenerPostIds.forEach { engagementManager.stopListening(it) }
        activeListenerPostIds.clear()
    }
}
