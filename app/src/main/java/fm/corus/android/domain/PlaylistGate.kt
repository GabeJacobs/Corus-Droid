package fm.corus.android.domain

import fm.corus.android.data.model.MusicService

/**
 * Whether tapping "Generate Playlist" should show the "Generate Spotify
 * Playlist?" alert first (vs. generating directly). Single source of truth
 * for the feed and profile screens, which used to duplicate this logic.
 *
 * - **TIDAL** builds the playlist on the user's own TIDAL account (client-side),
 *   so it never shows the alert — and TIDAL silently skips SoundCloud tracks.
 * - **Apple Music / Deezer** have no client-side playlist path on Android (Apple
 *   Music needs iOS-only MusicKit; Deezer is link-out only), so they still get
 *   the Spotify alert — confirming builds a Spotify playlist server-side.
 * - **Spotify** only shows the alert to warn that SoundCloud tracks (which can't
 *   go in a Spotify playlist) will be skipped.
 */
fun shouldShowSpotifyPlaylistAlert(service: MusicService, hasSoundCloud: Boolean): Boolean =
    usesSpotifyFallback(service) || (service == MusicService.SPOTIFY && hasSoundCloud)

const val SPOTIFY_PLAYLIST_ALERT_TITLE = "Generate Spotify Playlist?"

/** Body for [SPOTIFY_PLAYLIST_ALERT_TITLE]. SoundCloud skip when it applies. */
fun spotifyPlaylistAlertMessage(hasSoundCloud: Boolean): String =
    if (hasSoundCloud) "SoundCloud songs will be skipped."
    else "This creates a Spotify playlist."

/** Quick-vs-all export chooser when a source has more than 75 songs. */
const val PLAYLIST_EXPORT_CHOOSER_TITLE = "Export playlist"

enum class PlaylistExportChooserSource {
    OwnProfile,
    OtherProfile,
    Hashtag,
}

/** Where the playlist actually lands for this viewer preference on Android. */
fun playlistExportDestinationLabel(service: MusicService): String =
    if (usesSpotifyFallback(service) || service == MusicService.SPOTIFY) {
        MusicService.SPOTIFY.displayLabel
    } else {
        service.displayLabel
    }

/**
 * Body for [PLAYLIST_EXPORT_CHOOSER_TITLE]. Names the real destination and only
 * mentions SoundCloud when those songs would be skipped from a Spotify playlist.
 */
fun playlistExportChooserMessage(
    source: PlaylistExportChooserSource,
    service: MusicService,
    hasSoundCloud: Boolean,
): String {
    val label = playlistExportDestinationLabel(service)
    val article = if (label == MusicService.APPLE_MUSIC.displayLabel) "An" else "A"
    val base = when (source) {
        PlaylistExportChooserSource.OwnProfile ->
            "$article $label playlist of your profile."
        PlaylistExportChooserSource.OtherProfile ->
            "$article $label playlist of their profile."
        PlaylistExportChooserSource.Hashtag ->
            "$article $label playlist from this hashtag."
    }
    val isSpotifyDestination = label == MusicService.SPOTIFY.displayLabel
    return if (hasSoundCloud && isSpotifyDestination) {
        "$base SoundCloud songs will be skipped."
    } else {
        base
    }
}

/**
 * Whether the service has no native playlist path on Android and therefore
 * always produces a Spotify playlist instead (Apple Music needs iOS-only
 * MusicKit; Deezer and YouTube Music are link-out only). These always show the
 * "Generate Spotify Playlist?" warning and never the first-time export
 * explainer. TIDAL and Spotify export natively, so they get the one-time
 * explainer instead.
 */
fun usesSpotifyFallback(service: MusicService): Boolean =
    service == MusicService.APPLE_MUSIC ||
        service == MusicService.DEEZER ||
        service == MusicService.YOUTUBE_MUSIC

/**
 * Eligible-song count for the playlist source the given profile tab maps to,
 * using the server-maintained denormalized counts. Posts → trackCount (films
 * already excluded), Likes → likesCount, Saves → savesCount. Unknown counts
 * (null on older payloads) read as 0. Segment mapping mirrors the screens
 * (0/1 = posts, 2 = likes, 3 = saves).
 */
fun profilePlaylistEligibleCount(
    selectedSegment: Int,
    trackCount: Int?,
    likesCount: Int?,
    savesCount: Int,
): Int = when (selectedSegment) {
    2 -> likesCount ?: 0
    3 -> savesCount
    else -> trackCount ?: 0
}

/**
 * Whether to offer the "quick vs export all" chooser. Only meaningful above 75
 * eligible songs — at or below, the quick snapshot already is the whole source,
 * so there's no choice to make.
 */
fun shouldOfferProfileFullExport(
    selectedSegment: Int,
    trackCount: Int?,
    likesCount: Int?,
    savesCount: Int,
): Boolean = profilePlaylistEligibleCount(selectedSegment, trackCount, likesCount, savesCount) > 75

/**
 * Hashtag-page variant of [shouldOfferProfileFullExport]: same 75-song
 * threshold, keyed on the tag's total post count (the server excludes film
 * posts when it actually builds the playlist).
 */
fun shouldOfferHashtagFullExport(totalCount: Int): Boolean = totalCount > 75
