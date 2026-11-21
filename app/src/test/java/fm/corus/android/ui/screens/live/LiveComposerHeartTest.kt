package fm.corus.android.ui.screens.live

import android.app.Application
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class, qualifiers = "en-rUS-w393dp-h800dp")
class LiveComposerHeartTest {
    @get:Rule val compose = createComposeRule()

    private fun room(id: String = "room", playing: Boolean = true, dj: String = "host") = LiveRoom(id, mapOf(
        "hostId" to "host", "status" to if (playing) "playing" else "waiting",
        "current" to if (playing) mapOf("id" to "song", "title" to "Song", "durationMs" to 10_000L) else null,
        "currentDJ" to mapOf("uid" to dj), "deadline" to 10_000L,
    ))

    @Test fun knownSongDoesNotWaitForAudioAndHeartSurvivesTransitions() {
        val currentRoom = mutableStateOf(room())
        val now = mutableStateOf(1L)
        val editing = mutableStateOf(false)
        compose.setContent {
            LiveComposerHeart(currentRoom.value, LiveUiState(uid = "listener", audioReady = false),
                now.value, editing.value) {}
        }
        val heart = compose.onNodeWithContentDescription("Like song")
        heart.assertIsDisplayed().assertIsEnabled()
        compose.runOnIdle { now.value = 10_000L }
        heart.assertIsDisplayed().assertIsNotEnabled()
        compose.runOnIdle { currentRoom.value = room(playing = false) }
        heart.assertIsDisplayed().assertIsNotEnabled()
        compose.runOnIdle { currentRoom.value = room(); now.value = 1L }
        heart.assertIsDisplayed().assertIsEnabled()
        compose.runOnIdle { editing.value = true }
        heart.assertDoesNotExist()
        compose.runOnIdle { editing.value = false; currentRoom.value = room(id = "other", playing = false) }
        heart.assertDoesNotExist()
    }

    @Test fun hostCannotLikeOwnSongButCanLikeGuestSong() {
        val currentRoom = mutableStateOf(room())
        val now = mutableStateOf(1L)
        compose.setContent {
            LiveComposerHeart(currentRoom.value, LiveUiState(uid = "host", room = currentRoom.value), now.value, false) {}
        }
        val heart = compose.onNodeWithContentDescription("Like song")
        heart.assertDoesNotExist()
        compose.runOnIdle { currentRoom.value = room(dj = "guest") }
        heart.assertIsDisplayed().assertIsEnabled()
        compose.runOnIdle { now.value = 10_000L }
        heart.assertIsDisplayed().assertIsNotEnabled()
        compose.runOnIdle { currentRoom.value = room() }
        heart.assertDoesNotExist()
    }
}
