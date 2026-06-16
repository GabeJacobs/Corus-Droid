package fm.corus.android.ui.screens.feed

import android.app.Application
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import fm.corus.android.data.model.CymbalPost
import fm.corus.android.data.model.CymbalTrack
import fm.corus.android.data.model.CymbalUser
import fm.corus.android.data.model.MediaType
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate
import java.time.ZoneOffset
import kotlin.math.abs

/**
 * Regression tests for the "Posted by N users" rows on the song/film detail
 * screens. Two things must hold (matching iOS and Web):
 *
 *  1. The gold "1ST" first-poster trophy sits next to the prominent *@username*
 *     line (the rows lead with the username, display name muted below), not the
 *     display-name line.
 *  2. The purple "NEW RELEASE" pill never appears in this poster list — it is
 *     only meant to show on a post itself.
 *
 * The fixtures deliberately use a release date of "today" so a regression that
 * re-wires `isNewRelease` into the row would actually render the pill and fail.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class PostedByRowBadgeTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val todayIso: String = LocalDate.now(ZoneOffset.UTC).toString()

    private val firstPoster = CymbalUser(
        id = "u1",
        username = "alice",
        displayName = "Alice Anderson",
    )

    private fun songPost() = CymbalPost(
        id = "p1",
        user = firstPoster,
        track = CymbalTrack(
            id = "t1",
            name = "Track",
            artistName = "Artist",
            albumName = "Album",
            releaseDate = todayIso,
            releaseDatePrecision = "day",
        ),
        isFirstPoster = true,
        mediaType = MediaType.TRACK,
    )

    private fun filmPost() = CymbalPost(
        id = "p2",
        user = firstPoster,
        track = CymbalTrack(id = "t2", name = "n", artistName = "a", albumName = "al"),
        isFirstPoster = true,
        mediaType = MediaType.MOVIE,
        movieReleaseDate = todayIso,
    )

    /** The 1ST badge's vertical center must align with the @username line. */
    private fun assertTrophyOnUsernameLine() {
        // The row is clickable, which merges descendant semantics into one node;
        // query the unmerged tree so each text node reports its own bounds.
        val displayName = composeRule
            .onNodeWithText("Alice Anderson", useUnmergedTree = true).getUnclippedBoundsInRoot()
        val username = composeRule
            .onNodeWithText("@alice", useUnmergedTree = true).getUnclippedBoundsInRoot()
        val trophy = composeRule
            .onNodeWithText("1ST", useUnmergedTree = true).getUnclippedBoundsInRoot()

        val trophyCenterY = (trophy.top + trophy.bottom).value / 2f
        val nameCenterY = (displayName.top + displayName.bottom).value / 2f
        val usernameCenterY = (username.top + username.bottom).value / 2f

        assertTrue(
            "1ST badge (centerY=$trophyCenterY) should sit on the @username line " +
                "(centerY=$usernameCenterY), not the display-name line (centerY=$nameCenterY)",
            abs(trophyCenterY - usernameCenterY) < abs(trophyCenterY - nameCenterY),
        )
    }

    @Test
    fun `song row shows 1ST next to username and no new release pill`() {
        composeRule.setContent { PostedByRow(post = songPost()) }

        composeRule.onNodeWithText("1ST").assertExists()
        composeRule.onNodeWithText("NEW RELEASE").assertDoesNotExist()
        assertTrophyOnUsernameLine()
    }

    @Test
    fun `film row shows 1ST next to username and no new release pill`() {
        composeRule.setContent { FilmPostedByRow(post = filmPost()) }

        composeRule.onNodeWithText("1ST").assertExists()
        composeRule.onNodeWithText("NEW RELEASE").assertDoesNotExist()
        assertTrophyOnUsernameLine()
    }
}
