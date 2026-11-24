package fm.corus.android.ui.screens.live

import android.app.Application
import androidx.lifecycle.viewModelScope
import androidx.test.core.app.ApplicationProvider
import fm.corus.android.data.model.CymbalTrack
import fm.corus.android.data.model.TrackSource
import fm.corus.android.data.repository.AuthRepository
import fm.corus.android.domain.SaveChangedEvent
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
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
class LiveQueueAdditionTest {
    @Test fun additionFinishesAfterPickerScopeEnds() = checkAddition(fail = false)
    @Test fun unavailableAdditionReportsRoomErrorWithoutSaving() = checkAddition(fail = true)
    @Test fun stalePickerAdditionSendsOnlyNewSongsWithoutVersion() = checkAddition(fail = false, stale = true)

    private fun checkAddition(fail: Boolean, stale: Boolean = false) = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val audio = CompletableDeferred<String?>()
        val track = LiveTrack(CymbalTrack(id = "sc:1", name = "Song", artistName = "Artist",
            albumName = "Album", durationMs = 120000,
            source = TrackSource.SOUNDCLOUD, soundcloudId = "1"))
        val sent = mutableListOf<LiveFields>()
        val repository = mock<LiveRepository> {
            onBlocking { sourceAudioUrl(track) } doSuspendableAnswer { audio.await() }
            onBlocking { call(eq("queue"), eq("room"), any()) } doSuspendableAnswer {
                sent += it.getArgument<LiveFields>(2)
                emptyMap()
            }
        }
        val auth = mock<AuthRepository> { on { currentUser } doReturn MutableStateFlow(null) }
        val vm = LiveViewModel(ApplicationProvider.getApplicationContext(), repository, auth, mock(),
            mock(), mock(), mock(), mock(), mock(), mock(), SaveChangedEvent(), spotifySaveAutoAdd = mock(), appleMusicSaveAutoAdd = mock())
        try {
            runCurrent()
            val field = LiveViewModel::class.java.getDeclaredField("_state").apply { isAccessible = true }
            @Suppress("UNCHECKED_CAST")
            val state = field.get(vm) as MutableStateFlow<LiveUiState>
            state.value = state.value.copy(uid = "host",
                room = LiveRoom("room", mapOf("hostId" to "host", "status" to "playing", "queueVersion" to if (stale) 5L else 0L)))
            val picker = launch { assertTrue(vm.updateQueue(listOf(track), 0, true,
                previousQueue = if (stale) emptyList() else null)) }
            runCurrent()
            picker.cancel()
            assertTrue("Queue must wait for audio validation", sent.isEmpty())
            assertTrue(vm.state.value.queueUpdating)
            audio.complete(if (fail) null else "https://example.com/audio.mp3")
            runCurrent()
            if (fail) {
                assertTrue(sent.isEmpty())
                assertNotNull(vm.state.value.error)
                assertFalse(vm.state.value.queueUpdating)
            } else {
                assertEquals(listOf(track.fields()), sent.single()["queue"])
                if (stale) {
                    assertEquals(true, sent.single()["append"])
                    assertNotNull(sent.single()["requestId"])
                    assertFalse(sent.single().containsKey("queueVersion"))
                }
            }
        } finally {
            vm.viewModelScope.cancel()
            LiveViewModel::class.java.getDeclaredMethod("onCleared").apply { isAccessible = true }.invoke(vm)
            Dispatchers.resetMain()
        }
    }
}
