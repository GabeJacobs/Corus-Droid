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
import fm.corus.android.data.remote.CloudFunctionsDataSource
import fm.corus.android.data.remote.TMDBApiService
import fm.corus.android.data.remote.TMDBMovieDetails
import fm.corus.android.data.repository.AuthRepository
import fm.corus.android.data.repository.MessageRepository
import fm.corus.android.data.repository.PostRepository
import fm.corus.android.data.repository.UserRepository
import fm.corus.android.domain.CommentDeletedEvent
import fm.corus.android.domain.CommentEditedEvent
import fm.corus.android.domain.NowPlayingManager
import fm.corus.android.domain.PostDeletionEvent
import fm.corus.android.domain.PostEngagementManager
import fm.corus.android.domain.FullSongPlayCoordinator
import fm.corus.android.domain.toQueuedTrack
import fm.corus.android.ui.screens.feed.applyCommentDeleteToPosts
import fm.corus.android.ui.screens.feed.applyCommentEditToPosts
import fm.corus.android.service.AnalyticsService
import fm.corus.android.service.RemoteConfigService
import fm.corus.android.ui.components.PostMenuActions
import fm.corus.android.ui.components.ToastManager
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class ProfileFeedSource(val mediaType: MediaType? = null) {
    SONGS(MediaType.TRACK),
    FILMS(MediaType.MOVIE),
    LIKES,
    SAVES,
    HASHTAG,
}

/**
 * In-memory cache for passing initial posts from profile grids to the
 * ProfileFeedScreen. Set just before navigating; consumed on init.
 */
object ProfileFeedCache {
    var posts: List<CymbalPost> = emptyList()
    var hasMore: Boolean = false
    var profileUser: CymbalUser? = null
}

@HiltViewModel
class ProfileFeedViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val authRepository: AuthRepository,
    private val cloudFunctions: CloudFunctionsDataSource,
    private val postRepository: PostRepository,
    private val userRepository: UserRepository,
    private val messageRepository: MessageRepository,
    private val engagementManager: PostEngagementManager,
    private val postDeletionEvent: PostDeletionEvent,
    private val commentEditedEvent: CommentEditedEvent,
    private val commentDeletedEvent: CommentDeletedEvent,
    private val tmdbApiService: TMDBApiService,
    val nowPlayingManager: NowPlayingManager,
    val feedScrollRouter: fm.corus.android.domain.FeedScrollRouter,
    override val remoteConfig: RemoteConfigService,
    override val analyticsService: AnalyticsService,
    private val preferencesDataStore: fm.corus.android.data.local.PreferencesDataStore,
    private val playbackModePromptManager: fm.corus.android.domain.PlaybackModePromptManager,
    val musicServicePreference: fm.corus.android.domain.MusicServicePreference,
) : ViewModel(), PostMenuActions {

    /**
     * Resolve the link-out URL for a Spotify-source track given the viewer's
     * preferred service (Apple Music / TIDAL / Deezer). See FeedViewModel.
     */
    override suspend fun resolveServiceLinkUrl(track: fm.corus.android.data.model.CymbalTrack): String? =
        fm.corus.android.domain.MusicServiceLinkOut.resolveLinkOutUrl(
            track, musicServicePreference.current.value, cloudFunctions,
        )

    override suspend fun resolveSpotifyFromAppleTrack(track: fm.corus.android.data.model.CymbalTrack): String? =
        fm.corus.android.domain.MusicServiceLinkOut.resolveSpotifyUrlForAppleTrack(track, cloudFunctions)

    override suspend fun resolveAlbumIdForTrack(track: fm.corus.android.data.model.CymbalTrack): String? =
        resolveTrackDestinationsForTrack(track).albumId?.takeIf { it.isNotBlank() }

    override suspend fun resolveTrackDestinationsForTrack(track: fm.corus.android.data.model.CymbalTrack): fm.corus.android.data.remote.CloudFunctionsDataSource.TrackDestinations =
        cloudFunctions.resolveTrackDestinations(
            track.id, track.isrc, track.name, track.artistName, track.appleMusicId,
        )

    override suspend fun resolveArtistIdForTrack(track: fm.corus.android.data.model.CymbalTrack): String? =
        resolveTrackDestinationsForTrack(track).artistIds.firstOrNull { it.isNotBlank() }

    /**
     * Mirrors iOS @AppStorage("feedFollowsNowPlaying"). Same key as
     * FeedViewModel; ProfileFeedScreen reuses this to gate auto-scroll.
     */
    val feedFollowsNowPlaying: StateFlow<Boolean> = preferencesDataStore.feedFollowsNowPlaying
        .stateIn(viewModelScope, SharingStarted.Eagerly, true)

    val playFullSongs: StateFlow<Boolean> = preferencesDataStore.playFullSongs
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    override suspend fun fetchBackCover(postId: String): String? {
        return postRepository.fetchBackCover(postId)
    }

    private val _posts = MutableStateFlow<List<CymbalPost>>(emptyList())
    val posts: StateFlow<List<CymbalPost>> = _posts.asStateFlow()

    private val _hasMore = MutableStateFlow(false)
    val hasMore: StateFlow<Boolean> = _hasMore.asStateFlow()

    private val _isLoadingMore = MutableStateFlow(false)
    val isLoadingMore: StateFlow<Boolean> = _isLoadingMore.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    override val engagementStates = engagementManager.states
    val currentUserProfile = authRepository.userProfile

    val currentUserId: String? get() = authRepository.currentUserId

    private var source: ProfileFeedSource = ProfileFeedSource.SONGS
    private var userId: String = ""
    private var hashtag: String = ""
    private var initialized = false
    private var profileUser: CymbalUser? = null

    /** Public read of the current hashtag, used by the screen to show `#tag` in the title bar. */
    val currentHashtag: String get() = hashtag

    private val PAGE_SIZE = 15

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


    init {
        // Keep NowPlayingManager's queue in sync with the paginated profile feed
        // so the mini-player next button stays enabled past the first page.
        viewModelScope.launch {
            combine(_posts, _hasMore) { posts, hasMore -> posts to hasMore }.collect { (posts, hasMore) ->
                val tracks = posts
                    .filter { it.mediaType == MediaType.TRACK }
                    .map { it.toQueuedTrack() }
                if (tracks.isEmpty()) return@collect
                nowPlayingManager.updateFeedQueue(
                    newQueue = tracks,
                    hasMore = hasMore,
                    loadMore = { loadMoreSuspending() },
                )
            }
        }
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

    /**
     * Seeds the feed from [ProfileFeedCache]. Returns true if posts were
     * available, false if the cache was empty (e.g. after process death
     * restored this screen without a populated cache).
     */
    fun initFeed(userId: String, segment: Int, hashtag: String = ""): Boolean {
        if (initialized) return _posts.value.isNotEmpty()
        initialized = true
        this.userId = userId
        this.hashtag = hashtag.lowercase()
        this.source = when (segment) {
            0 -> ProfileFeedSource.SONGS
            1 -> ProfileFeedSource.FILMS
            2 -> ProfileFeedSource.LIKES
            3 -> ProfileFeedSource.SAVES
            4 -> ProfileFeedSource.HASHTAG
            else -> ProfileFeedSource.SONGS
        }
        val cachedPosts = ProfileFeedCache.posts.distinctBy { it.id }
        val profileUser = ProfileFeedCache.profileUser
        this.profileUser = profileUser
        _hasMore.value = ProfileFeedCache.hasMore
        // Clear cache after consuming
        ProfileFeedCache.posts = emptyList()
        ProfileFeedCache.hasMore = false
        ProfileFeedCache.profileUser = null

        // Profile API may return posts with empty user data since
        // the caller is already on the profile page. Enrich posts
        // with the profile user so PostCard can render avatar/username.
        _posts.value = if (profileUser != null) {
            cachedPosts.map { post ->
                if (post.user.username.isBlank() || post.user.avatarURL.isNullOrBlank()) {
                    post.copy(user = profileUser)
                } else {
                    post
                }
            }
        } else {
            cachedPosts
        }

        // Init engagement states for all posts
        val viewerId = authRepository.currentUserId ?: return _posts.value.isNotEmpty()
        _posts.value.forEach { post ->
            engagementManager.initState(
                postId = post.id,
                likeCount = post.likeCount,
                commentCount = post.commentCount,
                repostCount = post.repostCount, saveCount = post.saveCount,
                isLiked = post.isLiked,
                isSaved = false,
            )
        }
        viewModelScope.launch {
            engagementManager.checkLikeStatuses(_posts.value.map { it.id }, viewerId)
            engagementManager.checkSaveStatuses(_posts.value.map { it.id }, viewerId)
        }
        return _posts.value.isNotEmpty()
    }

    private fun enrichPost(post: CymbalPost): CymbalPost {
        val pu = profileUser ?: return post
        return if (post.user.username.isBlank() || post.user.avatarURL.isNullOrBlank()) {
            post.copy(user = pu)
        } else {
            post
        }
    }

    fun loadMore() {
        viewModelScope.launch { loadMoreSuspending() }
    }

    /**
     * Re-fetches the first page so engagement counts (likes, comments, reposts)
     * reflect the latest server state. Replaces the post list rather than
     * appending, and re-seeds engagement listeners.
     */
    fun refresh() {
        if (!initialized) return
        viewModelScope.launch {
            val viewerId = authRepository.currentUserId ?: return@launch
            _isRefreshing.value = true
            try {
                val (newPosts, newHasMore) = when (source) {
                    ProfileFeedSource.SONGS, ProfileFeedSource.FILMS -> {
                        val page = cloudFunctions.getProfilePosts(
                            userId = userId,
                            viewerId = viewerId,
                            limit = PAGE_SIZE,
                            lastTimestamp = null,
                            mediaType = source.mediaType?.value,
                        )
                        page.map { enrichPost(it) } to (page.size >= PAGE_SIZE)
                    }
                    ProfileFeedSource.LIKES -> {
                        val page = cloudFunctions.getLikedPosts(
                            userId = userId,
                            viewerId = viewerId,
                            limit = PAGE_SIZE,
                            offset = 0,
                        )
                        page.posts to page.hasMore
                    }
                    ProfileFeedSource.SAVES -> {
                        val fetched = cloudFunctions.getSavedPosts(
                            userId = userId,
                            limit = PAGE_SIZE,
                            offset = 0,
                        )
                        fetched to (fetched.size >= PAGE_SIZE)
                    }
                    ProfileFeedSource.HASHTAG -> {
                        val page = cloudFunctions.getHashtagPosts(
                            hashtag = hashtag,
                            pageSize = PAGE_SIZE,
                            beforeMs = null,
                        )
                        page.posts to page.hasMore
                    }
                }
                _posts.value = newPosts
                _hasMore.value = newHasMore
                newPosts.forEach { post ->
                    engagementManager.initState(
                        postId = post.id,
                        likeCount = post.likeCount,
                        commentCount = post.commentCount,
                        repostCount = post.repostCount, saveCount = post.saveCount,
                        isLiked = post.isLiked,
                        isSaved = false,
                    )
                }
                if (newPosts.isNotEmpty()) {
                    engagementManager.checkLikeStatuses(newPosts.map { it.id }, viewerId)
                    engagementManager.checkSaveStatuses(newPosts.map { it.id }, viewerId)
                }
            } catch (_: Exception) { }
            _isRefreshing.value = false
        }
    }

    private suspend fun loadMoreSuspending() {
        if (!_hasMore.value || _isLoadingMore.value) return
        val viewerId = authRepository.currentUserId ?: return
        _isLoadingMore.value = true
        try {
            when (source) {
                    ProfileFeedSource.SONGS, ProfileFeedSource.FILMS -> {
                        val lastTimestamp = _posts.value.lastOrNull()?.timestamp?.time ?: run {
                            _hasMore.value = false
                            _isLoadingMore.value = false
                            return
                        }
                        val page = cloudFunctions.getProfilePosts(
                            userId = userId,
                            viewerId = viewerId,
                            limit = PAGE_SIZE,
                            lastTimestamp = lastTimestamp,
                            mediaType = source.mediaType?.value,
                        )
                        val existingIds = _posts.value.map { it.id }.toSet()
                        val unique = page.filter { it.id !in existingIds }.map { enrichPost(it) }
                        _posts.value = _posts.value + unique
                        if (page.size < PAGE_SIZE) _hasMore.value = false
                        // Init engagement for new posts
                        unique.forEach { post ->
                            engagementManager.initState(
                                postId = post.id,
                                likeCount = post.likeCount,
                                commentCount = post.commentCount,
                                repostCount = post.repostCount, saveCount = post.saveCount,
                                isLiked = post.isLiked,
                                isSaved = false,
                            )
                        }
                        if (unique.isNotEmpty()) {
                            engagementManager.checkLikeStatuses(unique.map { it.id }, viewerId)
                            engagementManager.checkSaveStatuses(unique.map { it.id }, viewerId)
                        }
                    }
                    ProfileFeedSource.LIKES -> {
                        val offset = _posts.value.size
                        val fetched = cloudFunctions.getLikedPosts(
                            userId = userId,
                            viewerId = viewerId,
                            limit = PAGE_SIZE,
                            offset = offset,
                        ).posts
                        val existingIds = _posts.value.map { it.id }.toSet()
                        val unique = fetched.filter { it.id !in existingIds }
                        _posts.value = _posts.value + unique
                        if (fetched.size < PAGE_SIZE) _hasMore.value = false
                        unique.forEach { post ->
                            engagementManager.initState(
                                postId = post.id,
                                likeCount = post.likeCount,
                                commentCount = post.commentCount,
                                repostCount = post.repostCount, saveCount = post.saveCount,
                                isLiked = post.isLiked,
                                isSaved = false,
                            )
                        }
                        if (unique.isNotEmpty()) {
                            engagementManager.checkLikeStatuses(unique.map { it.id }, viewerId)
                            engagementManager.checkSaveStatuses(unique.map { it.id }, viewerId)
                        }
                    }
                    ProfileFeedSource.SAVES -> {
                        val offset = _posts.value.size
                        val fetched = cloudFunctions.getSavedPosts(
                            userId = userId,
                            limit = PAGE_SIZE,
                            offset = offset,
                        )
                        val existingIds = _posts.value.map { it.id }.toSet()
                        val unique = fetched.filter { it.id !in existingIds }
                        _posts.value = _posts.value + unique
                        if (fetched.size < PAGE_SIZE) _hasMore.value = false
                        unique.forEach { post ->
                            engagementManager.initState(
                                postId = post.id,
                                likeCount = post.likeCount,
                                commentCount = post.commentCount,
                                repostCount = post.repostCount, saveCount = post.saveCount,
                                isLiked = post.isLiked,
                                isSaved = false,
                            )
                        }
                        if (unique.isNotEmpty()) {
                            engagementManager.checkLikeStatuses(unique.map { it.id }, viewerId)
                            engagementManager.checkSaveStatuses(unique.map { it.id }, viewerId)
                        }
                    }
                    ProfileFeedSource.HASHTAG -> {
                        val lastTimestamp = _posts.value.lastOrNull()?.timestamp?.time ?: run {
                            _hasMore.value = false
                            _isLoadingMore.value = false
                            return
                        }
                        val page = cloudFunctions.getHashtagPosts(
                            hashtag = hashtag,
                            pageSize = PAGE_SIZE,
                            beforeMs = lastTimestamp,
                        )
                        val fetched = page.posts
                        val existingIds = _posts.value.map { it.id }.toSet()
                        val unique = fetched.filter { it.id !in existingIds }
                        _posts.value = _posts.value + unique
                        if (!page.hasMore) _hasMore.value = false
                        unique.forEach { post ->
                            engagementManager.initState(
                                postId = post.id,
                                likeCount = post.likeCount,
                                commentCount = post.commentCount,
                                repostCount = post.repostCount, saveCount = post.saveCount,
                                isLiked = post.isLiked,
                                isSaved = false,
                            )
                        }
                        if (unique.isNotEmpty()) {
                            engagementManager.checkLikeStatuses(unique.map { it.id }, viewerId)
                            engagementManager.checkSaveStatuses(unique.map { it.id }, viewerId)
                        }
                    }
                }
        } catch (_: Exception) { }
        _isLoadingMore.value = false
    }

    fun playPreview(post: CymbalPost) {
        viewModelScope.launch { routePostPlayTap(post, preferFullSong = false) }
    }

    fun playFullSong(post: CymbalPost) {
        viewModelScope.launch {
            routePostPlayTap(post, preferFullSong = true, skipPlaybackModePrompt = true)
        }
    }

    private suspend fun routePostPlayTap(
        post: CymbalPost,
        preferFullSong: Boolean,
        skipPlaybackModePrompt: Boolean = false,
    ) {
        nowPlayingManager.lastUserInitiatedSourcePostId = post.id
        val musicService = musicServicePreference.current.value
        if (!preferFullSong &&
            nowPlayingManager.isFullSongSessionActive(musicService, post.track.id, post.id)
        ) {
            nowPlayingManager.togglePlayPause()
            return
        }
        val trackPosts = _posts.value.filter { it.mediaType == MediaType.TRACK }
        val queue = trackPosts.map { it.toQueuedTrack() }
        val track = post.toQueuedTrack()
        val playFullSongs = preferencesDataStore.effectivePlayFullSongsSync()
        val outcome = FullSongPlayCoordinator.playTapOutcome(
            track = post.track,
            sourcePostId = post.id,
            queue = queue,
            nowPlaying = nowPlayingManager,
            remoteConfig = remoteConfig,
            musicService = musicService,
            playFullSongs = playFullSongs,
            playbackModePromptManager = playbackModePromptManager,
            skipPlaybackModePrompt = skipPlaybackModePrompt,
            preferFullSong = preferFullSong,
        )
        FullSongPlayCoordinator.applyPlayTapOutcome(
            outcome = outcome,
            track = post.track,
            sourcePostId = post.id,
            queue = queue,
            nowPlaying = nowPlayingManager,
            remoteConfig = remoteConfig,
            musicService = musicService,
            playFullSongs = playFullSongs,
            playbackModePromptManager = playbackModePromptManager,
            onPreview = {
                if (queue.any { it.trackId == track.trackId }) {
                    nowPlayingManager.play(track = track, queue = queue)
                    nowPlayingManager.updateFeedQueue(
                        newQueue = queue,
                        hasMore = _hasMore.value,
                        loadMore = { loadMoreSuspending() },
                    )
                } else {
                    nowPlayingManager.play(track = track, queue = listOf(track))
                }
            },
            scope = viewModelScope,
        )
    }

    fun toggleLike(postId: String) {
        val userId = authRepository.currentUserId ?: return
        engagementManager.toggleLike(postId, userId)
    }

    override fun toggleSave(postId: String) {
        val userId = authRepository.currentUserId ?: return
        engagementManager.toggleSave(postId, userId)
    }

    override fun deletePost(postId: String) {
        val userId = authRepository.currentUserId ?: return
        viewModelScope.launch {
            try {
                postRepository.deletePost(postId, userId)
                _posts.value = _posts.value.filter { it.id != postId }
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

    suspend fun resolveUsernameToId(username: String): String? {
        return try {
            userRepository.fetchUserByUsername(username)?.id
        } catch (_: Exception) { null }
    }

    // ── Share contacts & search ──

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
                _shareSearchResults.value = userRepository.searchUsers(trimmed, includeFollowed = true)
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
                messageRepository.sendSharedPostMessage(
                    threadId = threadId,
                    fromUserId = currentUserId,
                    postId = post.id,
                    text = message.trim(),
                )
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
                _posts.value = _posts.value.filter { it.user.id != targetUserId }
                ToastManager.show(context.getString(R.string.feed_toast_user_blocked))
            } catch (_: Exception) {
                ToastManager.show(context.getString(R.string.feed_toast_failed_block))
            }
        }
    }

    suspend fun fetchMovieDetails(movieId: Int): TMDBMovieDetails? {
        return try {
            tmdbApiService.getMovieDetails(movieId)
        } catch (_: Exception) {
            null
        }
    }

    override fun onCleared() {
        super.onCleared()
    }
}
