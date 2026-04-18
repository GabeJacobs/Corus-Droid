package fm.corus.android.domain

import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import dagger.hilt.android.qualifiers.ApplicationContext
import fm.corus.android.data.local.PreferencesDataStore
import fm.corus.android.data.remote.CloudFunctionsDataSource
import fm.corus.android.ui.components.ToastManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.SocketTimeoutException
import java.net.URL
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
) {
    val hasActiveTrack: Boolean get() = trackId != null
}

@Singleton
class NowPlayingManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val cloudFunctions: CloudFunctionsDataSource,
    private val preferencesDataStore: PreferencesDataStore,
) {
    private val managerScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    @Volatile
    private var autoplayEnabled: Boolean = true

    init {
        managerScope.launch {
            preferencesDataStore.autoplayNextSong.collect { autoplayEnabled = it }
        }
    }

    private var queue: List<QueuedTrack> = emptyList()
    private var currentQueueIndex: Int? = null

    private fun computeHasNext(): Boolean =
        currentQueueIndex?.let { it + 1 < queue.size } ?: false
    private var player: ExoPlayer? = null

    private val _state = MutableStateFlow(NowPlayingState())
    val state: StateFlow<NowPlayingState> = _state.asStateFlow()

    private val _loadingTrackId = MutableStateFlow<String?>(null)
    val loadingTrackId: StateFlow<String?> = _loadingTrackId.asStateFlow()

    /** Incremented on each cancel; play() checks this to bail out after URL resolution. */
    private var playGeneration = 0

    // Preview URL cache — avoids redundant iTunes API calls
    private val previewCache = mutableMapOf<String, String>()
    private val noMatchCache = mutableSetOf<String>()

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

    suspend fun generateFeedPlaylist() {
        _isGeneratingPlaylist.value = true
        ToastManager.show("Generating playlist\u2026")

        if (!isNetworkAvailable()) {
            _playlistError.value = "Couldn't connect. Check your connection."
            _isGeneratingPlaylist.value = false
            return
        }

        try {
            val result = cloudFunctions.generateFeedPlaylist()
            if (!result.cached) {
                delay(2000)
            }
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(result.playlistWebURL)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: CloudFunctionsDataSource.PaywallRequiredException) {
            _paywallRequested.value = true
        } catch (e: UnknownHostException) {
            _playlistError.value = "Couldn't connect. Check your connection."
        } catch (e: SocketTimeoutException) {
            _playlistError.value = "Couldn't connect. Check your connection."
        } catch (e: Exception) {
            _playlistError.value = "Something went wrong. Try again later."
        }
        _isGeneratingPlaylist.value = false
    }

    suspend fun generateProfilePlaylist(userId: String) {
        _isGeneratingPlaylist.value = true
        ToastManager.show("Generating playlist\u2026")

        if (!isNetworkAvailable()) {
            _playlistError.value = "Couldn't connect. Check your connection."
            _isGeneratingPlaylist.value = false
            return
        }

        try {
            val result = cloudFunctions.generateProfilePlaylist(userId)
            if (!result.cached) {
                delay(2000) // Wait for Spotify to process
            }
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(result.playlistWebURL)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: CloudFunctionsDataSource.PaywallRequiredException) {
            _paywallRequested.value = true
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
        playInternal(track)
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
    ) {
        // Single-track path: clear any queued context so hasNext is false.
        queue = emptyList()
        currentQueueIndex = null
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

        // Resolve preview URL — use stored URL or look it up
        val resolvedUrl = track.previewUrl?.takeIf { it.isNotBlank() }
            ?: previewCache[trackId]
            ?: lookupPreviewUrl(trackId, track.trackName, track.artistName, track.isrc)

        // If cancelled while resolving, bail out
        if (generation != playGeneration) return

        _loadingTrackId.value = null

        if (resolvedUrl == null) return

        // Cache for future taps
        previewCache[trackId] = resolvedUrl

        // New track — start playback
        stopPlayerOnly()
        player = ExoPlayer.Builder(context).build().apply {
            addListener(object : Player.Listener {
                override fun onPlaybackStateChanged(playbackState: Int) {
                    if (playbackState == Player.STATE_ENDED) {
                        _state.value = _state.value.copy(isPlaying = false)
                        handlePlaybackEnded()
                    }
                }
            })
            setMediaItem(MediaItem.fromUri(resolvedUrl))
            prepare()
            play()
        }

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
        )
    }

    /** Auto-advance to the next queued preview when enabled by user setting. */
    private fun handlePlaybackEnded() {
        if (!autoplayEnabled) return
        val idx = currentQueueIndex ?: return
        val next = queue.getOrNull(idx + 1) ?: return
        managerScope.launch {
            currentQueueIndex = idx + 1
            playInternal(next)
        }
    }

    fun skipToNext() {
        val idx = currentQueueIndex ?: return
        val next = queue.getOrNull(idx + 1) ?: return
        managerScope.launch {
            currentQueueIndex = idx + 1
            playInternal(next)
        }
    }

    /** Stops the player without clearing the queue — used between queued tracks. */
    private fun stopPlayerOnly() {
        player?.release()
        player = null
    }

    /** 3-tier iTunes lookup matching iOS AppleMusicPreviewService:
     *  1. ISRC direct lookup
     *  2. Text search (name + artist)
     *  3. Cloud Function appleMusicLookup fallback
     */
    private suspend fun lookupPreviewUrl(
        trackId: String,
        name: String,
        artist: String,
        isrc: String?,
    ): String? {
        if (noMatchCache.contains(trackId)) return null

        return try {
            // 1. ISRC lookup
            if (!isrc.isNullOrBlank()) {
                val url = itunesIsrcLookup(isrc)
                if (url != null) return url
            }

            // 2. Text search
            val url = itunesTextSearch(name, artist)
            if (url != null) return url

            // 3. Cloud Function fallback
            val cfUrl = cloudFunctions.appleMusicLookup(name, artist, isrc, trackId)
            if (cfUrl != null) return cfUrl

            noMatchCache.add(trackId)
            null
        } catch (_: Exception) {
            null
        }
    }

    private suspend fun itunesIsrcLookup(isrc: String): String? = withContext(Dispatchers.IO) {
        try {
            val encoded = java.net.URLEncoder.encode(isrc, "UTF-8")
            val json = URL("https://itunes.apple.com/lookup?isrc=$encoded&entity=song").readText()
            parseItunesPreviewUrl(json)
        } catch (_: Exception) { null }
    }

    private suspend fun itunesTextSearch(name: String, artist: String): String? = withContext(Dispatchers.IO) {
        try {
            val term = java.net.URLEncoder.encode("$name $artist", "UTF-8")
            val json = URL("https://itunes.apple.com/search?term=$term&media=music&entity=song&limit=10").readText()
            parseItunesPreviewUrl(json)
        } catch (_: Exception) { null }
    }

    private fun parseItunesPreviewUrl(json: String): String? {
        return try {
            val results = JSONObject(json).getJSONArray("results")
            for (i in 0 until results.length()) {
                val item = results.getJSONObject(i)
                val preview = item.optString("previewUrl")
                if (preview.isNotBlank()) return preview
            }
            null
        } catch (_: Exception) { null }
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
        player?.release()
        player = null
        queue = emptyList()
        currentQueueIndex = null
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

    // Preview playback (separate from main player)
    private var previewPlayer: ExoPlayer? = null

    fun playPreview(url: String) {
        stopPreview()
        previewPlayer = ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(url))
            prepare()
            play()
        }
    }

    fun stopPreview() {
        previewPlayer?.release()
        previewPlayer = null
    }
}
