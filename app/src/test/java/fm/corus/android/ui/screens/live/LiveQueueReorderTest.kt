package fm.corus.android.ui.screens.live

import android.app.Application
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class LiveQueueReorderTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun draggingAtTopEdgeReachesTheFirstSlotFromFarDownTheQueue() {
        var drop: Pair<Int, Int>? = null
        compose.setContent {
            val list = rememberLazyListState(initialFirstVisibleItemIndex = 13)
            val preview = remember { mutableStateOf<LiveQueueDrag?>(null) }
            LazyColumn(Modifier.height(180.dp).testTag("queue"), state = list) {
                items(20, key = { it }) { index ->
                    LiveQueueReorderRow(
                        index, 20, 60.dp,
                        move = { drop = index to it },
                        dragPreview = preview,
                        scrollState = list,
                    ) { handle ->
                        Row(
                            Modifier.height(60.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text("Song $index", Modifier.weight(1f))
                            handle()
                        }
                    }
                }
            }
        }

        val queue = compose.onNodeWithTag("queue")
        compose.onNodeWithText("Song 15").assertIsDisplayed()
        compose.mainClock.autoAdvance = false
        queue.performTouchInput {
            val x = width - 20.dp.toPx()
            down(Offset(x, 150.dp.toPx()))
            moveTo(Offset(x, 5.dp.toPx()))
        }
        compose.mainClock.advanceTimeBy(2_500)
        compose.onNodeWithText("Song 0").assertIsDisplayed()
        compose.onNodeWithText("Song 15").assertIsDisplayed()
        queue.performTouchInput { up() }
        compose.mainClock.advanceTimeBy(300)
        compose.runOnIdle { assertEquals(15 to 0, drop) }
    }
}
