package fm.corus.android.ui.screens.feed

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import fm.corus.android.R
import fm.corus.android.data.model.CymbalComment
import fm.corus.android.data.model.CymbalPost
import fm.corus.android.data.model.CymbalUser
import fm.corus.android.data.repository.AuthRepository
import fm.corus.android.data.repository.MessageRepository
import fm.corus.android.data.repository.PostRepository
import fm.corus.android.data.repository.UserRepository
import fm.corus.android.domain.NowPlayingManager
import fm.corus.android.domain.PostDeletionEvent
import fm.corus.android.domain.PostEngagementManager
import fm.corus.android.service.AnalyticsService
import fm.corus.android.service.RemoteConfigService
import fm.corus.android.ui.components.PostMenuActions
import fm.corus.android.ui.components.ToastManager
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
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
    private val messageRepository: MessageRepository,
    private val engagementManager: PostEngagementManager,
    private val postDeletionEvent: PostDeletionEvent,
    val nowPlayingManager: NowPlayingManager,
    override val remoteConfig: RemoteConfigService,
    override val analyticsService: AnalyticsService,
    @ApplicationContext private val context: Context,
) : ViewModel(), PostMenuActions {

    override suspend fun fetchBackCover(postId: String): String? {
        return postRepository.fetchBackCover(postId)
    }

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

    // ── Share search state ──
    private val _shareSearchResults = MutableStateFlow<List<CymbalUser>>(emptyList())
    override val shareSearchResults: StateFlow<List<CymbalUser>> = _shareSearchResults.asStateFlow()

    private val _recentShareContacts = MutableStateFlow<List<CymbalUser>>(emptyList())
    override val recentShareContacts: StateFlow<List<CymbalUser>> = _recentShareContacts.asStateFlow()

    private val _isShareSearching = MutableStateFlow(false)
    override val isShareSearching: StateFlow<Boolean> = _isShareSearching.asStateFlow()

    private val _isLoadingShareContacts = MutableStateFlow(true)
    override val isLoadingShareContacts: StateFlow<Boolean> = _isLoadingShareContacts.asStateFlow()

    private var shareSearchJob: Job? = null

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

    override fun deletePost(postId: String) {
        val userId = authRepository.currentUserId ?: return
        viewModelScope.launch {
            try {
                postRepository.deletePost(postId, userId)
                authRepository.bumpCymbalCount(-1)
                postDeletionEvent.notifyPostDeleted(postId)
                ToastManager.show(context.getString(R.string.feed_toast_post_deleted))
            } catch (_: Exception) {
                ToastManager.show(context.getString(R.string.feed_toast_failed_delete))
            }
        }
    }

    override fun isOwnPost(post: CymbalPost): Boolean {
        return post.user.id == authRepository.currentUserId
    }

    override fun loadRecentShareContacts() {
        val userId = authRepository.currentUserId ?: return
        _isLoadingShareContacts.value = true
        viewModelScope.launch {
            try {
                val threads = messageRepository.listThreads(userId)
                val contacts = threads.mapNotNull { it.otherUser }
                if (contacts.isNotEmpty()) {
                    _recentShareContacts.value = contacts.take(20)
                } else {
                    val following = userRepository.fetchFollowingPaginated(userId, limit = 20).users
                    val followers = userRepository.fetchFollowersPaginated(userId, limit = 20).users
                    val seen = mutableSetOf<String>()
                    val combined = mutableListOf<CymbalUser>()
                    for (user in following + followers) {
                        if (seen.add(user.id)) combined.add(user)
                    }
                    _recentShareContacts.value = combined.take(20)
                }
            } catch (_: Exception) { }
            _isLoadingShareContacts.value = false
        }
    }

    override fun searchShareUsers(query: String) {
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
                _shareSearchResults.value = userRepository.searchUsers(trimmed)
            } catch (_: Exception) {
                _shareSearchResults.value = emptyList()
            }
            _isShareSearching.value = false
        }
    }

    override fun sendPostToUser(userId: String, post: CymbalPost, message: String) {
        val currentUserId = authRepository.currentUserId ?: return
        viewModelScope.launch {
            try {
                val threadId = messageRepository.getOrCreateThread(currentUserId, userId)
                val text = message.trim()
                if (post.isMovie) {
                    messageRepository.sendSharedFilmMessage(
                        threadId = threadId,
                        fromUserId = currentUserId,
                        text = text,
                        movieTitle = post.movieTitle ?: post.displayTitle,
                        directorName = post.directorName ?: post.displaySubtitle,
                        posterURL = post.posterURL,
                        tmdbWebURL = post.tmdbWebURL,
                    )
                } else {
                    messageRepository.sendSharedTrackMessage(
                        threadId = threadId,
                        fromUserId = currentUserId,
                        text = text,
                        trackName = post.track.name,
                        artistName = post.track.artistName,
                        albumArtURL = post.track.albumArtURL,
                        spotifyURL = post.track.spotifyWebURL.ifBlank { null },
                    )
                }
            } catch (_: Exception) {
                ToastManager.show(context.getString(R.string.feed_toast_failed_send_post))
            }
        }
    }

    override fun reportPost(postId: String, postUserId: String) {
        val currentUserId = authRepository.currentUserId ?: return
        analyticsService.logReportPost(postId, "reported_from_feed")
        viewModelScope.launch {
            try {
                userRepository.submitReport(
                    reporterId = currentUserId,
                    targetUserId = postUserId,
                    postId = postId,
                    reason = "reported_from_feed",
                    details = "",
                )
                ToastManager.show(context.getString(R.string.feed_toast_post_reported))
            } catch (_: Exception) {
                ToastManager.show(context.getString(R.string.feed_toast_failed_report))
            }
        }
    }

    override fun blockUser(targetUserId: String) {
        val currentUserId = authRepository.currentUserId ?: return
        viewModelScope.launch {
            try {
                userRepository.blockUser(currentUserId, targetUserId)
                ToastManager.show(context.getString(R.string.feed_toast_user_blocked))
            } catch (_: Exception) {
                ToastManager.show(context.getString(R.string.feed_toast_failed_block))
            }
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
