package fm.corus.android.ui.screens.search

import android.app.Application
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import fm.corus.android.data.model.CymbalUser
import fm.corus.android.data.model.MusicMatchData
import fm.corus.android.data.model.SuggestedUserMatch
import fm.corus.android.ui.LocalHapticManager
import fm.corus.android.ui.theme.CorusTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class, qualifiers = "w411dp-h915dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class FeaturedTasteMatchSearchTest {
    @get:Rule val compose = createComposeRule()
    private val ranked = (1..3).map {
        SuggestedUserMatch(CymbalUser(id = "$it", username = "listener$it", displayName = "Listener $it"),
            MusicMatchData(sharedArtistNames = listOf("Artist $it")))
    }
    private var profiles = 0
    private var follows = 0
    private var loads = 0

    private fun render(featured: Boolean = true, hideFirst: Boolean = false, count: Int = 3, hasMore: Boolean = false) {
        val followed = mutableStateOf(emptySet<String>())
        compose.setContent {
            CompositionLocalProvider(LocalHapticManager provides mock()) {
                CorusTheme(darkTheme = false) {
                    SuggestedUsersListScreen(
                        matches = if (hideFirst) ranked.drop(1) else ranked.take(count),
                        featuredMatch = if (featured) ranked.first() else null,
                        hasMore = hasMore, onLoadMore = { loads++ }, followedIds = followed.value,
                        onNavigateToUser = { profiles++ },
                        onFollow = { follows++; followed.value = followed.value + it.id },
                    )
                }
            }
        }
    }

    @Test fun `featured card appears once and following does not open the profile`() {
        render()
        compose.onNodeWithText("#1").assertIsDisplayed()
        compose.onAllNodesWithText("listener1", substring = true).assertCountEquals(1)
        compose.onNodeWithTag("closest-match-follow").performClick().assertTextContains("FOLLOWING")
        assertEquals(1, follows)
        assertEquals(0, profiles)
        compose.onNodeWithText("Listener 1").performClick()
        assertEquals(1, profiles)
    }
    @Test fun `flag off keeps the original grid`() {
        render(featured = false)
        compose.onNodeWithText("#1").assertDoesNotExist()
        compose.onNodeWithText("listener1").assertIsDisplayed()
    }
    @Test fun `filter does not relabel the next person as number one`() {
        render(hideFirst = true)
        compose.onNodeWithText("#1").assertDoesNotExist()
        compose.onNodeWithText("listener2").assertIsDisplayed()
    }
    @Test fun `a featured-only first page still requests more matches`() {
        render(count = 1, hasMore = true)
        compose.onNodeWithText("#1").assertIsDisplayed()
        compose.runOnIdle { assertEquals(1, loads) }
    }
}
