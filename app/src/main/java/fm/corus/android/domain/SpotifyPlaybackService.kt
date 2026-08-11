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
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.locks.ReentrantLock
import javax.inject.Inject
import javax.inject.Provider
import kotlin.concurrent.withLock
import javax.inject.Singleton
import kotlin.coroutines.cancellation.CancellationException
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
    private val spotifyAuthService: SpotifyAuthService,
    private val analyticsService: fm.corus.android.service.AnalyticsService,
    // Provider, not a direct injection: SpotifySaveAutoAdd depends on this
    // service to deliver saves, so a plain field would be a Hilt cycle.
    private val spotifySaveAutoAdd: Provider<SpotifySaveAutoAdd>,
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

    var currentTrackTitle: String? = null
        private set
    var currentTrackArtistName: String? = null
        private set
    var currentTrackAlbumTitle: String? = null
        private set
    /** Raw App Remote `ImageUri.raw` (usually `spotify:image:…`). */
    var currentTrackImageUriRaw: String? = null
        private set

    data class AppRemoteDisplayMetadata(
        val name: String,
        val artistName: String,
        val albumName: String,
        /** HTTPS CDN URL derived from App Remote image URI, when available. */
        val albumArtURL: String? = null,
    )

    /** Display metadata for whatever Spotify is playing — from App Remote, no Web API. */
    fun appRemoteDisplayMetadata(): AppRemoteDisplayMetadata? {
        val name = currentTrackTitle?.takeIf { it.isNotEmpty() } ?: return null
        val artist = currentTrackArtistName?.takeIf { it.isNotEmpty() } ?: return null
        return AppRemoteDisplayMetadata(
            name = name,
            artistName = artist,
            albumName = currentTrackAlbumTitle.orEmpty(),
            albumArtURL = spotifyImageUriToHttps(currentTrackImageUriRaw),
        )
    }

    /** Set on track change until the next player-context event (Android has no context on PlayerState). */
    private var incomingContextAwaitingSinceMs: Long? = null

    var onTrackEnded: (() -> Unit)? = null
    var onPlayerTrackChanged: ((String) -> Unit)? = null
    /** Fired when player context arrives after a track change so queue reconcile can re-run. */
    var onPlayerContextUpdated: (() -> Unit)? = null
    var onPlayerStateUpdated: (() -> Unit)? = null

    fun isAwaitingIncomingContext(maxWaitMs: Long = 800L): Boolean {
        val since = incomingContextAwaitingSinceMs ?: return false
        if (incomingContextUri != null) return false
        return System.currentTimeMillis() - since < maxWaitMs
    }

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

    /**
     * Lock-screen / native Spotify skip can land on a stale queue entry before
     * [applyPlayerState] runs on the main coroutine — pause misrouted tracks on
     * the App Remote callback thread (mirrors iOS `attemptFastPathMisroutePause`).
     */
    private data class FastPathGuardState(
        var expectedURI: String? = null,
        var guardUntilMs: Long = 0L,
        var lastObservedURI: String? = null,
    )

    private val fastPathGuardLock = ReentrantLock()
    private val fastPathGuardState = FastPathGuardState()

    /** Immutable for the player-state callback thread once connected. */
    @Volatile
    private var fastPathAppRemote: SpotifyAppRemote? = null

    private var proactiveReconnectJob: kotlinx.coroutines.Job? = null
    private var seekJob: kotlinx.coroutines.Job? = null
    private var lastProactiveReconnectAttempt: Long = 0L

    val isConnected: Boolean get() = appRemote?.isConnected == true

    companion object {
        private const val PROACTIVE_RECONNECT_DEBOUNCE_MS = 5_000L
        private const val PLAY_TIMEOUT_MS = 15_000L
        private const val FAST_PATH_MISROUTE_MAX_POSITION_SEC = 2.0
        private const val LAST_USAGE_KEY = "fm.corus.spotify.lastAppRemoteUsage"
        private const val PREFS_NAME = "corus_prefs"

        fun normalizedSpotifyTrackId(trackId: String): String = when {
            trackId.startsWith("am:") -> trackId.removePrefix("am:")
            trackId.startsWith("sc:") -> trackId.removePrefix("sc:")
            else -> trackId
        }

        fun isSpotifyAppInstalled(context: Context): Boolean =
            context.packageManager.getLaunchIntentForPackage("com.spotify.music") != null

        private fun spotifyTrackIdFromUri(uri: String): String? =
            uri.removePrefix("spotify:track:").takeIf { uri.startsWith("spotify:track:") }

        fun spotifyURIsMatch(a: String, b: String): Boolean {
            if (a == b) return true
            val aId = spotifyTrackIdFromUri(a) ?: return false
            val bId = spotifyTrackIdFromUri(b) ?: return false
            return normalizedSpotifyTrackId(aId) == normalizedSpotifyTrackId(bId)
        }
    }

    /**
     * Arm the delegate fast-path so a misrouted native Spotify skip is paused
     * before the async [applyPlayerState] coroutine runs.
     */
    fun setFastPathPlaybackGuard(
        expectedURI: String,
        fromCurrentURI: String? = null,
        durationMs: Long = 8_000L,
    ) {
        fastPathGuardLock.withLock {
            fastPathGuardState.expectedURI = expectedURI
            fastPathGuardState.guardUntilMs = System.currentTimeMillis() + durationMs
            if (!fromCurrentURI.isNullOrEmpty()) {
                fastPathGuardState.lastObservedURI = fromCurrentURI
            }
        }
    }

    fun clearFastPathPlaybackGuard() {
        fastPathGuardLock.withLock {
            fastPathGuardState.expectedURI = null
            fastPathGuardState.guardUntilMs = 0L
        }
    }

    fun trySilentReconnectIfNeeded() {
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
            android.util.Log.i("SpotifyPlayback", "Auth redirect received — reconnecting App Remote")
            scope.launch {
                runCatching { connect(showAuthView = false) }
                    .onFailure { error ->
                        android.util.Log.w(
                            "SpotifyPlayback",
                            "Post-redirect connect failed: ${error.message}",
                        )
                    }
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
        try {
            playInternal(spotifyTrackId, uri, replaceQueue, queueSessionId)
            analyticsService.logSpotifyFullSongPlayStarted(spotifyTrackId, "spotify")
            analyticsService.setSpotifyFullPlaybackConnected(true)
        } catch (e: Exception) {
            analyticsService.logSpotifyFullSongPlayFailed(
                spotifyTrackId,
                e.message ?: e.javaClass.simpleName,
            )
            throw e
        }
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

        // Always try silent first, whatever [SpotifyAuthService] holds. The App
        // Remote grant lives in the Spotify app, not here: [ConnectionParams]
        // carries only the client id and redirect URI, and the cached token is
        // never handed to the SDK — it only records that we authorized once, and
        // it expires after an hour with no refresh. Gating this attempt on it
        // sent every play tap past that hour into auth-lib's consent flow, which
        // on devices that fall back to its WebView handler paints a full-screen
        // "Loading…" dialog over Corus. Interactive auth is the fallback for a
        // connect App Remote actually refused.
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
            android.util.Log.w(
                "SpotifyPlayback",
                "Silent connect failed: ${e.message}",
            )
            discardStaleAppRemoteSession()
        }
        android.util.Log.w("SpotifyPlayback", "Silent reconnect failed, opening Spotify auth")

        withPlayTimeout {
            ensureAuthorizedAndConnected(requireInteractiveAuth = true)
            playUri(trackUri)
        }
        queueSessionId?.let { lastAppliedQueueSessionId = it }
        markRecentUsage()
    }

    /**
     * Silent App Remote connect, or auth-lib login first when [requireInteractiveAuth].
     * Avoids ConnectionParams.showAuthView(true), which Android 14+ blocks when Spotify
     * is not already in the foreground.
     */
    private suspend fun ensureAuthorizedAndConnected(requireInteractiveAuth: Boolean) {
        if (isConnected) return
        if (requireInteractiveAuth) {
            android.util.Log.i("SpotifyPlayback", "Starting interactive Spotify auth (auth-lib)")
            val token = SpotifyConnectContext.awaitInteractiveAuthorization()
            spotifyAuthService.storeAppRemoteAccessToken(token)
            android.util.Log.i("SpotifyPlayback", "Interactive auth succeeded — connecting App Remote")
        }
        connect(showAuthView = false)
    }

    private suspend fun connect(showAuthView: Boolean) {
        if (isConnected) return
        val connectContext = SpotifyConnectContext.currentActivity() ?: context
        if (showAuthView && SpotifyConnectContext.currentActivity() == null) {
            android.util.Log.w(
                "SpotifyPlayback",
                "connect(showAuthView=true) without Activity — auth UI may not appear",
            )
        }
        android.util.Log.i(
            "SpotifyPlayback",
            "App Remote connect(showAuthView=$showAuthView, context=${connectContext.javaClass.simpleName})",
        )
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
                connectContext,
                params,
                object : Connector.ConnectionListener {
                    override fun onConnected(remote: SpotifyAppRemote) {
                        appRemote = remote
                        fastPathAppRemote = remote
                        subscribePlayerState()
                        subscribePlayerContext()
                        if (deferred.isActive) deferred.complete(Unit)
                        spotifySaveAutoAdd.get().flushPendingLibraryAdds()
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
        val newUri = ctx.uri?.takeIf { it.isNotEmpty() } ?: return
        val wasAwaitingContext = incomingContextAwaitingSinceMs != null
        if (wasAwaitingContext) {
            incomingContextUri = newUri
            incomingContextAwaitingSinceMs = null
        }
        currentContextUri = newUri
        if (wasAwaitingContext) {
            onPlayerContextUpdated?.invoke()
        }
    }

    private fun subscribePlayerState() {
        val remote = appRemote ?: return
        cancelSubscription(playerStateSubscription)
        playerStateSubscription = remote.playerApi.subscribeToPlayerState().setEventCallback { state ->
            attemptFastPathMisroutePause(state)
            scope.launch {
                runCatching { applyPlayerState(state) }.onFailure { error ->
                    android.util.Log.w("SpotifyPlayback", "applyPlayerState failed: ${error.message}")
                }
            }
        }
    }

    /**
     * Pause a misrouted native skip on the App Remote callback thread — before
     * [scope.launch] lets Spotify audibly start the wrong track.
     */
    private fun attemptFastPathMisroutePause(state: PlayerState) {
        val track = state.track ?: return
        val uri = track.uri ?: return
        if (uri.isEmpty() || state.isPaused) return

        val position = state.playbackPosition / 1000.0
        if (position >= FAST_PATH_MISROUTE_MAX_POSITION_SEC) return

        // Podcasts, audiobooks and local files can never be a misrouted skip into
        // Corus's stale Spotify queue — there is no such content on Corus, so the
        // user started it in the Spotify app. Disarm instead of pausing it.
        if (SpotifyContentUri.kindOf(uri) != SpotifyContentKind.TRACK) {
            fastPathGuardLock.withLock {
                fastPathGuardState.expectedURI = null
                fastPathGuardState.guardUntilMs = 0L
            }
            return
        }

        val shouldPause = fastPathGuardLock.withLock {
            val now = System.currentTimeMillis()
            val expected = fastPathGuardState.expectedURI
            if (expected == null || now >= fastPathGuardState.guardUntilMs) return@withLock false
            val trackChanged = fastPathGuardState.lastObservedURI?.let { last ->
                !spotifyURIsMatch(last, uri)
            } ?: true
            if (!trackChanged) return@withLock false
            fastPathGuardState.lastObservedURI = uri
            !spotifyURIsMatch(uri, expected)
        }
        if (!shouldPause) return

        fastPathAppRemote?.playerApi?.pause()?.setResultCallback { _isPlaying.value = false }
    }

    private fun applyPlayerState(state: PlayerState) {
        val track = state.track
        if (track == null) {
            // Spotify emits empty player state during connect/handoff/disconnect.
            _isPlaying.value = !state.isPaused
            _positionSeconds.value = state.playbackPosition / 1000.0
            currentTrackImageUriRaw = null
            onPlayerStateUpdated?.invoke()
            return
        }

        val uri = track.uri ?: ""
        val playing = !state.isPaused
        currentTrackTitle = track.name
        currentTrackArtistName = track.artist?.name
        currentTrackAlbumTitle = track.album?.name
        currentTrackImageUriRaw = track.imageUri?.raw?.takeIf { it.isNotBlank() }
        val playStateChanged = _isPlaying.value != playing
        val trackChanged = uri.isNotEmpty() && uri != lastObservedTrackUri
        val newPosition = state.playbackPosition / 1000.0
        val newDuration = (track.duration / 1000.0).coerceAtLeast(0.0)

        if (trackChanged) {
            val matchedExpected = fastPathGuardLock.withLock {
                val now = System.currentTimeMillis()
                val expected = fastPathGuardState.expectedURI
                expected != null &&
                    now < fastPathGuardState.guardUntilMs &&
                    spotifyURIsMatch(uri, expected)
            }
            if (matchedExpected) {
                clearFastPathPlaybackGuard()
            }

            lastOutgoingTrackUri = lastObservedTrackUri
            lastOutgoingPlaybackPosition = _positionSeconds.value
            lastOutgoingDuration = _durationSeconds.value
            lastOutgoingContextUri = currentContextUri
            incomingPlaybackPosition = newPosition
            // iOS reads state.contextURI in the same callback; Android only gets context
            // from subscribeToPlayerContext, so leave incoming null until applyPlayerContext.
            incomingContextUri = null
            incomingContextAwaitingSinceMs = System.currentTimeMillis()
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
        // Optimistic + await (iOS parity). The old fire-and-forget job returned
        // before seekTo finished, so Corus re-anchored while App Remote still
        // reported the pre-scrub position.
        seekJob?.cancel()
        _positionSeconds.value = seconds
        val job = scope.async {
            awaitPlayerApiVoid(
                onSuccess = { _positionSeconds.value = seconds },
            ) { it.playerApi.seekTo(ms) }
        }
        seekJob = job
        try {
            job.await()
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            android.util.Log.w("SpotifyPlayback", "seek failed: ${error.message}")
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
            fastPathAppRemote = null
            cancelSubscription(playerStateSubscription)
            playerStateSubscription = null
            cancelSubscription(playerContextSubscription)
            playerContextSubscription = null
            delay(150)
        }
        cancelPendingContinuations()
    }

    fun stop() {
        clearFastPathPlaybackGuard()
        proactiveReconnectJob?.cancel()
        seekJob?.cancel()
        cancelPendingContinuations()
        cancelSubscription(playerStateSubscription)
        playerStateSubscription = null
        cancelSubscription(playerContextSubscription)
        playerContextSubscription = null
        appRemote?.let { remote ->
            if (remote.isConnected) {
                runCatching { remote.playerApi.pause().setResultCallback { } }
            }
            SpotifyAppRemote.disconnect(remote)
        }
        appRemote = null
        fastPathAppRemote = null
        _isPlaying.value = false
        _positionSeconds.value = 0.0
        _durationSeconds.value = 0.0
        _currentTrackUri.value = null
        lastEndedUri = null
        lastObservedTrackUri = null
        lastAppliedQueueSessionId = null
    }

    /**
     * After authorizeAndPlay, Spotify may already be audible even when App Remote
     * never finished connecting. [stop] alone won't pause in that state —
     * reconnect briefly and pause so preview fallback doesn't leave a second
     * audio engine running.
     */
    suspend fun forcePauseAfterFailedHandoff() {
        clearFastPathPlaybackGuard()
        proactiveReconnectJob?.cancel()
        seekJob?.cancel()
        cancelPendingContinuations()

        if (isConnected) {
            runCatching { pause() }
            stop()
            return
        }

        if (spotifyAuthService.cachedAccessToken() == null && !hasRecentUsage()) {
            _isPlaying.value = false
            return
        }

        runCatching {
            withTimeoutOrNull(4_000L) {
                connect(showAuthView = false)
                pause()
            }
        }.onFailure { error ->
            android.util.Log.w(
                "SpotifyPlayback",
                "forcePauseAfterFailedHandoff failed: ${error.message}",
            )
        }

        // Always tear down — pause may have succeeded even if later steps failed.
        if (isConnected) {
            runCatching { pauseImmediately() }
        }
        stop()
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
     * Adds a track or album to the user's Spotify library ("Liked Songs").
     *
     * The Spotify app performs the write over IPC, so this never reaches
     * api.spotify.com and isn't subject to the Web API's development-mode
     * 25-user cap — which is the whole reason this path exists. Requires a
     * live App Remote session; callers park the URI in [SpotifyLibraryQueue]
     * when there isn't one.
     *
     * Unlike [awaitPlayerApiVoid], a missing session throws rather than
     * silently succeeding: the caller has to tell "delivered" from "couldn't
     * deliver" to decide whether to keep the save queued.
     */
    suspend fun addToLibrary(uri: String) {
        val remote = appRemote ?: throw SpotifyPlaybackError.NotConnected()
        if (!remote.isConnected) throw SpotifyPlaybackError.NotConnected()
        val result = remote.userApi.addToLibrary(uri)
        suspendCancellableCoroutine { cont ->
            var finished = false
            fun finish(error: Throwable?) {
                if (finished) return
                finished = true
                if (!cont.isActive) return
                if (error == null) {
                    cont.resume(Unit)
                } else {
                    cont.resumeWithException(
                        error as? Exception ?: SpotifyPlaybackError.ApiError(
                            error.message ?: "Couldn't save to your Spotify library",
                        ),
                    )
                }
            }
            cont.invokeOnCancellation { finished = true }
            result.setResultCallback { finish(null) }
            result.setErrorCallback { error ->
                finish(
                    SpotifyPlaybackError.ApiError(
                        error.message ?: "Couldn't save to your Spotify library",
                    ),
                )
            }
        }
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

    private fun discardStaleAppRemoteSession() {
        spotifyAuthService.clearAccessToken()
        clearRecentUsage()
        if (appRemote?.isConnected == true) {
            runCatching {
                appRemote?.playerApi?.pause()?.setResultCallback { }
            }
            appRemote?.let { SpotifyAppRemote.disconnect(it) }
        }
        appRemote = null
        fastPathAppRemote = null
        cancelSubscription(playerStateSubscription)
        playerStateSubscription = null
        cancelSubscription(playerContextSubscription)
        playerContextSubscription = null
    }

    private fun markRecentUsage() {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putLong(LAST_USAGE_KEY, System.currentTimeMillis())
            .apply()
    }

    private fun scheduleProactiveReconnect() {
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

/**
 * App Remote `ImageUri.raw` → Spotify CDN HTTPS URL.
 * `spotify:image:<hex>` becomes `https://i.scdn.co/image/<hex>`.
 */
internal fun spotifyImageUriToHttps(raw: String?): String? {
    if (raw.isNullOrBlank()) return null
    val trimmed = raw.trim()
    if (trimmed.startsWith("https://", ignoreCase = true) ||
        trimmed.startsWith("http://", ignoreCase = true)
    ) {
        return trimmed
    }
    val id = trimmed.removePrefix("spotify:image:").trim()
    if (id.isEmpty() || id == trimmed) return null
    return "https://i.scdn.co/image/$id"
}
