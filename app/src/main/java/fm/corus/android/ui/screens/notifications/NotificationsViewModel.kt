package fm.corus.android.ui.screens.notifications

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import fm.corus.android.R
import fm.corus.android.data.model.CommentAttachedFilm
import fm.corus.android.data.model.CommentAttachedSong
import fm.corus.android.data.model.CymbalMovie
import fm.corus.android.data.model.CymbalNotification
import fm.corus.android.data.model.CymbalTrack
import fm.corus.android.data.repository.AuthRepository
import fm.corus.android.data.repository.NotificationRepository
import fm.corus.android.data.repository.PostRepository
import fm.corus.android.data.repository.UserRepository
import fm.corus.android.domain.PostEngagementManager
import fm.corus.android.service.AnalyticsService
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import android.util.Log
import javax.inject.Inject

@HiltViewModel
class NotificationsViewModel @Inject constructor(
    private val notificationRepository: NotificationRepository,
    private val authRepository: AuthRepository,
    private val userRepository: UserRepository,
    private val postRepository: PostRepository,
    private val engagementManager: PostEngagementManager,
    private val analyticsService: AnalyticsService,
    @ApplicationContext private val context: Context,
) : ViewModel() {

    private val pageSize = 15

    private val _notifications = MutableStateFlow<List<CymbalNotification>>(emptyList())
    val notifications: StateFlow<List<CymbalNotification>> = _notifications.asStateFlow()

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    private val _isLoadingMore = MutableStateFlow(false)
    val isLoadingMore: StateFlow<Boolean> = _isLoadingMore.asStateFlow()

    private val _hasMoreNotifications = MutableStateFlow(true)
    val hasMoreNotifications: StateFlow<Boolean> = _hasMoreNotifications.asStateFlow()

    val followingIds: StateFlow<Set<String>> = userRepository.followingIds

    private val _likedCommentIds = MutableStateFlow<Set<String>>(emptySet())
    val likedCommentIds: StateFlow<Set<String>> = _likedCommentIds.asStateFlow()

    private val _replyingToNotification = MutableStateFlow<CymbalNotification?>(null)
    val replyingToNotification: StateFlow<CymbalNotification?> = _replyingToNotification.asStateFlow()

    private val _isSendingReply = MutableStateFlow(false)
    val isSendingReply: StateFlow<Boolean> = _isSendingReply.asStateFlow()

    // ── Reply attachment state ──
    private val _replyPendingSong = MutableStateFlow<CommentAttachedSong?>(null)
    val replyPendingSong: StateFlow<CommentAttachedSong?> = _replyPendingSong.asStateFlow()

    private val _replyPendingFilm = MutableStateFlow<CommentAttachedFilm?>(null)
    val replyPendingFilm: StateFlow<CommentAttachedFilm?> = _replyPendingFilm.asStateFlow()

    fun attachReplySong(track: CymbalTrack) {
        _replyPendingSong.value = CommentAttachedSong.fromTrack(track)
        _replyPendingFilm.value = null
    }

    fun attachReplyFilm(movie: CymbalMovie) {
        _replyPendingFilm.value = CommentAttachedFilm.fromMovie(movie)
        _replyPendingSong.value = null
    }

    fun clearReplyAttachment() {
        _replyPendingSong.value = null
        _replyPendingFilm.value = null
    }

    /** One-shot "Reply sent" / "Failed to send reply" toast events, consumed by the screen. */
    private val _replyToastEvents = Channel<String>(Channel.BUFFERED)
    val replyToastEvents: Flow<String> = _replyToastEvents.receiveAsFlow()

    /**
     * Client-side set of notification IDs that arrived since the user last
     * visited the Activity tab. Drives the subtle accent-tint background on
     * rows the user hasn't interacted with yet — matches iOS
     * `newNotificationIds`. Cleared when the user taps a row, and reset
     * each time the ViewModel is re-created.
     */
    private val _newNotificationIds = MutableStateFlow<Set<String>>(emptySet())
    val newNotificationIds: StateFlow<Set<String>> = _newNotificationIds.asStateFlow()

    private var hasStartedLoading = false
    /** Captured before updating the user doc so we can flag later-arriving
     * notifications as "new" relative to the previous seen cutoff. */
    private var lastSeenCutoffMs: Long? = null
    /** Tracks whether we've already computed the new-id set for this session —
     * subsequent listener emissions update counts but don't reshuffle the
     * highlight set. */
    private var hasComputedNewIds = false

    fun refreshNotifications() {
        val userId = authRepository.currentUserId ?: return
        viewModelScope.launch {
            _isRefreshing.value = true
            try {
                _notifications.value = notificationRepository.getNotifications(userId, limit = pageSize)
                _hasMoreNotifications.value = _notifications.value.size >= pageSize
                loadCommentLikeStatuses(userId)
            } catch (_: Exception) { }
            _isRefreshing.value = false
        }
    }

    fun loadNotifications() {
        if (hasStartedLoading) return
        hasStartedLoading = true
        val userId = authRepository.currentUserId ?: return
        viewModelScope.launch {
            _isLoading.value = true
            // Capture the previous "seen" cutoff BEFORE stamping the new one,
            // so we can flag later-arriving notifications as new for dim styling.
            lastSeenCutoffMs = try {
                notificationRepository.fetchLastSeenNotificationsAt(userId)
            } catch (_: Exception) { null }
            // Fire-and-forget: stamp lastSeen + mark all read. Matches iOS
            // NotificationsView.loadNotifications.
            viewModelScope.launch {
                try { notificationRepository.updateLastSeenNotificationsAt(userId) } catch (_: Exception) { }
            }
            markAllRead()
            try {
                // Use real-time listener for the first page
                notificationRepository.observeNotifications(userId, limit = pageSize).collect { incoming ->
                    computeNewIdsOnce(incoming)
                    mergeNotifications(incoming)
                    _hasMoreNotifications.value = incoming.size >= pageSize
                    _isLoading.value = false
                    loadCommentLikeStatuses(userId)
                }
            } catch (_: Exception) {
                // Fallback to one-shot fetch
                try {
                    val fetched = notificationRepository.getNotifications(userId, limit = pageSize)
                    computeNewIdsOnce(fetched)
                    _notifications.value = fetched
                    _hasMoreNotifications.value = fetched.size >= pageSize
                    loadCommentLikeStatuses(userId)
                } catch (_: Exception) { }
                _isLoading.value = false
            }
        }
    }

    /**
     * On the first batch of notifications we receive, compute the set of
     * "new since last seen" ids. A notification is highlighted only if it
     * is still unread AND (there's no prior cutoff, or its timestamp is
     * newer than the cutoff). The isRead check is essential: once the user
     * taps a notification (or the tab auto-marks all read), that `isRead=true`
     * state persists server-side — we must respect it on the next launch even
     * though the timestamp is still newer than the previous `lastSeen` stamp.
     */
    private fun computeNewIdsOnce(items: List<CymbalNotification>) {
        if (hasComputedNewIds) return
        hasComputedNewIds = true
        val cutoff = lastSeenCutoffMs
        val ids = items.filter { notif ->
            !notif.isRead && (cutoff == null || notif.timestamp.time > cutoff)
        }.map { it.id }
        _newNotificationIds.value = ids.toSet()
    }

    /** Removes a single notification from the "new" set — called when the user
     * taps the row. Mirrors iOS `markAsRead(notification)` behaviour. */
    fun markNotificationTapped(notificationId: String) {
        if (_newNotificationIds.value.contains(notificationId)) {
            _newNotificationIds.value = _newNotificationIds.value - notificationId
        }
        viewModelScope.launch {
            try { notificationRepository.markNotificationRead(notificationId) } catch (_: Exception) { }
        }
        // Taste-match-specific tap analytics (mirrors iOS).
        val n = _notifications.value.firstOrNull { it.id == notificationId } ?: return
        if (n.type == fm.corus.android.data.model.NotificationType.TASTE_MATCH) {
            analyticsService.logTasteMatchFeedRowTapped(
                subtype = n.subtype ?: "unknown",
                fromUserId = n.fromUser.id,
            )
        }
    }

    /** Fires once when a taste_match row appears in the activity feed. */
    fun markTasteMatchRowViewed(notification: CymbalNotification) {
        if (notification.type != fm.corus.android.data.model.NotificationType.TASTE_MATCH) return
        analyticsService.logTasteMatchFeedRowViewed(
            subtype = notification.subtype ?: "unknown",
            fromUserId = notification.fromUser.id,
        )
    }

    /**
     * Merges incoming real-time listener results (first page) with existing
     * paginated items, matching iOS behaviour. Only the "head" window is
     * reconciled — older paginated "tail" items are kept untouched.
     */
    private fun mergeNotifications(incoming: List<CymbalNotification>) {
        val current = _notifications.value
        if (current.isEmpty()) {
            _notifications.value = incoming
            Log.d("Notifications", "merge: initial set, ${incoming.size} items")
            return
        }

        val incomingIds = incoming.map { it.id }.toSet()

        // Split: items in the incoming window vs older paginated items
        val tailItems = current.filter { it.id !in incomingIds }

        Log.d("Notifications", "merge: incoming=${incoming.size}, tail=${tailItems.size}, total=${incoming.size + tailItems.size}")

        // Build head from incoming order (includes new + updated items)
        _notifications.value = incoming + tailItems
    }

    fun loadMoreNotifications() {
        val userId = authRepository.currentUserId ?: return
        Log.d("Notifications", "loadMore called: isLoadingMore=${_isLoadingMore.value}, hasMore=${_hasMoreNotifications.value}, count=${_notifications.value.size}")
        if (_isLoadingMore.value || !_hasMoreNotifications.value) return
        val lastTimestamp = _notifications.value.lastOrNull()?.timestamp?.time
        if (lastTimestamp == null) {
            Log.d("Notifications", "loadMore: no lastTimestamp, aborting")
            return
        }
        Log.d("Notifications", "loadMore: fetching with lastTimestamp=$lastTimestamp")

        viewModelScope.launch {
            _isLoadingMore.value = true
            try {
                val fetched = notificationRepository.getNotifications(
                    userId, limit = pageSize, lastTimestamp = lastTimestamp,
                )
                Log.d("Notifications", "loadMore: fetched ${fetched.size} items")
                val existingIds = _notifications.value.map { it.id }.toSet()
                val newItems = fetched.filter { it.id !in existingIds }
                Log.d("Notifications", "loadMore: ${newItems.size} new items after dedup")
                _notifications.value = _notifications.value + newItems
                _hasMoreNotifications.value = fetched.size >= pageSize && newItems.isNotEmpty()
                Log.d("Notifications", "loadMore: hasMore=${_hasMoreNotifications.value}, total=${_notifications.value.size}")
                loadCommentLikeStatuses(userId)
            } catch (e: Exception) {
                Log.e("Notifications", "loadMore failed", e)
                _hasMoreNotifications.value = false
            }
            _isLoadingMore.value = false
        }
    }

    fun toggleFollow(targetUserId: String) {
        val currentUserId = authRepository.currentUserId ?: return
        viewModelScope.launch {
            val wasFollowing = userRepository.isFollowing(targetUserId)
            try {
                if (wasFollowing) {
                    userRepository.unfollowUser(currentUserId, targetUserId)
                } else {
                    userRepository.followUser(currentUserId, targetUserId)
                }
            } catch (_: Exception) { }
        }
    }

    fun markAllRead() {
        val userId = authRepository.currentUserId ?: return
        viewModelScope.launch {
            try { notificationRepository.markAllRead(userId) } catch (_: Exception) { }
        }
    }

    // ── Comment actions on notification rows (matches iOS NotificationsView) ──

    /**
     * Loads the current user's like status for each comment referenced by a
     * notification that supports comment actions. Mirrors iOS
     * `loadCommentLikeStatuses` — Firestore doesn't return isCommentLiked, so
     * we query the subcollection per (postId, commentId).
     */
    private fun loadCommentLikeStatuses(userId: String) {
        val pending = _notifications.value
            .filter { it.supportsCommentActions }
            .mapNotNull { notif ->
                val pid = notif.postId
                val cid = notif.commentId
                if (pid != null && cid != null && cid !in _likedCommentIds.value) pid to cid else null
            }
            .distinct()
        if (pending.isEmpty()) return

        viewModelScope.launch {
            val results = pending.map { (pid, cid) ->
                async {
                    cid to (try { postRepository.isCommentLiked(userId, pid, cid) } catch (_: Exception) { false })
                }
            }.awaitAll()
            val likedIds = results.filter { it.second }.map { it.first }.toSet()
            if (likedIds.isNotEmpty()) {
                _likedCommentIds.value = _likedCommentIds.value + likedIds
            }
        }
    }

    fun toggleCommentLike(notification: CymbalNotification) {
        val userId = authRepository.currentUserId ?: return
        val postId = notification.postId ?: return
        val commentId = notification.commentId ?: return

        val wasLiked = _likedCommentIds.value.contains(commentId)
        // Optimistic update
        _likedCommentIds.value = if (wasLiked) {
            _likedCommentIds.value - commentId
        } else {
            _likedCommentIds.value + commentId
        }

        viewModelScope.launch {
            try {
                if (wasLiked) {
                    postRepository.unlikeComment(userId, postId, commentId)
                } else {
                    postRepository.likeComment(userId, postId, commentId)
                    // Send comment_like notification to the comment owner.
                    // The comment owner is whoever the notification was originally
                    // *from* for COMMENT/REPLY/MENTION (they wrote the comment).
                    // For COMMENT_LIKE, the current user wrote the comment, so
                    // we skip (can't like-notify yourself).
                    if (notification.type != fm.corus.android.data.model.NotificationType.COMMENT_LIKE) {
                        try {
                            val post = postRepository.getCachedPost(postId)
                            postRepository.createNotification(
                                type = "comment_like",
                                fromUserId = userId,
                                toUserId = notification.fromUser.id,
                                postId = postId,
                                postAlbumArtURL = post?.displayImageURL,
                                commentId = commentId,
                            )
                        } catch (_: Exception) { }
                    }
                }
            } catch (_: Exception) {
                // Revert on failure
                _likedCommentIds.value = if (wasLiked) {
                    _likedCommentIds.value + commentId
                } else {
                    _likedCommentIds.value - commentId
                }
            }
        }
    }

    fun setReplyingToNotification(notification: CymbalNotification?) {
        _replyingToNotification.value = notification
    }

    fun sendReply(text: String) {
        val userId = authRepository.currentUserId ?: return
        val notification = _replyingToNotification.value ?: return
        val postId = notification.postId ?: return
        val parentCommentId = notification.commentId ?: return
        val trimmed = text.trim()
        val attachedSong = _replyPendingSong.value
        val attachedFilm = _replyPendingFilm.value
        if (trimmed.isEmpty() && attachedSong == null && attachedFilm == null) return

        // Optimistically dismiss the reply bar so the user sees immediate
        // acknowledgment even if the network call is slow. A Toast confirms
        // the final outcome (success or failure).
        _replyingToNotification.value = null
        _replyPendingSong.value = null
        _replyPendingFilm.value = null
        _isSendingReply.value = true

        viewModelScope.launch {
            try {
                val newCommentId = postRepository.addComment(
                    postId = postId,
                    userId = userId,
                    text = trimmed,
                    parentCommentId = parentCommentId,
                    replyToUserId = notification.fromUser.id,
                    attachedSong = attachedSong,
                    attachedFilm = attachedFilm,
                )
                engagementManager.incrementCommentCount(postId)

                // Create reply notification to the parent comment author. Push copy
                // suppresses synthesized fallback text so it reads "shared a song/film".
                try {
                    val post = postRepository.getCachedPost(postId)
                    val attachmentType = when {
                        attachedSong != null -> "song"
                        attachedFilm != null -> "film"
                        else -> null
                    }
                    val truncated = when {
                        trimmed.isNotEmpty() ->
                            if (trimmed.length > 100) trimmed.take(100) + "…" else trimmed
                        attachmentType == "song" -> "shared a song"
                        attachmentType == "film" -> "shared a film"
                        else -> ""
                    }
                    postRepository.createNotification(
                        type = "reply",
                        fromUserId = userId,
                        toUserId = notification.fromUser.id,
                        postId = postId,
                        postAlbumArtURL = post?.displayImageURL,
                        commentText = truncated,
                        commentId = newCommentId,
                        attachmentType = attachmentType,
                    )
                } catch (_: Exception) { }

                _replyToastEvents.trySend(context.getString(R.string.notifications_toast_reply_sent))
            } catch (e: Exception) {
                Log.e("Notifications", "Failed to send reply", e)
                _replyToastEvents.trySend(context.getString(R.string.notifications_toast_reply_failed))
                // Restore so the user can retry
                _replyingToNotification.value = notification
                _replyPendingSong.value = attachedSong
                _replyPendingFilm.value = attachedFilm
            }
            _isSendingReply.value = false
        }
    }
}
