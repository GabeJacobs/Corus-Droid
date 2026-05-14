package fm.corus.android.data.model

/**
 * Stub-track enrichment helpers, mirrored on iOS PostService.enrichedIfStub
 * and the server-side spotifyEnrichment.js. The compose flow can reach the
 * `createPost` callable with a CymbalTrack built from a DM/comment
 * attachment, Now Playing, or another non-search entry point — those
 * constructions don't carry album name, duration, ISRC, or release-date
 * metadata. Without enrichment the post lands with empty fields and the
 * NEW RELEASE pill / album display / playback estimates silently degrade.
 *
 * These helpers are pure so they can be unit-tested without mocks. The
 * actual Spotify network call lives in ComposeViewModel, which already has
 * SpotifyRepository injected.
 */

/**
 * True if this track is Spotify-sourced and missing one of the fields a
 * normal Spotify search result would carry. Returns false for SoundCloud
 * tracks (their IDs aren't in Spotify's catalog) and for empty IDs.
 */
fun CymbalTrack.hasStubMetadata(): Boolean {
    if (source != TrackSource.SPOTIFY) return false
    if (id.isEmpty()) return false
    if (albumName.isEmpty()) return true
    if (durationMs <= 0) return true
    if (releaseDate.isNullOrEmpty()) return true
    if (releaseDatePrecision.isNullOrEmpty()) return true
    if (isrc.isNullOrEmpty()) return true
    return false
}

/**
 * Per-field fill: only overwrite values that look stubbed in `this`, so a
 * partially-populated CymbalTrack (e.g. real album art and name but missing
 * release date) is repaired without clobbering anything good. Mirrors the
 * "never overwrite real data with empties" contract of the server-side
 * buildEnrichmentPatch.
 */
fun CymbalTrack.mergeMissing(spotify: CymbalTrack): CymbalTrack = copy(
    artistIds = if (artistIds.isEmpty()) spotify.artistIds else artistIds,
    albumName = if (albumName.isEmpty()) spotify.albumName else albumName,
    albumArtURL = albumArtURL ?: spotify.albumArtURL,
    albumArtLargeURL = albumArtLargeURL ?: spotify.albumArtLargeURL,
    durationMs = if (durationMs <= 0) spotify.durationMs else durationMs,
    previewUrl = previewUrl ?: spotify.previewUrl,
    isrc = if (isrc.isNullOrEmpty()) spotify.isrc else isrc,
    releaseDate = if (releaseDate.isNullOrEmpty()) spotify.releaseDate else releaseDate,
    releaseDatePrecision = if (releaseDatePrecision.isNullOrEmpty())
        spotify.releaseDatePrecision
    else
        releaseDatePrecision,
)
