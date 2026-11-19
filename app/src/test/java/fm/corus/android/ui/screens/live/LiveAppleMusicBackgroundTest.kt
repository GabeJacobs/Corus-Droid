package fm.corus.android.ui.screens.live

import android.app.Application
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.viewModelScope
import androidx.media3.common.Player
import androidx.test.core.app.ApplicationProvider
import fm.corus.android.data.model.MusicService
import fm.corus.android.data.model.TrackSource
import fm.corus.android.data.repository.AuthRepository
import fm.corus.android.domain.AppleMusicLiveQueue
import fm.corus.android.domain.AppleMusicPlayback
import fm.corus.android.domain.SaveChangedEvent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.isNull
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class LiveAppleMusicBackgroundTest {
    @Test
    fun lockingPreservesTheNativeQueueAndReportsTheTrackThatStarted() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val repository = mock<LiveRepository>()
        val appleMusic = mock<AppleMusicPlayback>()
        val auth = mock<AuthRepository> {
            on { currentUser } doReturn MutableStateFlow(null)
        }
        val vm = LiveViewModel(ApplicationProvider.getApplicationContext(), repository, auth, mock(),
            appleMusic, mock(), mock(), mock(), mock(), mock(), SaveChangedEvent())
        try {
            runCurrent()
            val now = System.currentTimeMillis()
            val room = LiveRoom("room", mapOf(
                "hostId" to "host", "status" to "playing", "revision" to 4L, "queueVersion" to 2L,
                "startedAt" to now, "deadline" to now + 120_000, "leaseUntil" to now + 180_000,
                "current" to mapOf("id" to "first", "title" to "First", "source" to "applemusic", "durationMs" to 180_000),
                "queue" to listOf(mapOf("id" to "next", "title" to "Next", "source" to "applemusic", "durationMs" to 180_000)),
            ))
            val nativePlayer = mock<NativePlayer> {
                on { isPlaying } doReturn true
                on { playbackState } doReturn Player.STATE_READY
                on { stagedPosition("next", 4L) } doReturn null
            }
            whenever(appleMusic.resolveConnectedSong(any())).thenReturn("apple-next")
            whenever(repository.discovery(any())).thenReturn(emptyFlow())
            whenever(repository.observeRoom(room.id)).thenReturn(emptyFlow())
            whenever(repository.messages(room.id)).thenReturn(emptyFlow())
            whenever(repository.membership(room.id, "host")).thenReturn(emptyFlow())
            whenever(repository.ownRequest(eq(room.id), any(), eq("host"))).thenReturn(emptyFlow())
            whenever(repository.call(any(), eq(room.id), any())).thenReturn(emptyMap())

            // Start with a mounted host whose current Apple Music song already synced.
            @Suppress("UNCHECKED_CAST")
            val uiState = field("_state").get(vm) as MutableStateFlow<LiveUiState>
            uiState.value = LiveUiState(
                uid = "host", room = room,
                access = LiveAccess(false, false, false, false, false, false, false,
                    appleMusicHostAutoplay = true, appleMusicHostEarlyAdvance = true),
            )
            field("liveProvider").set(vm, MusicService.APPLE_MUSIC)
            field("confirmedRevision").set(vm, room.revision)
            field("applePlayer").set(vm, nativePlayer)
            field("appleTrackKey").set(vm, TrackSource.APPLEMUSIC to "first")
            field("foreground").setBoolean(vm, true)
            LiveViewModel::class.java.getDeclaredMethod("observe", String::class.java)
                .apply { isAccessible = true }.invoke(vm, room.id)
            runCurrent()

            verify(nativePlayer).stageNext(eq("apple-next"), eq(4L), any())

            vm.onStop(mock<LifecycleOwner>())
            advanceTimeBy(501)
            runCurrent()
            verify(nativePlayer, never()).stageNext(isNull(), eq(4L), isNull())
            verify(repository, never()).call(eq("confirmAppleMusicAdvance"), eq(room.id), any())

            whenever(nativePlayer.stagedPosition("next", 4L)).thenReturn(600L)
            advanceTimeBy(501)
            runCurrent()

            verify(repository).call("confirmAppleMusicAdvance", room.id, mapOf(
                "revision" to 4L, "queueVersion" to 2L, "trackId" to "next",
                "trackSource" to "applemusic", "positionMs" to 600L,
            ))
        } finally {
            vm.viewModelScope.cancel()
            LiveViewModel::class.java.getDeclaredMethod("onCleared").apply { isAccessible = true }.invoke(vm)
            Dispatchers.resetMain()
        }
    }

    private fun field(name: String) = LiveViewModel::class.java.getDeclaredField(name).apply { isAccessible = true }

    private interface NativePlayer : Player, AppleMusicLiveQueue
}
