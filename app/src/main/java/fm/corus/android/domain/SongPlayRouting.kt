package fm.corus.android.domain

import fm.corus.android.data.model.MusicService
import fm.corus.android.data.model.TrackSource

/**
 * Where a play tap on a song row sends its audio. Single testable rule shared
 * across feed, detail, search, and comment attachment surfaces.
 */
object SongPlayRouting {
    /** True when a play tap should go to MusicKit full-song playback (iOS only). */
    fun wantsFullSong(
        source: TrackSource,
        service: MusicService,
        playFullSongs: Boolean,
    ): Boolean {
        if (service != MusicService.APPLE_MUSIC || !playFullSongs) return false
        return when (source) {
            TrackSource.SPOTIFY, TrackSource.APPLEMUSIC -> true
            TrackSource.SOUNDCLOUD, TrackSource.AUDIOMACK, TrackSource.TIDAL, TrackSource.DEEZER -> false
        }
    }

    /** True when the Spotify OAuth full-playback experiment should intercept a play tap. */
    fun wantsSpotifyAuthExperiment(
        source: TrackSource,
        service: MusicService,
        experimentEnabled: Boolean,
        playFullSongs: Boolean,
    ): Boolean {
        if (!experimentEnabled || service != MusicService.SPOTIFY || !playFullSongs) return false
        return when (source) {
            TrackSource.SPOTIFY, TrackSource.APPLEMUSIC -> true
            TrackSource.SOUNDCLOUD, TrackSource.AUDIOMACK, TrackSource.TIDAL, TrackSource.DEEZER -> false
        }
    }

    fun wantsSpotifyExperiment(
        source: TrackSource,
        service: MusicService,
        experimentEnabled: Boolean,
        playFullSongs: Boolean,
    ): Boolean = wantsSpotifyAuthExperiment(source, service, experimentEnabled, playFullSongs)
}
