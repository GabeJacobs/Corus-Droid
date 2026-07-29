package fm.corus.android.domain

import android.content.Context
import android.net.Uri
import com.spotify.android.appremote.api.ConnectionParams
import com.spotify.android.appremote.api.Connector
import com.spotify.android.appremote.api.SpotifyAppRemote
import com.spotify.protocol.client.CallResult
import com.spotify.protocol.client.Subscription
import com.spotify.protocol.types.PlayerContext
import com.spotify.protocol.types.PlayerState
import dagger.hilt.android.qualifiers.ApplicationContext
import fm.corus.android.service.RemoteConfigService
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

sealed class SpotifyPlaybackError(message: String) : Exception(message) {
    class NotAuthorized : SpotifyPlaybackError("Spotify is not connected.")
    class NotConnected : SpotifyPlaybackError("Couldn't connect to the Spotify app.")
    class SpotifyNotInstalled : SpotifyPlaybackError("Install the Spotify app to play full songs from Corus.")
    class ApiError(message: String) : SpotifyPlaybackError(message)
}

/**
 * Full-track Spotify playback via the official App Remote SDK. Audio runs in
 * the Spotify app; Corus controls transport over IPC.
 */
@Singleton
class SpotifyPlaybackService @Inject constructor(
    @ApplicationContext private val context: Context,
    private val remoteConfig: RemoteConfigService,
    private val spotifyAuthService: SpotifyAuthService,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _positionSeconds = MutableStateFlow(0.0)
    val positionSeconds: StateFlow<Double> = _positionSeconds.asStateFlow()

    private val _durationSeconds = MutableStateFlow(0.0)
    val durationSeconds: StateFlow<Double> = _durationSeconds.asStateFlow()

    private val _currentTrackUri = MutableStateFlow<String?>(null)
    val currentTrackUri: StateFlow<String?> = _currentTrackUri.asStateFlow()

    var currentContextUri: String? = null
        private set
    var lastOutgoingTrackUri: String? = null
        private set
    var lastOutgoingPlaybackPosition: Double = 0.0
        private set
    var lastOutgoingDuration: Double = 0.0
        private set
    var lastOutgoingContextUri: String? = null
        private set
    var incomingPlaybackPosition: Double = 0.0
        private set
    var incomingContextUri: String? = null
        private set

    var onTrackEnded: (() -> Unit)? = null
    var onPlayerTrackChanged: ((String) -> Unit)? = null
    var onPlayerStateUpdated: (() -> Unit)? = null

    private var appRemote: SpotifyAppRemote? = null
    private var playerStateSubscription: Subscription<PlayerState>? = null
    private var playerContextSubscription: Subscription<PlayerContext>? = null

    private var connectDeferred: CompletableDeferred<Unit>? = null
    private var playDeferred: CompletableDeferred<Unit>? = null
    private var pendingPlayUri: String? = null

    private var lastObservedTrackUri: String? = null
    private var lastEndedUri: String? = null
    private var lastAppliedQueueSessionId: Int? = null
    private var lastPositionOnlyApplyTime: Long = 0L

    private var proactiveReconnectJob: kotlinx.coroutines.Job? = null
    private var seekJob: kotlinx.coroutines.Job? = null
    private var lastProactiveReconnectAttempt: Long = 0L

    val isConnected: Boolean get() = appRemote?.isConnected == true

    companion object {
        private const val PROACTIVE_RECONNECT_DEBOUNCE_MS = 5_000L
        private const val PLAY_TIMEOUT_MS = 15_000L
        private const val LAST_USAGE_KEY = "fm.corus.spotify.lastAppRemoteUsage"
        private const val PREFS_NAME = "corus_prefs"

        fun normalizedSpotifyTrackId(trackId: String): String = when {
            trackId.startsWith("am:") -> trackId.removePrefix("am:")
            trackId.startsWith("sc:") -> trackId.removePrefix("sc:")
            else -> trackId
        }

        fun isSpotifyAppInstalled(context: Context): Boolean =
            context.packageManager.getLaunchIntentForPackage("com.spotify.music") != null
    }

    fun trySilentReconnectIfNeeded() {
        if (!remoteConfig.spotifyAuthExperimentEnabled) return
        scheduleProactiveReconnect()
    }

    /** Handle `corus://spotify-auth` callbacks from App Remote authorization. */
    fun handleRedirectUri(uri: Uri): Boolean {
        if (uri.scheme != "corus" || uri.host != "spotify-auth") return false

        val error = uri.getQueryParameter("error")
        if (error != null) {
            connectDeferred?.let { if (it.isActive) it.completeExceptionally(SpotifyPlaybackError.NotAuthorized()) }
            connectDeferred = null
            playDeferred?.let { if (it.isActive) it.completeExceptionally(SpotifyPlaybackError.NotAuthorized()) }
            playDeferred = null
            return true
        }

        val token = uri.getQueryParameter("access_token")
            ?: uri.fragment?.split("&")?.firstOrNull { it.startsWith("access_token=") }
                ?.removePrefix("access_token=")
        if (!token.isNullOrBlank()) {
            spotifyAuthService.storeAppRemoteAccessToken(token)
            connectDeferred?.let { if (it.isActive) it.complete(Unit) }
            connectDeferred = null
            playDeferred?.let { if (it.isActive) it.complete(Unit) }
            playDeferred = null
            if (!isConnected) {
                scope.launch { runCatching { connect(showAuthView = false) } }
            }
            return true
        }
        return false
    }

    suspend fun play(
        spotifyTrackId: String,
        uri: String?,
        replaceQueue: Boolean = true,
        queueSessionId: Int? = null,
    ) {
        playInternal(spotifyTrackId, uri, replaceQueue, queueSessionId)
    }

    private suspend fun playInternal(
        spotifyTrackId: String,
        uri: String?,
        replaceQueue: Boolean,
        queueSessionId: Int?,
    ) {
        if (!isSpotifyAppInstalled(context)) {
            throw SpotifyPlaybackError.SpotifyNotInstalled()
        }

        val trackUri = normalizedUri(spotifyTrackId, uri)
        val needsQueueReset = replaceQueue && shouldResetNativeQueue(queueSessionId)

        if (isConnected && !needsQueueReset) {
            withPlayTimeout { playUri(trackUri) }
            queueSessionId?.let { lastAppliedQueueSessionId = it }
            markRecentUsage()
            return
        }

        if (needsQueueReset) {
            clearStaleSpotifyQueue()
        }

        if (spotifyAuthService.cachedAccessToken() != null) {
            var lastError: Exception? = null
            repeat(2) { attempt ->
                try {
                    withPlayTimeout {
                        connect(showAuthView = false)
                        playUri(trackUri)
                    }
                    queueSessionId?.let { lastAppliedQueueSessionId = it }
                    markRecentUsage()
                    return
                } catch (e: Exception) {
                    cancelPendingContinuations()
                    lastError = e
                    if (isSpotifyAuthRequiredError(e)) {
                        spotifyAuthService.clearAccessToken()
                        clearRecentUsage()
                    }
                    if (attempt == 0) delay(300)
                }
            }
            android.util.Log.w(
                "SpotifyPlayback",
                "Silent reconnect failed, opening Spotify auth: ${lastError?.message}",
            )
        }

        withPlayTimeout {
            connect(showAuthView = true)
            playUri(trackUri)
        }
        queueSessionId?.let { lastAppliedQueueSessionId = it }
        markRecentUsage()
    }

    private suspend fun connect(showAuthView: Boolean) {
        if (isConnected) return
        suspendCancellableCoroutine { cont ->
            val deferred = CompletableDeferred<Unit>()
            connectDeferred = deferred

            cont.invokeOnCancellation {
                connectDeferred = null
            }

            // Single resume path: redirect callbacks, SDK listeners, and
            // cancelPendingContinuations() all complete [connectDeferred];
            // only [invokeOnCompletion] resumes the coroutine (once).
            deferred.invokeOnCompletion { cause ->
                connectDeferred = null
                if (!cont.isActive) return@invokeOnCompletion
                when (cause) {
                    null -> cont.resume(Unit)
                    else -> {
                        val error = cause as? Exception ?: SpotifyPlaybackError.NotConnected()
                        cont.resumeWithException(error)
                    }
                }
            }

            val params = ConnectionParams.Builder(SpotifyAuthService.CLIENT_ID)
                .setRedirectUri(SpotifyAuthService.REDIRECT_URI)
                .showAuthView(showAuthView)
                .build()
            SpotifyAppRemote.connect(
                SpotifyConnectContext.activityOr(context),
                params,
                object : Connector.ConnectionListener {
                    override fun onConnected(remote: SpotifyAppRemote) {
                        appRemote = remote
                        subscribePlayerState()
                        subscribePlayerContext()
                        if (deferred.isActive) deferred.complete(Unit)
                    }

                    override fun onFailure(error: Throwable) {
                        val wrapped = SpotifyPlaybackError.ApiError(
                            error.message ?: "Couldn't connect to Spotify",
                        )
                        if (deferred.isActive) deferred.completeExceptionally(wrapped)
                    }
                },
            )
        }
    }

    private suspend fun playUri(uri: String) {
        val remote = appRemote ?: throw SpotifyPlaybackError.NotConnected()
        suspendCancellableCoroutine { cont ->
            val deferred = CompletableDeferred<Unit>()
            playDeferred = deferred

            cont.invokeOnCancellation {
                playDeferred = null
            }

            deferred.invokeOnCompletion { cause ->
                playDeferred = null
                if (!cont.isActive) return@invokeOnCompletion
                when (cause) {
                    null -> cont.resume(Unit)
                    else -> {
                        val error = cause as? Exception ?: SpotifyPlaybackError.NotConnected()
                        cont.resumeWithException(error)
                    }
                }
            }

            val result = remote.playerApi.play(uri)
            result.setResultCallback { _ ->
                _currentTrackUri.value = uri
                _isPlaying.value = true
                if (deferred.isActive) deferred.complete(Unit)
            }
            result.setErrorCallback { error ->
                val wrapped = SpotifyPlaybackError.ApiError(
                    error.message ?: "Couldn't play in Spotify",
                )
                if (deferred.isActive) deferred.completeExceptionally(wrapped)
            }
        }
        subscribePlayerState()
        subscribePlayerContext()
    }

    private fun subscribePlayerContext() {
        val remote = appRemote ?: return
        cancelSubscription(playerContextSubscription)
        playerContextSubscription = remote.playerApi.subscribeToPlayerContext().setEventCallback { ctx ->
            scope.launch { applyPlayerContext(ctx) }
        }
    }

    private fun applyPlayerContext(ctx: PlayerContext) {
        currentContextUri = ctx.uri?.takeIf { it.isNotEmpty() }
    }

    private fun subscribePlayerState() {
        val remote = appRemote ?: return
        cancelSubscription(playerStateSubscription)
        playerStateSubscription = remote.playerApi.subscribeToPlayerState().setEventCallback { state ->
            scope.launch {
                runCatching { applyPlayerState(state) }.onFailure { error ->
                    android.util.Log.w("SpotifyPlayback", "applyPlayerState failed: ${error.message}")
                }
            }
        }
    }

    private fun applyPlayerState(state: PlayerState) {
        val track = state.track
        if (track == null) {
            // Spotify emits empty player state during connect/handoff/disconnect.
            _isPlaying.value = !state.isPaused
            _positionSeconds.value = state.playbackPosition / 1000.0
            onPlayerStateUpdated?.invoke()
            return
        }

        val uri = track.uri ?: ""
        val playing = !state.isPaused
        val playStateChanged = _isPlaying.value != playing
        val trackChanged = uri.isNotEmpty() && uri != lastObservedTrackUri
        val newPosition = state.playbackPosition / 1000.0
        val newDuration = (track.duration / 1000.0).coerceAtLeast(0.0)

        if (trackChanged) {
            lastOutgoingTrackUri = lastObservedTrackUri
            lastOutgoingPlaybackPosition = _positionSeconds.value
            lastOutgoingDuration = _durationSeconds.value
            lastOutgoingContextUri = currentContextUri
            incomingPlaybackPosition = newPosition
            incomingContextUri = currentContextUri
            lastObservedTrackUri = uri
            onPlayerTrackChanged?.invoke(uri)
        }

        _isPlaying.value = playing
        _positionSeconds.value = newPosition
        _durationSeconds.value = newDuration
        _currentTrackUri.value = uri.takeIf { it.isNotEmpty() }

        onPlayerStateUpdated?.invoke()

        if (!playStateChanged && !trackChanged) {
            val now = System.currentTimeMillis()
            if (now - lastPositionOnlyApplyTime < 450) return
            lastPositionOnlyApplyTime = now
        }

        if (state.isPaused &&
            _durationSeconds.value > 0 &&
            _positionSeconds.value >= _durationSeconds.value - 1.5 &&
            lastEndedUri != uri
        ) {
            lastEndedUri = uri
            onTrackEnded?.invoke()
        }
    }

    suspend fun pause() {
        val remote = appRemote
        if (remote == null || !remote.isConnected) {
            _isPlaying.value = false
            return
        }
        awaitPlayerApiVoid(
            onSuccess = { _isPlaying.value = false },
        ) { it.playerApi.pause() }
    }

    /** Fire-and-forget pause for skip recovery (see iOS `pauseImmediately`). */
    fun pauseImmediately() {
        val remote = appRemote
        if (remote == null || !remote.isConnected) {
            _isPlaying.value = false
            return
        }
        remote.playerApi.pause().setResultCallback { _isPlaying.value = false }
        _isPlaying.value = false
    }

    suspend fun resume() {
        val remote = appRemote ?: return
        if (!remote.isConnected) return
        awaitPlayerApiVoid(
            onSuccess = { _isPlaying.value = true },
        ) { it.playerApi.resume() }
    }

    suspend fun seek(toSeconds: Double) {
        val remote = appRemote ?: return
        if (!remote.isConnected) return
        val seconds = maxOf(0.0, toSeconds)
        val ms = (seconds * 1000).toLong()
        seekJob?.cancel()
        seekJob = scope.launch {
            runCatching {
                awaitPlayerApiVoid(
                    onSuccess = { _positionSeconds.value = seconds },
                ) { it.playerApi.seekTo(ms) }
            }.onFailure { error ->
                android.util.Log.w("SpotifyPlayback", "seek failed: ${error.message}")
            }
        }
    }

    fun cancelPendingPlayRequest() {
        cancelPendingContinuations()
    }

    private suspend fun clearStaleSpotifyQueue() {
        proactiveReconnectJob?.cancel()
        cancelPendingContinuations()
        lastObservedTrackUri = null
        lastEndedUri = null
        val remote = appRemote
        if (remote?.isConnected == true) {
            runCatching { pause() }
            SpotifyAppRemote.disconnect(remote)
            appRemote = null
            cancelSubscription(playerStateSubscription)
            playerStateSubscription = null
            cancelSubscription(playerContextSubscription)
            playerContextSubscription = null
            delay(150)
        }
        cancelPendingContinuations()
    }

    fun stop() {
        proactiveReconnectJob?.cancel()
        seekJob?.cancel()
        cancelPendingContinuations()
        cancelSubscription(playerStateSubscription)
        playerStateSubscription = null
        cancelSubscription(playerContextSubscription)
        playerContextSubscription = null
        appRemote?.let { SpotifyAppRemote.disconnect(it) }
        appRemote = null
        _isPlaying.value = false
        _positionSeconds.value = 0.0
        _durationSeconds.value = 0.0
        _currentTrackUri.value = null
        lastEndedUri = null
        lastObservedTrackUri = null
        lastAppliedQueueSessionId = null
    }

    suspend fun refreshState(timeoutMs: Long = 2000L) {
        val remote = appRemote ?: return
        if (!remote.isConnected) return
        withTimeoutOrNull(timeoutMs) {
            suspendCancellableCoroutine { cont ->
                var finished = false
                fun finish(success: Boolean, error: Throwable? = null) {
                    if (finished) return
                    finished = true
                    if (!cont.isActive) return
                    when {
                        success -> cont.resume(Unit)
                        error != null -> cont.resumeWithException(
                            error as? Exception
                                ?: SpotifyPlaybackError.ApiError(error.message ?: "Spotify request failed"),
                        )
                    }
                }
                cont.invokeOnCancellation { finished = true }
                remote.playerApi.getPlayerState().apply {
                    setResultCallback { state ->
                        state?.let { scope.launch { applyPlayerState(it) } }
                        finish(success = true)
                    }
                    setErrorCallback { error ->
                        finish(
                            success = false,
                            error = SpotifyPlaybackError.ApiError(
                                error.message ?: "Couldn't read Spotify player state",
                            ),
                        )
                    }
                }
            }
        }
    }

    private fun cancelSubscription(subscription: Subscription<*>?) {
        runCatching { subscription?.cancel() }
    }

    /**
     * Resume a Spotify [CallResult] at most once, with error handling.
     * Prevents "Already resumed" crashes when the SDK fires duplicate callbacks.
     */
    private suspend fun awaitPlayerApiVoid(
        onSuccess: () -> Unit = {},
        block: (SpotifyAppRemote) -> CallResult<*>?,
    ) {
        val remote = appRemote ?: return
        if (!remote.isConnected) return
        val result = block(remote) ?: return
        suspendCancellableCoroutine { cont ->
            var finished = false
            fun finish(success: Boolean, error: Throwable? = null) {
                if (finished) return
                finished = true
                if (!cont.isActive) return
                when {
                    success -> cont.resume(Unit)
                    error != null -> cont.resumeWithException(
                        error as? Exception
                            ?: SpotifyPlaybackError.ApiError(error.message ?: "Spotify request failed"),
                    )
                }
            }
            cont.invokeOnCancellation { finished = true }
            result.setResultCallback {
                onSuccess()
                finish(success = true)
            }
            result.setErrorCallback { error ->
                finish(
                    success = false,
                    error = SpotifyPlaybackError.ApiError(
                        error.message ?: "Spotify request failed",
                    ),
                )
            }
        }
    }

    private fun shouldResetNativeQueue(queueSessionId: Int?): Boolean {
        if (queueSessionId == null) return true
        val last = lastAppliedQueueSessionId ?: return true
        return queueSessionId != last
    }

    private fun normalizedUri(trackId: String, uri: String?): String {
        val trimmed = uri?.takeIf { it.isNotEmpty() }
        return trimmed ?: "spotify:track:${normalizedSpotifyTrackId(trackId)}"
    }

    private suspend fun withPlayTimeout(block: suspend () -> Unit) {
        withTimeoutOrNull(PLAY_TIMEOUT_MS) { block() }
            ?: throw SpotifyPlaybackError.ApiError("Timed out connecting to Spotify")
    }

    private fun cancelPendingContinuations() {
        playDeferred?.let { if (it.isActive) it.completeExceptionally(SpotifyPlaybackError.NotConnected()) }
        playDeferred = null
        connectDeferred?.let { if (it.isActive) it.completeExceptionally(SpotifyPlaybackError.NotConnected()) }
        connectDeferred = null
    }

    private fun hasRecentUsage(withinMs: Long = 3_600_000L): Boolean {
        val last = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getLong(LAST_USAGE_KEY, 0L)
        if (last <= 0L) return false
        return System.currentTimeMillis() - last < withinMs
    }

    private fun clearRecentUsage() {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .remove(LAST_USAGE_KEY)
            .apply()
    }

    private fun isSpotifyAuthRequiredError(error: Exception): Boolean {
        val message = error.message ?: return false
        return message.contains("authorization", ignoreCase = true) ||
            message.contains("auth-flow", ignoreCase = true)
    }

    private fun markRecentUsage() {
        if (!remoteConfig.spotifyAuthExperimentEnabled) return
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putLong(LAST_USAGE_KEY, System.currentTimeMillis())
            .apply()
    }

    private fun scheduleProactiveReconnect() {
        if (!remoteConfig.spotifyAuthExperimentEnabled) return
        if (!hasRecentUsage() && spotifyAuthService.cachedAccessToken() == null) return
        if (isConnected) return
        val now = System.currentTimeMillis()
        if (now - lastProactiveReconnectAttempt < PROACTIVE_RECONNECT_DEBOUNCE_MS) return
        if (proactiveReconnectJob?.isActive == true) return
        lastProactiveReconnectAttempt = now
        proactiveReconnectJob = scope.launch {
            runCatching {
                connect(showAuthView = false)
                subscribePlayerState()
                subscribePlayerContext()
                markRecentUsage()
            }
        }
    }
}
