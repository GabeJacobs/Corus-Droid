package fm.corus.android.domain

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
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
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Regression coverage for the foreground-start denial recovery path
 * (ForegroundServiceStartNotAllowedException on API 31+).
 *
 * Android can refuse the promotion in two places:
 * 1. [NowPlayingManager.startForegroundServiceIfNeeded] —
 *    `startForegroundService()` itself throws while the app is backgrounded
 *    (1.4.2 crash: Spotify failure → preview fallback).
 * 2. [fm.corus.android.service.CorusPlaybackService.onStartCommand] —
 *    `startForeground()` throws after the service process has started.
 *
 * Both recover through [NowPlayingManager.onForegroundStartDenied]: pause,
 * clear the started flag, and let the next foreground play() retry.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class NowPlayingManagerForegroundServiceTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var context: Context
    private val cloudFunctions = mock<CloudFunctionsDataSource>()
    private val preferencesDataStore = mock<PreferencesDataStore> {
        on { autoplayNextSong } doReturn MutableStateFlow(true)
    }
    private val unfollowEvents = MutableSharedFlow<String>(extraBufferCapacity = 16)
    private val userRepository = mock<UserRepository> {
        on { this.unfollowEvents } doReturn unfollowEvents
    }

    private fun newManager(): NowPlayingManager =
        NowPlayingManager(context, cloudFunctions, preferencesDataStore, userRepository, mock(), mock(), mock(), mock(), mock(), mock(), mock(), mock(), mock(), mock(), mock())

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        context = ApplicationProvider.getApplicationContext()
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

    @Test
    fun startForegroundServiceIfNeeded_denial_recoversWithoutThrowing() = runTest {
        val manager = newManager()
        manager.startForegroundServiceAction = {
            throw IllegalStateException("startForegroundService() not allowed")
        }

        assertFalse(manager.startForegroundServiceIfNeeded())
        assertFalse(manager.isPlaying)
    }

    @Test
    fun startForegroundServiceIfNeeded_denial_allowsLaterRetry() = runTest {
        val manager = newManager()
        var attempts = 0
        manager.startForegroundServiceAction = {
            attempts++
            if (attempts == 1) {
                throw IllegalStateException("startForegroundService() not allowed")
            }
        }

        assertFalse(manager.startForegroundServiceIfNeeded())
        assertTrue(manager.startForegroundServiceIfNeeded())
        assertTrue(manager.startForegroundServiceIfNeeded())
        assertEquals(2, attempts)
    }
}
