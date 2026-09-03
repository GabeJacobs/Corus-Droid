package fm.corus.android.ui.screens.profile

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import fm.corus.android.R
import fm.corus.android.data.model.CymbalPost
import fm.corus.android.data.model.CymbalUser
import fm.corus.android.service.AnalyticsService
import fm.corus.android.service.NetworkMonitor
import fm.corus.android.service.RemoteConfigService
import fm.corus.android.data.model.LinkedArtist
import fm.corus.android.data.model.MediaType
import fm.corus.android.data.model.MusicMatchData
import fm.corus.android.data.remote.CloudFunctionsDataSource
import fm.corus.android.data.repository.AuthRepository
import fm.corus.android.data.repository.MessageRepository
import fm.corus.android.data.repository.PostRepository
import fm.corus.android.data.repository.SubscriptionRepository
import fm.corus.android.data.repository.UserRepository
import fm.corus.android.domain.CommentDeletedEvent
import fm.corus.android.domain.CommentEditedEvent
import fm.corus.android.domain.NowPlayingManager
import fm.corus.android.domain.PlaylistTrialField
import fm.corus.android.domain.PostDeletionEvent
import fm.corus.android.domain.PostEngagementManager
import fm.corus.android.ui.components.ShareCardTheme
import fm.corus.android.ui.components.ToastManager
import fm.corus.android.ui.screens.feed.applyCommentDeleteToPosts
import fm.corus.android.ui.screens.feed.applyCommentEditToPosts
import fm.corus.android.ui.screens.feed.loadRecentDmShareContacts
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
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
    private val messageRepository: MessageRepository,
    val nowPlayingManager: NowPlayingManager,
    private val cloudFunctions: fm.corus.android.data.remote.CloudFunctionsDataSource,
    val musicServicePreference: fm.corus.android.domain.MusicServicePreference,
    private val engagementManager: PostEngagementManager,
    private val subscriptionRepository: SubscriptionRepository,
    private val postDeletionEvent: PostDeletionEvent,
    private val commentEditedEvent: CommentEditedEvent,
    private val commentDeletedEvent: CommentDeletedEvent,
    private val analyticsService: AnalyticsService,
    private val remoteConfig: RemoteConfigService,
    private val networkMonitor: NetworkMonitor,
    private val favoriteChangedEvent: fm.corus.android.domain.FavoriteChangedEvent,
) : ViewModel() {

    /** Whether the Favorites feature (star button) is enabled in Remote Config. */
    val favoritesEnabled: Boolean get() = remoteConfig.favoritesEnabled

    /** Immersive frosted header/status-bar gate (shared with the hero + feed pages). */
    val immersiveArtistHeaderEnabled: Boolean get() = remoteConfig.immersiveArtistHeaderEnabled

    /**
     * Resolve the link-out URL for a Spotify-source track given the viewer's
     * preferred service (Apple Music / TIDAL / Deezer), for the featured-post
     * service-logo tap. Returns null for Spotify / no-match. See FeedViewModel.
     */
    suspend fun resolveServiceLinkUrl(track: fm.corus.android.data.model.CymbalTrack): String? =
        fm.corus.android.domain.MusicServiceLinkOut.resolveLinkOutUrl(
            track, musicServicePreference.current.value, cloudFunctions,
        )

    suspend fun resolveSpotifyFromAppleTrack(track: fm.corus.android.data.model.CymbalTrack): String? =
        fm.corus.android.domain.MusicServiceLinkOut.resolveSpotifyUrlForAppleTrack(track, cloudFunctions)

    val hasFullAccess = subscriptionRepository.hasFullAccessFlow

    fun shouldPaywallOtherProfilePlaylist(): Boolean =
        subscriptionRepository.shouldPaywallPlaylist(PlaylistTrialField.OtherProfile)

    /** Send-side gate for the in-app "Share Profile" Corus share sheet. */
    val profileShareEnabled: Boolean get() = remoteConfig.profileShareEnabled

    // ── Profile share sheet ──
    // Mirrors the destination-page share plumbing (recipient picker + DM send),
    // but shares a *profile*: DMs send a `sharedProfile` message deep-linking to
    // the user's profile. Recents are the most recent people you've DMed.

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

    fun sendProfileToUser(userId: String, sharedUserId: String, username: String, displayName: String?, avatarUrl: String?, message: String) {
        val currentUserId = authRepository.currentUserId ?: return
        viewModelScope.launch {
            try {
                val threadId = messageRepository.getOrCreateThread(currentUserId, userId)
                messageRepository.sendSharedProfileMessage(
                    threadId = threadId,
                    fromUserId = currentUserId,
                    text = message.trim(),
                    sharedUserId = sharedUserId,
                    username = username,
                    displayName = displayName,
                    avatarUrl = avatarUrl,
                )
            } catch (_: Exception) {
                ToastManager.show(context.getString(R.string.feed_toast_failed_send_post))
            }
        }
    }

    /** Inbox hit → real thread id; miss → blank so the thread screen composes new. */
    fun resolveDirectThreadId(otherUserId: String): String =
        messageRepository.directThreadId(otherUserId).orEmpty()

    fun logProfileShared(
        profileUserId: String,
        method: String,
        isOwnProfile: Boolean,
        cardTheme: ShareCardTheme? = null,
    ) {
        analyticsService.logProfileShared(
            profileUserId = profileUserId,
            method = method,
            isOwnProfile = isOwnProfile,
            cardTheme = cardTheme?.analyticsValue,
        )
    }

    fun logProfileShareSheetOpened(profileUserId: String, isOwnProfile: Boolean, entryPoint: String) {
        analyticsService.logProfileShareSheetOpened(profileUserId, isOwnProfile, entryPoint)
    }

    fun generatePlaylist(
        userId: String,
        source: CloudFunctionsDataSource.ProfilePlaylistSource = CloudFunctionsDataSource.ProfilePlaylistSource.Posts,
        // Lifts the backend's 75-track snapshot cap to export the whole source.
        fullExport: Boolean = false,
    ) {
        viewModelScope.launch {
            // Someone else's profile → TIDAL playlist is titled with their username.
            nowPlayingManager.generateProfilePlaylist(userId, source, isOwnProfile = false, fullExport = fullExport)
        }
    }

    private val _profile = MutableStateFlow<CymbalUser?>(null)
    val profile: StateFlow<CymbalUser?> = _profile.asStateFlow()

    // Set when getProfileData returns NOT_FOUND — the account is banned (shadow
    // or hard) or deleted and doesn't exist for this viewer. The screen swaps to
    // an "unavailable" state so we never render a stale header, and we must NOT
    // fall back to a direct Firestore read (which bypasses the ban).
    private val _profileUnavailable = MutableStateFlow(false)
    val profileUnavailable: StateFlow<Boolean> = _profileUnavailable.asStateFlow()

    private val _hasLoadError = MutableStateFlow(false)
    val hasLoadError: StateFlow<Boolean> = _hasLoadError.asStateFlow()

    private val _posts = MutableStateFlow<List<CymbalPost>>(emptyList())
    val posts: StateFlow<List<CymbalPost>> = _posts.asStateFlow()

    // Starts true (mirrors iOS isGridLoading) so the first frame cannot
    // paint "No songs yet" before loadProfile() sets posts.
    private val _isLoading = MutableStateFlow(true)
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

    // Song-backfill state, symmetric to the film fetch above. The MUSIC grid is
    // a client-side filter over the recency-ordered [posts] window, so a
    // film-dominant poster whose latest ~PAGE_SIZE posts are all films arrives
    // with no songs in that window. These drive a mediaType="track" backfill so
    // the MUSIC tab shows a skeleton → songs instead of flashing "No songs yet".
    private val _isLoadingSongs = MutableStateFlow(false)
    val isLoadingSongs: StateFlow<Boolean> = _isLoadingSongs.asStateFlow()

    private val _hasFetchedSongPage = MutableStateFlow(false)
    val hasFetchedSongPage: StateFlow<Boolean> = _hasFetchedSongPage.asStateFlow()

    // Liked posts — a separate, lazily-loaded list for the LIKES tab (segment 2).
    // These are the posts the profile *owner* has liked, NOT their own posts.
    // The MUSIC/FILM tabs filter the owner's own [posts]; likes come from a
    // dedicated backend call (getLikedPosts reads the owner's `liked`
    // subcollection). Mirrors iOS ProfileViewModel.likedPosts and the
    // self-profile ProfileViewModel.
    private val _likedPosts = MutableStateFlow<List<CymbalPost>>(emptyList())
    val likedPosts: StateFlow<List<CymbalPost>> = _likedPosts.asStateFlow()

    private val _isLoadingLiked = MutableStateFlow(false)
    val isLoadingLiked: StateFlow<Boolean> = _isLoadingLiked.asStateFlow()

    private val _likedHasMore = MutableStateFlow(true)
    val likedHasMore: StateFlow<Boolean> = _likedHasMore.asStateFlow()

    // True once the LIKES tab's first page has finished loading (success, empty,
    // or error). The grid uses this — mirroring [hasFetchedFilmPage] — to show
    // the skeleton and suppress the empty state until the lazy load completes,
    // so the empty state never flashes before the likes arrive.
    private val _hasFetchedLikedPage = MutableStateFlow(false)
    val hasFetchedLikedPage: StateFlow<Boolean> = _hasFetchedLikedPage.asStateFlow()

    // Offset-based cursor for likes pagination (getLikedPosts is offset-paged,
    // unlike the timestamp-cursored profile posts). Reset when the cache is
    // invalidated (profile switch / pull-to-refresh).
    private var likedOffset: Int = 0
    private var likedLoaded = false
    private var likedLoadedUserId: String? = null

    private var postsLastTimestamp: Long? = null
    private val PAGE_SIZE = 30
    private val _isFollowing = MutableStateFlow(false)
    val isFollowing: StateFlow<Boolean> = _isFollowing.asStateFlow()

    // Whether the *viewed* user follows the local user — drives the "FOLLOW BACK"
    // label on the follow pill (matches iOS `followsMe`). Resolved server-side in
    // loadProfile/refresh via a single following-edge read; defaults false so the
    // pill shows plain "FOLLOW" until the answer lands and never flashes a wrong
    // label. On read error it stays false (graceful: shows FOLLOW, never lies).
    private val _followsMe = MutableStateFlow(false)
    val followsMe: StateFlow<Boolean> = _followsMe.asStateFlow()


    private val _isBlocked = MutableStateFlow(false)
    val isBlocked: StateFlow<Boolean> = _isBlocked.asStateFlow()

    private val _isMuted = MutableStateFlow(false)
    val isMuted: StateFlow<Boolean> = _isMuted.asStateFlow()

    private val _isSubscribedToNotifications = MutableStateFlow(false)
    val isSubscribedToNotifications: StateFlow<Boolean> = _isSubscribedToNotifications.asStateFlow()

    private val _isFavorite = MutableStateFlow(false)
    val isFavorite: StateFlow<Boolean> = _isFavorite.asStateFlow()

    // Set when a favorite attempt hits the favorite-people cap. The screen
    // observes this to open the Club paywall, then calls clear. Mirrors the
    // now-playing paywall-request flow.
    private val _favoriteCapPaywallRequested = MutableStateFlow(false)
    val favoriteCapPaywallRequested: StateFlow<Boolean> = _favoriteCapPaywallRequested.asStateFlow()
    fun clearFavoriteCapPaywallRequested() { _favoriteCapPaywallRequested.value = false }

    // Taste-match payload alongside the target user — drives the profile teaser
    // pill and the detail sheet. Null when there's no overlap (or on own-profile).
    private val _matchData = MutableStateFlow<MusicMatchData?>(null)
    val matchData: StateFlow<MusicMatchData?> = _matchData.asStateFlow()

    // Catalog artist linked via artistLinks. Arrives with getProfileData so the
    // card paints with the rest of the header (no follow-up callable).
    private val _linkedArtist = MutableStateFlow<LinkedArtist?>(null)
    val linkedArtist: StateFlow<LinkedArtist?> = _linkedArtist.asStateFlow()

    val isProfileArtistLinkEnabled: Boolean
        get() = remoteConfig.isProfileArtistLinkEnabled(authRepository.userProfile.value?.username)

    fun logProfileArtistLinkTapped(artistId: String, profileUserId: String) {
        analyticsService.logProfileArtistLinkTapped(artistId, profileUserId)
    }

    val currentUserId: String? get() = authRepository.currentUserId

    val engagementStates = engagementManager.states

    fun toggleLike(postId: String) {
        val userId = authRepository.currentUserId ?: return
        engagementManager.toggleLike(postId, userId)
    }


    // Tracks the userId this ViewModel has loaded data for. Used to skip
    // redundant fetches when the composable re-enters composition after
    // forward-then-back navigation (e.g. profile → profile feed → back).
    private var loadedUserId: String? = null

    // All MutableStateFlow backing fields must be declared before `init` —
    // viewModelScope.launch uses Dispatchers.Main.immediate, so these
    // collectors run synchronously up to first suspension and StateFlow.collect
    // emits its current value eagerly. A null backing field at that point
    // crashes with NPE on .getValue (same class as Crashlytics 96b87ad5).
    init {
        viewModelScope.launch {
            networkMonitor.isConnected.collect { connected ->
                // Auto-retry when the network returns if the previous attempt
                // failed and nothing is on screen (matches own ProfileViewModel).
                val userId = loadedUserId
                if (connected && _hasLoadError.value && _profile.value == null && userId != null) {
                    retryLoad(userId)
                }
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

    fun start(userId: String, initialIsFollowing: Boolean?) {
        // Skip a redundant fetch when this owner is already on screen or
        // still loading. A previous failure (profile still null, not banned)
        // must retry — otherwise LaunchedEffect re-entry after a flake leaves
        // the chrome-only blank page forever.
        if (loadedUserId == userId &&
            (_profile.value != null || _isLoading.value || _profileUnavailable.value)
        ) return
        // Seed from the explicit hint when a caller passes an authoritative
        // optimistic value; otherwise fall back to the cached following set
        // (the same source the feed's Follow pill reads) so the button is
        // correct on first paint instead of defaulting to a stale value.
        // loadProfile() reconciles against the server regardless.
        _isFollowing.value = initialIsFollowing ?: userRepository.isFollowing(userId)
        loadProfile(userId)
    }

    /** Re-run [loadProfile] after a previous failure. */
    fun retryLoad(userId: String) {
        loadedUserId = null
        _hasLoadError.value = false
        start(userId, initialIsFollowing = _isFollowing.value)
    }

    fun loadProfile(userId: String) {
        loadedUserId = userId
        _profileUnavailable.value = false
        // Reset the lazily-loaded LIKES state so a reused ViewModel (profile ->
        // profile navigation) never shows the previous owner's likes or a stale
        // loaded/empty flag while the new owner's likes fetch.
        likedLoaded = false
        likedLoadedUserId = null
        likedOffset = 0
        _likedPosts.value = emptyList()
        _likedHasMore.value = true
        _hasFetchedLikedPage.value = false
        // Reset the song-backfill state too, so a reused ViewModel (profile ->
        // profile navigation) never carries the previous owner's fetched flag.
        _hasFetchedSongPage.value = false
        _isLoadingSongs.value = false
        // Same for films: a reused ViewModel that already fetched films for
        // the previous owner would skip loadFilmPageIfNeeded and leave a
        // film-primary profile stuck on "No films yet".
        _hasFetchedFilmPage.value = false
        _isLoadingFilms.value = false
        _linkedArtist.value = null
        viewModelScope.launch {
            _isLoading.value = true
            _hasLoadError.value = false
            try {
                _isFollowing.value = userRepository.isFollowing(userId)
                _isBlocked.value = userRepository.blockedIds.value.contains(userId)
                _isMuted.value = userRepository.isUserMuted(userId)
                val viewerIdForSub = authRepository.currentUserId
                if (viewerIdForSub != null) {
                    _isSubscribedToNotifications.value = userRepository.isSubscribedToUserPosts(viewerIdForSub, userId)
                    _isFavorite.value = userRepository.isFavorite(viewerIdForSub, userId)
                    // Does the viewed user follow me? Drives the FOLLOW BACK label.
                    _followsMe.value = runCatching {
                        userRepository.doesUserFollow(userId, viewerIdForSub)
                    }.getOrDefault(false)
                }

                val viewerId = authRepository.currentUserId
                if (viewerId == null) {
                    _hasLoadError.value = true
                    return@launch
                }

                // Single round-trip: user (with live cymbalCount via count() aggregation)
                // and the first page of posts come from the same backend call, so the
                // header count and the grid can't disagree on cold load. If this fails,
                // fall back to the legacy two-call path.
                val page: List<CymbalPost> = try {
                    val data = postRepository.getProfileData(userId = userId, pageSize = PAGE_SIZE)
                    if (data.user != null) {
                        _profile.value = data.user
                        _matchData.value = data.match
                        _linkedArtist.value = data.linkedArtist
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
                } catch (e: Exception) {
                    // Banned (shadow or hard) or deleted → getProfileData returns
                    // NOT_FOUND. Bounce to the unavailable state; do NOT fall back
                    // to the direct read below, which bypasses the ban and would
                    // leak the profile. Transient errors still take the fallback.
                    if (e is com.google.firebase.functions.FirebaseFunctionsException &&
                        e.code == com.google.firebase.functions.FirebaseFunctionsException.Code.NOT_FOUND
                    ) {
                        _profileUnavailable.value = true
                        _isLoading.value = false
                        return@launch
                    }
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
                        repostCount = post.repostCount, saveCount = post.saveCount,
                        isLiked = post.isLiked,
                        isSaved = false,
                    )
                }
                engagementManager.checkLikeStatuses(page.map { it.id }, viewerId)
                engagementManager.checkSaveStatuses(page.map { it.id }, viewerId)
                // Backfill songs when the recency window is film-only so the
                // MUSIC tab shows skeleton → songs instead of flashing "No
                // songs yet". Symmetric to the FILM tab's loadFilmPageIfNeeded.
                // Bots have a single tab, so skip the backfill for them.
                if (_profile.value?.isBot != true && page.none { it.mediaType == MediaType.TRACK }) {
                    loadSongPageIfNeeded(userId)
                }
            } catch (_: Exception) {
                if (_profile.value == null && !_profileUnavailable.value) {
                    _hasLoadError.value = true
                }
            }
            if (_profile.value == null && !_profileUnavailable.value) {
                _hasLoadError.value = true
            }
            _isLoading.value = false
            // After the mixed page is applied and loading has cleared — a
            // film-primary profile opens already on FILM, and the recency
            // window can be music-only. Kick here (not only on tab change)
            // so first load matches pull-to-refresh. Must run after
            // isLoading=false; loadFilmPageIfNeeded no-ops while loading
            // so an early LaunchedEffect can't mark the tab fetched empty.
            if (_profile.value?.isBot != true &&
                _posts.value.none { it.mediaType == MediaType.MOVIE }
            ) {
                loadFilmPageIfNeeded(userId)
            }
        }
    }

    fun refresh(userId: String, includeFilms: Boolean = false, includeLikes: Boolean = false) {
        if (_isRefreshing.value) return
        val viewerId = authRepository.currentUserId ?: return
        loadedUserId = userId
        // Invalidate the likes cache so the next LIKES-tab visit refetches. When
        // we're on the LIKES tab now (includeLikes), reload it inline below so
        // the visible grid updates without a tab round-trip.
        if (!includeLikes) {
            likedLoaded = false
            _hasFetchedLikedPage.value = false
        }
        // Only reset the film-fetch flag if there's actually something to re-fetch.
        // The counter (when available) is authoritative; fall back to the free
        // guard for older backends without the field.
        val knownZeroFilms = _profile.value?.movieCount == 0
        val certainlyZeroFilms = knownZeroFilms ||
            (!_hasMore.value && _posts.value.none { it.mediaType == MediaType.MOVIE })
        if (!certainlyZeroFilms) {
            _hasFetchedFilmPage.value = false
        }
        // Same for songs: allow the backfill to run again unless we're certain
        // there are none, so a post-refresh film-only window re-fetches songs.
        val knownZeroSongs = _profile.value?.trackCount == 0
        val certainlyZeroSongs = knownZeroSongs ||
            (!_hasMore.value && _posts.value.none { it.mediaType == MediaType.TRACK })
        if (!certainlyZeroSongs) {
            _hasFetchedSongPage.value = false
        }
        _isRefreshing.value = true
        viewModelScope.launch {
            try {
                // Route the user fetch through getProfileData so a ban that lands
                // while the profile is open bounces on pull-to-refresh too, instead
                // of re-showing the now-hidden account via a direct read.
                val user = try {
                    val data = postRepository.getProfileData(userId = userId, pageSize = 1)
                    _matchData.value = data.match
                    _linkedArtist.value = data.linkedArtist
                    data.user ?: userRepository.fetchUserProfile(userId)
                } catch (e: Exception) {
                    if (e is com.google.firebase.functions.FirebaseFunctionsException &&
                        e.code == com.google.firebase.functions.FirebaseFunctionsException.Code.NOT_FOUND
                    ) {
                        _profileUnavailable.value = true
                        _isRefreshing.value = false
                        return@launch
                    }
                    userRepository.fetchUserProfile(userId)
                }
                _profile.value = user
                _isFollowing.value = userRepository.isFollowing(userId)
                _isBlocked.value = userRepository.blockedIds.value.contains(userId)
                _isMuted.value = userRepository.isUserMuted(userId)
                _isSubscribedToNotifications.value =
                    userRepository.isSubscribedToUserPosts(viewerId, userId)
                _followsMe.value = runCatching {
                    userRepository.doesUserFollow(userId, viewerId)
                }.getOrDefault(false)

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
                        repostCount = post.repostCount, saveCount = post.saveCount,
                        isLiked = post.isLiked,
                        isSaved = false,
                    )
                }
                engagementManager.checkLikeStatuses(merged.map { it.id }, viewerId)
                engagementManager.checkSaveStatuses(merged.map { it.id }, viewerId)
                // Re-backfill songs if the refreshed recency window has none, so
                // the MUSIC tab never settles on "No songs yet" for a film-heavy
                // poster whose songs live deeper in history. Bots skip this.
                if (_profile.value?.isBot != true && merged.none { it.mediaType == MediaType.TRACK }) {
                    loadSongPageIfNeeded(userId)
                }

                if (includeLikes) {
                    val liked = cloudFunctions.getLikedPosts(userId, viewerId, limit = PAGE_SIZE, offset = 0)
                    _likedPosts.value = liked.posts
                    likedOffset = PAGE_SIZE
                    _likedHasMore.value = liked.hasMore
                    likedLoaded = true
                    likedLoadedUserId = userId
                    _hasFetchedLikedPage.value = true
                    initEngagement(liked.posts, viewerId)
                }
            } catch (_: Exception) { }
            _isRefreshing.value = false
        }
    }

    fun loadFilmPageIfNeeded(userId: String) {
        if (_hasFetchedFilmPage.value) return
        // Mixed posts aren't applied yet. Return without flipping the fetched
        // flag so the call after loadProfile (or LaunchedEffect when loading
        // finishes) still runs — otherwise a film-primary first frame can
        // mark the tab fetched against an empty window and stick on empty.
        if (_isLoading.value) return
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
                            repostCount = post.repostCount, saveCount = post.saveCount,
                            isLiked = post.isLiked,
                            isSaved = false,
                        )
                    }
                    engagementManager.checkLikeStatuses(additions.map { it.id }, viewerId)
                    engagementManager.checkSaveStatuses(additions.map { it.id }, viewerId)
                }
            } catch (_: Exception) {
                _hasFetchedFilmPage.value = false
            }
            _isLoadingFilms.value = false
        }
    }

    /**
     * Backfills the most-recent songs when the recency-ordered first page held
     * only films. The MUSIC grid is a client-side filter over the mixed [posts]
     * window, so a film-dominant poster whose latest ~PAGE_SIZE posts are all
     * films would otherwise read "No songs yet" despite having songs deeper in
     * their history. Symmetric to [loadFilmPageIfNeeded].
     */
    fun loadSongPageIfNeeded(userId: String) {
        if (_hasFetchedSongPage.value || _isLoadingSongs.value) return
        val viewerId = authRepository.currentUserId ?: return
        val hasAnySongs = _posts.value.any { it.mediaType == MediaType.TRACK }
        // Prefer the counter (authoritative); fall back to the "all posts loaded,
        // none are songs" free guard for older backends without the field.
        val knownZeroSongs = _profile.value?.trackCount == 0
        if (knownZeroSongs || (!_hasMore.value && !hasAnySongs)) {
            _hasFetchedSongPage.value = true
            return
        }
        // If the counter is present and we've already cached at least that many
        // songs, there's nothing left to backfill.
        val total = _profile.value?.trackCount
        val cachedSongs = _posts.value.count { it.mediaType == MediaType.TRACK }
        if (total != null && cachedSongs >= total) {
            _hasFetchedSongPage.value = true
            return
        }
        _hasFetchedSongPage.value = true
        if (!hasAnySongs) _isLoadingSongs.value = true
        viewModelScope.launch {
            try {
                val songs = postRepository.getProfilePosts(
                    userId = userId,
                    viewerId = viewerId,
                    limit = PAGE_SIZE,
                    lastTimestamp = null,
                    mediaType = "track",
                )
                val existing = _posts.value
                val additions = songs.filter { s -> existing.none { it.id == s.id } }
                if (additions.isNotEmpty()) {
                    _posts.value = existing + additions
                    initEngagement(additions, viewerId)
                }
            } catch (_: Exception) {
                _hasFetchedSongPage.value = false
            }
            _isLoadingSongs.value = false
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
                        repostCount = post.repostCount, saveCount = post.saveCount,
                        isLiked = post.isLiked,
                        isSaved = false,
                    )
                }
                engagementManager.checkLikeStatuses(newPosts.map { it.id }, viewerId)
                engagementManager.checkSaveStatuses(newPosts.map { it.id }, viewerId)
            } catch (_: Exception) { }
            _isLoadingMore.value = false
        }
    }

    /**
     * Lazy-load the profile owner's liked posts for the LIKES tab. Runs once
     * per profile (guarded by [likedLoaded]); the guard is reset on profile
     * switch and pull-to-refresh. getLikedPosts reads the owner's `liked`
     * subcollection server-side — the viewerId arg is for caller-scoped like
     * state, not whose likes are returned.
     */
    fun loadLikedPosts(userId: String) {
        if (likedLoaded && likedLoadedUserId == userId) return
        val viewerId = authRepository.currentUserId ?: return
        likedLoaded = true
        likedLoadedUserId = userId
        _isLoadingLiked.value = true
        viewModelScope.launch {
            try {
                val liked = cloudFunctions.getLikedPosts(userId, viewerId, limit = PAGE_SIZE, offset = 0)
                _likedPosts.value = liked.posts
                // getLikedPosts pages by *ref* offset (server-side), so advance by
                // the page size we requested, not by how many posts hydrated — a
                // short page (orphan/banned refs filtered out) must not desync the
                // cursor. Use the server's hasMore for the same reason.
                likedOffset = PAGE_SIZE
                _likedHasMore.value = liked.hasMore
                initEngagement(liked.posts, viewerId)
            } catch (_: Exception) {
                // Allow a retry on the next tab visit.
                likedLoaded = false
            }
            _isLoadingLiked.value = false
            _hasFetchedLikedPage.value = true
        }
    }

    fun loadMoreLiked(userId: String) {
        if (!_likedHasMore.value || _isLoadingMore.value || _isLoadingLiked.value) return
        val viewerId = authRepository.currentUserId ?: return
        _isLoadingMore.value = true
        viewModelScope.launch {
            try {
                val more = cloudFunctions.getLikedPosts(userId, viewerId, limit = PAGE_SIZE, offset = likedOffset)
                val existingIds = _likedPosts.value.mapTo(HashSet()) { it.id }
                val unique = more.posts.filter { it.id !in existingIds }
                _likedPosts.value = _likedPosts.value + unique
                // Advance by the ref page size (not posts returned) to stay aligned
                // with the server's ref-based offset; trust the server's hasMore so
                // a short page doesn't prematurely halt pagination.
                likedOffset += PAGE_SIZE
                _likedHasMore.value = more.hasMore
                initEngagement(unique, viewerId)
            } catch (_: Exception) { }
            _isLoadingMore.value = false
        }
    }

    /** Seed engagement state + real-time listeners for a freshly-fetched page. */
    private fun initEngagement(posts: List<CymbalPost>, viewerId: String) {
        posts.forEach { post ->
            engagementManager.initState(
                postId = post.id,
                likeCount = post.likeCount,
                commentCount = post.commentCount,
                repostCount = post.repostCount, saveCount = post.saveCount,
                isLiked = post.isLiked,
                isSaved = false,
            )
        }
        if (posts.isNotEmpty()) engagementManager.checkLikeStatuses(posts.map { it.id }, viewerId)
        if (posts.isNotEmpty()) engagementManager.checkSaveStatuses(posts.map { it.id }, viewerId)
    }

    fun logFollowingOptionsOpened(targetUserId: String) {
        analyticsService.logFollowingOptionsOpened(targetUserId)
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
                    analyticsService.logUnfollowUser(userId)
                } else {
                    userRepository.followUser(currentUserId, userId)
                    analyticsService.logFollowUser(userId)
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
                analyticsService.logFollowError(userId, e.message ?: e.toString())
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

    /** Returns the new favorited state so the screen can show the right toast. */
    fun toggleFavorite(userId: String): Boolean {
        val currentUserId = authRepository.currentUserId ?: return _isFavorite.value
        val wasFavorite = _isFavorite.value
        // Instant local cap pre-check (only when adding) — open the paywall
        // instead of attempting the favorite. Server enforces independently.
        if (!wasFavorite && subscriptionRepository.shouldRejectFavorite()) {
            analyticsService.logFavoritePeopleCapReached(subscriptionRepository.favoritesCount.value)
            _favoriteCapPaywallRequested.value = true
            return wasFavorite
        }
        val nowFavorite = !wasFavorite
        _isFavorite.value = nowFavorite
        if (nowFavorite) analyticsService.logFavoriteAdded(userId)
        else analyticsService.logFavoriteRemoved(userId)
        // Let the Favorites feed update itself without a manual refresh.
        favoriteChangedEvent.notify(userId, nowFavorite)
        viewModelScope.launch {
            try {
                if (wasFavorite) {
                    userRepository.removeFavorite(currentUserId, userId)
                } else {
                    userRepository.addFavorite(currentUserId, userId)
                }
            } catch (e: CloudFunctionsDataSource.FavoriteCapReachedException) {
                // Server backstop — roll back and open the paywall.
                _isFavorite.value = wasFavorite
                favoriteChangedEvent.notify(userId, wasFavorite)
                subscriptionRepository.setFavoritesCount(e.favoritesCount)
                analyticsService.logFavoritePeopleCapReached(e.favoritesCount)
                _favoriteCapPaywallRequested.value = true
            } catch (_: Exception) {
                _isFavorite.value = wasFavorite
                favoriteChangedEvent.notify(userId, wasFavorite)
            }
        }
        return nowFavorite
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
                    analyticsService.logMuteUser(userId)
                    val message = if (username != null) context.getString(R.string.other_profile_muted_format, username) else context.getString(R.string.other_profile_muted)
                    ToastManager.update(toastId, message)
                } else {
                    userRepository.unmuteUser(currentUserId, userId)
                    analyticsService.logUnmuteUser(userId)
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
    }
}
