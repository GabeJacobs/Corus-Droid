package fm.corus.android.data.model

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

fun albumTitlesMatch(a: String, b: String): Boolean {
    val na = foldEntityName(a)
    val nb = foldEntityName(b)
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

/** A New Albums row: multi-track catalog that is already out and inside
 *  the 30-day window. Singles and unreleased LPs never qualify. */
fun albumCatalogQualifiesAsNewFullAlbum(
    isPreRelease: Boolean,
    releaseDate: String?,
    trackCount: Int,
    today: String = catalogDateToday(),
): Boolean {
    if (trackCount < 2) return false
    if (albumCatalogIsUnreleased(isPreRelease, releaseDate, today)) return false
    return albumCatalogIsNewRelease(releaseDate, today)
}

data class NewAlbumCandidate(
    val albumId: String,
    val albumName: String,
    val artistName: String,
    val albumArtURL: String? = null,
    val count: Int,
    val trackNames: Set<String>,
)

/** One unique track whose title is the album title — a single, not an LP. */
fun newReleaseGroupLooksLikeSingle(albumName: String, trackNames: Collection<String>): Boolean {
    val unique = mutableListOf<String>()
    val seen = mutableSetOf<String>()
    for (name in trackNames) {
        val folded = foldEntityName(name)
        if (folded.isEmpty() || folded in seen) continue
        seen.add(folded)
        unique.add(name)
    }
    if (unique.size != 1) return false
    return albumTitlesMatch(albumName, unique[0])
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
 * when that catalog matches the painted name.
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
    val title = album.displayTitle.trim()
    val artist = album.artistName.trim()
    if (album.albumId.isNotEmpty()) {
        try {
            val catalog = fetchCatalog(album.albumId, title, artist)
            if (catalog != null) {
                if (albumCatalogIsUnreleased(catalog.isPreRelease, catalog.releaseDate, today)) {
                    return album.asSongTrack()?.let { TrendingAlbumOpen.Song(it) }
                }
                if (!trendingAlbumShouldResolveByName(title, catalog.title)) {
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
    album.asSongTrack()?.let { return TrendingAlbumOpen.Song(it) }
    if (album.albumId.isEmpty()) return null
    return TrendingAlbumOpen.Album(
        albumId = album.albumId,
        title = title,
        artist = artist,
        coverUrl = album.albumArtLargeURL ?: album.albumArtURL,
    )
}
