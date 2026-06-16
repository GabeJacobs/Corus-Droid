package fm.corus.android.ui.screens.feed

import android.app.Application
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import fm.corus.android.data.model.CymbalPost
import fm.corus.android.data.model.CymbalTrack
import fm.corus.android.data.model.CymbalUser
import fm.corus.android.data.model.MediaType
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Regression tests for the two-zone tap behavior on the "Posted by N users"
 * rows (matching iOS and Web):
 *
 *  - Tapping the row opens the poster's POST (onPostTap).
 *  - Tapping the display name (or avatar) opens their PROFILE (onUserTap).
 *  - A one-line caption snippet renders when present, and the legacy
 *    "Posted this" filler is never shown when the caption is empty.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class PostedByRowTapTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val poster = CymbalUser(
        id = "u1",
        username = "alice",
        displayName = "Alice Anderson",
    )

    private fun post(caption: String? = null) = CymbalPost(
        id = "p1",
        user = poster,
        track = CymbalTrack(id = "t1", name = "Track", artistName = "Artist", albumName = "Album"),
        caption = caption,
        mediaType = MediaType.TRACK,
    )

    @Test
    fun `tapping display name opens profile, not post`() {
        var userTapped = false
        var postTapped = false
        composeRule.setContent {
            PostedByRow(
                post = post(caption = "great track"),
                onUserTap = { userTapped = true },
                onPostTap = { postTapped = true },
            )
        }

        composeRule.onNodeWithText("Alice Anderson", useUnmergedTree = true).performClick()

        assertTrue("display name tap should open the profile", userTapped)
        assertFalse("display name tap should NOT open the post", postTapped)
    }

    @Test
    fun `tapping caption opens post, not profile`() {
        var userTapped = false
        var postTapped = false
        composeRule.setContent {
            PostedByRow(
                post = post(caption = "great track"),
                onUserTap = { userTapped = true },
                onPostTap = { postTapped = true },
            )
        }

        composeRule.onNodeWithText("great track", substring = true, useUnmergedTree = true)
            .performClick()

        assertTrue("caption tap should open the post", postTapped)
        assertFalse("caption tap should NOT open the profile", userTapped)
    }

    @Test
    fun `caption snippet renders when present`() {
        composeRule.setContent { PostedByRow(post = post(caption = "great track")) }

        composeRule.onNodeWithText("great track", substring = true, useUnmergedTree = true)
            .assertExists()
    }

    @Test
    fun `empty caption shows no caption text and no Posted this filler`() {
        composeRule.setContent { PostedByRow(post = post(caption = null)) }

        // The legacy filler must never come back.
        composeRule.onNodeWithText("Posted this", substring = true).assertDoesNotExist()
    }

    @Test
    fun `film row tapping caption opens post, not profile`() {
        var userTapped = false
        var postTapped = false
        composeRule.setContent {
            FilmPostedByRow(
                post = post(caption = "so good").copy(mediaType = MediaType.MOVIE),
                onUserTap = { userTapped = true },
                onPostTap = { postTapped = true },
            )
        }

        composeRule.onNodeWithText("so good", substring = true, useUnmergedTree = true)
            .performClick()

        assertTrue("caption tap should open the post", postTapped)
        assertFalse("caption tap should NOT open the profile", userTapped)
    }
}
