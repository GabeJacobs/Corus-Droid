package fm.corus.android.domain

import java.net.URI
import java.util.Locale

/** Upgrade known public CDN thumbnails without modifying arbitrary or signed URLs. */
internal fun collectionArtworkUrl(url: String): String {
    val uri = runCatching { URI(url) }.getOrNull() ?: return url
    if (uri.scheme !in listOf("https", "http")) return url
    val host = uri.host?.lowercase(Locale.ROOT) ?: return url
    val path = uri.rawPath ?: return url
    val upgradedPath = when {
        host == "i.scdn.co" -> path.replace(
            Regex("^/image/ab67616d(?:00004851|00001e02)"),
            "/image/ab67616d0000b273",
        )
        host == "mzstatic.com" || host.endsWith(".mzstatic.com") -> {
            Regex("/(\\d+)x(\\d+)(bb[^/]*)$").replace(path) { match ->
                val width = match.groupValues[1].toIntOrNull() ?: return@replace match.value
                val height = match.groupValues[2].toIntOrNull() ?: return@replace match.value
                if (maxOf(width, height) < 600) "/600x600${match.groupValues[3]}" else match.value
            }
        }
        host == "sndcdn.com" || host.endsWith(".sndcdn.com") ->
            path.replace(Regex("-large(?=\\.[^/]+$)"), "-t500x500")
        else -> path
    }
    if (upgradedPath == path) return url
    val pathStart = url.indexOf(uri.rawAuthority, uri.scheme.length + 3) + uri.rawAuthority.length
    return url.replaceRange(pathStart, pathStart + path.length, upgradedPath)
}
