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
    val source = when {
        id.startsWith("bc:", ignoreCase = true)
            || (d["source"] as? String).equals("bandcamp", ignoreCase = true)
            || !(d["bandcampUrl"] as? String).isNullOrBlank() -> TrackSource.BANDCAMP
        id.startsWith("amk:", ignoreCase = true) -> TrackSource.AUDIOMACK
        id.startsWith("sc:", ignoreCase = true) -> TrackSource.SOUNDCLOUD
        id.startsWith("am:", ignoreCase = true) -> TrackSource.APPLEMUSIC
        else -> TrackSource.fromRaw(d["source"] as? String)
    }
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
        // Audiomack-only tracks (source == "audiomack", id == "amk:<id>"). Carry
        // the id + canonical page url; both are used for the external link-out
        // (Audiomack blocks in-app playback). Absent/null for other sources.
        audiomackId = (d["audiomackId"] as? String)?.ifEmpty { null },
        audiomackUrl = (d["audiomackUrl"] as? String)?.ifEmpty { null },
        // Audiomack artist/album page URLs — link-out targets for "Go to Artist"/
        // "Go to Album" (Audiomack has no Spotify artistIds/albumId). Album url is
        // "" (-> null) for loose singles.
        audiomackArtistUrl = (d["audiomackArtistUrl"] as? String)?.ifEmpty { null },
        audiomackAlbumUrl = (d["audiomackAlbumUrl"] as? String)?.ifEmpty { null },
        bandcampId = (d["bandcampId"] as? String)?.ifEmpty { null }
            ?: id.takeIf { it.startsWith("bc:", ignoreCase = true) }?.substringAfter(':'),
        bandcampUrl = (d["bandcampUrl"] as? String)?.ifEmpty { null },
        bandcampArtistUrl = (d["bandcampArtistUrl"] as? String)?.ifEmpty { null },
        bandcampAlbumUrl = (d["bandcampAlbumUrl"] as? String)?.ifEmpty { null },
        // Apple-Music-only tracks ship `appleMusicId` directly from
        // search; the `am:` prefix on `id` is the discriminator the
        // rest of the app branches on, but carrying the raw id avoids
        // re-parsing later when constructing Apple Music link-outs.
        appleMusicId = (d["appleMusicId"] as? String)?.ifEmpty { null },
        // Additive backend field — drives the compact "E" badge in search rows.
        explicit = (d["explicit"] as? Boolean) ?: false,
        isPlayable = d["isPlayable"] as? Boolean,
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
        /** Album rows (near-exact title matches, max 3; with
         *  `albumsMatchArtist` also artist matches, max 5) — only populated
         *  when the caller passed `includeAlbums = true` on page one. */
        val albums: List<AlbumSearchSummary> = emptyList(),
        /** True when the query is a song-title search (a track title exactly
         *  matches the query). The UI renders same-titled album rows below the
         *  songs in that case so a direct song hit isn't buried by same-named
         *  albums; false keeps albums above the songs (album-intent queries). */
        val songsFirst: Boolean = false,
    )

    @Suppress("UNCHECKED_CAST")
    suspend fun search(
        query: String,
        offset: Int = 0,
        limit: Int = 20,
        includeSoundCloud: Boolean = false,
        includeArtists: Boolean = false,
        includeAlbums: Boolean = false,
        /** Album pickers only: keep albums whose ARTIST matches the query, so
         *  "arcade fire" lists their records instead of nothing. Off elsewhere
         *  — the search tab deliberately matches album TITLES only. */
        albumsMatchArtist: Boolean = false,
        collapse: String = "recording",
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
            albumsMatchArtist = albumsMatchArtist,
            collapse = collapse,
        )
        val raw = result["tracks"] as? List<Map<String, Any?>> ?: return Page(emptyList(), false)
        val tracks = raw.mapNotNull(::parseUnifiedTrack)
        val hasMore = result["hasMore"] as? Boolean ?: false
        val artists = (result["artists"] as? List<Map<String, Any?>>)
            ?.mapNotNull { ArtistSummary.fromMap(it) } ?: emptyList()
        val albums = (result["albums"] as? List<Map<String, Any?>>)
            ?.mapNotNull { AlbumSearchSummary.fromMap(it) } ?: emptyList()
        val songsFirst = result["songsFirst"] as? Boolean ?: false
        return Page(tracks, hasMore, artists, albums, songsFirst)
    }
}
