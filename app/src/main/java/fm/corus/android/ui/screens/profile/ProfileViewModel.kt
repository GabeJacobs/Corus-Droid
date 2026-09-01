package fm.corus.android.ui.screens.profile

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import fm.corus.android.R
import fm.corus.android.data.model.CymbalPost
import fm.corus.android.data.model.CymbalUser
import fm.corus.android.data.model.LinkedArtist
import fm.corus.android.data.model.MediaType
import fm.corus.android.data.remote.CloudFunctionsDataSource
import fm.corus.android.data.repository.AuthRepository
import fm.corus.android.data.repository.MessageRepository
import fm.corus.android.data.repository.SubscriptionRepository
import fm.corus.android.data.repository.UserRepository
import fm.corus.android.domain.CommentDeletedEvent
import fm.corus.android.domain.CommentEditedEvent
import fm.corus.android.domain.NowPlayingManager
import fm.corus.android.domain.PlaylistTrialField
import fm.corus.android.domain.PostCreationEvent
import fm.corus.android.domain.PostDeletionEvent
import fm.corus.android.domain.PostEngagementManager
import fm.corus.android.service.AnalyticsService
import fm.corus.android.service.NetworkMonitor
import fm.corus.android.service.RemoteConfigService
import fm.corus.android.ui.components.ToastManager
import fm.corus.android.ui.components.ShareCardTheme
import fm.corus.android.ui.screens.feed.applyCommentDeleteToPosts
import fm.corus.android.ui.screens.feed.applyCommentEditToPosts
import fm.corus.android.ui.screens.feed.loadRecentDmShareContacts
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Media-type lens for the Likes/Saves grids (which mix songs + films, unlike
 * the type-pure Music/Film tabs). Mirrors the web + iOS profile filter.
 * [mediaType] is the post field this lens matches, or null for "all".
 */
enum class ProfileMediaFilter(val mediaType: String?) {
    ALL(null),
    MUSIC("track"),
    FILM("movie"),
}

/** One page of filtered engagement posts, with the RAW offset to resume from. */
data class FilteredEngagementPage(
    val posts: List<CymbalPost>,
    val nextOffset: Int,
    val hasMore: Boolean,
)

/**
 * Pure offset/match computation for a media-filtered Likes/Saves page, split out
 * so it can be unit-tested without the network. Given the raw rows scanned so
 * far (in engagement-recency order), keeps the matches up to [pageSize] and
 * computes the RAW offset to resume from — from the last match returned, so the
 * next page neither skips nor repeats — plus whether more remain. [moreAfterScan]
 * is whether the underlying source still had rows beyond what was scanned.
 */
fun computeFilteredEngagementPage(
    rawRows: List<CymbalPost>,
    startOffset: Int,
    mediaType: String,
    pageSize: Int,
    moreAfterScan: Boolean,
): FilteredEngagementPage {
    val matchIndices = rawRows.indices.filter { rawRows[it].mediaType.value == mediaType }
    val take = matchIndices.take(pageSize)
    val posts = take.map { rawRows[it] }
    val nextOffset =
        if (matchIndices.size > pageSize) startOffset + take.last() + 1
        else startOffset + rawRows.size
    val hasMore = matchIndices.size > pageSize || moreAfterScan
    return FilteredEngagementPage(posts, nextOffset, hasMore)
}

/**
 * First-load apply can race a just-posted corus: the launch cache /
 * getProfilePosts page started before the write. Keep local posts that
 * aren't on that page and are newer than its newest row.
 */
fun mergeProfilePostsPreservingOptimistic(
    incoming: List<CymbalPost>,
    local: List<CymbalPost>,
): List<CymbalPost> {
    if (local.isEmpty()) return incoming
    val incomingIds = incoming.mapTo(HashSet()) { it.id }
    val newestIncoming = incoming.maxOfOrNull { it.timestamp.time }
    val extras = local.filter { post ->
        post.id !in incomingIds && (newestIncoming == null || post.timestamp.time >= newestIncoming)
    }
    if (extras.isEmpty()) return incoming
    return extras + incoming
}

@HiltViewModel
class ProfileViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val authRepository: AuthRepository,
    private val cloudFunctions: CloudFunctionsDataSource,
    private val userRepository: UserRepository,
    private val messageRepository: MessageRepository,
    private val subscriptionRepository: SubscriptionRepository,
    val nowPlayingManager: NowPlayingManager,
    val musicServicePreference: fm.corus.android.domain.MusicServicePreference,
    private val engagementManager: PostEngagementManager,
    private val postCreationEvent: PostCreationEvent,
    private val postDeletionEvent: PostDeletionEvent,
    private val commentEditedEvent: CommentEditedEvent,
    private val commentDeletedEvent: CommentDeletedEvent,
    private val saveChangedEvent: fm.corus.android.domain.SaveChangedEvent,
    private val analyticsService: AnalyticsService,
    private val remoteConfigService: RemoteConfigService,
    private val networkMonitor: NetworkMonitor,
    private val ownProfileLaunchCache: OwnProfileLaunchCache,
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

    suspend fun resolveSpotifyFromAppleTrack(track: fm.corus.android.data.model.CymbalTrack): String? =
        fm.corus.android.domain.MusicServiceLinkOut.resolveSpotifyUrlForAppleTrack(track, cloudFunctions)

    val isClubMember = subscriptionRepository.isClubMember
    val hasFullAccess = subscriptionRepository.hasFullAccessFlow

    fun shouldPaywallOwnProfilePlaylist(): Boolean =
        subscriptionRepository.shouldPaywallPlaylist(PlaylistTrialField.OwnProfile)

    val stylePack1Enabled: Boolean get() = remoteConfigService.stylePack1Enabled
    val immersiveArtistHeaderEnabled: Boolean get() = remoteConfigService.immersiveArtistHeaderEnabled

    val corusFlairOpen: Boolean get() = remoteConfigService.corusFlairOpen

    /** Send-side gate for the in-app "Share Profile Link" Corus share sheet. */
    val profileShareEnabled: Boolean get() = remoteConfigService.profileShareEnabled
    val instagramShareEnabled: Boolean get() = remoteConfigService.instagramShareEnabled

    // ── Profile share sheet ──
    // Sharing your own profile: DMs send a `sharedProfile` message deep-linking
    // to your profile. Recents are the most recent people you've DMed.

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

    fun logProfileShareThemeChanged(profileUserId: String, cardTheme: ShareCardTheme) {
        analyticsService.logProfileShareThemeChanged(profileUserId, cardTheme.analyticsValue)
    }

    val engagementStates = engagementManager.states

    private val _profile = MutableStateFlow<CymbalUser?>(null)
    val profile: StateFlow<CymbalUser?> = _profile.asStateFlow()

    // Catalog artist linked via artistLinks. Own-profile loads posts separately
    // from getProfileData, so this is fetched via getLinkedArtistForUser.
    private val _linkedArtist = MutableStateFlow<LinkedArtist?>(null)
    val linkedArtist: StateFlow<LinkedArtist?> = _linkedArtist.asStateFlow()

    val isProfileArtistLinkEnabled: Boolean
        get() = remoteConfigService.isProfileArtistLinkEnabled(authRepository.userProfile.value?.username)

    fun logProfileArtistLinkTapped(artistId: String, profileUserId: String) {
        analyticsService.logProfileArtistLinkTapped(artistId, profileUserId)
    }

    // Optimistic avatar preview: set at the start of uploadAvatar and cleared
    // once the server round-trip completes, so the new avatar appears immediately
    // without waiting for Firestore + Storage + cache-busted URL reload.
    private val _pendingAvatarBytes = MutableStateFlow<ByteArray?>(null)
    val pendingAvatarBytes: StateFlow<ByteArray?> = _pendingAvatarBytes.asStateFlow()

    // Profile posts (tracks + movies together, shared by MUSIC and FILM tabs)
    private val _posts = MutableStateFlow<List<CymbalPost>>(emptyList())
    val posts: StateFlow<List<CymbalPost>> = _posts.asStateFlow()

    // Liked & saved posts are separate lists, loaded lazily
    private val _likedPosts = MutableStateFlow<List<CymbalPost>>(emptyList())
    val likedPosts: StateFlow<List<CymbalPost>> = _likedPosts.asStateFlow()

    private val _savedPosts = MutableStateFlow<List<CymbalPost>>(emptyList())
    val savedPosts: StateFlow<List<CymbalPost>> = _savedPosts.asStateFlow()

    // Active media lens for whichever of Likes/Saves is selected; reset to ALL
    // when the segment changes (see [resetLikesSavesLens]).
    private val _likesSavesFilter = MutableStateFlow(ProfileMediaFilter.ALL)
    val likesSavesFilter: StateFlow<ProfileMediaFilter> = _likesSavesFilter.asStateFlow()

    // Fires after the user creates a post, carrying the logical profile segment
    // that surfaces it (0 = Music, 1 = Film). The screen uses this to auto-switch
    // tabs so the just-posted content is visible instead of leaving the user on a
    // tab where it never appears (e.g. posting a film while on the Music tab).
    private val _switchToSegment = MutableSharedFlow<Int>(extraBufferCapacity = 1)
    val switchToSegment = _switchToSegment.asSharedFlow()

    // All MutableStateFlow backing fields must be declared before `init` —
    // viewModelScope.launch uses Dispatchers.Main.immediate, so the launches
    // below run synchronously up to first suspension and StateFlow.collect
    // emits its current value eagerly. A null backing field at that point
    // crashes with NPE on .setValue. (Crashlytics 96b87ad5.)

    // Starts true (mirrors iOS isGridLoading) so the off-screen own-profile
    // tab cannot paint the music empty prompt before loadProfile() runs.
    // Seeded from auth with empty posts; LaunchedEffect only fetches once
    // the tab is selected, one frame after first composition.
    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    /** True when [loadProfile] failed AND there's no profile/posts to render. */
    private val _hasLoadError = MutableStateFlow(false)
    val hasLoadError: StateFlow<Boolean> = _hasLoadError.asStateFlow()

    val isConnected: StateFlow<Boolean> = networkMonitor.isConnected

    private val _isLoadingMore = MutableStateFlow(false)
    val isLoadingMore: StateFlow<Boolean> = _isLoadingMore.asStateFlow()

    private val _isLoadingLiked = MutableStateFlow(false)
    val isLoadingLiked: StateFlow<Boolean> = _isLoadingLiked.asStateFlow()

    private val _isLoadingSaved = MutableStateFlow(false)
    val isLoadingSaved: StateFlow<Boolean> = _isLoadingSaved.asStateFlow()

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

    private val _isSavingStyle = MutableStateFlow(false)
    val isSavingStyle: StateFlow<Boolean> = _isSavingStyle.asStateFlow()

    private val _currentSegment = MutableStateFlow(0)

    private val _hasMore = MutableStateFlow(mapOf(0 to true, 1 to true, 2 to true, 3 to true))
    val hasMore: StateFlow<Map<Int, Boolean>> = _hasMore.asStateFlow()

    init {
        viewModelScope.launch {
            networkMonitor.isConnected.collect { connected ->
                // Auto-retry profile load when the network returns if the previous
                // attempt failed and nothing is on screen.
                if (connected && _hasLoadError.value && _profile.value == null) {
                    retryLoad()
                }
            }
        }
        // Keep _profile in sync with authRepository so edits from EditProfileScreen
        // (which refresh authRepository._userProfile) are reflected without a manual reload.
        viewModelScope.launch {
            authRepository.userProfile.collect { user ->
                if (user != null) _profile.value = user
            }
        }
        // Optimistic insert when compose sent a card (matches iOS). Fall
        // back to a short live refetch when only the media type arrived.
        viewModelScope.launch {
            postCreationEvent.events.collect { created ->
                _switchToSegment.tryEmit(if (created.mediaType == MediaType.MOVIE) 1 else 0)
                if (created.post != null) {
                    insertOptimisticPost(created.post)
                } else {
                    delay(500)
                    refreshProfile()
                }
            }
        }
        // Drop deleted posts from any visible list so returning from
        // PostDetail reflects the deletion immediately (matches iOS).
        viewModelScope.launch {
            postDeletionEvent.events.collect { deletedId ->
                _posts.value = _posts.value.filter { it.id != deletedId }
                _likedPosts.value = _likedPosts.value.filter { it.id != deletedId }
                _savedPosts.value = _savedPosts.value.filter { it.id != deletedId }
            }
        }
        viewModelScope.launch {
            commentEditedEvent.events.collect { payload ->
                _posts.value = applyCommentEditToPosts(_posts.value, payload)
                _likedPosts.value = applyCommentEditToPosts(_likedPosts.value, payload)
                _savedPosts.value = applyCommentEditToPosts(_savedPosts.value, payload)
            }
        }
        viewModelScope.launch {
            commentDeletedEvent.events.collect { payload ->
                _posts.value = applyCommentDeleteToPosts(_posts.value, payload)
                _likedPosts.value = applyCommentDeleteToPosts(_likedPosts.value, payload)
                _savedPosts.value = applyCommentDeleteToPosts(_savedPosts.value, payload)
            }
        }
        // Keep the Saves tab in sync with save/unsave actions taken anywhere
        // else this session (feed, post detail, other profiles) without a manual
        // refresh: unsaving drops the post instantly, saving inserts it at the
        // top. Only mutates a list that's already been loaded — an unopened tab
        // fetches fresh on first visit, which already reflects the change.
        viewModelScope.launch {
            saveChangedEvent.events.collect { change ->
                if (!savedLoaded) return@collect
                if (change.isSaved) {
                    val post = change.post ?: return@collect
                    val matchesFilter = when (_likesSavesFilter.value) {
                        ProfileMediaFilter.ALL -> true
                        ProfileMediaFilter.MUSIC -> post.isTrack
                        ProfileMediaFilter.FILM -> post.isMovie
                    }
                    if (matchesFilter && _savedPosts.value.none { it.id == change.postId }) {
                        _savedPosts.value = listOf(post) + _savedPosts.value
                    }
                } else {
                    _savedPosts.value = _savedPosts.value.filter { it.id != change.postId }
                }
            }
        }
    }

    fun toggleLike(postId: String) {
        val userId = authRepository.currentUserId ?: return
        engagementManager.toggleLike(postId, userId)
    }

    fun uploadAvatar(imageData: ByteArray) {
        val uid = authRepository.currentUserId ?: return
        _pendingAvatarBytes.value = imageData
        viewModelScope.launch {
            try {
                userRepository.uploadAvatar(uid, imageData)
                authRepository.refreshUserProfile()
                _profile.value = authRepository.userProfile.value
                analyticsService.logAvatarChanged()
            } catch (e: Exception) {
                android.util.Log.e("ProfileViewModel", "uploadAvatar failed", e)
            } finally {
                _pendingAvatarBytes.value = null
            }
        }
    }

    fun generatePlaylist(
        source: CloudFunctionsDataSource.ProfilePlaylistSource = CloudFunctionsDataSource.ProfilePlaylistSource.Posts,
        // Lifts the backend's 75-track snapshot cap to export the whole source.
        fullExport: Boolean = false,
    ) {
        val userId = authRepository.currentUserId ?: return
        analyticsService.logProfilePlaylistTapped(userId)
        viewModelScope.launch {
            nowPlayingManager.generateProfilePlaylist(userId, source, isOwnProfile = true, fullExport = fullExport)
        }
    }

    private var postsLastTimestamp: Long? = null
    // Separate film-only cursor — segment 1 paginates with mediaType="movie",
    // so it can't share the mixed-media cursor used by segment 0 (the shared
    // cursor advances past films into music-only territory and pagination
    // returns no films, leaving the tab stuck on the featured post).
    private var filmsLastTimestamp: Long? = null
    private var likedOffset: Int = 0
    private var savedOffset: Int = 0

    private var likedLoaded = false
    private var savedLoaded = false

    private var segmentLoadJob: Job? = null

    // Guards against redundant fetches when the composable re-enters composition
    // after forward-then-back navigation (e.g. profile → profile feed → back).
    // Reset by refreshProfile() so pull-to-refresh still fetches.
    private var hasLoaded = false

    // Throttle window for tab-activation refreshes of the featured post(s).
    // Stamped by loadProfile/refreshProfile so a freshly-loaded screen doesn't
    // immediately refetch on entry.
    private var lastFeaturedRefreshAt: Long = 0L

    // Clock seam — overridden in tests so the throttle window can be exercised
    // without sleeping. Production uses System.currentTimeMillis.
    @androidx.annotation.VisibleForTesting
    var clock: () -> Long = System::currentTimeMillis

    private val PAGE_SIZE = 30
    fun loadProfile() {
        if (hasLoaded) return
        val userId = authRepository.currentUserId ?: return
        hasLoaded = true
        viewModelScope.launch {
            _isLoading.value = true
            try {
                authRepository.refreshUserProfile()
                _profile.value = authRepository.userProfile.value
                _profile.value?.let { profile ->
                    subscriptionRepository.updateVerifiedStatus(profile.isVerified)
                    analyticsService.setUserProperties(
                        userId = profile.id,
                        username = profile.username,
                        isClubMember = profile.isClubMember,
                        isVerified = profile.isVerified,
                        postCount = profile.cymbalCount,
                        followerCount = profile.followerCount,
                        followingCount = profile.followingCount,
                        isBot = profile.isBot,
                    )
                    analyticsService.logProfileViewed(profile.id, isOwnProfile = true)
                }
                // Prefer the launch prefetch so tapping Profile right after
                // open already has posts. Fetch stays off the hidden tab
                // until this apply — same as iOS OwnProfileLaunchCache.
                ownProfileLaunchCache.awaitInFlight(userId)
                val cached = ownProfileLaunchCache.peek(userId)
                val page = if (cached != null) {
                    cached
                } else {
                    cloudFunctions.getProfilePosts(userId, userId, limit = PAGE_SIZE, lastTimestamp = null)
                }
                val merged = mergeProfilePostsPreservingOptimistic(page, _posts.value)
                ownProfileLaunchCache.store(userId, merged)
                _posts.value = merged
                if (merged.isNotEmpty()) postsLastTimestamp = merged.last().timestamp.time
                val serverHasMore = page.size >= PAGE_SIZE
                _hasMore.value = _hasMore.value.toMutableMap().apply {
                    this[0] = serverHasMore
                    this[1] = serverHasMore
                }
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
                engagementManager.checkLikeStatuses(merged.map { it.id }, userId)
                engagementManager.checkSaveStatuses(merged.map { it.id }, userId)
                lastFeaturedRefreshAt = clock()
                _hasLoadError.value = false
                // Backfill songs when the recency window is film-only so the
                // MUSIC tab shows skeleton → songs instead of flashing "No
                // songs yet". Symmetric to the FILM tab's loadFilmPageIfNeeded.
                if (merged.none { it.mediaType == MediaType.TRACK }) loadSongPageIfNeeded()
                // Film-primary own profiles land on FILM without a tab tap, so
                // loadSegment(1) never runs. Kick the movie-only fetch when the
                // mixed recency window missed films — same empty-state bug as
                // other-profile / iOS.
                if (merged.none { it.mediaType == MediaType.MOVIE }) loadFilmPageIfNeeded()
                loadLinkedArtist(userId)
            } catch (e: Exception) {
                android.util.Log.e("ProfileViewModel", "loadProfile failed", e)
                if (_profile.value == null) {
                    _hasLoadError.value = true
                }
            } finally {
                _isLoading.value = false
            }
        }
    }

    private fun loadLinkedArtist(userId: String) {
        if (!isProfileArtistLinkEnabled) return
        viewModelScope.launch {
            _linkedArtist.value = runCatching { cloudFunctions.getLinkedArtistForUser(userId) }.getOrNull()
        }
    }

    fun insertOptimisticPost(post: CymbalPost) {
        if (_posts.value.any { it.id == post.id }) return
        _posts.value = listOf(post) + _posts.value
        ownProfileLaunchCache.prependOptimistic(post)
    }

    /** Re-run [loadProfile] after a previous failure. */
    fun retryLoad() {
        hasLoaded = false
        _hasLoadError.value = false
        loadProfile()
    }

    fun refreshProfile() {
        hasLoaded = true
        // Whether to clear hasFetchedFilmPage. If we're already on the Films
        // tab, we run the movie-only fetch inline below and swap in fresh
        // posts silently — clearing the flag would flip filmFetchPending true
        // and flash the skeleton over the existing grid (music doesn't have
        // an equivalent flag, so its grid swaps silently). Skip the clear
        // also when we're confident films are zero (counter says 0 or all
        // posts are loaded and none are movies) — same reason: avoid an
        // empty-state-to-skeleton-to-empty-state flicker. In all other
        // cases (refreshing from a non-film tab, films unknown), clear so
        // the next Films tab visit refetches.
        val onFilmsTab = _currentSegment.value == 1
        val knownZeroFilms = _profile.value?.movieCount == 0
        val allPostsLoaded = _hasMore.value[0] != true
        val noFilmsCached = _posts.value.none { it.mediaType == fm.corus.android.data.model.MediaType.MOVIE }
        if (!(onFilmsTab || knownZeroFilms || (allPostsLoaded && noFilmsCached))) {
            _hasFetchedFilmPage.value = false
        }
        // Same for songs: allow the backfill to run again unless we're certain
        // there are none, so a post-refresh film-only window re-fetches songs.
        // Unlike films, the music grid swaps silently (no on-tab inline fetch),
        // so there's no on-MUSIC-tab carve-out to make here.
        val knownZeroSongs = _profile.value?.trackCount == 0
        val noSongsCached = _posts.value.none { it.mediaType == fm.corus.android.data.model.MediaType.TRACK }
        if (!(knownZeroSongs || (allPostsLoaded && noSongsCached))) {
            _hasFetchedSongPage.value = false
        }
        viewModelScope.launch {
            _isLoading.value = true
            _isRefreshing.value = true
            try {
                authRepository.refreshUserProfile()
                _profile.value = authRepository.userProfile.value
                val userId = authRepository.currentUserId ?: return@launch
                val page = cloudFunctions.getProfilePosts(userId, userId, limit = PAGE_SIZE, lastTimestamp = null)
                val movieSupplement = if (onFilmsTab) {
                    runCatching {
                        cloudFunctions.getProfilePosts(userId, userId, limit = PAGE_SIZE, lastTimestamp = null, mediaType = "movie")
                    }.getOrDefault(emptyList())
                } else emptyList()
                val withFilms = page + movieSupplement.filter { m -> page.none { it.id == m.id } }
                val merged = mergeProfilePostsPreservingOptimistic(withFilms, _posts.value)
                ownProfileLaunchCache.store(userId, merged)
                _posts.value = merged
                if (page.isNotEmpty()) postsLastTimestamp = page.last().timestamp.time
                filmsLastTimestamp = if (onFilmsTab && movieSupplement.isNotEmpty()) {
                    movieSupplement.last().timestamp.time
                } else null
                if (onFilmsTab) _hasFetchedFilmPage.value = true
                val serverHasMore = page.size >= PAGE_SIZE
                _hasMore.value = _hasMore.value.toMutableMap().apply {
                    this[0] = serverHasMore
                    // When refreshing on the Films tab we did a movie-only fetch — use
                    // its size to decide film hasMore. Otherwise leave it open so the
                    // next Films tab visit triggers loadFilmPageIfNeeded().
                    this[1] = if (onFilmsTab) movieSupplement.size >= PAGE_SIZE else true
                }
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
                engagementManager.checkLikeStatuses(merged.map { it.id }, userId)
                engagementManager.checkSaveStatuses(merged.map { it.id }, userId)
                lastFeaturedRefreshAt = clock()
                // Re-backfill songs if the refreshed recency window has none, so
                // the MUSIC tab never settles on "No songs yet" for a film-heavy
                // poster whose songs live deeper in history.
                if (merged.none { it.mediaType == MediaType.TRACK }) loadSongPageIfNeeded()
                loadLinkedArtist(userId)
                // Reset lazy-loaded segments so they reload on next visit
                likedLoaded = false
                savedLoaded = false
                _hasMore.value = _hasMore.value.toMutableMap().apply {
                    this[2] = true
                    this[3] = true
                }
                likedOffset = 0
                savedOffset = 0
            } catch (e: Exception) {
                android.util.Log.e("ProfileViewModel", "refreshProfile failed", e)
            } finally {
                _isLoading.value = false
                _isRefreshing.value = false
            }
            // If the user is currently viewing Likes/Saves, re-fetch that segment
            // immediately so pull-to-refresh doesn't leave them on an empty state.
            when (_currentSegment.value) {
                2 -> loadLikedPosts()
                3 -> loadSavedPosts()
            }
        }
    }

    /**
     * Cheap refresh of the featured posts' engagement counts, fired when the
     * user re-enters the profile tab. Throttled — stamped by loadProfile and
     * refreshProfile so a freshly-loaded screen doesn't immediately refetch,
     * and frequent tab-switching can't spam Firestore.
     *
     * Now-stamp is set BEFORE the async fetches so flapping/network failures
     * don't cause repeated retries within the throttle window. Pull-to-refresh
     * remains the user-visible recovery path.
     */
    fun refreshFeaturedPostsIfStale(minIntervalMs: Long = 60_000L) {
        val now = clock()
        if (lastFeaturedRefreshAt != 0L && now - lastFeaturedRefreshAt < minIntervalMs) return
        val userId = authRepository.currentUserId ?: return
        val currentPosts = _posts.value
        val latestTrack = currentPosts.firstOrNull { it.mediaType == MediaType.TRACK }
        val latestMovie = currentPosts.firstOrNull { it.mediaType == MediaType.MOVIE }
        val ids = listOfNotNull(latestTrack?.id, latestMovie?.id).distinct()
        if (ids.isEmpty()) return

        lastFeaturedRefreshAt = now

        viewModelScope.launch {
            ids.forEach { postId ->
                launch {
                    try {
                        val updated = cloudFunctions.getPostDetail(postId, userId) ?: return@launch
                        _posts.value = _posts.value.map { if (it.id == updated.id) updated else it }
                        engagementManager.initState(
                            postId = updated.id,
                            likeCount = updated.likeCount,
                            commentCount = updated.commentCount,
                            repostCount = updated.repostCount, saveCount = updated.saveCount,
                            isLiked = updated.isLiked,
                            isSaved = false,
                        )
                    } catch (e: Exception) {
                        android.util.Log.e("ProfileViewModel", "refreshFeaturedPostsIfStale failed for $postId", e)
                    }
                }
            }
        }
    }

    fun saveStyleSelections(fields: Map<String, Any>, onResult: (Boolean) -> Unit = {}) {
        val uid = authRepository.currentUserId ?: return
        _isSavingStyle.value = true
        viewModelScope.launch {
            val ok = try {
                userRepository.updateUserProfile(uid, fields)
                authRepository.refreshUserProfile()
                _profile.value = authRepository.userProfile.value
                true
            } catch (e: Exception) {
                android.util.Log.e("ProfileViewModel", "saveStyleSelections failed", e)
                false
            }
            _isSavingStyle.value = false
            onResult(ok)
        }
    }

    /**
     * Called when the user taps a segment tab.
     * Segments 0 (MUSIC) shares posts already loaded; FILM (1) may need a
     * movie-only supplementary fetch if the recency-sorted initial page didn't
     * include films.
     * Segments 2 (LIKES) and 3 (SAVES) lazy-load on first visit.
     */
    fun loadSegment(index: Int) {
        _currentSegment.value = index
        analyticsService.logProfileSegmentChanged(segmentAnalyticsName(index))
        when (index) {
            // Posts already loaded in loadProfile; backfill songs if this tab's
            // recency window held only films (mirrors the FILM tab's fetch).
            0 -> loadSongPageIfNeeded()
            1 -> loadFilmPageIfNeeded()
            2 -> if (!likedLoaded) loadLikedPosts()
            3 -> if (!savedLoaded) loadSavedPosts()
        }
    }

    fun loadFilmPageIfNeeded() {
        if (_hasFetchedFilmPage.value || _isLoadingFilms.value) return
        val userId = authRepository.currentUserId ?: return
        // Synchronous "we know there are zero films" short-circuit (matches
        // iOS). Skip the fetch and mark the page fetched so the empty state
        // renders immediately, no skeleton flash. Two signals:
        //   1. movieCount == 0 (counter is authoritative when present)
        //   2. The initial posts page returned everything (hasMore[0] is
        //      false) and no movies are cached. Covers fresh users whose
        //      movieCount field hasn't been initialized yet (still null) and
        //      music-only users with < PAGE_SIZE posts.
        // Drift on a non-zero counter only delays new films appearing until
        // next refresh, which we accept to avoid the new-user skeleton flash.
        val knownZeroFilms = _profile.value?.movieCount == 0
        val allPostsLoaded = _hasMore.value[0] != true
        val noFilmsCached = _posts.value.none { it.mediaType == fm.corus.android.data.model.MediaType.MOVIE }
        if (knownZeroFilms || (allPostsLoaded && noFilmsCached)) {
            _hasFetchedFilmPage.value = true
            return
        }
        // The denormalized movieCount on the user doc can drift (the backend
        // ships a backfillMediaCounts repair job for exactly this reason), so
        // we don't trust any *non-zero* value to short-circuit the film-only
        // fetch. The fetch is a single callable; running it once per Film tab
        // visit is cheap and keeps the displayed list authoritative regardless
        // of counter state. Use isLoadingFilms as the in-flight guard
        // (re-entrancy is blocked by the early return above) so the skeleton
        // stays up while the fetch is running, even when one or more films
        // are already cached from the recency-sorted initial page.
        _isLoadingFilms.value = true
        viewModelScope.launch {
            try {
                val movies = cloudFunctions.getProfilePosts(
                    userId, userId,
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
                    engagementManager.checkLikeStatuses(additions.map { it.id }, userId)
                    engagementManager.checkSaveStatuses(additions.map { it.id }, userId)
                }
                if (movies.isNotEmpty()) filmsLastTimestamp = movies.last().timestamp.time
                _hasMore.value = _hasMore.value.toMutableMap().apply {
                    this[1] = movies.size >= PAGE_SIZE
                }
                _hasFetchedFilmPage.value = true
            } catch (e: Exception) {
                android.util.Log.e("ProfileViewModel", "loadFilmPageIfNeeded failed", e)
            }
            _isLoadingFilms.value = false
        }
    }

    /**
     * Fetches a page of song-only posts so the MUSIC tab isn't empty when the
     * recency-sorted initial page contained only films. Symmetric to
     * [loadFilmPageIfNeeded]. Idempotent — [_hasFetchedSongPage] prevents
     * re-entry once it has run (or once we've proven there are zero songs).
     */
    fun loadSongPageIfNeeded() {
        if (_hasFetchedSongPage.value || _isLoadingSongs.value) return
        val userId = authRepository.currentUserId ?: return
        // Synchronous "we know there are zero songs" short-circuit (matches
        // iOS): skip the fetch and mark the page fetched so the empty state
        // renders immediately, no skeleton flash. Two signals:
        //   1. trackCount == 0 (counter is authoritative when present)
        //   2. The initial posts page returned everything (hasMore[0] is false)
        //      and no songs are cached. Covers fresh users whose trackCount
        //      field hasn't been initialized yet (still null).
        val knownZeroSongs = _profile.value?.trackCount == 0
        val allPostsLoaded = _hasMore.value[0] != true
        val noSongsCached = _posts.value.none { it.mediaType == MediaType.TRACK }
        if (knownZeroSongs || (allPostsLoaded && noSongsCached)) {
            _hasFetchedSongPage.value = true
            return
        }
        // When the counter is present and we've already cached at least that
        // many songs, there's nothing left to backfill.
        val total = _profile.value?.trackCount
        val cachedSongs = _posts.value.count { it.mediaType == MediaType.TRACK }
        if (total != null && cachedSongs >= total) {
            _hasFetchedSongPage.value = true
            return
        }
        // Set the flag up-front to prevent re-entry while the network call is
        // awaiting; reset on failure so a retry is possible. isLoadingSongs
        // keeps the skeleton up during the fetch.
        _hasFetchedSongPage.value = true
        _isLoadingSongs.value = true
        viewModelScope.launch {
            try {
                val songs = cloudFunctions.getProfilePosts(
                    userId, userId,
                    limit = PAGE_SIZE,
                    lastTimestamp = null,
                    mediaType = "track",
                )
                val existing = _posts.value
                val additions = songs.filter { s -> existing.none { it.id == s.id } }
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
                    engagementManager.checkLikeStatuses(additions.map { it.id }, userId)
                    engagementManager.checkSaveStatuses(additions.map { it.id }, userId)
                }
            } catch (e: Exception) {
                android.util.Log.e("ProfileViewModel", "loadSongPageIfNeeded failed", e)
                _hasFetchedSongPage.value = false
            }
            _isLoadingSongs.value = false
        }
    }

    private fun segmentAnalyticsName(index: Int): String = when (index) {
        0 -> "music"
        1 -> "film"
        2 -> "likes"
        3 -> "saves"
        else -> "unknown"
    }

    private enum class EngagementKind { LIKED, SAVED }

    /**
     * Fetches a page of liked/saved posts, optionally filtered by media type.
     * The callables can't filter by type (it lives on the post, not the
     * engagement index), so with a filter we page through the raw rows in
     * bounded batches and keep the matches until a page is filled or the rows
     * run out — capped by a scan budget so a type-sparse history can't fan out
     * into unbounded round-trips. The continuation cursor is the RAW offset.
     * Mirrors the web + iOS implementation.
     */
    private suspend fun fetchFilteredEngagement(
        kind: EngagementKind,
        userId: String,
        startOffset: Int,
        mediaType: String?,
    ): FilteredEngagementPage {
        suspend fun raw(limit: Int, offset: Int): List<CymbalPost> = when (kind) {
            EngagementKind.LIKED -> cloudFunctions.getLikedPosts(userId, userId, limit = limit, offset = offset).posts
            EngagementKind.SAVED -> cloudFunctions.getSavedPosts(userId, limit = limit, offset = offset)
        }

        if (mediaType == null) {
            val posts = raw(PAGE_SIZE, startOffset)
            return FilteredEngagementPage(posts, startOffset + posts.size, posts.size >= PAGE_SIZE)
        }

        val batch = 45
        val maxRounds = 6 // ≤270 rows scanned per page
        val rawRows = mutableListOf<CymbalPost>()
        var more = true
        var rounds = 0
        while (rounds < maxRounds && more) {
            val r = raw(batch, startOffset + rawRows.size)
            rawRows.addAll(r)
            more = r.size >= batch
            rounds++
            if (r.isEmpty()) { more = false; break }
            if (rawRows.count { it.mediaType.value == mediaType } >= PAGE_SIZE) break
        }
        return computeFilteredEngagementPage(rawRows, startOffset, mediaType, PAGE_SIZE, more)
    }

    private fun loadLikedPosts() {
        val userId = authRepository.currentUserId ?: return
        segmentLoadJob?.cancel()
        _isLoadingLiked.value = true
        segmentLoadJob = viewModelScope.launch {
            try {
                val page = fetchFilteredEngagement(EngagementKind.LIKED, userId, 0, _likesSavesFilter.value.mediaType)
                _likedPosts.value = page.posts
                likedLoaded = true
                likedOffset = page.nextOffset
                _hasMore.value = _hasMore.value.toMutableMap().apply {
                    this[2] = page.hasMore
                }
            } catch (e: Exception) {
                android.util.Log.e("ProfileViewModel", "loadLikedPosts failed", e)
            }
            _isLoadingLiked.value = false
        }
    }

    private fun loadSavedPosts() {
        val userId = authRepository.currentUserId ?: return
        segmentLoadJob?.cancel()
        _isLoadingSaved.value = true
        segmentLoadJob = viewModelScope.launch {
            try {
                val page = fetchFilteredEngagement(EngagementKind.SAVED, userId, 0, _likesSavesFilter.value.mediaType)
                _savedPosts.value = page.posts
                // These came from the viewer's own Saves index, so they ARE saved —
                // seed isSaved=true so opening one shows a filled bookmark instantly
                // (no unfilled→filled flash from the detail screen's reconcile).
                engagementManager.seedSavedState(page.posts.map { it.id })
                savedLoaded = true
                savedOffset = page.nextOffset
                _hasMore.value = _hasMore.value.toMutableMap().apply {
                    this[3] = page.hasMore
                }
            } catch (e: Exception) {
                android.util.Log.e("ProfileViewModel", "loadSavedPosts failed", e)
            }
            _isLoadingSaved.value = false
        }
    }

    /** Apply a new media lens to the active Likes/Saves segment: reset that
     *  list and reload it under the new filter from the start. */
    fun setLikesSavesFilter(filter: ProfileMediaFilter, segment: Int) {
        if (_likesSavesFilter.value == filter) return
        _likesSavesFilter.value = filter
        when (segment) {
            2 -> {
                _likedPosts.value = emptyList()
                likedOffset = 0
                likedLoaded = false
                _hasMore.value = _hasMore.value.toMutableMap().apply { this[2] = true }
                loadLikedPosts()
            }
            3 -> {
                _savedPosts.value = emptyList()
                savedOffset = 0
                savedLoaded = false
                _hasMore.value = _hasMore.value.toMutableMap().apply { this[3] = true }
                loadSavedPosts()
            }
        }
    }

    /** Drop any active Likes/Saves lens when the segment changes, so a stale
     *  filter never silently hides content on the next visit. Clears the (now
     *  type-filtered) cached lists so the next visit reloads the full set. */
    fun resetLikesSavesLens() {
        if (_likesSavesFilter.value == ProfileMediaFilter.ALL) return
        _likesSavesFilter.value = ProfileMediaFilter.ALL
        _likedPosts.value = emptyList(); likedOffset = 0; likedLoaded = false
        _savedPosts.value = emptyList(); savedOffset = 0; savedLoaded = false
        _hasMore.value = _hasMore.value.toMutableMap().apply { this[2] = true; this[3] = true }
    }

    fun loadMoreForSegment(segment: Int) {
        if (_hasMore.value[segment] != true) return
        if (_isLoadingMore.value || _isLoading.value) return
        val userId = authRepository.currentUserId ?: return

        _isLoadingMore.value = true
        viewModelScope.launch {
            try {
                when (segment) {
                    0 -> {
                        val cursor = postsLastTimestamp
                        if (cursor != null) {
                            val newPosts = cloudFunctions.getProfilePosts(userId, userId, limit = PAGE_SIZE, lastTimestamp = cursor)
                            val existingIds = _posts.value.mapTo(HashSet()) { it.id }
                            val unique = newPosts.filter { it.id !in existingIds }
                            _posts.value = _posts.value + unique
                            if (newPosts.isNotEmpty()) {
                                postsLastTimestamp = newPosts.last().timestamp.time
                            }
                            _hasMore.value = _hasMore.value.toMutableMap().apply {
                                this[0] = newPosts.size >= PAGE_SIZE
                            }
                        } else {
                            _hasMore.value = _hasMore.value.toMutableMap().apply {
                                this[0] = false
                            }
                        }
                    }
                    1 -> {
                        // Wait for the film-only fetch to populate the cursor; without
                        // it we'd either short-circuit hasMore[1] to false or, worse,
                        // paginate a mixed-media stream that the UI filters down to nothing.
                        val ready = _hasFetchedFilmPage.value && !_isLoadingFilms.value
                        val cursor = if (ready) filmsLastTimestamp else null
                        if (!ready) {
                            // No-op: leave hasMore[1] true so the next scroll retries.
                        } else if (cursor != null) {
                            val newPosts = cloudFunctions.getProfilePosts(
                                userId, userId,
                                limit = PAGE_SIZE,
                                lastTimestamp = cursor,
                                mediaType = "movie",
                            )
                            val existingIds = _posts.value.mapTo(HashSet()) { it.id }
                            val unique = newPosts.filter { it.id !in existingIds }
                            if (unique.isNotEmpty()) {
                                _posts.value = _posts.value + unique
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
                                engagementManager.checkLikeStatuses(unique.map { it.id }, userId)
                                engagementManager.checkSaveStatuses(unique.map { it.id }, userId)
                            }
                            if (newPosts.isNotEmpty()) {
                                filmsLastTimestamp = newPosts.last().timestamp.time
                            }
                            _hasMore.value = _hasMore.value.toMutableMap().apply {
                                this[1] = newPosts.size >= PAGE_SIZE
                            }
                        } else {
                            _hasMore.value = _hasMore.value.toMutableMap().apply {
                                this[1] = false
                            }
                        }
                    }
                    2 -> {
                        val page = fetchFilteredEngagement(EngagementKind.LIKED, userId, likedOffset, _likesSavesFilter.value.mediaType)
                        val existingIds = _likedPosts.value.mapTo(HashSet()) { it.id }
                        val unique = page.posts.filter { it.id !in existingIds }
                        _likedPosts.value = _likedPosts.value + unique
                        likedOffset = page.nextOffset
                        _hasMore.value = _hasMore.value.toMutableMap().apply {
                            this[2] = page.hasMore
                        }
                    }
                    3 -> {
                        val page = fetchFilteredEngagement(EngagementKind.SAVED, userId, savedOffset, _likesSavesFilter.value.mediaType)
                        val existingIds = _savedPosts.value.mapTo(HashSet()) { it.id }
                        val unique = page.posts.filter { it.id !in existingIds }
                        _savedPosts.value = _savedPosts.value + unique
                        engagementManager.seedSavedState(unique.map { it.id })
                        savedOffset = page.nextOffset
                        _hasMore.value = _hasMore.value.toMutableMap().apply {
                            this[3] = page.hasMore
                        }
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("ProfileViewModel", "loadMoreForSegment failed", e)
            }
            _isLoadingMore.value = false
        }
    }
}

/**
 * Fetches own-profile posts at launch without publishing to [ProfileViewModel],
 * so the hidden Profile tab doesn't rebuild during first feed scroll.
 * Tapping Profile applies this snapshot (header still seeds from auth/disk).
 */
@Singleton
class OwnProfileLaunchCache @Inject constructor(
    private val cloudFunctions: CloudFunctionsDataSource,
) {
    private val mutex = Mutex()
    private var userId: String? = null
    private var posts: List<CymbalPost>? = null
    private var inFlight: Deferred<List<CymbalPost>?>? = null
    private val pendingOptimistic = mutableListOf<CymbalPost>()

    fun prefetchIfNeeded(userId: String, scope: CoroutineScope) {
        scope.launch {
            val fetch: Deferred<List<CymbalPost>?> = mutex.withLock {
                if (this@OwnProfileLaunchCache.userId != userId) {
                    posts = null
                    pendingOptimistic.clear()
                    inFlight?.cancel()
                    inFlight = null
                    this@OwnProfileLaunchCache.userId = userId
                }
                if (posts != null) return@launch
                inFlight?.let { return@withLock it }
                scope.async {
                    runCatching {
                        cloudFunctions.getProfilePosts(userId, userId, limit = 30, lastTimestamp = null)
                    }.getOrNull()
                }.also { inFlight = it }
            }
            val result = runCatching { fetch.await() }.getOrNull()
            mutex.withLock {
                if (this@OwnProfileLaunchCache.userId == userId) {
                    if (result != null) posts = applyingPending(result)
                    if (inFlight === fetch) inFlight = null
                }
            }
        }
    }

    fun peek(userId: String): List<CymbalPost>? =
        posts.takeIf { this.userId == userId }

    /** No-op when nothing is in flight (unit tests never prefetch). */
    suspend fun awaitInFlight(userId: String) {
        val pending = inFlight.takeIf { this.userId == userId } ?: return
        runCatching { pending.await() }
    }

    fun store(userId: String, page: List<CymbalPost>) {
        this.userId = userId
        posts = applyingPending(page)
        inFlight = null
    }

    fun prependOptimistic(post: CymbalPost) {
        if (userId == null) {
            userId = post.user.id
        } else if (userId != post.user.id) {
            return
        }
        val current = posts
        if (current != null) {
            posts = mergeProfilePostsPreservingOptimistic(current, listOf(post))
            return
        }
        if (pendingOptimistic.none { it.id == post.id }) {
            pendingOptimistic.add(0, post)
        }
    }

    private fun applyingPending(page: List<CymbalPost>): List<CymbalPost> {
        val extras = pendingOptimistic.toList()
        pendingOptimistic.clear()
        return mergeProfilePostsPreservingOptimistic(page, extras)
    }
}
