package fm.corus.android.ui.screens.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import fm.corus.android.data.model.CymbalPost
import fm.corus.android.data.model.CymbalUser
import fm.corus.android.data.model.MediaType
import fm.corus.android.data.remote.CloudFunctionsDataSource
import fm.corus.android.data.repository.AuthRepository
import fm.corus.android.data.repository.SubscriptionRepository
import fm.corus.android.data.repository.UserRepository
import fm.corus.android.domain.NowPlayingManager
import fm.corus.android.domain.PostCreationEvent
import fm.corus.android.domain.PostDeletionEvent
import fm.corus.android.domain.PostEngagementManager
import fm.corus.android.service.AnalyticsService
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val cloudFunctions: CloudFunctionsDataSource,
    private val userRepository: UserRepository,
    private val subscriptionRepository: SubscriptionRepository,
    val nowPlayingManager: NowPlayingManager,
    val musicServicePreference: fm.corus.android.domain.MusicServicePreference,
    private val engagementManager: PostEngagementManager,
    private val postCreationEvent: PostCreationEvent,
    private val postDeletionEvent: PostDeletionEvent,
    private val analyticsService: AnalyticsService,
) : ViewModel() {

    val isClubMember = subscriptionRepository.isClubMember
    val hasFullAccess = subscriptionRepository.hasFullAccessFlow

    val engagementStates = engagementManager.states

    init {
        // Auto-refresh profile when a new post is created
        viewModelScope.launch {
            postCreationEvent.events.collect {
                delay(500) // brief delay for Firestore propagation
                refreshProfile()
            }
        }
        // Drop deleted posts from any visible list so returning from
        // PostDetail reflects the deletion immediately (matches iOS).
        viewModelScope.launch {
            postDeletionEvent.events.collect { deletedId ->
                _posts.value = _posts.value.filter { it.id != deletedId }
                _likedPosts.value = _likedPosts.value.filter { it.id != deletedId }
                _savedPosts.value = _savedPosts.value.filter { it.id != deletedId }
            }
        }
    }

    fun toggleLike(postId: String) {
        val userId = authRepository.currentUserId ?: return
        engagementManager.toggleLike(postId, userId)
    }

    fun uploadAvatar(imageData: ByteArray) {
        val uid = authRepository.currentUserId ?: return
        _pendingAvatarBytes.value = imageData
        viewModelScope.launch {
            try {
                userRepository.uploadAvatar(uid, imageData)
                authRepository.refreshUserProfile()
                _profile.value = authRepository.userProfile.value
                analyticsService.logAvatarChanged()
            } catch (e: Exception) {
                android.util.Log.e("ProfileViewModel", "uploadAvatar failed", e)
            } finally {
                _pendingAvatarBytes.value = null
            }
        }
    }

    fun generatePlaylist() {
        val userId = authRepository.currentUserId ?: return
        analyticsService.logProfilePlaylistTapped(userId)
        viewModelScope.launch {
            nowPlayingManager.generateProfilePlaylist(userId)
        }
    }

    private val _profile = MutableStateFlow<CymbalUser?>(null)
    val profile: StateFlow<CymbalUser?> = _profile.asStateFlow()

    // Optimistic avatar preview: set at the start of uploadAvatar and cleared
    // once the server round-trip completes, so the new avatar appears immediately
    // without waiting for Firestore + Storage + cache-busted URL reload.
    private val _pendingAvatarBytes = MutableStateFlow<ByteArray?>(null)
    val pendingAvatarBytes: StateFlow<ByteArray?> = _pendingAvatarBytes.asStateFlow()

    // Profile posts (tracks + movies together, shared by MUSIC and FILM tabs)
    private val _posts = MutableStateFlow<List<CymbalPost>>(emptyList())
    val posts: StateFlow<List<CymbalPost>> = _posts.asStateFlow()

    // Liked & saved posts are separate lists, loaded lazily
    private val _likedPosts = MutableStateFlow<List<CymbalPost>>(emptyList())
    val likedPosts: StateFlow<List<CymbalPost>> = _likedPosts.asStateFlow()

    private val _savedPosts = MutableStateFlow<List<CymbalPost>>(emptyList())
    val savedPosts: StateFlow<List<CymbalPost>> = _savedPosts.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    private val _isLoadingMore = MutableStateFlow(false)
    val isLoadingMore: StateFlow<Boolean> = _isLoadingMore.asStateFlow()

    private val _isLoadingLiked = MutableStateFlow(false)
    val isLoadingLiked: StateFlow<Boolean> = _isLoadingLiked.asStateFlow()

    private val _isLoadingSaved = MutableStateFlow(false)
    val isLoadingSaved: StateFlow<Boolean> = _isLoadingSaved.asStateFlow()

    private val _isLoadingFilms = MutableStateFlow(false)
    val isLoadingFilms: StateFlow<Boolean> = _isLoadingFilms.asStateFlow()

    private val _hasFetchedFilmPage = MutableStateFlow(false)
    val hasFetchedFilmPage: StateFlow<Boolean> = _hasFetchedFilmPage.asStateFlow()

    private val _isSavingStyle = MutableStateFlow(false)
    val isSavingStyle: StateFlow<Boolean> = _isSavingStyle.asStateFlow()

    private val _currentSegment = MutableStateFlow(0)

    private val _hasMore = MutableStateFlow(mapOf(0 to true, 1 to true, 2 to true, 3 to true))
    val hasMore: StateFlow<Map<Int, Boolean>> = _hasMore.asStateFlow()

    private var postsLastTimestamp: Long? = null
    private var likedOffset: Int = 0
    private var savedOffset: Int = 0

    private var likedLoaded = false
    private var savedLoaded = false

    private var segmentLoadJob: Job? = null

    // Guards against redundant fetches when the composable re-enters composition
    // after forward-then-back navigation (e.g. profile → profile feed → back).
    // Reset by refreshProfile() so pull-to-refresh still fetches.
    private var hasLoaded = false

    private val PAGE_SIZE = 30
    fun loadProfile() {
        if (hasLoaded) return
        val userId = authRepository.currentUserId ?: return
        hasLoaded = true
        viewModelScope.launch {
            _isLoading.value = true
            try {
                authRepository.refreshUserProfile()
                _profile.value = authRepository.userProfile.value
                // Load initial page of posts (matching iOS fixed-page approach).
                // Users load more on demand via loadMorePosts().
                val page = cloudFunctions.getProfilePosts(userId, userId, limit = PAGE_SIZE, lastTimestamp = null)
                _posts.value = page
                if (page.isNotEmpty()) postsLastTimestamp = page.last().timestamp.time
                val serverHasMore = page.size >= PAGE_SIZE
                _hasMore.value = _hasMore.value.toMutableMap().apply {
                    this[0] = serverHasMore
                    this[1] = serverHasMore
                }
                page.forEach { post ->
                    engagementManager.initState(
                        postId = post.id,
                        likeCount = post.likeCount,
                        commentCount = post.commentCount,
                        repostCount = post.repostCount,
                        isLiked = post.isLiked,
                        isSaved = false,
                    )
                }
                engagementManager.checkLikeStatuses(page.map { it.id }, userId)
            } catch (e: Exception) {
                android.util.Log.e("ProfileViewModel", "loadProfile failed", e)
            }
            _isLoading.value = false
        }
    }

    fun refreshProfile() {
        hasLoaded = true
        // Only reset the film-fetch flag if there's actually something to re-fetch.
        // The counter (when available) is authoritative; fall back to the free
        // guard for older backends without the field.
        val knownZeroFilms = _profile.value?.movieCount == 0
        val certainlyZeroFilms = knownZeroFilms ||
            (!(_hasMore.value[0] ?: true) && _posts.value.none { it.mediaType == MediaType.MOVIE })
        if (!certainlyZeroFilms) {
            _hasFetchedFilmPage.value = false
        }
        viewModelScope.launch {
            _isLoading.value = true
            _isRefreshing.value = true
            val onFilmsTab = _currentSegment.value == 1
            try {
                authRepository.refreshUserProfile()
                _profile.value = authRepository.userProfile.value
                val userId = authRepository.currentUserId ?: return@launch
                val page = cloudFunctions.getProfilePosts(userId, userId, limit = PAGE_SIZE, lastTimestamp = null)
                val movieSupplement = if (onFilmsTab) {
                    runCatching {
                        cloudFunctions.getProfilePosts(userId, userId, limit = PAGE_SIZE, lastTimestamp = null, mediaType = "movie")
                    }.getOrDefault(emptyList())
                } else emptyList()
                val merged = page + movieSupplement.filter { m -> page.none { it.id == m.id } }
                _posts.value = merged
                if (page.isNotEmpty()) postsLastTimestamp = page.last().timestamp.time
                if (onFilmsTab) _hasFetchedFilmPage.value = true
                val serverHasMore = page.size >= PAGE_SIZE
                _hasMore.value = _hasMore.value.toMutableMap().apply {
                    this[0] = serverHasMore
                    this[1] = serverHasMore
                }
                merged.forEach { post ->
                    engagementManager.initState(
                        postId = post.id,
                        likeCount = post.likeCount,
                        commentCount = post.commentCount,
                        repostCount = post.repostCount,
                        isLiked = post.isLiked,
                        isSaved = false,
                    )
                }
                engagementManager.checkLikeStatuses(merged.map { it.id }, userId)
                // Reset lazy-loaded segments so they reload on next visit
                likedLoaded = false
                savedLoaded = false
                _likedPosts.value = emptyList()
                _savedPosts.value = emptyList()
                _hasMore.value = _hasMore.value.toMutableMap().apply {
                    this[2] = true
                    this[3] = true
                }
                likedOffset = 0
                savedOffset = 0
            } catch (e: Exception) {
                android.util.Log.e("ProfileViewModel", "refreshProfile failed", e)
            }
            _isLoading.value = false
            _isRefreshing.value = false
        }
    }

    fun saveStyleSelections(fields: Map<String, Any>) {
        val uid = authRepository.currentUserId ?: return
        _isSavingStyle.value = true
        viewModelScope.launch {
            try {
                userRepository.updateUserProfile(uid, fields)
                authRepository.refreshUserProfile()
                _profile.value = authRepository.userProfile.value
            } catch (e: Exception) {
                android.util.Log.e("ProfileViewModel", "saveStyleSelections failed", e)
            }
            _isSavingStyle.value = false
        }
    }

    /**
     * Called when the user taps a segment tab.
     * Segments 0 (MUSIC) shares posts already loaded; FILM (1) may need a
     * movie-only supplementary fetch if the recency-sorted initial page didn't
     * include films.
     * Segments 2 (LIKES) and 3 (SAVES) lazy-load on first visit.
     */
    fun loadSegment(index: Int) {
        _currentSegment.value = index
        analyticsService.logProfileSegmentChanged(segmentAnalyticsName(index))
        when (index) {
            0 -> { /* Posts already loaded in loadProfile */ }
            1 -> loadFilmPageIfNeeded()
            2 -> if (!likedLoaded) loadLikedPosts()
            3 -> if (!savedLoaded) loadSavedPosts()
        }
    }

    fun loadFilmPageIfNeeded() {
        if (_hasFetchedFilmPage.value) return
        val userId = authRepository.currentUserId ?: return
        val hasAnyMovies = _posts.value.any { it.mediaType == MediaType.MOVIE }
        val hasMoreMixed = _hasMore.value[0] ?: true
        // Prefer the counter (authoritative); fall back to the "all posts loaded,
        // none are films" free guard for older backends without the field.
        val knownZeroFilms = _profile.value?.movieCount == 0
        if (knownZeroFilms || (!hasMoreMixed && !hasAnyMovies)) {
            _hasFetchedFilmPage.value = true
            return
        }
        _hasFetchedFilmPage.value = true
        if (!hasAnyMovies) _isLoadingFilms.value = true
        viewModelScope.launch {
            try {
                val movies = cloudFunctions.getProfilePosts(
                    userId, userId,
                    limit = PAGE_SIZE,
                    lastTimestamp = null,
                    mediaType = "movie",
                )
                val existing = _posts.value
                val additions = movies.filter { m -> existing.none { it.id == m.id } }
                if (additions.isNotEmpty()) {
                    _posts.value = existing + additions
                    additions.forEach { post ->
                        engagementManager.initState(
                            postId = post.id,
                            likeCount = post.likeCount,
                            commentCount = post.commentCount,
                            repostCount = post.repostCount,
                            isLiked = post.isLiked,
                            isSaved = false,
                        )
                    }
                    engagementManager.checkLikeStatuses(additions.map { it.id }, userId)
                }
            } catch (e: Exception) {
                android.util.Log.e("ProfileViewModel", "loadFilmPageIfNeeded failed", e)
                _hasFetchedFilmPage.value = false
            }
            _isLoadingFilms.value = false
        }
    }

    private fun segmentAnalyticsName(index: Int): String = when (index) {
        0 -> "music"
        1 -> "film"
        2 -> "likes"
        3 -> "saves"
        else -> "unknown"
    }

    private fun loadLikedPosts() {
        val userId = authRepository.currentUserId ?: return
        segmentLoadJob?.cancel()
        _isLoadingLiked.value = true
        segmentLoadJob = viewModelScope.launch {
            try {
                val posts = cloudFunctions.getLikedPosts(userId, userId, limit = PAGE_SIZE, offset = 0)
                _likedPosts.value = posts
                likedLoaded = true
                likedOffset = posts.size
                _hasMore.value = _hasMore.value.toMutableMap().apply {
                    this[2] = posts.size >= PAGE_SIZE
                }
            } catch (e: Exception) {
                android.util.Log.e("ProfileViewModel", "loadLikedPosts failed", e)
            }
            _isLoadingLiked.value = false
        }
    }

    private fun loadSavedPosts() {
        val userId = authRepository.currentUserId ?: return
        segmentLoadJob?.cancel()
        _isLoadingSaved.value = true
        segmentLoadJob = viewModelScope.launch {
            try {
                val posts = cloudFunctions.getSavedPosts(userId, limit = PAGE_SIZE, offset = 0)
                _savedPosts.value = posts
                savedLoaded = true
                savedOffset = posts.size
                _hasMore.value = _hasMore.value.toMutableMap().apply {
                    this[3] = posts.size >= PAGE_SIZE
                }
            } catch (e: Exception) {
                android.util.Log.e("ProfileViewModel", "loadSavedPosts failed", e)
            }
            _isLoadingSaved.value = false
        }
    }

    fun loadMoreForSegment(segment: Int) {
        if (_hasMore.value[segment] != true) return
        if (_isLoadingMore.value || _isLoading.value) return
        val userId = authRepository.currentUserId ?: return

        _isLoadingMore.value = true
        viewModelScope.launch {
            try {
                when (segment) {
                    0, 1 -> {
                        val cursor = postsLastTimestamp
                        if (cursor != null) {
                            val newPosts = cloudFunctions.getProfilePosts(userId, userId, limit = PAGE_SIZE, lastTimestamp = cursor)
                            val existingIds = _posts.value.mapTo(HashSet()) { it.id }
                            val unique = newPosts.filter { it.id !in existingIds }
                            _posts.value = _posts.value + unique
                            if (newPosts.isNotEmpty()) {
                                postsLastTimestamp = newPosts.last().timestamp.time
                            }
                            _hasMore.value = _hasMore.value.toMutableMap().apply {
                                this[0] = newPosts.size >= PAGE_SIZE
                                this[1] = newPosts.size >= PAGE_SIZE
                            }
                        } else {
                            _hasMore.value = _hasMore.value.toMutableMap().apply {
                                this[0] = false
                                this[1] = false
                            }
                        }
                    }
                    2 -> {
                        val newPosts = cloudFunctions.getLikedPosts(userId, userId, limit = PAGE_SIZE, offset = likedOffset)
                        val existingIds = _likedPosts.value.mapTo(HashSet()) { it.id }
                        val unique = newPosts.filter { it.id !in existingIds }
                        _likedPosts.value = _likedPosts.value + unique
                        likedOffset += newPosts.size
                        _hasMore.value = _hasMore.value.toMutableMap().apply {
                            this[2] = newPosts.size >= PAGE_SIZE
                        }
                    }
                    3 -> {
                        val newPosts = cloudFunctions.getSavedPosts(userId, limit = PAGE_SIZE, offset = savedOffset)
                        val existingIds = _savedPosts.value.mapTo(HashSet()) { it.id }
                        val unique = newPosts.filter { it.id !in existingIds }
                        _savedPosts.value = _savedPosts.value + unique
                        savedOffset += newPosts.size
                        _hasMore.value = _hasMore.value.toMutableMap().apply {
                            this[3] = newPosts.size >= PAGE_SIZE
                        }
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("ProfileViewModel", "loadMoreForSegment failed", e)
            }
            _isLoadingMore.value = false
        }
    }
}
