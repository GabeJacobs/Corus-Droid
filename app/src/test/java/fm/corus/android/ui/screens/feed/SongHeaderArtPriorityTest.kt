package fm.corus.android.ui.screens.feed

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
}
