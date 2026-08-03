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
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock

/**
 * Tests that [NowPlayingManager] correctly drives [ScrubberClock] for the
 * mini-player scrubber port from iOS:
 *  - `skipToNext()` snaps the scrubber to 0 instantly so the outgoing track's
 *    position doesn't briefly tween into the new one.
 *  - `seek(toMs)` updates the clock eagerly so the scrubber doesn't flash
 *    backward to the pre-seek position before the next poll lands.
 *  - `stop()` resets the clock so the scrubber clears when playback teardown
 *    happens.
 *
 * These verify the public API contract; the actual ExoPlayer-driven polling
 * loop requires Android instrumentation and is exercised at runtime.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class NowPlayingManagerScrubberTest {

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
        NowPlayingManager(context, cloudFunctions, preferencesDataStore, userRepository, mock(), mock(), mock(), mock(), mock(), mock(), mock())

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        ScrubberClock.reset()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        ScrubberClock.reset()
    }

    @Test
    fun skipToNext_resetsScrubberClock() = runTest {
        val manager = newManager()
        ScrubberClock.update(time = 12000L, duration = 30000L)

        manager.skipToNext()

        assertEquals(0L, ScrubberClock.time.value)
        assertEquals(0L, ScrubberClock.duration.value)
    }

    @Test
    fun seek_setsScrubberClockTimeImmediately() = runTest {
        val manager = newManager()
        ScrubberClock.update(time = 1000L, duration = 30000L)

        manager.seek(15000L)

        assertEquals(15000L, ScrubberClock.time.value)
        // Duration unaffected by seek.
        assertEquals(30000L, ScrubberClock.duration.value)
    }

    @Test
    fun seek_clampsNegativeToZero() = runTest {
        val manager = newManager()
        ScrubberClock.update(time = 5000L, duration = 30000L)

        manager.seek(-100L)

        assertEquals(0L, ScrubberClock.time.value)
    }

    @Test
    fun stop_resetsScrubberClock() = runTest {
        val manager = newManager()
        ScrubberClock.update(time = 8000L, duration = 30000L)

        manager.stop()

        assertEquals(0L, ScrubberClock.time.value)
        assertEquals(0L, ScrubberClock.duration.value)
    }
}
