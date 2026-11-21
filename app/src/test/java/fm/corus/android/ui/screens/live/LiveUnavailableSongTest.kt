package fm.corus.android.ui.screens.live

import android.app.Application
import android.content.ComponentName
import android.content.Intent
import android.content.IntentFilter
import androidx.lifecycle.viewModelScope
import androidx.test.core.app.ApplicationProvider
import fm.corus.android.data.model.MusicService
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
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.*
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class LiveUnavailableSongTest {
    private val room = LiveRoom("room", mapOf("hostId" to "host", "revision" to 3L))

    @Test fun hostSeesUnavailableStatusForTheirProvider() {
        assertTrue(LiveUiState(uid = "host", room = room, spotifyUnavailableRevision = 3L).isSpotifySongUnavailable)
    }

    @Test fun listenerSeesUnavailableStatusOnlyForTheAffectedSong() {
        val state = LiveUiState(uid = "listener", room = room, spotifyUnavailableRevision = 3L)
        assertTrue(state.isSpotifySongUnavailable)
        assertFalse(state.copy(room = LiveRoom("room", mapOf("revision" to 4L))).isSpotifySongUnavailable)
        assertFalse(state.copy(spotifyUnavailableRevision = null).isSpotifySongUnavailable)
    }

    @Test fun unavailableSpotifySkipStillAdvancesTheSharedRoom() = checkSkip(transportFailure = false)

    @Test fun failedLookupDoesNotAdvanceTheSharedRoom() = checkSkip(transportFailure = true)

    private fun checkSkip(transportFailure: Boolean) = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val context = ApplicationProvider.getApplicationContext<Application>()
        val launcher = ComponentName("com.spotify.music", "com.spotify.music.MainActivity")
        Shadows.shadowOf(context.packageManager).apply {
            addActivityIfNotPresent(launcher)
            addIntentFilterForActivity(launcher, IntentFilter(Intent.ACTION_MAIN).apply { addCategory(Intent.CATEGORY_LAUNCHER) })
        }
        val commands = mutableListOf<Pair<String, LiveFields>>()
        val repository = mock<LiveRepository> {
            onBlocking { call(any(), eq("room"), any()) } doSuspendableAnswer {
                commands += it.getArgument<String>(0) to it.getArgument<LiveFields>(2)
                emptyMap()
            }
        }
        whenever(repository.discovery(any())).thenReturn(emptyFlow())
        if (transportFailure) whenever(repository.spotifyUri(any())).thenThrow(IllegalStateException("Offline"))
        else whenever(repository.spotifyUri(any())).thenReturn(null)
        val auth = mock<AuthRepository> { on { currentUser } doReturn MutableStateFlow(null) }
        val vm = LiveViewModel(context, repository, auth, mock(), mock(), mock(), mock(), mock(), mock(), mock(), SaveChangedEvent())
        try {
            runCurrent()
            val song = mapOf("id" to "am:123", "source" to "applemusic", "title" to "Robot Stop", "durationMs" to 180_000)
            val playingRoom = LiveRoom("room", mapOf("hostId" to "host", "revision" to 3L,
                "status" to "playing", "current" to song, "queue" to listOf(song)))
            @Suppress("UNCHECKED_CAST")
            val state = LiveViewModel::class.java.getDeclaredField("_state").apply { isAccessible = true }.get(vm) as MutableStateFlow<LiveUiState>
            state.value = LiveUiState(uid = "host", room = playingRoom,
                access = LiveAccess(false, true, false, false, false, false, false))
            LiveViewModel::class.java.getDeclaredField("liveProvider").apply { isAccessible = true }.set(vm, MusicService.SPOTIFY)
            vm.skip("room", 3L)
            runCurrent()
            if (transportFailure) {
                assertEquals(listOf("beginSkip", "cancelSkip"), commands.map { it.first })
                assertNull(vm.state.value.spotifyUnavailableRevision)
            } else {
                assertEquals(listOf("beginSkip", "completeSkip"), commands.map { it.first })
                assertEquals(0L, commands.last().second["positionMs"])
                assertEquals(4L, vm.state.value.spotifyUnavailableRevision)
                assertNull(vm.state.value.error)
                assertNull(vm.state.value.audioError)
            }
        } finally {
            vm.viewModelScope.cancel()
            LiveViewModel::class.java.getDeclaredMethod("onCleared").apply { isAccessible = true }.invoke(vm)
            Dispatchers.resetMain()
        }
    }
}
