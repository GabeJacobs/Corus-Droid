package fm.corus.android.ui.screens.auth

import fm.corus.android.data.model.CymbalPost
import fm.corus.android.data.model.CymbalTrack
import fm.corus.android.data.model.CymbalUser
import fm.corus.android.data.model.MediaType
import fm.corus.android.domain.NowPlayingManager
import fm.corus.android.domain.toQueuedTrack
import kotlinx.coroutines.test.runTest
import org.mockito.kotlin.verifyBlocking
import org.mockito.kotlin.verifyNoMoreInteractions
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verifyNoInteractions

class UserPreviewPlaybackTest {
    @Test fun `artwork bypasses full song coordinator`() = runTest {
        val nowPlaying = mock<NowPlayingManager>()
        playUserPostPreview(post(), nowPlaying)
        verifyBlocking(nowPlaying) { play(post().toQueuedTrack(), emptyList(), previewOnly = true) }
        verifyNoMoreInteractions(nowPlaying)
    }

    @Test fun `film artwork does not start music`() = runTest {
        val nowPlaying = mock<NowPlayingManager>()
        playUserPostPreview(post().copy(mediaType = MediaType.MOVIE), nowPlaying)
        verifyNoInteractions(nowPlaying)
    }

    private fun post() = CymbalPost(
        id = "preview-post", user = CymbalUser(id = "preview-user", username = "listener", displayName = "Listener"),
        track = CymbalTrack(id = "preview-track", name = "Song", artistName = "Artist", albumName = "Album",
            albumArtURL = null, albumArtLargeURL = null, spotifyURI = "spotify:track:preview-track",
            spotifyWebURL = "", durationMs = 180000),
    )
}
