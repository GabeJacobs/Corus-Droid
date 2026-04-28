package fm.corus.android.data.repository

import fm.corus.android.data.model.CymbalTrack
import fm.corus.android.data.model.TrackSource
import fm.corus.android.data.remote.CloudFunctionsDataSource
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Unified song search across Spotify + SoundCloud.
 *
 * Calls the `searchSongs` Cloud Function which fans out to both providers,
 * merges, and ranks server-side — so iOS, Android, and Web all get identical
 * ordering. Replaces direct calls to `SpotifyRepository.search` for new code.
 */
@Singleton
class MusicSearchRepository @Inject constructor(
    private val cloudFunctions: CloudFunctionsDataSource,
) {
    data class Page(val tracks: List<CymbalTrack>, val hasMore: Boolean)

    @Suppress("UNCHECKED_CAST")
    suspend fun search(
        query: String,
        offset: Int = 0,
        limit: Int = 20,
        includeSoundCloud: Boolean = false,
    ): Page {
        if (query.isBlank()) return Page(emptyList(), false)
        val market = java.util.Locale.getDefault().country.ifEmpty { "US" }
        val result = cloudFunctions.searchSongs(query, offset, limit, market, includeSoundCloud)
        val raw = result["tracks"] as? List<Map<String, Any?>> ?: return Page(emptyList(), false)
        val tracks = raw.mapNotNull(::parseUnifiedTrack)
        val hasMore = result["hasMore"] as? Boolean ?: false
        return Page(tracks, hasMore)
    }

    private fun parseUnifiedTrack(d: Map<String, Any?>): CymbalTrack? {
        val id = d["id"] as? String ?: return null
        val name = d["name"] as? String ?: return null
        val artistName = d["artistName"] as? String ?: return null
        val source = TrackSource.fromRaw(d["source"] as? String)
        return CymbalTrack(
            id = id,
            name = name,
            artistName = artistName,
            albumName = d["albumName"] as? String ?: "",
            albumArtURL = (d["albumArtURL"] as? String)?.ifEmpty { null },
            albumArtLargeURL = (d["albumArtLargeURL"] as? String)?.ifEmpty { null },
            spotifyURI = d["spotifyURI"] as? String ?: "",
            spotifyWebURL = d["spotifyWebURL"] as? String ?: "",
            durationMs = (d["durationMs"] as? Number)?.toInt() ?: 0,
            previewUrl = (d["previewUrl"] as? String)?.ifEmpty { null },
            isrc = (d["isrc"] as? String)?.ifEmpty { null },
            releaseDate = (d["releaseDate"] as? String)?.ifEmpty { null },
            releaseDatePrecision = (d["releaseDatePrecision"] as? String)?.ifEmpty { null },
            source = source,
            soundcloudId = (d["soundcloudId"] as? String)?.ifEmpty { null },
            soundcloudPermalinkUrl = (d["soundcloudPermalinkUrl"] as? String)?.ifEmpty { null },
        )
    }
}
