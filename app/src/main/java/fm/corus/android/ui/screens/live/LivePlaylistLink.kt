package fm.corus.android.ui.screens.live

import java.net.URI
import java.util.Locale

internal sealed interface LivePlaylistLink {
    data class Spotify(val id: String) : LivePlaylistLink
    data class AppleMusic(val id: String, val storefront: String) : LivePlaylistLink

    companion object {
        private val spotifyId = Regex("[A-Za-z0-9]{22}")
        private val appleId = Regex("pl\\.[A-Za-z0-9._-]+")
        private val storefrontCode = Regex("[A-Za-z]{2}")

        fun parse(link: String): LivePlaylistLink? {
            val uri = runCatching { URI(link.trim()) }.getOrNull() ?: return null
            if (!uri.scheme.equals("https", ignoreCase = true)) return null
            val segments = uri.path.orEmpty().split('/').filter(String::isNotEmpty)
            if ("playlist" !in segments.dropLast(1)) return null
            val id = segments.last()
            return when (uri.host?.lowercase(Locale.ROOT)) {
                "open.spotify.com" -> id.takeIf(spotifyId::matches)?.let(::Spotify)
                "music.apple.com" -> id.takeIf(appleId::matches)?.let {
                    val storefront = segments.first().takeIf(storefrontCode::matches)?.lowercase(Locale.ROOT) ?: "us"
                    AppleMusic(id, storefront)
                }
                else -> null
            }
        }
    }
}
