package fm.corus.android.ui.screens.feed

import android.app.Application
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import fm.corus.android.data.model.FeedFilter
import fm.corus.android.domain.FeedNewTabPolicy
import fm.corus.android.ui.theme.CorusTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class, qualifiers = "w393dp-h852dp")
class FeedNewTabTest {
    @get:Rule val compose = createComposeRule()

    @Test fun `flag off retains old tabs while on includes New without favorites`() {
        assertEquals(listOf("following", "tasteMatches", "trending", "favorites"),
            visibleFeedModeTabs(true, true, true, 1, prototypeEnabled = true))
        assertEquals(listOf("following", "tasteMatches", "trending", "newReleases"),
            visibleFeedModeTabs(true, true, true, 0, prototypeEnabled = true, newTabEnabled = true))
        assertEquals(listOf("following", "trending", "tasteMatches", "newReleases"),
            visibleFeedModeTabs(true, false, false, 0, newTabEnabled = true))
    }
    @Test fun `New layout is independent of For You and old availability gates`() {
        assertEquals(listOf("following", "trending", "tasteMatches", "newReleases"),
            visibleFeedModeTabs(false, false, false, 0, prototypeEnabled = false, newTabEnabled = true))
        assertEquals(listOf("following", "tasteMatches", "trending", "newReleases"),
            visibleFeedModeTabs(false, false, false, 0, prototypeEnabled = true, newTabEnabled = true))
        assertEquals(listOf("following"),
            visibleFeedModeTabs(false, false, false, 0, prototypeEnabled = false, newTabEnabled = false))
    }
    @Test fun `flag off removes New request policy and preserves release filters`() {
        assertEquals("following", FeedNewTabPolicy.resolveMode("newReleases", false))
        assertFalse(FeedNewTabPolicy.isNew("newReleases", false))
        assertFalse(FeedNewTabPolicy.newReleasesOnly("following", false, FeedFilter.ALL))
        assertTrue(FeedNewTabPolicy.newReleasesOnly("trending", false, FeedFilter.MUSIC_NEW_RELEASES))
        assertEquals("favorites", FeedNewTabPolicy.pageMode("favorites", false))
        assertEquals("following", FeedNewTabPolicy.pageMode("favorites", true))
        assertEquals("following", FeedNewTabPolicy.followingMode("favorites", false))
    }
    @Test fun `all media choices force releases only within New`() {
        FeedFilter.entries.forEach { filter ->
            assertTrue(FeedNewTabPolicy.newReleasesOnly("newReleases", true, filter))
            assertEquals(filter.mediaType, FeedNewTabPolicy.mediaFilter(filter, true).mediaType)
            assertFalse(FeedNewTabPolicy.mediaFilter(filter, true).newReleasesOnly)
        }
        assertEquals("trending", FeedNewTabPolicy.wireMode("newReleases", true))
    }
    @Test fun `text opens selector repeatedly and flag off restores ordinary tab tap`() {
        var enabled by mutableStateOf(true)
        var following by mutableStateOf("following")
        var selections = 0
        compose.setContent {
            CorusTheme {
                FeedModeTabBar(listOf("following", "trending", "newReleases"), "following", 0f,
                    { selections++ }, followingMode = following,
                    showsFollowingChoices = enabled, onPickFollowingMode = { following = it })
            }
        }
        compose.onNodeWithText("Following", useUnmergedTree = true).performClick()
        compose.onNodeWithText("Favorites").assertIsDisplayed().performClick()
        compose.runOnIdle { assertEquals("favorites", following); assertEquals(0, selections) }
        compose.onNodeWithText("Favorites", useUnmergedTree = true).performClick()
        compose.onNodeWithText("Following").assertIsDisplayed().performClick()
        compose.onNodeWithText("Following", useUnmergedTree = true).performClick()
        compose.onNodeWithText("Favorites").assertIsDisplayed()
        compose.runOnIdle { enabled = false }
        compose.onNodeWithText("Favorites").assertDoesNotExist()
        compose.onNodeWithText("Following", useUnmergedTree = true).performClick()
        compose.runOnIdle { assertEquals(1, selections) }
    }
}
