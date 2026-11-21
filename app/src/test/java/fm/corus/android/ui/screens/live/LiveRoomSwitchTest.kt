package fm.corus.android.ui.screens.live

import android.app.Application
import androidx.lifecycle.viewModelScope
import androidx.test.core.app.ApplicationProvider
import fm.corus.android.data.model.MusicService
import fm.corus.android.data.repository.AuthRepository
import fm.corus.android.domain.MusicServicePreference
import fm.corus.android.domain.SaveChangedEvent
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.*
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class LiveRoomSwitchTest {
    private fun room(id: String) = LiveRoom(id, mapOf(
        "hostId" to if (id == "B") "other-host" else "host", "status" to "waiting", "leaseUntil" to Long.MAX_VALUE,
        "deadline" to Long.MAX_VALUE,
    ))

    private class Fixture(val repository: LiveRepository) {
        init {
            whenever(repository.discovery(any())).thenReturn(emptyFlow())
            whenever(repository.invites(any())).thenReturn(emptyFlow())
        }
        val voice = mock<LiveVoice>()
        val auth = mock<AuthRepository> {
            on { currentUser } doReturn MutableStateFlow(null)
            on { currentUserId } doReturn "host"
        }
        val music = mock<MusicServicePreference> {
            on { current } doReturn MutableStateFlow(MusicService.SPOTIFY)
        }
        val vm = LiveViewModel(ApplicationProvider.getApplicationContext(), repository, auth, mock(),
            mock(), mock(), music, voice, mock(), mock(), SaveChangedEvent())
        @Suppress("UNCHECKED_CAST")
        val state = LiveViewModel::class.java.getDeclaredField("_state").apply { isAccessible = true }
            .get(vm) as MutableStateFlow<LiveUiState>
        fun seed(room: LiveRoom, host: Boolean = false) {
            state.value = state.value.copy(uid = if (host) "host" else "listener", room = room,
                minimized = true, access = LiveAccess(false, false, false, false, false, false, false))
        }
        fun close() {
            vm.viewModelScope.cancel()
            LiveViewModel::class.java.getDeclaredMethod("onCleared").apply { isAccessible = true }.invoke(vm)
            Dispatchers.resetMain()
        }
    }

    @Test fun listenerJoinsWhileOldLeaveIsSuspendedAndCleanupCannotCloseNewRoom() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val leave = CompletableDeferred<LiveFields>()
        val lookUp = CompletableDeferred<LiveRoom?>()
        val calls = mutableListOf<Pair<String, String>>()
        val repository = mock<LiveRepository> {
            onBlocking { room("B") } doSuspendableAnswer { lookUp.await() }
            onBlocking { call(any(), any(), any()) } doSuspendableAnswer {
                val action = it.getArgument<String>(0)
                val id = it.getArgument<String>(1)
                calls += action to id
                if (action == "leave") leave.await() else emptyMap()
            }
            on { observeRoom(any()) } doReturn emptyFlow()
            on { messages(any()) } doReturn emptyFlow()
            on { membership(any(), any()) } doReturn emptyFlow()
            on { ownRequest(any(), any(), any()) } doReturn emptyFlow()
        }
        val f = Fixture(repository)
        try {
            runCurrent()
            f.seed(room("A"))
            clearInvocations(f.voice)
            f.vm.join("B")
            runCurrent()
            assertNull("The old room must detach before the target lookup completes", f.vm.state.value.room)
            verify(f.voice).disconnect()
            f.vm.join("C")
            runCurrent()
            verify(repository, never()).room("C")
            lookUp.complete(room("B"))
            runCurrent()
            advanceTimeBy(LiveJoinMinimumMs.toLong() + 1)
            runCurrent()
            assertEquals("B", f.vm.state.value.room?.id)
            assertFalse(f.vm.state.value.minimized)
            assertTrue(calls.contains("join" to "B"))
            assertFalse(leave.isCompleted)
            leave.complete(mapOf("serverNow" to 1L))
            runCurrent()
            assertEquals("B", f.vm.state.value.room?.id)
            assertNull(f.vm.state.value.error)
            val clock = LiveViewModel::class.java.getDeclaredField("offset").apply { isAccessible = true }
            assertEquals("Old-room cleanup must not change the new room's playback clock", 0L, clock.getLong(f.vm))
            f.vm.minimize()
            f.vm.join("B")
            runCurrent()
            assertFalse(f.vm.state.value.minimized)
            assertEquals(1, calls.count { it == "join" to "B" })
        } finally { f.close() }
    }

    @Test fun rapidReturnWaitsForOnlyThatRoomsOldLeaveAndIgnoresCleanupFailure() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val leaveA = CompletableDeferred<LiveFields>()
        val leaveB = CompletableDeferred<LiveFields>()
        val calls = mutableListOf<Pair<String, String>>()
        val repository = mock<LiveRepository> {
            onBlocking { room(any()) } doSuspendableAnswer { room(it.getArgument(0)) }
            onBlocking { call(any(), any(), any()) } doSuspendableAnswer {
                val action = it.getArgument<String>(0)
                val id = it.getArgument<String>(1)
                calls += action to id
                if (action == "leave") (if (id == "A") leaveA else leaveB).await() else emptyMap()
            }
            on { observeRoom(any()) } doReturn emptyFlow()
            on { messages(any()) } doReturn emptyFlow()
            on { membership(any(), any()) } doReturn emptyFlow()
            on { ownRequest(any(), any(), any()) } doReturn emptyFlow()
        }
        val f = Fixture(repository)
        try {
            runCurrent()
            f.seed(room("A"))
            f.vm.join("B")
            runCurrent()
            advanceTimeBy(LiveJoinMinimumMs.toLong() + 1)
            runCurrent()
            assertEquals("B", f.vm.state.value.room?.id)
            f.vm.minimize()
            f.vm.join("A")
            runCurrent()
            assertFalse("An old leave must finish before rejoining the same room", calls.contains("join" to "A"))
            leaveA.complete(emptyMap())
            runCurrent()
            advanceTimeBy(LiveJoinMinimumMs.toLong() + 1)
            runCurrent()
            assertEquals("A", f.vm.state.value.room?.id)
            assertFalse(leaveB.isCompleted)
            leaveB.completeExceptionally(IllegalStateException("Offline"))
            runCurrent()
            assertEquals("A", f.vm.state.value.room?.id)
            assertNull(f.vm.state.value.error)
            assertEquals(1, calls.count { it == "leave" to "A" })
            assertEquals(1, calls.count { it == "leave" to "B" })
        } finally { f.close() }
    }

    @Test fun hostMustConfirmAndCancellationKeepsBroadcastAlive() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val calls = mutableListOf<Pair<String, String>>()
        val repository = mock<LiveRepository> {
            onBlocking { room(any()) } doSuspendableAnswer { room(it.getArgument(0)) }
            onBlocking { call(any(), any(), any()) } doSuspendableAnswer {
                calls += it.getArgument<String>(0) to it.getArgument<String>(1)
                emptyMap()
            }
            on { observeRoom(any()) } doReturn emptyFlow()
            on { messages(any()) } doReturn emptyFlow()
            on { membership(any(), any()) } doReturn emptyFlow()
            on { ownRequest(any(), any(), any()) } doReturn emptyFlow()
        }
        val f = Fixture(repository)
        try {
            runCurrent()
            f.seed(room("A"), host = true)
            f.vm.join("B")
            runCurrent()
            assertEquals(true, f.vm.state.value.playbackExitEndsShow)
            assertEquals("A", f.vm.state.value.room?.id)
            assertTrue("No end or join is allowed before confirmation", calls.isEmpty())
            f.vm.confirmPlaybackExit(false)
            runCurrent()
            assertEquals("A", f.vm.state.value.room?.id)
            assertTrue(calls.isEmpty())
            f.vm.join("B")
            runCurrent()
            f.vm.confirmPlaybackExit(true)
            runCurrent()
            advanceTimeBy(LiveJoinMinimumMs.toLong() + 1)
            runCurrent()
            assertEquals("B", f.vm.state.value.room?.id)
            assertTrue(calls.indexOf("end" to "A") < calls.indexOf("join" to "B"))
            f.seed(room("A"), host = true)
            whenever(repository.call("end", "A", emptyMap())).thenThrow(IllegalStateException("Offline"))
            f.vm.join("B")
            runCurrent()
            f.vm.confirmPlaybackExit(true)
            runCurrent()
            assertEquals("A", f.vm.state.value.room?.id)
            assertNotNull(f.vm.state.value.error)
            assertNull(f.vm.state.value.playbackExitEndsShow)
            assertEquals(1, calls.count { it == "join" to "B" })
        } finally { f.close() }
    }
}
