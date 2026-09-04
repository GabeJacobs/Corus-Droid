package fm.corus.android.domain

import android.util.Log
import fm.corus.android.data.model.CymbalTrack
import fm.corus.android.data.model.MusicService
import fm.corus.android.service.RemoteConfigService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Shared full-song vs preview routing for catalog play taps.
 */
object FullSongPlayCoordinator {
    enum class PlayTapOutcome {
        UsePreview,
        HandledByExperiment,
        TogglePause,
        CancelLoading,
        NeedsPlaybackModeChoice,
    }

    suspend fun playTapOutcome(
        track: CymbalTrack,
        sourcePostId: String? = null,
        queue: List<QueuedTrack> = emptyList(),
        nowPlaying: NowPlayingManager,
        remoteConfig: RemoteConfigService,
        musicService: MusicService,
        playFullSongs: Boolean,
        playbackModePromptManager: PlaybackModePromptManager,
        skipPlaybackModePrompt: Boolean = false,
        preferFullSong: Boolean = false,
        forcePlay: Boolean = false,
    ): PlayTapOutcome {
        remoteConfig.awaitInitialFetch()
        if (playbackModePromptManager.shouldPrompt(
                track = track,
                sourcePostId = sourcePostId,
                musicService = musicService,
                nowPlaying = nowPlaying,
                skipForResume = skipPlaybackModePrompt || preferFullSong,
            )
        ) {
            return PlayTapOutcome.NeedsPlaybackModeChoice
        }

        val playFull = preferFullSong || playFullSongs

        if (SongPlayRouting.wantsSpotifyAuthExperiment(
                source = track.source,
                service = musicService,
                playFullSongs = playFull,
                trackId = track.id,
                spotifyURI = track.spotifyURI,
                knownNotOnSpotify = track.notOnSpotify,
            )
        ) {
            seedQueueIfNeeded(track, sourcePostId, queue, nowPlaying)
            return spotifyExperimentOutcome(
                track = track,
                sourcePostId = sourcePostId,
                nowPlaying = nowPlaying,
                preferFullSong = preferFullSong,
                forcePlay = forcePlay,
            )
        }

        Log.w(
            "SpotifyPlayRouting",
            "UsePreview track=${track.id} source=${track.source} " +
                "service=$musicService playFullSongs=$playFull preferFullSong=$preferFullSong",
        )
        return PlayTapOutcome.UsePreview
    }

    private fun seedQueueIfNeeded(
        track: CymbalTrack,
        sourcePostId: String?,
        queue: List<QueuedTrack>,
        nowPlaying: NowPlayingManager,
    ) {
        if (queue.isEmpty()) return
        nowPlaying.setQueueFromCoordinator(
            queue = queue,
            playingTrackId = track.id,
            playingSourcePostId = sourcePostId,
        )
    }

    private fun spotifyExperimentOutcome(
        track: CymbalTrack,
        sourcePostId: String?,
        nowPlaying: NowPlayingManager,
        preferFullSong: Boolean,
        forcePlay: Boolean = false,
    ): PlayTapOutcome {
        val isReTap = isReTapOfActiveEntry(
            activeTrackId = nowPlaying.currentTrackId,
            activeSourcePostId = nowPlaying.currentSourcePostId,
            tappedTrackId = track.id,
            tappedSourcePostId = sourcePostId,
        )
        if (!forcePlay && isReTap) {
            if (nowPlaying.isResolvingSpotify) {
                return PlayTapOutcome.CancelLoading
            }
            if (nowPlaying.isSpotifyConnectPlaying) {
                nowPlaying.togglePlayPause()
                return PlayTapOutcome.HandledByExperiment
            }
            if (nowPlaying.isPreviewMode && !preferFullSong) {
                return PlayTapOutcome.UsePreview
            }
        }
        if (SpotifyPlaybackExperiment.begin(
                track = track,
                sourcePostId = sourcePostId,
                nowPlaying = nowPlaying,
                preferFullSong = preferFullSong,
            )
        ) {
            return PlayTapOutcome.HandledByExperiment
        }
        return PlayTapOutcome.UsePreview
    }

    fun applyPlayTapOutcome(
        outcome: PlayTapOutcome,
        track: CymbalTrack,
        sourcePostId: String? = null,
        queue: List<QueuedTrack> = emptyList(),
        nowPlaying: NowPlayingManager,
        remoteConfig: RemoteConfigService,
        musicService: MusicService,
        playFullSongs: Boolean,
        playbackModePromptManager: PlaybackModePromptManager,
        onPreview: suspend () -> Unit,
        scope: CoroutineScope,
    ) {
        when (outcome) {
            PlayTapOutcome.NeedsPlaybackModeChoice -> {
                val kind = playbackModePromptManager.catalogPromptKind()
                    ?: SpotifyFtuePromptKind.CHOOSE_LISTEN
                playbackModePromptManager.present(
                    PendingPlaybackModeChoice(
                        kind = kind,
                        surface = if (kind == SpotifyFtuePromptKind.LINK_SPOTIFY) {
                            SpotifyFtuePromptSurface.SETTINGS_ALWAYS_FULL
                        } else {
                            SpotifyFtuePromptSurface.FIRST_PLAY
                        },
                        track = track,
                        sourcePostId = sourcePostId,
                        queue = queue,
                        onPreview = onPreview,
                        nowPlaying = nowPlaying,
                        remoteConfig = remoteConfig,
                        musicService = musicService,
                        scope = scope,
                    ),
                )
            }
            PlayTapOutcome.UsePreview -> scope.launch { onPreview() }
            PlayTapOutcome.CancelLoading -> nowPlaying.cancelLoading()
            PlayTapOutcome.HandledByExperiment, PlayTapOutcome.TogglePause -> Unit
        }
    }
}

/** Gates Spotify App Remote full-playback routing. */
object SpotifyPlaybackExperiment {
    fun shouldIntercept(
        source: fm.corus.android.data.model.TrackSource,
        nowPlaying: NowPlayingManager,
        preferFullSong: Boolean = false,
        trackId: String? = null,
        spotifyURI: String? = null,
    ): Boolean = nowPlaying.spotifyExperimentEnabledForTrack(
        source, preferFullSong, trackId, spotifyURI,
    )

    fun begin(
        track: CymbalTrack,
        sourcePostId: String? = null,
        nowPlaying: NowPlayingManager,
        @Suppress("UNUSED_PARAMETER") scope: CoroutineScope? = null,
        preferFullSong: Boolean = false,
    ): Boolean {
        if (!nowPlaying.spotifyExperimentEnabledForTrack(
                source = track.source,
                preferFullSong = preferFullSong,
                trackId = track.id,
                spotifyURI = track.spotifyURI,
                knownNotOnSpotify = track.notOnSpotify,
            )
        ) return false
        val pending = SpotifyAuthPendingPlay(
            trackId = track.id,
            name = track.name,
            artist = track.artistName,
            isrc = track.isrc,
            albumArtURL = track.albumArtURL,
            albumArtLargeURL = track.albumArtLargeURL,
            spotifyWebURL = track.spotifyWebURL,
            spotifyURI = track.spotifyURI,
            sourcePostId = sourcePostId,
            source = track.source,
        )
        // Always go through [NowPlayingManager.launchSpotifyConnectPlay] so the
        // Job is tracked and quick Next can cancel a just-started Connect play.
        nowPlaying.launchSpotifyConnectPlay {
            nowPlaying.playViaSpotifyConnect(pending, replaceSpotifyQueue = true)
        }
        return true
    }
}
