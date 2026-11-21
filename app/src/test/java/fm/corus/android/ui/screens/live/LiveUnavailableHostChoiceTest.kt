package fm.corus.android.ui.screens.live

import android.app.Application
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class, qualifiers = "en-rUS-w393dp-h800dp")
class LiveUnavailableHostChoiceTest {
    @get:Rule val compose = createComposeRule()
    private fun unavailableState(revision: Long = 3, pausedAt: Long? = null, voiceMode: String = "music", pending: Boolean = false) = LiveUiState(uid = "host", spotifyUnavailableRevision = revision,
        room = LiveRoom("room", mapOf("hostId" to "host", "status" to "playing", "revision" to revision,
            "djs" to emptyList<Any>(), "pausedAt" to pausedAt, "voiceMode" to voiceMode,
            "pendingSkip" to if (pending) mapOf("id" to "switch") else null, "current" to mapOf("id" to "am:123", "source" to "applemusic",
                "title" to "Gamma Knife", "durationMs" to 180_000))))

    @Test fun continueDismissesOnceWithoutChangingTheSharedRoom() {
        val state = mutableStateOf(unavailableState())
        val skips = mutableListOf<Pair<String, Long>>()
        compose.setContent { HostUnavailableSongChoice(state.value, true) { id, revision -> skips += id to revision } }
        compose.onNodeWithText("Not available on Spotify").assertIsDisplayed()
        compose.onNodeWithText("Gamma Knife\n\nApple Music listeners may still hear this song.").assertIsDisplayed()
        compose.onNodeWithText("Continue anyway").performClick()
        compose.onNodeWithText("Not available on Spotify").assertDoesNotExist()
        compose.runOnIdle { state.value = state.value.copy(audioReady = false) }
        compose.onNodeWithText("Not available on Spotify").assertDoesNotExist()
        compose.runOnIdle {
            assertEquals(3L, state.value.room?.revision)
            assertEquals(emptyList<Pair<String, Long>>(), skips)
            state.value = unavailableState(4)
        }
        compose.onNodeWithText("Not available on Spotify").assertIsDisplayed()
    }

    @Test fun skipTargetsExactlyTheCurrentRevisionIncludingAnEmptyCollaborativeQueue() {
        val skips = mutableListOf<Pair<String, Long>>()
        compose.setContent { HostUnavailableSongChoice(unavailableState(), true) { id, revision -> skips += id to revision } }
        compose.onNodeWithText("Skip song").performClick()
        compose.onNodeWithText("Not available on Spotify").assertDoesNotExist()
        compose.runOnIdle { assertEquals(listOf("room" to 3L), skips) }
    }

    @Test fun listenerAndUnknownFailuresNeverShowAChoiceAndStaleChoicesClose() {
        val state = mutableStateOf(unavailableState().copy(uid = "listener"))
        compose.setContent { HostUnavailableSongChoice(state.value, true) { _, _ -> error("Unexpected skip") } }
        compose.onNodeWithText("Not available on Spotify").assertDoesNotExist()
        compose.runOnIdle { state.value = unavailableState().copy(spotifyUnavailableRevision = null, audioError = "Timed out") }
        compose.onNodeWithText("Not available on Spotify").assertDoesNotExist()
        compose.runOnIdle { state.value = unavailableState() }
        compose.onNodeWithText("Not available on Spotify").assertIsDisplayed()
        compose.runOnIdle { state.value = unavailableState().copy(room = unavailableState(4).room) }
        compose.onNodeWithText("Not available on Spotify").assertDoesNotExist()
    }

    @Test fun backgroundPausedAndTalkStatesDoNotOfferTheChoice() {
        val current = unavailableState()
        assertNull(current.copy(minimized = true).unavailableHostSongRevision)
        assertNull(current.copy(muted = true).unavailableHostSongRevision)
        assertNull(unavailableState(pausedAt = 1).unavailableHostSongRevision)
        assertNull(unavailableState(voiceMode = "talk").unavailableHostSongRevision)
        assertNull(unavailableState(pending = true).unavailableHostSongRevision)
        val show = mutableStateOf(false)
        compose.setContent { HostUnavailableSongChoice(current, show.value) { _, _ -> error("Unexpected skip") } }
        compose.onNodeWithText("Not available on Spotify").assertDoesNotExist()
        compose.runOnIdle { show.value = true }
        compose.onNodeWithText("Not available on Spotify").assertIsDisplayed()
    }
}
