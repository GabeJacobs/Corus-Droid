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

/** Apple-only notification playback must retain its verdict without a queue. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class NowPlayingManagerNotificationPreviewTest {

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
        NowPlayingManager(context, cloudFunctions, preferencesDataStore, userRepository, mock(), mock<MusicServicePreference> { on { current } doReturn MutableStateFlow(fm.corus.android.data.model.MusicService.SPOTIFY) }, org.mockito.kotlin.mock(), mock(), mock(), mock(), mock(), mock(), mock(), mock(), mock(), mock(), mock(),
            bandcampPlaybackService = org.mockito.kotlin.mock(),
        )

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
    fun notificationPreviewKeepsAppleOnlyFlagAndClearsItOnNextTrack() = runTest(testDispatcher) {
        val manager = newManager()
        manager.startForegroundServiceAction = {}
        try {
            for (absent in listOf(true, false)) {
                manager.play(
                    trackId = if (absent) "am:exclusive" else "am:unknown",
                    trackName = "Song",
                    artistName = "Artist",
                    albumArtURL = null,
                    previewUrl = "file:///android_asset/preview.m4a",
                    source = fm.corus.android.data.model.TrackSource.APPLEMUSIC,
                    notOnSpotify = absent,
                )
                assertEquals(absent, manager.state.value.notOnSpotify)
                assertFalse(manager.state.value.hasNext)
            }
        } finally {
            manager.stop()
        }
    }
}
