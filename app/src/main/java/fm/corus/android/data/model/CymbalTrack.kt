package fm.corus.android.data.model

import fm.corus.android.ui.navigation.SongDetailRoute

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
    )

    companion object {
        val EMPTY = CymbalTrack(id = "", name = "", artistName = "", albumName = "")

        fun fromMap(data: Map<String, Any?>): CymbalTrack = CymbalTrack(
            id = data["trackId"] as? String ?: data["id"] as? String ?: "",
            name = data["trackName"] as? String ?: data["name"] as? String ?: "",
            artistName = data["artistName"] as? String ?: "",
            albumName = data["albumName"] as? String ?: "",
            albumArtURL = data["albumArtURL"] as? String ?: data["albumArtThumbnailURL"] as? String,
            albumArtLargeURL = data["albumArtLargeURL"] as? String,
            spotifyURI = data["spotifyURI"] as? String ?: "",
            spotifyWebURL = data["spotifyWebURL"] as? String ?: "",
            durationMs = (data["durationMs"] as? Number)?.toInt() ?: 0,
            previewUrl = data["previewUrl"] as? String ?: data["previewURL"] as? String,
            isrc = data["isrc"] as? String,
            albumArtBackURL = data["albumArtBackURL"] as? String,
            releaseDate = (data["trackReleaseDate"] as? String)?.ifEmpty { null },
            releaseDatePrecision = (data["trackReleaseDatePrecision"] as? String)?.ifEmpty { null },
        )
    }
}
