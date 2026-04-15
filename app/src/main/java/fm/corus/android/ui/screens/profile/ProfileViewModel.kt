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
import fm.corus.android.domain.PostEngagementManager
import kotlinx.coroutines.Job
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
    private val engagementManager: PostEngagementManager,
) : ViewModel() {

    val isClubMember = subscriptionRepository.isClubMember
    val hasFullAccess = subscriptionRepository.hasFullAccessFlow

    val engagementStates = engagementManager.states

    fun toggleLike(postId: String) {
        val userId = authRepository.currentUserId ?: return
        engagementManager.toggleLike(postId, userId)
    }

    fun uploadAvatar(imageData: ByteArray) {
        val uid = authRepository.currentUserId ?: return
        viewModelScope.launch {
            try {
                userRepository.uploadAvatar(uid, imageData)
                authRepository.refreshUserProfile()
                _profile.value = authRepository.userProfile.value
            } catch (e: Exception) {
                android.util.Log.e("ProfileViewModel", "uploadAvatar failed", e)
            }
        }
    }

    fun generatePlaylist() {
        val userId = authRepository.currentUserId ?: return
        viewModelScope.launch {
            nowPlayingManager.generateProfilePlaylist(userId)
        }
    }

    private val _profile = MutableStateFlow<CymbalUser?>(null)
    val profile: StateFlow<CymbalUser?> = _profile.asStateFlow()

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

    private val _isLoadingMore = MutableStateFlow(false)
    val isLoadingMore: StateFlow<Boolean> = _isLoadingMore.asStateFlow()

    private val _isLoadingLiked = MutableStateFlow(false)
    val isLoadingLiked: StateFlow<Boolean> = _isLoadingLiked.asStateFlow()

    private val _isLoadingSaved = MutableStateFlow(false)
    val isLoadingSaved: StateFlow<Boolean> = _isLoadingSaved.asStateFlow()

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

    private val PAGE_SIZE = 30
    // Minimum filtered posts per segment before we stop auto-fetching more pages
    private val MIN_SEGMENT_POSTS = 12

    fun loadProfile() {
        val userId = authRepository.currentUserId ?: return
        viewModelScope.launch {
            _isLoading.value = true
            try {
                authRepository.refreshUserProfile()
                _profile.value = authRepository.userProfile.value
                // Load profile posts (shared by MUSIC and FILM tabs).
                // Keep fetching pages until both segments have enough filtered
                // posts or the server runs out.
                var allPosts = listOf<CymbalPost>()
                var cursor: Long? = null
                var serverHasMore = true
                while (serverHasMore) {
                    val page = cloudFunctions.getProfilePosts(userId, userId, limit = PAGE_SIZE, lastTimestamp = cursor)
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
                _hasMore.value = _hasMore.value.toMutableMap().apply {
                    this[0] = serverHasMore
                    this[1] = serverHasMore
                }
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
                engagementManager.checkLikeStatuses(allPosts.map { it.id }, userId)
            } catch (e: Exception) {
                android.util.Log.e("ProfileViewModel", "loadProfile failed", e)
            }
            _isLoading.value = false
        }
    }

    fun refreshProfile() {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                authRepository.refreshUserProfile()
                _profile.value = authRepository.userProfile.value
                val userId = authRepository.currentUserId ?: return@launch
                var allPosts = listOf<CymbalPost>()
                var cursor: Long? = null
                var serverHasMore = true
                while (serverHasMore) {
                    val page = cloudFunctions.getProfilePosts(userId, userId, limit = PAGE_SIZE, lastTimestamp = cursor)
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
                _hasMore.value = _hasMore.value.toMutableMap().apply {
                    this[0] = serverHasMore
                    this[1] = serverHasMore
                }
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
                engagementManager.checkLikeStatuses(allPosts.map { it.id }, userId)
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
     * Segments 0 (MUSIC) and 1 (FILM) share the same posts list — no re-fetch needed.
     * Segments 2 (LIKES) and 3 (SAVES) lazy-load on first visit.
     */
    fun loadSegment(index: Int) {
        _currentSegment.value = index
        when (index) {
            0, 1 -> { /* Posts already loaded in loadProfile */ }
            2 -> if (!likedLoaded) loadLikedPosts()
            3 -> if (!savedLoaded) loadSavedPosts()
        }
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
                            _posts.value = _posts.value + newPosts
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
                        _likedPosts.value = _likedPosts.value + newPosts
                        likedOffset += newPosts.size
                        _hasMore.value = _hasMore.value.toMutableMap().apply {
                            this[2] = newPosts.size >= PAGE_SIZE
                        }
                    }
                    3 -> {
                        val newPosts = cloudFunctions.getSavedPosts(userId, limit = PAGE_SIZE, offset = savedOffset)
                        _savedPosts.value = _savedPosts.value + newPosts
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
