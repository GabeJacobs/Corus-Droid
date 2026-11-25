package fm.corus.android.ui.screens.live

import android.app.Application
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.viewModelScope
import androidx.test.core.app.ApplicationProvider
import fm.corus.android.data.model.MusicService
import fm.corus.android.data.repository.AuthRepository
import fm.corus.android.domain.MusicServicePreference
import fm.corus.android.domain.NowPlayingManager
import fm.corus.android.domain.SaveChangedEvent
import fm.corus.android.domain.SpotifyPlaybackService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
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

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class LiveSpotifyPreviewLifecycleTest {
    @Test fun backgroundRevisionsStaySuspendedAndReturnClearsOldConfirmation() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        var previewing = false
        val spotify = mock<SpotifyPlaybackService>()
        whenever(spotify.isLiveListenerPreviewSuspended).thenAnswer { previewing }
        val player = mock<NowPlayingManager>()
        whenever(player.endLiveSpotifyPlayback(any())).thenReturn(Job())
        val repository = mock<LiveRepository>()
        whenever(repository.discovery(any())).thenReturn(emptyFlow())
        whenever(repository.invites(any())).thenReturn(emptyFlow())
        val auth = mock<AuthRepository> {
            on { currentUser } doReturn MutableStateFlow(null)
            on { currentUserId } doReturn "listener"
        }
        val music = mock<MusicServicePreference> {
            on { current } doReturn MutableStateFlow(MusicService.SPOTIFY)
        }
        val vm = LiveViewModel(ApplicationProvider.getApplicationContext(), repository, auth, spotify,
            mock(), player, music, mock(), mock(), mock(), SaveChangedEvent(),
            spotifySaveAutoAdd = mock(), appleMusicSaveAutoAdd = mock())
        fun field(name: String) = LiveViewModel::class.java.getDeclaredField(name).apply { isAccessible = true }
        try {
            runCurrent()
            val room = LiveRoom("room", mapOf("hostId" to "host", "status" to "playing",
                "current" to mapOf("id" to "room-song", "title" to "Song", "durationMs" to 180_000),
                "deadline" to Long.MAX_VALUE, "leaseUntil" to Long.MAX_VALUE, "revision" to 2L))
            @Suppress("UNCHECKED_CAST")
            val state = field("_state").get(vm) as MutableStateFlow<LiveUiState>
            state.value = state.value.copy(uid = "listener", room = room, audioReady = true)
            field("confirmedRevision").set(vm, 1L)
            field("expectedUri").set(vm, "spotify:track:old")
            previewing = true
            clearInvocations(spotify)
            val synchronize = LiveViewModel::class.java.getDeclaredMethod("synchronize", LiveRoom::class.java, Boolean::class.javaPrimitiveType)
                .apply { isAccessible = true }
            assertNull(synchronize.invoke(vm, room, false))
            verify(spotify, never()).pauseImmediately()
            verify(spotify, never()).followLiveListenerTrack(any(), any())
            // The return handler clears confirmation before scheduling the new
            // room revision, so it cannot resume the outgoing song at an old time.
            vm.onStart(mock<LifecycleOwner>())
            verify(spotify).liveListenerTrackUri = null
            assertNull(field("confirmedRevision").get(vm))
            assertNull(field("expectedUri").get(vm))
            assertEquals("room", vm.state.value.room?.id)
            // Explicit room exit must also leave Spotify's selected song alone.
            field("ownsLivePlayback").setBoolean(vm, true)
            field("expectedUri").set(vm, "spotify:track:old")
            LiveViewModel::class.java.getDeclaredMethod("releasePlayback").apply { isAccessible = true }.invoke(vm)
            verify(player).endLiveSpotifyPlayback(pauseSpotify = false)
        } finally {
            vm.viewModelScope.cancel()
            LiveViewModel::class.java.getDeclaredMethod("onCleared").apply { isAccessible = true }.invoke(vm)
            Dispatchers.resetMain()
        }
    }
}
