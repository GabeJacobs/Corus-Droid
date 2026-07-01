package fm.corus.android.data.repository

import fm.corus.android.data.model.CymbalHashtag
import fm.corus.android.data.model.HashtagSuggestion
import fm.corus.android.data.model.TrendingMovie
import fm.corus.android.data.model.TrendingSong
import fm.corus.android.data.model.TrendingWindow
import fm.corus.android.data.remote.FirestoreDataSource
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ExploreRepository @Inject constructor(
    private val firestoreDataSource: FirestoreDataSource,
) {
    companion object {
        private const val TRENDING_TTL_MS = 5L * 60 * 1000 // 5 minutes — matches iOS
    }

    // Per-media caches hold all three windows from a single Firestore read so
    // toggling the window in the UI doesn't trigger an extra network call.
    @Volatile private var trendingSongsCache: CacheEntry<Map<TrendingWindow, List<TrendingSong>>>? = null
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

    fun clearCaches() {
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
