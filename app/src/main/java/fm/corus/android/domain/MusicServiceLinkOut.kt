package fm.corus.android.domain

import androidx.annotation.DrawableRes
import fm.corus.android.R
import fm.corus.android.data.model.CymbalTrack
import fm.corus.android.data.model.MusicService
import fm.corus.android.data.remote.CloudFunctionsDataSource
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Music-service link-out helper. Mirrors the iOS link-out behavior: a Spotify
 * post's "open in <service>" button reflects the viewer's *preferred* service and
 * resolves that service's catalog URL on demand (Spotify is the post's native
 * source, so it needs no resolution; Apple Music / TIDAL / Deezer require a
 * `<service>Lookup` Cloud Function round-trip).
 *
 * In-memory caches mirror iOS's per-session `appleMusicCache` / `tidalCache` /
 * `deezerCache` so a given track resolves at most once per process.
 *
 * [isResolving] drives a single GLOBAL loading overlay (MainTabScreen), mirroring
 * iOS's `NowPlayingManager.isResolvingLinkOut`. It flips true only around an actual
 * network fetch (cache hits and Spotify stay instant), so the overlay never flashes
 * on the fast path.
 */
object MusicServiceLinkOut {
    private val appleCache = mutableMapOf<String, String>()
    private val tidalCache = mutableMapOf<String, String>()
    private val deezerCache = mutableMapOf<String, String>()
    private val spotifyCache = mutableMapOf<String, String>() // Apple-sourced trackId -> Spotify web URL

    private val _isResolving = MutableStateFlow(false)
    val isResolving: StateFlow<Boolean> = _isResolving.asStateFlow()

    // Apple-sourced track ids a tap CONFIRMED aren't on Spotify. Observable so the
    // mini-player logo recomposes from the optimistic Spotify glyph to Apple once
    // confirmed. In-memory / per-process — zero reads on browse.
    private val _absentFromSpotify = MutableStateFlow<Set<String>>(emptySet())
    val absentFromSpotify: StateFlow<Set<String>> = _absentFromSpotify.asStateFlow()

    /** True once an open-tap CONFIRMED this Apple-sourced track isn't on Spotify. */
    fun knownNotOnSpotify(trackId: String?): Boolean =
        trackId != null && _absentFromSpotify.value.contains(trackId)

    /** Spotify search URL for a track — the fallback when the exact-track resolve
     *  ERRORS (transient / not-yet-deployed). The tap promised Spotify, so keep the
     *  user in Spotify rather than opening Apple; only a CONFIRMED absence opens Apple. */
    fun spotifySearchUrl(name: String, artist: String): String {
        val q = java.net.URLEncoder.encode("$name $artist".trim(), "UTF-8")
        return "https://open.spotify.com/search/$q"
    }

    /** Drawable for the preferred-service glyph shown on a Spotify-source post. */
    @DrawableRes
    fun logoRes(service: MusicService): Int = when (service) {
        MusicService.SPOTIFY -> R.drawable.spotify_logo
        MusicService.APPLE_MUSIC -> R.drawable.apple_music_logo
        MusicService.TIDAL -> R.drawable.tidal_logo
        MusicService.DEEZER -> R.drawable.deezer_logo
    }

    /**
     * Resolve the page URL to open for a Spotify-source track given the viewer's
     * preferred service. Returns null for [MusicService.SPOTIFY] (the caller opens
     * the post's own Spotify URI/web URL synchronously) and on no-match / error.
     * Apple Music short-circuits to the post's denormalized `appleMusicURL` when
     * present, avoiding a round-trip.
     */
    suspend fun resolveLinkOutUrl(
        track: CymbalTrack,
        service: MusicService,
        cloud: CloudFunctionsDataSource,
    ): String? {
        if (service == MusicService.SPOTIFY) return null
        if (service == MusicService.APPLE_MUSIC) {
            val amid = track.appleMusicId
            // Empty id = backend CONFIRMED no Apple Music match: the badge shows
            // Spotify, so open Spotify rather than leaving the tap dead.
            if (amid == "") return spotifyFallbackUrl(track)
            // Has an id, but Apple ids are storefront-specific. If the viewer
            // can't open it (e.g. a Brazil-only release for a US listener) the
            // badge shows Spotify, so open Spotify rather than a dead Apple Music
            // page. Keeps the tap in sync with the storefront-aware badge.
            if (!amid.isNullOrEmpty() && !track.appleMusicReachable(from = deviceStorefront())) {
                return spotifyFallbackUrl(track)
            }
            // Reachable id -> storefront-correct appleMusicURL. A null id is just
            // "unknown" -> fall through to the live lookup, which may find one.
            track.appleMusicURL?.let { return it }
        }
        return resolveLinkOutUrlRaw(track.id, track.name, track.artistName, track.isrc, service, cloud)
    }

    /**
     * Universal Spotify link for a Spotify-source track, used as a graceful
     * fallback when the viewer's preferred service has no catalog match. Prefers
     * the web URL (always opens) over the `spotify:` URI (needs the app).
     */
    private fun spotifyFallbackUrl(track: CymbalTrack): String? =
        track.spotifyWebURL.takeIf { it.isNotBlank() }
            ?: track.spotifyURI.takeIf { it.isNotBlank() }

    /**
     * Best-effort viewer storefront from the device locale. Android has no
     * MusicKit, so the device region is our proxy for the user's Apple Music
     * storefront. Apple ids are storefront-specific; defaults to "us".
     */
    fun deviceStorefront(): String =
        java.util.Locale.getDefault().country.takeIf { it.isNotEmpty() }?.lowercase() ?: "us"

    /**
     * Same as [resolveLinkOutUrl] but from raw track fields rather than a
     * [CymbalTrack] — used by the mini-player, whose now-playing state isn't a
     * full track model. No Apple `appleMusicURL` short-circuit (state doesn't
     * carry it), so Apple resolves via the callable like TIDAL/Deezer.
     */
    suspend fun resolveLinkOutUrlRaw(
        trackId: String,
        name: String,
        artist: String,
        isrc: String?,
        service: MusicService,
        cloud: CloudFunctionsDataSource,
    ): String? = when (service) {
        MusicService.SPOTIFY -> null
        MusicService.APPLE_MUSIC -> cached(appleCache, trackId) { cloud.appleMusicLinkOutUrl(name, artist, isrc, trackId) }
        MusicService.TIDAL -> cached(tidalCache, trackId) { cloud.tidalLinkOutUrl(name, artist, isrc, trackId) }
        MusicService.DEEZER -> cached(deezerCache, trackId) { cloud.deezerLinkOutUrl(name, artist, isrc, trackId) }
    }

    /**
     * Resolve an Apple-sourced track to its Spotify page and open URL. Under
     * Apple-primary search a preview is Apple-sourced but usually also on Spotify,
     * so a Spotify viewer's open-tap resolves here. Cache-first, then the
     * ISRC-cache-first `spotifyTrackLookup` callable (the common tap costs zero
     * Spotify calls). Returns the URL on a hit; null on a CONFIRMED miss (the
     * track is remembered so its logo flips to Apple) or a transient error (not
     * remembered — a later tap retries). Mirror of [resolveLinkOutUrlRaw]'s Apple
     * branch in the opposite direction.
     */
    suspend fun resolveSpotifyFromApple(
        trackId: String,
        name: String,
        artist: String,
        isrc: String?,
        cloud: CloudFunctionsDataSource,
    ): String? {
        spotifyCache[trackId]?.let { return it } // cache hit → instant, no overlay
        _isResolving.value = true
        try {
            val res = runCatching { cloud.spotifyTrackLookup(name, artist, isrc, trackId) }.getOrNull()
                ?: return null // transient error — don't remember; let a later tap retry
            if (res.found && !res.webUrl.isNullOrBlank()) {
                spotifyCache[trackId] = res.webUrl
                return res.webUrl
            }
            _absentFromSpotify.value = _absentFromSpotify.value + trackId // confirmed absent
            return null
        } finally {
            _isResolving.value = false
        }
    }

    private suspend fun cached(
        cache: MutableMap<String, String>,
        key: String,
        fetch: suspend () -> String?,
    ): String? {
        cache[key]?.let { return it } // cache hit → instant, no loading overlay
        _isResolving.value = true
        try {
            val url = runCatching { fetch() }.getOrNull()
            if (!url.isNullOrBlank()) cache[key] = url
            return url
        } finally {
            _isResolving.value = false
        }
    }
}
