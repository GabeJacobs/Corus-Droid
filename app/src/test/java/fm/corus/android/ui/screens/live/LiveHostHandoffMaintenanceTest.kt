package fm.corus.android.ui.screens.live

import android.app.Application
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.viewModelScope
import androidx.test.core.app.ApplicationProvider
import com.google.firebase.functions.FirebaseFunctionsException
import fm.corus.android.data.repository.AuthRepository
import fm.corus.android.domain.SaveChangedEvent
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.*
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class LiveHostHandoffMaintenanceTest {
    private class Fixture {
        val pending = CompletableDeferred<LiveFields>()
        val repository = mock<LiveRepository> {
            on { discovery(any()) } doReturn emptyFlow()
            on { invites(any()) } doReturn emptyFlow()
            onBlocking { call(any(), any(), any()) } doSuspendableAnswer { pending.await() }
        }
        val auth = mock<AuthRepository> {
            on { currentUser } doReturn MutableStateFlow(null)
            on { currentUserId } doReturn "outgoing"
        }
        val vm = LiveViewModel(ApplicationProvider.getApplicationContext(), repository, auth, mock(),
            mock(), mock(), mock(), mock(), mock(), mock(), SaveChangedEvent(),
            spotifySaveAutoAdd = mock(), appleMusicSaveAutoAdd = mock())
        @Suppress("UNCHECKED_CAST")
        val state = LiveViewModel::class.java.getDeclaredField("_state").apply { isAccessible = true }
            .get(vm) as MutableStateFlow<LiveUiState>
        val owner = mock<LifecycleOwner>()
        val original = LiveRoom("room", mapOf("hostId" to "outgoing", "status" to "waiting"))
        val transferred = LiveRoom("room", mapOf("hostId" to "incoming", "status" to "waiting",
            "djs" to listOf(mapOf("uid" to "outgoing", "order" to 1, "queue" to emptyList<Any>(), "version" to 0))))
        fun seed(room: LiveRoom) {
            state.value = state.value.copy(uid = "outgoing", room = room)
        }
        fun deny() = pending.completeExceptionally(mock<FirebaseFunctionsException> {
            on { code } doReturn FirebaseFunctionsException.Code.PERMISSION_DENIED
        })
        fun close() {
            vm.viewModelScope.cancel()
            LiveViewModel::class.java.getDeclaredMethod("onCleared").apply { isAccessible = true }.invoke(vm)
            Dispatchers.resetMain()
        }
    }

    @Test fun rejectedHostHeartbeatAfterHandoffKeepsTheOutgoingDJInTheRoom() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val f = Fixture()
        try {
            runCurrent()
            f.seed(f.original)
            f.vm.onStop(f.owner)
            runCurrent()
            verify(f.repository).call(eq("tick"), eq("room"), any())
            f.seed(f.transferred)
            f.deny()
            runCurrent()
            assertEquals(f.transferred, f.vm.state.value.room)
            assertNotNull(f.vm.state.value.dj)
            assertNull(f.vm.state.value.error)
        } finally { f.close() }
    }

    @Test fun rejectedMembershipPresenceStillRemovesTheParticipant() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val f = Fixture()
        try {
            runCurrent()
            f.seed(f.transferred)
            f.vm.onStop(f.owner)
            runCurrent()
            verify(f.repository).call(eq("presence"), eq("room"), any())
            f.deny()
            runCurrent()
            assertNull(f.vm.state.value.room)
            assertNotNull(f.vm.state.value.error)
        } finally { f.close() }
    }
}
