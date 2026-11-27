package fm.corus.android.ui.screens.live

import android.app.Application
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.*
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class, qualifiers = "en-rUS-w393dp-h800dp")
class LiveBrowseTest {
    @get:Rule val compose = createComposeRule()
    private fun room(id: String, dj: String = "guest") = LiveRoom(id, mapOf("title" to "Room $id", "hostId" to "host", "hostUsername" to "host",
        "currentDJ" to mapOf("uid" to dj, "displayName" to dj), "djs" to listOf(mapOf("uid" to "guest", "displayName" to "guest"))))

    @Test fun currentPerformerComesFirstWithoutDuplicatingHostOrGuest() {
        assertEquals(listOf("guest", "host"), liveBrowseLineup(room("1")).map { it.uid })
        assertEquals(listOf("host", "guest"), liveBrowseLineup(room("1", "host")).map { it.uid })
    }

    @Test fun paginationSkipsEmptyCandidatePagesAndKeepsExistingRoomsOnFailure() = runBlocking {
        val first = mapOf("createdAt" to 1L, "id" to "cursor", "visitorCount" to 1L)
        val entry = mapOf("id" to "archive", "room" to mapOf("title" to "Archive"), "visitorCount" to 3L)
        val vm = mock<LiveViewModel> {
            onBlocking { browseRooms("past", "", null) } doReturn mapOf("rooms" to emptyList<Any>(), "nextCursor" to first)
            onBlocking { browseRooms("past", "", first) } doReturn mapOf("rooms" to listOf(entry), "nextCursor" to null)
        }
        val state = LiveBrowsePageState()
        state.load(vm, "past", "")
        assertEquals(listOf("archive"), state.rooms.map { it.room.id })
        assertTrue(state.loaded); assertNull(state.cursor)
        whenever(vm.browseRooms("past", "", null)).thenThrow(IllegalStateException("offline"))
        state.load(vm, "past", "", reset = true)
        assertTrue(state.failed); assertFalse(state.loading)
        assertEquals("archive", state.rooms.single().room.id)
    }

    @Test fun tenLiveRoomsAreReachableAndOpenTheTappedRoom() {
        val access = LiveAccess(true, true, false, true, true, true, true)
        val state = MutableStateFlow(LiveUiState(uid = "viewer", access = access))
        val model = LiveBrowsePageState().apply { loaded = true }
        val vm = mock<LiveViewModel>()
        whenever(vm.state).thenReturn(state)
        whenever(vm.browseModel("viewer", "past", "")).thenReturn(model)
        whenever(vm.browseModel("viewer", "active", "")).thenReturn(LiveBrowsePageState())
        val nav = LiveNavigation(LiveNavigationState(access, false, (1..10).map { room(it.toString()) },
            emptyMap(), trendingRoom = null, showGoLive = true, canOpenSetup = true,
            mixedSaves = null, mixedSavesLoading = false, mixedSavesHasMore = false, mixedSavesFailed = false), vm, {})
        whenever(vm.discoverySnapshotModel("viewer", "")).thenReturn(LiveDiscoverySnapshotState().apply {
            snapshot = LiveDiscoverySnapshot(nav.state.discovery.map { LiveDiscoverySnapshotEntry(it, emptyList(), 3, 3 + 1 + it.djs.size) }, 0)
        })
        compose.setContent { CompositionLocalProvider(LocalLiveNavigation provides nav) {
            LiveBrowseScreen("", rememberLazyGridState(), true)
        } }
        compose.onNode(hasScrollToIndexAction()).performScrollToIndex(10)
        compose.onNodeWithText("Room 10").assertIsDisplayed().performClick()
        verify(vm).join("10")
        verify(vm).event("browse_room_impression", source = "live_browse", roomId = "10")
        verify(vm).event("browse_room_opened", source = "live_browse", roomId = "10")
    }
    @Test fun busiestRoomsAppearFirstIncludingHostAndDJs() {
        val access = LiveAccess(true, true, false, true, true, true, true)
        val state = MutableStateFlow(LiveUiState(uid = "viewer", access = access, discoveryListenerCounts = mapOf("1" to 0, "2" to 4)))
        val model = LiveBrowsePageState().apply { loaded = true }
        val vm = mock<LiveViewModel>()
        whenever(vm.state).thenReturn(state)
        whenever(vm.browseModel("viewer", "past", "")).thenReturn(model)
        whenever(vm.browseModel("viewer", "active", "")).thenReturn(LiveBrowsePageState())
        val nav = LiveNavigation(LiveNavigationState(access, false, (1..2).map { room(it.toString()) },
            emptyMap(), trendingRoom = null, showGoLive = true, canOpenSetup = true,
            mixedSaves = null, mixedSavesLoading = false, mixedSavesHasMore = false, mixedSavesFailed = false), vm, {})
        whenever(vm.discoverySnapshotModel("viewer", "")).thenReturn(LiveDiscoverySnapshotState().apply {
            snapshot = LiveDiscoverySnapshot(nav.state.discovery.reversed().map { LiveDiscoverySnapshotEntry(it, emptyList(), if (it.id == "2") 4 else 0, if (it.id == "2") 6 else 2) }, 0)
        })
        compose.setContent { CompositionLocalProvider(LocalLiveNavigation provides nav) {
            LiveBrowseScreen("", rememberLazyGridState(), true)
        } }
        val first = compose.onNodeWithText("Room 1").fetchSemanticsNode().boundsInRoot.top
        val busy = compose.onNodeWithText("Room 2").fetchSemanticsNode().boundsInRoot.top
        assertTrue("The room with seven people must precede the room with three", busy < first)
    }

}
