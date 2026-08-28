package fm.corus.android.data.model

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class AlbumTitleMatchTest {

    @Before
    fun clearTrendingDestCache() {
        TrendingAlbumDestinationCache.clear()
    }

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
    fun editionSuffixIsTheSameAlbum() {
        assertTrue(albumTitlesMatch("Jolene", "Jolene (Expanded Edition)"))
        assertTrue(albumTitlesMatch("CRASH", "CRASH (Deluxe)"))
        assertFalse(trendingAlbumShouldResolveByName("Jolene", "Jolene (Expanded Edition)"))
    }

    private fun railAlbum(
        albumId: String = "2v2iR6vtrLVTyiNIEsv5Sg",
        name: String = "Jolene",
        artist: String = "Dolly Parton",
    ) = TrendingAlbum(
        id = "id:$albumId",
        rank = 1,
        albumId = albumId,
        albumName = name,
        artistName = artist,
        cymbalCount = 7,
    )

    @Test
    fun trendingRailRowHasNoTrack() {
        assertTrue(isTrendingAlbumRailRow(railAlbum()))
        assertFalse(
            isTrendingAlbumRailRow(
                TrendingAlbum(
                    id = "track:t1",
                    rank = 1,
                    albumId = "single-id",
                    albumName = "GET MEAN",
                    artistName = "Boy Harsher",
                    cymbalCount = 1,
                    openAsSong = true,
                    trackId = "t1",
                    trackName = "Tough Luck",
                ),
            ),
        )
    }

    @Test
    fun trendingTapNeverErrorsWhenAlbumIdIsPresent() {
        val dest = guaranteeTrendingAlbumTap(railAlbum(), null)
        assertTrue(dest is TrendingAlbumOpen.Album)
        assertEquals("2v2iR6vtrLVTyiNIEsv5Sg", (dest as TrendingAlbumOpen.Album).albumId)
        assertEquals("Jolene", dest.title)
        assertEquals("Dolly Parton", dest.artist)
    }

    @Test
    fun trendingTapLeavesAPostedSongOnItsSongDest() {
        val songRow = TrendingAlbum(
            id = "track:t1",
            rank = 1,
            albumId = "tough-luck-single-id",
            albumName = "GET MEAN",
            artistName = "Boy Harsher",
            cymbalCount = 1,
            openAsSong = false,
            trackId = "t1",
            trackName = "Tough Luck",
        )
        val song = requireNotNull(songRow.asSongTrack())
        val dest = guaranteeTrendingAlbumTap(songRow, TrendingAlbumOpen.Song(song))
        assertTrue(dest is TrendingAlbumOpen.Song)
        assertEquals("t1", (dest as TrendingAlbumOpen.Song).track.id)
    }

    @Test
    fun trendingTapRewritesASongDestToTheStampedAlbum() {
        val album = railAlbum()
        val songRow = album.copy(trackId = "t1", trackName = "Jolene")
        val song = requireNotNull(songRow.asSongTrack())
        val dest = guaranteeTrendingAlbumTap(album, TrendingAlbumOpen.Song(song))
        assertTrue(dest is TrendingAlbumOpen.Album)
        assertEquals(album.albumId, (dest as TrendingAlbumOpen.Album).albumId)
    }

    @Test
    fun trendingTapKeepsAResolvedAlbumDest() {
        val dest = guaranteeTrendingAlbumTap(
            railAlbum(),
            TrendingAlbumOpen.Album(
                albumId = "am:1062400323",
                title = "Jolene",
                artist = "Dolly Parton",
                coverUrl = null,
            ),
        )
        assertTrue(dest is TrendingAlbumOpen.Album)
        assertEquals("am:1062400323", (dest as TrendingAlbumOpen.Album).albumId)
    }

    @Test
    fun trendingTapCannotInventAnId() {
        assertEquals(null, guaranteeTrendingAlbumTap(railAlbum(albumId = ""), null))
    }

    @Test
    fun catalogThrowStillOpensAnAlbumPage() = runBlocking {
        val dest = resolveTrendingAlbumOpen(
            album = railAlbum(),
            fetchCatalog = { _, _, _ -> throw RuntimeException("catalog miss") },
            resolveByName = { _, _ -> null },
            today = "2026-08-28",
        )
        assertTrue(dest is TrendingAlbumOpen.Album)
        assertEquals("2v2iR6vtrLVTyiNIEsv5Sg", (dest as TrendingAlbumOpen.Album).albumId)
    }

    @Test
    fun editionSuffixCatalogStillOpensAnAlbumPage() = runBlocking {
        val dest = resolveTrendingAlbumOpen(
            album = railAlbum(),
            fetchCatalog = { _, _, _ ->
                AlbumCatalog(
                    id = "2v2iR6vtrLVTyiNIEsv5Sg",
                    title = "Jolene (Expanded Edition)",
                    artistName = "Dolly Parton",
                    releaseDate = "1974-02-04",
                )
            },
            resolveByName = { _, _ -> error("should not resolve by name") },
            today = "2026-08-28",
        )
        assertTrue(dest is TrendingAlbumOpen.Album)
        assertTrue((dest as TrendingAlbumOpen.Album).albumId.isNotEmpty())
    }

    @Test
    fun mismatchedCatalogAndNameMissStillOpensAnAlbumPage() = runBlocking {
        val dest = resolveTrendingAlbumOpen(
            album = railAlbum(),
            fetchCatalog = { _, _, _ ->
                AlbumCatalog(
                    id = "2v2iR6vtrLVTyiNIEsv5Sg",
                    title = "The Essential Dolly Parton",
                    artistName = "Dolly Parton",
                    releaseDate = "2005-01-01",
                )
            },
            resolveByName = { _, _ -> null },
            today = "2026-08-28",
        )
        assertTrue(dest is TrendingAlbumOpen.Album)
        assertEquals("2v2iR6vtrLVTyiNIEsv5Sg", (dest as TrendingAlbumOpen.Album).albumId)
    }

    @Test
    fun destCacheKeyPrefersAlbumId() {
        assertEquals("id:2v2iR6vtrLVTyiNIEsv5Sg", trendingAlbumDestinationCacheKey(railAlbum()))
        assertEquals("name:jolene|dolly parton", trendingAlbumDestinationCacheKey(railAlbum(albumId = "")))
    }

    @Test
    fun destCacheServesARepeatTapWithoutRefetching() = runBlocking {
        var fetches = 0
        val first = resolveTrendingAlbumOpen(
            album = railAlbum(),
            fetchCatalog = { _, _, _ ->
                fetches += 1
                AlbumCatalog(
                    id = "2v2iR6vtrLVTyiNIEsv5Sg",
                    title = "Jolene",
                    artistName = "Dolly Parton",
                    releaseDate = "1974-02-04",
                )
            },
            resolveByName = { _, _ -> error("should not resolve by name") },
            today = "2026-08-28",
        )
        assertEquals(1, fetches)
        assertTrue(TrendingAlbumDestinationCache.peek(railAlbum()) is TrendingAlbumOpen.Album)
        val second = resolveTrendingAlbumOpen(
            album = railAlbum(),
            fetchCatalog = { _, _, _ -> error("repeat tap must not refetch") },
            resolveByName = { _, _ -> error("repeat tap must not resolve by name") },
            today = "2026-08-28",
        )
        assertEquals(first, second)
        assertEquals(1, fetches)
    }

    @Test
    fun unreleasedCatalogWithoutASongStillOpensAnAlbumPage() = runBlocking {
        val dest = resolveTrendingAlbumOpen(
            album = railAlbum(),
            fetchCatalog = { _, _, _ ->
                AlbumCatalog(
                    id = "2v2iR6vtrLVTyiNIEsv5Sg",
                    title = "Jolene",
                    artistName = "Dolly Parton",
                    releaseDate = "2026-12-01",
                )
            },
            resolveByName = { _, _ -> null },
            today = "2026-08-28",
        )
        assertTrue(dest is TrendingAlbumOpen.Album)
        assertEquals("2v2iR6vtrLVTyiNIEsv5Sg", (dest as TrendingAlbumOpen.Album).albumId)
    }
}
