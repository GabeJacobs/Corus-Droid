package fm.corus.android.ui.player

import fm.corus.android.data.model.CymbalPost
import fm.corus.android.data.model.CymbalTrack
import fm.corus.android.data.model.CymbalUser
import fm.corus.android.data.model.MusicService
import fm.corus.android.data.model.TrackSource
import fm.corus.android.domain.NowPlayingState

/**
 * Overflow "Open in …" / "Play in …" copy — mirrors feed [PostActionMenu] and
 * iOS `BlankFullPlayerView.openInServiceMenuTitle`.
 */
internal fun fullPlayerOpenInServiceLabelKey(
    source: TrackSource,
    musicService: MusicService,
): FullPlayerOpenInLabel {
    return when (source) {
        TrackSource.SOUNDCLOUD -> FullPlayerOpenInLabel.OpenSoundCloud
        TrackSource.AUDIOMACK -> FullPlayerOpenInLabel.OpenAudiomack
        TrackSource.TIDAL -> FullPlayerOpenInLabel.OpenTidal
        TrackSource.DEEZER -> FullPlayerOpenInLabel.OpenDeezer
        TrackSource.APPLEMUSIC -> FullPlayerOpenInLabel.PlayIn(MusicService.APPLE_MUSIC.displayLabel)
        else -> FullPlayerOpenInLabel.PlayIn(musicService.displayLabel)
    }
}

internal sealed class FullPlayerOpenInLabel {
    data object OpenSoundCloud : FullPlayerOpenInLabel()
    data object OpenAudiomack : FullPlayerOpenInLabel()
    data object OpenTidal : FullPlayerOpenInLabel()
    data object OpenDeezer : FullPlayerOpenInLabel()
    data class PlayIn(val serviceLabel: String) : FullPlayerOpenInLabel()
}

/**
 * Post used for menu gating / destination taps. Prefers the loaded source post;
 * otherwise a synthetic track post from now-playing (catalog / no-caption plays).
 */
internal fun fullPlayerMenuPost(
    sourcePost: CymbalPost?,
    state: NowPlayingState,
): CymbalPost? {
    if (sourcePost != null) return sourcePost
    val trackId = state.trackId ?: return null
    return CymbalPost(
        id = "_full_player",
        user = CymbalUser(id = "", username = "", displayName = ""),
        track = CymbalTrack(
            id = trackId,
            name = state.trackName,
            artistName = state.artistName,
            albumName = "",
            albumArtURL = state.albumArtURL,
            albumArtLargeURL = state.albumArtLargeURL,
            spotifyURI = state.spotifyURI.orEmpty(),
            spotifyWebURL = state.spotifyWebURL.orEmpty(),
            isrc = state.isrc,
            source = state.source,
            audiomackUrl = state.audiomackUrl,
        ),
    )
}

/**
 * iOS `BlankFullPlayerView.canResolveArtistRow` — artist pages on, and source
 * is not SoundCloud / Tidal / Deezer (Spotify, Apple Music, Audiomack show).
 */
internal fun fullPlayerShowsArtistRow(
    source: TrackSource?,
    artistPagesEnabled: Boolean,
): Boolean {
    if (!artistPagesEnabled || source == null) return false
    return source != TrackSource.SOUNDCLOUD &&
        source != TrackSource.TIDAL &&
        source != TrackSource.DEEZER
}

/**
 * iOS `BlankFullPlayerView.canResolveAlbumRow` — artist pages on, and source
 * is Spotify / Apple Music only.
 */
internal fun fullPlayerShowsAlbumRow(
    source: TrackSource?,
    artistPagesEnabled: Boolean,
): Boolean {
    if (!artistPagesEnabled || source == null) return false
    return source != TrackSource.SOUNDCLOUD &&
        source != TrackSource.AUDIOMACK &&
        source != TrackSource.TIDAL &&
        source != TrackSource.DEEZER
}

internal fun fullPlayerShowsShareRow(sourcePost: CymbalPost?): Boolean = sourcePost != null

/** Prefer source-post track source, else now-playing — same as iOS chrome. */
internal fun fullPlayerMenuTrackSource(
    sourcePost: CymbalPost?,
    state: NowPlayingState,
): TrackSource? {
    sourcePost?.track?.source?.let { return it }
    return state.trackId?.let { state.source }
}
