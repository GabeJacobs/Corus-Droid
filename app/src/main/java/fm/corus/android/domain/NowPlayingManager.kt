package fm.corus.android.domain

import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import dagger.hilt.android.qualifiers.ApplicationContext
import fm.corus.android.data.remote.CloudFunctionsDataSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.SocketTimeoutException
import java.net.URL
import java.net.UnknownHostException
import javax.inject.Inject
import javax.inject.Singleton

data class NowPlayingState(
    val trackId: String? = null,
    val trackName: String = "",
    val artistName: String = "",
    val albumArtURL: String? = null,
    val spotifyURI: String? = null,
    val spotifyWebURL: String? = null,
    val isPlaying: Boolean = false,
    val sourcePostId: String? = null,
) {
    val hasActiveTrack: Boolean get() = trackId != null
}

@Singleton
class NowPlayingManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val cloudFunctions: CloudFunctionsDataSource,
) {
    private var player: ExoPlayer? = null

    private val _state = MutableStateFlow(NowPlayingState())
    val state: StateFlow<NowPlayingState> = _state.asStateFlow()

    private val _loadingTrackId = MutableStateFlow<String?>(null)
    val loadingTrackId: StateFlow<String?> = _loadingTrackId.asStateFlow()

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

    private fun isNetworkAvailable(): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    suspend fun generateFeedPlaylist() {
        _isGeneratingPlaylist.value = true

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
        // If same track, toggle pause/play
        if (_state.value.trackId == trackId && player != null) {
            togglePlayPause()
            return
        }

        // Signal loading state
        _loadingTrackId.value = trackId

        // Resolve preview URL — use stored URL or look it up
        val resolvedUrl = previewUrl?.takeIf { it.isNotBlank() }
            ?: previewCache[trackId]
            ?: lookupPreviewUrl(trackId, trackName, artistName, isrc)

        _loadingTrackId.value = null

        if (resolvedUrl == null) return

        // Cache for future taps
        previewCache[trackId] = resolvedUrl

        // New track — start playback
        stop()
        player = ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(resolvedUrl))
            prepare()
            play()
        }

        _state.value = NowPlayingState(
            trackId = trackId,
            trackName = trackName,
            artistName = artistName,
            albumArtURL = albumArtURL,
            spotifyURI = spotifyURI,
            spotifyWebURL = spotifyWebURL,
            isPlaying = true,
            sourcePostId = sourcePostId,
        )
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

    fun togglePlayPause() {
        if (player?.isPlaying == true) {
            player?.pause()
            _state.value = _state.value.copy(isPlaying = false)
        } else {
            player?.play()
            _state.value = _state.value.copy(isPlaying = true)
        }
    }

    fun stop() {
        player?.release()
        player = null
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
