package fm.corus.android.ui.screens.messaging

import android.app.Application
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
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
class InboxScrollPositionTest {
    @get:Rule val compose = createComposeRule()
    private val rows = mutableStateOf(listOf("joyce", "other", "third", "fourth", "fifth"))
    private lateinit var state: LazyListState

    private fun showInbox() {
        compose.setContent {
            state = rememberLazyListState()
            KeepInboxTopVisible(state, rows.value, enabled = true)
            LazyColumn(state = state, modifier = Modifier.size(300.dp, 150.dp)) {
                items(rows.value, key = { it }) { Text(it, modifier = Modifier.height(60.dp)) }
            }
        }
        compose.waitForIdle()
    }

    @Test
    fun newCliftonRowStaysVisibleAboveJoyceWhenAtTop() {
        showInbox()
        compose.runOnIdle { rows.value = listOf("clifton") + rows.value }
        compose.waitForIdle()
        compose.runOnIdle {
            assertEquals(0, state.firstVisibleItemIndex)
            assertEquals(0, state.firstVisibleItemScrollOffset)
            assertEquals("clifton", state.layoutInfo.visibleItemsInfo.first().key)
        }
    }

    @Test
    fun movingExistingConversationToTopRevealsIt() {
        showInbox()
        compose.runOnIdle { rows.value = listOf("fifth", "joyce", "other", "third", "fourth") }
        compose.waitForIdle()
        compose.runOnIdle { assertEquals("fifth", state.layoutInfo.visibleItemsInfo.first().key) }
    }

    @Test
    fun preservesReadingPositionWhenBrowsingOlderConversations() {
        showInbox()
        compose.runOnIdle { runBlocking { state.scrollToItem(2, 10) } }
        compose.runOnIdle { rows.value = listOf("clifton") + rows.value }
        compose.waitForIdle()
        compose.runOnIdle {
            assertEquals("third", state.layoutInfo.visibleItemsInfo.first().key)
            assertEquals(10, state.firstVisibleItemScrollOffset)
        }
    }
}
