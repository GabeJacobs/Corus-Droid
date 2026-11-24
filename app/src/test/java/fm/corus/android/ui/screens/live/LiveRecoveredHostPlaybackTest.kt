package fm.corus.android.ui.screens.live

import android.app.Application
import androidx.lifecycle.viewModelScope
import androidx.test.core.app.ApplicationProvider
import com.google.firebase.auth.FirebaseUser
import fm.corus.android.data.repository.AuthRepository
import fm.corus.android.domain.NowPlayingManager
import fm.corus.android.domain.SaveChangedEvent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class LiveRecoveredHostPlaybackTest {
    @Test
    fun startupLookupRetriesAndShowsTheHostedRoomWithoutMountingIt() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val repository = mock<LiveRepository>()
        val user = mock<FirebaseUser> { on { uid } doReturn "host" }
        val auth = mock<AuthRepository> {
            on { currentUser } doReturn MutableStateFlow<FirebaseUser?>(user)
        }
        val room = LiveRoom("room", mapOf("hostId" to user.uid, "status" to "waiting"))
        whenever(repository.hostedRoom("host")).thenThrow(IllegalStateException("Offline")).thenReturn(room, null)
        val vm = LiveViewModel(ApplicationProvider.getApplicationContext(), repository, auth, mock(),
            mock(), mock(), mock(), mock(), mock(), mock(), SaveChangedEvent(), spotifySaveAutoAdd = mock(), appleMusicSaveAutoAdd = mock())
        try {
            val lookup = async { vm.refreshHostedRoomPreview("host") }
            runCurrent()
            assertNull(vm.state.value.miniPlayerRoom)
            advanceTimeBy(10_001)
            assertEquals(room, vm.state.value.miniPlayerRoom)
            assertNull(vm.state.value.room)
            advanceTimeBy(10_001)
            assertNull(vm.state.value.miniPlayerRoom)
            lookup.await()
        } finally {
            vm.viewModelScope.cancel()
            LiveViewModel::class.java.getDeclaredMethod("onCleared").apply { isAccessible = true }.invoke(vm)
            Dispatchers.resetMain()
        }
    }

    @Test
    fun recoveredHostMustConfirmBeforePlayback() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val repository = mock<LiveRepository>()
        val player = mock<NowPlayingManager>()
        val user = mock<FirebaseUser> { on { uid } doReturn "host" }
        val users = MutableStateFlow<FirebaseUser?>(null)
        val auth = mock<AuthRepository> {
            on { currentUser } doReturn users
            on { currentUserId } doReturn "host"
        }
        val vm = LiveViewModel(ApplicationProvider.getApplicationContext(), repository, auth, mock(),
            mock(), player, mock(), mock(), mock(), mock(), SaveChangedEvent(), spotifySaveAutoAdd = mock(), appleMusicSaveAutoAdd = mock())
        try {
            runCurrent()
            val room = LiveRoom("room", mapOf("hostId" to user.uid, "status" to "waiting"))
            // Seed the state delivered by the startup lookup, without opening the room.
            val field = LiveViewModel::class.java.getDeclaredField("_state").apply { isAccessible = true }
            @Suppress("UNCHECKED_CAST")
            val state = field.get(vm) as MutableStateFlow<LiveUiState>
            users.value = user
            runCurrent()
            state.value = state.value.copy(recoverableHostRoom = room)
            assertEquals(room, vm.state.value.miniPlayerRoom)
            whenever(repository.call("end", room.id, emptyMap())).thenReturn(emptyMap())
            val callback = argumentCaptor<suspend () -> Boolean>()
            verify(player).livePlaybackExit = callback.capture()

            val cancelled = async { callback.firstValue() }
            runCurrent()
            assertFalse("Playback must wait for confirmation", cancelled.isCompleted)
            vm.confirmPlaybackExit(false)
            assertFalse(cancelled.await())
            assertEquals(room, vm.state.value.miniPlayerRoom)

            val confirmed = async { callback.firstValue() }
            runCurrent()
            vm.confirmPlaybackExit(true)
            assertTrue(confirmed.await())
            verify(repository).call("end", room.id, emptyMap())
            assertNull(vm.state.value.miniPlayerRoom)
        } finally {
            vm.viewModelScope.cancel()
            LiveViewModel::class.java.getDeclaredMethod("onCleared").apply { isAccessible = true }.invoke(vm)
            Dispatchers.resetMain()
        }
    }
}
