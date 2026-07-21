package fm.corus.android.ui.screens.destination

import fm.corus.android.data.model.primaryNameHint
import fm.corus.android.data.remote.TMDBPerson
import fm.corus.android.data.remote.parseAlbumCatalogResponse
import fm.corus.android.data.remote.parseAlbumPostsResponse
import fm.corus.android.data.remote.parseArtistDetailResponse
import fm.corus.android.data.remote.parseArtistIdByNameResponse
import fm.corus.android.data.remote.parseDestinationPostsResponse
import fm.corus.android.data.remote.parseDirectorDetailResponse
import fm.corus.android.data.repository.filterDirectorSearchResults
import fm.corus.android.data.repository.parseUnifiedTrack
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure parsing tests for the destination-page callable responses (kept
 * top-level in the data layer so no FirebaseFunctions mocking is needed),
 * plus the primaryNameHint rule and the director search filter.
 */
class DestinationParsingTest {

    // ── getArtistDetail ──

    @Test
    fun `parses artist detail with top tracks and albums`() {
        val detail = parseArtistDetailResponse(
            mapOf(
                "artist" to mapOf(
                    "id" to "spotify-artist-1",
                    "name" to "Mount Kimbie",
                    "imageUrl" to "https://img/x.jpg",
                    "genres" to listOf("electronic", "post-dubstep"),
                ),
                "topTracks" to listOf(
                    mapOf(
                        "id" to "t1",
                        "name" to "Made to Stray",
                        "artistName" to "Mount Kimbie",
                        "artistIds" to listOf("spotify-artist-1"),
                        "albumName" to "CSFLY",
                        "albumArtURL" to "https://img/a.jpg",
                        "durationMs" to 240000,
                        "spotifyURI" to "spotify:track:t1",
                        "releaseDate" to "2013-05-27",
                    ),
                ),
                "albums" to listOf(
                    mapOf("id" to "al1", "title" to "CSFLY", "year" to 2013, "albumType" to "album"),
                    mapOf("id" to "al2", "title" to "A Single", "year" to 2020, "albumType" to "single"),
                ),
            )
        )

        assertEquals("spotify-artist-1", detail?.id)
        assertEquals("Mount Kimbie", detail?.name)
        assertEquals(listOf("electronic", "post-dubstep"), detail?.genres)
        assertEquals(1, detail?.topTracks?.size)
        assertEquals("Made to Stray", detail?.topTracks?.first()?.name)
        assertEquals(listOf("spotify-artist-1"), detail?.topTracks?.first()?.artistIds)
        assertEquals(2, detail?.albums?.size)
        assertEquals("single", detail?.albums?.get(1)?.albumType)
    }

    @Test
    fun `artist detail parse returns null on a malformed payload`() {
        assertNull(parseArtistDetailResponse(null))
        assertNull(parseArtistDetailResponse(mapOf("artist" to "nope")))
        assertNull(parseArtistDetailResponse(mapOf("artist" to mapOf("name" to "No Id"))))
    }

    // ── getAlbumCatalog ──

    @Test
    fun `parses an apple-resolved album with empty artistIds`() {
        val catalog = parseAlbumCatalogResponse(
            mapOf(
                "album" to mapOf(
                    "id" to "am:12345",
                    "title" to "Divers",
                    "artistName" to "Joanna Newsom",
                    "artistIds" to emptyList<String>(),
                    "year" to 2015,
                    "coverUrl" to "https://img/c.jpg",
                ),
                "tracks" to listOf(
                    mapOf(
                        "id" to "am:t9",
                        "name" to "Sapokanikan",
                        "artistName" to "Joanna Newsom",
                        "source" to "applemusic",
                        "durationMs" to 311000,
                    ),
                ),
            )
        )

        assertEquals("am:12345", catalog?.id)
        assertTrue(catalog?.artistIds?.isEmpty() == true)
        assertEquals(1, catalog?.tracks?.size)
        assertEquals(
            fm.corus.android.data.model.TrackSource.APPLEMUSIC,
            catalog?.tracks?.first()?.source,
        )
    }

    // ── getDirectorDetail ──

    @Test
    fun `parses director detail with filmography`() {
        val detail = parseDirectorDetailResponse(
            mapOf(
                "director" to mapOf(
                    "id" to "1032",
                    "name" to "Martin Scorsese",
                    "imageUrl" to "https://img/p.jpg",
                    "knownFor" to "Directing",
                    "biography" to "…",
                ),
                "films" to listOf(
                    mapOf("id" to "tmdb_769", "title" to "GoodFellas", "year" to 1990, "posterUrl" to "https://img/g.jpg"),
                    mapOf("id" to "tmdb_111", "title" to "No Year"),
                ),
            )
        )

        assertEquals("1032", detail?.id)
        assertEquals(2, detail?.films?.size)
        assertEquals("tmdb_769", detail?.films?.first()?.id)
        assertNull(detail?.films?.get(1)?.year)
    }

    // ── getArtistPosts / getDirectorPosts ──

    @Test
    fun `parses destination posts with posters and viewer posts`() {
        val page = parseDestinationPostsResponse(
            mapOf(
                "posts" to listOf(
                    mapOf(
                        "id" to "p1",
                        "userId" to "u1",
                        "trackId" to "t1",
                        "trackName" to "Song",
                        "artistName" to "Artist",
                        "artistIds" to listOf("a1"),
                        "createdAt" to 1700000000000L,
                        "user" to mapOf("id" to "u1", "username" to "amy", "displayName" to "Amy"),
                    ),
                ),
                "uniquePosterCount" to 7,
                "posters" to listOf(
                    // Note the wire key is avatarUrl (lowercase url) on UserLite.
                    mapOf("id" to "u1", "username" to "amy", "displayName" to "Amy", "avatarUrl" to "https://a/1.jpg"),
                    mapOf("username" to "no-id-dropped"),
                ),
                "viewerPosts" to listOf(
                    mapOf(
                        "id" to "vp1",
                        "userId" to "viewer",
                        "trackId" to "t2",
                        "trackName" to "Mine",
                        "artistName" to "Artist",
                        "createdAt" to 1700000001000L,
                    ),
                ),
            )
        )

        assertEquals(1, page.posts.size)
        assertEquals(listOf("a1"), page.posts.first().track.artistIds)
        assertEquals(7, page.uniquePosterCount)
        assertEquals(1, page.posters.size)
        assertEquals("https://a/1.jpg", page.posters.first().avatarUrl)
        assertEquals(1, page.viewerPosts.size)
        assertEquals("vp1", page.viewerPosts.first().id)
    }

    @Test
    fun `destination posts parse degrades to empty on null payload`() {
        val page = parseDestinationPostsResponse(null)
        assertTrue(page.posts.isEmpty())
        assertEquals(0, page.uniquePosterCount)
    }

    // ── getAlbumPosts ──

    @Test
    fun `parses album posts with first poster id`() {
        val page = parseAlbumPostsResponse(
            mapOf(
                "posts" to listOf(
                    mapOf("id" to "p1", "userId" to "u1", "trackId" to "t1", "createdAt" to 1700000000000L),
                ),
                "uniquePosterCount" to 3,
                "firstPosterId" to "u1",
            )
        )
        assertEquals(1, page.posts.size)
        assertEquals(3, page.uniquePosterCount)
        assertEquals("u1", page.firstPosterId)
    }

    @Test
    fun `parses album posts per-track share counts (Int and Double)`() {
        val page = parseAlbumPostsResponse(
            mapOf(
                "posts" to emptyList<Map<String, Any?>>(),
                "uniquePosterCount" to 4,
                // Firebase callables deliver JS numbers as Int or Double.
                "trackShareCounts" to mapOf("t1" to 3, "t2" to 1.0),
            )
        )
        assertEquals(mapOf("t1" to 3, "t2" to 1), page.trackShareCounts)
    }

    @Test
    fun `album posts share counts default to empty when absent`() {
        val page = parseAlbumPostsResponse(
            mapOf("posts" to emptyList<Map<String, Any?>>(), "uniquePosterCount" to 0)
        )
        assertTrue(page.trackShareCounts.isEmpty())
    }

    // ── resolveArtistIdByName (song page artist-line fallback) ──

    @Test
    fun `artist id by name accepts only an exact case-insensitive trimmed match`() {
        val response = mapOf(
            "artists" to listOf(
                mapOf("id" to "a1", "name" to "  joanna newsom "),
            ),
        )
        assertEquals("a1", parseArtistIdByNameResponse(response, "Joanna Newsom"))
        // A near-miss must never resolve — no guessing.
        assertNull(parseArtistIdByNameResponse(response, "Joanna Newsom Band"))
    }

    @Test
    fun `artist id by name skips malformed rows and degrades to null`() {
        assertNull(parseArtistIdByNameResponse(null, "Tool"))
        assertNull(parseArtistIdByNameResponse(mapOf("artists" to "nope"), "Tool"))
        // Empty id and missing name rows are skipped, not matched.
        assertNull(
            parseArtistIdByNameResponse(
                mapOf(
                    "artists" to listOf(
                        mapOf("id" to "", "name" to "Tool"),
                        mapOf("id" to "a2"),
                    ),
                ),
                "Tool",
            )
        )
    }

    // ── albumId (song page album-line link) ──

    @Test
    fun `unified track parse carries albumId and treats empty as null`() {
        val base = mapOf(
            "id" to "t1",
            "name" to "Made to Stray",
            "artistName" to "Mount Kimbie",
        )
        assertEquals("al1", parseUnifiedTrack(base + mapOf("albumId" to "al1"))?.albumId)
        // Apple-sourced tracks ship no album id; "" and absent both → null.
        assertNull(parseUnifiedTrack(base + mapOf("albumId" to ""))?.albumId)
        assertNull(parseUnifiedTrack(base)?.albumId)
    }

    @Test
    fun `toSongDetailRoute carries the albumId hint`() {
        val track = fm.corus.android.data.model.CymbalTrack(
            id = "t1",
            name = "Made to Stray",
            artistName = "Mount Kimbie",
            albumName = "CSFLY",
            albumId = "al1",
        )
        assertEquals("al1", track.toSongDetailRoute().albumId)
        assertNull(track.copy(albumId = null).toSongDetailRoute().albumId)
    }

    // ── primaryNameHint ──

    @Test
    fun `primaryNameHint splits joined credits only for multi-id arrays`() {
        // Joined credit string with two ids → first credited name only.
        assertEquals("Mount Kimbie", primaryNameHint("Mount Kimbie, Micachu", 2))
        // Single artist with a comma IN the name must pass through whole.
        assertEquals("Tyler, The Creator", primaryNameHint("Tyler, The Creator", 1))
        // Unknown id count (0) behaves like a single credit.
        assertEquals("Tyler, The Creator", primaryNameHint("Tyler, The Creator", 0))
    }

    // ── Director search filter ──

    private fun person(id: Int, name: String, dept: String?) =
        TMDBPerson(id = id, name = name, knownForDepartment = dept)

    @Test
    fun `director filter requires a name match and prefers Directing`() {
        val people = listOf(
            person(1, "Mark Ibold", "Acting"), // pavement bassist — alias match, dropped
            person(2, "Sofia Coppola", "Directing"),
            person(3, "Sofia Coppola Fan", "Acting"),
        )
        val results = filterDirectorSearchResults(people, "sofia coppola")
        assertEquals(1, results.size)
        assertEquals("2", results.first().id)
    }

    @Test
    fun `director filter falls back to the single top name match`() {
        // Director-actors (e.g. Greta Gerwig, TMDB department "Acting") must
        // still surface when no Directing-department person matches.
        val people = listOf(
            person(10, "Greta Gerwig", "Acting"),
            person(11, "Greta Gerwig Lookalike", "Acting"),
        )
        val results = filterDirectorSearchResults(people, "greta gerwig")
        assertEquals(1, results.size)
        assertEquals("10", results.first().id)
    }

    @Test
    fun `director filter caps at three and maps image urls`() {
        val people = (1..5).map { person(it, "John Smith $it", "Directing") }
            .map { it.copy(profilePath = "/p.jpg") }
        val results = filterDirectorSearchResults(people, "john smith")
        assertEquals(3, results.size)
        assertEquals("https://image.tmdb.org/t/p/w185/p.jpg", results.first().imageUrl)
    }
}
