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
    APPLEMUSIC("applemusic");

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
     * Apple Music catalog id. Populated for Apple-Music-only tracks (where
     * `source == APPLEMUSIC` and `id` is `am:<appleMusicId>`); also lazily
     * filled for Spotify-source posts by the backend's apple_music_mappings
     * resolver. null when we haven't resolved yet or there's no Apple match.
     */
    val appleMusicId: String? = null,
    val unavailable: Boolean = false,
    val unavailableReason: String? = null,
) {
    val formattedDuration: String
        get() {
            val seconds = durationMs / 1000
            return "${seconds / 60}:${"%02d".format(seconds % 60)}"
        }

    /**
     * Direct link to the song's Apple Music page. Prefers the resolved
     * `appleMusicId`; falls back to extracting it from an `am:`-prefixed
     * trackId for Apple-only tracks where the id wasn't stored separately.
     */
    val appleMusicURL: String?
        get() {
            val amid = appleMusicId?.takeIf { it.isNotEmpty() }
                ?: id.takeIf { it.startsWith("am:") }?.removePrefix("am:")?.takeIf { it.isNotEmpty() }
                ?: return null
            return "https://music.apple.com/us/song/$amid"
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
            val isNonSpotify = source == TrackSource.SOUNDCLOUD || source == TrackSource.APPLEMUSIC
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
                // Tri-state, drives the service badge. Preserve "" (resolver
                // confirmed NOT on Apple Music) vs null (unknown). See
                // CymbalPost.fromMap and PostCard for why the distinction matters.
                appleMusicId = data["appleMusicId"] as? String,
                unavailable = data["trackUnavailable"] as? Boolean ?: false,
                unavailableReason = (data["trackUnavailableReason"] as? String)?.ifEmpty { null },
            )
        }
    }
}
