package fm.corus.android.ui.screens.live

import android.app.Application
import androidx.lifecycle.viewModelScope
import androidx.test.core.app.ApplicationProvider
import com.google.firebase.firestore.FirebaseFirestoreException
import fm.corus.android.data.repository.AuthRepository
import fm.corus.android.domain.SaveChangedEvent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class LiveBackgroundDiscoveryTest {
    @Test fun startupListenersRetryWithoutAlertAndStopWhenAccountChanges() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val repository = mock<LiveRepository>()
        val auth = mock<AuthRepository> { on { currentUser } doReturn MutableStateFlow(null) }
        var discoveryAttempts = 0
        var inviteAttempts = 0
        val denied = FirebaseFirestoreException("Denied", FirebaseFirestoreException.Code.PERMISSION_DENIED)
        val room = LiveRoom("public-room", mapOf("status" to "playing", "hostId" to "other",
            "deadline" to Long.MAX_VALUE, "leaseUntil" to Long.MAX_VALUE))
        whenever(repository.discovery(true)).thenReturn(flow {
            discoveryAttempts++
            if (discoveryAttempts == 1) throw denied
            emit(listOf(room))
            awaitCancellation()
        })
        whenever(repository.invites("tester")).thenReturn(flow {
            inviteAttempts++
            throw denied
        })
        val vm = LiveViewModel(ApplicationProvider.getApplicationContext(), repository, auth, mock(),
            mock(), mock(), mock(), mock(), mock(), mock(), SaveChangedEvent(), spotifySaveAutoAdd = mock(), appleMusicSaveAutoAdd = mock())
        try {
            runCurrent()
            val field = LiveViewModel::class.java.getDeclaredField("_state").apply { isAccessible = true }
            @Suppress("UNCHECKED_CAST")
            val state = field.get(vm) as MutableStateFlow<LiveUiState>
            state.value = LiveUiState(uid = "tester", access = LiveAccess(true, false, false, false,
                false, false, false, privacyMetadataSupported = true))
            runCurrent()
            assertEquals(1, discoveryAttempts)
            assertEquals(1, inviteAttempts)
            assertNull("Passive startup failures must not open an alert", vm.state.value.error)
            advanceTimeBy(2_001)
            assertEquals(2, discoveryAttempts)
            assertEquals(2, inviteAttempts)
            assertEquals(listOf(room), vm.state.value.discovery)
            assertNull(vm.state.value.error)
            whenever(repository.room("unavailable")).thenAnswer { throw denied }
            vm.join("unavailable")
            runCurrent()
            advanceTimeBy(1_801)
            assertNotNull("A deliberate join failure must still show its error", vm.state.value.error)
            vm.clearError()
            state.value = LiveUiState()
            runCurrent()
            advanceTimeBy(60_001)
            assertEquals("Signing out must cancel pending retries", 2, inviteAttempts)
        } finally {
            vm.viewModelScope.cancel()
            LiveViewModel::class.java.getDeclaredMethod("onCleared").apply { isAccessible = true }.invoke(vm)
            Dispatchers.resetMain()
        }
    }
}
