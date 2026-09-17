package fm.corus.android.data.remote

import fm.corus.android.data.model.TrendingArtist
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

/** Catalog headshot from getArtistDetail. Never trending-cache album art. */
suspend fun CloudFunctionsDataSource.catalogArtistPortraitUrl(name: String): String? {
    val resolved = resolveArtistByName(name) ?: return null
    return runCatching { fetchArtistDetail(resolved.id, resolved.name).imageUrl }
        .getOrNull()
        ?.takeIf { it.isNotBlank() }
}

/** Catalog headshot from getDirectorDetail. Never trending-cache posters. */
suspend fun CloudFunctionsDataSource.catalogDirectorPortraitUrl(directorId: String): String? {
    if (directorId.isBlank()) return null
    return runCatching { fetchDirectorDetail(directorId).imageUrl }
        .getOrNull()
        ?.takeIf { it.isNotBlank() }
}

suspend fun CloudFunctionsDataSource.paintArtistCatalogImages(
    artists: List<TrendingArtist>,
): List<TrendingArtist> {
    if (artists.isEmpty()) return artists
    return coroutineScope {
        artists.map { artist ->
            async {
                if (!artist.catalogImageURL.isNullOrBlank()) artist
                else artist.copy(catalogImageURL = catalogArtistPortraitUrl(artist.artistName))
            }
        }.awaitAll()
    }
}
