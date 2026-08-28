package fm.corus.android.data.model

import java.util.concurrent.ConcurrentHashMap

/**
 * Folded name + art collapse used by New Releases / Trending album rails.
 * Mirrors web `album-title-match.ts` and iOS `DestinationModels.swift`.
 */

fun foldEntityName(s: String): String =
    s.lowercase()
        .replace("&", " and ")
        .replace(",", " ")
        .split(Regex("\\s+"))
        .filter { it.isNotEmpty() }
        .joinToString(" ")

/** Strip query/hash and Apple size suffixes so the same cover is one key. */
fun normalizeAlbumArtKey(url: String?): String {
    var u = url?.trim()?.lowercase().orEmpty()
    if (u.isEmpty()) return ""
    u = u.substringBefore("?").substringBefore("#")
    return u.replace(Regex("/\\d+x\\d+[a-z]*\\.jpg$", RegexOption.IGNORE_CASE), "")
}

/** Same-release key for Apple vs Spotify copies. No art → null (do not guess). */
fun albumReleaseCollapseKey(
    albumName: String?,
    artistName: String?,
    albumArtURL: String? = null,
    albumArtLargeURL: String? = null,
): String? {
    val name = foldEntityName(albumName.orEmpty())
    val artist = foldEntityName(artistName.orEmpty())
    val art = normalizeAlbumArtKey(albumArtLargeURL ?: albumArtURL)
    if (name.isEmpty() || artist.isEmpty() || art.isEmpty()) return null
    return "$name|$artist|$art"
}

/** Drop catalog edition tags so "Jolene" matches "Jolene (Expanded Edition)".
 *  Remix / live / "when you don't call" parentheticals stay — those are
 *  different releases, not the same LP remastered. */
fun stripAlbumEditionQualifier(s: String): String =
    s.replace(
        Regex(
            """\s*\((?:deluxe|remaster|expanded|anniversary|special|bonus|limited|standard|mono|stereo|explicit|clean|super deluxe|complete)[^)]*\)""",
            RegexOption.IGNORE_CASE,
        ),
        "",
    ).replace(
        Regex(
            """\s*\[(?:deluxe|remaster|expanded|anniversary|special|bonus|limited|standard|mono|stereo|explicit|clean|super deluxe|complete)[^\]]*\]""",
            RegexOption.IGNORE_CASE,
        ),
        "",
    ).trim()

fun albumTitlesMatch(a: String, b: String): Boolean {
    val na = stripAlbumEditionQualifier(foldEntityName(a))
    val nb = stripAlbumEditionQualifier(foldEntityName(b))
    if (na.isEmpty() || nb.isEmpty()) return false
    if (na == nb) return true
    if (na.startsWith(nb) || nb.startsWith(na)) {
        return kotlin.math.abs(na.length - nb.length) <= 2
    }
    return false
}

fun trendingAlbumShouldResolveByName(displayedTitle: String, catalogTitle: String?): Boolean {
    val catalog = catalogTitle?.trim().orEmpty()
    if (catalog.isEmpty()) return false
    return !albumTitlesMatch(displayedTitle, catalog)
}

/** Search "Trending albums" rail — not a New Songs track row. */
fun isTrendingAlbumRailRow(album: TrendingAlbum): Boolean =
    !album.openAsSong && album.trackId.trim().isEmpty()

/**
 * A trending-album tap must open an album page. Catalog / name-resolve can
 * still pick a better id, but they must never toast: a stamped `albumId`
 * is always a valid destination.
 */
fun guaranteeTrendingAlbumTap(
    album: TrendingAlbum,
    dest: TrendingAlbumOpen?,
): TrendingAlbumOpen? {
    if (!isTrendingAlbumRailRow(album)) return dest
    if (dest is TrendingAlbumOpen.Album && dest.albumId.isNotEmpty()) return dest
    val albumId = album.albumId.trim()
    if (albumId.isEmpty()) return dest
    return TrendingAlbumOpen.Album(
        albumId = albumId,
        title = album.displayTitle,
        artist = album.artistName,
        coverUrl = album.albumArtLargeURL ?: album.albumArtURL,
    )
}

fun trendingAlbumDestinationCacheKey(album: TrendingAlbum): String {
    val albumId = album.albumId.trim()
    if (albumId.isNotEmpty()) return "id:$albumId"
    return "name:${album.displayTitle.trim().lowercase()}|${album.artistName.trim().lowercase()}"
}

object TrendingAlbumDestinationCache {
    private val dests = ConcurrentHashMap<String, TrendingAlbumOpen>()

    fun peek(album: TrendingAlbum): TrendingAlbumOpen? {
        if (album.openAsSong) return album.asSongTrack()?.let { TrendingAlbumOpen.Song(it) }
        return dests[trendingAlbumDestinationCacheKey(album)]
    }

    fun remember(album: TrendingAlbum, dest: TrendingAlbumOpen?) {
        if (dest == null) return
        dests[trendingAlbumDestinationCacheKey(album)] = dest
    }

    fun clear() {
        dests.clear()
    }
}

fun newReleaseShouldOpenAsSong(parentAlbumUnreleased: Boolean): Boolean = parentAlbumUnreleased

fun catalogDateToday(): String =
    java.time.LocalDate.now(java.time.ZoneOffset.UTC).toString()

fun albumCatalogIsNewRelease(
    releaseDate: String?,
    today: String = catalogDateToday(),
): Boolean {
    val raw = releaseDate?.trim().orEmpty()
    if (raw.length < 10) return false
    val day = raw.take(10)
    if (day > today) return false
    val released = Regex("""^(\d{4})-(\d{2})-(\d{2})$""").matchEntire(day) ?: return false
    val now = Regex("""^(\d{4})-(\d{2})-(\d{2})$""").matchEntire(today) ?: return false
    val releaseMs = java.time.LocalDate.of(
        released.groupValues[1].toInt(),
        released.groupValues[2].toInt(),
        released.groupValues[3].toInt(),
    ).toEpochDay()
    val todayMs = java.time.LocalDate.of(
        now.groupValues[1].toInt(),
        now.groupValues[2].toInt(),
        now.groupValues[3].toInt(),
    ).toEpochDay()
    return todayMs - releaseMs >= 0 && todayMs - releaseMs < 30
}

fun albumCatalogIsUnreleased(
    isPreRelease: Boolean,
    releaseDate: String?,
    today: String,
): Boolean {
    if (isPreRelease) return true
    val raw = releaseDate?.trim().orEmpty()
    if (raw.isEmpty()) return false
    if (raw.length >= 10) return raw.take(10) > today
    if (raw.length == 4 && raw.all { it.isDigit() } && today.length >= 4) {
        return raw > today.take(4)
    }
    return false
}

data class NewReleasePostedSong(
    val trackId: String,
    val trackName: String,
    val albumArtURL: String? = null,
    val albumArtLargeURL: String? = null,
    val releaseDate: String,
    val count: Int,
)

data class NewReleaseAlbumDraft(
    val id: String,
    var album: TrendingAlbum,
    var releaseDate: String,
    var songs: List<NewReleasePostedSong>,
)

fun newReleaseAlbumKey(
    albumId: String?,
    albumName: String?,
    artistName: String?,
    trackName: String?,
    trackId: String?,
    parentAlbumUnreleased: Boolean,
): String? {
    val tid = trackId?.trim().orEmpty()
    if (parentAlbumUnreleased && tid.isNotEmpty()) return "track:${tid.lowercase()}"
    val id = albumId?.trim().orEmpty()
    if (id.isNotEmpty()) return "id:${id.lowercase()}"
    val title = (albumName ?: trackName)?.trim().orEmpty()
    val artist = artistName?.trim().orEmpty()
    if (title.isNotEmpty()) return "name:${artist.lowercase()}\u0000${title.lowercase()}"
    return if (tid.isNotEmpty()) "track:${tid.lowercase()}" else null
}

private fun mergeNewReleaseSongs(
    a: List<NewReleasePostedSong>,
    b: List<NewReleasePostedSong>,
): List<NewReleasePostedSong> {
    val byId = linkedMapOf<String, NewReleasePostedSong>()
    val rest = mutableListOf<NewReleasePostedSong>()
    for (song in a + b) {
        val tid = song.trackId.trim().lowercase()
        if (tid.isEmpty()) {
            rest.add(song)
            continue
        }
        val hit = byId[tid]
        if (hit != null) {
            byId[tid] = hit.copy(
                count = hit.count + song.count,
                releaseDate = if (song.releaseDate > hit.releaseDate) song.releaseDate else hit.releaseDate,
                albumArtURL = hit.albumArtURL ?: song.albumArtURL,
                albumArtLargeURL = hit.albumArtLargeURL ?: song.albumArtLargeURL,
            )
        } else {
            byId[tid] = song
        }
    }
    return byId.values.toList() + rest
}

/**
 * Collapse Apple + Spotify copies of the same released album (same folded
 * name + artist + art). Song rows (`openAsSong`) stay keyed by track.
 */
fun collapseNewReleaseAlbumDrafts(drafts: List<NewReleaseAlbumDraft>): List<NewReleaseAlbumDraft> {
    val collapsed = linkedMapOf<String, NewReleaseAlbumDraft>()
    val passthrough = mutableListOf<NewReleaseAlbumDraft>()
    for (draft in drafts) {
        if (draft.album.openAsSong) {
            passthrough.add(draft)
            continue
        }
        val key = albumReleaseCollapseKey(
            albumName = draft.album.albumName,
            artistName = draft.album.artistName,
            albumArtURL = draft.album.albumArtURL,
            albumArtLargeURL = draft.album.albumArtLargeURL,
        )
        if (key == null) {
            passthrough.add(draft)
            continue
        }
        val existing = collapsed[key]
        if (existing == null) {
            collapsed[key] = draft.copy(
                album = draft.album,
                songs = draft.songs.toList(),
            )
            continue
        }
        val takeIncoming = draft.album.cymbalCount > existing.album.cymbalCount
        val winner = if (takeIncoming) draft else existing
        val loser = if (takeIncoming) existing else draft
        collapsed[key] = NewReleaseAlbumDraft(
            id = winner.id,
            releaseDate = maxOf(draft.releaseDate, existing.releaseDate),
            songs = mergeNewReleaseSongs(existing.songs, draft.songs),
            album = winner.album.copy(
                albumId = winner.album.albumId.ifEmpty { loser.album.albumId },
                cymbalCount = existing.album.cymbalCount + draft.album.cymbalCount,
            ),
        )
    }
    return collapsed.values.toList() + passthrough
}

/**
 * Destination for a New Releases / Trending album row.
 * Unreleased catalogs open as the posted song. A stamped id is used only
 * when that catalog matches the painted name. Rail rows always fall back
 * to the stamped album id so the tap never toasts.
 */
suspend fun resolveTrendingAlbumOpen(
    album: TrendingAlbum,
    fetchCatalog: suspend (albumId: String, albumName: String, artistName: String) -> AlbumCatalog?,
    resolveByName: suspend (name: String, artist: String) -> AlbumSearchSummary?,
    today: String,
): TrendingAlbumOpen? {
    if (album.openAsSong) {
        return album.asSongTrack()?.let { TrendingAlbumOpen.Song(it) }
    }
    TrendingAlbumDestinationCache.peek(album)?.let { return it }
    val dest = guaranteeTrendingAlbumTap(
        album,
        resolveTrendingAlbumOpenRaw(album, fetchCatalog, resolveByName, today),
    )
    TrendingAlbumDestinationCache.remember(album, dest)
    return dest
}

private suspend fun resolveTrendingAlbumOpenRaw(
    album: TrendingAlbum,
    fetchCatalog: suspend (albumId: String, albumName: String, artistName: String) -> AlbumCatalog?,
    resolveByName: suspend (name: String, artist: String) -> AlbumSearchSummary?,
    today: String,
): TrendingAlbumOpen? {
    val title = album.displayTitle.trim()
    val artist = album.artistName.trim()
    if (album.albumId.isNotEmpty()) {
        try {
            val catalog = fetchCatalog(album.albumId, title, artist)
            if (catalog != null) {
                val unreleased = albumCatalogIsUnreleased(
                    catalog.isPreRelease,
                    catalog.releaseDate,
                    today,
                )
                if (unreleased) {
                    album.asSongTrack()?.let { return TrendingAlbumOpen.Song(it) }
                } else if (!trendingAlbumShouldResolveByName(title, catalog.title)) {
                    return TrendingAlbumOpen.Album(
                        albumId = catalog.id.ifEmpty { album.albumId },
                        title = catalog.title.ifEmpty { title },
                        artist = catalog.artistName.ifEmpty { artist },
                        coverUrl = catalog.coverUrl ?: album.albumArtLargeURL ?: album.albumArtURL,
                    )
                }
            }
        } catch (_: Exception) {
            // Unknown catalog — fall through to name resolve, then the song.
        }
    }
    val resolved = resolveByName(title, artist)
    if (resolved != null) {
        return TrendingAlbumOpen.Album(
            albumId = resolved.id,
            title = resolved.title.ifEmpty { title },
            artist = resolved.artistName.ifEmpty { artist },
            coverUrl = resolved.coverUrl ?: album.albumArtLargeURL ?: album.albumArtURL,
        )
    }
    return album.asSongTrack()?.let { TrendingAlbumOpen.Song(it) }
}
