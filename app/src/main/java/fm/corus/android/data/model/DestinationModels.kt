package fm.corus.android.data.model

/**
 * Models for the artist / album / director destination pages (feature-flagged
 * behind `artist_pages_enabled`). Shapes mirror the backend callables exactly —
 * see Corus-Web `backend/functions/index.js` (getArtistDetail / getArtistPosts /
 * getAlbumCatalog / getAlbumPosts / getDirectorDetail / getDirectorPosts) and
 * the web client's `app/lib/artist-album.ts`. All `fromMap` parsers are
 * defensive: a missing/mistyped field degrades to a safe default instead of
 * throwing, matching the other cloud-payload models in this package.
 */

/** An artist row from `searchSongs`' opt-in `includeArtists` extension, and the
 *  director row from TMDB person search (same three fields). */
data class ArtistSummary(
    val id: String,
    val name: String,
    val imageUrl: String? = null,
) {
    companion object {
        fun fromMap(data: Map<String, Any?>): ArtistSummary? {
            val id = (data["id"] as? String)?.takeIf { it.isNotEmpty() } ?: return null
            return ArtistSummary(
                id = id,
                name = data["name"] as? String ?: "",
                imageUrl = (data["imageUrl"] as? String)?.ifEmpty { null },
            )
        }
    }
}

/** An album row from `searchSongs`' opt-in `includeAlbums` extension. `id` is a
 *  plain Spotify album id, ready for the album page. */
data class AlbumSearchSummary(
    val id: String,
    val title: String,
    val artistName: String,
    val year: Int? = null,
    val coverUrl: String? = null,
) {
    companion object {
        fun fromMap(data: Map<String, Any?>): AlbumSearchSummary? {
            val id = (data["id"] as? String)?.takeIf { it.isNotEmpty() } ?: return null
            return AlbumSearchSummary(
                id = id,
                title = data["title"] as? String ?: "",
                artistName = data["artistName"] as? String ?: "",
                year = (data["year"] as? Number)?.toInt(),
                coverUrl = (data["coverUrl"] as? String)?.ifEmpty { null },
            )
        }
    }
}

/** One album in an artist's discography (getArtistDetail `albums`).
 *  `albumType` is Spotify's album_type: "album" | "single" (covers singles AND
 *  EPs) | "compilation". `id` may be `am:`-prefixed for Apple-resolved albums. */
data class AlbumSummary(
    val id: String,
    val title: String,
    val year: Int? = null,
    val coverUrl: String? = null,
    val albumType: String = "album",
) {
    companion object {
        fun fromMap(data: Map<String, Any?>): AlbumSummary? {
            val id = (data["id"] as? String)?.takeIf { it.isNotEmpty() } ?: return null
            return AlbumSummary(
                id = id,
                title = data["title"] as? String ?: "",
                year = (data["year"] as? Number)?.toInt(),
                coverUrl = (data["coverUrl"] as? String)?.ifEmpty { null },
                albumType = (data["albumType"] as? String)?.ifEmpty { null } ?: "album",
            )
        }
    }
}

/** getArtistDetail response: artist object + top tracks + discography +
 *  music videos. */
data class ArtistDetail(
    val id: String,
    val name: String,
    val imageUrl: String? = null,
    val genres: List<String> = emptyList(),
    val topTracks: List<CymbalTrack> = emptyList(),
    val albums: List<AlbumSummary> = emptyList(),
    /** Empty on payloads written before the music-videos backend deploy. */
    val musicVideos: List<MusicVideo> = emptyList(),
    /** The Corus user manually linked to this catalog artist, or null when no
     *  link exists (backend omits the `corusUser` key entirely — the "is on
     *  Corus" badge only renders when this is non-null). */
    val corusUser: CorusUserLink? = null,
)

/** A Corus user account manually linked to a catalog artist (getArtistDetail
 *  `corusUser`). Drives the "{displayName} is on Corus" badge. The blue check
 *  renders for [isVerified] OR [isClubMember] (see UsernameWithFlair). */
data class CorusUserLink(
    val id: String,
    val username: String,
    val displayName: String,
    val avatarUrl: String? = null,
    val profileFlair: String? = null,
    val isVerified: Boolean = false,
    val isClubMember: Boolean = false,
    val isBot: Boolean = false,
) {
    companion object {
        fun fromMap(data: Map<String, Any?>): CorusUserLink? {
            val id = (data["id"] as? String)?.takeIf { it.isNotEmpty() } ?: return null
            return CorusUserLink(
                id = id,
                username = data["username"] as? String ?: "",
                displayName = data["displayName"] as? String ?: "",
                avatarUrl = (data["avatarUrl"] as? String)?.ifEmpty { null },
                profileFlair = (data["profileFlair"] as? String)?.ifEmpty { null },
                isVerified = data["isVerified"] as? Boolean ?: false,
                isClubMember = data["isClubMember"] as? Boolean ?: false,
                isBot = data["isBot"] as? Boolean ?: false,
            )
        }
    }
}

/** One music video (getArtistDetail `musicVideos`). Apple supplies metadata
 *  (video-frame thumbnail, title, year, duration); [youtubeId] — when the
 *  backend matched the official upload — drives full inline playback. The rail
 *  shows ONLY matched videos, so a null [youtubeId] entry is filtered out. */
data class MusicVideo(
    val id: String,
    val title: String,
    val year: Int? = null,
    val durationMs: Long = 0,
    val thumbnailUrl: String? = null,
    val youtubeId: String? = null,
) {
    /** "2023 · 4:38" — parts drop out when unknown. */
    val yearAndDurationLabel: String
        get() = buildList {
            year?.let { add(it.toString()) }
            if (durationMs > 0) {
                val total = durationMs / 1000
                add("%d:%02d".format(total / 60, total % 60))
            }
        }.joinToString(" · ")

    companion object {
        fun fromMap(data: Map<String, Any?>): MusicVideo? {
            val id = (data["id"] as? String)?.takeIf { it.isNotEmpty() } ?: return null
            val title = (data["title"] as? String)?.takeIf { it.isNotEmpty() } ?: return null
            return MusicVideo(
                id = id,
                title = title,
                year = (data["year"] as? Number)?.toInt(),
                durationMs = (data["durationMs"] as? Number)?.toLong() ?: 0L,
                thumbnailUrl = (data["thumbnailUrl"] as? String)?.ifEmpty { null },
                youtubeId = (data["youtubeId"] as? String)?.ifEmpty { null },
            )
        }
    }
}

/** getAlbumCatalog response: album header + full tracklist. `artistIds` is
 *  empty for `am:` (Apple-resolved) albums — artist renders as plain text. */
data class AlbumCatalog(
    val id: String,
    val title: String,
    val artistName: String,
    val artistIds: List<String> = emptyList(),
    val year: Int? = null,
    val coverUrl: String? = null,
    val tracks: List<CymbalTrack> = emptyList(),
)

/** getDirectorDetail response: TMDB person + filmography (films directed). */
data class DirectorDetail(
    val id: String,
    val name: String,
    val imageUrl: String? = null,
    val knownFor: String = "",
    val biography: String = "",
    val films: List<DirectorFilm> = emptyList(),
    /** Official YouTube trailers for the director's films (newest-first), in the
     *  same MusicVideo shape as artist [musicVideos] so the trailers rail reuses
     *  the music-video card/player. Empty on payloads before the trailers deploy. */
    val trailers: List<MusicVideo> = emptyList(),
)

/** One directed film. `id` is `tmdb_{id}` — ready for the film detail route. */
data class DirectorFilm(
    val id: String,
    val title: String,
    val year: Int? = null,
    val posterUrl: String? = null,
) {
    companion object {
        fun fromMap(data: Map<String, Any?>): DirectorFilm? {
            val id = (data["id"] as? String)?.takeIf { it.isNotEmpty() } ?: return null
            return DirectorFilm(
                id = id,
                title = data["title"] as? String ?: "",
                year = (data["year"] as? Number)?.toInt(),
                posterUrl = (data["posterUrl"] as? String)?.ifEmpty { null },
            )
        }
    }
}

/** Lightweight user for the "Shared by N people" avatar cluster. Note the
 *  backend key is `avatarUrl` (lowercase url), unlike the full user shape. */
data class UserLite(
    val id: String,
    val username: String,
    val displayName: String,
    val avatarUrl: String? = null,
    val flair: String? = null,
) {
    companion object {
        fun fromMap(data: Map<String, Any?>): UserLite? {
            val id = (data["id"] as? String)?.takeIf { it.isNotEmpty() } ?: return null
            return UserLite(
                id = id,
                username = data["username"] as? String ?: "",
                displayName = data["displayName"] as? String ?: "",
                avatarUrl = (data["avatarUrl"] as? String)?.ifEmpty { null },
                flair = (data["flair"] as? String)?.ifEmpty { null },
            )
        }
    }
}

/**
 * First credited name from a denormalized joined credit string ("Mount Kimbie,
 * Micachu") for artist/director page title hints — the page is keyed to ids[0],
 * so hinting the full join briefly flashes the wrong title. Only splits when
 * [idCount] says the string is genuinely a multi-person join, so a single
 * artist with commas in their name ("Tyler, The Creator") passes through whole.
 * Mirrors web `primaryNameHint` in app/lib/artist-album.ts.
 */
fun primaryNameHint(joinedName: String, idCount: Int): String {
    val name = joinedName.trim()
    if (idCount > 1) {
        val first = name.split(",").first().trim()
        return first.ifEmpty { name }
    }
    return name
}
