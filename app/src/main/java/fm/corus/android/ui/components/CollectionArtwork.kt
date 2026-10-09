package fm.corus.android.ui.components

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import coil3.size.Precision
import fm.corus.android.domain.collectionArtworkUrl

internal fun collectionArtworkRequest(context: Context, url: String): ImageRequest =
    ImageRequest.Builder(context)
        .data(url)
        // AsyncImage supplies the tile's physical pixel size. EXACT prevents a
        // smaller sampled bitmap from satisfying a larger request at this URL.
        .precision(Precision.EXACT)
        .memoryCacheKey("collection-artwork-v1:$url")
        // Share downloaded bytes with the rest of the app. Never alias a failed
        // high-resolution URL to the thumbnail's cache entry.
        .diskCacheKey(url)
        .crossfade(false)
        .build()

@Composable
internal fun CollectionArtwork(
    url: String,
    modifier: Modifier = Modifier,
    fallbackUrl: String? = null,
    contentDescription: String? = null,
) {
    val context = LocalContext.current
    val candidates = remember(url, fallbackUrl) {
        listOfNotNull(collectionArtworkUrl(url), fallbackUrl?.let(::collectionArtworkUrl), url, fallbackUrl)
            .filter { it.isNotBlank() }.distinct()
    }
    var candidateIndex by remember(candidates) { mutableIntStateOf(0) }
    val activeUrl = candidates.getOrNull(candidateIndex)
    val request = remember(context, activeUrl) { activeUrl?.let { collectionArtworkRequest(context, it) } }
    AsyncImage(
        model = request,
        contentDescription = contentDescription,
        contentScale = ContentScale.Crop,
        modifier = modifier,
        onError = { if (candidateIndex < candidates.lastIndex) candidateIndex++ },
    )
}
