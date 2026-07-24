package fm.corus.android.ui.screens.feed

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import fm.corus.android.R
import fm.corus.android.data.model.CymbalMovie
import fm.corus.android.data.model.CymbalPost
import fm.corus.android.data.model.CymbalUser
import fm.corus.android.data.repository.AuthRepository
import fm.corus.android.data.repository.MessageRepository
import fm.corus.android.data.repository.PostRepository
import fm.corus.android.data.repository.UserRepository
import fm.corus.android.domain.CommentDeletedEvent
import fm.corus.android.domain.CommentEditedEvent
import fm.corus.android.domain.NowPlayingManager
import fm.corus.android.service.AnalyticsService
import fm.corus.android.ui.components.ToastManager
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class FilmDetailViewModel @Inject constructor(
    private val postRepository: PostRepository,
    val nowPlayingManager: NowPlayingManager,
    val analyticsService: AnalyticsService,
    private val commentEditedEvent: CommentEditedEvent,
    private val commentDeletedEvent: CommentDeletedEvent,
    private val authRepository: AuthRepository,
    private val userRepository: UserRepository,
    private val messageRepository: MessageRepository,
    private val cloudFunctions: fm.corus.android.data.remote.CloudFunctionsDataSource,
    private val remoteConfigService: fm.corus.android.service.RemoteConfigService,
    @ApplicationContext private val context: Context,
) : ViewModel() {

    /** Prototype gate for the immersive (blurred-poster hero + frosted collapsing
     *  bar) film header. Shares the artist header's debug-on / RC-gated flag. */
    val immersiveHeaderEnabled: Boolean
        get() = remoteConfigService.immersiveArtistHeaderEnabled

    private val _posts = MutableStateFlow<List<CymbalPost>>(emptyList())
    val posts: StateFlow<List<CymbalPost>> = _posts.asStateFlow()

    // ── On-tap director resolution (artist_pages_enabled) ──
    // "Go to Director" always shows; when the seed post lacks directorIds,
    // resolveDirectors looks them up via resolveFilmDirectors (server-cached by
    // movieId) and caches the id here for instant repeat taps. Mirrors
    // SongDetailViewModel.resolveDestinations.
    private val _resolvedDirectorId = MutableStateFlow<String?>(null)
    val resolvedDirectorId: StateFlow<String?> = _resolvedDirectorId.asStateFlow()

    private val _isResolvingDestination = MutableStateFlow(false)
    val isResolvingDestination: StateFlow<Boolean> = _isResolvingDestination.asStateFlow()

    /** Resolve the film's TMDB director ids on demand (server-cached), cache the
     *  first id, and return the list so the caller can navigate immediately
     *  (state emission is async). Empty on a miss. */
    suspend fun resolveDirectors(movieId: String): List<String> {
        _isResolvingDestination.value = true
        val ids = cloudFunctions.resolveFilmDirectors(movieId)
        _isResolvingDestination.value = false
        ids.firstOrNull()?.let { _resolvedDirectorId.value = it }
        return ids
    }

    init {
        viewModelScope.launch {
            commentEditedEvent.events.collect { payload ->
                _posts.value = applyCommentEditToPosts(_posts.value, payload)
            }
        }
        viewModelScope.launch {
            commentDeletedEvent.events.collect { payload ->
                _posts.value = applyCommentDeleteToPosts(_posts.value, payload)
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

    // ── Share-a-film state + actions (mirrors SongDetailViewModel's track share;
    // DMs a `sharedFilm` message deep-linking to the film page in-app). ──

    private val _shareSearchResults = MutableStateFlow<List<CymbalUser>>(emptyList())
    val shareSearchResults: StateFlow<List<CymbalUser>> = _shareSearchResults.asStateFlow()

    private val _recentShareContacts = MutableStateFlow<List<CymbalUser>>(emptyList())
    val recentShareContacts: StateFlow<List<CymbalUser>> = _recentShareContacts.asStateFlow()

    private val _isShareSearching = MutableStateFlow(false)
    val isShareSearching: StateFlow<Boolean> = _isShareSearching.asStateFlow()

    private val _isLoadingShareContacts = MutableStateFlow(true)
    val isLoadingShareContacts: StateFlow<Boolean> = _isLoadingShareContacts.asStateFlow()

    private var shareSearchJob: Job? = null

    fun loadRecentShareContacts() {
        val userId = authRepository.currentUserId ?: return
        _isLoadingShareContacts.value = true
        viewModelScope.launch {
            try {
                val shareRecipients = runCatching { messageRepository.listShareRecipients(12) }.getOrDefault(emptyList())
                val threadContacts = messageRepository.listThreads(userId).mapNotNull { it.otherUser }

                val followFallback = if (rankShareContacts(shareRecipients, threadContacts, emptyList()).size < SHARE_CONTACTS_TARGET) {
                    val following = userRepository.fetchFollowingPaginated(userId, limit = 20).users
                    val followers = userRepository.fetchFollowersPaginated(userId, limit = 20).users
                    following + followers
                } else {
                    emptyList()
                }

                _recentShareContacts.value = rankShareContacts(shareRecipients, threadContacts, followFallback)
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
                _shareSearchResults.value = userRepository.searchUsers(trimmed, includeFollowed = true)
            } catch (_: Exception) {
                _shareSearchResults.value = emptyList()
            }
            _isShareSearching.value = false
        }
    }

    fun sendFilmToUser(userId: String, movie: CymbalMovie, message: String) {
        val currentUserId = authRepository.currentUserId ?: return
        viewModelScope.launch {
            try {
                val threadId = messageRepository.getOrCreateThread(currentUserId, userId)
                messageRepository.sendSharedFilmMessage(
                    threadId = threadId,
                    fromUserId = currentUserId,
                    text = message.trim(),
                    movie = movie,
                )
            } catch (_: Exception) {
                ToastManager.show(context.getString(R.string.feed_toast_failed_send_post))
            }
        }
    }

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

                // Fill header gaps from the first post WITHOUT displacing the
                // identity the caller (search/catalog row) already seeded — a
                // poster the poster snapshotted can differ from the tapped row's
                // current art. Posts only supply fields the route lacked.
                page.posts.firstOrNull()?.let { post ->
                    _movieHeader.value = mergeMovieHeader(
                        existing = _movieHeader.value,
                        fromPost = MovieHeaderInfo(
                            movieTitle = post.movieTitle,
                            directorName = post.directorName,
                            releaseYear = post.releaseYear,
                            posterURL = post.posterURL,
                            posterLargeURL = post.posterLargeURL,
                            trailerURL = post.trailerURL,
                        ),
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

/**
 * Merge the first post's header snapshot into the header the caller already
 * seeded from the tapped row. The seed's identity — poster, title, director,
 * year, trailer — wins field-by-field; the post only fills fields the route
 * left blank (deep links / notifications arrive with no seed, so the whole
 * post-derived header is taken). Prevents the film-page poster visibly
 * swapping to whatever art the first poster happened to snapshot, matching iOS
 * (whose header renders the immutable seed movie) and the song page.
 */
internal fun mergeMovieHeader(existing: MovieHeaderInfo?, fromPost: MovieHeaderInfo): MovieHeaderInfo {
    if (existing == null) return fromPost
    fun keep(seed: String?, post: String?) = seed?.takeIf { it.isNotBlank() } ?: post
    return MovieHeaderInfo(
        movieTitle = keep(existing.movieTitle, fromPost.movieTitle),
        directorName = keep(existing.directorName, fromPost.directorName),
        releaseYear = keep(existing.releaseYear, fromPost.releaseYear),
        posterURL = keep(existing.posterURL, fromPost.posterURL),
        posterLargeURL = keep(existing.posterLargeURL, fromPost.posterLargeURL),
        trailerURL = keep(existing.trailerURL, fromPost.trailerURL),
    )
}
