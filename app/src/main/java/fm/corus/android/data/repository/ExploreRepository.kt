package fm.corus.android.data.repository

import fm.corus.android.data.model.CymbalHashtag
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
