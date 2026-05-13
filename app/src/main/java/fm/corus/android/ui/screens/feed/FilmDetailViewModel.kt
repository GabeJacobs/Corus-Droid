package fm.corus.android.ui.screens.feed

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import fm.corus.android.data.model.CymbalPost
import fm.corus.android.data.repository.PostRepository
import fm.corus.android.domain.CommentEditedEvent
import fm.corus.android.domain.NowPlayingManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class FilmDetailViewModel @Inject constructor(
    private val postRepository: PostRepository,
    val nowPlayingManager: NowPlayingManager,
    private val commentEditedEvent: CommentEditedEvent,
) : ViewModel() {

    private val _posts = MutableStateFlow<List<CymbalPost>>(emptyList())
    val posts: StateFlow<List<CymbalPost>> = _posts.asStateFlow()

    init {
        viewModelScope.launch {
            commentEditedEvent.events.collect { payload ->
                _posts.value = applyCommentEditToPosts(_posts.value, payload)
            }
        }
    }

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _isLoadingMore = MutableStateFlow(false)
    val isLoadingMore: StateFlow<Boolean> = _isLoadingMore.asStateFlow()

    private val _hasMore = MutableStateFlow(true)
    val hasMore: StateFlow<Boolean> = _hasMore.asStateFlow()

    private val _loadError = MutableStateFlow<String?>(null)
    val loadError: StateFlow<String?> = _loadError.asStateFlow()

    private val _uniquePosterCount = MutableStateFlow<Int?>(null)
    val uniquePosterCount: StateFlow<Int?> = _uniquePosterCount.asStateFlow()

    private var firstPosterId: String? = null
    private var paginationCursor: Long? = null
    private val pageSize = 15

    private val _movieHeader = MutableStateFlow<MovieHeaderInfo?>(null)
    val movieHeader: StateFlow<MovieHeaderInfo?> = _movieHeader.asStateFlow()

    private var currentMovieId: String = ""
    private var movieTitle: String? = null

    fun setInitialMovieInfo(info: MovieHeaderInfo) {
        if (_movieHeader.value == null) {
            _movieHeader.value = info
        }
    }

    fun loadMoviePosts(movieId: String, movieTitle: String? = null) {
        this.currentMovieId = movieId
        this.movieTitle = movieTitle
        viewModelScope.launch {
            _loadError.value = null
            _isLoading.value = true
            try {
                val page = postRepository.fetchMoviePostsFromCloud(
                    movieId = movieId,
                    movieTitle = movieTitle,
                    pageSize = pageSize,
                )
                firstPosterId = page.firstPosterId
                _uniquePosterCount.value = page.uniquePosterCount
                paginationCursor = page.posts.lastOrNull()?.timestamp?.time

                val unique = deduplicateByUser(page.posts)
                _posts.value = moveFirstPosterToTop(unique, page.firstPosterId)
                _hasMore.value = page.posts.size >= pageSize

                // Update movie header from fetched post data
                page.posts.firstOrNull()?.let { post ->
                    _movieHeader.value = MovieHeaderInfo(
                        movieTitle = post.movieTitle,
                        directorName = post.directorName,
                        releaseYear = post.releaseYear,
                        posterURL = post.posterURL,
                        posterLargeURL = post.posterLargeURL,
                        trailerURL = post.trailerURL,
                    )
                }
            } catch (e: Exception) {
                _loadError.value = "Couldn't load posts for this film."
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
                val page = postRepository.fetchMoviePostsFromCloud(
                    movieId = currentMovieId,
                    movieTitle = movieTitle,
                    pageSize = pageSize,
                    beforeMs = cursor,
                )
                paginationCursor = page.posts.lastOrNull()?.timestamp?.time

                val existingUserIds = _posts.value.map { it.user.id }.toSet()
                val newPosts = page.posts.filter { it.user.id !in existingUserIds }
                _posts.value = _posts.value + newPosts
                _hasMore.value = page.posts.size >= pageSize
            } catch (_: Exception) { }
            _isLoadingMore.value = false
        }
    }

    private fun deduplicateByUser(posts: List<CymbalPost>): List<CymbalPost> {
        val seen = mutableSetOf<String>()
        return posts.filter { seen.add(it.user.id) }
    }

    private fun moveFirstPosterToTop(posts: List<CymbalPost>, firstPosterId: String?): List<CymbalPost> {
        // Partition: non-bots first, bots last, preserving relative order
        val nonBots = posts.filter { !it.user.isBot }
        val bots = posts.filter { it.user.isBot }
        val sorted = (nonBots + bots).toMutableList()

        if (firstPosterId != null) {
            val idx = sorted.indexOfFirst { it.user.id == firstPosterId }
            if (idx > 0) {
                val first = sorted.removeAt(idx)
                sorted.add(0, first)
            }
        }
        return sorted
    }
}

data class MovieHeaderInfo(
    val movieTitle: String? = null,
    val directorName: String? = null,
    val releaseYear: String? = null,
    val posterURL: String? = null,
    val posterLargeURL: String? = null,
    val trailerURL: String? = null,
)
