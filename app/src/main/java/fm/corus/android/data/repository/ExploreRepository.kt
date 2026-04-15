package fm.corus.android.data.repository

import fm.corus.android.data.model.CymbalHashtag
import fm.corus.android.data.model.TrendingMovie
import fm.corus.android.data.model.TrendingSong
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

    @Volatile private var trendingSongsCache: CacheEntry<List<TrendingSong>>? = null
    @Volatile private var trendingMoviesCache: CacheEntry<List<TrendingMovie>>? = null
    @Volatile private var trendingHashtagsCache: CacheEntry<List<CymbalHashtag>>? = null

    suspend fun fetchTrendingHashtags(limit: Int = 10): List<CymbalHashtag> {
        trendingHashtagsCache?.let { if (it.isValid(TRENDING_TTL_MS)) return it.value }
        return firestoreDataSource.fetchTrendingHashtags(limit).also {
            trendingHashtagsCache = CacheEntry(it)
        }
    }

    suspend fun fetchTrendingSongs(limit: Int = 20): List<TrendingSong> {
        trendingSongsCache?.let { if (it.isValid(TRENDING_TTL_MS)) return it.value }
        return firestoreDataSource.fetchTrendingSongs(limit).also {
            trendingSongsCache = CacheEntry(it)
        }
    }

    suspend fun fetchTrendingMovies(limit: Int = 20): List<TrendingMovie> {
        trendingMoviesCache?.let { if (it.isValid(TRENDING_TTL_MS)) return it.value }
        return firestoreDataSource.fetchTrendingMovies(limit).also {
            trendingMoviesCache = CacheEntry(it)
        }
    }

    fun clearCaches() {
        trendingSongsCache = null
        trendingMoviesCache = null
        trendingHashtagsCache = null
    }
}
