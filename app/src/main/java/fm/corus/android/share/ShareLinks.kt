package fm.corus.android.share

import java.net.URI

/**
 * A music link handed to the share target.
 *
 * KEEP IN SYNC with the iOS extension's `SharedSongLink`
 * (Corus-iOS/CymbalShare/ShareTrackResolver.swift) — same services, same
 * URL shapes, same rejections. Artists, playlists, podcasts, audiobooks,
 * and random links never parse (podcast/show/episode paths and hosts are
 * structurally excluded), which is the content-safety gate: nothing Corus
 * search filters out can enter the share composer.
 */
sealed class SharedMusicLink {
    data class SpotifyTrack(val trackId: String) : SharedMusicLink()
    data class SpotifyAlbum(val albumId: String) : SharedMusicLink()
    data class AppleMusicSong(val id: String, val storefront: String?) : SharedMusicLink()
    data class AppleMusicAlbum(val albumId: String, val storefront: String?) : SharedMusicLink()

    /** Normalized `https://soundcloud.com/{artist}/{slug}` track page URL. */
    data class SoundCloudTrack(val url: String) : SharedMusicLink()

    /** Normalized `https://audiomack.com/{artist}/song/{slug}` page URL. */
    data class AudiomackTrack(val url: String) : SharedMusicLink()
    data class DeezerTrack(val id: String) : SharedMusicLink()
    data class DeezerAlbum(val id: String) : SharedMusicLink()
    data class TidalTrack(val id: String) : SharedMusicLink()
    data class TidalAlbum(val id: String) : SharedMusicLink()

    val isAlbum: Boolean
        get() = this is SpotifyAlbum || this is AppleMusicAlbum || this is DeezerAlbum || this is TidalAlbum

    companion object {

        /**
         * Extracts a supported link from a URL string, or null for anything
         * else. Pure — short links (spotify.link, on.soundcloud.com,
         * deezer.page.link) must be expanded by the caller first.
         */
        fun parse(raw: String): SharedMusicLink? {
            val url = UrlParts.from(raw) ?: return null
            spotifyId(url, "track")?.let { return SpotifyTrack(it) }
            spotifyId(url, "album")?.let { return SpotifyAlbum(it) }
            appleMusicSong(url)?.let { return it }
            appleMusicAlbum(url)?.let { return it }
            soundcloudTrack(url)?.let { return it }
            audiomackTrack(url)?.let { return it }
            deezerId(url, "track")?.let { return DeezerTrack(it) }
            deezerId(url, "album")?.let { return DeezerAlbum(it) }
            tidalId(url, "track")?.let { return TidalTrack(it) }
            tidalId(url, "album")?.let { return TidalAlbum(it) }
            return null
        }

        /** Hosts that wrap real links behind a redirect. Expand before parsing. */
        fun isShortLink(raw: String): Boolean {
            val host = UrlParts.from(raw)?.host ?: return false
            return listOf("spotify.link", "on.soundcloud.com", "deezer.page.link", "dzr.page.link")
                .any { host == it || host.endsWith(".$it") }
        }

        /**
         * First http(s) URL in a block of shared text. Spotify (and several
         * other apps) share the link as plain text, sometimes with words
         * around it.
         */
        fun firstUrlIn(text: String): String? {
            val match = URL_REGEX.find(text) ?: return null
            return match.value.trimEnd(*TRAILING_PUNCTUATION)
        }

        private val URL_REGEX = Regex("""https?://\S+""", RegexOption.IGNORE_CASE)
        private val TRAILING_PUNCTUATION = charArrayOf('.', ',', ')', ']', '>', '!', '?', ';', ':', '\'', '"')

        /**
         * Spotify entity id from `open.spotify.com/[intl-xx/]<entity>/<id>`.
         * Spotify ids are 22 alphanumerics. Episodes / shows / audiobooks use
         * different path entities and never match.
         */
        private fun spotifyId(url: UrlParts, entity: String): String? {
            if (!url.host.contains("spotify.com")) return null
            val index = url.segments.indexOfFirst { it.equals(entity, ignoreCase = true) }
            if (index == -1 || index + 1 >= url.segments.size) return null
            val id = url.segments[index + 1]
            if (id.length !in 15..30 || !id.all { it.isLetterOrDigit() }) return null
            return id
        }

        /**
         * Apple Music SONG link, both shapes Apple produces:
         *   album-scoped song: `…/<sf>/album/<name>/<albumId>?i=<songId>`
         *   direct song link:  `…/<sf>/song/<name>/<songId>`
         * Apple catalog ids are numeric.
         */
        private fun appleMusicSong(url: UrlParts): AppleMusicSong? {
            if (!url.host.contains("music.apple.com")) return null
            val songId = url.query["i"]
            if (songId != null && isAppleCatalogId(songId)) {
                return AppleMusicSong(songId, appleStorefront(url))
            }
            if (url.segments.any { it.equals("song", ignoreCase = true) }) {
                val last = url.segments.lastOrNull() ?: return null
                if (isAppleCatalogId(last)) return AppleMusicSong(last, appleStorefront(url))
            }
            return null
        }

        /** Apple Music ALBUM link (no `?i=` — that shape is a song, above). */
        private fun appleMusicAlbum(url: UrlParts): AppleMusicAlbum? {
            if (!url.host.contains("music.apple.com")) return null
            if (!url.segments.any { it.equals("album", ignoreCase = true) }) return null
            val last = url.segments.lastOrNull() ?: return null
            if (!isAppleCatalogId(last)) return null
            return AppleMusicAlbum(last, appleStorefront(url))
        }

        private fun appleStorefront(url: UrlParts): String? {
            val first = url.segments.firstOrNull() ?: return null
            return if (first.length == 2 && first.all { it.isLetter() }) first.lowercase() else null
        }

        private fun isAppleCatalogId(s: String): Boolean =
            s.length >= 4 && s.all { it.isDigit() }

        /**
         * SoundCloud TRACK page: exactly two path segments
         * (`/{artist}/{track-slug}`). Profiles are one segment; playlists are
         * `/{artist}/sets/{name}`; app pages don't match the two-segment shape.
         */
        private fun soundcloudTrack(url: UrlParts): SoundCloudTrack? {
            val isSoundcloud = url.host == "soundcloud.com" || url.host.endsWith(".soundcloud.com")
            if (!isSoundcloud || url.host.startsWith("on.")) return null
            if (url.segments.size != 2) return null
            val (artist, slug) = url.segments
            if (artist.isEmpty() || slug.isEmpty() || slug.equals("sets", ignoreCase = true)) return null
            return SoundCloudTrack("https://soundcloud.com/$artist/$slug")
        }

        /**
         * Audiomack SONG page: `audiomack.com/{artist}/song/{slug}`. Albums
         * and playlists use /album/ and /playlist/ segments and never match.
         */
        private fun audiomackTrack(url: UrlParts): AudiomackTrack? {
            val isAudiomack = url.host == "audiomack.com" || url.host.endsWith(".audiomack.com")
            if (!isAudiomack) return null
            if (url.segments.size != 3 || !url.segments[1].equals("song", ignoreCase = true)) return null
            val (artist, _, slug) = url.segments
            if (artist.isEmpty() || slug.isEmpty()) return null
            return AudiomackTrack("https://audiomack.com/$artist/song/$slug")
        }

        /**
         * TIDAL entity id from `tidal.com/[browse/]<entity>/<id>[/u]`.
         * Numeric ids; playlists/mixes/videos use different path entities and
         * never match. Handles listen.tidal.com and the /u share suffix.
         */
        private fun tidalId(url: UrlParts, entity: String): String? {
            val isTidal = url.host == "tidal.com" || url.host.endsWith(".tidal.com")
            if (!isTidal) return null
            val index = url.segments.indexOfFirst { it.equals(entity, ignoreCase = true) }
            if (index == -1 || index + 1 >= url.segments.size) return null
            val id = url.segments[index + 1]
            if (id.isEmpty() || !id.all { it.isDigit() }) return null
            return id
        }

        /**
         * Deezer entity id from `deezer.com/[<locale>/]<entity>/<id>`.
         * Deezer podcasts use /show/ and /episode/ paths and never match.
         */
        private fun deezerId(url: UrlParts, entity: String): String? {
            val isDeezer = url.host == "deezer.com" || url.host.endsWith(".deezer.com")
            if (!isDeezer) return null
            val index = url.segments.indexOfFirst { it.equals(entity, ignoreCase = true) }
            if (index == -1 || index + 1 >= url.segments.size) return null
            val id = url.segments[index + 1]
            if (id.isEmpty() || !id.all { it.isDigit() }) return null
            return id
        }
    }

    /**
     * Minimal pure-JVM URL decomposition (host lowercased, path segments,
     * query map). `java.net.URI` rather than `android.net.Uri` so the parser
     * unit-tests without Robolectric.
     */
    internal data class UrlParts(
        val host: String,
        val segments: List<String>,
        val query: Map<String, String>,
    ) {
        companion object {
            fun from(raw: String): UrlParts? {
                val uri = try {
                    URI(raw.trim())
                } catch (_: Exception) {
                    return null
                }
                val host = uri.host?.lowercase() ?: return null
                val segments = (uri.rawPath ?: "")
                    .split('/')
                    .filter { it.isNotEmpty() }
                val query = (uri.rawQuery ?: "")
                    .split('&')
                    .filter { it.isNotEmpty() }
                    .mapNotNull { pair ->
                        val eq = pair.indexOf('=')
                        if (eq <= 0) null else pair.substring(0, eq) to pair.substring(eq + 1)
                    }
                    .toMap()
                return UrlParts(host, segments, query)
            }
        }
    }
}
