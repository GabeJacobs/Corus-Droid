package fm.corus.android.domain

import fm.corus.android.data.model.MusicService

/**
 * Whether tapping "Generate Playlist" should show the "Spotify Feature" alert
 * first (vs. generating directly). Single source of truth for the feed and
 * profile screens, which used to duplicate this logic.
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
    when (service) {
        MusicService.TIDAL -> false
        MusicService.APPLE_MUSIC, MusicService.DEEZER -> true
        MusicService.SPOTIFY -> hasSoundCloud
    }
