package fm.corus.android.ui.screens.feed

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import fm.corus.android.R
import fm.corus.android.data.model.CommentAttachedFilm
import fm.corus.android.data.model.CommentAttachedSong
import fm.corus.android.data.model.CymbalComment
import fm.corus.android.data.model.KlipyGif
import fm.corus.android.data.model.CymbalMovie
import fm.corus.android.data.model.CymbalPost
import fm.corus.android.data.model.CymbalTrack
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
import fm.corus.android.ui.components.extractMentions
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class CommentsViewModel @Inject constructor(
    private val postRepository: PostRepository,
    val authRepository: AuthRepository,
    val userRepository: UserRepository,
    private val messageRepository: MessageRepository,
    private val engagementManager: PostEngagementManager,
    private val postDeletionEvent: PostDeletionEvent,
    val nowPlayingManager: NowPlayingManager,
    private val remoteConfigService: RemoteConfigService,
    private val gifRepository: fm.corus.android.data.repository.GifRepository,
    override val analyticsService: AnalyticsService,
    @ApplicationContext private val context: Context,
) : ViewModel(), PostMenuActions {

    override val remoteConfig: RemoteConfigService get() = remoteConfigService

    val gifSupport: Boolean
        get() = remoteConfigService.gifSupport

    /**
     * Per-post comments-audience gate, derived from the loaded post and the
     * follow-edge that the audience requires. `null` means the viewer can
     * comment; non-null is the reason the composer is hidden. Mirrors web's
     * `useCanComment` and iOS's `commentBlockReason` short-circuits.
     *
     * We deliberately don't gate on `commentControlsOnPosts` here: once a
     * post on the wire carries `commentsAudience`, the server enforces it
     * regardless of the viewer's feature-flag state, so the client must too
     * — otherwise the viewer sees a composer and gets a permission-denied
     * on submit.
     *
     * - Missing field, "everyone" audience, viewer == author: allow.
     * - "off": always blocked (including author).
     * - "followers": blocked unless the viewer follows the author.
     * - "following": blocked unless the author follows the viewer.
     *
     * While the lookup is still resolving, treat the viewer as blocked so we
     * don't flash a composer that may disappear once the lookup lands.
     */
    private val _followGateEdgeExists = MutableStateFlow<Boolean?>(null)
    /// Lazy so initialization runs *after* `_post` is constructed below —
    /// referencing `_post` in a property initializer at this position would
    /// otherwise dereference an uninitialized field.
    val commentBlockReason: StateFlow<fm.corus.android.ui.components.CommentsBlockReason?> by lazy {
        combine(_post, _followGateEdgeExists) { post, edgeExists ->
            computeCommentBlockReason(post, edgeExists)
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = null,
        )
    }

    /**
     * Author exemption nuance: for OFF, EVERYONE is blocked (matches
     * IG/Threads — the author can re-enable comments if they want to
     * participate). For FOLLOWERS and FOLLOWING, the author is implicitly
     * in their own audience and keeps posting access on their own thread.
     */
    private fun computeCommentBlockReason(
        post: CymbalPost?,
        edgeExists: Boolean?,
    ): fm.corus.android.ui.components.CommentsBlockReason? {
        if (post == null) return null
        val audience = post.commentsAudience ?: return null
        if (audience == fm.corus.android.data.model.CommentsAudience.EVERYONE) return null
        val viewerId = authRepository.currentUserId
        val isAuthor = viewerId != null && viewerId == post.user.id
        return when (audience) {
            fm.corus.android.data.model.CommentsAudience.EVERYONE -> null
            fm.corus.android.data.model.CommentsAudience.OFF ->
                fm.corus.android.ui.components.CommentsBlockReason.OFF
            fm.corus.android.data.model.CommentsAudience.FOLLOWERS -> when {
                isAuthor -> null
                edgeExists == true -> null
                else -> fm.corus.android.ui.components.CommentsBlockReason.FOLLOWERS
            }
            fm.corus.android.data.model.CommentsAudience.FOLLOWING -> when {
                isAuthor -> null
                edgeExists == true -> null
                else -> fm.corus.android.ui.components.CommentsBlockReason.FOLLOWING
            }
        }
    }

    /**
     * Resolves the follow edge that gates the comment composer. Direction
     * depends on the audience:
     *  - FOLLOWERS: does the viewer follow the author? Cheap — answered from
     *    the in-memory `userRepository.isFollowing` cache populated at sign-in.
     *  - FOLLOWING: does the author follow the viewer? The viewer's local
     *    cache can't answer this, so we hit Firestore.
     */
    private fun refreshFollowGate(post: CymbalPost) {
        val audience = post.commentsAudience ?: return
        if (audience != fm.corus.android.data.model.CommentsAudience.FOLLOWERS &&
            audience != fm.corus.android.data.model.CommentsAudience.FOLLOWING) return
        val viewerId = authRepository.currentUserId ?: return
        if (viewerId == post.user.id) return
        when (audience) {
            fm.corus.android.data.model.CommentsAudience.FOLLOWERS -> {
                // Fast path: in-memory cache of who *the viewer* follows.
                _followGateEdgeExists.value = userRepository.isFollowing(post.user.id)
            }
            fm.corus.android.data.model.CommentsAudience.FOLLOWING -> {
                // Need the AUTHOR's following set — out-of-cache, hit Firestore.
                viewModelScope.launch {
                    try {
                        _followGateEdgeExists.value = userRepository
                            .doesUserFollow(post.user.id, viewerId)
                    } catch (e: Exception) {
                        Log.e("CommentsViewModel", "follow gate lookup failed", e)
                        // Leave null → composer stays hidden until the user
                        // re-opens the sheet. Better than flashing a composer
                        // the server will reject.
                    }
                }
            }
            else -> Unit
        }
    }

    // ── Pending attachment state for the composer ──
    private val _pendingSong = MutableStateFlow<CommentAttachedSong?>(null)
    val pendingSong: StateFlow<CommentAttachedSong?> = _pendingSong.asStateFlow()

    private val _pendingFilm = MutableStateFlow<CommentAttachedFilm?>(null)
    val pendingFilm: StateFlow<CommentAttachedFilm?> = _pendingFilm.asStateFlow()

    private val _pendingGif = MutableStateFlow<KlipyGif?>(null)
    val pendingGif: StateFlow<KlipyGif?> = _pendingGif.asStateFlow()

    fun attachSong(track: CymbalTrack) {
        _pendingSong.value = CommentAttachedSong.fromTrack(track)
        _pendingFilm.value = null
        _pendingGif.value = null
    }

    fun attachFilm(movie: CymbalMovie) {
        _pendingFilm.value = CommentAttachedFilm.fromMovie(movie)
        _pendingSong.value = null
        _pendingGif.value = null
    }

    fun attachGif(gif: KlipyGif) {
        _pendingGif.value = gif
        _pendingSong.value = null
        _pendingFilm.value = null
    }

    fun clearAttachment() {
        _pendingSong.value = null
        _pendingFilm.value = null
        _pendingGif.value = null
    }

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

    private val _isSearchingMentions = MutableStateFlow(false)
    val isSearchingMentions: StateFlow<Boolean> = _isSearchingMentions.asStateFlow()

    private var mentionSearchJob: Job? = null

    private val _post = MutableStateFlow<CymbalPost?>(null)
    val post: StateFlow<CymbalPost?> = _post.asStateFlow()

    val currentUserId: String? get() = authRepository.currentUserId
    val engagementStates = engagementManager.states
    val currentUserProfile = authRepository.userProfile

    private var postId: String = ""
    private var _currentUserProfile: CymbalUser? = null
    private var listeningPostId: String? = null

    private val _editingComment = MutableStateFlow<CymbalComment?>(null)
    val editingComment: StateFlow<CymbalComment?> = _editingComment.asStateFlow()

    private val _sendError = MutableStateFlow<String?>(null)
    val sendError: StateFlow<String?> = _sendError.asStateFlow()

    fun clearSendError() { _sendError.value = null }

    fun startEditing(comment: CymbalComment) {
        _replyingTo.value = null
        _editingComment.value = comment
    }

    fun cancelEditing() {
        _editingComment.value = null
    }

    fun loadPost(postId: String) {
        // Show cached post immediately so the caption appears without delay
        val cached = postRepository.getCachedPost(postId)
        if (cached != null && _post.value == null) {
            _post.value = cached
            refreshFollowGate(cached)
        }

        val userId = authRepository.currentUserId ?: return
        viewModelScope.launch {
            try {
                val loadedPost = postRepository.getPostDetail(postId, userId)
                _post.value = loadedPost
                if (loadedPost != null) {
                    refreshFollowGate(loadedPost)
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
        val postChanged = this.postId != postId
        this.postId = postId
        if (postChanged) {
            _comments.value = emptyList()
            _repliesByParent.value = emptyMap()
            _pendingSong.value = null
            _pendingFilm.value = null
            _replyingTo.value = null
        }
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val allComments = postRepository.getComments(postId)
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
        val attachedSong = _pendingSong.value
        val attachedFilm = _pendingFilm.value
        // Allow attachment-only sends (text empty but attachment set).
        if (text.isBlank() && attachedSong == null && attachedFilm == null) return

        viewModelScope.launch {
            _isSending.value = true

            val replyTo = _replyingTo.value
            val parentId = replyTo?.parentCommentId ?: replyTo?.id
            val replyToUserId = replyTo?.user?.id

            // Build optimistic comment
            val userProfile = _currentUserProfile
                ?: try { userRepository.fetchUserProfile(userId) } catch (_: Exception) { null }
            _currentUserProfile = userProfile

            val isFallback = text.isBlank() && (attachedSong != null || attachedFilm != null)
            val optimisticText = when {
                text.isNotBlank() -> text
                attachedSong != null -> attachedSong.fallbackText
                attachedFilm != null -> attachedFilm.fallbackText
                else -> ""
            }

            val tempId = "temp_${System.currentTimeMillis()}"
            val optimisticComment = CymbalComment(
                id = tempId,
                user = userProfile ?: CymbalUser(id = userId, username = "", displayName = ""),
                text = optimisticText,
                timestamp = java.util.Date(),
                parentCommentId = parentId,
                replyToUser = replyTo?.user,
                attachedSong = attachedSong,
                attachedFilm = attachedFilm,
                textIsAttachmentFallback = isFallback,
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
            _pendingSong.value = null
            _pendingFilm.value = null
            _isSending.value = false

            // Send to server, reload on success, rollback on failure
            try {
                val newCommentId = postRepository.addComment(
                    postId = postId,
                    userId = userId,
                    text = text,
                    parentCommentId = parentId,
                    replyToUserId = replyToUserId,
                    attachedSong = attachedSong,
                    attachedFilm = attachedFilm,
                )
                loadComments(postId)
                sendCommentNotifications(
                    userId = userId,
                    postId = postId,
                    newCommentId = newCommentId,
                    text = text,
                    gifURL = null,
                    parentId = parentId,
                    replyToUserId = replyToUserId,
                    isAttachmentFallback = isFallback,
                    attachmentType = when {
                        attachedSong != null -> "song"
                        attachedFilm != null -> "film"
                        else -> null
                    },
                )
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
                _sendError.value = context.getString(R.string.comments_send_error_send)
            }
        }
    }

    fun sendGifComment(gifURL: String, slug: String = "", text: String = "") {
        val userId = authRepository.currentUserId ?: return
        val trimmedText = text.trim()

        // Fire-and-forget the share-trigger ping so the optimistic insert
        // doesn't have to wait on a network round-trip.
        if (slug.isNotEmpty()) {
            viewModelScope.launch { gifRepository.triggerShare(slug) }
        }

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
                text = trimmedText,
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
            _pendingGif.value = null
            _isSending.value = false

            try {
                val newCommentId = postRepository.addComment(
                    postId = postId,
                    userId = userId,
                    text = trimmedText,
                    parentCommentId = parentId,
                    replyToUserId = replyToUserId,
                    gifURL = gifURL,
                )
                loadComments(postId)
                sendCommentNotifications(
                    userId = userId,
                    postId = postId,
                    newCommentId = newCommentId,
                    text = trimmedText,
                    gifURL = gifURL,
                    parentId = parentId,
                    replyToUserId = replyToUserId,
                )
            } catch (_: Exception) {
                if (parentId != null) {
                    _repliesByParent.value = _repliesByParent.value.toMutableMap().apply {
                        this[parentId] = (this[parentId] ?: emptyList()).filter { it.id != tempId }
                    }
                } else {
                    _comments.value = _comments.value.filter { it.id != tempId }
                }
                engagementManager.decrementCommentCount(postId)
                _sendError.value = context.getString(R.string.comments_send_error_gif)
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

    fun editComment(commentId: String, newText: String) {
        val trimmed = newText.trim()
        if (trimmed.isBlank()) return

        val editing = _editingComment.value ?: return
        if (trimmed == editing.text) {
            cancelEditing()
            return
        }

        // Optimistic update
        val updated = editing.copy(text = trimmed, editedAt = java.util.Date())
        updateCommentInPlace(updated)
        cancelEditing()

        viewModelScope.launch {
            try {
                postRepository.editComment(postId, commentId, trimmed)
            } catch (_: Exception) {
                // Revert on failure
                updateCommentInPlace(editing)
                _sendError.value = context.getString(R.string.comments_edit_error)
            }
        }
    }

    private fun updateCommentInPlace(updated: CymbalComment) {
        if (updated.parentCommentId != null) {
            _repliesByParent.value = _repliesByParent.value.toMutableMap().apply {
                val parentId = updated.parentCommentId
                this[parentId] = (this[parentId] ?: emptyList()).map {
                    if (it.id == updated.id) updated else it
                }
            }
        } else {
            _comments.value = _comments.value.map {
                if (it.id == updated.id) updated else it
            }
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
                    // Send comment_like notification (matches iOS)
                    try {
                        val comment = _comments.value.find { it.id == commentId }
                            ?: _repliesByParent.value.values.flatten().find { it.id == commentId }
                        val commentOwnerId = comment?.user?.id
                        val post = postRepository.getCachedPost(postId)
                        if (commentOwnerId != null) {
                            postRepository.createNotification(
                                type = "comment_like",
                                fromUserId = userId,
                                toUserId = commentOwnerId,
                                postId = postId,
                                postAlbumArtURL = post?.displayImageURL,
                                commentId = commentId,
                            )
                        }
                    } catch (_: Exception) { }
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
        mentionSearchJob?.cancel()
        if (query.length < 2) {
            _mentionSuggestions.value = emptyList()
            _isSearchingMentions.value = false
            return
        }
        _isSearchingMentions.value = true
        mentionSearchJob = viewModelScope.launch {
            try {
                val results = userRepository.searchUsers(query, limit = 4)
                _mentionSuggestions.value = results
            } catch (_: Exception) {
                _mentionSuggestions.value = emptyList()
            } finally {
                _isSearchingMentions.value = false
            }
        }
    }

    fun clearMentions() {
        mentionSearchJob?.cancel()
        mentionSearchJob = null
        _mentionSuggestions.value = emptyList()
        _isSearchingMentions.value = false
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
        nowPlayingManager.lastUserInitiatedSourcePostId = post.id
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

    // ── Comment/reply/mention notifications (matches iOS addComment) ──

    private fun sendCommentNotifications(
        userId: String,
        postId: String,
        newCommentId: String,
        text: String,
        gifURL: String?,
        parentId: String?,
        replyToUserId: String?,
        isAttachmentFallback: Boolean = false,
        attachmentType: String? = null,
    ) {
        viewModelScope.launch {
            try {
                val post = postRepository.getCachedPost(postId)
                val ownerId = post?.user?.id
                val albumArtURL = post?.displayImageURL
                // Suppress synthesized fallback text from notification copy so push reads
                // "shared a song/film" instead of "🎵 Anti-Hero — Taylor Swift".
                val captionForNotif = if (isAttachmentFallback) "" else text
                val truncatedText = when {
                    gifURL != null -> "sent a GIF"
                    captionForNotif.isNotEmpty() ->
                        if (captionForNotif.length > 100) captionForNotif.take(100) + "…"
                        else captionForNotif
                    attachmentType == "song" -> "shared a song"
                    attachmentType == "film" -> "shared a film"
                    else -> ""
                }

                val notifiedIds = mutableSetOf(userId) // Don't notify self

                if (parentId != null) {
                    // Reply — notify parent comment author
                    val parentComment = _comments.value.find { it.id == parentId }
                        ?: _repliesByParent.value.values.flatten().find { it.id == parentId }
                    val parentOwnerId = parentComment?.user?.id
                    if (parentOwnerId != null && notifiedIds.add(parentOwnerId)) {
                        try {
                            postRepository.createNotification(
                                type = "reply",
                                fromUserId = userId,
                                toUserId = parentOwnerId,
                                postId = postId,
                                postAlbumArtURL = albumArtURL,
                                commentText = truncatedText,
                                commentId = newCommentId,
                                attachmentType = attachmentType,
                            )
                        } catch (_: Exception) { }
                    }

                    // Notify the user being replied to (if different from parent author)
                    if (replyToUserId != null && notifiedIds.add(replyToUserId)) {
                        try {
                            postRepository.createNotification(
                                type = "reply",
                                fromUserId = userId,
                                toUserId = replyToUserId,
                                postId = postId,
                                postAlbumArtURL = albumArtURL,
                                commentText = truncatedText,
                                commentId = newCommentId,
                                attachmentType = attachmentType,
                            )
                        } catch (_: Exception) { }
                    }

                    // Notify post owner as a comment notification (if not already notified)
                    if (ownerId != null && notifiedIds.add(ownerId)) {
                        try {
                            postRepository.createNotification(
                                type = "comment",
                                fromUserId = userId,
                                toUserId = ownerId,
                                postId = postId,
                                postAlbumArtURL = albumArtURL,
                                commentText = truncatedText,
                                commentId = newCommentId,
                                attachmentType = attachmentType,
                            )
                        } catch (_: Exception) { }
                    }
                } else {
                    // Top-level comment — notify post owner
                    if (ownerId != null && notifiedIds.add(ownerId)) {
                        try {
                            postRepository.createNotification(
                                type = "comment",
                                fromUserId = userId,
                                toUserId = ownerId,
                                postId = postId,
                                postAlbumArtURL = albumArtURL,
                                commentText = truncatedText,
                                commentId = newCommentId,
                                attachmentType = attachmentType,
                            )
                        } catch (_: Exception) { }
                    }
                }

                // Mention notifications
                val mentionedUsernames = extractMentions(text)
                for (username in mentionedUsernames) {
                    val mentionedUser = try { userRepository.fetchUserByUsername(username) } catch (_: Exception) { null }
                    if (mentionedUser != null && mentionedUser.id != userId && mentionedUser.id != ownerId) {
                        try {
                            postRepository.createNotification(
                                type = "mention",
                                fromUserId = userId,
                                toUserId = mentionedUser.id,
                                postId = postId,
                                postAlbumArtURL = albumArtURL,
                                commentText = truncatedText,
                                commentId = newCommentId,
                                attachmentType = attachmentType,
                            )
                        } catch (_: Exception) { }
                    }
                }
            } catch (_: Exception) { }
        }
    }

    // ── Post menu actions (share, report, block, etc) ──

    private val _shareSearchResults = MutableStateFlow<List<CymbalUser>>(emptyList())
    override val shareSearchResults: StateFlow<List<CymbalUser>> = _shareSearchResults.asStateFlow()

    private val _recentShareContacts = MutableStateFlow<List<CymbalUser>>(emptyList())
    override val recentShareContacts: StateFlow<List<CymbalUser>> = _recentShareContacts.asStateFlow()

    private val _isShareSearching = MutableStateFlow(false)
    override val isShareSearching: StateFlow<Boolean> = _isShareSearching.asStateFlow()

    private val _isLoadingShareContacts = MutableStateFlow(true)
    override val isLoadingShareContacts: StateFlow<Boolean> = _isLoadingShareContacts.asStateFlow()

    private var shareSearchJob: Job? = null

    override fun isOwnPost(post: CymbalPost): Boolean =
        post.user.id == authRepository.currentUserId

    override suspend fun fetchBackCover(postId: String): String? =
        postRepository.fetchBackCover(postId)

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
                        movie = post.toSharedMovie(),
                    )
                } else {
                    messageRepository.sendSharedTrackMessage(
                        threadId = threadId,
                        fromUserId = currentUserId,
                        text = text,
                        track = post.track,
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

    override fun onCleared() {
        super.onCleared()
        listeningPostId?.let { engagementManager.stopListening(it) }
    }
}
