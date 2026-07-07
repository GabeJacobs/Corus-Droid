package fm.corus.android.data.repository

import fm.corus.android.data.model.AlbumSearchSummary
import fm.corus.android.data.model.ArtistSummary
import fm.corus.android.data.model.CymbalTrack
import fm.corus.android.data.model.TrackSource
import fm.corus.android.data.remote.CloudFunctionsDataSource
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Parses one track from a unified-track payload (searchSongs `tracks`, and the
 * artist/album catalog callables, which return the same normalized shape).
 * Top-level so the destination-page parsers in CloudFunctionsDataSource can
 * reuse it and unit tests can hit it without a repository instance.
 */
internal fun parseUnifiedTrack(d: Map<String, Any?>): CymbalTrack? {
    val id = d["id"] as? String ?: return null
    val name = d["name"] as? String ?: return null
    val artistName = d["artistName"] as? String ?: return null
    val source = TrackSource.fromRaw(d["source"] as? String)
    val artistIds = (d["artistIds"] as? List<*>)?.mapNotNull { it as? String }?.filter { it.isNotEmpty() }
        ?: emptyList()
    return CymbalTrack(
        id = id,
        name = name,
        artistName = artistName,
        artistIds = artistIds,
        albumName = d["albumName"] as? String ?: "",
        // Additive backend extension (Spotify album id; absent/null for
        // Apple-sourced tracks) — powers the song page's album-page link.
        albumId = (d["albumId"] as? String)?.ifEmpty { null },
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
        // Apple-Music-only tracks ship `appleMusicId` directly from
        // search; the `am:` prefix on `id` is the discriminator the
        // rest of the app branches on, but carrying the raw id avoids
        // re-parsing later when constructing Apple Music link-outs.
        appleMusicId = (d["appleMusicId"] as? String)?.ifEmpty { null },
    )
}

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
    data class Page(
        val tracks: List<CymbalTrack>,
        val hasMore: Boolean,
        /** Artist rows (max 3) — only populated when the caller passed
         *  `includeArtists = true` (artist_pages_enabled on) on page one. */
        val artists: List<ArtistSummary> = emptyList(),
        /** Album rows (max 3, near-exact title matches) — only populated when
         *  the caller passed `includeAlbums = true` on page one. */
        val albums: List<AlbumSearchSummary> = emptyList(),
    )

    @Suppress("UNCHECKED_CAST")
    suspend fun search(
        query: String,
        offset: Int = 0,
        limit: Int = 20,
        includeSoundCloud: Boolean = false,
        includeArtists: Boolean = false,
        includeAlbums: Boolean = false,
    ): Page {
        if (query.isBlank()) return Page(emptyList(), false)
        val market = java.util.Locale.getDefault().country.ifEmpty { "US" }
        val result = cloudFunctions.searchSongs(
            query = query,
            offset = offset,
            limit = limit,
            market = market,
            includeSoundCloud = includeSoundCloud,
            includeArtists = includeArtists,
            includeAlbums = includeAlbums,
        )
        val raw = result["tracks"] as? List<Map<String, Any?>> ?: return Page(emptyList(), false)
        val tracks = raw.mapNotNull(::parseUnifiedTrack)
        val hasMore = result["hasMore"] as? Boolean ?: false
        val artists = (result["artists"] as? List<Map<String, Any?>>)
            ?.mapNotNull { ArtistSummary.fromMap(it) } ?: emptyList()
        val albums = (result["albums"] as? List<Map<String, Any?>>)
            ?.mapNotNull { AlbumSearchSummary.fromMap(it) } ?: emptyList()
        return Page(tracks, hasMore, artists, albums)
    }
}
