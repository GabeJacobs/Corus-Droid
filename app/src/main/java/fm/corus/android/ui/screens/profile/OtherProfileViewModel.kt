package fm.corus.android.ui.screens.profile

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import fm.corus.android.R
import fm.corus.android.data.model.CymbalPost
import fm.corus.android.data.model.CymbalUser
import fm.corus.android.data.model.MediaType
import fm.corus.android.data.model.MusicMatchData
import fm.corus.android.data.remote.CloudFunctionsDataSource
import fm.corus.android.data.repository.AuthRepository
import fm.corus.android.data.repository.PostRepository
import fm.corus.android.data.repository.SubscriptionRepository
import fm.corus.android.data.repository.UserRepository
import fm.corus.android.domain.CommentDeletedEvent
import fm.corus.android.domain.CommentEditedEvent
import fm.corus.android.domain.NowPlayingManager
import fm.corus.android.domain.PostDeletionEvent
import fm.corus.android.domain.PostEngagementManager
import fm.corus.android.ui.components.ToastManager
import fm.corus.android.ui.screens.feed.applyCommentDeleteToPosts
import fm.corus.android.ui.screens.feed.applyCommentEditToPosts
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class OtherProfileViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val userRepository: UserRepository,
    private val postRepository: PostRepository,
    private val authRepository: AuthRepository,
    val nowPlayingManager: NowPlayingManager,
    private val cloudFunctions: fm.corus.android.data.remote.CloudFunctionsDataSource,
    val musicServicePreference: fm.corus.android.domain.MusicServicePreference,
    private val engagementManager: PostEngagementManager,
    private val subscriptionRepository: SubscriptionRepository,
    private val postDeletionEvent: PostDeletionEvent,
    private val commentEditedEvent: CommentEditedEvent,
    private val commentDeletedEvent: CommentDeletedEvent,
) : ViewModel() {

    /**
     * Resolve the link-out URL for a Spotify-source track given the viewer's
     * preferred service (Apple Music / TIDAL / Deezer), for the featured-post
     * service-logo tap. Returns null for Spotify / no-match. See FeedViewModel.
     */
    suspend fun resolveServiceLinkUrl(track: fm.corus.android.data.model.CymbalTrack): String? =
        fm.corus.android.domain.MusicServiceLinkOut.resolveLinkOutUrl(
            track, musicServicePreference.current.value, cloudFunctions,
        )

    val hasFullAccess = subscriptionRepository.hasFullAccessFlow

    init {
        viewModelScope.launch {
            postDeletionEvent.events.collect { deletedId ->
                _posts.value = _posts.value.filter { it.id != deletedId }
            }
        }
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

    fun generatePlaylist(
        userId: String,
        source: CloudFunctionsDataSource.ProfilePlaylistSource = CloudFunctionsDataSource.ProfilePlaylistSource.Posts,
    ) {
        viewModelScope.launch {
            nowPlayingManager.generateProfilePlaylist(userId, source)
        }
    }

    private val _profile = MutableStateFlow<CymbalUser?>(null)
    val profile: StateFlow<CymbalUser?> = _profile.asStateFlow()

    private val _posts = MutableStateFlow<List<CymbalPost>>(emptyList())
    val posts: StateFlow<List<CymbalPost>> = _posts.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _isLoadingMore = MutableStateFlow(false)
    val isLoadingMore: StateFlow<Boolean> = _isLoadingMore.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    private val _hasMore = MutableStateFlow(true)
    val hasMore: StateFlow<Boolean> = _hasMore.asStateFlow()

    private val _isLoadingFilms = MutableStateFlow(false)
    val isLoadingFilms: StateFlow<Boolean> = _isLoadingFilms.asStateFlow()

    private val _hasFetchedFilmPage = MutableStateFlow(false)
    val hasFetchedFilmPage: StateFlow<Boolean> = _hasFetchedFilmPage.asStateFlow()

    private var postsLastTimestamp: Long? = null
    private val PAGE_SIZE = 30
    private val _isFollowing = MutableStateFlow(false)
    val isFollowing: StateFlow<Boolean> = _isFollowing.asStateFlow()


    private val _isBlocked = MutableStateFlow(false)
    val isBlocked: StateFlow<Boolean> = _isBlocked.asStateFlow()

    private val _isMuted = MutableStateFlow(false)
    val isMuted: StateFlow<Boolean> = _isMuted.asStateFlow()

    private val _isSubscribedToNotifications = MutableStateFlow(false)
    val isSubscribedToNotifications: StateFlow<Boolean> = _isSubscribedToNotifications.asStateFlow()

    // Taste-match payload alongside the target user — drives the profile teaser
    // pill and the detail sheet. Null when there's no overlap (or on own-profile).
    private val _matchData = MutableStateFlow<MusicMatchData?>(null)
    val matchData: StateFlow<MusicMatchData?> = _matchData.asStateFlow()

    val currentUserId: String? get() = authRepository.currentUserId

    val engagementStates = engagementManager.states

    fun toggleLike(postId: String) {
        val userId = authRepository.currentUserId ?: return
        engagementManager.toggleLike(postId, userId)
    }

    // Track which posts have active real-time listeners (matching iOS PostEngagementStore)
    private val activeListenerPostIds = mutableSetOf<String>()

    // Tracks the userId this ViewModel has loaded data for. Used to skip
    // redundant fetches when the composable re-enters composition after
    // forward-then-back navigation (e.g. profile → profile feed → back).
    private var loadedUserId: String? = null

    fun start(userId: String, initialIsFollowing: Boolean?) {
        if (loadedUserId == userId) return
        if (initialIsFollowing != null) _isFollowing.value = initialIsFollowing
        loadProfile(userId)
    }

    fun loadProfile(userId: String) {
        loadedUserId = userId
        viewModelScope.launch {
            _isLoading.value = true
            try {
                _isFollowing.value = userRepository.isFollowing(userId)
                _isBlocked.value = userRepository.blockedIds.value.contains(userId)
                _isMuted.value = userRepository.isUserMuted(userId)
                val viewerIdForSub = authRepository.currentUserId
                if (viewerIdForSub != null) {
                    _isSubscribedToNotifications.value = userRepository.isSubscribedToUserPosts(viewerIdForSub, userId)
                }

                val viewerId = authRepository.currentUserId ?: return@launch

                // Single round-trip: user (with live cymbalCount via count() aggregation)
                // and the first page of posts come from the same backend call, so the
                // header count and the grid can't disagree on cold load. If this fails,
                // fall back to the legacy two-call path.
                val page: List<CymbalPost> = try {
                    val data = postRepository.getProfileData(userId = userId, pageSize = PAGE_SIZE)
                    if (data.user != null) {
                        _profile.value = data.user
                        _matchData.value = data.match
                        data.posts
                    } else {
                        _profile.value = userRepository.fetchUserProfile(userId)
                        postRepository.getProfilePosts(
                            userId = userId,
                            viewerId = viewerId,
                            limit = PAGE_SIZE,
                            lastTimestamp = null,
                        )
                    }
                } catch (_: Exception) {
                    _profile.value = userRepository.fetchUserProfile(userId)
                    postRepository.getProfilePosts(
                        userId = userId,
                        viewerId = viewerId,
                        limit = PAGE_SIZE,
                        lastTimestamp = null,
                    )
                }

                _posts.value = page
                if (page.isNotEmpty()) postsLastTimestamp = page.last().timestamp.time
                _hasMore.value = page.size >= PAGE_SIZE

                page.forEach { post ->
                    engagementManager.initState(
                        postId = post.id,
                        likeCount = post.likeCount,
                        commentCount = post.commentCount,
                        repostCount = post.repostCount,
                        isLiked = post.isLiked,
                        isSaved = false,
                    )
                    if (activeListenerPostIds.add(post.id)) {
                        engagementManager.startListening(post.id)
                    }
                }
                engagementManager.checkLikeStatuses(page.map { it.id }, viewerId)
            } catch (_: Exception) { }
            _isLoading.value = false
        }
    }

    fun refresh(userId: String, includeFilms: Boolean = false) {
        if (_isRefreshing.value) return
        val viewerId = authRepository.currentUserId ?: return
        loadedUserId = userId
        // Only reset the film-fetch flag if there's actually something to re-fetch.
        // The counter (when available) is authoritative; fall back to the free
        // guard for older backends without the field.
        val knownZeroFilms = _profile.value?.movieCount == 0
        val certainlyZeroFilms = knownZeroFilms ||
            (!_hasMore.value && _posts.value.none { it.mediaType == MediaType.MOVIE })
        if (!certainlyZeroFilms) {
            _hasFetchedFilmPage.value = false
        }
        _isRefreshing.value = true
        viewModelScope.launch {
            try {
                val user = userRepository.fetchUserProfile(userId)
                _profile.value = user
                _isFollowing.value = userRepository.isFollowing(userId)
                _isBlocked.value = userRepository.blockedIds.value.contains(userId)
                _isMuted.value = userRepository.isUserMuted(userId)
                _isSubscribedToNotifications.value =
                    userRepository.isSubscribedToUserPosts(viewerId, userId)

                val page = postRepository.getProfilePosts(
                    userId = userId,
                    viewerId = viewerId,
                    limit = PAGE_SIZE,
                    lastTimestamp = null,
                )
                val movieSupplement = if (includeFilms) {
                    runCatching {
                        postRepository.getProfilePosts(
                            userId = userId,
                            viewerId = viewerId,
                            limit = PAGE_SIZE,
                            lastTimestamp = null,
                            mediaType = "movie",
                        )
                    }.getOrDefault(emptyList())
                } else emptyList()

                postsLastTimestamp = if (page.isNotEmpty()) page.last().timestamp.time else null
                _hasMore.value = page.size >= PAGE_SIZE
                val merged = page + movieSupplement.filter { m -> page.none { it.id == m.id } }
                _posts.value = merged
                if (includeFilms) _hasFetchedFilmPage.value = true

                merged.forEach { post ->
                    engagementManager.initState(
                        postId = post.id,
                        likeCount = post.likeCount,
                        commentCount = post.commentCount,
                        repostCount = post.repostCount,
                        isLiked = post.isLiked,
                        isSaved = false,
                    )
                    if (activeListenerPostIds.add(post.id)) {
                        engagementManager.startListening(post.id)
                    }
                }
                engagementManager.checkLikeStatuses(merged.map { it.id }, viewerId)
            } catch (_: Exception) { }
            _isRefreshing.value = false
        }
    }

    fun loadFilmPageIfNeeded(userId: String) {
        if (_hasFetchedFilmPage.value) return
        val viewerId = authRepository.currentUserId ?: return
        val hasAnyMovies = _posts.value.any { it.mediaType == MediaType.MOVIE }
        // Prefer the counter (authoritative); fall back to the "all posts loaded,
        // none are films" free guard for older backends without the field.
        val knownZeroFilms = _profile.value?.movieCount == 0
        if (knownZeroFilms || (!_hasMore.value && !hasAnyMovies)) {
            _hasFetchedFilmPage.value = true
            return
        }
        _hasFetchedFilmPage.value = true
        if (!hasAnyMovies) _isLoadingFilms.value = true
        viewModelScope.launch {
            try {
                val movies = postRepository.getProfilePosts(
                    userId = userId,
                    viewerId = viewerId,
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
                        if (activeListenerPostIds.add(post.id)) {
                            engagementManager.startListening(post.id)
                        }
                    }
                    engagementManager.checkLikeStatuses(additions.map { it.id }, viewerId)
                }
            } catch (_: Exception) {
                _hasFetchedFilmPage.value = false
            }
            _isLoadingFilms.value = false
        }
    }

    fun loadMore(userId: String) {
        if (!_hasMore.value || _isLoadingMore.value) return
        val viewerId = authRepository.currentUserId ?: return
        val cursor = postsLastTimestamp ?: return
        _isLoadingMore.value = true
        viewModelScope.launch {
            try {
                val newPosts = postRepository.getProfilePosts(
                    userId = userId,
                    viewerId = viewerId,
                    limit = PAGE_SIZE,
                    lastTimestamp = cursor,
                )
                _posts.value = _posts.value + newPosts
                if (newPosts.isNotEmpty()) {
                    postsLastTimestamp = newPosts.last().timestamp.time
                }
                _hasMore.value = newPosts.size >= PAGE_SIZE

                newPosts.forEach { post ->
                    engagementManager.initState(
                        postId = post.id,
                        likeCount = post.likeCount,
                        commentCount = post.commentCount,
                        repostCount = post.repostCount,
                        isLiked = post.isLiked,
                        isSaved = false,
                    )
                    if (activeListenerPostIds.add(post.id)) {
                        engagementManager.startListening(post.id)
                    }
                }
                engagementManager.checkLikeStatuses(newPosts.map { it.id }, viewerId)
            } catch (_: Exception) { }
            _isLoadingMore.value = false
        }
    }

    fun toggleFollow(userId: String) {
        val currentUserId = authRepository.currentUserId ?: return
        val wasFollowing = _isFollowing.value
        // Optimistic UI — flip immediately, roll back on failure.
        _isFollowing.value = !wasFollowing
        _profile.value = _profile.value?.copy(
            followerCount = if (wasFollowing) {
                maxOf(0, (_profile.value?.followerCount ?: 1) - 1)
            } else {
                (_profile.value?.followerCount ?: 0) + 1
            }
        )
        viewModelScope.launch {
            try {
                if (wasFollowing) {
                    userRepository.unfollowUser(currentUserId, userId)
                } else {
                    userRepository.followUser(currentUserId, userId)
                }
            } catch (e: Exception) {
                _isFollowing.value = wasFollowing
                _profile.value = _profile.value?.copy(
                    followerCount = if (wasFollowing) {
                        (_profile.value?.followerCount ?: 0) + 1
                    } else {
                        maxOf(0, (_profile.value?.followerCount ?: 1) - 1)
                    }
                )
                if (e is CloudFunctionsDataSource.FollowLimitReachedException) {
                    ToastManager.show(buildFollowLimitMessage(e))
                }
            }
        }
    }

    private fun buildFollowLimitMessage(
        e: CloudFunctionsDataSource.FollowLimitReachedException,
    ): String = when (val d = describeFollowLimitRetry(e.retryAfterSeconds)) {
        is FollowLimitDuration.Hours ->
            context.getString(R.string.follow_limit_reached_hours, e.dailyLimit, d.count)
        is FollowLimitDuration.Minutes ->
            context.getString(R.string.follow_limit_reached_minutes, e.dailyLimit, d.count)
        FollowLimitDuration.Soon ->
            context.getString(R.string.follow_limit_reached_soon, e.dailyLimit)
    }

    fun blockUser(userId: String) {
        val currentUserId = authRepository.currentUserId ?: return
        viewModelScope.launch {
            try {
                userRepository.blockUser(currentUserId, userId)
                _isBlocked.value = true
            } catch (_: Exception) { }
        }
    }

    fun unblockUser(userId: String) {
        val currentUserId = authRepository.currentUserId ?: return
        viewModelScope.launch {
            try {
                userRepository.unblockUser(currentUserId, userId)
                _isBlocked.value = false
            } catch (_: Exception) { }
        }
    }

    suspend fun fetchUserIdByUsername(username: String): String? {
        return try {
            userRepository.fetchUserByUsername(username)?.id
        } catch (_: Exception) { null }
    }

    fun togglePostNotifications(userId: String) {
        val currentUserId = authRepository.currentUserId ?: return
        val wasSubscribed = _isSubscribedToNotifications.value
        _isSubscribedToNotifications.value = !wasSubscribed
        viewModelScope.launch {
            try {
                if (wasSubscribed) {
                    userRepository.unsubscribeFromUserPosts(currentUserId, userId)
                } else {
                    userRepository.subscribeToUserPosts(currentUserId, userId)
                }
            } catch (_: Exception) {
                _isSubscribedToNotifications.value = wasSubscribed
            }
        }
    }

    fun toggleMute(userId: String) {
        val currentUserId = authRepository.currentUserId ?: return
        val wasMuted = _isMuted.value
        _isMuted.value = !wasMuted
        val username = _profile.value?.username
        val loadingMessage = if (!wasMuted) {
            if (username != null) context.getString(R.string.other_profile_muting_format, username) else context.getString(R.string.other_profile_muting)
        } else {
            if (username != null) context.getString(R.string.other_profile_unmuting_format, username) else context.getString(R.string.other_profile_unmuting)
        }
        val toastId = ToastManager.showLoading(loadingMessage)
        viewModelScope.launch {
            try {
                if (!wasMuted) {
                    userRepository.muteUser(currentUserId, userId)
                    val message = if (username != null) context.getString(R.string.other_profile_muted_format, username) else context.getString(R.string.other_profile_muted)
                    ToastManager.update(toastId, message)
                } else {
                    userRepository.unmuteUser(currentUserId, userId)
                    val message = if (username != null) context.getString(R.string.other_profile_unmuted_format, username) else context.getString(R.string.other_profile_unmuted)
                    ToastManager.update(toastId, message)
                }
            } catch (_: Exception) {
                _isMuted.value = wasMuted
                ToastManager.dismiss(toastId)
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        activeListenerPostIds.forEach { engagementManager.stopListening(it) }
        activeListenerPostIds.clear()
    }
}
