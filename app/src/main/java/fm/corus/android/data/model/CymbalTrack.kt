package fm.corus.android.data.model

import fm.corus.android.ui.navigation.SongDetailRoute

enum class TrackSource(val raw: String) {
    SPOTIFY("spotify"),
    SOUNDCLOUD("soundcloud"),

    /**
     * Tracks that exist on Apple Music's catalog but not on Spotify
     * (Joanna Newsom, Tool pre-2019, plenty of indie/classical). Backend
     * marks these with `trackSource: "applemusic"` and an `am:<id>` prefixed
     * trackId — same discriminator pattern as SoundCloud's `sc:` prefix.
     */
    APPLEMUSIC("applemusic"),

    /**
     * Audiomack-sourced tracks (rap / hip-hop / afrobeats catalog that isn't on
     * Spotify or Apple Music). Backend marks these with `source: "audiomack"` and
     * an `amk:<id>` prefixed trackId — same discriminator pattern as SoundCloud's
     * `sc:` and Apple's `am:`. LINK-OUT ONLY: Audiomack blocks full streams, so
     * there is no in-app playback — play/badge taps open `audiomackUrl` externally.
     */
    AUDIOMACK("audiomack"),

    /**
     * TIDAL-exclusive tracks (no Spotify/Apple match anywhere). Backend-created
     * only, marked `trackSource: "tidal"` with a `tdl:<id>` prefixed trackId —
     * same discriminator pattern as Audiomack's `amk:`. LINK-OUT ONLY (Audiomack
     * treatment): no in-app playback resolver — badge/CTA taps open `tidalURL`
     * externally.
     */
    TIDAL("tidal"),

    /**
     * Deezer-exclusive tracks (no Spotify/Apple match anywhere). Backend-created
     * only, marked `trackSource: "deezer"` with a `dzr:<id>` prefixed trackId —
     * same discriminator pattern as Audiomack's `amk:`. LINK-OUT ONLY (Audiomack
     * treatment): no in-app playback resolver — badge/CTA taps open `deezerURL`
     * externally.
     */
    DEEZER("deezer");

    companion object {
        fun fromRaw(raw: String?): TrackSource = entries.firstOrNull { it.raw == raw } ?: SPOTIFY
    }
}

data class CymbalTrack(
    val id: String,
    val name: String,
    val artistName: String,
    /**
     * Per-artist Spotify IDs from the search response. Empty for SoundCloud tracks
     * (no artist-ID concept) and for tracks decoded from older data that pre-dates
     * the ID migration. Used by ID-based taste matching to sidestep the multi-
     * artist credit collision (e.g. "Sufjan Stevens" never matched
     * "Sufjan Stevens, My Brightest Diamond" under string equality).
     */
    val artistIds: List<String> = emptyList(),
    val albumName: String,
    /**
     * Spotify album id from the search/catalog response — powers the song
     * page's album-page link (artist-pages feature). Null for Apple-sourced
     * and older tracks. Mirrors iOS `CymbalTrack.albumId`.
     */
    val albumId: String? = null,
    val albumArtURL: String? = null,
    val albumArtLargeURL: String? = null,
    val spotifyURI: String = "",
    val spotifyWebURL: String = "",
    val durationMs: Int = 0,
    val previewUrl: String? = null,
    val isrc: String? = null,
    val albumArtBackURL: String? = null,
    val releaseDate: String? = null,
    val releaseDatePrecision: String? = null,
    val source: TrackSource = TrackSource.SPOTIFY,
    val soundcloudId: String? = null,
    val soundcloudPermalinkUrl: String? = null,
    /**
     * Audiomack track id + canonical page URL. Populated only for
     * `source == AUDIOMACK` tracks (where `id` is `amk:<audiomackId>`).
     * Audiomack is link-out only — `previewUrl` / `isrc` are null, so play and
     * badge taps open [audiomackUrl] externally instead of streaming in-app.
     */
    val audiomackId: String? = null,
    val audiomackUrl: String? = null,
    /**
     * Audiomack artist + album page URLs (backend-derived). Populated only for
     * `source == AUDIOMACK` tracks. Audiomack is source-locked and link-out only
     * (no Spotify artistIds/albumId), so these external URLs are what the post
     * menu's "Go to Artist"/"Go to Album" rows and the tappable artist name open
     * instead of a Corus artist/album page. `audiomackArtistUrl` is always present
     * for Audiomack tracks; `audiomackAlbumUrl` is "" (-> null) for loose singles.
     */
    val audiomackArtistUrl: String? = null,
    val audiomackAlbumUrl: String? = null,
    /**
     * TIDAL / Deezer track id + canonical page URL. Populated only for
     * `source == TIDAL` / `source == DEEZER` exclusive tracks (where `id` is
     * `tdl:<tidalId>` / `dzr:<deezerId>`). Both sources are link-out only
     * (Audiomack treatment) — badge/CTA taps open [tidalURL] / [deezerURL]
     * externally. Unlike Audiomack there are no artist/album page URLs, so the
     * "Go to Artist"/"Go to Album" rows simply stay hidden (empty artistIds /
     * albumId).
     */
    val tidalId: String? = null,
    val tidalURL: String? = null,
    val deezerId: String? = null,
    val deezerURL: String? = null,
    /**
     * Apple Music catalog id. Populated for Apple-Music-only tracks (where
     * `source == APPLEMUSIC` and `id` is `am:<appleMusicId>`); also lazily
     * filled for Spotify-source posts by the backend's apple_music_mappings
     * resolver. null when we haven't resolved yet or there's no Apple match.
     */
    val appleMusicId: String? = null,
    /**
     * Storefront the `appleMusicId` is valid in (Apple ids are storefront-
     * specific). Lets clients tell whether the viewing user can actually open
     * the track — a Brazil-only release shouldn't badge as Apple for a US
     * listener. null/empty = unknown (legacy/unresolved) -> treat as reachable.
     */
    val appleMusicStorefront: String? = null,
    val unavailable: Boolean = false,
    val unavailableReason: String? = null,
    /**
     * Whether the recording is explicit. Additive backend field on every
     * searchSongs track; defaulted so `EMPTY` and existing call sites still
     * compile. Drives the compact "E" badge next to the title in search rows.
     */
    val explicit: Boolean = false,
) {
    val formattedDuration: String
        get() {
            val seconds = durationMs / 1000
            return "${seconds / 60}:${"%02d".format(seconds % 60)}"
        }

    /**
     * External URL to open for a link-out-only source (Audiomack). Non-null only
     * when this is an Audiomack track carrying a usable [audiomackUrl]; null for
     * streamable sources (Spotify / SoundCloud / Apple) and for Audiomack tracks
     * missing a url. Centralizes the link-out decision so the UI and tests agree.
     */
    val audiomackLinkOutUrl: String?
        get() = if (source == TrackSource.AUDIOMACK) audiomackUrl?.takeIf { it.isNotBlank() } else null

    /**
     * External URL to open for a TIDAL-exclusive track. Non-null only when this
     * is a TIDAL track carrying a usable [tidalURL]; null for streamable sources
     * and for TIDAL tracks missing a url. Mirrors [audiomackLinkOutUrl].
     */
    val tidalLinkOutUrl: String?
        get() = if (source == TrackSource.TIDAL) tidalURL?.takeIf { it.isNotBlank() } else null

    /**
     * External URL to open for a Deezer-exclusive track. Non-null only when this
     * is a Deezer track carrying a usable [deezerURL]; null for streamable
     * sources and for Deezer tracks missing a url. Mirrors [audiomackLinkOutUrl].
     */
    val deezerLinkOutUrl: String?
        get() = if (source == TrackSource.DEEZER) deezerURL?.takeIf { it.isNotBlank() } else null

    /**
     * External Audiomack artist-page URL to link out to. Non-null only for an
     * Audiomack track carrying a usable [audiomackArtistUrl]; null for streamable
     * sources and for Audiomack tracks missing the url. Mirrors [audiomackLinkOutUrl]
     * so the "Go to Artist" row / tappable artist name and tests agree on when to show.
     */
    val audiomackArtistLinkOutUrl: String?
        get() = if (source == TrackSource.AUDIOMACK) audiomackArtistUrl?.takeIf { it.isNotBlank() } else null

    /**
     * External Audiomack album-page URL to link out to. Non-null only for an
     * Audiomack track carrying a usable [audiomackAlbumUrl]; null for streamable
     * sources and for Audiomack loose singles (backend sends "" -> null), so the
     * "Go to Album" row is never a dead item. Mirrors [audiomackLinkOutUrl].
     */
    val audiomackAlbumLinkOutUrl: String?
        get() = if (source == TrackSource.AUDIOMACK) audiomackAlbumUrl?.takeIf { it.isNotBlank() } else null

    /**
     * Direct link to the song's Apple Music page. Prefers the resolved
     * `appleMusicId`; falls back to extracting it from an `am:`-prefixed
     * trackId for Apple-only tracks where the id wasn't stored separately.
     * Uses the id's home storefront in the path (Apple ids are storefront-
     * specific) so the link resolves instead of 404-ing; defaults to "us".
     */
    val appleMusicURL: String?
        get() {
            val amid = appleMusicId?.takeIf { it.isNotEmpty() }
                ?: id.takeIf { it.startsWith("am:") }?.removePrefix("am:")?.takeIf { it.isNotEmpty() }
                ?: return null
            val sf = appleMusicStorefront?.takeIf { it.isNotEmpty() }?.lowercase() ?: "us"
            return "https://music.apple.com/$sf/song/$amid"
        }

    /**
     * Whether this track's Apple Music entry is reachable from the viewer's
     * storefront [from]. Apple ids are storefront-specific: a track that resolved
     * only in Brazil's catalog can't be opened by a US listener. "us" is treated
     * as broadly available; unknown origin (null/empty) defaults to reachable so
     * we never hide a valid badge.
     */
    fun appleMusicReachable(from: String): Boolean {
        val origin = appleMusicStorefront?.takeIf { it.isNotEmpty() }?.lowercase() ?: return true
        return origin == "us" || origin == from.lowercase()
    }

    fun toSongDetailRoute() = SongDetailRoute(
        trackId = id,
        albumArtURL = albumArtURL,
        albumArtLargeURL = albumArtLargeURL,
        songName = name,
        artistName = artistName,
        spotifyURI = spotifyURI,
        spotifyWebURL = spotifyWebURL,
        previewUrl = previewUrl,
        source = source.raw,
        soundcloudId = soundcloudId,
        soundcloudPermalinkUrl = soundcloudPermalinkUrl,
        audiomackUrl = audiomackUrl,
        tidalURL = tidalURL,
        deezerURL = deezerURL,
        isrc = isrc,
        artistId = artistIds.firstOrNull(),
        artistIdCount = artistIds.size,
        albumId = albumId,
        releaseDate = releaseDate,
        releaseDatePrecision = releaseDatePrecision,
    )

    companion object {
        val EMPTY = CymbalTrack(id = "", name = "", artistName = "", albumName = "")

        fun fromMap(data: Map<String, Any?>): CymbalTrack {
            val source = TrackSource.fromRaw(data["trackSource"] as? String ?: data["source"] as? String)
            // Apple-Music-only and SoundCloud tracks don't live in Spotify's
            // catalog. Synthesizing `spotify:track:am:<id>` or
            // `spotify:track:sc:<id>` from a non-Spotify trackId just
            // produces broken "Open in Spotify" links — leave those fields
            // blank for non-Spotify sources.
            val isNonSpotify = source == TrackSource.SOUNDCLOUD || source == TrackSource.APPLEMUSIC ||
                source == TrackSource.AUDIOMACK || source == TrackSource.TIDAL || source == TrackSource.DEEZER
            val rawSpotifyURI = data["spotifyURI"] as? String ?: ""
            val rawSpotifyWebURL = data["spotifyWebURL"] as? String ?: ""
            @Suppress("UNCHECKED_CAST")
            val rawArtistIds = (data["artistIds"] as? List<*>)?.mapNotNull { it as? String }?.filter { it.isNotEmpty() }
                ?: emptyList()
            return CymbalTrack(
                id = data["trackId"] as? String ?: data["id"] as? String ?: "",
                name = data["trackName"] as? String ?: data["name"] as? String ?: "",
                artistName = data["artistName"] as? String ?: "",
                artistIds = rawArtistIds,
                albumName = data["albumName"] as? String ?: "",
                albumId = (data["albumId"] as? String)?.ifEmpty { null },
                albumArtURL = data["albumArtURL"] as? String ?: data["albumArtThumbnailURL"] as? String,
                albumArtLargeURL = data["albumArtLargeURL"] as? String,
                spotifyURI = if (isNonSpotify) "" else rawSpotifyURI,
                spotifyWebURL = if (isNonSpotify) "" else rawSpotifyWebURL,
                durationMs = (data["durationMs"] as? Number)?.toInt() ?: 0,
                previewUrl = data["previewUrl"] as? String ?: data["previewURL"] as? String,
                isrc = data["isrc"] as? String,
                albumArtBackURL = data["albumArtBackURL"] as? String,
                releaseDate = (data["trackReleaseDate"] as? String)?.ifEmpty { null },
                releaseDatePrecision = (data["trackReleaseDatePrecision"] as? String)?.ifEmpty { null },
                source = source,
                soundcloudId = (data["soundcloudId"] as? String)?.ifEmpty { null },
                soundcloudPermalinkUrl = (data["soundcloudPermalinkUrl"] as? String)?.ifEmpty { null },
                audiomackId = (data["audiomackId"] as? String)?.ifEmpty { null },
                audiomackUrl = (data["audiomackUrl"] as? String)?.ifEmpty { null },
                audiomackArtistUrl = (data["audiomackArtistUrl"] as? String)?.ifEmpty { null },
                audiomackAlbumUrl = (data["audiomackAlbumUrl"] as? String)?.ifEmpty { null },
                // TIDAL/Deezer exclusive link-out fields (Audiomack treatment).
                // Present only on `trackSource: "tidal"` / `"deezer"` post docs.
                tidalId = (data["tidalId"] as? String)?.ifEmpty { null },
                tidalURL = (data["tidalURL"] as? String)?.ifEmpty { null },
                deezerId = (data["deezerId"] as? String)?.ifEmpty { null },
                deezerURL = (data["deezerURL"] as? String)?.ifEmpty { null },
                // Tri-state, drives the service badge. Preserve "" (resolver
                // confirmed NOT on Apple Music) vs null (unknown). See
                // CymbalPost.fromMap and PostCard for why the distinction matters.
                appleMusicId = data["appleMusicId"] as? String,
                appleMusicStorefront = data["appleMusicStorefront"] as? String,
                unavailable = data["trackUnavailable"] as? Boolean ?: false,
                unavailableReason = (data["trackUnavailableReason"] as? String)?.ifEmpty { null },
                explicit = (data["explicit"] as? Boolean) ?: false,
            )
        }
    }
}
