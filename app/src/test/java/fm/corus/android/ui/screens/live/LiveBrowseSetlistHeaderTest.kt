package fm.corus.android.ui.screens.live

import android.app.Application
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.*
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = Application::class, qualifiers = "en-rUS-w393dp-h800dp")
class LiveBrowseSetlistHeaderTest {
    @get:Rule val compose = createComposeRule()
    private fun room(description: String = "") = LiveRoom("archive", mapOf("title" to "Room identity", "status" to "ended", "hostId" to "new-host",
        "creator" to mapOf("uid" to "creator", "displayName" to "creator"), "roomDescription" to description,
        "createdAt" to 100000L, "endedAt" to 200000L))
    private fun vm(uid: String): LiveViewModel = mock<LiveViewModel>().also {
        whenever(it.state).thenReturn(MutableStateFlow(LiveUiState(uid = uid)))
    }

    private fun djTrack(uid: String, username: String) = LiveTrack(
        fm.corus.android.data.model.CymbalTrack(id = "song-$uid", name = "Song", artistName = "Artist", albumName = "Album", durationMs = 180000),
        playedBy = LivePerson(uid, username, null),
    )

    @Test fun singleDJOpensTheirProfileDirectly() {
        val vm = vm("viewer")
        var opened: String? = null
        compose.setContent { Column { LiveBrowseSetlistHeader(room(), listOf(djTrack("solo", "arielle")), vm, {}, { opened = it }) } }
        compose.onNodeWithText("@arielle").performClick()
        assertEquals("solo", opened)
        compose.onNodeWithText("DJs").assertDoesNotExist()
    }

    @Test fun multipleDJsStillOpenTheListBeforeAProfile() {
        val vm = vm("viewer")
        var opened: String? = null
        compose.setContent { Column { LiveBrowseSetlistHeader(room(), listOf(djTrack("solo", "arielle"), djTrack("second", "gabe")), vm, {}, { opened = it }) } }
        compose.onNodeWithText("2 DJs").performClick()
        compose.onNodeWithText("DJs").assertIsDisplayed()
        assertEquals(null, opened)
        compose.onNodeWithText("@arielle").performClick()
        assertEquals("solo", opened)
    }

    @Test fun creatorCanCancelDeletionWithoutSendingDelete() {
        val vm = vm("creator")
        val room = room()
        compose.setContent { Column { LiveBrowseSetlistHeader(room, emptyList(), vm, {}, {}) } }
        compose.onNodeWithContentDescription("See more").performClick()
        compose.onNodeWithText("Delete room").performClick()
        compose.onNodeWithText("Delete room?").assertIsDisplayed()
        compose.onNodeWithText("Cancel").performClick()
        compose.onNodeWithText("Delete room?").assertDoesNotExist()
        verify(vm).event("past_room_delete_cancelled", source = "setlist", roomId = "archive")
        kotlinx.coroutines.runBlocking { verify(vm, never()).deletePastRoom(any()) }
    }

    @Test fun transferredHostCannotSeeCreatorsDeleteMenu() {
        val vm = vm("new-host")
        compose.setContent { Column { LiveBrowseSetlistHeader(room(), emptyList(), vm, {}, {}) } }
        compose.onNodeWithContentDescription("See more").assertDoesNotExist()
    }

    @Test fun expansionKeepsTheRoomIdentityAnchoredAboveTheDescription() {
        val vm = vm("viewer")
        val room = room(("Long room description with music and friends. ").repeat(20))
        compose.setContent { Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            LiveBrowseSetlistHeader(room, emptyList(), vm, {}, {})
        } }
        val before = compose.onNodeWithText("Room identity").fetchSemanticsNode().boundsInRoot.top
        compose.onNodeWithText("See more", substring = true).performClick()
        compose.waitForIdle()
        val after = compose.onNodeWithText("Room identity").fetchSemanticsNode().boundsInRoot.top
        assertEquals(before, after, 1f)
        compose.onNodeWithText("Show less", substring = true).assertDoesNotExist()
        compose.onNodeWithText(room.roomDescription.trim()).assertHasNoClickAction()
        verify(vm).event("setlist_description_toggled", "expanded", "setlist", "archive")
    }
    @Test fun djSummaryKeepsItsRowHeightWhileLoadingThenRevealsTheDJ() {
        val vm = vm("viewer")
        var loading by mutableStateOf(true)
        compose.setContent { Column {
            LiveBrowseSetlistHeader(room(), listOf(djTrack("solo", "arielle")), vm, {}, {}, loading = loading)
        } }
        compose.onNodeWithTag("live-setlist-djs-loading").assertIsDisplayed().assertHeightIsAtLeast(44.dp)
        compose.onNodeWithText("@arielle").assertDoesNotExist()
        compose.runOnIdle { loading = false }
        compose.onNodeWithTag("live-setlist-djs-loading").assertDoesNotExist()
        compose.onNodeWithText("@arielle").assertIsDisplayed()
    }

}
