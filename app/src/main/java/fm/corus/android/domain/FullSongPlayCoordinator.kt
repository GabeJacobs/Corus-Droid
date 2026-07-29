package fm.corus.android.domain

import android.util.Log
import fm.corus.android.data.model.CymbalTrack
import fm.corus.android.data.model.MusicService
import fm.corus.android.service.RemoteConfigService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Shared full-song vs preview routing for catalog play taps. All Spotify
 * experiment branches are no-ops when the Remote Config flag is off.
 */
object FullSongPlayCoordinator {
    enum class PlayTapOutcome {
        UsePreview,
        HandledByExperiment,
        TogglePause,
        CancelLoading,
    }

    suspend fun playTapOutcome(
        track: CymbalTrack,
        sourcePostId: String? = null,
        queue: List<QueuedTrack> = emptyList(),
        nowPlaying: NowPlayingManager,
        remoteConfig: RemoteConfigService,
        musicService: MusicService,
        playFullSongs: Boolean,
    ): PlayTapOutcome {
        remoteConfig.awaitInitialFetch()
        if (nowPlaying.spotifyExperimentEnabledForTrack(track.source)) {
            seedQueueIfNeeded(track, sourcePostId, queue, nowPlaying)
            return spotifyExperimentOutcome(track, sourcePostId, nowPlaying)
        }
        Log.w(
            "SpotifyPlayRouting",
            "UsePreview track=${track.id} source=${track.source} " +
                "experiment=${remoteConfig.spotifyAuthExperimentEnabled} " +
                "service=$musicService playFullSongs=$playFullSongs",
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
    ): PlayTapOutcome {
        val isReTap = isReTapOfActiveEntry(
            activeTrackId = nowPlaying.currentTrackId,
            activeSourcePostId = nowPlaying.currentSourcePostId,
            tappedTrackId = track.id,
            tappedSourcePostId = sourcePostId,
        )
        if (isReTap) {
            if (nowPlaying.isResolvingSpotify) {
                return PlayTapOutcome.CancelLoading
            }
            if (nowPlaying.isSpotifyConnectPlaying) {
                nowPlaying.togglePlayPause()
                return PlayTapOutcome.HandledByExperiment
            }
            if (nowPlaying.isPreviewMode) {
                return PlayTapOutcome.UsePreview
            }
        }
        SpotifyPlaybackExperiment.begin(track, sourcePostId, nowPlaying)
        return PlayTapOutcome.HandledByExperiment
    }

    fun applyPlayTapOutcome(
        outcome: PlayTapOutcome,
        nowPlaying: NowPlayingManager,
        onPreview: suspend () -> Unit,
        scope: CoroutineScope,
    ) {
        when (outcome) {
            PlayTapOutcome.UsePreview -> scope.launch { onPreview() }
            PlayTapOutcome.CancelLoading -> nowPlaying.cancelLoading()
            PlayTapOutcome.HandledByExperiment, PlayTapOutcome.TogglePause -> Unit
        }
    }
}

/** Gates the Spotify user-OAuth full-playback experiment behind Remote Config. */
object SpotifyPlaybackExperiment {
    fun shouldIntercept(
        source: fm.corus.android.data.model.TrackSource,
        nowPlaying: NowPlayingManager,
    ): Boolean = nowPlaying.spotifyExperimentEnabledForTrack(source)

    fun begin(
        track: CymbalTrack,
        sourcePostId: String? = null,
        nowPlaying: NowPlayingManager,
        scope: CoroutineScope? = null,
    ): Boolean {
        if (!nowPlaying.spotifyExperimentEnabledForTrack(track.source)) return false
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
        val launchBlock: suspend () -> Unit = {
            nowPlaying.playViaSpotifyConnect(pending)
        }
        if (scope != null) {
            scope.launch { launchBlock() }
        } else {
            nowPlaying.launchSpotifyConnectPlay(launchBlock)
        }
        return true
    }
}
