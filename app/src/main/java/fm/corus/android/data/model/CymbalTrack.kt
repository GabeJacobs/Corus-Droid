package fm.corus.android.data.model

import fm.corus.android.ui.navigation.SongDetailRoute

enum class TrackSource(val raw: String) {
    SPOTIFY("spotify"),
    SOUNDCLOUD("soundcloud");

    companion object {
        fun fromRaw(raw: String?): TrackSource = entries.firstOrNull { it.raw == raw } ?: SPOTIFY
    }
}

data class CymbalTrack(
    val id: String,
    val name: String,
    val artistName: String,
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
    val unavailable: Boolean = false,
    val unavailableReason: String? = null,
) {
    val formattedDuration: String
        get() {
            val seconds = durationMs / 1000
            return "${seconds / 60}:${"%02d".format(seconds % 60)}"
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
            val isSoundCloud = source == TrackSource.SOUNDCLOUD
            val rawSpotifyURI = data["spotifyURI"] as? String ?: ""
            val rawSpotifyWebURL = data["spotifyWebURL"] as? String ?: ""
            // Don't synthesize Spotify URLs for SoundCloud tracks — those IDs
            // aren't in Spotify's catalog and would 404 on tap.
            return CymbalTrack(
                id = data["trackId"] as? String ?: data["id"] as? String ?: "",
                name = data["trackName"] as? String ?: data["name"] as? String ?: "",
                artistName = data["artistName"] as? String ?: "",
                albumName = data["albumName"] as? String ?: "",
                albumArtURL = data["albumArtURL"] as? String ?: data["albumArtThumbnailURL"] as? String,
                albumArtLargeURL = data["albumArtLargeURL"] as? String,
                spotifyURI = if (isSoundCloud) "" else rawSpotifyURI,
                spotifyWebURL = if (isSoundCloud) "" else rawSpotifyWebURL,
                durationMs = (data["durationMs"] as? Number)?.toInt() ?: 0,
                previewUrl = data["previewUrl"] as? String ?: data["previewURL"] as? String,
                isrc = data["isrc"] as? String,
                albumArtBackURL = data["albumArtBackURL"] as? String,
                releaseDate = (data["trackReleaseDate"] as? String)?.ifEmpty { null },
                releaseDatePrecision = (data["trackReleaseDatePrecision"] as? String)?.ifEmpty { null },
                source = source,
                soundcloudId = (data["soundcloudId"] as? String)?.ifEmpty { null },
                soundcloudPermalinkUrl = (data["soundcloudPermalinkUrl"] as? String)?.ifEmpty { null },
                unavailable = data["trackUnavailable"] as? Boolean ?: false,
                unavailableReason = (data["trackUnavailableReason"] as? String)?.ifEmpty { null },
            )
        }
    }
}
