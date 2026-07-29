package fm.corus.android.domain

import android.content.Context
import fm.corus.android.data.local.PreferencesDataStore
import fm.corus.android.data.remote.CloudFunctionsDataSource
import fm.corus.android.data.repository.UserRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock

/**
 * Regression coverage for the foreground-start denial recovery path
 * (ForegroundServiceStartNotAllowedException on API 31+).
 *
 * When Android refuses to promote [fm.corus.android.service.CorusPlaybackService]
 * to the foreground — playback was kicked off while the app was backgrounded —
 * the service catches the exception (previously an uncaught crash:
 * CorusPlaybackService.onStartCommand) and calls
 * [NowPlayingManager.onForegroundStartDenied] to recover instead of crashing.
 *
 * This verifies that hook's public contract: it pauses (clears the playing
 * state) and is safe to call when no player is active, which is exactly the
 * timing in the denial scenario. The service-side try/catch around
 * startForeground() itself is exercised at runtime / on-device, since the
 * system exception can't be faithfully simulated off-device.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class NowPlayingManagerForegroundServiceTest {

    private val testDispatcher = StandardTestDispatcher()
    private val context = mock<Context>()
    private val cloudFunctions = mock<CloudFunctionsDataSource>()
    private val preferencesDataStore = mock<PreferencesDataStore> {
        on { autoplayNextSong } doReturn MutableStateFlow(true)
    }
    private val unfollowEvents = MutableSharedFlow<String>(extraBufferCapacity = 16)
    private val userRepository = mock<UserRepository> {
        on { this.unfollowEvents } doReturn unfollowEvents
    }

    private fun newManager(): NowPlayingManager =
        NowPlayingManager(context, cloudFunctions, preferencesDataStore, userRepository, mock(), mock(), mock(), mock(), mock(), mock())

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun onForegroundStartDenied_clearsPlayingState() = runTest {
        val manager = newManager()

        manager.onForegroundStartDenied()

        assertFalse(manager.isPlaying)
    }

    @Test
    fun onForegroundStartDenied_isSafeWithNoActivePlayer() = runTest {
        val manager = newManager()

        // No track has played, so player == null. The recovery hook fires from
        // onStartCommand at exactly this point in the denial case, so it must
        // not throw on a null player.
        manager.onForegroundStartDenied()
        manager.onForegroundStartDenied()

        assertFalse(manager.isPlaying)
    }
}
