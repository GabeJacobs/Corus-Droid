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
import androidx.annotation.OptIn
import androidx.annotation.VisibleForTesting
import androidx.core.content.ContextCompat
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.SilenceMediaSource
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

internal fun bandcampIdFromTrackId(trackId: String): String? =
    trackId.takeIf { it.startsWith("bc:") }?.removePrefix("bc:")?.takeIf { it.isNotEmpty() }

/**
 * iOS Previous: restart current when >3s in (or no prior queue item); otherwise
 * skip to the previous entry. [positionMs] is Android ScrubberClock time.
 */
internal fun shouldRestartInsteadOfSkipPrevious(positionMs: Long, queueIndex: Int?): Boolean {
    if (positionMs > 3_000L) return true
    if (queueIndex == null || queueIndex <= 0) return true
    return false
}

/** Index just after Now Playing and any contiguous user-queued block. */
internal fun userQueueInsertionIndex(queue: List<QueuedTrack>, currentIndex: Int?): Int {
    val idx = currentIndex ?: return queue.size
    var insertAt = idx + 1
    while (insertAt < queue.size && queue[insertAt].isUserQueued) {
        insertAt++
    }
    return insertAt
}

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
/**
 * Artwork published to the system media session (lock screen, Control Center,
 * Samsung QS media card). Those surfaces upscale the image to a full-bleed
 * background, so the 64px feed thumbnail looks pixelated.
 */
internal fun sessionArtworkUrl(albumArtURL: String?, albumArtLargeURL: String?): String? =
    albumArtLargeURL?.takeIf { it.isNotBlank() } ?: albumArtURL?.takeIf { it.isNotBlank() }

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
    val bandcampUrl: String? = null,
    /** Where this track was played from, when it came from an artist/album page.
     *  See [CatalogPlaybackOrigin]. */
    val catalogOrigin: CatalogPlaybackOrigin? = null,
    /**
     * Explicitly added via “Add to Queue”. These sit after Now Playing (and
     * any other user-queued tracks) and play before auto/context Up Next.
     * Mirrors iOS `QueuedTrack.isUserQueued`.
     */
    val isUserQueued: Boolean = false,
    val appleMusicId: String? = null,
    val durationMs: Int? = null,
    val albumName: String? = null,
    val appleMusicStorefront: String? = null,
    val notOnSpotify: Boolean = false,
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
    bandcampUrl = bandcampUrl,
    catalogOrigin = origin,
    appleMusicId = appleMusicId,
    durationMs = durationMs.takeIf { it > 0 },
    albumName = albumName,
    appleMusicStorefront = appleMusicStorefront,
    notOnSpotify = notOnSpotify,
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
    bandcampUrl = track.bandcampUrl,
    appleMusicId = track.appleMusicId,
    durationMs = track.durationMs.takeIf { it > 0 },
    albumName = track.albumName,
    appleMusicStorefront = track.appleMusicStorefront,
    notOnSpotify = track.notOnSpotify,
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
    val bandcampUrl: String? = null,
    /** Set when the playing track came from an artist/album page; drives the
     *  mini-player "return to origin" tap. See [CatalogPlaybackOrigin]. */
    val catalogOrigin: CatalogPlaybackOrigin? = null,
    val notOnSpotify: Boolean = false,
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
    private val hapticManager: HapticManager,
    private val analyticsService: fm.corus.android.service.AnalyticsService,
) {
    companion object {
        /**
         * DEBUG: hide FeedPlayingPill on album art (and skip staging it on Next)
         * to A/B whether pill snap-in is hitching feed autoscroll vs playback work.
         * Mirrors iOS `NowPlayingManager.debugDisableAlbumArtPlayingPill`.
         */
        const val debugDisableAlbumArtPlayingPill = false

        /**
         * Pre-pill quiet after scroll kickoff. Tap stays free of pause/Connect.
         * 0 lagged again; settled on 150ms.
         */
        private const val FEED_SKIP_SCROLL_QUIET_MS = 150L
        /** Preview mini Next: extra quiet vs full-song 150ms (chrome+play hitch). */
        private const val FEED_SKIP_SCROLL_QUIET_PREVIEW_MS = 350L
        private const val FEED_SKIP_PILL_LEAD_IN_MS = 220L
        /** Preview path: longer quiet after arming pill so snap finishes before chrome/AV. */
        private const val FEED_SKIP_PILL_LEAD_IN_PREVIEW_MS = 280L
        private const val FEED_SKIP_AUDIO_AFTER_CHROME_MS = 200L
    }

    private val managerScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    /** Cancels a pending mini Next lead-in Task when the user taps Next again. */
    private var feedSkipLeadInGeneration = 0
    /** True while staged mini Next is sleeping toward play (pause must abort). */
    private var stagedFeedSkipInFlight = false

    /**
     * Mini/full-player transport spinner from Next tap until audio is live.
     * Independent of [stagedFeedSkipInFlight] (which drops before play starts)
     * so chrome doesn't flash pause→play mid-handoff. Mirrors iOS
     * `NowPlayingManager.stagedFeedSkipLoading`.
     */
    private val _stagedFeedSkipLoading = MutableStateFlow(false)
    val stagedFeedSkipLoading: StateFlow<Boolean> = _stagedFeedSkipLoading.asStateFlow()

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

    private fun setSpotifyDeviceLockedForQueueDriving(locked: Boolean) {
        spotifyDeviceLockedForQueueDriving = locked
        spotifyPlaybackService.playExpectedOnMisroute =
            SpotifyConnectFastPath.shouldPlayExpectedOnMisroute(locked)
    }

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
                    Lifecycle.Event.ON_PAUSE -> {
                        spotifyPlaybackService.playQueueNextOnUnexpected = isSpotifyConnectPlaying
                    }
                    Lifecycle.Event.ON_RESUME -> {
                        spotifyPlaybackService.playQueueNextOnUnexpected = false
                        val keyguard = context.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
                        setSpotifyDeviceLockedForQueueDriving(keyguard.isKeyguardLocked)
                    }
                    Lifecycle.Event.ON_STOP -> {
                        spotifyCorusBackgroundedAt = System.currentTimeMillis()
                        spotifyPlaybackService.playQueueNextOnUnexpected = isSpotifyConnectPlaying
                        refreshSpotifyFastPathSkipGuardWhenLocked()
                        startSpotifyConnectKeepAlive()
                    }
                    Lifecycle.Event.ON_START -> {
                        spotifyCorusBackgroundedAt = null
                        managerScope.launch {
                            reconcileExternalSpotifyOnForeground()
                            if (!isSpotifyConnectPlaying && !spotifyCorusPlayIntentInFlight()) {
                                spotifyPlaybackService.trySilentReconnectIfNeeded()
                            }
                        }
                    }
                    else -> Unit
                }
            },
        )
        val keyguard = context.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
        setSpotifyDeviceLockedForQueueDriving(keyguard.isKeyguardLocked)
        val lockReceiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context?, intent: Intent?) {
                val locked = when (intent?.action) {
                    Intent.ACTION_SCREEN_OFF -> {
                        refreshSpotifyFastPathSkipGuardWhenLocked()
                        startSpotifyConnectKeepAlive()
                        true
                    }
                    Intent.ACTION_USER_PRESENT -> false
                    else -> keyguard.isKeyguardLocked
                }
                setSpotifyDeviceLockedForQueueDriving(locked)
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
            ContextCompat.RECEIVER_EXPORTED,
        )
    }

    // ── Spotify Connect (auth experiment) ──────────────────────────────────

    @Volatile
    var isSpotifyConnectPlaying: Boolean = false
        private set

    /**
     * Inaudible looping ExoPlayer while Spotify Connect is active — Android's
     * analog of iOS's silent WAV keep-alive. Keeps the media FGS + session up
     * so lock-screen Next and natural-end handlers still belong to Corus.
     */
    @Volatile
    private var spotifyConnectKeepAliveActive = false
    @VisibleForTesting
    internal var skipConnectKeepAlive = false

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
            if (isExternalSpotifyListening) return
            if (isPlaying && isPreviewMode) {
                upgradeCurrentPreviewToFullSong()
            } else {
                restartCurrentTrackForDesiredPlaybackMode()
            }
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
                bandcampUrl = _state.value.bandcampUrl,
            )
        }
        val q = queue.ifEmpty { listOf(track) }
        this.queue = q
        this.currentQueueIndex = q.indexOfActive(track.trackId, track.sourcePostId)
        // Don't skip the prompt or force preferFullSong — Settings Always Full
        // defers the A link sheet to this play tap.
        routePlayTap(
            track = track.toCymbalTrack(),
            sourcePostId = track.sourcePostId,
            queue = q,
            preferFullSong = false,
            skipPlaybackModePrompt = false,
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
        bandcampUrl = bandcampUrl,
        notOnSpotify = notOnSpotify,
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
    /** Requested URI has been observed as current — later next-URI is a real skip. */
    private var spotifyRequestedTrackConfirmed = false
    private var spotifyRequestedTrackConfirmedAtMs: Long = 0L
    private var spotifyPendingExternalUri: String? = null
    /** URI of a Connect handoff that failed after Spotify may already be audible. */
    private var spotifyFailedHandoffUri: String? = null
    private var spotifyFailedHandoffSuppressExternalUntilMs: Long? = null
    private var spotifyRelinquishJob: Job? = null
    private var spotifyNaturalEndAdvanceJob: Job? = null
    /** External URI we force-advanced over while away; roll back if context later says pick. */
    private var speculativeAwaySkipUri: String? = null
    private var speculativeAwaySkipUntilMs: Long = 0L
    /** Unexpected Spotify URI we muted while waiting to tell Control Center Next from a Liked Song. */
    private var awaySkipMutedUri: String? = null
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
    /**
     * After a user scrub, ignore stale App Remote position callbacks that still
     * report the pre-seek time (they were yanking the scrubber back on release).
     * Mirrors iOS `spotifyScrubSeekInFlightUntil`.
     */
    private var spotifyScrubSeekInFlightUntilMs: Long? = null
    private var spotifyScrubSeekTargetSec: Double? = null
    private var spotifyPositionJob: Job? = null
    private var spotifySeekJob: Job? = null

    fun spotifyExperimentEnabledForTrack(
        source: TrackSource,
        preferFullSong: Boolean = false,
        trackId: String? = null,
        spotifyURI: String? = null,
        knownNotOnSpotify: Boolean = false,
    ): Boolean {
        if (!SpotifyPlaybackService.isSpotifyAppInstalled(context)) return false
        val playFull = preferFullSong || preferencesDataStore.effectivePlayFullSongsSync()
        return SongPlayRouting.wantsSpotifyExperiment(
            source = source,
            service = musicServicePreference.current.value,
            playFullSongs = playFull,
            trackId = trackId,
            spotifyURI = spotifyURI,
            knownNotOnSpotify = knownNotOnSpotify,
        )
    }

    fun spotifyExperimentEnabledForTrack(
        track: QueuedTrack,
        preferFullSong: Boolean = false,
    ): Boolean = spotifyExperimentEnabledForTrack(
        source = track.source,
        preferFullSong = preferFullSong,
        trackId = track.trackId,
        spotifyURI = track.spotifyURI,
        knownNotOnSpotify = track.notOnSpotify,
    )

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

    /**
     * Mini-player / lock-screen Next honor Play Full Songs — not whether the
     * *current* track is ExoPlayer chrome. SoundCloud / Audiomack always set
     * [isPreviewMode] (ExoPlayer), which previously forced preview-chaining
     * even when the next catalog track should go full Spotify Connect.
     */
    val preferPreviewOnInAppSkip: Boolean
        get() = !preferencesDataStore.effectivePlayFullSongsSync()

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
        // Explicit play seed — take the caller's order (same as iOS `playingTrackId`).
        queueOrderPinnedByUser = false
        val preserved = snapshotUserQueuedUpNext()
        this.queue = queue
        currentQueueIndex = queue.indexOfActive(playingTrackId, playingSourcePostId)
        restoreUserQueuedUpNext(preserved)
        publishHasNextIfChanged()
    }

    /**
     * Spotify-style manual queue: insert after Now Playing (and after any
     * already user-queued tracks). Plays before the auto/context Up Next.
     * If nothing is playing, starts playback with this track.
     * Mirrors iOS `NowPlayingManager.addToUserQueue`.
     */
    fun addToUserQueue(track: QueuedTrack): Boolean {
        if (track.source == TrackSource.TIDAL || track.source == TrackSource.DEEZER) {
            return false
        }
        var item = track.copy(isUserQueued = true)
        if (!_state.value.hasActiveTrack) {
            item = item.copy(isUserQueued = false)
            managerScope.launch { play(item, listOf(item)) }
            return true
        }
        val insertAt = userQueueInsertionIndex(queue, currentQueueIndex)
        val mutable = queue.toMutableList()
        mutable.add(insertAt, item)
        queue = mutable
        // Pin like Edit→reorder: feed sync only preserves `isUserQueued` rows.
        // Without this, once the added song starts, pagination rebuilds Up Next
        // from feed order and skips tracks that were already queued after it
        // (Add One Fine Day → Next → Next plays Forever instead of Baby…).
        queueOrderPinnedByUser = true
        publishHasNextIfChanged()
        // iOS addToUserQueue — light when inserting into an active session.
        hapticManager.impact(HapticManager.ImpactStyle.LIGHT)
        analyticsService.logAddToQueueTapped(item.trackId, item.sourcePostId)
        return true
    }

    /** User-queued tracks still waiting after the current song. */
    private fun snapshotUserQueuedUpNext(): List<QueuedTrack> {
        val idx = currentQueueIndex ?: return queue.filter { it.isUserQueued }
        if (idx + 1 >= queue.size) return emptyList()
        return queue.subList(idx + 1, queue.size).filter { it.isUserQueued }
    }

    /** Re-insert preserved user-queue items after Now Playing. */
    private fun restoreUserQueuedUpNext(items: List<QueuedTrack>) {
        if (items.isEmpty()) return
        val insertAt = userQueueInsertionIndex(queue, currentQueueIndex)
        val existingKeys = queue.filter { it.isUserQueued }.map { userQueueKey(it) }.toSet()
        val unique = items.filter { userQueueKey(it) !in existingKeys }
        if (unique.isEmpty()) return
        val mutable = queue.toMutableList()
        mutable.addAll(insertAt, unique)
        queue = mutable
    }

    private fun userQueueKey(track: QueuedTrack): String =
        "${track.trackId}|${track.sourcePostId.orEmpty()}"

    /**
     * Append feed tracks that aren't already in the pinned queue. Does not
     * reshuffle existing rows (preserves Edit → reorder / Add to Queue).
     */
    private fun appendNewTracksPreservingUserOrder(from: List<QueuedTrack>) {
        val seen = queue.map { userQueueKey(it) }.toMutableSet()
        val tail = ArrayList<QueuedTrack>()
        for (track in from) {
            val key = userQueueKey(track)
            if (!seen.add(key)) continue
            tail.add(track.copy(isUserQueued = false))
        }
        if (tail.isEmpty()) return
        queue = queue + tail
    }

    private fun publishHasNextIfChanged() {
        val next = computeHasNext()
        if (_state.value.hasNext != next) {
            _state.value = _state.value.copy(hasNext = next)
        }
    }

    /** Clear the user-queue flag when a manually queued entry becomes Now Playing. */
    private fun clearUserQueuedFlagAt(index: Int) {
        val track = queue.getOrNull(index) ?: return
        if (!track.isUserQueued) return
        queue = queue.toMutableList().also { it[index] = track.copy(isUserQueued = false) }
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
        spotifyConnectPlayJob?.cancel()
        val job = managerScope.launch { block() }
        spotifyConnectPlayJob = job
        job.invokeOnCompletion {
            if (spotifyConnectPlayJob === job) {
                spotifyConnectPlayJob = null
            }
        }
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
    /**
     * After Edit→reorder or Add to Queue, feed/profile [updateFeedQueue] must not
     * rebuild Up Next from feed order (only `isUserQueued` rows survive that path).
     * Keep the pinned order and append brand-new posts only — mirrors iOS
     * `queueOrderPinnedByUser`. Cleared on an explicit play seed or [stop].
     */
    private var queueOrderPinnedByUser: Boolean = false

    /** Snapshot of the in-memory queue for the full-player Queue sheet. */
    fun queueSnapshot(): List<QueuedTrack> = queue

    fun currentQueueIndexSnapshot(): Int? = currentQueueIndex

    fun testingHandleSpotifyNaturalFeedTrackEnd(uri: String) {
        handleSpotifyNaturalFeedTrackEnd(uri)
    }

    fun testingHandleSpotifyConnectTrackEnded() {
        handleSpotifyConnectTrackEnded()
    }

    fun testingSpotifyNaturalEndAdvanceJobActive(): Boolean =
        spotifyNaturalEndAdvanceJob?.isActive == true

    fun testingSpotifyCorusRequestedUri(): String? = spotifyCorusRequestedUri

    fun testingCancelSpotifyNaturalEndAdvanceJob() {
        cancelSpotifyNaturalEndAdvanceJob()
    }

    fun testingSetIsSpotifyConnectPlaying(playing: Boolean) {
        isSpotifyConnectPlaying = playing
    }

    fun testingSetSpotifyDeviceLockedForQueueDriving(locked: Boolean) {
        setSpotifyDeviceLockedForQueueDriving(locked)
    }

    fun testingRefreshSpotifyFastPathSkipGuardForUpcomingTrackIfNeeded() {
        refreshSpotifyFastPathSkipGuardForUpcomingTrackIfNeeded()
    }

    fun testingArmCorusPlayIntent(uri: String, windowMs: Long = 8000L) {
        spotifyCorusRequestedUri = uri
        spotifyCorusRequestedUntil = System.currentTimeMillis() + windowMs
        clearSpotifyRequestedTrackConfirmed()
    }

    fun testingMarkRequestedTrackConfirmed(settled: Boolean = false) {
        spotifyRequestedTrackConfirmed = true
        spotifyRequestedTrackConfirmedAtMs = if (settled) {
            System.currentTimeMillis() - SpotifyConnectWake.HANDOFF_NEXT_SETTLE_MS - 1
        } else {
            System.currentTimeMillis()
        }
    }

    fun testingArmFeedSkip(windowMs: Long = 5000L) {
        spotifyFeedSkipRequestedUntil = System.currentTimeMillis() + windowMs
    }

    fun testingReconcileSpotifyQueuePosition(uri: String) {
        reconcileSpotifyQueuePosition(uri)
    }

    /**
     * Recents swipe must not tear down Connect — otherwise Spotify owns the
     * next song. Same as iOS home/lock keeping Corus in control.
     */
    fun shouldKeepPlaybackServiceAfterTaskRemoved(): Boolean =
        isSpotifyConnectPlaying || _state.value.isPlaying

    fun removeQueueItem(index: Int) {
        if (index !in queue.indices) return
        val playingIdx = currentQueueIndex
        if (playingIdx == index) return // Don't remove the now-playing row.
        val removedId = queue[index].trackId
        val pruned = queue.toMutableList().also { it.removeAt(index) }
        queue = pruned
        currentQueueIndex = pruned.indexOfActive(_state.value.trackId, _state.value.sourcePostId)
        publishHasNextIfChanged()
        analyticsService.logQueueItemRemoved(removedId)
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
        queueOrderPinnedByUser = true
        currentQueueIndex = mutable.indexOfActive(_state.value.trackId, _state.value.sourcePostId)
        publishHasNextIfChanged()
        analyticsService.logQueueItemReordered()
    }

    fun jumpToQueueIndex(index: Int) {
        if (index !in queue.indices) return
        clearUserQueuedFlagAt(index)
        val track = queue[index]
        currentQueueIndex = index
        publishHasNextIfChanged()
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
            val seconds = toMs.coerceAtLeast(0L) / 1000.0
            spotifyScrubberHoldAtZero = false
            spotifyScrubberHoldUntilTrackChangeFromUri = null
            beginSpotifyScrubSeekInFlight(seconds)
            if (_state.value.isPlaying) userInitiatedPause = false
            val previousMs = ScrubberClock.time.value
            val clampedMs = toMs.coerceAtLeast(0L)
            ScrubberClock.snapTime(clampedMs)
            syncSpotifyScrubAnchor(seconds)
            spotifySeekJob?.cancel()
            spotifySeekJob = managerScope.launch {
                if (isExternalSpotifyListening) {
                    val ok = withBriefExternalSpotifyConnection { remote ->
                        remote.seek(seconds)
                    }
                    if (!ok) {
                        ScrubberClock.snapTime(previousMs)
                        syncSpotifyScrubAnchor(previousMs / 1000.0)
                        notifyExternalSpotifyTransportFailed()
                    }
                } else {
                    runCatching { spotifyPlaybackService.seek(seconds) }
                        .onFailure { error ->
                            android.util.Log.w("NowPlaying", "Spotify seek failed: ${error.message}")
                        }
                    // Re-pin after the API ack so a late pre-seek PlayerState can't win.
                    ScrubberClock.snapTime(clampedMs)
                    syncSpotifyScrubAnchor(seconds)
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
     * Next post to show [fm.corus.android.ui.components.FeedPlayingPill] during
     * mini/feed Next — armed on the tap frame so snap-in can finish before
     * identity/`sourcePostId` changes trigger follow-scroll. Mirrors iOS
     * `stagedFeedSkipPillTrackId` (not [_loadingTrackId], which has cancel /
     * play-routing side effects).
     */
    private val _stagedFeedSkipPillTrackId = MutableStateFlow<String?>(null)
    val stagedFeedSkipPillTrackId: StateFlow<String?> = _stagedFeedSkipPillTrackId.asStateFlow()
    private val _stagedFeedSkipPillSourcePostId = MutableStateFlow<String?>(null)
    val stagedFeedSkipPillSourcePostId: StateFlow<String?> =
        _stagedFeedSkipPillSourcePostId.asStateFlow()

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
        queueOrderPinnedByUser = false
        val preserved = snapshotUserQueuedUpNext()
        this.queue = queue
        this.currentQueueIndex = queue.indexOfActive(track.trackId, track.sourcePostId)
        // New playback context — drop any previous paginated-queue hook until caller re-wires it.
        this.queueHasMore = false
        this.loadMoreQueue = null
        restoreUserQueuedUpNext(preserved)
        playInternal(track)
    }

    /**
     * Sync the now-playing queue with a paginated feed.
     *
     * Callers (FeedViewModel, ProfileFeedViewModel) invoke this whenever the feed
     * list or its `hasMore` flag changes, and pass a `loadMore` that fetches the
     * next page. This keeps the mini-player's next button enabled — and functional —
     * when the user exhausts the currently-loaded page.
     *
     * After the user reorders Up Next or uses Add to Queue, the order is pinned:
     * sync only appends brand-new posts instead of rebuilding from feed order
     * (mirrors iOS `queueOrderPinnedByUser`).
     */
    fun updateFeedQueue(
        newQueue: List<QueuedTrack>,
        hasMore: Boolean,
        loadMore: suspend () -> Unit,
    ) {
        val currentTrackId = _state.value.trackId
        // Don't clobber an unrelated now-playing context (e.g. track started from search).
        if (currentTrackId != null && newQueue.none { it.trackId == currentTrackId }) return
        val preserved = snapshotUserQueuedUpNext()
        if (queueOrderPinnedByUser) {
            appendNewTracksPreservingUserOrder(newQueue)
            queueHasMore = hasMore
            loadMoreQueue = loadMore
            currentQueueIndex = queue.indexOfActive(currentTrackId, _state.value.sourcePostId)
            restoreUserQueuedUpNext(preserved)
            publishHasNextIfChanged()
            return
        }
        queue = newQueue
        queueHasMore = hasMore
        loadMoreQueue = loadMore
        currentQueueIndex = newQueue.indexOfActive(currentTrackId, _state.value.sourcePostId)
        restoreUserQueuedUpNext(preserved)
        publishHasNextIfChanged()
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

        // Cancel any in-flight load for a different track. Keep a staged Next
        // pill armed for [track] — that target is the song we're about to load.
        cancelLoading(clearStagedSkipPill = false)

        // iOS song_preview_played — ExoPlayer / preview path (Connect logs separately).
        analyticsService.logSongPreviewPlayed(trackId)

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
            TrackSource.BANDCAMP -> resolveBandcampPreview(
                bandcampUrl = track.bandcampUrl,
                bandcampId = bandcampIdFromTrackId(trackId),
                name = track.trackName,
                artist = track.artistName,
            )?.first
            else -> track.previewUrl?.takeIf { it.isNotBlank() }
                ?: previewCache[trackId]
                ?: lookupPreviewUrl(
                    trackId,
                    track.trackName,
                    track.artistName,
                    track.isrc,
                    appleMusicId = track.appleMusicId,
                    durationMs = track.durationMs,
                    albumName = track.albumName,
                    storefront = track.appleMusicStorefront,
                )
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
            _stagedFeedSkipLoading.value = false
            clearStagedFeedSkipPill()
            if (userInitiated) _previewUnavailable.tryEmit(Unit)
            return
        }

        // Cache for future taps
        previewCache[trackId] = resolvedUrl

        // Quick Next can bump [playGeneration] after URL resolve — don't let the
        // outgoing track's confirm overwrite the staged next identity/pill.
        if (generation != playGeneration) return

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
                    .setArtworkUri(sessionArtworkUrl(track.albumArtURL, track.albumArtLargeURL)?.let { Uri.parse(it) })
                    .build()
            )
            .build()
        stopSpotifyConnectKeepAlive()
        val exo = ensurePlayerAndSession()
        exo.setMediaItem(mediaItem)
        exo.prepare()
        exo.play()
        // Promote the service only after the session is playing — otherwise
        // MediaSessionService can't post its notification fast enough and
        // Android kills us with ForegroundServiceDidNotStartInTimeException.
        // If Android refuses the start (app already backgrounded), bail —
        // onForegroundStartDenied() already paused. Do not mark isPlaying.
        if (!startForegroundServiceIfNeeded()) return

        if (generation != playGeneration) {
            exo.pause()
            return
        }

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
            bandcampUrl = track.bandcampUrl,
            catalogOrigin = track.catalogOrigin,
            notOnSpotify = track.notOnSpotify,
        )

        // This corus is now playing in-app — report a unique play. Reached for
        // both initial taps and auto-advance (which also routes through here);
        // the same-track pause/resume path returns early above, so this won't
        // double-fire on resume.
        recordPlayIfNeeded(track.sourcePostId)
        onPlaybackBecamePlaying(trackId)
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

    private suspend fun resolveBandcampPreview(
        bandcampUrl: String?,
        bandcampId: String?,
        name: String,
        artist: String,
    ): Pair<String, String?>? {
        return try {
            val resolved = withContext(Dispatchers.IO) {
                cloudFunctions.resolveBandcampPreview(
                    bandcampUrl = bandcampUrl,
                    bandcampId = bandcampId,
                    name = name,
                    artist = artist,
                )
            }
            android.util.Log.i(
                "NowPlaying",
                "resolveBandcampPreview OK id=$bandcampId url=${resolved?.first?.take(60)}…",
            )
            resolved
        } catch (e: Exception) {
            android.util.Log.w("NowPlaying", "resolveBandcampPreview FAILED id=$bandcampId msg=${e.message}", e)
            null
        }
    }

    /** Auto-advance to the next queued track when playback ends. */
    private fun handlePlaybackEnded() {
        skipToNext(preferPreviewOnNext = preferPreviewOnInAppSkip)
    }

    /**
     * @param immediate Full-player Next — skip the mini-player scroll lead-in
     *   so chrome/audio handoff is not held ~320ms.
     */
    fun skipToNext(preferPreviewOnNext: Boolean = false, immediate: Boolean = false) {
        // iOS skipToNext: lock / background skips staging delays so Spotify
        // isn't left playing a stale native-queue track while we wait for feed chrome.
        val skipStaging = immediate || shouldSkipSpotifyFeedAdvanceStaging()
        if (shouldRouteSpotifyFeedSkip(preferPreviewOnNext)) {
            cancelDebouncedSpotifyRelinquish()
            spotifyFeedSkipRequestedUntil = System.currentTimeMillis() + 5000
            armSpotifyFastPathSkipGuardForUpcomingFeedTrack()
            forceSpotifyFeedAdvanceToNextEntry(immediate = skipStaging)
            return
        }
        stopPositionPolling(resetClock = false)
        if (isSpotifyConnectPlaying) {
            resetScrubberPosition()
        } else {
            ScrubberClock.reset()
        }
        advanceToNext(immediate = skipStaging)
    }

    /**
     * Restart if >3s into the track; otherwise jump to the previous queue item.
     * Mirrors iOS `NowPlayingManager.skipToPreviousOrRestart` (iOS ScrubberClock
     * seconds → Android ms).
     */
    fun skipToPreviousOrRestart() {
        val positionMs = ScrubberClock.time.value
        val idx = currentQueueIndex
        if (shouldRestartInsteadOfSkipPrevious(positionMs, idx)) {
            seek(0L)
            return
        }
        val previousIndex = idx!! - 1
        manualSkipGuardUntil = System.currentTimeMillis() + 1_000
        resetScrubberPosition()
        jumpToQueueIndex(previousIndex)
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
                trackId = next.trackId,
                spotifyURI = next.spotifyURI,
            )
        ) {
            return false
        }
        return isSpotifyConnectPlaying || spotifyExperimentEnabledForTrack(next)
    }

    fun forceSpotifyFeedAdvanceToNextEntry(immediate: Boolean = false) {
        if (adoptSpotifyConnectNextIfMatching(spotifyPlaybackService.currentTrackUri.value)) {
            return
        }
        // Full-player: pause now. Mini: defer pause — App Remote pause on the
        // scroll kickoff frame hitchs feed autoscroll.
        if (immediate) {
            awaySkipMutedUri = null
            spotifyPlaybackService.pauseImmediately()
        }
        val idx = currentQueueIndex ?: run { skipToNextLegacyPreview(); return }
        if (idx + 1 >= queue.size) {
            if (queueHasMore) {
                skipToNextLegacyPreview()
            }
            return
        }
        val next = queue[idx + 1]
        if (!spotifyExperimentEnabledForTrack(next)) {
            userInitiatedPause = false
            silentlyHaltSpotifyForPreview()
            manualSkipGuardUntil = System.currentTimeMillis() + 1500
            spotifyFeedSkipRequestedUntil = System.currentTimeMillis() + 5000
            cancelDebouncedSpotifyRelinquish()
            stopPositionPolling(resetClock = false)
            resetScrubberPosition()
            advanceToNext(immediate = immediate)
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
        clearUserQueuedFlagAt(idx + 1)
        currentQueueIndex = idx + 1
        refreshSpotifyQueueNextUri()
        val track = queue[idx + 1]
        val leadInGen = ++feedSkipLeadInGeneration

        if (immediate) {
            beginStagedFeedSkipLoading(track, armPill = false)
            abortOutgoingPlaybackForStagedSkip()
            launchSpotifyConnectPlay {
                updateStateForTrack(track)
                playViaSpotifyConnect(spotifyPendingPlay(track), replaceSpotifyQueue = true)
            }
            return
        }

        // c10b495 parity: scroll only on tap (sourcePostId), then quiet →
        // abort/chrome → play. Pill + mini spinner arm on the tap frame so
        // they stay in sync (iOS stagedFeedSkipLoading).
        publishSourcePostIdForFeedScroll(track.sourcePostId)
        stagedFeedSkipInFlight = true
        beginStagedFeedSkipLoading(track, armPill = true)
        android.util.Log.i("NowPlaying", "staged two-phase Next → ${track.trackName} (150+220+200ms)")
        managerScope.launch {
            delay(FEED_SKIP_SCROLL_QUIET_MS)
            if (leadInGen != feedSkipLeadInGeneration) return@launch
            // Warm during post-scroll waits (cache only — no player IPC).
            resolveSpotifyPlaybackURI(track)
            delay(FEED_SKIP_PILL_LEAD_IN_MS)
            if (leadInGen != feedSkipLeadInGeneration) return@launch
            abortOutgoingPlaybackForStagedSkip()
            delay(FEED_SKIP_AUDIO_AFTER_CHROME_MS)
            if (leadInGen != feedSkipLeadInGeneration) return@launch
            stagedFeedSkipInFlight = false
            android.util.Log.i("NowPlaying", "staged two-phase play → ${track.trackName}")
            launchSpotifyConnectPlay {
                updateStateForTrack(track)
                playViaSpotifyConnect(spotifyPendingPlay(track), replaceSpotifyQueue = true)
            }
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
    private fun advanceToNext(immediate: Boolean = false) {
        val idx = currentQueueIndex ?: return
        val localNext = queue.getOrNull(idx + 1)
        if (localNext != null) {
            clearUserQueuedFlagAt(idx + 1)
            currentQueueIndex = idx + 1
            val track = queue[idx + 1]
            val leadInGen = ++feedSkipLeadInGeneration

            if (immediate) {
                beginStagedFeedSkipLoading(track, armPill = false)
                abortOutgoingPlaybackForStagedSkip()
                managerScope.launch {
                    playInternal(track, userInitiated = false)
                }
                return
            }

            // Preview mini Next: scroll quiet → chrome + play. Pill + mini
            // spinner arm on the tap frame so they stay in sync.
            publishSourcePostIdForFeedScroll(track.sourcePostId)
            stagedFeedSkipInFlight = true
            beginStagedFeedSkipLoading(track, armPill = true)
            android.util.Log.i("NowPlaying", "staged preview Next → ${track.trackName} (350+280ms then chrome+play)")
            managerScope.launch {
                delay(FEED_SKIP_SCROLL_QUIET_PREVIEW_MS)
                if (leadInGen != feedSkipLeadInGeneration) return@launch
                delay(FEED_SKIP_PILL_LEAD_IN_PREVIEW_MS)
                if (leadInGen != feedSkipLeadInGeneration) return@launch
                abortOutgoingPlaybackForStagedSkip()
                stagedFeedSkipInFlight = false
                android.util.Log.i("NowPlaying", "staged preview play → ${track.trackName}")
                playInternal(track, userInitiated = false)
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
            val leadInGen = ++feedSkipLeadInGeneration
            stagedFeedSkipInFlight = true
            currentQueueIndex = currentIdx + 1
            publishSourcePostIdForFeedScroll(next.sourcePostId)
            beginStagedFeedSkipLoading(next, armPill = true)
            android.util.Log.i("NowPlaying", "staged preview Next → ${next.trackName} (350+280ms then chrome+play)")
            delay(FEED_SKIP_SCROLL_QUIET_PREVIEW_MS)
            if (leadInGen != feedSkipLeadInGeneration) return@launch
            delay(FEED_SKIP_PILL_LEAD_IN_PREVIEW_MS)
            if (leadInGen != feedSkipLeadInGeneration) return@launch
            abortOutgoingPlaybackForStagedSkip()
            stagedFeedSkipInFlight = false
            android.util.Log.i("NowPlaying", "staged preview play → ${next.trackName}")
            playInternal(next, userInitiated = false)
        }
    }

    /**
     * Kick feed follow-scroll during mini Next lead-in without rewriting the
     * full mini-player identity (that waits until play). Same sourcePostId is
     * re-applied by [playInternal] / [updateStateForTrack] afterward — no
     * second scroll.
     */
    private fun publishSourcePostIdForFeedScroll(postId: String?) {
        if (postId == null) return
        if (_state.value.sourcePostId == postId) return
        _state.value = _state.value.copy(sourcePostId = postId)
    }

    /**
     * Quick Next after art-tap: cancel in-flight Connect/preview confirms so they
     * can't finish and wipe the next track's loading chrome (mirrors iOS
     * staged-skip abort). Keeps the Spotify session; only stops audio.
     */
    private fun abortOutgoingPlaybackForStagedSkip() {
        playGeneration++
        spotifyConnectPlayGeneration++
        spotifyConnectPlayJob?.cancel()
        spotifyConnectPlayJob = null
        spotifyPlaybackService.cancelPendingPlayRequest()
        spotifyPlaybackService.pauseImmediately()
        player?.pause()
    }

    /** Light FeedPlayingPill on the next post during staged Next scroll. */
    private fun armStagedFeedSkipPill(track: QueuedTrack) {
        if (debugDisableAlbumArtPlayingPill) return
        _stagedFeedSkipPillTrackId.value = track.trackId
        _stagedFeedSkipPillSourcePostId.value = track.sourcePostId
    }

    /**
     * Sticky transport spinner (+ optional feed pill) from Next tap until
     * [onPlaybackBecamePlaying]. Arming both on the tap frame keeps mini
     * player and album-art pill in sync.
     */
    private fun beginStagedFeedSkipLoading(track: QueuedTrack, armPill: Boolean) {
        _stagedFeedSkipLoading.value = true
        if (armPill) armStagedFeedSkipPill(track)
    }

    /**
     * Drop Next-handoff chrome once this track is actually playing. Ignore an
     * outgoing track's late play-confirm so we don't kill the *next* post's
     * spinner/pill. Mirrors iOS `isPlaying` didSet.
     */
    private fun onPlaybackBecamePlaying(trackId: String?) {
        val stagedTarget = _stagedFeedSkipPillTrackId.value
        val outgoingConfirmDuringStagedSkip =
            stagedTarget != null && trackId != stagedTarget
        if (outgoingConfirmDuringStagedSkip) return
        _stagedFeedSkipLoading.value = false
        clearStagedFeedSkipPill()
    }

    private fun clearStagedFeedSkipPill() {
        if (_stagedFeedSkipPillTrackId.value != null) {
            _stagedFeedSkipPillTrackId.value = null
        }
        if (_stagedFeedSkipPillSourcePostId.value != null) {
            _stagedFeedSkipPillSourcePostId.value = null
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
                    if (spotifyConnectKeepAliveActive) return
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
                    if (spotifyConnectKeepAliveActive) return
                    if (isPlaying) startPositionPolling()
                    else stopPositionPolling(resetClock = false)
                }

                override fun onMediaItemTransition(
                    mediaItem: MediaItem?,
                    reason: Int,
                ) {
                    if (spotifyConnectKeepAliveActive) return
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
                    this@NowPlayingManager.forceSpotifyFeedAdvanceToNextEntry(immediate = true)
                } else {
                    this@NowPlayingManager.skipToNext(
                        preferPreviewOnNext = preferPreview,
                        immediate = true,
                    )
                }
            }

            override fun seekToNextMediaItem() = seekToNext()

            override fun hasNextMediaItem(): Boolean = computeHasNext()

            override fun seekToPrevious() {
                this@NowPlayingManager.skipToPreviousOrRestart()
            }

            override fun seekToPreviousMediaItem() = seekToPrevious()

            override fun hasPreviousMediaItem(): Boolean {
                val idx = currentQueueIndex ?: return false
                return idx > 0
            }

            override fun isPlaying(): Boolean {
                if (isSpotifyConnectPlaying) {
                    // Media3 uses this to keep the FGS alive. Prefer Corus chrome
                    // so a stalled App Remote isPlaying=false doesn't tear us down.
                    return _state.value.isPlaying
                }
                return super.isPlaying()
            }

            override fun play() {
                if (isExternalSpotifyListening) {
                    if (!_state.value.isPlaying) togglePlayPause()
                    return
                }
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
                if (isExternalSpotifyListening) {
                    if (_state.value.isPlaying) togglePlayPause()
                    return
                }
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
                    .add(Player.COMMAND_SEEK_TO_PREVIOUS)
                    .add(Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM)
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
            .setId("corus-${System.identityHashCode(this)}")
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
     *
     * Android 12+ throws [android.app.ForegroundServiceStartNotAllowedException]
     * (an [IllegalStateException]) from [ContextCompat.startForegroundService]
     * itself when the app is backgrounded — the service never reaches
     * onStartCommand, so that catch cannot help. Recover here instead of crashing.
     *
     * @return true if the service is (or already was) started.
     */
    @VisibleForTesting
    internal fun startForegroundServiceIfNeeded(): Boolean {
        if (foregroundServiceStarted) return true
        return try {
            startForegroundServiceAction(Intent(context, CorusPlaybackService::class.java))
            foregroundServiceStarted = true
            true
        } catch (e: IllegalStateException) {
            android.util.Log.w("NowPlaying", "startForegroundService denied: ${e.message}")
            onForegroundStartDenied()
            false
        }
    }

    /**
     * Hook for [startForegroundServiceIfNeeded]. Production starts the playback
     * service; tests replace this to simulate Android 12+ background denial.
     */
    @VisibleForTesting
    internal var startForegroundServiceAction: (Intent) -> Unit = { intent ->
        ContextCompat.startForegroundService(context, intent)
    }

    /**
     * Loop inaudible ExoPlayer audio while Spotify Connect is active so the
     * media foreground service stays up. Without this, Media3 sees an idle
     * player, drops FGS, and Samsung LMK can kill Corus — Spotify then owns
     * the next song. Mirrors iOS `startSpotifyBackgroundKeepAliveIfNeeded`.
     */
    @OptIn(UnstableApi::class)
    private fun startSpotifyConnectKeepAlive() {
        if (skipConnectKeepAlive) {
            startForegroundServiceIfNeeded()
            return
        }
        if (!isSpotifyConnectPlaying) return
        try {
            val exo = ensurePlayerAndSession()
            if (spotifyConnectKeepAliveActive) {
                if (!exo.isPlaying) exo.play()
                startForegroundServiceIfNeeded()
                return
            }
            spotifyConnectKeepAliveActive = true
            val attrs = AudioAttributes.Builder()
                .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                .setUsage(C.USAGE_MEDIA)
                .build()
            exo.setAudioAttributes(attrs, /* handleAudioFocus = */ false)
            exo.setHandleAudioBecomingNoisy(false)
            exo.volume = 0f
            exo.repeatMode = Player.REPEAT_MODE_ONE
            exo.setMediaSource(SilenceMediaSource(/* durationUs= */ 30_000_000L))
            exo.prepare()
            exo.play()
            startForegroundServiceIfNeeded()
        } catch (e: RuntimeException) {
            spotifyConnectKeepAliveActive = false
            android.util.Log.w("SpotifyPlayback", "Connect keep-alive failed: ${e.message}")
            startForegroundServiceIfNeeded()
        }
    }

    @OptIn(UnstableApi::class)
    private fun stopSpotifyConnectKeepAlive(restoreAudioFocus: Boolean = true) {
        val exo = player
        if (spotifyConnectKeepAliveActive) {
            if (exo == null) {
                spotifyConnectKeepAliveActive = false
            } else {
                exo.repeatMode = Player.REPEAT_MODE_OFF
                exo.volume = 1f
                exo.setHandleAudioBecomingNoisy(false)
                // Idle the silent player *before* any focus restore. Flipping
                // focus on while silence is still playing requests AUDIOFOCUS_GAIN
                // and pauses the Liked Song the user just started in Spotify.
                exo.playWhenReady = false
                if (exo.isPlaying) exo.pause()
                exo.stop()
                exo.clearMediaItems()
                spotifyConnectKeepAliveActive = false
            }
        }
        // Relinquish must not restore focus — setAudioAttributes(handleAudioFocus)
        // requests AUDIOFOCUS_GAIN even when the player is idle.
        if (!restoreAudioFocus || exo == null) return
        exo.setHandleAudioBecomingNoisy(true)
        exo.setAudioAttributes(
            AudioAttributes.Builder()
                .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                .setUsage(C.USAGE_MEDIA)
                .build(),
            /* handleAudioFocus = */ true,
        )
    }

    /**
     * Called when Android refused to promote the service to the foreground
     * (ForegroundServiceStartNotAllowedException on API 31+) — i.e. playback
     * was started while the app was backgrounded. Fired from
     * [startForegroundServiceIfNeeded] (the startForegroundService() call
     * itself) or from [CorusPlaybackService.onStartCommand] (startForeground()).
     * We can't keep audio running without the foreground service, so pause and
     * clear [foregroundServiceStarted] so the next foreground play() retries the
     * promotion cleanly. Reached on the main thread, the same thread that owns
     * the player, so no dispatch is needed.
     */
    fun onForegroundStartDenied() {
        foregroundServiceStarted = false
        stopSpotifyConnectKeepAlive()
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
        appleMusicId: String? = null,
        durationMs: Int? = null,
        albumName: String? = null,
        storefront: String? = null,
    ): String? {
        if (noMatchCache.contains(trackId)) return null

        val url = try {
            withContext(Dispatchers.IO) {
                cloudFunctions.appleMusicLookup(
                    name,
                    artist,
                    isrc,
                    trackId,
                    appleMusicId = appleMusicId,
                    durationMs = durationMs,
                    albumName = albumName,
                    storefront = storefront,
                )
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

    fun cancelLoading(clearStagedSkipPill: Boolean = true) {
        if (stagedFeedSkipInFlight || _stagedFeedSkipLoading.value) {
            cancelStagedFeedSkip()
            return
        }
        val cancellingFullSongResolve =
            _isResolvingSpotify.value || spotifyConnectPlayJob?.isActive == true
        playGeneration++
        spotifyConnectPlayGeneration++
        _loadingTrackId.value = null
        _loadingSourcePostId.value = null
        if (clearStagedSkipPill) {
            clearStagedFeedSkipPill()
        }
        if (cancellingFullSongResolve) {
            // Art re-tap while loading — cancel job and hard-pause so a late
            // Connect confirm can't flip chrome back to playing.
            _isResolvingSpotify.value = false
            spotifyConnectPlayJob?.cancel()
            spotifyConnectPlayJob = null
            spotifyPlaybackService.cancelPendingPlayRequest()
            spotifyPlaybackService.pauseImmediately()
            player?.pause()
            userInitiatedPause = true
            _state.value = _state.value.copy(isPlaying = false)
        }
    }

    /** Abort staged mini Next (scroll quiet / pill / delayed play) and hard-pause. */
    private fun cancelStagedFeedSkip() {
        feedSkipLeadInGeneration++
        stagedFeedSkipInFlight = false
        _stagedFeedSkipLoading.value = false
        abortOutgoingPlaybackForStagedSkip()
        userInitiatedPause = true
        _state.value = _state.value.copy(isPlaying = false)
        clearStagedFeedSkipPill()
        _isResolvingSpotify.value = false
        _loadingTrackId.value = null
        _loadingSourcePostId.value = null
    }

    fun togglePlayPause() {
        // Pause during staged Next must abort the deferred play — otherwise the
        // sleeping lead-in still starts the next track after chrome settles.
        if (stagedFeedSkipInFlight || _stagedFeedSkipLoading.value) {
            cancelStagedFeedSkip()
            return
        }
        // Paused session in the wrong mode (e.g. Always Full flipped while paused):
        // restart into the desired engine instead of silently resuming the old one.
        if (shouldRestartPausedSessionForDesiredPlaybackMode()) {
            restartCurrentTrackForDesiredPlaybackMode()
            return
        }
        // External Spotify — optimistic chrome, rollback if App Remote fails
        // (iOS parity: command-only brief reconnect, no live play/pause sync).
        if (isExternalSpotifyListening) {
            managerScope.launch {
                if (_state.value.isPlaying) {
                    externalSpotifyUserPaused = true
                    _state.value = _state.value.copy(isPlaying = false)
                    val ok = withBriefExternalSpotifyConnection { it.pause() }
                    if (!isExternalSpotifyListening) return@launch
                    if (!ok) {
                        externalSpotifyUserPaused = false
                        _state.value = _state.value.copy(isPlaying = true)
                        syncSpotifyScrubAnchor(ScrubberClock.time.value / 1000.0)
                        notifyExternalSpotifyTransportFailed()
                    }
                } else {
                    externalSpotifyUserPaused = false
                    _state.value = _state.value.copy(isPlaying = true)
                    syncSpotifyScrubAnchor(ScrubberClock.time.value / 1000.0)
                    val ok = withBriefExternalSpotifyConnection { it.resume() }
                    if (!isExternalSpotifyListening) return@launch
                    if (!ok) {
                        externalSpotifyUserPaused = true
                        _state.value = _state.value.copy(isPlaying = false)
                        notifyExternalSpotifyTransportFailed()
                    }
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
        feedSkipLeadInGeneration++
        stagedFeedSkipInFlight = false
        clearExternalSpotifyListening()
        if (isSpotifyConnectPlaying) {
            silentlyHaltSpotifyForPreview()
        }
        stopSpotifyConnectKeepAlive()
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
        queueOrderPinnedByUser = false
        _stagedFeedSkipLoading.value = false
        clearStagedFeedSkipPill()
        _state.value = NowPlayingState()
    }

    fun dismiss() {
        stop()
    }

    fun pause() {
        if (isExternalSpotifyListening) {
            if (_state.value.isPlaying) togglePlayPause()
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
            if (!_state.value.isPlaying) togglePlayPause()
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
        awaySkipMutedUri = null
        spotifyPlaybackService.clearContextTakeoverWithoutTrackChange()
        clearSpotifyHandoffFailureSuppression()
        if (!SpotifyPlaybackService.isSpotifyAppInstalled(context)) {
            handleSpotifyPlaybackFailure(queuedTrackFrom(pending), userInitiated = true)
            return
        }

        val prefs = context.getSharedPreferences("corus_prefs", Context.MODE_PRIVATE)
        val hasPriorSession = spotifyAuthService.cachedAccessToken() != null ||
            prefs.getLong("fm.corus.spotify.lastAppRemoteUsage", 0L) > 0
        val wasActive = isSpotifyConnectPlaying || spotifyPlaybackService.isConnected || hasPriorSession
        // Token / last-usage from hours ago is not a live App Remote. Extended
        // verify after a freeze must not be skipped just because we authed once.
        val isLiveConnect = spotifyPlaybackService.isConnected &&
            (isSpotifyConnectPlaying || spotifyPlaybackService.isPlaying.value)

        // Prior Job is cancelled by [launchSpotifyConnectPlay] / staged-skip abort.
        // Do not cancel [spotifyConnectPlayJob] here — this coroutine may be that job.
        spotifyPlaybackService.cancelPendingPlayRequest()
        spotifyUriToQueueIndex.clear()
        spotifyQueueTransitionUntil = System.currentTimeMillis() + 4000
        spotifyPlaybackService.onPlayerTrackChanged = null
        spotifyPlaybackService.onTrackEnded = null
        spotifyPlaybackService.onPlayerContextUpdated = null
        spotifyPlaybackService.onLibraryContextDetected = {
            handleLibraryPickContext()
        }

        spotifyConnectPlayGeneration += 1
        val generation = spotifyConnectPlayGeneration
        clearSpotifyRequestedTrackConfirmed()

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
        // Start FGS while this is still a user-started play (usually still
        // foreground). If Connect later fails, preview fallback can reuse the
        // running service instead of calling startForegroundService from the
        // background (ForegroundServiceStartNotAllowedException on API 31+).
        ensurePlayerAndSession()
        startForegroundServiceIfNeeded()

        val expectedTrackId = pending.trackId
        val replaceQueue = replaceSpotifyQueue || !wasActive || isFeedTrackSwitch
        val queueSession = ++spotifyQueueSessionId
        var resolvedUri: String? = null

        try {
            val track = queuedTrackFrom(pending)
            resolvedUri = resolveSpotifyPlaybackURI(track)
            if (spotifyConnectPlayGeneration != generation) return
            if (resolvedUri == null) {
                if (_state.value.trackId == pending.trackId &&
                    spotifyConnectPlayGeneration == generation
                ) {
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
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            android.util.Log.w("NowPlaying", "Spotify play failed: ${e.message}")
            if (_state.value.trackId == expectedTrackId &&
                spotifyConnectPlayGeneration == generation
            ) {
                if (SpotifyConnectWake.shouldAbandonSilentRetries(e)) {
                    // Connect timed out. Preview fallback is what showed
                    // "No preview available" over a playable Spotify URI.
                    _isResolvingSpotify.value = false
                    _state.value = _state.value.copy(isPlaying = false)
                    return
                }
                handleSpotifyPlaybackFailure(
                    queuedTrackFrom(pending),
                    userInitiated = true,
                    mayHaveStartedAudio = true,
                )
            }
            return
        }

        if (spotifyConnectPlayGeneration != generation) return
        if (_state.value.trackId != expectedTrackId) {
            // Superseded by Next / another tap — don't clear the newer request's
            // resolving spinner.
            return
        }

        installSpotifyConnectDelegates()
        markSpotifyRequestedTrackConfirmedIfNeeded()
        spotifyPlaybackService.currentTrackUri.value?.let { maybeRollbackSpeculativeAwaySkip(it) }
        spotifyUriToQueueIndex[resolvedUri!!] = currentQueueIndex ?: 0
        spotifyExpectedTrackUri = null
        spotifyQueueTransitionUntil = null

        isSpotifyConnectPlaying = true
        spotifyPlaybackService.muteUnexpectedWhileConnectPlaying = true
        spotifyPlaybackService.playQueueNextOnUnexpected = corusAppIsAwayFromForeground()
        refreshSpotifyQueueNextUri()
        spotifyConnectStartedAt = System.currentTimeMillis()
        spotifyConnectWasPlaying = true
        syncSpotifyScrubAnchor(spotifyPlaybackService.positionSeconds.value)
        if (spotifyConnectPlayGeneration != generation) return
        _state.value = _state.value.copy(isPlaying = true)
        userInitiatedPause = false
        startSpotifyConnectKeepAlive()
        startSpotifyConnectTimePolling()
        onPlaybackBecamePlaying(expectedTrackId)

        // Drop the spinner as soon as play() returned. Keeping it through
        // verify left art + mini spinning for the whole (sometimes multi-
        // second) confirm — or forever if App Remote IPC stalled on first
        // connect. Mirrors iOS playViaSpotifyConnect.
        _isResolvingSpotify.value = false

        val svc = spotifyPlaybackService
        if (!svc.isPlaying.value && svc.isConnected) {
            svc.resume()
        }

        var verified = verifySpotifyConnectPlaybackStarted(
            expectedUri = resolvedUri!!,
            extended = SpotifyConnectWake.shouldUseExtendedVerify(isLiveConnect),
        )
        // Connected with the right track but paused — one more resume pass
        // before giving up (common right after the first authorizeAndPlay).
        if (!verified &&
            svc.isConnected &&
            svc.currentTrackUri.value?.let { spotifyUriMatchesExpected(it, resolvedUri!!) } == true
        ) {
            svc.resume()
            delay(600)
            verified = verifySpotifyConnectPlaybackStarted(
                expectedUri = resolvedUri!!,
                extended = true,
            )
        }
        if (spotifyConnectPlayGeneration != generation) return
        if (_state.value.trackId != expectedTrackId) return

        if (!verified) {
            val hasMatchingTrack = svc.currentTrackUri.value?.let {
                spotifyUriMatchesExpected(it, resolvedUri!!)
            } == true
            if (SpotifyConnectWake.shouldKeepUnverifiedSession(
                    isPlaying = svc.isPlaying.value,
                    hasMatchingTrackUri = hasMatchingTrack,
                )
            ) {
                // getPlayerState can stall while audio is already starting.
                // Keep only when we have the expected track or isPlaying —
                // isConnected alone left muted keep-alive after a long freeze.
                android.util.Log.w(
                    "SpotifyPlayback",
                    "Connect verify timed out — keeping Connect session",
                )
                markSpotifyRequestedTrackConfirmedIfNeeded()
                clearSpotifyHandoffFailureSuppression()
                refreshSpotifyFastPathSkipGuardWhenLocked()
                return
            }
            // play() returned success but Spotify never reported the track.
            // Stop muted keep-alive so we don't look like we're playing
            // silence, drop the zombie IPC, and replay (silent then wake).
            android.util.Log.w(
                "SpotifyPlayback",
                "Connect verify failed — no audible evidence, retrying App Remote play",
            )
            stopSpotifyConnectKeepAlive()
            isSpotifyConnectPlaying = false
            _state.value = _state.value.copy(isPlaying = false)
            svc.disconnectPreservingToken()
            _isResolvingSpotify.value = true
            try {
                spotifyPlaybackService.play(
                    spotifyTrackId = pending.trackId,
                    uri = resolvedUri,
                    replaceQueue = true,
                    queueSessionId = ++spotifyQueueSessionId,
                )
                if (!svc.isPlaying.value && svc.isConnected) {
                    svc.resume()
                }
                verified = verifySpotifyConnectPlaybackStarted(
                    expectedUri = resolvedUri,
                    extended = true,
                )
            } catch (e: Exception) {
                android.util.Log.w("SpotifyPlayback", "Connect retry play failed: ${e.message}")
            } finally {
                if (spotifyConnectPlayGeneration == generation) {
                    _isResolvingSpotify.value = false
                }
            }
            if (spotifyConnectPlayGeneration != generation) return
            if (_state.value.trackId != expectedTrackId) return
            val retryHasMatchingTrack = svc.currentTrackUri.value?.let {
                spotifyUriMatchesExpected(it, resolvedUri!!)
            } == true
            if (verified ||
                SpotifyConnectWake.shouldKeepUnverifiedSession(
                    isPlaying = svc.isPlaying.value,
                    hasMatchingTrackUri = retryHasMatchingTrack,
                )
            ) {
                isSpotifyConnectPlaying = true
                startSpotifyConnectKeepAlive()
                if (verified || svc.isPlaying.value) {
                    _state.value = _state.value.copy(isPlaying = true)
                    onPlaybackBecamePlaying(expectedTrackId)
                }
                markSpotifyRequestedTrackConfirmedIfNeeded()
                clearSpotifyHandoffFailureSuppression()
                refreshSpotifyFastPathSkipGuardWhenLocked()
                return
            }
            // Still no audible evidence. Do not leave muted chrome, and do
            // not dump a playable Spotify URI into "No preview available".
            android.util.Log.w(
                "SpotifyPlayback",
                "Connect verify timed out — parking without preview fallback",
            )
            stopSpotifyConnectKeepAlive()
            isSpotifyConnectPlaying = false
            _state.value = _state.value.copy(isPlaying = false)
            _isResolvingSpotify.value = false
            return
        }
        markSpotifyRequestedTrackConfirmedIfNeeded()
        clearSpotifyHandoffFailureSuppression()
        refreshSpotifyFastPathSkipGuardWhenLocked()
    }

    private fun installSpotifyConnectDelegates() {
        clearExternalSpotifyListening()
        spotifyPlaybackService.onTrackEnded = { handleSpotifyConnectTrackEnded() }
        spotifyPlaybackService.onPlayerTrackChanged = { uri ->
            markSpotifyRequestedTrackConfirmedIfNeeded(uri)
            reconcileSpotifyQueuePosition(uri)
        }
        spotifyPlaybackService.onLibraryContextDetected = {
            handleLibraryPickContext()
        }
        spotifyPlaybackService.onPlayerContextUpdated = {
            if (!handleLibraryPickContext()) {
                spotifyPlaybackService.currentTrackUri.value?.let { uri ->
                    reconcileSpotifyQueuePosition(uri)
                }
            }
        }
        spotifyPlaybackService.onPlayerStateUpdated = {
            if (isSpotifyConnectPlaying) {
                markSpotifyRequestedTrackConfirmedIfNeeded()
                refreshSpotifyFastPathSkipGuardForUpcomingTrackIfNeeded()
                if (!spotifyScrubberHoldAtZero &&
                    !userInitiatedPause &&
                    spotifyScrubberShouldAdvance()
                ) {
                    val reported = spotifyPlaybackService.positionSeconds.value
                    if (spotifyScrubSeekInFlight()) {
                        // Only adopt once App Remote has caught up to the scrub target.
                        maybeCompleteSpotifyScrubSeekInFlight(reported)
                    } else {
                        syncSpotifyScrubAnchor(reported)
                    }
                }
            }
        }
    }

    /**
     * Poll App Remote state after play. First connect of a session gets extra
     * time — Spotify is still waking up and getPlayerState IPC can stall.
     * Mirrors iOS `verifySpotifyConnectPlaybackStarted(extended:)`.
     */
    private suspend fun verifySpotifyConnectPlaybackStarted(
        expectedUri: String? = null,
        extended: Boolean = false,
    ): Boolean {
        val maxTicks = if (extended) 100 else 50
        val resumeAfterTick = if (extended) 3 else 5
        repeat(maxTicks) { tick ->
            if (spotifyPlaybackService.isPlaying.value) return true
            if (expectedUri != null) {
                val current = spotifyPlaybackService.currentTrackUri.value
                if (current != null &&
                    spotifyUriMatchesExpected(current, expectedUri) &&
                    tick >= resumeAfterTick
                ) {
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
        if (!foregroundServiceStarted && corusAppIsBackgrounded()) {
            // Preview needs a media FGS. We are already backgrounded and never
            // got one — startForegroundService() would throw. Stay paused; the
            // next foreground play() retries.
            android.util.Log.w(
                "SpotifyPlayback",
                "Skipping preview fallback — backgrounded without FGS",
            )
            _state.value = _state.value.copy(isPlaying = false)
            return
        }
        playInternal(track, userInitiated = userInitiated, forceSwitch = true)
    }

    fun silentlyHaltSpotifyForPreview() {
        stopSpotifyConnectKeepAlive()
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
        spotifyPlaybackService.muteUnexpectedWhileConnectPlaying = false
        spotifyPlaybackService.playQueueNextOnUnexpected = false
        spotifyPlaybackService.clearMutedUnexpectedUri()
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
        clearSpotifyRequestedTrackConfirmed()
        spotifyPendingExternalUri = null
        spotifyScrubberHoldAtZero = false
        spotifyScrubberHoldUntilTrackChangeFromUri = null
        clearSpotifyScrubSeekInFlight()
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
            if (shouldRelinquishBecauseSpotifyAppTakeover() || incomingLooksLikeLibraryPick()) {
                relinquishSpotifyToExternalPlayback(uri)
                return
            }
            adoptSpotifyConnectNextIfMatching(uri)
            return
        }
        reclaimSpotifyQueueAfterExternalSkip(uri)
    }

    private fun reclaimSpotifyQueueAfterExternalSkip(reporting: String) {
        if (maybeRollbackSpeculativeAwaySkip(reporting)) return
        cancelDebouncedSpotifyRelinquish()

        // Ahead of the natural-end check: a song ending does not entitle Corus to
        // play over the podcast the user started in the meantime.
        if (SpotifyContentUri.isUserChosenNonCorusContent(reporting)) {
            relinquishSpotifyToExternalPlayback(reporting)
            return
        }

        if (spotifyOutgoingChangeWasNaturalFeedTrackEnd()) return

        if (shouldRelinquishBecauseSpotifyAppTakeover() || incomingLooksLikeLibraryPick()) {
            relinquishSpotifyToExternalPlayback(reporting)
            return
        }

        if (shouldRelinquishExternalSpotifyPlayback(reporting)) {
            relinquishSpotifyToExternalPlayback(reporting)
            return
        }

        spotifyInferMisroutedLockScreenSkipIfNeeded(reporting)

        if (adoptSpotifyConnectNextIfMatching(reporting)) {
            return
        }

        if (spotifyFeedSkipRequestedUntil?.let { System.currentTimeMillis() < it } == true) {
            if (adoptSpotifyConnectNextIfMatching(reporting)) return
            if (spotifyCorusRecentlyRequested(reporting)) return
            if (speculativeAwaySkipUri?.let {
                    SpotifyPlaybackService.spotifyURIsMatch(reporting, it)
                } == true
            ) {
                return
            }
            // Leftover native-queue URI during Next handoff is not another Next.
            if (!SpotifyConnectWake.shouldForceAdvanceOnUnexpectedDuringFeedSkip(
                    playIntentInFlight = spotifyCorusPlayIntentInFlight(),
                    requestedTrackConfirmed = spotifyRequestedTrackConfirmed,
                    confirmedForMs = spotifyRequestedTrackConfirmedForMs(),
                )
            ) {
                return
            }
            if (!computeHasNext()) return
            forceSpotifyFeedAdvanceToNextEntry(immediate = true)
            return
        }
        // Only ignore leftover state from the play we just issued (Bobby).
        // Control Center Next is a *new* URI and must still force-advance.
        if (spotifyCorusPlayIntentInFlight() &&
            (
                spotifyCorusRecentlyRequested(reporting) ||
                    spotifyPlaybackService.lastOutgoingTrackUri?.let {
                        SpotifyPlaybackService.spotifyURIsMatch(reporting, it)
                    } == true
                )
        ) {
            return
        }
        if (shouldForceSpotifyFeedAdvanceForMisroutedSkip(reporting)) {
            forceAdvancePossiblySpeculative(reporting)
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
        if (incomingLooksLikeLibraryPick()) {
            relinquishSpotifyToExternalPlayback(reporting)
            return
        }
        // Wait briefly so Liked Songs can deliver `collection` before we play next.
        if (!spotifyDeviceLockedForQueueDriving &&
            corusAppIsAwayFromForeground() &&
            computeHasNext()
        ) {
            scheduleAwaySkipDecision(reporting)
            return
        }
        if (!computeHasNext()) return
        scheduleDebouncedSpotifyExternalPlaybackDecision(reporting)
    }

    private fun handleSpotifyConnectTrackEnded() {
        if (!isSpotifyConnectPlaying) return
        // Stale end-of-track from the previous Spotify song during a Corus play()
        // handoff — don't pause the track we just requested and skip to next.
        if (spotifyCorusPlayIntentInFlight()) return
        // The song ended, but Spotify is already on a podcast the user started —
        // hand the session over instead of advancing the feed on top of it.
        val reporting = spotifyPlaybackService.currentTrackUri.value
        if (SpotifyContentUri.isUserChosenNonCorusContent(reporting)) {
            relinquishSpotifyToExternalPlayback(reporting)
            return
        }
        if (adoptSpotifyConnectNextIfMatching(reporting)) {
            return
        }
        if (reporting != null &&
            (
                shouldRelinquishForManualSpotifyPlayback(reporting) ||
                    shouldRelinquishExternalSpotifyPlayback(reporting)
                )
        ) {
            relinquishSpotifyToExternalPlayback(reporting)
            return
        }
        // Mute stale Spotify queue auto-advance immediately — the 400ms debounce
        // below is only for coalescing duplicate end signals, not for waiting.
        spotifyPlaybackService.pauseImmediately()
        spotifyFeedSkipRequestedUntil = System.currentTimeMillis() + 5000
        cancelSpotifyNaturalEndAdvanceJob()
        spotifyNaturalEndAdvanceJob = managerScope.launch {
            delay(200)
            if (!isSpotifyConnectPlaying) return@launch
            if (adoptSpotifyConnectNextIfMatching(spotifyPlaybackService.currentTrackUri.value)) {
                return@launch
            }
            if (computeHasNext()) {
                forceSpotifyFeedAdvanceToNextEntry(immediate = true)
            } else {
                _state.value = _state.value.copy(isPlaying = false)
                ScrubberClock.reset()
            }
        }
    }

    /** Spotify often auto-advances without pausing — onTrackEnded never fires; reconcile must drive advance. */
    private fun handleSpotifyNaturalFeedTrackEnd(reporting: String) {
        if (adoptSpotifyConnectNextIfMatching(reporting)) {
            return
        }
        handleSpotifyConnectTrackEnded()
    }

    /**
     * When App Remote already reports the Corus next entry, adopt it without
     * pause + re-play. Cancels pending natural-end force-advance so it cannot
     * skip an extra track after adopt.
     */
    private fun adoptSpotifyConnectNextIfMatching(uri: String?): Boolean {
        if (uri.isNullOrEmpty()) return false
        val idx = currentQueueIndex ?: return false
        if (idx + 1 >= queue.size) return false
        val next = queue[idx + 1]
        if (!spotifyURIMatchesTrack(uri, next)) return false
        cancelSpotifyNaturalEndAdvanceJob()
        advanceSpotifyToQueueIndex(idx + 1)
        val nextUri = spotifyURI(next)
        if (!spotifyPlaybackService.isPlaying.value) {
            userInitiatedPause = false
            spotifyFeedSkipRequestedUntil = System.currentTimeMillis() + 5000
            spotifyCorusRequestedUri = nextUri
            spotifyCorusRequestedUntil = System.currentTimeMillis() + 8000
            clearSpotifyRequestedTrackConfirmed()
            launchSpotifyConnectPlay {
                playViaSpotifyConnect(spotifyPendingPlay(next), replaceSpotifyQueue = true)
            }
        } else {
            // Already on the adopted track — settle before a leftover
            // following URI can chain another adopt.
            spotifyCorusRequestedUri = nextUri
            spotifyCorusRequestedUntil = System.currentTimeMillis() + 8000
            spotifyRequestedTrackConfirmed = true
            spotifyRequestedTrackConfirmedAtMs = System.currentTimeMillis()
        }
        return true
    }

    private fun cancelSpotifyNaturalEndAdvanceJob() {
        spotifyNaturalEndAdvanceJob?.cancel()
        spotifyNaturalEndAdvanceJob = null
    }

    private fun spotifyCorusPlayIntentInFlight(): Boolean {
        val until = spotifyCorusRequestedUntil ?: return false
        return System.currentTimeMillis() < until
    }

    private fun clearSpotifyRequestedTrackConfirmed() {
        spotifyRequestedTrackConfirmed = false
        spotifyRequestedTrackConfirmedAtMs = 0L
    }

    private fun spotifyRequestedTrackConfirmedForMs(): Long {
        if (!spotifyRequestedTrackConfirmed || spotifyRequestedTrackConfirmedAtMs <= 0L) return 0L
        return (System.currentTimeMillis() - spotifyRequestedTrackConfirmedAtMs).coerceAtLeast(0L)
    }

    private fun markSpotifyRequestedTrackConfirmedIfNeeded(
        uri: String? = spotifyPlaybackService.currentTrackUri.value,
    ) {
        if (spotifyRequestedTrackConfirmed) return
        val requested = spotifyCorusRequestedUri ?: return
        val reported = uri ?: return
        if (spotifyCorusRecentlyRequested(reported) ||
            spotifyUriMatchesExpected(reported, requested)
        ) {
            spotifyRequestedTrackConfirmed = true
            spotifyRequestedTrackConfirmedAtMs = System.currentTimeMillis()
        }
    }

    /**
     * During a Corus-initiated play handoff, Spotify often reports stale queue
     * state (e.g. the next feed entry) before the requested track starts. Ignore
     * those callbacks until the requested track has settled — including after
     * miniplayer Next, which used to disable this ignore and chain-skip.
     */
    private fun shouldIgnoreSpotifyReconcileDuringCorusHandoff(uri: String): Boolean {
        if (!spotifyCorusPlayIntentInFlight()) return false
        if (spotifyCorusRecentlyRequested(uri)) return true
        val idx = currentQueueIndex
        if (idx != null && idx < queue.size && spotifyURIMatchesTrack(uri, queue[idx])) {
            return true
        }
        // Stale leftover of the song we just left (Bobby ← Better Off Alone).
        val outgoing = spotifyPlaybackService.lastOutgoingTrackUri
        if (!outgoing.isNullOrEmpty() &&
            SpotifyPlaybackService.spotifyURIsMatch(uri, outgoing)
        ) {
            return true
        }
        // Stale native-queue next (Ladies play → Spotify briefly reports Unluck).
        val reportedIsNext = idx != null &&
            idx + 1 < queue.size &&
            spotifyURIMatchesTrack(uri, queue[idx + 1])
        if (SpotifyConnectWake.shouldIgnoreStaleNextDuringHandoff(
                playIntentInFlight = true,
                reportedIsNextQueueEntry = reportedIsNext,
                requestedTrackConfirmed = spotifyRequestedTrackConfirmed,
                confirmedForMs = spotifyRequestedTrackConfirmedForMs(),
            )
        ) {
            android.util.Log.i(
                "NowPlaying",
                "Ignoring stale next URI during Corus play handoff: $uri",
            )
            return true
        }
        return false
    }

    private fun corusAppIsBackgrounded(): Boolean =
        !ProcessLifecycleOwner.get().lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)

    /** User is not looking at Corus — ON_PAUSE (opened Spotify) or ON_STOP. */
    private fun corusAppIsAwayFromForeground(): Boolean =
        !ProcessLifecycleOwner.get().lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)

    private fun incomingLooksLikeLibraryPick(): Boolean =
        SpotifyConnectFastPath.contextLooksLikeLibraryPick(
            uri = spotifyPlaybackService.incomingContextUri
                ?: spotifyPlaybackService.currentContextUri,
            type = spotifyPlaybackService.currentContextType,
            title = spotifyPlaybackService.currentContextTitle,
        )

    /** User opened a Spotify page (context changed before the track). */
    private fun shouldRelinquishBecauseSpotifyAppTakeover(): Boolean {
        if (!isSpotifyConnectPlaying) return false
        if (!SpotifyConnectFastPath.shouldHonorContextTakeoverAsSpotifyTap(
                locked = spotifyDeviceLockedForQueueDriving,
                awayFromForeground = corusAppIsAwayFromForeground(),
                fullyBackgrounded = corusAppIsBackgrounded(),
            )
        ) {
            return false
        }
        return spotifyPlaybackService.contextTakeoverWithoutTrackChange
    }

    private fun markSpeculativeAwaySkip(reporting: String) {
        speculativeAwaySkipUri = reporting
        speculativeAwaySkipUntilMs = System.currentTimeMillis() + 6_000L
    }

    private fun clearSpeculativeAwaySkip() {
        speculativeAwaySkipUri = null
        speculativeAwaySkipUntilMs = 0L
    }

    /**
     * Control Center Next and a Liked Song tap look the same until context
     * arrives. Mute the unexpected track so the user doesn't hear ~2s of the
     * wrong song; resume on relinquish, leave paused on force-advance.
     */
    private fun maybeMuteAwayMisroute(reporting: String) {
        if (awaySkipMutedUri != null) return
        if (spotifyDeviceLockedForQueueDriving) return
        if (!corusAppIsAwayFromForeground()) return
        if (!isSpotifyConnectPlaying) return
        if (SpotifyContentUri.isUserChosenNonCorusContent(reporting)) return
        if (spotifyCorusPlayIntentInFlight() &&
            (
                spotifyCorusRecentlyRequested(reporting) ||
                    spotifyPlaybackService.lastOutgoingTrackUri?.let {
                        SpotifyPlaybackService.spotifyURIsMatch(reporting, it)
                    } == true
                )
        ) {
            return
        }
        awaySkipMutedUri = reporting
        spotifyPlaybackService.pauseImmediately()
    }

    private fun resumeAwaySkipMuteIfNeeded(externalUri: String?) {
        val muted = awaySkipMutedUri ?: spotifyPlaybackService.mutedUnexpectedUri
        awaySkipMutedUri = null
        spotifyPlaybackService.clearMutedUnexpectedUri()
        if (muted == null) return
        if (externalUri != null &&
            !SpotifyPlaybackService.spotifyURIsMatch(muted, externalUri)
        ) {
            return
        }
        spotifyPlaybackService.resumeImmediately()
    }

    private fun forceAdvancePossiblySpeculative(reporting: String) {
        if (!spotifyDeviceLockedForQueueDriving && corusAppIsAwayFromForeground()) {
            markSpeculativeAwaySkip(reporting)
        }
        forceSpotifyFeedAdvanceToNextEntry(immediate = true)
    }

    /**
     * Liked Songs / album arrived. Hand off now if we were waiting to decide,
     * or undo a speculative Corus jump.
     */
    private fun handleLibraryPickContext(): Boolean {
        if (!incomingLooksLikeLibraryPick()) return false
        if (maybeRollbackSpeculativeAwaySkip()) return true
        if (!isSpotifyConnectPlaying) return false
        if (!corusAppIsAwayFromForeground()) return false
        val pending = spotifyPendingExternalUri
            ?: spotifyPlaybackService.currentTrackUri.value
            ?: return false
        cancelDebouncedSpotifyRelinquish()
        relinquishSpotifyToExternalPlayback(pending)
        return true
    }

    private fun scheduleAwaySkipDecision(externalUri: String) {
        cancelDebouncedSpotifyRelinquish()
        spotifyPendingExternalUri = externalUri
        spotifyRelinquishJob = managerScope.launch {
            delay(SpotifyConnectFastPath.AWAY_SKIP_DECISION_MS)
            if (!isSpotifyConnectPlaying) return@launch
            if (incomingLooksLikeLibraryPick() ||
                shouldRelinquishBecauseSpotifyAppTakeover()
            ) {
                relinquishSpotifyToExternalPlayback(externalUri)
                return@launch
            }
            if (computeHasNext()) {
                forceAdvancePossiblySpeculative(externalUri)
            }
        }
    }

    private fun maybeRollbackSpeculativeAwaySkip(reporting: String? = null): Boolean {
        val keep = speculativeAwaySkipUri ?: return false
        if (System.currentTimeMillis() > speculativeAwaySkipUntilMs) {
            clearSpeculativeAwaySkip()
            return false
        }
        if (!incomingLooksLikeLibraryPick()) {
            return false
        }
        clearSpeculativeAwaySkip()
        managerScope.launch { rollbackSpeculativeAwaySkip(keep) }
        return true
    }

    private suspend fun rollbackSpeculativeAwaySkip(keepUri: String) {
        spotifyConnectPlayJob?.cancel()
        spotifyFeedSkipRequestedUntil = null
        spotifyCorusRequestedUri = null
        spotifyCorusRequestedUntil = null
        clearSpotifyRequestedTrackConfirmed()
        spotifyPlaybackService.clearFastPathPlaybackGuard()
        val alreadyOnKeep = spotifyPlaybackService.currentTrackUri.value?.let {
            SpotifyPlaybackService.spotifyURIsMatch(it, keepUri)
        } == true
        if (!alreadyOnKeep && keepUri.startsWith("spotify:track:")) {
            runCatching {
                spotifyPlaybackService.play(
                    spotifyTrackId = keepUri.removePrefix("spotify:track:"),
                    uri = keepUri,
                    replaceQueue = false,
                )
            }
        }
        if (isSpotifyConnectPlaying) {
            relinquishSpotifyToExternalPlayback(keepUri)
        } else {
            adoptExternalSpotifyPlayback(keepUri)
        }
    }

    /** iOS `applicationState != .active` — skip mini-player feed-scroll staging. */
    private fun shouldSkipSpotifyFeedAdvanceStaging(): Boolean =
        spotifyDeviceLockedForQueueDriving || corusAppIsAwayFromForeground()

    /** User started playing outside the Corus feed in Spotify — release App Remote without pausing Spotify, then mirror in the mini player. */
    fun relinquishSpotifyToExternalPlayback(reporting: String? = null) {
        if (!isSpotifyConnectPlaying) return
        val externalUri = reporting ?: spotifyPlaybackService.currentTrackUri.value
        // We may have muted this track while deciding CC Next vs Liked Songs.
        resumeAwaySkipMuteIfNeeded(externalUri)
        spotifyPlaybackService.clearContextTakeoverWithoutTrackChange()
        spotifyPlaybackService.muteUnexpectedWhileConnectPlaying = false
        spotifyPlaybackService.playQueueNextOnUnexpected = false
        spotifyPlaybackService.queueNextUri = null
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
        clearSpotifyRequestedTrackConfirmed()
        spotifyPendingExternalUri = null
        clearSpeculativeAwaySkip()
        isSpotifyConnectPlaying = false
        spotifyConnectStartedAt = null
        spotifyConnectWasPlaying = false
        userInitiatedPause = false
        externalSpotifyUserPaused = false
        _isResolvingSpotify.value = false
        resetSpotifyScrubAnchor()
        pauseSpotifyConnectTimePolling()
        stopSpotifyConnectKeepAlive(restoreAudioFocus = false)
        spotifyPlaybackService.resumeImmediately()
        spotifyScrubberHoldAtZero = false
        spotifyScrubberHoldUntilTrackChangeFromUri = null
        clearSpotifyScrubSeekInFlight()

        // Only songs can be mirrored in the mini player — a podcast has no Corus
        // song page, so just stop showing Corus as playing and leave Spotify alone.
        if (!externalUri.isNullOrEmpty() &&
            SpotifyContentUri.kindOf(externalUri) == SpotifyContentKind.TRACK
        ) {
            managerScope.launch { adoptExternalSpotifyPlayback(externalUri) }
        } else {
            _state.value = _state.value.copy(isPlaying = false)
            spotifyPlaybackService.disconnectPreservingPlayback()
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
        clearSpotifyScrubSeekInFlight()
        pauseExternalSpotifyTimePolling()
        if (!isSpotifyConnectPlaying) {
            clearExternalSpotifyAppRemoteSession()
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
            externalSpotifyUserPaused = false
            _state.value = _state.value.copy(isPlaying = svc.isPlaying.value || _state.value.isPlaying)
            clearSpotifyScrubberHold()
            if (svc.isConnected) {
                syncSpotifyScrubAnchor(svc.positionSeconds.value)
                syncExternalSpotifyScrubber()
                // Live App Remote during external mirror hitchs feed scroll —
                // drop IPC and advance the scrubber locally (iOS parity).
                clearExternalSpotifyAppRemoteSession()
            } else if (spotifyScrubAnchorWallTime == null) {
                syncSpotifyScrubAnchor(ScrubberClock.time.value / 1000.0)
            }
            return
        }

        clearSpotifyHandoffFailureSuppression()
        // Hold scrubber at 0 only while hydrating — released below once we seed
        // from App Remote (iOS clearSpotifyScrubberHold after adopt). Using the
        // incoming URI as holdFrom would pin the scrubber at 0 forever.
        beginSpotifyScrubberHoldAtZero(externalSpotifyTrackURI)
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
                        albumArtURL = meta.albumArtURL,
                        albumArtLargeURL = meta.albumArtURL,
                        spotifyURI = spotifyURI,
                        spotifyWebURL = "https://open.spotify.com/track/$trackId",
                        durationMs = durationMs,
                    )
                }
            } else if (track.albumArtURL.isNullOrBlank()) {
                // Web API sometimes omits images; App Remote still has cover art.
                val art = (appRemoteMeta ?: svc.appRemoteDisplayMetadata())?.albumArtURL
                if (!art.isNullOrBlank()) {
                    track = track.copy(albumArtURL = art, albumArtLargeURL = art)
                }
            }
            if (track == null) return

            externalSpotifyCachedTrack = track
            externalSpotifyTrackURI = spotifyURI
            _isExternalSpotifyListening.value = true
            isSpotifyConnectPlaying = false
            externalSpotifyUserPaused = false
            currentQueueIndex = null

            _state.value = NowPlayingState(
                trackId = track.id,
                trackName = track.name,
                artistName = track.artistName,
                albumArtURL = track.albumArtURL,
                albumArtLargeURL = track.albumArtLargeURL ?: track.albumArtURL,
                spotifyURI = track.spotifyURI,
                spotifyWebURL = track.spotifyWebURL,
                isrc = track.isrc,
                // iOS sets isPlaying = true after external adopt (App Remote may
                // briefly report paused during handoff).
                isPlaying = true,
                source = TrackSource.SPOTIFY,
                hasNext = false,
            )
            // iOS: clear hold, seed anchor + ScrubberClock from App Remote position.
            clearSpotifyScrubberHold()
            val positionSec = maxOf(0.0, svc.positionSeconds.value)
            val durationSec = when {
                svc.durationSeconds.value > 0 -> svc.durationSeconds.value
                track.durationMs > 0 -> track.durationMs / 1000.0
                else -> 0.0
            }
            syncSpotifyScrubAnchor(positionSec)
            if (durationSec > 0) {
                ScrubberClock.update((positionSec * 1000).toLong(), (durationSec * 1000).toLong())
            } else {
                ScrubberClock.snapTime((positionSec * 1000).toLong())
            }
            // Do NOT keep a live App Remote session for external mirroring —
            // that IPC path hitchs feed scroll until the next Corus-owned play.
            clearExternalSpotifyAppRemoteSession()
            startExternalSpotifyTimePolling()
        } finally {
            _isHydratingExternalSpotify.value = false
        }
    }

    private suspend fun reconcileExternalSpotifyOnForeground() {
        if (spotifyCorusPlayIntentInFlight()) return

        val svc = spotifyPlaybackService

        // External mirror intentionally drops App Remote after adopt. Briefly
        // reconnect on foreground so a song started in Spotify replaces Corus
        // identity, then adopt disconnects again.
        if (isExternalSpotifyListening && !svc.isConnected) {
            withBriefExternalSpotifyConnection { remote ->
                remote.refreshState()
                val uri = remote.currentTrackUri.value?.takeIf { it.isNotEmpty() }
                    ?: return@withBriefExternalSpotifyConnection true
                if (shouldSuppressExternalAdoption(uri)) {
                    remote.forcePauseAfterFailedHandoff()
                    return@withBriefExternalSpotifyConnection true
                }
                if (remote.isPlaying.value || isExternalSpotifyListening) {
                    adoptExternalSpotifyPlayback(uri)
                } else {
                    _state.value = _state.value.copy(isPlaying = false)
                }
                true
            }
            return
        }

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

    /** Clear App Remote callbacks + disconnect without pausing Spotify audio. */
    private fun clearExternalSpotifyAppRemoteSession() {
        spotifyPlaybackService.onPlayerTrackChanged = null
        spotifyPlaybackService.onTrackEnded = null
        spotifyPlaybackService.onPlayerStateUpdated = null
        spotifyPlaybackService.onPlayerContextUpdated = null
        spotifyPlaybackService.disconnectPreservingPlayback()
    }

    /**
     * Briefly reconnect App Remote for one external transport/reconcile call,
     * then drop it again so feed scroll isn’t hitching on Spotify IPC.
     * Play/pause chrome is never driven from App Remote here — only commands.
     */
    private suspend fun withBriefExternalSpotifyConnection(
        work: suspend (SpotifyPlaybackService) -> Boolean,
    ): Boolean {
        val svc = spotifyPlaybackService
        svc.onPlayerTrackChanged = null
        svc.onTrackEnded = null
        svc.onPlayerStateUpdated = null
        svc.onPlayerContextUpdated = null
        svc.allowProactiveReconnect()
        if (!svc.isConnected) {
            svc.attemptSilentReconnect()
        }
        svc.onPlayerTrackChanged = null
        svc.onTrackEnded = null
        svc.onPlayerStateUpdated = null
        svc.onPlayerContextUpdated = null

        val succeeded = if (svc.isConnected) {
            runCatching { work(svc) }.getOrDefault(false)
        } else {
            false
        }

        if (isExternalSpotifyListening) {
            clearExternalSpotifyAppRemoteSession()
        }
        return succeeded
    }

    private fun notifyExternalSpotifyTransportFailed() {
        hapticManager.notification(HapticManager.NotificationType.ERROR)
    }

    private fun syncExternalSpotifyScrubber() {
        var durationMs = (spotifyPlaybackService.durationSeconds.value * 1000).toLong()
        if (durationMs <= 0) {
            externalSpotifyCachedTrack?.durationMs?.takeIf { it > 0 }?.let { durationMs = it.toLong() }
        }
        if (durationMs <= 0) {
            durationMs = ScrubberClock.duration.value
        }
        val timeMs = (interpolatedSpotifyPosition() * 1000).toLong()
        if (durationMs > 0) {
            ScrubberClock.update(timeMs, durationMs)
        } else {
            ScrubberClock.snapTime(timeMs)
        }
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
        // External chrome play/pause is local. Scrubber advances from the
        // wall-clock anchor — never from live App Remote play/pause samples.
        if (spotifyScrubberHoldAtZero) {
            clearSpotifyScrubberHold()
            if (spotifyScrubAnchorWallTime == null) {
                syncSpotifyScrubAnchor(ScrubberClock.time.value / 1000.0)
            }
        }
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
        // ON_PAUSE (opened Spotify) is still STARTED — require "not resumed",
        // not full ON_STOP, or we never relinquish and then force-advance.
        if (!corusAppIsAwayFromForeground()) return false
        if (spotifyOutgoingChangeWasNaturalFeedTrackEnd()) return false

        // A context change is the one reliable manual-pick signal: tapping a song
        // in Spotify sets an album/playlist/search context, while misrouted
        // lock-screen skips keep the context Corus started. "URI not in queue"
        // alone must NOT relinquish — stale Spotify queue entries from misrouted
        // skips also aren't in the Corus queue.
        return incomingLooksLikeLibraryPick()
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
        if (spotifyCorusPlayIntentInFlight() &&
            (
                spotifyCorusRecentlyRequested(reporting) ||
                    spotifyPlaybackService.lastOutgoingTrackUri?.let {
                        SpotifyPlaybackService.spotifyURIsMatch(reporting, it)
                    } == true
                )
        ) {
            return false
        }
        val idx = currentQueueIndex
        if (idx != null && idx < queue.size && spotifyURIMatchesTrack(reporting, queue[idx])) {
            return false
        }
        // In the feed a handoff blip must not skip, even if lock state is stale.
        if (!corusAppIsAwayFromForeground()) return false
        if (spotifyDeviceLockedForQueueDriving) return true
        val svc = spotifyPlaybackService
        return SpotifyConnectFastPath.shouldForceAdvanceWhenAway(
            awaitingContext = svc.isAwaitingIncomingContext(),
            previousContext = svc.lastOutgoingContextUri,
            incomingContext = svc.incomingContextUri,
            incomingType = svc.currentContextType,
            incomingTitle = svc.currentContextTitle,
        )
    }

    private fun scheduleDebouncedSpotifyExternalPlaybackDecision(externalUri: String) {
        cancelDebouncedSpotifyRelinquish()
        spotifyPendingExternalUri = externalUri
        spotifyRelinquishJob = managerScope.launch {
            delay(600)
            if (!isSpotifyConnectPlaying) return@launch
            if (spotifyCorusPlayIntentInFlight() &&
                (
                    spotifyCorusRecentlyRequested(externalUri) ||
                        spotifyPlaybackService.lastOutgoingTrackUri?.let {
                            SpotifyPlaybackService.spotifyURIsMatch(externalUri, it)
                        } == true
                    )
            ) {
                return@launch
            }
            if (shouldRelinquishForManualSpotifyPlayback(externalUri)) {
                relinquishSpotifyToExternalPlayback(externalUri)
                return@launch
            }
            if (shouldRelinquishBecauseCorusPausedAndExternalTrack(externalUri)) {
                relinquishSpotifyToExternalPlayback(externalUri)
                return@launch
            }
            if (corusSpotifySessionSuspended()) return@launch
            if (spotifyPlaybackService.isAwaitingIncomingContext()) {
                delay(1_000)
            }
            if (shouldRelinquishForManualSpotifyPlayback(externalUri)) {
                relinquishSpotifyToExternalPlayback(externalUri)
                return@launch
            }
            if (!spotifyDeviceLockedForQueueDriving && corusAppIsAwayFromForeground()) {
                val svc = spotifyPlaybackService
                if (SpotifyConnectFastPath.shouldForceAdvanceWhenAway(
                        awaitingContext = svc.isAwaitingIncomingContext(),
                        previousContext = svc.lastOutgoingContextUri,
                        incomingContext = svc.incomingContextUri,
                        incomingType = svc.currentContextType,
                        incomingTitle = svc.currentContextTitle,
                    ) && computeHasNext()
                ) {
                    forceAdvancePossiblySpeculative(externalUri)
                    return@launch
                }
                if (SpotifyConnectFastPath.isTrackLevelContext(svc.lastOutgoingContextUri) &&
                    svc.lastOutgoingContextUri == svc.incomingContextUri
                ) {
                    delay(800)
                    if (!isSpotifyConnectPlaying) return@launch
                    if (shouldRelinquishForManualSpotifyPlayback(externalUri)) {
                        relinquishSpotifyToExternalPlayback(externalUri)
                        return@launch
                    }
                    if (computeHasNext()) {
                        forceAdvancePossiblySpeculative(externalUri)
                    }
                }
                return@launch
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
        refreshSpotifyQueueNextUri()
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
            bandcampUrl = track.bandcampUrl,
            notOnSpotify = track.notOnSpotify,
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

    private fun spotifyURI(track: QueuedTrack): String {
        track.spotifyURI?.takeIf { it.startsWith("spotify:track:") }?.let { return it }
        val normalized = SpotifyPlaybackService.normalizedSpotifyTrackId(track.trackId)
        if (normalized.length == 22 &&
            !track.trackId.startsWith("am:") &&
            !track.trackId.startsWith("sc:") &&
            !track.trackId.startsWith("amk:")
        ) {
            return "spotify:track:$normalized"
        }
        return track.spotifyURI.orEmpty()
    }

    private fun refreshSpotifyQueueNextUri() {
        val idx = currentQueueIndex
        val next = idx?.let { queue.getOrNull(it + 1) }
        spotifyPlaybackService.queueNextUri = next?.let { spotifyURI(it) }
    }

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
        if (!spotifyExperimentEnabledForTrack(next)) return
        armSpotifyFastPathSkipGuard(expectedURI = spotifyURI(next), durationMs = durationMs)
    }

    /** Pre-arm while locked/backgrounded so a native Spotify skip is silenced early. */
    private fun refreshSpotifyFastPathSkipGuardWhenLocked() {
        refreshSpotifyFastPathSkipGuardForUpcomingTrackIfNeeded()
    }

    private fun refreshSpotifyFastPathSkipGuardForUpcomingTrackIfNeeded() {
        val lockedOrBackgrounded =
            spotifyDeviceLockedForQueueDriving || corusAppIsBackgrounded()
        if (!SpotifyConnectFastPath.shouldArmUpcomingSkipGuard(
                connectPlaying = isSpotifyConnectPlaying,
                hasNext = computeHasNext(),
                feedSkipInFlight = spotifyFeedSkipRequestedUntil?.let {
                    System.currentTimeMillis() < it
                } == true,
                playIntentInFlight = spotifyCorusPlayIntentInFlight(),
                lockedOrBackgrounded = lockedOrBackgrounded,
                positionSec = spotifyPlaybackService.positionSeconds.value,
                durationSec = spotifyPlaybackService.durationSeconds.value,
                requestedTrackConfirmed = spotifyRequestedTrackConfirmed,
            )
        ) {
            return
        }
        refreshSpotifyQueueNextUri()
        armSpotifyFastPathSkipGuardForUpcomingFeedTrack(
            durationMs = SpotifyConnectFastPath.guardDurationMs(lockedOrBackgrounded),
        )
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
        refreshSpotifyFastPathSkipGuardForUpcomingTrackIfNeeded()
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
        // After external adopt we disconnect App Remote; advance from Corus play flag.
        if (isExternalSpotifyListening) return _state.value.isPlaying
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

    /** iOS `clearSpotifyScrubberHold` — let the scrubber advance again. */
    private fun clearSpotifyScrubberHold() {
        spotifyScrubberHoldAtZero = false
        spotifyScrubberHoldUntilTrackChangeFromUri = null
    }

    private fun beginSpotifyScrubSeekInFlight(targetSec: Double) {
        spotifyScrubSeekTargetSec = maxOf(0.0, targetSec)
        spotifyScrubSeekInFlightUntilMs = System.currentTimeMillis() + 2_500L
    }

    private fun clearSpotifyScrubSeekInFlight() {
        spotifyScrubSeekInFlightUntilMs = null
        spotifyScrubSeekTargetSec = null
    }

    /** iOS `spotifyScrubSeekInFlight()`. */
    private fun spotifyScrubSeekInFlight(): Boolean {
        val until = spotifyScrubSeekInFlightUntilMs ?: return false
        if (System.currentTimeMillis() >= until) {
            clearSpotifyScrubSeekInFlight()
            return false
        }
        return true
    }

    private fun maybeCompleteSpotifyScrubSeekInFlight(reportedSec: Double) {
        val target = spotifyScrubSeekTargetSec ?: return
        if (kotlin.math.abs(reportedSec - target) <= 2.0) {
            syncSpotifyScrubAnchor(reportedSec)
            clearSpotifyScrubSeekInFlight()
        }
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
        // External Spotify with App Remote torn down: wall-clock only.
        if (isExternalSpotifyListening && !spotifyPlaybackService.isConnected) {
            if (!spotifyScrubberShouldAdvance()) {
                return if (spotifyScrubSeekInFlight()) {
                    spotifyScrubAnchorPosition
                } else {
                    ScrubberClock.time.value / 1000.0
                }
            }
            val anchorTime = spotifyScrubAnchorWallTime ?: run {
                val seeded = ScrubberClock.time.value / 1000.0
                syncSpotifyScrubAnchor(seeded)
                return seeded
            }
            val elapsed = (System.currentTimeMillis() - anchorTime) / 1000.0
            var time = spotifyScrubAnchorPosition + elapsed
            val durationSec = ScrubberClock.duration.value / 1000.0
            if (durationSec > 0) time = minOf(time, durationSec)
            return maxOf(0.0, time)
        }
        if (!spotifyScrubberShouldAdvance()) {
            // Keep the post-scrub clock stable while Spotify briefly reports paused.
            if (spotifyScrubSeekInFlight()) {
                return spotifyScrubAnchorPosition
            }
            spotifyScrubAnchorWallTime = null
            spotifyScrubAnchorPosition = reported
            return reported
        }
        val anchorTime = spotifyScrubAnchorWallTime ?: run {
            if (spotifyScrubSeekInFlight()) {
                return spotifyScrubSeekTargetSec ?: reported
            }
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
