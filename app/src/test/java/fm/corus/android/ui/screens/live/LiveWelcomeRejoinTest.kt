package fm.corus.android.ui.screens.live

import android.app.Application
import android.media.MediaPlayer
import androidx.lifecycle.viewModelScope
import androidx.test.core.app.ApplicationProvider
import fm.corus.android.data.repository.AuthRepository
import fm.corus.android.domain.SaveChangedEvent
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowMediaPlayer
import org.robolectric.shadows.util.DataSource

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class LiveWelcomeRejoinTest {
    @Test
    fun rejoinSkipsWelcomeAfterPlaybackStartsButReplayIsStillAvailable() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val url = "https://example.test/welcome.m4a"
        ShadowMediaPlayer.addMediaInfo(DataSource.toDataSource(url), ShadowMediaPlayer.MediaInfo(5_000, 0))
        val repository = mock<LiveRepository> {
            on { observeRoom(any()) } doReturn emptyFlow()
            on { messages(any()) } doReturn emptyFlow()
            on { membership(any(), any()) } doReturn emptyFlow()
            on { ownRequest(any(), any(), any()) } doReturn emptyFlow()
        }
        val auth = mock<AuthRepository> {
            on { currentUser } doReturn MutableStateFlow(null)
        }
        val vm = LiveViewModel(ApplicationProvider.getApplicationContext(), repository, auth, mock(),
            mock(), mock(), mock(), mock(), mock(), mock(), SaveChangedEvent(), spotifySaveAutoAdd = mock(), appleMusicSaveAutoAdd = mock())
        val type = LiveViewModel::class.java
        val mount = type.getDeclaredMethod("mount", LiveRoom::class.java).apply { isAccessible = true }
        val close = type.getDeclaredMethod("closeRoom", LiveRoom::class.java).apply { isAccessible = true }
        val player = type.getDeclaredField("welcomePlayer").apply { isAccessible = true }
        val fields = mapOf(
            "hostId" to "host", "status" to "waiting", "leaseUntil" to Long.MAX_VALUE,
            "welcomeMessage" to mapOf("url" to url),
        )
        val room = LiveRoom("room", fields)
        try {
            runCurrent()
            @Suppress("UNCHECKED_CAST")
            val state = type.getDeclaredField("_state").apply { isAccessible = true }.get(vm) as MutableStateFlow<LiveUiState>
            state.value = state.value.copy(uid = "listener")

            mount.invoke(vm, room)
            assertTrue(vm.state.value.welcomePlaying)
            close.invoke(vm, null)
            mount.invoke(vm, room)
            assertTrue("Leaving before playback starts must allow the welcome on rejoin", vm.state.value.welcomePlaying)

            val audio = player.get(vm) as MediaPlayer
            shadowOf(audio).invokePreparedListener()
            runCurrent()
            assertTrue(audio.isPlaying)
            close.invoke(vm, null)
            mount.invoke(vm, room)
            assertFalse("A welcome that started should be skipped on rejoin", vm.state.value.welcomePlaying)
            vm.replayWelcome()
            assertTrue(vm.state.value.welcomePlaying)

            close.invoke(vm, null)
            mount.invoke(vm, LiveRoom("another-room", fields))
            assertTrue("A new room should play its own welcome", vm.state.value.welcomePlaying)
        } finally {
            vm.viewModelScope.cancel()
            type.getDeclaredMethod("onCleared").apply { isAccessible = true }.invoke(vm)
            Dispatchers.resetMain()
        }
    }
}
