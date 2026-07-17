package fm.corus.android.ui.screens.feed

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import fm.corus.android.data.model.CymbalPost
import fm.corus.android.data.remote.CloudFunctionsDataSource
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * "Reposts" — the people who reposted a post, each row their own repost of it.
 * A repost is a real post (carrying repostedFromPostId), so this is a full
 * paginated feed of those reposting posts with a beforeMs cursor, mirroring
 * [DestinationPostsViewModel]. `totalCount` is the live total repost count for
 * the header.
 */
@HiltViewModel
class RepostersViewModel @Inject constructor(
    private val cloudFunctions: CloudFunctionsDataSource,
) : ViewModel() {

    private val _posts = MutableStateFlow<List<CymbalPost>>(emptyList())
    val posts: StateFlow<List<CymbalPost>> = _posts.asStateFlow()

    private val _totalCount = MutableStateFlow(0)
    val totalCount: StateFlow<Int> = _totalCount.asStateFlow()

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _isLoadingMore = MutableStateFlow(false)
    val isLoadingMore: StateFlow<Boolean> = _isLoadingMore.asStateFlow()

    private val _hasMore = MutableStateFlow(false)
    val hasMore: StateFlow<Boolean> = _hasMore.asStateFlow()

    private val _loadError = MutableStateFlow(false)
    val loadError: StateFlow<Boolean> = _loadError.asStateFlow()

    private var postId: String = ""
    private var paginationCursor: Long? = null
    private var loaded = false

    fun load(postId: String) {
        if (loaded && this.postId == postId) return
        loaded = true
        this.postId = postId
        viewModelScope.launch {
            _isLoading.value = true
            _loadError.value = false
            try {
                val page = cloudFunctions.fetchReposters(postId = postId, pageSize = PAGE_SIZE)
                _posts.value = page.posts
                _totalCount.value = page.uniquePosterCount
                paginationCursor = page.posts.lastOrNull()?.timestamp?.time
                _hasMore.value = page.posts.size >= PAGE_SIZE
            } catch (_: Exception) {
                _loadError.value = true
            }
            _isLoading.value = false
        }
    }

    fun loadMore() {
        val cursor = paginationCursor ?: return
        if (_isLoadingMore.value || !_hasMore.value) return
        viewModelScope.launch {
            _isLoadingMore.value = true
            try {
                val page = cloudFunctions.fetchReposters(
                    postId = postId,
                    pageSize = PAGE_SIZE,
                    beforeMs = cursor,
                )
                paginationCursor = page.posts.lastOrNull()?.timestamp?.time
                // Guard against the same repost appearing twice across page
                // boundaries.
                val seenIds = _posts.value.map { it.id }.toSet()
                _posts.value = _posts.value + page.posts.filter { it.id !in seenIds }
                _hasMore.value = page.posts.size >= PAGE_SIZE
            } catch (_: Exception) { }
            _isLoadingMore.value = false
        }
    }

    companion object {
        const val PAGE_SIZE = 20
    }
}
