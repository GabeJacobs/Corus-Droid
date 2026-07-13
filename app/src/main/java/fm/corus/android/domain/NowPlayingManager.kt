package fm.corus.android.domain

import android.app.PendingIntent
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
import fm.corus.android.data.model.MusicService
import fm.corus.android.data.model.TrackSource
import fm.corus.android.data.remote.CloudFunctionsDataSource
import fm.corus.android.data.remote.TidalPlaylistService
import fm.corus.android.MainActivity
import fm.corus.android.data.repository.UserRepository
import fm.corus.android.service.CorusPlaybackService
import fm.corus.android.ui.components.ToastManager
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
    val isPlaying: Boolean = false,
    val sourcePostId: String? = null,
    val hasNext: Boolean = false,
    val source: TrackSource = TrackSource.SPOTIFY,
    val soundcloudPermalinkUrl: String? = null,
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
        albumArtLargeURL: String? = null,
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
                albumArtLargeURL = albumArtLargeURL,
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

    private suspend fun playInternal(track: QueuedTrack, userInitiated: Boolean = true) {
        val trackId = track.trackId

        // Audio sources are mutually exclusive: starting music stops any inline
        // trailer and any playing audio caption so they never play over each other.
        TrailerPlaybackCoordinator.stopAll()
        VoiceNotePlayerManager.stopActivePlayer()

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
        val resolvedUrl = when (track.source) {
            TrackSource.SOUNDCLOUD -> track.soundcloudId?.let { resolveSoundCloudStream(it) }
            // Audiomack is link-out only — never stream in-app, and never fall
            // through to the Apple-preview lookup (which would play a WRONG
            // track for an Audiomack id). Yields null so playback stops
            // gracefully here; the UI opens the Audiomack page instead.
            TrackSource.AUDIOMACK -> null
            else -> track.previewUrl?.takeIf { it.isNotBlank() }
                ?: previewCache[trackId]
                ?: lookupPreviewUrl(trackId, track.trackName, track.artistName, track.isrc)
        }

        // If cancelled while resolving, bail out
        if (generation != playGeneration) return

        _loadingTrackId.value = null

        if (resolvedUrl == null) {
            // No playable preview (e.g. a Spotify track with no Apple Music
            // match). Tell the user, but only on an explicit tap — auto-advance
            // just stops rather than spamming a toast per dead track. Audiomack
            // is intentionally unplayable (link-out only), so suppress the toast
            // for it — the tap surfaces open the Audiomack page instead.
            if (userInitiated && track.source != TrackSource.AUDIOMACK) _previewUnavailable.tryEmit(Unit)
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
            isPlaying = true,
            sourcePostId = track.sourcePostId,
            hasNext = computeHasNext(),
            source = track.source,
            soundcloudPermalinkUrl = track.soundcloudPermalinkUrl,
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

    /** Auto-advance to the next queued preview when enabled by user setting. */
    private fun handlePlaybackEnded() {
        if (!autoplayEnabled) return
        advanceToNext()
    }

    fun skipToNext() {
        // Snap scrubber to 0 instantly so it doesn't briefly tween forward
        // on the outgoing track while the next one is loading. Mirrors the
        // iOS resetScrubberPosition() behavior.
        // Stop polling first so the outgoing track's position-polling
        // coroutine can't push another (advancing) update into ScrubberClock
        // between reset() and the next track actually being loaded — that
        // straggler tick would briefly drag the scrubber forward.
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
            // Resuming the song silences any active audio caption.
            VoiceNotePlayerManager.stopActivePlayer()
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
        // Resuming the song silences any active audio caption.
        VoiceNotePlayerManager.stopActivePlayer()
        player?.play()
        _state.value = _state.value.copy(isPlaying = true)
    }

}
