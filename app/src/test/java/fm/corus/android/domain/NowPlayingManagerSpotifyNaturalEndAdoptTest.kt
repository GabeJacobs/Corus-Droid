package fm.corus.android.domain

import android.app.Application
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.test.core.app.ApplicationProvider
import fm.corus.android.data.local.PreferencesDataStore
import fm.corus.android.data.model.MusicService
import fm.corus.android.data.remote.CloudFunctionsDataSource
import fm.corus.android.data.repository.UserRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class NowPlayingManagerSpotifyNaturalEndAdoptTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var context: Context
    private val currentTrackUri = MutableStateFlow<String?>(null)
    private val isPlaying = MutableStateFlow(false)
    private val positionSeconds = MutableStateFlow(0.0)
    private val durationSeconds = MutableStateFlow(0.0)
    private lateinit var spotifyPlaybackService: SpotifyPlaybackService
    private val musicServicePreference = mock<MusicServicePreference> {
        on { current } doReturn MutableStateFlow(MusicService.SPOTIFY)
    }

    private val preferencesDataStore = mock<PreferencesDataStore> {
        on { autoplayNextSong } doReturn MutableStateFlow(true)
    }
    private val userRepository = mock<UserRepository> {
        on { this.unfollowEvents } doReturn MutableSharedFlow(extraBufferCapacity = 16)
    }

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        context = ApplicationProvider.getApplicationContext()
        whenever(preferencesDataStore.effectivePlayFullSongsSync()).thenReturn(true)
        installSpotifyApp()
        spotifyPlaybackService = mock {
            on { this.currentTrackUri } doReturn currentTrackUri
            on { this.isPlaying } doReturn isPlaying
            on { this.positionSeconds } doReturn positionSeconds
            on { this.durationSeconds } doReturn durationSeconds
            on { this.lastOutgoingTrackUri } doReturn null
        }
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun installSpotifyApp() {
        val app = context.packageManager
        Shadows.shadowOf(app).addPackage("com.spotify.music")
        Shadows.shadowOf(app).addActivityIfNotPresent(
            android.content.ComponentName("com.spotify.music", "com.spotify.music.MainActivity"),
        )
        Shadows.shadowOf(app).addIntentFilterForActivity(
            android.content.ComponentName("com.spotify.music", "com.spotify.music.MainActivity"),
            IntentFilter(Intent.ACTION_MAIN).apply { addCategory(Intent.CATEGORY_LAUNCHER) },
        )
    }

    private fun newManager(): NowPlayingManager =
        NowPlayingManager(
            context,
            mock<CloudFunctionsDataSource>(),
            preferencesDataStore,
            userRepository,
            musicServicePreference,
            mock(),
            mock(),
            mock(),
            spotifyPlaybackService,
            mock(),
            mock(),
            mock(),
            mock(),
            mock(),
            mock(),
        ).also { it.skipConnectKeepAlive = true }

    private fun track(id: String, name: String = id) = QueuedTrack(
        trackId = id,
        trackName = name,
        artistName = "Artist",
        albumArtURL = null,
        previewUrl = null,
        spotifyURI = "spotify:track:$id",
        spotifyWebURL = "https://open.spotify.com/track/$id",
        isrc = null,
        sourcePostId = "post-$id",
    )

    private fun seedConnect(
        manager: NowPlayingManager,
        tracks: List<QueuedTrack>,
        playing: QueuedTrack,
    ) {
        manager.setQueueFromCoordinator(
            queue = tracks,
            playingTrackId = playing.trackId,
            playingSourcePostId = playing.sourcePostId,
        )
        val stateField = NowPlayingManager::class.java.getDeclaredField("_state").apply {
            isAccessible = true
        }
        @Suppress("UNCHECKED_CAST")
        val flow = stateField.get(manager) as MutableStateFlow<NowPlayingState>
        flow.value = flow.value.copy(
            trackId = playing.trackId,
            trackName = playing.trackName,
            artistName = playing.artistName,
            sourcePostId = playing.sourcePostId,
            spotifyURI = playing.spotifyURI,
            isPlaying = true,
        )
        manager.testingSetIsSpotifyConnectPlaying(true)
    }

    @Test
    fun forceAdvanceAdoptsAlreadyPlayingCorrectNextWithoutPause() = runTest(testDispatcher) {
        val manager = newManager()
        val trackA = track("ne-a", "A")
        val trackB = track("ne-b", "B")
        val trackC = track("ne-c", "C")
        seedConnect(manager, listOf(trackA, trackB, trackC), trackA)
        currentTrackUri.value = trackB.spotifyURI
        isPlaying.value = true

        manager.forceSpotifyFeedAdvanceToNextEntry()

        assertEquals(trackB.trackId, manager.state.value.trackId)
        assertEquals("B", manager.state.value.trackName)
        assertTrue(isPlaying.value)
        assertTrue(manager.state.value.isPlaying)
        assertNull(manager.testingSpotifyCorusRequestedUri())
        verify(spotifyPlaybackService, never()).pauseImmediately()
        manager.stop()
    }

    @Test
    fun forceAdvanceOnPausedCorrectNextDoesNotPauseAgain() = runTest(testDispatcher) {
        val manager = newManager()
        val trackA = track("ne-pa", "A")
        val trackB = track("ne-pb", "B")
        seedConnect(manager, listOf(trackA, trackB), trackA)
        currentTrackUri.value = trackB.spotifyURI
        isPlaying.value = false

        manager.forceSpotifyFeedAdvanceToNextEntry()

        assertEquals(trackB.trackId, manager.state.value.trackId)
        verify(spotifyPlaybackService, never()).pauseImmediately()
        assertEquals(trackB.spotifyURI, manager.testingSpotifyCorusRequestedUri())
        manager.stop()
    }

    @Test
    fun naturalEndAdoptCancelsPendingForceAdvanceWithoutSkippingExtraTrack() = runTest(testDispatcher) {
        val manager = newManager()
        val trackA = track("ne-ca", "A")
        val trackB = track("ne-cb", "B")
        val trackC = track("ne-cc", "C")
        seedConnect(manager, listOf(trackA, trackB, trackC), trackA)
        currentTrackUri.value = trackA.spotifyURI
        isPlaying.value = false

        manager.testingHandleSpotifyConnectTrackEnded()
        assertTrue(manager.testingSpotifyNaturalEndAdvanceJobActive())

        currentTrackUri.value = trackB.spotifyURI
        isPlaying.value = true
        manager.testingHandleSpotifyNaturalFeedTrackEnd(trackB.spotifyURI!!)

        assertEquals(trackB.trackId, manager.state.value.trackId)
        assertFalse(manager.testingSpotifyNaturalEndAdvanceJobActive())

        advanceTimeBy(350)
        advanceUntilIdle()

        assertEquals(trackB.trackId, manager.state.value.trackId)
        assertEquals("B", manager.state.value.trackName)
        assertTrue(isPlaying.value)
        manager.stop()
    }

    @Test
    fun forceAdvanceStillAdvancesWhenAppRemoteOnWrongUri() = runTest(testDispatcher) {
        val manager = newManager()
        val trackA = track("ne-wa", "A")
        val trackB = track("ne-wb", "B")
        seedConnect(manager, listOf(trackA, trackB), trackA)
        currentTrackUri.value = "spotify:track:stale-native-queue"
        isPlaying.value = true

        manager.forceSpotifyFeedAdvanceToNextEntry()

        assertEquals(trackB.trackId, manager.queueSnapshot()[manager.currentQueueIndexSnapshot()!!].trackId)
        assertEquals("B", manager.queueSnapshot()[manager.currentQueueIndexSnapshot()!!].trackName)
        assertEquals(trackB.spotifyURI, manager.testingSpotifyCorusRequestedUri())
        manager.stop()
    }

    @Test
    fun lockedRefreshDoesNotReArmFastPathPastForceAdvanceTarget() = runTest(testDispatcher) {
        val manager = newManager()
        val trackA = track("ne-fp-a", "A")
        val trackB = track("ne-fp-b", "B")
        val trackC = track("ne-fp-c", "C")
        seedConnect(manager, listOf(trackA, trackB, trackC), trackA)
        manager.testingSetSpotifyDeviceLockedForQueueDriving(true)
        currentTrackUri.value = "spotify:track:stale-native-queue"
        positionSeconds.value = 0.2
        durationSeconds.value = 180.0
        isPlaying.value = false

        manager.forceSpotifyFeedAdvanceToNextEntry()

        verify(spotifyPlaybackService).setFastPathPlaybackGuard(
            eq(trackB.spotifyURI!!),
            anyOrNull(),
            any(),
        )

        manager.testingRefreshSpotifyFastPathSkipGuardForUpcomingTrackIfNeeded()

        verify(spotifyPlaybackService, times(1)).setFastPathPlaybackGuard(
            eq(trackB.spotifyURI!!),
            anyOrNull(),
            any(),
        )
        verify(spotifyPlaybackService, never()).setFastPathPlaybackGuard(
            eq(trackC.spotifyURI!!),
            anyOrNull(),
            any(),
        )
        manager.stop()
    }

    @Test
    fun handoffDoesNotAdoptStaleNextWhileRequestedTrackIsStarting() = runTest(testDispatcher) {
        val manager = newManager()
        val ladies = track("ladies", "Ladies")
        val unluck = track("unluck", "Unluck")
        seedConnect(manager, listOf(ladies, unluck), ladies)
        currentTrackUri.value = unluck.spotifyURI
        isPlaying.value = true
        manager.testingArmCorusPlayIntent(ladies.spotifyURI!!)

        manager.testingReconcileSpotifyQueuePosition(unluck.spotifyURI!!)

        assertEquals(ladies.trackId, manager.state.value.trackId)
        assertEquals("Ladies", manager.state.value.trackName)
        manager.stop()
    }

    @Test
    fun afterHandoffWindowAdoptsNextWhenSpotifyLandedThere() = runTest(testDispatcher) {
        val manager = newManager()
        val ladies = track("ladies-late", "Ladies")
        val unluck = track("unluck-late", "Unluck")
        seedConnect(manager, listOf(ladies, unluck), ladies)
        currentTrackUri.value = unluck.spotifyURI
        isPlaying.value = true

        manager.testingReconcileSpotifyQueuePosition(unluck.spotifyURI!!)

        assertEquals(unluck.trackId, manager.state.value.trackId)
        assertEquals("Unluck", manager.state.value.trackName)
        manager.stop()
    }

    @Test
    fun userFeedSkipDuringHandoffStillAdoptsNext() = runTest(testDispatcher) {
        val manager = newManager()
        val ladies = track("ladies-skip", "Ladies")
        val unluck = track("unluck-skip", "Unluck")
        seedConnect(manager, listOf(ladies, unluck), ladies)
        currentTrackUri.value = unluck.spotifyURI
        isPlaying.value = true
        manager.testingArmCorusPlayIntent(ladies.spotifyURI!!)
        manager.testingArmFeedSkip()

        manager.testingReconcileSpotifyQueuePosition(unluck.spotifyURI!!)

        assertEquals(unluck.trackId, manager.state.value.trackId)
        assertEquals("Unluck", manager.state.value.trackName)
        manager.stop()
    }

    @Test
    fun connectPlaybackKeepsServiceAfterTaskRemoved() = runTest(testDispatcher) {
        val manager = newManager()
        val trackA = track("ne-ka", "A")
        seedConnect(manager, listOf(trackA), trackA)

        assertTrue(manager.shouldKeepPlaybackServiceAfterTaskRemoved())

        manager.testingSetIsSpotifyConnectPlaying(false)
        val stateField = NowPlayingManager::class.java.getDeclaredField("_state").apply {
            isAccessible = true
        }
        @Suppress("UNCHECKED_CAST")
        val flow = stateField.get(manager) as MutableStateFlow<NowPlayingState>
        flow.value = flow.value.copy(isPlaying = false)

        assertFalse(manager.shouldKeepPlaybackServiceAfterTaskRemoved())
    }
}
