package fm.corus.android.ui.screens.live

import com.spotify.protocol.types.PlayerState
import com.spotify.protocol.types.Track
import fm.corus.android.domain.SpotifyPlaybackService
import fm.corus.android.data.model.MusicService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class LiveSpotifySyncTest {
    private val uri = "spotify:track:4cOdK2wGLETKBW3PvgPWqT"
    private val spotify = mock<SpotifyPlaybackService>()

    private suspend fun prepareSnapshot(positionMs: Long, paused: Boolean = false, trackUri: String = uri) {
        val track = Track(null, emptyList(), null, 180_000L, "Song", trackUri, null, false, false)
        whenever(spotify.refreshState(any())).thenReturn(PlayerState(track, paused, 1f, positionMs, null, null))
        whenever(spotify.currentTrackUri).thenReturn(MutableStateFlow(uri))
        whenever(spotify.isPlaying).thenReturn(MutableStateFlow(true))
        whenever(spotify.positionSeconds).thenReturn(MutableStateFlow(15.0))
        whenever(spotify.seek(any())).thenReturn(true)
        whenever(spotify.resume()).thenReturn(true)
    }

    @Test fun stalePositionDoesNotInterruptInSyncPlayback() = runTest {
        prepareSnapshot(positionMs = 30_000L)

        assertEquals(true, spotify.syncLiveTrack(uri, false, { 30_000L }) {})

        verify(spotify, never()).seek(any())
        verify(spotify, never()).resume()
    }

    @Test fun confirmedDriftStillSeeksToRoomPosition() = runTest {
        prepareSnapshot(positionMs = 27_000L)
        val first = spotify.refreshState()
        prepareSnapshot(positionMs = 27_400L)
        val second = spotify.refreshState()
        whenever(spotify.refreshState(any())).thenReturn(first, second)

        assertEquals(true, spotify.syncLiveTrack(uri, false, { 30_000L }) {})

        verify(spotify).seek(30.0)
    }

    @Test fun sustainedGapJustOverOneSecondSeeks() = runTest {
        prepareSnapshot(positionMs = 28_850L)
        val first = spotify.refreshState()
        prepareSnapshot(positionMs = 29_250L)
        val second = spotify.refreshState()
        whenever(spotify.refreshState(any())).thenReturn(first, second)
        var now = 30_000L

        assertEquals(true, spotify.syncLiveTrack(uri, false, { now.also { now = 30_400L } }) {})

        verify(spotify).seek(30.4)
    }

    @Test fun spotifyCatalogTransitionsDoNotAllowListenerLag() = runTest {
        assertEquals(false, supportsLiveListenerGrace(MusicService.SPOTIFY, false, false))
        assertEquals(false, supportsLiveListenerGrace(MusicService.SPOTIFY, false, null))
        assertEquals(false, supportsLiveListenerGrace(MusicService.SPOTIFY, true, false))
        assertEquals(false, supportsLiveListenerGrace(MusicService.SPOTIFY, false, true))
        assertEquals(true, supportsLiveListenerGrace(MusicService.SPOTIFY, true, true))
        assertEquals(true, supportsLiveListenerGrace(MusicService.APPLE_MUSIC, false, false))
    }

    @Test fun frozenProviderPositionDoesNotSeek() = runTest {
        prepareSnapshot(positionMs = 0)

        assertEquals(true, spotify.syncLiveTrack(uri, false, { 30_000L }) {})

        verify(spotify, never()).seek(any())
    }

    @Test fun exactlyOneSecondDoesNotSeek() = runTest {
        prepareSnapshot(positionMs = 29_000L)

        assertEquals(true, spotify.syncLiveTrack(uri, false, { 30_000L }) {})

        verify(spotify, never()).seek(any())
    }

    @Test fun missingSnapshotDefersCorrection() = runTest {
        prepareSnapshot(positionMs = 0)
        whenever(spotify.refreshState(any())).thenReturn(null)

        assertNull(spotify.syncLiveTrack(uri, false, { 30_000L }) {})

        verify(spotify, never()).seek(any())
        verify(spotify, never()).resume()
    }

    @Test fun pausedSpotifyResumesWithoutSeekingWhenAlreadyInSync() = runTest {
        prepareSnapshot(positionMs = 30_000L, paused = true)

        assertEquals(true, spotify.syncLiveTrack(uri, false, { 30_000L }) {})

        verify(spotify).resume()
        verify(spotify, never()).seek(any())
    }

    @Test fun intentionalTrackEndPauseIsNotResumedBySynchronization() = runTest {
        prepareSnapshot(positionMs = 179_700, paused = true)
        whenever(spotify.liveListenerEndHeld).thenReturn(true)
        whenever(spotify.liveListenerTrackUri).thenReturn(uri)

        assertNull(spotify.syncLiveTrack(uri, false, { 179_700 }) {})

        verify(spotify, never()).resume()
        verify(spotify, never()).seek(any())
    }

    @Test fun differentTrackRequestsRecoveryWithoutSeekingIt() = runTest {
        prepareSnapshot(positionMs = 0, trackUri = "spotify:track:another")

        assertEquals(false, spotify.syncLiveTrack(uri, false, { 30_000L }) {})

        verify(spotify, never()).seek(any())
    }
}
