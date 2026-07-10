package fm.corus.android.data.model

import fm.corus.android.ui.navigation.AlbumPageRoute
import fm.corus.android.ui.navigation.ArtistPageRoute
import fm.corus.android.ui.navigation.DirectorPageRoute
import fm.corus.android.ui.navigation.FilmDetailRoute
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [RecentSearchItem] backs the Search screen's "Recent" list, which now
 * remembers any tapped result (not just users). These tests pin the two things
 * PreferencesDataStore relies on: the polymorphic `kind`-keyed round-trip, and
 * the backward-compat fallback that keeps recents saved by older (users-only)
 * builds from being wiped on upgrade.
 */
class RecentSearchItemTest {

    // Mirrors PreferencesDataStore.recentSearchJson exactly.
    private val json = Json {
        ignoreUnknownKeys = true
        classDiscriminator = "kind"
    }

    @Test
    fun `dedupe keys are namespaced by kind`() {
        assertEquals("user:1", RecentSearchItem.UserEntry("1", "u", "U").dedupeKey)
        assertEquals("artist:1", RecentSearchItem.ArtistEntry("1", "A").dedupeKey)
        assertEquals("album:1", RecentSearchItem.AlbumEntry("1", "T").dedupeKey)
        assertEquals("song:1", RecentSearchItem.SongEntry("1", "S", "Ar").dedupeKey)
        assertEquals("film:1", RecentSearchItem.FilmEntry("1", "F").dedupeKey)
        assertEquals("director:1", RecentSearchItem.DirectorEntry("1", "D").dedupeKey)
        assertEquals("hashtag:jazz", RecentSearchItem.HashtagEntry("jazz").dedupeKey)
        // Same id across kinds must not collide (dedupe/remove operate on the key).
        assertTrue(
            RecentSearchItem.ArtistEntry("x", "A").dedupeKey !=
                RecentSearchItem.AlbumEntry("x", "T").dedupeKey,
        )
    }

    @Test
    fun `polymorphic json round-trips every kind`() {
        val items = listOf<RecentSearchItem>(
            RecentSearchItem.UserEntry("u1", "gabe", "Gabe", isVerified = true),
            RecentSearchItem.ArtistEntry("a1", "Modest Mouse", "http://img"),
            RecentSearchItem.AlbumEntry("al1", "Moon", "Modest Mouse", "http://cover", 2000),
            RecentSearchItem.SongEntry("s1", "Float On", "Modest Mouse", albumId = "al1", isrc = "X"),
            RecentSearchItem.FilmEntry("tmdb_1", "Inception", "Nolan", "2010", "http://poster"),
            RecentSearchItem.DirectorEntry("d1", "Nolan"),
            RecentSearchItem.HashtagEntry("nowplaying"),
        )
        val raw = json.encodeToString(items)
        assertEquals(items, json.decodeFromString<List<RecentSearchItem>>(raw))
        assertTrue("discriminator key must be `kind`", raw.contains("\"kind\":\"artist\""))
    }

    @Test
    fun `legacy users-only json (no kind) forces the fallback path`() {
        // Exactly what old builds wrote: an array of user objects, no `kind`.
        val legacyRaw = """[{"id":"u1","username":"gabe","displayName":"Gabe","isVerified":true}]"""
        var polymorphicFailed = false
        try {
            json.decodeFromString<List<RecentSearchItem>>(legacyRaw)
        } catch (_: Exception) {
            polymorphicFailed = true
        }
        assertTrue("legacy array must NOT decode polymorphically", polymorphicFailed)

        // PreferencesDataStore falls back to the legacy shape, which DOES read —
        // proving existing recents survive the upgrade.
        val legacy = json.decodeFromString<List<LegacyUserEntryProbe>>(legacyRaw)
        assertEquals(1, legacy.size)
        assertEquals("gabe", legacy[0].username)
        assertTrue(legacy[0].isVerified)
    }

    /** Same fields/defaults as the private PreferencesDataStore.LegacyRecentUserEntry. */
    @Serializable
    private data class LegacyUserEntryProbe(
        val id: String,
        val username: String = "",
        val displayName: String = "",
        val isVerified: Boolean = false,
        val isClubMember: Boolean = false,
        val isBot: Boolean = false,
        val profileFlair: String = "checkmark",
    )

    @Test
    fun `converters rebuild navigation payloads`() {
        val artist = RecentSearchItem.fromArtist(ArtistPageRoute("a1", "Modest Mouse", "img"))
        assertEquals(ArtistPageRoute("a1", "Modest Mouse", "img"), artist.toRoute())

        val album = RecentSearchItem.fromAlbum(AlbumPageRoute("al1", "Moon", "MM", "cover", 2000))
        assertEquals(AlbumPageRoute("al1", "Moon", "MM", "cover", 2000), album.toRoute())

        val director = RecentSearchItem.fromDirector(DirectorPageRoute("d1", "Nolan", null))
        assertEquals(DirectorPageRoute("d1", "Nolan", null), director.toRoute())

        val film = RecentSearchItem.fromFilm(FilmDetailRoute("m1", "Inception", "Nolan", "2010", posterURL = "p"))
        assertEquals("m1", film.toRoute().movieId)
        assertEquals("Inception", film.toRoute().movieTitle)
        assertEquals("2010", film.toRoute().releaseYear)

        // A recent song must carry enough to rebuild its SongDetailRoute.
        val track = CymbalTrack(
            id = "s1", name = "Float On", artistName = "MM", albumName = "GN",
            albumId = "al1", isrc = "X", artistIds = listOf("a1"),
        )
        val route = RecentSearchItem.fromTrack(track).toTrack().toSongDetailRoute()
        assertEquals("s1", route.trackId)
        assertEquals("al1", route.albumId)
        assertEquals("X", route.isrc)
        assertEquals("a1", route.artistId)
    }

    @Test
    fun `blank hints degrade to null in rebuilt routes`() {
        val artist = RecentSearchItem.fromArtist(ArtistPageRoute("a1", null, null))
        assertEquals("", artist.name)
        assertNull(artist.toRoute().name)
    }
}
