package fm.corus.android.ui.screens.live

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class, qualifiers = "en-rUS-w393dp-h800dp")
class LiveRoomSwipeReleaseTest {
    @get:Rule
    val compose = createComposeRule()

    private val vm = mock<LiveViewModel>()
    private val room = LiveRoom("room", mapOf("hostId" to "host", "hostUsername" to "host", "status" to "waiting", "title" to "Test Live Set"))
    private val state = mutableStateOf(LiveUiState(uid = "host", room = room))

    private fun showRoom(minimized: Boolean = false) {
        state.value = state.value.copy(minimized = minimized)
        whenever(vm.profile).thenReturn(MutableStateFlow(null))
        doAnswer { state.value = state.value.copy(minimized = true) }.whenever(vm).minimize()
        doAnswer { state.value = state.value.copy(minimized = false) }.whenever(vm).expand()
        compose.setContent {
            Box(Modifier.fillMaxSize()) {
                LivePresentation(
                    state.value, vm, openProfile = {}, openSong = {}, nowPlayingDetails = { _, _ -> },
                    roomExpansion = rememberLiveRoomExpansion(state.value),
                    parkInsetPx = 80f,
                )
            }
        }
    }

    @Test
    fun joiningReplacesLoadingWithTheFullyExpandedRoomOnTheFirstFrame() {
        state.value = LiveUiState(uid = "host", progress = LiveProgress.TUNING)
        whenever(vm.profile).thenReturn(MutableStateFlow(null))
        compose.setContent {
            Box(Modifier.fillMaxSize()) {
                LivePresentation(
                    state.value, vm, openProfile = {}, openSong = {}, nowPlayingDetails = { _, _ -> },
                    roomExpansion = rememberLiveRoomExpansion(state.value),
                    parkInsetPx = 80f,
                )
            }
        }
        // Spotify can mount the room behind the entrance before loading finishes.
        compose.runOnIdle { state.value = state.value.copy(room = room) }
        compose.onNodeWithText("Tuning in…").assertIsDisplayed()
        compose.mainClock.autoAdvance = false
        compose.runOnIdle { state.value = state.value.copy(progress = null) }
        // Advance through recomposition of the loading state, stopping on the
        // first frame where the entrance has actually been replaced.
        val loading = compose.onNodeWithText("Tuning in…")
        repeat(5) {
            if (compose.onAllNodesWithText("Tuning in…").fetchSemanticsNodes().isNotEmpty()) {
                compose.mainClock.advanceTimeByFrame()
                compose.waitForIdle()
            }
        }
        loading.assertDoesNotExist()
        val title = compose.onNodeWithText("Test Live Set", useUnmergedTree = true)
        title.assertIsDisplayed()
        val entryTop = title.getUnclippedBoundsInRoot().top
        compose.mainClock.autoAdvance = true
        compose.waitForIdle()
        assertEquals("The first room frame must already cover the feed", title.getUnclippedBoundsInRoot().top, entryTop)
    }

    @Test
    fun shortPullSnapsBackAndFlickOrLongPullMinimizes() {
        showRoom()
        val player = compose.onNodeWithText("Test Live Set", useUnmergedTree = true)
        val start = player.fetchSemanticsNode().boundsInRoot.center
        val expandedTop = player.getUnclippedBoundsInRoot().top
        compose.onRoot().performTouchInput {
            swipe(start, start + Offset(0f, height * .12f), durationMillis = 600)
        }
        assertEquals(start, player.fetchSemanticsNode().boundsInRoot.center)
        verify(vm, never()).minimize()

        compose.onRoot().performTouchInput {
            swipe(start, start + Offset(0f, height * .25f), durationMillis = 100)
        }
        player.assertDoesNotExist()
        verify(vm).minimize()

        compose.mainClock.autoAdvance = false
        compose.runOnIdle { state.value = state.value.copy(minimized = false) }
        repeat(10) {
            compose.mainClock.advanceTimeByFrame()
            compose.waitForIdle()
        }
        assertTrue(player.getUnclippedBoundsInRoot().top > expandedTop)
        compose.mainClock.autoAdvance = true
        assertEquals(start, player.fetchSemanticsNode().boundsInRoot.center)
        compose.onRoot().performTouchInput {
            down(start)
            moveTo(start + Offset(0f, height * .1f), delayMillis = 120)
        }
        assertTrue(player.fetchSemanticsNode().boundsInRoot.center.y > start.y)
        compose.onRoot().performTouchInput {
            moveTo(start + Offset(0f, height * .6f), delayMillis = 800)
        }
        compose.onRoot().performTouchInput {
            advanceEventTime(150) // Release after a pause to exercise distance without fling velocity.
            up()
        }
        player.assertDoesNotExist()
        verify(vm, times(2)).minimize()
    }

    @Test
    fun swipeUpMovesTheMiniPlayerAndOpensTheRoomOnRelease() {
        showRoom(minimized = true)
        val miniPlayer = compose.onNodeWithText("@host", useUnmergedTree = true)
        val start = miniPlayer.fetchSemanticsNode().boundsInRoot.center
        compose.onRoot().performTouchInput {
            down(start)
            moveTo(start - Offset(0f, height * .12f), delayMillis = 120)
        }
        assertTrue(miniPlayer.fetchSemanticsNode().boundsInRoot.center.y < start.y)
        verify(vm, never()).expand()

        compose.onRoot().performTouchInput {
            moveTo(start - Offset(0f, height * .65f), delayMillis = 600)
            advanceEventTime(150)
            up()
        }
        compose.onNodeWithText("Recently Played").assertIsDisplayed()
        verify(vm).expand()
    }

    @Test
    fun swipeLeftOpensChatAndPullInChatKeepsRoomOpen() {
        showRoom()
        val chatButton = compose.onNodeWithText("Swipe left for chat")
        val y = chatButton.fetchSemanticsNode().boundsInRoot.center.y
        compose.onRoot().performTouchInput {
            swipe(Offset(width * .8f, y), Offset(width * .2f, y), durationMillis = 250)
        }
        val composer = compose.onNodeWithText("Say something…")
        composer.assertIsDisplayed()

        val chatStart = compose.onNodeWithText("Waiting for the next song", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot.bottom
        val chatEnd = composer.fetchSemanticsNode().boundsInRoot.top
        compose.onRoot().performTouchInput {
            val range = chatEnd - chatStart
            swipe(Offset(centerX, chatStart + range * .55f), Offset(centerX, chatStart + range * .9f), durationMillis = 100)
        }
        composer.assertIsDisplayed()
        verify(vm, never()).minimize()
    }
}
