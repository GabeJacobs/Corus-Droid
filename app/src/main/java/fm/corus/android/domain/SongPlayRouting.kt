package fm.corus.android.domain

import android.content.Context
import fm.corus.android.data.local.PreferencesDataStore
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

    /**
     * True when this identity can be sent to Spotify App Remote as a
     * `spotify:track:` URI. Apple-sourced rows (`am:…`) and empty URIs cannot —
     * Connect would open Spotify onto nothing.
     */
    fun hasPlayableSpotifyId(trackId: String?, spotifyURI: String?): Boolean {
        val uri = spotifyURI?.takeIf { it.startsWith("spotify:track:") }
        if (uri != null) {
            return uri.removePrefix("spotify:track:").length == 22
        }
        val id = trackId ?: return false
        if (id.startsWith("am:") || id.startsWith("sc:") ||
            id.startsWith("amk:") || id.startsWith("tdl:") || id.startsWith("dzr:")
        ) {
            return false
        }
        return id.length == 22
    }

    /**
     * True when Spotify App Remote full-playback should intercept a play tap.
     *
     * Artist Popular / album rows are Apple-sourced on purpose (they carry a
     * 30s previewUrl) even when the recording is on Spotify. Those taps still
     * belong on Connect when the user wants full songs — playViaSpotifyConnect
     * resolves a real `spotify:track:` URI via ISRC before opening the app.
     * `am:` ids are never sent as Spotify URIs. A lookup miss falls back to
     * the 30s preview. [trackId] / [spotifyURI] are kept for callers and tests;
     * they no longer gate this function.
     */
    fun wantsSpotifyAuthExperiment(
        source: TrackSource,
        service: MusicService,
        playFullSongs: Boolean,
        @Suppress("UNUSED_PARAMETER") trackId: String? = null,
        @Suppress("UNUSED_PARAMETER") spotifyURI: String? = null,
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
        trackId: String? = null,
        spotifyURI: String? = null,
    ): Boolean = wantsSpotifyAuthExperiment(source, service, playFullSongs, trackId, spotifyURI)

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
            // Apple Music full songs are iOS/MusicKit-only — never on Android.
            // TIDAL full streaming has no Android player engine yet — don't advertise.
            MusicService.SPOTIFY -> SpotifyPlaybackService.isSpotifyAppInstalled(context)
            else -> false
        }
    }

    /**
     * Service whose logo the mini-player / full-player shows, and that tapping
     * it opens. Matches the feed post badge:
     *
     * Apple-Music-only tracks (`source == APPLEMUSIC`) aren't on Spotify, so a
     * Spotify viewer sees and opens Apple Music — the service that actually
     * carries the song. Waiting for a link-out tap to "confirm" absence left
     * the mini-player showing Spotify on songs the feed already badged as
     * Apple Music. TIDAL / Deezer / YouTube Music viewers keep their own
     * service. Source-locked logos (SoundCloud, Audiomack, TIDAL, Deezer) are
     * handled by the caller.
     */
    fun displayedLinkOutService(
        source: TrackSource,
        viewer: MusicService,
    ): MusicService =
        if (source == TrackSource.APPLEMUSIC && viewer == MusicService.SPOTIFY) {
            MusicService.APPLE_MUSIC
        } else {
            viewer
        }

    /** Whether the mini-player 30s/Full toggle should appear (Always Play Full Songs off). */
    fun showsFeedPlaybackModeToggle(alwaysPlayFullSongs: Boolean): Boolean =
        !alwaysPlayFullSongs

    fun showsFeedPlaybackModeToggle(preferencesDataStore: PreferencesDataStore): Boolean =
        showsFeedPlaybackModeToggle(preferencesDataStore.alwaysPlayFullSongsSync())

    /**
     * Mini-player / full-player 30s/Full toggle visibility.
     * Hidden while mirroring external Spotify, when Always Play Full Songs is on,
     * or when in-app full playback isn't available for this track/service.
     */
    fun showsMiniPlayerPlaybackModeToggle(
        context: Context,
        source: TrackSource,
        service: MusicService,
        remoteConfig: RemoteConfigService,
        isExternalSpotifyListening: Boolean,
        alwaysPlayFullSongs: Boolean,
    ): Boolean {
        if (isExternalSpotifyListening) return false
        if (!showsFeedPlaybackModeToggle(alwaysPlayFullSongs)) return false
        return supportsInAppFullSong(context, source, service, remoteConfig)
    }

    fun realizedSessionMatchesDesiredMode(
        isPreviewMode: Boolean,
        desiresFullSong: Boolean,
    ): Boolean = isPreviewMode != desiresFullSong

    fun shouldRestartPausedSessionForDesiredMode(
        hasActiveTrack: Boolean,
        isPlaying: Boolean,
        isPreviewMode: Boolean,
        desiresFullSong: Boolean,
        isExternalSpotifyListening: Boolean = false,
    ): Boolean {
        if (!hasActiveTrack || isPlaying || isExternalSpotifyListening) return false
        return !realizedSessionMatchesDesiredMode(isPreviewMode, desiresFullSong)
    }

    /** Album/song/artist pages follow the mini-player 30s/Full toggle (and Always Play Full Songs). */
    fun preferFullPlaybackOnCatalog(preferencesDataStore: PreferencesDataStore): Boolean =
        preferencesDataStore.effectivePlayFullSongsSync()

    /** Whether the viewer's service can play full tracks in-app at all (ignores toggle). */
    fun catalogListeningEntitled(
        context: Context,
        service: MusicService,
        remoteConfig: RemoteConfigService,
    ): Boolean {
        if (!nativeAppInstalled(context, service)) return false
        return when (service) {
            MusicService.SPOTIFY -> SpotifyPlaybackService.isSpotifyAppInstalled(context)
            // TIDAL full streaming is iOS-only until an Android player ships.
            MusicService.TIDAL, MusicService.APPLE_MUSIC, MusicService.DEEZER, MusicService.YOUTUBE_MUSIC -> false
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
