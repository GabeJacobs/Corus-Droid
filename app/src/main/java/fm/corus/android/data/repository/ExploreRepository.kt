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
    suspend fun fetchTrendingHashtags(limit: Int = 10): List<CymbalHashtag> {
        return firestoreDataSource.fetchTrendingHashtags(limit)
    }

    suspend fun fetchTrendingSongs(limit: Int = 20): List<TrendingSong> {
        return firestoreDataSource.fetchTrendingSongs(limit)
    }

    suspend fun fetchTrendingMovies(limit: Int = 20): List<TrendingMovie> {
        return firestoreDataSource.fetchTrendingMovies(limit)
    }
}
