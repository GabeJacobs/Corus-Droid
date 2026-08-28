package fm.corus.android.data.repository

import fm.corus.android.data.model.AlbumCatalog
import fm.corus.android.data.model.CymbalHashtag
import fm.corus.android.data.model.HashtagSuggestion
import fm.corus.android.data.model.NewAlbumCandidate
import fm.corus.android.data.model.TrendingAlbum
import fm.corus.android.data.model.TrendingMovie
import fm.corus.android.data.model.TrendingArtist
import fm.corus.android.data.model.TrendingSong
import fm.corus.android.data.model.TrendingWindow
import fm.corus.android.data.model.albumCatalogQualifiesAsNewFullAlbum
import fm.corus.android.data.model.albumTitlesMatch
import fm.corus.android.data.model.catalogDateToday
import fm.corus.android.data.model.newReleaseGroupLooksLikeSingle
import fm.corus.android.data.model.trendingAlbumShouldResolveByName
import fm.corus.android.data.remote.CloudFunctionsDataSource
import fm.corus.android.data.remote.FirestoreDataSource
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ExploreRepository @Inject constructor(
    private val firestoreDataSource: FirestoreDataSource,
    private val cloudFunctions: CloudFunctionsDataSource,
    private val musicSearchRepository: MusicSearchRepository,
) {
    companion object {
        private const val TRENDING_TTL_MS = 5L * 60 * 1000 // 5 minutes — matches iOS
        private const val NEW_ALBUM_CATALOG_CAP = 30
    }

    // Per-media caches hold all three windows from a single Firestore read so
    // toggling the window in the UI doesn't trigger an extra network call.
    @Volatile private var trendingSongsCache: CacheEntry<Map<TrendingWindow, List<TrendingSong>>>? = null
    @Volatile private var trendingArtistsCache: CacheEntry<Map<TrendingWindow, List<TrendingArtist>>>? = null
    @Volatile private var trendingAlbumsCache: CacheEntry<Map<TrendingWindow, List<TrendingAlbum>>>? = null
    @Volatile private var newReleaseAlbumsCache: CacheEntry<List<TrendingAlbum>>? = null
    @Volatile private var newAlbumsCache: CacheEntry<List<TrendingAlbum>>? = null
    @Volatile private var trendingMoviesCache: CacheEntry<Map<TrendingWindow, List<TrendingMovie>>>? = null
    @Volatile private var trendingHashtagsCache: CacheEntry<List<CymbalHashtag>>? = null

    suspend fun fetchTrendingHashtags(limit: Int = 10): List<CymbalHashtag> {
        trendingHashtagsCache?.let { if (it.isValid(TRENDING_TTL_MS)) return it.value }
        return firestoreDataSource.fetchTrendingHashtags(limit).also {
            trendingHashtagsCache = CacheEntry(it)
        }
    }

    /** Composer hashtag autocomplete. A bare "#" (empty query) shows the
     *  trending list (no badge; count = weekly windowed). A typed prefix runs a
     *  trending-first merge (see [mergeHashtagSuggestions]). Trending is served
     *  from the 5-minute cache. */
    suspend fun fetchHashtagSuggestions(query: String, limit: Int = 3): List<HashtagSuggestion> {
        if (query.isEmpty()) {
            // A bare "#" surfaces popular/trending tags to tap. Every row is
            // from the trending set, so each shows the "Trending" badge.
            return fetchTrendingHashtags(limit)
                .map { HashtagSuggestion(name = it.name, count = it.cymbalCount, trending = true) }
        }
        val trending = fetchTrendingHashtags(20)
        val prefix = firestoreDataSource.searchHashtagsByPrefix(query, 15)
        return mergeHashtagSuggestions(query, trending, prefix, limit)
    }

    suspend fun fetchTrendingSongs(
        window: TrendingWindow = TrendingWindow.DEFAULT,
        limit: Int = 20,
    ): List<TrendingSong> {
        trendingSongsCache?.let { if (it.isValid(TRENDING_TTL_MS)) return it.value[window].orEmpty() }
        val all = firestoreDataSource.fetchTrendingSongsByWindow(limit)
        trendingSongsCache = CacheEntry(all)
        return all[window].orEmpty()
    }

    suspend fun fetchTrendingMovies(
        window: TrendingWindow = TrendingWindow.DEFAULT,
        limit: Int = 20,
    ): List<TrendingMovie> {
        trendingMoviesCache?.let { if (it.isValid(TRENDING_TTL_MS)) return it.value[window].orEmpty() }
        val all = firestoreDataSource.fetchTrendingMoviesByWindow(limit)
        trendingMoviesCache = CacheEntry(all)
        return all[window].orEmpty()
    }

    suspend fun fetchTrendingArtists(
        window: TrendingWindow = TrendingWindow.YEAR,
        limit: Int = 20,
    ): List<TrendingArtist> {
        trendingArtistsCache?.let { if (it.isValid(TRENDING_TTL_MS)) return it.value[window].orEmpty() }
        val all = firestoreDataSource.fetchTrendingArtistsByWindow(limit)
        trendingArtistsCache = CacheEntry(all)
        return all[window].orEmpty()
    }

    suspend fun fetchTrendingAlbums(
        window: TrendingWindow = TrendingWindow.DEFAULT,
        limit: Int = 20,
    ): List<TrendingAlbum> {
        trendingAlbumsCache?.let { if (it.isValid(TRENDING_TTL_MS)) return it.value[window].orEmpty() }
        val all = firestoreDataSource.fetchTrendingAlbumsByWindow(limit)
        trendingAlbumsCache = CacheEntry(all)
        return all[window].orEmpty()
    }

    suspend fun fetchNewReleaseAlbums(limit: Int = 20): List<TrendingAlbum> {
        newReleaseAlbumsCache?.let { if (it.isValid(TRENDING_TTL_MS)) return it.value }
        return firestoreDataSource.fetchNewReleaseAlbums(limit).also {
            newReleaseAlbumsCache = CacheEntry(it)
        }
    }

    /** Fully-out multi-track albums in the 30-day window. Mirrors iOS/web
     *  `fetchNewAlbums` / `getNewAlbums`. */
    suspend fun fetchNewAlbums(limit: Int = 20): List<TrendingAlbum> {
        newAlbumsCache?.let { if (it.isValid(TRENDING_TTL_MS)) return it.value }
        val candidates = firestoreDataSource.fetchNewAlbumCandidates()
            .filter { !newReleaseGroupLooksLikeSingle(it.albumName, it.trackNames) }
            .sortedByDescending { it.count }
            .take(NEW_ALBUM_CATALOG_CAP)
        val today = catalogDateToday()
        val hits = coroutineScope {
            candidates.map { candidate ->
                async {
                    val catalog = qualifyNewFullAlbum(candidate) ?: return@async null
                    if (!albumCatalogQualifiesAsNewFullAlbum(
                            isPreRelease = catalog.isPreRelease,
                            releaseDate = catalog.releaseDate,
                            trackCount = catalog.tracks.size,
                            today = today,
                        )
                    ) {
                        return@async null
                    }
                    catalog to candidate.count
                }
            }.awaitAll().filterNotNull()
        }
        val byId = linkedMapOf<String, Triple<TrendingAlbum, String, Int>>()
        for ((catalog, count) in hits) {
            val id = catalog.id
            val date = catalog.releaseDate?.trim().orEmpty()
            val existing = byId[id]
            if (existing != null) {
                byId[id] = Triple(
                    existing.first,
                    maxOf(existing.second, date),
                    existing.third + count,
                )
                continue
            }
            byId[id] = Triple(
                TrendingAlbum(
                    id = id,
                    rank = 0,
                    albumId = id,
                    albumName = catalog.title,
                    artistName = catalog.artistName,
                    albumArtURL = catalog.coverUrl,
                    albumArtLargeURL = catalog.coverUrl,
                    cymbalCount = count,
                    trackReleaseDate = date,
                ),
                date,
                count,
            )
        }
        val ranked = byId.values
            .sortedWith(compareByDescending<Triple<TrendingAlbum, String, Int>> { it.second }.thenByDescending { it.third })
            .take(limit)
            .mapIndexed { index, (album, _, count) ->
                album.copy(rank = index + 1, cymbalCount = count)
            }
        return ranked.also { newAlbumsCache = CacheEntry(it) }
    }

    private suspend fun qualifyNewFullAlbum(candidate: NewAlbumCandidate): AlbumCatalog? {
        var catalog: AlbumCatalog? = null
        if (candidate.albumId.isNotEmpty()) {
            catalog = runCatching {
                cloudFunctions.fetchAlbumCatalog(
                    candidate.albumId,
                    candidate.albumName.ifEmpty { null },
                    candidate.artistName.ifEmpty { null },
                )
            }.getOrNull()
            if (catalog != null && candidate.albumName.isNotEmpty() &&
                trendingAlbumShouldResolveByName(candidate.albumName, catalog.title)
            ) {
                catalog = null
            }
        }
        if (catalog == null) {
            if (candidate.albumName.isEmpty()) return null
            val query = listOf(candidate.albumName, candidate.artistName)
                .filter { it.isNotBlank() }
                .joinToString(" ")
            if (query.isBlank()) return null
            val page = runCatching {
                musicSearchRepository.search(
                    query = query,
                    limit = 5,
                    includeAlbums = true,
                    albumsMatchArtist = true,
                )
            }.getOrNull() ?: return null
            val resolved = page.albums.firstOrNull { albumTitlesMatch(it.title, candidate.albumName) }
                ?: page.albums.firstOrNull()
                ?: return null
            catalog = runCatching {
                cloudFunctions.fetchAlbumCatalog(resolved.id, resolved.title, resolved.artistName)
            }.getOrNull()
        }
        return catalog
    }

    fun clearCaches() {
        trendingArtistsCache = null
        trendingAlbumsCache = null
        newReleaseAlbumsCache = null
        newAlbumsCache = null
        trendingSongsCache = null
        trendingMoviesCache = null
        trendingHashtagsCache = null
    }
}

/**
 * Trending-first merge for a typed prefix — mirrors the web/iOS
 * `mergeHashtagSuggestions`. Trending matches come first (badge + weekly
 * count); the rest are ranked by this-week activity (`recentCount`, then
 * all-time) with a light floor that hides one-off / likely-typo tags but
 * always keeps an exact-name match. Pure function for unit testing.
 */
fun mergeHashtagSuggestions(
    query: String,
    trending: List<CymbalHashtag>,
    prefix: List<CymbalHashtag>,
    limit: Int,
): List<HashtagSuggestion> {
    val trendingMatches = trending
        .filter { it.name.startsWith(query) }
        .take(limit)
        .map { HashtagSuggestion(name = it.name, count = it.cymbalCount, trending = true) }
    val trendingNames = trendingMatches.map { it.name }.toSet()
    val others = prefix
        .filter { it.name !in trendingNames }
        .filter { it.recentCount > 0 || it.cymbalCount >= 2 || it.name == query }
        .sortedWith(
            compareByDescending<CymbalHashtag> { it.recentCount }.thenByDescending { it.cymbalCount },
        )
        .map { HashtagSuggestion(name = it.name, count = it.recentCount, trending = false) }
    return (trendingMatches + others).take(limit)
}
