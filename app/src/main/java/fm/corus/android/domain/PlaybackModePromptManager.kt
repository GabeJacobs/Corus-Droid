package fm.corus.android.domain

import fm.corus.android.data.local.PreferencesDataStore
import fm.corus.android.data.model.CymbalTrack
import fm.corus.android.data.model.MusicService
import fm.corus.android.data.model.TrackSource
import fm.corus.android.service.AnalyticsService
import fm.corus.android.service.RemoteConfigService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

data class PendingPlaybackModeChoice(
    val kind: SpotifyFtuePromptKind,
    val surface: String,
    val track: CymbalTrack? = null,
    val sourcePostId: String? = null,
    val queue: List<QueuedTrack> = emptyList(),
    val onPreview: suspend () -> Unit = {},
    val onEnableFull: suspend () -> Unit = {},
    val nowPlaying: NowPlayingManager,
    val remoteConfig: RemoteConfigService,
    val musicService: MusicService,
    val scope: CoroutineScope,
)

@Singleton
class PlaybackModePromptManager @Inject constructor(
    private val preferencesDataStore: PreferencesDataStore,
    private val analyticsService: AnalyticsService,
) {
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
        if (!debugAlwaysShowPlaybackModePrompt && catalogPromptKind() == null) {
            return false
        }
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
            // Feed art tap seeds identity before routing, so first play looks
            // like a re-tap. Still show an unconsumed B chooser or A link sheet.
            return catalogPromptKind() != null
        }
        return musicService == MusicService.SPOTIFY
    }

    fun shouldInterceptEnableFull(): Boolean {
        if (debugAlwaysShowPlaybackModePrompt) return true
        return SpotifyFtueExperiment.shouldPromptEnableFull(
            assignedVariant = SpotifyFtueVariant.fromRaw(preferencesDataStore.spotifyFtueVariantSync()),
            linkPromptConsumed = preferencesDataStore.spotifyFtueLinkPromptConsumedSync(),
            firstPlayChooserConsumed = preferencesDataStore.spotifyFtueFirstPlayChooserConsumedSync(),
        )
    }

    fun catalogPromptKind(): SpotifyFtuePromptKind? {
        val variant = SpotifyFtueVariant.fromRaw(preferencesDataStore.spotifyFtueVariantSync())
        val firstPlayConsumed = preferencesDataStore.spotifyFtueFirstPlayChooserConsumedSync()
        if (SpotifyFtueExperiment.shouldPromptFirstPlay(variant, firstPlayConsumed)) {
            return SpotifyFtuePromptKind.CHOOSE_LISTEN
        }
        if (SpotifyFtueExperiment.shouldPromptAlwaysFullPlay(
                assignedVariant = variant,
                alwaysPlayFullSongs = preferencesDataStore.alwaysPlayFullSongsSync(),
                linkPromptConsumed = preferencesDataStore.spotifyFtueLinkPromptConsumedSync(),
                firstPlayChooserConsumed = firstPlayConsumed,
            )
        ) {
            return SpotifyFtuePromptKind.LINK_SPOTIFY
        }
        return null
    }

    fun trackSupportsPreviews(track: CymbalTrack): Boolean {
        if (track.unavailable) return false
        if (track.source != TrackSource.SPOTIFY && track.source != TrackSource.APPLEMUSIC) {
            return false
        }
        if (!track.previewUrl.isNullOrBlank()) return true
        return track.id.isNotBlank() && track.name.isNotBlank()
    }

    fun present(pending: PendingPlaybackModeChoice) {
        logPromptShown(pending)
        _pending.value = pending
    }

    fun interceptEnableFull(
        surface: String,
        nowPlaying: NowPlayingManager,
        remoteConfig: RemoteConfigService,
        musicService: MusicService,
        scope: CoroutineScope,
        onEnableFull: suspend () -> Unit,
    ): Boolean {
        if (!shouldInterceptEnableFull()) return false
        present(
            PendingPlaybackModeChoice(
                kind = SpotifyFtuePromptKind.LINK_SPOTIFY,
                surface = surface,
                onEnableFull = onEnableFull,
                nowPlaying = nowPlaying,
                remoteConfig = remoteConfig,
                musicService = musicService,
                scope = scope,
            ),
        )
        return true
    }

    fun choosePreviews() {
        val pending = _pending.value ?: return
        _pending.value = null
        pending.scope.launch {
            preferencesDataStore.setSpotifyFtueFirstPlayChooserConsumed()
            logChoice(pending, "previews")
            preferencesDataStore.setAlwaysPlayFullSongs(false)
            if (!debugAlwaysShowPlaybackModePrompt) {
                markChosen()
            }
            pending.onPreview()
        }
    }

    fun chooseNotNow() {
        val pending = _pending.value ?: return
        _pending.value = null
        pending.scope.launch {
            logChoice(pending, "not_now")
            preferencesDataStore.setAlwaysPlayFullSongs(false)
            if (!debugAlwaysShowPlaybackModePrompt) {
                markChosen()
            }
            if (pending.track != null) {
                pending.onPreview()
            }
        }
    }

    fun chooseLinkSpotify() {
        val pending = _pending.value ?: return
        _pending.value = null
        pending.scope.launch {
            preferencesDataStore.setSpotifyFtueLinkPromptConsumed()
            if (pending.kind == SpotifyFtuePromptKind.CHOOSE_LISTEN) {
                preferencesDataStore.setSpotifyFtueFirstPlayChooserConsumed()
                preferencesDataStore.setAlwaysPlayFullSongs(true)
            }
            logChoice(pending, "link")
            if (!debugAlwaysShowPlaybackModePrompt) {
                markChosen()
            }
            when (pending.kind) {
                SpotifyFtuePromptKind.LINK_SPOTIFY -> {
                    if (pending.surface == SpotifyFtuePromptSurface.SETTINGS_ALWAYS_FULL) {
                        preferencesDataStore.setAlwaysPlayFullSongs(true)
                    } else {
                        preferencesDataStore.setPlayFullSongs(true)
                    }
                    if (pending.track != null) {
                        val track = pending.track
                        val outcome = FullSongPlayCoordinator.playTapOutcome(
                            track = track,
                            sourcePostId = pending.sourcePostId,
                            queue = pending.queue,
                            nowPlaying = pending.nowPlaying,
                            remoteConfig = pending.remoteConfig,
                            musicService = pending.musicService,
                            playFullSongs = true,
                            playbackModePromptManager = this@PlaybackModePromptManager,
                            skipPlaybackModePrompt = true,
                            preferFullSong = true,
                        )
                        FullSongPlayCoordinator.applyPlayTapOutcome(
                            outcome = outcome,
                            track = track,
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
                    } else {
                        pending.onEnableFull()
                    }
                }
                SpotifyFtuePromptKind.CHOOSE_LISTEN -> {
                    val track = pending.track ?: return@launch
                    val outcome = FullSongPlayCoordinator.playTapOutcome(
                        track = track,
                        sourcePostId = pending.sourcePostId,
                        queue = pending.queue,
                        nowPlaying = pending.nowPlaying,
                        remoteConfig = pending.remoteConfig,
                        musicService = pending.musicService,
                        playFullSongs = true,
                        playbackModePromptManager = this@PlaybackModePromptManager,
                        skipPlaybackModePrompt = true,
                        preferFullSong = true,
                    )
                    FullSongPlayCoordinator.applyPlayTapOutcome(
                        outcome = outcome,
                        track = track,
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
    }

    private fun logPromptShown(pending: PendingPlaybackModeChoice) {
        analyticsService.logSpotifyAuthConnectPromptShown(
            trackId = pending.track?.id.orEmpty(),
            variant = preferencesDataStore.spotifyFtueVariantSync() ?: "off",
            surface = pending.surface,
        )
    }

    private fun logChoice(pending: PendingPlaybackModeChoice, choice: String) {
        analyticsService.logSpotifyFtuePromptChosen(
            variant = preferencesDataStore.spotifyFtueVariantSync() ?: "off",
            surface = pending.surface,
            choice = choice,
        )
    }
}
