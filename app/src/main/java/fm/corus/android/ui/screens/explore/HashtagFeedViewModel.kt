package fm.corus.android.ui.screens.explore

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import fm.corus.android.data.model.CymbalPost
import fm.corus.android.data.remote.FirestoreDataSource
import fm.corus.android.data.repository.AuthRepository
import fm.corus.android.data.repository.PostRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class HashtagFeedViewModel @Inject constructor(
    private val postRepository: PostRepository,
    private val authRepository: AuthRepository,
    private val firestoreDataSource: FirestoreDataSource,
) : ViewModel() {

    private val _posts = MutableStateFlow<List<CymbalPost>>(emptyList())
    val posts: StateFlow<List<CymbalPost>> = _posts.asStateFlow()

    private val _totalCount = MutableStateFlow(0)
    val totalCount: StateFlow<Int> = _totalCount.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _hasMore = MutableStateFlow(true)
    val hasMore: StateFlow<Boolean> = _hasMore.asStateFlow()

    private val _loadError = MutableStateFlow<String?>(null)
    val loadError: StateFlow<String?> = _loadError.asStateFlow()

    private val _isFollowing = MutableStateFlow(false)
    val isFollowing: StateFlow<Boolean> = _isFollowing.asStateFlow()

    private val _hasLoadedFollowState = MutableStateFlow(false)
    val hasLoadedFollowState: StateFlow<Boolean> = _hasLoadedFollowState.asStateFlow()

    private val _isTogglingFollow = MutableStateFlow(false)
    val isTogglingFollow: StateFlow<Boolean> = _isTogglingFollow.asStateFlow()

    private var lastTimestamp: Long? = null
    private var currentHashtag: String? = null
    private var hasInitiatedFollowLoad = false

    fun loadHashtagPosts(hashtag: String, refresh: Boolean = false) {
        authRepository.currentUserId ?: return
        currentHashtag = hashtag
        viewModelScope.launch {
            if (_isLoading.value) return@launch
            _isLoading.value = true
            _loadError.value = null
            if (refresh) lastTimestamp = null
            try {
                val page = postRepository.getHashtagPosts(
                    hashtag = hashtag,
                    pageSize = 15,
                    beforeMs = if (refresh) null else lastTimestamp,
                )
                if (refresh) {
                    _posts.value = page.posts
                } else {
                    _posts.value = _posts.value + page.posts
                }
                _totalCount.value = page.totalCount
                _hasMore.value = page.hasMore
                if (page.posts.isNotEmpty()) lastTimestamp = page.posts.last().timestamp.time
            } catch (_: Exception) {
                _loadError.value = "Couldn't load posts"
            }
            _isLoading.value = false
        }
    }

    fun retry() {
        _loadError.value = null
        currentHashtag?.let { loadHashtagPosts(it, refresh = true) }
    }

    fun loadFollowState(hashtag: String) {
        if (hasInitiatedFollowLoad) return
        hasInitiatedFollowLoad = true
        val uid = authRepository.currentUserId
        if (uid == null) {
            _hasLoadedFollowState.value = true
            return
        }
        viewModelScope.launch {
            try {
                _isFollowing.value = firestoreDataSource.isHashtagFollowed(uid, hashtag)
            } catch (_: Exception) { }
            _hasLoadedFollowState.value = true
        }
    }

    fun toggleFollow(hashtag: String) {
        val uid = authRepository.currentUserId ?: return
        if (_isTogglingFollow.value) return
        viewModelScope.launch {
            _isTogglingFollow.value = true
            val wasFollowing = _isFollowing.value
            _isFollowing.value = !wasFollowing
            try {
                if (wasFollowing) {
                    firestoreDataSource.unfollowHashtag(uid, hashtag)
                } else {
                    firestoreDataSource.followHashtag(uid, hashtag)
                }
            } catch (_: Exception) {
                _isFollowing.value = wasFollowing
            }
            _isTogglingFollow.value = false
        }
    }
}
