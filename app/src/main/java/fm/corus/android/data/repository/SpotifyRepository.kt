package fm.corus.android.data.repository

import fm.corus.android.data.model.CymbalTrack
import fm.corus.android.data.remote.CloudFunctionsDataSource
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SpotifyRepository @Inject constructor(
    private val cloudFunctions: CloudFunctionsDataSource,
) {
    @Suppress("UNCHECKED_CAST")
    suspend fun getTrack(trackId: String): CymbalTrack? {
        val item = cloudFunctions.spotifyGetTrack(trackId)
        if (item.isEmpty()) return null
        val artists = item["artists"] as? List<Map<String, Any?>> ?: emptyList()
        val artistName = artists.joinToString(", ") { it["name"] as? String ?: "" }
        val artistIds = artists.mapNotNull { it["id"] as? String }.filter { it.isNotEmpty() }
        val album = item["album"] as? Map<String, Any?> ?: emptyMap()
        val images = album["images"] as? List<Map<String, Any?>> ?: emptyList()
        val thumbnailURL = images.minByOrNull { (it["width"] as? Number)?.toInt() ?: Int.MAX_VALUE }?.get("url") as? String
        val largeURL = images.maxByOrNull { (it["width"] as? Number)?.toInt() ?: 0 }?.get("url") as? String
        val externalUrls = item["external_urls"] as? Map<String, Any?> ?: emptyMap()
        return CymbalTrack(
            id = item["id"] as? String ?: "",
            name = item["name"] as? String ?: "",
            artistName = artistName,
            artistIds = artistIds,
            albumName = album["name"] as? String ?: "",
            albumId = (album["id"] as? String)?.ifEmpty { null },
            albumArtURL = thumbnailURL,
            albumArtLargeURL = largeURL,
            spotifyURI = item["uri"] as? String ?: "",
            spotifyWebURL = externalUrls["spotify"] as? String ?: "",
            durationMs = (item["duration_ms"] as? Number)?.toInt() ?: 0,
            previewUrl = item["preview_url"] as? String,
            releaseDate = album["release_date"] as? String,
            releaseDatePrecision = album["release_date_precision"] as? String,
        )
    }

    @Suppress("UNCHECKED_CAST")
    suspend fun search(query: String, offset: Int = 0, limit: Int = 20): List<CymbalTrack> {
        val market = java.util.Locale.getDefault().country.ifEmpty { "US" }
        val result = cloudFunctions.spotifySearch(query, offset, limit, market)
        val tracks = result["tracks"] as? Map<String, Any?> ?: return emptyList()
        val items = tracks["items"] as? List<Map<String, Any?>> ?: return emptyList()
        return items.map { item ->
            val artists = item["artists"] as? List<Map<String, Any?>> ?: emptyList()
            val artistName = artists.joinToString(", ") { it["name"] as? String ?: "" }
            val artistIds = artists.mapNotNull { it["id"] as? String }.filter { it.isNotEmpty() }
            val album = item["album"] as? Map<String, Any?> ?: emptyMap()
            val images = album["images"] as? List<Map<String, Any?>> ?: emptyList()
            val thumbnailURL = images.minByOrNull { (it["width"] as? Number)?.toInt() ?: Int.MAX_VALUE }?.get("url") as? String
            val largeURL = images.maxByOrNull { (it["width"] as? Number)?.toInt() ?: 0 }?.get("url") as? String
            val externalUrls = item["external_urls"] as? Map<String, Any?> ?: emptyMap()

            CymbalTrack(
                id = item["id"] as? String ?: "",
                name = item["name"] as? String ?: "",
                artistName = artistName,
                artistIds = artistIds,
                albumName = album["name"] as? String ?: "",
                albumId = (album["id"] as? String)?.ifEmpty { null },
                albumArtURL = thumbnailURL,
                albumArtLargeURL = largeURL,
                spotifyURI = item["uri"] as? String ?: "",
                spotifyWebURL = externalUrls["spotify"] as? String ?: "",
                durationMs = (item["duration_ms"] as? Number)?.toInt() ?: 0,
                previewUrl = item["preview_url"] as? String,
                releaseDate = album["release_date"] as? String,
                releaseDatePrecision = album["release_date_precision"] as? String,
            )
        }
    }
}
