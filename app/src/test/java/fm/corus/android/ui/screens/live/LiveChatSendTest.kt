package fm.corus.android.ui.screens.live

import android.app.Application
import androidx.lifecycle.viewModelScope
import androidx.test.core.app.ApplicationProvider
import fm.corus.android.data.repository.AuthRepository
import fm.corus.android.domain.SaveChangedEvent
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.doSuspendableAnswer
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class LiveChatSendTest {
    @Test
    fun repeatedTapsSendOnceAndFailureAllowsRetry() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val firstRequest = CompletableDeferred<LiveFields>()
        val requests = mutableListOf<LiveFields>()
        val repository = mock<LiveRepository> {
            onBlocking { call(eq("chat"), eq("room"), any()) } doSuspendableAnswer {
                requests += it.getArgument<LiveFields>(2)
                if (requests.size == 1) firstRequest.await() else emptyMap()
            }
        }
        val auth = mock<AuthRepository> {
            on { currentUser } doReturn MutableStateFlow(null)
        }
        val vm = LiveViewModel(ApplicationProvider.getApplicationContext(), repository, auth, mock(),
            mock(), mock(), mock(), mock(), mock(), mock(), SaveChangedEvent(), spotifySaveAutoAdd = mock(), appleMusicSaveAutoAdd = mock())
        try {
            runCurrent()
            val field = LiveViewModel::class.java.getDeclaredField("_state").apply { isAccessible = true }
            @Suppress("UNCHECKED_CAST")
            val state = field.get(vm) as MutableStateFlow<LiveUiState>
            state.value = state.value.copy(
                uid = "listener",
                room = LiveRoom("room", mapOf("hostId" to "host", "status" to "playing", "chatEnabled" to true)),
            )

            var acknowledged = 0
            fun tapSend() = vm.send("yo yooo", null) { acknowledged++ }
            tapSend()
            tapSend()
            runCurrent()
            tapSend()
            runCurrent()
            assertEquals(1, requests.size)
            assertEquals(0, acknowledged)

            firstRequest.completeExceptionally(IllegalStateException("Offline"))
            runCurrent()
            assertEquals(0, acknowledged)
            tapSend()
            runCurrent()
            assertEquals(listOf("yo yooo", "yo yooo"), requests.map { it["text"] })
            assertEquals(1, acknowledged)
        } finally {
            vm.viewModelScope.cancel()
            LiveViewModel::class.java.getDeclaredMethod("onCleared").apply { isAccessible = true }.invoke(vm)
            Dispatchers.resetMain()
        }
    }
}
