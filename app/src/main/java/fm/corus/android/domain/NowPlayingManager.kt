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
import fm.corus.android.data.model.MusicService
import fm.corus.android.data.model.TrackSource
import fm.corus.android.data.remote.CloudFunctionsDataSource
import fm.corus.android.data.remote.TidalPlaylistService
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
    private var autoplayEnabled: Boolean = true

    private var spotifyCorusBackgroundedAt: Long? = null
    private var spotifyDeviceLockedForQueueDriving = false

    init {
        // Let the trailer coordinator pause music when a trailer starts, keeping
        // the two audio sources mutually exclusive without a direct dependency.
        TrailerPlaybackCoordinator.pauseMusic = { pause() }
        // Same for an audio caption: starting one pauses the song.
        VoiceNotePlayerManager.pauseMusic = { pause() }
        managerScope.launch {
            preferencesDataStore.autoplayNextSong.collect { autoplayEnabled = it }
        }
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
                    }
                    Lifecycle.Event.ON_START -> spotifyCorusBackgroundedAt = null
                    else -> Unit
                }
            },
        )
        val keyguard = context.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
        spotifyDeviceLockedForQueueDriving = keyguard.isKeyguardLocked
        val lockReceiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context?, intent: Intent?) {
                spotifyDeviceLockedForQueueDriving = when (intent?.action) {
                    Intent.ACTION_SCREEN_OFF -> true
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

    val currentSourcePostId: String? get() = _state.value.sourcePostId
    val isPreviewMode: Boolean
        get() = _state.value.hasActiveTrack && !isSpotifyConnectPlaying && player != null

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
    private var spotifyRelinquishJob: Job? = null
    private var spotifyNaturalEndAdvanceJob: Job? = null
    private var spotifyExpectedTrackUri: String? = null
    private var spotifyQueueTransitionUntil: Long? = null
    private val spotifyUriToQueueIndex = mutableMapOf<String, Int>()
    private val spotifyPlaybackUriCache = mutableMapOf<String, String>()
    private val spotifyAbsentTrackIds = mutableSetOf<String>()
    private var spotifyScrubAnchorWallTime: Long? = null
    private var spotifyScrubAnchorPosition = 0.0
    private var spotifyPositionJob: Job? = null
    private var spotifySeekJob: Job? = null

    fun spotifyExperimentEnabledForTrack(source: TrackSource): Boolean {
        if (!remoteConfigService.spotifyAuthExperimentEnabled) return false
        return SongPlayRouting.wantsSpotifyExperiment(
            source = source,
            service = musicServicePreference.current.value,
            experimentEnabled = true,
            playFullSongs = preferencesDataStore.playFullSongsSync(),
        )
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
        onPreview: suspend () -> Unit,
    ) {
        managerScope.launch {
            val outcome = FullSongPlayCoordinator.playTapOutcome(
                track = track,
                sourcePostId = sourcePostId,
                queue = queue,
                nowPlaying = this@NowPlayingManager,
                remoteConfig = remoteConfigService,
                musicService = musicServicePreference.current.value,
                playFullSongs = preferencesDataStore.playFullSongsSync(),
            )
            FullSongPlayCoordinator.applyPlayTapOutcome(
                outcome = outcome,
                nowPlaying = this@NowPlayingManager,
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
        if (isSpotifyConnectPlaying && remoteConfigService.spotifyAuthExperimentEnabled) {
            val seconds = toMs / 1000.0
            syncSpotifyScrubAnchor(seconds)
            ScrubberClock.setTime(toMs)
            spotifySeekJob?.cancel()
            spotifySeekJob = managerScope.launch {
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

    fun clearPaywallRequested() {
        _paywallRequested.value = false
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
        } catch (e: CloudFunctionsDataSource.PaywallRequiredException) {
            _paywallRequested.value = true
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
        } catch (e: CloudFunctionsDataSource.PaywallRequiredException) {
            _paywallRequested.value = true
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
        } catch (e: CloudFunctionsDataSource.PaywallRequiredException) {
            _paywallRequested.value = true
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
                    _paywallRequested.value = true
                is CloudFunctionsDataSource.PlaylistTracksOutcome.Failure ->
                    _playlistError.value = if (outcome.soundcloudSkipped > 0)
                        "Your feed only has SoundCloud tracks — playlists aren't available for those."
                    else
                        "No tracks found in your feed yet."
                is CloudFunctionsDataSource.PlaylistTracksOutcome.Tracks ->
                    buildTidalPlaylist(feedPlaylistName(feedMode), "Generated by Corus", outcome.descriptors, outcome.soundcloudSkipped)
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
                    _paywallRequested.value = true
                is CloudFunctionsDataSource.PlaylistTracksOutcome.Failure ->
                    _playlistError.value = if (outcome.soundcloudSkipped > 0)
                        "This profile only has SoundCloud tracks — playlists aren't available for those."
                    else
                        "No songs to add to a playlist yet."
                is CloudFunctionsDataSource.PlaylistTracksOutcome.Tracks -> {
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
                    _paywallRequested.value = true
                is CloudFunctionsDataSource.PlaylistTracksOutcome.Failure ->
                    _playlistError.value = if (outcome.soundcloudSkipped > 0)
                        "This hashtag only has SoundCloud tracks, so playlists aren't available for those."
                    else
                        "No songs to add to a playlist yet."
                is CloudFunctionsDataSource.PlaylistTracksOutcome.Tracks -> {
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

    private suspend fun playInternal(track: QueuedTrack, userInitiated: Boolean = true) {
        val trackId = track.trackId

        // Audio sources are mutually exclusive: starting music stops any inline
        // trailer and any playing audio caption so they never play over each other.
        TrailerPlaybackCoordinator.stopAll()
        VoiceNotePlayerManager.stopActivePlayer()

        // If the SAME post's track is already playing, toggle pause/play. A
        // *different* post of the same song (same trackId, different sourcePostId)
        // is a distinct feed card, so it falls through and switches playback to it
        // instead of pausing. See [isReTapOfActiveEntry] / [PostPlaybackHighlight].
        if (player != null &&
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
            // Audiomack plays a signed ~30s preview resolved at play time (short-
            // lived; never cached on the post), exactly like the Spotify/Apple
            // preview path. The raw Audiomack id is recovered from the `amk:`
            // trackId prefix. It never falls through to the Apple-preview lookup
            // (which would play a WRONG track for an Audiomack id); a failed
            // resolve yields null → no-op, and the full song stays reachable via
            // the "Listen on Audiomack" link-out.
            TrackSource.AUDIOMACK -> audiomackIdFromTrackId(trackId)?.let { resolveAudiomackPreview(it) }
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
     * The URL is short-lived — resolved at play time, never persisted. Returns
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

    /** Auto-advance to the next queued preview when enabled by user setting. */
    private fun handlePlaybackEnded() {
        if (!autoplayEnabled) return
        advanceToNext()
    }

    fun skipToNext() {
        if (shouldRouteSpotifyFeedSkip()) {
            cancelDebouncedSpotifyRelinquish()
            spotifyFeedSkipRequestedUntil = System.currentTimeMillis() + 5000
            forceSpotifyFeedAdvanceToNextEntry()
            return
        }
        stopPositionPolling(resetClock = false)
        if (isSpotifyConnectPlaying) resetSpotifyScrubAnchor() else ScrubberClock.reset()
        advanceToNext()
    }

    fun shouldRouteSpotifyFeedSkip(): Boolean {
        if (!remoteConfigService.spotifyAuthExperimentEnabled) return false
        if (musicServicePreference.current.value != MusicService.SPOTIFY) return false
        if (!_state.value.hasActiveTrack) return false
        val idx = currentQueueIndex ?: return isSpotifyConnectPlaying
        val next = queue.getOrNull(idx + 1) ?: return isSpotifyConnectPlaying
        if (!SongPlayRouting.wantsSpotifyExperiment(
                next.source,
                MusicService.SPOTIFY,
                experimentEnabled = true,
                playFullSongs = preferencesDataStore.playFullSongsSync(),
            )
        ) {
            return false
        }
        return isSpotifyConnectPlaying || spotifyExperimentEnabledForTrack(next.source)
    }

    fun forceSpotifyFeedAdvanceToNextEntry() {
        if (!remoteConfigService.spotifyAuthExperimentEnabled) {
            skipToNextLegacyPreview()
            return
        }
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
            ScrubberClock.reset()
            advanceToNext()
            return
        }
        userInitiatedPause = false
        manualSkipGuardUntil = System.currentTimeMillis() + 1500
        spotifyFeedSkipRequestedUntil = System.currentTimeMillis() + 5000
        cancelDebouncedSpotifyRelinquish()
        stopPositionPolling(resetClock = false)
        ScrubberClock.reset()
        spotifyExpectedTrackUri = null
        spotifyQueueTransitionUntil = null
        val nextUri = spotifyURI(next)
        spotifyCorusRequestedUri = nextUri
        spotifyCorusRequestedUntil = System.currentTimeMillis() + 8000
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
                if (shouldRouteSpotifyFeedSkip()) {
                    cancelDebouncedSpotifyRelinquish()
                    spotifyFeedSkipRequestedUntil = System.currentTimeMillis() + 5000
                    this@NowPlayingManager.forceSpotifyFeedAdvanceToNextEntry()
                } else {
                    this@NowPlayingManager.skipToNext()
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
        if (isSpotifyConnectPlaying && remoteConfigService.spotifyAuthExperimentEnabled) {
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
        if (isSpotifyConnectPlaying) {
            managerScope.launch { spotifyPlaybackService.pause() }
            _state.value = _state.value.copy(isPlaying = false)
            return
        }
        player?.pause()
        _state.value = _state.value.copy(isPlaying = false)
    }

    fun resume() {
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
        if (!remoteConfigService.spotifyAuthExperimentEnabled) {
            android.util.Log.w(
                "SpotifyPlayback",
                "playViaSpotifyConnect skipped — spotify_auth_experiment_enabled=false",
            )
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

        spotifyConnectPlayGeneration += 1
        val generation = spotifyConnectPlayGeneration

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
        )
        currentQueueIndex = resolveQueueIndex(pending.trackId, pending.sourcePostId)
        _isResolvingSpotify.value = true
        spotifyConnectStartedAt = System.currentTimeMillis()
        if (!wasActive) spotifyConnectWasPlaying = false
        player?.pause()

        val expectedTrackId = pending.trackId
        val replaceQueue = replaceSpotifyQueue || !wasActive
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

            spotifyPlaybackService.play(
                spotifyTrackId = pending.trackId,
                uri = resolvedUri,
                replaceQueue = replaceQueue,
                queueSessionId = queueSession,
            )
        } catch (e: Exception) {
            android.util.Log.w("NowPlaying", "Spotify play failed: ${e.message}")
            if (_state.value.trackId == expectedTrackId) {
                handleSpotifyPlaybackFailure(queuedTrackFrom(pending), userInitiated = true)
            }
            return
        } finally {
            if (spotifyConnectPlayGeneration == generation) {
                _isResolvingSpotify.value = false
            }
        }

        if (_state.value.trackId != expectedTrackId) return

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

        if (!verifySpotifyConnectPlaybackStarted() && _state.value.trackId == expectedTrackId) {
            android.util.Log.w("SpotifyPlayback", "Connect verify timed out — falling back to preview")
            isSpotifyConnectPlaying = false
            _state.value = _state.value.copy(isPlaying = false)
            resetSpotifyScrubAnchor()
            pauseSpotifyConnectTimePolling()
            handleSpotifyPlaybackFailure(queuedTrackFrom(pending), userInitiated = true)
        }
    }

    private fun installSpotifyConnectDelegates() {
        spotifyPlaybackService.onTrackEnded = { handleSpotifyConnectTrackEnded() }
        spotifyPlaybackService.onPlayerTrackChanged = { uri -> reconcileSpotifyQueuePosition(uri) }
        spotifyPlaybackService.onPlayerStateUpdated = {
            if (remoteConfigService.spotifyAuthExperimentEnabled && isSpotifyConnectPlaying) {
                syncSpotifyScrubAnchor(spotifyPlaybackService.positionSeconds.value)
            }
        }
    }

    private suspend fun verifySpotifyConnectPlaybackStarted(): Boolean {
        repeat(40) { tick ->
            if (spotifyPlaybackService.isPlaying.value) return true
            if (tick % 3 == 0) spotifyPlaybackService.refreshState()
            delay(100)
        }
        return false
    }

    private suspend fun handleSpotifyPlaybackFailure(track: QueuedTrack, userInitiated: Boolean) {
        android.util.Log.w("SpotifyPlayback", "Falling back to 30s preview for ${track.trackId}")
        _isResolvingSpotify.value = false
        isSpotifyConnectPlaying = false
        silentlyHaltSpotifyForPreview()
        playInternal(track, userInitiated = userInitiated)
    }

    fun silentlyHaltSpotifyForPreview() {
        spotifyConnectPlayJob?.cancel()
        cancelDebouncedSpotifyRelinquish()
        spotifyPlaybackService.onPlayerTrackChanged = null
        spotifyPlaybackService.onTrackEnded = null
        spotifyPlaybackService.onPlayerStateUpdated = null
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
    }

    private fun reconcileSpotifyQueuePosition(uri: String) {
        if (!isSpotifyConnectPlaying || _isResolvingSpotify.value) return
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

        if (spotifyOutgoingChangeWasNaturalFeedTrackEnd()) return

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
            if (!computeHasNext()) return
            forceSpotifyFeedAdvanceToNextEntry()
            return
        }
        if (shouldForceSpotifyFeedAdvanceForMisroutedSkip(reporting)) {
            forceSpotifyFeedAdvanceToNextEntry()
            return
        }
        if (shouldRelinquishForManualSpotifyPlayback(reporting)) {
            if (corusAppIsBackgrounded()) {
                relinquishSpotifyToExternalPlayback()
            } else {
                scheduleDebouncedSpotifyExternalPlaybackDecision(reporting)
            }
            return
        }
        if (!computeHasNext()) return
        scheduleDebouncedSpotifyExternalPlaybackDecision(reporting)
    }

    private fun handleSpotifyConnectTrackEnded() {
        if (!isSpotifyConnectPlaying) return
        // Mute stale Spotify queue auto-advance immediately — the 400ms debounce
        // below is only for coalescing duplicate end signals, not for waiting.
        spotifyPlaybackService.pauseImmediately()
        spotifyFeedSkipRequestedUntil = System.currentTimeMillis() + 5000
        spotifyNaturalEndAdvanceJob?.cancel()
        spotifyNaturalEndAdvanceJob = managerScope.launch {
            delay(200)
            if (!isSpotifyConnectPlaying) return@launch
            if (autoplayEnabled && computeHasNext()) {
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

    private fun corusAppIsBackgrounded(): Boolean =
        !ProcessLifecycleOwner.get().lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)

    private fun corusAppIsInactiveLike(): Boolean {
        val state = ProcessLifecycleOwner.get().lifecycle.currentState
        return state.isAtLeast(Lifecycle.State.STARTED) &&
            !state.isAtLeast(Lifecycle.State.RESUMED)
    }

    /** User started playing outside the Corus feed in Spotify — release App Remote without pausing Spotify. */
    fun relinquishSpotifyToExternalPlayback() {
        if (!isSpotifyConnectPlaying) return
        spotifyConnectPlayJob?.cancel()
        cancelDebouncedSpotifyRelinquish()
        spotifyPlaybackService.onPlayerTrackChanged = null
        spotifyPlaybackService.onTrackEnded = null
        spotifyPlaybackService.onPlayerStateUpdated = null
        spotifyUriToQueueIndex.clear()
        spotifyExpectedTrackUri = null
        spotifyQueueTransitionUntil = null
        spotifyCorusRequestedUri = null
        spotifyCorusRequestedUntil = null
        spotifyPendingExternalUri = null
        isSpotifyConnectPlaying = false
        _state.value = _state.value.copy(isPlaying = false)
        spotifyConnectStartedAt = null
        spotifyConnectWasPlaying = false
        userInitiatedPause = false
        _isResolvingSpotify.value = false
        resetSpotifyScrubAnchor()
        pauseSpotifyConnectTimePolling()
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

    private fun shouldRelinquishForManualSpotifyPlayback(reporting: String): Boolean {
        if (!isSpotifyConnectPlaying) return false
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
        // A manual Spotify pick (backgrounded + unlocked + manual signals) must
        // relinquish, not force — the "URI not in queue" heuristic below would
        // otherwise hijack every manual pick.
        if (shouldRelinquishForManualSpotifyPlayback(reporting)) return false
        if (spotifyFeedSkipRequestedUntil?.let { System.currentTimeMillis() < it } == true) return true
        if (spotifyCorusPlayIntentInFlight()) return true
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
            if (shouldRelinquishForManualSpotifyPlayback(externalUri)) {
                relinquishSpotifyToExternalPlayback()
                return@launch
            }
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
        currentQueueIndex = index
        updateStateForTrack(track)
        _state.value = _state.value.copy(isPlaying = spotifyPlaybackService.isPlaying.value)
        syncSpotifyScrubAnchor(spotifyPlaybackService.positionSeconds.value)
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
        val timeSec = interpolatedSpotifyPosition()
        val durationSec = spotifyPlaybackService.durationSeconds.value
        val durationMs = if (durationSec > 0) (durationSec * 1000).toLong()
        else ScrubberClock.duration.value
        ScrubberClock.update((timeSec * 1000).toLong(), durationMs)
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
        val reported = spotifyPlaybackService.positionSeconds.value
        val shouldAdvance = spotifyPlaybackService.isPlaying.value ||
            (_state.value.isPlaying && isSpotifyConnectPlaying)
        if (!shouldAdvance) {
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
