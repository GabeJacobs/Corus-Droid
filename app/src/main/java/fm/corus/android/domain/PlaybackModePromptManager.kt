package fm.corus.android.domain

import fm.corus.android.data.local.PreferencesDataStore
import fm.corus.android.data.model.CymbalTrack
import fm.corus.android.data.model.MusicService
import fm.corus.android.data.model.TrackSource
import fm.corus.android.service.RemoteConfigService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

data class PendingPlaybackModeChoice(
    val track: CymbalTrack,
    val sourcePostId: String?,
    val queue: List<QueuedTrack>,
    val onPreview: suspend () -> Unit,
    val nowPlaying: NowPlayingManager,
    val remoteConfig: RemoteConfigService,
    val musicService: MusicService,
    val scope: CoroutineScope,
)

/**
 * One-time chooser on first eligible catalog play (new users and existing users
 * after the rollout migration). Device-local flag in [PreferencesDataStore].
 */
@Singleton
class PlaybackModePromptManager @Inject constructor(
    private val preferencesDataStore: PreferencesDataStore,
) {
    /** Permanently disabled — same model as iOS (feed previews + per-post full button). */
    var promptEnabled: Boolean = false

    /** TEMP — set to `false` before shipping. Shows the chooser on every eligible play tap. */
    var debugAlwaysShowPlaybackModePrompt: Boolean = false

    private val _pending = MutableStateFlow<PendingPlaybackModeChoice?>(null)
    val pending: StateFlow<PendingPlaybackModeChoice?> = _pending.asStateFlow()

    fun hasChosenPlaybackMode(): Boolean = preferencesDataStore.playbackModeChosenSync()

    suspend fun markChosen() {
        preferencesDataStore.setPlaybackModeChosen(true)
    }

    fun shouldPrompt(
        track: CymbalTrack,
        sourcePostId: String?,
        musicService: MusicService,
        nowPlaying: NowPlayingManager,
        skipForResume: Boolean = false,
    ): Boolean {
        if (skipForResume) return false
        if (!promptEnabled) return false
        if (!debugAlwaysShowPlaybackModePrompt && hasChosenPlaybackMode()) return false
        if (!trackSupportsPreviews(track)) return false
        if (track.source != TrackSource.SPOTIFY && track.source != TrackSource.APPLEMUSIC) {
            return false
        }
        if (isReTapOfActiveEntry(
                activeTrackId = nowPlaying.currentTrackId,
                activeSourcePostId = nowPlaying.currentSourcePostId,
                tappedTrackId = track.id,
                tappedSourcePostId = sourcePostId,
            )
        ) {
            return false
        }
        return when (musicService) {
            MusicService.SPOTIFY -> true
            // Apple Music full playback is iOS-only today; web has its own path.
            MusicService.APPLE_MUSIC -> false
            else -> false
        }
    }

    /** Only offer the chooser when a preview is actually available. */
    fun trackSupportsPreviews(track: CymbalTrack): Boolean {
        if (track.unavailable) return false
        if (track.source != TrackSource.SPOTIFY && track.source != TrackSource.APPLEMUSIC) {
            return false
        }
        if (!track.previewUrl.isNullOrBlank()) return true
        return track.id.isNotBlank() && track.name.isNotBlank()
    }

    fun present(pending: PendingPlaybackModeChoice) {
        _pending.value = pending
    }

    fun choosePreviews() {
        val pending = _pending.value ?: return
        _pending.value = null
        pending.scope.launch {
            preferencesDataStore.setPlayFullSongs(false)
            if (!debugAlwaysShowPlaybackModePrompt) {
                markChosen()
            }
            pending.onPreview()
        }
    }

    fun chooseFullSongs() {
        val pending = _pending.value ?: return
        _pending.value = null
        pending.scope.launch {
            preferencesDataStore.setPlayFullSongs(true)
            if (!debugAlwaysShowPlaybackModePrompt) {
                markChosen()
            }
            val outcome = FullSongPlayCoordinator.playTapOutcome(
                track = pending.track,
                sourcePostId = pending.sourcePostId,
                queue = pending.queue,
                nowPlaying = pending.nowPlaying,
                remoteConfig = pending.remoteConfig,
                musicService = pending.musicService,
                playFullSongs = true,
                playbackModePromptManager = this@PlaybackModePromptManager,
                skipPlaybackModePrompt = true,
            )
            FullSongPlayCoordinator.applyPlayTapOutcome(
                outcome = outcome,
                track = pending.track,
                sourcePostId = pending.sourcePostId,
                queue = pending.queue,
                nowPlaying = pending.nowPlaying,
                remoteConfig = pending.remoteConfig,
                musicService = pending.musicService,
                playFullSongs = true,
                playbackModePromptManager = this@PlaybackModePromptManager,
                onPreview = pending.onPreview,
                scope = pending.scope,
            )
        }
    }
}
