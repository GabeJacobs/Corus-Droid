package fm.corus.android.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AlbumTitleMatchTest {

    private val joleneSmall =
        "https://is1-ssl.mzstatic.com/image/thumb/Music124/v4/0b/f8/1e/0bf81e88-de6e-aba3-9fd4-e7407bfbcc31/886445438048.jpg/100x100bb.jpg"
    private val joleneLarge =
        "https://is1-ssl.mzstatic.com/image/thumb/Music124/v4/0b/f8/1e/0bf81e88-de6e-aba3-9fd4-e7407bfbcc31/886445438048.jpg/640x640bb.jpg"

    @Test
    fun appleArtSizeSuffixesCollapseToOneKey() {
        assertEquals(normalizeAlbumArtKey(joleneSmall), normalizeAlbumArtKey(joleneLarge))
        assertTrue(normalizeAlbumArtKey(joleneSmall).endsWith("886445438048.jpg"))
    }

    @Test
    fun appleAndSpotifyJoleneDraftsCollapse() {
        val apple = NewReleaseAlbumDraft(
            id = "id:am:1062400323",
            releaseDate = "1974-02-04",
            songs = listOf(
                NewReleasePostedSong("t1", "Jolene", releaseDate = "1974-02-04", count = 2),
            ),
            album = TrendingAlbum(
                id = "id:am:1062400323",
                rank = 0,
                albumId = "am:1062400323",
                albumName = "Jolene",
                artistName = "Dolly Parton",
                albumArtURL = joleneSmall,
                albumArtLargeURL = joleneLarge,
                cymbalCount = 3,
            ),
        )
        val spotify = NewReleaseAlbumDraft(
            id = "id:2v2iR6vtrLVTyiNIEsv5Sg",
            releaseDate = "1974-02-04",
            songs = listOf(
                NewReleasePostedSong("t2", "Jolene", releaseDate = "1974-02-04", count = 1),
            ),
            album = TrendingAlbum(
                id = "id:2v2iR6vtrLVTyiNIEsv5Sg",
                rank = 0,
                albumId = "2v2iR6vtrLVTyiNIEsv5Sg",
                albumName = "Jolene",
                artistName = "Dolly Parton",
                albumArtURL = joleneSmall,
                albumArtLargeURL = joleneLarge,
                cymbalCount = 1,
            ),
        )
        val out = collapseNewReleaseAlbumDrafts(listOf(apple, spotify))
        assertEquals(1, out.size)
        assertEquals("am:1062400323", out[0].album.albumId)
        assertEquals(4, out[0].album.cymbalCount)
        assertEquals(2, out[0].songs.size)
    }

    @Test
    fun songRowsDoNotCollapseIntoAlbumRows() {
        val album = NewReleaseAlbumDraft(
            id = "id:am:1",
            releaseDate = "2026-08-01",
            songs = emptyList(),
            album = TrendingAlbum(
                id = "id:am:1",
                rank = 0,
                albumId = "am:1",
                albumName = "GET MEAN",
                artistName = "Boy Harsher",
                albumArtURL = joleneSmall,
                cymbalCount = 2,
            ),
        )
        val song = NewReleaseAlbumDraft(
            id = "track:t1",
            releaseDate = "2026-08-01",
            songs = emptyList(),
            album = TrendingAlbum(
                id = "track:t1",
                rank = 0,
                albumId = "am:1",
                albumName = "GET MEAN",
                artistName = "Boy Harsher",
                albumArtURL = joleneSmall,
                cymbalCount = 1,
                openAsSong = true,
                trackId = "t1",
                trackName = "Tough Luck",
            ),
        )
        val out = collapseNewReleaseAlbumDrafts(listOf(album, song))
        assertEquals(2, out.size)
        assertTrue(out.any { it.album.openAsSong })
    }

    @Test
    fun sameTitleDifferentArtStaysSplit() {
        val blue = NewReleaseAlbumDraft(
            id = "id:blue",
            releaseDate = "1994-05-10",
            songs = emptyList(),
            album = TrendingAlbum(
                id = "id:blue",
                rank = 0,
                albumId = "blue",
                albumName = "Weezer",
                artistName = "Weezer",
                albumArtURL = "https://example.com/blue.jpg",
                cymbalCount = 4,
            ),
        )
        val green = NewReleaseAlbumDraft(
            id = "id:green",
            releaseDate = "2001-05-15",
            songs = emptyList(),
            album = TrendingAlbum(
                id = "id:green",
                rank = 0,
                albumId = "green",
                albumName = "Weezer",
                artistName = "Weezer",
                albumArtURL = "https://example.com/green.jpg",
                cymbalCount = 3,
            ),
        )
        assertEquals(2, collapseNewReleaseAlbumDrafts(listOf(blue, green)).size)
    }

    @Test
    fun missingArtDoesNotCollapse() {
        assertEquals(
            null,
            albumReleaseCollapseKey("Jolene", "Dolly Parton", albumArtURL = null),
        )
    }

    @Test
    fun matchingTitlesTrustTheStampedId() {
        assertTrue(albumTitlesMatch("GET MEAN", "GET MEAN"))
        assertTrue(albumTitlesMatch("Blonde", "Blond"))
        assertFalse(trendingAlbumShouldResolveByName("GET MEAN", "GET MEAN"))
    }

    @Test
    fun newAlbumsRequireAReleasedMultiTrackCatalog() {
        assertTrue(
            albumCatalogQualifiesAsNewFullAlbum(
                isPreRelease = false,
                releaseDate = "2026-08-20",
                trackCount = 12,
                today = "2026-08-27",
            ),
        )
        assertFalse(
            albumCatalogQualifiesAsNewFullAlbum(
                isPreRelease = false,
                releaseDate = "2026-08-20",
                trackCount = 1,
                today = "2026-08-27",
            ),
        )
        assertFalse(
            albumCatalogQualifiesAsNewFullAlbum(
                isPreRelease = false,
                releaseDate = "2026-09-18",
                trackCount = 12,
                today = "2026-08-27",
            ),
        )
        assertTrue(newReleaseGroupLooksLikeSingle("Cellsword", listOf("Cellsword")))
        assertFalse(newReleaseGroupLooksLikeSingle("GNX", listOf("TV Off")))
    }
}
