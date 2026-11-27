package fm.corus.android.ui.screens.live

import android.app.Application
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.*
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class LiveDiscoverySnapshotTest {
    private fun response() = mapOf<String, Any?>("protocol" to 1, "generatedAt" to 1000,
        "rankedRoomIds" to listOf("busy", "solo"), "rooms" to listOf(
            mapOf("id" to "busy", "room" to mapOf("status" to "waiting", "leaseUntil" to 30000, "deadline" to 0),
                "listenerCount" to 3, "peopleCount" to 6),
            mapOf("id" to "solo", "room" to mapOf("status" to "playing", "leaseUntil" to 30000),
                "listenerCount" to 0, "peopleCount" to 1)))
    @Test fun publishesOnlyCompleteServerRankedDataAndKeepsItOnRefreshFailure() = runBlocking {
        val answer = CompletableDeferred<LiveFields>()
        val vm = mock<LiveViewModel> { onBlocking { loadDiscoverySnapshot("") } doSuspendableAnswer { answer.await() } }
        val state = LiveDiscoverySnapshotState()
        val load = async { state.load(vm, "") }
        kotlinx.coroutines.yield()
        assertNull(state.snapshot)
        answer.complete(response()); load.await()
        assertEquals(listOf("busy", "solo"), state.snapshot!!.rooms.map { it.room.id })
        assertEquals(6, state.snapshot!!.rooms.first().peopleCount)
        val prior = state.snapshot
        whenever(vm.loadDiscoverySnapshot("")).thenThrow(IllegalStateException("offline"))
        state.load(vm, "")
        assertSame(prior, state.snapshot)
        assertTrue(state.failed)
    }
    @Test fun rejectsPartialCountsAndMismatchedOrder() {
        assertThrows(IllegalArgumentException::class.java) { LiveDiscoverySnapshot.parse(response() + ("rankedRoomIds" to listOf("solo", "busy"))) }
        assertThrows(IllegalStateException::class.java) { LiveDiscoverySnapshot.parse(response() + ("rooms" to listOf(mapOf("id" to "missing-count", "room" to emptyMap<String, Any>())))) }
    }
    @Test fun songDeadlineDoesNotEndDiscoveryButRoomStatusAndLeaseDo() {
        fun room(status: String, lease: Long, deadline: Long = 1) = LiveRoom("room", mapOf("status" to status, "leaseUntil" to lease, "deadline" to deadline))
        assertTrue(liveDiscoveryVisible(room("playing", 20000), 10000))
        assertTrue(liveDiscoveryVisible(room("waiting", 20000), 10000))
        assertFalse(liveDiscoveryVisible(room("ended", 20000), 10000))
        assertFalse(liveDiscoveryVisible(room("playing", 9999, 20000), 10000))
    }
}
