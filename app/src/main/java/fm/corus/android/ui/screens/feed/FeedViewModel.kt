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
import fm.corus.android.data.model.FeedDecade
import fm.corus.android.data.model.FeedFilter
import fm.corus.android.data.model.MediaType
import fm.corus.android.data.model.SuggestedUserMatch
import fm.corus.android.data.remote.CloudFunctionsDataSource
import fm.corus.android.data.remote.TasteMatchesTrial
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
import fm.corus.android.domain.PlaylistTrialField
import fm.corus.android.domain.PostCreationEvent
import fm.corus.android.domain.PostDeletionEvent
import fm.corus.android.domain.PostEngagementManager
import fm.corus.android.domain.FullSongPlayCoordinator
import fm.corus.android.domain.toQueuedTrack
import fm.corus.android.service.AnalyticsService
import fm.corus.android.service.FeedSwitchHintManager
import fm.corus.android.service.NetworkMonitor
import fm.corus.android.service.RemoteConfigService
import fm.corus.android.ui.components.PostMenuActions
import fm.corus.android.ui.components.ToastManager
import kotlinx.coroutines.CancellationException
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
 *  created on or after this instant see the one-time hint on the first eligible
 *  track in the feed; older accounts never see it. */
internal val ALBUM_ART_HINT_CUTOFF_MS: Long =
    java.time.LocalDate.of(2026, 5, 10)
        .atStartOfDay(java.time.ZoneOffset.UTC)
        .toInstant()
        .toEpochMilli()

internal fun isNewAlbumArtHintAccount(creationTimestampMs: Long?): Boolean {
    val created = creationTimestampMs ?: return false
    return created >= ALBUM_ART_HINT_CUTOFF_MS
}

/** First track in [posts] eligible for the new-user album-art tap hint. Skips
 *  films and unavailable tracks so the hint still appears when the feed opens
 *  with a movie post. */
internal fun albumArtHintTargetPostId(posts: List<CymbalPost>): String? =
    posts.firstOrNull { it.isTrack && !it.track.unavailable }?.id

/** In-memory page per mode/filter so tab switches don't wipe the list
 *  and flash the skeleton. Restored on the way back in if it's still
 *  fresh; after [FEED_PAGE_CACHE_TTL_MS] a switch is a cold load.
 *  Pull-to-refresh always fetches. */
data class FeedModePageSnapshot(
    val posts: List<CymbalPost>,
    val hasMore: Boolean,
    val forYouSessionToken: String?,
    val forYouPageIndex: Int,
    val forYouLoadFailed: Boolean,
    val lastLoadFailed: Boolean,
    val lastTimestamp: Long?,
    val tasteMatchesGate: FeedViewModel.TasteMatchesGate?,
    val tasteMatchesTrial: TasteMatchesTrial?,
    val cachedAt: Long,
)

private const val FEED_PAGE_CACHE_TTL_MS = 5 * 60 * 1000L

/** Base backoff before a silent retry of a feed load that failed while the
 *  device is online. Long enough for a cold-start App Check / Play Integrity
 *  token to finish minting (the usual culprit), short enough that the first
 *  retry still feels like part of the initial load. Escalated per attempt
 *  (base * attemptNumber) so later retries wait proportionally longer. */
private const val FEED_TRANSIENT_RETRY_BACKOFF_MS = 1200L

/** How many times an online feed load is retried silently before the error
 *  panel is surfaced. A cold start after the OS killed the process (waking the
 *  phone from a long sleep) can leave App Check / Play Integrity and a cold
 *  Functions instance unready for several seconds — far longer than a single
 *  retry covered, which is what stranded users on the error panel after a normal
 *  wake. With escalating backoff these retries span ~12s (1.2+2.4+3.6+4.8), long
 *  enough that a normal cold start always recovers on its own. The error is then
 *  reserved for a genuine, persistent outage, not a transient warm-up blip. */
private const val FEED_TRANSIENT_MAX_RETRIES = 4

/** How many of those retries fire even when the connectivity flag reads offline.
 *  On a cold start right after waking the phone, the Wi-Fi radio may not have
 *  re-associated yet, so NetworkMonitor can momentarily seed `isConnected` false
 *  and the first call fails. Bailing straight to the offline error there is the
 *  "empty error flash" we want to avoid — a normal wake should just load. So give
 *  connectivity a brief grace (~1.2+2.4s) to establish before trusting an offline
 *  reading; a genuinely offline device still surfaces the offline panel once the
 *  grace is spent, then auto-recovers via the reconnect handler when it returns. */
private const val FEED_TRANSIENT_CONNECTIVITY_GRACE_RETRIES = 2

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
    private val feedSwitchHintManager: FeedSwitchHintManager,
    private val postCreationEvent: PostCreationEvent,
    private val postDeletionEvent: PostDeletionEvent,
    private val commentEditedEvent: CommentEditedEvent,
    private val commentDeletedEvent: CommentDeletedEvent,
    private val favoriteChangedEvent: fm.corus.android.domain.FavoriteChangedEvent,
    networkMonitor: NetworkMonitor,
    private val preferencesDataStore: fm.corus.android.data.local.PreferencesDataStore,
    private val playbackModePromptManager: fm.corus.android.domain.PlaybackModePromptManager,
    @ApplicationContext private val context: Context,
) : ViewModel(), PostMenuActions {

    /** One-time feed-switch hint coachmark visibility (device-local). */
    val feedSwitchHintVisible: StateFlow<Boolean> = feedSwitchHintManager.shouldShow

    /** Evaluate whether to show the feed-switch hint (called when the feed is shown). */
    fun evaluateFeedSwitchHint() = feedSwitchHintManager.evaluate()

    /** The feed-mode switcher (Corus logo) was opened — baseline signal + retire. */
    fun onFeedSwitcherOpened() = feedSwitchHintManager.markSwitcherOpened()

    /** The user tapped the hint bubble to dismiss it. */
    fun dismissFeedSwitchHint() = feedSwitchHintManager.dismiss()

    /**
     * Resolve the link-out URL for a Spotify-source track given the viewer's
     * preferred service (Apple Music / TIDAL / Deezer). Returns null for Spotify
     * (caller opens the post's own URI) and on no-match / error. Network-bound
     * for Apple/TIDAL/Deezer; cached per-process by MusicServiceLinkOut.
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

    // Seed the active filter synchronously from the SharedPreferences mirror so
    // a saved music/film selection is in place before the screen kicks off its
    // first load — without this the initial page fetches with ALL (the async
    // DataStore restore lands a frame later) and the user sees an all-content
    // flash before it corrects to the saved filter.
    private val _feedFilter = MutableStateFlow(
        runCatching { FeedFilter.valueOf(preferencesDataStore.feedFilterSyncSeed()) }
            .getOrDefault(FeedFilter.ALL)
    )
    val feedFilter: StateFlow<FeedFilter> = _feedFilter.asStateFlow()

    private val _feedDecade = MutableStateFlow(
        FeedDecade.fromStored(preferencesDataStore.feedDecadeSyncSeed())
    )
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
    val filteredPosts: StateFlow<List<CymbalPost>> = combine(_posts, _feedFilter, userRepository.hiddenUserIds) { posts, filter, hidden ->
        val visible = posts
            .filter { post ->
                post.user.id !in hidden &&
                    (post.repostedFromUserId.isNullOrEmpty() || post.repostedFromUserId !in hidden)
            }
            .map { post ->
                post.copy(
                    comments = post.comments.filter { it.user.id !in hidden },
                    likers = post.likers.filter { it.id !in hidden },
                )
            }
        if (filter.newReleasesOnly) visible.filter { it.isNewRelease() } else visible
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val _newReleaseFilterPaywall = MutableStateFlow<PaywallSource?>(null)
    val newReleaseFilterPaywall: StateFlow<PaywallSource?> = _newReleaseFilterPaywall.asStateFlow()
    fun clearNewReleaseFilterPaywall() { _newReleaseFilterPaywall.value = null }

    // ── Taste Matches (premium curator-first feed) gate ──
    /** Server-authoritative gate for the Taste Matches feed: cold-start (post
     *  more) or paywall. Null for a served feed. Only meaningful while
     *  feedMode == "tasteMatches". Mirrors iOS TasteMatchesGateState. */
    sealed interface TasteMatchesGate {
        data class NeedMorePosts(val postCount: Int, val threshold: Int) : TasteMatchesGate
        data object Paywall : TasteMatchesGate
        /** Server returned the feature disabled / no cohort yet (gated:"unavailable").
         *  Renders a neutral "No Taste Matches right now" empty (mirrors iOS). */
        data object Unavailable : TasteMatchesGate
        /** Posted enough, but shares no artist/director with anyone yet
         *  (gated:"noMatchesYet"). Actionable empty: post more (mirrors iOS). */
        data object NoMatchesYet : TasteMatchesGate
    }
    private val _tasteMatchesGate = MutableStateFlow<TasteMatchesGate?>(null)
    val tasteMatchesGate: StateFlow<TasteMatchesGate?> = _tasteMatchesGate.asStateFlow()

    /** Trigger for the Club paywall when a non-member taps Taste Matches. */
    private val _tasteMatchesPaywall = MutableStateFlow<PaywallSource?>(null)
    val tasteMatchesPaywall: StateFlow<PaywallSource?> = _tasteMatchesPaywall.asStateFlow()
    fun clearTasteMatchesPaywall() { _tasteMatchesPaywall.value = null }

    /** Free-trial banner state (`taste_matches_free_trial` RC) attached to the
     *  LIVE Taste Matches feed for a free (non-full-access) viewer. Null when
     *  not on the free-trial path (full access, RC off, gated, or another
     *  feed mode). Mirrors iOS/web `TasteMatchesTrial`. */
    private val _tasteMatchesTrial = MutableStateFlow<TasteMatchesTrial?>(null)
    val tasteMatchesTrial: StateFlow<TasteMatchesTrial?> = _tasteMatchesTrial.asStateFlow()

    /** The in-feed free-trial banner was tapped — route to the Club paywall.
     *  Distinct analytics source ("taste_matches_banner") from the menu-tap
     *  paywall so the funnels stay separable. */
    fun onTasteMatchesBannerTapped() {
        val trial = _tasteMatchesTrial.value ?: return
        analyticsService.logTasteMatchesBannerTapped(trial.phase, trial.daysRemaining)
        _tasteMatchesPaywall.value = PaywallSource.TASTE_MATCHES_BANNER
    }

    /** Cover art for the cold-start seed slots: already-counted posts (fetched)
     *  plus posts made optimistically this session. */
    private val _tasteMatchesSeedArt = MutableStateFlow<List<String>>(emptyList())
    val tasteMatchesSeedArt: StateFlow<List<String>> = _tasteMatchesSeedArt.asStateFlow()
    /** Count of posts made this session while on the cold-start (optimistic). */
    private val _tasteMatchesSeedCount = MutableStateFlow(0)
    val tasteMatchesSeedCount: StateFlow<Int> = _tasteMatchesSeedCount.asStateFlow()
    /** True during the post-3rd hand-off: keep the skeleton up while the server
     *  count catches up and serves the real feed. */
    private val _tasteMatchesSeeding = MutableStateFlow(false)
    val tasteMatchesSeeding: StateFlow<Boolean> = _tasteMatchesSeeding.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    private val _hasLoaded = MutableStateFlow(false)
    val hasLoaded: StateFlow<Boolean> = _hasLoaded.asStateFlow()
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    private val _hasMore = MutableStateFlow(true)
    val hasMore: StateFlow<Boolean> = _hasMore.asStateFlow()

    private var lastTimestamp: Long? = null

    /** Mirror of the viewer's starred-people count. Powers the Favorites tab
     *  gate and the auto-switch back to Following when the last favorite is
     *  removed. Declared before [feedMode] so resolve can read it on seed. */
    val favoritesCount: StateFlow<Int> = subscriptionRepository.favoritesCount
    val favoritesTabUnlocked: StateFlow<Boolean> = subscriptionRepository.favoritesTabUnlocked

    // ── Ranked feed state ──
    // Shared by the ranked modes (Trending + Taste Matches) that call
    // `getForYouFeed`. The `forYou*` field names below are historical (that
    // callable's name); the standalone "For You" feed MODE was retired in
    // favor of Taste Matches. Effective feed mode: "following" | "trending" |
    // "favorites" | "tasteMatches". An empty/unrecognized stored value (incl.
    // the retired "forYou" or never-shipped "discovery") opens on Following.
    /** Taste Matches is available when the RC master switch is on OR the viewer
     *  is a comped internal tester. Mirrors iOS `tasteMatchesAvailable`. */
    private val tasteMatchesAvailable: Boolean
        get() = remoteConfig.tasteMatchesEnabled || remoteConfig.tasteMatchesTester

    private fun resolveFeedMode(
        stored: String,
        favoritesCount: Int = this.favoritesCount.value,
        favoritesUnlocked: Boolean = this.favoritesTabUnlocked.value,
    ): String {
        // First resolve the stored choice. Anything unrecognized — empty
        // ("never picked"), the retired "forYou" mode, or the never-shipped
        // "discovery" — opens on Following.
        val resolved = when (stored) {
            "following", "trending", "favorites", "tasteMatches" -> stored
            else -> "following"
        }
        // …then guarantee it's a mode that's actually AVAILABLE. A ranked/gated
        // mode is only valid while its RC gate is on, but feedMode is
        // device-persisted and can outlive the flag — e.g. Favorites after the
        // flag is turned off. Returning a disabled mode leaves the menu with
        // nothing selected and loads a feed the user can't switch away from.
        // Following has no gate, so it's the safe fallback.
        // When the tab switcher is on, Favorites is also withheld until the
        // viewer has favorited someone (the tab itself is hidden) — same as iOS.
        return when (resolved) {
            "trending" -> if (remoteConfig.trendingFeedEnabled) "trending" else "following"
            "favorites" -> {
                if (!remoteConfig.favoritesEnabled) "following"
                else if (remoteConfig.feedModeTabsEnabled &&
                    !fm.corus.android.domain.FavoritesTabGate.showsTab(
                        featureEnabled = true,
                        count = favoritesCount,
                        unlocked = favoritesUnlocked,
                    )
                ) "following"
                else "favorites"
            }
            "tasteMatches" -> if (tasteMatchesAvailable) "tasteMatches" else "following"
            else -> "following"
        }
    }
    val feedMode: StateFlow<String> = combine(
        preferencesDataStore.feedMode,
        favoritesCount,
        favoritesTabUnlocked,
    ) { stored, count, unlocked -> resolveFeedMode(stored, count, unlocked) }
        // Seed from the synchronous mirror so the header icon is correct on the
        // first frame instead of flashing Following before DataStore resolves.
        // For an existing install that hasn't mirrored yet the seed reads ""
        // (→ resolves to the default), and the init collector below re-syncs
        // once the real persisted value lands — a one-launch fallback that then
        // self-heals as the mirror is written.
        .stateIn(
            viewModelScope,
            SharingStarted.Eagerly,
            resolveFeedMode(preferencesDataStore.feedModeSyncSeed()),
        )

    fun isDecadeFilterVisible(mode: String): Boolean =
        remoteConfig.feedDecadeFilterEnabled && mode == "trending"

    private fun decadeApplicableTo(mode: String, decade: Int?): Int? =
        if (isDecadeFilterVisible(mode)) decade else null

    val appliedFeedDecade: StateFlow<Int?> =
        combine(feedMode, _feedDecade) { mode, decade -> decadeApplicableTo(mode, decade) }
            .stateIn(
                viewModelScope,
                SharingStarted.Eagerly,
                decadeApplicableTo(feedMode.value, _feedDecade.value),
            )

    // The mode the currently-shown page was actually loaded with. feedMode
    // resolves from DataStore asynchronously (its eager seed is "following"),
    // so the screen's first loadFeed() can fire and fetch Following before the
    // persisted ranked mode lands — leaving the menu showing e.g. Trending as
    // selected while the posts are Following. Tracking what each load used lets
    // the init collector below re-sync when the resolved mode diverges.
    private var lastLoadedMode: String? = null

    private val feedPageCache = mutableMapOf<String, FeedModePageSnapshot>()
    private var appliedFeedSignature: String = ""
    private val _pageCacheRevision = MutableStateFlow(0)
    val pageCacheRevision: StateFlow<Int> = _pageCacheRevision.asStateFlow()

    private fun feedRequestSignature(mode: String = feedMode.value): String {
        val decade = decadeApplicableTo(mode, _feedDecade.value) ?: 0
        return "$mode|${_feedFilter.value.name}|$decade"
    }

    private fun applyFeedSignatureChange(from: String, to: String) {
        if (from == to && appliedFeedSignature == to) return
        stashFeedPage(from)
        if (restoreFeedPage(to)) {
            appliedFeedSignature = to
            lastLoadedMode = to.substringBefore('|')
            return
        }
        lastTimestamp = null
        _posts.value = emptyList()
        forYouSessionToken = null
        forYouPageIndex = 0
        _forYouLoadFailed.value = false
        _lastLoadFailed.value = false
        _tasteMatchesGate.value = null
        _tasteMatchesTrial.value = null
        _isRefreshing.value = true
        _hasMore.value = true
        appliedFeedSignature = to
        lastLoadedMode = to.substringBefore('|')
        loadFeed(refresh = true)
    }

    private fun stashFeedPage(signature: String) {
        if (signature.isEmpty()) return
        // Only snapshot a page we actually applied. Otherwise the first
        // filter/mode change (before loadFeed) would cache an empty ALL
        // page, and switching back would restore that blank snapshot
        // instead of fetching.
        if (appliedFeedSignature != signature) return
        // Don't snapshot a mid-flight first load — coming back should retry,
        // not restore a blank "loaded" page.
        if ((_isLoading.value || _isRefreshing.value) &&
            _posts.value.isEmpty() &&
            _tasteMatchesGate.value == null &&
            !_lastLoadFailed.value
        ) return
        feedPageCache[signature] = FeedModePageSnapshot(
            posts = _posts.value,
            hasMore = _hasMore.value,
            forYouSessionToken = forYouSessionToken,
            forYouPageIndex = forYouPageIndex,
            forYouLoadFailed = _forYouLoadFailed.value,
            lastLoadFailed = _lastLoadFailed.value,
            lastTimestamp = lastTimestamp,
            tasteMatchesGate = _tasteMatchesGate.value,
            tasteMatchesTrial = _tasteMatchesTrial.value,
            cachedAt = System.currentTimeMillis(),
        )
        _pageCacheRevision.value += 1
    }

    private fun restoreFeedPage(signature: String): Boolean {
        val snap = feedPageCache[signature] ?: return false
        if (System.currentTimeMillis() - snap.cachedAt >= FEED_PAGE_CACHE_TTL_MS) {
            feedPageCache.remove(signature)
            return false
        }
        _posts.value = snap.posts
        _hasMore.value = snap.hasMore
        forYouSessionToken = snap.forYouSessionToken
        forYouPageIndex = snap.forYouPageIndex
        _forYouLoadFailed.value = snap.forYouLoadFailed
        _lastLoadFailed.value = snap.lastLoadFailed
        lastTimestamp = snap.lastTimestamp
        _tasteMatchesGate.value = snap.tasteMatchesGate
        _tasteMatchesTrial.value = snap.tasteMatchesTrial
        _isLoading.value = false
        _isRefreshing.value = false
        _hasLoaded.value = true
        _pageCacheRevision.value += 1
        return true
    }

    /**
     * Neighbor tabs always read their snapshot. The selected tab uses live
     * posts once those posts belong to it; until restore runs, keep showing
     * the snapshot the user was already dragging.
     */
    fun postsForTabPage(mode: String): List<CymbalPost> {
        val cached = feedPageCache[feedRequestSignature(mode)]?.posts ?: emptyList()
        if (mode != feedMode.value) return cached
        if (cached.isEmpty()) return _posts.value
        if (_posts.value.any { live -> cached.any { it.id == live.id } }) return _posts.value
        return cached
    }

    /** Fires `taste_matches_feed_viewed` once per session the first time the
     *  SERVED (non-gated) Taste Matches feed loads with posts. */
    private var loggedTasteMatchesFeedViewed = false

    private var forYouSessionToken: String? = null
    private var forYouPageIndex: Int = 0
    private var forYouSeenIds: MutableList<String> = mutableListOf()
    private val _forYouLoadFailed = MutableStateFlow(false)
    val forYouLoadFailed: StateFlow<Boolean> = _forYouLoadFailed.asStateFlow()

    override val engagementStates = engagementManager.states
    val currentUserProfile = authRepository.userProfile

    /**
     * One-time tap-to-play hint on the first eligible track's album art. Mirrors
     * iOS AlbumArtView showsTapHint. Only newly-signed-up accounts see it, and
     * only until they tap any album art once.
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

    /**
     * False until the user has confirmed the first feed-playlist generation.
     * The playlist button leaves Corus to open the music service, so the first
     * tap shows an explainer + confirm; afterwards taps run directly.
     */
    val hasConfirmedFeedPlaylist: StateFlow<Boolean> = preferencesDataStore.hasConfirmedFeedPlaylist
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    val playFullSongs: StateFlow<Boolean> = preferencesDataStore.playFullSongs
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    fun markFeedPlaylistConfirmed() {
        viewModelScope.launch { preferencesDataStore.setHasConfirmedFeedPlaylist() }
    }

    fun shouldPaywallFeedPlaylist(): Boolean =
        subscriptionRepository.shouldPaywallPlaylist(PlaylistTrialField.Feed)

    val hasFullAccess = subscriptionRepository.hasFullAccessFlow

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

    /** All user ids the viewer follows (remote following set + optimistic local
     *  follows). Powers the inline Trending follow pill, which checks membership
     *  synchronously so the pill is correct on first composition — no per-post
     *  read. (Same underlying set as [followedBotIds]; aliased for clarity.) */
    val followingUserIds: StateFlow<Set<String>> = followedBotIds

    /** True once the viewer's following set has been seeded at least once (from
     *  the persisted cache or network). The inline Trending pill stays hidden
     *  until this flips so it never flashes "Follow" for an author the viewer
     *  may already follow while membership is still unknown on a cold start. */
    val followingLoaded: StateFlow<Boolean> = userRepository.followingLoaded

    init {
        // Restore the persisted feed filter on startup so a music/film/new-releases
        // selection survives an app restart (mirrors iOS @AppStorage("feedFilter")).
        viewModelScope.launch {
            val saved = runCatching {
                FeedFilter.valueOf(preferencesDataStore.feedFilter.first())
            }.getOrDefault(FeedFilter.ALL)
            val savedDecade = FeedDecade.fromStored(
                runCatching { preferencesDataStore.feedDecade.first() }.getOrNull()
            )
            val oldSig = feedRequestSignature()
            val changed = saved != _feedFilter.value || savedDecade != _feedDecade.value
            _feedFilter.value = saved
            _feedDecade.value = savedDecade
            if (changed) {
                // If the screen already kicked off a default (ALL) load before the
                // async restore landed, redo it so the page matches the restored
                // filter. When the restore wins the race, the screen's initial
                // load simply reads the already-correct filter and no refetch runs.
                if (_hasLoaded.value || _isLoading.value || _isRefreshing.value) {
                    applyFeedSignatureChange(from = oldSig, to = feedRequestSignature())
                }
            }
        }
        // Re-sync the feed when the resolved mode lands after the screen has
        // already kicked off a load with a different mode. feedMode resolves
        // from DataStore asynchronously, so a cold launch can fetch Following
        // (the eager seed) before the persisted ranked mode arrives — the menu
        // then shows e.g. Trending as selected while the posts are Following.
        // When the resolved mode diverges from what the current page loaded
        // with, redo the load so data matches the selection. Mirrors the
        // feedFilter restore guard above. setFeedMode pre-claims lastLoadedMode,
        // so user taps don't double-load through here.
        viewModelScope.launch {
            feedMode.collect { mode ->
                if (mode != lastLoadedMode &&
                    (_hasLoaded.value || _isLoading.value || _isRefreshing.value)
                ) {
                    val oldSig = lastLoadedMode?.let { feedRequestSignature(it) } ?: ""
                    applyFeedSignatureChange(from = oldSig, to = feedRequestSignature(mode))
                }
            }
        }
        // iOS keeps the Favorites tab once unlocked — a later 0 must not
        // kick the user off the tab.
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
        // First follow (onboarding or later) unblocks the feed-switch hint for
        // users who were auto-landed on Trending with an empty following set.
        viewModelScope.launch {
            followingUserIds.collect { ids ->
                if (ids.isNotEmpty()) feedSwitchHintManager.noteFollowedSomeone()
            }
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
        // Auto-refresh feed when a new post is created, but only in the
        // chronological Following feed where the new post belongs at the top.
        // Trending is ranked (the post hasn't earned a slot) and Favorites
        // can't contain your own post, so refetching them on every post is
        // wasted work and would misleadingly jump the post to the top.
        viewModelScope.launch {
            postCreationEvent.events.collect {
                if (feedMode.value == "following") {
                    delay(500) // brief delay for Firestore propagation
                    loadFeed(refresh = true)
                } else if (feedMode.value == "tasteMatches") {
                    // Cold-start: fill the next slot optimistically (self-guards
                    // on the NeedMorePosts gate).
                    handleTasteMatchesSeedPost()
                    // "No matches yet": the fresh post can share an artist/director
                    // with someone and flip the gate to a served feed. That overlap
                    // is rebuilt by a backend trigger (~1-5s), so an immediate
                    // refetch still reads "no matches" — poll until it serves
                    // instead of stranding the user on the empty state until they
                    // pull-to-refresh (they'd think they still have no matches).
                    if (_tasteMatchesGate.value is TasteMatchesGate.NoMatchesYet) {
                        beginTasteMatchesRematchAfterPost()
                    }
                }
            }
        }
        viewModelScope.launch {
            postDeletionEvent.events.collect { deletedId ->
                _posts.value = _posts.value.filter { it.id != deletedId }
                // On the Taste Matches cold-start a delete changes your post count
                // — reconcile with the server so the meter and slot art drop the
                // removed post instead of showing stale optimistic state.
                if (feedMode.value == "tasteMatches") {
                    delay(500) // let the count trigger propagate
                    loadFeed(refresh = true)
                }
            }
        }
        // Keep the Favorites feed live when the viewer toggles a favorite from a
        // profile, so they don't have to pull-to-refresh. Only acts while the
        // Favorites feed is the active mode — switching INTO it already reloads.
        viewModelScope.launch {
            favoriteChangedEvent.events.collect { change ->
                if (feedMode.value != "favorites") return@collect
                if (change.isFavorited) {
                    // Newly favorited — fetch so their posts appear. Brief delay
                    // lets the favorites doc write propagate before the query.
                    delay(300)
                    loadFeed(refresh = true)
                } else {
                    // Unfavorited — drop that author's posts instantly, no network.
                    _posts.value = _posts.value.filter { it.user.id != change.userId }
                }
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

    private suspend fun loadFeedSuspending(refresh: Boolean, attempt: Int = 0) {
        val userId = authRepository.currentUserId ?: return

        if (refresh) {
            _isRefreshing.value = true
            lastTimestamp = null
        } else {
            if (_isLoading.value) return
            _isLoading.value = true
        }

        // Ranked feed covers Trending (pool = whole app) and Taste Matches
        // (premium curator pool). Both hit the same callable with a `scope`
        // arg. Keyed off the resolved/persisted mode, NOT the Remote Config
        // flags: on a cold launch the flags can briefly read false before RC
        // fetches, which used to load Recent instead of the user's chosen
        // ranked feed. A ranked mode can only have been persisted while its
        // flag was on.
        val mode = feedMode.value
        lastLoadedMode = mode
        val useRanked = mode == "trending" || mode == "tasteMatches"
        val useFavorites = mode == "favorites"
        val rankedScope = when (mode) {
            "trending" -> "trending"
            "tasteMatches" -> "tasteMatches"
            else -> "trending"
        }
        // A fresh (user-driven) load resets the optimistic seed slots so the
        // count reflects pure server truth. During the post-3rd hand-off
        // (`_tasteMatchesSeeding`) we keep them so the meter never flickers back
        // while the server count catches up. Mirrors iOS.
        if (!_tasteMatchesSeeding.value) {
            _tasteMatchesSeedCount.value = 0
            _tasteMatchesSeedArt.value = emptyList()
        }
        // Clear any stale gate/trial from another mode; the ranked branch re-sets them.
        if (mode != "tasteMatches") {
            _tasteMatchesGate.value = null
            _tasteMatchesTrial.value = null
        }

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
                    // Pull-to-refresh (refresh=true) → boost recency so the
                    // newest posts lead. First load / pagination doesn't.
                    isRefresh = refresh,
                    releaseDecade = decadeApplicableTo(mode, _feedDecade.value),
                )
                // Superseded-mode guard for the RANKED branch. Every write below
                // (gate, session token, page index, seen-IDs) mutates state that
                // is SHARED across the ranked modes. If the user switched away
                // while this fetch was in flight, applying them corrupts the
                // now-active mode: a stale Taste Matches response would write its
                // session token here, and the next Trending page would paginate
                // with that Taste Matches cursor and splice its posts in. The
                // posts-level guard further down is too late for these writes, so
                // bail here before touching any shared ranked state. Mirrors iOS.
                if (feedMode.value != mode) return
                // Taste Matches gate: a {gated:...} response carries no posts and
                // drives the cold-start / paywall screen instead of the feed.
                if (mode == "tasteMatches") {
                    when (forYouPage.gated) {
                        "needMorePosts" -> {
                            _tasteMatchesGate.value = TasteMatchesGate.NeedMorePosts(
                                forYouPage.gatedPostCount, forYouPage.gatedThreshold,
                            )
                            // Seed the leftmost slots with the caller's existing covers.
                            loadTasteMatchesSeedArt(forYouPage.gatedPostCount, forYouPage.gatedThreshold)
                        }
                        "paywall" -> _tasteMatchesGate.value = TasteMatchesGate.Paywall
                        "unavailable" -> _tasteMatchesGate.value = TasteMatchesGate.Unavailable
                        "noMatchesYet" -> _tasteMatchesGate.value = TasteMatchesGate.NoMatchesYet
                        else -> _tasteMatchesGate.value = null
                    }
                    if (forYouPage.gated != null) {
                        // Gated → no feed. Reset paging state, surface empty, bail.
                        if (feedMode.value != mode) return
                        _posts.value = emptyList()
                        _hasMore.value = false
                        _tasteMatchesTrial.value = null
                        _lastLoadFailed.value = false
                        _forYouLoadFailed.value = false
                        _isLoading.value = false
                        _isRefreshing.value = false
                        _hasLoaded.value = true
                        return
                    }
                    // Un-gated (served) response: mirror the free-trial banner
                    // state so the feed can render it. Null for full-access
                    // viewers, RC off, or once the trial expired (that path
                    // returns gated:"paywall" above instead).
                    _tasteMatchesTrial.value = forYouPage.trial
                }
                if (forYouPage.sessionToken != (forYouSessionToken ?: "") && forYouPage.sessionToken.isNotEmpty()) {
                    forYouSessionToken = forYouPage.sessionToken
                    forYouPageIndex = 0
                } else {
                    forYouPageIndex = nextIndex
                }
                newPosts = forYouPage.posts
                pageHasMore = forYouPage.hasMore
                // The served Taste Matches feed loaded (gated == null guaranteed —
                // the gated branch returned above) with posts. Log once per session.
                if (mode == "tasteMatches" && !loggedTasteMatchesFeedViewed && newPosts.isNotEmpty()) {
                    loggedTasteMatchesFeedViewed = true
                    analyticsService.logTasteMatchesFeedViewed(newPosts.size)
                }
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
            } else if (useFavorites) {
                // Chronological feed limited to favorited users. Paged by
                // lastTimestamp like Following, but via getFavoritesFeedPage.
                val page = postRepository.getFavoritesFeedPage(
                    userId = userId,
                    pageSize = 7,
                    lastTimestamp = if (refresh) null else lastTimestamp,
                    mediaType = _feedFilter.value.mediaType,
                    newReleasesOnly = _feedFilter.value.newReleasesOnly,
                )
                newPosts = page.posts
                pageHasMore = page.hasMore
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
                    saveCount = post.saveCount,
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

            // Drop a stale response: the user switched feed modes while this
            // fetch was in flight. A newer loadFeed for the current mode owns
            // the UI now — applying these posts would flash the wrong feed
            // (e.g. show Following posts while Trending is selected). The newer
            // task resets the loading flags on its own completion.
            if (feedMode.value != mode) return

            if (refresh) {
                _posts.value = newPosts
            } else {
                _posts.value = (_posts.value + newPosts).distinctBy { it.id }
            }

            _hasMore.value = pageHasMore
            if (newPosts.isNotEmpty() && !useRanked) {
                lastTimestamp = newPosts.last().timestamp.time
            }

            // NO per-post real-time listeners on the feed (matching iOS). Counts
            // render from the denormalized values seeded via initState() above;
            // live per-post Firestore listeners are attached ONLY in PostDetail /
            // Comments. Feed-wide per-post listeners were the dominant source of
            // /posts reads (~30 open listeners per feed page, re-attached on
            // scroll). Optimistic like/save/comment still work (they mutate the
            // engagement store directly), and an FCM notification about a post
            // triggers a one-shot refreshCountsFromServer for that post.

            // Check actual like status from Firestore (backend doesn't return isLiked)
            engagementManager.checkLikeStatuses(newPosts.map { it.id }, userId)
            engagementManager.checkSaveStatuses(newPosts.map { it.id }, userId)
            _lastLoadFailed.value = false
            appliedFeedSignature = feedRequestSignature(mode)
        } catch (e: CancellationException) {
            // Coroutine cancellation is NOT a load failure. Swallowing it here
            // would paint a normal cancellation (the ViewModel scope tearing
            // down, a superseding load) as "couldn't connect" — the same
            // false-error class of bug we fixed in search. Always rethrow.
            throw e
        } catch (_: Exception) {
            // A transient cold-start hiccup (App Check / Play Integrity token
            // still minting, a Functions UNAVAILABLE/INTERNAL, a dropped first
            // request) lands here even on strong wifi — exactly the blip a manual
            // "Retry" clears. When the screen has nothing to show, retry
            // automatically before surfacing any error, so a momentary server blip
            // never masquerades as "you're offline." A single retry didn't cover a
            // real cold start (process killed during a long sleep), so retry
            // several times with escalating backoff; the device was online the
            // whole time, so these retries are the only automatic recovery (the
            // reconnect handler needs a connectivity transition that never comes).
            // The first couple retries fire even if isConnected reads false, since
            // the radio may not have re-associated yet on a fresh wake — trusting
            // that stale-false reading is what flashed the empty error panel. The
            // loading flag stays set through the backoff so the skeleton holds
            // instead of flashing an error.
            val withinConnectivityGrace = attempt < FEED_TRANSIENT_CONNECTIVITY_GRACE_RETRIES
            if (attempt < FEED_TRANSIENT_MAX_RETRIES &&
                (isConnected.value || withinConnectivityGrace) &&
                _posts.value.isEmpty()
            ) {
                delay(FEED_TRANSIENT_RETRY_BACKOFF_MS * (attempt + 1))
                loadFeedSuspending(refresh = true, attempt = attempt + 1)
                return
            }
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
        val oldSig = feedRequestSignature()
        _feedFilter.value = filter
        // Persist so the selection survives an app restart (mirrors iOS).
        viewModelScope.launch { preferencesDataStore.setFeedFilter(filter.name) }
        if (filter.newReleasesOnly && _feedDecade.value != null) {
            analyticsService.logFeedDecadeChanged(FeedDecade.analyticsValue(null))
            storeFeedDecade(null)
        }
        applyFeedSignatureChange(from = oldSig, to = feedRequestSignature())
    }

    fun setFeedDecade(decade: Int?) {
        val next = FeedDecade.normalize(decade)
        if (next == _feedDecade.value) return
        analyticsService.logFeedDecadeChanged(FeedDecade.analyticsValue(next))
        val oldSig = feedRequestSignature()
        storeFeedDecade(next)
        val downgraded = when {
            next == null -> null
            _feedFilter.value == FeedFilter.MUSIC_NEW_RELEASES -> FeedFilter.MUSIC
            _feedFilter.value == FeedFilter.FILM_NEW_RELEASES -> FeedFilter.FILM
            else -> null
        }
        if (downgraded != null) {
            _feedFilter.value = downgraded
            viewModelScope.launch { preferencesDataStore.setFeedFilter(downgraded.name) }
        }
        applyFeedSignatureChange(from = oldSig, to = feedRequestSignature())
    }

    private fun storeFeedDecade(decade: Int?) {
        _feedDecade.value = decade
        viewModelScope.launch { preferencesDataStore.setFeedDecade(FeedDecade.toStored(decade)) }
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
     * Switch the active feed mode ("following" / "trending" / "favorites" /
     * "tasteMatches"). Persists to DataStore and resets the ranked-feed session
     * state, then refetches. No-op when the requested mode matches the current
     * value.
     */
    fun setFeedMode(mode: String) {
        // Selecting a mode means the user found the switcher — retire the hint
        // (silently; the open already logged feed_switcher_opened). Before the
        // no-op guard so re-selecting the current mode still retires it.
        feedSwitchHintManager.noteSwitcherUsed()
        if (feedMode.value == mode) return
        // Premium gate: a non-member (and non-tester) tapping Taste Matches gets
        // the paywall, not the feed — UNLESS the free-trial RC is on, in which
        // case they enter the mode (server enforces the trial-expiry backstop
        // via the gated:"paywall" response). Mirrors iOS.
        if (mode == "tasteMatches") {
            val hasAccess = subscriptionRepository.hasFullAccess || remoteConfig.tasteMatchesTester
            val freeTrial = remoteConfig.tasteMatchesFreeTrial
            // Log the tap before the gate so the funnel captures every access tier.
            analyticsService.logTasteMatchesSelected(hasAccess || freeTrial)
            if (!hasAccess && !freeTrial) {
                _tasteMatchesPaywall.value = PaywallSource.TASTE_MATCHES
                return
            }
        }
        // Claim this mode now so the feedMode collector (init) treats the
        // upcoming DataStore emission as already-handled — this path persists
        // and applies the cache (or cold-loads) directly, so we don't want the
        // collector to fire a second redundant load.
        val oldSig = feedRequestSignature()
        lastLoadedMode = mode
        analyticsService.logFeedModeChanged(mode)
        viewModelScope.launch { preferencesDataStore.setFeedMode(mode) }
        applyFeedSignatureChange(from = oldSig, to = feedRequestSignature(mode))
    }

    /**
     * A Club purchase completed from a Taste Matches paywall (mode-switcher tap
     * or the in-feed gated backstop). Complete the original intent: the user
     * tapped the premium mode and paid, so enter the feed without a second tap.
     * If they were already on the mode (the backstop case), reload it instead —
     * the gated page they're looking at was fetched as a non-member.
     * [setFeedMode]'s premium gate passes because the repository flips
     * hasFullAccess before reporting purchase success.
     */
    fun onTasteMatchesUnlocked() {
        if (feedMode.value == "tasteMatches") {
            loadFeed(refresh = true)
        } else {
            setFeedMode("tasteMatches")
        }
    }

    // ── Taste Matches cold-start seeding ──

    /** Fetch the caller's already-counted post covers (songs AND films) so the
     *  leftmost seed slots show real art on first load. No-op if there's nothing
     *  to count or we already have art. */
    private fun loadTasteMatchesSeedArt(base: Int, threshold: Int) {
        if (base <= 0 || _tasteMatchesSeedArt.value.isNotEmpty()) return
        refreshTasteMatchesSeedArt(threshold)
    }

    private fun refreshTasteMatchesSeedArt(limit: Int) {
        val uid = authRepository.currentUserId ?: return
        viewModelScope.launch {
            runCatching {
                postRepository.getProfilePosts(uid, uid, limit = limit.coerceAtLeast(1))
            }.onSuccess { posts ->
                // getProfilePosts is newest-first; the seed slots fill left→right in
                // the order you posted (your first post anchors slot 0 and stays
                // there), so flip to oldest-first. Large art so the 72dp slots render
                // sharp, not a blurry thumbnail.
                val urls = posts.mapNotNull { it.displayImageLargeURL ?: it.displayImageURL }
                    .take(limit)
                    .reversed()
                if (urls.isNotEmpty()) _tasteMatchesSeedArt.value = urls
            }
        }
    }

    /** A post arrived while on the cold-start: fill the next slot immediately and,
     *  if it completes the meter, hand off to the real feed. Mirrors iOS. */
    private fun handleTasteMatchesSeedPost() {
        val gate = _tasteMatchesGate.value as? TasteMatchesGate.NeedMorePosts ?: return
        val filledBefore = minOf(gate.threshold, gate.postCount + _tasteMatchesSeedCount.value)
        if (filledBefore >= gate.threshold) return
        _tasteMatchesSeedCount.value = _tasteMatchesSeedCount.value + 1
        // Pull the just-posted cover into the slots (after a beat for propagation).
        viewModelScope.launch {
            delay(600)
            refreshTasteMatchesSeedArt(gate.threshold)
        }
        val filledAfter = minOf(gate.threshold, gate.postCount + _tasteMatchesSeedCount.value)
        if (filledAfter >= gate.threshold) beginTasteMatchesSeedHandoff()
    }

    /** After the final seed post: celebrate briefly, then poll the ranked feed
     *  until the server count catches up and serves it (keeping the skeleton up
     *  across gated retries so the meter never flickers back). Mirrors iOS. */
    private fun beginTasteMatchesSeedHandoff() {
        _tasteMatchesSeeding.value = true
        viewModelScope.launch {
            delay(700) // celebrate the last slot
            if (feedMode.value != "tasteMatches") { _tasteMatchesSeeding.value = false; return@launch }
            _tasteMatchesGate.value = null
            for (attempt in 0 until 5) {
                loadFeedSuspending(refresh = true)
                if (feedMode.value != "tasteMatches") break
                if (_tasteMatchesGate.value == null) break   // served — feed is live
                if (attempt < 4) delay(1200)
            }
            // Either way, drop the optimistic seed so the cold-start (if the
            // server is still catching up) reflects the real server count rather
            // than a stale over-count — e.g. after deleting a seed post.
            _tasteMatchesSeedCount.value = 0
            _tasteMatchesSeedArt.value = emptyList()
            // If we fell back to the cold-start, refetch sharp covers for the
            // reconciled (real) post count.
            (_tasteMatchesGate.value as? TasteMatchesGate.NeedMorePosts)?.let {
                refreshTasteMatchesSeedArt(it.threshold)
            }
            _tasteMatchesSeeding.value = false
        }
    }

    /** A post arrived while resting on the "no matches yet" empty state. The new
     *  post can share an artist/director with someone and flip the gate to a
     *  served feed, but that overlap is recomputed from a backend trigger that
     *  rebuilds the taste index (~1-5s), so an immediate refetch would still read
     *  "no matches." Hold the loading skeleton (reusing `_tasteMatchesSeeding`,
     *  which also survives the gated reloads) and poll the ranked feed until it
     *  serves — or we exhaust retries and settle back on the empty state. Mirrors
     *  the cold-start hand-off and iOS. */
    private fun beginTasteMatchesRematchAfterPost() {
        if (_tasteMatchesSeeding.value) return // a hand-off / poll is already running
        _tasteMatchesSeeding.value = true
        viewModelScope.launch {
            for (attempt in 0 until 5) {
                delay(if (attempt == 0) 600 else 1200) // let the taste-index trigger propagate
                if (feedMode.value != "tasteMatches") break
                if (_tasteMatchesGate.value !is TasteMatchesGate.NoMatchesYet) break
                loadFeedSuspending(refresh = true)
                if (feedMode.value != "tasteMatches") break
                if (_tasteMatchesGate.value == null) break // served — feed is live
            }
            _tasteMatchesSeeding.value = false
        }
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
        val trackPosts = filteredPosts.value.filter { it.mediaType == MediaType.TRACK }
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
                        loadMore = { loadFeedSuspending(refresh = false) },
                    )
                } else {
                    nowPlayingManager.play(track = track, queue = listOf(track))
                }
            },
            scope = viewModelScope,
        )
    }

    fun generateFeedPlaylist() {
        analyticsService.logFeedPlaylistTapped()
        // Build the playlist from whichever feed is on screen, and (for the
        // ranked modes — Trending AND Taste Matches) from the exact ranked
        // session the user is scrolling. The server names it per mode
        // ("Corus Trending" / "Corus Taste Matches" / "Corus Favorites" / "Corus Feed").
        val mode = feedMode.value
        viewModelScope.launch {
            nowPlayingManager.generateFeedPlaylist(
                newReleasesOnly = _feedFilter.value.newReleasesOnly,
                feedMode = mode,
                sessionToken = if (mode == "trending" || mode == "tasteMatches") forYouSessionToken else null,
            )
        }
    }

    fun toggleLike(postId: String) {
        val userId = authRepository.currentUserId ?: return
        engagementManager.toggleLike(postId, userId)
    }

    override fun toggleSave(postId: String) {
        val userId = authRepository.currentUserId ?: return
        engagementManager.toggleSave(postId, userId)
    }

    // ── Share contacts & search ──

    override fun loadRecentShareContacts() {
        val userId = authRepository.currentUserId ?: return
        _isLoadingShareContacts.value = true
        viewModelScope.launch {
            try {
                // Rank by people you actually share with, then fill gaps from recent
                // DM threads, then fall back to following + followers. Keeps the grid
                // full for new users while surfacing real share intent for everyone.
                val shareRecipients = runCatching { messageRepository.listShareRecipients(12) }.getOrDefault(emptyList())
                val threadContacts = messageRepository.listThreads(userId).mapNotNull { it.otherUser }

                // Only pay for the follow-graph fetch when shares + DMs don't fill the grid.
                val followFallback = if (rankShareContacts(shareRecipients, threadContacts, emptyList()).size < SHARE_CONTACTS_TARGET) {
                    val following = userRepository.fetchFollowingPaginated(userId, limit = 20).users
                    val followers = userRepository.fetchFollowersPaginated(userId, limit = 20).users
                    following + followers
                } else {
                    emptyList()
                }

                _recentShareContacts.value = rankShareContacts(shareRecipients, threadContacts, followFallback)
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

    /** Follow a post's author from the inline Trending pill. The pill animates
     *  its own optimistic confirm; here we run the network follow and, on
     *  success, `userRepository.followUser` adds the author to the shared
     *  following set (which feeds [followingUserIds]) so the menu state stays in
     *  sync. A failure simply leaves the viewer not-following — the pill will
     *  reappear on the next feed load. */
    fun followAuthor(targetUserId: String) {
        val uid = authRepository.currentUserId ?: return
        viewModelScope.launch {
            try {
                userRepository.followUser(uid, targetUserId)
                analyticsService.logFollowUser(targetUserId)
            } catch (_: Exception) {
            }
        }
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

}

/** Target number of contacts before we stop reaching for fallback sources. */
const val SHARE_CONTACTS_TARGET = 12

/** Max contacts shown in the share sheet grid. */
const val SHARE_CONTACTS_CAP = 20

/**
 * Merge the share-sheet contact sources in priority order — people you deliberately
 * share with first, then recent DM partners, then the follow-graph fallback — keeping
 * the first occurrence of each user (preserving rank) and capping the result.
 */
fun rankShareContacts(
    shareRecipients: List<CymbalUser>,
    threadContacts: List<CymbalUser>,
    followFallback: List<CymbalUser>,
    cap: Int = SHARE_CONTACTS_CAP,
): List<CymbalUser> {
    val seen = mutableSetOf<String>()
    val combined = mutableListOf<CymbalUser>()
    for (user in shareRecipients + threadContacts + followFallback) {
        if (user.id.isNotEmpty() && seen.add(user.id)) combined.add(user)
    }
    return combined.take(cap)
}
