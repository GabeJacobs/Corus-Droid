package fm.corus.android.ui.screens.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import fm.corus.android.data.model.CymbalPost
import fm.corus.android.data.model.CymbalUser
import fm.corus.android.data.model.MediaType
import fm.corus.android.data.repository.AuthRepository
import fm.corus.android.data.repository.PostRepository
import fm.corus.android.data.repository.UserRepository
import fm.corus.android.domain.NowPlayingManager
import fm.corus.android.domain.PostEngagementManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class OtherProfileViewModel @Inject constructor(
    private val userRepository: UserRepository,
    private val postRepository: PostRepository,
    private val authRepository: AuthRepository,
    val nowPlayingManager: NowPlayingManager,
    private val engagementManager: PostEngagementManager,
) : ViewModel() {

    fun generatePlaylist(userId: String) {
        viewModelScope.launch {
            nowPlayingManager.generateProfilePlaylist(userId)
        }
    }

    private val _profile = MutableStateFlow<CymbalUser?>(null)
    val profile: StateFlow<CymbalUser?> = _profile.asStateFlow()

    private val _posts = MutableStateFlow<List<CymbalPost>>(emptyList())
    val posts: StateFlow<List<CymbalPost>> = _posts.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _isLoadingMore = MutableStateFlow(false)
    val isLoadingMore: StateFlow<Boolean> = _isLoadingMore.asStateFlow()

    private val _hasMore = MutableStateFlow(true)
    val hasMore: StateFlow<Boolean> = _hasMore.asStateFlow()

    private var postsLastTimestamp: Long? = null
    private val PAGE_SIZE = 30
    private val MIN_SEGMENT_POSTS = 12

    private val _isFollowing = MutableStateFlow(false)
    val isFollowing: StateFlow<Boolean> = _isFollowing.asStateFlow()

    private val _isFollowLoading = MutableStateFlow(false)
    val isFollowLoading: StateFlow<Boolean> = _isFollowLoading.asStateFlow()

    private val _isBlocked = MutableStateFlow(false)
    val isBlocked: StateFlow<Boolean> = _isBlocked.asStateFlow()

    private val _isMuted = MutableStateFlow(false)
    val isMuted: StateFlow<Boolean> = _isMuted.asStateFlow()

    private val _isSubscribedToNotifications = MutableStateFlow(false)
    val isSubscribedToNotifications: StateFlow<Boolean> = _isSubscribedToNotifications.asStateFlow()

    val currentUserId: String? get() = authRepository.currentUserId

    fun loadProfile(userId: String) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val user = userRepository.fetchUserProfile(userId)
                _profile.value = user
                _isFollowing.value = userRepository.isFollowing(userId)
                _isBlocked.value = userRepository.blockedIds.value.contains(userId)
                _isMuted.value = userRepository.isUserMuted(userId)
                val viewerIdForSub = authRepository.currentUserId
                if (viewerIdForSub != null) {
                    _isSubscribedToNotifications.value = userRepository.isSubscribedToUserPosts(viewerIdForSub, userId)
                }

                // Load user's posts — keep fetching until both music and film
                // tabs have enough filtered posts or the server runs out
                val viewerId = authRepository.currentUserId ?: return@launch
                var allPosts = listOf<CymbalPost>()
                var cursor: Long? = null
                var serverHasMore = true
                while (serverHasMore) {
                    val page = postRepository.getProfilePosts(
                        userId = userId,
                        viewerId = viewerId,
                        limit = PAGE_SIZE,
                        lastTimestamp = cursor,
                    )
                    allPosts = allPosts + page
                    serverHasMore = page.size >= PAGE_SIZE
                    if (page.isNotEmpty()) cursor = page.last().timestamp.time
                    val tracks = allPosts.count { it.mediaType == MediaType.TRACK }
                    val movies = allPosts.count { it.mediaType == MediaType.MOVIE }
                    if (tracks >= MIN_SEGMENT_POSTS && movies >= MIN_SEGMENT_POSTS) break
                    if (!serverHasMore) break
                }
                _posts.value = allPosts
                postsLastTimestamp = cursor
                _hasMore.value = serverHasMore

                allPosts.forEach { post ->
                    engagementManager.initState(
                        postId = post.id,
                        likeCount = post.likeCount,
                        commentCount = post.commentCount,
                        repostCount = post.repostCount,
                        isLiked = post.isLiked,
                        isSaved = false,
                    )
                }
                engagementManager.checkLikeStatuses(allPosts.map { it.id }, viewerId)
            } catch (_: Exception) { }
            _isLoading.value = false
        }
    }

    fun loadMore(userId: String) {
        if (!_hasMore.value || _isLoadingMore.value) return
        val viewerId = authRepository.currentUserId ?: return
        val cursor = postsLastTimestamp ?: return
        _isLoadingMore.value = true
        viewModelScope.launch {
            try {
                val newPosts = postRepository.getProfilePosts(
                    userId = userId,
                    viewerId = viewerId,
                    limit = PAGE_SIZE,
                    lastTimestamp = cursor,
                )
                _posts.value = _posts.value + newPosts
                if (newPosts.isNotEmpty()) {
                    postsLastTimestamp = newPosts.last().timestamp.time
                }
                _hasMore.value = newPosts.size >= PAGE_SIZE

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
                engagementManager.checkLikeStatuses(newPosts.map { it.id }, viewerId)
            } catch (_: Exception) { }
            _isLoadingMore.value = false
        }
    }

    fun toggleFollow(userId: String) {
        val currentUserId = authRepository.currentUserId ?: return
        viewModelScope.launch {
            _isFollowLoading.value = true
            try {
                if (_isFollowing.value) {
                    userRepository.unfollowUser(currentUserId, userId)
                    _isFollowing.value = false
                    _profile.value = _profile.value?.copy(
                        followerCount = maxOf(0, (_profile.value?.followerCount ?: 1) - 1)
                    )
                } else {
                    userRepository.followUser(currentUserId, userId)
                    _isFollowing.value = true
                    _profile.value = _profile.value?.copy(
                        followerCount = (_profile.value?.followerCount ?: 0) + 1
                    )
                }
            } catch (_: Exception) { }
            _isFollowLoading.value = false
        }
    }

    fun blockUser(userId: String) {
        val currentUserId = authRepository.currentUserId ?: return
        viewModelScope.launch {
            try {
                userRepository.blockUser(currentUserId, userId)
                _isBlocked.value = true
            } catch (_: Exception) { }
        }
    }

    fun unblockUser(userId: String) {
        val currentUserId = authRepository.currentUserId ?: return
        viewModelScope.launch {
            try {
                userRepository.unblockUser(currentUserId, userId)
                _isBlocked.value = false
            } catch (_: Exception) { }
        }
    }

    suspend fun fetchUserIdByUsername(username: String): String? {
        return try {
            userRepository.fetchUserByUsername(username)?.id
        } catch (_: Exception) { null }
    }

    fun togglePostNotifications(userId: String) {
        val currentUserId = authRepository.currentUserId ?: return
        val wasSubscribed = _isSubscribedToNotifications.value
        _isSubscribedToNotifications.value = !wasSubscribed
        viewModelScope.launch {
            try {
                if (wasSubscribed) {
                    userRepository.unsubscribeFromUserPosts(currentUserId, userId)
                } else {
                    userRepository.subscribeToUserPosts(currentUserId, userId)
                }
            } catch (_: Exception) {
                _isSubscribedToNotifications.value = wasSubscribed
            }
        }
    }

    fun toggleMute(userId: String) {
        val currentUserId = authRepository.currentUserId ?: return
        val wasMuted = _isMuted.value
        _isMuted.value = !wasMuted
        viewModelScope.launch {
            try {
                if (!wasMuted) {
                    userRepository.muteUser(currentUserId, userId)
                } else {
                    userRepository.unmuteUser(currentUserId, userId)
                }
            } catch (_: Exception) {
                _isMuted.value = wasMuted
            }
        }
    }
}
