package fm.corus.android.ui.screens.feed

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import android.util.Log
import fm.corus.android.R
import fm.corus.android.data.model.CymbalPost
import fm.corus.android.data.model.CymbalUser
import fm.corus.android.data.model.FeedFilter
import fm.corus.android.data.model.MediaType
import fm.corus.android.data.model.SuggestedUserMatch
import fm.corus.android.data.remote.CloudFunctionsDataSource
import fm.corus.android.data.remote.TMDBApiService
import fm.corus.android.data.remote.TMDBMovieDetails
import fm.corus.android.data.repository.AuthRepository
import fm.corus.android.data.repository.MessageRepository
import fm.corus.android.data.repository.PostRepository
import fm.corus.android.data.repository.SubscriptionRepository
import fm.corus.android.data.repository.UserRepository
import fm.corus.android.ui.screens.subscription.PaywallSource
import fm.corus.android.domain.CommentDeletedEvent
import fm.corus.android.domain.CommentEditedEvent
import fm.corus.android.domain.NowPlayingManager
import fm.corus.android.domain.PostCreationEvent
import fm.corus.android.domain.PostDeletionEvent
import fm.corus.android.domain.PostEngagementManager
import fm.corus.android.domain.QueuedTrack
import fm.corus.android.service.AnalyticsService
import fm.corus.android.service.NetworkMonitor
import fm.corus.android.service.RemoteConfigService
import fm.corus.android.ui.components.PostMenuActions
import fm.corus.android.ui.components.ToastManager
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.launch
import javax.inject.Inject

/** 2026-05-10 00:00 UTC — the moment the album-art tap hint shipped. Accounts
 *  created on or after this instant see the one-time hint on the first feed
 *  post; older accounts never see it. */
internal val ALBUM_ART_HINT_CUTOFF_MS: Long =
    java.time.LocalDate.of(2026, 5, 10)
        .atStartOfDay(java.time.ZoneOffset.UTC)
        .toInstant()
        .toEpochMilli()

internal fun isNewAlbumArtHintAccount(creationTimestampMs: Long?): Boolean {
    val created = creationTimestampMs ?: return false
    return created >= ALBUM_ART_HINT_CUTOFF_MS
}

/** Patches the matching post's preview-comments entry with the edited text so
 *  recycled post cards re-bind to fresh text instead of the stale denormalized
 *  `previewComments` snapshot on the post doc. Server-side
 *  `onCommentUpdatedUpdatePreview` eventually rewrites the doc; this avoids
 *  waiting for the next fetch. Returns the original list unchanged if no post
 *  carries a matching preview entry. */
internal fun applyCommentEditToPosts(
    posts: List<CymbalPost>,
    payload: CommentEditedEvent.Payload,
): List<CymbalPost> {
    var didChange = false
    val updated = posts.map { post ->
        if (post.id != payload.postId) return@map post
        val previews = post.comments
        if (previews.none { it.id == payload.commentId }) return@map post
        didChange = true
        post.copy(
            comments = previews.map { c ->
                if (c.id == payload.commentId) c.copy(text = payload.newText, editedAt = java.util.Date()) else c
            }
        )
    }
    return if (didChange) updated else posts
}

/** Removes the deleted comment from any post's preview snapshot so a recycled
 *  PostCard doesn't keep rendering it (which would make the delete look like
 *  it silently failed). Returns the original list unchanged if no post carries
 *  a matching preview entry. */
internal fun applyCommentDeleteToPosts(
    posts: List<CymbalPost>,
    payload: CommentDeletedEvent.Payload,
): List<CymbalPost> {
    var didChange = false
    val updated = posts.map { post ->
        if (post.id != payload.postId) return@map post
        val previews = post.comments
        val filtered = previews.filter { it.id != payload.commentId }
        if (filtered.size == previews.size) return@map post
        didChange = true
        post.copy(comments = filtered)
    }
    return if (didChange) updated else posts
}

@HiltViewModel
class FeedViewModel @Inject constructor(
    private val postRepository: PostRepository,
    private val authRepository: AuthRepository,
    private val subscriptionRepository: SubscriptionRepository,
    private val engagementManager: PostEngagementManager,
    private val userRepository: UserRepository,
    private val messageRepository: MessageRepository,
    private val cloudFunctions: CloudFunctionsDataSource,
    private val tmdbApiService: TMDBApiService,
    val nowPlayingManager: NowPlayingManager,
    val feedScrollRouter: fm.corus.android.domain.FeedScrollRouter,
    val musicServicePreference: fm.corus.android.domain.MusicServicePreference,
    override val remoteConfig: RemoteConfigService,
    override val analyticsService: AnalyticsService,
    private val postCreationEvent: PostCreationEvent,
    private val postDeletionEvent: PostDeletionEvent,
    private val commentEditedEvent: CommentEditedEvent,
    private val commentDeletedEvent: CommentDeletedEvent,
    networkMonitor: NetworkMonitor,
    private val preferencesDataStore: fm.corus.android.data.local.PreferencesDataStore,
    @ApplicationContext private val context: Context,
) : ViewModel(), PostMenuActions {

    /**
     * Resolve the link-out URL for a Spotify-source track given the viewer's
     * preferred service (Apple Music / TIDAL / Deezer). Returns null for Spotify
     * (caller opens the post's own URI) and on no-match / error. Network-bound
     * for Apple/TIDAL/Deezer; cached per-process by MusicServiceLinkOut.
     */
    suspend fun resolveServiceLinkUrl(track: fm.corus.android.data.model.CymbalTrack): String? =
        fm.corus.android.domain.MusicServiceLinkOut.resolveLinkOutUrl(
            track, musicServicePreference.current.value, cloudFunctions,
        )

    /**
     * Mirrors iOS @AppStorage("feedFollowsNowPlaying"). When true, the feed
     * scrolls to the now-playing post on song changes (gated further by the
     * UI layer on tab/sub-screen visibility and a tap-marker on
     * NowPlayingManager).
     */
    val feedFollowsNowPlaying: StateFlow<Boolean> = preferencesDataStore.feedFollowsNowPlaying
        .stateIn(viewModelScope, SharingStarted.Eagerly, true)

    /** Live network connectivity. Mirrors iOS `NetworkMonitor.isConnected`. */
    val isConnected: StateFlow<Boolean> = networkMonitor.isConnected

    /** True when the most recent feed load threw — used to drive the
     *  offline empty state. Cleared on the next successful load. */
    private val _lastLoadFailed = MutableStateFlow(false)
    val lastLoadFailed: StateFlow<Boolean> = _lastLoadFailed.asStateFlow()

    private val _posts = MutableStateFlow<List<CymbalPost>>(emptyList())
    val posts: StateFlow<List<CymbalPost>> = _posts.asStateFlow()

    private val _feedFilter = MutableStateFlow(FeedFilter.ALL)
    val feedFilter: StateFlow<FeedFilter> = _feedFilter.asStateFlow()
    // Back-compat for existing observers — derived from _feedFilter.
    val feedMediaFilter: StateFlow<MediaType?> = _feedFilter
        .let { upstream ->
            kotlinx.coroutines.flow.MutableStateFlow(upstream.value.mediaType).also { mirror ->
                viewModelScope.launch { upstream.collect { mirror.value = it.mediaType } }
            }
        }
        .asStateFlow()

    // Filtering happens server-side in getFeedPage; _posts already reflects the active filter.
    // For the new-releases filter we additionally apply a client-side
    // defense-in-depth check using `isNewRelease()` so the user never sees a
    // post that crossed the threshold mid-flight.
    val filteredPosts: StateFlow<List<CymbalPost>> = combine(_posts, _feedFilter) { posts, filter ->
        if (filter.newReleasesOnly) posts.filter { it.isNewRelease() } else posts
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val _newReleaseFilterPaywall = MutableStateFlow<PaywallSource?>(null)
    val newReleaseFilterPaywall: StateFlow<PaywallSource?> = _newReleaseFilterPaywall.asStateFlow()
    fun clearNewReleaseFilterPaywall() { _newReleaseFilterPaywall.value = null }

    private val _isLoading = MutableStateFlow(false)
    private val _hasLoaded = MutableStateFlow(false)
    val hasLoaded: StateFlow<Boolean> = _hasLoaded.asStateFlow()
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    private val _hasMore = MutableStateFlow(true)
    val hasMore: StateFlow<Boolean> = _hasMore.asStateFlow()

    private var lastTimestamp: Long? = null

    // ── For You feed state ──
    // Effective feed mode: "following" | "forYou" | "discovery". The stored
    // value may be empty ("never picked") — in that case we resolve the
    // opening mode from Remote Config (`default_for_you_feed_enabled`). Once
    // the user taps a mode it's persisted and wins.
    private fun resolveFeedMode(stored: String): String = when (stored) {
        "following", "forYou", "discovery" -> stored
        else ->
            if (remoteConfig.defaultForYouFeedEnabled && remoteConfig.forYouEnabled) "forYou"
            else "following"
    }
    val feedMode: StateFlow<String> = preferencesDataStore.feedMode
        .map { resolveFeedMode(it) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, "following")

    private var forYouSessionToken: String? = null
    private var forYouPageIndex: Int = 0
    private var forYouSeenIds: MutableList<String> = mutableListOf()
    private val _forYouLoadFailed = MutableStateFlow(false)
    val forYouLoadFailed: StateFlow<Boolean> = _forYouLoadFailed.asStateFlow()

    val engagementStates = engagementManager.states
    val currentUserProfile = authRepository.userProfile

    /**
     * One-time tap-to-play hint on the first feed post's album art. Mirrors iOS
     * AlbumArtView showsTapHint. Only newly-signed-up accounts see it, and only
     * until they tap any album art once.
     */
    val hasTappedAlbumArt: StateFlow<Boolean> = preferencesDataStore.hasTappedAlbumArt
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    /**
     * True if the signed-in Firebase account was created on or after the
     * feature launch cutoff. Existing users (creationDate < cutoff) never see
     * the hint — they've already learned the gesture.
     */
    val isNewAccount: StateFlow<Boolean> = authRepository.currentUser
        .let { upstream ->
            kotlinx.coroutines.flow.MutableStateFlow(
                isNewAlbumArtHintAccount(upstream.value?.metadata?.creationTimestamp)
            ).also { mirror ->
                viewModelScope.launch {
                    upstream.collect { mirror.value = isNewAlbumArtHintAccount(it?.metadata?.creationTimestamp) }
                }
            }
        }
        .asStateFlow()

    fun markAlbumArtTapped() {
        viewModelScope.launch { preferencesDataStore.setHasTappedAlbumArt() }
    }

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

    // ── Empty-feed follow tracking ──
    // Used by the empty-state HorizontalPopularUsersRail; the rail itself
    // owns the popular-users list, but we mirror local follow state here
    // so the card buttons reflect taps optimistically.
    private val _followingIds = MutableStateFlow<Set<String>>(emptySet())
    private val _localFollowedIds = MutableStateFlow<Set<String>>(emptySet())

    val followedBotIds: StateFlow<Set<String>> =
        combine(_followingIds, _localFollowedIds) { remote, local -> remote + local }
            .stateIn(viewModelScope, SharingStarted.Eagerly, emptySet())

    // Track which posts have active real-time listeners (matching iOS PostEngagementStore)
    private val activeListenerPostIds = mutableSetOf<String>()

    init {
        // Restore the persisted feed filter on startup so a music/film/new-releases
        // selection survives an app restart (mirrors iOS @AppStorage("feedFilter")).
        viewModelScope.launch {
            val saved = runCatching {
                FeedFilter.valueOf(preferencesDataStore.feedFilter.first())
            }.getOrDefault(FeedFilter.ALL)
            if (saved != _feedFilter.value) {
                _feedFilter.value = saved
                // If the screen already kicked off a default (ALL) load before the
                // async restore landed, redo it so the page matches the restored
                // filter. When the restore wins the race, the screen's initial
                // load simply reads the already-correct filter and no refetch runs.
                if (_hasLoaded.value || _isLoading.value || _isRefreshing.value) {
                    lastTimestamp = null
                    _posts.value = emptyList()
                    _hasMore.value = true
                    loadFeed(refresh = true)
                }
            }
        }
        // Restore For You seen-IDs ring buffer from DataStore on startup so
        // we suppress already-shown posts after an app restart.
        viewModelScope.launch {
            val json = preferencesDataStore.forYouSeenIdsJson.first()
            runCatching {
                val arr = org.json.JSONArray(json)
                val ids = mutableListOf<String>()
                for (i in 0 until arr.length()) {
                    val s = arr.optString(i, null)
                    if (!s.isNullOrEmpty()) ids.add(s)
                }
                forYouSeenIds = ids
            }
        }
        // Mirror the global following set so the empty-state rail's follow
        // pills reflect users the viewer already follows from elsewhere.
        viewModelScope.launch {
            userRepository.followingIds.collect { ids -> _followingIds.value = ids }
        }
        // Auto-retry the feed when the network returns if the previous load
        // failed and the screen has no posts to show. Mirrors iOS FeedView's
        // reconnect handler.
        viewModelScope.launch {
            isConnected.collect { connected ->
                if (connected && _lastLoadFailed.value && _posts.value.isEmpty()) {
                    loadFeed(refresh = true)
                }
            }
        }
        // Auto-refresh feed when a new post is created
        viewModelScope.launch {
            postCreationEvent.events.collect {
                delay(500) // brief delay for Firestore propagation
                loadFeed(refresh = true)
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
        // Keep NowPlayingManager's queue in sync with the paginated feed so the
        // mini-player next button stays enabled (and functional) past the first page.
        viewModelScope.launch {
            combine(_posts, _hasMore) { posts, hasMore -> posts to hasMore }.collect { (posts, hasMore) ->
                val tracks = posts
                    .filter { it.mediaType == MediaType.TRACK }
                    .map { it.toQueuedTrack() }
                if (tracks.isEmpty()) return@collect
                nowPlayingManager.updateFeedQueue(
                    newQueue = tracks,
                    hasMore = hasMore,
                    loadMore = { loadFeedSuspending(refresh = false) },
                )
            }
        }
    }

    fun loadFeed(refresh: Boolean = false) {
        viewModelScope.launch { loadFeedSuspending(refresh) }
    }

    private suspend fun loadFeedSuspending(refresh: Boolean) {
        val userId = authRepository.currentUserId ?: return

        if (refresh) {
            _isRefreshing.value = true
            lastTimestamp = null
        } else {
            if (_isLoading.value) return
            _isLoading.value = true
        }

        // Ranked feed covers both For You (pool = follows) and Discovery
        // (pool = whole app). Both hit the same callable with a `scope` arg.
        // Keyed off the resolved/persisted mode, NOT the Remote Config flags:
        // on a cold launch the flags can briefly read false before RC fetches,
        // which used to load Recent instead of the user's chosen ranked feed.
        // A ranked mode can only have been persisted while its flag was on,
        // and resolveFeedMode already handles the unset case via the RC default.
        val mode = feedMode.value
        val useRanked = mode == "forYou" || mode == "discovery"
        val rankedScope = if (mode == "discovery") "discovery" else "forYou"

        try {
            val newPosts: List<CymbalPost>
            val pageHasMore: Boolean
            if (useRanked) {
                if (refresh) {
                    forYouSessionToken = null
                    forYouPageIndex = 0
                }
                val nextIndex = if (refresh) 0 else forYouPageIndex + (if (forYouSessionToken != null) 1 else 0)
                val forYouPage = postRepository.getForYouFeed(
                    userId = userId,
                    pageSize = 7,
                    sessionToken = forYouSessionToken,
                    pageIndex = nextIndex,
                    seenPostIds = forYouSeenIds.toList(),
                    mediaType = _feedFilter.value.mediaType,
                    newReleasesOnly = _feedFilter.value.newReleasesOnly,
                    scope = rankedScope,
                )
                if (forYouPage.sessionToken != (forYouSessionToken ?: "") && forYouPage.sessionToken.isNotEmpty()) {
                    forYouSessionToken = forYouPage.sessionToken
                    forYouPageIndex = 0
                } else {
                    forYouPageIndex = nextIndex
                }
                newPosts = forYouPage.posts
                pageHasMore = forYouPage.hasMore
                // Update seen-IDs ring buffer (cap 500).
                for (p in newPosts) {
                    if (!forYouSeenIds.contains(p.id)) forYouSeenIds.add(p.id)
                }
                if (forYouSeenIds.size > 500) {
                    forYouSeenIds = forYouSeenIds.takeLast(500).toMutableList()
                }
                viewModelScope.launch {
                    runCatching {
                        val json = org.json.JSONArray(forYouSeenIds).toString()
                        preferencesDataStore.setForYouSeenIdsJson(json)
                    }
                }
                _forYouLoadFailed.value = false
            } else {
                val page = postRepository.getFeedPage(
                    userId = userId,
                    pageSize = 7,
                    lastTimestamp = if (refresh) null else lastTimestamp,
                    mediaType = _feedFilter.value.mediaType,
                    newReleasesOnly = _feedFilter.value.newReleasesOnly,
                )
                newPosts = page.posts
                pageHasMore = page.hasMore
            }
            // The remaining engagement/state code below was written assuming a
            // page variable was in scope; we synthesize a parallel pair here so
            // the rest of the function reads as before.
            newPosts.forEach { post ->
                engagementManager.initState(
                    postId = post.id,
                    likeCount = post.likeCount,
                    commentCount = post.commentCount,
                    repostCount = post.repostCount,
                    isLiked = post.isLiked,
                    isSaved = false,
                )
                // Safety net: the denormalized `commentCount` on the post doc
                // lags behind the comments subcollection by ~10–20s while the
                // count-aggregation trigger commits. If the feed payload
                // includes more preview comments than the badge claims, ratchet
                // the badge up so it matches what's visibly on screen.
                engagementManager.reconcileCommentCount(post.id, atLeast = post.comments.size)
            }

            if (refresh) {
                _posts.value = newPosts
            } else {
                _posts.value = (_posts.value + newPosts).distinctBy { it.id }
            }

            _hasMore.value = pageHasMore
            if (newPosts.isNotEmpty() && !useRanked) {
                lastTimestamp = newPosts.last().timestamp.time
            }

            // Start real-time listeners for new posts (matching iOS PostEngagementStore)
            newPosts.forEach { post ->
                if (activeListenerPostIds.add(post.id)) {
                    engagementManager.startListening(post.id)
                }
            }

            // Check actual like status from Firestore (backend doesn't return isLiked)
            engagementManager.checkLikeStatuses(newPosts.map { it.id }, userId)
            _lastLoadFailed.value = false
        } catch (_: Exception) {
            _lastLoadFailed.value = true
            if (useRanked) _forYouLoadFailed.value = true
        }

        _isLoading.value = false
        _isRefreshing.value = false
        _hasLoaded.value = true
    }

    fun setFeedFilter(filter: FeedFilter) {
        if (_feedFilter.value == filter) return
        if (filter.newReleasesOnly && remoteConfig.newReleaseFilterClubOnly && !subscriptionRepository.hasFullAccess) {
            _newReleaseFilterPaywall.value = PaywallSource.NEW_RELEASE_FILTER
            return
        }
        analyticsService.logFeedFilterChanged(filter.analyticsValue)
        _feedFilter.value = filter
        // Persist so the selection survives an app restart (mirrors iOS).
        viewModelScope.launch { preferencesDataStore.setFeedFilter(filter.name) }
        // Server-side filter changed — reset the paginated feed and re-fetch
        // so the returned page matches the new filter.
        lastTimestamp = null
        _posts.value = emptyList()
        _hasMore.value = true
        loadFeed(refresh = true)
    }

    /**
     * Legacy entrypoint preserved so any non-screen call sites (e.g. tests)
     * can still narrow by media type without going through the new enum.
     * Internally maps to the corresponding FeedFilter value, preserving the
     * "new releases" half of the state if it was already set.
     */
    fun setFeedMediaFilter(filter: MediaType?) {
        val target = when (filter) {
            null -> FeedFilter.ALL
            MediaType.TRACK -> if (_feedFilter.value.newReleasesOnly) FeedFilter.MUSIC_NEW_RELEASES else FeedFilter.MUSIC
            MediaType.MOVIE -> if (_feedFilter.value.newReleasesOnly) FeedFilter.FILM_NEW_RELEASES else FeedFilter.FILM
        }
        setFeedFilter(target)
    }

    /**
     * Flip between Following ("following") and For You ("forYou"). Persists to
     * DataStore and resets the For You session state, then refetches. No-op
     * when the requested mode matches the current value.
     */
    fun setFeedMode(mode: String) {
        if (feedMode.value == mode) return
        forYouSessionToken = null
        forYouPageIndex = 0
        _forYouLoadFailed.value = false
        lastTimestamp = null
        _posts.value = emptyList()
        _hasMore.value = true
        viewModelScope.launch {
            preferencesDataStore.setFeedMode(mode)
            loadFeedSuspending(refresh = true)
        }
    }

    fun playPreview(post: fm.corus.android.data.model.CymbalPost) {
        // Mark this as a user-initiated play so the feed's auto-scroll
        // handler skips the resulting sourcePostId change — the user
        // already sees this card (they just tapped it).
        nowPlayingManager.lastUserInitiatedSourcePostId = post.id
        viewModelScope.launch {
            val trackPosts = filteredPosts.value.filter { it.mediaType == MediaType.TRACK }
            val queue = trackPosts.map { it.toQueuedTrack() }
            val track = post.toQueuedTrack()
            if (queue.any { it.trackId == track.trackId }) {
                nowPlayingManager.play(track = track, queue = queue)
                // Re-wire the paginated-feed hook that `play` resets, so the next
                // button stays live past the first page without waiting for
                // _posts/_hasMore to change.
                nowPlayingManager.updateFeedQueue(
                    newQueue = queue,
                    hasMore = _hasMore.value,
                    loadMore = { loadFeedSuspending(refresh = false) },
                )
            } else {
                nowPlayingManager.play(track = track, queue = listOf(track))
            }
        }
    }

    private fun fm.corus.android.data.model.CymbalPost.toQueuedTrack() = QueuedTrack(
        trackId = track.id,
        trackName = track.name,
        artistName = track.artistName,
        albumArtURL = track.albumArtURL,
        previewUrl = track.previewUrl,
        spotifyURI = track.spotifyURI,
        spotifyWebURL = track.spotifyWebURL,
        isrc = track.isrc,
        sourcePostId = id,
        posterUserId = user.id,
        source = track.source,
        soundcloudId = track.soundcloudId,
        soundcloudPermalinkUrl = track.soundcloudPermalinkUrl,
    )

    fun generateFeedPlaylist() {
        analyticsService.logFeedPlaylistTapped()
        viewModelScope.launch {
            nowPlayingManager.generateFeedPlaylist(newReleasesOnly = _feedFilter.value.newReleasesOnly)
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

    // ── Report / Block / Mute ──

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

    fun muteUser(targetUserId: String) {
        val currentUserId = authRepository.currentUserId ?: return
        viewModelScope.launch {
            try {
                userRepository.muteUser(currentUserId, targetUserId)
                _posts.value = _posts.value.filter { it.user.id != targetUserId }
                ToastManager.show(context.getString(R.string.feed_toast_user_muted))
            } catch (_: Exception) {
                ToastManager.show(context.getString(R.string.feed_toast_failed_mute))
            }
        }
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

    override suspend fun fetchBackCover(postId: String): String? {
        return postRepository.fetchBackCover(postId)
    }

    fun isPostSaved(postId: String): Boolean {
        return engagementManager.getState(postId)?.isSaved ?: false
    }

    fun toggleBotFollow(user: CymbalUser) {
        val uid = authRepository.currentUserId ?: return
        val isFollowed = _localFollowedIds.value.contains(user.id) || _followingIds.value.contains(user.id)
        viewModelScope.launch {
            if (isFollowed) {
                _localFollowedIds.value = _localFollowedIds.value - user.id
                _followingIds.value = _followingIds.value - user.id
                try { userRepository.unfollowUser(uid, user.id) } catch (_: Exception) {
                    _followingIds.value = _followingIds.value + user.id
                }
            } else {
                _localFollowedIds.value = _localFollowedIds.value + user.id
                try { userRepository.followUser(uid, user.id) } catch (_: Exception) {
                    _localFollowedIds.value = _localFollowedIds.value - user.id
                }
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
        activeListenerPostIds.forEach { engagementManager.stopListening(it) }
        activeListenerPostIds.clear()
    }
}
