package fm.corus.android.data.model

import fm.corus.android.ui.navigation.AlbumPageRoute
import fm.corus.android.ui.navigation.ArtistPageRoute
import fm.corus.android.ui.navigation.DirectorPageRoute
import fm.corus.android.ui.navigation.FilmDetailRoute
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * One entry in the Search screen's "Recent" list. Historically recents were
 * users only; now ANY tapped search result — user, artist, album, song, film,
 * director, hashtag — is remembered, so this is a discriminated union that
 * carries just enough to render the row AND rebuild its navigation without a
 * network round-trip.
 *
 * Persisted to DataStore as a polymorphic JSON array keyed by a `kind`
 * discriminator (see [fm.corus.android.data.local.PreferencesDataStore]).
 * Mirrors iOS `RecentSearchItem` and web `RecentSearchEntry`.
 */
@Serializable
sealed class RecentSearchItem {
    /** Stable identity within a kind. */
    abstract val id: String

    /** "kind:id" — unique across kinds, so ids can't collide on dedupe/remove. */
    abstract val dedupeKey: String

    @Serializable
    @SerialName("user")
    data class UserEntry(
        override val id: String,
        val username: String,
        val displayName: String,
        val avatarURL: String? = null,
        val avatarThumbURL: String? = null,
        val isVerified: Boolean = false,
        val isClubMember: Boolean = false,
        val isBot: Boolean = false,
        val profileFlair: String = "checkmark",
    ) : RecentSearchItem() {
        override val dedupeKey: String get() = "user:$id"

        fun toUser() = CymbalUser(
            id = id,
            username = username,
            displayName = displayName,
            avatarURL = avatarURL,
            avatarThumbURL = avatarThumbURL,
            isVerified = isVerified,
            isClubMember = isClubMember,
            isBot = isBot,
            profileFlair = profileFlair,
        )
    }

    @Serializable
    @SerialName("artist")
    data class ArtistEntry(
        override val id: String,
        val name: String,
        val imageUrl: String? = null,
    ) : RecentSearchItem() {
        override val dedupeKey: String get() = "artist:$id"

        fun toRoute() = ArtistPageRoute(artistId = id, name = name.ifBlank { null }, imageUrl = imageUrl)
    }

    @Serializable
    @SerialName("album")
    data class AlbumEntry(
        override val id: String,
        val title: String,
        val artistName: String = "",
        val coverUrl: String? = null,
        val year: Int? = null,
    ) : RecentSearchItem() {
        override val dedupeKey: String get() = "album:$id"

        fun toRoute() = AlbumPageRoute(
            albumId = id,
            title = title.ifBlank { null },
            artist = artistName.ifBlank { null },
            coverUrl = coverUrl,
            year = year,
        )
    }

    @Serializable
    @SerialName("song")
    data class SongEntry(
        override val id: String,
        val name: String,
        val artistName: String,
        val albumName: String = "",
        val albumArtURL: String? = null,
        val albumArtLargeURL: String? = null,
        val spotifyURI: String = "",
        val spotifyWebURL: String = "",
        val previewUrl: String? = null,
        val source: String = "spotify",
        val soundcloudId: String? = null,
        val soundcloudPermalinkUrl: String? = null,
        val audiomackId: String? = null,
        val audiomackUrl: String? = null,
        val audiomackArtistUrl: String? = null,
        val audiomackAlbumUrl: String? = null,
        val isrc: String? = null,
        val artistIds: List<String> = emptyList(),
        val albumId: String? = null,
        val releaseDate: String? = null,
        val releaseDatePrecision: String? = null,
    ) : RecentSearchItem() {
        override val dedupeKey: String get() = "song:$id"

        /** Rebuild a [CymbalTrack] rich enough to navigate to the song page
         *  (see [CymbalTrack.toSongDetailRoute]). */
        fun toTrack() = CymbalTrack(
            id = id,
            name = name,
            artistName = artistName,
            artistIds = artistIds,
            albumName = albumName,
            albumId = albumId,
            albumArtURL = albumArtURL,
            albumArtLargeURL = albumArtLargeURL,
            spotifyURI = spotifyURI,
            spotifyWebURL = spotifyWebURL,
            previewUrl = previewUrl,
            isrc = isrc,
            releaseDate = releaseDate,
            releaseDatePrecision = releaseDatePrecision,
            source = TrackSource.fromRaw(source),
            soundcloudId = soundcloudId,
            soundcloudPermalinkUrl = soundcloudPermalinkUrl,
            audiomackId = audiomackId,
            audiomackUrl = audiomackUrl,
            audiomackArtistUrl = audiomackArtistUrl,
            audiomackAlbumUrl = audiomackAlbumUrl,
        )
    }

    @Serializable
    @SerialName("film")
    data class FilmEntry(
        override val id: String,
        val title: String,
        val directorName: String? = null,
        val releaseYear: String? = null,
        val posterURL: String? = null,
        val posterLargeURL: String? = null,
        val trailerURL: String? = null,
        val movieReleaseDate: String? = null,
    ) : RecentSearchItem() {
        override val dedupeKey: String get() = "film:$id"

        fun toRoute() = FilmDetailRoute(
            movieId = id,
            movieTitle = title.ifBlank { null },
            directorName = directorName,
            releaseYear = releaseYear,
            posterURL = posterURL,
            posterLargeURL = posterLargeURL,
            trailerURL = trailerURL,
            movieReleaseDate = movieReleaseDate,
        )
    }

    @Serializable
    @SerialName("director")
    data class DirectorEntry(
        override val id: String,
        val name: String,
        val imageUrl: String? = null,
    ) : RecentSearchItem() {
        override val dedupeKey: String get() = "director:$id"

        fun toRoute() = DirectorPageRoute(directorId = id, name = name.ifBlank { null }, imageUrl = imageUrl)
    }

    @Serializable
    @SerialName("hashtag")
    data class HashtagEntry(
        /** The tag name (lowercased, without the leading `#`). */
        override val id: String,
    ) : RecentSearchItem() {
        override val dedupeKey: String get() = "hashtag:$id"

        val tag: String get() = id
    }

    companion object {
        fun fromUser(u: CymbalUser) = UserEntry(
            id = u.id,
            username = u.username,
            displayName = u.displayName,
            avatarURL = u.avatarURL,
            avatarThumbURL = u.avatarThumbURL,
            isVerified = u.isVerified,
            isClubMember = u.isClubMember,
            isBot = u.isBot,
            profileFlair = u.profileFlair,
        )

        fun fromArtist(r: ArtistPageRoute) =
            ArtistEntry(id = r.artistId, name = r.name ?: "", imageUrl = r.imageUrl)

        fun fromAlbum(r: AlbumPageRoute) = AlbumEntry(
            id = r.albumId,
            title = r.title ?: "",
            artistName = r.artist ?: "",
            coverUrl = r.coverUrl,
            year = r.year,
        )

        fun fromDirector(r: DirectorPageRoute) =
            DirectorEntry(id = r.directorId, name = r.name ?: "", imageUrl = r.imageUrl)

        fun fromFilm(r: FilmDetailRoute) = FilmEntry(
            id = r.movieId,
            title = r.movieTitle ?: "",
            directorName = r.directorName,
            releaseYear = r.releaseYear,
            posterURL = r.posterURL,
            posterLargeURL = r.posterLargeURL,
            trailerURL = r.trailerURL,
            movieReleaseDate = r.movieReleaseDate,
        )

        fun fromTrack(t: CymbalTrack) = SongEntry(
            id = t.id,
            name = t.name,
            artistName = t.artistName,
            albumName = t.albumName,
            albumArtURL = t.albumArtURL,
            albumArtLargeURL = t.albumArtLargeURL,
            spotifyURI = t.spotifyURI,
            spotifyWebURL = t.spotifyWebURL,
            previewUrl = t.previewUrl,
            source = t.source.raw,
            soundcloudId = t.soundcloudId,
            soundcloudPermalinkUrl = t.soundcloudPermalinkUrl,
            audiomackId = t.audiomackId,
            audiomackUrl = t.audiomackUrl,
            audiomackArtistUrl = t.audiomackArtistUrl,
            audiomackAlbumUrl = t.audiomackAlbumUrl,
            isrc = t.isrc,
            artistIds = t.artistIds,
            albumId = t.albumId,
            releaseDate = t.releaseDate,
            releaseDatePrecision = t.releaseDatePrecision,
        )

        fun fromHashtag(tag: String) = HashtagEntry(id = tag)
    }
}
