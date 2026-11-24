package fm.corus.android.ui.screens.live

import android.app.Application
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class LiveQueueScrollAnchorTest {
    @get:Rule val compose = createComposeRule()

    @Test fun returningFromEditorShowsNewFirstSongInsteadOfAnchoringOldFirstSong() {
        val queue = mutableStateOf(listOf("Hawk Dreams", "Au Pays", "el cielo", "San Lorenzo", "Alice"))
        lateinit var scroll: LazyListState
        compose.setContent {
            val songs = queue.value
            scroll = rememberLiveQueueScroll(songs)
            LazyColumn(Modifier.height(180.dp), state = scroll) {
                items(songs, key = { it }) { Text(it, Modifier.height(60.dp)) }
            }
        }
        compose.onNodeWithText("Hawk Dreams").assertIsDisplayed()
        compose.runOnIdle { queue.value = listOf("San Lorenzo", "Hawk Dreams", "Au Pays", "el cielo", "Alice") }
        compose.waitForIdle()
        compose.runOnIdle { assertEquals("Keep the viewport on slot 1 after editing", 0, scroll.firstVisibleItemIndex) }
        compose.onNodeWithText("San Lorenzo").assertIsDisplayed()
        // A user scrolled farther down should continue seeing the same song.
        compose.runOnIdle { runBlocking { scroll.scrollToItem(2) } }
        compose.waitForIdle()
        compose.runOnIdle { queue.value = listOf("New song") + queue.value }
        compose.waitForIdle()
        compose.runOnIdle { assertEquals(3, scroll.firstVisibleItemIndex) }
        compose.onNodeWithText("Au Pays").assertIsDisplayed()
    }
}
