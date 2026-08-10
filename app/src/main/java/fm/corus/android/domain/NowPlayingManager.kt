package fm.corus.android.domain

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.app.KeyguardManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import androidx.annotation.VisibleForTesting
import androidx.core.content.ContextCompat
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import dagger.hilt.android.qualifiers.ApplicationContext
import fm.corus.android.data.local.PreferencesDataStore
import fm.corus.android.data.model.CymbalPost
import fm.corus.android.data.model.CymbalTrack
import fm.corus.android.data.model.MusicService
import fm.corus.android.data.model.TrackSource
import fm.corus.android.data.remote.CloudFunctionsDataSource
import fm.corus.android.data.remote.TidalPlaylistService
import fm.corus.android.data.repository.SpotifyRepository
import fm.corus.android.data.repository.SubscriptionRepository
import fm.corus.android.MainActivity
import fm.corus.android.data.repository.UserRepository
import fm.corus.android.service.CorusPlaybackService
import fm.corus.android.service.RemoteConfigService
import fm.corus.android.ui.components.ToastManager
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ProcessLifecycleOwner
import fm.corus.android.ui.components.VoiceNotePlayerManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The artist or album destination page a catalog track was played from. Rides
 * on the individual [QueuedTrack] so the currently-playing track — including
 * auto-advanced ones, which [advanceToNext] plays straight from the queue —
 * remembers its origin. The mini-player reads it to return to that page and
 * scroll to the song instead of opening the generic song-detail page. Carries
 * display hints so the return navigation paints the header instantly. Null for
 * feed / search / single-track playback (which keep the song-detail behavior).
 */
sealed interface CatalogPlaybackOrigin {
    data class Artist(val id: String, val name: String?, val imageUrl: String?) : CatalogPlaybackOrigin
    data class Album(
        val id: String,
        val title: String?,
        val artist: String?,
        val coverUrl: String?,
    ) : CatalogPlaybackOrigin
}

/**
 * Derives the raw Audiomack track id from an `amk:<id>`-prefixed Corus trackId,
 * or null when the id isn't an Audiomack id (wrong/missing prefix, or nothing
 * after the prefix). The backend tags Audiomack tracks with this prefix (same
 * pattern as SoundCloud/Apple), so the preview resolver can recover the raw id
 * without threading a separate field through the play queue. Kept top-level +
 * pure so it's unit-tested without the manager.
 */
internal fun audiomackIdFromTrackId(trackId: String): String? =
    trackId.takeIf { it.startsWith("amk:") }?.removePrefix("amk:")?.takeIf { it.isNotEmpty() }

/**
 * Whether a play tap for [tappedTrackId] coming from post [tappedSourcePostId]
 * targets the SAME now-playing (or loading) entry given by [activeTrackId] /
 * [activeSourcePostId] — the only case that should toggle pause/play instead of
 * switching playback.
 *
 * The trending feed can show one song posted by several people: distinct cards
 * that share a trackId but each carry their own post id. Matching on trackId
 * alone made tapping a *second* post of the playing song pause it — it looked
 * like a re-tap of the current track. When the active entry and the tap both
 * know their post, they must be the same post to count as a re-tap; a different
 * post of the same song falls through and switches playback to it. When either
 * post id is unknown (single-track / search / detail playback with no
 * originating post) we fall back to a track-id match so those flows keep
 * toggling exactly as before. Mirrors the post-aware disambiguation in
 * [PostPlaybackHighlight].
 */
internal fun isReTapOfActiveEntry(
    activeTrackId: String?,
    activeSourcePostId: String?,
    tappedTrackId: String,
    tappedSourcePostId: String?,
): Boolean {
    if (activeTrackId != tappedTrackId) return false
    if (activeSourcePostId == null || tappedSourcePostId == null) return true
    return activeSourcePostId == tappedSourcePostId
}

data class QueuedTrack(
    val trackId: String,
    val trackName: String,
    val artistName: String,
    val albumArtURL: String?,
    /**
     * High-resolution artwork URL, carried alongside the thumbnail so that
     * posting a track straight from the mini-player (tap art → Post Song)
     * writes the full-res image instead of the blurry preview thumbnail.
     */
    val albumArtLargeURL: String? = null,
    val previewUrl: String?,
    val spotifyURI: String?,
    val spotifyWebURL: String?,
    val isrc: String?,
    val sourcePostId: String?,
    /**
     * User who posted the source CymbalPost. Lets the queue be pruned when
     * the local user unfollows them — otherwise their tracks linger in the
     * in-memory queue and the mini-player's next button keeps playing them
     * even after they've left the visible feed.
     */
    val posterUserId: String? = null,
    val source: TrackSource = TrackSource.SPOTIFY,
    val soundcloudId: String? = null,
    val soundcloudPermalinkUrl: String? = null,
    /** audiomack.com link-out for an Audiomack-source track. Audiomack is source-
     *  locked (link-out only, no Spotify/Apple equivalent), so the mini-player
     *  shows the Audiomack mark + opens this, regardless of the viewer's service. */
    val audiomackUrl: String? = null,
    /** Where this track was played from, when it came from an artist/album page.
     *  See [CatalogPlaybackOrigin]. */
    val catalogOrigin: CatalogPlaybackOrigin? = null,
)

/**
 * A catalog track as a [QueuedTrack] for now-playing. Catalog tracks have no
 * source post, so the queue resolves and advances by track id. Used by album /
 * artist rows, trending, and [SongPreviewArtwork].
 */
fun CymbalTrack.toQueuedTrack(origin: CatalogPlaybackOrigin? = null) = QueuedTrack(
    trackId = id,
    trackName = name,
    artistName = artistName,
    albumArtURL = albumArtURL,
    albumArtLargeURL = albumArtLargeURL,
    previewUrl = previewUrl,
    spotifyURI = spotifyURI.ifBlank { null },
    spotifyWebURL = spotifyWebURL.ifBlank { null },
    isrc = isrc,
    sourcePostId = null,
    source = source,
    soundcloudId = soundcloudId,
    soundcloudPermalinkUrl = soundcloudPermalinkUrl,
    audiomackUrl = audiomackUrl,
    catalogOrigin = origin,
)

/**
 * A feed/profile post as a [QueuedTrack]. Carries [CymbalTrack.albumArtLargeURL]
 * so the full player can render sharp cover art (not the mini/thumbnail URL).
 */
fun CymbalPost.toQueuedTrack() = QueuedTrack(
    trackId = track.id,
    trackName = track.name,
    artistName = track.artistName,
    albumArtURL = track.albumArtURL,
    albumArtLargeURL = track.albumArtLargeURL,
    previewUrl = track.previewUrl,
    spotifyURI = track.spotifyURI.ifBlank { null },
    spotifyWebURL = track.spotifyWebURL.ifBlank { null },
    isrc = track.isrc,
    sourcePostId = id,
    posterUserId = user.id,
    source = track.source,
    soundcloudId = track.soundcloudId,
    soundcloudPermalinkUrl = track.soundcloudPermalinkUrl,
    audiomackUrl = track.audiomackUrl,
)

data class NowPlayingState(
    val trackId: String? = null,
    val trackName: String = "",
    val artistName: String = "",
    val albumArtURL: String? = null,
    /** High-res artwork for posting from the mini-player; see [QueuedTrack.albumArtLargeURL]. */
    val albumArtLargeURL: String? = null,
    val spotifyURI: String? = null,
    val spotifyWebURL: String? = null,
    /** Recording ISRC when known (Apple search results carry one). Lets the
     *  mini-player's Apple→Spotify open-tap hit the server ISRC cache (zero
     *  Spotify calls). Null for older/SoundCloud tracks → name+artist fallback. */
    val isrc: String? = null,
    val isPlaying: Boolean = false,
    val sourcePostId: String? = null,
    val hasNext: Boolean = false,
    val source: TrackSource = TrackSource.SPOTIFY,
    val soundcloudPermalinkUrl: String? = null,
    /** audiomack.com link-out for an Audiomack-source track; source-locked mark. */
    val audiomackUrl: String? = null,
    /** Set when the playing track came from an artist/album page; drives the
     *  mini-player "return to origin" tap. See [CatalogPlaybackOrigin]. */
    val catalogOrigin: CatalogPlaybackOrigin? = null,
) {
    val hasActiveTrack: Boolean get() = trackId != null
}

@Singleton
class NowPlayingManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val cloudFunctions: CloudFunctionsDataSource,
    private val preferencesDataStore: PreferencesDataStore,
    private val userRepository: UserRepository,
    private val musicServicePreference: MusicServicePreference,
    private val tidalAuthService: TidalAuthService,
    private val tidalPlaylistService: TidalPlaylistService,
    private val remoteConfigService: RemoteConfigService,
    private val spotifyPlaybackService: SpotifyPlaybackService,
    private val spotifyAuthService: SpotifyAuthService,
    private val spotifyRepository: SpotifyRepository,
    private val subscriptionRepository: SubscriptionRepository,
    private val playbackModePromptManager: PlaybackModePromptManager,
) {
    private val managerScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    /**
     * Corus postIds we've already reported an in-app play for THIS app session.
     * Avoids re-calling the `recordPlay` callable when the same corus plays
     * again; the backend is the real dedup (lifetime-unique by uid). Confined
     * to [managerScope]'s Main dispatcher, so no synchronization is needed.
     * Resets on cold start.
     */
    private val recordedPlayPostIds = mutableSetOf<String>()

    @Volatile
    private var spotifyCorusBackgroundedAt: Long? = null
    private var spotifyDeviceLockedForQueueDriving = false

    init {
        // Let the trailer coordinator pause music when a trailer starts, keeping
        // the two audio sources mutually exclusive without a direct dependency.
        TrailerPlaybackCoordinator.pauseMusic = { pause() }
        // Same for an audio caption: starting one pauses the song.
        VoiceNotePlayerManager.pauseMusic = { pause() }
        // Prune the queue whenever the local user unfollows someone — keeps
        // the mini-player from advancing into tracks belonging to a user
        // they just stopped following.
        managerScope.launch {
            userRepository.unfollowEvents.collect { unfollowedUserId ->
                removeFromQueue(setOf(unfollowedUserId))
            }
        }
        ProcessLifecycleOwner.get().lifecycle.addObserver(
            LifecycleEventObserver { _, event ->
                when (event) {
                    Lifecycle.Event.ON_STOP -> {
                        spotifyCorusBackgroundedAt = System.currentTimeMillis()
                        refreshSpotifyFastPathSkipGuardWhenLocked()
                    }
                    Lifecycle.Event.ON_START -> {
                        spotifyCorusBackgroundedAt = null
                        managerScope.launch { reconcileExternalSpotifyOnForeground() }
                    }
                    else -> Unit
                }
            },
        )
        val keyguard = context.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
        spotifyDeviceLockedForQueueDriving = keyguard.isKeyguardLocked
        val lockReceiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context?, intent: Intent?) {
                spotifyDeviceLockedForQueueDriving = when (intent?.action) {
                    Intent.ACTION_SCREEN_OFF -> {
                        refreshSpotifyFastPathSkipGuardWhenLocked()
                        true
                    }
                    Intent.ACTION_USER_PRESENT -> false
                    else -> keyguard.isKeyguardLocked
                }
            }
        }
        val lockFilter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_USER_PRESENT)
            addAction(Intent.ACTION_SCREEN_ON)
        }
        ContextCompat.registerReceiver(
            context,
            lockReceiver,
            lockFilter,
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
    }

    // ── Spotify Connect (auth experiment) ──────────────────────────────────

    @Volatile
    var isSpotifyConnectPlaying: Boolean = false
        private set

    /** User is listening in Spotify outside the Corus feed — mini player mirrors metadata. */
    private val _isExternalSpotifyListening = MutableStateFlow(false)
    val isExternalSpotifyListeningFlow: StateFlow<Boolean> = _isExternalSpotifyListening.asStateFlow()
    val isExternalSpotifyListening: Boolean get() = _isExternalSpotifyListening.value

    private val _isHydratingExternalSpotify = MutableStateFlow(false)
    val isHydratingExternalSpotify: StateFlow<Boolean> = _isHydratingExternalSpotify.asStateFlow()

    private var externalSpotifyCachedTrack: CymbalTrack? = null
    private var externalSpotifyTrackURI: String? = null
    private var externalSpotifyUserPaused = false
    private var externalSpotifyPositionJob: Job? = null

    /** Catalog track for external Spotify playback — mini-player tap → song page. */
    fun externalSpotifyCymbalTrack(): CymbalTrack? = externalSpotifyCachedTrack

    val currentSourcePostId: String? get() = _state.value.sourcePostId
    val isPreviewMode: Boolean
        get() = _state.value.hasActiveTrack && !isSpotifyConnectPlaying && player != null

    /** Upgrade the current feed preview to in-app full playback (mini-player toggle). */
    suspend fun upgradeCurrentPreviewToFullSong() {
        if (!isPreviewMode || !isPlaying) return
        val idx = currentQueueIndex
        val track = when {
            idx != null && idx in queue.indices -> queue[idx]
            else -> QueuedTrack(
                trackId = _state.value.trackId ?: return,
                trackName = _state.value.trackName,
                artistName = _state.value.artistName,
                albumArtURL = _state.value.albumArtURL,
                albumArtLargeURL = _state.value.albumArtLargeURL,
                previewUrl = null,
                spotifyURI = _state.value.spotifyURI,
                spotifyWebURL = _state.value.spotifyWebURL,
                isrc = _state.value.isrc,
                sourcePostId = _state.value.sourcePostId,
                source = _state.value.source,
            )
        }
        val playFullSongs = preferencesDataStore.effectivePlayFullSongsSync()
        val outcome = FullSongPlayCoordinator.playTapOutcome(
            track = track.toCymbalTrack(),
            sourcePostId = track.sourcePostId,
            queue = queue,
            nowPlaying = this,
            remoteConfig = remoteConfigService,
            musicService = musicServicePreference.current.value,
            playFullSongs = playFullSongs,
            playbackModePromptManager = playbackModePromptManager,
            skipPlaybackModePrompt = true,
            preferFullSong = true,
        )
        FullSongPlayCoordinator.applyPlayTapOutcome(
            outcome = outcome,
            track = track.toCymbalTrack(),
            sourcePostId = track.sourcePostId,
            queue = queue,
            nowPlaying = this,
            remoteConfig = remoteConfigService,
            musicService = musicServicePreference.current.value,
            playFullSongs = playFullSongs,
            playbackModePromptManager = playbackModePromptManager,
            onPreview = { playInternal(track, forceSwitch = true) },
            scope = managerScope,
        )
    }

    /** Downgrade the current in-app full session to a 30s preview (mini-player toggle). */
    suspend fun downgradeCurrentFullSongToPreview() {
        if (!_state.value.hasActiveTrack || !isPlaying || isPreviewMode) return
        val idx = currentQueueIndex
        val track = when {
            idx != null && idx in queue.indices -> queue[idx]
            else -> QueuedTrack(
                trackId = _state.value.trackId ?: return,
                trackName = _state.value.trackName,
                artistName = _state.value.artistName,
                albumArtURL = _state.value.albumArtURL,
                albumArtLargeURL = _state.value.albumArtLargeURL,
                previewUrl = null,
                spotifyURI = _state.value.spotifyURI,
                spotifyWebURL = _state.value.spotifyWebURL,
                isrc = _state.value.isrc,
                sourcePostId = _state.value.sourcePostId,
                source = _state.value.source,
            )
        }
        if (isExternalSpotifyListening) {
            spotifyPlaybackService.pause()
            clearExternalSpotifyListening()
        }
        silentlyHaltSpotifyForPreview()
        playInternal(track, userInitiated = true, forceSwitch = true)
    }

    private var playbackModeDowngradeJob: Job? = null

    /**
     * Apply a mini-player mode flip. Mirrors iOS `applyPlaybackModeToggle`:
     * - → Full: only while playing a preview (not external Spotify)
     * - → 30s + external Spotify playing: fall back to preview
     * - → 30s + external Spotify paused: clear mirror so the toggle can reappear
     * - → 30s + in-app full playing: wait ~200ms for pill anim, then downgrade
     * - → 30s while paused (non-external): no-op; resume path restarts if needed
     */
    suspend fun applyPlaybackModeToggle(toFull: Boolean) {
        if (!_state.value.hasActiveTrack) return
        playbackModeDowngradeJob?.cancel()
        playbackModeDowngradeJob = null

        if (toFull) {
            if (!isPlaying || isExternalSpotifyListening) return
            upgradeCurrentPreviewToFullSong()
            return
        }

        // → 30s
        if (isExternalSpotifyListening) {
            if (isPlaying) {
                downgradeCurrentFullSongToPreview()
            } else {
                clearExternalSpotifyListening()
            }
            return
        }

        if (!isPlaying) return
        playbackModeDowngradeJob = managerScope.launch {
            delay(200)
            if (!_state.value.hasActiveTrack || !isPlaying || isPreviewMode) return@launch
            downgradeCurrentFullSongToPreview()
        }
    }

    fun shouldRestartPausedSessionForDesiredPlaybackMode(): Boolean {
        return SongPlayRouting.shouldRestartPausedSessionForDesiredMode(
            hasActiveTrack = _state.value.hasActiveTrack,
            isPlaying = isPlaying,
            isPreviewMode = isPreviewMode,
            desiresFullSong = preferencesDataStore.effectivePlayFullSongsSync(),
            isExternalSpotifyListening = isExternalSpotifyListening,
        )
    }

    fun restartCurrentTrackForDesiredPlaybackMode() {
        val idx = currentQueueIndex
        val track = when {
            idx != null && idx in queue.indices -> queue[idx]
            else -> QueuedTrack(
                trackId = _state.value.trackId ?: return,
                trackName = _state.value.trackName,
                artistName = _state.value.artistName,
                albumArtURL = _state.value.albumArtURL,
                albumArtLargeURL = _state.value.albumArtLargeURL,
                previewUrl = null,
                spotifyURI = _state.value.spotifyURI,
                spotifyWebURL = _state.value.spotifyWebURL,
                isrc = _state.value.isrc,
                sourcePostId = _state.value.sourcePostId,
                source = _state.value.source,
                soundcloudId = null,
                soundcloudPermalinkUrl = _state.value.soundcloudPermalinkUrl,
                audiomackUrl = _state.value.audiomackUrl,
            )
        }
        val preferFull = preferencesDataStore.effectivePlayFullSongsSync()
        val q = queue.ifEmpty { listOf(track) }
        this.queue = q
        this.currentQueueIndex = q.indexOfActive(track.trackId, track.sourcePostId)
        routePlayTap(
            track = track.toCymbalTrack(),
            sourcePostId = track.sourcePostId,
            queue = q,
            preferFullSong = preferFull,
            skipPlaybackModePrompt = true,
            onPreview = {
                playInternal(track, userInitiated = true, forceSwitch = true)
            },
        )
    }

    private fun QueuedTrack.toCymbalTrack() = CymbalTrack(
        id = trackId,
        name = trackName,
        artistName = artistName,
        albumName = "",
        albumArtURL = albumArtURL,
        albumArtLargeURL = albumArtLargeURL,
        spotifyURI = spotifyURI.orEmpty(),
        spotifyWebURL = spotifyWebURL.orEmpty(),
        previewUrl = previewUrl,
        isrc = isrc,
        source = source,
        soundcloudId = soundcloudId,
        soundcloudPermalinkUrl = soundcloudPermalinkUrl,
        audiomackUrl = audiomackUrl,
    )

    private val _isResolvingSpotify = MutableStateFlow(false)
    val isResolvingSpotifyFlow: StateFlow<Boolean> = _isResolvingSpotify.asStateFlow()
    val isResolvingSpotify: Boolean get() = _isResolvingSpotify.value

    private var spotifyConnectPlayJob: Job? = null
    private var spotifyConnectPlayGeneration = 0
    private var spotifyQueueSessionId = 0
    private var spotifyConnectStartedAt: Long? = null
    private var spotifyConnectWasPlaying = false
    private var userInitiatedPause = false
    private var manualSkipGuardUntil: Long? = null
    private var spotifyFeedSkipRequestedUntil: Long? = null
    private var spotifyCorusRequestedUri: String? = null
    private var spotifyCorusRequestedUntil: Long? = null
    private var spotifyPendingExternalUri: String? = null
    /** URI of a Connect handoff that failed after Spotify may already be audible. */
    private var spotifyFailedHandoffUri: String? = null
    private var spotifyFailedHandoffSuppressExternalUntilMs: Long? = null
    private var spotifyRelinquishJob: Job? = null
    private var spotifyNaturalEndAdvanceJob: Job? = null
    private var spotifyExpectedTrackUri: String? = null
    private var spotifyQueueTransitionUntil: Long? = null
    private val spotifyUriToQueueIndex = mutableMapOf<String, Int>()
    private val spotifyPlaybackUriCache = mutableMapOf<String, String>()
    private val spotifyAbsentTrackIds = mutableSetOf<String>()
    private var spotifyScrubAnchorWallTime: Long? = null
    private var spotifyScrubAnchorPosition = 0.0
    /** After skip/track-change, pin scrubber at 0 until Spotify reports the new track playing. */
    private var spotifyScrubberHoldAtZero = false
    private var spotifyScrubberHoldUntilTrackChangeFromUri: String? = null
    /** Outgoing-track position reports can lag behind URI changes after skip. */
    private val spotifyScrubberHoldMaxReleasePositionSec = 3.0
    private var spotifyPositionJob: Job? = null
    private var spotifySeekJob: Job? = null

    fun spotifyExperimentEnabledForTrack(source: TrackSource, preferFullSong: Boolean = false): Boolean {
        if (!SpotifyPlaybackService.isSpotifyAppInstalled(context)) return false
        val playFull = preferFullSong || preferencesDataStore.effectivePlayFullSongsSync()
        return SongPlayRouting.wantsSpotifyExperiment(
            source = source,
            service = musicServicePreference.current.value,
            playFullSongs = playFull,
        )
    }

    /** Album-art play/pause overlay for in-app full-song playback. */
    fun showsFullSongPlayingOverlay(service: MusicService): Boolean {
        if (_isResolvingSpotify.value) return false
        return when (service) {
            MusicService.SPOTIFY -> {
                val spotifyActive = isSpotifyConnectPlaying || isExternalSpotifyListening
                spotifyActive && _state.value.isPlaying
            }
            else -> false
        }
    }

    /** Same post is in a full-song session — art tap toggles transport. */
    fun isFullSongSessionActive(service: MusicService, trackId: String, sourcePostId: String): Boolean {
        if (!isReTapOfActiveEntry(
                activeTrackId = _state.value.trackId,
                activeSourcePostId = _state.value.sourcePostId,
                tappedTrackId = trackId,
                tappedSourcePostId = sourcePostId,
            )
        ) {
            return false
        }
        if (isPreviewMode) return false
        return when (service) {
            MusicService.SPOTIFY -> isSpotifyConnectPlaying || isExternalSpotifyListening
            else -> false
        }
    }

    /** Mini-player / lock-screen Next chains previews in 30s mode or during preview playback. */
    val preferPreviewOnInAppSkip: Boolean
        get() = isPreviewMode || !preferencesDataStore.effectivePlayFullSongsSync()

    private val isActiveFullSongSession: Boolean
        get() {
            if (isPreviewMode) return false
            return when (musicServicePreference.current.value) {
                MusicService.SPOTIFY -> isSpotifyConnectPlaying || isExternalSpotifyListening
                else -> false
            }
        }

    private fun shouldChainFullPlaybackOnSkip(preferPreviewOnNext: Boolean): Boolean {
        if (preferPreviewOnNext) return false
        return preferencesDataStore.effectivePlayFullSongsSync()
    }

    fun setQueueFromCoordinator(
        queue: List<QueuedTrack>,
        playingTrackId: String,
        playingSourcePostId: String?,
    ) {
        this.queue = queue
        currentQueueIndex = queue.indexOfActive(playingTrackId, playingSourcePostId)
        _state.value = _state.value.copy(hasNext = computeHasNext())
    }

    /**
     * Stage a catalog list (e.g. trending chart) so song-detail play can seed
     * Next without threading the queue through navigation routes. Resolved by
     * [catalogQueueForPlayback] when the matching track is played.
     */
    private var stagedCatalogQueue: List<QueuedTrack> = emptyList()

    fun stageCatalogQueue(queue: List<QueuedTrack>) {
        stagedCatalogQueue = queue
    }

    /**
     * Queue to use when playing [trackId] from song detail: keep the active
     * queue if it already contains the track (re-tap after trending play),
     * otherwise consume a staged chart from [stageCatalogQueue].
     */
    fun catalogQueueForPlayback(trackId: String): List<QueuedTrack> {
        if (queue.any { it.trackId == trackId }) return queue
        val staged = stagedCatalogQueue
        stagedCatalogQueue = emptyList()
        if (staged.isEmpty() || staged.none { it.trackId == trackId }) return emptyList()
        return staged
    }

    fun launchSpotifyConnectPlay(block: suspend () -> Unit) {
        managerScope.launch { block() }
    }

    /**
     * Route a catalog play tap through [FullSongPlayCoordinator] when the Spotify
     * experiment is on; otherwise run [onPreview] unchanged (30s ExoPlayer path).
     */
    fun routePlayTap(
        track: fm.corus.android.data.model.CymbalTrack,
        sourcePostId: String? = null,
        queue: List<QueuedTrack> = emptyList(),
        preferFullSong: Boolean = false,
        skipPlaybackModePrompt: Boolean = false,
        onPreview: suspend () -> Unit,
    ) {
        managerScope.launch {
            val playFullSongs = preferencesDataStore.effectivePlayFullSongsSync()
            val outcome = FullSongPlayCoordinator.playTapOutcome(
                track = track,
                sourcePostId = sourcePostId,
                queue = queue,
                nowPlaying = this@NowPlayingManager,
                remoteConfig = remoteConfigService,
                musicService = musicServicePreference.current.value,
                playFullSongs = playFullSongs,
                playbackModePromptManager = playbackModePromptManager,
                skipPlaybackModePrompt = skipPlaybackModePrompt,
                preferFullSong = preferFullSong,
            )
            FullSongPlayCoordinator.applyPlayTapOutcome(
                outcome = outcome,
                track = track,
                sourcePostId = sourcePostId,
                queue = queue,
                nowPlaying = this@NowPlayingManager,
                remoteConfig = remoteConfigService,
                musicService = musicServicePreference.current.value,
                playFullSongs = playFullSongs,
                playbackModePromptManager = playbackModePromptManager,
                onPreview = onPreview,
                scope = managerScope,
            )
        }
    }

    private var queue: List<QueuedTrack> = emptyList()
    private var currentQueueIndex: Int? = null
    private var queueHasMore: Boolean = false
    private var loadMoreQueue: (suspend () -> Unit)? = null
    private var isLoadingMoreQueue: Boolean = false

    /** Snapshot of the in-memory queue for the full-player Queue sheet. */
    fun queueSnapshot(): List<QueuedTrack> = queue

    fun currentQueueIndexSnapshot(): Int? = currentQueueIndex

    fun removeQueueItem(index: Int) {
        if (index !in queue.indices) return
        val playingIdx = currentQueueIndex
        if (playingIdx == index) return // Don't remove the now-playing row.
        val pruned = queue.toMutableList().also { it.removeAt(index) }
        queue = pruned
        currentQueueIndex = pruned.indexOfActive(_state.value.trackId, _state.value.sourcePostId)
        _state.value = _state.value.copy(hasNext = computeHasNext())
    }

    fun moveQueueItem(fromIndex: Int, toIndex: Int) {
        if (fromIndex !in queue.indices || toIndex !in queue.indices) return
        val playingIdx = currentQueueIndex ?: return
        // Only allow reordering within Up Next (after the playing index).
        if (fromIndex <= playingIdx || toIndex <= playingIdx) return
        val mutable = queue.toMutableList()
        val item = mutable.removeAt(fromIndex)
        mutable.add(toIndex, item)
        queue = mutable
        currentQueueIndex = mutable.indexOfActive(_state.value.trackId, _state.value.sourcePostId)
        _state.value = _state.value.copy(hasNext = computeHasNext())
    }

    fun jumpToQueueIndex(index: Int) {
        if (index !in queue.indices) return
        val track = queue[index]
        currentQueueIndex = index
        _state.value = _state.value.copy(hasNext = computeHasNext())
        val preferFull = !preferPreviewOnInAppSkip
        routePlayTap(
            track = track.toCymbalTrack(),
            sourcePostId = track.sourcePostId,
            queue = queue,
            preferFullSong = preferFull,
            skipPlaybackModePrompt = true,
            onPreview = {
                playInternal(track, userInitiated = true, forceSwitch = true)
            },
        )
    }

    private fun computeHasNext(): Boolean {
        val idx = currentQueueIndex ?: return false
        return idx + 1 < queue.size || queueHasMore
    }

    /**
     * Locate the queue slot for the active entry, preferring an exact post match
     * so duplicate songs (one trackId across several posts) resolve to the post
     * that was actually tapped rather than the first copy in the queue — which
     * would otherwise make "next"/auto-advance continue from the wrong card.
     * Falls back to a track-id match when the post id is unknown or absent from
     * the queue, leaving non-feed / single-track playback unaffected.
     */
    private fun List<QueuedTrack>.indexOfActive(trackId: String?, sourcePostId: String?): Int? {
        if (trackId == null) return null
        if (sourcePostId != null) {
            val exact = indexOfFirst { it.sourcePostId == sourcePostId }
            if (exact >= 0) return exact
        }
        return indexOfFirst { it.trackId == trackId }.takeIf { it >= 0 }
    }
    private var player: ExoPlayer? = null

    /**
     * MediaSession backing the lock-screen / notification media controls.
     * Read by [CorusPlaybackService.onGetSession] so system controllers
     * (lock screen, Bluetooth, Wear) can drive playback. Built lazily once
     * the first track starts and reused across track changes within the
     * same playback context — releasing/recreating per track tears the
     * notification down and back up, which flickers.
     */
    var mediaSession: MediaSession? = null
        private set
    private var foregroundServiceStarted: Boolean = false

    /**
     * Polling coroutine that pumps ExoPlayer's `currentPosition` into the
     * [ScrubberClock] every 250ms while audio is actually playing. Started by
     * the player's `onIsPlayingChanged(true)` and stopped on pause / end /
     * release. Mirrors the iOS AVPlayer periodic time observer + MusicKit
     * polling timer (which on Android collapses to a single source).
     */
    private var positionJob: Job? = null

    private fun startPositionPolling() {
        positionJob?.cancel()
        positionJob = managerScope.launch {
            while (isActive) {
                val p = player?.currentPosition?.coerceAtLeast(0L) ?: 0L
                val d = (player?.duration ?: 0L).takeIf { it > 0L } ?: 0L
                ScrubberClock.update(p, d)
                delay(250L)
            }
        }
    }

    private fun stopPositionPolling(resetClock: Boolean) {
        positionJob?.cancel()
        positionJob = null
        if (resetClock) ScrubberClock.reset()
    }

    /**
     * Seek the active player to [toMs]. Called by the mini-player scrubber on
     * release. Updates [ScrubberClock] eagerly so the scrubber doesn't flash
     * back to the pre-seek position before the next poll lands.
     */
    fun seek(toMs: Long) {
        if (isSpotifyConnectPlaying || isExternalSpotifyListening) {
            val seconds = toMs / 1000.0
            spotifyScrubberHoldAtZero = false
            spotifyScrubberHoldUntilTrackChangeFromUri = null
            if (_state.value.isPlaying) userInitiatedPause = false
            ScrubberClock.snapTime(toMs)
            syncSpotifyScrubAnchor(seconds)
            spotifySeekJob?.cancel()
            spotifySeekJob = managerScope.launch {
                if (isExternalSpotifyListening && !spotifyPlaybackService.isConnected) {
                    spotifyPlaybackService.trySilentReconnectIfNeeded()
                    delay(300)
                }
                runCatching { spotifyPlaybackService.seek(seconds) }
                    .onFailure { error ->
                        android.util.Log.w("NowPlaying", "Spotify seek failed: ${error.message}")
                    }
            }
            return
        }
        val clamped = toMs.coerceAtLeast(0L)
        player?.seekTo(clamped)
        ScrubberClock.setTime(clamped)
    }

    // Read by the persistent ForwardingPlayer's getAvailableCommands to
    // gate the scrubber per source. Spotify/Apple previews are 30s clips
    // where a seek bar feels broken; SoundCloud is full HLS where it's
    // the whole point. Updated on each track change before setMediaItem
    // so media3 picks up the new command set.
    @Volatile
    private var currentTrackIsSoundCloud: Boolean = false

    private val _state = MutableStateFlow(NowPlayingState())
    val state: StateFlow<NowPlayingState> = _state.asStateFlow()

    /**
     * One-shot signal: set to a post id when the user explicitly taps album
     * art on a feed card to start/toggle playback. Read & cleared by feeds'
     * auto-scroll-to-now-playing handlers so we don't re-center on a card the
     * user just tapped (it's already on screen). Mirrors iOS
     * NowPlayingManager.lastUserInitiatedSourcePostId.
     */
    @Volatile
    var lastUserInitiatedSourcePostId: String? = null

    private val _loadingTrackId = MutableStateFlow<String?>(null)
    val loadingTrackId: StateFlow<String?> = _loadingTrackId.asStateFlow()

    /**
     * The `sourcePostId` of the post whose track is currently loading. Lets a
     * feed card tell "I'm the one loading" apart from a *different* post of the
     * same song, which shares the same [loadingTrackId]. Null when loading was
     * started without an originating post. Maintained in lockstep with
     * [_loadingTrackId]. Mirrors iOS MusicPlaybackService.loadingSourcePostId.
     */
    private val _loadingSourcePostId = MutableStateFlow<String?>(null)
    val loadingSourcePostId: StateFlow<String?> = _loadingSourcePostId.asStateFlow()

    /**
     * One-shot event emitted when a *user-initiated* play resolves to no playable
     * preview (e.g. a Spotify track with no Apple Music match). The UI shows a
     * "No preview available" toast. Auto-advance does NOT emit — it silently
     * moves on rather than spamming a toast per dead track in the queue.
     */
    private val _previewUnavailable = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val previewUnavailable: SharedFlow<Unit> = _previewUnavailable.asSharedFlow()

    /** Incremented on each cancel; play() checks this to bail out after URL resolution. */
    private var playGeneration = 0

    // Preview URL cache — avoids redundant Cloud Function calls in-session.
    private val previewCache = mutableMapOf<String, String>()
    @VisibleForTesting
    internal val noMatchCache = mutableSetOf<String>()

    private val _isGeneratingPlaylist = MutableStateFlow(false)
    val isGeneratingPlaylist: StateFlow<Boolean> = _isGeneratingPlaylist.asStateFlow()

    private val _playlistError = MutableStateFlow<String?>(null)
    val playlistError: StateFlow<String?> = _playlistError.asStateFlow()

    fun clearPlaylistError() {
        _playlistError.value = null
    }

    private val _paywallRequested = MutableStateFlow(false)
    val paywallRequested: StateFlow<Boolean> = _paywallRequested.asStateFlow()

    private val _playlistPaywallContext = MutableStateFlow<PlaylistTrialField?>(null)
    val playlistPaywallContext: StateFlow<PlaylistTrialField?> = _playlistPaywallContext.asStateFlow()

    fun clearPaywallRequested() {
        _paywallRequested.value = false
        _playlistPaywallContext.value = null
    }

    private fun requestPlaylistPaywall(field: PlaylistTrialField) {
        _playlistPaywallContext.value = field
        _paywallRequested.value = true
    }

    private fun handleTrialConsumedIfNeeded(trialConsumed: Boolean, field: PlaylistTrialField) {
        if (!trialConsumed) return
        subscriptionRepository.markPlaylistTrialUsed(field)
        val messageRes = when (field) {
            PlaylistTrialField.Feed -> fm.corus.android.R.string.playlist_trial_consumed_feed
            PlaylistTrialField.OwnProfile -> fm.corus.android.R.string.playlist_trial_consumed_own_profile
            PlaylistTrialField.OtherProfile -> fm.corus.android.R.string.playlist_trial_consumed_other_profile
            PlaylistTrialField.Hashtag -> fm.corus.android.R.string.playlist_trial_consumed_hashtag
        }
        ToastManager.show(context.getString(messageRes))
    }

    private fun isNetworkAvailable(): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    suspend fun generateFeedPlaylist(
        newReleasesOnly: Boolean = false,
        feedMode: String = "following",
        sessionToken: String? = null,
    ) {
        // TIDAL users get the playlist on their own account, built client-side
        // from the backend's resolved track list (mirrors iOS). Apple Music /
        // Deezer have no client-side path on Android and are blocked at the UI.
        if (musicServicePreference.current.value == MusicService.TIDAL) {
            generateFeedPlaylistTidal(newReleasesOnly, feedMode, sessionToken)
            return
        }
        _isGeneratingPlaylist.value = true
        ToastManager.show("Generating playlist\u2026")

        if (!isNetworkAvailable()) {
            _playlistError.value = "Couldn't connect. Check your connection."
            _isGeneratingPlaylist.value = false
            return
        }

        try {
            val result = cloudFunctions.generateFeedPlaylist(newReleasesOnly, feedMode, sessionToken)
            if (result.soundcloudSkipped > 0) {
                android.util.Log.i("NowPlaying", "Feed playlist skipped ${result.soundcloudSkipped} SoundCloud track(s)")
            }
            if (!result.cached) {
                delay(2000)
            }
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(result.playlistWebURL)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            handleTrialConsumedIfNeeded(result.trialConsumed, PlaylistTrialField.Feed)
        } catch (e: CloudFunctionsDataSource.PaywallRequiredException) {
            requestPlaylistPaywall(PlaylistTrialField.Feed)
        } catch (e: CloudFunctionsDataSource.OnlySoundCloudException) {
            _playlistError.value = "Your feed only has SoundCloud tracks — Spotify playlists aren't available for those."
        } catch (e: UnknownHostException) {
            _playlistError.value = "Couldn't connect. Check your connection."
        } catch (e: SocketTimeoutException) {
            _playlistError.value = "Couldn't connect. Check your connection."
        } catch (e: Exception) {
            android.util.Log.e("NowPlaying", "Feed playlist failed (feedMode=$feedMode)", e)
            _playlistError.value = "Something went wrong. Try again later."
        }
        _isGeneratingPlaylist.value = false
    }

    suspend fun generateProfilePlaylist(
        userId: String,
        source: CloudFunctionsDataSource.ProfilePlaylistSource = CloudFunctionsDataSource.ProfilePlaylistSource.Posts,
        isOwnProfile: Boolean = true,
        // Lifts the backend's 75-track snapshot cap to export the whole source.
        fullExport: Boolean = false,
    ) {
        if (musicServicePreference.current.value == MusicService.TIDAL) {
            generateProfilePlaylistTidal(userId, source, isOwnProfile, fullExport)
            return
        }
        _isGeneratingPlaylist.value = true
        ToastManager.show("Generating playlist\u2026")

        if (!isNetworkAvailable()) {
            _playlistError.value = "Couldn't connect. Check your connection."
            _isGeneratingPlaylist.value = false
            return
        }

        try {
            val result = cloudFunctions.generateProfilePlaylist(userId, source, fullExport)
            if (result.soundcloudSkipped > 0) {
                android.util.Log.i("NowPlaying", "Profile playlist skipped ${result.soundcloudSkipped} SoundCloud track(s)")
            }
            if (!result.cached) {
                delay(2000) // Wait for Spotify to process
            }
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(result.playlistWebURL)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            handleTrialConsumedIfNeeded(
                result.trialConsumed,
                PlaylistTrialUsed.profileField(isOwnProfile),
            )
        } catch (e: CloudFunctionsDataSource.PaywallRequiredException) {
            requestPlaylistPaywall(PlaylistTrialUsed.profileField(isOwnProfile))
        } catch (e: CloudFunctionsDataSource.OnlySoundCloudException) {
            _playlistError.value = "This profile only has SoundCloud tracks — Spotify playlists aren't available for those."
        } catch (e: UnknownHostException) {
            _playlistError.value = "Couldn't connect. Check your connection."
        } catch (e: SocketTimeoutException) {
            _playlistError.value = "Couldn't connect. Check your connection."
        } catch (e: Exception) {
            android.util.Log.e("NowPlaying", "Profile playlist failed", e)
            _playlistError.value = "Something went wrong. Try again later."
        }
        _isGeneratingPlaylist.value = false
    }

    suspend fun generateHashtagPlaylist(
        hashtag: String,
        // Lifts the backend's 75-track snapshot cap to export the whole tag.
        fullExport: Boolean = false,
    ) {
        if (musicServicePreference.current.value == MusicService.TIDAL) {
            generateHashtagPlaylistTidal(hashtag, fullExport)
            return
        }
        _isGeneratingPlaylist.value = true
        ToastManager.show("Generating playlist…")

        if (!isNetworkAvailable()) {
            _playlistError.value = "Couldn't connect. Check your connection."
            _isGeneratingPlaylist.value = false
            return
        }

        try {
            val result = cloudFunctions.generateHashtagPlaylist(hashtag, fullExport)
            if (result.soundcloudSkipped > 0) {
                android.util.Log.i("NowPlaying", "Hashtag playlist skipped ${result.soundcloudSkipped} SoundCloud track(s)")
            }
            if (!result.cached) {
                delay(2000) // Wait for Spotify to process
            }
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(result.playlistWebURL)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            handleTrialConsumedIfNeeded(result.trialConsumed, PlaylistTrialField.Hashtag)
        } catch (e: CloudFunctionsDataSource.PaywallRequiredException) {
            requestPlaylistPaywall(PlaylistTrialField.Hashtag)
        } catch (e: CloudFunctionsDataSource.OnlySoundCloudException) {
            _playlistError.value = context.getString(fm.corus.android.R.string.hashtag_playlist_only_soundcloud)
        } catch (e: UnknownHostException) {
            _playlistError.value = "Couldn't connect. Check your connection."
        } catch (e: SocketTimeoutException) {
            _playlistError.value = "Couldn't connect. Check your connection."
        } catch (e: Exception) {
            android.util.Log.e("NowPlaying", "Hashtag playlist failed (hashtag=$hashtag)", e)
            _playlistError.value = "Something went wrong. Try again later."
        }
        _isGeneratingPlaylist.value = false
    }

    // ── TIDAL playlists (client-side, on the user's own account) ─────────────
    // Mirrors iOS: the backend resolves the feed/profile into track descriptors
    // (appleMusicTracks flag), the client resolves each to a TIDAL id and creates
    // the playlist via TIDAL's Web API. Only TIDAL has a client-side path on
    // Android — Apple Music needs iOS-only MusicKit, Deezer is link-out only.

    /** Spotify-trackId → resolved TIDAL trackId, memoized for the session. */
    @VisibleForTesting
    internal val tidalIdCache = mutableMapOf<String, String>()

    /** TIDAL id lookups per concurrent batch (each lookup is a callable). */
    private val tidalResolveBatch = 5

    private suspend fun generateFeedPlaylistTidal(
        newReleasesOnly: Boolean,
        feedMode: String,
        sessionToken: String?,
    ) {
        if (!isNetworkAvailable()) {
            _playlistError.value = "Couldn't connect. Check your connection."
            return
        }
        // Tapping generate is the sign-in trigger; log in before the overlay.
        if (!ensureTidalLogin()) return

        _isGeneratingPlaylist.value = true
        // A persistent spinner toast: TIDAL generation is multi-step (resolve
        // each track, then create + populate the playlist), so it stays up until
        // the playlist opens or an error replaces it.
        val toastId = ToastManager.showLoading("Generating playlist…")
        try {
            when (val outcome = cloudFunctions.generateFeedPlaylistTracks(newReleasesOnly, feedMode, sessionToken)) {
                is CloudFunctionsDataSource.PlaylistTracksOutcome.Paywall ->
                    requestPlaylistPaywall(PlaylistTrialField.Feed)
                is CloudFunctionsDataSource.PlaylistTracksOutcome.Failure ->
                    _playlistError.value = if (outcome.soundcloudSkipped > 0)
                        "Your feed only has SoundCloud tracks — playlists aren't available for those."
                    else
                        "No tracks found in your feed yet."
                is CloudFunctionsDataSource.PlaylistTracksOutcome.Tracks -> {
                    handleTrialConsumedIfNeeded(outcome.trialConsumed, PlaylistTrialField.Feed)
                    buildTidalPlaylist(feedPlaylistName(feedMode), "Generated by Corus", outcome.descriptors, outcome.soundcloudSkipped)
                }
            }
        } catch (e: UnknownHostException) {
            _playlistError.value = "Couldn't connect. Check your connection."
        } catch (e: SocketTimeoutException) {
            _playlistError.value = "Couldn't connect. Check your connection."
        } catch (e: Exception) {
            android.util.Log.e("NowPlaying", "TIDAL feed playlist failed", e)
            _playlistError.value = "Something went wrong. Try again later."
        } finally {
            ToastManager.dismiss(toastId)
            _isGeneratingPlaylist.value = false
        }
    }

    private suspend fun generateProfilePlaylistTidal(
        userId: String,
        source: CloudFunctionsDataSource.ProfilePlaylistSource,
        isOwnProfile: Boolean,
        fullExport: Boolean = false,
    ) {
        if (!isNetworkAvailable()) {
            _playlistError.value = "Couldn't connect. Check your connection."
            return
        }
        if (!ensureTidalLogin()) return

        _isGeneratingPlaylist.value = true
        val toastId = ToastManager.showLoading("Generating playlist…")
        try {
            when (val outcome = cloudFunctions.generateProfilePlaylistTracks(userId, source, fullExport)) {
                is CloudFunctionsDataSource.PlaylistTracksOutcome.Paywall ->
                    requestPlaylistPaywall(PlaylistTrialUsed.profileField(isOwnProfile))
                is CloudFunctionsDataSource.PlaylistTracksOutcome.Failure ->
                    _playlistError.value = if (outcome.soundcloudSkipped > 0)
                        "This profile only has SoundCloud tracks — playlists aren't available for those."
                    else
                        "No songs to add to a playlist yet."
                is CloudFunctionsDataSource.PlaylistTracksOutcome.Tracks -> {
                    handleTrialConsumedIfNeeded(
                        outcome.trialConsumed,
                        PlaylistTrialUsed.profileField(isOwnProfile),
                    )
                    val (title, description) = profilePlaylistNaming(outcome.username ?: "Corus", source, isOwnProfile)
                    buildTidalPlaylist(title, description, outcome.descriptors, outcome.soundcloudSkipped)
                }
            }
        } catch (e: UnknownHostException) {
            _playlistError.value = "Couldn't connect. Check your connection."
        } catch (e: SocketTimeoutException) {
            _playlistError.value = "Couldn't connect. Check your connection."
        } catch (e: Exception) {
            android.util.Log.e("NowPlaying", "TIDAL profile playlist failed", e)
            _playlistError.value = "Something went wrong. Try again later."
        } finally {
            ToastManager.dismiss(toastId)
            _isGeneratingPlaylist.value = false
        }
    }

    private suspend fun generateHashtagPlaylistTidal(
        hashtag: String,
        fullExport: Boolean = false,
    ) {
        if (!isNetworkAvailable()) {
            _playlistError.value = "Couldn't connect. Check your connection."
            return
        }
        if (!ensureTidalLogin()) return

        _isGeneratingPlaylist.value = true
        val toastId = ToastManager.showLoading("Generating playlist…")
        try {
            when (val outcome = cloudFunctions.generateHashtagPlaylistTracks(hashtag, fullExport)) {
                is CloudFunctionsDataSource.PlaylistTracksOutcome.Paywall ->
                    requestPlaylistPaywall(PlaylistTrialField.Hashtag)
                is CloudFunctionsDataSource.PlaylistTracksOutcome.Failure ->
                    _playlistError.value = if (outcome.soundcloudSkipped > 0)
                        "This hashtag only has SoundCloud tracks, so playlists aren't available for those."
                    else
                        "No songs to add to a playlist yet."
                is CloudFunctionsDataSource.PlaylistTracksOutcome.Tracks -> {
                    handleTrialConsumedIfNeeded(outcome.trialConsumed, PlaylistTrialField.Hashtag)
                    val (title, description) = hashtagPlaylistNaming(hashtag)
                    buildTidalPlaylist(title, description, outcome.descriptors, outcome.soundcloudSkipped)
                }
            }
        } catch (e: UnknownHostException) {
            _playlistError.value = "Couldn't connect. Check your connection."
        } catch (e: SocketTimeoutException) {
            _playlistError.value = "Couldn't connect. Check your connection."
        } catch (e: Exception) {
            android.util.Log.e("NowPlaying", "TIDAL hashtag playlist failed", e)
            _playlistError.value = "Something went wrong. Try again later."
        } finally {
            ToastManager.dismiss(toastId)
            _isGeneratingPlaylist.value = false
        }
    }

    /** Ensure a TIDAL user session, launching the login flow if needed. Sets the
     *  error message and returns false if the user didn't complete sign-in. */
    private suspend fun ensureTidalLogin(): Boolean {
        if (tidalAuthService.isUserLoggedIn()) return true
        if (!tidalAuthService.login()) {
            _playlistError.value = "Sign in to TIDAL to create a playlist."
            return false
        }
        // The TIDAL login screen is animating away; let the feed return to the
        // front before the caller shows the "Generating…" toast, otherwise it
        // flashes behind the transition and the user misses it.
        delay(500)
        return true
    }

    private suspend fun buildTidalPlaylist(
        name: String,
        description: String,
        descriptors: List<CloudFunctionsDataSource.PlaylistTrackDescriptor>,
        soundcloudSkipped: Int,
    ) {
        val tidalIds = resolveTidalIds(descriptors)
        if (tidalIds.isEmpty()) {
            _playlistError.value = "Couldn't find these songs on TIDAL."
            return
        }
        val token = tidalAuthService.accessToken()
        if (token == null) {
            _playlistError.value = "Sign in to TIDAL to create a playlist."
            return
        }
        try {
            createTidalPlaylistOpening(name, description, tidalIds, token, soundcloudSkipped)
        } catch (e: TidalPlaylistService.PlaylistException.InsufficientScope) {
            // Session predates the playlists.write scope — re-consent and retry once.
            if (!tidalAuthService.reauthenticateForPlaylists()) {
                _playlistError.value = "TIDAL needs permission to manage your playlists."
                return
            }
            val fresh = tidalAuthService.accessToken()
            if (fresh == null) {
                _playlistError.value = "Sign in to TIDAL to create a playlist."
                return
            }
            try {
                createTidalPlaylistOpening(name, description, tidalIds, fresh, soundcloudSkipped)
            } catch (e2: Exception) {
                android.util.Log.e("NowPlaying", "TIDAL playlist creation failed after re-auth", e2)
                _playlistError.value = "Couldn't create the TIDAL playlist."
            }
        } catch (e: Exception) {
            android.util.Log.e("NowPlaying", "TIDAL playlist creation failed", e)
            _playlistError.value = "Couldn't create the TIDAL playlist."
        }
    }

    private suspend fun createTidalPlaylistOpening(
        name: String,
        description: String,
        tidalIds: List<String>,
        token: String,
        soundcloudSkipped: Int,
    ) {
        val result = tidalPlaylistService.createPlaylist(name, description, tidalIds, token)
        result.url?.let { url ->
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        }
        val unresolved = result.requestedCount - result.addedCount
        if (unresolved > 0 || soundcloudSkipped > 0) {
            android.util.Log.i(
                "NowPlaying",
                "TIDAL playlist: added ${result.addedCount}/${result.requestedCount}, $soundcloudSkipped SoundCloud skipped",
            )
        }
    }

    /** Resolve descriptors → TIDAL track ids, preserving order and de-duping.
     *  Resolves in bounded-concurrency batches since each lookup is a callable. */
    @VisibleForTesting
    internal suspend fun resolveTidalIds(
        descriptors: List<CloudFunctionsDataSource.PlaylistTrackDescriptor>,
    ): List<String> {
        val byIndex = HashMap<Int, String>()
        descriptors.indices.chunked(tidalResolveBatch).forEach { batch ->
            coroutineScope {
                batch.map { i -> async { i to resolveTidalId(descriptors[i]) } }.awaitAll()
            }.forEach { (i, id) -> if (id != null) byIndex[i] = id }
        }
        val seen = HashSet<String>()
        val ordered = ArrayList<String>()
        for (i in descriptors.indices) {
            val id = byIndex[i] ?: continue
            if (seen.add(id)) ordered.add(id)
        }
        return ordered
    }

    private suspend fun resolveTidalId(d: CloudFunctionsDataSource.PlaylistTrackDescriptor): String? {
        if (d.trackId.isNotEmpty()) tidalIdCache[d.trackId]?.let { return it }
        val id = cloudFunctions.tidalLookupId(d.name, d.artist, d.isrc, d.trackId.ifEmpty { null })
        if (id != null && d.trackId.isNotEmpty()) tidalIdCache[d.trackId] = id
        return id
    }

    private fun feedPlaylistName(feedMode: String): String = when (feedMode) {
        "trending" -> "Corus Trending"
        "tasteMatches" -> "Corus Taste Matches"
        "favorites" -> "Corus Favorites"
        else -> "Corus Feed"
    }

    /** Title + description for a TIDAL profile playlist. Mirrors the iOS strings;
     *  on the user's own profile we say "Your" rather than echoing their username. */
    @VisibleForTesting
    internal fun profilePlaylistNaming(
        username: String,
        source: CloudFunctionsDataSource.ProfilePlaylistSource,
        isOwnProfile: Boolean,
    ): Pair<String, String> {
        if (isOwnProfile) {
            return when (source) {
                CloudFunctionsDataSource.ProfilePlaylistSource.Likes -> "Corus · Your Likes" to "Songs you liked on Corus"
                CloudFunctionsDataSource.ProfilePlaylistSource.Saves -> "Corus · Your Saves" to "Songs you saved on Corus"
                CloudFunctionsDataSource.ProfilePlaylistSource.Posts -> "Corus · Your Profile" to "Songs from your Corus profile"
            }
        }
        return when (source) {
            CloudFunctionsDataSource.ProfilePlaylistSource.Likes -> "Corus · $username's Likes" to "Songs $username liked on Corus"
            CloudFunctionsDataSource.ProfilePlaylistSource.Saves -> "Corus · $username's Saves" to "Songs $username saved on Corus"
            CloudFunctionsDataSource.ProfilePlaylistSource.Posts -> "Corus · $username" to "Songs from $username's Corus profile"
        }
    }

    /** Title + description for a TIDAL hashtag playlist. Matches the backend's
     *  Spotify title ("Corus · #tag") so the two paths name playlists alike. */
    @VisibleForTesting
    internal fun hashtagPlaylistNaming(hashtag: String): Pair<String, String> =
        "Corus · #$hashtag" to "Songs tagged #$hashtag on Corus"

    val isPlaying: Boolean get() = _state.value.isPlaying
    val currentTrackId: String? get() = _state.value.trackId

    /** Play a track that's part of a queue — enables autoplay and the mini-player next button. */
    suspend fun play(track: QueuedTrack, queue: List<QueuedTrack>) {
        this.queue = queue
        this.currentQueueIndex = queue.indexOfActive(track.trackId, track.sourcePostId)
        // New playback context — drop any previous paginated-queue hook until caller re-wires it.
        this.queueHasMore = false
        this.loadMoreQueue = null
        playInternal(track)
    }

    /**
     * Sync the now-playing queue with a paginated feed.
     *
     * Callers (FeedViewModel, ProfileFeedViewModel) invoke this whenever the feed
     * list or its `hasMore` flag changes, and pass a `loadMore` that fetches the
     * next page. This keeps the mini-player's next button enabled — and functional —
     * when the user exhausts the currently-loaded page.
     */
    fun updateFeedQueue(
        newQueue: List<QueuedTrack>,
        hasMore: Boolean,
        loadMore: suspend () -> Unit,
    ) {
        val currentTrackId = _state.value.trackId
        // Don't clobber an unrelated now-playing context (e.g. track started from search).
        if (currentTrackId != null && newQueue.none { it.trackId == currentTrackId }) return
        queue = newQueue
        queueHasMore = hasMore
        loadMoreQueue = loadMore
        currentQueueIndex = newQueue.indexOfActive(currentTrackId, _state.value.sourcePostId)
        _state.value = _state.value.copy(hasNext = computeHasNext())
    }

    /**
     * Drop tracks posted by the given users from the in-memory queue.
     *
     * Called when the local user unfollows someone — without this the queue
     * keeps the unfollowed user's tracks even after their posts leave the
     * visible feed, so tapping "next" plays songs from a person they no
     * longer follow. If the *currently playing* track itself was posted by
     * one of those users, stop playback entirely; the user already signaled
     * they don't want this person's content.
     */
    fun removeFromQueue(userIds: Set<String>) {
        if (userIds.isEmpty() || queue.isEmpty()) return
        val oldQueue = queue
        val curIdx = currentQueueIndex
        val currentRemoved = curIdx != null &&
            curIdx in oldQueue.indices &&
            oldQueue[curIdx].posterUserId in userIds
        val pruned = oldQueue.filter { it.posterUserId == null || it.posterUserId !in userIds }
        if (pruned.size == oldQueue.size) return
        if (currentRemoved) {
            stop()
            return
        }
        queue = pruned
        currentQueueIndex = pruned.indexOfActive(_state.value.trackId, _state.value.sourcePostId)
        _state.value = _state.value.copy(hasNext = computeHasNext())
    }

    suspend fun play(
        trackId: String,
        trackName: String,
        artistName: String,
        albumArtURL: String?,
        albumArtLargeURL: String? = null,
        previewUrl: String?,
        spotifyURI: String? = null,
        spotifyWebURL: String? = null,
        isrc: String? = null,
        sourcePostId: String? = null,
        source: TrackSource = TrackSource.SPOTIFY,
        soundcloudId: String? = null,
        soundcloudPermalinkUrl: String? = null,
        audiomackUrl: String? = null,
    ) {
        // Single-track path: clear any queued context so hasNext is false.
        queue = emptyList()
        currentQueueIndex = null
        queueHasMore = false
        loadMoreQueue = null
        playInternal(
            QueuedTrack(
                trackId = trackId,
                trackName = trackName,
                artistName = artistName,
                albumArtURL = albumArtURL,
                albumArtLargeURL = albumArtLargeURL,
                previewUrl = previewUrl,
                spotifyURI = spotifyURI,
                spotifyWebURL = spotifyWebURL,
                isrc = isrc,
                sourcePostId = sourcePostId,
                source = source,
                soundcloudId = soundcloudId,
                soundcloudPermalinkUrl = soundcloudPermalinkUrl,
                audiomackUrl = audiomackUrl,
            ),
        )
    }

    private suspend fun playInternal(
        track: QueuedTrack,
        userInitiated: Boolean = true,
        forceSwitch: Boolean = false,
    ) {
        val trackId = track.trackId

        // Audio sources are mutually exclusive: starting music stops any inline
        // trailer and any playing audio caption so they never play over each other.
        TrailerPlaybackCoordinator.stopAll()
        VoiceNotePlayerManager.stopActivePlayer()

        // If the SAME post's track is already playing, toggle pause/play. A
        // *different* post of the same song (same trackId, different sourcePostId)
        // is a distinct feed card, so it falls through and switches playback to it
        // instead of pausing. See [isReTapOfActiveEntry] / [PostPlaybackHighlight].
        // Skipped when recovering from a failed Spotify Connect attempt that already
        // updated the mini-player state to this track.
        if (!forceSwitch &&
            player != null &&
            isReTapOfActiveEntry(_state.value.trackId, _state.value.sourcePostId, trackId, track.sourcePostId)
        ) {
            togglePlayPause()
            return
        }

        // If the SAME post's track is already loading, cancel the request. A
        // different post of the same song has its own in-flight load — let it through.
        if (isReTapOfActiveEntry(_loadingTrackId.value, _loadingSourcePostId.value, trackId, track.sourcePostId)) {
            cancelLoading()
            return
        }

        // Cancel any in-flight load for a different track
        cancelLoading()

        if (isSpotifyConnectPlaying) {
            silentlyHaltSpotifyForPreview()
        }

        // Signal loading state. Track id + the post it came from are kept in
        // lockstep so a feed card can tell its own load apart from a different
        // post of the same song (both share the track id).
        _loadingTrackId.value = trackId
        _loadingSourcePostId.value = track.sourcePostId
        val generation = ++playGeneration

        // Resolve playback URL.
        //   SoundCloud → fetch a fresh signed HLS URL (short-lived, never cached on the post).
        //   Spotify/Apple → use the 30s preview URL (looked up server-side via Apple Music).
        val resolvedUrl = when (track.source) {
            TrackSource.SOUNDCLOUD -> track.soundcloudId?.let { resolveSoundCloudStream(it) }
            // Audiomack: prefer denormalized previewUrl (stamped at search/
            // createPost). Fall back to resolveAudiomackPreview when missing —
            // covers legacy posts and refresh-on-fail. Never falls through to
            // the Apple-preview lookup (wrong track risk for an `amk:` id).
            TrackSource.AUDIOMACK -> {
                val denorm = track.previewUrl?.takeIf { it.isNotBlank() }
                if (denorm != null) denorm
                else audiomackIdFromTrackId(trackId)?.let { resolveAudiomackPreview(it) }
            }
            // TIDAL/Deezer exclusives are link-out only with no preview resolver.
            // Like Audiomack, they never fall through to the Apple-preview lookup
            // (a text-search match for a `tdl:`/`dzr:` id risks playing a WRONG
            // track). A doc-carried previewUrl still plays; otherwise null →
            // the unavailable toast, and the full song stays reachable via the
            // TIDAL/Deezer badge link-out.
            TrackSource.TIDAL, TrackSource.DEEZER -> track.previewUrl?.takeIf { it.isNotBlank() }
            else -> track.previewUrl?.takeIf { it.isNotBlank() }
                ?: previewCache[trackId]
                ?: lookupPreviewUrl(trackId, track.trackName, track.artistName, track.isrc)
        }

        // If cancelled while resolving, bail out
        if (generation != playGeneration) return

        _loadingTrackId.value = null
        _loadingSourcePostId.value = null

        if (resolvedUrl == null) {
            // No playable preview (e.g. a Spotify track with no Apple Music
            // match, or an Audiomack preview that failed to resolve). Tell the
            // user, but only on an explicit tap — auto-advance just stops rather
            // than spamming a toast per dead track. Audiomack additionally keeps
            // its "Listen on Audiomack" link-out for the full song.
            if (userInitiated) _previewUnavailable.tryEmit(Unit)
            return
        }

        // Cache for future taps
        previewCache[trackId] = resolvedUrl

        // New track — switch the persistent player+session to it.
        // We do NOT release & rebuild the MediaSession per track:
        // MediaSessionService binds its internal controller on first
        // attach, and a freshly-built session isn't auto-reattached, so
        // releasing kills the system media notification permanently.
        currentTrackIsSoundCloud = track.source == TrackSource.SOUNDCLOUD
        val mediaItem = MediaItem.Builder()
            .setUri(resolvedUrl)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(track.trackName)
                    .setArtist(track.artistName)
                    .setArtworkUri(track.albumArtURL?.let { Uri.parse(it) })
                    .build()
            )
            .build()
        val exo = ensurePlayerAndSession()
        exo.setMediaItem(mediaItem)
        exo.prepare()
        exo.play()
        // Promote the service only after the session is playing — otherwise
        // MediaSessionService can't post its notification fast enough and
        // Android kills us with ForegroundServiceDidNotStartInTimeException.
        startForegroundServiceIfNeeded()

        _state.value = NowPlayingState(
            trackId = trackId,
            trackName = track.trackName,
            artistName = track.artistName,
            albumArtURL = track.albumArtURL,
            albumArtLargeURL = track.albumArtLargeURL,
            spotifyURI = track.spotifyURI,
            spotifyWebURL = track.spotifyWebURL,
            isrc = track.isrc,
            isPlaying = true,
            sourcePostId = track.sourcePostId,
            hasNext = computeHasNext(),
            source = track.source,
            soundcloudPermalinkUrl = track.soundcloudPermalinkUrl,
            audiomackUrl = track.audiomackUrl,
            catalogOrigin = track.catalogOrigin,
        )

        // This corus is now playing in-app — report a unique play. Reached for
        // both initial taps and auto-advance (which also routes through here);
        // the same-track pause/resume path returns early above, so this won't
        // double-fire on resume.
        recordPlayIfNeeded(track.sourcePostId)
    }

    /**
     * Fire-and-forget: report a UNIQUE in-app play of [postId] to the backend.
     * Lenient — counts as soon as in-app playback starts. Only the `recordPlay`
     * callable mutates playCount; self-plays and repeat listeners are filtered
     * server-side. Never blocks playback or surfaces errors.
     */
    private fun recordPlayIfNeeded(postId: String?) {
        val id = postId ?: return
        if (!recordedPlayPostIds.add(id)) return // already reported this session
        managerScope.launch {
            try {
                cloudFunctions.recordPlay(id)
            } catch (e: Exception) {
                // Best-effort only. Drop the marker so a genuine play can retry
                // later this session; the backend stays idempotent regardless.
                recordedPlayPostIds.remove(id)
            }
        }
    }

    /**
     * Calls the `soundcloudResolveStream` Cloud Function. On 404 / blocked,
     * fans out a marker to mark all posts of this track as unavailable so
     * other clients render the post in a greyed-out state.
     */
    private suspend fun resolveSoundCloudStream(soundcloudId: String): String? {
        android.util.Log.i("NowPlaying", "resolveSoundCloudStream START id=$soundcloudId")
        return try {
            val data = withContext(Dispatchers.IO) {
                cloudFunctions.soundcloudResolveStream(soundcloudId)
            }
            val streamUrl = (data["streamUrl"] as? String)?.takeIf { it.isNotBlank() }
            android.util.Log.i("NowPlaying", "resolveSoundCloudStream OK id=$soundcloudId streamUrl=${streamUrl?.take(60)}…")
            streamUrl
        } catch (e: Exception) {
            android.util.Log.w("NowPlaying", "resolveSoundCloudStream FAILED id=$soundcloudId msg=${e.message}", e)
            // Best-effort marking of unavailability when the track has been
            // deleted, privatized, or geo-blocked. Non-fatal to playback path.
            val message = e.message?.lowercase().orEmpty()
            val reason = when {
                "not-found" in message || "not_found" in message -> "deleted"
                "failed-precondition" in message || "blocked" in message -> "blocked"
                else -> null
            }
            if (reason != null) {
                managerScope.launch {
                    runCatching { cloudFunctions.markSoundCloudUnavailable(soundcloudId, reason) }
                }
            }
            null
        }
    }

    /**
     * Calls the `resolveAudiomackPreview` Cloud Function for a signed ~30s
     * preview URL (streams audio/mp4 directly, playable by ExoPlayer as-is).
     * Prefer denormalized [Track.previewUrl] at play time; this is the
     * fallback for legacy posts and when the stamped URL is missing. Returns
     * null on any error so playback degrades gracefully; the UI still exposes
     * the "Listen on Audiomack" link-out for the full song. Mirrors
     * [resolveSoundCloudStream] and [lookupPreviewUrl].
     */
    private suspend fun resolveAudiomackPreview(audiomackId: String): String? {
        return try {
            val url = withContext(Dispatchers.IO) {
                cloudFunctions.resolveAudiomackPreview(audiomackId)
            }
            android.util.Log.i("NowPlaying", "resolveAudiomackPreview OK id=$audiomackId url=${url?.take(60)}…")
            url
        } catch (e: Exception) {
            android.util.Log.w("NowPlaying", "resolveAudiomackPreview FAILED id=$audiomackId msg=${e.message}", e)
            null
        }
    }

    /** Auto-advance to the next queued track when playback ends. */
    private fun handlePlaybackEnded() {
        skipToNext(preferPreviewOnNext = true)
    }

    fun skipToNext(preferPreviewOnNext: Boolean = false) {
        if (shouldRouteSpotifyFeedSkip(preferPreviewOnNext)) {
            cancelDebouncedSpotifyRelinquish()
            spotifyFeedSkipRequestedUntil = System.currentTimeMillis() + 5000
            armSpotifyFastPathSkipGuardForUpcomingFeedTrack()
            forceSpotifyFeedAdvanceToNextEntry()
            return
        }
        stopPositionPolling(resetClock = false)
        if (isSpotifyConnectPlaying) {
            resetScrubberPosition()
        } else {
            ScrubberClock.reset()
        }
        advanceToNext()
    }

    fun shouldRouteSpotifyFeedSkip(preferPreviewOnNext: Boolean = false): Boolean {
        if (!shouldChainFullPlaybackOnSkip(preferPreviewOnNext)) return false
        if (musicServicePreference.current.value != MusicService.SPOTIFY) return false
        if (!_state.value.hasActiveTrack) return false
        val idx = currentQueueIndex ?: return isSpotifyConnectPlaying
        val next = queue.getOrNull(idx + 1) ?: return isSpotifyConnectPlaying
        if (!SongPlayRouting.wantsSpotifyExperiment(
                next.source,
                MusicService.SPOTIFY,
                playFullSongs = preferencesDataStore.effectivePlayFullSongsSync(),
            )
        ) {
            return false
        }
        return isSpotifyConnectPlaying || spotifyExperimentEnabledForTrack(next.source)
    }

    fun forceSpotifyFeedAdvanceToNextEntry() {
        spotifyPlaybackService.pauseImmediately()
        val idx = currentQueueIndex ?: run { skipToNextLegacyPreview(); return }
        if (idx + 1 >= queue.size) {
            if (queueHasMore) {
                skipToNextLegacyPreview()
            }
            return
        }
        val next = queue[idx + 1]
        if (!spotifyExperimentEnabledForTrack(next.source)) {
            userInitiatedPause = false
            silentlyHaltSpotifyForPreview()
            manualSkipGuardUntil = System.currentTimeMillis() + 1500
            spotifyFeedSkipRequestedUntil = System.currentTimeMillis() + 5000
            cancelDebouncedSpotifyRelinquish()
            stopPositionPolling(resetClock = false)
            resetScrubberPosition()
            advanceToNext()
            return
        }
        userInitiatedPause = false
        manualSkipGuardUntil = System.currentTimeMillis() + 1500
        spotifyFeedSkipRequestedUntil = System.currentTimeMillis() + 5000
        cancelDebouncedSpotifyRelinquish()
        stopPositionPolling(resetClock = false)
        resetScrubberPosition()
        spotifyExpectedTrackUri = null
        spotifyQueueTransitionUntil = null
        val nextUri = spotifyURI(next)
        spotifyCorusRequestedUri = nextUri
        spotifyCorusRequestedUntil = System.currentTimeMillis() + 8000
        armSpotifyFastPathSkipGuard(expectedURI = nextUri)
        currentQueueIndex = idx + 1
        updateStateForTrack(next)
        managerScope.launch {
            playViaSpotifyConnect(spotifyPendingPlay(next), replaceSpotifyQueue = true)
        }
    }

    private fun skipToNextLegacyPreview() {
        stopPositionPolling(resetClock = false)
        ScrubberClock.reset()
        advanceToNext()
    }

    /**
     * Shared advance logic. If the next track is already in the queue, play it.
     * Otherwise, if the queue is backed by a paginated feed with more pages,
     * fetch the next page and advance once it arrives.
     */
    private fun advanceToNext() {
        val idx = currentQueueIndex ?: return
        val localNext = queue.getOrNull(idx + 1)
        if (localNext != null) {
            managerScope.launch {
                currentQueueIndex = idx + 1
                playInternal(localNext, userInitiated = false)
            }
            return
        }
        if (!queueHasMore) return
        val load = loadMoreQueue ?: return
        if (isLoadingMoreQueue) return
        managerScope.launch {
            isLoadingMoreQueue = true
            try {
                load()
            } catch (_: Exception) { }
            isLoadingMoreQueue = false
            val currentIdx = currentQueueIndex ?: return@launch
            val next = queue.getOrNull(currentIdx + 1) ?: return@launch
            currentQueueIndex = currentIdx + 1
            playInternal(next, userInitiated = false)
        }
    }

    /**
     * Lazily build the persistent ExoPlayer + ForwardingPlayer + MediaSession
     * and start the foreground service. All three live for the full playback
     * lifecycle and are only torn down in [stop]. Track changes reuse them
     * via [ExoPlayer.setMediaItem].
     */
    private fun ensurePlayerAndSession(): ExoPlayer {
        player?.let { return it }

        val exo = ExoPlayer.Builder(context)
            // Tell media3 to manage Android audio focus on our behalf: request it
            // on play, release on stop/pause, and auto-pause when another app
            // (e.g. Spotify) takes focus. Also auto-pause when the route becomes
            // noisy (headphones unplugged).
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .setUsage(C.USAGE_MEDIA)
                    .build(),
                /* handleAudioFocus = */ true,
            )
            .setHandleAudioBecomingNoisy(true)
            .build().apply {
            addListener(object : Player.Listener {
                override fun onPlaybackStateChanged(playbackState: Int) {
                    if (playbackState == Player.STATE_ENDED) {
                        _state.value = _state.value.copy(isPlaying = false)
                        stopPositionPolling(resetClock = false)
                        // Snap scrubber to 0 — same behavior as iOS song-end:
                        // line stays visible at fraction 0 if there's no
                        // auto-advance, and gets immediately overwritten by
                        // the new track's first poll if there is one.
                        ScrubberClock.setTime(0L)
                        handlePlaybackEnded()
                    } else if (playbackState == Player.STATE_READY) {
                        if (player?.playWhenReady == true) startPositionPolling()
                    }
                }

                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    if (isPlaying) startPositionPolling()
                    else stopPositionPolling(resetClock = false)
                }

                override fun onMediaItemTransition(
                    mediaItem: MediaItem?,
                    reason: Int,
                ) {
                    // Track change — snap clock to 0 so the scrubber doesn't
                    // briefly tween from the outgoing track's position into
                    // the new one's. Polling will refill it on the next tick.
                    ScrubberClock.reset()
                }
            })
        }
        // Redirect the system "next" command to our own queue (ExoPlayer
        // has no knowledge of the feed-backed queue) and gate the scrubber
        // per source via currentTrackIsSoundCloud.
        val sessionPlayer = object : ForwardingPlayer(exo) {
            override fun seekToNext() {
                val preferPreview = preferPreviewOnInAppSkip
                if (shouldRouteSpotifyFeedSkip(preferPreview)) {
                    cancelDebouncedSpotifyRelinquish()
                    spotifyFeedSkipRequestedUntil = System.currentTimeMillis() + 5000
                    armSpotifyFastPathSkipGuardForUpcomingFeedTrack()
                    this@NowPlayingManager.forceSpotifyFeedAdvanceToNextEntry()
                } else {
                    this@NowPlayingManager.skipToNext(preferPreviewOnNext = preferPreview)
                }
            }

            override fun seekToNextMediaItem() = seekToNext()

            override fun hasNextMediaItem(): Boolean = computeHasNext()

            override fun isPlaying(): Boolean {
                if (isSpotifyConnectPlaying) return spotifyPlaybackService.isPlaying.value
                return super.isPlaying()
            }

            override fun play() {
                if (isSpotifyConnectPlaying) {
                    managerScope.launch {
                        spotifyPlaybackService.resume()
                        _state.value = _state.value.copy(isPlaying = true)
                    }
                    return
                }
                super.play()
            }

            override fun pause() {
                if (isSpotifyConnectPlaying) {
                    userInitiatedPause = true
                    managerScope.launch {
                        spotifyPlaybackService.pause()
                        _state.value = _state.value.copy(isPlaying = false)
                    }
                    return
                }
                super.pause()
            }

            override fun getAvailableCommands(): Player.Commands {
                val builder = Player.Commands.Builder()
                    .addAll(super.getAvailableCommands())
                    .add(Player.COMMAND_SEEK_TO_NEXT)
                    .add(Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM)
                if (!currentTrackIsSoundCloud) {
                    builder.remove(Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM)
                }
                return builder.build()
            }
        }
        player = exo
        // setSessionActivity makes tapping the now-playing card (QS media player,
        // lock screen, Android Auto, media3's own notification provider) open
        // Corus instead of doing nothing.
        mediaSession = MediaSession.Builder(context, sessionPlayer)
            .setSessionActivity(appLaunchPendingIntent())
            .build()
        return exo
    }

    /**
     * PendingIntent that brings Corus to the foreground. Used as the media
     * session's tap target (QS media card / lock screen) and as the playback
     * notification's content intent. MainActivity is `singleTask`, so this
     * resumes the existing task rather than starting a fresh copy.
     */
    fun appLaunchPendingIntent(): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
        return PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    /**
     * Promote [CorusPlaybackService] to foreground. Must be called only after the
     * session's player has prepare()+play() running, so media3 can post its rich
     * media-style notification within Android's 5s ANR window. Calling this with
     * an idle session causes a `ForegroundServiceDidNotStartInTimeException`.
     */
    private fun startForegroundServiceIfNeeded() {
        if (foregroundServiceStarted) return
        ContextCompat.startForegroundService(
            context,
            Intent(context, CorusPlaybackService::class.java),
        )
        foregroundServiceStarted = true
    }

    /**
     * Called by [CorusPlaybackService] when Android refused to promote the
     * service to the foreground (ForegroundServiceStartNotAllowedException on
     * API 31+) — i.e. playback was started while the app was backgrounded. We
     * can't keep audio running without the foreground service, so pause and
     * clear [foregroundServiceStarted] so the next foreground play() retries the
     * promotion cleanly. Reached on the main thread from onStartCommand, the
     * same thread that owns the player, so no dispatch is needed.
     */
    fun onForegroundStartDenied() {
        foregroundServiceStarted = false
        player?.pause()
        _state.value = _state.value.copy(isPlaying = false)
    }

    /**
     * Resolve an Apple Music preview URL for a Spotify track. The Cloud Function
     * `appleMusicLookup` is the sole source of truth: it runs Apple's authed
     * catalog ISRC lookup, then a suffix-stripped text search, with our full
     * matching ranker (artist tokens, variant stripping, duration/album
     * tie-breakers, distinctive-token filters). It also caches resolutions
     * globally in Firestore so repeat lookups of any track ever posted are
     * fast.
     *
     * We used to fall back to the iTunes Search API on-device. That produced
     * wrong matches (e.g. "Tomorrow Never Knows - Remastered 2009" → the Take 1
     * outtake) because iTunes ranks keyword hits and has no variant filtering.
     * All matching now lives server-side for parity with iOS.
     */
    @VisibleForTesting
    internal suspend fun lookupPreviewUrl(
        trackId: String,
        name: String,
        artist: String,
        isrc: String?,
    ): String? {
        if (noMatchCache.contains(trackId)) return null

        val url = try {
            withContext(Dispatchers.IO) {
                cloudFunctions.appleMusicLookup(name, artist, isrc, trackId)
            }
        } catch (_: Exception) {
            // Network/transient error — surface as "no preview" same as today.
            // Don't poison `noMatchCache` so the next tap gets a fresh attempt.
            return null
        }

        if (url == null) {
            noMatchCache.add(trackId)
            return null
        }
        return url
    }

    fun cancelLoading() {
        playGeneration++
        _loadingTrackId.value = null
        _loadingSourcePostId.value = null
        if (_isResolvingSpotify.value) {
            _isResolvingSpotify.value = false
            spotifyConnectPlayJob?.cancel()
            spotifyPlaybackService.cancelPendingPlayRequest()
        }
    }

    fun togglePlayPause() {
        // Paused session in the wrong mode (e.g. Always Full flipped while paused):
        // restart into the desired engine instead of silently resuming the old one.
        if (shouldRestartPausedSessionForDesiredPlaybackMode()) {
            restartCurrentTrackForDesiredPlaybackMode()
            return
        }
        if (isExternalSpotifyListening) {
            managerScope.launch {
                val svc = spotifyPlaybackService
                if (_state.value.isPlaying) {
                    externalSpotifyUserPaused = true
                    _state.value = _state.value.copy(isPlaying = false)
                    if (!svc.isConnected) svc.trySilentReconnectIfNeeded()
                    delay(300)
                    svc.pause()
                } else {
                    externalSpotifyUserPaused = false
                    _state.value = _state.value.copy(isPlaying = true)
                    if (!svc.isConnected) svc.trySilentReconnectIfNeeded()
                    delay(300)
                    svc.resume()
                }
            }
            return
        }
        if (isSpotifyConnectPlaying) {
            managerScope.launch {
                if (spotifyPlaybackService.isPlaying.value) {
                    userInitiatedPause = true
                    spotifyPlaybackService.pause()
                    _state.value = _state.value.copy(isPlaying = false)
                } else {
                    userInitiatedPause = false
                    spotifyPlaybackService.resume()
                    _state.value = _state.value.copy(isPlaying = true)
                }
            }
            return
        }
        val p = player ?: return
        if (p.isPlaying) {
            p.pause()
            _state.value = _state.value.copy(isPlaying = false)
        } else {
            if (p.playbackState == Player.STATE_ENDED) {
                p.seekTo(0)
            }
            // Resuming the song silences any active audio caption.
            VoiceNotePlayerManager.stopActivePlayer()
            p.play()
            _state.value = _state.value.copy(isPlaying = true)
        }
    }

    fun stop() {
        clearExternalSpotifyListening()
        if (isSpotifyConnectPlaying) {
            silentlyHaltSpotifyForPreview()
        }
        stopPositionPolling(resetClock = true)
        mediaSession?.release()
        mediaSession = null
        player?.release()
        player = null
        if (foregroundServiceStarted) {
            context.stopService(Intent(context, CorusPlaybackService::class.java))
            foregroundServiceStarted = false
        }
        queue = emptyList()
        currentQueueIndex = null
        queueHasMore = false
        loadMoreQueue = null
        _state.value = NowPlayingState()
    }

    fun dismiss() {
        stop()
    }

    fun pause() {
        if (isExternalSpotifyListening) {
            externalSpotifyUserPaused = true
            managerScope.launch { spotifyPlaybackService.pause() }
            _state.value = _state.value.copy(isPlaying = false)
            return
        }
        if (isSpotifyConnectPlaying) {
            managerScope.launch { spotifyPlaybackService.pause() }
            _state.value = _state.value.copy(isPlaying = false)
            return
        }
        player?.pause()
        _state.value = _state.value.copy(isPlaying = false)
    }

    fun resume() {
        if (isExternalSpotifyListening) {
            externalSpotifyUserPaused = false
            VoiceNotePlayerManager.stopActivePlayer()
            managerScope.launch { spotifyPlaybackService.resume() }
            _state.value = _state.value.copy(isPlaying = true)
            return
        }
        if (isSpotifyConnectPlaying) {
            VoiceNotePlayerManager.stopActivePlayer()
            managerScope.launch { spotifyPlaybackService.resume() }
            _state.value = _state.value.copy(isPlaying = true)
            return
        }
        VoiceNotePlayerManager.stopActivePlayer()
        player?.play()
        _state.value = _state.value.copy(isPlaying = true)
    }

    suspend fun playViaSpotifyConnect(
        pending: SpotifyAuthPendingPlay,
        replaceSpotifyQueue: Boolean = false,
    ) {
        clearExternalSpotifyListening()
        clearSpotifyHandoffFailureSuppression()
        if (!SpotifyPlaybackService.isSpotifyAppInstalled(context)) {
            handleSpotifyPlaybackFailure(queuedTrackFrom(pending), userInitiated = true)
            return
        }

        val prefs = context.getSharedPreferences("corus_prefs", Context.MODE_PRIVATE)
        val hasPriorSession = spotifyAuthService.cachedAccessToken() != null ||
            prefs.getLong("fm.corus.spotify.lastAppRemoteUsage", 0L) > 0
        val wasActive = isSpotifyConnectPlaying || spotifyPlaybackService.isConnected || hasPriorSession

        spotifyConnectPlayJob?.cancel()
        spotifyPlaybackService.cancelPendingPlayRequest()
        spotifyUriToQueueIndex.clear()
        spotifyQueueTransitionUntil = System.currentTimeMillis() + 4000
        spotifyPlaybackService.onPlayerTrackChanged = null
        spotifyPlaybackService.onTrackEnded = null
        spotifyPlaybackService.onPlayerContextUpdated = null

        spotifyConnectPlayGeneration += 1
        val generation = spotifyConnectPlayGeneration

        val previousSourcePostId = _state.value.sourcePostId
        val previousTrackId = _state.value.trackId
        val isFeedTrackSwitch = previousSourcePostId != pending.sourcePostId ||
            (previousTrackId != null && previousTrackId != pending.trackId)
        if (isFeedTrackSwitch || wasActive || ScrubberClock.time.value > 0L) {
            beginSpotifyScrubberHoldAtZero(spotifyPlaybackService.currentTrackUri.value)
        }

        _isResolvingSpotify.value = true

        _state.value = _state.value.copy(
            trackId = pending.trackId,
            trackName = pending.name,
            artistName = pending.artist,
            albumArtURL = pending.albumArtURL,
            albumArtLargeURL = pending.albumArtLargeURL,
            sourcePostId = pending.sourcePostId,
            source = pending.source,
            spotifyURI = pending.spotifyURI,
            spotifyWebURL = pending.spotifyWebURL,
            isrc = pending.isrc,
            hasNext = computeHasNext(),
            isPlaying = false,
        )
        currentQueueIndex = resolveQueueIndex(pending.trackId, pending.sourcePostId)
        spotifyConnectStartedAt = System.currentTimeMillis()
        if (!wasActive) spotifyConnectWasPlaying = false
        player?.pause()

        val expectedTrackId = pending.trackId
        val replaceQueue = replaceSpotifyQueue || !wasActive || isFeedTrackSwitch
        val queueSession = ++spotifyQueueSessionId
        var resolvedUri: String? = null

        try {
            val track = queuedTrackFrom(pending)
            resolvedUri = resolveSpotifyPlaybackURI(track)
            if (resolvedUri == null) {
                if (_state.value.trackId == pending.trackId) {
                    handleSpotifyPlaybackFailure(track, userInitiated = true)
                }
                return
            }

            spotifyExpectedTrackUri = resolvedUri
            spotifyCorusRequestedUri = resolvedUri
            spotifyCorusRequestedUntil = System.currentTimeMillis() + 8000
            armSpotifyFastPathSkipGuard(expectedURI = resolvedUri)

            spotifyPlaybackService.play(
                spotifyTrackId = pending.trackId,
                uri = resolvedUri,
                replaceQueue = replaceQueue,
                queueSessionId = queueSession,
            )
        } catch (e: Exception) {
            android.util.Log.w("NowPlaying", "Spotify play failed: ${e.message}")
            if (_state.value.trackId == expectedTrackId) {
                handleSpotifyPlaybackFailure(
                    queuedTrackFrom(pending),
                    userInitiated = true,
                    mayHaveStartedAudio = true,
                )
            }
            return
        }

        if (_state.value.trackId != expectedTrackId) {
            _isResolvingSpotify.value = false
            return
        }

        installSpotifyConnectDelegates()
        spotifyUriToQueueIndex[resolvedUri!!] = currentQueueIndex ?: 0
        spotifyExpectedTrackUri = null
        spotifyQueueTransitionUntil = null

        isSpotifyConnectPlaying = true
        spotifyConnectStartedAt = System.currentTimeMillis()
        spotifyConnectWasPlaying = true
        syncSpotifyScrubAnchor(spotifyPlaybackService.positionSeconds.value)
        _state.value = _state.value.copy(isPlaying = true)
        userInitiatedPause = false
        startForegroundServiceIfNeeded()
        startSpotifyConnectTimePolling()

        val svc = spotifyPlaybackService
        if (!svc.isPlaying.value && svc.isConnected) {
            svc.resume()
        }

        if (!verifySpotifyConnectPlaybackStarted(resolvedUri!!) && _state.value.trackId == expectedTrackId) {
            android.util.Log.w("SpotifyPlayback", "Connect verify timed out — falling back to preview")
            silentlyHaltSpotifyForPreview()
            spotifyExpectedTrackUri = null
            spotifyQueueTransitionUntil = null
            handleSpotifyPlaybackFailure(
                queuedTrackFrom(pending),
                userInitiated = true,
                mayHaveStartedAudio = true,
            )
            return
        }
        clearSpotifyHandoffFailureSuppression()
        _isResolvingSpotify.value = false
        refreshSpotifyFastPathSkipGuardWhenLocked()
    }

    private fun installSpotifyConnectDelegates() {
        clearExternalSpotifyListening()
        spotifyPlaybackService.onTrackEnded = { handleSpotifyConnectTrackEnded() }
        spotifyPlaybackService.onPlayerTrackChanged = { uri -> reconcileSpotifyQueuePosition(uri) }
        spotifyPlaybackService.onPlayerContextUpdated = {
            spotifyPlaybackService.currentTrackUri.value?.let { uri ->
                reconcileSpotifyQueuePosition(uri)
            }
        }
        spotifyPlaybackService.onPlayerStateUpdated = {
            if (isSpotifyConnectPlaying &&
                !spotifyScrubberHoldAtZero &&
                !userInitiatedPause &&
                spotifyScrubberShouldAdvance()
            ) {
                syncSpotifyScrubAnchor(spotifyPlaybackService.positionSeconds.value)
            }
        }
    }

    private suspend fun verifySpotifyConnectPlaybackStarted(expectedUri: String? = null): Boolean {
        repeat(50) { tick ->
            if (spotifyPlaybackService.isPlaying.value) return true
            if (expectedUri != null) {
                val current = spotifyPlaybackService.currentTrackUri.value
                if (current != null && spotifyUriMatchesExpected(current, expectedUri) && tick >= 5) {
                    if (!spotifyPlaybackService.isPlaying.value && spotifyPlaybackService.isConnected) {
                        spotifyPlaybackService.resume()
                    }
                    if (spotifyPlaybackService.isPlaying.value) return true
                }
            }
            if (tick % 3 == 0) spotifyPlaybackService.refreshState()
            delay(100)
        }
        return false
    }

    private fun spotifyUriMatchesExpected(uri: String, expectedUri: String): Boolean {
        if (uri == expectedUri) return true
        fun trackIdFrom(uri: String): String? =
            uri.removePrefix("spotify:track:").takeIf { uri.startsWith("spotify:track:") }
        val reported = trackIdFrom(uri) ?: return false
        val expected = trackIdFrom(expectedUri) ?: return false
        return reported == expected
    }

    private suspend fun handleSpotifyPlaybackFailure(
        track: QueuedTrack,
        userInitiated: Boolean,
        mayHaveStartedAudio: Boolean = false,
    ) {
        android.util.Log.w("SpotifyPlayback", "Falling back to 30s preview for ${track.trackId}")
        _isResolvingSpotify.value = false
        isSpotifyConnectPlaying = false
        // authorizeAndPlay can leave Spotify audible without a live App Remote
        // session — stop() alone won't pause it. Only force-pause when we may
        // have reached that handoff (not URI lookup / install failures).
        val failedUri = spotifyCorusRequestedUri ?: spotifyExpectedTrackUri
        val shouldForcePause = failedUri != null || mayHaveStartedAudio
        if (shouldForcePause) {
            markSpotifyHandoffFailure(failedUri ?: track.spotifyURI)
            spotifyPlaybackService.forcePauseAfterFailedHandoff()
        }
        silentlyHaltSpotifyForPreview()
        playInternal(track, userInitiated = userInitiated, forceSwitch = true)
    }

    fun silentlyHaltSpotifyForPreview() {
        spotifyConnectPlayJob?.cancel()
        cancelDebouncedSpotifyRelinquish()
        spotifyPlaybackService.onPlayerTrackChanged = null
        spotifyPlaybackService.onTrackEnded = null
        spotifyPlaybackService.onPlayerStateUpdated = null
        spotifyPlaybackService.onPlayerContextUpdated = null
        if (isSpotifyConnectPlaying || spotifyPlaybackService.isConnected) {
            spotifyPlaybackService.stop()
        }
        isSpotifyConnectPlaying = false
        _isResolvingSpotify.value = false
        spotifyConnectStartedAt = null
        spotifyConnectWasPlaying = false
        userInitiatedPause = false
        resetSpotifyScrubAnchor()
        pauseSpotifyConnectTimePolling()
        spotifyUriToQueueIndex.clear()
        spotifyExpectedTrackUri = null
        spotifyQueueTransitionUntil = null
        spotifyCorusRequestedUri = null
        spotifyCorusRequestedUntil = null
        spotifyPendingExternalUri = null
        spotifyScrubberHoldAtZero = false
        spotifyScrubberHoldUntilTrackChangeFromUri = null
    }

    private fun reconcileSpotifyQueuePosition(uri: String) {
        if (!isSpotifyConnectPlaying || _isResolvingSpotify.value) return
        if (shouldIgnoreSpotifyReconcileDuringCorusHandoff(uri)) return
        // A podcast / audiobook / local file is proof the user picked it in the
        // Spotify app — Corus has no such content to skip to or reclaim. Release
        // before any of the misrouted-skip heuristics below can force-advance the
        // feed over it. Checked after the handoff guard so starting a Corus song
        // *while* a podcast plays still works.
        if (SpotifyContentUri.isUserChosenNonCorusContent(uri)) {
            cancelDebouncedSpotifyRelinquish()
            relinquishSpotifyToExternalPlayback(uri)
            return
        }
        manualSkipGuardUntil?.let { if (System.currentTimeMillis() < it) return }
        if (spotifyOutgoingChangeWasNaturalFeedTrackEnd()) {
            handleSpotifyNaturalFeedTrackEnd(uri)
            return
        }

        val idx = currentQueueIndex ?: run {
            reclaimSpotifyQueueAfterExternalSkip(uri)
            return
        }
        val current = queue.getOrNull(idx) ?: run {
            reclaimSpotifyQueueAfterExternalSkip(uri)
            return
        }
        if (spotifyURIMatchesTrack(uri, current)) return
        if (idx + 1 >= queue.size) {
            reclaimSpotifyQueueAfterExternalSkip(uri)
            return
        }
        val nextTrack = queue[idx + 1]
        if (spotifyURIMatchesTrack(uri, nextTrack)) {
            advanceSpotifyToQueueIndex(idx + 1)
            return
        }
        reclaimSpotifyQueueAfterExternalSkip(uri)
    }

    private fun reclaimSpotifyQueueAfterExternalSkip(reporting: String) {
        cancelDebouncedSpotifyRelinquish()

        // Ahead of the natural-end check: a song ending does not entitle Corus to
        // play over the podcast the user started in the meantime.
        if (SpotifyContentUri.isUserChosenNonCorusContent(reporting)) {
            relinquishSpotifyToExternalPlayback(reporting)
            return
        }

        if (spotifyOutgoingChangeWasNaturalFeedTrackEnd()) return

        if (shouldRelinquishExternalSpotifyPlayback(reporting)) {
            relinquishSpotifyToExternalPlayback(reporting)
            return
        }

        spotifyInferMisroutedLockScreenSkipIfNeeded(reporting)

        val idx = currentQueueIndex
        if (idx != null && idx + 1 < queue.size && spotifyURIMatchesTrack(reporting, queue[idx + 1])) {
            advanceSpotifyToQueueIndex(idx + 1)
            return
        }

        if (spotifyFeedSkipRequestedUntil?.let { System.currentTimeMillis() < it } == true) {
            if (!computeHasNext()) return
            forceSpotifyFeedAdvanceToNextEntry()
            return
        }
        if (spotifyCorusPlayIntentInFlight()) {
            return
        }
        if (shouldForceSpotifyFeedAdvanceForMisroutedSkip(reporting)) {
            forceSpotifyFeedAdvanceToNextEntry()
            return
        }
        if (shouldRelinquishForManualSpotifyPlayback(reporting)) {
            relinquishSpotifyToExternalPlayback(reporting)
            return
        }
        if (shouldRelinquishBecauseCorusPausedAndExternalTrack(reporting)) {
            relinquishSpotifyToExternalPlayback(reporting)
            return
        }
        if (!computeHasNext()) return
        scheduleDebouncedSpotifyExternalPlaybackDecision(reporting)
    }

    private fun handleSpotifyConnectTrackEnded() {
        if (!isSpotifyConnectPlaying) return
        // The song ended, but Spotify is already on a podcast the user started —
        // hand the session over instead of advancing the feed on top of it.
        val reporting = spotifyPlaybackService.currentTrackUri.value
        if (SpotifyContentUri.isUserChosenNonCorusContent(reporting)) {
            relinquishSpotifyToExternalPlayback(reporting)
            return
        }
        // Mute stale Spotify queue auto-advance immediately — the 400ms debounce
        // below is only for coalescing duplicate end signals, not for waiting.
        spotifyPlaybackService.pauseImmediately()
        spotifyFeedSkipRequestedUntil = System.currentTimeMillis() + 5000
        spotifyNaturalEndAdvanceJob?.cancel()
        spotifyNaturalEndAdvanceJob = managerScope.launch {
            delay(200)
            if (!isSpotifyConnectPlaying) return@launch
            if (computeHasNext()) {
                forceSpotifyFeedAdvanceToNextEntry()
            } else {
                _state.value = _state.value.copy(isPlaying = false)
                ScrubberClock.reset()
            }
        }
    }

    /** Spotify often auto-advances without pausing — onTrackEnded never fires; reconcile must drive advance. */
    private fun handleSpotifyNaturalFeedTrackEnd(reporting: String) {
        val idx = currentQueueIndex
        if (idx != null && idx + 1 < queue.size && spotifyURIMatchesTrack(reporting, queue[idx + 1])) {
            advanceSpotifyToQueueIndex(idx + 1)
        } else {
            handleSpotifyConnectTrackEnded()
        }
    }

    private fun spotifyCorusPlayIntentInFlight(): Boolean {
        val until = spotifyCorusRequestedUntil ?: return false
        if (System.currentTimeMillis() >= until) return false
        return _isResolvingSpotify.value || spotifyConnectPlayJob?.isActive == true
    }

    /**
     * During a Corus-initiated play handoff, Spotify often reports stale queue
     * state (e.g. the next feed entry) before the requested track starts. Ignore
     * those callbacks unless the user explicitly skipped.
     */
    private fun shouldIgnoreSpotifyReconcileDuringCorusHandoff(uri: String): Boolean {
        if (spotifyFeedSkipRequestedUntil?.let { System.currentTimeMillis() < it } == true) {
            return false
        }
        if (!spotifyCorusPlayIntentInFlight()) return false
        if (spotifyCorusRecentlyRequested(uri)) return true
        val idx = currentQueueIndex
        if (idx != null && idx < queue.size && spotifyURIMatchesTrack(uri, queue[idx])) {
            return true
        }
        return true
    }

    private fun corusAppIsBackgrounded(): Boolean =
        !ProcessLifecycleOwner.get().lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)

    private fun corusAppIsInactiveLike(): Boolean {
        val state = ProcessLifecycleOwner.get().lifecycle.currentState
        return state.isAtLeast(Lifecycle.State.STARTED) &&
            !state.isAtLeast(Lifecycle.State.RESUMED)
    }

    /** User started playing outside the Corus feed in Spotify — release App Remote without pausing Spotify, then mirror in the mini player. */
    fun relinquishSpotifyToExternalPlayback(reporting: String? = null) {
        if (!isSpotifyConnectPlaying) return
        val externalUri = reporting ?: spotifyPlaybackService.currentTrackUri.value
        // User (or true external) takeover — don't keep suppressing adoption.
        clearSpotifyHandoffFailureSuppression()
        spotifyPlaybackService.clearFastPathPlaybackGuard()
        spotifyConnectPlayJob?.cancel()
        cancelDebouncedSpotifyRelinquish()
        spotifyPlaybackService.onPlayerTrackChanged = null
        spotifyPlaybackService.onTrackEnded = null
        spotifyPlaybackService.onPlayerStateUpdated = null
        spotifyPlaybackService.onPlayerContextUpdated = null
        spotifyUriToQueueIndex.clear()
        spotifyExpectedTrackUri = null
        spotifyQueueTransitionUntil = null
        spotifyCorusRequestedUri = null
        spotifyCorusRequestedUntil = null
        spotifyPendingExternalUri = null
        isSpotifyConnectPlaying = false
        spotifyConnectStartedAt = null
        spotifyConnectWasPlaying = false
        userInitiatedPause = false
        externalSpotifyUserPaused = false
        _isResolvingSpotify.value = false
        resetSpotifyScrubAnchor()
        pauseSpotifyConnectTimePolling()
        spotifyScrubberHoldAtZero = false
        spotifyScrubberHoldUntilTrackChangeFromUri = null

        // Only songs can be mirrored in the mini player — a podcast has no Corus
        // song page, so just stop showing Corus as playing and leave Spotify alone.
        if (!externalUri.isNullOrEmpty() &&
            SpotifyContentUri.kindOf(externalUri) == SpotifyContentKind.TRACK
        ) {
            managerScope.launch { adoptExternalSpotifyPlayback(externalUri) }
        } else {
            _state.value = _state.value.copy(isPlaying = false)
        }
    }

    private fun clearExternalSpotifyListening() {
        if (!isExternalSpotifyListening) return
        externalSpotifyCachedTrack = null
        externalSpotifyTrackURI = null
        externalSpotifyUserPaused = false
        _isExternalSpotifyListening.value = false
        _isHydratingExternalSpotify.value = false
        spotifyScrubberHoldAtZero = false
        spotifyScrubberHoldUntilTrackChangeFromUri = null
        pauseExternalSpotifyTimePolling()
        if (!isSpotifyConnectPlaying) {
            spotifyPlaybackService.onPlayerTrackChanged = null
            spotifyPlaybackService.onPlayerStateUpdated = null
            spotifyPlaybackService.onPlayerContextUpdated = null
        }
    }

    /** Hydrate and show whatever Spotify is playing — every Spotify song has a Corus song page. */
    private suspend fun adoptExternalSpotifyPlayback(spotifyURI: String) {
        val trackId = spotifyURI.removePrefix("spotify:track:")
            .takeIf { spotifyURI.startsWith("spotify:track:") } ?: return

        if (shouldSuppressExternalAdoption(spotifyURI)) {
            spotifyPlaybackService.forcePauseAfterFailedHandoff()
            return
        }

        // External Spotify and in-app preview must never share ScrubberClock.
        stopPositionPolling(resetClock = false)
        player?.pause()

        val svc = spotifyPlaybackService
        if (svc.isConnected) {
            svc.refreshState()
        }

        val normalizedIncoming = SpotifyPlaybackService.normalizedSpotifyTrackId(trackId)
        val normalizedCurrent = _state.value.trackId?.let { SpotifyPlaybackService.normalizedSpotifyTrackId(it) }

        if (isExternalSpotifyListening && normalizedCurrent == normalizedIncoming) {
            _state.value = _state.value.copy(isPlaying = svc.isPlaying.value)
            syncExternalSpotifyScrubber()
            return
        }

        clearSpotifyHandoffFailureSuppression()
        beginSpotifyScrubberHoldAtZero(externalSpotifyTrackURI ?: svc.currentTrackUri.value)
        _isHydratingExternalSpotify.value = true
        try {
            val appRemoteMeta = svc.appRemoteDisplayMetadata()
            var track = withContext(Dispatchers.IO) {
                runCatching { spotifyRepository.getTrack(trackId) }.getOrNull()
            }
            if (track == null) {
                val meta = appRemoteMeta ?: svc.appRemoteDisplayMetadata()
                if (meta != null) {
                    val durationMs = maxOf(0, (svc.durationSeconds.value * 1000).toInt())
                    track = CymbalTrack(
                        id = trackId,
                        name = meta.name,
                        artistName = meta.artistName,
                        albumName = meta.albumName,
                        spotifyURI = spotifyURI,
                        spotifyWebURL = "https://open.spotify.com/track/$trackId",
                        durationMs = durationMs,
                    )
                }
            }
            if (track == null) return

            externalSpotifyCachedTrack = track
            externalSpotifyTrackURI = spotifyURI
            _isExternalSpotifyListening.value = true
            isSpotifyConnectPlaying = false
            currentQueueIndex = null

            _state.value = NowPlayingState(
                trackId = track.id,
                trackName = track.name,
                artistName = track.artistName,
                albumArtURL = track.albumArtURL,
                albumArtLargeURL = track.albumArtLargeURL,
                spotifyURI = track.spotifyURI,
                spotifyWebURL = track.spotifyWebURL,
                isrc = track.isrc,
                isPlaying = svc.isPlaying.value,
                source = TrackSource.SPOTIFY,
                hasNext = false,
            )
            syncSpotifyScrubAnchor(svc.positionSeconds.value)
            syncExternalSpotifyScrubber()
            installExternalSpotifyDelegates()
            startExternalSpotifyTimePolling()
        } finally {
            _isHydratingExternalSpotify.value = false
        }
    }

    private suspend fun reconcileExternalSpotifyOnForeground() {
        if (spotifyCorusPlayIntentInFlight()) return

        val svc = spotifyPlaybackService
        if (svc.isConnected) {
            svc.refreshState()
        } else if (spotifyAuthService.cachedAccessToken() != null) {
            svc.trySilentReconnectIfNeeded()
            delay(500)
            if (svc.isConnected) {
                svc.refreshState()
            }
        }

        val uri = svc.currentTrackUri.value?.takeIf { it.isNotEmpty() } ?: run {
            if (isExternalSpotifyListening) {
                _state.value = _state.value.copy(isPlaying = false)
            }
            return
        }

        if (shouldSuppressExternalAdoption(uri)) {
            svc.forcePauseAfterFailedHandoff()
            return
        }

        if (isSpotifyConnectPlaying) {
            if (shouldRelinquishExternalSpotifyPlayback(uri)) {
                relinquishSpotifyToExternalPlayback(uri)
            }
            return
        }

        if (svc.isPlaying.value || isExternalSpotifyListening) {
            adoptExternalSpotifyPlayback(uri)
        } else if (isExternalSpotifyListening) {
            _state.value = _state.value.copy(isPlaying = false)
        }
    }

    private fun shouldSuppressExternalAdoption(uri: String): Boolean =
        SpotifyHandoffRecovery.shouldSuppressExternalAdoption(
            reportedUri = uri,
            failedHandoffUri = spotifyFailedHandoffUri,
            suppressUntilMs = spotifyFailedHandoffSuppressExternalUntilMs,
        )

    private fun markSpotifyHandoffFailure(uri: String?) {
        if (uri.isNullOrEmpty()) return
        spotifyFailedHandoffUri = uri
        spotifyFailedHandoffSuppressExternalUntilMs =
            System.currentTimeMillis() + SpotifyHandoffRecovery.EXTERNAL_ADOPTION_SUPPRESS_MS
    }

    private fun clearSpotifyHandoffFailureSuppression() {
        spotifyFailedHandoffUri = null
        spotifyFailedHandoffSuppressExternalUntilMs = null
    }

    private fun installExternalSpotifyDelegates() {
        spotifyPlaybackService.clearFastPathPlaybackGuard()
        spotifyPlaybackService.onTrackEnded = null
        spotifyPlaybackService.onPlayerTrackChanged = { uri ->
            managerScope.launch { adoptExternalSpotifyPlayback(uri) }
        }
        spotifyPlaybackService.onPlayerStateUpdated = {
            syncExternalSpotifyPlaybackState()
        }
        spotifyPlaybackService.onPlayerContextUpdated = null
    }

    private fun syncExternalSpotifyPlaybackState() {
        if (!isExternalSpotifyListening) return
        val svc = spotifyPlaybackService
        if (externalSpotifyUserPaused) {
            if (svc.isPlaying.value) {
                externalSpotifyUserPaused = false
                _state.value = _state.value.copy(isPlaying = true)
            } else {
                _state.value = _state.value.copy(isPlaying = false)
            }
        } else {
            _state.value = _state.value.copy(isPlaying = svc.isPlaying.value)
        }
    }

    private fun syncExternalSpotifyScrubber() {
        var durationMs = (spotifyPlaybackService.durationSeconds.value * 1000).toLong()
        if (durationMs <= 0) {
            externalSpotifyCachedTrack?.durationMs?.takeIf { it > 0 }?.let { durationMs = it.toLong() }
        }
        publishSpotifyScrubberTime(durationMs)
    }

    private fun startExternalSpotifyTimePolling() {
        externalSpotifyPositionJob?.cancel()
        externalSpotifyPositionJob = managerScope.launch {
            refreshExternalSpotifyTime()
            while (isActive) {
                delay(500)
                refreshExternalSpotifyTime()
            }
        }
    }

    private fun pauseExternalSpotifyTimePolling() {
        externalSpotifyPositionJob?.cancel()
        externalSpotifyPositionJob = null
    }

    private fun refreshExternalSpotifyTime() {
        if (!isExternalSpotifyListening) return
        syncExternalSpotifyPlaybackState()
        syncExternalSpotifyScrubber()
    }

    private fun spotifyCorusRecentlyRequested(uri: String): Boolean {
        val requested = spotifyCorusRequestedUri ?: return false
        val until = spotifyCorusRequestedUntil ?: return false
        if (System.currentTimeMillis() >= until) return false
        if (uri == requested) return true
        val reportingId = uri.removePrefix("spotify:track:").takeIf { uri.startsWith("spotify:track:") }
            ?: return false
        val requestedId = requested.removePrefix("spotify:track:").takeIf { requested.startsWith("spotify:track:") }
            ?: return false
        return reportingId == requestedId
    }

    private fun spotifyPlaybackWasCorusInitiated(reporting: String): Boolean {
        if (spotifyCorusRecentlyRequested(reporting)) return true
        val expected = spotifyExpectedTrackUri
        val until = spotifyQueueTransitionUntil
        if (expected != null && until != null && System.currentTimeMillis() < until) {
            if (reporting == expected) return true
            val reportingId = reporting.removePrefix("spotify:track:").takeIf { reporting.startsWith("spotify:track:") }
            val expectedId = expected.removePrefix("spotify:track:").takeIf { expected.startsWith("spotify:track:") }
            if (reportingId != null && expectedId != null && reportingId == expectedId) return true
        }
        val idx = currentQueueIndex
        if (idx != null && idx < queue.size && spotifyURIMatchesTrack(reporting, queue[idx])) return true
        if (spotifyFeedSkipRequestedUntil?.let { System.currentTimeMillis() < it } == true) {
            if (idx != null && idx + 1 < queue.size && spotifyURIMatchesTrack(reporting, queue[idx + 1])) {
                return true
            }
        }
        return false
    }

    private fun spotifyReportingMatchesNextFeedEntry(uri: String): Boolean {
        val idx = currentQueueIndex ?: return false
        if (idx + 1 >= queue.size) return false
        return spotifyURIMatchesTrack(uri, queue[idx + 1])
    }

    private fun spotifyInferMisroutedLockScreenSkipIfNeeded(reporting: String) {
        if (!isSpotifyConnectPlaying) return
        if (spotifyFeedSkipRequestedUntil?.let { System.currentTimeMillis() < it } == true) return
        if (!corusAppIsBackgrounded()) return
        if (spotifyOutgoingChangeWasNaturalFeedTrackEnd()) return
        val idx = currentQueueIndex ?: return
        val current = queue.getOrNull(idx) ?: return
        if (spotifyURIMatchesTrack(reporting, current)) return

        val svc = spotifyPlaybackService
        val previous = svc.lastOutgoingContextUri
        val currentContext = svc.incomingContextUri ?: svc.currentContextUri
        val contextChanged =
            !previous.isNullOrEmpty() && !currentContext.isNullOrEmpty() && previous != currentContext
        if (contextChanged && !spotifyReportingMatchesNextFeedEntry(reporting)) return

        if (svc.incomingPlaybackPosition >= 3.0 ||
            svc.lastOutgoingPlaybackPosition <= 5.0 ||
            svc.lastOutgoingDuration <= 0 ||
            svc.lastOutgoingPlaybackPosition >= svc.lastOutgoingDuration - 2.0
        ) {
            return
        }

        spotifyFeedSkipRequestedUntil = System.currentTimeMillis() + 5000
    }

    private fun spotifyOutgoingChangeWasNaturalFeedTrackEnd(): Boolean {
        val idx = currentQueueIndex ?: return false
        val current = queue.getOrNull(idx) ?: return false
        val outgoing = spotifyPlaybackService.lastOutgoingTrackUri ?: return false
        if (!spotifyURIMatchesTrack(outgoing, current)) return false
        val duration = spotifyPlaybackService.lastOutgoingDuration
        if (duration <= 0) return false
        return spotifyPlaybackService.lastOutgoingPlaybackPosition >= duration - 1.5
    }

    private fun spotifyURIExistsInCorusQueue(uri: String): Boolean =
        queue.any { spotifyURIMatchesTrack(uri, it) }

    /** Corus intentionally paused Spotify Connect — don't auto-advance the feed. */
    private fun corusSpotifySessionSuspended(): Boolean {
        if (!isSpotifyConnectPlaying) return false
        return userInitiatedPause || !_state.value.isPlaying
    }

    /** User paused from Corus, then started a different track in Spotify. */
    private fun shouldRelinquishBecauseCorusPausedAndExternalTrack(reporting: String): Boolean {
        if (!isSpotifyConnectPlaying) return false
        if (spotifyFeedSkipRequestedUntil?.let { System.currentTimeMillis() < it } == true) return false
        if (spotifyCorusPlayIntentInFlight()) return false
        if (!corusSpotifySessionSuspended()) return false
        val idx = currentQueueIndex ?: return false
        val current = queue.getOrNull(idx) ?: return false
        return !spotifyURIMatchesTrack(reporting, current)
    }

    /** Spotify is playing outside the Corus feed — mirror in the mini player, don't force-advance. */
    private fun shouldRelinquishExternalSpotifyPlayback(reporting: String): Boolean {
        if (!isSpotifyConnectPlaying) return false
        if (spotifyCorusPlayIntentInFlight()) return false
        // Corus can never have started a podcast / audiobook / local file, so no
        // feed-skip or lock-screen heuristic below applies — it is the user's own pick.
        if (SpotifyContentUri.isUserChosenNonCorusContent(reporting)) return true
        if (spotifyReportingMatchesNextFeedEntry(reporting)) return false
        if (spotifyPlaybackWasCorusInitiated(reporting)) return false
        if (spotifyFeedSkipRequestedUntil?.let { System.currentTimeMillis() < it } == true) return false
        if (spotifyCorusPlayIntentInFlight()) return false
        if (shouldRelinquishForManualSpotifyPlayback(reporting)) return true
        if (shouldRelinquishBecauseCorusPausedAndExternalTrack(reporting)) return true
        if (corusAppIsBackgrounded()) return false
        if (_state.value.isPlaying) return false
        val idx = currentQueueIndex
        if (idx == null || idx >= queue.size) {
            return !spotifyURIExistsInCorusQueue(reporting)
        }
        return !spotifyURIMatchesTrack(reporting, queue[idx])
    }

    private fun shouldRelinquishForManualSpotifyPlayback(reporting: String): Boolean {
        if (!isSpotifyConnectPlaying) return false
        // Non-track content is unambiguous: only the user can have started it, from
        // anywhere — Spotify app, lock screen, Bluetooth, Android Auto. No context
        // change needed to prove it (Android gets context on a later callback, so
        // the check below is blind at track-change time), and it holds while locked.
        if (SpotifyContentUri.isUserChosenNonCorusContent(reporting)) return true
        if (spotifyReportingMatchesNextFeedEntry(reporting)) return false
        if (spotifyPlaybackWasCorusInitiated(reporting)) return false
        if (spotifyFeedSkipRequestedUntil?.let { System.currentTimeMillis() < it } == true) return false
        if (spotifyDeviceLockedForQueueDriving) return false
        if (!corusAppIsBackgrounded()) return false
        if (spotifyOutgoingChangeWasNaturalFeedTrackEnd()) return false

        // A context change is the one reliable manual-pick signal: tapping a song
        // in Spotify sets an album/playlist/search context, while misrouted
        // lock-screen skips keep the context Corus started. "URI not in queue"
        // alone must NOT relinquish — stale Spotify queue entries from misrouted
        // skips also aren't in the Corus queue.
        val svc = spotifyPlaybackService
        val previous = svc.lastOutgoingContextUri
        val current = svc.incomingContextUri ?: svc.currentContextUri
        return !previous.isNullOrEmpty() && !current.isNullOrEmpty() && previous != current
    }

    private fun shouldForceSpotifyFeedAdvanceForMisroutedSkip(reporting: String): Boolean {
        if (!computeHasNext() || !isSpotifyConnectPlaying) return false
        // Never force the feed over a podcast / audiobook — it is not a skip that
        // misrouted to Spotify, it is what the user asked Spotify to play.
        if (SpotifyContentUri.isUserChosenNonCorusContent(reporting)) return false
        if (spotifyPlaybackService.isAwaitingIncomingContext()) return false
        if (shouldRelinquishExternalSpotifyPlayback(reporting)) return false
        if (shouldRelinquishForManualSpotifyPlayback(reporting)) return false
        if (spotifyFeedSkipRequestedUntil?.let { System.currentTimeMillis() < it } == true) return true
        if (spotifyCorusPlayIntentInFlight()) return false
        if (spotifyDeviceLockedForQueueDriving) return true
        if (corusAppIsInactiveLike()) return true
        if (!spotifyURIExistsInCorusQueue(reporting)) return true
        val idx = currentQueueIndex ?: return false
        if (idx + 1 >= queue.size) return false
        return !spotifyURIMatchesTrack(reporting, queue[idx + 1])
    }

    private fun scheduleDebouncedSpotifyExternalPlaybackDecision(externalUri: String) {
        cancelDebouncedSpotifyRelinquish()
        spotifyPendingExternalUri = externalUri
        spotifyRelinquishJob = managerScope.launch {
            delay(600)
            if (!isSpotifyConnectPlaying) return@launch
            if (spotifyCorusPlayIntentInFlight()) return@launch
            if (shouldRelinquishForManualSpotifyPlayback(externalUri)) {
                relinquishSpotifyToExternalPlayback(externalUri)
                return@launch
            }
            if (shouldRelinquishBecauseCorusPausedAndExternalTrack(externalUri)) {
                relinquishSpotifyToExternalPlayback(externalUri)
                return@launch
            }
            if (corusSpotifySessionSuspended()) return@launch
            if (computeHasNext()) {
                forceSpotifyFeedAdvanceToNextEntry()
            }
        }
    }

    private fun cancelDebouncedSpotifyRelinquish() {
        spotifyRelinquishJob?.cancel()
        spotifyRelinquishJob = null
        spotifyPendingExternalUri = null
    }

    private fun advanceSpotifyToQueueIndex(index: Int) {
        val track = queue.getOrNull(index) ?: return
        resetScrubberPosition()
        currentQueueIndex = index
        updateStateForTrack(track)
        _state.value = _state.value.copy(isPlaying = spotifyPlaybackService.isPlaying.value)
        if (!spotifyScrubberHoldAtZero) {
            syncSpotifyScrubAnchor(spotifyPlaybackService.positionSeconds.value)
        }
    }

    private fun updateStateForTrack(track: QueuedTrack) {
        _state.value = _state.value.copy(
            trackId = track.trackId,
            trackName = track.trackName,
            artistName = track.artistName,
            albumArtURL = track.albumArtURL,
            albumArtLargeURL = track.albumArtLargeURL,
            sourcePostId = track.sourcePostId,
            source = track.source,
            spotifyURI = track.spotifyURI,
            spotifyWebURL = track.spotifyWebURL,
            isrc = track.isrc,
            soundcloudPermalinkUrl = track.soundcloudPermalinkUrl,
            audiomackUrl = track.audiomackUrl,
            hasNext = computeHasNext(),
        )
    }

    private suspend fun resolveSpotifyPlaybackURI(track: QueuedTrack): String? {
        track.spotifyURI?.takeIf { it.startsWith("spotify:track:") }?.let { return it }
        spotifyPlaybackUriCache[track.trackId]?.let { return it }
        val normalized = SpotifyPlaybackService.normalizedSpotifyTrackId(track.trackId)
        if (normalized.length == 22 &&
            !track.trackId.startsWith("am:") &&
            !track.trackId.startsWith("sc:") &&
            !track.trackId.startsWith("amk:")
        ) {
            val uri = "spotify:track:$normalized"
            spotifyPlaybackUriCache[track.trackId] = uri
            return uri
        }
        if (spotifyAbsentTrackIds.contains(track.trackId)) return null
        return try {
            val result = cloudFunctions.spotifyTrackLookup(
                name = track.trackName,
                artist = track.artistName,
                isrc = track.isrc,
                appleTrackId = track.trackId,
            )
            if (result.found) {
                val uri = result.spotifyUri?.takeIf { it.startsWith("spotify:track:") }
                    ?: result.webUrl?.let { web ->
                        Regex("track/([a-zA-Z0-9]+)").find(web)?.groupValues?.get(1)
                            ?.let { id -> "spotify:track:$id" }
                    }
                if (uri != null) {
                    spotifyPlaybackUriCache[track.trackId] = uri
                    uri
                } else {
                    spotifyAbsentTrackIds.add(track.trackId)
                    null
                }
            } else {
                spotifyAbsentTrackIds.add(track.trackId)
                null
            }
        } catch (e: Exception) {
            android.util.Log.w("NowPlaying", "spotifyTrackLookup failed: ${e.message}")
            null
        }
    }

    private fun spotifyPendingPlay(track: QueuedTrack) = SpotifyAuthPendingPlay(
        trackId = track.trackId,
        name = track.trackName,
        artist = track.artistName,
        isrc = track.isrc,
        albumArtURL = track.albumArtURL,
        albumArtLargeURL = track.albumArtLargeURL,
        spotifyWebURL = track.spotifyWebURL,
        spotifyURI = track.spotifyURI,
        sourcePostId = track.sourcePostId,
        source = track.source,
    )

    private fun queuedTrackFrom(pending: SpotifyAuthPendingPlay) = QueuedTrack(
        trackId = pending.trackId,
        trackName = pending.name,
        artistName = pending.artist,
        albumArtURL = pending.albumArtURL,
        albumArtLargeURL = pending.albumArtLargeURL,
        previewUrl = null,
        spotifyURI = pending.spotifyURI,
        spotifyWebURL = pending.spotifyWebURL,
        isrc = pending.isrc,
        sourcePostId = pending.sourcePostId,
        source = pending.source,
    )

    private fun resolveQueueIndex(trackId: String, sourcePostId: String?): Int? =
        queue.indexOfActive(trackId, sourcePostId)

    private fun spotifyURI(track: QueuedTrack): String =
        track.spotifyURI ?: "spotify:track:${SpotifyPlaybackService.normalizedSpotifyTrackId(track.trackId)}"

    /** Arm Spotify's delegate fast-path before async reconcile (mirrors iOS). */
    private fun armSpotifyFastPathSkipGuard(expectedURI: String, durationMs: Long = 8_000L) {
        spotifyPlaybackService.setFastPathPlaybackGuard(
            expectedURI = expectedURI,
            fromCurrentURI = spotifyPlaybackService.currentTrackUri.value,
            durationMs = durationMs,
        )
    }

    private fun armSpotifyFastPathSkipGuardForUpcomingFeedTrack(durationMs: Long = 8_000L) {
        val idx = currentQueueIndex ?: return
        val next = queue.getOrNull(idx + 1) ?: return
        if (!spotifyExperimentEnabledForTrack(next.source)) return
        armSpotifyFastPathSkipGuard(expectedURI = spotifyURI(next), durationMs = durationMs)
    }

    /** Pre-arm while locked/backgrounded so a native Spotify skip is silenced early. */
    private fun refreshSpotifyFastPathSkipGuardWhenLocked() {
        if (!spotifyDeviceLockedForQueueDriving && !corusAppIsBackgrounded()) return
        if (!isSpotifyConnectPlaying || !computeHasNext()) return
        armSpotifyFastPathSkipGuardForUpcomingFeedTrack(durationMs = 6_000L)
    }

    private fun spotifyURIMatchesTrack(uri: String, track: QueuedTrack): Boolean {
        if (uri == spotifyURI(track)) return true
        spotifyPlaybackUriCache[track.trackId]?.let { if (uri == it) return true }
        val reportingId = uri.removePrefix("spotify:track:").takeIf { uri.startsWith("spotify:track:") }
            ?: return false
        return SpotifyPlaybackService.normalizedSpotifyTrackId(track.trackId) == reportingId
    }

    private fun startSpotifyConnectTimePolling() {
        spotifyPositionJob?.cancel()
        spotifyPositionJob = managerScope.launch {
            refreshSpotifyConnectTime()
            while (isActive) {
                delay(500)
                refreshSpotifyConnectTime()
            }
        }
    }

    private fun pauseSpotifyConnectTimePolling() {
        spotifyPositionJob?.cancel()
        spotifyPositionJob = null
    }

    private fun refreshSpotifyConnectTime() {
        if (!isSpotifyConnectPlaying) return
        val durationSec = spotifyPlaybackService.durationSeconds.value
        val durationMs = if (durationSec > 0) (durationSec * 1000).toLong()
        else ScrubberClock.duration.value
        publishSpotifyScrubberTime(durationMs)
    }

    private fun publishSpotifyScrubberTime(durationMs: Long) {
        if (spotifyScrubberHoldAtZero) {
            if (shouldReleaseSpotifyScrubberHoldAtZero()) {
                spotifyScrubberHoldAtZero = false
                spotifyScrubberHoldUntilTrackChangeFromUri = null
                val positionSec = spotifyPlaybackService.positionSeconds.value
                syncSpotifyScrubAnchor(positionSec)
                val timeMs = (interpolatedSpotifyPosition() * 1000).toLong()
                if (spotifyScrubberShouldAdvance()) {
                    ScrubberClock.snapTime(timeMs)
                    if (durationMs > 0 && ScrubberClock.duration.value != durationMs) {
                        ScrubberClock.update(timeMs, durationMs)
                    }
                } else if (durationMs > 0 && ScrubberClock.duration.value != durationMs) {
                    ScrubberClock.update(ScrubberClock.time.value, durationMs)
                }
            } else {
                if (ScrubberClock.time.value != 0L) {
                    ScrubberClock.snapTime(0)
                } else if (durationMs > 0 && ScrubberClock.duration.value != durationMs) {
                    ScrubberClock.update(time = 0, duration = durationMs)
                }
            }
            return
        }
        val timeSec = interpolatedSpotifyPosition()
        if (spotifyScrubberShouldAdvance()) {
            ScrubberClock.update((timeSec * 1000).toLong(), durationMs)
        } else if (durationMs > 0 && ScrubberClock.duration.value != durationMs) {
            ScrubberClock.update(ScrubberClock.time.value, durationMs)
        }
    }

    private fun shouldReleaseSpotifyScrubberHoldAtZero(): Boolean {
        if (!spotifyPlaybackService.isPlaying.value || !_state.value.isPlaying) return false
        val holdFrom = spotifyScrubberHoldUntilTrackChangeFromUri
        if (holdFrom != null) {
            val current = spotifyPlaybackService.currentTrackUri.value ?: return false
            if (current == holdFrom) return false
        }
        // Spotify often updates track URI before position resets after skip/next.
        if (spotifyPlaybackService.positionSeconds.value > spotifyScrubberHoldMaxReleasePositionSec) {
            return false
        }
        return true
    }

    private fun spotifyScrubberShouldAdvance(): Boolean {
        if (spotifyScrubberHoldAtZero) return false
        if (isExternalSpotifyListening) {
            if (externalSpotifyUserPaused) return false
            return spotifyPlaybackService.isPlaying.value || _state.value.isPlaying
        }
        if (isSpotifyConnectPlaying && corusSpotifySessionSuspended()) return false
        if (isSpotifyConnectPlaying && userInitiatedPause) return false
        return spotifyPlaybackService.isPlaying.value ||
            (_state.value.isPlaying && isSpotifyConnectPlaying)
    }

    private fun resetScrubberPosition() {
        if (isSpotifyConnectPlaying || isExternalSpotifyListening) {
            beginSpotifyScrubberHoldAtZero(spotifyPlaybackService.currentTrackUri.value)
        } else {
            ScrubberClock.snapTime(0)
        }
    }

    private fun beginSpotifyScrubberHoldAtZero(fromPreviousTrackUri: String?) {
        ScrubberClock.snapTime(0)
        resetSpotifyScrubAnchor()
        spotifyScrubberHoldAtZero = true
        spotifyScrubberHoldUntilTrackChangeFromUri = fromPreviousTrackUri
    }

    private fun resetSpotifyScrubAnchor() {
        spotifyScrubAnchorWallTime = null
        spotifyScrubAnchorPosition = 0.0
    }

    private fun syncSpotifyScrubAnchor(positionSec: Double) {
        spotifyScrubAnchorPosition = maxOf(0.0, positionSec)
        spotifyScrubAnchorWallTime = System.currentTimeMillis()
    }

    private fun interpolatedSpotifyPosition(): Double {
        if (spotifyScrubberHoldAtZero) return 0.0
        val reported = spotifyPlaybackService.positionSeconds.value
        if (!spotifyScrubberShouldAdvance()) {
            spotifyScrubAnchorWallTime = null
            spotifyScrubAnchorPosition = reported
            return reported
        }
        val anchorTime = spotifyScrubAnchorWallTime ?: run {
            syncSpotifyScrubAnchor(reported)
            return reported
        }
        val elapsed = (System.currentTimeMillis() - anchorTime) / 1000.0
        var time = spotifyScrubAnchorPosition + elapsed
        val duration = spotifyPlaybackService.durationSeconds.value
        if (duration > 0) time = minOf(time, duration)
        return maxOf(0.0, time)
    }

}
