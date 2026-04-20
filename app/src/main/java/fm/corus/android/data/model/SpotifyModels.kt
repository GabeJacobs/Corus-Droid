package fm.corus.android.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class SpotifySearchResponse(
    val tracks: SpotifyTrackList? = null,
)

@Serializable
data class SpotifyTrackList(
    val items: List<SpotifyTrack> = emptyList(),
    val total: Int = 0,
    val offset: Int = 0,
    val limit: Int = 20,
)

@Serializable
data class SpotifyTrack(
    val id: String,
    val name: String,
    val artists: List<SpotifyArtist> = emptyList(),
    val album: SpotifyAlbum = SpotifyAlbum(),
    val uri: String = "",
    @SerialName("external_urls")
    val externalUrls: SpotifyExternalUrls = SpotifyExternalUrls(),
    @SerialName("duration_ms")
    val durationMs: Int = 0,
    @SerialName("preview_url")
    val previewUrl: String? = null,
) {
    val artistName: String get() = artists.joinToString(", ") { it.name }
}

@Serializable
data class SpotifyArtist(
    val id: String = "",
    val name: String = "",
)

@Serializable
data class SpotifyAlbum(
    val id: String = "",
    val name: String = "",
    val images: List<SpotifyImage> = emptyList(),
    @SerialName("release_date")
    val releaseDate: String? = null,
    @SerialName("release_date_precision")
    val releaseDatePrecision: String? = null,
) {
    val thumbnailURL: String?
        get() = images.minByOrNull { it.width ?: Int.MAX_VALUE }?.url

    val largestImageURL: String?
        get() = images.maxByOrNull { it.width ?: 0 }?.url
}

@Serializable
data class SpotifyImage(
    val url: String = "",
    val width: Int? = null,
    val height: Int? = null,
)

@Serializable
data class SpotifyExternalUrls(
    val spotify: String = "",
)
