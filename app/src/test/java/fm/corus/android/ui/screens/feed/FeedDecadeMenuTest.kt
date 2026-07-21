package fm.corus.android.ui.screens.feed

import android.app.Application
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import fm.corus.android.data.model.FeedFilter
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class FeedDecadeMenuTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun header(
        menuOpen: Boolean = true,
        showDecadeFilter: Boolean = true,
        feedDecade: Int? = null,
        feedFilter: FeedFilter = FeedFilter.ALL,
        onSetDecade: (Int?) -> Unit = {},
    ) {
        composeRule.setContent {
            FeedHeader(
                showPlaylistButton = false,
                isGeneratingPlaylist = false,
                feedFilter = feedFilter,
                filterMenuExpanded = menuOpen,
                onFilterMenuExpandedChange = {},
                onSetFilter = {},
                onGeneratePlaylist = {},
                showDecadeFilter = showDecadeFilter,
                feedDecade = feedDecade,
                onSetDecade = onSetDecade,
            )
        }
    }

    @Test
    fun `the decade group is one drill-in row, not seven top-level rows`() {
        header()

        composeRule.onNodeWithText("Decade").assertIsDisplayed()
        composeRule.onNodeWithText("Music Only").assertIsDisplayed()
        composeRule.onNodeWithText("90s").assertDoesNotExist()
        composeRule.onNodeWithText("Any Decade").assertDoesNotExist()
    }

    @Test
    fun `drilling in swaps the panel for every offered decade, newest first`() {
        header()

        composeRule.onNodeWithText("Decade").performClick()

        composeRule.onNodeWithText("Any Decade").assertIsDisplayed()
        listOf("2020s", "2010s", "2000s", "90s", "80s", "70s", "60s").forEach {
            composeRule.onNodeWithText(it).assertIsDisplayed()
        }
        composeRule.onNodeWithText("Music Only").assertDoesNotExist()
        composeRule.onNodeWithText("New Music Releases").assertDoesNotExist()
    }

    @Test
    fun `picking a decade reports it as an integer decade`() {
        var picked: Int? = null
        var calls = 0
        header(onSetDecade = { picked = it; calls++ })

        composeRule.onNodeWithText("Decade").performClick()
        composeRule.onNodeWithText("90s").performClick()

        assertEquals(1, calls)
        assertEquals(1990, picked)
    }

    @Test
    fun `Any Decade clears the narrowing from inside the group`() {
        var picked: Int? = 1990
        var calls = 0
        header(feedDecade = 1990, onSetDecade = { picked = it; calls++ })

        composeRule.onNodeWithText("Decade · 90s").performClick()
        composeRule.onNodeWithText("Any Decade").performClick()

        assertEquals(1, calls)
        assertEquals(null, picked)
    }

    @Test
    fun `an active decade is legible in the header without opening the menu`() {
        header(menuOpen = false, feedDecade = 1990)

        composeRule.onNodeWithText("90s").assertIsDisplayed()
    }

    @Test
    fun `the group row names the active decade`() {
        header(feedDecade = 1970)

        composeRule.onNodeWithText("Decade · 70s").assertIsDisplayed()
    }

    @Test
    fun `no decade group and no header label while the gate is off`() {
        header(showDecadeFilter = false)

        composeRule.onNodeWithText("Music Only").assertIsDisplayed()
        composeRule.onNodeWithText("Decade").assertDoesNotExist()
    }

    @Test
    fun `an empty decade blames the decade and offers a way out, never the clock`() {
        var cleared = 0
        composeRule.setContent {
            FeedDecadeEmptyState(decade = 1990, onShowAllDecades = { cleared++ })
        }

        composeRule.onNodeWithText("Nothing from the 90s").assertIsDisplayed()
        composeRule.onNodeWithText("Check back", substring = true).assertDoesNotExist()
        composeRule.onNodeWithText("Show all decades").performClick()

        assertEquals(1, cleared)
    }
}
