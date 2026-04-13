package fm.corus.android.data.repository

import fm.corus.android.data.model.TenorGif
import fm.corus.android.data.remote.CloudFunctionsDataSource
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GifRepository @Inject constructor(
    private val cloudFunctions: CloudFunctionsDataSource,
) {
    data class GifSearchResult(
        val gifs: List<TenorGif>,
        val nextCursor: String,
    )

    suspend fun searchGifs(query: String, pos: String = ""): GifSearchResult {
        val result = cloudFunctions.searchGifs(query = query, pos = pos)
        return GifSearchResult(gifs = result.results, nextCursor = result.next)
    }

    suspend fun getTrendingGifs(pos: String = ""): GifSearchResult {
        val result = cloudFunctions.searchGifs(query = "", pos = pos)
        return GifSearchResult(gifs = result.results, nextCursor = result.next)
    }
}
