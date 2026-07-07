package fm.corus.android.ui.screens.destination

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
 * "Who shared {name}" — the artist/director posts see-all. A full paginated
 * feed of ALL posts (deliberately NOT deduped by user, unlike the song page)
 * with a beforeMs cursor, mirroring the web people pages.
 */
@HiltViewModel
class DestinationPostsViewModel @Inject constructor(
    private val cloudFunctions: CloudFunctionsDataSource,
) : ViewModel() {

    enum class Kind { ARTIST, DIRECTOR }

    private val _posts = MutableStateFlow<List<CymbalPost>>(emptyList())
    val posts: StateFlow<List<CymbalPost>> = _posts.asStateFlow()

    private val _uniquePosterCount = MutableStateFlow(0)
    val uniquePosterCount: StateFlow<Int> = _uniquePosterCount.asStateFlow()

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _isLoadingMore = MutableStateFlow(false)
    val isLoadingMore: StateFlow<Boolean> = _isLoadingMore.asStateFlow()

    private val _hasMore = MutableStateFlow(false)
    val hasMore: StateFlow<Boolean> = _hasMore.asStateFlow()

    private val _loadError = MutableStateFlow(false)
    val loadError: StateFlow<Boolean> = _loadError.asStateFlow()

    private var kind: Kind = Kind.ARTIST
    private var subjectId: String = ""
    private var subjectName: String? = null
    private var paginationCursor: Long? = null
    private var loaded = false

    fun load(kind: Kind, id: String, name: String?) {
        if (loaded && this.kind == kind && this.subjectId == id) return
        loaded = true
        this.kind = kind
        this.subjectId = id
        this.subjectName = name
        viewModelScope.launch {
            _isLoading.value = true
            _loadError.value = false
            try {
                val page = fetchPage(beforeMs = null)
                _posts.value = page.posts
                _uniquePosterCount.value = page.uniquePosterCount
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
                val page = fetchPage(beforeMs = cursor)
                paginationCursor = page.posts.lastOrNull()?.timestamp?.time
                // Full history, not deduped by user — only guard against the
                // same post appearing twice across page boundaries.
                val seenIds = _posts.value.map { it.id }.toSet()
                _posts.value = _posts.value + page.posts.filter { it.id !in seenIds }
                _hasMore.value = page.posts.size >= PAGE_SIZE
            } catch (_: Exception) { }
            _isLoadingMore.value = false
        }
    }

    private suspend fun fetchPage(beforeMs: Long?): CloudFunctionsDataSource.DestinationPostsPage =
        when (kind) {
            Kind.ARTIST -> cloudFunctions.fetchArtistPosts(
                artistId = subjectId,
                artistName = subjectName,
                pageSize = PAGE_SIZE,
                beforeMs = beforeMs,
                postersLimit = 1,
            )
            Kind.DIRECTOR -> cloudFunctions.fetchDirectorPosts(
                directorId = subjectId,
                directorName = subjectName,
                pageSize = PAGE_SIZE,
                beforeMs = beforeMs,
                postersLimit = 1,
            )
        }

    companion object {
        const val PAGE_SIZE = 15
    }
}
