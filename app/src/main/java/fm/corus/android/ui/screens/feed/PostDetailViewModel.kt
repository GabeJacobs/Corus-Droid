package fm.corus.android.ui.screens.feed

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import fm.corus.android.data.model.CymbalComment
import fm.corus.android.data.model.CymbalPost
import fm.corus.android.data.repository.AuthRepository
import fm.corus.android.data.repository.PostRepository
import fm.corus.android.data.repository.UserRepository
import fm.corus.android.domain.NowPlayingManager
import fm.corus.android.domain.PostEngagementManager
import fm.corus.android.service.AnalyticsService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PostDetailViewModel @Inject constructor(
    private val postRepository: PostRepository,
    private val authRepository: AuthRepository,
    private val userRepository: UserRepository,
    private val engagementManager: PostEngagementManager,
    val nowPlayingManager: NowPlayingManager,
    val analyticsService: AnalyticsService,
) : ViewModel() {

    private val _post = MutableStateFlow<CymbalPost?>(null)
    val post: StateFlow<CymbalPost?> = _post.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    private val _comments = MutableStateFlow<List<CymbalComment>>(emptyList())
    val comments: StateFlow<List<CymbalComment>> = _comments.asStateFlow()

    val engagementStates = engagementManager.states
    val currentUserProfile = authRepository.userProfile

    val currentUserId: String? get() = authRepository.currentUserId

    private var listeningPostId: String? = null

    fun loadPost(postId: String) {
        val userId = authRepository.currentUserId ?: return
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val loadedPost = postRepository.getPostDetail(postId, userId)
                _post.value = loadedPost
                if (loadedPost != null) {
                    engagementManager.initState(
                        postId = loadedPost.id,
                        likeCount = loadedPost.likeCount,
                        commentCount = loadedPost.commentCount,
                        repostCount = loadedPost.repostCount,
                        isLiked = loadedPost.isLiked,
                        isSaved = false,
                    )
                    // Start real-time listener (matching iOS PostEngagementStore)
                    listeningPostId?.let { engagementManager.stopListening(it) }
                    engagementManager.startListening(loadedPost.id)
                    listeningPostId = loadedPost.id

                    // Check actual like status from Firestore (backend doesn't return isLiked)
                    engagementManager.checkLikeStatuses(listOf(loadedPost.id), userId)
                }
                // Load comments
                val loadedComments = postRepository.getComments(postId)
                _comments.value = loadedComments
            } catch (_: Exception) { }
            _isLoading.value = false
        }
    }

    fun refresh(postId: String) {
        viewModelScope.launch {
            _isRefreshing.value = true
            val userId = authRepository.currentUserId
            if (userId != null) {
                try {
                    val loadedPost = postRepository.getPostDetail(postId, userId)
                    _post.value = loadedPost
                    if (loadedPost != null) {
                        engagementManager.initState(
                            postId = loadedPost.id,
                            likeCount = loadedPost.likeCount,
                            commentCount = loadedPost.commentCount,
                            repostCount = loadedPost.repostCount,
                            isLiked = loadedPost.isLiked,
                            isSaved = false,
                        )
                        engagementManager.checkLikeStatuses(listOf(loadedPost.id), userId)
                    }
                    _comments.value = postRepository.getComments(postId)
                } catch (_: Exception) { }
            }
            _isRefreshing.value = false
        }
    }

    fun toggleLike(postId: String) {
        val userId = authRepository.currentUserId ?: return
        engagementManager.toggleLike(postId, userId)
    }

    fun toggleSave(postId: String) {
        val userId = authRepository.currentUserId ?: return
        engagementManager.toggleSave(postId, userId)
    }

    fun repost(post: CymbalPost) {
        val userId = authRepository.currentUserId ?: return
        engagementManager.repostPost(post, userId)
    }

    fun playPreview(post: CymbalPost) {
        viewModelScope.launch {
            nowPlayingManager.play(
                trackId = post.track.id,
                trackName = post.track.name,
                artistName = post.track.artistName,
                albumArtURL = post.track.albumArtURL,
                previewUrl = post.track.previewUrl,
                spotifyURI = post.track.spotifyURI,
                spotifyWebURL = post.track.spotifyWebURL,
                isrc = post.track.isrc,
                sourcePostId = post.id,
            )
        }
    }

    fun deletePost(postId: String) {
        val userId = authRepository.currentUserId ?: return
        viewModelScope.launch {
            try {
                postRepository.deletePost(postId, userId)
            } catch (_: Exception) { }
        }
    }

    suspend fun resolveUsernameToId(username: String): String? {
        return try {
            userRepository.fetchUserByUsername(username)?.id
        } catch (_: Exception) {
            null
        }
    }

    override fun onCleared() {
        super.onCleared()
        listeningPostId?.let { engagementManager.stopListening(it) }
    }
}
