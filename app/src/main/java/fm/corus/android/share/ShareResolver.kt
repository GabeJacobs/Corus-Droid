package fm.corus.android.share

import com.google.firebase.functions.FirebaseFunctions
import fm.corus.android.data.model.CymbalTrack
import fm.corus.android.data.model.TrackSource
import fm.corus.android.data.remote.CloudFunctionsDataSource
import fm.corus.android.data.repository.MusicSearchRepository
import fm.corus.android.data.repository.SpotifyRepository
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.tasks.await
import org.json.JSONObject
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/** One pickable row on the in-sheet album page. Catalog albums (Apple +
 * Spotify via getAlbumCatalog) arrive with every row already postable
 * ([preResolved]); Deezer albums resolve lazily on tap via [deezerTrackId]. */
data class ShareAlbumTrack(
    val id: String,
    val name: String,
    val artistName: String,
    val durationMs: Int,
    val preResolved: CymbalTrack? = null,
    val deezerTrackId: String? = null,
    /** TIDAL rows carry their ISRC directly (the album callable returns it),
     * so selection cross-resolves with zero extra fetches. */
    val lazyIsrc: String? = null,
) {
    val formattedDuration: String
        get() {
            val seconds = durationMs / 1000
            return "%d:%02d".format(seconds / 60, seconds % 60)
        }
}

/** A shared album for the in-sheet picker (header mirrors AlbumPageScreen). */
data class ShareAlbum(
    val id: String,
    val title: String,
    val artistName: String,
    val year: String?,
    val coverUrl: String?,
    /** Full release date when the source provides one (Deezer/TIDAL) — gates
     * pre-release albums at open time. Null for catalog albums. */
    val releaseDate: String? = null,
    val tracks: List<ShareAlbumTrack>,
)

/**
 * Resolves shared music links into postable [CymbalTrack]s / pickable
 * [ShareAlbum]s.
 *
 * KEEP IN SYNC with the iOS extension's ShareTrackResolver — same strategy
 * per service, same validation gates, same degrades:
 *  - Spotify song → `spotifyGetTrack` (via [SpotifyRepository.getTrack]).
 *  - Apple song → instant provisional card from ONE public iTunes lookup,
 *    then the full canonical resolution (dev token → catalog ISRC → Spotify
 *    cross-ref, apple-only degrade) in the background.
 *  - SoundCloud → slug → the same `searchSongs` callable as in-app search
 *    (inheriting its content filtering), accepting ONLY an exact
 *    normalized-permalink match.
 *  - Deezer → public API → ISRC cross-ref (no match = unavailable; Deezer
 *    isn't a Corus post source).
 *  - Apple/Spotify albums → `getAlbumCatalog` (rows directly postable);
 *    Deezer albums → public API with lazy per-row resolution.
 *
 * Every network path funnels through the same backend surfaces the app
 * itself uses, so anything Corus filters out of in-app search (podcasts,
 * audiobooks, episodes) cannot be posted from here either.
 */
@Singleton
class ShareResolver @Inject constructor(
    private val cloudFunctions: CloudFunctionsDataSource,
    private val spotifyRepository: SpotifyRepository,
    private val musicSearchRepository: MusicSearchRepository,
    private val functions: FirebaseFunctions,
    private val httpClient: HttpClient,
) {

    // ── Short links ────────────────────────────────────────────────────────

    /** Follows a short link (spotify.link, on.soundcloud.com, deezer.page.link)
     * to its destination URL so the normal parser can read it. */
    suspend fun expandShortLink(url: String): String? = runCatching {
        val response = httpClient.get(url)
        response.call.request.url.toString()
    }.getOrNull()

    // ── Songs ──────────────────────────────────────────────────────────────

    /** Full canonical resolution for a song link; null = unavailable. */
    suspend fun resolveSong(link: SharedMusicLink): CymbalTrack? = when (link) {
        is SharedMusicLink.SpotifyTrack ->
            runCatching { spotifyRepository.getTrack(link.trackId) }.getOrNull()
                ?.takeIf { it.id.isNotEmpty() }

        is SharedMusicLink.AppleMusicSong -> resolveAppleSong(link.id, link.storefront)

        is SharedMusicLink.SoundCloudTrack -> resolveSoundCloud(link.url)

        is SharedMusicLink.AudiomackTrack -> resolveAudiomack(link.url)

        is SharedMusicLink.DeezerTrack -> resolveDeezerTrack(link.id)

        is SharedMusicLink.TidalTrack -> resolveTidalTrack(link.id)

        else -> null // albums route through the picker loaders
    }

    // ── TIDAL ──────────────────────────────────────────────────────────────

    /** TIDAL track → Spotify via ISRC (the `tidalGetTrack` callable returns
     * normalized metadata incl. ISRC). TIDAL isn't a Corus post source, so
     * no validated match = null — same rule as Deezer. */
    suspend fun resolveTidalTrack(id: String): CymbalTrack? {
        val metadata = tidalTrackMetadata(id) ?: return null
        return resolveIsrcMatch(
            isrc = metadata.isrc ?: "",
            fallbackName = metadata.name,
            fallbackArtist = metadata.artistName,
        )
    }

    /** Display metadata for a TIDAL track from ONE callable — paints the
     * composer card after a single round-trip (the backend includes cover
     * art). Carries the ISRC so the background canonical resolution needs no
     * second TIDAL call. NEVER POSTABLE as-is (TIDAL isn't a post source). */
    suspend fun tidalTrackMetadata(id: String): CymbalTrack? {
        val track = runCatching {
            @Suppress("UNCHECKED_CAST")
            val result = functions.getHttpsCallable("tidalGetTrack")
                .call(mapOf("tidalId" to id)).await()
            (result.getData() as? Map<String, Any?>)?.get("track") as? Map<String, Any?>
        }.getOrNull() ?: return null
        val name = (track["name"] as? String)?.takeIf { it.isNotEmpty() } ?: return null
        val cover = (track["coverUrl"] as? String)?.ifEmpty { null }
        val release = (track["releaseDate"] as? String)?.takeIf { it.length >= 10 }?.take(10)
        return CymbalTrack(
            id = "tidal:$id",
            name = name,
            artistName = track["artistName"] as? String ?: "",
            albumName = track["albumName"] as? String ?: "",
            albumArtURL = cover,
            albumArtLargeURL = cover,
            durationMs = (track["durationMs"] as? Number)?.toInt() ?: 0,
            isrc = (track["isrc"] as? String)?.ifEmpty { null },
            releaseDate = release,
            releaseDatePrecision = release?.let { "day" },
        )
    }

    /** TIDAL album via the `tidalGetAlbum` callable. Rows arrive with their
     * ISRCs, so a tap cross-resolves client-side with no extra fetch. */
    suspend fun fetchTidalAlbum(id: String): ShareAlbum? {
        val data = runCatching {
            @Suppress("UNCHECKED_CAST")
            val result = functions.getHttpsCallable("tidalGetAlbum")
                .call(mapOf("albumId" to id)).await()
            result.getData() as? Map<String, Any?>
        }.getOrNull() ?: return null
        @Suppress("UNCHECKED_CAST")
        val album = data["album"] as? Map<String, Any?> ?: return null
        val title = (album["title"] as? String)?.takeIf { it.isNotEmpty() } ?: return null
        val artistName = album["artistName"] as? String ?: ""
        @Suppress("UNCHECKED_CAST")
        val tracks = (data["tracks"] as? List<Map<String, Any?>>)?.mapNotNull { item ->
            val tidalId = item["tidalId"] as? String ?: return@mapNotNull null
            val name = (item["name"] as? String)?.takeIf { it.isNotEmpty() } ?: return@mapNotNull null
            ShareAlbumTrack(
                id = tidalId,
                name = name,
                artistName = item["artistName"] as? String ?: artistName,
                durationMs = (item["durationMs"] as? Number)?.toInt() ?: 0,
                lazyIsrc = (item["isrc"] as? String) ?: "",
            )
        } ?: emptyList()
        if (tracks.isEmpty()) return null
        val releaseDate = album["releaseDate"] as? String
        return ShareAlbum(
            id = id,
            title = title,
            artistName = artistName,
            year = releaseDate?.takeIf { it.length >= 4 }?.take(4),
            coverUrl = (album["coverUrl"] as? String)?.ifEmpty { null },
            releaseDate = releaseDate,
            tracks = tracks,
        )
    }

    /** Public wrapper over the validated ISRC-first cross-reference, for the
     * album picker's lazy TIDAL rows. */
    suspend fun resolveMatch(isrc: String, name: String, artist: String): CymbalTrack? =
        resolveIsrcMatch(isrc = isrc, fallbackName = name, fallbackArtist = artist)

    // ── Apple Music ────────────────────────────────────────────────────────

    /**
     * Instant provisional display data for a shared Apple song: ONE public
     * tokenless iTunes lookup paints the composer immediately; the full
     * resolution runs in the background and lands by post time. No ISRC here.
     */
    suspend fun itunesLookup(appleMusicId: String, storefront: String?): CymbalTrack? {
        val country = storefront?.takeIf { it.isNotEmpty() } ?: "us"
        val json = getPublicJson("https://itunes.apple.com/lookup?id=$appleMusicId&country=$country")
            ?: return null
        val results = json.optJSONArray("results") ?: return null
        for (i in 0 until results.length()) {
            val item = results.optJSONObject(i) ?: continue
            if (item.optString("kind") != "song") continue
            val name = item.optString("trackName").ifEmpty { return null }
            val art = item.optString("artworkUrl100")
                .takeIf { it.isNotEmpty() }
                ?.replace("100x100", "600x600")
            val release = item.optString("releaseDate").takeIf { it.length >= 10 }?.take(10)
            return CymbalTrack(
                id = "am:$appleMusicId",
                name = name,
                artistName = item.optString("artistName"),
                albumName = item.optString("collectionName"),
                albumArtURL = art,
                albumArtLargeURL = art,
                durationMs = item.optInt("trackTimeMillis", 0),
                releaseDate = release,
                releaseDatePrecision = release?.let { "day" },
                source = TrackSource.APPLEMUSIC,
                appleMusicId = appleMusicId,
                appleMusicStorefront = storefront,
            )
        }
        return null
    }

    /** Full apple resolution: catalog fetch (for ISRC + canonical metadata) →
     * Spotify cross-ref → apple-only degrade. Null only when the catalog
     * fetch itself fails. */
    private suspend fun resolveAppleSong(id: String, storefront: String?): CymbalTrack? {
        val song = fetchAppleCatalogSong(id, storefront) ?: return null
        resolveIsrcMatch(song.isrc ?: "", song.name, song.artistName)?.let { return it }
        return CymbalTrack(
            id = "am:${song.id}",
            name = song.name,
            artistName = song.artistName,
            albumName = song.albumName,
            albumArtURL = song.artworkURL,
            albumArtLargeURL = song.artworkURL,
            durationMs = song.durationMs,
            isrc = song.isrc,
            releaseDate = song.releaseDate,
            releaseDatePrecision = song.releaseDate?.let { releasePrecision(it) },
            source = TrackSource.APPLEMUSIC,
            appleMusicId = song.id,
            appleMusicStorefront = storefront,
        )
    }

    private data class AppleCatalogSong(
        val id: String,
        val name: String,
        val artistName: String,
        val albumName: String,
        val durationMs: Int,
        val isrc: String?,
        val releaseDate: String?,
        val artworkURL: String?,
    )

    private suspend fun fetchAppleCatalogSong(id: String, storefront: String?): AppleCatalogSong? {
        val token = appleDeveloperToken() ?: return null
        val primary = storefront?.takeIf { it.isNotEmpty() } ?: "us"
        fetchAppleCatalogSong(id, primary, token)?.let { return it }
        // Cross-storefront ids 404 in the wrong storefront; retry the broad one.
        if (primary != "us") return fetchAppleCatalogSong(id, "us", token)
        return null
    }

    private suspend fun fetchAppleCatalogSong(id: String, storefront: String, token: String): AppleCatalogSong? {
        val json = getAppleJson("https://api.music.apple.com/v1/catalog/$storefront/songs/$id", token)
            ?: return null
        val first = json.optJSONArray("data")?.optJSONObject(0) ?: return null
        val attributes = first.optJSONObject("attributes") ?: return null
        val name = attributes.optString("name").ifEmpty { return null }
        val artistName = attributes.optString("artistName").ifEmpty { return null }
        val artworkURL = attributes.optJSONObject("artwork")?.optString("url")
            ?.takeIf { it.isNotEmpty() }
            ?.replace("{w}", "1000")
            ?.replace("{h}", "1000")
        return AppleCatalogSong(
            id = first.optString("id").ifEmpty { id },
            name = name,
            artistName = artistName,
            albumName = attributes.optString("albumName"),
            durationMs = attributes.optInt("durationInMillis", 0),
            isrc = attributes.optString("isrc").takeIf { it.isNotEmpty() },
            releaseDate = attributes.optString("releaseDate").takeIf { it.isNotEmpty() },
            artworkURL = artworkURL,
        )
    }

    /** Backend-minted MusicKit developer token, cached in-process for 24h so
     * repeat Apple resolutions skip the callable round-trip. */
    @Volatile private var cachedDevToken: Pair<String, Long>? = null

    private suspend fun appleDeveloperToken(): String? {
        cachedDevToken?.let { (token, expiry) ->
            if (System.currentTimeMillis() < expiry - 60_000) return token
        }
        return runCatching {
            val result = functions.getHttpsCallable("getAppleMusicDeveloperToken")
                .call(emptyMap<String, Any>()).await()
            @Suppress("UNCHECKED_CAST")
            val data = result.getData() as? Map<String, Any?>
            val token = (data?.get("token") as? String)?.takeIf { it.isNotEmpty() }
            if (token != null) {
                cachedDevToken = token to (System.currentTimeMillis() + 24 * 3600_000L)
            }
            token
        }.getOrNull()
    }

    /** Apple/Deezer give YYYY-MM-DD (occasionally partial); map to the
     * precision vocabulary the post model uses. */
    private fun releasePrecision(date: String): String? = when (date.length) {
        10 -> "day"
        7 -> "month"
        4 -> "year"
        else -> null
    }

    // ── SoundCloud ─────────────────────────────────────────────────────────

    /**
     * SoundCloud share URLs are slugs, not ids, and resolving them needs
     * credentials only the backend holds. Instead: slug → search query →
     * the same `searchSongs` callable in-app search uses (inheriting all of
     * its content filtering) → accept ONLY the result whose permalink
     * matches the shared URL exactly.
     */
    private suspend fun resolveSoundCloud(url: String): CymbalTrack? {
        val segments = url.substringAfter("soundcloud.com/").split('/')
        if (segments.size != 2) return null
        val query = segments.joinToString(" ")
            .replace('-', ' ')
            .replace('_', ' ')
        val page = runCatching {
            musicSearchRepository.search(
                query = query,
                limit = 20,
                includeSoundCloud = true,
                // Compose-picker mode: keeps distinct rows pickable, matching
                // the in-app compose search behavior.
                collapse = "cover",
            )
        }.getOrNull() ?: return null
        val target = normalizedPermalink(url)
        return page.tracks.firstOrNull { track ->
            track.soundcloudPermalinkUrl?.let { normalizedPermalink(it) == target } == true
        }
    }

    /** Audiomack mirrors the SoundCloud strategy: slug → the same
     * `searchSongs` callable as in-app search (inheriting its filtering + the
     * audiomack RC gate), accepting ONLY the exact audiomackUrl match.
     * Audiomack IS a post source, so the match posts directly. */
    private suspend fun resolveAudiomack(url: String): CymbalTrack? {
        val segments = url.substringAfter("audiomack.com/").split('/')
        if (segments.size != 3) return null
        val query = "${segments[0]} ${segments[2]}"
            .replace('-', ' ')
            .replace('_', ' ')
        val page = runCatching {
            musicSearchRepository.search(query = query, limit = 20, includeSoundCloud = true, collapse = "cover")
        }.getOrNull() ?: return null
        val target = normalizedPermalink(url)
        return page.tracks.firstOrNull { track ->
            track.audiomackUrl?.let { normalizedPermalink(it) == target } == true
        }
    }

    private fun normalizedPermalink(raw: String): String {
        var out = raw.lowercase().substringBefore('?')
        for (prefix in listOf("https://", "http://")) {
            if (out.startsWith(prefix)) out = out.removePrefix(prefix)
        }
        for (prefix in listOf("www.", "m.")) {
            if (out.startsWith(prefix)) out = out.removePrefix(prefix)
        }
        return out.trimEnd('/')
    }

    // ── Deezer ─────────────────────────────────────────────────────────────

    /** Deezer track → Spotify via ISRC (Deezer's public API carries it).
     * Deezer isn't a Corus post source, so no validated match = null. */
    suspend fun resolveDeezerTrack(id: String): CymbalTrack? {
        val metadata = deezerTrackMetadata(id) ?: return null
        return resolveIsrcMatch(metadata.isrc ?: "", metadata.name, metadata.artistName)
    }

    /** Display metadata for a Deezer track from ONE public fetch — paints the
     * composer card instantly. Carries the ISRC so the background canonical
     * resolution needs no second Deezer call. NEVER POSTABLE as-is (Deezer
     * isn't a post source). */
    suspend fun deezerTrackMetadata(id: String): CymbalTrack? {
        val track = getPublicJson("https://api.deezer.com/track/$id") ?: return null
        val title = track.optString("title").ifEmpty { return null }
        val album = track.optJSONObject("album")
        val cover = (album?.optString("cover_xl")?.ifEmpty { null })
            ?: (album?.optString("cover_big")?.ifEmpty { null })
        val release = track.optString("release_date").takeIf { it.length >= 10 }?.take(10)
        return CymbalTrack(
            id = "deezer:$id",
            name = title,
            artistName = track.optJSONObject("artist")?.optString("name") ?: "",
            albumName = album?.optString("title") ?: "",
            albumArtURL = cover,
            albumArtLargeURL = cover,
            durationMs = track.optInt("duration", 0) * 1000,
            isrc = track.optString("isrc").ifEmpty { null },
            releaseDate = release,
            releaseDatePrecision = release?.let { "day" },
        )
    }

    /** Deezer album via the public API; rows resolve lazily on tap because
     * the album tracklist doesn't carry ISRCs. */
    suspend fun fetchDeezerAlbum(id: String): ShareAlbum? {
        val album = getPublicJson("https://api.deezer.com/album/$id") ?: return null
        val title = album.optString("title").ifEmpty { return null }
        val artistName = album.optJSONObject("artist")?.optString("name") ?: ""
        val cover = album.optString("cover_xl").ifEmpty { album.optString("cover_big") }
            .takeIf { it.isNotEmpty() }
        val year = album.optString("release_date").takeIf { it.length >= 4 }?.take(4)

        var items = album.optJSONObject("tracks")?.optJSONArray("data")
        // The inline tracklist can be truncated; page the tracks endpoint.
        val total = album.optInt("nb_tracks", -1)
        if (total >= 0 && (items?.length() ?: 0) < total) {
            val collected = org.json.JSONArray()
            var path: String? = "https://api.deezer.com/album/$id/tracks?limit=100"
            var pageGuard = 0
            while (path != null && pageGuard < 5) {
                pageGuard++
                val page = getPublicJson(path) ?: break
                val data = page.optJSONArray("data") ?: break
                for (i in 0 until data.length()) collected.put(data.opt(i))
                path = page.optString("next").takeIf { it.isNotEmpty() }
            }
            if (collected.length() > 0) items = collected
        }

        val tracks = buildList {
            val array = items ?: return@buildList
            for (i in 0 until array.length()) {
                val item = array.optJSONObject(i) ?: continue
                val trackId = item.optLong("id", -1)
                val name = item.optString("title")
                if (trackId <= 0 || name.isEmpty()) continue
                add(
                    ShareAlbumTrack(
                        id = trackId.toString(),
                        name = name,
                        artistName = item.optJSONObject("artist")?.optString("name") ?: artistName,
                        durationMs = item.optInt("duration", 0) * 1000,
                        deezerTrackId = trackId.toString(),
                    )
                )
            }
        }
        if (tracks.isEmpty()) return null
        return ShareAlbum(id, title, artistName, year, cover, album.optString("release_date").ifEmpty { null }, tracks)
    }

    // ── Catalog albums (Apple + Spotify) ───────────────────────────────────

    /**
     * The same `getAlbumCatalog` callable the app's album pages use. It
     * resolves both id shapes (Spotify id / `am:{appleAlbumId}`) server-side
     * and returns tracks in the normalized, directly-postable shape.
     */
    suspend fun fetchCatalogAlbum(albumId: String): ShareAlbum? = runCatching {
        val catalog = cloudFunctions.fetchAlbumCatalog(albumId)
        ShareAlbum(
            id = catalog.id,
            title = catalog.title,
            artistName = catalog.artistName,
            year = catalog.year?.toString(),
            coverUrl = catalog.coverUrl,
            tracks = catalog.tracks.map { track ->
                ShareAlbumTrack(
                    id = track.id,
                    name = track.name,
                    artistName = track.artistName,
                    durationMs = track.durationMs,
                    preResolved = track,
                )
            },
        )
    }.getOrNull()?.takeIf { it.tracks.isNotEmpty() }

    // ── Spotify cross-reference (shared by Apple + Deezer paths) ───────────

    /**
     * Port of the ISRC-first resolve with the same validation layers as the
     * backend/iOS: pre-flight ISRC format gate, post-flight ISRC equality
     * check, and name+artist alignment gates on the text fallback.
     */
    private suspend fun resolveIsrcMatch(isrc: String, fallbackName: String, fallbackArtist: String): CymbalTrack? {
        val trimmed = isrc.trim()
        if (ShareTrackMatch.isValidISRC(trimmed)) {
            val hit = spotifySearchOne("isrc:$trimmed")
            if (hit?.isrc?.uppercase(Locale.ROOT) == trimmed.uppercase(Locale.ROOT)) return hit
        }
        val query = "$fallbackName $fallbackArtist".trim()
        if (query.isEmpty()) return null
        val candidate = spotifySearchOne(query) ?: return null
        if (!ShareTrackMatch.namesAlign(candidate.name, fallbackName)) return null
        if (!ShareTrackMatch.artistMatches(fallbackArtist, candidate.artistName)) return null
        return candidate
    }

    private suspend fun spotifySearchOne(query: String): CymbalTrack? = runCatching {
        spotifyRepository.search(query, limit = 1).firstOrNull()?.takeIf { it.id.isNotEmpty() }
    }.getOrNull()

    // ── HTTP helpers ───────────────────────────────────────────────────────

    private suspend fun getPublicJson(url: String): JSONObject? = runCatching {
        val response = httpClient.get(url)
        if (response.status.value != 200) return@runCatching null
        JSONObject(response.bodyAsText()).takeIf { !it.has("error") }
    }.getOrNull()

    private suspend fun getAppleJson(url: String, token: String): JSONObject? = runCatching {
        val response = httpClient.get(url) { header("Authorization", "Bearer $token") }
        if (response.status.value != 200) return@runCatching null
        JSONObject(response.bodyAsText())
    }.getOrNull()
}
