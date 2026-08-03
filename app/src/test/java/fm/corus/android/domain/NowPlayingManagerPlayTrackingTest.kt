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
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.doSuspendableAnswer
import org.mockito.kotlin.stub
import org.mockito.kotlin.times
import org.mockito.kotlin.verifyBlocking

/**
 * Tests for in-app play reporting in [NowPlayingManager].
 *
 * `recordPlayIfNeeded(postId)` fires the `recordPlay` callable once per unique
 * corus per app session (the backend is the lifetime-unique dedup). It must:
 *  - call exactly once for a given postId, then no-op on repeats this session,
 *  - ignore a null postId (track with no source post),
 *  - drop its session marker on failure so a genuine play can retry.
 *
 * The method is private and fire-and-forget, so we invoke it via reflection and
 * drain the manager scope with [advanceUntilIdle] (mirrors the reflection helper
 * used by the other NowPlayingManager tests).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class NowPlayingManagerPlayTrackingTest {

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

    private fun NowPlayingManager.recordPlayIfNeeded(postId: String?) {
        val m = NowPlayingManager::class.java
            .getDeclaredMethod("recordPlayIfNeeded", String::class.java)
            .apply { isAccessible = true }
        m.invoke(this, postId)
    }

    @Before
    fun setUp() { Dispatchers.setMain(testDispatcher) }

    @After
    fun tearDown() { Dispatchers.resetMain() }

    @Test
    fun `reports a play once per unique corus`() = runTest(testDispatcher) {
        val manager = newManager()

        manager.recordPlayIfNeeded("post-1")
        manager.recordPlayIfNeeded("post-1") // repeat — should be a no-op this session
        advanceUntilIdle()

        verifyBlocking(cloudFunctions, times(1)) { recordPlay("post-1") }
    }

    @Test
    fun `ignores a null source post id`() = runTest(testDispatcher) {
        val manager = newManager()

        manager.recordPlayIfNeeded(null)
        advanceUntilIdle()

        verifyBlocking(cloudFunctions, times(0)) { recordPlay(org.mockito.kotlin.any()) }
    }

    @Test
    fun `reports distinct corus ids independently`() = runTest(testDispatcher) {
        val manager = newManager()

        manager.recordPlayIfNeeded("post-1")
        manager.recordPlayIfNeeded("post-2")
        advanceUntilIdle()

        verifyBlocking(cloudFunctions, times(1)) { recordPlay("post-1") }
        verifyBlocking(cloudFunctions, times(1)) { recordPlay("post-2") }
    }

    @Test
    fun `retries after a failure clears the session marker`() = runTest(testDispatcher) {
        var calls = 0
        cloudFunctions.stub {
            onBlocking { recordPlay("post-1") } doSuspendableAnswer {
                calls++
                if (calls == 1) throw RuntimeException("transient") else Unit
            }
        }
        val manager = newManager()

        manager.recordPlayIfNeeded("post-1") // fails -> marker dropped
        advanceUntilIdle()
        manager.recordPlayIfNeeded("post-1") // retried
        advanceUntilIdle()

        verifyBlocking(cloudFunctions, times(2)) { recordPlay("post-1") }
    }
}
