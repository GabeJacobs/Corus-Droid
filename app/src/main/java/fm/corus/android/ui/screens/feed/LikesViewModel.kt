package fm.corus.android.ui.screens.feed

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import fm.corus.android.data.model.CymbalUser
import fm.corus.android.data.repository.AuthRepository
import fm.corus.android.data.repository.PostRepository
import fm.corus.android.data.repository.UserRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class LikesViewModel @Inject constructor(
    private val postRepository: PostRepository,
    private val userRepository: UserRepository,
    private val authRepository: AuthRepository,
) : ViewModel() {

    private val _likers = MutableStateFlow<List<CymbalUser>>(emptyList())
    val likers: StateFlow<List<CymbalUser>> = _likers.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _isLoadingMore = MutableStateFlow(false)
    val isLoadingMore: StateFlow<Boolean> = _isLoadingMore.asStateFlow()

    private val _followingIds = MutableStateFlow<Set<String>>(emptySet())
    val followingIds: StateFlow<Set<String>> = _followingIds.asStateFlow()

    private val _followerIds = MutableStateFlow<Set<String>>(emptySet())
    val followerIds: StateFlow<Set<String>> = _followerIds.asStateFlow()

    val currentUserId: String? get() = authRepository.currentUserId
    val currentUserProfile = authRepository.userProfile

    private var postId: String = ""
    private var lastTimestamp: Long? = null
    private var hasMore = true
    private val pageSize = 20

    fun loadLikers(postId: String) {
        this.postId = postId
        viewModelScope.launch {
            _isLoading.value = true

            // Pre-populate from cached following set
            _followingIds.value = userRepository.followingIds.value

            loadNextPage(reset = true)
            _isLoading.value = false
        }
    }

    fun loadNextPageIfNeeded() {
        if (_isLoadingMore.value || !hasMore) return
        viewModelScope.launch {
            loadNextPage(reset = false)
        }
    }

    private suspend fun loadNextPage(reset: Boolean) {
        val uid = authRepository.currentUserId ?: return
        if (_isLoadingMore.value) return

        _isLoadingMore.value = true

        if (reset) {
            hasMore = true
            lastTimestamp = null
        }

        try {
            val page = postRepository.fetchPostLikers(
                postId = postId,
                limit = pageSize,
                lastTimestamp = if (reset) null else lastTimestamp,
            )

            val visibleUsers = page.users.filter { !userRepository.isUserHidden(it.id) }
            if (reset) {
                _likers.value = visibleUsers
            } else {
                // Merge unique
                val existing = _likers.value.map { it.id }.toSet()
                val newUsers = visibleUsers.filter { it.id !in existing }
                _likers.value = _likers.value + newUsers
            }

            lastTimestamp = page.lastTimestamp
            hasMore = page.hasMore

            // Check follower status for "Follow back" labels
            val pageUserIds = page.users.map { it.id }.filter { it != uid }
            if (pageUserIds.isNotEmpty()) {
                // Check which of these users follow the current user
                val currentFollowerIds = _followerIds.value.toMutableSet()
                for (userId in pageUserIds) {
                    try {
                        val followers = userRepository.fetchFollowerIds(uid)
                        currentFollowerIds.addAll(followers.intersect(pageUserIds.toSet()))
                        break // Only need to fetch once
                    } catch (_: Exception) { }
                }
                _followerIds.value = currentFollowerIds
            }
        } catch (_: Exception) {
            hasMore = false
        }

        _isLoadingMore.value = false
    }

    fun toggleFollow(targetUserId: String) {
        val myId = authRepository.currentUserId ?: return
        val isCurrentlyFollowing = _followingIds.value.contains(targetUserId)

        // Optimistic update
        if (isCurrentlyFollowing) {
            _followingIds.value = _followingIds.value - targetUserId
        } else {
            _followingIds.value = _followingIds.value + targetUserId
        }

        viewModelScope.launch {
            try {
                if (isCurrentlyFollowing) {
                    userRepository.unfollowUser(myId, targetUserId)
                } else {
                    userRepository.followUser(myId, targetUserId)
                }
            } catch (_: Exception) {
                // Revert
                if (isCurrentlyFollowing) {
                    _followingIds.value = _followingIds.value + targetUserId
                } else {
                    _followingIds.value = _followingIds.value - targetUserId
                }
            }
        }
    }
}
