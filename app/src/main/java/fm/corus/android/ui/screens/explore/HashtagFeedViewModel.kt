package fm.corus.android.ui.screens.explore

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import fm.corus.android.data.model.CymbalPost
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
) : ViewModel() {

    private val _posts = MutableStateFlow<List<CymbalPost>>(emptyList())
    val posts: StateFlow<List<CymbalPost>> = _posts.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _hasMore = MutableStateFlow(true)
    val hasMore: StateFlow<Boolean> = _hasMore.asStateFlow()

    private val _loadError = MutableStateFlow<String?>(null)
    val loadError: StateFlow<String?> = _loadError.asStateFlow()

    private var lastTimestamp: Long? = null
    private var currentHashtag: String? = null

    fun loadHashtagPosts(hashtag: String, refresh: Boolean = false) {
        val userId = authRepository.currentUserId ?: return
        currentHashtag = hashtag
        viewModelScope.launch {
            if (_isLoading.value) return@launch
            _isLoading.value = true
            _loadError.value = null
            if (refresh) lastTimestamp = null
            try {
                val newPosts = postRepository.getHashtagPosts(
                    hashtag = hashtag,
                    userId = userId,
                    limit = 15,
                    lastTimestamp = if (refresh) null else lastTimestamp,
                )
                if (refresh) {
                    _posts.value = newPosts
                } else {
                    _posts.value = _posts.value + newPosts
                }
                _hasMore.value = newPosts.size >= 15
                if (newPosts.isNotEmpty()) lastTimestamp = newPosts.last().timestamp.time
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
}
