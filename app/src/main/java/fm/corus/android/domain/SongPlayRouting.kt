package fm.corus.android.domain

import android.content.Context
import fm.corus.android.data.model.MusicService
import fm.corus.android.data.model.TrackSource
import fm.corus.android.service.RemoteConfigService

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

    /** True when Spotify App Remote full-playback should intercept a play tap. */
    fun wantsSpotifyAuthExperiment(
        source: TrackSource,
        service: MusicService,
        playFullSongs: Boolean,
    ): Boolean {
        if (service != MusicService.SPOTIFY || !playFullSongs) return false
        return when (source) {
            TrackSource.SPOTIFY, TrackSource.APPLEMUSIC -> true
            TrackSource.SOUNDCLOUD, TrackSource.AUDIOMACK, TrackSource.TIDAL, TrackSource.DEEZER -> false
        }
    }

    fun wantsSpotifyExperiment(
        source: TrackSource,
        service: MusicService,
        playFullSongs: Boolean,
    ): Boolean = wantsSpotifyAuthExperiment(source, service, playFullSongs)

    /** Whether in-app full playback is available for this track source (ignores Settings). */
    fun supportsInAppFullSong(
        context: Context,
        source: TrackSource,
        service: MusicService,
        remoteConfig: RemoteConfigService,
    ): Boolean {
        if (!nativeAppInstalled(context, service)) return false
        when (source) {
            TrackSource.SPOTIFY, TrackSource.APPLEMUSIC -> Unit
            TrackSource.SOUNDCLOUD, TrackSource.AUDIOMACK, TrackSource.TIDAL, TrackSource.DEEZER -> return false
        }
        return when (service) {
            MusicService.SPOTIFY -> SpotifyPlaybackService.isSpotifyAppInstalled(context)
            MusicService.TIDAL -> tidalFullPlaybackEnabled(remoteConfig)
            else -> false
        }
    }

    /** Album, song, and artist Popular pages play full when entitled — not Settings. */
    fun catalogListeningEntitled(
        context: Context,
        service: MusicService,
        remoteConfig: RemoteConfigService,
    ): Boolean {
        if (!nativeAppInstalled(context, service)) return false
        return when (service) {
            MusicService.SPOTIFY -> SpotifyPlaybackService.isSpotifyAppInstalled(context)
            MusicService.TIDAL -> tidalFullPlaybackEnabled(remoteConfig)
            MusicService.APPLE_MUSIC, MusicService.DEEZER, MusicService.YOUTUBE_MUSIC -> false
        }
    }

    fun tidalFullPlaybackEnabled(remoteConfig: RemoteConfigService): Boolean =
        remoteConfig.tidalEnabled && remoteConfig.tidalFullPlaybackEnabled

    fun nativeAppInstalled(context: Context, service: MusicService): Boolean =
        when (service) {
            MusicService.SPOTIFY -> SpotifyPlaybackService.isSpotifyAppInstalled(context)
            MusicService.TIDAL, MusicService.DEEZER, MusicService.YOUTUBE_MUSIC -> true
            MusicService.APPLE_MUSIC -> false
        }
}
