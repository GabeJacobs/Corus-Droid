package fm.corus.android.ui.screens.live

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import fm.corus.android.ui.navigation.SongDetailRoute
import fm.corus.android.ui.player.rememberPlayerExpansionState
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class, qualifiers = "en-rUS-w393dp-h800dp")
class LiveSetlistNavigationTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun backFromSongRestoresSetlistScrollPosition() {
        val vm = mock<LiveViewModel>()
        whenever(vm.profile).thenReturn(MutableStateFlow(null))
        val room = LiveRoom("room", mapOf(
            "title" to "Test Live Set",
            "recent" to (1..25).reversed().map { number ->
                mapOf("id" to "track-$number", "title" to "Song $number", "durationMs" to 180_000)
            },
        ))
        compose.setContent {
            val navController = rememberNavController()
            Box(Modifier.fillMaxSize()) {
                NavHost(navController, startDestination = "feed") {
                    composable("feed") { Text("Feed") }
                    composable<SongDetailRoute> { entry ->
                        val song = entry.toRoute<SongDetailRoute>()
                        Button(onClick = { navController.popBackStack() }) { Text("Back from ${song.songName}") }
                    }
                }
                LivePresentation(
                    state = LiveUiState(ended = room),
                    vm = vm,
                    openProfile = {},
                    openSong = {},
                    setlistNavController = navController,
                    nowPlayingDetails = { _, _ -> },
                    roomExpansion = rememberPlayerExpansionState(),
                    parkInsetPx = 0f,
                )
            }
        }

        compose.onNodeWithText("View setlist", ignoreCase = true).performClick()
        compose.onNode(hasScrollToIndexAction()).performScrollToIndex(20)
        compose.onNodeWithText("Song 21").performClick()
        compose.onNodeWithText("Live setlist").assertDoesNotExist()
        compose.onNodeWithText("Back from Song 21").assertIsDisplayed().performClick()
        compose.onNodeWithText("Live setlist").assertIsDisplayed()
        compose.onNodeWithText("Song 21").assertIsDisplayed()
    }
}
