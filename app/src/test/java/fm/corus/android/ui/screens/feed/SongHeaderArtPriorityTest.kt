package fm.corus.android.ui.screens.feed

import fm.corus.android.data.model.CymbalTrack
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Regression tests for the song-page header swapping to a different album
 * cover once posts load.
 *
 * Repro (Kelela "point blank"): the Apple-primary search row carried the
 * current "new avatar" album art; the first poster's snapshot carried the
 * June single's art for the same recording (same ISRC, different pressing).
 * The header painted the tapped art, then visibly swapped to the older cover
 * when the first post loaded.
 *
 * Rule (iOS parity — its header renders the immutable seed track): the
 * art/title/artist of the row the user tapped (route) always win; loaded
 * posts only fill fields the route did not carry.
 */
class SongHeaderArtPriorityTest {

    @Test
    fun routeArtWinsOverFirstPostSnapshot() {
        assertEquals(
            "https://route/new-avatar-large.png",
            resolveSongHeaderArtUrl(
                routeLarge = "https://route/new-avatar-large.png",
                routeSmall = "https://route/new-avatar-small.png",
                postLarge = "https://post/single-large.png",
                postSmall = "https://post/single-small.png",
            ),
        )
    }

    @Test
    fun routeSmallArtStillBeatsPostArtWhenRouteLargeMissing() {
        assertEquals(
            "https://route/new-avatar-small.png",
            resolveSongHeaderArtUrl(
                routeLarge = null,
                routeSmall = "https://route/new-avatar-small.png",
                postLarge = "https://post/single-large.png",
                postSmall = "https://post/single-small.png",
            ),
        )
    }

    @Test
    fun postsFillArtWhenRouteCarriedNone() {
        assertEquals(
            "https://post/single-large.png",
            resolveSongHeaderArtUrl(
                routeLarge = null,
                routeSmall = null,
                postLarge = "https://post/single-large.png",
                postSmall = "https://post/single-small.png",
            ),
        )
    }

    @Test
    fun blankRouteArtDoesNotShadowPostArt() {
        assertEquals(
            "https://post/single-large.png",
            resolveSongHeaderArtUrl(
                routeLarge = "",
                routeSmall = "",
                postLarge = "https://post/single-large.png",
                postSmall = null,
            ),
        )
    }

    @Test
    fun noArtAnywhereResolvesNull() {
        assertEquals(
            null,
            resolveSongHeaderArtUrl(routeLarge = null, routeSmall = "", postLarge = null, postSmall = null),
        )
    }

    @Test
    fun routeTitleAndArtistWinOverPostSnapshot() {
        assertEquals("point blank", resolveSongHeaderText(route = "point blank", post = "point blank (single)"))
        assertEquals("Kelela", resolveSongHeaderText(route = "Kelela", post = "Kelela, someone else"))
    }

    @Test
    fun postsFillTitleWhenRouteLacksOne() {
        assertEquals("point blank", resolveSongHeaderText(route = null, post = "point blank"))
        assertEquals("point blank", resolveSongHeaderText(route = "", post = "point blank"))
    }

    @Test
    fun blankEverywhereResolvesNullTitle() {
        assertEquals(null, resolveSongHeaderText(route = "", post = " "))
    }

    // ── Album line: the tapped pressing's album name + year win over the first
    // post's snapshot (the album line reuses resolveSongHeaderText for both). For
    // Kelela "point blank": route = the "new avatar" album pressing; the first
    // post = the earlier "point blank" single.
    @Test
    fun routeAlbumNameAndYearWinOverFirstPost() {
        assertEquals("new avatar", resolveSongHeaderText(route = "new avatar", post = "point blank"))
        // Year is derived from the resolved release date, take(4).
        val year = (resolveSongHeaderText(route = "2026-07-10", post = "2026-06-01") ?: "").take(4)
        assertEquals("2026", year)
    }

    @Test
    fun albumNameFallsBackToPostWhenRouteLacksIt() {
        assertEquals("point blank", resolveSongHeaderText(route = null, post = "point blank"))
    }

    // ── Route threading: toSongDetailRoute must carry albumName so the song page
    // (album line + "Post Song" draft) can name the tapped pressing. A regression
    // that drops it silently reverts the album line to the first post's album.
    @Test
    fun toSongDetailRouteCarriesAlbumName() {
        val track = CymbalTrack(
            id = "am:1888561542",
            name = "point blank",
            artistName = "Kelela",
            albumName = "new avatar",
        )
        assertEquals("new avatar", track.toSongDetailRoute().albumName)
    }
}
