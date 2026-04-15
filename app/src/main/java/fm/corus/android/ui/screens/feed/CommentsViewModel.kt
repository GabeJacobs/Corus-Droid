package fm.corus.android.ui.screens.feed

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import fm.corus.android.data.model.CymbalComment
import fm.corus.android.data.model.CymbalPost
import fm.corus.android.data.model.CymbalUser
import fm.corus.android.data.repository.AuthRepository
import fm.corus.android.data.repository.PostRepository
import fm.corus.android.data.repository.UserRepository
import fm.corus.android.domain.NowPlayingManager
import fm.corus.android.domain.PostEngagementManager
import fm.corus.android.service.RemoteConfigService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class CommentsViewModel @Inject constructor(
    private val postRepository: PostRepository,
    private val authRepository: AuthRepository,
    private val userRepository: UserRepository,
    private val engagementManager: PostEngagementManager,
    val nowPlayingManager: NowPlayingManager,
    private val remoteConfigService: RemoteConfigService,
) : ViewModel() {

    val giphySupport: Boolean
        get() = remoteConfigService.giphySupport

    private val _comments = MutableStateFlow<List<CymbalComment>>(emptyList())
    val comments: StateFlow<List<CymbalComment>> = _comments.asStateFlow()

    private val _repliesByParent = MutableStateFlow<Map<String, List<CymbalComment>>>(emptyMap())
    val repliesByParent: StateFlow<Map<String, List<CymbalComment>>> = _repliesByParent.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _isSending = MutableStateFlow(false)
    val isSending: StateFlow<Boolean> = _isSending.asStateFlow()

    private val _replyingTo = MutableStateFlow<CymbalComment?>(null)
    val replyingTo: StateFlow<CymbalComment?> = _replyingTo.asStateFlow()

    private val _mentionSuggestions = MutableStateFlow<List<CymbalUser>>(emptyList())
    val mentionSuggestions: StateFlow<List<CymbalUser>> = _mentionSuggestions.asStateFlow()

    private val _post = MutableStateFlow<CymbalPost?>(null)
    val post: StateFlow<CymbalPost?> = _post.asStateFlow()

    val currentUserId: String? get() = authRepository.currentUserId
    val engagementStates = engagementManager.states
    val currentUserProfile = authRepository.userProfile

    private var postId: String = ""
    private var _currentUserProfile: CymbalUser? = null
    private var listeningPostId: String? = null

    private val _sendError = MutableStateFlow<String?>(null)
    val sendError: StateFlow<String?> = _sendError.asStateFlow()

    fun clearSendError() { _sendError.value = null }

    fun loadPost(postId: String) {
        val userId = authRepository.currentUserId ?: return
        viewModelScope.launch {
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

                    // Start real-time listener (matching iOS PostEngagementStore)
                    listeningPostId?.let { engagementManager.stopListening(it) }
                    engagementManager.startListening(loadedPost.id)
                    listeningPostId = loadedPost.id
                }
            } catch (_: Exception) { }
        }
    }

    fun clearReply() {
        _replyingTo.value = null
    }

    fun addComment(postId: String, text: String) {
        this.postId = postId
        sendComment(text)
    }

    fun toggleCommentLike(postId: String, commentId: String) {
        this.postId = postId
        toggleCommentLike(commentId)
    }

    fun setReplyTo(comment: CymbalComment) {
        _replyingTo.value = comment
    }

    fun loadComments(postId: String) {
        this.postId = postId
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val allComments = postRepository.getComments(postId)
                // Separate top-level comments from replies
                val topLevel = allComments.filter { it.parentCommentId == null }
                val replies = allComments.filter { it.parentCommentId != null }
                    .groupBy { it.parentCommentId!! }

                _comments.value = topLevel
                _repliesByParent.value = replies
            } catch (_: Exception) { }
            _isLoading.value = false
        }
    }

    fun setReplyingTo(comment: CymbalComment?) {
        _replyingTo.value = comment
    }

    fun sendComment(text: String) {
        val userId = authRepository.currentUserId ?: return
        if (text.isBlank()) return

        viewModelScope.launch {
            _isSending.value = true

            val replyTo = _replyingTo.value
            val parentId = replyTo?.parentCommentId ?: replyTo?.id
            val replyToUserId = replyTo?.user?.id

            // Build optimistic comment
            val userProfile = _currentUserProfile
                ?: try { userRepository.fetchUserProfile(userId) } catch (_: Exception) { null }
            _currentUserProfile = userProfile

            val tempId = "temp_${System.currentTimeMillis()}"
            val optimisticComment = CymbalComment(
                id = tempId,
                user = userProfile ?: CymbalUser(id = userId, username = "", displayName = ""),
                text = text,
                timestamp = java.util.Date(),
                parentCommentId = parentId,
                replyToUser = replyTo?.user,
            )

            // Insert optimistically
            if (parentId != null) {
                _repliesByParent.value = _repliesByParent.value.toMutableMap().apply {
                    this[parentId] = (this[parentId] ?: emptyList()) + optimisticComment
                }
            } else {
                _comments.value = _comments.value + optimisticComment
            }
            engagementManager.incrementCommentCount(postId)
            _replyingTo.value = null
            _isSending.value = false

            // Send to server, reload on success, rollback on failure
            try {
                postRepository.addComment(
                    postId = postId,
                    userId = userId,
                    text = text,
                    parentCommentId = parentId,
                    replyToUserId = replyToUserId,
                )
                loadComments(postId)
            } catch (_: Exception) {
                // Rollback optimistic insert
                if (parentId != null) {
                    _repliesByParent.value = _repliesByParent.value.toMutableMap().apply {
                        this[parentId] = (this[parentId] ?: emptyList()).filter { it.id != tempId }
                    }
                } else {
                    _comments.value = _comments.value.filter { it.id != tempId }
                }
                engagementManager.decrementCommentCount(postId)
                _sendError.value = "Failed to send comment"
            }
        }
    }

    fun sendGifComment(gifURL: String) {
        val userId = authRepository.currentUserId ?: return

        viewModelScope.launch {
            _isSending.value = true

            val replyTo = _replyingTo.value
            val parentId = replyTo?.parentCommentId ?: replyTo?.id
            val replyToUserId = replyTo?.user?.id

            val userProfile = _currentUserProfile
                ?: try { userRepository.fetchUserProfile(userId) } catch (_: Exception) { null }
            _currentUserProfile = userProfile

            val tempId = "temp_${System.currentTimeMillis()}"
            val optimisticComment = CymbalComment(
                id = tempId,
                user = userProfile ?: CymbalUser(id = userId, username = "", displayName = ""),
                text = "",
                timestamp = java.util.Date(),
                parentCommentId = parentId,
                replyToUser = replyTo?.user,
                gifURL = gifURL,
            )

            if (parentId != null) {
                _repliesByParent.value = _repliesByParent.value.toMutableMap().apply {
                    this[parentId] = (this[parentId] ?: emptyList()) + optimisticComment
                }
            } else {
                _comments.value = _comments.value + optimisticComment
            }
            engagementManager.incrementCommentCount(postId)
            _replyingTo.value = null
            _isSending.value = false

            try {
                postRepository.addComment(
                    postId = postId,
                    userId = userId,
                    text = "",
                    parentCommentId = parentId,
                    replyToUserId = replyToUserId,
                    gifURL = gifURL,
                )
                loadComments(postId)
            } catch (_: Exception) {
                if (parentId != null) {
                    _repliesByParent.value = _repliesByParent.value.toMutableMap().apply {
                        this[parentId] = (this[parentId] ?: emptyList()).filter { it.id != tempId }
                    }
                } else {
                    _comments.value = _comments.value.filter { it.id != tempId }
                }
                engagementManager.decrementCommentCount(postId)
                _sendError.value = "Failed to send GIF"
            }
        }
    }

    fun deleteComment(commentId: String) {
        viewModelScope.launch {
            try {
                postRepository.deleteComment(postId, commentId)
                engagementManager.decrementCommentCount(postId)
                // Remove from local state optimistically
                _comments.value = _comments.value.filter { it.id != commentId }
                _repliesByParent.value = _repliesByParent.value.mapValues { (_, replies) ->
                    replies.filter { it.id != commentId }
                }
            } catch (_: Exception) { }
        }
    }

    // ── Comment Liking ──

    private val _likedCommentIds = MutableStateFlow<Set<String>>(emptySet())
    val likedCommentIds: StateFlow<Set<String>> = _likedCommentIds.asStateFlow()

    fun toggleCommentLike(commentId: String) {
        val userId = authRepository.currentUserId ?: return
        val isCurrentlyLiked = _likedCommentIds.value.contains(commentId)

        // Optimistic update
        if (isCurrentlyLiked) {
            _likedCommentIds.value = _likedCommentIds.value - commentId
            updateCommentLikeCount(commentId, -1)
        } else {
            _likedCommentIds.value = _likedCommentIds.value + commentId
            updateCommentLikeCount(commentId, 1)
        }

        viewModelScope.launch {
            try {
                if (isCurrentlyLiked) {
                    postRepository.unlikeComment(userId, postId, commentId)
                } else {
                    postRepository.likeComment(userId, postId, commentId)
                }
            } catch (_: Exception) {
                // Revert optimistic update on failure
                if (isCurrentlyLiked) {
                    _likedCommentIds.value = _likedCommentIds.value + commentId
                    updateCommentLikeCount(commentId, 1)
                } else {
                    _likedCommentIds.value = _likedCommentIds.value - commentId
                    updateCommentLikeCount(commentId, -1)
                }
            }
        }
    }

    private fun updateCommentLikeCount(commentId: String, delta: Int) {
        _comments.value = _comments.value.map { comment ->
            if (comment.id == commentId) comment.copy(likeCount = (comment.likeCount + delta).coerceAtLeast(0))
            else comment
        }
        _repliesByParent.value = _repliesByParent.value.mapValues { (_, replies) ->
            replies.map { reply ->
                if (reply.id == commentId) reply.copy(likeCount = (reply.likeCount + delta).coerceAtLeast(0))
                else reply
            }
        }
    }

    // ── Mention Search ──

    fun searchMentions(query: String) {
        if (query.length < 2) {
            _mentionSuggestions.value = emptyList()
            return
        }
        viewModelScope.launch {
            try {
                val results = userRepository.searchUsers(query, limit = 4)
                _mentionSuggestions.value = results
            } catch (_: Exception) {
                _mentionSuggestions.value = emptyList()
            }
        }
    }

    // ── Post Engagement ──

    fun togglePostLike(postId: String) {
        val userId = authRepository.currentUserId ?: return
        engagementManager.toggleLike(postId, userId)
    }

    fun togglePostSave(postId: String) {
        val userId = authRepository.currentUserId ?: return
        engagementManager.toggleSave(postId, userId)
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

    // ── Username resolution ──

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
