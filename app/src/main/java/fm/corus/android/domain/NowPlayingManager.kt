package fm.corus.android.domain

import android.content.Context
import android.content.Intent
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
import fm.corus.android.data.model.TrackSource
import fm.corus.android.data.remote.CloudFunctionsDataSource
import fm.corus.android.data.repository.UserRepository
import fm.corus.android.service.CorusPlaybackService
import fm.corus.android.ui.components.ToastManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.inject.Inject
import javax.inject.Singleton

data class QueuedTrack(
    val trackId: String,
    val trackName: String,
    val artistName: String,
    val albumArtURL: String?,
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
)

data class NowPlayingState(
    val trackId: String? = null,
    val trackName: String = "",
    val artistName: String = "",
    val albumArtURL: String? = null,
    val spotifyURI: String? = null,
    val spotifyWebURL: String? = null,
    val isPlaying: Boolean = false,
    val sourcePostId: String? = null,
    val hasNext: Boolean = false,
    val source: TrackSource = TrackSource.SPOTIFY,
    val soundcloudPermalinkUrl: String? = null,
) {
    val hasActiveTrack: Boolean get() = trackId != null
}

@Singleton
class NowPlayingManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val cloudFunctions: CloudFunctionsDataSource,
    private val preferencesDataStore: PreferencesDataStore,
    private val userRepository: UserRepository,
) {
    private val managerScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    @Volatile
    private var autoplayEnabled: Boolean = true

    init {
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

    suspend fun generateFeedPlaylist(newReleasesOnly: Boolean = false) {
        _isGeneratingPlaylist.value = true
        ToastManager.show("Generating playlist\u2026")

        if (!isNetworkAvailable()) {
            _playlistError.value = "Couldn't connect. Check your connection."
            _isGeneratingPlaylist.value = false
            return
        }

        try {
            val result = cloudFunctions.generateFeedPlaylist(newReleasesOnly)
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
            _playlistError.value = "Something went wrong. Try again later."
        }
        _isGeneratingPlaylist.value = false
    }

    suspend fun generateProfilePlaylist(
        userId: String,
        source: CloudFunctionsDataSource.ProfilePlaylistSource = CloudFunctionsDataSource.ProfilePlaylistSource.Posts,
    ) {
        _isGeneratingPlaylist.value = true
        ToastManager.show("Generating playlist\u2026")

        if (!isNetworkAvailable()) {
            _playlistError.value = "Couldn't connect. Check your connection."
            _isGeneratingPlaylist.value = false
            return
        }

        try {
            val result = cloudFunctions.generateProfilePlaylist(userId, source)
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
            _playlistError.value = "Something went wrong. Try again later."
        }
        _isGeneratingPlaylist.value = false
    }

    val isPlaying: Boolean get() = _state.value.isPlaying
    val currentTrackId: String? get() = _state.value.trackId

    /** Play a track that's part of a queue — enables autoplay and the mini-player next button. */
    suspend fun play(track: QueuedTrack, queue: List<QueuedTrack>) {
        this.queue = queue
        this.currentQueueIndex = queue.indexOfFirst { it.trackId == track.trackId }.takeIf { it >= 0 }
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
        currentQueueIndex = currentTrackId?.let { id ->
            newQueue.indexOfFirst { it.trackId == id }.takeIf { it >= 0 }
        }
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
        val curId = _state.value.trackId
        currentQueueIndex = curId?.let { id ->
            pruned.indexOfFirst { it.trackId == id }.takeIf { it >= 0 }
        }
        _state.value = _state.value.copy(hasNext = computeHasNext())
    }

    suspend fun play(
        trackId: String,
        trackName: String,
        artistName: String,
        albumArtURL: String?,
        previewUrl: String?,
        spotifyURI: String? = null,
        spotifyWebURL: String? = null,
        isrc: String? = null,
        sourcePostId: String? = null,
        source: TrackSource = TrackSource.SPOTIFY,
        soundcloudId: String? = null,
        soundcloudPermalinkUrl: String? = null,
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
                previewUrl = previewUrl,
                spotifyURI = spotifyURI,
                spotifyWebURL = spotifyWebURL,
                isrc = isrc,
                sourcePostId = sourcePostId,
                source = source,
                soundcloudId = soundcloudId,
                soundcloudPermalinkUrl = soundcloudPermalinkUrl,
            ),
        )
    }

    private suspend fun playInternal(track: QueuedTrack) {
        val trackId = track.trackId

        // If same track is already playing, toggle pause/play
        if (_state.value.trackId == trackId && player != null) {
            togglePlayPause()
            return
        }

        // If same track is loading, cancel the request
        if (_loadingTrackId.value == trackId) {
            cancelLoading()
            return
        }

        // Cancel any in-flight load for a different track
        cancelLoading()

        // Signal loading state
        _loadingTrackId.value = trackId
        val generation = ++playGeneration

        // Resolve playback URL.
        //   SoundCloud → fetch a fresh signed HLS URL (short-lived, never cached on the post).
        //   Spotify/Apple → use the 30s preview URL (looked up server-side via Apple Music).
        val resolvedUrl = if (track.source == TrackSource.SOUNDCLOUD) {
            track.soundcloudId?.let { resolveSoundCloudStream(it) }
        } else {
            track.previewUrl?.takeIf { it.isNotBlank() }
                ?: previewCache[trackId]
                ?: lookupPreviewUrl(trackId, track.trackName, track.artistName, track.isrc)
        }

        // If cancelled while resolving, bail out
        if (generation != playGeneration) return

        _loadingTrackId.value = null

        if (resolvedUrl == null) return

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
            spotifyURI = track.spotifyURI,
            spotifyWebURL = track.spotifyWebURL,
            isPlaying = true,
            sourcePostId = track.sourcePostId,
            hasNext = computeHasNext(),
            source = track.source,
            soundcloudPermalinkUrl = track.soundcloudPermalinkUrl,
        )
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

    /** Auto-advance to the next queued preview when enabled by user setting. */
    private fun handlePlaybackEnded() {
        if (!autoplayEnabled) return
        advanceToNext()
    }

    fun skipToNext() {
        // Snap scrubber to 0 instantly so it doesn't briefly tween forward
        // on the outgoing track while the next one is loading. Mirrors the
        // iOS resetScrubberPosition() behavior.
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
                playInternal(localNext)
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
            playInternal(next)
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
                this@NowPlayingManager.skipToNext()
            }

            override fun seekToNextMediaItem() {
                this@NowPlayingManager.skipToNext()
            }

            override fun hasNextMediaItem(): Boolean = computeHasNext()

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
        mediaSession = MediaSession.Builder(context, sessionPlayer).build()
        return exo
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
    }

    fun togglePlayPause() {
        val p = player ?: return
        if (p.isPlaying) {
            p.pause()
            _state.value = _state.value.copy(isPlaying = false)
        } else {
            if (p.playbackState == Player.STATE_ENDED) {
                p.seekTo(0)
            }
            p.play()
            _state.value = _state.value.copy(isPlaying = true)
        }
    }

    fun stop() {
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
        player?.pause()
        _state.value = _state.value.copy(isPlaying = false)
    }

    fun resume() {
        player?.play()
        _state.value = _state.value.copy(isPlaying = true)
    }

}
