package fm.corus.android.domain

import fm.corus.android.data.model.CymbalTrack

/** Option A: pre-release parent album → stay on the song page until RC enables
 *  album pages. [goToAlbumAsSong] comes from resolveTrackDestinations overlay. */
fun shouldRouteGoToAlbumToSong(
    track: CymbalTrack,
    prereleaseAlbumPagesEnabled: Boolean,
    goToAlbumAsSong: Boolean = false,
): Boolean =
    goToAlbumAsSong || (track.parentAlbumUnreleased && !prereleaseAlbumPagesEnabled)

fun trackIsCatalogPlayable(track: CymbalTrack): Boolean =
    track.isPlayable ?: ((track.durationMs > 0) || !track.previewUrl.isNullOrBlank())
