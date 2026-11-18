package fm.corus.android.ui.screens.live

import android.app.Application
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
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
class LiveMiniPlayerControlsTest {
    @get:Rule val compose = createComposeRule()

    @Test fun controlsStayMountedThroughSkipAndQueueExhaustion() {
        val vm = mock<LiveViewModel>()
        whenever(vm.profile).thenReturn(MutableStateFlow(null))
        whenever(vm.serverTime()).thenReturn(1_000L)
        val song = mapOf("id" to "song", "title" to "Song", "durationMs" to 180_000)
        fun room(waiting: Boolean = false, pending: Boolean = false, queued: Boolean = true) = LiveRoom("room", mapOf(
            "hostId" to "host", "hostUsername" to "host", "status" to if (waiting) "waiting" else "playing",
            "current" to if (waiting) null else song, "queue" to if (queued) listOf(song) else emptyList<Any>(),
            "startedAt" to 1, "deadline" to 180_001,
            "pendingSkip" to if (pending) mapOf("id" to "skip") else null,
        ))
        val state = mutableStateOf(LiveUiState(uid = "host", room = room(), minimized = true))
        compose.setContent { LiveMiniPlayer(state.value, vm) }
        val next = compose.onNodeWithContentDescription("Skip song")
        val mute = compose.onNodeWithContentDescription("Mute audio")
        next.assertIsDisplayed().assertIsEnabled()
        mute.assertIsDisplayed().assertIsEnabled()
        compose.runOnIdle { state.value = state.value.copy(room = room(pending = true, queued = false)) }
        next.assertIsDisplayed().assertIsEnabled()
        mute.assertIsDisplayed().assertIsEnabled()
        compose.runOnIdle { state.value = state.value.copy(room = room(queued = false)) }
        next.assertIsDisplayed().assertIsNotEnabled()
        mute.assertIsDisplayed().assertIsEnabled()
        compose.runOnIdle { state.value = state.value.copy(room = room(waiting = true, queued = false)) }
        next.assertIsDisplayed().assertIsNotEnabled()
        mute.assertIsDisplayed().assertIsNotEnabled()
    }
}
