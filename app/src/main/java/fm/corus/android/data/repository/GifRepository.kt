package fm.corus.android.data.repository

import fm.corus.android.data.model.KlipyGif
import fm.corus.android.data.remote.CloudFunctionsDataSource
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GifRepository @Inject constructor(
    private val cloudFunctions: CloudFunctionsDataSource,
) {
    data class GifPage(
        val gifs: List<KlipyGif>,
        val currentPage: Int,
        val hasNext: Boolean,
    )

    suspend fun searchGifs(query: String, page: Int = 1): GifPage {
        val result = cloudFunctions.searchKlipyGifs(query = query, page = page)
        return GifPage(gifs = result.results, currentPage = result.currentPage, hasNext = result.hasNext)
    }

    suspend fun getTrendingGifs(page: Int = 1): GifPage {
        val result = cloudFunctions.searchKlipyGifs(query = "", page = page)
        return GifPage(gifs = result.results, currentPage = result.currentPage, hasNext = result.hasNext)
    }

    suspend fun triggerShare(slug: String) {
        cloudFunctions.triggerKlipyShare(slug)
    }
}
