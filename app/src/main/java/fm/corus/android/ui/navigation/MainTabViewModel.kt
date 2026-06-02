package fm.corus.android.ui.navigation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import fm.corus.android.data.local.PreferencesDataStore
import fm.corus.android.data.model.CymbalPost
import fm.corus.android.data.model.CymbalTrack
import fm.corus.android.data.repository.AuthRepository
import fm.corus.android.data.repository.SubscriptionRepository
import fm.corus.android.data.repository.UnreadCountsRepository
import fm.corus.android.domain.NowPlayingManager
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class MilestonePaywallSource {
    FIRST_POST,
    TENTH_POST,
}

@HiltViewModel
class MainTabViewModel @Inject constructor(
    val nowPlayingManager: NowPlayingManager,
    private val cloudFunctions: fm.corus.android.data.remote.CloudFunctionsDataSource,
    val musicServicePreference: fm.corus.android.domain.MusicServicePreference,
    val subscriptionRepository: SubscriptionRepository,
    private val preferencesDataStore: PreferencesDataStore,
    private val unreadCountsRepository: UnreadCountsRepository,
    val postEngagementManager: fm.corus.android.domain.PostEngagementManager,
    private val authRepository: AuthRepository,
    private val analyticsService: fm.corus.android.service.AnalyticsService,
    val feedScrollRouter: fm.corus.android.domain.FeedScrollRouter,
) : ViewModel() {

    /**
     * Toggle the like state of the post that originated the currently-playing
     * track, mirroring the iOS mini-player heart button. No-ops if the current
     * track has no source post (e.g. a queued track surfaced outside the feed)
     * or if the user is signed out.
     */
    /**
     * Resolve the link-out URL for the currently-playing Spotify-source track
     * given the viewer's preferred service (Apple Music / TIDAL / Deezer), for the
     * mini-player service-logo tap. Returns null for Spotify / no current track.
     */
    suspend fun resolveCurrentServiceLinkUrl(): String? {
        val s = nowPlayingManager.state.value
        val trackId = s.trackId ?: return null
        return fm.corus.android.domain.MusicServiceLinkOut.resolveLinkOutUrlRaw(
            trackId = trackId,
            name = s.trackName,
            artist = s.artistName,
            isrc = null,
            service = musicServicePreference.current.value,
            cloud = cloudFunctions,
        )
    }

    fun toggleLikeForCurrentTrack() {
        val postId = nowPlayingManager.state.value.sourcePostId ?: return
        val userId = authRepository.currentUserId ?: return
        postEngagementManager.toggleLike(postId, userId)
    }

    fun logPaywallShown(source: String) = analyticsService.logPaywallShown(source)
    fun logSaveWarningTapped(remaining: Int) = analyticsService.logSaveWarningToastTapped(remaining)

    val notificationCount: StateFlow<Int> = unreadCountsRepository.notificationCount
    val unreadMessageCount: StateFlow<Int> = unreadCountsRepository.unreadMessageCount

    /**
     * Called when the user taps the Activity tab. Optimistically zeros the
     * notification count so the badge doesn't re-appear when leaving to
     * another tab if the Firestore markAllRead write is slow or fails.
     * Mirrors iOS MainTabView behaviour.
     */
    fun onActivityTabEntered() {
        unreadCountsRepository.clearNotificationCount()
    }

    /** True once we've shown the Android push-permission prompt (onboarding or
     * MainTab fallback). Used to avoid re-prompting users who already saw it. */
    val hasRequestedPushPermission: StateFlow<Boolean> = preferencesDataStore
        .hasRequestedPushPermission
        .stateIn(viewModelScope, SharingStarted.Eagerly, true)

    fun markPushPermissionRequested() {
        viewModelScope.launch {
            preferencesDataStore.setHasRequestedPushPermission()
        }
    }

    // Pre-selected media for compose-with-preselection flow.
    // When non-null, MainTabScreen opens ComposeScreen with this track/movie pre-selected.
    private val _preSelectedTrackId = MutableStateFlow<String?>(null)
    val preSelectedTrackId: StateFlow<String?> = _preSelectedTrackId.asStateFlow()

    // Full-track preselect — used when the caller already has a complete CymbalTrack
    // (e.g. SoundCloud tracks, where a Spotify-by-id lookup would lose source info).
    private val _preSelectedTrack = MutableStateFlow<CymbalTrack?>(null)
    val preSelectedTrack: StateFlow<CymbalTrack?> = _preSelectedTrack.asStateFlow()

    private val _preSelectedMovieId = MutableStateFlow<String?>(null)
    val preSelectedMovieId: StateFlow<String?> = _preSelectedMovieId.asStateFlow()

    fun setPreSelectedTrackId(trackId: String?) {
        _preSelectedTrackId.value = trackId
    }

    fun setPreSelectedTrack(track: CymbalTrack?) {
        _preSelectedTrack.value = track
    }

    fun setPreSelectedMovieId(movieId: String?) {
        _preSelectedMovieId.value = movieId
    }

    // Repost context — when non-null, MainTabScreen opens ComposeScreen with this
    // post's media pre-selected and the attribution toggle visible.
    private val _repostOriginalPost = MutableStateFlow<CymbalPost?>(null)
    val repostOriginalPost: StateFlow<CymbalPost?> = _repostOriginalPost.asStateFlow()

    fun setRepostOriginalPost(post: CymbalPost?) {
        _repostOriginalPost.value = post
    }

    /** Called after compose overlay closes to clear any pre-selection. */
    fun clearPreSelectedMedia() {
        _preSelectedTrackId.value = null
        _preSelectedTrack.value = null
        _preSelectedMovieId.value = null
        _repostOriginalPost.value = null
    }

    private val _showMilestonePaywall = MutableStateFlow(false)
    val showMilestonePaywall: StateFlow<Boolean> = _showMilestonePaywall.asStateFlow()

    private val _milestonePaywallSource = MutableStateFlow<MilestonePaywallSource?>(null)
    val milestonePaywallSource: StateFlow<MilestonePaywallSource?> = _milestonePaywallSource.asStateFlow()

    fun dismissMilestonePaywall() {
        _showMilestonePaywall.value = false
        _milestonePaywallSource.value = null
    }

    /**
     * Called after a post is successfully created.
     * Checks if we should show a milestone paywall (1st post or 10th post).
     */
    fun checkPostMilestonePaywall() {
        if (subscriptionRepository.hasFullAccess) return

        viewModelScope.launch {
            val todayCount = subscriptionRepository.recentPostCount.value
            val totalCount = subscriptionRepository.totalPostCount.value

            // 1st post today — show first-post paywall if not seen
            if (todayCount == 1 && !preferencesDataStore.hasSeenFirstPostPaywall.first()) {
                preferencesDataStore.setHasSeenFirstPostPaywall()
                _milestonePaywallSource.value = MilestonePaywallSource.FIRST_POST
                _showMilestonePaywall.value = true
                return@launch
            }

            // 10th total post — show tenth-post paywall if not seen
            if (totalCount >= 10 && !preferencesDataStore.hasSeenTenthPostPaywall.first()) {
                preferencesDataStore.setHasSeenTenthPostPaywall()
                _milestonePaywallSource.value = MilestonePaywallSource.TENTH_POST
                _showMilestonePaywall.value = true
                return@launch
            }
        }
    }
}
