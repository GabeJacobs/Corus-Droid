package fm.corus.android.ui.player

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import fm.corus.android.R
import fm.corus.android.data.model.CymbalComment
import fm.corus.android.data.model.CymbalPost
import fm.corus.android.data.model.CymbalTrack
import fm.corus.android.data.model.CymbalUser
import fm.corus.android.data.remote.CloudFunctionsDataSource
import fm.corus.android.data.repository.AuthRepository
import fm.corus.android.data.repository.MessageRepository
import fm.corus.android.data.repository.PostRepository
import fm.corus.android.data.repository.UserRepository
import fm.corus.android.domain.HapticManager
import fm.corus.android.domain.NowPlayingState
import fm.corus.android.domain.PostEngagementManager
import fm.corus.android.service.AnalyticsService
import fm.corus.android.ui.components.ToastManager
import fm.corus.android.ui.screens.feed.loadRecentDmShareContacts
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class OptimisticCommentEvent(
    val comment: CymbalComment,
    val parentCommentId: String?,
)

@HiltViewModel
class FullPlayerViewModel @Inject constructor(
    private val postRepository: PostRepository,
    private val authRepository: AuthRepository,
    private val engagementManager: PostEngagementManager,
    private val cloudFunctions: CloudFunctionsDataSource,
    private val hapticManager: HapticManager,
    private val userRepository: UserRepository,
    private val messageRepository: MessageRepository,
    val analyticsService: AnalyticsService,
    @ApplicationContext private val context: Context,
) : ViewModel() {
    val currentUserProfile: StateFlow<CymbalUser?> get() = authRepository.userProfile

    private val _sourcePost = MutableStateFlow<CymbalPost?>(null)
    val sourcePost: StateFlow<CymbalPost?> = _sourcePost.asStateFlow()

    private val _isLoadingSourcePost = MutableStateFlow(false)
    val isLoadingSourcePost: StateFlow<Boolean> = _isLoadingSourcePost.asStateFlow()

    private var loadedPostId: String? = null

    private val _comments = MutableStateFlow<List<CymbalComment>>(emptyList())
    val comments: StateFlow<List<CymbalComment>> = _comments.asStateFlow()

    private val _repliesByParent = MutableStateFlow<Map<String, List<CymbalComment>>>(emptyMap())
    val repliesByParent: StateFlow<Map<String, List<CymbalComment>>> = _repliesByParent.asStateFlow()

    private val _isLoadingComments = MutableStateFlow(false)
    val isLoadingComments: StateFlow<Boolean> = _isLoadingComments.asStateFlow()

    private val _isTransitioningComments = MutableStateFlow(false)
    val isTransitioningComments: StateFlow<Boolean> = _isTransitioningComments.asStateFlow()

    private val _likedCommentIds = MutableStateFlow<Set<String>>(emptySet())
    val likedCommentIds: StateFlow<Set<String>> = _likedCommentIds.asStateFlow()

    private val _commentLikeCounts = MutableStateFlow<Map<String, Int>>(emptyMap())
    val commentLikeCounts: StateFlow<Map<String, Int>> = _commentLikeCounts.asStateFlow()

    private val likeInFlightIds = mutableSetOf<String>()
    private var commentsLoadedPostId: String? = null
    private var commentsRefreshToken = 0
    private val _commentsRefreshEpoch = MutableStateFlow(0)
    val commentsRefreshEpoch: StateFlow<Int> = _commentsRefreshEpoch.asStateFlow()

    private val _catalogPosts = MutableStateFlow<List<CymbalPost>>(emptyList())
    val catalogPosts: StateFlow<List<CymbalPost>> = _catalogPosts.asStateFlow()

    private val _isLoadingCatalogPosts = MutableStateFlow(false)
    val isLoadingCatalogPosts: StateFlow<Boolean> = _isLoadingCatalogPosts.asStateFlow()

    private val _isLoadingMoreCatalogPosts = MutableStateFlow(false)
    val isLoadingMoreCatalogPosts: StateFlow<Boolean> = _isLoadingMoreCatalogPosts.asStateFlow()

    private val _catalogPostsError = MutableStateFlow<String?>(null)
    val catalogPostsError: StateFlow<String?> = _catalogPostsError.asStateFlow()

    private val _catalogHasMorePages = MutableStateFlow(false)
    val catalogHasMorePages: StateFlow<Boolean> = _catalogHasMorePages.asStateFlow()

    private val _catalogUniquePosterCount = MutableStateFlow<Int?>(null)
    val catalogUniquePosterCount: StateFlow<Int?> = _catalogUniquePosterCount.asStateFlow()

    private var catalogLoadedTrackId: String? = null
    private var catalogFirstPosterId: String? = null
    private var catalogPaginationCursor: Long? = null
    private var catalogSpotifyURI: String? = null
    private var catalogIsrc: String? = null
    private var catalogTrackName: String? = null
    private var catalogArtistName: String? = null
    private var catalogLoadJob: Job? = null

    private val catalogPageSize = 15

    fun loadSourcePost(postId: String?) {
        if (postId.isNullOrBlank()) {
            loadedPostId = null
            _sourcePost.value = null
            _isLoadingSourcePost.value = false
            clearComments()
            return
        }
        if (postId == loadedPostId && _sourcePost.value != null) return
        loadedPostId = postId
        _isLoadingSourcePost.value = true
        _sourcePost.value = null
        viewModelScope.launch {
            val uid = authRepository.currentUserId
            val post = if (uid != null) {
                runCatching { postRepository.getPostDetail(postId, uid) }.getOrNull()
            } else {
                null
            }
            if (loadedPostId == postId) {
                _sourcePost.value = post
                _isLoadingSourcePost.value = false
                if (post != null) {
                    engagementManager.initState(
                        postId = post.id,
                        likeCount = post.likeCount,
                        commentCount = post.commentCount,
                        repostCount = post.repostCount,
                        isLiked = post.isLiked,
                        isSaved = engagementManager.getState(post.id)?.isSaved ?: false,
                        saveCount = post.saveCount,
                    )
                    engagementManager.startListening(post.id)
                    uid?.let { engagementManager.checkSaveStatuses(listOf(post.id), it) }
                    loadComments(post.id, reason = CommentReloadReason.PostChanged)
                    fetchRecentLikersIfNeeded(post)
                } else {
                    clearComments()
                }
            }
        }
    }

    fun onPlaybackIdentityChanged(sourcePostId: String?, trackId: String?) {
        if (sourcePostId.isNullOrBlank()) {
            loadSourcePost(null)
            if (trackId.isNullOrBlank()) {
                clearCatalogPosts()
            }
        } else {
            clearCatalogPosts()
            loadSourcePost(sourcePostId)
        }
    }

    fun loadCatalogPostsIfNeeded(
        trackId: String?,
        spotifyURI: String? = null,
        isrc: String? = null,
        trackName: String? = null,
        artistName: String? = null,
        force: Boolean = false,
    ) {
        if (trackId.isNullOrBlank()) {
            clearCatalogPosts()
            return
        }
        if (!force && catalogLoadedTrackId == trackId) return
        catalogSpotifyURI = spotifyURI
        catalogIsrc = isrc
        catalogTrackName = trackName
        catalogArtistName = artistName
        catalogLoadJob?.cancel()
        catalogLoadJob = viewModelScope.launch {
            _isLoadingCatalogPosts.value = true
            _catalogPostsError.value = null
            _catalogPosts.value = emptyList()
            catalogPaginationCursor = null
            _catalogHasMorePages.value = false
            _catalogUniquePosterCount.value = null
            catalogFirstPosterId = null
            try {
                val page = postRepository.fetchSongPostsFromCloud(
                    trackId = trackId,
                    spotifyURI = spotifyURI,
                    isrc = isrc,
                    trackName = trackName,
                    artistName = artistName,
                    pageSize = catalogPageSize,
                )
                catalogFirstPosterId = page.firstPosterId
                _catalogUniquePosterCount.value = page.uniquePosterCount
                catalogPaginationCursor = page.posts.lastOrNull()?.timestamp?.time
                val unique = deduplicateByUser(page.posts)
                _catalogPosts.value = moveFirstPosterToTop(unique, page.firstPosterId)
                _catalogHasMorePages.value = page.posts.size >= catalogPageSize
                catalogLoadedTrackId = trackId
                _catalogPostsError.value = null
            } catch (_: Exception) {
                _catalogPostsError.value = "Couldn't load posts for this song."
                catalogLoadedTrackId = trackId
            }
            _isLoadingCatalogPosts.value = false
        }
    }

    fun loadMoreCatalogPosts() {
        val cursor = catalogPaginationCursor ?: return
        val trackId = catalogLoadedTrackId ?: return
        if (_isLoadingMoreCatalogPosts.value || !_catalogHasMorePages.value) return
        viewModelScope.launch {
            _isLoadingMoreCatalogPosts.value = true
            try {
                val page = postRepository.fetchSongPostsFromCloud(
                    trackId = trackId,
                    spotifyURI = catalogSpotifyURI,
                    isrc = catalogIsrc,
                    trackName = catalogTrackName,
                    artistName = catalogArtistName,
                    pageSize = catalogPageSize,
                    beforeMs = cursor,
                )
                catalogPaginationCursor = page.posts.lastOrNull()?.timestamp?.time
                val existingUserIds = _catalogPosts.value.map { it.user.id }.toSet()
                val newPosts = page.posts.filter { it.user.id !in existingUserIds }
                _catalogPosts.value = _catalogPosts.value + newPosts
                _catalogHasMorePages.value = page.posts.size >= catalogPageSize
            } catch (_: Exception) {
            }
            _isLoadingMoreCatalogPosts.value = false
        }
    }

    fun refreshComments() {
        val postId = _sourcePost.value?.id ?: return
        commentsRefreshToken += 1
        _commentsRefreshEpoch.value = commentsRefreshToken
        loadComments(postId, reason = CommentReloadReason.Refresh)
    }

    fun insertOptimisticComment(event: OptimisticCommentEvent) {
        insertOptimisticComment(event.comment, event.parentCommentId)
    }

    fun insertOptimisticComment(comment: CymbalComment, parentId: String?) {
        _isLoadingComments.value = false
        _isTransitioningComments.value = false
        if (_comments.value.any { it.id == comment.id }) return
        if (parentId != null &&
            _repliesByParent.value.values.any { replies -> replies.any { it.id == comment.id } }
        ) {
            return
        }
        if (parentId != null) {
            _repliesByParent.value = _repliesByParent.value.toMutableMap().apply {
                this[parentId] = (this[parentId] ?: emptyList()) + comment
            }
            _comments.value = _comments.value.map { parent ->
                if (parent.id == parentId) {
                    parent.copy(replyCount = parent.replyCount + 1)
                } else {
                    parent
                }
            }
        } else {
            _comments.value = _comments.value + comment
        }
        _commentLikeCounts.value = _commentLikeCounts.value + (comment.id to comment.likeCount)
    }

    fun toggleCommentLike(comment: CymbalComment) {
        val postId = _sourcePost.value?.id ?: return
        val uid = authRepository.currentUserId ?: return
        if (!likeInFlightIds.add(comment.id)) return
        // iOS FullPlayerCommentsSection — light on comment like.
        hapticManager.impact(HapticManager.ImpactStyle.LIGHT)
        val wasLiked = _likedCommentIds.value.contains(comment.id)
        val previousCount = _commentLikeCounts.value[comment.id] ?: comment.likeCount
        if (wasLiked) {
            _likedCommentIds.value = _likedCommentIds.value - comment.id
            _commentLikeCounts.value = _commentLikeCounts.value + (comment.id to maxOf(0, previousCount - 1))
        } else {
            _likedCommentIds.value = _likedCommentIds.value + comment.id
            _commentLikeCounts.value = _commentLikeCounts.value + (comment.id to previousCount + 1)
        }
        viewModelScope.launch {
            try {
                if (wasLiked) {
                    postRepository.unlikeComment(uid, postId, comment.id)
                } else {
                    postRepository.likeComment(uid, postId, comment.id)
                }
            } catch (_: Exception) {
                if (wasLiked) {
                    _likedCommentIds.value = _likedCommentIds.value + comment.id
                } else {
                    _likedCommentIds.value = _likedCommentIds.value - comment.id
                }
                _commentLikeCounts.value = _commentLikeCounts.value + (comment.id to previousCount)
            }
            likeInFlightIds.remove(comment.id)
        }
    }

    fun composeTrackFromNowPlaying(state: NowPlayingState): CymbalTrack? {
        val trackId = state.trackId ?: return null
        return CymbalTrack(
            id = trackId,
            name = state.trackName,
            artistName = state.artistName,
            albumName = "",
            albumArtURL = state.albumArtURL,
            albumArtLargeURL = state.albumArtLargeURL,
            spotifyURI = state.spotifyURI.orEmpty(),
            spotifyWebURL = state.spotifyWebURL.orEmpty(),
            isrc = state.isrc,
            source = state.source,
        )
    }

    private enum class CommentReloadReason {
        PostChanged,
        Refresh,
    }

    private fun loadComments(postId: String, reason: CommentReloadReason) {
        viewModelScope.launch {
            val switchingPost =
                reason == CommentReloadReason.PostChanged &&
                    commentsLoadedPostId != null &&
                    commentsLoadedPostId != postId
            val firstLoad = commentsLoadedPostId == null
            if (switchingPost) {
                _isTransitioningComments.value = true
                delay(180)
            } else if (firstLoad || commentsLoadedPostId != postId) {
                _isLoadingComments.value = true
            }

            val preserveOptimistic =
                reason == CommentReloadReason.Refresh || !switchingPost
            fetchComments(postId, preserveOptimistic)

            commentsLoadedPostId = postId
            _isLoadingComments.value = false
            _isTransitioningComments.value = false
        }
    }

    private suspend fun fetchComments(postId: String, preserveOptimistic: Boolean) {
        val preservedOptimistic = if (preserveOptimistic) {
            _comments.value.filter { it.id.startsWith("temp_") }
        } else {
            emptyList()
        }
        val preservedReplyOptimistic = if (preserveOptimistic) {
            _repliesByParent.value.mapValues { (_, replies) ->
                replies.filter { it.id.startsWith("temp_") }
            }
        } else {
            emptyMap()
        }
        try {
            val fetched = postRepository.getComments(postId)
            var topLevel = fetched.filter { it.parentCommentId == null }
            var replies = fetched
                .filter { it.parentCommentId != null }
                .groupBy { it.parentCommentId!! }
                .mapValues { (_, value) -> value.sortedBy { it.timestamp } }
                .toMutableMap()

            for (temp in preservedOptimistic) {
                val confirmed = topLevel.any { server ->
                    server.user.id == temp.user.id &&
                        server.text == temp.text &&
                        kotlin.math.abs(server.timestamp.time - temp.timestamp.time) < 60_000
                }
                if (!confirmed) {
                    topLevel = topLevel + temp
                }
            }
            for ((parentId, temps) in preservedReplyOptimistic) {
                for (temp in temps) {
                    val siblingReplies = replies[parentId] ?: emptyList()
                    val confirmed = siblingReplies.any { server ->
                        server.user.id == temp.user.id &&
                            server.text == temp.text &&
                            kotlin.math.abs(server.timestamp.time - temp.timestamp.time) < 60_000
                    }
                    if (!confirmed) {
                        replies[parentId] = (siblingReplies + temp).sortedBy { it.timestamp }
                    }
                }
            }

            _comments.value = topLevel
            _repliesByParent.value = replies
            _commentLikeCounts.value = fetched.associate { it.id to it.likeCount }
            val viewerFlagsPresent = fetched.any { it.likedByViewer != null }
            if (viewerFlagsPresent) {
                _likedCommentIds.value = fetched.filter { it.likedByViewer == true }.map { it.id }.toSet()
            } else {
                val uid = authRepository.currentUserId
                if (uid != null && fetched.isNotEmpty()) {
                    val liked = runCatching {
                        postRepository.checkCommentLikesBatch(
                            userId = uid,
                            postId = postId,
                            commentIds = fetched.map { it.id },
                        )
                    }.getOrDefault(emptySet())
                    _likedCommentIds.value = liked
                } else {
                    _likedCommentIds.value = emptySet()
                }
            }
        } catch (_: Exception) {
        }
    }

    private fun fetchRecentLikersIfNeeded(post: CymbalPost) {
        if (post.likers.isNotEmpty()) return
        if (post.likeCount <= 0 && !post.isLiked) return
        val postId = post.id
        viewModelScope.launch {
            val page = runCatching {
                postRepository.fetchPostLikers(postId, limit = 3)
            }.getOrNull() ?: return@launch
            if (loadedPostId != postId) return@launch
            val current = _sourcePost.value ?: return@launch
            if (current.id == postId && current.likers.isEmpty()) {
                _sourcePost.value = current.copy(likers = page.users.take(3))
            }
        }
    }

    private fun clearComments() {
        commentsLoadedPostId = null
        _comments.value = emptyList()
        _repliesByParent.value = emptyMap()
        _likedCommentIds.value = emptySet()
        _commentLikeCounts.value = emptyMap()
        _isLoadingComments.value = false
        _isTransitioningComments.value = false
    }

    private fun clearCatalogPosts() {
        catalogLoadJob?.cancel()
        catalogLoadJob = null
        _catalogPosts.value = emptyList()
        catalogLoadedTrackId = null
        _catalogPostsError.value = null
        catalogPaginationCursor = null
        _catalogHasMorePages.value = false
        _catalogUniquePosterCount.value = null
        catalogFirstPosterId = null
        _isLoadingCatalogPosts.value = false
        _isLoadingMoreCatalogPosts.value = false
    }

    private fun deduplicateByUser(posts: List<CymbalPost>): List<CymbalPost> {
        val seen = mutableSetOf<String>()
        return posts.filter { seen.add(it.user.id) }
    }

    private fun moveFirstPosterToTop(posts: List<CymbalPost>, firstPosterId: String?): List<CymbalPost> {
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

    /** Overflow "Go to Artist" / "Go to Album" — same resolve as feed post menus. */
    suspend fun resolveTrackDestinationsForTrack(track: CymbalTrack): CloudFunctionsDataSource.TrackDestinations =
        cloudFunctions.resolveTrackDestinations(
            track.id, track.isrc, track.name, track.artistName, track.appleMusicId,
        )

    suspend fun resolveArtistIdForTrack(track: CymbalTrack): String? =
        resolveTrackDestinationsForTrack(track).artistIds.firstOrNull { it.isNotBlank() }

    // ── Song share sheet (overflow Share) ──
    // Same recipient-picker plumbing as SongDetailViewModel — DMs send a
    // `sharedTrack` message that deep-links to the song page.

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
        loadRecentDmShareContacts(
            userId = userId,
            messageRepository = messageRepository,
            currentContacts = _recentShareContacts.value,
            setContacts = { _recentShareContacts.value = it },
            setLoading = { _isLoadingShareContacts.value = it },
            scope = viewModelScope,
        )
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

    fun sendTrackToUser(userId: String, track: CymbalTrack, message: String) {
        val currentUserId = authRepository.currentUserId ?: return
        viewModelScope.launch {
            try {
                val threadId = messageRepository.getOrCreateThread(currentUserId, userId)
                messageRepository.sendSharedTrackMessage(
                    threadId = threadId,
                    fromUserId = currentUserId,
                    text = message.trim(),
                    track = track,
                )
            } catch (_: Exception) {
                ToastManager.show(context.getString(R.string.feed_toast_failed_send_post))
            }
        }
    }
}
