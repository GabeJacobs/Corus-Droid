package fm.corus.android.ui.screens.live

import java.net.URI
import java.util.Locale

internal sealed interface LivePlaylistLink {
    data class Spotify(val id: String, val isAlbum: Boolean = false) : LivePlaylistLink
    data class AppleMusic(val id: String, val storefront: String, val isAlbum: Boolean = false) : LivePlaylistLink

    companion object {
        private val spotifyId = Regex("[A-Za-z0-9]{22}")
        private val appleId = Regex("pl\\.[A-Za-z0-9._-]+")
        private val storefrontCode = Regex("[A-Za-z]{2}")

        fun parse(link: String): LivePlaylistLink? {
            val uri = runCatching { URI(link.trim()) }.getOrNull() ?: return null
            if (!uri.scheme.equals("https", ignoreCase = true)) return null
            val segments = uri.path.orEmpty().split('/').filter(String::isNotEmpty)
            val isAlbum = "album" in segments.dropLast(1)
            if (!isAlbum && "playlist" !in segments.dropLast(1)) return null
            val id = segments.last()
            return when (uri.host?.lowercase(Locale.ROOT)) {
                "open.spotify.com" -> id.takeIf(spotifyId::matches)?.let { Spotify(it, isAlbum) }
                "music.apple.com" -> id.takeIf { if (isAlbum) it.matches(Regex("[0-9]+")) else appleId.matches(it) }?.let {
                    val storefront = segments.first().takeIf(storefrontCode::matches)?.lowercase(Locale.ROOT) ?: "us"
                    AppleMusic(id, storefront, isAlbum)
                }
                else -> null
            }
        }
    }
}
