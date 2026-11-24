package fm.corus.android.ui.screens.live

import android.app.Application
import androidx.lifecycle.viewModelScope
import androidx.test.core.app.ApplicationProvider
import fm.corus.android.data.repository.AuthRepository
import fm.corus.android.domain.SaveChangedEvent
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
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class LiveMentionResolutionTest {
    @Test fun loadedRoomProfileResolvesWithoutAUsernameQuery() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val repository = mock<LiveRepository> {
            on { cachedChatReactionPeople(any()) } doReturn mapOf("gabe-id" to LivePerson("gabe-id", "gabe", null))
        }
        val auth = mock<AuthRepository> {
            on { currentUser } doReturn MutableStateFlow(null)
            on { userProfile } doReturn MutableStateFlow(null)
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
                room = LiveRoom("room", mapOf("hostId" to "gabe-id", "hostUsername" to "gabe")),
            )
            assertEquals("gabe-id", vm.chatMentionUserId("gabe"))
            verify(repository, never()).chatMentionUserId(any())
        } finally {
            vm.viewModelScope.cancel()
            LiveViewModel::class.java.getDeclaredMethod("onCleared").apply { isAccessible = true }.invoke(vm)
            Dispatchers.resetMain()
        }
    }
}
